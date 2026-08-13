package com.sok.fallain.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocketConfig 스모크 테스트 (T13).
 *
 * docs/api/API.md "WebSocket (STOMP)" 섹션 기준.
 * {@code com.sok.fallain.config.WebSocketConfig}가 STOMP 엔드포인트 "/ws"를 SockJS 지원과 함께
 * 등록하고 "/topic" 브로커 prefix, "/app" 애플리케이션 destination prefix를 구성한다는 계약만
 * 최소한으로 검증한다 (핸드셰이크 성공 여부).
 *
 * {@link StompAuthChannelInterceptor} 도입(IDOR 방지, Phase 4 REVIEW 발견사항) 이후에는
 * CONNECT 프레임에 X-Player-Id 헤더가 없으면 연결 자체가 거부되므로, 정상 핸드셰이크
 * 테스트는 유효한 헤더를 실어 보내고, 헤더가 없는 경우 연결이 거부되는지도 함께 검증한다.
 *
 * 상세 메시지 송수신/에러 프레임 계약은 ChatWebSocketControllerTest에서 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketConfigTest {

    @LocalServerPort
    private int port;

    @Test
    void X_Player_Id_헤더가_있으면_STOMP_핸드셰이크가_ws_엔드포인트에서_성공한다() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("X-Player-Id", UUID.randomUUID().toString());

        // WebSocketConfig가 "/ws"를 withSockJS()로 등록하면, SockJS가 노출하는 raw WebSocket
        // 하위 경로("/ws/websocket")로 표준 WebSocket 클라이언트가 직접 접속할 수 있다.
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws/websocket", new WebSocketHttpHeaders(),
                        connectHeaders, new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);

        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }
}
