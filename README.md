# 알림 발송 시스템

수강 신청 완료, 결제 확정, 강의 시작 D-1 등의 이벤트 발생 시 사용자에게 이메일/인앱 알림을 비동기로 발송하는 시스템입니다.

## 프로젝트 개요

알림 발송 요청을 접수하면 즉시 응답을 반환하고, 별도의 비동기 파이프라인(Relay → Redis Queue → Worker)이 실제 발송을 처리합니다. 발송 실패 시 자동 재시도하며, 동일 이벤트에 대한 중복 발송을 방지합니다.

### 주요 기능

| 구분 | 기능 |
|------|------|
| 필수 | 알림 발송 요청/상태 조회, 비동기 처리, 재시도, 중복 방지, 운영 복구 |
| 선택 | 예약 발송, 템플릿 관리, 읽음 처리, 최종 실패 알림 수동 재시도 |

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어/프레임워크 | Java 17, Spring Boot 3.5.0 |
| ORM | Spring Data JPA (Hibernate) |
| DB (구동용) | MySQL 8.0 (Docker Compose) |
| DB (테스트) | H2 인메모리 (`./gradlew test` 전용) |
| 큐 | Redis 7 (List 기반 메시지 큐) |
| 비동기 워커 | Python 3.12 (Relay, Worker) |
| 문서 | springdoc-openapi (Swagger UI) |
| 빌드 | Gradle, Docker Compose |

## 실행 방법

### 사전 요구사항

- Docker, Docker Compose

### 실행

```bash
# 1. 저장소 클론
git clone https://github.com/gigcot/liveclass-assignment-be-c-notification.git
cd liveclass-assignment-be-c-notification

# 2. 환경변수 설정 (기본값이 포함되어 있어 그대로 사용 가능)
cp .env.example .env

# 3. 실행
docker compose up --build
```

모든 서비스가 정상 기동되면:
- **API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html

### 종료

```bash
docker compose down        # 컨테이너 종료
docker compose down -v     # 컨테이너 + DB 볼륨 삭제
```

### 테스트 실행 (로컬)

```bash
./gradlew test
```

테스트는 H2 인메모리 DB를 사용하므로 별도 환경 구성이 필요 없습니다.

---

## 실행 시나리오

Docker Compose 기동 후, 아래 순서대로 curl 명령을 실행하면 전체 흐름을 확인할 수 있습니다.

### 1단계: 템플릿 등록

알림을 발송하려면 먼저 메시지 템플릿이 필요합니다.

```bash
# 수강 신청 완료 이메일 템플릿
curl -s -X POST http://localhost:8080/api/templates \
  -H "Content-Type: application/json" \
  -d '{
    "type": "ENROLLMENT_COMPLETE",
    "channel": "EMAIL",
    "title": "{userName}님, 수강 신청이 완료되었습니다",
    "body": "{courseName} 강의 수강 신청이 정상 처리되었습니다. 강의 시작일: {startDate}"
  }' | jq .
```

응답 예시:
```json
{
  "id": "a1b2c3d4-...",
  "type": "ENROLLMENT_COMPLETE",
  "channel": "EMAIL",
  "title": "{userName}님, 수강 신청이 완료되었습니다",
  "body": "{courseName} 강의 수강 신청이 정상 처리되었습니다. 강의 시작일: {startDate}",
  "createdAt": "2026-05-02T10:00:00"
}
```

### 2단계: 알림 발송 요청

```bash
# 응답의 Location 헤더에서 알림 ID를 확인합니다
curl -s -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -D - \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "eventId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "notificationType": "ENROLLMENT_COMPLETE",
    "channel": "EMAIL",
    "referenceData": {
      "userName": "홍길동",
      "courseName": "Spring Boot 마스터 클래스",
      "startDate": "2026-06-01"
    }
  }'
```

응답: `201 Created` + `Location: /api/notifications/{id}`

### 3단계: 알림 상태 조회

Location 헤더에서 받은 ID로 상태를 확인합니다.

