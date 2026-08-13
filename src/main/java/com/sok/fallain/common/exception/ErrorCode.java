package com.sok.fallain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    ENTITY_NOT_FOUND("E001", "Entity not found", HttpStatus.NOT_FOUND),
    INVALID_INPUT_VALUE("E002", "Invalid input value", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("E500", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
