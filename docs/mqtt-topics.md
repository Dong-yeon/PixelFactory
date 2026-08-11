# MQTT 토픽 계약

simulator → oee-service 이벤트 백본. 브로커는 Mosquitto(로컬 1883, 인증 없음 — 로컬 한정).

## 토픽 구조

```
factory/{lineCode}/{equipmentCode}/{kind}
```

- `lineCode`: 라인 코드 (예: `LINE-1`)
- `equipmentCode`: 설비 코드 (예: `CNC-01`) — oee-service의 equipments 마스터와 일치해야 함
- `kind`: `status` | `cycle`

oee-service는 `factory/#`를 QoS 1로 구독한다.

## 페이로드

### `status` — 설비 상태 변경

```json
{ "status": "DOWN", "reason": "BREAKDOWN", "ts": "2026-07-16T10:00:00Z" }
```

- `status`: `RUNNING` | `IDLE` | `DOWN` | `QUALITY_HOLD`
- `reason`: 선택 (상태 사유)
- 처리: equipments.status 갱신 + `EQUIPMENT_STATUS_CHANGED` 이벤트 기록
  (DOWN→ERROR, QUALITY_HOLD→WARNING, 그 외 INFO)

이 payload 스키마(`status`/`reason`/`ts`)는 MQTT 수신 경로(`MqttMessageHandler`)뿐 아니라
작업지시 조작으로 설비 상태가 바뀌는 경로(`WorkOrderService.start()`/`hold()`)에서도 동일하게
따른다 — `EQUIPMENT_STATUS_CHANGED`의 생산자가 둘이어도 OEE 계산이 payload를 하나의 형식으로만
파싱하면 되도록 하기 위함이다. 두 경로 모두 `equipments.status`를 실제로 갱신한다.

### `cycle` — 사이클 완료 (부품 1개 가공 완료)

```json
{ "cycleTimeMs": 31200, "defect": false, "ts": "2026-07-16T10:00:31Z" }
```

- `cycleTimeMs`: 실제 사이클 타임 — OEE Performance 계산 입력
- `defect`: 불량 여부 — OEE Quality 계산 입력
- 처리: `CYCLE_COMPLETED` 이벤트 기록 (defect=true → WARNING)

## OEE 계산과의 관계

`OeeCalculationService`(설비 단위, MVP)는 시프트/캘린더 개념 없이 "조회 시점 기준 최근 24시간"을
계획 시간으로 고정해 계산한다. 라인 단위 롤업과 시프트 개념 도입은 이후 과제.

- **Availability** = 가동 시간 / 계획 시간 ← `status` 이벤트의 RUNNING/DOWN 구간
- **Performance** = (이상 사이클타임 × 생산수) / 가동 시간 ← `cycle` 수 × equipments.ideal_cycle_time_ms
- **Quality** = 양품 수 / 생산 수 ← `cycle`의 defect 비율

**Quality 소스는 `CYCLE_COMPLETED` 이벤트 하나뿐이다.** `WorkOrder.producedQty`/`defectQty`는
작업지시 완료 시 사람이 입력하는 완료보고 수량으로, 검사/마감 워크플로에만 쓰이고 OEE 계산에는
들어가지 않는다 (이벤트가 단일 진실 공급원이라는 CLAUDE.md 절대 원칙 1에 따름). 두 수치는 집계
단위가 달라 자연히 어긋날 수 있으며, 이는 버그가 아니다.
