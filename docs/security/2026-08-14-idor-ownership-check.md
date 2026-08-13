# 보안 인시던트: IDOR — 관계(UC) 리소스 소유권 검증 부재

## 요약
| 항목 | 내용 |
|------|------|
| 발견일 | 2026-08-14 (Phase 4 REVIEW + SECURITY 병렬 검토) |
| 심각도 | CRITICAL |
| 유형 | IDOR (Insecure Direct Object Reference, CWE-639) |
| 영향받은 엔드포인트 | 4개 (REST 3 + WS 1) |
| 상태 | RESOLVED — 수정 커밋 `cb91ac5`, 재검증 완료 (코드 확인 + 실제 테스트 실행) |

## 발견 경위
`feat/frontend-chat-ui` 브랜치 Phase 4에서 REVIEW·SECURITY 에이전트가 병렬로 관계(UserCharacter, 이하 UC) 관련 REST/WS 엔드포인트를 감사하는 과정에서, 모든 UC 리소스 접근 경로가 `ucId` PathVariable만으로 리소스를 조회하고 요청자가 그 UC의 소유자인지 전혀 확인하지 않는 것을 발견했다. 이 프로젝트는 ADR-003에 따라 `X-Player-Id` 헤더 기반의 경량 식별 방식을 쓰는데, 헤더는 클라이언트가 임의로 설정 가능하므로 "내 X-Player-Id로 남의 ucId를 조회"하는 것을 막을 장치가 반드시 서버 쪽에 있어야 했으나 누락되어 있었다.

## 취약점 상세

### 공격 시나리오
1. 공격자는 자신의 `X-Player-Id`(UUID)로 정상적으로 관계를 하나 시작해 앱을 사용한다.
2. `ucId`는 auto-increment PK이므로 순차적으로 예측 가능하다. 공격자는 `ucId=1, 2, 3...` 을 순회하며 **자신의** `X-Player-Id` 헤더를 유지한 채 다른 사람의 `ucId`에 대해 요청을 보낸다.
3. 서버는 `ucId`로 UC 엔티티를 조회할 뿐 그 UC의 `player`가 요청자와 같은지 검증하지 않으므로, 다음이 전부 가능했다:
   - 타인의 관계 상태(호감도 intimacy, stage, 오늘 사용한 턴 수, memoryCandidates 등 사적인 대화 진행 정보) 열람
   - 타인의 관계에 임의의 메시지를 주입해 LLM 응답을 받아 타인의 턴 예산을 소모시키고, 대화 흐름을 조작
   - 타인의 하루 대화를 강제로 조기 종료
   - WebSocket으로도 동일 — `/app/chat/{ucId}`를 구독/발행할 때 `ucId` 소유권을 검증하지 않으므로 REST와 동일한 조작이 실시간 채널에서도 가능했고, 게다가 WS는 CONNECT 프레임 자체에 `X-Player-Id` 검증이 전혀 없어 인증되지 않은 클라이언트도 연결이 가능했다.

### 영향받은 엔드포인트
| 메서드/경로 | 컨트롤러 | 문제 |
|---|---|---|
| `GET /api/relationships/{ucId}` | `RelationshipStatusController` | 소유권 검증 없이 임의 UC 상태 열람 가능 |
| `POST /api/relationships/{ucId}/message` | `TurnOrchestrationController` | 소유권 검증 없이 임의 UC에 메시지 주입 가능 (턴 예산 소모, 대화 오염) |
| `POST /api/relationships/{ucId}/end-day` | `TurnOrchestrationController` | 소유권 검증 없이 임의 UC의 하루를 강제 종료 가능 |
| `SEND /app/chat/{ucId}` (WS STOMP) | `ChatWebSocketController` | 위 message와 동일 + CONNECT 프레임 자체에 `X-Player-Id` 검증 부재 |

## 수정 내용 (커밋 `cb91ac5`)

