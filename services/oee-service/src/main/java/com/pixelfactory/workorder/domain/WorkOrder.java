package com.pixelfactory.workorder.domain;

import com.pixelfactory.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "work_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String workOrderNo;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long processId;

    @Column(nullable = false)
    private Long equipmentId;

    @Column(nullable = false)
    private Long assignedUserId;

    @Column(nullable = false, length = 50)
    private String lotNo;

    @Column(nullable = false)
    private Integer plannedQty;

    // 작업지시 완료 시 사람이 보고하는 완료 수량 — 검사/마감 워크플로용.
    // OEE Quality 계산의 소스가 아니다. OEE Quality는 FactoryEvent(CYCLE_COMPLETED)의
    // defect 비율에서만 산출한다 (docs/mqtt-topics.md 참고).
    @Column(nullable = false)
    private Integer producedQty;

    @Column(nullable = false)
    private Integer defectQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkOrderStatus status;

    @Column(nullable = false)
    private LocalDateTime plannedStartAt;

    @Column(nullable = false)
    private LocalDateTime plannedEndAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(length = 500)
    private String holdReason;

    public WorkOrder(
            String workOrderNo,
            Long itemId,
            Long processId,
            Long equipmentId,
            Long assignedUserId,
            String lotNo,
            Integer plannedQty,
            LocalDateTime plannedStartAt,
            LocalDateTime plannedEndAt
    ) {
        this.workOrderNo = workOrderNo;
        this.itemId = itemId;
        this.processId = processId;
        this.equipmentId = equipmentId;
        this.assignedUserId = assignedUserId;
        this.lotNo = lotNo;
        this.plannedQty = plannedQty;
        this.producedQty = 0;
        this.defectQty = 0;
        this.status = WorkOrderStatus.ASSIGNED;
        this.plannedStartAt = plannedStartAt;
        this.plannedEndAt = plannedEndAt;
    }

    public void start(LocalDateTime startedAt) {
        this.status = WorkOrderStatus.IN_PROGRESS;
        this.startedAt = startedAt;
        this.holdReason = null;
    }

    // producedQty/defectQty는 완료보고 스냅샷일 뿐, OEE Quality 소스가 아니다 (위 필드 주석 참고).
    public void completeProduction(int producedQty, int defectQty) {
        this.status = WorkOrderStatus.INSPECTION_WAITING;
        this.producedQty = producedQty;
        this.defectQty = defectQty;
    }

    public void hold(String holdReason) {
        this.status = WorkOrderStatus.ON_HOLD;
        this.holdReason = holdReason;
    }

    public void close(LocalDateTime completedAt) {
        this.status = WorkOrderStatus.COMPLETED;
        this.completedAt = completedAt;
    }
}
