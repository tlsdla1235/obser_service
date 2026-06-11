---
artifactType: project-structure
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: dashboard-sot-final-consolidated
date: 2026-06-11
scope: active-mvc-artifacts
---

# Project Structure - MVC Version

## 1. 결정 요약

구현 구조는 **Traditional MVC + Service/Repository Layering**으로 고정하되, portal package 배치는 **feature-first MVC**로 둔다.

`domain` package는 순수 DDD domain layer가 아니다. 이 프로젝트에서는 catalog, ingest, dashboard 같은 업무 기능을 묶는 namespace이며, 각 feature 아래에 필요한 `controller`, `dto`, `service`, `repository`, `model` package를 둔다.

build system은 **Gradle Groovy DSL**을 권장 기본값으로 둔다. 현재 repo root에는 `settings.gradle`과 `build.gradle` 기반 multi-module build를 둔다.

Java baseline은 사용자 host app과의 호환성을 위해 **17**로 고정한다. Gradle toolchain과 `options.release`는 이 baseline을 따른다.

선택 이유:

- starter library와 portal runtime을 한 repo에서 module 단위로 나누기 쉽다.
- Spring Boot app module과 starter/library module의 plugin/dependency 차이를 관리하기 쉽다.
- Story 1.2에서 최소 skeleton과 test task를 빠르게 만들 수 있다.

Epic 13 이후 dashboard 구현은 Project Entry, Application List, Application Dashboard live, Snapshot history/detail, Instance Dashboard live/snapshot mode, Instance Snapshot Trend 화면을 같은 portal runtime 안에서 제공한다. Project/Application navigation은 catalog/dashboard read model 책임이고, snapshot/instance trend는 stored dashboard snapshot/read model projection 책임이다.

## 2. Root / Module 구조

목표 root 구조:

```text
observation/
  settings.gradle
  build.gradle
  gradle/
    wrapper/
  gradlew
  gradlew.bat
  observability-portal/
    build.gradle
    src/main/java/
    src/main/resources/
    src/test/java/
    src/test/resources/
  observability-spring-boot-starter/
    build.gradle
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
- Gradle group: `com.sst`
- Gradle version: `0.1.0-SNAPSHOT`
- Java baseline: `17`
- portal Java package: `com.observation.portal`
- default test command: `./gradlew :observability-portal:test`

## 3. observability-portal 구조

```text
observability-portal/
  build.gradle
  src/main/java/com/observation/portal/
    PortalApplication.java
    common/
      package-info.java
      time/package-info.java
    domain/
      package-info.java
      catalog/
        package-info.java
        controller/package-info.java
        dto/package-info.java
        entity/package-info.java
        model/package-info.java
        repository/package-info.java
        service/package-info.java
      account/
        package-info.java
        controller/package-info.java
        dto/package-info.java
        entity/package-info.java
        model/package-info.java
        repository/package-info.java
        service/package-info.java
      ingest/
        package-info.java
        controller/package-info.java
        dto/package-info.java
        service/package-info.java
      dashboard/
        package-info.java
        controller/package-info.java
        dto/package-info.java
        model/package-info.java
        service/package-info.java
      admin/
        package-info.java
        controller/package-info.java
        dto/package-info.java
      metric/
        package-info.java
        entity/package-info.java
        model/package-info.java
        repository/package-info.java
        service/package-info.java
      state/
        package-info.java
        model/package-info.java
        service/package-info.java
      triage/
        package-info.java
        model/package-info.java
        service/package-info.java
      cleanup/
        package-info.java
        service/package-info.java
      snapshot/
        package-info.java
        entity/package-info.java
        repository/package-info.java
      bucket/
        package-info.java
        entity/package-info.java
        repository/package-info.java
      history/
        package-info.java
        controller/package-info.java
        dto/package-info.java
        service/package-info.java
      instance/
        package-info.java
        controller/package-info.java
        dto/package-info.java
        service/package-info.java
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
    domain/
      catalog/repository/
    support/
  src/test/resources/
