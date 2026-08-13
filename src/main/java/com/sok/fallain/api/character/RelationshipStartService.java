package com.sok.fallain.api.character;

import com.sok.fallain.api.character.dto.RelationshipStartResponse;
import com.sok.fallain.common.exception.BusinessException;
import com.sok.fallain.common.exception.ErrorCode;
import com.sok.fallain.domain.character.Character;
import com.sok.fallain.domain.character.CharacterRepository;
import com.sok.fallain.domain.player.Player;
import com.sok.fallain.domain.relationship.DayState;
import com.sok.fallain.domain.relationship.RelationshipStatus;
import com.sok.fallain.domain.relationship.UserCharacter;
import com.sok.fallain.domain.relationship.UserCharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관계 시작(부트스트랩) 서비스.
 *
 * player+character 조합으로 기존 UserCharacter가 있으면 그 ucId를 재사용하고(멱등), 없으면
 * 초기값으로 신규 생성한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RelationshipStartService {

    private final CharacterRepository characterRepository;
    private final UserCharacterRepository userCharacterRepository;

    /**
     * @throws BusinessException ENTITY_NOT_FOUND (404) - characterId에 해당하는 Character가 없을 때
     */
    public RelationshipStartResponse start(Long characterId, Player player) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        UserCharacter userCharacter = userCharacterRepository.findByPlayerAndCharacter(player, character)
                .orElseGet(() -> userCharacterRepository.save(newUserCharacter(player, character)));

        return new RelationshipStartResponse(userCharacter.getId());
    }

    private UserCharacter newUserCharacter(Player player, Character character) {
        return UserCharacter.builder()
                .player(player)
                .character(character)
                .intimacy(0)
                .currentDay(1)
                .turnsUsedToday(0)
                .pendingTurn(false)
                .dayState(DayState.IN_PROGRESS)
                .lastTouchedDay(1)
                .status(RelationshipStatus.ONGOING)
                .build();
    }
}
