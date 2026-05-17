---
artifactType: epics
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
sourcePolicy: 기존 Lightweight Hexagonal 산출물의 제품/계약 의미를 Traditional MVC로 재해석
status: mvc-version-generated
date: 2026-05-09
---

# Epics - Traditional MVC 기준 재산출

## Epic 1. Architecture Foundation

목표: starter와 portal이 Traditional MVC/Layered 구조를 일관되게 따르는 기본 뼈대를 만든다.

### Stories

1. Starter package skeleton 생성
   - `model`, `service`, `spring`, `client.http`, `queue`, `config`
   - service는 host request path에서 network call을 하지 않는다.
2. Portal MVC package skeleton 생성
   - `controller`, `service`, `repository`, `model`, `dto`, `security`, `scheduler`, `config`
   - controller는 repository를 직접 참조하지 않는다.
3. MVC layer guard test 추가
   - controller에서 repository 직접 참조를 금지한다.
   - repository에서 controller/dto 참조를 금지한다.
   - service/model 외부에서 state/rule/p95/endpoint priority 계산을 금지한다.
4. Portal physical schema foundation
   - PostgreSQL migration 도구와 local/test DB runtime 기준을 세팅한다.
   - `projects`, `applications`, `application_instances` physical schema를 만든다.
   - table/column 한국어 comment를 migration에 포함한다.
   - UUID는 application-generated UUID로 두고, project key는 `key_prefix` + BCrypt hash 검증 경계로 둔다.
   - `accepted_metric_buckets`와 `dashboard_snapshots`는 Epic 3/5에서 구현하되, migration naming과 comment convention은 Epic 1에서 고정한다.

## Epic 2. Starter Direct Ingest Producer

목표: 사용자가 starter를 추가하면 host app request path를 막지 않고 30초 bucket을 전송한다.

Operational Event History, p99/tail latency judgment, dashboard snapshot history, operational events API, recent history UI, alert/snapshot deep link는 Epic 2에 포함하지 않는다.

### Stories

1. Micrometer observation binding
   - HTTP/JVM/datasource signal을 수집한다.
2. Route normalization and low-cardinality guard
   - raw path parameter와 high-cardinality tag를 payload에 넣지 않는다.
3. Bucket rollup service
   - app summary와 endpoint histogram bucket을 30초 UTC boundary로 집계한다.
4. Async flush worker
   - bounded queue, retry/backoff, drop policy를 구현한다.
   - request thread에서 network call을 하지 않는다.
   - portal timeout/down 상황에서도 host app request path를 막지 않는다.
5. Ingest envelope builder service
   - `ingest-envelope` contract에 맞는 payload를 만든다.
6. Negative path guard
   - scrape config, pull metric query, arbitrary query UI 경로가 starter MVP path에 없음을 테스트한다.

## Epic 3. Portal Ingest Acceptance

목표: portal이 ingest envelope를 검증하고 idempotent하게 저장한다.

Epic 3은 `accepted_metric_buckets` 저장과 idempotent acceptance까지만 닫는다. Operational event 저장, 계산, API는 구현하지 않는다.

### Stories

1. Project key verification service
   - `X-OBS-Project-Key`를 검증한다.
2. Ingest acceptance service
   - schema version, bucket boundary, metric taxonomy, idempotency key를 검증한다.
3. PostgreSQL bucket repository
   - accepted bucket과 payload hash를 저장한다.
4. Duplicate handling
   - 동일 payload는 성공, 동일 key/다른 hash는 conflict로 처리한다.

## Epic 4. State Semantics and Time Windows

목표: 첫 화면 상태 언어를 portal service/model의 단일 판단으로 고정한다.

### Stories

1. Time bucket contract implementation
   - 30초 bucket, 15분 current, 15분 baseline, UTC 기준을 구현한다.
2. Lifecycle state service
   - `waiting_first_data`, `unknown`, `idle`, `active`, `stale`, `down`, `degraded`를 판정한다.
3. Recovery guidance
   - stale/down 이후 sample 부족 상태를 안전하게 표현한다.