```bash
# {id}를 실제 ID로 교체하세요
curl -s http://localhost:8080/api/notifications/{id} | jq .
```

비동기 처리 흐름에 따라 상태가 전이됩니다:

```
PENDING → QUEUED → SENDING → SENT
```

처음 조회하면 `PENDING`이고, 잠시 후 다시 조회하면 `SENT`로 변경됩니다.

### 4단계: 비동기 처리 로그 확인

별도 터미널에서 Relay와 Worker의 처리 로그를 확인할 수 있습니다.

```bash
# Relay 로그 — DB에서 미처리 알림을 가져와 Redis 큐에 적재
docker compose logs relay --tail=20

# Worker 로그 — Redis 큐에서 꺼내 실제 발송 (Mock)
docker compose logs worker --tail=20
```

### 5단계: 사용자 알림 목록 조회

```bash
# 전체 알림 목록
curl -s "http://localhost:8080/api/notifications/users/550e8400-e29b-41d4-a716-446655440000" | jq .

# 안읽은 알림만
curl -s "http://localhost:8080/api/notifications/users/550e8400-e29b-41d4-a716-446655440000?unreadOnly=true" | jq .
```

### 6단계: 읽음 처리

```bash
curl -s -X PATCH http://localhost:8080/api/notifications/{id}/read
```

응답: `204 No Content`

### 7단계: 읽음 상태 확인

```bash
curl -s http://localhost:8080/api/notifications/{id} | jq '{sendStatus, readAt}'
```

`readAt`에 읽음 처리 시각이 채워진 것을 확인할 수 있습니다:

```json
{
  "sendStatus": "SENT",
  "readAt": "2026-05-02T10:05:00"
}
```

---

## 요구사항 해석 및 가정

자세한 내용은 [docs/INTERPRETATION.md](docs/INTERPRETATION.md)를 참고하세요.

| 항목 | 해석 |
|------|------|
| 재시도 정책 | 발송 채널 실패에 대해 exponential backoff로 최대 3회 재시도 (4s → 8s → 16s). 5xx/429/timeout만 재시도, 4xx는 즉시 FAILED |
| 중복 방지 | API 레벨 `(eventId, userId)` 유니크 제약 + Relay `SELECT FOR UPDATE SKIP LOCKED` + Worker `/claim` 원자적 상태 전환 |
| 비동기 구조 | "실제 메시지 브로커 없이, 운영 전환 가능한 구조" → Transactional Outbox 패턴 + Redis List 큐 채택 |
| 템플릿 | `{key}` 플레이스홀더를 referenceData로 치환. 타입/채널별 최신 템플릿 자동 매칭 또는 templateId 직접 지정 |
| 운영 복구 | PENDING/QUEUED/SENDING 상태가 일정 시간 초과 시 스케줄러가 FAILED 전환 |

## 설계 결정과 이유

### 아키텍처: Hexagonal + DDD

도메인 로직(상태 전이, 재시도, 템플릿 치환)을 인프라에 의존하지 않도록 분리했습니다. 상세 결정은 [docs/adr/](docs/adr/)에 기록했습니다.

### 비동기 처리: Transactional Outbox + Redis Queue

```
Client → [Spring Boot API] → DB (Notification + OutboxEvent 원자적 저장)
                                    ↓
                              [Relay] DB 폴링 (FOR UPDATE SKIP LOCKED)
                                    ↓
                              [Redis] LPUSH
                                    ↓
                              [Worker] BRPOP → Mock 발송 → 콜백 API
```

- **Outbox 패턴**: 알림 저장과 이벤트 발행을 하나의 트랜잭션으로 처리하여 데이터 유실 방지
- **Redis List**: 실제 메시지 브로커 없이 큐 역할. 운영 환경에서는 Kafka/RabbitMQ로 교체 가능
- **Relay/Worker 분리**: 각각 독립적으로 스케일 아웃 가능 (`RELAY_REPLICAS`, `WORKER_REPLICAS`)

