package com.sok.fallain.domain.player;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Player 엔티티 / PlayerRepository 저장·조회 테스트.
 *
 * BLUEPRINT 섹션 2 DB스키마: domain.player.Player — playerId(UUID, unique), nickname(nullable).
 * BaseEntity 상속 (createdAt/updatedAt/createdBy/updatedBy/isDeleted).
 *
 * 현재 com.sok.fallain.domain.player 패키지에 Player/PlayerRepository가 존재하지 않으므로
 * 컴파일 실패(RED)가 정상이다. DB 에이전트가 엔티티/Repository를 구현하면 GREEN 전환된다.
 */
@DataJpaTest
class PlayerRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void player를_저장하고_ID로_조회할_수_있다() {
        UUID playerId = UUID.randomUUID();
        Player player = Player.builder()
                .playerId(playerId)
                .nickname("경청이")
                .build();

        Player saved = playerRepository.saveAndFlush(player);
        testEntityManager.clear();

        Optional<Player> found = playerRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getPlayerId()).isEqualTo(playerId);
        assertThat(found.get().getNickname()).isEqualTo("경청이");
    }

    @Test
    void nickname은_null이어도_저장된다() {
        Player player = Player.builder()
                .playerId(UUID.randomUUID())
                .nickname(null)
                .build();

        Player saved = playerRepository.saveAndFlush(player);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getNickname()).isNull();
    }

    @Test
    void playerId는_중복될_수_없다() {
        UUID duplicateId = UUID.randomUUID();
        Player first = Player.builder().playerId(duplicateId).nickname("A").build();
        playerRepository.saveAndFlush(first);

        Player second = Player.builder().playerId(duplicateId).nickname("B").build();

        assertThatThrownBy(() -> playerRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
