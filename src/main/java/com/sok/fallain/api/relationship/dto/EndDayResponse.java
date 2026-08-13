package com.sok.fallain.api.relationship.dto;

public record EndDayResponse(
        Long ucId,
        Integer closedDay,
        String closedReason,
        String dayState
) {
}