### 상태 전이

```
PENDING → QUEUED → SENDING → SENT
                      ↓
                    FAILED (재시도 횟수 < 3이면 다시 QUEUED)
```

### 중복 방지 (3단계 방어)

1. **DB 유니크 제약**: `(eventId, userId)` — 동일 이벤트 중복 접수 차단
2. **Relay 잠금**: `SELECT FOR UPDATE SKIP LOCKED` — 다중 Relay 인스턴스 경합 방지
3. **Worker Claim**: `QUEUED → SENDING` 원자적 전환 — 다중 Worker 중복 발송 방지

## 미구현 / 제약사항

- 실제 이메일 발송 없이 Mock 로그로 대체
- Spring Security 미적용 — userId를 API 파라미터/바디로 직접 전달
- 대량 알림 일괄 발송 API 미구현
- 알림 채널별 발송 속도 제한(rate limiting) 미구현

## AI 활용 범위

- **주 도구**: Claude Code (Opus, Sonnet) — 설계 논의, 구현, 리팩토링 전반에 활용
- **교차 검증**: Gemini — 설계 결정 시 트레이드오프 교차 검증 용도로 병행 사용
- **설계 결정**: 도메인 모델, 아키텍처(Hexagonal, Outbox 패턴 등)는 AI에 질문하고 답변을 바탕으로 본인이 판단하여 결정
- **구현**: 전체 코드 작성은 Claude Code가 수행

---

## API 목록 및 예시

Swagger UI에서 전체 API를 확인할 수 있습니다: http://localhost:8080/swagger-ui/index.html

### 공개 API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | /api/notifications | 알림 발송 요청 |
| GET | /api/notifications/{id} | 특정 알림 조회 |
| GET | /api/notifications/users/{userId} | 사용자 알림 목록 조회 |
| PATCH | /api/notifications/{id}/read | 읽음 처리 |
| GET | /api/notifications/failed | 최종 실패 알림 조회 |
| POST | /api/notifications/{id}/retry | 실패 알림 수동 재시도 |
| POST | /api/templates | 템플릿 등록 |
| GET | /api/templates | 템플릿 목록 조회 |
| DELETE | /api/templates/{id} | 템플릿 삭제 |

### 내부 API (Relay/Worker → Spring Boot 콜백)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | /internal/notifications/{id}/queued | Relay가 큐 적재 후 호출 |
| POST | /internal/notifications/{id}/claim | Worker가 발송 전 선점 |
| POST | /internal/notifications/{id}/sent | Worker가 발송 성공 후 호출 |
| POST | /internal/notifications/{id}/failed | Worker가 발송 실패 후 호출 |

### 요청/응답 예시

<details>
<summary>POST /api/notifications — 알림 발송 요청</summary>

**Request**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "eventId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "notificationType": "ENROLLMENT_COMPLETE",
  "channel": "EMAIL",
  "referenceData": {
    "userName": "홍길동",
    "courseName": "Spring Boot 마스터 클래스",
    "startDate": "2026-06-01"
  },
  "scheduledAt": null
}
```

**Response** — `201 Created`
```
Location: /api/notifications/3fa85f64-5717-4562-b3fc-2c963f66afa6
```
</details>

<details>
<summary>GET /api/notifications/{id} — 알림 상태 조회</summary>

**Response** — `200 OK`
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "eventId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "templateId": "a1b2c3d4-...",
  "channel": "EMAIL",
  "sendStatus": "SENT",
  "renderedTitle": "홍길동님, 수강 신청이 완료되었습니다",
  "renderedBody": "Spring Boot 마스터 클래스 강의 수강 신청이 정상 처리되었습니다. 강의 시작일: 2026-06-01",
  "referenceData": {
    "userName": "홍길동",
    "courseName": "Spring Boot 마스터 클래스",
    "startDate": "2026-06-01"
  },
  "retryCount": 0,
  "failureReason": null,
  "scheduledAt": null,
  "sentAt": "2026-05-02T10:00:05",
  "readAt": null,
  "createdAt": "2026-05-02T10:00:00"
}
```
</details>

