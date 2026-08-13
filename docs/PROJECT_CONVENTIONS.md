# 프로젝트 컨벤션 — 경청이 사랑

## 개요
"경청이 사랑"은 AI 연인 서비스 프로토타입이다. 피그말리온(Pygmalion Studio)의 "프로젝트 디토"(30일 편지 로맨스)가 실사용자 리뷰에서 지적받은 발화일관성 붕괴 빈틈에 대한 의도적 응답이며, 핵심 훅은 감정 주체 이전이다 — AI가 흘리는 사실(Canon)을 유저가 능동적으로 기억하고 되짚어야 호감이 상승한다.

관련 설계 결정의 상세 근거는 `docs/adr/` 디렉토리를, API 명세는 `docs/api/API.md`를 참조한다.

---

## 패키지 구조

백엔드 루트 패키지는 `com.sok.fallain`이며, 하위 구조는 다음과 같다.

```
com.sok.fallain
├── config          # Spring 설정 클래스 (WebSocket, JPA, CORS 등)
├── common           # 공통 유틸/예외/응답 래퍼 등 도메인 무관 공용 코드
├── domain            # 도메인별 하위 패키지로 분리
│   ├── player         # 플레이어(익명 X-Player-Id 식별) 관련
│   ├── character       # AI 캐릭터 정의/Canon 데이터
│   ├── relationship    # 유저-캐릭터 관계(UC), intimacy/stage/turn 예산
│   └── conversation     # 대화 메시지, LLM 연동, 되짚기 판정
├── api               # REST 컨트롤러 계층
└── ws                # WebSocket(STOMP) 핸들러/컨트롤러 계층
```

**원칙**
- 각 `domain` 하위 패키지는 자신의 entity, repository, service를 포함한다 (도메인 응집).
- `api`/`ws`는 얇은 어댑터 계층으로 유지하고, 실제 비즈니스 로직은 `domain` 하위 서비스에 둔다.
- `common`은 특정 도메인에 속하지 않는 범용 코드(예외 클래스, 공통 응답 포맷, 유틸)만 포함한다. 도메인 로직을 `common`에 두지 않는다.
- 서비스 간 통신은 이벤트버스 없이 직접 호출로 처리한다 (ADR-007 참조) — 예: `RelationshipService`가 필요 시 `CharacterService`, `ConversationService`를 직접 호출.

---

## 브랜치 전략

- **이슈 번호 없음**: 이 프로젝트는 이슈 트래커 연동 없이 진행하므로 브랜치명에 이슈 번호를 포함하지 않는다.
- **브랜치명 형식**: `feat/[topic]` 형태를 사용한다. 예: `feat/relationship-start`, `feat/turn-budget`, `feat/ws-chat`.
- **작업영역별 브랜치 분리**: 하나의 브랜치가 여러 작업영역(예: 백엔드 도메인 로직 + 프론트엔드 UI)을 동시에 다루지 않는다. 작업영역(도메인/레이어) 단위로 브랜치를 분리한다.
  - 예: 관계 시작 API 백엔드 구현과 그 화면 프론트엔드 구현은 별도 브랜치로 분리 가능 (`feat/relationship-start-api`, `feat/relationship-start-ui` 등)
- 브랜치 생성은 Phase 2.5(PLANNER)에서 수행하며, 이후 Phase 3a/3b 구현 작업이 해당 브랜치 위에서 진행된다.

---

## 기술 스택

### 백엔드
| 항목 | 선택 | 비고 |
|------|------|------|
| 프레임워크 | Spring Boot 3.5.x | 4.1.0에서 다운핀 (ADR-004) |
| 언어 | Java 17 | |
| 빌드 도구 | Gradle | 리포지토리 기존 설정 유지 |
| 데이터베이스 | MySQL | `ddl-auto` 기반 스키마 관리, Flyway 등 마이그레이션 도구 미도입 (ADR-008) |
| 실시간 통신 | WebSocket (STOMP) | `SEND /app/chat/{ucId}`, `SUBSCRIBE /topic/chat/{ucId}` |
| LLM 연동 | Spring AI + Gemini | Canon 발화 생성, 되짚기 판정 신호(EXACT/PARTIAL/WRONG, FITTING/FORCED) 산출 (ADR-002) |
| 동시성 제어 | JPA 낙관적락(`@Version`) + 원자적 조건부 UPDATE | Kafka/Outbox/분산락/Redis 배제 (ADR-001) |
| 인증 | 익명 `X-Player-Id`(UUID) 헤더 | Spring Security/JWT 미도입 (ADR-003) |

