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
| route attribution은 `http.route`/framework route template 최우선, raw path candidate의 query 폐기 후 allowlist 일시 입력, exact-one allowlist template, 그 외 `UNKNOWN`을 따른다. query key/value와 raw detail은 route/tag/metric key/payload/log/rollup/read model에 남기지 않는다 | Epic 2, 3, 5 | `metric-taxonomy.md`, `ingest-envelope.md`, `read-model-contract.md` | `MicrometerHttpServerObservationBinder`, `LowCardinalityHttpObservationGuard`, `RouteNormalizationService` | `MicrometerHttpServerObservationBinderTest`, `LowCardinalityHttpObservationGuardTest`, `RouteNormalizationServiceTest` |
| host app build/startup/request path는 portal 장애로 막히지 않는다 | Epic 2 | `architecture.md`, `starter-failure-semantics.md` | `HttpObservationCollectionService`, `BoundedMetricQueue`, `PortalIngestHttpClient` | `StarterNonBlockingIngestTest` |
| project key는 active project로만 검증되고 raw key를 저장하거나 repository lookup surface에 남기지 않는다 | Epic 3 / Story 3.1 | `api-surface.md`, `database-schema.md` | `ProjectKeyVerificationService`, `ProjectRepository` | `ProjectKeyVerificationServiceTest` |
| 신규 사용자는 GitHub OAuth 성공 후에만 내부 account가 생성되거나 기존 GitHub identity와 연결된다 | Epic 6 / Account Auth | `account-auth-policy.md`, `api-surface.md` | `AccountAuthService`, `ExternalIdentityRepository` | `AccountAuthPolicyTest` |
| GitHub OAuth 실패 또는 취소 시 account row를 생성하지 않는다 | Epic 6 / Account Auth | `account-auth-policy.md`, `api-surface.md` | `AccountAuthService` | `GithubOAuthFailureTest` |
| email/password, magic link, GitHub 외 provider, anonymous signup은 MVP에서 지원하지 않는다 | Epic 6 / Account Auth | `account-auth-policy.md`, `api-surface.md` | `AccountAuthController`, `AccountAuthService` | `UnsupportedSignupMethodTest` |
| 일반 resource API response/log/error는 access token, refresh token, GitHub OAuth token, provider raw payload, secret을 노출하지 않는다 | Epic 6 / Account Auth | `account-auth-policy.md`, `architecture.md` | `AccountAuthController`, `ServiceTokenService`, logging/error mapping | `AuthSecretExposureGuardTest` |
| Auth token issuance/refresh response의 token 전달 channel은 별도 승인 전까지 구현하지 않는다 | Epic 6 / Account Auth | `account-auth-policy.md`, `api-surface.md` | `AccountAuthController`, `ServiceTokenService` | `AuthTokenDeliveryDecisionTest` |
| MVP API 인증은 cookie 기반 server session 없이 Bearer access token/JWT와 refresh token rotation/token store 기준을 사용한다 | Epic 6 / Account Auth | `account-auth-policy.md`, `architecture.md` | `ServiceTokenService`, `RefreshTokenStore` | `ServiceTokenPolicyTest` |
| portal ingest validation은 starter `schemaVersion: 1.0` 계약을 mirror한다 | Epic 3 / Story 3.2 | `ingest-envelope.md`, `metric-taxonomy.md`, `time-buckets.md` | `IngestAcceptanceService` | `PortalIngestValidationFixtureTest` |
| accepted metric bucket은 PostgreSQL에 idempotency metadata와 함께 저장된다 | Epic 3 / Story 3.3 | `database-schema.md`, `ingest-envelope.md` | `MetricBucketRepository`, `ApplicationCatalogService` | `MetricBucketRepositoryIntegrationTest` |
| duplicate ingest는 같은 payload success, 다른 payload conflict로 수렴한다 | Epic 3 / Story 3.4 | `ingest-envelope.md`, `api-surface.md`, `database-schema.md` | `IngestAcceptanceService`, `MetricBucketRepository` | `DuplicateIngestAcceptanceTest` |
| p95/p99는 starter canonical percentile source를 따른다 | Epic 5 | `ingest-envelope.md`, `histogram-merge.md` | `DashboardReadModelService` | `StarterCanonicalPercentileReadModelTest` |
| UI는 state/rule/p95/p99/endpoint priority를 재계산하지 않는다 | Epic 1, 5, 6 | `read-model-contract.md`, `histogram-merge.md` | `DashboardReadModelService`, `HistogramMergeService` | `DashboardReadModelSnapshotTest` |
| `triageCards=[]`는 빈 화면이 아니라 zero-insight reason을 가진다 | Epic 5 | `read-model-contract.md` | `TriageSummaryService` | `ZeroInsightReadModelTest` |
| stale/down/recovery는 사용자 행동으로 이어진다 | Epic 4, 5 | `state-semantics.md`, `read-model-contract.md` | `LifecycleStateService` | `RecoveryReadModelTest` |
| endpoint priority는 rank, reason, evidence, confidence, freshness를 가진다 | Epic 5 | `read-model-contract.md`, `insight-rules.md` | `EndpointPriorityService` | `EndpointPriorityReadModelTest` |
| Operational event history는 raw snapshot explorer가 아니라 bounded recent event surface다 | Epic 5, 6 | `operational-event-history.md`, `time-buckets.md`, `database-schema.md` | `OperationalEventHistoryService` candidate + `DashboardSnapshotRepository` | `OperationalEventHistoryReadModelTest` |
| Recent history UI는 event를 표시하고 snapshot deep link를 열 뿐 판단을 재계산하지 않는다 | Epic 6 | `operational-event-history.md`, `read-model-contract.md`, `api-surface.md` | dashboard UI + history API read model | `RecentHistoryUiContractTest` |
| 첫 화면은 alive / slow / error / where to look first를 답한다 | Epic 5, 6 | `read-model-contract.md` | dashboard read model | `FirstScreenContractE2ETest` |
| portal physical schema는 catalog부터 구현 가능하고 DB comment를 포함한다 | Epic 1 | `database-schema.md` | `repository.catalog`, migration | `MigrationSchemaCommentTest` |
| controller는 repository를 직접 호출하지 않는다 | Epic 1 | `architecture.md` | controller -> service -> repository | `MvcLayerBoundaryTest` |
| repository는 controller DTO를 참조하지 않는다 | Epic 1 | `architecture.md` | repository isolation | `MvcLayerBoundaryTest` |
