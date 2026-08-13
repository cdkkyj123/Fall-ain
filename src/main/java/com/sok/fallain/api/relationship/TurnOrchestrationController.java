package com.sok.fallain.api.relationship;

import com.sok.fallain.api.relationship.dto.EndDayResponse;
import com.sok.fallain.api.relationship.dto.TurnMessageRequest;
import com.sok.fallain.api.relationship.dto.TurnMessageResponse;
import com.sok.fallain.config.CurrentPlayer;
import com.sok.fallain.domain.player.Player;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대화 턴 전송(REST 폴백) / 하루 조기종료 API.
 * 응답 형식: 래퍼 없이 리소스 그대로 반환 (ADR-009).
 */
@RestController
@RequestMapping("/api/relationships")
@RequiredArgsConstructor
public class TurnOrchestrationController {

    private final TurnOrchestrationService turnOrchestrationService;

    /**
     * 대화 메시지를 전송한다 (ADR-006 2단계 커밋). 요청자(player)가 ucId의 소유자가 아니면
     * 존재 자체를 숨기기 위해 RELATIONSHIP_NOT_FOUND(404)로 응답한다 (IDOR 방지).
     *
     * @throws com.sok.fallain.common.exception.BusinessException RELATIONSHIP_NOT_FOUND(404) /
     *      TURN_IN_PROGRESS(409) / TURN_BUDGET_EXCEEDED(409) / DAY_CLOSED(409) /
     *      ARC_ENDED(409) / LLM_UNAVAILABLE(503)
     */
    @PostMapping("/{ucId}/message")
    public ResponseEntity<TurnMessageResponse> sendMessage(@PathVariable Long ucId,
                                                             @CurrentPlayer Player player,
                                                             @Valid @RequestBody TurnMessageRequest request) {
        TurnMessageResponse response = turnOrchestrationService.sendMessage(ucId, request.content(), player);
        return ResponseEntity.ok(response);
    }

    /**
     * 오늘의 대화를 조기 종료한다. 요청자(player)가 ucId의 소유자가 아니면 존재 자체를 숨기기
     * 위해 RELATIONSHIP_NOT_FOUND(404)로 응답한다 (IDOR 방지).
     *
     * @throws com.sok.fallain.common.exception.BusinessException RELATIONSHIP_NOT_FOUND(404) /
     *      DAY_CLOSED(409) / TURN_IN_PROGRESS(409)
     */
    @PostMapping("/{ucId}/end-day")
    public ResponseEntity<EndDayResponse> endDay(@PathVariable Long ucId, @CurrentPlayer Player player) {
        EndDayResponse response = turnOrchestrationService.endDay(ucId, player);
        return ResponseEntity.ok(response);
    }
}
