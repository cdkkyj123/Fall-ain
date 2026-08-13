package com.sok.fallain.domain.relationship;

import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.CharacterRepository;
import com.sok.fallain.domain.player.Player;
import com.sok.fallain.domain.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserCharacter(UC) 엔티티 / UserCharacterRepository 테스트.
 *
 * BLUEPRINT 섹션 2 DB스키마: domain.relationship.UserCharacter — Player N:1, Character N:1,
 * intimacy(0~1000 clamp), currentDay(1~30), turnsUsedToday(0~8), pendingTurn(boolean),
 * dayState(enum IN_PROGRESS/CLOSED), lastTouchedDay, status(enum ONGOING/ENDED), @Version(낙관적락),
 * UNIQUE(player,character).
 *
 * ADR-001: 동시성 제어는 JPA 낙관적락(@Version) + 원자적 조건부 UPDATE로 처리 (분산락 없음).
 * ADR-006: 턴 예산은 pending 예약 -> 확정 2단계 커밋이므로 pendingTurn 필드가 필요.
 *
 * 현재 UserCharacter/UserCharacterRepository가 존재하지 않으므로 컴파일 실패(RED)가 정상이다.
 */
@DataJpaTest
class UserCharacterRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private UserCharacterRepository userCharacterRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private Player persistPlayer() {
        return playerRepository.saveAndFlush(
                Player.builder().playerId(UUID.randomUUID()).nickname("플레이어").build()
        );
    }

    private Character persistCharacter() {
        return characterRepository.saveAndFlush(
                Character.builder().name("소울").persona("따뜻하지만 상처를 숨기는 성격").arcLengthDays(30).build()
        );
    }

    private UserCharacter newUserCharacter(Player player, Character character) {
        return UserCharacter.builder()
                .player(player)
                .character(character)
                .intimacy(0)
                .currentDay(1)
                .turnsUsedToday(0)
                .pendingTurn(false)
                .dayState(DayState.IN_PROGRESS)
                .lastTouchedDay(1)
                .status(RelationshipStatus.ONGOING)
                .build();
    }

    @Test
    void userCharacter를_저장하고_ID로_조회할_수_있다() {
        Player player = persistPlayer();
        Character character = persistCharacter();

        UserCharacter saved = userCharacterRepository.saveAndFlush(newUserCharacter(player, character));
        testEntityManager.clear();

        Optional<UserCharacter> found = userCharacterRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getIntimacy()).isEqualTo(0);
        assertThat(found.get().getCurrentDay()).isEqualTo(1);
        assertThat(found.get().getTurnsUsedToday()).isEqualTo(0);
        assertThat(found.get().getPendingTurn()).isFalse();
        assertThat(found.get().getDayState()).isEqualTo(DayState.IN_PROGRESS);
        assertThat(found.get().getLastTouchedDay()).isEqualTo(1);
        assertThat(found.get().getStatus()).isEqualTo(RelationshipStatus.ONGOING);
        assertThat(found.get().getPlayer().getId()).isEqualTo(player.getId());
        assertThat(found.get().getCharacter().getId()).isEqualTo(character.getId());
    }

    @Test
    void intimacy_currentDay_turnsUsedToday_필드는_스펙_범위값을_저장할_수_있다() {
        Player player = persistPlayer();
        Character character = persistCharacter();

        UserCharacter uc = UserCharacter.builder()
                .player(player)
                .character(character)
                .intimacy(1000)
                .currentDay(30)
                .turnsUsedToday(8)
                .pendingTurn(true)
                .dayState(DayState.CLOSED)
                .lastTouchedDay(30)
                .status(RelationshipStatus.ENDED)
                .build();

        UserCharacter saved = userCharacterRepository.saveAndFlush(uc);

        assertThat(saved.getIntimacy()).isEqualTo(1000);
        assertThat(saved.getCurrentDay()).isEqualTo(30);
        assertThat(saved.getTurnsUsedToday()).isEqualTo(8);
        assertThat(saved.getPendingTurn()).isTrue();
        assertThat(saved.getDayState()).isEqualTo(DayState.CLOSED);
        assertThat(saved.getStatus()).isEqualTo(RelationshipStatus.ENDED);
    }

    @Test
    void 같은_player_character_조합은_중복_생성될_수_없다() {
        Player player = persistPlayer();
        Character character = persistCharacter();

        userCharacterRepository.saveAndFlush(newUserCharacter(player, character));

        UserCharacter duplicate = newUserCharacter(player, character);

        assertThatThrownBy(() -> userCharacterRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void version_필드는_저장_시점에_0으로_초기화된다() {
        Player player = persistPlayer();
        Character character = persistCharacter();

        UserCharacter saved = userCharacterRepository.saveAndFlush(newUserCharacter(player, character));

        assertThat(saved.getVersion()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);
    }

    @Test
    void 동시에_로드된_두_인스턴스_중_먼저_저장하지_않은_쪽은_낙관적락_예외가_발생한다() {
        Player player = persistPlayer();
        Character character = persistCharacter();
        Long ucId = userCharacterRepository.saveAndFlush(newUserCharacter(player, character)).getId();
        testEntityManager.clear();

        UserCharacter first = userCharacterRepository.findById(ucId).orElseThrow();
        testEntityManager.detach(first);
        UserCharacter second = userCharacterRepository.findById(ucId).orElseThrow();
        testEntityManager.detach(second);

        first.setIntimacy(50);
        userCharacterRepository.saveAndFlush(first);

        second.setIntimacy(100);
        assertThatThrownBy(() -> userCharacterRepository.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
