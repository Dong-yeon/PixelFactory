package com.pixelfactory.oee.domain;

import com.pixelfactory.equipment.domain.EquipmentStatus;
import java.time.LocalDateTime;

/** EQUIPMENT_STATUS_CHANGED 이벤트에서 추출한 설비 상태 전환 시점. */
public record StatusChange(LocalDateTime at, EquipmentStatus status) {
}
