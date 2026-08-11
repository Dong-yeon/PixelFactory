package com.pixelfactory.oee.dto;

import com.pixelfactory.oee.domain.OeeInput;
import com.pixelfactory.oee.domain.OeeMetrics;

public record OeeMetricsResponse(
        double availability,
        double performance,
        double quality,
        double oee,
        long plannedTimeMs,
        long runtimeMs,
        int cycleCount,
        int defectCount
) {

    public static OeeMetricsResponse of(OeeMetrics metrics, OeeInput input) {
        return new OeeMetricsResponse(
                metrics.availability(),
                metrics.performance(),
                metrics.quality(),
                metrics.oee(),
                input.plannedTimeMs(),
                input.runtimeMs(),
                input.cycleCount(),
                input.defectCount()
        );
    }
}
