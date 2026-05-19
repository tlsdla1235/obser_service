# BMAD Restart Context

This project has selected the MVC version as the active implementation baseline.

## Source Material Policy

- Active implementation artifacts live in `planning-artifacts/` and `implementation-artifacts/`.
- Legacy Lightweight Hexagonal artifacts are preserved under `archive/hexagonal-version/`.
- Existing restart context in `bmad-restart-context-pack/` may be used only for the product problem, observable workflows, and UX intent.
- Legacy technical structure, framework choices, layering decisions, module boundaries, and integration decisions are historical context only.

## Architecture Policy

- The selected architecture is **Traditional MVC + Service/Repository Layering**.
- Portal source packages use **feature-first MVC** under `com.observation.portal.domain`.
- In this project, `domain` means a business feature grouping namespace, not a pure DDD domain layer.
- observability-portal repository 구현 표준은 PostgreSQL 위의 **Spring Data JPA / Jakarta Persistence + Hibernate**다.
- Flyway SQL migration이 schema source of truth다. Hibernate DDL auto create/update는 사용하지 않는다.
- Service layer는 빠른 MVC 구현을 위해 필요하면 Spring Data JPA repository와 JPA entity를 직접 사용할 수 있다.
- JPA entity는 persistence model이다. controller response DTO, public API surface, service result/external return model로 직접 반환하지 않는다.
- JPA entity와 Spring Data repository 위치는 feature-first package 안에서 실제 코드와 story 기준에 맞춰 둔다. `domain.<feature>.repository.entity` 또는 `domain.<feature>.repository.jpa`를 필수 구조로 요구하지 않는다.
- Controller는 repository를 직접 호출하지 않고 service를 호출한다.
- Repository/JPA 구현은 controller/dto에 의존하지 않는다.
- raw project key 같은 secret은 DB row, migration, log, exception, response body, repository lookup surface에 남기지 않는다.
- Repository integration test는 PostgreSQL Testcontainers 기반을 유지하며 Flyway migration과 repository behavior를 함께 검증한다.
- Account signup/login 정책은 GitHub OAuth only다.
- MVP는 email/password signup, local account registration, local password, password reset, email verification required for signup, magic link, Google/Kakao/Naver OAuth, anonymous user flow를 지원하지 않는다.
- 사용자 API 인증은 cookie 기반 server session이 아니라 `Authorization: Bearer <access_token>` header, 짧은 만료 JWT access token, rotation/revoke/reuse detection을 갖춘 refresh token 기준으로 둔다.
- Refresh Token 저장소는 Redis로 고정하지 않고 token store 추상 기준으로 둔다. 초기 후보는 RDBMS hashed refresh token 또는 token family metadata다.
- GitHub OAuth token과 우리 서비스 access/refresh token을 구분한다. Provider token/raw payload/secret은 response/log/error에 노출하지 않는다.
- Do not reopen the Simple MVC vs Lightweight Hexagonal choice during story implementation.
- Do not blend MVC and Hexagonal package boundaries.
- Do not recreate `application`, `port`, or `adapter` packages.
- Do not present a hybrid architecture as the final recommendation.
- Implementation stories must follow the selected MVC style consistently.

## Current Context Pack

Use `bmad-restart-context-pack/` to understand the product problem and UX intent only.
Use `archive/hexagonal-version/` only when historical comparison is explicitly needed.
