package com.pixelfactory.oee.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LineOeeSnapshot(
        Long lineId,
        String lineCode,
        String lineName,
        LocalDateTime windowFrom,
        LocalDateTime windowTo,
        long totalCount,
        long defectCount,
        double availability,
        double performance,
        double quality,
        double oee,
        List<OeeSnapshot> equipments
) {
}