4. State semantics tests
   - freshness, minimum sample, baseline insufficient edge case를 테스트한다.

## Epic 5. Triage Summary and Endpoint Priority

목표: UX가 요구한 first-screen read model을 계산해 UI가 그대로 표시할 수 있게 한다.

### Stories

1. Histogram merge service
   - instance bucket을 app/endpoint 기준으로 병합해 p95를 계산한다.
   - `histogram-merge` golden fixture를 통과한다.
   - UI/client-side p95 계산을 금지한다.
2. Insight rule service
   - MVP rule set과 guard, ranking, max 3 노출을 구현한다.
3. App triage summary read model
   - state, rationale, core metrics, triage cards, zero-insight reason, recovery guidance를 반환한다.
4. Endpoint priority read model
   - slow/error/comparative evidence, confidence, freshness 기반 목록을 만든다.
5. Dashboard query API
   - `read-model-contract` contract를 반환한다.
6. Operational event history read model/API 후보
   - Epic 5/6 착수 전 구현 기준으로 `operational-event-history` contract를 따른다.
   - `dashboard_snapshots` 기반으로 bounded event 목록을 파생한다.
   - 별도 `operational_events` table이나 event repository는 만들지 않는다.
   - p99/tail latency는 auxiliary evidence로만 사용하고 단독 판단으로 쓰지 않는다.

## Epic 6. First-Screen Delivery and Demo Hardening

목표: 설치 후 30~60초 안에 운영 첫 화면이 보이는 end-to-end 경험을 닫는다.

### Stories

1. Minimal onboarding guide
   - dependency 추가, portal base URL, project key, environment 설정만 설명한다.
2. Demo app green path
   - starter 추가 후 first bucket 수신과 app alive 표시를 검증한다.
3. Failure path demo
   - portal down, duplicate ingest, stale/down 상태를 시연한다.
4. Dashboard UI integration
   - UI는 read model을 표시하고 별도 state/rule 판단을 하지 않는다.
5. Recent operational history UI와 snapshot deep link 후보
   - UI는 bounded event 목록과 snapshot detail link를 표시한다.
   - UI는 state/rule/p95/p99/endpoint priority를 재계산하지 않는다.
   - raw snapshot explorer, arbitrary time-series query UI, alert delivery log 병합은 non-goal이다.

## Post-MVP Candidate Backlog

### Runtime Gauge Aggregate Extension

목표: JVM CPU, JVM heap, datasource pool usage의 latest-only 한계를 보완해 짧은 saturation spike와 지속 압력을 함께 표현한다.

후보 stories:

1. Runtime aggregate contract/schema version
   - `ingest-envelope` schema를 MVP `1.0`과 분리한다.
   - `latest`, `max`, `avg`, `sampleCount` 의미와 validation rule을 고정한다.
2. Starter runtime aggregate rollup
   - 30초 bucket 안 JVM/datasource valid sample에서 latest/max/avg/sampleCount를 계산한다.
   - raw sample 배열이나 arbitrary custom metric map은 만들지 않는다.
3. Portal acceptance and persistence update
   - aggregate ratio range, `avg <= max`, `latest <= max`, `sampleCount > 0`을 검증한다.
   - accepted bucket persistence에 max/avg/sampleCount를 추가한다.
4. Saturation hint read model update
   - `max`는 peak evidence, `avg`는 sustained pressure evidence, `latest`는 current-state evidence로 분리해 read model과 insight rule evidence에 노출한다.
   - multi-instance 평균은 sampleCount 기반 weighted average로 계산한다.

이 후보는 MVP 필수 경로가 아니다. 구현 전 `metric-taxonomy`, `ingest-envelope`, `database-schema`, `insight-rules`, dashboard read model contract를 함께 갱신해야 한다.

### Starter Flush Worker Wake-up Strategy

목표: MVP의 bounded in-memory queue + background flush worker 구조를 유지하되, worker idle 대기와 shutdown wake-up 방식을 운영 관측 결과에 맞춰 더 정교하게 조정한다.

