---
artifactType: epics
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Lightweight Hexagonal
sourcePolicy: PRD와 UX 의도는 유지하고, 기존 아키텍처 결정은 계승하지 않음
status: party-mode-fixes-applied
date: 2026-05-08
---

# Epics - Lightweight Hexagonal 기준 재산출

## Epic 1. Architecture Foundation

목표: starter와 portal이 Lightweight Hexagonal 구조를 일관되게 따르는 기본 뼈대를 만든다.

### Stories

1. Starter package skeleton 생성
   - `domain`, `application`, `application.port.in`, `application.port.out`, `adapter.in.spring`, `adapter.out.http`, `bootstrap`
   - domain/application은 Spring framework 의존 없이 시작한다.
2. Portal package skeleton 생성
   - `domain`, `application`, `application.port.in`, `application.port.out`, `adapter.in.web`, `adapter.out.persistence`, `bootstrap`
3. Architecture guard test 추가
   - domain/application에서 adapter package 참조를 금지한다.
   - UI/read API에서 state/rule 재계산을 금지한다.
   - starter core에서 Spring/Micrometer 타입 직접 참조를 금지한다.

## Epic 2. Starter Direct Ingest Producer

목표: 사용자가 starter를 추가하면 host app request path를 막지 않고 30초 bucket을 전송한다.

### Stories

1. Micrometer observation binding
   - HTTP/JVM/datasource signal을 수집한다.
2. Route normalization and low-cardinality guard
   - raw path parameter와 high-cardinality tag를 payload에 넣지 않는다.
3. Bucket rollup
   - app summary와 endpoint histogram bucket을 30초 UTC boundary로 집계한다.
4. Async flush worker
   - bounded queue, retry/backoff, drop policy를 구현한다.
   - request thread에서 network call을 하지 않는다.
   - portal timeout/down 상황에서도 host app request path를 막지 않는다.
5. Ingest envelope builder
   - `ingest-envelope` contract에 맞는 payload를 만든다.
6. Negative path guard
   - scrape config, pull metric query, arbitrary query UI 경로가 starter MVP path에 없음을 테스트한다.

## Epic 3. Portal Ingest Acceptance

목표: portal이 ingest envelope를 검증하고 idempotent하게 저장한다.

### Stories

1. Project key verification adapter
   - `X-OBS-Project-Key`를 검증한다.
2. Accept ingest use case
   - schema version, bucket boundary, metric taxonomy, idempotency key를 검증한다.
3. PostgreSQL bucket persistence adapter
   - accepted bucket과 payload hash를 저장한다.
4. Duplicate handling
   - 동일 payload는 성공, 동일 key/다른 hash는 conflict로 처리한다.

## Epic 4. State Semantics and Time Windows

목표: 첫 화면 상태 언어를 portal application/domain의 단일 판단으로 고정한다.

### Stories

1. Time bucket contract implementation
   - 30초 bucket, 15분 current, 15분 baseline, UTC 기준을 구현한다.
2. Lifecycle state evaluator
   - `waiting_first_data`, `unknown`, `idle`, `active`, `stale`, `down`, `degraded`를 판정한다.
3. Recovery guidance
   - stale/down 이후 sample 부족 상태를 안전하게 표현한다.
4. State semantics tests
   - freshness, minimum sample, baseline insufficient edge case를 테스트한다.

## Epic 5. Triage Summary and Endpoint Priority

목표: UX가 요구한 first-screen read model을 계산해 UI가 그대로 표시할 수 있게 한다.

### Stories

1. Histogram merge
   - instance bucket을 app/endpoint 기준으로 병합해 p95를 계산한다.
   - `histogram-merge` golden fixture를 통과한다.
   - UI/client-side p95 계산을 금지한다.
2. Insight rule engine
   - MVP rule set과 guard, ranking, max 3 노출을 구현한다.
3. App triage summary read model
   - state, rationale, core metrics, triage cards, zero-insight reason, recovery guidance를 반환한다.
4. Endpoint priority read model
   - slow/error/comparative evidence, confidence, freshness 기반 목록을 만든다.
5. Dashboard query API
   - `read-model-contract` contract를 반환한다.

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

## Cross-Epic Acceptance Criteria

- MVP 필수 경로에 Prometheus 설치, scrape config, selector 등록, PromQL query가 없다.
- host app request path는 portal 장애에 의해 막히지 않는다.
- p95 source of truth는 server-side histogram merge다.
- first-screen state와 triage 문구는 dashboard read model에서 온다.
- 아키텍처 스타일은 Lightweight Hexagonal 하나다.
- `triageCards=[]`이면 zero-insight reason과 recommended action이 반드시 있다.
- endpoint priority는 rank, reason, evidence, confidence, freshness를 포함한다.

## AC Traceability Matrix

| AC | Epic | Contract | Package Boundary | Planned Test |
|---|---|---|---|---|
| Lightweight Hexagonal only | Epic 1 | `architecture.md` | `domain/application` cannot depend on `adapter` | `ArchitectureBoundaryTest` |
| No pull metric MVP path | Epic 1, 2, 6 | `metric-taxonomy.md`, `ingest-envelope.md` | starter inbound/outbound adapters only support direct ingest | `NoPrometheusMvpPathTest` |
| Host request path not blocked | Epic 2 | `architecture.md`, `ingest-envelope.md` | `adapter.in.spring` -> `application.port.in` -> bounded queue | `StarterNonBlockingIngestTest` |
| Ingest idempotency | Epic 3 | `ingest-envelope.md` | `AcceptIngestEnvelopeUseCase` + `MetricBucketStorePort` | `AcceptIngestEnvelopeUseCaseTest` |
| Server-side p95 source | Epic 5 | `histogram-merge.md` | `MergeHistogramBucketsUseCase` | `HistogramMergeGoldenFixtureTest` |
| First-screen state source | Epic 4, 5 | `state-semantics.md`, `read-model-contract.md` | `QueryDashboardSnapshotUseCase` | `DashboardReadModelSnapshotTest` |
| 0-insight is explicit | Epic 5 | `read-model-contract.md` | `BuildAppTriageSummaryUseCase` | `ZeroInsightReadModelTest` |
| Endpoint priority is explainable | Epic 5 | `insight-rules.md`, `read-model-contract.md` | `ListEndpointPriorityUseCase` | `EndpointPriorityReadModelTest` |
| Demo promise | Epic 6 | `read-model-contract.md` | starter + portal e2e | `FirstBucketToAliveE2ETest` |
