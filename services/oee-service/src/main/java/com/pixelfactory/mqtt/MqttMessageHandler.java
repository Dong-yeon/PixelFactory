package com.pixelfactory.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.equipment.service.EquipmentService;
import com.pixelfactory.event.domain.EventSeverity;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.SourceType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.service.FactoryEventService;
import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.service.WorkOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final EquipmentService equipmentService;
    private final FactoryEventService factoryEventService;
    private final WorkOrderService workOrderService;
    private final ObjectMapper objectMapper;

    public MqttMessageHandler(
            EquipmentService equipmentService,
            FactoryEventService factoryEventService,
            WorkOrderService workOrderService,
            ObjectMapper objectMapper
    ) {
        this.equipmentService = equipmentService;
        this.factoryEventService = factoryEventService;
        this.workOrderService = workOrderService;
        this.objectMapper = objectMapper;
    }

    // Topic contract: factory/{lineCode}/{equipmentCode}/{kind} — see docs/mqtt-topics.md
    @Transactional
    public void handle(String topic, String payload) throws Exception {
        String[] parts = topic.split("/");
        if (parts.length != 4 || !"factory".equals(parts[0])) {
            log.debug("Ignoring message on unexpected topic: {}", topic);
            return;
        }

        String equipmentCode = parts[2];
        String kind = parts[3];
        JsonNode json = objectMapper.readTree(payload);
        Long equipmentId = equipmentService.findByCode(equipmentCode)
                .map(Equipment::getId)
                .orElse(null);

        if (equipmentId == null) {
            log.warn("Received event for unknown equipment '{}'. Recording without target id.", equipmentCode);
        }

        switch (kind) {
            case "status" -> handleStatus(equipmentCode, equipmentId, json, payload);
            case "cycle" -> handleCycle(equipmentCode, equipmentId, json, payload);
            case "anomaly" -> handleAnomaly(equipmentCode, equipmentId, json, payload);
            default -> log.debug("Ignoring unsupported message kind '{}' on topic {}", kind, topic);
        }
    }

    private void handleStatus(String equipmentCode, Long equipmentId, JsonNode json, String payload) {
        EquipmentStatus status;
        try {
            status = EquipmentStatus.valueOf(json.path("status").asText());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown equipment status '{}' from {}", json.path("status").asText(), equipmentCode);
            return;
        }

        if (equipmentId != null) {
            equipmentService.changeStatus(equipmentId, status);
        }

        EventSeverity severity = switch (status) {
            case DOWN -> EventSeverity.ERROR;
            case QUALITY_HOLD -> EventSeverity.WARNING;
            default -> EventSeverity.INFO;
        };

        factoryEventService.record(
                FactoryEventType.EQUIPMENT_STATUS_CHANGED,
                SourceType.EQUIPMENT,
                equipmentId,
                TargetType.EQUIPMENT,
                equipmentId,
                null,
                null,
                severity,
                "Equipment " + equipmentCode + " changed to " + status,
                payload
        );
    }

    private void handleCycle(String equipmentCode, Long equipmentId, JsonNode json, String payload) {
        boolean defect = json.path("defect").asBoolean(false);

        // Attach the running work order at ingestion time so cycle events can be traced
        // back to a work order / lot without the simulator knowing about work orders.
        WorkOrder activeWorkOrder = equipmentId == null
                ? null
                : workOrderService.findActiveByEquipmentId(equipmentId).orElse(null);

        factoryEventService.record(
                FactoryEventType.CYCLE_COMPLETED,
                SourceType.EQUIPMENT,
                equipmentId,
                TargetType.EQUIPMENT,
                equipmentId,
                activeWorkOrder == null ? null : activeWorkOrder.getId(),
                activeWorkOrder == null ? null : activeWorkOrder.getLotNo(),
                defect ? EventSeverity.WARNING : EventSeverity.INFO,
                (defect ? "Defect cycle completed: " : "Cycle completed: ") + equipmentCode,
                payload
        );
    }

    // ai-service가 발행한 anomaly — AI_ANOMALY_DETECTED로 영속화 (계약: docs/mqtt-topics.md)
    private void handleAnomaly(String equipmentCode, Long equipmentId, JsonNode json, String payload) {
        String anomalyType = json.path("anomalyType").asText("UNKNOWN");

        WorkOrder activeWorkOrder = equipmentId == null
                ? null
                : workOrderService.findActiveByEquipmentId(equipmentId).orElse(null);

        factoryEventService.record(
                FactoryEventType.AI_ANOMALY_DETECTED,
                SourceType.AI,
                null,
                TargetType.EQUIPMENT,
                equipmentId,
                activeWorkOrder == null ? null : activeWorkOrder.getId(),
                activeWorkOrder == null ? null : activeWorkOrder.getLotNo(),
                EventSeverity.WARNING,
                "AI anomaly detected: " + equipmentCode + " " + anomalyType,
                payload
        );
    }
}
