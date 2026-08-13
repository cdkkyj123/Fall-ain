package com.sok.fallain.domain.character;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PersonaFact 엔티티 / PersonaFactRepository 저장·조회 테스트.
 *
 * BLUEPRINT 섹션 2 DB스키마: domain.character.PersonaFact — Character N:1, factKey(캐릭터내 unique),
 * category(enum TASTE/WOUND/HABIT/VALUE/SECRET), tier(1~3), content, unlockDayFrom/unlockDayTo.
 * 읽기전용 Canon 시드 데이터.
 *
 * 현재 PersonaFact/PersonaFactRepository/PersonaFactCategory가 존재하지 않으므로
 * 컴파일 실패(RED)가 정상이다.
 */
@DataJpaTest
class PersonaFactRepositoryTest {

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private PersonaFactRepository personaFactRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private Character persistCharacter() {
        Character character = Character.builder()
                .name("소울")
                .persona("따뜻하지만 상처를 숨기는 성격")
                .arcLengthDays(30)
                .build();
        return characterRepository.saveAndFlush(character);
    }

    @Test
    void personaFact를_저장하고_ID로_조회할_수_있다() {
        Character character = persistCharacter();

        PersonaFact fact = PersonaFact.builder()
                .character(character)
                .factKey("favorite_food")
                .category(PersonaFactCategory.TASTE)
                .tier(1)
                .content("사실 나 민트초코 싫어해")
                .unlockDayFrom(1)
                .unlockDayTo(10)
                .build();

        PersonaFact saved = personaFactRepository.saveAndFlush(fact);
        testEntityManager.clear();

        Optional<PersonaFact> found = personaFactRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getFactKey()).isEqualTo("favorite_food");
        assertThat(found.get().getCategory()).isEqualTo(PersonaFactCategory.TASTE);
        assertThat(found.get().getTier()).isEqualTo(1);
        assertThat(found.get().getContent()).isEqualTo("사실 나 민트초코 싫어해");
        assertThat(found.get().getUnlockDayFrom()).isEqualTo(1);
        assertThat(found.get().getUnlockDayTo()).isEqualTo(10);
        assertThat(found.get().getCharacter().getId()).isEqualTo(character.getId());
    }

    @Test
    void tier는_1에서_3사이_값을_가질_수_있다() {
        Character character = persistCharacter();

        PersonaFact tier3Fact = PersonaFact.builder()
                .character(character)
                .factKey("deepest_secret")
                .category(PersonaFactCategory.SECRET)
                .tier(3)
                .content("아무에게도 말 못한 비밀")
                .unlockDayFrom(20)
                .unlockDayTo(30)
                .build();

        PersonaFact saved = personaFactRepository.saveAndFlush(tier3Fact);

        assertThat(saved.getTier()).isEqualTo(3);
    }

    @Test
    void 같은_캐릭터에서_factKey는_중복될_수_없다() {
        Character character = persistCharacter();

        PersonaFact first = PersonaFact.builder()
                .character(character)
                .factKey("favorite_food")
                .category(PersonaFactCategory.TASTE)
                .tier(1)
                .content("사실 나 민트초코 싫어해")
                .unlockDayFrom(1)
                .unlockDayTo(10)
                .build();
        personaFactRepository.saveAndFlush(first);

        PersonaFact duplicate = PersonaFact.builder()
                .character(character)
                .factKey("favorite_food")
                .category(PersonaFactCategory.HABIT)
                .tier(2)
                .content("다른 내용")
                .unlockDayFrom(5)
                .unlockDayTo(15)
                .build();

        assertThatThrownBy(() -> personaFactRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_캐릭터라면_같은_factKey를_가질_수_있다() {
        Character characterA = persistCharacter();
        Character characterB = characterRepository.saveAndFlush(
                Character.builder().name("리아").persona("밝고 솔직한 성격").arcLengthDays(30).build()
        );

        PersonaFact factA = personaFactRepository.saveAndFlush(
                PersonaFact.builder()
                        .character(characterA)
                        .factKey("favorite_food")
                        .category(PersonaFactCategory.TASTE)
                        .tier(1)
                        .content("A의 취향")
                        .unlockDayFrom(1)
                        .unlockDayTo(10)
                        .build()
        );

        PersonaFact factB = personaFactRepository.saveAndFlush(
                PersonaFact.builder()
                        .character(characterB)
                        .factKey("favorite_food")
                        .category(PersonaFactCategory.TASTE)
                        .tier(1)
                        .content("B의 취향")
                        .unlockDayFrom(1)
                        .unlockDayTo(10)
                        .build()
        );

        assertThat(factA.getId()).isNotEqualTo(factB.getId());
    }
}
