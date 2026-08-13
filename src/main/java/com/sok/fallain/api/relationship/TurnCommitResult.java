package com.sok.fallain.api.relationship;

import java.util.List;

/**
 * TX2(확정) 결과를 서비스 계층에 전달하기 위한 내부 값 객체.
 */
record TurnCommitResult(
        Long ucId,
        String replyText,
        List<String> droppedFactKeys,
        Integer intimacy,
        Integer intimacyDelta,
        Integer turnsUsedToday,
        Integer turnsLeftToday,
        String dayState,
        Boolean dayJustClosed
) {
}
