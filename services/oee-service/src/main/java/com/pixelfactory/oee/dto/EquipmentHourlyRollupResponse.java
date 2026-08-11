package com.pixelfactory.oee.dto;

import java.time.LocalDateTime;

/** 시간 단위 롤업 조회 — 이력/트렌드 차트의 데이터 소스. */
public record EquipmentHourlyRollupResponse(
        Long equipmentId,
        LocalDateTime hourStart,
        OeeMetricsResponse metrics
) {
}
