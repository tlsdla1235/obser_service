---
artifactType: acceptance-traceability
architectureStyle: Lightweight Hexagonal
status: party-mode-fixes-applied
date: 2026-05-08
---

# Acceptance Traceability

## Purpose

이 문서는 파티모드 검증에서 나온 필수 지적을 구현 추적 단위로 고정한다.

## Matrix

| Acceptance Criterion | Epic | Contract | Package/Use Case | Planned Test |
|---|---|---|---|---|
| 아키텍처 스타일은 Lightweight Hexagonal 하나다 | Epic 1 | `architecture.md` | `starter.domain`, `starter.application`, `portal.domain`, `portal.application` | `ArchitectureBoundaryTest` |
| 기존 PRD/UX/spec은 재작성하지 않고 아키텍처 결정만 재산출한다 | Epic 1 | `architecture-rewrite-index.md` | planning artifact boundary | document review |
| MVP 필수 경로에는 pull metric backend, scrape config, arbitrary query UI가 없다 | Epic 1, 2, 6 | `metric-taxonomy.md`, `ingest-envelope.md` | starter direct ingest path | `NoPrometheusMvpPathTest` |
| host app request path는 portal 장애로 막히지 않는다 | Epic 2 | `architecture.md` | `RecordHttpObservationPort`, `BoundedQueuePort`, `IngestClientPort` | `StarterNonBlockingIngestTest` |
| ingest envelope는 idempotent하게 수용된다 | Epic 3 | `ingest-envelope.md` | `AcceptIngestEnvelopeUseCase`, `MetricBucketStorePort` | `AcceptIngestEnvelopeUseCaseTest` |
| p95는 server-side histogram merge 결과다 | Epic 5 | `histogram-merge.md` | `MergeHistogramBucketsUseCase` | `HistogramMergeGoldenFixtureTest` |
| UI는 state/rule/endpoint priority를 재계산하지 않는다 | Epic 1, 5, 6 | `read-model-contract.md` | `QueryDashboardSnapshotUseCase` | `DashboardReadModelSnapshotTest` |
| `triageCards=[]`는 빈 화면이 아니라 zero-insight reason을 가진다 | Epic 5 | `read-model-contract.md` | `BuildAppTriageSummaryUseCase` | `ZeroInsightReadModelTest` |
| stale/down/recovery는 사용자 행동으로 이어진다 | Epic 4, 5 | `state-semantics.md`, `read-model-contract.md` | `EvaluateLifecycleStateUseCase` | `RecoveryReadModelTest` |
| endpoint priority는 rank, reason, evidence, confidence, freshness를 가진다 | Epic 5 | `read-model-contract.md`, `insight-rules.md` | `ListEndpointPriorityUseCase` | `EndpointPriorityReadModelTest` |
| 첫 화면은 alive / slow / error / where to look first를 답한다 | Epic 5, 6 | `read-model-contract.md` | dashboard read model | `FirstScreenContractE2ETest` |
| portal physical schema는 catalog부터 구현 가능하고 DB comment를 포함한다 | Epic 1 | `database-schema.md` | `adapter.out.persistence.catalog`, migration | `MigrationSchemaCommentTest` |
