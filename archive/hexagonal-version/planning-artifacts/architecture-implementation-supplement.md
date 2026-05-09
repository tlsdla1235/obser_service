---
artifactType: architecture-implementation-supplement
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Lightweight Hexagonal
sourcePolicy: PRD와 UX 의도는 유지하고, 기존 아키텍처 결정은 계승하지 않음
status: draft-for-ir
date: 2026-05-08
---

# Architecture Implementation Supplement

## 1. 목적

이 문서는 `architecture.md`, `epics.md`, `contracts/*`를 구현자가 바로 Sprint Planning으로 옮길 수 있도록 보조하는 러프 구현 설계다.

제품 약속, UX 본문, PRD 본문을 다시 정의하지 않는다. 단일 아키텍처 선택은 계속 **Lightweight Hexagonal**이다. Simple MVC, layered service/repository 중심 설계, hybrid architecture, Prometheus/scrape/query UI 중심 설계로 되돌아가지 않는다.

## 2. Repo / Module 구조 러프안

초기 구현 repo는 2개 runtime 단위를 반영하는 2개 모듈로 시작한다.

```text
observation/
  settings.gradle(.kts) 또는 pom.xml
  build.gradle(.kts)
  observability-spring-boot-starter/
    src/main/java/...
    src/test/java/...
  observability-portal/
    src/main/java/...
    src/main/resources/
      db/migration/
      static/dashboard/
    src/test/java/...
  planning-artifacts/
```

선택 기준:

- `observability-spring-boot-starter`는 host Spring Boot app 안에 붙는 starter/library다.
- `observability-portal`은 ingest API, dashboard query API, persistence, dashboard static UI를 포함하는 하나의 portal runtime이다.
- dashboard UI는 별도 backend deployable을 만들지 않는다. MVP에서는 portal의 presentation adapter로 보고 `observability-portal/src/main/resources/static/dashboard`에 build output을 둔다.
- React/Vite 같은 frontend build가 필요해지면 source는 `observability-portal/src/main/frontend`에 둘 수 있지만, runtime 배포 단위는 계속 portal 하나다.
- Redis, Nginx, 별도 worker runtime은 MVP 필수 모듈로 만들지 않는다. 필요성이 확인되기 전까지 PostgreSQL + portal in-process refresh로 닫는다.

## 3. Package Tree

base package 예시는 `com.observation`으로 둔다. 실제 group id는 구현 시작 시 한 번만 정하고, 아래 package suffix 경계를 유지한다.

### 3.1 Starter Module

```text
com.observation.starter
  domain
    identity
    metric
    route
    time
  application
    port.in
    port.out
  adapter.in.spring
    observation
    autoconfigure
    schedule
  adapter.out.http
  adapter.out.queue
  bootstrap
```

Starter core 원칙:

- `domain`과 `application`은 Spring, Micrometer, HTTP client 타입을 직접 참조하지 않는다.
- Spring/Micrometer 객체는 `adapter.in.spring`에서 starter application DTO로 변환한다.
- request thread는 network call을 하지 않는다.
- bounded queue overflow는 host app business flow를 막지 않는 drop/backpressure 정책으로 처리한다.

### 3.2 Portal Module

```text
com.observation.portal
  domain
    catalog
    ingest
    metric
    state
    triage
    time
  application
    port.in
    port.out
  adapter.in.web
    ingest
    dashboard
    admin
  adapter.out.persistence
    catalog
    bucket
    snapshot
  adapter.out.security
  adapter.out.time
  bootstrap
```

Portal core 원칙:

- `domain`과 `application`은 Spring MVC, JPA/JdbcTemplate, servlet, frontend 타입을 직접 참조하지 않는다.
- lifecycle state, insight rule ranking, endpoint priority, p95 merge는 portal domain/application에서만 계산한다.
- `adapter.out.persistence`는 저장과 조회 최적화를 맡지만 의미 계산을 만들지 않는다.
- `adapter.in.web` controller는 request/response 변환과 use case 위임만 맡는다.

## 4. 주요 Use Case와 Port 목록

### 4.1 Starter Application

Use case:

| Use Case | 책임 |
|---|---|
| `CollectHttpObservationUseCase` | Spring/Micrometer adapter가 넘긴 HTTP observation을 low-cardinality input으로 기록 |
| `RollupMetricBucketUseCase` | 30초 UTC bucket boundary 기준으로 app/endpoint cumulative histogram bucket 집계 |
| `BuildIngestEnvelopeUseCase` | `ingest-envelope` contract에 맞는 envelope 생성 |
| `FlushMetricBucketUseCase` | due bucket을 queue에서 꺼내 portal ingest API 전송을 orchestration |

