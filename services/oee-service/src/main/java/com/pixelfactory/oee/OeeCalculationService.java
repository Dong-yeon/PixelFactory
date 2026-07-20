package com.pixelfactory.oee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfactory.common.exception.BusinessException;
import com.pixelfactory.common.exception.ErrorCode;
import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.equipment.domain.ProductionLine;
import com.pixelfactory.equipment.repository.EquipmentRepository;
import com.pixelfactory.equipment.repository.ProductionLineRepository;
import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.repository.FactoryEventRepository;
import com.pixelfactory.oee.dto.LineOeeSnapshot;
import com.pixelfactory.oee.dto.OeeSnapshot;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OEE = Availability × Performance × Quality.
 *
 * 이벤트 스트림(EQUIPMENT_STATUS_CHANGED, CYCLE_COMPLETED)만으로 계산한다 —
 * 절대 원칙 1(이벤트가 단일 진실 공급원)에 따라 별도 집계 상태를 두지 않는다.
 *
 * - Availability = RUNNING 시간 / 윈도우 길이
 * - Performance  = (이상 사이클타임 × 사이클 수) / RUNNING 시간 (상한 1.0)
 * - Quality      = 양품 수 / 사이클 수 (사이클 없으면 1.0)
 */
@Service
@Transactional(readOnly = true)
public class OeeCalculationService {

    private final EquipmentRepository equipmentRepository;
    private final ProductionLineRepository productionLineRepository;
    private final FactoryEventRepository factoryEventRepository;
    private final ObjectMapper objectMapper;

    public OeeCalculationService(
            EquipmentRepository equipmentRepository,
            ProductionLineRepository productionLineRepository,
            FactoryEventRepository factoryEventRepository,
            ObjectMapper objectMapper
    ) {
        this.equipmentRepository = equipmentRepository;
        this.productionLineRepository = productionLineRepository;
        this.factoryEventRepository = factoryEventRepository;
        this.objectMapper = objectMapper;
    }

    public OeeSnapshot snapshotForEquipment(Long equipmentId, LocalDateTime from, LocalDateTime to) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Equipment not found."));
        return snapshot(equipment, from, to);
    }

    public List<OeeSnapshot> snapshotAll(LocalDateTime from, LocalDateTime to) {
        return equipmentRepository.findAll()
                .stream()
                .map(equipment -> snapshot(equipment, from, to))
                .toList();
    }

    public LineOeeSnapshot snapshotForLine(Long lineId, LocalDateTime from, LocalDateTime to) {
        ProductionLine line = productionLineRepository.findById(lineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Production line not found."));

        List<OeeSnapshot> snapshots = equipmentRepository.findAll()
                .stream()
                .filter(equipment -> line.getId().equals(equipment.getLineId()))
                .map(equipment -> snapshot(equipment, from, to))
                .toList();

        long totalCount = snapshots.stream().mapToLong(OeeSnapshot::totalCount).sum();
        long defectCount = snapshots.stream().mapToLong(OeeSnapshot::defectCount).sum();

        return new LineOeeSnapshot(
                line.getId(),
                line.getLineCode(),
                line.getName(),
                from,
                to,
                totalCount,
                defectCount,
                average(snapshots, OeeSnapshot::availability),
                average(snapshots, OeeSnapshot::performance),
                average(snapshots, OeeSnapshot::quality),
                average(snapshots, OeeSnapshot::oee),
                snapshots
        );
    }

    private OeeSnapshot snapshot(Equipment equipment, LocalDateTime from, LocalDateTime to) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveTo = to.isAfter(now) ? now : to;
        long windowMs = Math.max(0, Duration.between(from, effectiveTo).toMillis());

        long runtimeMs = windowMs == 0 ? 0 : calculateRuntimeMs(equipment.getId(), from, effectiveTo);

        List<FactoryEvent> cycles = factoryEventRepository
                .findByEventTypeAndTargetTypeAndTargetIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                        FactoryEventType.CYCLE_COMPLETED, TargetType.EQUIPMENT, equipment.getId(), from, effectiveTo);
        long totalCount = cycles.size();
        long defectCount = cycles.stream().filter(this::isDefect).count();

        double availability = windowMs == 0 ? 0 : (double) runtimeMs / windowMs;
        double performance = runtimeMs == 0 ? 0
                : Math.min(1.0, (double) equipment.getIdealCycleTimeMs() * totalCount / runtimeMs);
        double quality = totalCount == 0 ? 1.0 : (double) (totalCount - defectCount) / totalCount;
        double oee = availability * performance * quality;

        return new OeeSnapshot(
                equipment.getId(),
                equipment.getEquipmentCode(),
                equipment.getName(),
                equipment.getStatus(),
                from,
                effectiveTo,
                runtimeMs,
                totalCount,
                defectCount,
                round(availability),
                round(performance),
                round(quality),
                round(oee)
        );
    }

    /**
     * 상태 이벤트로 RUNNING 구간을 재구성한다. 윈도우 시작 시점의 상태는
     * 그 이전 마지막 상태 이벤트에서 가져오고, 없으면 IDLE로 본다.
     */
    private long calculateRuntimeMs(Long equipmentId, LocalDateTime from, LocalDateTime to) {
        EquipmentStatus current = factoryEventRepository
                .findFirstByEventTypeAndTargetTypeAndTargetIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        FactoryEventType.EQUIPMENT_STATUS_CHANGED, TargetType.EQUIPMENT, equipmentId, from)
                .map(this::parseStatus)
                .orElse(EquipmentStatus.IDLE);

        List<FactoryEvent> statusEvents = factoryEventRepository
                .findByEventTypeAndTargetTypeAndTargetIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                        FactoryEventType.EQUIPMENT_STATUS_CHANGED, TargetType.EQUIPMENT, equipmentId, from, to);

        long runtimeMs = 0;
        LocalDateTime cursor = from;
        for (FactoryEvent event : statusEvents) {
            if (current == EquipmentStatus.RUNNING) {
                runtimeMs += Duration.between(cursor, event.getCreatedAt()).toMillis();
            }
            current = parseStatus(event);
            cursor = event.getCreatedAt();
        }
        if (current == EquipmentStatus.RUNNING) {
            runtimeMs += Duration.between(cursor, to).toMillis();
        }
        return runtimeMs;
    }

    private EquipmentStatus parseStatus(FactoryEvent event) {
        try {
            JsonNode json = objectMapper.readTree(event.getPayloadJson());
            return EquipmentStatus.valueOf(json.path("status").asText());
        } catch (Exception e) {
            return EquipmentStatus.IDLE;
        }
    }

    private boolean isDefect(FactoryEvent event) {
        try {
            return objectMapper.readTree(event.getPayloadJson()).path("defect").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private double average(List<OeeSnapshot> snapshots, java.util.function.ToDoubleFunction<OeeSnapshot> metric) {
        return round(snapshots.stream().mapToDouble(metric).average().orElse(0));
    }

    private double round(double value) {
        return Math.round(value * 10000) / 10000.0;
    }
}
