---
artifactType: story
storyId: "1.2"
epic: "Epic 1. Architecture Foundation"
title: "Portal MVC Package Skeleton"
architectureStyle: Traditional MVC
status: done
date: 2026-05-10
---

# Story 1.2 - Portal MVC Package Skeleton

## User Story

구현자로서, portal runtime을 feature-first Traditional MVC 구조로 일관되게 확장할 수 있도록 최소 module과 package skeleton을 먼저 만들고 싶다.

## Scope

이 story는 portal 구현의 시작점이다. 기능 구현보다 package boundary와 build/test 기반을 먼저 마련한다.

포함:

- Gradle Groovy DSL root build 기준 생성
- `observability-portal` module 생성
- portal base package를 `com.observation.portal`로 고정
- feature-first MVC package skeleton 생성
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
- `domain` package는 순수 DDD domain layer가 아니라 업무 기능 묶음 namespace다.
- controller/service/repository/dto/model은 최상위 layer package가 아니라 feature package 아래에 둔다.
- repo에 기존 build system이 없으므로 Gradle Groovy DSL을 권장 기본값으로 사용한다.
- root project name은 `observation`으로 둔다.
- 이번 story에서 settings에 포함할 module은 `observability-portal` 하나다.
- Gradle group은 `com.sst`이며 portal Java package는 `com.observation.portal`이다.
- Java 빈 package는 Git에서 사라질 수 있으므로 `package-info.java` marker를 둔다.
- `PortalApplication.java`는 Spring Boot module wiring을 위해 필요할 때만 허용되는 최소 entrypoint다.
- portal runtime은 MVP에서 별도 worker deployable 없이 하나의 runtime으로 둔다.
- dashboard UI는 별도 backend deployable이 아니라 portal static view로 본다.
- Story 1.2에서는 static dashboard 위치를 문서 기준으로만 유지하고 UI asset을 만들지 않는다.

## Required Package Skeleton

아래 feature-first MVC 경계를 유지한다.

```text
com.observation.portal
  common
    time
  domain
    catalog
      model
      repository
      service
    ingest
      controller
      dto
      service
    dashboard
      controller
      dto
      service
    admin
      controller
      dto
    metric
      model
      repository
      service
    state
      model
      service
    triage
      model
      service
    cleanup
      service
    snapshot
      repository
    bucket
      repository
  security
  scheduler
  config
```

`domain`은 feature grouping namespace다. Hexagonal의 port/adapter/application 구조를 복원하는 뜻이 아니다.

초기 skeleton은 `package-info.java` 같은 marker와 최소 boot/test wiring만 허용한다. 실제 controller, service, repository, entity, DTO 구현 class를 이 story에서 만들지 않는다.

금지 package:

- `application`
- `port`
- `adapter`
- `adapter.in`
- `adapter.out`

## Acceptance Criteria

1. repo는 Gradle Groovy DSL root build를 가진다.
2. `settings.gradle`는 `observability-portal` module을 build/test 대상으로 인식한다.
3. portal main source tree와 test source tree가 존재한다.
4. portal base package는 `com.observation.portal`로 확정되어 feature-first package tree와 일치한다.
5. `common`, `domain`, `security`, `scheduler`, `config` package가 존재하고 marker source로 추적 가능하다.
6. `domain.ingest.controller`, `domain.dashboard.controller`, `domain.admin.controller` package가 구분되어 있다.
7. `domain.catalog.repository`, `domain.bucket.repository`, `domain.snapshot.repository`, `domain.metric.repository` package가 구분되어 있다.
8. skeleton 상태에서 `:observability-portal:test`가 성공한다.
9. `observability-spring-boot-starter` module/source tree는 생성하지 않는다.
10. 이 story에서는 API, DB migration, repository 구현, service behavior를 만들지 않는다.

## Suggested Tasks

1. repo의 현재 build 상태를 확인한다.
2. Gradle Groovy DSL root build를 만든다.
3. `observability-portal` module을 추가한다.
4. portal main/test source directory를 만든다.
5. required package skeleton을 `package-info.java` marker로 만든다.
6. skeleton 상태에서 test command가 성공하도록 최소 smoke test를 추가한다.
7. `application/port/adapter` package가 생성되지 않았는지 확인한다.
8. 다음 story가 사용할 package 위치를 build file과 source tree만으로 추론 가능하게 둔다.

## Test Requirements

