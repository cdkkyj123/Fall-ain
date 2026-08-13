package com.sok.fallain.api.relationship;

import com.sok.fallain.common.exception.BusinessException;
import com.sok.fallain.common.exception.ErrorCode;
import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.PersonaFact;
import com.sok.fallain.domain.character.PersonaFactRepository;
import com.sok.fallain.domain.conversation.CallbackAccuracy;
import com.sok.fallain.domain.conversation.DaySummary;
import com.sok.fallain.domain.conversation.DaySummaryClosedReason;
import com.sok.fallain.domain.conversation.DaySummaryRepository;
import com.sok.fallain.domain.conversation.FactDropValidator;
import com.sok.fallain.domain.conversation.IntimacyScoreTable;
import com.sok.fallain.domain.conversation.LlmTurnRequest;
import com.sok.fallain.domain.conversation.LlmTurnResult;
import com.sok.fallain.domain.conversation.MemoryCandidate;
import com.sok.fallain.domain.conversation.MemoryCandidateRepository;
import com.sok.fallain.domain.conversation.MemoryCandidateState;
import com.sok.fallain.domain.conversation.Message;
import com.sok.fallain.domain.conversation.MessageRepository;
import com.sok.fallain.domain.conversation.MessageRole;
import com.sok.fallain.domain.conversation.PromptBuilder;
import com.sok.fallain.domain.relationship.DayState;
import com.sok.fallain.domain.relationship.RelationshipStatus;
import com.sok.fallain.domain.relationship.UserCharacter;
import com.sok.fallain.domain.relationship.UserCharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TurnOrchestrationService의 협력 빈. 트랜잭션 경계(TX1 예약 / 롤백 / TX2 확정)를 실제 프록시가
 * 개입하도록 별도 빈으로 분리했다 — 같은 클래스 내 self-invocation은 {@code @Transactional} AOP가
 * 적용되지 않기 때문이다 (ADR-006 참조).
 *
 * <p>LLM 호출은 이 빈의 어떤 메서드에도 포함되지 않는다. LLM 호출은 반드시 TX1 종료(커밋) 이후,
 * TX2 시작 이전, 트랜잭션 밖에서 {@link TurnOrchestrationService}가 직접 수행한다.</p>
 */
@Component
@RequiredArgsConstructor
class TurnTransactionSupport {

    private static final int TURN_BUDGET = 8;

    private final UserCharacterRepository userCharacterRepository;
    private final PersonaFactRepository personaFactRepository;
    private final MemoryCandidateRepository memoryCandidateRepository;
    private final MessageRepository messageRepository;
    private final DaySummaryRepository daySummaryRepository;

    /**
     * TX0: 날짜 상태를 준비한다(롤오버 또는 아크 종료 판정). dayState==CLOSED이고 아크가 아직
     * 진행 중(status==ONGOING)이면 롤오버(다음 날로 전환)를 시도한다. 롤오버 결과 아크 길이를
     * 넘어서면 이 트랜잭션 안에서 status=ENDED로 전환해 커밋하고 ARC_JUST_ENDED를 반환한다.
     *
     * <p>주의: 이 메서드는 ARC 종료 판정 시에도 예외를 던지지 않고 결과를 반환한다 — 상태 변경을
     * 이 트랜잭션 안에서 커밋시킨 뒤, 트랜잭션 밖(서비스 계층)에서 예외를 던지도록 하기 위함이다.
     * 같은 트랜잭션 메서드 안에서 쓰기 직후 예외를 던지면 그 트랜잭션 전체가 롤백되어 방금 커밋하려던
     * status=ENDED 변경마저 사라진다.</p>
     */
    @Transactional
    public RolloverOutcome prepareDayState(Long ucId) {
        UserCharacter uc = userCharacterRepository.findById(ucId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND));

        if (uc.getStatus() == RelationshipStatus.ENDED) {
            return RolloverOutcome.ARC_ALREADY_ENDED;
        }

        if (uc.getDayState() == DayState.CLOSED) {
            int nextDay = uc.getCurrentDay() + 1;
            int arcLengthDays = uc.getCharacter().getArcLengthDays();
            if (nextDay > arcLengthDays) {
                uc.setStatus(RelationshipStatus.ENDED);
                userCharacterRepository.save(uc);
                return RolloverOutcome.ARC_JUST_ENDED;
            }
            uc.setCurrentDay(nextDay);
            uc.setTurnsUsedToday(0);
            uc.setPendingTurn(false);
            uc.setDayState(DayState.IN_PROGRESS);
            userCharacterRepository.save(uc);
        }

