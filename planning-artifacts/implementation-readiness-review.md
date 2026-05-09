---
artifactType: implementation-readiness-review
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: ready-for-story-1-2-after-doc-updates
date: 2026-05-09
scope: active-mvc-artifacts
---

# Implementation Readiness Review - MVC Version

## 1. 검토 범위

이 검토는 active `planning-artifacts/`와 `implementation-artifacts/` 산출물만 기준으로 수행했다.

`archive/hexagonal-version/`과 `bmad-restart-context-pack/`는 제품 문제와 UX 의도 참고 경계로만 보며, 이번 IR 결과의 구현 기준이 아니다.

최종 아키텍처 선택은 **Traditional MVC + Service/Repository Layering** 하나로 고정한다.

## 2. IR 결론

Story 1.2는 문서 보강 후 바로 구현 가능한 상태다.

다만 기존 산출물에는 구현 시작 직전에 흔들릴 수 있는 결정이 남아 있었다. 이번 보강으로 아래 사항을 확정한다.

- build system은 Gradle Kotlin DSL을 권장 기본값으로 둔다.
- root build는 Story 1.2에서 만들되, 구현 module은 `observability-portal` 하나만 생성한다.
- `observability-spring-boot-starter`는 목표 module 구조에 포함하지만 Story 1.2에서는 생성하지 않는다.
- base package는 `com.observation`으로 고정한다.
- 빈 Java package는 `package-info.java` marker로 추적 가능하게 만든다.
- Story 1.2의 테스트는 기능 테스트가 아니라 portal module build/test wiring smoke test로 제한한다.
- Story 1.3의 architecture guard는 `controller`, `service`, `repository`, `dto` 의존 방향을 먼저 고정한다.
- Story 1.4의 PostgreSQL/Flyway/Testcontainers 결정은 Story 1.2가 아니라 Story 1.4에서 시작한다.

## 3. 발견 사항과 조치

| ID | 구분 | 발견 사항 | 조치 |
|---|---|---|---|
| IR-1 | 누락 결정 | `Gradle 또는 Maven` 선택지가 열려 있어 Story 1.2 구현자가 build system부터 재논의해야 했다. | `project-structure.md`, `sprint-plan.md`, Story 1.2, `next-context-prompt.md`에 Gradle Kotlin DSL 권장 기본값을 고정했다. |
| IR-2 | 구현 모호성 | target module은 starter와 portal 두 개인데 Story 1.2가 starter module까지 만들지 여부가 모호했다. | Story 1.2는 `observability-portal`만 생성하고 starter module은 이후 story 목표 구조로만 문서화한다. |
| IR-3 | Story readiness | Java 빈 package는 Git/build에서 사라질 수 있어 package skeleton AC가 구현 결과로 남지 않을 수 있었다. | `package-info.java` marker 전략을 Story 1.2와 project structure에 추가했다. |
| IR-4 | 테스트 기준 부족 | Story 1.2의 smoke test와 Story 1.3의 architecture test 실행 기준이 추상적이었다. | test package 구조와 `:observability-portal:test` 기준을 문서화했다. |
| IR-5 | 패키지 경계 충돌 | DTO가 controller boundary shape라는 설명은 있으나 service/repository가 DTO에 의존하지 말아야 한다는 guard가 약했다. | `project-structure.md`와 Story 1.3에 `repository -> dto`, `service -> dto/controller` 금지 방향을 보강했다. |
| IR-6 | Scope creep 위험 | Story 1.2가 root/module skeleton 작업 중 Flyway, PostgreSQL, starter, API stub까지 당겨올 여지가 있었다. | Story 1.2의 "할 것/하지 않을 것"을 구체화하고, Story 1.4에서 migration/test runtime을 시작하도록 분리했다. |

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
- `..portal.controller..`는 `..portal.repository..`에 의존하지 않는다.
- `..portal.repository..`는 `..portal.controller..`와 `..portal.dto..`에 의존하지 않는다.
- `..portal.service..`는 `..portal.controller..`와 `..portal.dto..`에 의존하지 않는다.
- state/rule/p95/endpoint priority 계산 class는 `..portal.service..` 또는 `..portal.model..` 아래에만 허용한다.

Story 1.4:

- Flyway migration은 `observability-portal/src/main/resources/db/migration/`에 둔다.
- PostgreSQL integration test는 Testcontainers를 우선 기준으로 한다.
- migration 적용, unique constraint, foreign key, table/column comment 존재를 검증한다.

## 6. 남은 리스크

- 실제 implementation 컨텍스트에서 Gradle wrapper 생성 가능 여부는 로컬 Gradle 설치 상태에 영향을 받을 수 있다. wrapper 생성이 불가능하면 구현자는 대체 실행 명령과 사유를 보고해야 한다.
- Spring Boot/Gradle plugin의 정확한 버전은 구현 시점의 안정 버전으로 확정해야 한다. 이 IR은 module/package/test 경계를 고정하고, 외부 버전 최신성 판단은 구현 작업에서 확인한다.
- Story 1.2는 구조 skeleton만 만들기 때문에 product behavior는 아직 검증하지 않는다. 첫 product behavior 검증은 Epic 3~6에서 닫는다.
