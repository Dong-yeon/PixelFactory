package com.pixelfactory.oee.dto;

import java.time.LocalDateTime;

public record EquipmentOeeResponse(
        String equipmentCode,
        String name,
        Long lineId,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        long plannedTimeMs,
        long runningTimeMs,
        double availability,
        long cycleCount,
        long defectCount,
        double performance,
        double quality,
        double oee
) {
}
