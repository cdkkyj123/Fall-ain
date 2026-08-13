package com.sok.fallain.domain.conversation;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * LLM이 반환한 droppedFactKeys에 대한 화이트리스트 검증 순수함수.
 * 그 턴에 실제로 주입한 PersonaFact 후보 집합(injectedCandidateKeys)에 없는 key는 허용하지 않는다.
 */
public final class FactDropValidator {

    private FactDropValidator() {
    }

    /**
     * 후보 집합에 포함된 key만 남긴다. droppedFactKeys가 null/empty면 빈 리스트를 반환한다.
     */
    public static List<String> validate(List<String> droppedFactKeys, Set<String> injectedCandidateKeys) {
        if (droppedFactKeys == null || droppedFactKeys.isEmpty()) {
            return Collections.emptyList();
        }
        return droppedFactKeys.stream()
                .filter(injectedCandidateKeys::contains)
                .toList();
    }

    /**
     * 위반이 하나라도 있으면 false. droppedFactKeys가 null이면 true(위반 없음으로 간주).
     */
    public static boolean isFullyValid(List<String> droppedFactKeys, Set<String> injectedCandidateKeys) {
        if (droppedFactKeys == null) {
            return true;
        }
        return droppedFactKeys.stream().allMatch(injectedCandidateKeys::contains);
    }
}
