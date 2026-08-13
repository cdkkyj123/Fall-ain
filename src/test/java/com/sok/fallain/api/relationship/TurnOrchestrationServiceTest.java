package com.sok.fallain.api.relationship;

import com.sok.fallain.api.relationship.dto.EndDayResponse;
import com.sok.fallain.api.relationship.dto.TurnMessageResponse;
import com.sok.fallain.common.exception.BusinessException;
import com.sok.fallain.common.exception.ErrorCode;
import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.CharacterRepository;
import com.sok.fallain.domain.character.PersonaFact;
import com.sok.fallain.domain.character.PersonaFactCategory;
import com.sok.fallain.domain.character.PersonaFactRepository;
import com.sok.fallain.domain.conversation.DaySummary;
import com.sok.fallain.domain.conversation.DaySummaryClosedReason;
import com.sok.fallain.domain.conversation.DaySummaryRepository;
import com.sok.fallain.domain.conversation.LlmClient;
import com.sok.fallain.domain.conversation.LlmTurnResult;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * TurnOrchestrationService 오케스트레이션 통합테스트 (T9~T12).
 *
 * BLUEPRINT 섹션 3~6, ADR-002(결정론 점수표), ADR-006(2단계 커밋) 기준.
 * LlmClient는 실제 Gemini 연동 없이 @MockBean으로 대체한다 (ADR 미해결: Gemini API 키 미발급).
 *
 * 소유권 검증(IDOR 방지, Phase 4 REVIEW 발견사항): sendMessage/endDay 모두 세 번째 인자로
 * 요청자 Player를 받아, uc.getPlayer()와 다르면 RELATIONSHIP_NOT_FOUND(404 상당)를 던진다
 * (존재 자체를 숨긴다). 그래서 정상 케이스들은 반드시 uc를 생성할 때 실제로 사용한 Player
 * 인스턴스를 sendMessage/endDay에 그대로 넘겨야 한다.
 */
@SpringBootTest
class TurnOrchestrationServiceTest {

    @Autowired
    private TurnOrchestrationService turnOrchestrationService;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private PersonaFactRepository personaFactRepository;

    @Autowired
    private UserCharacterRepository userCharacterRepository;

    @Autowired
    private MemoryCandidateRepository memoryCandidateRepository;

    @Autowired
    private DaySummaryRepository daySummaryRepository;

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

    private PersonaFact persistPersonaFact(Character character, String factKey, int unlockFrom, int unlockTo) {
        return personaFactRepository.saveAndFlush(
                PersonaFact.builder()
                        .character(character)
                        .factKey(factKey)
                        .category(PersonaFactCategory.TASTE)
                        .tier(1)
                        .content("설명-" + factKey)
                        .unlockDayFrom(unlockFrom)
                        .unlockDayTo(unlockTo)
                        .build()
        );
    }

