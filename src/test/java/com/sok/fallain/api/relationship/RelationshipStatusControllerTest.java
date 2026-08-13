package com.sok.fallain.api.relationship;

import com.sok.fallain.common.exception.ErrorCode;
import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.CharacterRepository;
import com.sok.fallain.domain.conversation.MemoryCandidate;
import com.sok.fallain.domain.conversation.MemoryCandidateRepository;
import com.sok.fallain.domain.conversation.MemoryCandidateState;
import com.sok.fallain.domain.player.Player;
import com.sok.fallain.domain.player.PlayerRepository;
import com.sok.fallain.domain.relationship.DayState;
import com.sok.fallain.domain.relationship.RelationshipStatus;
import com.sok.fallain.domain.relationship.UserCharacter;
import com.sok.fallain.domain.relationship.UserCharacterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/relationships/{ucId} 관계 상태 조회 컨트롤러 테스트.
 *
 * 에러(404): 존재하지 않는 ucId -> {"code": "RELATIONSHIP_NOT_FOUND", "message": ...}
 * 소유권 검증(IDOR 방지, Phase 4 REVIEW 발견사항): 요청자(X-Player-Id)가 ucId의 소유자가 아니면
 * 존재 여부를 노출하지 않기 위해 존재하지 않는 경우와 동일하게 404 + RELATIONSHIP_NOT_FOUND를
 * 반환한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RelationshipStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private UserCharacterRepository userCharacterRepository;

    @Autowired
    private MemoryCandidateRepository memoryCandidateRepository;

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

    @Test
    void 존재하는_ucId로_조회하면_200과_전체_상태_필드를_반환한다() throws Exception {
        Player player = persistPlayer();
        Character character = persistCharacter();
        UserCharacter uc = userCharacterRepository.saveAndFlush(
                UserCharacter.builder()
                        .player(player)
                        .character(character)
                        .intimacy(200)
                        .currentDay(5)
                        .turnsUsedToday(3)
                        .pendingTurn(false)
                        .dayState(DayState.IN_PROGRESS)
                        .lastTouchedDay(5)
                        .status(RelationshipStatus.ONGOING)
                        .build()
        );

        memoryCandidateRepository.saveAndFlush(
                MemoryCandidate.builder()
                        .userCharacter(uc)
                        .factKey("favorite_food")
                        .state(MemoryCandidateState.DROPPED)
                        .droppedOnDay(5)
                        .build()
        );

        mockMvc.perform(get("/api/relationships/{ucId}", uc.getId())
                        .header("X-Player-Id", player.getPlayerId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ucId").value(uc.getId()))
                .andExpect(jsonPath("$.day").value(5))
                .andExpect(jsonPath("$.intimacy").value(200))
                .andExpect(jsonPath("$.stage").value("ACQUAINTED"))
                .andExpect(jsonPath("$.turnsUsedToday").value(3))
                .andExpect(jsonPath("$.turnBudget").value(8))
                .andExpect(jsonPath("$.turnsLeftToday").value(5))
                .andExpect(jsonPath("$.dayState").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.memoryCandidates.length()").value(1))
                .andExpect(jsonPath("$.memoryCandidates[0].factKey").value("favorite_food"))
                .andExpect(jsonPath("$.memoryCandidates[0].freshness").value("ACTIVE"));
    }

    @Test
    void 활성_DROPPED_후보만_응답에_포함되고_RECALLED_EXPIRED는_제외된다() throws Exception {
        Player player = persistPlayer();
        Character character = persistCharacter();
        UserCharacter uc = userCharacterRepository.saveAndFlush(
                UserCharacter.builder()
                        .player(player)
                        .character(character)
                        .intimacy(0)
                        .currentDay(10)
                        .turnsUsedToday(0)
                        .pendingTurn(false)
                        .dayState(DayState.IN_PROGRESS)
                        .lastTouchedDay(10)
                        .status(RelationshipStatus.ONGOING)
                        .build()
        );

        memoryCandidateRepository.saveAndFlush(
                MemoryCandidate.builder()
                        .userCharacter(uc)
                        .factKey("daily_habit")
                        .state(MemoryCandidateState.DROPPED)
                        .droppedOnDay(8)
                        .build()
        );
        memoryCandidateRepository.saveAndFlush(
                MemoryCandidate.builder()
                        .userCharacter(uc)
                        .factKey("childhood_wound")
                        .state(MemoryCandidateState.RECALLED)
                        .droppedOnDay(3)
                        .build()
        );
        memoryCandidateRepository.saveAndFlush(
                MemoryCandidate.builder()
                        .userCharacter(uc)
                        .factKey("deepest_secret")
                        .state(MemoryCandidateState.EXPIRED)
                        .droppedOnDay(1)
                        .build()
        );

        mockMvc.perform(get("/api/relationships/{ucId}", uc.getId())
                        .header("X-Player-Id", player.getPlayerId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memoryCandidates.length()").value(1))
                .andExpect(jsonPath("$.memoryCandidates[0].factKey").value("daily_habit"))
                .andExpect(jsonPath("$.memoryCandidates[0].freshness").value("FADING"));
    }

    @Test
    void 존재하지_않는_ucId로_조회하면_404와_에러바디를_반환한다() throws Exception {
        mockMvc.perform(get("/api/relationships/{ucId}", 999999L)
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RELATIONSHIP_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.RELATIONSHIP_NOT_FOUND.getMessage()));
    }

    @Test
    void 다른_플레이어의_ucId를_조회하면_404와_RELATIONSHIP_NOT_FOUND를_반환한다() throws Exception {
        Player owner = persistPlayer();
        Player intruder = persistPlayer();
        Character character = persistCharacter();
        UserCharacter uc = userCharacterRepository.saveAndFlush(
                UserCharacter.builder()
                        .player(owner)
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

        mockMvc.perform(get("/api/relationships/{ucId}", uc.getId())
                        .header("X-Player-Id", intruder.getPlayerId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RELATIONSHIP_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.RELATIONSHIP_NOT_FOUND.getMessage()));
    }
}
