package com.sok.fallain.domain.character;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonaFactRepository extends JpaRepository<PersonaFact, Long> {

    List<PersonaFact> findByCharacterIdAndUnlockDayFromLessThanEqualAndUnlockDayToGreaterThanEqual(
            Long characterId, Integer unlockDayFrom, Integer unlockDayTo);
}
