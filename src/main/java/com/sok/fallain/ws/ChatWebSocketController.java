package com.sok.fallain.ws;

import com.sok.fallain.api.relationship.TurnOrchestrationService;
import com.sok.fallain.api.relationship.dto.TurnMessageRequest;
import com.sok.fallain.api.relationship.dto.TurnMessageResponse;
import com.sok.fallain.common.exception.BusinessException;
import com.sok.fallain.common.exception.ErrorCode;
import com.sok.fallain.config.StompAuthChannelInterceptor;
import com.sok.fallain.domain.player.Player;
import com.sok.fallain.domain.player.PlayerRepository;
import com.sok.fallain.ws.dto.ErrorSignal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * WebSocket/STOMP 대화 채널 컨트롤러 (T13).
 *
 * docs/api/API.md "WebSocket (STOMP)" 섹션 기준. "/app/chat/{ucId}"로 수신한 메시지를
 * 기존 {@link TurnOrchestrationService#sendMessage(Long, String, Player)}로 그대로 위임하고,
 * 결과를 "/topic/chat/{ucId}"로 브로드캐스트한다. 처리 중 {@link BusinessException}이
 * 발생하면 예외를 밖으로 던지지 않고 같은 토픽으로 {@link ErrorSignal} 프레임을 전송한다
 * (세션이 끊기지 않도록).
 *
 * <p>요청자(player)는 CONNECT 프레임에서 {@link StompAuthChannelInterceptor}가 검증해
 * 세션 attributes에 저장해 둔 playerId를 사용한다 (IDOR 방지 — REST의 X-Player-Id 헤더와
 * 동등한 취급). {@link TurnOrchestrationService}가 ucId 소유권을 검증하므로, 다른 플레이어의
 * ucId로 접근하면 RELATIONSHIP_NOT_FOUND ErrorSignal이 전송된다.</p>
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private static final String TOPIC_PREFIX = "/topic/chat/";

    private final TurnOrchestrationService turnOrchestrationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PlayerRepository playerRepository;

    @MessageMapping("/chat/{ucId}")
    public void sendMessage(@DestinationVariable Long ucId, @Valid TurnMessageRequest request,
                             SimpMessageHeaderAccessor headerAccessor) {
        String destination = TOPIC_PREFIX + ucId;
        try {
            Player player = resolvePlayer(headerAccessor);
            TurnMessageResponse response = turnOrchestrationService.sendMessage(ucId, request.content(), player);
            messagingTemplate.convertAndSend(destination, response);
        } catch (BusinessException e) {
            messagingTemplate.convertAndSend(destination,
                    new ErrorSignal(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
        }
    }

    /**
     * {@code @Valid} 검증 실패(예: 빈 메시지, 2000자 초과)를 원래 목적지의 ErrorSignal 프레임으로
     * 변환해 브로드캐스트한다. 세션은 끊기지 않는다.
     */
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidationException(MethodArgumentNotValidException e) {
        Message<?> failedMessage = e.getFailedMessage();
        String stompDestination = StompHeaderAccessor.wrap(failedMessage).getDestination();
        if (stompDestination == null) {
            return;
        }
        String ucId = stompDestination.substring(stompDestination.lastIndexOf('/') + 1);
        messagingTemplate.convertAndSend(TOPIC_PREFIX + ucId,
                new ErrorSignal(ErrorCode.VALIDATION_ERROR.getCode(), ErrorCode.VALIDATION_ERROR.getMessage()));
    }

    /**
     * CONNECT 시점에 {@link StompAuthChannelInterceptor}가 세션 attributes에 저장해 둔
     * playerId(UUID)로 Player를 조회한다 (없으면 upsert — {@code CurrentPlayerArgumentResolver}와
     * 동일한 정책, ADR-003).
     */
    private Player resolvePlayer(SimpMessageHeaderAccessor headerAccessor) {
        Object sessionPlayerId = headerAccessor.getSessionAttributes() != null
                ? headerAccessor.getSessionAttributes().get(StompAuthChannelInterceptor.SESSION_PLAYER_ID_ATTR)
                : null;

        if (!(sessionPlayerId instanceof UUID playerId)) {
            throw new BusinessException(ErrorCode.AUTH_MISSING_PLAYER_ID);
        }

        return playerRepository.findByPlayerId(playerId)
                .orElseGet(() -> playerRepository.save(Player.builder().playerId(playerId).build()));
    }
}
