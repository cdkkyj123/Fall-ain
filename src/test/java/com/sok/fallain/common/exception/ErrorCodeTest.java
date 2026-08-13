package com.sok.fallain.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ErrorCode 단위 테스트.
 *
 * T3 스코프: com.sok.fallain.common.exception.ErrorCode 신규 작성 예정.
 * 현재는 클래스가 존재하지 않으므로 컴파일 실패(RED)가 정상이다.
 */
class ErrorCodeTest {

    @Test
    void entityNotFound_매핑된_상태코드와_코드값을_가진다() {
        ErrorCode errorCode = ErrorCode.ENTITY_NOT_FOUND;

        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errorCode.getCode()).isNotBlank();
        assertThat(errorCode.getMessage()).isNotBlank();
    }

    @Test
    void invalidInputValue_매핑된_상태코드와_코드값을_가진다() {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCode.getCode()).isNotBlank();
        assertThat(errorCode.getMessage()).isNotBlank();
    }

    @Test
    void internalServerError_매핑된_상태코드와_코드값을_가진다() {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        assertThat(errorCode.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(errorCode.getCode()).isNotBlank();
        assertThat(errorCode.getMessage()).isNotBlank();
    }

    @Test
    void 모든_ErrorCode는_코드값이_서로_중복되지_않는다() {
        ErrorCode[] values = ErrorCode.values();

        long distinctCodeCount = java.util.Arrays.stream(values)
                .map(ErrorCode::getCode)
                .distinct()
                .count();

        assertThat(distinctCodeCount).isEqualTo(values.length);
    }
}
