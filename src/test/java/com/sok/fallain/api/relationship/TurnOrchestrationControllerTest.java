package com.sok.fallain.api.relationship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sok.fallain.api.relationship.dto.TurnMessageRequest;
import com.sok.fallain.common.exception.ErrorCode;
import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.CharacterRepository;
import com.sok.fallain.domain.conversation.LlmClient;
import com.sok.fallain.domain.conversation.LlmTurnResult;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/relationships/{ucId}/message (REST 폴백), POST /api/relationships/{ucId}/end-day
 * MockMvc API 테스트 (T9, T12).
 *
 * docs/api/API.md 섹션 3~4, ADR-006(2단계 커밋) 기준.
 * LlmClient는 실제 Gemini 연동 없이 @MockBean으로 대체한다 (ADR 미해결: Gemini API 키 미발급).
 * 상세 오케스트레이션 로직(재시도, 화이트리스트, intimacy 점수 등)의 단위/통합 검증은
 * TurnOrchestrationServiceTest에서 담당하며, 이 테스트는 컨트롤러 계층 HTTP 매핑만 확인한다.
 *
 * 계약 (TurnOrchestrationServiceTest 문서 참조):
 *  - TurnMessageRequest(String content)
 *  - TurnMessageResponse(Long ucId, String replyText, List<String> droppedFactKeys, Integer intimacy,
 *      Integer intimacyDelta, Integer turnsUsedToday, Integer turnsLeftToday, String dayState,
 *      Boolean dayJustClosed)
 *  - EndDayResponse(Long ucId, Integer closedDay, String closedReason, String dayState)
 *
 * 현재 TurnOrchestrationController/Service/LlmClient/TurnMessageRequest 등이 존재하지 않으므로
 * 컴파일 실패(RED)가 정상이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TurnOrchestrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private UserCharacterRepository userCharacterRepository;

    @MockBean
    private LlmClient llmClient;

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

    private UserCharacter persistUserCharacter(Character character, int currentDay, int turnsUsedToday,
                                                boolean pendingTurn, DayState dayState, int intimacy) {
        return userCharacterRepository.saveAndFlush(
                UserCharacter.builder()
                        .player(persistPlayer())
                        .character(character)
                        .intimacy(intimacy)
                        .currentDay(currentDay)
                        .turnsUsedToday(turnsUsedToday)
                        .pendingTurn(pendingTurn)
                        .dayState(dayState)
                        .lastTouchedDay(currentDay)
                        .status(RelationshipStatus.ONGOING)
                        .build()
        );
    }

    @Test
    void 메시지_전송에_성공하면_200과_응답필드를_반환한다() throws Exception {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 1, 0, false, DayState.IN_PROGRESS, 0);

        given(llmClient.generateTurn(any())).willReturn(
                new LlmTurnResult("반가워!", List.of(), null)
        );

        mockMvc.perform(post("/api/relationships/{ucId}/message", uc.getId())
                        .header("X-Player-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TurnMessageRequest("안녕!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ucId").value(uc.getId()))
                .andExpect(jsonPath("$.replyText").value("반가워!"))
                .andExpect(jsonPath("$.turnsUsedToday").value(1))
                .andExpect(jsonPath("$.dayState").value("IN_PROGRESS"));
    }

    @Test
    void 턴예산_초과시_409와_TURN_BUDGET_EXCEEDED를_반환한다() throws Exception {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 1, 8, false, DayState.IN_PROGRESS, 0);

        mockMvc.perform(post("/api/relationships/{ucId}/message", uc.getId())
                        .header("X-Player-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TurnMessageRequest("한번더"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.TURN_BUDGET_EXCEEDED.getCode()));
    }

    @Test
    void LLM_호출이_계속_실패하면_503과_LLM_UNAVAILABLE을_반환한다() throws Exception {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 1, 0, false, DayState.IN_PROGRESS, 0);

        given(llmClient.generateTurn(any())).willThrow(new RuntimeException("LLM 장애"));

        mockMvc.perform(post("/api/relationships/{ucId}/message", uc.getId())
                        .header("X-Player-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TurnMessageRequest("안녕"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.LLM_UNAVAILABLE.getCode()));
    }

    @Test
    void 조기종료하면_200과_USER_ENDED_closedReason을_반환한다() throws Exception {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 3, 2, false, DayState.IN_PROGRESS, 30);

        mockMvc.perform(post("/api/relationships/{ucId}/end-day", uc.getId())
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ucId").value(uc.getId()))
                .andExpect(jsonPath("$.closedReason").value("USER_ENDED"))
                .andExpect(jsonPath("$.dayState").value("CLOSED"));

        UserCharacter reloaded = userCharacterRepository.findById(uc.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getDayState()).isEqualTo(DayState.CLOSED);
    }

    @Test
    void 이미_마감된_하루에_조기종료를_재요청하면_409와_DAY_CLOSED를_반환한다() throws Exception {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 3, 2, false, DayState.CLOSED, 30);

        mockMvc.perform(post("/api/relationships/{ucId}/end-day", uc.getId())
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DAY_CLOSED.getCode()));
    }
}
