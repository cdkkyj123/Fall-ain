package com.sok.fallain.domain.conversation;

import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.PersonaFact;
import com.sok.fallain.domain.character.PersonaFactCategory;
import com.sok.fallain.domain.relationship.UserCharacter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptBuilder 순수함수 단위테스트.
 *
 * BLUEPRINT 섹션 3~4(T9~T10): LLM 호출 전 요청(LlmTurnRequest)을 조립하는 순수함수.
 * character 정보, 현재 day, 노출가능 PersonaFact 후보 목록, 활성 MemoryCandidate 목록(되짚기 판정 대상),
 * 유저 메시지 텍스트를 받아 LlmTurnRequest로 매핑한다. Spring 컨텍스트 없이 순수 매핑만 검증한다.
 *
 * 계약: PromptBuilder.build(Long userCharacterId, Character character, Integer currentDay,
 *       List<PersonaFact> factCandidates, List<MemoryCandidate> activeMemoryCandidates,
 *       String userMessageText) -> LlmTurnRequest
 *
 * 현재 PromptBuilder/LlmTurnRequest가 존재하지 않으므로 컴파일 실패(RED)가 정상이다.
 */
class PromptBuilderTest {

    private Character character() {
        return Character.builder()
                .name("소울")
                .persona("따뜻하지만 상처를 숨기는 성격")
                .arcLengthDays(30)
                .build();
    }

    private PersonaFact fact(Character character, String key, int tier) {
        return PersonaFact.builder()
                .character(character)
                .factKey(key)
                .category(PersonaFactCategory.TASTE)
                .tier(tier)
                .content("내용-" + key)
                .unlockDayFrom(1)
                .unlockDayTo(30)
                .build();
    }

    private MemoryCandidate activeCandidate(String key, int droppedOnDay) {
        return MemoryCandidate.builder()
                .userCharacter(UserCharacter.builder().build())
                .factKey(key)
                .state(MemoryCandidateState.DROPPED)
                .droppedOnDay(droppedOnDay)
                .build();
    }

    @Test
    void character_day_fact후보_유저메시지를_LlmTurnRequest로_매핑한다() {
        Character character = character();
        PersonaFact fact1 = fact(character, "favorite_food", 1);
        PersonaFact fact2 = fact(character, "childhood_wound", 2);

        LlmTurnRequest request = PromptBuilder.build(
                42L, character, 7, List.of(fact1, fact2), List.of(), "오늘 하루 어땠어?"
        );

        assertThat(request.userCharacterId()).isEqualTo(42L);
        assertThat(request.characterName()).isEqualTo("소울");
        assertThat(request.characterPersona()).isEqualTo("따뜻하지만 상처를 숨기는 성격");
        assertThat(request.currentDay()).isEqualTo(7);
        assertThat(request.userMessageText()).isEqualTo("오늘 하루 어땠어?");
        assertThat(request.factCandidates()).hasSize(2);
        assertThat(request.factCandidates())
                .extracting(LlmTurnRequest.FactCandidate::factKey)
                .containsExactlyInAnyOrder("favorite_food", "childhood_wound");
        assertThat(request.factCandidates())
                .extracting(LlmTurnRequest.FactCandidate::content)
                .contains("내용-favorite_food", "내용-childhood_wound");
        assertThat(request.factCandidates())
                .extracting(LlmTurnRequest.FactCandidate::tier)
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void 활성_MemoryCandidate_목록이_되짚기_판정_대상으로_매핑된다() {
        Character character = character();

        LlmTurnRequest request = PromptBuilder.build(
                1L, character, 10, List.of(),
                List.of(activeCandidate("daily_habit", 8), activeCandidate("favorite_food", 5)),
                "그거 기억나?"
        );

        assertThat(request.activeMemoryCandidates()).hasSize(2);
        assertThat(request.activeMemoryCandidates())
                .extracting(LlmTurnRequest.ActiveCandidate::factKey)
                .containsExactlyInAnyOrder("daily_habit", "favorite_food");
        assertThat(request.activeMemoryCandidates())
                .extracting(LlmTurnRequest.ActiveCandidate::droppedOnDay)
                .containsExactlyInAnyOrder(8, 5);
    }

    @Test
    void fact후보와_활성candidate가_없어도_빈_리스트로_매핑된다() {
        Character character = character();

        LlmTurnRequest request = PromptBuilder.build(1L, character, 1, List.of(), List.of(), "안녕");

        assertThat(request.factCandidates()).isEmpty();
        assertThat(request.activeMemoryCandidates()).isEmpty();
    }
}
