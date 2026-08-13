package com.sok.fallain.domain.conversation;

/**
 * 외부 LLM(Gemini) 연동 포트. 실제 구현은 별도 어댑터에서 제공하며(ADR 미해결: Gemini API 키
 * 미발급), 테스트에서는 {@code @MockBean}으로 대체한다.
 */
public interface LlmClient {

    LlmTurnResult generateTurn(LlmTurnRequest request);
}
