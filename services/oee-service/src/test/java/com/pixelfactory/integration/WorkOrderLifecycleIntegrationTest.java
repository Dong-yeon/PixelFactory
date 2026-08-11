package com.pixelfactory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pixelfactory.support.AbstractIntegrationTest;
import com.pixelfactory.support.TestApiClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/**
 * REST 계약만으로 작업지시 상태머신 전 구간을 검증한다 — 조작이 설비 상태와
 * FactoryEvent를 실제로 갱신하는지가 핵심(Phase 2에서 고친 이벤트-상태 정합성).
 *
 * 설비 CNC-02를 전용으로 쓴다(MqttIngestionIntegrationTest는 CNC-01을 쓴다).
 */
class WorkOrderLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String EQUIPMENT = "CNC-02";

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    private TestApiClient api;

    @BeforeEach
    void login() {
        api = new TestApiClient(restTemplate, port).loginAs("operator");
    }

    @Test
    void 시작에서_종료까지_설비_상태와_이벤트가_함께_반응한다() {
        Long equipmentId = api.equipmentId(EQUIPMENT);
        Long workOrderId = createWorkOrder(equipmentId, "WO-LIFE-" + System.nanoTime(), "LOT-LIFE-01");

        Map<String, Object> assigned = TestApiClient.asMap(api.get("/api/work-orders/" + workOrderId).data());
        assertThat(assigned.get("status")).isEqualTo("ASSIGNED");

        // close()는 INSPECTION_WAITING/ON_HOLD에서만 허용 — ASSIGNED에서 호출하면 400.
        assertThat(api.statusOf(HttpMethod.PATCH, "/api/work-orders/" + workOrderId + "/close", null))
                .isEqualTo(HttpStatus.BAD_REQUEST);

        api.patch("/api/work-orders/" + workOrderId + "/start", null);
        assertThat(status(workOrderId)).isEqualTo("IN_PROGRESS");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(api.equipmentStatus(EQUIPMENT)).isEqualTo("RUNNING"));

        api.patch("/api/work-orders/" + workOrderId + "/complete-production",
                Map.of("producedQty", 95, "defectQty", 3));
        assertThat(status(workOrderId)).isEqualTo("INSPECTION_WAITING");
        // 생산완료 시 설비를 IDLE로 되돌린다(Phase 2 정합성 수정) — WORK_ORDER_STARTED 때 RUNNING으로
        // 바꿨던 것과 대칭.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(api.equipmentStatus(EQUIPMENT)).isEqualTo("IDLE"));

        api.patch("/api/work-orders/" + workOrderId + "/close", null);
        assertThat(status(workOrderId)).isEqualTo("COMPLETED");

        List<Map<String, Object>> events =
                TestApiClient.asList(api.get("/api/events/work-orders/" + workOrderId).data());
        List<String> eventTypes = events.stream().map(e -> (String) e.get("eventType")).toList();
        assertThat(eventTypes).contains(
                "WORK_ORDER_ASSIGNED",
                "WORK_ORDER_STARTED",
                "PRODUCTION_COMPLETED",
                "WORK_ORDER_COMPLETED"
        );
        assertThat(eventTypes.stream().filter("EQUIPMENT_STATUS_CHANGED"::equals).count())
                .as("start(RUNNING) + complete-production(IDLE) + close(IDLE) = 3건")
                .isEqualTo(3);
    }

    @Test
    void 중단하면_품질보류_상태로_전환되고_재시작하면_복구된다() {
        Long equipmentId = api.equipmentId(EQUIPMENT);
        Long workOrderId = createWorkOrder(equipmentId, "WO-HOLD-" + System.nanoTime(), "LOT-HOLD-01");

        api.patch("/api/work-orders/" + workOrderId + "/start", null);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(api.equipmentStatus(EQUIPMENT)).isEqualTo("RUNNING"));

        api.patch("/api/work-orders/" + workOrderId + "/hold", Map.of("reason", "품질 이상 확인(통합테스트)"));
        assertThat(status(workOrderId)).isEqualTo("ON_HOLD");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(api.equipmentStatus(EQUIPMENT)).isEqualTo("QUALITY_HOLD"));

        api.patch("/api/work-orders/" + workOrderId + "/start", null);
        assertThat(status(workOrderId)).isEqualTo("IN_PROGRESS");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(api.equipmentStatus(EQUIPMENT)).isEqualTo("RUNNING"));

        // ON_HOLD → COMPLETED는 완료 처리 없이도 직접 허용된다(validateTransition 계약).
        api.patch("/api/work-orders/" + workOrderId + "/hold", Map.of("reason", "재중단"));
        api.patch("/api/work-orders/" + workOrderId + "/close", null);
        assertThat(status(workOrderId)).isEqualTo("COMPLETED");
    }

    private Long createWorkOrder(Long equipmentId, String workOrderNo, String lotNo) {
        Map<String, Object> workOrder = TestApiClient.asMap(api.post("/api/work-orders", Map.of(
                "workOrderNo", workOrderNo,
                "itemId", 1, "processId", 1,
                "equipmentId", equipmentId, "assignedUserId", 1,
                "lotNo", lotNo, "plannedQty", 100,
                "plannedStartAt", LocalDateTime.now().plusMinutes(1).toString(),
                "plannedEndAt", LocalDateTime.now().plusHours(8).toString()
        )).data());
        return ((Number) workOrder.get("id")).longValue();
    }

    private String status(Long workOrderId) {
        return (String) TestApiClient.asMap(api.get("/api/work-orders/" + workOrderId).data()).get("status");
    }
}
