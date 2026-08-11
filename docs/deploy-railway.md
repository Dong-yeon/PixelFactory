# Railway 배포 가이드

하나의 Railway 프로젝트에 6개 서비스를 올린다. 서비스 간 통신은
Railway **private network**(`<service>.railway.internal`)를 쓰고,
외부에 노출하는 것은 oee-service(HTTP/WS)와 web(HTTP)뿐이다.

```
[web (nginx)] ──HTTPS──▶ 방문자
      │ fetch/ws
      ▼
[oee-service] ◀─private─ [simulator]─MQTT─▶ [mosquitto] ◀─MQTT─ [ai-service]
      │ private                                  ▲                (cycle 구독,
      ▼                                          │                 anomaly 발행)
[PostgreSQL (Railway 관리형)]                oee-service 구독
```

## 1. PostgreSQL

Railway 관리형 Postgres 추가. 생성되는 `DATABASE_URL` 등의 변수를
oee-service에서 참조한다.

## 2. mosquitto

- 소스: 이 리포, **Root Directory = `infra/mosquitto`** (Dockerfile 빌드)
- 공개 도메인 불필요 — private network로 1883만 사용.
- 현재 설정은 `allow_anonymous true`. 데모 한정이며, 공개 노출하지 않는 것이 전제.

## 3. oee-service

- 소스: 이 리포, **Root Directory = `services/oee-service`** (Dockerfile 빌드)
- 환경변수:

| 변수 | 값 예시 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` (Dockerfile 기본값) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<pg private host>:5432/railway` |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | Railway Postgres 변수 참조 |
| `JWT_SECRET` | 32바이트 이상 랜덤 문자열 |
| `MQTT_BROKER_URL` | `tcp://mosquitto.railway.internal:1883` |
| `CORS_ALLOWED_ORIGINS` | `https://<web 도메인>` |

- `PORT`는 Railway가 주입하고 애플리케이션이 그대로 사용한다 (`server.port: ${PORT:8081}`).
- Flyway가 기동 시 스키마를 만든다. 데모 유저 시드 포함.

## 4. simulator

- 소스: 이 리포, **Root Directory = `simulator`** (Dockerfile 빌드)
- 공개 도메인 불필요.
- 환경변수:

| 변수 | 값 |
|---|---|
| `MQTT_URL` | `tcp://mosquitto.railway.internal:1883` |
| `SIM_SPEED` | `10` (이벤트 적재량과 트레이드오프 — 과금 주의) |

## 5. ai-service

- 소스: 이 리포, **Root Directory = `ai-service`** (Dockerfile 빌드)
- 공개 도메인 불필요.
- 환경변수: `MQTT_URL` = `tcp://mosquitto.railway.internal:1883`

## 6. web

- 소스: 이 리포, **Root Directory = `web`** (Dockerfile 빌드)
- API 오리진은 **빌드 시점 주입**이므로 Railway 서비스 설정의 build args로 넣는다:

| Build Arg | 값 |
|---|---|
| `VITE_API_BASE_URL` | `https://<oee-service 도메인>` |
| `VITE_WS_URL` | `wss://<oee-service 도메인>/ws` |

## 운영 메모

- **이벤트 적재량**: factory_events는 계속 쌓인다. SIM_SPEED 10배속 기준
  설비 3대가 분당 수십 건을 만든다. 데모를 상시 켜두려면 보존 정책
  (오래된 이벤트 삭제/롤업 배치)을 먼저 넣거나 SIM_SPEED를 낮춘다.
- **JWT_SECRET**은 반드시 로컬 기본값과 다른 값으로.
- mosquitto는 익명 허용 상태이므로 공개 도메인을 만들지 않는다.
