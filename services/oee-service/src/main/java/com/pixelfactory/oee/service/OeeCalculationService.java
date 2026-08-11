package com.pixelfactory.oee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfactory.common.exception.BusinessException;
import com.pixelfactory.common.exception.ErrorCode;
import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.equipment.service.EquipmentService;
import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.repository.FactoryEventRepository;
import com.pixelfactory.oee.dto.EquipmentOeeResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설비 단위 OEE(A×P×Q) 계산 — FactoryEvent 스트림을 리플레이해서 산출한다 (이벤트가 단일
 * 진실 공급원이라는 CLAUDE.md 절대 원칙 1). 시프트/캘린더 개념은 아직 없어 "조회 시점 기준
 * 최근 24시간"을 계획 시간으로 고정한 MVP 단순화다 — 실제 가동 계획(휴게시간 등)을 반영하지
 * 않으므로 Availability 분모가 실제보다 크게(=수치가 낮게) 나올 수 있다.
 */
@Service
@Transactional(readOnly = true)
public class OeeCalculationService {

    private static final Logger log = LoggerFactory.getLogger(OeeCalculationService.class);

    private static final Duration WINDOW = Duration.ofHours(24);

    private final EquipmentService equipmentService;
    private final FactoryEventRepository factoryEventRepository;
    private final ObjectMapper objectMapper;

    public OeeCalculationService(
            EquipmentService equipmentService,
            FactoryEventRepository factoryEventRepository,
            ObjectMapper objectMapper
    ) {
        this.equipmentService = equipmentService;
        this.factoryEventRepository = factoryEventRepository;
        this.objectMapper = objectMapper;
    }

    public EquipmentOeeResponse calculateForEquipment(String equipmentCode, LocalDateTime now) {
        Equipment equipment = equipmentService.findByCode(equipmentCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Equipment not found: " + equipmentCode));

        LocalDateTime windowStart = now.minus(WINDOW);
        long plannedTimeMs = WINDOW.toMillis();
        long runningTimeMs = calculateRunningTimeMs(equipment.getId(), windowStart, now);
        CycleCounts cycleCounts = countCycles(equipment.getId(), windowStart, now);

        double availability = ratio(runningTimeMs, plannedTimeMs);
        double performance = runningTimeMs == 0
                ? 0.0
                : ratio((long) equipment.getIdealCycleTimeMs() * cycleCounts.total(), runningTimeMs);
        double quality = cycleCounts.total() == 0
                ? 0.0
                : ratio(cycleCounts.total() - cycleCounts.defect(), cycleCounts.total());

        return new EquipmentOeeResponse(
                equipment.getEquipmentCode(),
                equipment.getName(),
                equipment.getLineId(),
                windowStart,
                now,
                plannedTimeMs,
                runningTimeMs,
                round(availability),
                cycleCounts.total(),
                cycleCounts.defect(),
                round(performance),
                round(quality),
                round(availability * performance * quality)
        );
    }

    // 조회 창 시작 시점의 상태를 seed로 삼아, 창 내 EQUIPMENT_STATUS_CHANGED 전이를 순서대로
    // 리플레이하면서 RUNNING으로 머문 구간의 총 길이를 합산한다.
    private long calculateRunningTimeMs(Long equipmentId, LocalDateTime windowStart, LocalDateTime windowEnd) {
        EquipmentStatus current = resolveStatusAt(equipmentId, windowStart);
        List<FactoryEvent> transitions = factoryEventRepository
                .findByTargetTypeAndTargetIdAndEventTypeAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(
                        TargetType.EQUIPMENT, equipmentId, FactoryEventType.EQUIPMENT_STATUS_CHANGED,
                        windowStart, windowEnd
                );

        long runningMs = 0L;
        LocalDateTime cursor = windowStart;

        for (FactoryEvent event : transitions) {
            if (current == EquipmentStatus.RUNNING) {
                runningMs += Duration.between(cursor, event.getCreatedAt()).toMillis();
            }
            cursor = event.getCreatedAt();
            current = parseStatus(event).orElse(current);
        }

        if (current == EquipmentStatus.RUNNING) {
            runningMs += Duration.between(cursor, windowEnd).toMillis();
        }

        return runningMs;
    }

    private EquipmentStatus resolveStatusAt(Long equipmentId, LocalDateTime at) {
        return factoryEventRepository
                .findTopByTargetTypeAndTargetIdAndEventTypeAndCreatedAtBeforeOrderByCreatedAtDesc(
                        TargetType.EQUIPMENT, equipmentId, FactoryEventType.EQUIPMENT_STATUS_CHANGED, at
                )
                .flatMap(this::parseStatus)
                .orElse(EquipmentStatus.IDLE);
    }

    private CycleCounts countCycles(Long equipmentId, LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<FactoryEvent> cycles = factoryEventRepository
                .findByTargetTypeAndTargetIdAndEventTypeAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(
                        TargetType.EQUIPMENT, equipmentId, FactoryEventType.CYCLE_COMPLETED,
                        windowStart, windowEnd
                );

        long defectCount = cycles.stream().filter(this::isDefect).count();
        return new CycleCounts(cycles.size(), defectCount);
    }

    private boolean isDefect(FactoryEvent event) {
        if (event.getPayloadJson() == null) {
            return false;
        }
        try {
            return objectMapper.readTree(event.getPayloadJson()).path("defect").asBoolean(false);
        } catch (Exception e) {
            log.warn("Failed to parse cycle payload for event {}: {}", event.getId(), e.getMessage());
            return false;
        }
    }

    private Optional<EquipmentStatus> parseStatus(FactoryEvent event) {
        if (event.getPayloadJson() == null) {
            return Optional.empty();
        }
        try {
            String status = objectMapper.readTree(event.getPayloadJson()).path("status").asText();
            return Optional.of(EquipmentStatus.valueOf(status));
        } catch (Exception e) {
            log.warn("Failed to parse status payload for event {}: {}", event.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private double round(double value) {
        return Math.round(value * 10000) / 10000.0;
    }

    private record CycleCounts(long total, long defect) {
    }
}
