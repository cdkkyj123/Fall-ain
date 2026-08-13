package com.sok.fallain.api.character;

import com.sok.fallain.api.character.dto.CharacterListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 시드/등록된 캐릭터 목록 조회 API.
 * 응답 형식: 래퍼 없이 배열 그대로 반환 (ADR-009). 인증 불필요.
 */
@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @GetMapping
    public ResponseEntity<List<CharacterListItemResponse>> listCharacters() {
        return ResponseEntity.ok(characterService.listCharacters());
    }
}
