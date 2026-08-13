package com.sok.fallain.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * MVC 관련 전역 설정. {@link CurrentPlayerArgumentResolver}를 등록해
 * {@link CurrentPlayer} 어노테이션이 붙은 컨트롤러 파라미터를 처리할 수 있게 한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentPlayerArgumentResolver currentPlayerArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentPlayerArgumentResolver);
    }
}
