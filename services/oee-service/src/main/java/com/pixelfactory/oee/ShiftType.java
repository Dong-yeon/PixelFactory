package com.pixelfactory.oee;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public enum ShiftType {
    NIGHT(LocalTime.MIDNIGHT, LocalTime.of(8, 0)),
    DAY(LocalTime.of(8, 0), LocalTime.of(16, 0)),
    EVENING(LocalTime.of(16, 0), LocalTime.MIDNIGHT);

    private final LocalTime start;
    private final LocalTime end;

    ShiftType(LocalTime start, LocalTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalDateTime startOf(LocalDate date) {
        return date.atTime(start);
    }

    public LocalDateTime endOf(LocalDate date) {
        // EVENING ends at midnight of the next day.
        return end.equals(LocalTime.MIDNIGHT) ? date.plusDays(1).atStartOfDay() : date.atTime(end);
    }
}
