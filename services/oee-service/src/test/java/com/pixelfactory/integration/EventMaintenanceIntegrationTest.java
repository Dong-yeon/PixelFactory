package com.pixelfactory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.pixelfactory.support.AbstractIntegrationTest;
import com.pixelfactory.support.TestApiClient;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 롤업 + 보존 정책(POST /api/oee/maintenance/run)을 실제 DB 데이터로 검증한다.
 *
 * 시간을 통제해야 하므로(며칠 전 이벤트) MQTT/REST가 아니라 JdbcTemplate으로 직접
 * created_at을 소급해 심는다 — @CreatedDate는 persist 시점에 "지금"으로 덮어써서
 * JPA save로는 과거 시각을 만들 수 없다.
 *
 * 각 테스트마다 전용 설비(TEST-MAINT-*)를 새로 만들어 격리한다. 특히 "최신 1건 보존"
 * 시나리오는 대상 설비에 그 두 오래된 이벤트 외의 다른 상태 이벤트가 전혀 없어야
 * 의미가 있다 — 더 최근 상태 이벤트가 하나라도 있으면 "대체된 이벤트"가 되어
 * 오래된 것들이 전부(둘 다) 삭제 대상이 되기 때문에, 두 시나리오를 설비 단위로 분리한다.
 */
class EventMaintenanceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    private TestApiClient api;

    @BeforeEach
    void login() {
        api = new TestApiClient(restTemplate, port).loginAs("admin");
    }

    @Test
    void 완료된_시간을_시간단위_롤업으로_물화한다() {
        long equipmentId = createTestEquipment();
        LocalDateTime rollupHour = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.HOURS);

        insertStatusEvent(equipmentId, "RUNNING", rollupHour.plusMinutes(5));
        insertCycleEvent(equipmentId, 30000, false, rollupHour.plusMinutes(10));
        insertCycleEvent(equipmentId, 30000, false, rollupHour.plusMinutes(20));
        insertCycleEvent(equipmentId, 30000, true, rollupHour.plusMinutes(30));
        insertStatusEvent(equipmentId, "DOWN", rollupHour.plusMinutes(40));

        api.post("/api/oee/maintenance/run", null);

        List<Map<String, Object>> rollups = TestApiClient.asList(api.get(
                "/api/oee/rollups?equipmentId=" + equipmentId
                        + "&from=" + rollupHour
                        + "&to=" + rollupHour.plusHours(1)
        ).data());
        assertThat(rollups).hasSize(1);
        Map<String, Object> metrics = TestApiClient.asMap(rollups.get(0).get("metrics"));
        assertThat(((Number) metrics.get("cycleCount")).intValue()).isEqualTo(3);
        assertThat(((Number) metrics.get("defectCount")).intValue()).isEqualTo(1);
        // RUNNING(+5분)에서 DOWN(+40분)까지 35분 가동.
        assertThat(((Number) metrics.get("runtimeMs")).longValue()).isEqualTo(35 * 60_000L);

        // 재실행해도 이미 롤업된 시간은 그대로 유지된다(다음 실행은 마지막 롤업 이후부터 이어간다).
        api.post("/api/oee/maintenance/run", null);
        List<Map<String, Object>> rollupsAfterRerun = TestApiClient.asList(api.get(
                "/api/oee/rollups?equipmentId=" + equipmentId
                        + "&from=" + rollupHour
                        + "&to=" + rollupHour.plusHours(1)
        ).data());
        assertThat(rollupsAfterRerun).hasSize(1);
    }

    @Test
    void 보존기간이_지난_사이클_이벤트는_예외없이_삭제된다() {
        long equipmentId = createTestEquipment();
        LocalDateTime oldTelemetry = LocalDateTime.now().minusDays(8).truncatedTo(ChronoUnit.MICROS);

        insertCycleEvent(equipmentId, 30000, false, oldTelemetry);
        insertCycleEvent(equipmentId, 31000, true, oldTelemetry.plusSeconds(30));
        assertThat(countEvents(equipmentId, "CYCLE_COMPLETED")).isEqualTo(2);

        Map<String, Object> result = TestApiClient.asMap(api.post("/api/oee/maintenance/run", null).data());
        assertThat(((Number) result.get("purgedCycleEvents")).longValue()).isGreaterThanOrEqualTo(2);
        assertThat(countEvents(equipmentId, "CYCLE_COMPLETED")).isZero();
    }

    @Test
    void 보존기간이_지난_상태이벤트는_대체된_것만_삭제하고_최신_1건은_남긴다() {
        long equipmentId = createTestEquipment();
        LocalDateTime older = LocalDateTime.now().minusDays(9).truncatedTo(ChronoUnit.MICROS);
        LocalDateTime newer = LocalDateTime.now().minusDays(8).truncatedTo(ChronoUnit.MICROS);

        // 이 설비에는 이 두 건 외의 다른 상태 이벤트가 전혀 없다 — "최신 1건 보존"이
        // 의미 있게 검증되려면 필수 조건(주석 참고).
        insertStatusEvent(equipmentId, "RUNNING", older);
        insertStatusEvent(equipmentId, "IDLE", newer);
        assertThat(countEvents(equipmentId, "EQUIPMENT_STATUS_CHANGED")).isEqualTo(2);

        Map<String, Object> result = TestApiClient.asMap(api.post("/api/oee/maintenance/run", null).data());
        assertThat(((Number) result.get("purgedStatusEvents")).intValue()).isGreaterThanOrEqualTo(1);

        List<Map<String, Object>> remaining = statusEvents(equipmentId);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).get("created_at")).isEqualTo(java.sql.Timestamp.valueOf(newer));
    }

    private long createTestEquipment() {
        Long lineId = jdbcTemplate.queryForObject(
                "select id from production_lines where line_code = 'LINE-1'", Long.class);
        Long id = jdbcTemplate.queryForObject("""
                insert into equipments (equipment_code, name, line_id, ideal_cycle_time_ms, status, created_at, updated_at)
                values (?, 'Maintenance test rig', ?, 30000, 'IDLE', now(), now())
                returning id
                """, Long.class, "TEST-MAINT-" + System.nanoTime(), lineId);
        return id;
    }

    private void insertStatusEvent(long equipmentId, String status, LocalDateTime createdAt) {
        insertEvent(equipmentId, "EQUIPMENT_STATUS_CHANGED", "{\"status\":\"" + status + "\"}", createdAt);
    }

    private void insertCycleEvent(long equipmentId, long cycleTimeMs, boolean defect, LocalDateTime createdAt) {
        insertEvent(equipmentId, "CYCLE_COMPLETED",
                "{\"cycleTimeMs\":" + cycleTimeMs + ",\"defect\":" + defect + "}", createdAt);
    }

    private void insertEvent(long equipmentId, String eventType, String payloadJson, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                insert into factory_events
                    (event_type, source_type, source_id, target_type, target_id, severity, message, payload_json, created_at, updated_at)
                values (?, 'EQUIPMENT', ?, 'EQUIPMENT', ?, 'INFO', 'maintenance test seed', ?, ?, ?)
                """, eventType, equipmentId, equipmentId, payloadJson, createdAt, createdAt);
    }

    private long countEvents(long equipmentId, String eventType) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from factory_events where target_id = ? and event_type = ?",
                Long.class, equipmentId, eventType);
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> statusEvents(long equipmentId) {
        return jdbcTemplate.queryForList(
                "select created_at from factory_events where target_id = ? and event_type = 'EQUIPMENT_STATUS_CHANGED' order by created_at",
                equipmentId);
    }
}
