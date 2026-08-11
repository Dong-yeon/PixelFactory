package com.pixelfactory.oee.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.oee.domain.OeeInput;
import com.pixelfactory.oee.domain.OeeMetrics;
import com.pixelfactory.oee.domain.OeeWindow;
import com.pixelfactory.oee.domain.StatusChange;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OeeCalculatorTest {

    private final OeeCalculator calculator = new OeeCalculator();

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 8, 11, 6, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 8, 11, 7, 0);
    private static final OeeWindow WINDOW = OeeWindow.of(FROM, TO);

    @Test
    void 초기상태_RUNNING_전환없음_전체가동() {
        long runtime = calculator.runningTimeMs(WINDOW, EquipmentStatus.RUNNING, List.of());

        assertThat(runtime).isEqualTo(3_600_000L);
    }

    @Test
    void 초기상태_IDLE_전환없음_가동시간_0() {
        long runtime = calculator.runningTimeMs(WINDOW, EquipmentStatus.IDLE, List.of());

        assertThat(runtime).isZero();
    }

    @Test
    void 초기상태_없으면_IDLE로_간주() {
        long runtime = calculator.runningTimeMs(WINDOW, null, List.of());

        assertThat(runtime).isZero();
    }

    @Test
    void 중간_DOWN_구간은_가동시간에서_제외() {
        List<StatusChange> changes = List.of(
                new StatusChange(FROM.plusMinutes(20), EquipmentStatus.DOWN),
                new StatusChange(FROM.plusMinutes(30), EquipmentStatus.RUNNING)
        );

        long runtime = calculator.runningTimeMs(WINDOW, EquipmentStatus.RUNNING, changes);

        // 60분 중 DOWN 10분 → RUNNING 50분
        assertThat(runtime).isEqualTo(50 * 60_000L);
    }

    @Test
    void window_시작_이전_전환은_초기상태만_갱신() {
        List<StatusChange> changes = List.of(
                new StatusChange(FROM.minusMinutes(5), EquipmentStatus.RUNNING),
                new StatusChange(FROM.plusMinutes(30), EquipmentStatus.IDLE)
        );

        long runtime = calculator.runningTimeMs(WINDOW, EquipmentStatus.IDLE, changes);

        assertThat(runtime).isEqualTo(30 * 60_000L);
    }

    @Test
    void window_종료_이후_전환은_무시() {
        List<StatusChange> changes = List.of(
                new StatusChange(TO.plusMinutes(1), EquipmentStatus.DOWN)
        );

        long runtime = calculator.runningTimeMs(WINDOW, EquipmentStatus.RUNNING, changes);

        assertThat(runtime).isEqualTo(3_600_000L);
    }

    @Test
    void OEE_기본_계산() {
        // 계획 60분, 가동 48분(A=0.8) / 이상 30초×90 vs 실제 3000초(P=0.9) / 90개 중 불량 9개(Q=0.9)
        OeeInput input = new OeeInput(
                3_600_000L,
                2_880_000L,
                90,
                9,
                3_000_000L,
                2_700_000L
        );

        OeeMetrics metrics = calculator.calculate(input);

        assertThat(metrics.availability()).isEqualTo(0.8);
        assertThat(metrics.performance()).isEqualTo(0.9);
        assertThat(metrics.quality()).isEqualTo(0.9);
        assertThat(metrics.oee()).isEqualTo(0.648);
    }

    @Test
    void 이상보다_빠른_사이클은_성능_100퍼센트로_캡() {
        OeeInput input = new OeeInput(3_600_000L, 3_600_000L, 10, 0, 250_000L, 300_000L);

        OeeMetrics metrics = calculator.calculate(input);

        assertThat(metrics.performance()).isEqualTo(1.0);
    }

    @Test
    void 사이클이_없으면_품질과_성능은_0() {
        OeeInput input = new OeeInput(3_600_000L, 1_800_000L, 0, 0, 0L, 0L);

        OeeMetrics metrics = calculator.calculate(input);

        assertThat(metrics.availability()).isEqualTo(0.5);
        assertThat(metrics.performance()).isZero();
        assertThat(metrics.quality()).isZero();
        assertThat(metrics.oee()).isZero();
    }

    @Test
    void 계획시간이_0이면_가용성은_0() {
        OeeInput input = new OeeInput(0L, 0L, 0, 0, 0L, 0L);

        OeeMetrics metrics = calculator.calculate(input);

        assertThat(metrics.availability()).isZero();
        assertThat(metrics.oee()).isZero();
    }
}
