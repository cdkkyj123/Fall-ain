package com.sok.fallain.api.relationship;

import com.sok.fallain.api.relationship.dto.EndDayResponse;
import com.sok.fallain.api.relationship.dto.TurnMessageRequest;
import com.sok.fallain.api.relationship.dto.TurnMessageResponse;
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
     * 대화 메시지를 전송한다 (ADR-006 2단계 커밋).
     *
     * @throws com.sok.fallain.common.exception.BusinessException TURN_IN_PROGRESS(409) /
     *      TURN_BUDGET_EXCEEDED(409) / DAY_CLOSED(409) / ARC_ENDED(409) / LLM_UNAVAILABLE(503)
     */
    @PostMapping("/{ucId}/message")
    public ResponseEntity<TurnMessageResponse> sendMessage(@PathVariable Long ucId,
                                                             @RequestBody TurnMessageRequest request) {
        TurnMessageResponse response = turnOrchestrationService.sendMessage(ucId, request.content());
        return ResponseEntity.ok(response);
    }

    /**
     * 오늘의 대화를 조기 종료한다.
     *
     * @throws com.sok.fallain.common.exception.BusinessException DAY_CLOSED(409) / TURN_IN_PROGRESS(409)
     */
    @PostMapping("/{ucId}/end-day")
    public ResponseEntity<EndDayResponse> endDay(@PathVariable Long ucId) {
        EndDayResponse response = turnOrchestrationService.endDay(ucId);
        return ResponseEntity.ok(response);
    }
}
