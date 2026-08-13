package com.sok.fallain.config;

import com.sok.fallain.common.exception.BusinessException;
import com.sok.fallain.common.exception.ErrorCode;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * STOMP CONNECT 프레임의 X-Player-Id 헤더를 검증하는 채널 인터셉터 (IDOR 방지, WebSocket 편).
 *
 * REST의 {@link CurrentPlayerArgumentResolver}(ADR-003)와 동일한 정책을 WebSocket 연결에도
 * 적용한다: CONNECT 프레임에 X-Player-Id 헤더가 없거나 UUID 형식이 아니면 연결 자체를 거부한다
 * (BusinessException(AUTH_MISSING_PLAYER_ID)). 검증에 성공하면 playerId를 STOMP 세션
 * attributes에 저장해, 이후 이 세션으로 들어오는 모든 메시지({@link
 * com.sok.fallain.ws.ChatWebSocketController})가 별도 헤더 없이도 세션에 바인딩된 playerId로
 * 소유권 검증을 수행할 수 있게 한다.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** STOMP 세션 attributes에 저장되는 playerId(UUID) 키. */
    public static final String SESSION_PLAYER_ID_ATTR = "playerId";

    private static final String PLAYER_ID_HEADER = "X-Player-Id";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            UUID playerId = resolvePlayerId(accessor);
            if (accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put(SESSION_PLAYER_ID_ATTR, playerId);
            }
        }

        return message;
    }

    private UUID resolvePlayerId(StompHeaderAccessor accessor) {
        String headerValue = accessor.getFirstNativeHeader(PLAYER_ID_HEADER);
        if (headerValue == null || headerValue.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_MISSING_PLAYER_ID);
        }
        try {
            return UUID.fromString(headerValue);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_MISSING_PLAYER_ID);
        }
    }
}
