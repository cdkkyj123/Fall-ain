package com.sok.fallain.ws;

import com.sok.fallain.api.relationship.TurnOrchestrationService;
import com.sok.fallain.api.relationship.dto.TurnMessageRequest;
import com.sok.fallain.api.relationship.dto.TurnMessageResponse;
import com.sok.fallain.common.exception.BusinessException;
import com.sok.fallain.ws.dto.ErrorSignal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * WebSocket/STOMP 대화 채널 컨트롤러 (T13).
 *
 * docs/api/API.md "WebSocket (STOMP)" 섹션 기준. "/app/chat/{ucId}"로 수신한 메시지를
 * 기존 {@link TurnOrchestrationService#sendMessage(Long, String)}로 그대로 위임하고,
 * 결과를 "/topic/chat/{ucId}"로 브로드캐스트한다. 처리 중 {@link BusinessException}이
 * 발생하면 예외를 밖으로 던지지 않고 같은 토픽으로 {@link ErrorSignal} 프레임을 전송한다
 * (세션이 끊기지 않도록).
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private static final String TOPIC_PREFIX = "/topic/chat/";

    private final TurnOrchestrationService turnOrchestrationService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/{ucId}")
    public void sendMessage(@DestinationVariable Long ucId, TurnMessageRequest request) {
        String destination = TOPIC_PREFIX + ucId;
        try {
            TurnMessageResponse response = turnOrchestrationService.sendMessage(ucId, request.content());
            messagingTemplate.convertAndSend(destination, response);
        } catch (BusinessException e) {
            messagingTemplate.convertAndSend(destination,
                    new ErrorSignal(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
        }
    }
}
