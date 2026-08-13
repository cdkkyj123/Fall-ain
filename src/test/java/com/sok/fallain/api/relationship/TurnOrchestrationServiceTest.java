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
 * 계약:
 *  - TurnOrchestrationService.sendMessage(Long ucId, String content) -> TurnMessageResponse
 *    - TX1(예약): pendingTurn==false && turnsUsedToday<8 && dayState==IN_PROGRESS 원자적 조건부
 *      업데이트로 pendingTurn=true + 유저 메시지 저장. 조건 불충족 시 즉시 BusinessException:
 *      pendingTurn==true -> TURN_IN_PROGRESS(409), turnsUsedToday>=8 -> TURN_BUDGET_EXCEEDED(409),
 *      dayState==CLOSED -> DAY_CLOSED(409).
 *    - dayState==CLOSED && status==ONGOING인 상태에서 새 메시지가 오면 먼저 롤오버 처리:
 *      nextDay = currentDay+1. nextDay<=30이면 currentDay=nextDay, turnsUsedToday=0,
 *      pendingTurn=false, dayState=IN_PROGRESS로 갱신 후 TX1을 정상 진행한다.
 *      nextDay>30이면 status=ENDED로 전환하고 BusinessException(ErrorCode.ARC_ENDED, 409)를 던진다
 *      (currentDay는 30에 그대로 둔다). status가 이미 ENDED인 경우도 즉시 ARC_ENDED.
 *    - LLM 호출은 트랜잭션 밖에서 수행하고 실패 시 서버가 1회 재시도(총 2회 호출)한다.
 *      재시도까지 실패하면 TX(pendingTurn=false로 롤백, turnsUsedToday 불변)를 수행하고
 *      BusinessException(ErrorCode.LLM_UNAVAILABLE, 503)을 던진다.
 *    - LLM 성공 시 droppedFactKeys를 그 턴에 실제로 주입한 PersonaFact 후보 집합으로 화이트리스트
 *      검증한다. 위반이 있으면 1회 재프롬프트(동일 요청으로 LlmClient 재호출)하고, 그래도 위반이면
 *      안전 폴백: 응답의 droppedFactKeys/callback은 null, MemoryCandidate 미생성, intimacy 변화 없음.
 *      단 LLM 자체는 성공했으므로 턴은 정상 차감된다.
 *    - 화이트리스트를 통과하면: 통과분 factKey마다 MemoryCandidate(state=DROPPED, droppedOnDay=
 *      currentDay) 생성, callback이 있으면 IntimacyScoreTable.score(accuracy, timeliness)를,
 *      없으면 +2(일반 대화)를 intimacy에 더하고 [0,1000] clamp. callback.accuracy()==EXACT이면
 *      해당 factKey의 MemoryCandidate.state를 DROPPED->RECALLED로 전이(되짚기 성공).
 *    - 턴 확정: turnsUsedToday+=1, pendingTurn=false, lastTouchedDay=currentDay, 캐릭터 메시지 저장.
 *      turnsUsedToday==8에 도달하면 그 확정 트랜잭션 내에서 DaySummary(closedReason=
 *      TURNS_EXHAUSTED) 생성 + dayState=CLOSED까지 함께 처리한다(자동 하루마감, T12).
 *  - TurnOrchestrationService.endDay(Long ucId) -> EndDayResponse
 *    - dayState==CLOSED면 DAY_CLOSED(409), pendingTurn==true면 TURN_IN_PROGRESS(409).
 *    - 정상 시 DaySummary(closedReason=USER_ENDED, day=currentDay) 생성, dayState=CLOSED.
 *      (currentDay 자체의 롤오버는 다음 상호작용 시점에 수행 — end-day 시점에는 증가시키지 않는다.)
 *
 * 현재 TurnOrchestrationService/LlmClient/LlmTurnResult/TurnMessageResponse/EndDayResponse 및
 * ErrorCode.TURN_BUDGET_EXCEEDED/TURN_IN_PROGRESS/LLM_UNAVAILABLE/DAY_CLOSED/ARC_ENDED가
 * 존재하지 않으므로 컴파일 실패(RED)가 정상이다.
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

    private ErrorCode errorCodeOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    void 정상_턴_처리시_턴이_차감되고_MemoryCandidate가_생성되고_intimacy가_갱신된다() {
        Character character = persistCharacter();
        persistPersonaFact(character, "favorite_food", 1, 30);
        UserCharacter uc = persistUserCharacter(character, 2, 0, false, DayState.IN_PROGRESS, 100);

        given(llmClient.generateTurn(any())).willReturn(
                new LlmTurnResult("오늘 기분 좋아!", List.of("favorite_food"), null)
        );

        TurnMessageResponse response = turnOrchestrationService.sendMessage(uc.getId(), "오늘 뭐 좋아해?");

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
    void LLM_호출이_재시도까지_실패하면_턴이_차감되지_않고_pending이_해제된다() {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 1, 0, false, DayState.IN_PROGRESS, 0);

        given(llmClient.generateTurn(any())).willThrow(new RuntimeException("LLM 장애"));

        assertThatThrownBy(() -> turnOrchestrationService.sendMessage(uc.getId(), "안녕"))
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
        UserCharacter uc = persistUserCharacter(character, 3, 8, false, DayState.IN_PROGRESS, 50);

        assertThatThrownBy(() -> turnOrchestrationService.sendMessage(uc.getId(), "한번더"))
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
        UserCharacter uc = persistUserCharacter(character, 1, 2, true, DayState.IN_PROGRESS, 20);

        assertThatThrownBy(() -> turnOrchestrationService.sendMessage(uc.getId(), "또보내기"))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.TURN_IN_PROGRESS);

        verify(llmClient, never()).generateTurn(any());
    }

    @Test
    void 여덟번째_턴이_확정되면_자동으로_하루가_마감된다() {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 4, 7, false, DayState.IN_PROGRESS, 10);

        given(llmClient.generateTurn(any())).willReturn(
                new LlmTurnResult("마지막 대화야", List.of(), null)
        );

        turnOrchestrationService.sendMessage(uc.getId(), "오늘의 마지막 메시지");

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
        UserCharacter uc = persistUserCharacter(character, 1, 0, false, DayState.IN_PROGRESS, 50);

        given(llmClient.generateTurn(any())).willReturn(
                new LlmTurnResult("음... 그건 비밀이야", List.of("never_injected_key"), null)
        );

        TurnMessageResponse response = turnOrchestrationService.sendMessage(uc.getId(), "그거 뭐야?");

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
        UserCharacter uc = persistUserCharacter(character, 5, 8, false, DayState.CLOSED, 100);

        given(llmClient.generateTurn(any())).willReturn(
                new LlmTurnResult("좋은 아침이야", List.of(), null)
        );

        turnOrchestrationService.sendMessage(uc.getId(), "굿모닝");

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
        UserCharacter uc = persistUserCharacter(character, 30, 8, false, DayState.CLOSED, 500);

        assertThatThrownBy(() -> turnOrchestrationService.sendMessage(uc.getId(), "우리 얘기 더 하자"))
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
        UserCharacter uc = userCharacterRepository.saveAndFlush(
                UserCharacter.builder()
                        .player(persistPlayer())
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

        assertThatThrownBy(() -> turnOrchestrationService.sendMessage(uc.getId(), "다시 얘기하자"))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.ARC_ENDED);

        verify(llmClient, never()).generateTurn(any());
    }

    @Test
    void 조기종료하면_DaySummary가_USER_ENDED로_생성되고_dayState가_CLOSED로_바뀐다() {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 6, 3, false, DayState.IN_PROGRESS, 80);

        EndDayResponse response = turnOrchestrationService.endDay(uc.getId());

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
    void 이미_마감된_하루에_end_day를_재요청하면_DAY_CLOSED_예외가_발생한다() {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 6, 3, false, DayState.CLOSED, 80);

        assertThatThrownBy(() -> turnOrchestrationService.endDay(uc.getId()))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.DAY_CLOSED);
    }

    @Test
    void pending_턴이_있는_상태에서_end_day를_호출하면_TURN_IN_PROGRESS_예외가_발생한다() {
        Character character = persistCharacter();
        UserCharacter uc = persistUserCharacter(character, 6, 3, true, DayState.IN_PROGRESS, 80);

        assertThatThrownBy(() -> turnOrchestrationService.endDay(uc.getId()))
                .isInstanceOf(BusinessException.class)
                .matches(t -> errorCodeOf(t) == ErrorCode.TURN_IN_PROGRESS);
    }
}
