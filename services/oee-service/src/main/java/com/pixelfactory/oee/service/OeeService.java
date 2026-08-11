package com.pixelfactory.oee.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfactory.common.exception.BusinessException;
import com.pixelfactory.common.exception.ErrorCode;
import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.equipment.domain.ProductionLine;
import com.pixelfactory.equipment.repository.EquipmentRepository;
import com.pixelfactory.equipment.repository.ProductionLineRepository;
import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.repository.FactoryEventRepository;
import com.pixelfactory.oee.domain.OeeInput;
import com.pixelfactory.oee.domain.OeeWindow;
import com.pixelfactory.oee.domain.StatusChange;
import com.pixelfactory.oee.dto.EquipmentOeeReport;
import com.pixelfactory.oee.dto.EquipmentOeeResponse;
import com.pixelfactory.oee.dto.LineOeeReport;
import com.pixelfactory.oee.dto.LineOeeResponse;
import com.pixelfactory.oee.dto.OeeMetricsResponse;
import com.pixelfactory.oee.dto.OeeSummaryResponse;
import com.pixelfactory.oee.dto.OeeWindowResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 스트림(FactoryEvent)에서 OEE를 계산한다.
 * 별도 집계 테이블 없이 window 조회 시점에 즉석 계산 — 이벤트가 단일 진실 공급원.
 */
@Service
@Transactional(readOnly = true)
public class OeeService {

    private static final Logger log = LoggerFactory.getLogger(OeeService.class);

    private final EquipmentRepository equipmentRepository;
    private final ProductionLineRepository productionLineRepository;
    private final FactoryEventRepository factoryEventRepository;
    private final OeeCalculator oeeCalculator;
    private final ObjectMapper objectMapper;

    public OeeService(
            EquipmentRepository equipmentRepository,
            ProductionLineRepository productionLineRepository,
            FactoryEventRepository factoryEventRepository,
            OeeCalculator oeeCalculator,
            ObjectMapper objectMapper
    ) {
        this.equipmentRepository = equipmentRepository;
        this.productionLineRepository = productionLineRepository;
        this.factoryEventRepository = factoryEventRepository;
        this.oeeCalculator = oeeCalculator;
        this.objectMapper = objectMapper;
    }

    public EquipmentOeeReport getEquipmentOee(Long equipmentId, LocalDateTime from, LocalDateTime to) {
        OeeWindow window = resolveWindow(from, to);
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Equipment not found."));

        return new EquipmentOeeReport(OeeWindowResponse.from(window), buildEquipmentOee(equipment, window));
    }

