package com.pixelfactory.oee.domain;

import java.util.Collection;

/**
 * OEE 계산 입력값. 이벤트 스트림에서 집계한 원시 수치로,
 * 설비 단위 입력을 그대로 합산하면 라인 단위 입력이 된다.
 *
 * idealCycleTimeSumMs는 (이상 사이클타임 × 사이클타임이 보고된 사이클 수)의 합 —
 * 설비마다 이상 사이클타임이 달라도 라인 합산이 성립하도록 곱해서 보관한다.
 */
public record OeeInput(
        long plannedTimeMs,
        long runtimeMs,
        int cycleCount,
        int defectCount,
        long actualCycleTimeSumMs,
        long idealCycleTimeSumMs
) {

    public static OeeInput aggregate(Collection<OeeInput> inputs) {
        long plannedTimeMs = 0;
        long runtimeMs = 0;
        int cycleCount = 0;
        int defectCount = 0;
        long actualCycleTimeSumMs = 0;
        long idealCycleTimeSumMs = 0;

        for (OeeInput input : inputs) {
            plannedTimeMs += input.plannedTimeMs();
            runtimeMs += input.runtimeMs();
            cycleCount += input.cycleCount();
            defectCount += input.defectCount();
            actualCycleTimeSumMs += input.actualCycleTimeSumMs();
            idealCycleTimeSumMs += input.idealCycleTimeSumMs();
        }

        return new OeeInput(plannedTimeMs, runtimeMs, cycleCount, defectCount, actualCycleTimeSumMs, idealCycleTimeSumMs);
    }
}