    private UserCharacter persistUserCharacter(Player player, Character character, int currentDay,
                                                int turnsUsedToday, boolean pendingTurn, DayState dayState,
                                                int intimacy) {
        return userCharacterRepository.saveAndFlush(
                UserCharacter.builder()
                        .player(player)
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

    private ErrorCode errorCodeOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    void 정상_턴_처리시_턴이_차감되고_MemoryCandidate가_생성되고_intimacy가_갱신된다() {
        Character character = persistCharacter();
        persistPersonaFact(character, "favorite_food", 1, 30);
        Player player = persistPlayer();
        UserCharacter uc = persistUserCharacter(player, character, 2, 0, false, DayState.IN_PROGRESS, 100);

        given(llmClient.generateTurn(any())).willReturn(
                new LlmTurnResult("오늘 기분 좋아!", List.of("favorite_food"), null)
        );

        TurnMessageResponse response = turnOrchestrationService.sendMessage(uc.getId(), "오늘 뭐 좋아해?", player);

        assertThat(response.replyText()).isEqualTo("오늘 기분 좋아!");
        verify(llmClient, times(1)).generateTurn(any());

        UserCharacter reloaded = userCharacterRepository.findById(uc.getId()).orElseThrow();
        assertThat(reloaded.getTurnsUsedToday()).isEqualTo(1);
        assertThat(reloaded.getPendingTurn()).isFalse();
        assertThat(reloaded.getIntimacy()).isEqualTo(102);
        assertThat(reloaded.getLastTouchedDay()).isEqualTo(2);

        List<MemoryCandidate> candidates = memoryCandidateRepository
                .findByUserCharacterIdAndState(uc.getId(), MemoryCandidateState.DROPPED);
        assertThat(candidates).extracting(MemoryCandidate::getFactKey).contains("favorite_food");
    }

    @Test
    void 다른_플레이어가_메시지를_보내면_RELATIONSHIP_NOT_FOUND_예외가_발생하고_턴은_소비되지_않는다() {
        Character character = persistCharacter();
        Player owner = persistPlayer();
        Player intruder = persistPlayer();
        UserCharacter uc = persistUserCharacter(owner, character, 1, 0, false, DayState.IN_PROGRESS, 0);

        assertThatThrownBy(() -> turnOrchestrationService.sendMessage(uc.getId(), "몰래 보내기", intruder))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.RELATIONSHIP_NOT_FOUND);

        verify(llmClient, never()).generateTurn(any());

        UserCharacter reloaded = userCharacterRepository.findById(uc.getId()).orElseThrow();
        assertThat(reloaded.getTurnsUsedToday()).isEqualTo(0);
        assertThat(reloaded.getPendingTurn()).isFalse();
    }

    @Test
    void LLM_호출이_재시도까지_실패하면_턴이_차감되지_않고_pending이_해제된다() {
        Character character = persistCharacter();
        Player player = persistPlayer();
        UserCharacter uc = persistUserCharacter(player, character, 1, 0, false, DayState.IN_PROGRESS, 0);

        given(llmClient.generateTurn(any())).willThrow(new RuntimeException("LLM 장애"));

        assertThatThrownBy(() -> turnOrchestrationService.sendMessage(uc.getId(), "안녕", player))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.LLM_UNAVAILABLE);

        verify(llmClient, times(2)).generateTurn(any());

        UserCharacter reloaded = userCharacterRepository.findById(uc.getId()).orElseThrow();
        assertThat(reloaded.getTurnsUsedToday()).isEqualTo(0);
        assertThat(reloaded.getPendingTurn()).isFalse();
    }

    @Test
    void 턴예산을_모두_사용한_상태에서_메시지를_보내면_409와_함께_상태가_불변한다() {
        // dayState=IN_PROGRESS로 고정한 채 turnsUsedToday=8인 방어적(비정상) 상태를 직접 구성하여
        // TX1의 turnsUsedToday<8 가드조건 자체를 단위 격리해서 검증한다.
        // 실제 흐름에서는 8번째 턴 확정과 동시에 dayState=CLOSED로 전환되므로 이 조합은 방어 로직 검증용이다.
        Character character = persistCharacter();
        Player player = persistPlayer();
        UserCharacter uc = persistUserCharacter(player, character, 3, 8, false, DayState.IN_PROGRESS, 50);

        assertThatThrownBy(() -> turnOrchestrationService.sendMessage(uc.getId(), "한번더", player))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.TURN_BUDGET_EXCEEDED);

        verify(llmClient, never()).generateTurn(any());

