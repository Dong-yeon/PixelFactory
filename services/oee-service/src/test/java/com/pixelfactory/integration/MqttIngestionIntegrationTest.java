package com.pixelfactory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pixelfactory.support.AbstractIntegrationTest;
import com.pixelfactory.support.EventAwait;
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
 * simulator/ai-service가 발행하는 것과 동일한 MQTT 메시지를 실제 브로커에 발행해서
 * MqttEventSubscriber → MqttMessageHandler → FactoryEventService 경로 전체를 검증한다.
 *
 * 설비 CNC-01을 전용으로 쓴다 — 다른 통합 테스트 클래스와 공유 Postgres 컨테이너를
 * 쓰지만 상태(설비 status, work order)가 서로 간섭하지 않도록 클래스별로 설비를 분리했다.
 */
class MqttIngestionIntegrationTest extends AbstractIntegrationTest {

    private static final String LINE = "LINE-1";
    private static final String EQUIPMENT = "CNC-01";

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
    void 상태_이벤트가_설비_상태와_FactoryEvent를_함께_갱신한다() {
        publisher.publishStatus(LINE, EQUIPMENT, "RUNNING");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(api.equipmentStatus(EQUIPMENT)).isEqualTo("RUNNING"));

        Map<String, Object> event = EventAwait.untilEvent(api, e ->
                "EQUIPMENT_STATUS_CHANGED".equals(e.get("eventType"))
                        && String.valueOf(e.get("message")).contains(EQUIPMENT + " changed to RUNNING"));
        assertThat(event.get("severity")).isEqualTo("INFO");
    }

    @Test
    void 불량_사이클은_WARNING_심각도로_기록된다() {
        publisher.publishCycle(LINE, EQUIPMENT, 31000, true);

        Map<String, Object> event = EventAwait.untilEvent(api, e ->
                "CYCLE_COMPLETED".equals(e.get("eventType"))
                        && String.valueOf(e.get("message")).startsWith("Defect cycle completed: " + EQUIPMENT));
        assertThat(event.get("severity")).isEqualTo("WARNING");
    }

    @Test
    void 고장_상태는_ERROR_심각도로_기록된다() {
        publisher.publishStatus(LINE, EQUIPMENT, "DOWN");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(api.equipmentStatus(EQUIPMENT)).isEqualTo("DOWN"));

        Map<String, Object> event = EventAwait.untilEvent(api, e ->
                "EQUIPMENT_STATUS_CHANGED".equals(e.get("eventType"))
                        && String.valueOf(e.get("message")).contains(EQUIPMENT + " changed to DOWN"));
        assertThat(event.get("severity")).isEqualTo("ERROR");

        // 다음 테스트가 RUNNING 전제를 쓸 수 있도록 복구.
        publisher.publishStatus(LINE, EQUIPMENT, "RUNNING");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(api.equipmentStatus(EQUIPMENT)).isEqualTo("RUNNING"));
    }

    @Test
    void 사이클과_이상감지_이벤트는_진행중인_작업지시의_LOT과_연결된다() {
        Long equipmentId = api.equipmentId(EQUIPMENT);
        String lotNo = "LOT-IT-" + System.nanoTime();

        Map<String, Object> workOrder = TestApiClient.asMap(api.post("/api/work-orders", Map.of(
                "workOrderNo", "WO-IT-" + System.nanoTime(),
                "itemId", 1, "processId", 1,
                "equipmentId", equipmentId, "assignedUserId", 1,
                "lotNo", lotNo, "plannedQty", 100,
                "plannedStartAt", LocalDateTime.now().plusMinutes(1).toString(),
                "plannedEndAt", LocalDateTime.now().plusHours(8).toString()
        )).data());
        Number workOrderId = (Number) workOrder.get("id");
        api.patch("/api/work-orders/" + workOrderId + "/start", null);

        // 시뮬레이터는 작업지시를 모른다 — cycle 페이로드에는 LOT 정보가 없다.
        // oee-service가 수집 시점에 진행 중인 작업지시를 조회해서 연결해야 한다(Phase 2).
        publisher.publishCycle(LINE, EQUIPMENT, 30500, false);

        Map<String, Object> cycleEvent = EventAwait.untilEvent(api, e ->
                "CYCLE_COMPLETED".equals(e.get("eventType")) && lotNo.equals(e.get("lotNo")));
        assertThat(((Number) cycleEvent.get("workOrderId")).longValue()).isEqualTo(workOrderId.longValue());

        // ai-service도 같은 방식으로 연결되어야 한다.
        publisher.publishAnomaly(LINE, EQUIPMENT, "CYCLE_TIME_SPIKE");

        Map<String, Object> anomalyEvent = EventAwait.untilEvent(api, e ->
                "AI_ANOMALY_DETECTED".equals(e.get("eventType")) && lotNo.equals(e.get("lotNo")));
        assertThat(anomalyEvent.get("severity")).isEqualTo("WARNING");
        assertThat(anomalyEvent.get("sourceType")).isEqualTo("AI");
        assertThat(String.valueOf(anomalyEvent.get("message"))).contains("CYCLE_TIME_SPIKE");
    }
}
