package com.sok.fallain.api.relationship.dto;

import java.util.List;

/**
 * 관계 상태 조회 응답 DTO.
 * 테스트 계약: 래퍼 없이 리소스 그대로 반환.
 */
public record RelationshipStatusResponse(
        Long ucId,
        Integer day,
        Integer intimacy,
        String stage,
        Integer turnsUsedToday,
        Integer turnBudget,
        Integer turnsLeftToday,
        String dayState,
        List<MemoryCandidateDto> memoryCandidates
) {}
