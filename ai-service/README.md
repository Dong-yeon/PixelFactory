# ai-service

`factory/+/+/cycle`을 구독해서 설비별 이상을 감지하고
`factory/{lineCode}/{equipmentCode}/anomaly`로 발행한다.
oee-service가 이를 `AI_ANOMALY_DETECTED` 이벤트로 영속화한다.
계약: [docs/mqtt-topics.md](../docs/mqtt-topics.md)

## 감지 로직 (CycleAnomalyDetector)

- **CYCLE_TIME_SPIKE** — 최근 30사이클 baseline의 평균·표준편차 대비
  z-score ≥ 3.0. 최소 10샘플 축적 후 판정하며, 이상 샘플은 baseline에
  넣지 않는다(기준선 오염 방지).
- **DEFECT_BURST** — 최근 10사이클 중 불량 3개 이상. 발화 후 window를
  비워 같은 버스트로 연속 발화하지 않는다.

## 실행

```powershell
.\gradlew.bat run
```

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `MQTT_URL` | `tcp://localhost:1883` | MQTT 브로커 주소 |

## 테스트

```powershell
.\gradlew.bat test
```