### 프론트엔드
| 항목 | 선택 |
|------|------|
| 프레임워크 | React |
| 빌드 도구 | Vite |
| 언어 | TypeScript |

---

## 인증/인가

### 요청자 식별
모든 REST 엔드포인트는 요청자를 `@CurrentPlayer Player player` 파라미터(`CurrentPlayerArgumentResolver`, ADR-003)로 주입받는다. 컨트롤러가 직접 헤더를 파싱하거나 `PlayerRepository`를 호출하지 않는다. WebSocket(STOMP)은 `CONNECT` 프레임에서 `StompAuthChannelInterceptor`가 `X-Player-Id`를 검증해 세션 attributes에 바인딩하고, 이후 메시지 처리 시 이 세션 값을 사용한다 — REST와 별도의 신뢰 경로를 만들지 않는다.

### 소유권 검증 (필수)
특정 플레이어가 소유하는 리소스(예: 관계 UC)에 접근하는 **모든 신규 엔드포인트(REST/WS 불문)는 서비스 계층에서 반드시 소유권을 검증해야 한다.** 패턴과 근거는 `docs/adr/ADR-010-ownership-check-pattern.md`에 고정되어 있다 — 요약:

- 요청자는 `@CurrentPlayer`(REST) / 세션 바인딩 `playerId`(WS)로만 식별한다. 클라이언트가 보낸 다른 소유자 식별값은 신뢰하지 않는다.
- 리소스 조회 후 `resource.getPlayer().getId().equals(player.getId())`를 서비스 계층에서 검증한다(컨트롤러가 아님).
- **소유자 불일치와 리소스 미존재를 동일하게 취급**한다 — 둘 다 같은 404 에러코드(예: `RELATIONSHIP_NOT_FOUND`)로 응답한다. **403은 사용하지 않는다** — 존재 여부 자체가 유출되면 순차 PK를 통한 리소스 열거(enumeration) 공격이 가능해지기 때문 (ADR-010 참조).
- 새 엔드포인트를 추가할 때 이 검증을 빠뜨리는 것은 Phase 4 REVIEW/SECURITY에서 CRITICAL로 취급한다 (`docs/security/2026-08-14-idor-ownership-check.md`에 실제 발견 사례가 있다 — 참고할 것).

### 에러코드 컨벤션
`ErrorCode` enum의 `code` 필드는 **항상 enum 상수명 그대로**여야 한다 (예: `RELATIONSHIP_NOT_FOUND` enum의 `code`도 문자열 `"RELATIONSHIP_NOT_FOUND"`). `E001`, `E003` 같은 축약 코드나 enum명과 다른 임의의 문자열을 쓰지 않는다.

이 규칙은 과거 실제 버그 재발을 막기 위한 것이다 — 한때 일부 `ErrorCode`가 축약코드(`E001`/`E003`)를, 나머지는 enum명을 `code`로 썼는데, 프론트엔드 `ERROR_SIGNAL_MESSAGES`가 enum명을 키로 매핑하고 있어 축약코드 케이스에서 에러 메시지 매핑이 조용히 실패하는 실사용 버그로 이어졌다(수정: 커밋 `cb91ac5`, 상세: `docs/security/2026-08-14-idor-ownership-check.md`). 신규 `ErrorCode` 상수를 추가할 때는 반드시 `code` 인자에 상수명과 동일한 문자열을 넣는다.

---

## 기타 참고
- 도입하지 않은 인프라(Kafka, Outbox, 분산락, Redis, 인프로세스 이벤트버스, Flyway, 정식 인증 프레임워크)에 대한 근거는 각각 ADR-001, ADR-001, ADR-001, ADR-001, ADR-007, ADR-008, ADR-003에 있다. 이후 구현 중 "왜 이 익숙한 도구를 안 쓰지?"라는 의문이 들면 먼저 `docs/adr/`를 확인한다.
- API 성공 응답 공통 포맷은 아직 미확정 상태다 (ADR-009). Phase 3b 구현 중 확정되면 이 문서와 `docs/api/API.md`를 함께 갱신해야 한다.
- 리소스 소유권 검증 패턴은 ADR-010에 고정되어 있다 (위 "인증/인가" 섹션 참조).
