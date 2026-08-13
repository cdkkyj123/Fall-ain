package com.sok.fallain.domain.conversation;

import java.util.List;

/**
 * LLM(Gemini) 턴 생성 호출 결과.
 */
public record LlmTurnResult(
        String replyText,
        List<String> droppedFactKeys,
        LlmCallback callback
) {

    /**
     * 되짚기(callback) 판정 신호. 최종 intimacy 점수 변화량은 서버의 결정론 상수표
     * ({@link IntimacyScoreTable})가 계산하며, LLM은 정확도/타이밍 판정만 낸다 (ADR-002).
     */
    public record LlmCallback(String factKey, CallbackAccuracy accuracy, CallbackTimeliness timeliness) {
    }
}