        UserCharacter reloaded = userCharacterRepository.findById(uc.getId()).orElseThrow();
        assertThat(reloaded.getTurnsUsedToday()).isEqualTo(8);
        assertThat(reloaded.getPendingTurn()).isFalse();
        assertThat(reloaded.getIntimacy()).isEqualTo(50);
    }

    @Test
    void 이미_처리중인_턴이_있으면_TURN_IN_PROGRESS_예외가_발생한다() {
        Character character = persistCharacter();
        Player player = persistPlayer();
        UserCharacter uc = persistUserCharacter(player, character, 1, 2, true, DayState.IN_PROGRESS, 20);

        assertThatThrownBy(() -> turnOrchestrationService.sendMessage(uc.getId(), "또보내기", player))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.TURN_IN_PROGRESS);

        verify(llmClient, never()).generateTurn(any());
    }

    @Test
    void 여덟번째_턴이_확정되면_자동으로_하루가_마감된다() {
        Character character = persistCharacter();
        Player player = persistPlayer();
        UserCharacter uc = persistUserCharacter(player, character, 4, 7, false, DayState.IN_PROGRESS, 10);

        given(llmClient.generateTurn(any())).willReturn(
                new LlmTurnResult("마지막 대화야", List.of(), null)
        );

        turnOrchestrationService.sendMessage(uc.getId(), "오늘의 마지막 메시지", player);

        UserCharacter reloaded = userCharacterRepository.findById(uc.getId()).orElseThrow();
        assertThat(reloaded.getTurnsUsedToday()).isEqualTo(8);
        assertThat(reloaded.getDayState()).isEqualTo(DayState.CLOSED);

        List<DaySummary> summaries = daySummaryRepository.findAll().stream()
                .filter(s -> s.getUserCharacter().getId().equals(uc.getId()) && s.getDay().equals(4))
                .toList();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getClosedReason()).isEqualTo(DaySummaryClosedReason.TURNS_EXHAUSTED);
    }

    @Test
    void 화이트리스트_밖_factKey는_안전폴백으로_폐기되고_턴은_정상_차감된다() {
        Character character = persistCharacter();
        persistPersonaFact(character, "favorite_food", 1, 30);
        Player player = persistPlayer();
        UserCharacter uc = persistUserCharacter(player, character, 1, 0, false, DayState.IN_PROGRESS, 50);

        given(llmClient.generateTurn(any())).willReturn(
                new LlmTurnResult("음... 그건 비밀이야", List.of("never_injected_key"), null)
        );

        TurnMessageResponse response = turnOrchestrationService.sendMessage(uc.getId(), "그거 뭐야?", player);

        verify(llmClient, times(2)).generateTurn(any());
        assertThat(response.droppedFactKeys()).isNull();

        UserCharacter reloaded = userCharacterRepository.findById(uc.getId()).orElseThrow();
        assertThat(reloaded.getTurnsUsedToday()).isEqualTo(1);
        assertThat(reloaded.getPendingTurn()).isFalse();
        assertThat(reloaded.getIntimacy()).isEqualTo(50);

        List<MemoryCandidate> candidates = memoryCandidateRepository
                .findByUserCharacterIdAndState(uc.getId(), MemoryCandidateState.DROPPED);
        assertThat(candidates).isEmpty();
    }

    @Test
    void 하루가_마감된_상태에서_다음_메시지를_보내면_다음날로_롤오버된_후_정상_처리된다() {
        Character character = persistCharacter();
        persistPersonaFact(character, "favorite_food", 1, 30);
        Player player = persistPlayer();
        UserCharacter uc = persistUserCharacter(player, character, 5, 8, false, DayState.CLOSED, 100);

        given(llmClient.generateTurn(any())).willReturn(
                new LlmTurnResult("좋은 아침이야", List.of(), null)
        );

        turnOrchestrationService.sendMessage(uc.getId(), "굿모닝", player);

        UserCharacter reloaded = userCharacterRepository.findById(uc.getId()).orElseThrow();
        assertThat(reloaded.getCurrentDay()).isEqualTo(6);
        assertThat(reloaded.getTurnsUsedToday()).isEqualTo(1);
        assertThat(reloaded.getDayState()).isEqualTo(DayState.IN_PROGRESS);
        assertThat(reloaded.getPendingTurn()).isFalse();
        assertThat(reloaded.getStatus()).isEqualTo(RelationshipStatus.ONGOING);
    }

    @Test
    void 마지막_30일차에서_롤오버를_시도하면_관계아크가_종료된다() {
        Character character = persistCharacter();
        Player player = persistPlayer();
        UserCharacter uc = persistUserCharacter(player, character, 30, 8, false, DayState.CLOSED, 500);

        assertThatThrownBy(() -> turnOrchestrationService.sendMessage(uc.getId(), "우리 얘기 더 하자", player))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.ARC_ENDED);

        verify(llmClient, never()).generateTurn(any());

        UserCharacter reloaded = userCharacterRepository.findById(uc.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RelationshipStatus.ENDED);
        assertThat(reloaded.getCurrentDay()).isEqualTo(30);
    }

    @Test
    void 이미_종료된_관계아크에는_메시지를_보낼_수_없다() {
        Character character = persistCharacter();
        Player player = persistPlayer();
        UserCharacter uc = userCharacterRepository.saveAndFlush(
                UserCharacter.builder()
                        .player(player)
                        .character(character)
                        .intimacy(500)
                        .currentDay(30)
                        .turnsUsedToday(8)
                        .pendingTurn(false)
                        .dayState(DayState.CLOSED)
                        .lastTouchedDay(30)
                        .status(RelationshipStatus.ENDED)
                        .build()
        );

        assertThatThrownBy(() -> turnOrchestrationService.sendMessage(uc.getId(), "다시 얘기하자", player))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.ARC_ENDED);

        verify(llmClient, never()).generateTurn(any());
    }

    @Test
    void 조기종료하면_DaySummary가_USER_ENDED로_생성되고_dayState가_CLOSED로_바뀐다() {
        Character character = persistCharacter();
        Player player = persistPlayer();
        UserCharacter uc = persistUserCharacter(player, character, 6, 3, false, DayState.IN_PROGRESS, 80);

        EndDayResponse response = turnOrchestrationService.endDay(uc.getId(), player);

        assertThat(response.closedReason()).isEqualTo("USER_ENDED");
        assertThat(response.dayState()).isEqualTo("CLOSED");

        UserCharacter reloaded = userCharacterRepository.findById(uc.getId()).orElseThrow();
        assertThat(reloaded.getDayState()).isEqualTo(DayState.CLOSED);
        assertThat(reloaded.getStatus()).isEqualTo(RelationshipStatus.ONGOING);

        List<DaySummary> summaries = daySummaryRepository.findAll().stream()
                .filter(s -> s.getUserCharacter().getId().equals(uc.getId()) && s.getDay().equals(6))
                .toList();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getClosedReason()).isEqualTo(DaySummaryClosedReason.USER_ENDED);
    }

    @Test
    void 다른_플레이어가_조기종료를_요청하면_RELATIONSHIP_NOT_FOUND_예외가_발생한다() {
        Character character = persistCharacter();
        Player owner = persistPlayer();
        Player intruder = persistPlayer();
        UserCharacter uc = persistUserCharacter(owner, character, 6, 3, false, DayState.IN_PROGRESS, 80);

        assertThatThrownBy(() -> turnOrchestrationService.endDay(uc.getId(), intruder))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.RELATIONSHIP_NOT_FOUND);

        UserCharacter reloaded = userCharacterRepository.findById(uc.getId()).orElseThrow();
        assertThat(reloaded.getDayState()).isEqualTo(DayState.IN_PROGRESS);
    }

    @Test
    void 이미_마감된_하루에_end_day를_재요청하면_DAY_CLOSED_예외가_발생한다() {
        Character character = persistCharacter();
        Player player = persistPlayer();
        UserCharacter uc = persistUserCharacter(player, character, 6, 3, false, DayState.CLOSED, 80);

        assertThatThrownBy(() -> turnOrchestrationService.endDay(uc.getId(), player))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.DAY_CLOSED);
    }

    @Test
    void pending_턴이_있는_상태에서_end_day를_호출하면_TURN_IN_PROGRESS_예외가_발생한다() {
        Character character = persistCharacter();
        Player player = persistPlayer();
        UserCharacter uc = persistUserCharacter(player, character, 6, 3, true, DayState.IN_PROGRESS, 80);

        assertThatThrownBy(() -> turnOrchestrationService.endDay(uc.getId(), player))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.TURN_IN_PROGRESS);
    }
}
