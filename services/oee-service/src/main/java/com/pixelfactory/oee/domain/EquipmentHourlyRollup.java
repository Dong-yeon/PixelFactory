package com.pixelfactory.oee.domain;

import com.pixelfactory.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설비 1대의 1시간 OEE 입력값 물화(rollup).
 * raw FactoryEvent가 보존기간 경과로 삭제된 뒤에도 이력 지표를 계산할 수 있게 한다.
 * 필드는 {@link OeeInput}과 1:1 — 그대로 합산하면 기간/라인 지표가 된다.
 */
@Getter
@Entity
@Table(
        name = "equipment_hourly_rollups",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_equipment_hourly_rollups",
                columnNames = {"equipment_id", "hour_start"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentHourlyRollup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long equipmentId;

    @Column(nullable = false)
    private LocalDateTime hourStart;

    @Column(nullable = false)
    private Long plannedTimeMs;

    @Column(nullable = false)
    private Long runtimeMs;

    @Column(nullable = false)
    private Integer cycleCount;

    @Column(nullable = false)
    private Integer defectCount;

    @Column(nullable = false)
    private Long actualCycleTimeSumMs;

    @Column(nullable = false)
    private Long idealCycleTimeSumMs;

    public EquipmentHourlyRollup(Long equipmentId, LocalDateTime hourStart, OeeInput input) {
        this.equipmentId = equipmentId;
        this.hourStart = hourStart;
        this.plannedTimeMs = input.plannedTimeMs();
        this.runtimeMs = input.runtimeMs();
        this.cycleCount = input.cycleCount();
        this.defectCount = input.defectCount();
        this.actualCycleTimeSumMs = input.actualCycleTimeSumMs();
        this.idealCycleTimeSumMs = input.idealCycleTimeSumMs();
    }

    public OeeInput toInput() {
        return new OeeInput(
                plannedTimeMs,
                runtimeMs,
                cycleCount,
                defectCount,
                actualCycleTimeSumMs,
                idealCycleTimeSumMs
        );
    }
}
