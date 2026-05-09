---
artifactType: project-structure
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: ready-for-story-1-2
date: 2026-05-09
scope: active-mvc-artifacts
---

# Project Structure - MVC Version

## 1. 결정 요약

구현 구조는 **Traditional MVC + Service/Repository Layering**으로 고정한다.

build system은 **Gradle Kotlin DSL**을 권장 기본값으로 둔다. 현재 repo root에는 `pom.xml`, `build.gradle`, `settings.gradle` 계열 build file이 없으므로 Story 1.2 구현자는 Gradle 기반 multi-module root를 새로 시작한다.

선택 이유:

- starter library와 portal runtime을 한 repo에서 module 단위로 나누기 쉽다.
- Spring Boot app module과 starter/library module의 plugin/dependency 차이를 관리하기 쉽다.
- Story 1.2에서 최소 skeleton과 test task를 빠르게 만들 수 있다.

## 2. Root / Module 구조

목표 root 구조:

```text
observation/
  settings.gradle.kts
  build.gradle.kts
  gradle/
    libs.versions.toml
  gradlew
  gradlew.bat
  observability-portal/
    build.gradle.kts
    src/main/java/
    src/main/resources/
    src/test/java/
    src/test/resources/
  observability-spring-boot-starter/
    build.gradle.kts
    src/main/java/
    src/main/resources/
    src/test/java/
    src/test/resources/
  planning-artifacts/
  implementation-artifacts/
  archive/
```

Story 1.2에서는 `observability-portal`만 생성한다. `observability-spring-boot-starter`는 목표 구조에 포함하지만 Story 1.2 구현 범위가 아니다.

권장 root 설정:

- root project name: `observation`
- included module for Story 1.2: `observability-portal`
- base package: `com.observation`
- default test command: `./gradlew :observability-portal:test`

## 3. observability-portal 구조

```text
observability-portal/
  build.gradle.kts
  src/main/java/com/observation/portal/
    PortalApplication.java
    controller/
      package-info.java
      ingest/package-info.java
      dashboard/package-info.java
      admin/package-info.java
    service/
      package-info.java
      ingest/package-info.java
      catalog/package-info.java
      metric/package-info.java
      state/package-info.java
      triage/package-info.java
      dashboard/package-info.java
      cleanup/package-info.java
    repository/
      package-info.java
      catalog/package-info.java
      bucket/package-info.java
      snapshot/package-info.java
    model/
      package-info.java
      catalog/package-info.java
      metric/package-info.java
      state/package-info.java
      triage/package-info.java
      time/package-info.java
    dto/
      package-info.java
      ingest/package-info.java
      dashboard/package-info.java
      admin/package-info.java
    security/package-info.java
    scheduler/package-info.java
    config/package-info.java
  src/main/resources/
    application.yml
    db/migration/
    static/dashboard/
  src/test/java/com/observation/portal/
    PortalModuleSmokeTest.java
    architecture/
    controller/
    service/
    repository/
    support/
  src/test/resources/
```

`package-info.java`는 빈 package가 Git/build 결과에서 사라지지 않도록 두는 marker다. Story 1.2에서 실제 controller, service, repository 구현 class를 만들지 않는다.

`PortalApplication.java`는 Spring Boot portal runtime entrypoint가 필요할 때만 허용되는 최소 class다. API endpoint나 persistence behavior를 포함하지 않는다.

## 4. observability-spring-boot-starter 구조

이 module은 Story 1.2에서 만들지 않는다. 목표 구조는 아래로 고정한다.

```text
observability-spring-boot-starter/
  build.gradle.kts
  src/main/java/com/observation/starter/
    model/
      identity/
      metric/
      route/
      time/
    service/
    spring/
      observation/
      autoconfigure/
      schedule/
    client/http/
    queue/
    config/
  src/main/resources/
    META-INF/spring/
  src/test/java/com/observation/starter/
    service/
    spring/
    client/http/
    queue/
    architecture/
```

Starter에는 MVC web controller를 두지 않는다. host app request path에서는 network call을 하지 않고, service가 bounded queue enqueue와 bucket rollup을 맡는다.

## 5. Portal Package Tree

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

금지 package:

- `domain`
- `application`
- `port`
- `adapter`
- `adapter.in`
- `adapter.out`

