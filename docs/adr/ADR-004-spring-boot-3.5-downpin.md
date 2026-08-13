# ADR-004: Spring Boot 4.1.0 → 3.5.x 다운핀

## Status
Accepted

## Context
현재 리포지토리는 실제로 Spring Boot 4.1.0으로 초기화되어 있다(`build.gradle` 기준). 그러나 이 프로젝트는 Spring AI를 통해 Gemini LLM과 연동해야 하며(대화 생성, Canon 판정 신호 산출 등 핵심 기능), Spring AI 생태계의 안정성이 프로젝트 성패에 직결된다.

Spring AI의 안정 릴리스 라인은 현재 Spring Boot 3.x(Spring Framework 6 기준)에서 압도적으로 많이 검증되어 있다. Spring Boot 4.x는 상대적으로 최신 메이저 버전으로, Spring AI와의 호환성 검증 사례가 충분치 않다. 1주일짜리 프로토타입에서 프레임워크 호환성 이슈로 시간을 소모하는 것은 감당하기 어려운 리스크다.

## Decision
`build.gradle`의 Spring Boot 버전을 4.1.0에서 3.5.x 라인으로 다운핀한다. Java 버전은 17을 유지한다.

## Consequences

### 긍정적 영향
- Spring AI와의 호환성이 폭넓게 검증된 조합을 사용하여 통합 리스크 최소화
- 커뮤니티 자료, 스택오버플로우 등 트러블슈팅 참고 자료가 Boot 4.x 대비 훨씬 풍부함
- Spring Data JPA, Spring WebSocket(STOMP) 등 사용 예정 모듈들의 안정성이 검증됨

### 부정적 영향 / 트레이드오프
- Spring Boot 4.x의 최신 기능/개선사항을 활용하지 못함
- 향후 Boot 4.x로 재업그레이드 시 별도 마이그레이션 작업 필요
- 리포지토리 초기 상태(4.1.0)에서 다운그레이드 작업 자체가 추가 공수로 발생 (Phase 3b에서 BACKEND 에이전트가 처리)

### 재검토 조건
- Spring AI가 Spring Boot 4.x GA를 공식 지원한다고 확인되는 시점에 재평가 가능
