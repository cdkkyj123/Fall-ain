package com.sok.fallain.common.exception;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApiError {

    private String code;
    private String message;

    private ApiError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ApiError of(ErrorCode errorCode) {
        return new ApiError(errorCode.getCode(), errorCode.getMessage());
    }
}
