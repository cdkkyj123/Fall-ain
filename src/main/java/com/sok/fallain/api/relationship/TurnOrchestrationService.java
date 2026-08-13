package com.sok.fallain.api.relationship;

import com.sok.fallain.api.relationship.dto.EndDayResponse;
import com.sok.fallain.api.relationship.dto.TurnMessageResponse;
import com.sok.fallain.common.exception.BusinessException;
import com.sok.fallain.common.exception.ErrorCode;
import com.sok.fallain.domain.conversation.DaySummary;
import com.sok.fallain.domain.conversation.DaySummaryClosedReason;
import com.sok.fallain.domain.conversation.DaySummaryRepository;
import com.sok.fallain.domain.conversation.FactDropValidator;
import com.sok.fallain.domain.conversation.LlmClient;
import com.sok.fallain.domain.conversation.LlmTurnResult;
import com.sok.fallain.domain.relationship.DayState;
import com.sok.fallain.domain.relationship.UserCharacter;
import com.sok.fallain.domain.relationship.UserCharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화 턴 오케스트레이션 서비스 (ADR-006 2단계 커밋, ADR-002 결정론 점수표).
 *
 * <p>이 서비스 자체는 클래스 레벨에서 {@code @Transactional}이 아니다 — LLM 호출은 반드시
 * 트랜잭션 밖에서 이뤄져야 하기 때문이다. TX1(예약)/TX2(확정)는 협력 빈
 * {@link TurnTransactionSupport}가 담당한다(self-invocation 문제 회피).</p>
 */
@Service
@RequiredArgsConstructor
public class TurnOrchestrationService {

    private final TurnTransactionSupport turnTransactionSupport;
    private final LlmClient llmClient;
    private final UserCharacterRepository userCharacterRepository;
    private final DaySummaryRepository daySummaryRepository;

    /**
     * 유저 메시지를 접수하고 LLM 응답을 생성해 턴을 확정한다.
     *
     * @throws BusinessException TURN_IN_PROGRESS(409) / TURN_BUDGET_EXCEEDED(409) /
     *      DAY_CLOSED(409) / ARC_ENDED(409) / LLM_UNAVAILABLE(503)
     */
    public TurnMessageResponse sendMessage(Long ucId, String content) {
        RolloverOutcome rolloverOutcome = turnTransactionSupport.prepareDayState(ucId);
        if (rolloverOutcome == RolloverOutcome.ARC_JUST_ENDED || rolloverOutcome == RolloverOutcome.ARC_ALREADY_ENDED) {
            throw new BusinessException(ErrorCode.ARC_ENDED);
        }

        TurnReservationContext ctx = turnTransactionSupport.reserveTurn(ucId, content);

        LlmTurnResult result;
        try {
            result = invokeWithRetry(ctx);
        } catch (Exception e) {
            turnTransactionSupport.rollbackPendingTurn(ucId);
            throw new BusinessException(ErrorCode.LLM_UNAVAILABLE);
        }

        boolean fallback = false;
        if (!FactDropValidator.isFullyValid(result.droppedFactKeys(), ctx.injectedFactKeys())) {
            LlmTurnResult reprompted = repromptSafely(ctx);
            if (reprompted != null
                    && FactDropValidator.isFullyValid(reprompted.droppedFactKeys(), ctx.injectedFactKeys())) {
                result = reprompted;
            } else {
                fallback = true;
            }
        }

        TurnCommitResult commit = turnTransactionSupport.commitTurn(ctx, result, fallback);

        return new TurnMessageResponse(
                commit.ucId(),
                commit.replyText(),
                commit.droppedFactKeys(),
                commit.intimacy(),
                commit.intimacyDelta(),
                commit.turnsUsedToday(),
                commit.turnsLeftToday(),
                commit.dayState(),
                commit.dayJustClosed()
        );
    }

    /**
     * 유저 조기 종료. dayState==CLOSED면 DAY_CLOSED, pendingTurn==true면 TURN_IN_PROGRESS.
     * currentDay 자체의 롤오버는 다음 메시지 전송 시점에 수행한다.
     */
    @Transactional
    public EndDayResponse endDay(Long ucId) {
        UserCharacter uc = userCharacterRepository.findById(ucId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELATIONSHIP_NOT_FOUND));

        if (uc.getDayState() == DayState.CLOSED) {
            throw new BusinessException(ErrorCode.DAY_CLOSED);
        }
        if (Boolean.TRUE.equals(uc.getPendingTurn())) {
            throw new BusinessException(ErrorCode.TURN_IN_PROGRESS);
        }

        Integer closedDay = uc.getCurrentDay();
        uc.setDayState(DayState.CLOSED);
        userCharacterRepository.save(uc);

        daySummaryRepository.save(DaySummary.builder()
                .userCharacter(uc)
                .day(closedDay)
                .summaryText("Day " + closedDay + " 대화가 유저 요청으로 조기 종료되었습니다.")
                .intimacyDelta(0)
                .closedReason(DaySummaryClosedReason.USER_ENDED)
                .build());

        return new EndDayResponse(
                uc.getId(), closedDay, DaySummaryClosedReason.USER_ENDED.name(), uc.getDayState().name());
    }

    /**
     * LLM 호출 실패 시 즉시 1회 재시도(총 최대 2회 호출). 두 번째 시도도 실패하면 예외가 전파된다.
     */
    private LlmTurnResult invokeWithRetry(TurnReservationContext ctx) {
        try {
            return llmClient.generateTurn(ctx.request());
        } catch (Exception first) {
            return llmClient.generateTurn(ctx.request());
        }
    }

    /**
     * 화이트리스트 위반 시 1회 재프롬프트. 재프롬프트 호출 자체가 실패해도 턴은 이미 최초 LLM
     * 호출에 성공한 상태이므로 예외를 전파하지 않고 안전 폴백 경로로 넘긴다(null 반환).
     */
    private LlmTurnResult repromptSafely(TurnReservationContext ctx) {
        try {
            return llmClient.generateTurn(ctx.request());
        } catch (Exception e) {
            return null;
        }
    }
}
