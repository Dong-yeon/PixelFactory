package com.pixelfactory.oee.controller;

import com.pixelfactory.common.response.ApiResponse;
import com.pixelfactory.oee.dto.EquipmentOeeResponse;
import com.pixelfactory.oee.service.OeeCalculationService;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oee")
public class OeeController {

    private final OeeCalculationService oeeCalculationService;

    public OeeController(OeeCalculationService oeeCalculationService) {
        this.oeeCalculationService = oeeCalculationService;
    }

    // 조회 시점 기준 최근 24시간 창의 설비 단위 OEE.
    @GetMapping("/equipments/{equipmentCode}")
    public ApiResponse<EquipmentOeeResponse> getEquipmentOee(@PathVariable String equipmentCode) {
        return ApiResponse.ok(oeeCalculationService.calculateForEquipment(equipmentCode, LocalDateTime.now()));
    }
}
