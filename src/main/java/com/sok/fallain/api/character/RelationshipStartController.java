package com.sok.fallain.api.character;

import com.sok.fallain.api.character.dto.RelationshipStartResponse;
import com.sok.fallain.config.CurrentPlayer;
import com.sok.fallain.domain.player.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관계 시작(부트스트랩) API.
 * 응답 형식: 래퍼 없이 리소스 그대로 반환 (ADR-009).
 */
@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class RelationshipStartController {

    private final RelationshipStartService relationshipStartService;

    /**
     * 플레이어-캐릭터 관계를 시작한다. 기존 관계가 있으면 그대로 재사용한다(멱등).
     *
     * @throws com.sok.fallain.common.exception.BusinessException
     *      AUTH_MISSING_PLAYER_ID(401) - X-Player-Id 헤더 없음 /
     *      ENTITY_NOT_FOUND(404) - characterId에 해당하는 캐릭터 없음
     */
    @PostMapping("/{characterId}/start")
    public ResponseEntity<RelationshipStartResponse> start(@PathVariable Long characterId,
                                                             @CurrentPlayer Player player) {
        RelationshipStartResponse response = relationshipStartService.start(characterId, player);
        return ResponseEntity.ok(response);
    }
}
