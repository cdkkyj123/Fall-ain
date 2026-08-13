package com.sok.fallain.config;

import com.sok.fallain.common.exception.BusinessException;
import com.sok.fallain.common.exception.ErrorCode;
import com.sok.fallain.domain.player.Player;
import com.sok.fallain.domain.player.PlayerRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * X-Player-Id 헤더를 읽어 {@link CurrentPlayer}가 붙은 파라미터에 Player를 주입하는 리졸버
 * (ADR-003).
 *
 * - 헤더 없음 -> BusinessException(AUTH_MISSING_PLAYER_ID)(401)
 * - 헤더 있음 -> UUID 파싱 후 PlayerRepository.findByPlayerId로 조회. 있으면 그대로 사용,
 *   없으면 신규 Player(playerId=해당 UUID)를 생성해 저장 후 사용 (upsert).
 */
@Component
@RequiredArgsConstructor
public class CurrentPlayerArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String PLAYER_ID_HEADER = "X-Player-Id";

    private final PlayerRepository playerRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentPlayer.class)
                && Player.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String headerValue = request != null ? request.getHeader(PLAYER_ID_HEADER) : null;

        if (headerValue == null || headerValue.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_MISSING_PLAYER_ID);
        }

        UUID playerId = UUID.fromString(headerValue);

        return playerRepository.findByPlayerId(playerId)
                .orElseGet(() -> playerRepository.save(
                        Player.builder().playerId(playerId).build()
                ));
    }
}
