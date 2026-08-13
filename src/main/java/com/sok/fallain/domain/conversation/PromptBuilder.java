package com.sok.fallain.domain.conversation;

import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.PersonaFact;

import java.util.List;

/**
 * LLM 호출 전 요청({@link LlmTurnRequest})을 조립하는 순수함수.
 * Spring 컨텍스트에 의존하지 않는다.
 */
public final class PromptBuilder {

    private PromptBuilder() {
    }

    public static LlmTurnRequest build(Long userCharacterId,
                                        Character character,
                                        Integer currentDay,
                                        List<PersonaFact> factCandidates,
                                        List<MemoryCandidate> activeMemoryCandidates,
                                        String userMessageText) {
        List<LlmTurnRequest.FactCandidate> facts = factCandidates.stream()
                .map(fact -> new LlmTurnRequest.FactCandidate(fact.getFactKey(), fact.getContent(), fact.getTier()))
                .toList();

        List<LlmTurnRequest.ActiveCandidate> actives = activeMemoryCandidates.stream()
                .map(candidate -> new LlmTurnRequest.ActiveCandidate(candidate.getFactKey(), candidate.getDroppedOnDay()))
                .toList();

        return new LlmTurnRequest(
                userCharacterId,
                character.getName(),
                character.getPersona(),
                currentDay,
                facts,
                actives,
                userMessageText
        );
    }
}
