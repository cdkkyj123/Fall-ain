package com.sok.fallain.domain.relationship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCharacterRepository extends JpaRepository<UserCharacter, Long> {

    /**
     * TX1 예약: pendingTurn==false && turnsUsedToday<turnBudget && dayState==dayState 조건을
     * 원자적 조건부 UPDATE로 검사하여 pendingTurn=true로 전환한다 (ADR-006).
     *
     * @return 갱신된 행 수 (0이면 조건 불충족 — 호출측에서 구체적 사유를 재조회해 판별한다)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserCharacter uc SET uc.pendingTurn = TRUE "
            + "WHERE uc.id = :id AND uc.pendingTurn = FALSE AND uc.turnsUsedToday < :turnBudget "
            + "AND uc.dayState = :dayState")
    int reservePendingTurn(@Param("id") Long id, @Param("turnBudget") int turnBudget,
                            @Param("dayState") DayState dayState);
}
