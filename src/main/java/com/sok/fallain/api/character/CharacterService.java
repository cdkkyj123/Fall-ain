package com.sok.fallain.api.character;

import com.sok.fallain.api.character.dto.CharacterListItemResponse;
import com.sok.fallain.domain.character.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 시드/등록된 캐릭터 목록 조회 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterService {

    private final CharacterRepository characterRepository;

    /**
     * 등록된 캐릭터 전체 목록을 조회한다.
     * introduction 필드는 Character.persona 값을 그대로 재사용한다(스키마 변경 최소화).
     */
    public List<CharacterListItemResponse> listCharacters() {
        return characterRepository.findAll().stream()
                .map(character -> new CharacterListItemResponse(
                        character.getId(),
                        character.getName(),
                        character.getPersona()
                ))
                .toList();
    }
}
