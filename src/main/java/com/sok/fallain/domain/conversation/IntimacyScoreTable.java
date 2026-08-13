package com.sok.fallain.domain.conversation;

/**
 * ADR-002: LLM은 정확도(EXACT/PARTIAL/WRONG)·타이밍(FITTING/FORCED) 판정 신호만 내고,
 * 최종 intimacy 점수 변화량은 서버에 고정된 결정론 상수표로 계산한다.
 *
 * EXACT+FITTING=+25, EXACT+FORCED=+8, PARTIAL+FITTING=+10, PARTIAL+FORCED=+3,
 * WRONG(타이밍 무관)=-15, 콜백 없음(일반 대화)=+2.
 */
public final class IntimacyScoreTable {

    private static final int MIN_INTIMACY = 0;
    private static final int MAX_INTIMACY = 1000;

    private static final int SCORE_NO_CALLBACK = 2;
    private static final int SCORE_EXACT_FITTING = 25;
    private static final int SCORE_EXACT_FORCED = 8;
    private static final int SCORE_PARTIAL_FITTING = 10;
    private static final int SCORE_PARTIAL_FORCED = 3;
    private static final int SCORE_WRONG = -15;

    private IntimacyScoreTable() {
    }

    public static int score(CallbackAccuracy accuracy, CallbackTimeliness timeliness) {
        if (accuracy == null) {
            return SCORE_NO_CALLBACK;
        }
        return switch (accuracy) {
            case WRONG -> SCORE_WRONG;
            case EXACT -> timeliness == CallbackTimeliness.FITTING ? SCORE_EXACT_FITTING : SCORE_EXACT_FORCED;
            case PARTIAL -> timeliness == CallbackTimeliness.FITTING ? SCORE_PARTIAL_FITTING : SCORE_PARTIAL_FORCED;
        };
    }

    public static int clamp(int intimacy) {
        return Math.max(MIN_INTIMACY, Math.min(MAX_INTIMACY, intimacy));
    }
}
