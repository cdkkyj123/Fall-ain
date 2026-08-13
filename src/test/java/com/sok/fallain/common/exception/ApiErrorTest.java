package com.sok.fallain.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiError 단위 테스트.
 *
 * 에러 응답 포맷은 확정 스펙: {"code": ..., "message": ...} (ADR 기준, ApiResponseDto 성공 포맷과는 별개로 확정됨).
 * T3 스코프: com.sok.fallain.common.exception.ApiError 신규 작성 예정.
 * 현재는 클래스가 존재하지 않으므로 컴파일 실패(RED)가 정상이다.
 */
class ApiErrorTest {

    @Test
    void of_ErrorCode로부터_code와_message를_그대로_매핑한다() {
        ApiError apiError = ApiError.of(ErrorCode.ENTITY_NOT_FOUND);

        assertThat(apiError.getCode()).isEqualTo(ErrorCode.ENTITY_NOT_FOUND.getCode());
        assertThat(apiError.getMessage()).isEqualTo(ErrorCode.ENTITY_NOT_FOUND.getMessage());
    }

    @Test
    void 직렬화시_code_message_두_필드만_존재한다() throws Exception {
        ApiError apiError = ApiError.of(ErrorCode.INVALID_INPUT_VALUE);
        ObjectMapper objectMapper = new ObjectMapper();

        String json = objectMapper.writeValueAsString(apiError);

        assertThat(json).contains("\"code\"");
        assertThat(json).contains("\"message\"");

        @SuppressWarnings("unchecked")
        var asMap = objectMapper.readValue(json, java.util.Map.class);
        assertThat(asMap).containsOnlyKeys("code", "message");
    }
}
