package com.sok.fallain.ws;

import com.sok.fallain.api.relationship.dto.RelationshipStatusResponse;
import com.sok.fallain.api.relationship.dto.TurnMessageRequest;
import com.sok.fallain.api.relationship.dto.TurnMessageResponse;
import com.sok.fallain.common.exception.ErrorCode;
import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.CharacterRepository;
import com.sok.fallain.domain.conversation.LlmClient;
import com.sok.fallain.domain.conversation.LlmTurnResult;
import com.sok.fallain.domain.player.Player;
import com.sok.fallain.domain.player.PlayerRepository;
import com.sok.fallain.domain.relationship.DayState;
import com.sok.fallain.domain.relationship.RelationshipStatus;
import com.sok.fallain.domain.relationship.UserCharacter;
import com.sok.fallain.domain.relationship.UserCharacterRepository;
import com.sok.fallain.ws.dto.ErrorSignal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * WebSocket/STOMP 대화 채널 통합테스트 (T13).
 *
 * docs/api/API.md "WebSocket (STOMP)" 섹션 기준.
 * LlmClient는 실제 Gemini 연동 없이 @MockBean으로 대체한다 (기존 컨트롤러 테스트와 동일 패턴).
 *
 * 계약:
 *  - com.sok.fallain.config.WebSocketConfig: STOMP 엔드포인트 "/ws"(SockJS 지원),
 *    메시지 브로커 prefix "/topic", 애플리케이션 destination prefix "/app".
 *  - com.sok.fallain.ws.ChatWebSocketController:
 *    {@code @MessageMapping("/chat/{ucId}")}로 TurnMessageRequest를 수신, 내부적으로 기존
 *    TurnOrchestrationService.sendMessage(ucId, content)를 그대로 재사용한다. 성공 시
 *    SimpMessagingTemplate으로 "/topic/chat/{ucId}"에 TurnMessageResponse를 브로드캐스트한다.
 *  - com.sok.fallain.ws.dto.ErrorSignal(String code, String message): TurnOrchestrationService
 *    호출 중 BusinessException이 발생하면 이를 잡아 예외를 밖으로 던지지 않고 같은
 *    "/topic/chat/{ucId}"로 ErrorSignal 프레임(REST 에러 응답과 동일한 code/message 체계,
 *    docs/api/API.md 에러 코드 표 기준)을 브로드캐스트한다.
 *  - 재동기화 계약: WS는 상태를 갖지 않으며, 기존 GET /api/relationships/{ucId}
 *    (RelationshipStatusController)가 단일 진실원천이다 — WS 프레임으로 받은 turnsUsedToday/
 *    dayState는 언제든 REST 조회로 동일하게 재확인할 수 있어야 한다.
 *
 * 현재 WebSocketConfig/ChatWebSocketController/ErrorSignal이 존재하지 않으므로
 * 컴파일 실패(RED)가 정상이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketControllerTest {

    private static final long STOMP_TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private UserCharacterRepository userCharacterRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private LlmClient llmClient;

    private Player persistPlayer() {
        return playerRepository.saveAndFlush(
                Player.builder().playerId(UUID.randomUUID()).nickname("플레이어").build()
        );
    }

    private Character persistCharacter() {
        return characterRepository.saveAndFlush(
                Character.builder().name("소울").persona("따뜻하지만 상처를 숨기는 성격").arcLengthDays(30).build()
        );
    }

    private UserCharacter persistUserCharacter(Character character, int currentDay, int turnsUsedToday,
                                                boolean pendingTurn, DayState dayState, int intimacy) {
        return userCharacterRepository.saveAndFlush(
                UserCharacter.builder()
                        .player(persistPlayer())
                        .character(character)
                        .intimacy(intimacy)
                        .currentDay(currentDay)
                        .turnsUsedToday(turnsUsedToday)
                        .pendingTurn(pendingTurn)
                        .dayState(dayState)
                        .lastTouchedDay(currentDay)
                        .status(RelationshipStatus.ONGOING)
                        .build()
        );
    }

    private StompSession connect() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        // WebSocketConfig가 "/ws"를 withSockJS()로 등록하면, SockJS가 노출하는 raw WebSocket
        // 하위 경로("/ws/websocket")로 표준 WebSocket 클라이언트가 직접 접속할 수 있다.
        return stompClient
                .connectAsync("ws://localhost:" + port + "/ws/websocket", new StompSessionHandlerAdapter() {
                })
                .get(STOMP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void 정상_메시지_전송시_구독채널에서_TurnMessageResponse_프레임을_받는다() throws Exception {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 1, 0, false, DayState.IN_PROGRESS, 0);

        given(llmClient.generateTurn(any())).willReturn(
                new LlmTurnResult("반가워!", List.of(), null)
        );

        StompSession session = connect();
        BlockingQueue<TurnMessageResponse> frames = new LinkedBlockingQueue<>();
        session.subscribe("/topic/chat/" + uc.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TurnMessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add((TurnMessageResponse) payload);
            }
        });

        session.send("/app/chat/" + uc.getId(), new TurnMessageRequest("안녕!"));

        TurnMessageResponse response = frames.poll(STOMP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.ucId()).isEqualTo(uc.getId());
        assertThat(response.replyText()).isEqualTo("반가워!");
        assertThat(response.turnsUsedToday()).isEqualTo(1);
        assertThat(response.dayState()).isEqualTo("IN_PROGRESS");

        session.disconnect();
    }

    @Test
    void 턴예산_초과_상태에서_메시지_전송시_구독채널에서_ErrorSignal_프레임을_받는다() throws Exception {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 1, 8, false, DayState.IN_PROGRESS, 0);

        StompSession session = connect();
        BlockingQueue<ErrorSignal> frames = new LinkedBlockingQueue<>();
        session.subscribe("/topic/chat/" + uc.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ErrorSignal.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add((ErrorSignal) payload);
            }
        });

        // 예외가 그대로 터지지 않고 ErrorSignal 프레임으로 변환되어야 한다 — 세션은 끊기지 않는다.
        session.send("/app/chat/" + uc.getId(), new TurnMessageRequest("한번더"));

        ErrorSignal errorSignal = frames.poll(STOMP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(errorSignal).isNotNull();
        assertThat(errorSignal.code()).isEqualTo(ErrorCode.TURN_BUDGET_EXCEEDED.getCode());
        assertThat(errorSignal.message()).isEqualTo(ErrorCode.TURN_BUDGET_EXCEEDED.getMessage());
        assertThat(session.isConnected()).isTrue();

        session.disconnect();
    }

    @Test
    void WS로_받은_턴상태는_REST_상태조회_결과와_일치한다() throws Exception {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 1, 0, false, DayState.IN_PROGRESS, 0);

        given(llmClient.generateTurn(any())).willReturn(
                new LlmTurnResult("반가워!", List.of(), null)
        );

        StompSession session = connect();
        BlockingQueue<TurnMessageResponse> frames = new LinkedBlockingQueue<>();
        session.subscribe("/topic/chat/" + uc.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TurnMessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add((TurnMessageResponse) payload);
            }
        });

        session.send("/app/chat/" + uc.getId(), new TurnMessageRequest("안녕!"));
        TurnMessageResponse wsResponse = frames.poll(STOMP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(wsResponse).isNotNull();

        // WS는 상태를 갖지 않는다 — GET /api/relationships/{ucId}가 단일 진실원천이며,
        // WS 프레임으로 받은 턴 상태와 항상 동일해야 한다.
        ResponseEntity<RelationshipStatusResponse> restResponse = restTemplate.getForEntity(
                "/api/relationships/{ucId}", RelationshipStatusResponse.class, uc.getId());

        assertThat(restResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restResponse.getBody()).isNotNull();
        assertThat(restResponse.getBody().turnsUsedToday()).isEqualTo(wsResponse.turnsUsedToday());
        assertThat(restResponse.getBody().dayState()).isEqualTo(wsResponse.dayState());

        session.disconnect();
    }
}
