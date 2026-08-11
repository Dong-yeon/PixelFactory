package com.pixelfactory.oee.controller;

import com.pixelfactory.common.response.ApiResponse;
import com.pixelfactory.oee.dto.EquipmentOeeReport;
import com.pixelfactory.oee.dto.LineOeeReport;
import com.pixelfactory.oee.dto.OeeSummaryResponse;
import com.pixelfactory.oee.service.OeeService;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OEE 조회 API. from/to를 생략하면 현재 시프트(06/14/22시 기준 3교대) 구간으로 계산한다.
 */
@RestController
@RequestMapping("/api/oee")
public class OeeController {

    private final OeeService oeeService;

    public OeeController(OeeService oeeService) {
        this.oeeService = oeeService;
    }

    @GetMapping("/summary")
    public ApiResponse<OeeSummaryResponse> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ApiResponse.ok(oeeService.getSummary(from, to));
    }

    @GetMapping("/lines/{lineId}")
    public ApiResponse<LineOeeReport> getLineOee(
            @PathVariable Long lineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ApiResponse.ok(oeeService.getLineOee(lineId, from, to));
    }

    @GetMapping("/equipments/{equipmentId}")
    public ApiResponse<EquipmentOeeReport> getEquipmentOee(
            @PathVariable Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ApiResponse.ok(oeeService.getEquipmentOee(equipmentId, from, to));
    }
}
