# WebSocket 실시간 push 계약

oee-service → web 실시간 대시보드 push. STOMP over WebSocket(SockJS fallback 포함).

## 접속

- 엔드포인트: `/ws` (SockJS)
- 인증: 쿼리 파라미터 `?token=<JWT access token>` — REST와 동일한 토큰을 그대로 재사용한다.
  브라우저 네이티브 WebSocket/SockJS는 커스텀 헤더를 못 실어 보내기 때문에 REST의
  `Authorization: Bearer` 헤더 대신 쿼리 파라미터로 받는다 (`JwtHandshakeInterceptor`).
  토큰이 없거나 유효하지 않으면 핸드셰이크 자체가 401로 거부된다.

## 구독 채널

### `/topic/events` — 전체 이벤트 실시간 feed

모든 `FactoryEvent`가 저장되는 즉시(트랜잭션 커밋 후) 그대로 broadcast된다.
payload 형식은 REST `GET /api/events/recent`가 반환하는 `FactoryEventResponse`와 동일하다.
이벤트 타임라인 UI에 사용.

### `/topic/oee/equipments/{equipmentCode}` — 설비 단위 OEE 실시간 갱신

`EQUIPMENT_STATUS_CHANGED` 또는 `CYCLE_COMPLETED` 이벤트가 해당 설비에 커밋될 때마다
`OeeCalculationService`가 재계산한 최신 OEE를 broadcast한다. payload 형식은 REST
`GET /api/oee/equipments/{equipmentCode}`와 동일한 `EquipmentOeeResponse`.

이 두 이벤트 타입만 OEE에 영향을 주므로(Availability/Performance/Quality 산출 입력),
그 외 이벤트(WORK_ORDER_CREATED 등)로는 OEE push가 발생하지 않는다 — 다만 작업지시
시작/보류는 내부적으로 `EQUIPMENT_STATUS_CHANGED`를 함께 기록하므로
("조작하면 실시간으로 반응한다" 원칙, CLAUDE.md 절대 원칙 3) 여전히 즉시 반영된다.

## 설계 메모

- 폴링이 아니라 이벤트 트리거 방식이다: `FactoryEventService.record()`가 저장 직후
  `FactoryEventRecordedEvent`를 publish하고, 리스너(`FactoryEventPushListener`,
  `OeePushListener`)가 `@TransactionalEventListener(AFTER_COMMIT)`로 구독해서 push한다.
  롤백된 이벤트는 push되지 않는다.
- 라인/시프트 단위 OEE 채널은 아직 없다 (설비 단위 계산 엔진만 구현됨).
