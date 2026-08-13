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
 * BLUEPRINT / docs/api/API.md 섹션 "2. 관계 상태 조회" 기준 계약 (이번 TEST 라운드에서 구체 필드로 확정):
 *
 * 성공(200) 응답 바디 (래퍼 없이 리소스 그대로, ADR-009 관련 — TEST 라운드에서 평문 JSON으로 결정,
 * BACKEND 구현 시 이 계약을 따르고 필요 시 docs/api/API.md에 반영해야 한다):
 * <pre>
 * {
 *   "ucId": 1,
 *   "day": 5,                 // = currentDay
 *   "intimacy": 200,
 *   "stage": "ACQUAINTED",    // intimacy 파생: STRANGER 0-149 / ACQUAINTED 150-399 / CLOSE 400-699 / TRUSTED 700-899 / LOVED 900+
 *   "turnsUsedToday": 3,
 *   "turnBudget": 8,          // 고정값
 *   "turnsLeftToday": 5,      // = turnBudget - turnsUsedToday
 *   "dayState": "IN_PROGRESS",
 *   "memoryCandidates": [
 *     { "factKey": "favorite_food", "freshness": "ACTIVE" }
 *   ]
 * }
 * </pre>
 * memoryCandidates 목록에는 MemoryCandidateState.DROPPED 상태만 포함되고, freshness는
 * FreshnessCalculator.calculate(currentDay, droppedOnDay) 파생값이다 (ADR-005).
 *
 * 에러(404): 존재하지 않는 ucId -> {"code": "RELATIONSHIP_NOT_FOUND", "message": ...}
 * (ErrorCode.RELATIONSHIP_NOT_FOUND는 이번 라운드에 신규 추가 필요 — API.md에는 아직 이 코드가
 * 명시되어 있지 않으며 VALIDATION_ERROR/400으로만 되어 있으나, PLANNER 지시에 따라 이 라운드에서는
 * 404 + 전용 코드로 계약을 구체화한다. BACKEND 구현 완료 후 API.md 갱신 필요.)
 *
 * 현재 RelationshipStatusController/서비스/FreshnessCalculator/ErrorCode.RELATIONSHIP_NOT_FOUND가
 * 존재하지 않으므로 컴파일 실패(RED)가 정상이다.
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
}
