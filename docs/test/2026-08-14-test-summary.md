# 테스트 현황 요약 (2026-08-14, `feat/frontend-chat-ui` 기준)

## 전체 현황
| 스택 | 테스트 수 | 결과 | 실행 명령 |
|---|---|---|---|
| 백엔드 (JUnit 5 / Spring Boot Test) | 99 / 99 | GREEN | `./gradlew test` |
| 프론트엔드 (Vitest) | 23 / 23 | GREEN | `npm test`(`vitest run`) — `frontend/` |

수치는 실제 실행 결과(`./gradlew test`의 JUnit XML 리포트 합산, `vitest run` 출력)로 확인했다.

## TDD 사이클 적용 여부
프로젝트 전 구간에서 **RED(실패하는 테스트 커밋) → GREEN(구현 커밋)** 사이클이 일관되게 적용됐다. 각 구현 라운드는 `test: add RED tests for ...` 커밋이 먼저 들어가 컴파일 실패 또는 assertion 실패 상태를 만들고, 뒤이은 `feat: implement ...` 커밋이 그 테스트들을 통과시키는 구조다. Phase 4 보안 수정(`cb91ac5`)도 동일 원칙을 지켜, 소유권 불일치 케이스에 대한 RED 테스트를 먼저 추가한 뒤 구현으로 GREEN 전환했다(`docs/security/2026-08-14-idor-ownership-check.md` 참조).

## 브랜치별 구현 순서와 테스트 수 변화

브랜치는 "작업영역 단위로 분리"하는 `docs/PROJECT_CONVENTIONS.md` 원칙에 따라 순차적으로 생성/작업되었다. 아래 테스트 수는 각 커밋 시점의 `@Test`(백엔드) / `it(...)`(프론트) 카운트다.

### 1. `feat/project-scaffold` — 스캐폴딩
| 커밋 | 유형 | 백엔드 테스트 수 |
|---|---|---|
| `31e8ac8` | RED — ErrorCode/ApiError/GlobalExceptionHandler/local profile context load | 10 |
| `7ad5b74` | GREEN — Spring Boot 3.5 스캐폴딩, profile 분리, 공통 예외 계층 구현 | (병렬 브랜치에서 구현 후 병합) |
| `9aad6b8` | docs — 초기 ADR, API 명세, 프로젝트 컨벤션 | — |
| `633e701` | merge — 스캐폴딩 구현을 RED 테스트 위에 병합 | 10 GREEN |

### 2. `feat/domain-entities` — 도메인 엔티티/레포지토리
| 커밋 | 유형 | 백엔드 테스트 수 |
|---|---|---|
| `ca993cd` | RED — 도메인 엔티티/레포지토리 | 32 (+22) |
| `60560de`, `3d4382b`, `8500afe` | GREEN — 구현 + Message 컬럼명/데이터 초기화 순서 수정 | 32 GREEN |

### 3. `feat/relationship-status-api` — 프레시니스 계산 + 관계 상태 API
| 커밋 | 유형 | 백엔드 테스트 수 |
|---|---|---|
| `a581297` | RED — freshness calculator, 관계 상태 API | 39 (+7) |
| `f22d342` | GREEN — 구현 | 39 GREEN |
| `9c1ec12` | fix — 관계 상태 API 변경으로 발생한 `PersonaFactSeedDataTest` 회귀 수정 | 39 GREEN |

### 4. `feat/turn-orchestration` — 턴 오케스트레이션 / LLM 파이프라인
| 커밋 | 유형 | 백엔드 테스트 수 |
|---|---|---|
| `e7b93b8` | RED — 턴 오케스트레이션, LLM 파이프라인, 콜백 스코어링, 하루 마감 | 78 (+39) |
| `bcc380a` | GREEN — 구현 | 78 GREEN |

### 5. `feat/websocket-channel` — WebSocket STOMP 채널
| 커밋 | 유형 | 백엔드 테스트 수 |
|---|---|---|
| `6787e11` | RED — WebSocket STOMP 채널, ErrorSignal 프레임 | 82 (+4) |
| `4015599` | GREEN — 구현 | 82 GREEN |

### 6. `feat/frontend-chat-ui` — 프론트 채팅 UI + 백엔드 API 확장 + 보안 수정
| 커밋 | 유형 | 백엔드 | 프론트 |
|---|---|---|---|
| `35293c8` | RED — 관계 표시 로직, Vite/Vitest 스캐폴딩 | 82 | 19 (+19, 신규) |
| `4619c6f` | RED — 플레이어 부트스트랩, 캐릭터 목록, 관계 시작 API | 90 (+8) | 19 |
| `bcae515` | GREEN — 위 API 구현 | 90 | 19 |
| `88fbf93` | GREEN — 프론트 채팅 UI, WebSocket 클라이언트, 관계 표시 로직 구현 | 90 | 19 |
| `cb91ac5` | fix — IDOR 소유권 검증(RED→GREEN 케이스 포함) + 입력검증 + 에러코드 통일 | 99 (+9) | 22 (+3) |
| `bc84d5d` | fix — 프론트 `VALIDATION_ERROR` 매핑 누락 수정 | 99 | 23 (+1) |

Phase 4 REVIEW/SECURITY에서 발견된 이슈 수정(`cb91ac5`, `bc84d5d`)도 신규 테스트 추가를 동반했다 — 백엔드 +9(소유권 불일치 케이스, 검증 실패 케이스, 낙관적락 충돌 케이스 등), 프론트 +4(에러 코드 매핑 케이스). 즉 보안 수정 역시 "테스트로 문제를 재현 → 구현으로 해소"하는 동일한 TDD 사이클을 따랐다.

## 테스트 유형 구성 (참고)
- **백엔드**: `@SpringBootTest` + `MockMvc` 기반 컨트롤러 통합 테스트, 서비스 단위 테스트, `@DataJpaTest` 수준 레포지토리 테스트, WebSocket(STOMP) 통합 테스트(`ChatWebSocketControllerTest`, `WebSocketConfigTest`), 전역 예외 핸들러 테스트(`GlobalExceptionHandlerTest`)로 구성.
- **프론트엔드**: `relationshipDisplay.test.ts` 단일 파일에 순수 로직(스테이지 계산, 프레시니스 표시, 에러 코드 → 사용자 메시지 매핑 등) 단위 테스트 23개. 컴포넌트/E2E 레벨 테스트는 아직 없음.

## 알려진 커버리지 갭 (DEFERRED)
`docs/security/2026-08-14-idor-ownership-check.md`의 "잔여 리스크" 항목 (2), (5)와 동일:
- 턴 제출 멱등성 키 부재에 대한 동시성/재시도 시나리오 테스트 없음.
- WS negative-path(헤더 누락 거부, 세션 만료 후 발행 등)의 end-to-end 커버리지가 REST 대비 얕음. 코드는 구현되어 있으나 폭넓은 자동 테스트는 다음 라운드 과제로 남김.