<details>
<summary>POST /api/templates — 템플릿 등록</summary>

**Request**
```json
{
  "type": "ENROLLMENT_COMPLETE",
  "channel": "EMAIL",
  "title": "{userName}님, 수강 신청이 완료되었습니다",
  "body": "{courseName} 강의 수강 신청이 정상 처리되었습니다. 강의 시작일: {startDate}"
}
```

**Response** — `201 Created`
```json
{
  "id": "a1b2c3d4-...",
  "type": "ENROLLMENT_COMPLETE",
  "channel": "EMAIL",
  "title": "{userName}님, 수강 신청이 완료되었습니다",
  "body": "{courseName} 강의 수강 신청이 정상 처리되었습니다. 강의 시작일: {startDate}",
  "createdAt": "2026-05-02T10:00:00"
}
```
</details>

## 데이터 모델 설명

### ERD

```
┌──────────────────────────┐       ┌──────────────────────────┐
│   notification_template  │       │     user_notification    │
├──────────────────────────┤       ├──────────────────────────┤
│ id           UUID    PK  │◄──────│ id           UUID    PK  │
│ type         VARCHAR     │       │ user_id      UUID        │
│ channel      VARCHAR     │       │ event_id     UUID        │
│ title        VARCHAR     │       │ template_id  UUID    FK  │
│ body         TEXT        │       │ channel      VARCHAR     │
│ created_at   DATETIME    │       │ send_status  VARCHAR     │
│ updated_at   DATETIME    │       │ rendered_title VARCHAR   │
└──────────────────────────┘       │ rendered_body TEXT       │
                                   │ retry_count  INT        │
                                   │ failure_reason VARCHAR   │
┌──────────────────────────┐       │ scheduled_at DATETIME    │
│      outbox_event        │       │ sent_at      DATETIME    │
├──────────────────────────┤       │ read_at      DATETIME    │
│ id           UUID    PK  │       │ created_at   DATETIME    │
│ notification_id UUID FK  │──────►│ updated_at   DATETIME    │
│ status       VARCHAR     │       └────────────┬─────────────┘
│ payload      TEXT (JSON) │                    │ 1:N
│ scheduled_at DATETIME    │       ┌────────────┴─────────────┐
│ created_at   DATETIME    │       │ notification_reference_data│
└──────────────────────────┘       ├──────────────────────────┤
                                   │ notification_id UUID  FK │
                                   │ data_key     VARCHAR     │
                                   │ data_value   VARCHAR     │
                                   └──────────────────────────┘

제약조건: user_notification (event_id, user_id) UNIQUE
```

### 테이블 설명

| 테이블 | 설명 |
|--------|------|
| user_notification | 개별 사용자 알림. 상태 전이, 재시도 횟수, 렌더링된 메시지를 관리 |
| notification_reference_data | 알림별 참조 데이터 (Map). 템플릿 플레이스홀더 치환에 사용 |
| notification_template | 알림 타입/채널별 메시지 템플릿. `{key}` 형태 플레이스홀더 지원 |
| outbox_event | Transactional Outbox. 알림 저장과 동시에 생성되어 Relay가 폴링 |

### 상태 값

**SendStatus**: `PENDING` → `QUEUED` → `SENDING` → `SENT` / `FAILED`

**NotificationType**: `ENROLLMENT_COMPLETE`, `PAYMENT_CONFIRMED`, `CLASS_REMINDER`, `CANCELLATION`

**Channel**: `EMAIL`, `IN_APP`

**FailureReason**: `SEND_FAILED`, `TIMEOUT`, `CHANNEL_UNAVAILABLE`, `STALE_TIMEOUT`

## 테스트 실행 방법

```bash
./gradlew test
```

테스트는 H2 인메모리 DB를 사용하며, 외부 의존성(MySQL, Redis) 없이 독립 실행됩니다.
