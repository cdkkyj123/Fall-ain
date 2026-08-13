package com.sok.fallain.api.relationship.dto;

import java.util.List;

public record TurnMessageResponse(
        Long ucId,
        String replyText,
        List<String> droppedFactKeys,
        Integer intimacy,
        Integer intimacyDelta,
        Integer turnsUsedToday,
        Integer turnsLeftToday,
        String dayState,
        Boolean dayJustClosed
) {
}
