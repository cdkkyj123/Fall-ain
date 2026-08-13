# API 명세 — 경청이 사랑

프로토타입 "경청이 사랑"의 REST/WebSocket API 명세다. 이 문서는 BLUEPRINT 섹션 6에서 확정된 엔드포인트 목록을 기반으로 하며, Phase 3b(BACKEND) 구현 완료 후 실제 요청/응답 스키마와 성공 응답 포맷(ADR-009 참조)이 확정되는 대로 갱신되어야 한다.

## 공통 사항

### 인증
모든 요청은 `X-Player-Id` HTTP 헤더에 클라이언트가 생성한 UUID를 담아 전송해야 한다 (ADR-003 참조). 이 헤더가 없으면 `AUTH_MISSING_PLAYER_ID`(401)를 반환한다.

```
X-Player-Id: 3fa85f64-5717-4562-b3fc-2c963f66afa6
```

### 성공 응답 포맷
BLUEPRINT 단계에서 확정하지 않음 (ADR-009). Phase 3b BACKEND 구현 시점에 결정되며, 결정 즉시 본 문서에 반영한다.

### 에러 응답 포맷
모든 에러 응답은 아래 형태로 고정한다.

```json
{
  "code": "TURN_BUDGET_EXCEEDED",
  "message": "오늘의 대화 턴을 모두 사용했습니다."
}
```

### 에러 코드 - HTTP 상태코드 매핑

| 코드 | HTTP 상태 | 의미 |
|------|-----------|------|
| `AUTH_MISSING_PLAYER_ID` | 401 | `X-Player-Id` 헤더가 없음 |
| `TURN_BUDGET_EXCEEDED` | 409 | 하루 8턴 대화 예산을 모두 소진함 |
| `TURN_IN_PROGRESS` | 409 | 이미 처리 중인 턴(pending)이 있어 신규 메시지를 받을 수 없음 (ADR-006 참조) |
| `CONCURRENT_MODIFICATION` | 409 | 낙관적락(`@Version`) 충돌 — 동시 수정 감지 (ADR-001 참조) |
| `LLM_UNAVAILABLE` | 503 | LLM 호출 실패/타임아웃 (턴 예산은 소비되지 않음, ADR-006 참조) |
| `DAY_CLOSED` | 409 | 이미 종료된 하루(day)에 대한 조작 시도 |
| `VALIDATION_ERROR` | 400 | 요청 값 검증 실패 |

---

## REST 엔드포인트

### 1. 관계 시작

```
POST /api/characters/{id}/start
```

특정 캐릭터와의 관계(연애 시뮬레이션)를 시작한다. 유저-캐릭터 관계(UserCharacter, 이하 UC) 레코드를 생성하고 초기 상태(intimacy=0, stage=초기, currentDay=1 등)를 반환한다.

**Path Parameters**

| 이름 | 타입 | 설명 |
|------|------|------|
| `id` | Long | 캐릭터 ID |

**Headers**
- `X-Player-Id` (required)

**Request Body**
없음

**Response (성공)**
관계(UC) 생성 결과. 상세 스키마는 BACKEND 구현 시 확정 (ADR-009).

**에러 케이스**

| 상황 | 코드 | 상태 |
|------|------|------|
| `X-Player-Id` 누락 | `AUTH_MISSING_PLAYER_ID` | 401 |
| 존재하지 않는 캐릭터 ID | `VALIDATION_ERROR` | 400 |
| 이미 해당 캐릭터와 관계가 존재 | `VALIDATION_ERROR` | 400 |

---

### 2. 관계 상태 조회

```
GET /api/relationships/{ucId}
```

현재 관계 상태를 조회한다. WebSocket 재연결 시 상태를 재동기화하는 단일 소스(single source of truth) 역할을 한다.

**Path Parameters**

| 이름 | 타입 | 설명 |
|------|------|------|
| `ucId` | Long | UserCharacter(관계) ID |

**Headers**
- `X-Player-Id` (required)

**Response에 포함되어야 하는 필드**

| 필드 | 설명 |
|------|------|
| `intimacy` | 현재 친밀도 점수 (서버 결정론 점수표 기반, ADR-002 참조) |
| `stage` | 관계 단계 |
| `currentDay` | 현재 게임 내 일차 |
| `turnsUsedToday` | 오늘 사용한 대화 턴 수 (예산: 하루 8턴) |
| freshness (파생) | 각 기억(Memory)의 신선도 — 저장값이 아닌 조회 시점 파생 계산 (`currentDay - lastTouchedDay` 기반, ADR-005 참조) |
| 활성 candidate | 현재 되짚기 판정 대상이 될 수 있는 활성 기억 후보 목록 |

**에러 케이스**

| 상황 | 코드 | 상태 |
|------|------|------|
| `X-Player-Id` 누락 | `AUTH_MISSING_PLAYER_ID` | 401 |
| 존재하지 않는 `ucId` 또는 소유자 불일치 | `VALIDATION_ERROR` | 400 |

---

### 3. 대화 메시지 전송 (REST 폴백)

```
POST /api/relationships/{ucId}/message
```

