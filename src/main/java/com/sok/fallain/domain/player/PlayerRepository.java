package com.sok.fallain.domain.player;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByPlayerId(UUID playerId);
}
