package com.sok.fallain.domain.character;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PersonaFact Canon 시드데이터 로딩 테스트.
 *
 * BLUEPRINT 섹션 2: "PersonaFact 시드데이터 — 최소 1개 캐릭터 + tier1~3 PersonaFact 5개 이상"을
 * resources/data.sql 방식으로 제공한다 (ADR-008: Flyway 미도입, ddl-auto 기반 스키마 관리).
 * local 프로파일은 ddl-auto=create-drop이므로 data.sql이 스키마 생성 직후 자동 로드되어야 한다.
 *
 * 현재 PersonaFact/CharacterRepository/PersonaFactRepository/data.sql이 존재하지 않으므로
 * 컴파일 실패 및 컨텍스트 기동 실패(RED)가 정상이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
class PersonaFactSeedDataTest {

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private PersonaFactRepository personaFactRepository;

    @Test
    void data_sql로_최소_1개_캐릭터에_5개_이상의_personaFact가_시드된다() {
        List<Character> characters = characterRepository.findAll();
        assertThat(characters).isNotEmpty();

        List<PersonaFact> allFacts = personaFactRepository.findAll();
        assertThat(allFacts.size()).isGreaterThanOrEqualTo(5);

        Map<Long, List<PersonaFact>> factsByCharacter = allFacts.stream()
                .collect(Collectors.groupingBy(fact -> fact.getCharacter().getId()));

        boolean hasCharacterWithAtLeast5Facts = factsByCharacter.values().stream()
                .anyMatch(facts -> facts.size() >= 5);
        assertThat(hasCharacterWithAtLeast5Facts).isTrue();
    }

    @Test
    void 시드된_personaFact는_tier_1_2_3을_모두_포함한다() {
        List<PersonaFact> allFacts = personaFactRepository.findAll();

        List<Integer> distinctTiers = allFacts.stream()
                .map(PersonaFact::getTier)
                .distinct()
                .sorted()
                .toList();

        assertThat(distinctTiers).contains(1, 2, 3);
    }
}