Inbound port:

| Port | 호출 adapter | 목적 |
|---|---|---|
| `RecordHttpObservationPort` | `adapter.in.spring.observation` | request/response/runtime signal을 starter core DTO로 기록 |
| `FlushDueMetricBucketsPort` | `adapter.in.spring.schedule` | flush cadence에 맞춰 due bucket 전송 시작 |

Outbound port:

| Port | 구현 adapter | 목적 |
|---|---|---|
| `IngestClientPort` | `adapter.out.http` | portal ingest API 호출 |
| `ClockPort` | `bootstrap` 또는 time adapter | UTC bucket boundary 계산용 현재 시각 제공 |
| `InstanceIdentityPort` | `adapter.in.spring.autoconfigure` 또는 bootstrap | hostname/pod/generated instance id 제공 |
| `BoundedQueuePort` | `adapter.out.queue` | request thread와 flush worker 분리 |

### 4.2 Portal Application

Use case:

| Use Case | 책임 |
|---|---|
| `AcceptIngestEnvelopeUseCase` | project key, schema version, idempotency key, bucket boundary, metric taxonomy 검증 후 저장 |
| `MergeHistogramBucketsUseCase` | accepted bucket을 app/endpoint window 기준으로 병합하고 p95 계산 |
| `EvaluateLifecycleStateUseCase` | `state-semantics` 순서에 따라 waiting/unknown/idle/active/stale/down/degraded 판단 |
| `BuildAppTriageSummaryUseCase` | app-level state, rationale, zero insight, recovery, triage cards 생성 |
| `ListEndpointPriorityUseCase` | slow/error/comparative evidence 기반 endpoint priority 생성 |
| `QueryDashboardSnapshotUseCase` | UI가 그대로 표시할 `read-model-contract` 응답 조회/생성 |

Inbound port:

| Port | 호출 adapter | 목적 |
|---|---|---|
| `AcceptIngestEnvelopeCommand` | `adapter.in.web.ingest` | ingest envelope 수용 |
| `QueryDashboardSnapshotQuery` | `adapter.in.web.dashboard` | dashboard first-screen read model 조회 |
| `ListEndpointPriorityQuery` | `adapter.in.web.dashboard` | endpoint priority 부분 조회가 필요할 때 사용 |

Outbound port:

| Port | 구현 adapter | 목적 |
|---|---|---|
| `ProjectKeyVerifierPort` | `adapter.out.security` | raw project key 검증과 project 식별 |
| `ApplicationCatalogPort` | `adapter.out.persistence.catalog` | project/application/instance 식별과 생성/조회 |
| `MetricBucketStorePort` | `adapter.out.persistence.bucket` | accepted bucket 저장, idempotency 확인, window bucket 조회 |
| `SnapshotStorePort` | `adapter.out.persistence.snapshot` | dashboard snapshot 저장/조회 |
| `ClockPort` | `adapter.out.time` | freshness/window 계산용 현재 시각 제공 |

## 5. Adapter별 책임

### 5.1 Starter Adapters

| Adapter | 책임 | 금지 |
|---|---|---|
| `adapter.in.spring` | auto-configuration, Micrometer observation binding, route normalization hook, scheduled flush trigger | portal HTTP 호출 직접 수행, business request blocking |
| `adapter.out.http` | ingest envelope serialization, HTTP timeout, retry/backoff 실행 | bucket rollup, state/rule 계산 |
| `adapter.out.queue` | in-memory bounded queue, overflow/drop policy | durable outbox 구현, unbounded memory queue |
| `bootstrap` | properties binding, bean wiring, default config | domain/application에 Spring 타입 누수 |

### 5.2 Portal Adapters

| Adapter | 책임 | 금지 |
|---|---|---|
| `adapter.in.web` | REST DTO 변환, status code mapping, use case 위임, static dashboard serving | lifecycle state/rule/p95/endpoint priority 계산 |
| `adapter.out.persistence` | PostgreSQL read/write, transaction boundary, idempotency unique constraint, window bucket 조회 | p95 계산을 DB view로 숨김, rule ranking, state 판단 |
| `adapter.out.security` | project key hash 검증, 인증 실패 mapping | product onboarding 정책 확대 |
| `adapter.out.time` | UTC clock 제공 | freshness 의미 판단 |
| `bootstrap` | Spring configuration, transaction wiring, migration 실행 | core package가 adapter를 참조하게 만드는 wiring |

## 6. MVP에서 Stub/Mock으로 둘 수 있는 것

MVP 구현 속도를 위해 아래는 단순화할 수 있다.