## 6. Layer 책임

| Layer | 책임 | 금지 |
|---|---|---|
| `controller` | HTTP request/response, validation error mapping, status mapping, service 호출 | repository 직접 참조, state/rule/p95/endpoint priority 계산 |
| `service` | use case orchestration, validation orchestration, lifecycle state, insight rule, p95, endpoint priority, read model 생성 | HTTP status 결정, SQL 최적화 세부 구현, controller/dto package 의존 |
| `repository` | PostgreSQL 저장/조회, constraint 활용, window bucket 조회, snapshot 저장/조회 | controller/dto 참조, state/rule/p95/endpoint priority 계산 |
| `model` | project/application/bucket/state/triage/time/read model 제품 언어 | Spring MVC request/response 타입 보관, persistence framework 세부 타입 노출 |
| `dto` | controller boundary의 external API shape | repository 전달 객체, service 내부 source of truth |
| `config` | Spring bean wiring, properties, transaction/migration/test configuration | layer 우회 wiring, controller가 repository를 직접 호출하도록 노출 |
| `security` | project key hash 검증 보조 component | product onboarding/control plane 확대 |
| `scheduler` | cleanup 또는 optional refresh trigger | business semantics 직접 계산 |

DTO 원칙:

- request/response DTO는 controller boundary shape다.
- service는 command/query/model을 사용한다.
- repository는 DTO를 참조하지 않는다.

## 7. Test Package 구조

Portal test package:

```text
com.observation.portal
  PortalModuleSmokeTest
  architecture
    MvcLayerBoundaryTest
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
  support
```

Story별 test 범위:

- Story 1.2: `PortalModuleSmokeTest`만으로 module/test wiring을 확인한다.
- Story 1.3: `architecture/MvcLayerBoundaryTest`에서 MVC dependency direction을 검증한다.
- Story 1.4: `repository/catalog` 또는 `support` 아래에서 Flyway migration, PostgreSQL constraint, comment 검증을 수행한다.

## 8. Migration 위치

Flyway migration 위치:

```text
observability-portal/src/main/resources/db/migration/
```

Story 1.4에서 시작할 migration:

```text
V001__create_projects.sql
V002__create_applications_and_instances.sql
```

Story 1.4에서 만들지 않을 migration:

```text
V003__create_accepted_metric_buckets.sql
V004__create_dashboard_snapshots.sql
```

`V003`은 Epic 3, `V004`는 Epic 5에서 구현한다.

## 9. Static Dashboard 위치

static dashboard runtime asset 위치:

```text
observability-portal/src/main/resources/static/dashboard/
```

frontend source가 필요해질 경우 후보 위치:

```text
observability-portal/src/main/frontend/
```

MVP에서 dashboard UI는 read model을 표시만 한다. UI는 lifecycle state, insight rule, p95, endpoint priority를 재계산하지 않는다.

Story 1.2에서는 dashboard UI asset을 만들지 않는다.

## 10. Story 1.2에서 만들 것

Story 1.2 구현 범위:

- Gradle Kotlin DSL root build skeleton
- `settings.gradle.kts`
- root `build.gradle.kts`
- Gradle wrapper, 가능한 경우
- `observability-portal/build.gradle.kts`
- `observability-portal` main/test source tree
- `com.observation.portal` base package
- required package `package-info.java` markers
- optional minimal `PortalApplication.java`
- minimal `PortalModuleSmokeTest`
- `./gradlew :observability-portal:test` 기준의 test wiring

Story 1.2는 package skeleton을 만드는 story다. 구현 behavior를 넣지 않는다.

## 11. Story 1.2에서 만들지 않을 것

Story 1.2 제외 범위:

- `observability-spring-boot-starter` module/source tree
- ingest API controller
- dashboard API controller
- admin API controller
- service behavior
- repository implementation
- Flyway dependency와 migration file
- PostgreSQL/Testcontainers runtime
- `accepted_metric_buckets`
- `dashboard_snapshots`
- project key verification
- idempotency conflict handling
- histogram merge, p95 calculation
- lifecycle state calculation
- insight rule ranking
- endpoint priority calculation
- static dashboard UI asset
- Prometheus/scrape/query UI/high-cardinality custom metric/logs/traces/large tenancy 관련 dependency