- portal module test task가 성공해야 한다.
- 권장 실행 명령은 `./gradlew :observability-portal:test`다.
- Gradle wrapper 생성이 환경상 불가능하면 대체 실행 명령과 이유를 구현 결과에 남긴다.
- skeleton smoke test는 기능을 검증하지 않고 module/test wiring만 검증한다.
- MVC layer boundary test는 Story 1.3에서 본격 추가한다.
- migration test는 Story 1.4에서 추가한다.

## Developer Guardrails

- feature별 controller/service/repository 구조는 만들되 controller가 repository를 직접 호출하는 shortcut은 만들지 않는다.
- repository에 state/rule/p95/endpoint priority 계산을 넣지 않는다.
- DTO를 service/model 전체로 넓게 전파하지 않는다.
- `accepted_metric_buckets`, `dashboard_snapshots` 관련 구현을 시작하지 않는다.
- Flyway, PostgreSQL, Testcontainers dependency는 Story 1.4에서 추가한다.
- Prometheus, scrape, query UI, logs, traces 관련 dependency를 추가하지 않는다.

## Tasks/Subtasks

- [x] repo의 현재 build 상태를 확인한다.
- [x] Gradle Groovy DSL root build를 만든다.
- [x] `observability-portal` module을 추가한다.
- [x] portal main/test source directory를 만든다.
- [x] required package skeleton을 `package-info.java` marker로 만든다.
- [x] skeleton 상태에서 test command가 성공하도록 최소 smoke test를 추가한다.
- [x] `application/port/adapter` package가 생성되지 않았는지 확인한다.
- [x] 다음 story가 사용할 package 위치를 build file과 source tree만으로 추론 가능하게 둔다.

## Dev Agent Record

### Implementation Plan

- 사용자 Build/GAV 지시에 따라 `settings.gradle`/`build.gradle` Groovy DSL 기준을 적용한다.
- root project는 `observation`, module은 `observability-portal`, Gradle group/version은 `com.sst`/`0.1.0-SNAPSHOT`로 둔다.
- Java package는 MVC 산출물 기준인 `com.observation.portal`을 유지하고, feature-first skeleton package마다 `package-info.java` marker를 둔다.
- Spring Boot module wiring을 위해 최소 `PortalApplication`과 module/test wiring smoke test만 추가한다.

### Debug Log

- 로컬 `gradle` 명령은 설치되어 있지 않아 공식 Gradle 배포본 `9.5.0`을 `/tmp`에 내려받아 wrapper를 생성했다.
- Spring Boot 공식 프로젝트 페이지에서 현재 안정 버전으로 표시된 `4.0.6`을 사용했다.
- `./gradlew :observability-portal:test` 실행 결과: `BUILD SUCCESSFUL`, 3 actionable tasks executed.
- Groovy DSL 기준을 확인했고 starter module, forbidden hexagonal-style package, Flyway/PostgreSQL/Testcontainers dependency, metric bucket/snapshot 구현이 생기지 않았음을 확인했다.
- 2026-05-10: layer-first marker를 feature-first MVC package marker로 정렬했고, `domain`을 feature grouping namespace로 명시했다.

### Completion Notes

- Gradle Groovy DSL root skeleton과 `observability-portal` module build wiring을 추가했다.
- Java 17 toolchain/release, `com.sst` group, `0.1.0-SNAPSHOT` version, `com.observation.portal` base package를 구성했다.
- feature-first MVC package skeleton을 `package-info.java` marker로 추적 가능하게 만들었다.
- `PortalModuleSmokeTest`를 추가해 skeleton module/test wiring만 검증했다.

## File List

- `.gitignore`
- `settings.gradle`
- `build.gradle`
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/1-2-portal-package-skeleton.md`
- `observability-portal/build.gradle`
- `observability-portal/src/main/java/com/observation/portal/PortalApplication.java`
- `observability-portal/src/main/java/com/observation/portal/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/common/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/config/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/admin/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/admin/controller/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/admin/dto/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/service/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/cleanup/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/cleanup/service/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/controller/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/dto/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/metric/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/metric/model/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/metric/repository/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/metric/service/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/model/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/service/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/triage/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/triage/model/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/domain/triage/service/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/scheduler/package-info.java`
- `observability-portal/src/main/java/com/observation/portal/security/package-info.java`
- `observability-portal/src/test/java/com/observation/portal/PortalModuleSmokeTest.java`

## Change Log

- 2026-05-10: Story 1.2 portal Gradle Groovy DSL skeleton, MVC package markers, wrapper, and smoke test added.
- 2026-05-10: Story 1.2 package skeleton aligned from layer-first MVC to feature-first MVC.
- 2026-05-13: 사용자 host app 호환성을 위한 Java 17 baseline 문서 정합성만 보정했다.

## Status

done
