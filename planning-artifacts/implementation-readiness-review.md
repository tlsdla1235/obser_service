---
artifactType: implementation-readiness-review
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: feature-first-mvc-aligned
date: 2026-05-10
scope: active-mvc-artifacts
---

# Implementation Readiness Review - MVC Version

## 1. 검토 범위

이 검토는 active `planning-artifacts/`와 `implementation-artifacts/` 산출물만 기준으로 수행했다.

`archive/hexagonal-version/`과 `bmad-restart-context-pack/`는 제품 문제와 UX 의도 참고 경계로만 보며, 이번 IR 결과의 구현 기준이 아니다.

최종 아키텍처 선택은 **Traditional MVC + Service/Repository Layering** 하나로 고정한다.
Portal package 배치는 feature-first MVC이며, `domain`은 순수 DDD domain layer가 아니라 업무 기능 묶음 namespace다.

## 2. IR 결론

판정: **Pass**. Story 1.2는 문서 보강 후 바로 구현 가능한 상태다.

다만 기존 산출물에는 구현 시작 직전에 흔들릴 수 있는 결정이 남아 있었다. 이번 보강으로 아래 사항을 확정한다.

- build system은 Gradle Groovy DSL을 권장 기본값으로 둔다.
- root build는 Story 1.2에서 만들되, 구현 module은 `observability-portal` 하나만 생성한다.
- `observability-spring-boot-starter`는 목표 module 구조에 포함하지만 Story 1.2에서는 생성하지 않는다.
- Gradle group은 `com.sst`로 두고, portal Java package는 `com.observation.portal`로 고정한다.
- 빈 Java package는 `package-info.java` marker로 추적 가능하게 만든다.
- Story 1.2의 테스트는 기능 테스트가 아니라 portal module build/test wiring smoke test로 제한한다.
- Story 1.3의 architecture guard는 `domain.<feature>` 아래의 `controller`, `service`, `repository`, `dto` 의존 방향을 먼저 고정한다.
- Story 1.4의 PostgreSQL/Flyway/Testcontainers 결정은 Story 1.2가 아니라 Story 1.4에서 시작한다.

## 3. 발견 사항과 조치

| ID | 구분 | 발견 사항 | 조치 |
|---|---|---|---|
| IR-1 | 누락 결정 | `Gradle 또는 Maven` 선택지가 열려 있어 Story 1.2 구현자가 build system부터 재논의해야 했다. | `project-structure.md`, `sprint-plan.md`, Story 1.2, `next-context-prompt.md`에 Gradle Groovy DSL 권장 기본값을 고정했다. |
| IR-2 | 구현 모호성 | target module은 starter와 portal 두 개인데 Story 1.2가 starter module까지 만들지 여부가 모호했다. | Story 1.2는 `observability-portal`만 생성하고 starter module은 이후 story 목표 구조로만 문서화한다. |
| IR-3 | Story readiness | Java 빈 package는 Git/build에서 사라질 수 있어 package skeleton AC가 구현 결과로 남지 않을 수 있었다. | `package-info.java` marker 전략을 Story 1.2와 project structure에 추가했다. |
| IR-4 | 테스트 기준 부족 | Story 1.2의 smoke test와 Story 1.3의 architecture test 실행 기준이 추상적이었다. | test package 구조와 `:observability-portal:test` 기준을 문서화했다. |
| IR-5 | 패키지 경계 충돌 | DTO가 controller boundary shape라는 설명은 있으나 service/repository가 DTO에 의존하지 말아야 한다는 guard가 약했다. | `project-structure.md`와 Story 1.3에 `repository -> dto`, `service -> dto/controller` 금지 방향을 보강했다. |
| IR-6 | Scope creep 위험 | Story 1.2가 root/module skeleton 작업 중 Flyway, PostgreSQL, starter, API stub까지 당겨올 여지가 있었다. | Story 1.2의 "할 것/하지 않을 것"을 구체화하고, Story 1.4에서 migration/test runtime을 시작하도록 분리했다. |
| IR-7 | 문서 정렬 | `architecture-implementation-supplement.md`에 Maven 또는 혼합 build 후보와 group id 구현 시점 결정 표현이 남아 있어 Story 1.2의 Gradle Groovy DSL/GAV 결정과 충돌할 수 있었다. | `architecture-implementation-supplement.md`를 Gradle Groovy DSL, `settings.gradle`, `build.gradle`, `com.sst` Gradle group, `com.observation.portal` Java package 기준으로 보정했다. |
| IR-8 | 상태 해석 주의 | `sprint-status.yaml`에서 Story 1.3/1.4도 `ready-for-dev`라 자동 선택자가 다음 story를 잘못 고를 수 있다. | status 값은 story file 준비 상태로 유지하되, workflow note에 1.3/1.4는 선행조건 완료 전 next implementation target이 아니라고 명시했다. |

## 4. Story Readiness

| Story | Readiness | 선행 조건 | 구현 시작 판단 |
|---|---|---|---|
| Story 1.2 - Portal MVC Package Skeleton | Ready for dev | MVC 산출물, `project-structure.md` | 바로 시작 가능 |
| Story 1.3 - MVC Layer Guard Test | Ready after 1.2 | portal module과 marker package | Story 1.2 완료 후 시작 |
| Story 1.4 - Portal Physical Schema Foundation | Ready after 1.2 and 1.3 | Gradle portal module, architecture test 기반 | Story 1.3 완료 후 시작 |

## 5. 테스트 Readiness 기준

