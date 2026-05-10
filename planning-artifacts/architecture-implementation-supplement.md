---
artifactType: architecture-implementation-supplement
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
sourcePolicy: 기존 Lightweight Hexagonal 산출물의 제품/계약 의미를 Traditional MVC로 재해석
status: feature-first-mvc-aligned
date: 2026-05-10
---

# Architecture Implementation Supplement - MVC Version

## 1. 목적

이 문서는 `architecture.md`, `epics.md`, `contracts/*`를 구현자가 바로 Sprint Planning으로 옮길 수 있도록 보조하는 MVC 구현 설계다.

제품 약속, UX 본문, PRD 본문을 다시 정의하지 않는다. 단일 아키텍처 선택은 **Traditional MVC + Service/Repository Layering**이다. Portal 내부 package 배치는 layer-first가 아니라 **feature-first MVC**다.

## 2. Repo / Module 구조

목표 repo는 2개 runtime 단위를 반영하는 2개 module 구조를 사용한다. 단, Story 1.2에서는 `observability-portal` module만 생성하고 `observability-spring-boot-starter` module은 이후 starter story에서 추가한다.

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
    src/main/java/...
    src/main/resources/
      db/migration/
      static/dashboard/
    src/test/java/...
  observability-spring-boot-starter/
    build.gradle
    src/main/java/...
    src/test/java/...
  planning-artifacts/
  implementation-artifacts/
  archive/
```

선택 기준:

- `observability-spring-boot-starter`는 host Spring Boot app 안에 붙는 starter/library다.
- `observability-portal`은 ingest controller, dashboard controller, service, repository, persistence, dashboard static UI를 포함하는 하나의 portal runtime이다.
- Story 1.2의 root build는 Gradle Groovy DSL로 고정하고, `settings.gradle`에는 `observability-portal`만 include한다.
- Maven 또는 mixed build는 Story 1.2 구현 기준이 아니다.
- dashboard UI는 별도 backend deployable을 만들지 않는다. MVP에서는 portal의 static view로 보고 `observability-portal/src/main/resources/static/dashboard`에 build output을 둔다.
- React/Vite 같은 frontend build가 필요해지면 source는 `observability-portal/src/main/frontend`에 둘 수 있지만, runtime 배포 단위는 계속 portal 하나다.
- Redis, Nginx, 별도 worker runtime은 MVP 필수 모듈로 만들지 않는다. 필요성이 확인되기 전까지 PostgreSQL + portal in-process refresh로 닫는다.

## 3. Package Tree

Gradle group id는 `com.sst`로 고정한다. Java package는 MVC 산출물 기준을 유지하며, portal base package는 `com.observation.portal`이다. group id와 Java package는 다를 수 있다.

Portal의 `domain` package는 순수 DDD domain layer가 아니라 업무 기능을 묶는 namespace다. Hexagonal의 `domain/application/port/adapter` 경계를 되살리지 않고, Simple MVC의 `controller/service/repository/dto/model` 책임을 feature 아래에 모은다.

### 3.1 Starter Module

```text
com.observation.starter
  model
    identity
    metric
    route
    time
  service
  spring
    observation
    autoconfigure
    schedule
  client.http
  queue
  config
