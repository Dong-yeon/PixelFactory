package com.pixelfactory.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.pixelfactory.ai.CycleAnomalyDetector.Anomaly;
import com.pixelfactory.ai.CycleAnomalyDetector.CycleTimeSpike;
import com.pixelfactory.ai.CycleAnomalyDetector.DefectBurst;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CycleAnomalyDetectorTest {

    @Test
    void 최소_샘플_전에는_스파이크를_판정하지_않는다() {
        CycleAnomalyDetector detector = new CycleAnomalyDetector();

        for (int i = 0; i < 9; i++) {
            assertThat(detector.onCycle(30_000 + i * 100, false)).isEmpty();
        }
        // 10번째가 극단값이어도 baseline 10개가 차기 전이면 무시
        assertThat(detector.onCycle(90_000, false)).isEmpty();
    }

    @Test
    void 기준선_대비_급증한_사이클타임은_스파이크로_감지() {
        CycleAnomalyDetector detector = new CycleAnomalyDetector();
        seedNormalCycles(detector, 20);

        Optional<Anomaly> anomaly = detector.onCycle(60_000, false);

        assertThat(anomaly).isPresent();
        assertThat(anomaly.get()).isInstanceOf(CycleTimeSpike.class);
        CycleTimeSpike spike = (CycleTimeSpike) anomaly.get();
        assertThat(spike.zScore()).isGreaterThanOrEqualTo(3.0);
        assertThat(spike.cycleTimeMs()).isEqualTo(60_000);
    }

    @Test
    void 이상_샘플은_기준선을_오염시키지_않는다() {
        CycleAnomalyDetector detector = new CycleAnomalyDetector();
        seedNormalCycles(detector, 20);

        assertThat(detector.onCycle(60_000, false)).isPresent();
        // 스파이크가 baseline에 들어갔다면 두 번째 같은 값은 정상으로 묻힐 수 있다
        assertThat(detector.onCycle(60_000, false)).isPresent();
    }

    @Test
    void 정상_범위의_변동은_무시한다() {
        CycleAnomalyDetector detector = new CycleAnomalyDetector();
        seedNormalCycles(detector, 30);

        assertThat(detector.onCycle(31_500, false)).isEmpty();
    }

    @Test
    void 불량_버스트_감지_및_쿨다운() {
        // 스파이크 판정과 분리하려고 minSamples를 크게 잡는다
        CycleAnomalyDetector detector = new CycleAnomalyDetector(30, 100, 3.0, 10, 3);

        assertThat(detector.onCycle(30_000, true)).isEmpty();
        assertThat(detector.onCycle(30_000, true)).isEmpty();
        Optional<Anomaly> anomaly = detector.onCycle(30_000, true);

        assertThat(anomaly).isPresent();
        assertThat(anomaly.get()).isInstanceOf(DefectBurst.class);
        assertThat(((DefectBurst) anomaly.get()).defectCount()).isEqualTo(3);

        // 발화 직후 window가 비워져 바로 다음 불량 1건으로는 재발화하지 않는다
        assertThat(detector.onCycle(30_000, true)).isEmpty();
    }

    private void seedNormalCycles(CycleAnomalyDetector detector, int count) {
        // 30초 ± 약간의 변동 — 실제 시뮬레이터 분포(0.9~1.3배)와 유사한 스케일
        for (int i = 0; i < count; i++) {
            long cycleTime = 30_000 + (i % 7) * 500;
            detector.onCycle(cycleTime, false);
        }
    }
}
