package com.sok.fallain.api.relationship;

import com.sok.fallain.api.relationship.dto.RelationshipStatusResponse;
import com.sok.fallain.config.CurrentPlayer;
import com.sok.fallain.domain.player.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관계 상태 조회 API.
 * 응답 형식: 래퍼 없이 리소스 그대로 반환 (ADR-009).
 */
@RestController
@RequestMapping("/api/relationships")
@RequiredArgsConstructor
public class RelationshipStatusController {

    private final RelationshipStatusService relationshipStatusService;

    /**
     * 사용자-캐릭터 관계 상태를 조회한다. 요청자(player)가 해당 ucId의 소유자가 아니면
     * 존재 자체를 숨기기 위해 RELATIONSHIP_NOT_FOUND(404)로 응답한다 (IDOR 방지).
     *
     * @param ucId 사용자-캐릭터 ID
     * @return 관계 상태 응답 (200 OK)
     * @throws com.sok.fallain.common.exception.BusinessException RELATIONSHIP_NOT_FOUND (404)
     */
    @GetMapping("/{ucId}")
    public ResponseEntity<RelationshipStatusResponse> getRelationshipStatus(@PathVariable Long ucId,
                                                                              @CurrentPlayer Player player) {
        RelationshipStatusResponse response = relationshipStatusService.getRelationshipStatus(ucId, player);
        return ResponseEntity.ok(response);
    }
}
