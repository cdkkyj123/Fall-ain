package com.sok.fallain.api.relationship;

import com.sok.fallain.domain.conversation.LlmTurnRequest;

import java.util.Set;

/**
 * TX1(예약)에서 조립되어 트랜잭션 밖(LLM 호출)과 TX2(확정) 사이에서 전달되는 내부 컨텍스트.
 * 엔티티가 아닌 순수 값 객체로, 트랜잭션 경계를 넘나들어도 지연 로딩 문제가 없다.
 */
record TurnReservationContext(
        Long userCharacterId,
        LlmTurnRequest request,
        Set<String> injectedFactKeys,
        Integer currentDay,
        Integer turnIndexToday
) {
}
