package com.sok.fallain.domain.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByUserCharacterIdAndDayOrderByTurnIndexAsc(Long userCharacterId, Integer day);
}