```

Starter 원칙:

- `model`과 `service`는 Spring MVC request, servlet, HTTP client 구현 타입을 직접 보관하지 않는다.
- Spring/Micrometer 객체는 `spring` package에서 starter model 또는 service input DTO로 변환한다.
- request thread는 network call을 하지 않는다.
- bounded queue overflow는 host app business flow를 막지 않는 drop/backpressure 정책으로 처리한다.

### 3.2 Portal Module

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

Portal 원칙:

- `domain.<feature>.controller`는 request/response 변환과 HTTP status mapping만 맡는다.
- `domain.<feature>.service`는 lifecycle state, insight rule ranking, endpoint priority, p95 merge, read model 생성을 맡는다.
- `domain.<feature>.repository`는 저장과 조회 최적화를 맡지만 의미 계산을 만들지 않는다.
- `domain.<feature>.dto`는 controller boundary 밖으로 넓게 퍼지지 않게 한다. Service 내부에서는 command/query/model을 사용한다.

## 4. 주요 Service 목록

### 4.1 Starter Services

| Service | 책임 |
|---|---|
| `HttpObservationCollectionService` | Spring/Micrometer integration이 넘긴 HTTP observation을 low-cardinality input으로 기록 |
| `MetricBucketRollupService` | 30초 UTC bucket boundary 기준으로 app/endpoint cumulative histogram bucket 집계 |
| `IngestEnvelopeBuilderService` | `ingest-envelope` contract에 맞는 envelope 생성 |
| `MetricBucketFlushService` | due bucket을 queue에서 꺼내 portal ingest API 전송 orchestration |

Infrastructure dependency:

| Component | 구현 위치 | 목적 |
|---|---|---|
| `PortalIngestHttpClient` | `client.http` | portal ingest API 호출 |
| `UtcClock` | `config` 또는 `model.time` | UTC bucket boundary 계산용 현재 시각 제공 |
| `InstanceIdentityResolver` | `spring.autoconfigure` 또는 `config` | hostname/pod/generated instance id 제공 |
| `BoundedMetricQueue` | `queue` | request thread와 flush worker 분리 |

### 4.2 Portal Services

| Service | 책임 |
|---|---|
| `IngestAcceptanceService` | project key, schema version, idempotency key, bucket boundary, metric taxonomy 검증 후 저장 |
| `ProjectKeyVerificationService` | raw project key 검증과 project 식별 |
| `ApplicationCatalogService` | `domain.catalog.service`에서 project/application/instance 식별과 생성/조회 |
| `HistogramMergeService` | `domain.metric.service`에서 accepted bucket을 app/endpoint window 기준으로 병합하고 p95 계산 |
| `LifecycleStateService` | `domain.state.service`에서 `state-semantics` 순서에 따라 waiting/unknown/idle/active/stale/down/degraded 판단 |
| `TriageSummaryService` | `domain.triage.service`에서 app-level state, rationale, zero insight, recovery, triage cards 생성 |
| `EndpointPriorityService` | `domain.metric.service` 또는 `domain.dashboard.service`에서 slow/error/comparative evidence 기반 endpoint priority 생성 |
| `DashboardReadModelService` | `domain.dashboard.service`에서 UI가 그대로 표시할 `read-model-contract` 응답 조회/생성 |
| `RetentionCleanupService` | `domain.cleanup.service`에서 retention 기준으로 오래된 bucket/snapshot 삭제 orchestration |

Repository dependency:

| Repository | 구현 위치 | 목적 |
|---|---|---|
| `ProjectRepository` | `domain.catalog.repository` | project metadata와 key hash 조회 |
| `ApplicationRepository` | `domain.catalog.repository` | application/environment 식별과 생성/조회 |
| `ApplicationInstanceRepository` | `domain.catalog.repository` | instance 식별과 last seen 갱신 |
| `MetricBucketRepository` | `domain.bucket.repository` 또는 `domain.metric.repository` | accepted bucket 저장, idempotency 확인, window bucket 조회 |
| `DashboardSnapshotRepository` | `domain.snapshot.repository` | dashboard snapshot 저장/조회 |

## 5. Layer별 책임

### 5.1 Starter

| Layer | 책임 | 금지 |
|---|---|---|
| `spring` | auto-configuration, Micrometer binding, route normalization hook, scheduled flush trigger | portal HTTP 호출 직접 수행, business request blocking |
| `service` | bucket rollup, envelope 생성, flush orchestration | Spring request/response 타입을 장기 보관 |
| `client.http` | ingest envelope serialization, HTTP timeout, retry/backoff 실행 | bucket rollup, state/rule 계산 |
| `queue` | in-memory bounded queue, overflow/drop policy | durable outbox 구현, unbounded memory queue |
| `config` | properties binding, bean wiring, default config | service에 불필요한 framework coupling 강제 |

### 5.2 Portal

| Feature 하위 package | 책임 | 금지 |
|---|---|---|
| `domain.<feature>.controller` | REST DTO 변환, status code mapping, service 위임, static dashboard serving | lifecycle state/rule/p95/endpoint priority 계산, repository 직접 호출 |
| `domain.<feature>.service` | ingest orchestration, validation, idempotency 판단, state/rule/p95/read model 생성 | HTTP status 직접 결정, DB SQL 최적화 세부 구현 보유, controller response DTO 의존 |
| `domain.<feature>.repository` | PostgreSQL read/write, idempotency unique constraint, window bucket 조회 | p95 계산을 SQL view로 숨김, rule ranking, state 판단, controller/dto 의존 |
| `domain.<feature>.model` | feature 내부 제품 언어와 계산 입력/결과 모델 | Spring MVC request/response 타입 보관 |
| `domain.<feature>.dto` | controller boundary request/response shape | repository 전달 객체, service 내부 source of truth |
| `security` | project key hash 검증 보조 | product onboarding 정책 확대 |
| `scheduler` | cleanup, optional snapshot refresh trigger | business semantics 직접 판단 |
| `config` | Spring configuration, transaction wiring, migration 실행 | controller가 repository를 우회 호출하도록 wiring |

## 6. MVP에서 Stub/Mock으로 둘 수 있는 것

- project key 발급은 public product API 대신 dev seed 또는 내부 admin endpoint로 시작한다.
- Redis queue 없이 portal ingest transaction 직후 in-process로 dashboard snapshot refresh를 수행한다.
- Nginx/WebSocket 없이 portal static dashboard + HTTP polling으로 시작한다.
- starter instance identity는 hostname, pod name env, generated UUID fallback 정도로 시작한다.
- starter route allowlist가 없으면 framework normalized route + bounded top-N endpoint만 사용한다.
- dashboard UI는 `read-model-contract` response를 그대로 렌더링하고, 복잡한 client-side 상태 관리는 두지 않는다.
- local demo app은 최소 synthetic traffic으로 first bucket -> alive/degraded 경로만 검증한다.

## 7. MVP에서 절대 만들지 않을 것

- Prometheus 설치, scrape config, PromQL query, pull-based metric backend
- arbitrary metric query UI, dashboard builder, chart explorer
- high-cardinality custom metric/tag ingestion
- logs, traces, span search, trace-to-log correlation
- multi-tenant billing/large tenancy control plane
- durable starter outbox, Kafka, 별도 stream processing runtime
- UI에서 p95, lifecycle state, insight rule, endpoint priority 재계산
- PostgreSQL view/materialized view에 p95/state/rule 계산을 숨기는 구현
- MVC라고 해서 controller가 service를 건너뛰고 repository를 직접 호출하는 구현

## 8. MVC Layer Guard Test 기준

MVP 첫 스프린트에서 최소 아래 guard를 둔다.

### Starter

- `..starter.service..`는 servlet request/response 타입과 Spring MVC controller 타입을 참조하지 않는다.
- `..starter.service..`는 `..starter.client.http..` 구현체 세부 타입 대신 서비스 생성자에서 주입받은 component만 사용한다.
- request path integration test에서 portal timeout/down 상황에도 host request thread가 network timeout을 기다리지 않음을 검증한다.

### Portal

- `..portal.domain..controller..`는 `..portal.domain..repository..`를 직접 참조하지 않는다.
- `..portal.domain..repository..`는 `..portal.domain..controller..` 또는 `..portal.domain..dto..`를 참조하지 않는다.
- `..portal.domain..service..`는 `..portal.domain..controller..` 또는 controller response DTO에 의존하지 않는다.
- lifecycle state, insight rule, endpoint priority, p95 계산 class는 `service` 또는 `model` 아래에만 존재한다.
- `port`, `adapter`, `application` package는 만들지 않는다.
- dashboard controller test는 `read-model-contract`를 serialization할 뿐 state/rule/p95 계산을 하지 않음을 검증한다.

Frontend/UI는 architecture test 대상은 아니지만 아래 정적/리뷰 기준을 둔다.

- UI 코드에 p95 histogram merge 구현을 두지 않는다.
- UI 코드에 lifecycle transition table이나 insight ranking rule을 복제하지 않는다.
- UI는 `state`, `zeroInsight`, `recovery`, `triageCards`, `endpointPriority`를 read model에서 받은 대로 표시한다.

## 9. 2인 1개월 MVP 제한사항

Traditional MVC를 유지하되 layer가 비대해지지 않도록 아래 제한을 둔다.

- runtime module은 starter와 portal 2개로 제한한다.
- portal 내부에 별도 worker/service deployable을 만들지 않는다.
- portal controller는 `domain.ingest.controller`, `domain.dashboard.controller`, `domain.admin.controller` 정도로 제한한다.
- repository는 `domain.catalog.repository`, `domain.bucket.repository`, `domain.snapshot.repository`, 필요 시 `domain.metric.repository` 중심으로 제한한다.
- 각 use case는 먼저 하나의 service method 또는 service class로 구현하고, 중복이 실제로 생긴 뒤에만 helper를 추출한다.
- model은 contract 검증, state semantics, histogram merge, insight rule에 필요한 개념만 둔다.
- endpoint table 분리는 성능 문제가 아니라 구현 복잡도를 줄일 때만 도입한다.
- Redis, SSE/WebSocket, materialized view, complex cache는 IR 이후에도 별도 필요성이 없으면 만들지 않는다.
- 첫 화면 성공 기준은 계속 `alive / slow / error / where to look first`다.
