# PixelFactory

자동차 부품 가공 라인 **OEE 실시간 모니터링** 데모.
이벤트 기반 컴포저블 구조 — 자세한 목표/원칙/로드맵은 [CLAUDE.md](CLAUDE.md) 참고.

## 구조

| 디렉터리 | 역할 | 상태 |
|---|---|---|
| `services/oee-service/` | Spring Boot 3 백엔드 (MQTT 수집·OEE 계산·실시간 push·API) | 개발 중 |
| `simulator/` | 설비 시뮬레이터 (MQTT 발행) | 동작 |
| `ai-service/` | AI 이상 감지 — cycle 스트림 구독, anomaly 발행 | 동작 |
| `web/` | 실시간 OEE 대시보드 (React + Vite, STOMP) | 동작 |
| `infra/` | docker-compose (PostgreSQL, Mosquitto) | — |
| `docs/` | MQTT 토픽 계약, 배포 가이드, 백로그 | — |

## 실행 (로컬)

요구 사항: Docker Desktop, JDK 17

```powershell
# 1. PostgreSQL + Mosquitto 기동
cd infra
docker compose up -d

# 2. 백엔드 실행 (포트 8081) — 첫 기동 시 Flyway 마이그레이션 + 데모 유저 시드
cd ..\services\oee-service
.\gradlew.bat bootRun

# 3. (별도 터미널) 시뮬레이터 실행 — 설비 3대가 MQTT로 이벤트 발행
cd simulator
.\gradlew.bat run

# 4. (별도 터미널) AI 이상 감지 실행 — cycle 구독, anomaly 발행
cd ai-service
.\gradlew.bat run

# 5. (별도 터미널) 대시보드 실행 — http://localhost:5173
cd web
npm install
npm run dev
```

- Swagger UI: http://localhost:8081/swagger-ui.html
- Health: `GET http://localhost:8081/api/health`
- 이벤트 확인: `GET /api/events/recent`, 설비 상태: `GET /api/equipments`
- MQTT 토픽 계약: [docs/mqtt-topics.md](docs/mqtt-topics.md)
- 시뮬레이터 배속: `SIM_SPEED` 환경변수 (기본 10배속)

## OEE API (Phase 2)

`from`/`to`(ISO datetime)를 생략하면 **현재 시프트**(06/14/22시 3교대) 구간으로 계산한다.

| 엔드포인트 | 설명 |
|---|---|
| `GET /api/oee/summary` | 전체 라인 OEE 스냅샷 (라인별 + 설비별 A×P×Q) |
| `GET /api/oee/lines/{lineId}` | 라인 단위 OEE |
| `GET /api/oee/equipments/{equipmentId}` | 설비 단위 OEE |

## 실시간 push (WebSocket / STOMP)

- 엔드포인트: `ws://localhost:8081/ws`
- `/topic/events` — FactoryEvent 실시간 스트림 (영속화 커밋 후 push)
- `/topic/oee` — OEE 요약 스냅샷 (기본 5초 주기, `oee.push-interval-ms`)

## 이벤트 롤업 / 보존 정책

factory_events는 계속 쌓이므로 상시 운영을 위해 매시 10분에 유지보수가 돈다
(`oee.maintenance.*` 설정, `POST /api/oee/maintenance/run`으로 수동 실행 가능).

- **롤업**: 완료된 시간(hour)마다 설비별 OEE 입력값을 `equipment_hourly_rollups`에
  물화 — raw 삭제 후에도 이력 지표 유지. 조회: `GET /api/oee/rollups?equipmentId=`
- **보존**: 대량 텔레메트리(`CYCLE_COMPLETED`, `EQUIPMENT_STATUS_CHANGED`)만
  보존기간(기본 7일, `EVENT_RETENTION_DAYS`) 경과 시 삭제.
  작업지시 이력과 `AI_ANOMALY_DETECTED`는 보존하고, 상태 이벤트는 설비별 최신
  1건을 남겨 OEE의 "window 직전 상태" 조회가 깨지지 않게 한다.

## 배포 (Railway)

각 서비스에 Dockerfile 포함. 절차는 [docs/deploy-railway.md](docs/deploy-railway.md) 참고.

## 데모 계정

비밀번호는 모두 `password` (첫 기동 시 자동 시드).

| username | 롤 |
|---|---|
| `admin` | ADMIN |
| `inspector` | INSPECTOR |
| `operator` | OPERATOR |

```http
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{
  "username": "operator",
  "password": "password"
}
```