        return RolloverOutcome.OK;
    }

    /**
     * TX1: 턴을 pending으로 예약하고 LLM 호출에 필요한 요청을 조립한다.
     * {@link #prepareDayState(Long)} 호출로 날짜 상태가 이미 정리된 뒤에 호출되어야 한다.
     */
    @Transactional
    public TurnReservationContext reserveTurn(Long ucId, String content) {
        int updated = userCharacterRepository.reservePendingTurn(ucId, TURN_BUDGET, DayState.IN_PROGRESS);
        if (updated == 0) {
            throw resolveReservationFailure(ucId);
        }

        UserCharacter reserved = userCharacterRepository.findById(ucId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND));
        Character character = reserved.getCharacter();
        Integer currentDay = reserved.getCurrentDay();

        List<PersonaFact> factCandidates = personaFactRepository
                .findByCharacterIdAndUnlockDayFromLessThanEqualAndUnlockDayToGreaterThanEqual(
                        character.getId(), currentDay, currentDay);
        List<MemoryCandidate> activeCandidates = memoryCandidateRepository
                .findByUserCharacterIdAndState(ucId, MemoryCandidateState.DROPPED);

        int turnIndexToday = reserved.getTurnsUsedToday();
        messageRepository.save(Message.builder()
                .userCharacter(reserved)
                .day(currentDay)
                .turnIndex(turnIndexToday * 2)
                .role(MessageRole.USER)
                .content(content)
                .build());

        LlmTurnRequest request = PromptBuilder.build(
                ucId, character, currentDay, factCandidates, activeCandidates, content);
        Set<String> injectedKeys = factCandidates.stream()
                .map(PersonaFact::getFactKey)
                .collect(Collectors.toSet());

        return new TurnReservationContext(ucId, request, injectedKeys, currentDay, turnIndexToday);
    }

    private BusinessException resolveReservationFailure(Long ucId) {
        UserCharacter current = userCharacterRepository.findById(ucId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND));
        if (Boolean.TRUE.equals(current.getPendingTurn())) {
            return new BusinessException(ErrorCode.TURN_IN_PROGRESS);
        }
        if (current.getTurnsUsedToday() >= TURN_BUDGET) {
            return new BusinessException(ErrorCode.TURN_BUDGET_EXCEEDED);
        }
        return new BusinessException(ErrorCode.DAY_CLOSED);
    }

    /**
     * LLM 호출이 재시도까지 실패한 경우: pending 예약을 롤백한다. turnsUsedToday는 건드리지 않는다.
     */
    @Transactional
    public void rollbackPendingTurn(Long ucId) {
        UserCharacter uc = userCharacterRepository.findById(ucId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND));
        uc.setPendingTurn(false);
        userCharacterRepository.save(uc);
    }

    /**
     * TX2: 턴을 확정한다. fallback==true이면 화이트리스트를 재프롬프트까지 통과하지 못한 안전
     * 폴백 경로로, droppedFactKeys/callback을 모두 폐기하고 intimacy를 변경하지 않는다(단 턴은
     * 정상 차감된다). turnsUsedToday가 예산(8)에 도달하면 자동으로 하루를 마감한다.
     */
    @Transactional
    public TurnCommitResult commitTurn(TurnReservationContext ctx, LlmTurnResult result, boolean fallback) {
        UserCharacter uc = userCharacterRepository.findById(ctx.userCharacterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND));

        List<String> passedKeys;
        LlmTurnResult.LlmCallback callback;
        int intimacyDelta;

        if (fallback) {
            passedKeys = null;
            callback = null;
            intimacyDelta = 0;
        } else {
            passedKeys = FactDropValidator.validate(result.droppedFactKeys(), ctx.injectedFactKeys());
            callback = result.callback();
            intimacyDelta = IntimacyScoreTable.score(
                    callback != null ? callback.accuracy() : null,
                    callback != null ? callback.timeliness() : null
            );
        }

        applyDroppedFacts(uc, ctx.currentDay(), passedKeys);
        applyCallbackRecall(uc, callback);

        uc.setIntimacy(IntimacyScoreTable.clamp(uc.getIntimacy() + intimacyDelta));
        uc.setTurnsUsedToday(uc.getTurnsUsedToday() + 1);
        uc.setPendingTurn(false);
        uc.setLastTouchedDay(ctx.currentDay());

        messageRepository.save(Message.builder()
                .userCharacter(uc)
                .day(ctx.currentDay())
                .turnIndex(ctx.turnIndexToday() * 2 + 1)
                .role(MessageRole.CHARACTER)
                .content(result.replyText())
                .droppedFactKeysJson(toJson(passedKeys))
                .build());

        boolean dayJustClosed = false;
        if (uc.getTurnsUsedToday() >= TURN_BUDGET) {
            uc.setDayState(DayState.CLOSED);
            dayJustClosed = true;
            daySummaryRepository.save(DaySummary.builder()
                    .userCharacter(uc)
                    .day(ctx.currentDay())
                    .summaryText("Day " + ctx.currentDay() + " 대화가 턴 소진으로 마감되었습니다.")
                    .intimacyDelta(intimacyDelta)
                    .closedReason(DaySummaryClosedReason.TURNS_EXHAUSTED)
                    .build());
        }

        userCharacterRepository.save(uc);

        return new TurnCommitResult(
                uc.getId(),
                result.replyText(),
                passedKeys,
                uc.getIntimacy(),
                intimacyDelta,
                uc.getTurnsUsedToday(),
                TURN_BUDGET - uc.getTurnsUsedToday(),
                uc.getDayState().name(),
                dayJustClosed
        );
    }

    private void applyDroppedFacts(UserCharacter uc, Integer currentDay, List<String> passedKeys) {
        if (passedKeys == null) {
            return;
        }
        for (String key : passedKeys) {
            MemoryCandidate candidate = memoryCandidateRepository
                    .findByUserCharacterIdAndFactKey(uc.getId(), key)
                    .orElseGet(() -> MemoryCandidate.builder()
                            .userCharacter(uc)
                            .factKey(key)
                            .state(MemoryCandidateState.DROPPED)
                            .droppedOnDay(currentDay)
                            .build());
            candidate.markDropped(currentDay);
            memoryCandidateRepository.save(candidate);
        }
    }

    private void applyCallbackRecall(UserCharacter uc, LlmTurnResult.LlmCallback callback) {
        if (callback == null || callback.accuracy() != CallbackAccuracy.EXACT) {
            return;
        }
        memoryCandidateRepository.findByUserCharacterIdAndFactKey(uc.getId(), callback.factKey())
                .filter(mc -> mc.getState() == MemoryCandidateState.DROPPED)
                .ifPresent(MemoryCandidate::recall);
    }

    private String toJson(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        return "[" + keys.stream().map(k -> "\"" + k + "\"").collect(Collectors.joining(",")) + "]";
    }
}
