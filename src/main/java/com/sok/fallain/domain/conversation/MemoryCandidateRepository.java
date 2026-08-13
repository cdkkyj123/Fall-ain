package com.sok.fallain.domain.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryCandidateRepository extends JpaRepository<MemoryCandidate, Long> {
    List<MemoryCandidate> findByUserCharacterIdAndState(Long userCharacterId, MemoryCandidateState state);
}
