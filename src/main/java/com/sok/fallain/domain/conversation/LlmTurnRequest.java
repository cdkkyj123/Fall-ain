package com.sok.fallain.domain.conversation;

import java.util.List;

/**
 * LLM(Gemini) 턴 생성 호출을 위한 요청 페이로드.
 * {@link PromptBuilder}가 순수 매핑으로 조립한다.
 */
public record LlmTurnRequest(
        Long userCharacterId,
        String characterName,
        String characterPersona,
        Integer currentDay,
        List<FactCandidate> factCandidates,
        List<ActiveCandidate> activeMemoryCandidates,
        String userMessageText
) {

    /**
     * 이번 턴에 LLM에게 노출(주입)되는, 발화로 드러낼 수 있는 PersonaFact 후보.
     */
    public record FactCandidate(String factKey, String content, Integer tier) {
    }

    /**
     * 현재 되짚기(callback) 판정 대상이 될 수 있는 활성 MemoryCandidate.
     */
    public record ActiveCandidate(String factKey, Integer droppedOnDay) {
    }
}
