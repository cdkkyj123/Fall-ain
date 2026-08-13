package com.sok.fallain.api.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sok.fallain.common.exception.ErrorCode;
import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.CharacterRepository;
import com.sok.fallain.domain.player.Player;
import com.sok.fallain.domain.player.PlayerRepository;
import com.sok.fallain.domain.relationship.DayState;
import com.sok.fallain.domain.relationship.RelationshipStatus;
import com.sok.fallain.domain.relationship.UserCharacter;
import com.sok.fallain.domain.relationship.UserCharacterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/characters/{characterId}/start — 관계 시작(부트스트랩) API 테스트.
 *
 * ## 발견된 공백 (T8.5)
 * BLUEPRINT/PLANNER 태스크 분해 과정에서 X-Player-Id 인증 처리, 캐릭터 목록 조회, 관계 시작
 * API 자체가 통째로 누락되었다. Player/PlayerRepository, Character/PersonaFact/UserCharacter/
 * Message/MemoryCandidate 엔티티·Repository는 모두 존재하지만, 이를 "처음 진입"시키는 API가
 * 전혀 없어 프론트엔드가 세션을 시작할 방법이 없었다. 이번 TEST 라운드에서 이 공백을 메우는
 * 계약을 RED 테스트로 명시한다 (PlayerControllerTest, CharacterControllerTest와 함께 3종 세트).
 *
 * ## 계약 (다음 BACKEND가 그대로 구현)
 *
 * ### X-Player-Id 처리 방식
 * `@CurrentPlayer Player player` 커스텀 어노테이션 + HandlerMethodArgumentResolver 방식을
 * 권장한다 (필터/인터셉터도 가능하나, 엔드포인트별로 인증 요구 여부가 다르므로 — 예:
 * GET /api/characters는 인증 불필요, POST .../start는 필수 — 파라미터 단위로 선택 적용 가능한
 * ArgumentResolver가 더 유연하다):
 * - 헤더 없음 -> BusinessException(ErrorCode.AUTH_MISSING_PLAYER_ID) (401). 이 ErrorCode는
 *   아직 enum에 없으므로 신규 추가 필요 (ADR-003 / docs/api/API.md에는 이미 코드명이 명시돼 있음).
 * - 헤더 있음 -> UUID 파싱 후 PlayerRepository.findByPlayerId(UUID)로 조회. 있으면 그대로 사용,
 *   없으면 즉시 새 Player(playerId=해당 UUID)를 생성해 저장 후 사용 (upsert). PlayerRepository에
 *   findByPlayerId 신규 메서드 추가 필요.
 *
 * ### 관계 시작 로직
 * - characterId로 Character 조회, 없으면 404(코드는 BACKEND 판단 — 기존 ENTITY_NOT_FOUND 재사용
 *   또는 CHARACTER_NOT_FOUND 신규 추가 중 택1. 이번 라운드 RED 테스트는 이 케이스를 다루지 않는다).
 * - player+character 조합으로 기존 UserCharacter가 있으면 그 ucId를 그대로 반환 (재진입, 중복
 *   생성 금지). UserCharacterRepository에 findByPlayerAndCharacter(Player, Character) 신규
 *   메서드 추가 필요 (unique constraint(player_id, character_id)와 일치).
 * - 없으면 신규 생성: intimacy=0, currentDay=1, turnsUsedToday=0, pendingTurn=false,
 *   dayState=IN_PROGRESS, lastTouchedDay=1, status=ONGOING.
 * - 응답 200 OK, 바디(래퍼 없이): {@code {"ucId": 1}} — 최소 스코프로 ucId만 반환한다(전체 상태
 *   요약은 필요 시 클라이언트가 뒤이어 GET /api/relationships/{ucId}로 조회하면 되므로 중복 응답
 *   설계를 피한다).
 *
 * ## 기존 엔드포인트 소급 적용 여부 (다음 BACKEND 판단 필요)
 * GET /api/relationships/{ucId}, POST /api/relationships/{ucId}/message,
 * POST /api/relationships/{ucId}/end-day는 X-Player-Id 헤더를 테스트에서 보내고는 있지만
 * 컨트롤러/서비스 어디서도 검증·사용하지 않는다 (grep 확인 완료 — 헤더 값을 읽는 코드 없음).
 * 즉 ucId만 알면 누구든 다른 플레이어의 관계를 조회/조작할 수 있는 상태다. 이번 라운드는 신규
 * 엔드포인트에만 @CurrentPlayer를 적용하는 최소 스코프로 남겨두고, 기존 3개 엔드포인트에
 * 소유자 검증(player == userCharacter.getPlayer())을 소급 적용할지는 다음 BACKEND가 판단한다 —
 * 프로토타입 범위(ADR-003)라 당장 필수는 아니지만 방치하면 접근 통제가 사실상 없는 상태로 남는다.
 *
 * 컨트롤러(com.sok.fallain.api.character.CharacterController 또는 별도 클래스)가 아직 존재하지
 * 않고 ErrorCode.AUTH_MISSING_PLAYER_ID도 없으므로 컴파일 실패(RED)가 정상이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RelationshipStartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private UserCharacterRepository userCharacterRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Player persistPlayer() {
        return playerRepository.saveAndFlush(
                Player.builder().playerId(UUID.randomUUID()).nickname("플레이어").build()
        );
    }

    private Character persistCharacter() {
        return characterRepository.saveAndFlush(
                Character.builder().name("소울").persona("따뜻하지만 상처를 숨기는 성격").arcLengthDays(30).build()
        );
    }

    @Test
    void 최초_호출이면_200과_새_ucId를_반환하고_초기값으로_UserCharacter를_생성한다() throws Exception {
        Player player = persistPlayer();
        Character character = persistCharacter();

        MvcResult result = mockMvc.perform(post("/api/characters/{characterId}/start", character.getId())
                        .header("X-Player-Id", player.getPlayerId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ucId").exists())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Long ucId = body.get("ucId").asLong();

        UserCharacter created = userCharacterRepository.findById(ucId).orElseThrow();
        assertThat(created.getPlayer().getId()).isEqualTo(player.getId());
        assertThat(created.getCharacter().getId()).isEqualTo(character.getId());
        assertThat(created.getIntimacy()).isEqualTo(0);
        assertThat(created.getCurrentDay()).isEqualTo(1);
        assertThat(created.getTurnsUsedToday()).isEqualTo(0);
        assertThat(created.getPendingTurn()).isFalse();
        assertThat(created.getDayState()).isEqualTo(DayState.IN_PROGRESS);
        assertThat(created.getLastTouchedDay()).isEqualTo(1);
        assertThat(created.getStatus()).isEqualTo(RelationshipStatus.ONGOING);
    }

    @Test
    void 같은_player_character_조합으로_재호출하면_같은_ucId를_반환하고_중복_생성하지_않는다() throws Exception {
        Player player = persistPlayer();
        Character character = persistCharacter();

        MvcResult firstResult = mockMvc.perform(post("/api/characters/{characterId}/start", character.getId())
                        .header("X-Player-Id", player.getPlayerId().toString()))
                .andExpect(status().isOk())
                .andReturn();
        Long firstUcId = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                .get("ucId").asLong();

        MvcResult secondResult = mockMvc.perform(post("/api/characters/{characterId}/start", character.getId())
                        .header("X-Player-Id", player.getPlayerId().toString()))
                .andExpect(status().isOk())
                .andReturn();
        Long secondUcId = objectMapper.readTree(secondResult.getResponse().getContentAsString())
                .get("ucId").asLong();

        assertThat(secondUcId).isEqualTo(firstUcId);

        List<UserCharacter> all = userCharacterRepository.findAll();
        long matchingCount = all.stream()
                .filter(uc -> uc.getPlayer().getId().equals(player.getId())
                        && uc.getCharacter().getId().equals(character.getId()))
                .count();
        assertThat(matchingCount).isEqualTo(1);
    }

    @Test
    void X_Player_Id_헤더가_없으면_401과_AUTH_MISSING_PLAYER_ID를_반환한다() throws Exception {
        Character character = persistCharacter();

        mockMvc.perform(post("/api/characters/{characterId}/start", character.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_MISSING_PLAYER_ID.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.AUTH_MISSING_PLAYER_ID.getMessage()));
    }
}