현재 MVP는 `BlockingQueue.poll(timeout)` 기반 timeout 있는 blocking wait를 사용한다. 이는 CPU busy waiting은 아니며, 주기적으로 `running` 상태를 확인하고 `close()` interrupt에 반응하기 위한 단순한 구현이다.

후보 stories:

1. Worker wait strategy comparison
   - `poll(timeout)`, `take() + interrupt`, poison pill, executor/lifecycle 기반 종료 방식을 비교한다.
   - idle CPU wake-up, shutdown latency, 테스트 결정성, Spring bean lifecycle 연동 비용을 함께 본다.
2. Configurable worker lifecycle
   - poll interval, shutdown join timeout, retry/backoff를 starter configuration으로 열지 검토한다.
   - 기본값은 host app safety와 단순성을 우선하고, 잘못된 설정이 request path blocking으로 이어지지 않게 guard를 둔다.
3. Queue drain and worker observability
   - queue depth, dropped count, flush success/failure, last successful flush time, worker running state를 starter internal metric/log로 노출할지 검토한다.
   - durable outbox, Kafka/Redis, 별도 worker runtime 도입 여부와는 분리해서 판단한다.

이 후보는 MVP 필수 경로가 아니다. 구현 전 `starter-failure-semantics`, Story 2.4 Async Flush Worker, starter auto-configuration/lifecycle 설계를 함께 갱신해야 한다.

## Cross-Epic Acceptance Criteria

- MVP 필수 경로에 Prometheus 설치, scrape config, selector 등록, PromQL query가 없다.
- host app build/startup/request path는 portal 장애에 의해 막히지 않는다.
- p95 source of truth는 server-side histogram merge다.
- first-screen state와 triage 문구는 dashboard read model에서 온다.
- 아키텍처 스타일은 Traditional MVC 하나다.
- `triageCards=[]`이면 zero-insight reason과 recommended action이 반드시 있다.
- endpoint priority는 rank, reason, evidence, confidence, freshness를 포함한다.
- Operational Event History는 Epic 5/6에서 dashboard snapshot/read model 기반 bounded surface로 다루며 Epic 2/3에는 포함하지 않는다.
- MVP에서는 별도 `operational_events` table을 만들지 않는다.

## AC Traceability Matrix

| AC | Epic | Contract | MVC Layer Boundary | Planned Test |
|---|---|---|---|---|
| Traditional MVC only | Epic 1 | `architecture.md` | controller -> service -> repository direction | `MvcLayerBoundaryTest` |
| No pull metric MVP path | Epic 1, 2, 6 | `metric-taxonomy.md`, `ingest-envelope.md` | starter direct ingest service only | `NoPrometheusMvpPathTest` |
| Host build/startup/request path not blocked | Epic 2 | `architecture.md`, `ingest-envelope.md`, `starter-failure-semantics.md` | spring integration -> service -> bounded queue | `StarterNonBlockingIngestTest` |
| Ingest idempotency | Epic 3 | `ingest-envelope.md` | `IngestAcceptanceService` + `MetricBucketRepository` | `IngestAcceptanceServiceTest` |
| Server-side p95 source | Epic 5 | `histogram-merge.md` | `HistogramMergeService` | `HistogramMergeGoldenFixtureTest` |
| First-screen state source | Epic 4, 5 | `state-semantics.md`, `read-model-contract.md` | `DashboardReadModelService` | `DashboardReadModelSnapshotTest` |
| 0-insight is explicit | Epic 5 | `read-model-contract.md` | `TriageSummaryService` | `ZeroInsightReadModelTest` |
| Endpoint priority is explainable | Epic 5 | `insight-rules.md`, `read-model-contract.md` | `EndpointPriorityService` | `EndpointPriorityReadModelTest` |
| Bounded operational event history | Epic 5, 6 | `operational-event-history.md`, `read-model-contract.md` | `OperationalEventHistoryService` candidate + `DashboardSnapshotRepository` | `OperationalEventHistoryReadModelTest` |
| Demo promise | Epic 6 | `read-model-contract.md` | starter + portal e2e | `FirstBucketToAliveE2ETest` |
