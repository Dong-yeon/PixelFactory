package com.pixelfactory.oee.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * OEE 집계 대상 시간 구간.
 *
 * shift는 3교대(DAY 06시, EVENING 14시, NIGHT 22시) 기준이며,
 * 임의 구간 조회일 때는 null이다. 현재 시프트 구간의 끝은 "지금"이라서
 * 라이브 OEE("이번 시프트 현재까지")를 표현한다.
 */
public record OeeWindow(LocalDateTime from, LocalDateTime to, String shift) {

    private static final int DAY_START_HOUR = 6;
    private static final int EVENING_START_HOUR = 14;
    private static final int NIGHT_START_HOUR = 22;

    public static OeeWindow currentShift(LocalDateTime now) {
        int hour = now.getHour();

        if (hour >= NIGHT_START_HOUR) {
            return new OeeWindow(now.toLocalDate().atTime(NIGHT_START_HOUR, 0), now, "NIGHT");
        }
        if (hour < DAY_START_HOUR) {
            return new OeeWindow(now.toLocalDate().minusDays(1).atTime(NIGHT_START_HOUR, 0), now, "NIGHT");
        }
        if (hour < EVENING_START_HOUR) {
            return new OeeWindow(now.toLocalDate().atTime(DAY_START_HOUR, 0), now, "DAY");
        }
        return new OeeWindow(now.toLocalDate().atTime(EVENING_START_HOUR, 0), now, "EVENING");
    }

    public static OeeWindow of(LocalDateTime from, LocalDateTime to) {
        return new OeeWindow(from, to, null);
    }

    public long plannedTimeMs() {
        return Duration.between(from, to).toMillis();
    }
}
