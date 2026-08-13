# 경청이 사랑 (Fall-ain)

AI 연인 서비스 프로토타입. **피그말리온(Pygmalion Studio) 채용 사전과제**로 제작했다.

## 왜 이 방향인가

피그말리온의 "프로젝트 디토"는 30일간 하루 한 통씩 편지가 오는 로맨스 시뮬레이션으로, AI가 유저를 기억한다는 컨셉을 내세운다. 다만 실제 리뷰에서는 발화 일관성이 자주 깨진다는 지적이 있었다 — AI가 어제 한 말을 오늘 스스로 뒤집는 식의 빈틈이다.

"경청이 사랑"은 이 지점에 의도적으로 반대 방향으로 응답한다. AI가 유저를 일방적으로 기억해주는 "사랑받는 판타지" 대신, **유저가 AI의 말(Canon)을 능동적으로 기억하고 되짚어야 호감이 오르는 구조**를 만들었다 — "사랑할 줄 아는 경험"이다.

핵심 메커닉은 다음과 같다.

1. **PersonaFact (Canon 원장)** — AI 캐릭터가 대화 중 흘리는 사실들을 서버가 원장으로 관리한다.
2. **화이트리스트 검증** — 유저가 그 사실을 되짚었는지, LLM이 지어낸 말이 아니라 실제 Canon에 있는 내용인지 서버가 검증한다.
3. **되짚기 판정 파이프라인** — 키워드 게이트 → LLM Judge → 결정론 점수표 순으로 판정해, intimacy(호감도) 상승분은 최종적으로 서버의 결정론 로직이 확정한다(LLM에 점수 자체를 맡기지 않는다 — `docs/adr/ADR-002-intimacy-server-deterministic-score.md`).
4. **하루 8턴 예산 + 2단계 커밋** — 대화는 하루 8턴으로 제한되고, "pending 예약 → LLM 호출(트랜잭션 밖) → 확정" 2단계로 커밋된다. LLM 호출이 실패해도 유저의 턴이 소모되지 않는다(`docs/adr/ADR-006-turn-budget-two-phase-commit.md`).

설계 결정의 상세 근거는 [`docs/adr/`](docs/adr/), API 명세는 [`docs/api/API.md`](docs/api/API.md), 개발 컨벤션은 [`docs/PROJECT_CONVENTIONS.md`](docs/PROJECT_CONVENTIONS.md)를 참조.

---

## 기술 스택

| 영역 | 스택 |
|---|---|
| 백엔드 | Spring Boot 3.5, Java 17, Gradle |
| 영속성 | JPA/Hibernate — 로컬 H2(인메모리), 프로덕션 MySQL |
| 실시간 통신 | WebSocket (STOMP) |
| LLM 연동 | Spring AI 기반 `LlmClient` 추상화 (현재 실제 LLM 미연동, placeholder — 아래 "주의사항" 참조) |
| 프론트엔드 | React + Vite + TypeScript + Tailwind CSS |
| 프론트 실시간 클라이언트 | `@stomp/stompjs` + `sockjs-client` |
| 테스트 | JUnit 5 / Spring Boot Test (백엔드), Vitest (프론트엔드) |

---

## 실행 방법

### 1. 백엔드 (Spring Boot, 포트 8080)

별도 설정 없이 `local` 프로파일로 즉시 실행된다 — 인메모리 H2를 쓰므로 DB 설치가 필요 없다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

- H2 콘솔: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:fallain`, 계정: `sa` / 비밀번호 없음)
- 프로덕션 프로파일(`prod`)은 MySQL을 사용한다 — 로컬 실행에는 필요 없다.

### 2. 프론트엔드 (Vite, 포트 5173)

```bash
cd frontend
npm install
npm run dev
```

Vite 개발 서버는 `/api`, `/ws` 요청을 `http://localhost:8080`으로 프록시한다(`frontend/vite.config.ts`) — 백엔드에 CORS 설정이 없어도 동일 오리진처럼 동작한다.

### 3. 브라우저 접속

백엔드(8080)와 프론트(5173)를 모두 띄운 뒤 브라우저에서 **`http://localhost:5173`** 접속.

### 주의사항 — LLM 미연동 상태