Story 1.2:

- portal module test task가 skeleton 상태에서 성공해야 한다.
- package marker는 compile 가능한 Java source여야 한다.
- smoke test는 build/test wiring만 확인하고 API, DB, service behavior를 검증하지 않는다.

Story 1.3:

- ArchUnit 또는 동등한 JVM architecture test를 사용한다.
- `..portal.domain..controller..`는 `..portal.domain..repository..`에 의존하지 않는다.
- `..portal.domain..repository..`는 `..portal.domain..controller..`와 `..portal.domain..dto..`에 의존하지 않는다.
- `..portal.domain..service..`는 `..portal.domain..controller..`와 controller response DTO에 의존하지 않는다.
- state/rule/p95/endpoint priority 계산 class는 `service` 또는 `model` package 아래에만 허용한다.
- `port`, `adapter`, `application` package는 만들지 않는다.

Story 1.4:

- Flyway migration은 `observability-portal/src/main/resources/db/migration/`에 둔다.
- PostgreSQL integration test는 Testcontainers를 우선 기준으로 한다.
- migration 적용, unique constraint, foreign key, table/column comment 존재를 검증한다.

## 6. 남은 리스크

- 실제 implementation 컨텍스트에서 Gradle wrapper 생성 가능 여부는 로컬 Gradle 설치 상태에 영향을 받을 수 있다. wrapper 생성이 불가능하면 구현자는 대체 실행 명령과 사유를 보고해야 한다.
- Spring Boot/Gradle plugin의 정확한 버전은 구현 시점의 안정 버전으로 확정해야 한다. 이 IR은 module/package/test 경계를 고정하고, 외부 버전 최신성 판단은 구현 작업에서 확인한다.
- Story 1.2는 구조 skeleton만 만들기 때문에 product behavior는 아직 검증하지 않는다. 첫 product behavior 검증은 Epic 3~6에서 닫는다.

## 7. 추가 IR 재점검 결과

재점검 일시: 2026-05-09

### 판정

**Pass**

### P0/P1/P2 Findings

| Priority | Finding | Status |
|---|---|---|
| P0 | Story 1.2 구현을 막는 active MVC/Hexagonal 기준 충돌은 발견하지 못했다. | 없음 |
| P1 | `architecture-implementation-supplement.md`의 build system/group id 표현이 다른 active 문서보다 열려 있었다. | 수정 완료 |
| P2 | `sprint-status.yaml`의 1.3/1.4 `ready-for-dev`는 파일 준비 상태로는 맞지만, 다음 구현 대상을 고르는 사람이나 자동화가 순서를 오해할 수 있다. | workflow note 보강 완료 |

### 구현 직전 결론

- active 구현 기준은 feature-first package 배치를 사용하는 Traditional MVC + Service/Repository Layering으로 일관된다.
- Story 1.2 -> Story 1.3 -> Story 1.4 순서는 구현 가능하고 안전하다.
- Story 1.2 acceptance criteria는 Gradle Groovy DSL, `observability-portal`, `com.observation.portal`, `package-info.java`, smoke test 범위를 바로 개발 가능한 수준으로 닫고 있다.
- `archive/hexagonal-version/`은 legacy/historical reference로만 언급되며 active 구현 기준으로 참조되는 곳은 없다.
- 다음 단계는 Story 1.2 `bmad-dev-story`다.

## 8. Account Auth 정책 정렬 기록

재점검 일시: 2026-05-19

### 판정

**Policy aligned, implementation pending.**

MVP account signup/login 기준은 GitHub OAuth only로 고정한다. 이 결정은 starter ingest의 `X-OBS-Project-Key` 검증과 별개이며, Epic 3 project key story가 사용자 계정 인증을 구현하거나 확장하지 않는다.

### 적용 기준

- 신규 account 생성은 GitHub OAuth 성공 후에만 허용한다.
- GitHub user id 또는 provider subject를 external identity stable key로 사용한다.
- Login도 MVP에서는 GitHub OAuth로 생성되었거나 연결된 account에만 허용한다.
- Cookie 기반 server session은 MVP 인증 기준이 아니다.
- API 인증은 `Authorization: Bearer <access_token>` header, 짧은 만료 JWT access token, rotation/revoke/reuse detection을 갖춘 refresh token 기준으로 둔다.
- Redis는 token store 필수 인프라로 잠그지 않는다. 초기 후보는 RDBMS hashed refresh token 또는 token family metadata다.
- Email/password, local account registration, password reset, email verification required for signup, magic link, Google/Kakao/Naver OAuth, anonymous flow는 MVP 범위 밖이다.

### 남은 승인 차단

| Priority | Finding | 필요 결정 |
|---|---|---|
| P1 | Bearer AT/RT를 cookie server session 없이 client에 전달해야 하지만, 일반 API response/log/error에는 token을 노출하지 않는 정책도 함께 요구된다. | Token issuance/refresh response의 예외 범위와 client 저장/전달 방식을 구현 story 전에 승인해야 한다. |
| P1 | Account/auth physical schema는 아직 구현 story로 분리되어 있지 않다. | `users/accounts`, `external_identities`, `refresh_token_families` 또는 동등한 token store schema를 별도 story로 만들어야 한다. |
| P2 | GitHub OAuth token 저장 여부는 GitHub API 호출 필요성에 따라 달라진다. | MVP에서 GitHub API 호출이 필요한지 결정하고, 필요하면 암호화/최소 scope/회전/폐기 기준을 닫아야 한다. |
