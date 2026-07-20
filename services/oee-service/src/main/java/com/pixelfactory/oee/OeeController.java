package com.pixelfactory.oee;

import com.pixelfactory.common.exception.BusinessException;
import com.pixelfactory.common.exception.ErrorCode;
import com.pixelfactory.common.response.ApiResponse;
import com.pixelfactory.oee.dto.LineOeeSnapshot;
import com.pixelfactory.oee.dto.OeeSnapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조회 윈도우: date+shift가 오면 시프트 윈도우, 아니면 최근 lastMinutes(기본 60분).
 */
@RestController
@RequestMapping("/api/oee")
public class OeeController {

    private static final int DEFAULT_LAST_MINUTES = 60;
    private static final int MAX_LAST_MINUTES = 24 * 60;

    private final OeeCalculationService oeeCalculationService;

    public OeeController(OeeCalculationService oeeCalculationService) {
        this.oeeCalculationService = oeeCalculationService;
    }

    @GetMapping("/equipments")
    public ApiResponse<List<OeeSnapshot>> getAll(
            @RequestParam(required = false) Integer lastMinutes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ShiftType shift
    ) {
        Window window = resolveWindow(lastMinutes, date, shift);
        return ApiResponse.ok(oeeCalculationService.snapshotAll(window.from(), window.to()));
    }

    @GetMapping("/equipments/{equipmentId}")
    public ApiResponse<OeeSnapshot> getEquipment(
            @PathVariable Long equipmentId,
            @RequestParam(required = false) Integer lastMinutes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ShiftType shift
    ) {
        Window window = resolveWindow(lastMinutes, date, shift);
        return ApiResponse.ok(oeeCalculationService.snapshotForEquipment(equipmentId, window.from(), window.to()));
    }

    @GetMapping("/lines/{lineId}")
    public ApiResponse<LineOeeSnapshot> getLine(
            @PathVariable Long lineId,
            @RequestParam(required = false) Integer lastMinutes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ShiftType shift
    ) {
        Window window = resolveWindow(lastMinutes, date, shift);
        return ApiResponse.ok(oeeCalculationService.snapshotForLine(lineId, window.from(), window.to()));
    }

    private record Window(LocalDateTime from, LocalDateTime to) {
    }

    private Window resolveWindow(Integer lastMinutes, LocalDate date, ShiftType shift) {
        if (shift != null || date != null) {
            if (shift == null || date == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "date and shift must be provided together.");
            }
            return new Window(shift.startOf(date), shift.endOf(date));
        }

        int minutes = lastMinutes == null ? DEFAULT_LAST_MINUTES : lastMinutes;
        if (minutes < 1 || minutes > MAX_LAST_MINUTES) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "lastMinutes must be between 1 and " + MAX_LAST_MINUTES + ".");
        }
        LocalDateTime now = LocalDateTime.now();
        return new Window(now.minusMinutes(minutes), now);
    }
}