현재 `LlmClient`는 실제 LLM(Gemini) 연동 없이 `UnavailableLlmClient` placeholder로 동작한다(`src/main/java/com/sok/fallain/domain/conversation/UnavailableLlmClient.java`). 따라서 채팅에서 메시지를 보내면 다음과 같은 에러가 뜨는 것이 **정상 동작**이다.

> "잠시 연결이 불안정해요. 다시 시도해주세요." (`LLM_UNAVAILABLE`)

이 상태에서도 캐릭터 목록, 관계 시작, 채팅 화면 진입, 턴 예산 표시, 에러 배너, WebSocket 연결/구독 등 **UI/UX 흐름 전체는 확인 가능**하다 — 실제로 AI 응답이 오지 않을 뿐이다.

---

## 테스트 실행

```bash
# 백엔드 — 99개 테스트, GREEN
./gradlew test

# 프론트엔드 — 23개 테스트, GREEN
cd frontend
npx vitest run
```

전 구간 TDD(RED 테스트 커밋 → GREEN 구현 커밋)로 진행했다. 상세 내역은 [`docs/test/2026-08-14-test-summary.md`](docs/test/2026-08-14-test-summary.md) 참조.

---

## 프로젝트 구조

```
com.sok.fallain
├── config          # Spring 설정 (WebSocket, JPA, CORS 등)
├── common          # 도메인 무관 공용 코드 (예외, 공통 응답 포맷)
├── domain
│   ├── player          # 플레이어 (익명 X-Player-Id 식별)
│   ├── character        # AI 캐릭터 정의 / PersonaFact(Canon) 원장
│   ├── relationship      # 유저-캐릭터 관계(UC), intimacy/stage/턴 예산
│   └── conversation       # 대화 메시지, LlmClient 연동, 되짚기 판정
├── api               # REST 컨트롤러 계층 (얇은 어댑터)
└── ws                # WebSocket(STOMP) 핸들러/컨트롤러 계층

frontend/src
├── api          # REST client, WebSocket client, 타입 정의
├── components   # ChatWindow, IntimacyMeter, ErrorBanner 등
├── pages        # EntryPage, ChatPage, EndingPage
└── lib          # 에러 시그널 → 표시 메시지 매핑 등 순수 로직
```

---

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/adr/`](docs/adr/) | 아키텍처 설계 결정 기록 (ADR-001 ~ ADR-010) |
| [`docs/api/API.md`](docs/api/API.md) | REST/WebSocket API 명세 |
| [`docs/security/2026-08-14-idor-ownership-check.md`](docs/security/2026-08-14-idor-ownership-check.md) | Phase 4 REVIEW에서 발견/수정한 CRITICAL IDOR 인시던트 리포트 |
| [`docs/test/2026-08-14-test-summary.md`](docs/test/2026-08-14-test-summary.md) | 브랜치별 테스트 현황 및 TDD 사이클 요약 |
| [`docs/PROJECT_CONVENTIONS.md`](docs/PROJECT_CONVENTIONS.md) | 패키지 구조, 브랜치 전략 등 프로젝트 컨벤션 |
| [`CHANGELOG.md`](CHANGELOG.md) | 변경 이력 |

---

## 알려진 한계 / 다음 단계

- **LLM 미연동**: `LlmClient`는 현재 `UnavailableLlmClient` placeholder다. Gemini API 실연동이 다음 단계.
- **CORS 미설정**: 백엔드에 CORS 설정이 없다 — 로컬 개발은 Vite 프록시로 우회하고 있으나, 프론트를 별도 오리진으로 배포하려면 백엔드에 CORS 정책 추가가 필요하다.
- **성공 응답 포맷 미확정 상태로 시작**: 2xx 응답 공통 포맷은 BLUEPRINT 단계에서 미확정으로 남겨두고 BACKEND 구현 시점에 확정했다(`docs/adr/ADR-009-response-format-deferred.md`) — 문서 갱신 이력 참고.
- **인증**: 익명 `X-Player-Id` UUID 헤더 기반 경량 인증이다(`docs/adr/ADR-003-anonymous-player-id-auth.md`) — 실제 서비스라면 정식 인증/세션이 필요하다.
