package com.sok.fallain.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler 단위 테스트 (MockMvc standalone).
 *
 * 에러 응답 포맷 확정 스펙: HTTP 상태 = ErrorCode.status, 바디 = {"code": ErrorCode.code, "message": ErrorCode.message}.
 *
 * T3 스코프: com.sok.fallain.common.exception.GlobalExceptionHandler 신규 작성 예정.
 * 이 테스트는 GlobalExceptionHandler가 처리해야 하는 커스텀 예외(BusinessException, ErrorCode 보유)도
 * 함께 요구한다 — GlobalExceptionHandler가 ErrorCode 기반으로 응답을 만들려면 ErrorCode를 실어나르는
 * 예외 타입이 필요하기 때문이다. BusinessException은 PLANNER 산출물 T3 파일 목록에 명시되어 있지 않으므로
 * BACKEND 구현 시 GlobalExceptionHandler와 함께 추가 필요.
 *
 * 현재는 GlobalExceptionHandler/BusinessException이 존재하지 않고, spring-boot-starter-web도
 * 아직 추가되지 않았으므로(@RestController, MockMvc standaloneSetup 컴파일 불가) 컴파일 실패(RED)가 정상이다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

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
}
