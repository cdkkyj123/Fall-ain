package com.sok.fallain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * local 프로파일(H2 기반) 컨텍스트 로드 테스트.
 *
 * T1(build.gradle: H2/web/websocket/validation 추가, redis starter 제거) +
 * T2(application.yaml: local(H2)/prod(MySQL) 프로파일 분리)가 완료되어야
 * "local" 프로파일로 스프링 컨텍스트가 정상 기동된다.
 *
 * 현재는 T1/T2가 구현되지 않았으므로:
 *  - application.yaml에 "local" 프로파일 및 H2 데이터소스 설정이 없고
 *  - build.gradle에 H2 드라이버가 없다.
 * 따라서 컨텍스트 기동은 DataSource 관련 예외로 반드시 실패(RED)해야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
class LocalProfileContextLoadTest {

    @Test
    void local_프로파일로_H2_기반_컨텍스트가_정상_기동된다() {
        // 컨텍스트 로드 자체가 검증 대상.
        // T1~T3 구현 완료 전까지는 이 테스트가 반드시 실패해야 한다(RED).
    }
}
