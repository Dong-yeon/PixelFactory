package com.pixelfactory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pixelfactory.support.AbstractIntegrationTest;
import com.pixelfactory.support.MqttTestPublisher;
import com.pixelfactory.support.TestApiClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * MQTT로 실제 사이클/상태 이벤트를 발행하고 GET /api/oee/*가 이벤트 스트림에서
 * 올바른 A×P×Q를 계산하는지 REST 계약으로 검증한다. OeeCalculatorTest(단위)는
 * 계산식 자체만 검증하므로, 이 테스트는 그 계산식에 이르는 조회·집계 배선을 검증한다.
 *
 * 설비 MCT-01(idealCycleTimeMs=45000)을 전용으로 쓴다.
 */
class OeeSummaryIntegrationTest extends AbstractIntegrationTest {

    private static final String LINE = "LINE-1";
    private static final String EQUIPMENT = "MCT-01";
    private static final long IDEAL_CYCLE_TIME_MS = 45000;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    private static MqttTestPublisher publisher;

    private TestApiClient api;

    @BeforeAll
    static void startPublisher() {
        publisher = new MqttTestPublisher(mosquittoUrl());
    }

    @AfterAll
    static void stopPublisher() {
        publisher.close();
    }

    @BeforeEach
    void login() {
        api = new TestApiClient(restTemplate, port).loginAs("operator");
    }

    @Test
    void 실제_사이클타임_합_기준으로_성능과_품질을_계산한다() {
        Long equipmentId = api.equipmentId(EQUIPMENT);
        // RUNNING 발행 "직전"을 window 시작으로 잡는다 — 고정된 버퍼(예: 1초)를 두면
        // 나머지 검증(4개 사이클 처리)이 그보다 빨리 끝나는 빠른 환경(CI)에서
        // "버퍼 구간(IDLE 취급) / 전체 window" 비율이 커져 availability가 인위적으로
        // 낮아진다 — 같은 JVM 클록이라 스큐 걱정 없이 버퍼 없이 잡아도 된다.
        LocalDateTime from = LocalDateTime.now();

        publisher.publishStatus(LINE, EQUIPMENT, "RUNNING");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(api.equipmentStatus(EQUIPMENT)).isEqualTo("RUNNING"));

        // 이상 사이클타임과 정확히 같은 사이클 4개(불량 1개) → performance=1.0(cap), quality=0.75.
        publisher.publishCycle(LINE, EQUIPMENT, IDEAL_CYCLE_TIME_MS, false);
        publisher.publishCycle(LINE, EQUIPMENT, IDEAL_CYCLE_TIME_MS, false);
        publisher.publishCycle(LINE, EQUIPMENT, IDEAL_CYCLE_TIME_MS, false);
        publisher.publishCycle(LINE, EQUIPMENT, IDEAL_CYCLE_TIME_MS, true);

        Map<String, Object> metrics = awaitMetricsWithCycleCount(equipmentId, from, 4);

        assertThat(((Number) metrics.get("cycleCount")).intValue()).isEqualTo(4);
        assertThat(((Number) metrics.get("defectCount")).intValue()).isEqualTo(1);
        // 실제 사이클타임 합 기준이라 시뮬레이터 배속과 무관하게 정확히 1.0/0.75가 나와야 한다.
        assertThat(((Number) metrics.get("performance")).doubleValue()).isEqualTo(1.0);
        assertThat(((Number) metrics.get("quality")).doubleValue()).isEqualTo(0.75);
        // availability는 폴링에 걸린 실제 경과시간에 좌우되므로 범위만 검증한다 —
        // DOWN/IDLE 구간이 전혀 없었으므로 0 근처에 머물 이유가 없다는 정도만 확인.
        assertThat(((Number) metrics.get("availability")).doubleValue()).isGreaterThan(0.1);
        double expectedOee = 1.0 * 0.75 * ((Number) metrics.get("availability")).doubleValue();
        assertThat(((Number) metrics.get("oee")).doubleValue()).isEqualTo(expectedOee, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void summary는_라인_아래에_설비별_지표를_포함한다() {
        Map<String, Object> summary = TestApiClient.asMap(api.get("/api/oee/summary").data());
        var lines = TestApiClient.asList(summary.get("lines"));
        assertThat(lines).isNotEmpty();

        var line1 = lines.stream().filter(l -> "LINE-1".equals(l.get("lineCode"))).findFirst().orElseThrow();
        var equipments = TestApiClient.asList(line1.get("equipments"));
        assertThat(equipments).extracting(eq -> eq.get("equipmentCode"))
                .contains("CNC-01", "CNC-02", "MCT-01");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> awaitMetricsWithCycleCount(Long equipmentId, LocalDateTime from, int expectedCount) {
        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> lastMetrics = new java.util.concurrent.atomic.AtomicReference<>();

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            Map<String, Object> report = TestApiClient.asMap(
                    api.get("/api/oee/equipments/" + equipmentId + "?from=" + from).data());
            Map<String, Object> equipment = (Map<String, Object>) report.get("equipment");
            Map<String, Object> metrics = (Map<String, Object>) equipment.get("metrics");
            lastMetrics.set(metrics);
            assertThat(((Number) metrics.get("cycleCount")).intValue()).isEqualTo(expectedCount);
        });

        return lastMetrics.get();
    }
}
