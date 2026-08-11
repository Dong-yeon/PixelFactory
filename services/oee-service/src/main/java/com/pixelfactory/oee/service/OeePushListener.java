package com.pixelfactory.oee.service;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.service.EquipmentService;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.dto.FactoryEventResponse;
import com.pixelfactory.event.service.FactoryEventRecordedEvent;
import com.pixelfactory.oee.dto.EquipmentOeeResponse;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * "작업지시 조작 → 대시보드 즉시 반영"(CLAUDE.md 절대 원칙 3)을 구현하는 지점.
 * Availability/Performance/Quality에 영향을 주는 이벤트가 커밋될 때마다 해당 설비의
 * OEE를 즉시 재계산해서 push한다 — 폴링이 아니라 이벤트 트리거 방식.
 */
@Component
public class OeePushListener {

    private static final Logger log = LoggerFactory.getLogger(OeePushListener.class);

    private static final Set<FactoryEventType> OEE_AFFECTING_TYPES =
            EnumSet.of(FactoryEventType.EQUIPMENT_STATUS_CHANGED, FactoryEventType.CYCLE_COMPLETED);

    private final OeeCalculationService oeeCalculationService;
    private final EquipmentService equipmentService;
    private final SimpMessagingTemplate messagingTemplate;

    public OeePushListener(
            OeeCalculationService oeeCalculationService,
            EquipmentService equipmentService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.oeeCalculationService = oeeCalculationService;
        this.equipmentService = equipmentService;
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFactoryEventRecorded(FactoryEventRecordedEvent recordedEvent) {
        FactoryEventResponse event = recordedEvent.event();

        if (event.targetType() != TargetType.EQUIPMENT || !OEE_AFFECTING_TYPES.contains(event.eventType())) {
            return;
        }

        equipmentService.findById(event.targetId()).ifPresentOrElse(
                this::pushOee,
                () -> log.warn("Equipment {} not found while pushing OEE update", event.targetId())
        );
    }

    private void pushOee(Equipment equipment) {
        EquipmentOeeResponse oee = oeeCalculationService.calculateForEquipment(
                equipment.getEquipmentCode(), LocalDateTime.now()
        );
        messagingTemplate.convertAndSend("/topic/oee/equipments/" + equipment.getEquipmentCode(), oee);
    }
}
