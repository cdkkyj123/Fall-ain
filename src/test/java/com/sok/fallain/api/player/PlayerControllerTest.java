package com.sok.fallain.api.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sok.fallain.domain.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/players — 플레이어 부트스트랩(최초 진입) API 테스트.
 *
 * BLUEPRINT/PLANNER 태스크 분해에서 누락되었던 "T8.5" 진입점 API 중 하나(발견 경위는
 * RelationshipStartControllerTest 상단 주석 및 TEST 라운드 보고서 참조). 클라이언트가 최초 접속 시
 * X-Player-Id 없이 이 API를 호출해 새 playerId(UUID)를 발급받고, 이후 모든 요청에 X-Player-Id
 * 헤더로 이 값을 실어 보낸다 (ADR-003).
 *
 * 계약(이번 TEST 라운드에서 확정, 다음 BACKEND가 그대로 구현):
 * - 요청: X-Player-Id 헤더 불필요(있어도 무시 — 이 엔드포인트는 항상 새 Player를 발급한다).
 * - 응답 200 OK, 바디(래퍼 없이, ADR-009 관련 기존 결정과 동일 스타일):
 *   {@code {"playerId": "<uuid>"}}
 * - 서버는 UUID.randomUUID()로 신규 playerId를 생성하고 Player 엔티티를 저장한다.
 *
 * 컨트롤러/서비스(com.sok.fallain.api.player.PlayerController /
 * com.sok.fallain.api.player.PlayerService)가 아직 존재하지 않으므로 라우팅 미매핑으로 인한
 * 404(RED)가 정상이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 헤더_없이_호출하면_200과_UUID_형태의_playerId를_발급한다() throws Exception {
        long beforeCount = playerRepository.count();

        MvcResult result = mockMvc.perform(post("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").exists())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String playerId = body.get("playerId").asText();

        assertThatCode(() -> UUID.fromString(playerId))
                .as("playerId는 UUID 형식이어야 한다")
                .doesNotThrowAnyException();

        assertThat(playerRepository.count()).isEqualTo(beforeCount + 1);
        assertThat(playerRepository.findAll())
                .anyMatch(player -> player.getPlayerId().equals(UUID.fromString(playerId)));
    }

    @Test
    void 호출할_때마다_서로_다른_playerId가_발급된다() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/players"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/players"))
                .andExpect(status().isOk())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("playerId").asText();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("playerId").asText();

        assertThat(firstId).isNotEqualTo(secondId);
    }
}
