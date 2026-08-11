-- 시간 단위 OEE 롤업 — raw 이벤트 보존기간 경과 후에도 이력 지표를 유지한다.
create table equipment_hourly_rollups (
    id bigserial primary key,
    equipment_id bigint not null references equipments (id),
    hour_start timestamp not null,
    planned_time_ms bigint not null,
    runtime_ms bigint not null,
    cycle_count integer not null,
    defect_count integer not null,
    actual_cycle_time_sum_ms bigint not null,
    ideal_cycle_time_sum_ms bigint not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uq_equipment_hourly_rollups unique (equipment_id, hour_start)
);
