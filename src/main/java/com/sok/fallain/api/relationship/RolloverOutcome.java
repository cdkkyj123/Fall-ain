package com.sok.fallain.api.relationship;

/**
 * {@link TurnTransactionSupport#prepareDayState(Long)}의 결과.
 *
 * ARC_JUST_ENDED/ARC_ALREADY_ENDED 두 경우 모두 서비스 계층이 트랜잭션 밖에서
 * BusinessException(ARC_ENDED)을 던진다 — 상태 전이(ENDED)가 먼저 별도 트랜잭션으로 커밋된
 * 뒤에 예외가 발생해야, 같은 트랜잭션 안에서 쓰기 직후 예외를 던져 롤백되어 버리는 문제를 피할 수
 * 있다.
 */
enum RolloverOutcome {
    OK,
    ARC_JUST_ENDED,
    ARC_ALREADY_ENDED
}
