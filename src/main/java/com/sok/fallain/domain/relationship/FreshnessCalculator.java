package com.sok.fallain.domain.relationship;

/**
 * ADR-005: 기억(Memory)의 신선도(freshness)는 DB에 별도 컬럼으로 저장하지 않고,
 * 조회 시점에 gap = currentDay - referenceDay 로 파생 계산한다.
 *
 * 임계값 N1 = 2로 고정:
 *  - gap == 0        -> ACTIVE (오늘 또는 방금 터치됨)
 *  - 1 <= gap <= 2    -> FADING (N1=2, 서서히 옅어짐)
 *  - gap >= 3        -> FADED (완전히 잊혀짐)
 */
public class FreshnessCalculator {

    private static final int FADING_THRESHOLD = 2;

    public static Freshness calculate(int currentDay, int referenceDay) {
        int gap = currentDay - referenceDay;

        if (gap == 0) {
            return Freshness.ACTIVE;
        } else if (gap >= 1 && gap <= FADING_THRESHOLD) {
            return Freshness.FADING;
        } else {
            return Freshness.FADED;
        }
    }
}
