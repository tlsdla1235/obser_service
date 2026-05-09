---
artifactType: acceptance-traceability
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# Acceptance Traceability - MVC Version

## Purpose

이 문서는 기존 검증에서 나온 필수 지적을 MVC 구현 추적 단위로 고정한다.

## Matrix

| Acceptance Criterion | Epic | Contract | MVC Layer/Service | Planned Test |
|---|---|---|---|---|
| 아키텍처 스타일은 Traditional MVC 하나다 | Epic 1 | `architecture.md` | `controller`, `service`, `repository`, `model`, `dto` | `MvcLayerBoundaryTest` |
| 기존 PRD/UX/spec은 재작성하지 않고 아키텍처 결정만 MVC로 재산출한다 | Epic 1 | `architecture-rewrite-index.md` | planning artifact boundary | document review |
| MVP 필수 경로에는 pull metric backend, scrape config, arbitrary query UI가 없다 | Epic 1, 2, 6 | `metric-taxonomy.md`, `ingest-envelope.md` | starter direct ingest service path | `NoPrometheusMvpPathTest` |
| host app request path는 portal 장애로 막히지 않는다 | Epic 2 | `architecture.md` | `HttpObservationCollectionService`, `BoundedMetricQueue`, `PortalIngestHttpClient` | `StarterNonBlockingIngestTest` |
| ingest envelope는 idempotent하게 수용된다 | Epic 3 | `ingest-envelope.md` | `IngestAcceptanceService`, `MetricBucketRepository` | `IngestAcceptanceServiceTest` |
| p95는 server-side histogram merge 결과다 | Epic 5 | `histogram-merge.md` | `HistogramMergeService` | `HistogramMergeGoldenFixtureTest` |
| UI는 state/rule/endpoint priority를 재계산하지 않는다 | Epic 1, 5, 6 | `read-model-contract.md` | `DashboardReadModelService` | `DashboardReadModelSnapshotTest` |
| `triageCards=[]`는 빈 화면이 아니라 zero-insight reason을 가진다 | Epic 5 | `read-model-contract.md` | `TriageSummaryService` | `ZeroInsightReadModelTest` |
| stale/down/recovery는 사용자 행동으로 이어진다 | Epic 4, 5 | `state-semantics.md`, `read-model-contract.md` | `LifecycleStateService` | `RecoveryReadModelTest` |
| endpoint priority는 rank, reason, evidence, confidence, freshness를 가진다 | Epic 5 | `read-model-contract.md`, `insight-rules.md` | `EndpointPriorityService` | `EndpointPriorityReadModelTest` |
| 첫 화면은 alive / slow / error / where to look first를 답한다 | Epic 5, 6 | `read-model-contract.md` | dashboard read model | `FirstScreenContractE2ETest` |
| portal physical schema는 catalog부터 구현 가능하고 DB comment를 포함한다 | Epic 1 | `database-schema.md` | `repository.catalog`, migration | `MigrationSchemaCommentTest` |
| controller는 repository를 직접 호출하지 않는다 | Epic 1 | `architecture.md` | controller -> service -> repository | `MvcLayerBoundaryTest` |
| repository는 controller DTO를 참조하지 않는다 | Epic 1 | `architecture.md` | repository isolation | `MvcLayerBoundaryTest` |

