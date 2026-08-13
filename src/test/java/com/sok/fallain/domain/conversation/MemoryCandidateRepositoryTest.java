package com.sok.fallain.domain.conversation;

import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.CharacterRepository;
import com.sok.fallain.domain.player.Player;
import com.sok.fallain.domain.player.PlayerRepository;
import com.sok.fallain.domain.relationship.DayState;
import com.sok.fallain.domain.relationship.RelationshipStatus;
import com.sok.fallain.domain.relationship.UserCharacter;
import com.sok.fallain.domain.relationship.UserCharacterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MemoryCandidate 엔티티 / MemoryCandidateRepository 테스트.
 *
 * BLUEPRINT 섹션 2 DB스키마: domain.conversation.MemoryCandidate — UserCharacter N:1, factKey,
 * state(enum DROPPED/RECALLED/EXPIRED), droppedOnDay. UNIQUE(userCharacter, factKey).
 * 인덱스 (user_character_id, state).
 *
 * ADR-005: freshness는 저장하지 않고 조회 시점 파생 계산 — 이 테스트는 lastTouchedDay류 파생값이 아닌
 * MemoryCandidate 자체(state/droppedOnDay)의 저장·조회만 검증한다.
 *
 * 현재 MemoryCandidate/MemoryCandidateRepository/MemoryCandidateState가 존재하지 않으므로
 * 컴파일 실패(RED)가 정상이다.
 */
@DataJpaTest
class MemoryCandidateRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private UserCharacterRepository userCharacterRepository;

    @Autowired
    private MemoryCandidateRepository memoryCandidateRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private UserCharacter persistUserCharacter() {
        Player player = playerRepository.saveAndFlush(
                Player.builder().playerId(UUID.randomUUID()).nickname("플레이어").build()
        );
        Character character = characterRepository.saveAndFlush(
                Character.builder().name("소울").persona("따뜻하지만 상처를 숨기는 성격").arcLengthDays(30).build()
        );
        return userCharacterRepository.saveAndFlush(
                UserCharacter.builder()
                        .player(player)
                        .character(character)
                        .intimacy(0)
                        .currentDay(1)
                        .turnsUsedToday(0)
                        .pendingTurn(false)
                        .dayState(DayState.IN_PROGRESS)
                        .lastTouchedDay(1)
                        .status(RelationshipStatus.ONGOING)
                        .build()
        );
    }

    @Test
    void memoryCandidate를_저장하고_ID로_조회할_수_있다() {
        UserCharacter uc = persistUserCharacter();

        MemoryCandidate candidate = MemoryCandidate.builder()
                .userCharacter(uc)
                .factKey("favorite_food")
                .state(MemoryCandidateState.DROPPED)
                .droppedOnDay(3)
                .build();

        MemoryCandidate saved = memoryCandidateRepository.saveAndFlush(candidate);
        testEntityManager.clear();

        Optional<MemoryCandidate> found = memoryCandidateRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getFactKey()).isEqualTo("favorite_food");
        assertThat(found.get().getState()).isEqualTo(MemoryCandidateState.DROPPED);
        assertThat(found.get().getDroppedOnDay()).isEqualTo(3);
        assertThat(found.get().getUserCharacter().getId()).isEqualTo(uc.getId());
    }

    @Test
    void 같은_userCharacter_factKey_조합은_중복_생성될_수_없다() {
        UserCharacter uc = persistUserCharacter();

        memoryCandidateRepository.saveAndFlush(
                MemoryCandidate.builder().userCharacter(uc).factKey("favorite_food").state(MemoryCandidateState.DROPPED).droppedOnDay(3).build()
        );

        MemoryCandidate duplicate = MemoryCandidate.builder()
                .userCharacter(uc)
                .factKey("favorite_food")
                .state(MemoryCandidateState.RECALLED)
                .droppedOnDay(5)
                .build();

        assertThatThrownBy(() -> memoryCandidateRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByUserCharacterIdAndState는_해당_상태의_후보만_반환한다() {
        UserCharacter uc = persistUserCharacter();

        memoryCandidateRepository.saveAndFlush(
                MemoryCandidate.builder().userCharacter(uc).factKey("favorite_food").state(MemoryCandidateState.DROPPED).droppedOnDay(3).build()
        );
        memoryCandidateRepository.saveAndFlush(
                MemoryCandidate.builder().userCharacter(uc).factKey("deepest_secret").state(MemoryCandidateState.RECALLED).droppedOnDay(10).build()
        );
        testEntityManager.clear();

        List<MemoryCandidate> dropped = memoryCandidateRepository.findByUserCharacterIdAndState(uc.getId(), MemoryCandidateState.DROPPED);

        assertThat(dropped).hasSize(1);
        assertThat(dropped.get(0).getFactKey()).isEqualTo("favorite_food");
    }
}
