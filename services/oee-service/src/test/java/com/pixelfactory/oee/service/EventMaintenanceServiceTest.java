package com.pixelfactory.oee.service;

import static com.pixelfactory.oee.service.EventMaintenanceService.completedHourStarts;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventMaintenanceServiceTest {

    @Test
    void 현재_진행_중인_시간은_롤업_대상에서_제외() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 11, 10, 30);
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 12, 45);

        List<LocalDateTime> hours = completedHourStarts(from, now, 48);

        // 10시(시작 시각이 속한 시간), 11시 — 12시는 아직 미완료
        assertThat(hours).containsExactly(
                LocalDateTime.of(2026, 8, 11, 10, 0),
                LocalDateTime.of(2026, 8, 11, 11, 0)
        );
    }

    @Test
    void 정시_경계에서는_직전_시간까지만() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 11, 10, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 11, 0);

        assertThat(completedHourStarts(from, now, 48))
                .containsExactly(LocalDateTime.of(2026, 8, 11, 10, 0));
    }

    @Test
    void 완료된_시간이_없으면_빈_목록() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 11, 12, 10);
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 12, 50);

        assertThat(completedHourStarts(from, now, 48)).isEmpty();
    }

    @Test
    void 최대_시간_수_제한() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 12, 0);

        List<LocalDateTime> hours = completedHourStarts(from, now, 48);

        assertThat(hours).hasSize(48);
        assertThat(hours.get(0)).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThat(hours.get(47)).isEqualTo(LocalDateTime.of(2026, 8, 2, 23, 0));
    }

    @Test
    void 자정을_넘는_구간도_연속() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 10, 23, 5);
        LocalDateTime now = LocalDateTime.of(2026, 8, 11, 1, 30);

        assertThat(completedHourStarts(from, now, 48)).containsExactly(
                LocalDateTime.of(2026, 8, 10, 23, 0),
                LocalDateTime.of(2026, 8, 11, 0, 0)
        );
    }
}
