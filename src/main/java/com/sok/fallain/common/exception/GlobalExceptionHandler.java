package com.sok.fallain.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        ApiError apiError = ApiError.of(errorCode);
        return ResponseEntity.status(errorCode.getStatus()).body(apiError);
    }

    /**
     * {@code @Valid} 검증 실패(예: TurnMessageRequest.content 공백/길이 초과)를 400 +
     * VALIDATION_ERROR로 매핑한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException e) {
        ApiError apiError = ApiError.of(ErrorCode.VALIDATION_ERROR);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus()).body(apiError);
    }

    /**
     * 낙관적락(@Version) 충돌을 409 + CONCURRENT_MODIFICATION으로 매핑한다.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLockException(ObjectOptimisticLockingFailureException e) {
        ApiError apiError = ApiError.of(ErrorCode.CONCURRENT_MODIFICATION);
        return ResponseEntity.status(ErrorCode.CONCURRENT_MODIFICATION.getStatus()).body(apiError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(Exception e) {
        log.error("Unexpected exception occurred", e);
        ApiError apiError = ApiError.of(ErrorCode.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus()).body(apiError);
    }
}