- project key 발급은 public product API 대신 dev seed 또는 내부 admin endpoint로 시작한다.
- Redis queue 없이 portal ingest transaction 직후 in-process로 dashboard snapshot refresh를 수행한다.
- Nginx/WebSocket 없이 portal static dashboard + HTTP polling으로 시작한다.
- starter instance identity는 hostname, pod name env, generated UUID fallback 정도로 시작한다.
- starter route allowlist가 없으면 framework normalized route + bounded top-N endpoint만 사용한다.
- dashboard UI는 `read-model-contract` response를 그대로 렌더링하고, 복잡한 client-side 상태 관리는 두지 않는다.
- local demo app은 최소 synthetic traffic으로 first bucket -> alive/degraded 경로만 검증한다.

## 7. MVP에서 절대 만들지 않을 것

아래는 MVP 범위 밖이며 Sprint Planning에 들어가면 안 된다.

- Prometheus 설치, scrape config, PromQL query, pull-based metric backend
- arbitrary metric query UI, dashboard builder, chart explorer
- high-cardinality custom metric/tag ingestion
- logs, traces, span search, trace-to-log correlation
- multi-tenant billing/large tenancy control plane
- durable starter outbox, Kafka, 별도 stream processing runtime
- UI에서 p95, lifecycle state, insight rule, endpoint priority 재계산
- PostgreSQL view/materialized view에 p95/state/rule 계산을 숨기는 구현
- Simple MVC 또는 service/repository layered style을 최종 아키텍처로 되돌리는 구현

## 8. ArchUnit / Package Boundary Test 기준

MVP 첫 스프린트에서 최소 아래 guard를 둔다.

### Starter

- `..starter.domain..`은 `org.springframework..`, `io.micrometer..`, HTTP client, `..adapter..`를 참조하지 않는다.
- `..starter.application..`은 `org.springframework.web..`, `io.micrometer..`, `..adapter..`를 참조하지 않는다.
- `..starter.adapter.in.spring..`은 `application.port.in`을 통해서만 core를 호출한다.
- `..starter.adapter.out.http..`는 `application.port.out` 구현체로만 노출된다.
- request path adapter test에서 portal timeout/down 상황에도 host request thread가 network timeout을 기다리지 않음을 검증한다.

### Portal

- `..portal.domain..`은 `org.springframework..`, persistence framework, web framework, `..adapter..`를 참조하지 않는다.
- `..portal.application..`은 `..adapter..`를 참조하지 않고 `application.port.out`만 사용한다.
- `..portal.adapter.in.web..`은 persistence adapter를 직접 참조하지 않는다.
- `..portal.adapter.out.persistence..`는 web/controller DTO를 참조하지 않는다.
- lifecycle state, insight rule, endpoint priority 계산 package는 `domain` 또는 `application` 안에만 존재한다.
- dashboard controller test는 `read-model-contract`를 serialization할 뿐 state/rule/p95 계산을 하지 않음을 검증한다.

Frontend/UI는 ArchUnit 대상은 아니지만 아래 정적/리뷰 기준을 둔다.

- UI 코드에 p95 histogram merge 구현을 두지 않는다.
- UI 코드에 lifecycle transition table이나 insight ranking rule을 복제하지 않는다.
- UI는 `state`, `zeroInsight`, `recovery`, `triageCards`, `endpointPriority`를 read model에서 받은 대로 표시한다.

## 9. 2인 1개월 MVP 제한사항

Lightweight Hexagonal을 유지하되 구조가 과해지지 않도록 아래 제한을 둔다.

- runtime module은 starter와 portal 2개로 제한한다.
- portal 내부에 별도 worker/service deployable을 만들지 않는다.
- starter outbound port는 `IngestClientPort`, `ClockPort`, `InstanceIdentityPort`, `BoundedQueuePort`를 넘기지 않는다.
- portal outbound port는 `ProjectKeyVerifierPort`, `ApplicationCatalogPort`, `MetricBucketStorePort`, `SnapshotStorePort`, `ClockPort`를 넘기지 않는다.
- 각 use case는 먼저 하나의 application service로 구현하고, 중복이 실제로 생긴 뒤에만 helper를 추출한다.
- domain model은 contract 검증, state semantics, histogram merge, insight rule에 필요한 개념만 둔다.
- endpoint table 분리는 성능 문제가 아니라 구현 복잡도를 줄일 때만 도입한다.
- Redis, SSE/WebSocket, materialized view, complex cache는 IR 이후에도 별도 필요성이 없으면 만들지 않는다.
- 첫 화면 성공 기준은 계속 `alive / slow / error / where to look first`다.

