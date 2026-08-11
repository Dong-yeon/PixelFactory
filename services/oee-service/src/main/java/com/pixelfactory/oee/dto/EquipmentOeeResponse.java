package com.pixelfactory.oee.dto;

import com.pixelfactory.equipment.domain.EquipmentStatus;

public record EquipmentOeeResponse(
        Long equipmentId,
        String equipmentCode,
        String name,
        EquipmentStatus status,
        OeeMetricsResponse metrics
) {
}
