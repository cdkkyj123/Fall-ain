package com.sok.fallain.domain.conversation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactDropValidator 순수함수 단위테스트.
 *
 * T10 화이트리스트 검증: LLM이 반환한 droppedFactKeys 중 그 턴에 실제로 주입한 PersonaFact 후보
 * 집합(injectedCandidateKeys)에 없는 key는 폐기한다.
 *
 * 계약:
 *  - FactDropValidator.validate(List<String> droppedFactKeys, Set<String> injectedCandidateKeys)
 *    -> 후보 집합에 포함된 key만 남긴 List<String> (통과분). droppedFactKeys가 null/empty면 빈 리스트.
 *  - FactDropValidator.isFullyValid(List<String> droppedFactKeys, Set<String> injectedCandidateKeys)
 *    -> 위반이 하나라도 있으면 false (서비스가 재프롬프트 여부를 판단하는 데 사용). null이면 true.
 *
 * 현재 FactDropValidator가 존재하지 않으므로 컴파일 실패(RED)가 정상이다.
 */
class FactDropValidatorTest {

    @Test
    void 주입된_후보에_포함된_factKey만_통과한다() {
        List<String> result = FactDropValidator.validate(
                List.of("favorite_food", "childhood_wound"),
                Set.of("favorite_food", "childhood_wound", "deepest_secret")
        );

        assertThat(result).containsExactlyInAnyOrder("favorite_food", "childhood_wound");
    }

    @Test
    void 주입되지_않은_factKey는_폐기된다() {
        List<String> result = FactDropValidator.validate(
                List.of("favorite_food", "not_injected_key"),
                Set.of("favorite_food")
        );

        assertThat(result).containsExactly("favorite_food");
    }

    @Test
    void 전부_위반이면_빈_리스트를_반환한다() {
        List<String> result = FactDropValidator.validate(
                List.of("not_injected_1", "not_injected_2"),
                Set.of("favorite_food")
        );

        assertThat(result).isEmpty();
    }

    @Test
    void droppedFactKeys가_null이면_빈_리스트를_반환한다() {
        List<String> result = FactDropValidator.validate(null, Set.of("favorite_food"));

        assertThat(result).isEmpty();
    }

    @Test
    void droppedFactKeys가_빈_리스트면_빈_리스트를_반환한다() {
        List<String> result = FactDropValidator.validate(List.of(), Set.of("favorite_food"));

        assertThat(result).isEmpty();
    }

    @Test
    void isFullyValid는_모든_key가_후보에_있으면_true를_반환한다() {
        boolean valid = FactDropValidator.isFullyValid(
                List.of("favorite_food"), Set.of("favorite_food", "childhood_wound")
        );

        assertThat(valid).isTrue();
    }

    @Test
    void isFullyValid는_하나라도_후보밖이면_false를_반환한다() {
        boolean valid = FactDropValidator.isFullyValid(
                List.of("favorite_food", "not_injected"), Set.of("favorite_food")
        );

        assertThat(valid).isFalse();
    }

    @Test
    void isFullyValid는_droppedFactKeys가_null이면_true를_반환한다() {
        boolean valid = FactDropValidator.isFullyValid(null, Set.of("favorite_food"));

        assertThat(valid).isTrue();
    }
}
