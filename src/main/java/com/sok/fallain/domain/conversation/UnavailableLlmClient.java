package com.sok.fallain.domain.conversation;

import org.springframework.stereotype.Component;

/**
 * LlmClient의 기본(placeholder) 프로덕션 구현체.
 *
 * 실제 Gemini 연동은 아직 확정되지 않았다(ADR 미해결: Gemini API 키 미발급). 이 구현체는 호출 시
 * 항상 실패하여 TurnOrchestrationService의 재시도/롤백 경로가 LLM_UNAVAILABLE(503)로 안전하게
 * 귀결되도록 한다. 실제 연동이 준비되면 이 빈을 진짜 Gemini 어댑터로 교체한다.
 *
 * 테스트에서는 {@code @MockBean}으로 대체되어 이 구현체가 사용되지 않는다.
 */
@Component
public class UnavailableLlmClient implements LlmClient {

    @Override
    public LlmTurnResult generateTurn(LlmTurnRequest request) {
        throw new UnsupportedOperationException("LLM 연동이 아직 구성되지 않았습니다 (Gemini API 키 미발급, ADR 미해결).");
    }
}