```

`package-info.java`는 빈 package가 Git/build 결과에서 사라지지 않도록 두는 marker다. Story 1.2에서 실제 controller, service, repository 구현 class를 만들지 않는다.

JPA entity와 Spring Data repository marker는 feature-first package 안에서 실제 구현 story가 필요한 feature에만 추가한다. `domain.<feature>.repository.entity`나 `domain.<feature>.repository.jpa` 하위 package를 필수 구조로 요구하지 않는다.

`domain.account`는 GitHub OAuth only 계정 생성/로그인 story가 열릴 때 추가할 feature package다. Story 1.2 skeleton 또는 Epic 3 ingest acceptance 구현에서 미리 만들 필요는 없다.

`PortalApplication.java`는 Spring Boot portal runtime entrypoint가 필요할 때만 허용되는 최소 class다. API endpoint나 persistence behavior를 포함하지 않는다.

## 4. observability-spring-boot-starter 구조

이 module은 Story 1.2에서 만들지 않는다. 목표 구조는 아래로 고정한다.

```text
observability-spring-boot-starter/
  build.gradle
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
  common
    time
  domain
    catalog
      controller
      dto
      entity
      model
      repository
      service
    account
      controller
      dto
      entity
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
      model
      service
    admin
      controller
      dto
    metric
      entity
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
      entity
      repository
    bucket
      entity
      repository
    history
      controller
      dto
      service
    instance
      controller
      dto
      service
  security
  scheduler
  config
```

`domain`은 feature grouping namespace다. Hexagonal의 domain/application/port/adapter 구조를 복원하는 의미가 아니며, Simple MVC의 controller/service/repository/dto/model 책임을 feature 안에서 가까이 배치하기 위한 이름이다.

금지 package:

- `application`
- `port`
- `adapter`
- `adapter.in`
- `adapter.out`

## 6. Layer 책임

| Feature 하위 package | 책임 | 금지 |
|---|---|---|
| `domain.<feature>.controller` | HTTP request/response, validation error mapping, status mapping, service 호출 | repository 직접 참조, state/rule/p95/endpoint priority 계산 |
| `domain.<feature>.service` | use case orchestration, validation orchestration, lifecycle state, insight rule, p95, endpoint priority, read model 생성. 필요 시 Spring Data repository/JPA entity 직접 사용 | HTTP status 결정, controller/dto package 의존, JPA entity를 external return model로 노출 |
| `domain.<feature>.repository` | Spring Data JPA/Jakarta Persistence + Hibernate 기반 PostgreSQL 저장/조회, constraint 활용, window bucket 조회, snapshot 저장/조회 | controller/dto 참조, state/rule/p95/endpoint priority 계산 |
| `domain.<feature>.entity` 또는 feature 내부 persistence package | Flyway schema에 mapping되는 JPA entity | controller response DTO, public API surface, service result/external return model |
| `domain.<feature>.model` | project/application/bucket/state/triage/read model 제품 언어 | Spring MVC request/response 타입 보관, persistence framework 세부 타입 노출 |
| `domain.<feature>.dto` | controller boundary의 external API shape | repository 전달 객체, service 내부 source of truth |
| `common.time` | 여러 feature에서 공유할 수 있는 시간/UTC bucket 보조 개념 | business feature별 판단 로직 보관 |
| `config` | Spring bean wiring, properties, transaction/migration/test configuration | layer 우회 wiring, controller가 repository를 직접 호출하도록 노출 |
| `security` | project key hash 검증 보조 component, service token 검증/서명 보조 | GitHub OAuth provider token/raw payload/secret 노출, cookie server session 강제 |
| `scheduler` | cleanup 또는 optional refresh trigger | business semantics 직접 계산 |

DTO 원칙:

- request/response DTO는 controller boundary shape다.
- service는 command/query/model을 사용한다.
- repository는 DTO를 참조하지 않는다.

JPA persistence 원칙:

- observability-portal repository 구현 표준은 Spring Data JPA/Jakarta Persistence + Hibernate다.
- Flyway SQL migration이 schema source of truth다.
- Hibernate DDL auto 생성/갱신은 사용하지 않는다.
- Service는 빠른 MVC 구현을 위해 필요하면 Spring Data repository와 JPA entity를 직접 사용할 수 있다.
- JPA entity와 Spring Data repository는 feature-first package 안에서 실제 코드 기준에 맞춰 둔다.
- JPA entity는 controller response DTO, public API surface, service result/external return model로 노출하지 않는다.
- raw project key 같은 secret은 DB row, migration, log, exception, response body, repository lookup surface에 남기지 않는다.

Account/Auth package 원칙:

- account signup/login 구현은 `domain.account` feature package 아래의 controller/dto/model/repository/service로 둔다.
- Account signup과 login은 GitHub OAuth only다.
- Local password, password reset, email verification required for signup, magic link, Google/Kakao/Naver OAuth, anonymous flow는 MVP package/API surface를 만들지 않는다.
- OAuth provider identity는 provider=`github`와 GitHub user id/provider subject를 stable key로 저장한다.
- GitHub OAuth token은 필요한 경우에만 저장하고, 저장한다면 암호화/최소 scope/회전/폐기 기준을 함께 둔다.
- 우리 서비스 API 인증은 cookie 기반 server session이 아니라 Bearer access token/JWT와 refresh token rotation 기준을 따른다.

Dashboard/history package 원칙:

- Project Entry와 Application List read model은 `domain.catalog` 또는 `domain.dashboard` service에서 제공하되 상세 dashboard 판단을 대체하지 않는다.
- Application Dashboard read model은 `domain.dashboard`의 controller/dto/model/service boundary에 둔다.
- Instance Detail evidence read model은 `domain.instance` 또는 `domain.dashboard` service 후보에 둔다.
- Snapshot/history와 Instance Snapshot Trend는 `domain.history` service 후보가 `domain.snapshot.repository`를 재사용해 stored dashboard snapshot/read model에서 projection한다.
- `domain.history`는 별도 `OperationalEventRepository`, `operational_events` table, raw instance timeseries table을 요구하지 않는다.

## 7. Test Package 구조

Portal test package:

```text
com.observation.portal
  PortalModuleSmokeTest
  architecture
    MvcLayerBoundaryTest
  domain
    catalog
      repository
  support
