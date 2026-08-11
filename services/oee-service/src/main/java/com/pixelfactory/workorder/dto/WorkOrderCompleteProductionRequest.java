package com.pixelfactory.workorder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 작업지시 완료 시 사람이 입력하는 완료보고 수량.
 * OEE Quality 계산에는 사용되지 않는다 — OEE Quality는 FactoryEvent(CYCLE_COMPLETED)의
 * defect 비율에서만 산출한다 (docs/mqtt-topics.md 참고).
 */
public record WorkOrderCompleteProductionRequest(
        @NotNull @Min(0) Integer producedQty,
        @NotNull @Min(0) Integer defectQty
) {
}
