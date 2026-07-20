package com.pixelfactory.oee.dto;

import com.pixelfactory.equipment.domain.EquipmentStatus;
import java.time.LocalDateTime;

public record OeeSnapshot(
        Long equipmentId,
        String equipmentCode,
        String equipmentName,
        EquipmentStatus status,
        LocalDateTime windowFrom,
        LocalDateTime windowTo,
        long runtimeMs,
        long totalCount,
        long defectCount,
        double availability,
        double performance,
        double quality,
        double oee
) {
}
