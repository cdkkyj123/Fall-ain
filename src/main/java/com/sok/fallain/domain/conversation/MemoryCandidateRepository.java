package com.sok.fallain.domain.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemoryCandidateRepository extends JpaRepository<MemoryCandidate, Long> {
    List<MemoryCandidate> findByUserCharacterIdAndState(Long userCharacterId, MemoryCandidateState state);

    Optional<MemoryCandidate> findByUserCharacterIdAndFactKey(Long userCharacterId, String factKey);
}
