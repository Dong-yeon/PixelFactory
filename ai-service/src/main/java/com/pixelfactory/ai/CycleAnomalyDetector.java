package com.pixelfactory.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * 설비 1대의 사이클 스트림에서 이상을 감지하는 순수 로직.
 *
 * - CYCLE_TIME_SPIKE: 최근 window의 평균·표준편차 대비 z-score가 임계값 이상인 사이클타임.
 *   이상으로 판정된 샘플은 baseline window에 넣지 않는다 — 이상치가 기준선을 오염시켜
 *   후속 이상을 놓치는 것을 막기 위함.
 * - DEFECT_BURST: 최근 defectWindow 사이클 중 불량이 defectThreshold개 이상.
 *   발화 후에는 불량 window를 비워 같은 버스트로 연속 발화하지 않는다(쿨다운).
 */
public final class CycleAnomalyDetector {

    public sealed interface Anomaly permits CycleTimeSpike, DefectBurst {
    }

    public record CycleTimeSpike(long cycleTimeMs, double mean, double stdDev, double zScore)
            implements Anomaly {
    }

    public record DefectBurst(int defectCount, int windowSize) implements Anomaly {
    }

    private final int windowSize;
    private final int minSamples;
    private final double zThreshold;
    private final int defectWindowSize;
    private final int defectThreshold;

    private final Deque<Long> cycleTimes = new ArrayDeque<>();
    private final Deque<Boolean> defects = new ArrayDeque<>();

    public CycleAnomalyDetector() {
        this(30, 10, 3.0, 10, 3);
    }

    public CycleAnomalyDetector(
            int windowSize,
            int minSamples,
            double zThreshold,
            int defectWindowSize,
            int defectThreshold
    ) {
        this.windowSize = windowSize;
        this.minSamples = minSamples;
        this.zThreshold = zThreshold;
        this.defectWindowSize = defectWindowSize;
        this.defectThreshold = defectThreshold;
    }

    public Optional<Anomaly> onCycle(long cycleTimeMs, boolean defect) {
        Optional<Anomaly> spike = checkCycleTimeSpike(cycleTimeMs);
        Optional<Anomaly> burst = checkDefectBurst(defect);
        return spike.isPresent() ? spike : burst;
    }

    private Optional<Anomaly> checkCycleTimeSpike(long cycleTimeMs) {
        if (cycleTimes.size() >= minSamples) {
            double mean = cycleTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            double variance = cycleTimes.stream()
                    .mapToDouble(t -> (t - mean) * (t - mean))
                    .average()
                    .orElse(0);
            double stdDev = Math.sqrt(variance);

            if (stdDev > 0) {
                double zScore = (cycleTimeMs - mean) / stdDev;
                if (zScore >= zThreshold) {
                    return Optional.of(new CycleTimeSpike(cycleTimeMs, mean, stdDev, zScore));
                }
            }
        }

        cycleTimes.addLast(cycleTimeMs);
        if (cycleTimes.size() > windowSize) {
            cycleTimes.removeFirst();
        }
        return Optional.empty();
    }

    private Optional<Anomaly> checkDefectBurst(boolean defect) {
        defects.addLast(defect);
        if (defects.size() > defectWindowSize) {
            defects.removeFirst();
        }

        int defectCount = (int) defects.stream().filter(Boolean::booleanValue).count();
        if (defectCount >= defectThreshold) {
            defects.clear();
            return Optional.of(new DefectBurst(defectCount, defectWindowSize));
        }
        return Optional.empty();
    }
}
