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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Message 엔티티 / MessageRepository 테스트.
 *
 * BLUEPRINT 섹션 2 DB스키마: domain.conversation.Message — UserCharacter N:1, day, turnIndex,
 * role(enum USER/CHARACTER), content, droppedFactKeysJson(nullable).
 * 인덱스 (user_character_id, day, turn_index).
 *
 * 현재 Message/MessageRepository/MessageRole이 존재하지 않으므로 컴파일 실패(RED)가 정상이다.
 */
@DataJpaTest
class MessageRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private UserCharacterRepository userCharacterRepository;

    @Autowired
    private MessageRepository messageRepository;

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
    void message를_저장하고_ID로_조회할_수_있다() {
        UserCharacter uc = persistUserCharacter();

        Message message = Message.builder()
                .userCharacter(uc)
                .day(1)
                .turnIndex(0)
                .role(MessageRole.USER)
                .content("안녕")
                .droppedFactKeysJson(null)
                .build();

        Message saved = messageRepository.saveAndFlush(message);
        testEntityManager.clear();

        Optional<Message> found = messageRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getDay()).isEqualTo(1);
        assertThat(found.get().getTurnIndex()).isEqualTo(0);
        assertThat(found.get().getRole()).isEqualTo(MessageRole.USER);
        assertThat(found.get().getContent()).isEqualTo("안녕");
        assertThat(found.get().getDroppedFactKeysJson()).isNull();
        assertThat(found.get().getUserCharacter().getId()).isEqualTo(uc.getId());
    }

    @Test
    void droppedFactKeysJson은_값이_있을_때도_저장된다() {
        UserCharacter uc = persistUserCharacter();

        Message message = Message.builder()
                .userCharacter(uc)
                .day(2)
                .turnIndex(1)
                .role(MessageRole.CHARACTER)
                .content("사실 나 민트초코 싫어해")
                .droppedFactKeysJson("[\"favorite_food\"]")
                .build();

        Message saved = messageRepository.saveAndFlush(message);

        assertThat(saved.getDroppedFactKeysJson()).isEqualTo("[\"favorite_food\"]");
    }

    @Test
    void findByUserCharacterIdAndDay는_해당_UC와_day의_메시지만_turnIndex_순으로_반환한다() {
        UserCharacter uc = persistUserCharacter();

        messageRepository.saveAndFlush(
                Message.builder().userCharacter(uc).day(1).turnIndex(1).role(MessageRole.CHARACTER).content("두번째").build()
        );
        messageRepository.saveAndFlush(
                Message.builder().userCharacter(uc).day(1).turnIndex(0).role(MessageRole.USER).content("첫번째").build()
        );
        messageRepository.saveAndFlush(
                Message.builder().userCharacter(uc).day(2).turnIndex(0).role(MessageRole.USER).content("다른날").build()
        );
        testEntityManager.clear();

        List<Message> day1Messages = messageRepository.findByUserCharacterIdAndDayOrderByTurnIndexAsc(uc.getId(), 1);

        assertThat(day1Messages).hasSize(2);
        assertThat(day1Messages.get(0).getContent()).isEqualTo("첫번째");
        assertThat(day1Messages.get(1).getContent()).isEqualTo("두번째");
    }
}
