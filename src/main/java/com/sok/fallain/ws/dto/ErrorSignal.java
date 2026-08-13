package com.sok.fallain.ws.dto;

/**
 * WebSocket 대화 채널에서 처리 중 에러가 발생했을 때 브로드캐스트하는 에러 프레임.
 * REST 에러 응답과 동일한 code/message 체계를 따른다 (docs/api/API.md 참조).
 */
public record ErrorSignal(String code, String message) {
}
