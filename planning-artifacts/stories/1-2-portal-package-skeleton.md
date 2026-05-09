---
artifactType: story
storyId: "1.2"
epic: "Epic 1. Architecture Foundation"
title: "Portal MVC Package Skeleton"
architectureStyle: Traditional MVC
status: ready-for-dev
date: 2026-05-09
---

# Story 1.2 - Portal MVC Package Skeleton

## User Story

구현자로서, portal runtime을 Traditional MVC 구조로 일관되게 확장할 수 있도록 최소 module과 package skeleton을 먼저 만들고 싶다.

## Scope

이 story는 portal 구현의 시작점이다. 기능 구현보다 package boundary와 build/test 기반을 먼저 마련한다.

포함:

- Gradle Kotlin DSL root build 기준 생성
- `observability-portal` module 생성
- portal base package를 `com.observation.portal`로 고정
- Traditional MVC package skeleton 생성
- skeleton package를 `package-info.java` marker로 추적 가능하게 구성
- test source set 생성
- skeleton 상태에서 통과하는 최소 smoke test
- 이후 Story 1.3, Story 1.4가 붙을 수 있는 최소 build wiring

제외:

- `observability-spring-boot-starter` module/source tree 생성
- ingest API 구현
- dashboard query API 구현
- admin API 구현
- repository 구현
- Flyway migration 작성
- PostgreSQL/Testcontainers runtime 설정
- project key verification 구현
- accepted bucket 저장
- dashboard snapshot 저장
- p95/state/rule/endpoint priority 계산
- starter module 구현

## Source Artifacts

- `planning-artifacts/implementation-readiness-review.md`
- `planning-artifacts/project-structure.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/acceptance-traceability.md`

## Implementation Notes

- 최종 아키텍처 선택은 Traditional MVC 하나다.
- port/adapter package 구조를 만들지 않는다.
- repo에 기존 build system이 없으므로 Gradle Kotlin DSL을 권장 기본값으로 사용한다.
- root project name은 `observation`으로 둔다.
- 이번 story에서 settings에 포함할 module은 `observability-portal` 하나다.
- 문서의 base package는 `com.observation`이며 portal base package는 `com.observation.portal`이다.
- Java 빈 package는 Git에서 사라질 수 있으므로 `package-info.java` marker를 둔다.
- `PortalApplication.java`는 Spring Boot module wiring을 위해 필요할 때만 허용되는 최소 entrypoint다.
- portal runtime은 MVP에서 별도 worker deployable 없이 하나의 runtime으로 둔다.
- dashboard UI는 별도 backend deployable이 아니라 portal static view로 본다.
- Story 1.2에서는 static dashboard 위치를 문서 기준으로만 유지하고 UI asset을 만들지 않는다.

## Required Package Skeleton

아래 suffix 경계를 유지한다.

```text
com.observation.portal
  controller
    ingest
    dashboard
    admin
  service
    ingest
    catalog
    metric
    state
    triage
    dashboard
    cleanup
  repository
    catalog
    bucket
    snapshot
  model
    catalog
    metric
    state
    triage
    time
  dto
    ingest
    dashboard
    admin
  security
  scheduler
  config
```

초기 skeleton은 `package-info.java` 같은 marker와 최소 boot/test wiring만 허용한다. 실제 controller, service, repository, entity, DTO 구현 class를 이 story에서 만들지 않는다.

금지 package:

- `domain`
- `application`
- `port`
- `adapter`
- `adapter.in`
- `adapter.out`

## Acceptance Criteria

1. repo는 Gradle Kotlin DSL root build를 가진다.
2. `settings.gradle.kts`는 `observability-portal` module을 build/test 대상으로 인식한다.
3. portal main source tree와 test source tree가 존재한다.
4. portal base package는 `com.observation.portal`로 확정되어 문서의 package suffix와 일치한다.
5. `controller`, `service`, `repository`, `model`, `dto`, `security`, `scheduler`, `config` package가 존재하고 marker source로 추적 가능하다.
6. `controller.ingest`, `controller.dashboard`, `controller.admin` package가 구분되어 있다.
7. `repository.catalog`, `repository.bucket`, `repository.snapshot` package가 구분되어 있다.
8. skeleton 상태에서 `:observability-portal:test`가 성공한다.
9. `observability-spring-boot-starter` module/source tree는 생성하지 않는다.
10. 이 story에서는 API, DB migration, repository 구현, service behavior를 만들지 않는다.

## Suggested Tasks

1. repo의 현재 build 상태를 확인한다.
2. Gradle Kotlin DSL root build를 만든다.
3. `observability-portal` module을 추가한다.
4. portal main/test source directory를 만든다.
5. required package skeleton을 `package-info.java` marker로 만든다.
6. skeleton 상태에서 test command가 성공하도록 최소 smoke test를 추가한다.
7. `domain/application/port/adapter` package가 생성되지 않았는지 확인한다.
8. 다음 story가 사용할 package 위치를 build file과 source tree만으로 추론 가능하게 둔다.

## Test Requirements

- portal module test task가 성공해야 한다.
- 권장 실행 명령은 `./gradlew :observability-portal:test`다.
- Gradle wrapper 생성이 환경상 불가능하면 대체 실행 명령과 이유를 구현 결과에 남긴다.
- skeleton smoke test는 기능을 검증하지 않고 module/test wiring만 검증한다.
- MVC layer boundary test는 Story 1.3에서 본격 추가한다.
- migration test는 Story 1.4에서 추가한다.

## Developer Guardrails

- controller/service/repository 구조는 만들되 controller가 repository를 직접 호출하는 shortcut은 만들지 않는다.
- repository에 state/rule/p95/endpoint priority 계산을 넣지 않는다.
- DTO를 service/model 전체로 넓게 전파하지 않는다.
- `accepted_metric_buckets`, `dashboard_snapshots` 관련 구현을 시작하지 않는다.
- Flyway, PostgreSQL, Testcontainers dependency는 Story 1.4에서 추가한다.
- Prometheus, scrape, query UI, logs, traces 관련 dependency를 추가하지 않는다.