    public LineOeeReport getLineOee(Long lineId, LocalDateTime from, LocalDateTime to) {
        OeeWindow window = resolveWindow(from, to);
        ProductionLine line = productionLineRepository.findById(lineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Production line not found."));

        return new LineOeeReport(OeeWindowResponse.from(window), buildLineOee(line, window));
    }

    public OeeSummaryResponse getSummary(LocalDateTime from, LocalDateTime to) {
        OeeWindow window = resolveWindow(from, to);
        List<LineOeeResponse> lines = productionLineRepository.findAll()
                .stream()
                .map(line -> buildLineOee(line, window))
                .toList();

        return new OeeSummaryResponse(OeeWindowResponse.from(window), lines);
    }

    private LineOeeResponse buildLineOee(ProductionLine line, OeeWindow window) {
        List<Equipment> equipments = equipmentRepository.findByLineId(line.getId());
        List<OeeInput> inputs = new ArrayList<>();
        List<EquipmentOeeResponse> equipmentOees = new ArrayList<>();

        for (Equipment equipment : equipments) {
            OeeInput input = buildInput(equipment, window);
            inputs.add(input);
            equipmentOees.add(toEquipmentResponse(equipment, input));
        }

        OeeInput lineInput = OeeInput.aggregate(inputs);
        OeeMetricsResponse lineMetrics = OeeMetricsResponse.of(oeeCalculator.calculate(lineInput), lineInput);
        return new LineOeeResponse(line.getId(), line.getLineCode(), line.getName(), lineMetrics, equipmentOees);
    }

    private EquipmentOeeResponse buildEquipmentOee(Equipment equipment, OeeWindow window) {
        return toEquipmentResponse(equipment, buildInput(equipment, window));
    }

    private EquipmentOeeResponse toEquipmentResponse(Equipment equipment, OeeInput input) {
        OeeMetricsResponse metrics = OeeMetricsResponse.of(oeeCalculator.calculate(input), input);
        return new EquipmentOeeResponse(
                equipment.getId(),
                equipment.getEquipmentCode(),
                equipment.getName(),
                equipment.getStatus(),
                metrics
        );
    }

    private OeeInput buildInput(Equipment equipment, OeeWindow window) {
        EquipmentStatus initialStatus = factoryEventRepository
                .findFirstByTargetTypeAndTargetIdAndEventTypeAndCreatedAtLessThanOrderByCreatedAtDesc(
                        TargetType.EQUIPMENT,
                        equipment.getId(),
                        FactoryEventType.EQUIPMENT_STATUS_CHANGED,
                        window.from()
                )
                .map(event -> parseStatus(event.getPayloadJson()))
                .orElse(null);

        List<StatusChange> changes = factoryEventRepository
                .findTargetEventsInWindow(
                        TargetType.EQUIPMENT,
                        equipment.getId(),
                        FactoryEventType.EQUIPMENT_STATUS_CHANGED,
                        window.from(),
                        window.to()
                )
                .stream()
                .map(event -> {
                    EquipmentStatus status = parseStatus(event.getPayloadJson());
                    return status == null ? null : new StatusChange(event.getCreatedAt(), status);
                })
                .filter(change -> change != null)
                .toList();

        long runtimeMs = oeeCalculator.runningTimeMs(window, initialStatus, changes);

        List<FactoryEvent> cycleEvents = factoryEventRepository.findTargetEventsInWindow(
                TargetType.EQUIPMENT,
                equipment.getId(),
                FactoryEventType.CYCLE_COMPLETED,
                window.from(),
                window.to()
        );

        int cycleCount = 0;
        int defectCount = 0;
        int timedCycleCount = 0;
        long actualCycleTimeSumMs = 0;

        for (FactoryEvent event : cycleEvents) {
            cycleCount++;
            JsonNode payload = parsePayload(event.getPayloadJson());
            if (payload == null) {
                continue;
            }
            if (payload.path("defect").asBoolean(false)) {
                defectCount++;
            }
            long cycleTimeMs = payload.path("cycleTimeMs").asLong(0);
            if (cycleTimeMs > 0) {
                actualCycleTimeSumMs += cycleTimeMs;
                timedCycleCount++;
            }
        }

        long idealCycleTimeSumMs = (long) equipment.getIdealCycleTimeMs() * timedCycleCount;

        return new OeeInput(
                window.plannedTimeMs(),
                runtimeMs,
                cycleCount,
                defectCount,
                actualCycleTimeSumMs,
                idealCycleTimeSumMs
        );
    }

    private OeeWindow resolveWindow(LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            return OeeWindow.currentShift(LocalDateTime.now());
        }
        if (from == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "from is required when to is given.");
        }

        LocalDateTime resolvedTo = to == null ? LocalDateTime.now() : to;
        if (!resolvedTo.isAfter(from)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "to must be after from.");
        }
        return OeeWindow.of(from, resolvedTo);
    }

    private EquipmentStatus parseStatus(String payloadJson) {
        JsonNode payload = parsePayload(payloadJson);
        if (payload == null) {
            return null;
        }

        // Phase 1 work-order events used "equipmentStatus"; MQTT and current code use "status".
        String status = payload.path("status").asText(null);
        if (status == null) {
            status = payload.path("equipmentStatus").asText(null);
        }
        if (status == null) {
            return null;
        }

        try {
            return EquipmentStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(payloadJson);
        } catch (Exception e) {
            log.debug("Skipping unparseable event payload: {}", payloadJson, e);
            return null;
        }
    }
}
