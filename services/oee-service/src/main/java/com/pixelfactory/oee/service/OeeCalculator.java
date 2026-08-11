package com.pixelfactory.oee.service;

import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.oee.domain.OeeInput;
import com.pixelfactory.oee.domain.OeeMetrics;
import com.pixelfactory.oee.domain.OeeWindow;
import com.pixelfactory.oee.domain.StatusChange;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * OEE = Availability × Performance × Quality.
 *
 * - Availability = RUNNING 시간 / 계획 시간(window 길이)
 * - Performance  = Σ(이상 사이클타임 × 사이클 수) / Σ(실제 사이클타임)
 *   실제 사이클타임 합 기준이라 시뮬레이터 배속(SIM_SPEED)과 무관하게 성립한다.
 * - Quality      = (전체 사이클 - 불량 사이클) / 전체 사이클
 */
@Component
public class OeeCalculator {

    public OeeMetrics calculate(OeeInput input) {
        double availability = ratio(input.runtimeMs(), input.plannedTimeMs());
        // Cycles faster than the ideal cycle time would push performance past 100%.
        double performance = Math.min(ratio(input.idealCycleTimeSumMs(), input.actualCycleTimeSumMs()), 1.0);
        double quality = input.cycleCount() == 0
                ? 0.0
                : (input.cycleCount() - input.defectCount()) / (double) input.cycleCount();
        double oee = availability * performance * quality;

        return new OeeMetrics(round(availability), round(performance), round(quality), round(oee));
    }

    /**
     * 상태 전환 타임라인을 걸어가며 window 내 RUNNING 누적 시간을 계산한다.
     *
     * @param initialStatus window 시작 시점의 상태 (window 이전 마지막 상태 이벤트). 없으면 IDLE로 본다.
     * @param changes       window 내 상태 전환 목록 (시각 오름차순)
     */
    public long runningTimeMs(OeeWindow window, EquipmentStatus initialStatus, List<StatusChange> changes) {
        long runtimeMs = 0;
        EquipmentStatus current = initialStatus == null ? EquipmentStatus.IDLE : initialStatus;
        LocalDateTime cursor = window.from();

        for (StatusChange change : changes) {
            if (!change.at().isAfter(window.from())) {
                current = change.status();
                continue;
            }
            if (change.at().isAfter(window.to())) {
                break;
            }
            if (current == EquipmentStatus.RUNNING) {
                runtimeMs += Duration.between(cursor, change.at()).toMillis();
            }
            current = change.status();
            cursor = change.at();
        }

        if (current == EquipmentStatus.RUNNING && cursor.isBefore(window.to())) {
            runtimeMs += Duration.between(cursor, window.to()).toMillis();
        }

        return runtimeMs;
    }

    private double ratio(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : numerator / (double) denominator;
    }

    private double round(double value) {
        return Math.round(value * 10000) / 10000.0;
    }
}
