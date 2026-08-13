package com.sok.fallain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    ENTITY_NOT_FOUND("ENTITY_NOT_FOUND", "Entity not found", HttpStatus.NOT_FOUND),
    INVALID_INPUT_VALUE("INVALID_INPUT_VALUE", "Invalid input value", HttpStatus.BAD_REQUEST),
    RELATIONSHIP_NOT_FOUND("RELATIONSHIP_NOT_FOUND", "Relationship not found", HttpStatus.NOT_FOUND),
    TURN_BUDGET_EXCEEDED("TURN_BUDGET_EXCEEDED", "오늘의 대화 턴을 모두 사용했습니다.", HttpStatus.CONFLICT),
    TURN_IN_PROGRESS("TURN_IN_PROGRESS", "이미 처리 중인 턴이 있어 신규 메시지를 받을 수 없습니다.", HttpStatus.CONFLICT),
    LLM_UNAVAILABLE("LLM_UNAVAILABLE", "LLM 호출에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    DAY_CLOSED("DAY_CLOSED", "이미 종료된 하루입니다.", HttpStatus.CONFLICT),
    ARC_ENDED("ARC_ENDED", "관계 아크가 이미 종료되었습니다.", HttpStatus.CONFLICT),
    AUTH_MISSING_PLAYER_ID("AUTH_MISSING_PLAYER_ID", "X-Player-Id 헤더가 없습니다.", HttpStatus.UNAUTHORIZED),
    VALIDATION_ERROR("VALIDATION_ERROR", "요청 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    CONCURRENT_MODIFICATION("CONCURRENT_MODIFICATION", "다른 요청과 값이 충돌했어요. 다시 시도해주세요.", HttpStatus.CONFLICT),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
