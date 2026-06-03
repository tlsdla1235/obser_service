---
artifactType: acceptance-traceability
architectureStyle: Traditional MVC
status: dashboard-alignment-updated
date: 2026-05-25
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
| route attribution은 `route-attribution-policy.md`를 source of truth로 따른다. `http.route` 정규화 실패 후에도 low-cardinality `uri`/`path` raw 후보 fallback을 볼 수 있으며, normalized route는 policy가 허용한 safe template, safe prefix collapse 결과, allowlist template, `UNKNOWN`으로만 제한한다. query key/value와 raw detail은 route/tag/metric key/payload/log/rollup/read model에 남기지 않는다 | Epic 2, 3, 5 | `route-attribution-policy.md`, `metric-taxonomy.md`, `ingest-envelope.md`, `read-model-contract.md` | `MicrometerHttpServerObservationBinder`, `LowCardinalityHttpObservationGuard`, `RouteNormalizationService` | `MicrometerHttpServerObservationBinderTest`, `LowCardinalityHttpObservationGuardTest`, `RouteNormalizationServiceTest` |
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
| Project Entry와 Application List는 scope 선택/light summary surface이며 Application Dashboard 판단을 대체하지 않는다 | Epic 5, 6 | `current-product-source-of-truth.md`, `api-surface.md` | `ProjectApplicationNavigationService` candidate | `DashboardNavigationReadModelTest` |
| Instance Detail은 application 판단을 대체하지 않는 evidence drill-down이다 | Epic 5, 6 | `read-model-contract.md`, `api-surface.md` | `InstanceEvidenceReadModelService` candidate | `InstanceEvidenceReadModelTest` |
| Operational event history는 raw snapshot explorer가 아니라 bounded recent event surface다 | Epic 5, 6 | `operational-event-history.md`, `time-buckets.md`, `database-schema.md` | `OperationalEventHistoryService` candidate + `DashboardSnapshotRepository` | `OperationalEventHistoryReadModelTest` |
| Instance snapshot trend는 stored dashboard snapshot/read model projection이며 raw bucket explorer나 endpoint timeseries가 아니다 | Epic 5, 6 | `read-model-contract.md`, `operational-event-history.md`, `api-surface.md` | `InstanceSnapshotTrendService` candidate + `DashboardSnapshotRepository` | `InstanceSnapshotTrendProjectionTest` |
| Recent history UI는 event를 표시하고 snapshot deep link를 열 뿐 판단을 재계산하지 않는다 | Epic 6 | `operational-event-history.md`, `read-model-contract.md`, `api-surface.md` | dashboard UI + history API read model | `RecentHistoryUiContractTest` |
| 첫 화면은 alive / slow / error / where to look first를 답한다 | Epic 5, 6 | `read-model-contract.md` | dashboard read model | `FirstScreenContractE2ETest` |
| portal physical schema는 catalog부터 구현 가능하고 DB comment를 포함한다 | Epic 1 | `database-schema.md` | `repository.catalog`, migration | `MigrationSchemaCommentTest` |
| controller는 repository를 직접 호출하지 않는다 | Epic 1 | `architecture.md` | controller -> service -> repository | `MvcLayerBoundaryTest` |
| repository는 controller DTO를 참조하지 않는다 | Epic 1 | `architecture.md` | repository isolation | `MvcLayerBoundaryTest` |

## Epic 10 Acceptance Evidence - 2026-06-02

Story 10.7 acceptance gate는 Spring-served origin `http://127.0.0.1:8080`에서 built jar와 local acceptance PostgreSQL로 검증했다. 새 제품 기능, backend endpoint/controller/service/repository/migration, frontend auth/API/read-model behavior 변경은 수행하지 않았다.

| Evidence Area | Result | Evidence |
|---|---|---|
| Command/build/jar | Pass | `npm run typecheck`, `npm run build`, `./gradlew :observability-portal:bootJar --rerun-tasks` 성공. jar에 `BOOT-INF/classes/static/index.html`, `assets/index-132Tghla.js`, `assets/index-C6P2CycU.css` 포함. `BOOT-INF/classes/static/dashboard` 없음. |
| Spring static route guard | Pass | `/`, `/dashboard`, `/dashboard/`, `/docs`, `/docs/`는 SPA HTML로 수렴. `/api/projects`는 unauthenticated 401 JSON. 실제 JS/CSS asset은 JS/CSS content type. 없는 asset과 `/dashboard/app.js`, `/dashboard/styles.css`는 404 JSON으로 HTML fallback shadow 없음. |
| Auth/storage/401 | Partial with blocker | GitHub authorize JSON/no-store와 popup open은 확인. 실제 GitHub login 완료, callback relay, token exchange success는 headless browser에 interactive GitHub session이 없어 blocker. Runtime Bearer header와 401 token clear/auth-required UI는 local service JWT로 확인. URL/cookie/localStorage/sessionStorage는 비어 있음. |
| Read-model/no-recompute | Pass | Project -> Application -> Dashboard -> Evidence -> Snapshot Trend/History는 server links 또는 Story 10.4의 validated endpoint template만 호출. `triageCards=[]`에서 `zeroInsight` reason/action 확인. accepted bucket axis와 starter heartbeat axis 분리 확인. recompute grep은 DTO/server field display match로 분류. |
| Credential one-time | Pass | Project create와 credential rotation의 `displayValue`는 success 직후 one-time panel에만 표시되고 close 뒤 DOM/UI state/storage에서 제거됨. Metadata/revocation response에는 raw value/hash field 없음. |
| Endpoint allow-list | Pass | Runtime calls는 existing auth/resource/read-model/credential endpoints로 제한. Dashboard UI에서 `/api/ingest/v1/buckets`, `/api/ingest/v1/heartbeat` 호출 없음. docs/setup의 ingest API 언급은 runtime call이 아님. |
