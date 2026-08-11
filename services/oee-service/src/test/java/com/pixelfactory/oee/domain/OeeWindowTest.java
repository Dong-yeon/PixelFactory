package com.pixelfactory.oee.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OeeWindowTest {

    @Test
    void 주간_시프트_06시부터() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 10, 30);

        OeeWindow window = OeeWindow.currentShift(now);

        assertThat(window.shift()).isEqualTo("DAY");
        assertThat(window.from()).isEqualTo(LocalDateTime.of(2026, 8, 11, 6, 0));
        assertThat(window.to()).isEqualTo(now);
    }

    @Test
    void 저녁_시프트_14시부터() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 14, 0);

        OeeWindow window = OeeWindow.currentShift(now);

        assertThat(window.shift()).isEqualTo("EVENING");
        assertThat(window.from()).isEqualTo(LocalDateTime.of(2026, 8, 11, 14, 0));
    }

    @Test
    void 야간_시프트_22시_이후() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 23, 15);

        OeeWindow window = OeeWindow.currentShift(now);

        assertThat(window.shift()).isEqualTo("NIGHT");
        assertThat(window.from()).isEqualTo(LocalDateTime.of(2026, 8, 11, 22, 0));
    }

    @Test
    void 야간_시프트_자정_넘긴_구간은_전날_22시부터() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 3, 0);

        OeeWindow window = OeeWindow.currentShift(now);

        assertThat(window.shift()).isEqualTo("NIGHT");
        assertThat(window.from()).isEqualTo(LocalDateTime.of(2026, 8, 10, 22, 0));
    }

    @Test
    void 계획시간은_구간_길이_밀리초() {
        OeeWindow window = OeeWindow.of(
                LocalDateTime.of(2026, 8, 11, 6, 0),
                LocalDateTime.of(2026, 8, 11, 6, 30)
        );

        assertThat(window.plannedTimeMs()).isEqualTo(30 * 60_000L);
    }
}
