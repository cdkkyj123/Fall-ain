package com.sok.fallain.domain.relationship;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FreshnessCalculator 단위 테스트.
 *
 * ADR-005: 기억(Memory)의 신선도(freshness)는 DB에 별도 컬럼으로 저장하지 않고,
 * 조회 시점에 gap = currentDay - referenceDay 로 파생 계산한다 (배치/스케줄러 없음).
 *
 * BLUEPRINT는 등급 전이 임계값을 상수 "N1"으로만 언급하고 구체값은 미확정 상태였다.
 * 이번 TEST 라운드에서 N1=2로 확정하여 아래 구간 매핑으로 테스트를 명시한다.
 *  - gap == 0        -> ACTIVE (오늘 또는 방금 터치됨)
 *  - 1 <= gap <= 2    -> FADING (N1=2, 서서히 옅어짐)
 *  - gap >= 3        -> FADED (완전히 잊혀짐)
 *
 * 현재 FreshnessCalculator/Freshness가 존재하지 않으므로 컴파일 실패(RED)가 정상이다.
 */
class FreshnessCalculatorTest {

    @Test
    void gap이_0이면_ACTIVE다() {
        assertThat(FreshnessCalculator.calculate(5, 5)).isEqualTo(Freshness.ACTIVE);
    }

    @Test
    void gap이_1이면_FADING이다() {
        assertThat(FreshnessCalculator.calculate(6, 5)).isEqualTo(Freshness.FADING);
    }

    @Test
    void gap이_N1_경계값인_2이면_FADING이다() {
        assertThat(FreshnessCalculator.calculate(7, 5)).isEqualTo(Freshness.FADING);
    }

    @Test
    void gap이_3_이상이면_FADED다() {
        assertThat(FreshnessCalculator.calculate(8, 5)).isEqualTo(Freshness.FADED);
        assertThat(FreshnessCalculator.calculate(35, 5)).isEqualTo(Freshness.FADED);
    }
}
