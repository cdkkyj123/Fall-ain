package com.sok.fallain.api.character.dto;

public record CharacterListItemResponse(
        Long id,
        String name,
        String introduction
) {
}
