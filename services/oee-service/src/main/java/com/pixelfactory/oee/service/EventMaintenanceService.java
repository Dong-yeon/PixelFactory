package com.pixelfactory.oee.service;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.repository.EquipmentRepository;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.repository.FactoryEventRepository;
import com.pixelfactory.oee.domain.EquipmentHourlyRollup;
import com.pixelfactory.oee.domain.OeeInput;
import com.pixelfactory.oee.domain.OeeWindow;
import com.pixelfactory.oee.dto.EquipmentHourlyRollupResponse;
import com.pixelfactory.oee.dto.MaintenanceResult;
import com.pixelfactory.oee.dto.OeeMetricsResponse;
import com.pixelfactory.oee.repository.EquipmentHourlyRollupRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FactoryEvent 적재량 관리 — 상시 데모(Railway) 운영을 전제로 한다.
 *
 * 1) 롤업: 완료된 시간(hour)마다 설비별 OEE 입력값을 equipment_hourly_rollups로 물화.
 * 2) 보존: 대량 텔레메트리(CYCLE_COMPLETED, EQUIPMENT_STATUS_CHANGED)만 보존기간
 *    경과 시 삭제. 작업지시 이력·AI_ANOMALY_DETECTED는 저볼륨·고가치라 보존하고,
 *    상태 이벤트는 설비별 최신 1건을 남긴다.
 *
 * 롤업이 매시 도는데 보존기간이 며칠 단위이므로, 삭제 대상은 항상 롤업이 이미
 * 끝난 구간이다 — raw 삭제로 지표가 유실되지 않는다.
 */
@Service
public class EventMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(EventMaintenanceService.class);

    // 한 번에 롤업하는 최대 시간 수(설비당) — 오래 꺼져 있던 인스턴스의 첫 실행 폭주 방지.
    static final int MAX_HOURS_PER_EQUIPMENT_PER_RUN = 48;

    private final EquipmentRepository equipmentRepository;
    private final FactoryEventRepository factoryEventRepository;
    private final EquipmentHourlyRollupRepository rollupRepository;
    private final OeeService oeeService;
    private final OeeCalculator oeeCalculator;
    private final int retentionDays;

    public EventMaintenanceService(
            EquipmentRepository equipmentRepository,
            FactoryEventRepository factoryEventRepository,
            EquipmentHourlyRollupRepository rollupRepository,
            OeeService oeeService,
            OeeCalculator oeeCalculator,
            @Value("${oee.maintenance.retention-days:7}") int retentionDays
    ) {
        this.equipmentRepository = equipmentRepository;
        this.factoryEventRepository = factoryEventRepository;
        this.rollupRepository = rollupRepository;
        this.oeeService = oeeService;
        this.oeeCalculator = oeeCalculator;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public MaintenanceResult runMaintenance() {
        LocalDateTime now = LocalDateTime.now();

        int rolledUpHours = rollupCompletedHours(now);

        LocalDateTime cutoff = now.minusDays(retentionDays);
        long purgedCycleEvents = factoryEventRepository
                .deleteByEventTypeAndCreatedAtBefore(FactoryEventType.CYCLE_COMPLETED, cutoff);
        int purgedStatusEvents = factoryEventRepository
                .deleteOldEventsKeepingLatestPerTarget(FactoryEventType.EQUIPMENT_STATUS_CHANGED, cutoff);

        log.info("Event maintenance: rolled up {} hour(s), purged {} cycle / {} status events (cutoff={})",
                rolledUpHours, purgedCycleEvents, purgedStatusEvents, cutoff);
        return new MaintenanceResult(rolledUpHours, purgedCycleEvents, purgedStatusEvents);
    }

    @Transactional(readOnly = true)
    public List<EquipmentHourlyRollupResponse> getRollups(Long equipmentId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime resolvedTo = to == null ? LocalDateTime.now() : to;
        LocalDateTime resolvedFrom = from == null ? resolvedTo.minusHours(24) : from;

        return rollupRepository
                .findByEquipmentIdAndHourStartGreaterThanEqualAndHourStartLessThanOrderByHourStartAsc(
                        equipmentId, resolvedFrom, resolvedTo)
                .stream()
                .map(rollup -> {
                    OeeInput input = rollup.toInput();
                    return new EquipmentHourlyRollupResponse(
                            rollup.getEquipmentId(),
                            rollup.getHourStart(),
                            OeeMetricsResponse.of(oeeCalculator.calculate(input), input)
                    );
                })
                .toList();
    }

    private int rollupCompletedHours(LocalDateTime now) {
        int count = 0;

        for (Equipment equipment : equipmentRepository.findAll()) {
            LocalDateTime lastRolled = rollupRepository.findMaxHourStart(equipment.getId());
            LocalDateTime from;
            if (lastRolled != null) {
                from = lastRolled.plusHours(1);
            } else {
                LocalDateTime firstEventAt = factoryEventRepository
                        .findMinCreatedAtByTarget(TargetType.EQUIPMENT, equipment.getId());
                if (firstEventAt == null) {
                    continue;
                }
                from = firstEventAt;
            }

            for (LocalDateTime hourStart : completedHourStarts(from, now, MAX_HOURS_PER_EQUIPMENT_PER_RUN)) {
                if (rollupRepository.existsByEquipmentIdAndHourStart(equipment.getId(), hourStart)) {
                    continue;
                }
                OeeWindow window = OeeWindow.of(hourStart, hourStart.plusHours(1));
                OeeInput input = oeeService.computeInput(equipment, window);
                rollupRepository.save(new EquipmentHourlyRollup(equipment.getId(), hourStart, input));
                count++;
            }
        }
        return count;
    }

    /** from이 속한 시간부터, 아직 끝나지 않은 현재 시간을 제외한 완료된 hour 시작 시각 목록. */
    static List<LocalDateTime> completedHourStarts(LocalDateTime from, LocalDateTime now, int maxHours) {
        LocalDateTime endExclusive = now.truncatedTo(ChronoUnit.HOURS);
        List<LocalDateTime> hours = new ArrayList<>();

        LocalDateTime hour = from.truncatedTo(ChronoUnit.HOURS);
        while (hour.isBefore(endExclusive) && hours.size() < maxHours) {
            hours.add(hour);
            hour = hour.plusHours(1);
        }
        return hours;
    }
}
