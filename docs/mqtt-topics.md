# MQTT 토픽 계약

simulator/ai-service → oee-service 이벤트 백본. 브로커는 Mosquitto(로컬 1883, 인증 없음 — 로컬 한정).

## 토픽 구조

```
factory/{lineCode}/{equipmentCode}/{kind}
```

- `lineCode`: 라인 코드 (예: `LINE-1`)
- `equipmentCode`: 설비 코드 (예: `CNC-01`) — oee-service의 equipments 마스터와 일치해야 함
- `kind`: `status` | `cycle` | `anomaly`

발행 주체: `status`/`cycle`은 simulator, `anomaly`는 ai-service.
oee-service는 `factory/#`를, ai-service는 `factory/+/+/cycle`을 QoS 1로 구독한다.

## 페이로드

### `status` — 설비 상태 변경

```json
{ "status": "DOWN", "reason": "BREAKDOWN", "ts": "2026-07-16T10:00:00Z" }
```

- `status`: `RUNNING` | `IDLE` | `DOWN` | `QUALITY_HOLD`
- `reason`: 선택 (상태 사유)
- 처리: equipments.status 갱신 + `EQUIPMENT_STATUS_CHANGED` 이벤트 기록
  (DOWN→ERROR, QUALITY_HOLD→WARNING, 그 외 INFO)

### `cycle` — 사이클 완료 (부품 1개 가공 완료)

```json
{ "cycleTimeMs": 31200, "defect": false, "ts": "2026-07-16T10:00:31Z" }
```

- `cycleTimeMs`: 실제 사이클 타임 — OEE Performance 계산 입력
- `defect`: 불량 여부 — OEE Quality 계산 입력
- 처리: `CYCLE_COMPLETED` 이벤트 기록 (defect=true → WARNING)

### `anomaly` — AI 이상 감지 (ai-service 발행)

```json
{ "anomalyType": "CYCLE_TIME_SPIKE", "cycleTimeMs": 55100, "mean": 33000, "stdDev": 3400, "zScore": 6.5, "ts": "2026-08-11T12:00:00Z" }
{ "anomalyType": "DEFECT_BURST", "defectCount": 3, "windowSize": 10, "ts": "2026-08-11T12:00:00Z" }
```

- `CYCLE_TIME_SPIKE`: 최근 30사이클 baseline 대비 z-score ≥ 3 (이상 샘플은 baseline 미반영)
- `DEFECT_BURST`: 최근 10사이클 중 불량 ≥ 3 (발화 후 쿨다운)
- 처리: `AI_ANOMALY_DETECTED` 이벤트 기록 (WARNING, source=AI,
  cycle과 동일하게 IN_PROGRESS 작업지시의 workOrderId/lotNo 연결)

## OEE 계산과의 관계

`GET /api/oee/*`가 이벤트 스트림에서 window 단위로 즉석 계산한다 (기본 window = 현재 시프트, 06/14/22시 3교대).

- **Availability** = RUNNING 시간 / 계획 시간 ← `status` 이벤트로 만든 상태 타임라인
  (window 직전 마지막 상태 이벤트가 초기 상태, 없으면 IDLE)
- **Performance** = Σ(이상 사이클타임 × 사이클 수) / Σ(실제 `cycleTimeMs`)
  — 실제 사이클타임 합 기준이라 시뮬레이터 배속(`SIM_SPEED`)과 무관하게 성립. 100% 캡.
- **Quality** = (전체 사이클 - 불량 사이클) / 전체 사이클 ← `cycle`의 defect 비율

### 사이클 ↔ 작업지시 연결

`cycle` 페이로드에는 작업지시 정보가 없다(시뮬레이터는 작업지시를 모른다).
oee-service가 수집 시점에 해당 설비의 IN_PROGRESS 작업지시를 조회해서
`CYCLE_COMPLETED` 이벤트에 workOrderId/lotNo를 붙인다.
