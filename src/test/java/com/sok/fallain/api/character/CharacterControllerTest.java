package com.sok.fallain.api.character;

import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.CharacterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/characters — 시드/등록된 캐릭터 목록 조회 API 테스트.
 *
 * BLUEPRINT/PLANNER 태스크 분해에서 누락되었던 "T8.5" 진입점 API 중 하나(발견 경위는
 * RelationshipStartControllerTest 상단 주석 참조). 프론트엔드가 캐릭터 선택 화면을 그리려면
 * 이 목록 API가 반드시 있어야 한다.
 *
 * 계약(이번 TEST 라운드에서 확정, 다음 BACKEND가 그대로 구현):
 * - 인증 불필요 (X-Player-Id 헤더 없이도 200) — 캐릭터 목록 자체는 플레이어 소유물이 아니므로.
 * - 응답 200 OK, 바디(래퍼 없이): 배열. 각 원소:
 *   {@code {"id": 1, "name": "소울", "introduction": "..."}}
 *   `introduction` 필드는 이번 라운드에서는 Character.persona 값을 그대로 사용한다(별도의
 *   요약/소개문구 컬럼을 신규로 추가하지 않는다 — 스키마 변경 최소화). 추후 소개문구를 페르소나와
 *   분리하고 싶다면 Character에 별도 컬럼 추가가 필요하며, 이는 이번 라운드 스코프 밖이다.
 * - data.sql 시드 캐릭터(테스트 실행 시점 기준 "Soul" 1개, PersonaFact 5개)에 의존하지 않는다 —
 *   기본 프로파일은 spring.sql.init.mode=never라 컨트롤러 테스트(@SpringBootTest, 로컬 프로파일
 *   미지정)에서는 data.sql이 로드되지 않는다(RelationshipStatusControllerTest와 동일한 이유로
 *   Character를 테스트 내에서 직접 persist한다). 시드 데이터 자체는 @ActiveProfiles("local")
 *   조합의 PersonaFactSeedDataTest에서 별도로 검증된다.
 *
 * 컨트롤러(com.sok.fallain.api.character.CharacterController)가 아직 존재하지 않으므로
 * 라우팅 미매핑으로 인한 404(RED)가 정상이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CharacterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CharacterRepository characterRepository;

    private Character persistCharacter(String name, String persona) {
        return characterRepository.saveAndFlush(
                Character.builder().name(name).persona(persona).arcLengthDays(30).build()
        );
    }

    @Test
    void 등록된_캐릭터_목록을_200과_함께_반환한다() throws Exception {
        Character character = persistCharacter("소울", "따뜻하지만 상처를 숨기는 성격");

        mockMvc.perform(get("/api/characters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(character.getId()))
                .andExpect(jsonPath("$[0].name").value("소울"))
                .andExpect(jsonPath("$[0].introduction").value("따뜻하지만 상처를 숨기는 성격"));
    }

    @Test
    void 등록된_캐릭터가_여러_개면_모두_반환한다() throws Exception {
        persistCharacter("소울", "따뜻하지만 상처를 숨기는 성격");
        persistCharacter("레이", "차갑지만 다정한 성격");

        mockMvc.perform(get("/api/characters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 헤더_없이도_200을_반환한다() throws Exception {
        persistCharacter("소울", "따뜻하지만 상처를 숨기는 성격");

        // X-Player-Id 헤더를 의도적으로 실지 않는다 — 캐릭터 목록은 인증 불필요.
        mockMvc.perform(get("/api/characters"))
                .andExpect(status().isOk());
    }
}
