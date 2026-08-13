package com.sok.fallain.domain.character;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Character 엔티티 / CharacterRepository 저장·조회 테스트.
 *
 * BLUEPRINT 섹션 2 DB스키마: domain.character.Character — name, persona, arcLengthDays(기본 30).
 *
 * 현재 com.sok.fallain.domain.character 패키지에 Character/CharacterRepository가 존재하지 않으므로
 * 컴파일 실패(RED)가 정상이다.
 */
@DataJpaTest
class CharacterRepositoryTest {

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void character를_저장하고_ID로_조회할_수_있다() {
        Character character = Character.builder()
                .name("소울")
                .persona("따뜻하지만 상처를 숨기는 성격")
                .arcLengthDays(30)
                .build();

        Character saved = characterRepository.saveAndFlush(character);
        testEntityManager.clear();

        Optional<Character> found = characterRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("소울");
        assertThat(found.get().getPersona()).isEqualTo("따뜻하지만 상처를 숨기는 성격");
        assertThat(found.get().getArcLengthDays()).isEqualTo(30);
    }

    @Test
    void arcLengthDays를_지정하지_않으면_기본값_30이_적용된다() {
        Character character = Character.builder()
                .name("소울")
                .persona("따뜻하지만 상처를 숨기는 성격")
                .build();

        Character saved = characterRepository.saveAndFlush(character);

        assertThat(saved.getArcLengthDays()).isEqualTo(30);
    }
}
