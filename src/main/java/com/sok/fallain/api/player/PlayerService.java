package com.sok.fallain.api.player;

import com.sok.fallain.api.player.dto.PlayerBootstrapResponse;
import com.sok.fallain.domain.player.Player;
import com.sok.fallain.domain.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 플레이어 부트스트랩(최초 진입) 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlayerService {

    private final PlayerRepository playerRepository;

    /**
     * 항상 신규 UUID로 Player를 생성한다.
     *
     * @return 신규 발급된 playerId를 담은 응답 DTO
     */
    public PlayerBootstrapResponse bootstrap() {
        Player player = playerRepository.save(
                Player.builder().playerId(UUID.randomUUID()).build()
        );
        return new PlayerBootstrapResponse(player.getPlayerId());
    }
}
