package com.sok.fallain.api.player;

import com.sok.fallain.api.player.dto.PlayerBootstrapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플레이어 부트스트랩(최초 진입) API.
 * 응답 형식: 래퍼 없이 리소스 그대로 반환 (ADR-009).
 */
@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    /**
     * 항상 신규 playerId(UUID)를 발급하고 Player 엔티티를 저장한다.
     * X-Player-Id 헤더는 요구하지 않으며, 실려 있어도 무시한다.
     *
     * @return 신규 playerId를 담은 응답 (200 OK)
     */
    @PostMapping
    public ResponseEntity<PlayerBootstrapResponse> bootstrap() {
        PlayerBootstrapResponse response = playerService.bootstrap();
        return ResponseEntity.ok(response);
    }
}