REST 기반 대화 전송 엔드포인트. WebSocket 연결이 불가능한 클라이언트를 위한 폴백 경로이며, WebSocket 경로와 동일한 턴 예산/락을 공유한다 (동일 `ucId`에 대해 WS와 REST 중 어느 쪽으로 요청하든 턴 소비 상태가 일관되게 유지됨).

**Path Parameters**

| 이름 | 타입 | 설명 |
|------|------|------|
| `ucId` | Long | UserCharacter(관계) ID |

**Headers**
- `X-Player-Id` (required)

**Request Body**

| 필드 | 타입 | 설명 |
|------|------|------|
| `content` | String | 유저가 보낸 메시지 텍스트 |

**처리 흐름 (ADR-006 참조)**
1. 턴을 pending 상태로 예약 (원자적 조건부 UPDATE로 동시 요청 방지)
2. LLM 호출 (Canon 발화, 되짚기 판정 신호 산출 등)
3. LLM 호출 성공 시 턴을 확정(commit)하고 응답 반환
4. LLM 호출 실패 시 pending을 롤백하고 `LLM_UNAVAILABLE`(503) 반환 — 이 경우 턴은 소비되지 않음

**에러 케이스**

| 상황 | 코드 | 상태 |
|------|------|------|
| `X-Player-Id` 누락 | `AUTH_MISSING_PLAYER_ID` | 401 |
| 오늘 턴 예산 소진 | `TURN_BUDGET_EXCEEDED` | 409 |
| 이미 처리 중인 턴 존재 (WS/REST 공유 락) | `TURN_IN_PROGRESS` | 409 |
| 낙관적락 충돌 | `CONCURRENT_MODIFICATION` | 409 |
| LLM 호출 실패/타임아웃 | `LLM_UNAVAILABLE` | 503 |
| 이미 종료된 하루에 대한 요청 | `DAY_CLOSED` | 409 |
| 요청 본문 검증 실패 (예: 빈 메시지) | `VALIDATION_ERROR` | 400 |

---

### 4. 하루 조기 종료

```
POST /api/relationships/{ucId}/end-day
```

유저가 오늘의 대화를 조기에 종료하고 다음 날로 넘어간다.

**Path Parameters**

| 이름 | 타입 | 설명 |
|------|------|------|
| `ucId` | Long | UserCharacter(관계) ID |

**Headers**
- `X-Player-Id` (required)

**Request Body**
없음

**에러 케이스**

| 상황 | 코드 | 상태 |
|------|------|------|
| `X-Player-Id` 누락 | `AUTH_MISSING_PLAYER_ID` | 401 |
| 이미 종료된 하루에 대한 재요청 | `DAY_CLOSED` | 409 |
| 처리 중인 턴이 있는 상태에서 종료 시도 | `TURN_IN_PROGRESS` | 409 |
| 낙관적락 충돌 | `CONCURRENT_MODIFICATION` | 409 |

---

## WebSocket (STOMP)

### 연결
STOMP 기반 WebSocket을 사용한다. 연결 시에도 `X-Player-Id`에 준하는 식별 정보가 필요하다 (구체적인 핸드셰이크 방식은 BACKEND 구현 시 확정).

### 메시지 전송

```
SEND /app/chat/{ucId}
```

실시간 대화 메시지를 전송한다. 처리 흐름과 턴 예산 규칙은 REST `/api/relationships/{ucId}/message`와 동일하며, **동일한 락을 공유**한다 (ADR-006 — WS/REST 어느 경로로 요청해도 pending/확정 상태가 일관됨).

**Payload**

| 필드 | 타입 | 설명 |
|------|------|------|
| `content` | String | 유저가 보낸 메시지 텍스트 |

### 구독

```
SUBSCRIBE /topic/chat/{ucId}
```

해당 관계(`ucId`)의 실시간 대화 프레임을 구독한다.

**수신 프레임 종류**
- 정상 메시지 프레임: AI 캐릭터의 응답, 되짚기 판정 결과 등
- **ErrorSignal 프레임**: 처리 중 에러가 발생한 경우 전달되는 에러 프레임. REST의 에러 응답 포맷(`{code, message}`)과 동일한 `code`/`message` 체계를 따르며, 위 "에러 코드 - HTTP 상태코드 매핑" 표의 코드 값을 그대로 사용한다 (WebSocket에는 HTTP 상태코드 개념이 없으므로 상태코드 필드는 생략되고 `code`로만 구분).

**재동기화**
WebSocket 연결이 끊기거나 재연결된 경우, 클라이언트는 `GET /api/relationships/{ucId}`를 호출하여 최신 상태로 재동기화해야 한다. WebSocket 프레임 자체는 상태의 단일 소스가 아니며, REST 조회 엔드포인트가 단일 소스다.

---

## 미확정 사항 (추후 갱신 필요)
- 각 엔드포인트의 정확한 성공 응답 JSON 스키마 (ADR-009, Phase 3b 완료 후 반영)
- WebSocket 연결 핸드셰이크에서 `X-Player-Id`를 전달하는 정확한 방식 (STOMP CONNECT 헤더 vs 쿼리 파라미터 등)
