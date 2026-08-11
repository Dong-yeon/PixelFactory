-- OEE 계산이 (target, event_type, created_at) window 조회를 반복하므로 커버링 인덱스 추가.
create index idx_factory_events_target_window
    on factory_events (target_type, target_id, event_type, created_at);