### REST — 소유권 검증
- `RelationshipStatusService.getRelationshipStatus(ucId, player)` / `TurnOrchestrationService.sendMessage(ucId, content, player)` / `TurnOrchestrationService.endDay(ucId, player)`에 `Player` 파라미터를 추가하고, UC를 조회한 뒤 `uc.getPlayer().getId().equals(player.getId())`를 검증하는 `verifyOwnership(...)`를 도입했다.
- 컨트롤러는 `@CurrentPlayer Player player`(기존 ADR-003 리졸버, `CurrentPlayerArgumentResolver`)로 요청자를 주입받아 서비스에 그대로 전달한다.
- **존재하지 않는 ucId와 소유자가 다른 ucId를 동일하게 취급**한다 — 둘 다 `404 RELATIONSHIP_NOT_FOUND`. 403을 쓰지 않은 이유와 근거는 `docs/adr/ADR-010-ownership-check-pattern.md` 참조 (enumeration 방지).

### WebSocket — 인증 + 소유권 검증
- 신규 `StompAuthChannelInterceptor`(`ChannelInterceptor`)를 인바운드 채널에 등록(`WebSocketConfig#configureClientInboundChannel`)해, STOMP `CONNECT` 프레임의 `X-Player-Id` 네이티브 헤더를 검증한다. 헤더가 없거나 UUID 형식이 아니면 `BusinessException(AUTH_MISSING_PLAYER_ID)`로 연결 자체를 거부하고, 성공 시 `playerId`를 세션 attributes(`SESSION_PLAYER_ID_ATTR`)에 저장한다.
- `ChatWebSocketController.sendMessage`는 더 이상 요청자를 신뢰하지 않고, CONNECT 시점에 세션에 바인딩된 `playerId`로 `Player`를 조회/upsert한 뒤 `TurnOrchestrationService.sendMessage(ucId, content, player)`를 호출한다 — REST와 동일한 소유권 검증 경로를 그대로 탄다.
- 소유권 불일치 시 예외를 세션 밖으로 던지지 않고 `ErrorSignal(RELATIONSHIP_NOT_FOUND)` 프레임을 해당 토픽으로 브로드캐스트한다(세션 유지).

### 부수적으로 함께 수정된 항목 (같은 커밋)
REVIEW 과정에서 함께 발견되어 같은 커밋에 묶여 수정됨 — 별도 문서화:
- **HIGH — 입력 검증 부재**: `TurnMessageRequest.content`에 `@NotBlank` + `@Size(max=2000)` 추가, REST(`@Valid @RequestBody`)와 WS(`@Valid` + `@MessageExceptionHandler(MethodArgumentNotValidException.class)`) 양쪽에 적용. `GlobalExceptionHandler`에 `MethodArgumentNotValidException → 400 VALIDATION_ERROR`, `ObjectOptimisticLockingFailureException → 409 CONCURRENT_MODIFICATION` 매핑 추가.
- **HIGH — 실버그: ErrorCode.code 불일치**: `ErrorCode` enum의 내부 `code` 필드가 일부는 축약코드(`E001`, `E002`, `E003`, `E500`), 일부는 enum명 그대로였다. 프론트엔드 `ERROR_SIGNAL_MESSAGES`는 enum명(`RELATIONSHIP_NOT_FOUND` 등)을 키로 쓰므로, 축약코드를 반환하는 케이스(`ENTITY_NOT_FOUND`, `INVALID_INPUT_VALUE`, `RELATIONSHIP_NOT_FOUND`, `INTERNAL_SERVER_ERROR`)에서 프론트가 에러 메시지 매핑에 실패해 사용자에게 원인 불명의 상태만 노출되는 실사용 버그였다. 전체를 enum명 그대로 통일했다. 후속 커밋 `bc84d5d`에서 프론트 쪽 `VALIDATION_ERROR` 매핑 누락도 추가 수정.

## 검증 방법 (재현 테스트)

수정 전/후 모두 코드 직접 확인 + 실제 테스트 실행으로 검증했다 (풍문/추정 아님).

