package com.sok.fallain.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler 단위 테스트 (MockMvc standalone).
 *
 * 에러 응답 포맷 확정 스펙: HTTP 상태 = ErrorCode.status, 바디 = {"code": ErrorCode.code, "message": ErrorCode.message}.
 *
 * Phase 4 REVIEW/SECURITY 발견사항(HIGH: 입력값 검증 부재) 대응: {@code @Valid} 바인딩 실패
 * (MethodArgumentNotValidException)를 400 + VALIDATION_ERROR로, 낙관적락 충돌
 * (ObjectOptimisticLockingFailureException)을 409 + CONCURRENT_MODIFICATION으로 매핑하는지
 * 검증한다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    record TestRequest(@NotBlank String content) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business-exception")
        public void throwBusinessException() {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }

        @GetMapping("/test/unknown-exception")
        public void throwUnknownException() {
            throw new RuntimeException("boom");
        }

        @PostMapping("/test/validated")
        public void validated(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/optimistic-lock")
        public void throwOptimisticLockException() {
            throw new ObjectOptimisticLockingFailureException("UserCharacter", 1L);
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 커스텀_예외_발생시_ErrorCode에_매핑된_상태코드와_바디를_반환한다() throws Exception {
        mockMvc.perform(get("/test/business-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ENTITY_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ENTITY_NOT_FOUND.getMessage()));
    }

    @Test
    void 처리되지_않은_예외_발생시_500과_INTERNAL_SERVER_ERROR_바디를_반환한다() throws Exception {
        mockMvc.perform(get("/test/unknown-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_SERVER_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    @Test
    void Valid_검증_실패시_400과_VALIDATION_ERROR_바디를_반환한다() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.VALIDATION_ERROR.getMessage()));
    }

    @Test
    void 낙관적락_충돌시_409와_CONCURRENT_MODIFICATION_바디를_반환한다() throws Exception {
        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONCURRENT_MODIFICATION.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.CONCURRENT_MODIFICATION.getMessage()));
    }
}