```

Story별 test 범위:

- Story 1.2: `PortalModuleSmokeTest`만으로 module/test wiring을 확인한다.
- Story 1.3: `architecture/MvcLayerBoundaryTest`에서 MVC dependency direction을 검증한다.
- Story 1.4: `domain/catalog/repository` 또는 `support` 아래에서 Flyway migration, PostgreSQL constraint, comment 검증을 수행한다.
- Epic 5: `domain/dashboard`, `domain/instance`, `domain/history` service/controller slice에서 server read model serialization과 UI-side recomputation 금지를 검증한다.

## 8. Migration 위치

Flyway migration 위치:

```text
observability-portal/src/main/resources/db/migration/
```

Flyway migration은 PostgreSQL physical schema의 source of truth다. Hibernate DDL auto create/update로 schema를 생성하거나 갱신하지 않는다.

Story 1.4에서 시작할 migration:

```text
V001__create_projects.sql
V002__create_applications_and_instances.sql
```

Story 1.4에서 만들지 않을 migration:

```text
V003__create_accepted_metric_buckets.sql
V004__add_local_percentiles_to_accepted_metric_buckets.sql
V005__create_dashboard_snapshots.sql
```

`V003`은 Epic 3, `V004`는 Story 4.0 이후 local percentile 보존, `V005`는 Epic 5 dashboard snapshot/read model history에서 구현한다.

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

Epic 6 prototype은 historical baseline으로 보존한다. Epic 13 이후 최종 UI 흐름은 Dashboard Source of Truth 정렬 결과를 따른다.

1. Project Entry
2. Application List
3. Application Dashboard live
4. Snapshot history/date map/detail
5. Instance Dashboard live detail
6. selected Application Snapshot 기반 Instance Dashboard snapshot mode
7. Instance Snapshot Trend

Snapshot/History UI는 30분 slot과 stored dashboard read model 복원 surface이며, Instance Snapshot Trend는 stored `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection이다. 둘 다 raw explorer나 arbitrary query UI가 아니다.

## 10. Story 1.2에서 만들 것

Story 1.2 구현 범위:

- Gradle Groovy DSL root build skeleton
- `settings.gradle`
- root `build.gradle`
- Gradle wrapper, 가능한 경우
- `observability-portal/build.gradle`
- `observability-portal` main/test source tree
- `com.observation.portal` base package
- feature-first MVC package `package-info.java` markers
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
- histogram bucket distribution merge와 starter canonical percentile 표시 정책
- lifecycle state calculation
- insight rule ranking
- endpoint priority calculation
- static dashboard UI asset
- Prometheus/scrape/query UI/high-cardinality custom metric/logs/traces/large tenancy 관련 dependency
