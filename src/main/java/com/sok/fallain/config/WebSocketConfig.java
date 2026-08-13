package com.sok.fallain.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 기반 WebSocket 대화 채널 설정 (T13).
 *
 * docs/api/API.md "WebSocket (STOMP)" 섹션 기준.
 * "/ws" 엔드포인트를 SockJS 지원과 함께 등록하고, 메시지 브로커 prefix "/topic",
 * 애플리케이션 destination prefix "/app"을 구성한다.
 *
 * {@link StompAuthChannelInterceptor}를 인바운드 채널에 등록해 CONNECT 프레임의
 * X-Player-Id 헤더를 검증한다 (IDOR 방지).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
