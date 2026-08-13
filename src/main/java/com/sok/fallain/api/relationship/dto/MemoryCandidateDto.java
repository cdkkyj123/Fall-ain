package com.sok.fallain.api.relationship.dto;

/**
 * 메모리 후보 응답 DTO.
 * MemoryCandidateState.DROPPED 상태인 항목만 응답에 포함된다.
 */
public record MemoryCandidateDto(
        String factKey,
        String freshness
) {}