- REST 재현 테스트: `RelationshipStatusControllerTest.다른_플레이어의_ucId를_조회하면_404와_RELATIONSHIP_NOT_FOUND를_반환한다()`, `TurnOrchestrationControllerTest`의 동등 케이스 — 소유자가 아닌 `X-Player-Id`로 각 엔드포인트를 호출하면 `404 + {"code":"RELATIONSHIP_NOT_FOUND"}`를 기대하는 테스트를 RED로 먼저 추가한 뒤(수정 전 코드에서는 200/성공으로 실패), `cb91ac5` 구현으로 GREEN 전환을 확인했다.
- WS 재현 테스트: `ChatWebSocketControllerTest`, `WebSocketConfigTest`에 CONNECT 시 `X-Player-Id` 누락 시 연결 거부, 소유자가 아닌 세션으로 메시지를 보내면 `ErrorSignal(RELATIONSHIP_NOT_FOUND)`가 브로드캐스트되는지 확인하는 케이스 추가.
- 전체 회귀: 백엔드 99/99, 프론트엔드 23/23 전부 GREEN (`docs/test/2026-08-14-test-summary.md` 참조). 기존 컨트롤러/서비스/WS 테스트들도 "실제 소유자" `X-Player-Id`를 보내도록 함께 갱신됨 — 즉 회귀 테스트가 우연히 소유권 검증을 우회하지 않도록 픽스처를 정합성 있게 맞췄다.

## 잔여 리스크 (DEFERRED — 이번 라운드 범위 밖, 문서화만)

이번 수정으로 IDOR 자체는 해소되었으나, REVIEW/SECURITY 과정에서 함께 식별된 아래 항목들은 의도적으로 이번 라운드에 포함하지 않았다. 별도 라운드에서 재검토 필요.

1. **턴 롤백 시 USER 메시지 미삭제로 인한 turnIndex 중복 가능성**
   `TurnTransactionSupport.reserveTurn`(TX1)은 LLM 호출 전에 USER 메시지를 `turnIndex = turnsUsedToday * 2`로 먼저 저장한다. LLM 호출이 재시도까지 실패하면 `rollbackPendingTurn`이 `pendingTurn` 플래그만 되돌리고 `turnsUsedToday`는 그대로 두는데, 이미 저장된 USER 메시지 행은 삭제하지 않는다. 사용자가 재시도하면 동일한 `turnIndexToday`로 새 USER 메시지가 다시 저장되어 같은 `turnIndex` 값을 가진 메시지가 DB에 중복될 수 있다. 현재는 `PromptBuilder`가 메시지 이력을 조회하지 않는 구조라 즉각적인 기능 영향은 없지만, 향후 메시지 이력을 프롬프트에 포함시키는 시점에는 순서/중복 문제로 이어질 수 있다.
2. **턴 제출 멱등성 키 부재**
   REST/WS 모두 턴 제출에 멱등성 키(idempotency key)가 없다. WS 응답이 네트워크 문제로 유실된 상태에서 클라이언트가 수동으로 재시도하면, 서버 입장에서는 서로 다른 두 번의 정상 요청으로 처리되어 턴이 이중으로 차감되거나 메시지가 중복 처리될 가능성이 있다.
3. **Gemini 실제 LLM 미연동**
   `LlmClient` 구현체가 `UnavailableLlmClient` placeholder로, 실제 Gemini 연동 전까지는 모든 턴이 `LLM_UNAVAILABLE` 폴백 경로로만 동작한다. 보안 이슈는 아니지만 (1)(2) 항목의 실사용 영향도를 가늠할 때 함께 고려해야 한다.
4. **CORS 미설정**
   프로덕션 배포 시 필요한 CORS 정책이 아직 구성되지 않았다. 현재는 dev proxy(Vite)로 우회 중이라 로컬 개발에는 문제가 없으나, 별도 오리진에 배포 시 명시적으로 설정해야 한다.
5. **WS negative-path 자동 테스트 커버리지 갭**
   이번 라운드에서 WS 쪽 코드(헤더 누락 시 CONNECT 거부, `@Valid` 검증)는 구현되고 일부 테스트도 추가됐지만, REST만큼 폭넓은 negative-path end-to-end 테스트(예: 다양한 잘못된 헤더 포맷, 세션 만료 후 메시지 전송 등)는 아직 REST 대비 커버리지가 얕다. `docs/test/2026-08-14-test-summary.md`의 테스트 현황 참조.

## 관련 문서
- `docs/adr/ADR-010-ownership-check-pattern.md` — 소유권 검증 패턴을 아키텍처 결정으로 고정
- `docs/adr/ADR-003-anonymous-player-id-auth.md` — `X-Player-Id` 기반 식별 방식의 배경과 한계
- `docs/PROJECT_CONVENTIONS.md` "인증/인가" 섹션 — 신규 엔드포인트에 대한 적용 규칙
- `docs/test/2026-08-14-test-summary.md` — 전체 테스트 현황
