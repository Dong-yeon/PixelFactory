package com.pixelfactory.oee.dto;

import java.util.List;

public record LineOeeResponse(
        Long lineId,
        String lineCode,
        String name,
        OeeMetricsResponse metrics,
        List<EquipmentOeeResponse> equipments
) {
}
