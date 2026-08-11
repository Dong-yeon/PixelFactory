-- OEE 계산 엔진(Phase 2)이 "이 설비의 특정 이벤트 타입을 기간 내에서" 조회하는 패턴을 지원한다.
-- 예: EQUIPMENT_STATUS_CHANGED로 가동/정지 구간(Availability), CYCLE_COMPLETED로
-- 사이클수/불량수(Performance/Quality)를 target_type=EQUIPMENT, target_id=설비ID,
-- created_at 기간으로 조회.
--
-- target_type을 선두 컬럼에 둔 이유: source_id/target_id는 각 테이블의 bigserial이라
-- EQUIPMENT의 id=1과 WORK_ORDER의 id=1이 동시에 존재할 수 있어 target_id 단독으로는
-- 구분이 안 된다.
create index idx_factory_events_target_event_created
    on factory_events (target_type, target_id, event_type, created_at);
