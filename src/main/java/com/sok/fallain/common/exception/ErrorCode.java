package com.sok.fallain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    ENTITY_NOT_FOUND("E001", "Entity not found", HttpStatus.NOT_FOUND),
    INVALID_INPUT_VALUE("E002", "Invalid input value", HttpStatus.BAD_REQUEST),
    RELATIONSHIP_NOT_FOUND("E003", "Relationship not found", HttpStatus.NOT_FOUND),
    TURN_BUDGET_EXCEEDED("TURN_BUDGET_EXCEEDED", "오늘의 대화 턴을 모두 사용했습니다.", HttpStatus.CONFLICT),
    TURN_IN_PROGRESS("TURN_IN_PROGRESS", "이미 처리 중인 턴이 있어 신규 메시지를 받을 수 없습니다.", HttpStatus.CONFLICT),
    LLM_UNAVAILABLE("LLM_UNAVAILABLE", "LLM 호출에 실패했습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    DAY_CLOSED("DAY_CLOSED", "이미 종료된 하루입니다.", HttpStatus.CONFLICT),
    ARC_ENDED("ARC_ENDED", "관계 아크가 이미 종료되었습니다.", HttpStatus.CONFLICT),
    INTERNAL_SERVER_ERROR("E500", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
