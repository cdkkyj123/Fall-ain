package com.sok.fallain.domain.conversation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IntimacyScoreTable 순수함수 단위테스트.
 *
 * ADR-002: LLM은 정확도(EXACT/PARTIAL/WRONG)·타이밍(FITTING/FORCED) 판정 신호만 내고,
 * 최종 intimacy 점수 변화량은 서버에 고정된 결정론 상수표로 계산한다.
 *
 * 이번 TEST 라운드에서 확정하는 점수표 (BACKEND는 이 표대로 구현):
 *  EXACT   + FITTING = +25
 *  EXACT   + FORCED  = +8
 *  PARTIAL + FITTING = +10
 *  PARTIAL + FORCED  = +3   (BLUEPRINT 미명시 조합 — PARTIAL+FITTING(+10)과 WRONG(-15) 사이 값으로
 *                             이 라운드에서 합리적으로 확정. 추후 밸런싱 재조정 가능)
 *  WRONG   (FITTING/FORCED 무관) = -15
 *  콜백 없음(일반 대화, accuracy=null) = +2
 *
 * intimacy 자체는 [0, 1000] 범위로 clamp된다 (UserCharacter.intimacy 스펙, BLUEPRINT 섹션 2).
 *
 * 계약:
 *  - IntimacyScoreTable.score(CallbackAccuracy accuracy, CallbackTimeliness timeliness) -> int (델타)
 *  - IntimacyScoreTable.clamp(int intimacy) -> int ([0,1000]로 clamp)
 *
 * 현재 IntimacyScoreTable/CallbackAccuracy/CallbackTimeliness가 존재하지 않으므로
 * 컴파일 실패(RED)가 정상이다.
 */
class IntimacyScoreTableTest {

    @Test
    void EXACT_FITTING은_25점이다() {
        assertThat(IntimacyScoreTable.score(CallbackAccuracy.EXACT, CallbackTimeliness.FITTING)).isEqualTo(25);
    }

    @Test
    void EXACT_FORCED는_8점이다() {
        assertThat(IntimacyScoreTable.score(CallbackAccuracy.EXACT, CallbackTimeliness.FORCED)).isEqualTo(8);
    }

    @Test
    void PARTIAL_FITTING은_10점이다() {
        assertThat(IntimacyScoreTable.score(CallbackAccuracy.PARTIAL, CallbackTimeliness.FITTING)).isEqualTo(10);
    }

    @Test
    void PARTIAL_FORCED는_3점이다() {
        assertThat(IntimacyScoreTable.score(CallbackAccuracy.PARTIAL, CallbackTimeliness.FORCED)).isEqualTo(3);
    }

    @Test
    void WRONG_FITTING은_마이너스15점이다() {
        assertThat(IntimacyScoreTable.score(CallbackAccuracy.WRONG, CallbackTimeliness.FITTING)).isEqualTo(-15);
    }

    @Test
    void WRONG_FORCED도_마이너스15점이다() {
        assertThat(IntimacyScoreTable.score(CallbackAccuracy.WRONG, CallbackTimeliness.FORCED)).isEqualTo(-15);
    }

    @Test
    void 콜백이_없으면_2점이다() {
        assertThat(IntimacyScoreTable.score(null, null)).isEqualTo(2);
        assertThat(IntimacyScoreTable.score(null, CallbackTimeliness.FITTING)).isEqualTo(2);
    }

    @Test
    void clamp는_1000을_초과할_수_없다() {
        assertThat(IntimacyScoreTable.clamp(1050)).isEqualTo(1000);
    }

    @Test
    void clamp는_0_미만이_될_수_없다() {
        assertThat(IntimacyScoreTable.clamp(-30)).isEqualTo(0);
    }

    @Test
    void clamp는_범위내_값을_그대로_반환한다() {
        assertThat(IntimacyScoreTable.clamp(500)).isEqualTo(500);
    }

    @Test
    void clamp_경계값_0과_1000은_그대로_반환된다() {
        assertThat(IntimacyScoreTable.clamp(0)).isEqualTo(0);
        assertThat(IntimacyScoreTable.clamp(1000)).isEqualTo(1000);
    }
}
