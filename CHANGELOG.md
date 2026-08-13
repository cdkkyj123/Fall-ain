# Changelog

이 프로젝트의 주요 변경 사항을 기록한다. 형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.0.0/)를 따른다.

## [0.1.0] - 2026-08-14

"경청이 사랑" 프로토타입 — Spring Boot 3.5 백엔드 + React/Vite 프론트엔드, HARNESS V4.4 파이프라인(Phase 0~5)으로 TDD 전 구간(RED→GREEN) 진행. 백엔드 99/99, 프론트엔드 23/23 테스트 GREEN.

### Added

- **프로젝트 스캐폴딩** (`feat/project-scaffold`): Spring Boot 3.5 + Java 17 초기 설정, local/prod 프로파일 분리, 공통 예외 계층(`ErrorCode`/`ApiError`/`GlobalExceptionHandler`), 초기 ADR·API 명세·프로젝트 컨벤션 문서화.
- **도메인 엔티티/레포지토리** (`feat/domain-entities`): `Player`, `Character`, `PersonaFact`(Canon 원장), `UserCharacter`(관계), `Message`, `MemoryCandidate` 등 핵심 도메인 모델과 JPA 레포지토리.
- **관계 상태 API** (`feat/relationship-status-api`): 기억 신선도(freshness) 조회 시점 파생 계산기, `GET /api/relationships/{ucId}` 관계 상태 조회 API.
- **턴 오케스트레이션 / LLM 파이프라인** (`feat/turn-orchestration`): 하루 8턴 예산 관리, pending 예약 → LLM 호출(트랜잭션 밖) → 확정 2단계 커밋, 되짚기 판정(키워드 게이트 → LLM Judge → 결정론 점수표), day-close 처리, `LlmClient` 추상화(현재 `UnavailableLlmClient` placeholder).
- **WebSocket STOMP 채팅 채널** (`feat/websocket-channel`): `/app/chat/{ucId}` 실시간 메시지 송수신, `ErrorSignal` 프레임을 통한 클라이언트 에러 통지.
- **프론트엔드 채팅 UI** (`feat/frontend-chat-ui`): React + Vite + TypeScript + Tailwind 기반 EntryPage/ChatPage/EndingPage, WebSocket(STOMP) 클라이언트, `ChatWindow`/`IntimacyMeter`/`TurnBudgetBar`/`LingeringHints`/`ErrorBanner` 등 컴포넌트, 에러 시그널 → 표시 메시지 매핑 로직.
- **플레이어 부트스트랩 / 캐릭터 목록 / 관계 시작 API**: 익명 `X-Player-Id`(UUID) 기반 플레이어 식별(ADR-003), 캐릭터 목록 조회, `POST /api/characters/{id}/start` 관계 시작.
- **CI**: GitHub Actions 워크플로 추가 — 백엔드(`./gradlew test`)와 프론트엔드(`vitest run`) 테스트를 push/PR 시 병렬로 자동 실행.
- **README / CHANGELOG**: 실행 방법(백엔드/프론트엔드 로컬 구동, LLM placeholder 상태 안내), 기술 스택, 프로젝트 구조, 문서 링크를 포함한 README 전면 재작성.

### Fixed

- 프론트엔드 에러 메시지 매핑에서 누락된 `VALIDATION_ERROR` 케이스 추가.
- 관계 상태 API 변경으로 발생한 `PersonaFactSeedDataTest` 회귀(테스트 컨텍스트 간 H2 인메모리 DB 이름 충돌로 인한 데이터 오염) 수정 — local/default 프로파일의 H2 인스턴스 이름을 분리.
- `Message` 엔티티 컬럼명 및 데이터 초기화(`data.sql`) 순서 조정.

### Security

- **[CRITICAL] IDOR (CWE-639) 발견 및 수정** — Phase 4 REVIEW + SECURITY 병렬 검토 중, 관계(UserCharacter) 리소스에 접근하는 REST 3개 엔드포인트(`GET/POST /api/relationships/{ucId}`, `POST /api/relationships/{ucId}/message`, `POST /api/relationships/{ucId}/end-day`)와 WebSocket 1개 채널(`/app/chat/{ucId}`)이 `ucId`만으로 리소스를 조회하고 요청자가 실제 소유자인지 전혀 검증하지 않는 것을 발견했다. 클라이언트가 임의로 설정 가능한 `X-Player-Id` 헤더 특성상(ADR-003), 공격자가 자신의 헤더를 유지한 채 타인의 `ucId`를 순회해 관계 상태 열람·메시지 주입·하루 강제 종료가 가능했고, WebSocket은 CONNECT 프레임 자체에 인증 검증조차 없었다.
  - 수정: 서비스 계층에 `verifyOwnership(uc, player)` 검증을 도입하고, 컨트롤러에서 `@CurrentPlayer`로 주입된 요청자를 서비스에 전달하도록 변경(커밋 `cb91ac5`). 존재하지 않는 `ucId`와 소유자가 다른 `ucId`는 동일하게 `404 RELATIONSHIP_NOT_FOUND`로 응답해 리소스 존재 여부 자체가 노출(enumeration)되지 않도록 했다(`docs/adr/ADR-010-ownership-check-pattern.md`).
  - RED 테스트 선행 후 구현으로 GREEN 전환(TDD 원칙 준수), 재검증 완료.
  - 상세: [`docs/security/2026-08-14-idor-ownership-check.md`](docs/security/2026-08-14-idor-ownership-check.md).
