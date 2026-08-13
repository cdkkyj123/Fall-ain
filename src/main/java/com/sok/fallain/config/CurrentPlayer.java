package com.sok.fallain.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드 파라미터에 붙여 X-Player-Id 헤더 기반 현재 플레이어를 주입받기 위한 어노테이션
 * (ADR-003). {@link CurrentPlayerArgumentResolver}가 처리한다.
 *
 * 헤더가 없으면 BusinessException(ErrorCode.AUTH_MISSING_PLAYER_ID)(401)이 발생한다.
 * 헤더는 있으나 해당 UUID의 Player가 없으면 신규 Player를 upsert하여 사용한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentPlayer {
}
