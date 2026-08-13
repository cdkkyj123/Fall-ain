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

## 기타 참고
- 도입하지 않은 인프라(Kafka, Outbox, 분산락, Redis, 인프로세스 이벤트버스, Flyway, 정식 인증 프레임워크)에 대한 근거는 각각 ADR-001, ADR-001, ADR-001, ADR-001, ADR-007, ADR-008, ADR-003에 있다. 이후 구현 중 "왜 이 익숙한 도구를 안 쓰지?"라는 의문이 들면 먼저 `docs/adr/`를 확인한다.
- API 성공 응답 공통 포맷은 아직 미확정 상태다 (ADR-009). Phase 3b 구현 중 확정되면 이 문서와 `docs/api/API.md`를 함께 갱신해야 한다.
