---
artifactType: story
storyId: "13.4"
storyKey: "13-4-backend-application-dashboard-read-model-shape-alignment"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "Backend Application Dashboard Read Model Shape Alignment"
architectureStyle: Traditional MVC
status: done
date: 2026-06-10
phase: P3
workType: backend
implementationScope: "Application Dashboard response DTO/service shape and snapshot read_model_json payload alignment"
productionCodeChangeThisContext: false
plannedProductionCodeChange: true
sourceOfTruthMode: read-only
dependsOn:
  - P0
  - P1
  - P2
  - 13-2-frontend-read-model-contract-guard
  - 13-3-backend-recent-30-minutes-window-alignment
blocks:
  - P4
  - P5
  - P6
  - P10
rollbackBoundary: "Application Dashboard response DTO/mapper/service shape alignment with snapshot payload compatibility"
---

# Story 13.4 - Backend Application Dashboard Read Model Shape Alignment

## Status

done

2026-06-10: P3 backend alignment story artifact를 생성했다. 이번 컨텍스트에서는 backend/frontend/source/test/migration/schema를 구현하지 않고, story artifact와 sprint-status 정렬만 수행한다.
2026-06-10: BMAD dev-story 구현을 시작했고 backend Application Dashboard read model shape alignment를 진행한다.
2026-06-10: Backend canonical read model, snapshot stored payload/detail projection source alignment, guard tests, frontend verification을 완료해 review로 전환한다.
2026-06-10: BMAD code review 후 발견된 contract/legacy compatibility gap을 보강하고 검증을 통과해 done으로 전환한다.

## Story

backend 구현자로서, Application Dashboard API와 snapshot 저장 payload가 같은 `dashboard_read_model.v1` canonical shape를 제공하도록 read model response를 Source of Truth contract에 맞추고 싶다.

그래야 P4/P6 frontend realignment가 lifecycle state, endpoint priority, resource pattern, p95/p99, snapshot detail state를 재계산하지 않고 live dashboard와 stored snapshot detail을 같은 의미로 읽을 수 있다.

## Source of Truth

아래 문서는 이 story의 기준이다. 이 story는 해당 문서의 의미를 재정의하지 않고 backend 구현 지침으로만 옮긴다.

1. `implementation-artifacts/sprint-status.yaml`
2. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
3. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
4. `planning-artifacts/stories/13-doc-1-alignment-epic-story-creation.md`
5. `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`
6. `planning-artifacts/stories/13-3-backend-recent-30-minutes-window-alignment.md`
7. `planning-artifacts/contracts/read-model-contract.md`
8. `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
9. `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
10. `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
11. `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
12. `_bmad/custom/project-context.md`
13. `planning-artifacts/architecture.md`
14. `planning-artifacts/architecture-implementation-supplement.md`
15. `planning-artifacts/project-structure.md`

## Background

Epic 13은 완료된 Epic 4/5/6/10 story를 다시 여는 작업이 아니라, 확정된 Dashboard Source of Truth를 새 alignment story 묶음으로 적용하는 tracking epic이다. Story 13.2는 frontend guard를 완료했고, Story 13.3은 Application Dashboard live와 Instance Evidence live를 `recent_30_minutes` accepted bucket window로 정렬했다.

P3의 목적은 window 정렬 이후의 Application Dashboard API/read model shape를 닫는 것이다. 현재 backend는 `ApplicationDashboardReadModel`에 `generatedAt`, `application.sourceWindow`, `state`, `starterConnection`, `zeroInsight`, `recovery`, `metrics`, `sourceScopedPercentiles`, `histogramDistribution`, `triageCards`, `endpointPriority`, `instances`, `snapshot` 중심 skeleton을 제공한다. Source of Truth는 이 shape를 `dashboard_read_model.v1` canonical read model로 안정화해 live response와 `dashboard_snapshots.read_model_json` 저장 payload가 공유하도록 요구한다.

현재 코드 관찰 기준으로 P3에서 특히 조심해야 할 gap은 아래다.

- `ApplicationDashboardReadModel`은 `schemaVersion`, `mode`, canonical `window`, `thresholds`, `operatorSummary`, `dataQuality`, `signals`, `stateReasons`, `attentionEvidence`, `firstLookCandidates`, `readSemantics`를 아직 top-level canonical shape로 제공하지 않는다.
- Story 13.3은 `application.sourceWindow.baseline()`을 public read model에서 `null`로 허용하고, snapshot writer는 schema compatibility 때문에 not-null helper column에 직전 30분 window를 채운다. 이 helper value는 MVP 판단 근거가 아니다.
- Snapshot detail wrapper는 stored `read_model_json` projection을 반환하지만 `readSemantics` 언어가 frontend guard와 완전히 같지 않다. P1 guard는 `markerIsStateSource=false`를 요구하며, P3/P6는 `readSemantics.source`, `snapshotDetailRecalculates=false`, stored read model source를 명확히 해야 한다.
- Endpoint evidence의 `bucketDistributionSource`는 현재 backend/frontend guard에서 `accepted_bucket`을 사용하지만, 일부 contract/test fixture는 `histogram_bucket_distribution`을 사용한다. P3 구현자는 Source of Truth와 P1 frontend guard가 이해할 수 있는 단일 계약 또는 명시적 compatibility mapping을 조사해 닫아야 한다.

## Aligns

- `5-2-application-dashboard-read-model-skeleton`: Application Dashboard가 server-computed read model이라는 경계를 유지하되, legacy skeleton field를 Source of Truth canonical shape로 정렬한다.
- `5-3-source-scoped-percentile-and-histogram-distribution-read-model`: starter canonical p95/p99 source와 histogram distribution display boundary를 유지한다.
- `5-4-triage-summary-and-zero-insight-recovery-mapping`: triage/zeroInsight/recovery 결과를 UI 계산이 아니라 backend 판단 결과로 유지하되 `stateReasons`, `attentionEvidence`, `firstLookCandidates`, `operatorSummary`, `dataQuality`로 의미를 분리한다.
- `5-5-endpoint-priority-read-model`: endpoint priority order/rank/reason/evidence는 backend-computed value로 유지하고 frontend가 재정렬하지 않게 한다.
- `5-8-b-snapshot-marker-detail-recovery-source`: marker/helper column은 snapshot detail state/evidence source가 아니며, detail은 stored `read_model_json` 복원 source라는 경계를 강화한다.
- `10-3-wire-types-adapters-navigation-and-dashboard`: Vite SPA가 server-provided dashboard link/order/value를 보존하는 흐름을 유지한다.
- `13-2-frontend-read-model-contract-guard`: P1 guard가 요구하는 source/order/readSemantics 계약을 backend response/test로 만족시킨다.
- `13-3-backend-recent-30-minutes-window-alignment`: P2가 닫은 `recent_30_minutes`, baseline-free primary judgment, starter percentile/histogram boundary를 canonical shape로 옮긴다.

## Supersedes

아래 항목은 완료 story 본문이나 이력을 바꾸지 않고, P3 이후 backend 구현에서 좁게 대체하는 해석이다.

- UI가 `application.sourceWindow.current/baseline`, `triageCards`, `endpointPriority` 조합만으로 Application Dashboard 의미를 이해해야 한다는 해석.
- Snapshot detail이 live dashboard와 다른 compact projection이어서 lifecycle state, endpoint priority, attention evidence, first look order를 current metric이나 helper column으로 보완해도 된다는 해석.
- `dashboard_snapshots` helper column 또는 marker bucket을 stored snapshot detail의 state/evidence source처럼 읽는 해석.
- baseline comparison이 lifecycle state, operator summary, state reasons, attention evidence, first look candidates의 primary 판단 근거라는 해석.
- endpoint histogram source 문자열이 backend, frontend guard, Source of Truth 문서 사이에서 다르게 남아도 된다는 해석.

## Hardens

- Live Application Dashboard source는 `accepted_metric_buckets`와 `recent_30_minutes` window로 명시된다.
- `dashboard_read_model.v1`은 live response와 snapshot 저장 payload가 공유할 canonical shape가 된다.
- Snapshot detail은 `dashboard_snapshots.read_model_json` 저장본 복원이며 current metric, current threshold, current route metadata, current starter 상태를 다시 조인하지 않는다.
- `readSemantics.source`, `snapshotDetailRecalculates=false`, `markerIsStateSource=false`, baseline not used semantics가 response/test guard로 확인된다.
- UI가 lifecycle state, endpoint priority, resource pattern, p95/p99, snapshot detail state를 재계산해야만 이해되는 field가 남지 않게 한다.
- p95/p99는 starter canonical percentile source/scope를 유지하고 histogram bucket에서 계산하지 않는다.

## Rollback

- rollback 단위는 Application Dashboard response DTO/model, service mapper, snapshot `read_model_json` serialization/enrichment, snapshot detail readSemantics compatibility 변경이다.
- 기존 legacy field를 제거하기 전에는 frontend P1 guard와 controller serialization test를 먼저 통과시키고, 필요한 경우 compatibility alias를 한시적으로 둔다.
- snapshot writer identity(`application_id + current_window_end_utc`)와 persisted capture reason token rename은 이번 story의 rollback 단위가 아니다.
- migration/schema 변경은 기본 scope가 아니다. 구현 조사 중 schema 변경이 절대 필요하다고 증명되면 이번 story에서 진행하지 말고 별도 승인/후속 story로 분리한다.
- rollback은 Source of Truth 문서 수정, 완료 story 본문 수정, frontend guard 완화로 처리하지 않는다.

## Out of scope

- 이번 컨텍스트의 backend/frontend/source code/test/migration/schema 구현.
- Source of Truth 문서 수정 또는 의미 재정의.
- 완료 story 파일 본문 수정 또는 done 상태 변경.
- P4 frontend Application Dashboard IA 재배치.
- P5 scheduled snapshot 30분 cadence/horizon 정렬.
- P6 snapshot history/detail frontend 재정렬.
- P7/P8 Instance Dashboard live/snapshot mode split과 frontend surface split.
- P9 retention cleanup.
- frontend code 수정.
- migration/schema 변경. 단, create-story 조사 중 절대 필요성이 보이면 story implementation notes에 별도 승인/후속 story로 분리하라고 기록한다.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.

## Acceptance Criteria

1. Given Application Dashboard live response를 직렬화할 때, Then Source of Truth의 `dashboard_read_model.v1` 핵심 shape인 `schemaVersion`, `mode`, `generatedAt`, `application`, `window`, `thresholds`, `operatorSummary`, `dataQuality`, `starterConnection`, `signals`, `state`, `stateReasons`, `attentionEvidence`, `firstLookCandidates`, `readSemantics`가 제공된다.
2. Given live response를 생성할 때, Then `mode=live`, `readSemantics.source=accepted_metric_buckets`, `window.type=recent_30_minutes`, `bucket_end_utc > window.startUtc and bucket_end_utc <= window.endUtc` 의미가 response와 test에서 명확하다.
3. Given snapshot writer가 dashboard snapshot을 저장할 때, Then 저장 payload는 live response와 같은 canonical `dashboard_read_model.v1` shape를 공유할 수 있고 `dashboard_snapshots.read_model_json`에 저장된 shape가 snapshot detail 복원 source가 된다.
4. Given snapshot detail API를 조회할 때, Then detail은 stored `dashboard_snapshots.read_model_json`을 복원하며 current metric, current threshold, current route metadata, current starter heartbeat를 조인해 state/evidence를 재계산하지 않는다.
5. Given response `readSemantics`를 검증할 때, Then live/snapshot 모두 `source`, `snapshotDetailRecalculates=false`, `markerIsStateSource=false`, `baselineComparisonUsedForMvpDecision=false` 또는 동등한 baseline not used semantics를 노출하고 backend/frontend guard가 같은 의미로 이해한다.
6. Given lifecycle state, operator summary, state reasons, attention evidence, first look candidates를 만들 때, Then baseline comparison은 primary 판단 근거가 아니며 baseline/helper compatibility field는 limitation 또는 metadata로만 남는다.
7. Given marker/history helper field가 존재할 때, Then `state_code`, `markerBucket`, `captureReason`, `primary_rule_id`, `primary_endpoint_key`, `max_confidence`는 search/index/helper field로만 사용되고 snapshot detail state/evidence source가 아니다.
8. Given p95/p99가 response에 포함되거나 snapshot payload에 저장될 때, Then source는 starter canonical percentile이고 scope/display policy는 source-scoped point/series를 유지한다. Histogram bucket은 distribution display와 slow-share evidence에만 사용되며 p95/p99, average/max latency, regression 판단을 계산하지 않는다.
9. Given endpoint/histogram evidence를 제공할 때, Then `bucketDistributionSource`의 `accepted_bucket` vs `histogram_bucket_distribution` 계약을 조사해 Source of Truth와 P1 frontend guard가 이해할 수 있는 단일 canonical value 또는 명시적 compatibility mapping으로 닫고, controller/model/frontend guard fixture가 같은 의미를 검증한다.
10. Given frontend가 Application Dashboard, snapshot detail, endpoint evidence를 소비할 때, Then UI가 lifecycle state, endpoint priority, resource pattern, p95/p99, snapshot detail state를 재계산해야만 이해되는 필드 공백이나 모순이 남지 않는다.
11. Given implementation이 완료되면, Then `cd frontend && npm run guard:read-model-contract`, `cd frontend && npm run typecheck`, relevant backend controller/service/model/snapshot tests, `git diff --check`, `git status --short`가 통과해야 한다.
12. Given implementation diff를 검토할 때, Then Source of Truth 문서, 완료 story 파일, frontend code, migration/schema, 기존 untracked `dbml-error.log`가 변경되지 않았음이 verification에 포함된다. migration/schema 변경이 포함되면 story scope 위반이므로 별도 승인/후속 story로 분리되어야 한다.

## Tasks / Subtasks

- [x] 현재 Application Dashboard response shape와 Source of Truth canonical shape gap을 조사한다. (AC: 1, 2, 5, 10)
  - [x] `ApplicationDashboardReadModel`의 top-level legacy skeleton field와 `dashboard_read_model.v1` required field를 매핑한다.
  - [x] `application.sourceWindow.current/recent_30_minutes/baseline` compatibility path와 canonical `window` field를 어떻게 병존 또는 전환할지 정리한다.
  - [x] `thresholds`, `operatorSummary`, `dataQuality`, `signals`, `stateReasons`, `attentionEvidence`, `firstLookCandidates`, `readSemantics`의 service source를 기존 state/triage/endpoint/resource 결과에서 연결한다.
- [x] Canonical `dashboard_read_model.v1` response/model을 구현한다. (AC: 1, 2, 5, 6, 8, 10)
  - [x] `schemaVersion=dashboard_read_model.v1`, `mode=live`, `window.type=recent_30_minutes`, `readSemantics.source=accepted_metric_buckets`를 public response에 고정한다.
  - [x] threshold 값은 Source of Truth MVP threshold와 같은 값을 response에 포함해 snapshot detail이 저장 당시 기준을 재현할 수 있게 한다.
  - [x] `stateReasons`와 `attentionEvidence`를 분리해 state-changing reason과 attention-only endpoint/resource/data-quality evidence를 UI가 다시 판단하지 않게 한다.
  - [x] `firstLookCandidates`는 최대 3개 bounded queue로 만들고 deterministic priority reason을 보존한다.
- [x] Live response와 snapshot 저장 payload의 canonical shape 공유를 닫는다. (AC: 3, 4, 5, 7)
  - [x] `DashboardSnapshotWriterService`가 저장하는 `read_model_json`이 canonical shape를 보존하는지 확인한다.
  - [x] `DashboardSnapshotReadModelEnricher`와 projection parser가 endpoint evidence, instance summary, helper column을 canonical shape에서 추출하되 raw JSON escape hatch를 만들지 않도록 유지한다.
  - [x] `DashboardSnapshotDetailService`와 detail read model에 stored read model source, recalculation 금지, marker/helper non-source semantics를 response field와 test로 명시한다.
  - [x] public baseline이 `null`인 response와 not-null snapshot helper column compatibility path가 판단 근거로 노출되지 않도록 regression test를 둔다.
- [x] Endpoint/histogram source string 계약을 명확히 닫는다. (AC: 8, 9, 10)
  - [x] `bucketDistributionSource=accepted_bucket`와 `histogram_bucket_distribution`이 각각 "metric live source"와 "distribution evidence derivation" 중 무엇을 뜻하는지 Source of Truth/contract/frontend guard 기준으로 조사한다.
  - [x] canonical value를 정하거나 compatibility alias를 둔다면 backend model validation, controller JSON, snapshot stored payload, frontend guard fixture가 같은 의미를 검사하게 한다.
  - [x] histogram distribution은 p95/p99 source가 아니라 display/slow-share evidence source임을 test로 보호한다.
- [x] Backend test guard를 보강한다. (AC: 1~12)
  - [x] `DashboardControllerTest`에 canonical shape, `mode=live`, `window.type=recent_30_minutes`, `readSemantics.source=accepted_metric_buckets`, baseline not used semantics를 추가한다.
  - [x] `DashboardReadModelServiceTest`에 `sourceWindow.baseline == null` public response와 snapshot compatibility helper column non-primary path를 검증한다.
  - [x] `EndpointPriorityReadModelShapeTest` 또는 관련 model test에 endpoint/histogram source string 계약을 추가한다.
  - [x] `DashboardSnapshotWriterServiceTest`/integration test에 canonical `read_model_json` 저장 shape와 helper column non-source semantics를 추가한다.
  - [x] `DashboardSnapshotDetailServiceTest`/controller test에 `markerIsStateSource=false`, `snapshotDetailRecalculates=false`, stored `read_model_json` source를 추가한다.
- [x] Frontend guard와 보호 대상 verification을 실행한다. (AC: 11, 12)
  - [x] P1 guard `cd frontend && npm run guard:read-model-contract`가 backend shape 변경 후 통과하는지 확인한다.
  - [x] `cd frontend && npm run typecheck`로 frontend type/guard 계약 drift를 확인한다.
  - [x] relevant backend tests와 `git diff --check`를 실행한다.
  - [x] Source of Truth 문서, 완료 story 파일, frontend code, migration/schema, `dbml-error.log`가 변경되지 않았는지 `git status --short`와 diff path로 확인한다.

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. Portal package는 `com.observation.portal.domain` feature-first MVC 구조를 따른다.
- Controller는 serialization/status mapping만 맡고 service를 호출한다. Repository/JPA entity는 public API response DTO나 service external return model로 직접 노출하지 않는다.
- Flyway migration이 schema source of truth다. 이번 story의 기본 범위는 migration/schema 변경 없음이다.
- P2 완료 기준으로 `TimeBucketWindowCalculator`와 `DashboardTimeWindow`는 recent 30 minutes current window를 제공하고, public `ApplicationDashboardReadModel.Application.SourceWindow.baseline`은 null을 허용한다.
- 현재 `ApplicationDashboardReadModel`은 기존 read-model-contract skeleton이다. P3 implementation은 이 record를 확장하거나 canonical response DTO/model을 별도로 추가하되, UI가 legacy skeleton을 조합해 state/order/source를 해석해야 하는 상태를 남기지 않는다.
- `DashboardReadModelService`는 current aggregate, histogram, endpoint evidence, percentile rows, runtime ratio, heartbeat를 조립해 response를 만든다. P3 구현자는 기존 service 결과를 재사용하되 threshold/operator summary/data quality/signals/state reason/attention evidence/first look/read semantics mapping을 service layer 안에서 닫아야 한다.
- `TriageSummaryService`와 `EndpointPriorityService`는 13.3 이후 baseline comparison primary path를 제거했다. P3에서 baseline field가 남더라도 canonical response에는 `baselineComparisonUsedForMvpDecision=false` 또는 동등한 limitation으로 표현한다.
- `DashboardSnapshotWriterService`는 public baseline null을 지원하면서 기존 not-null `baseline_window_*` helper column에는 직전 30분 compatibility window를 채운다. 이 helper value는 MVP 판단 근거가 아니며 snapshot detail source도 아니다.
- `DashboardSnapshotDetailReadModel.SnapshotReadSemantics`는 현재 `stored_snapshot_detail`, `currentStateRecalculated=false`, `liveSourcesJoined=[]`, `rawReadModelJsonExposed=false`를 고정한다. P1 frontend guard는 추가로 `markerIsStateSource=false`를 기대하므로 P3/P6 구현에서 backend/frontend contract를 같은 field 언어로 맞춘다.
- `bucketDistributionSource`는 현재 backend endpoint evidence와 frontend fixture에서 `accepted_bucket`을 쓰지만, 일부 contract/test fixture에는 `histogram_bucket_distribution`이 남아 있다. 이 drift는 13.4의 명시 조사/정리 대상이다.
- p95/p99는 `sourceScopedPercentiles.source=starter_canonical_percentile`, `scope=instance_bucket`, `aggregatePolicy=no_average_no_max_no_merge_no_histogram_recalculation` 정책을 유지한다.
- Snapshot detail은 `dashboard_snapshots.read_model_json` stored read model 복원 surface다. current accepted bucket fallback, live source join, raw JSON explorer, marker-as-state behavior를 만들지 않는다.

### Candidate Implementation File List

- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/TriageSummaryService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointPriorityService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointEvidenceAggregationService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/controller/DashboardController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotReadModelEnricher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParser.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotController.java`

### Candidate Test File List

- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/TriageSummaryServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/EndpointPriorityServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/model/EndpointPriorityReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterServiceIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParserTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotReadModelEnricherCutoffTest.java`

### Suggested Verification Commands

```bash
./gradlew :observability-portal:test \
  --tests 'com.observation.portal.domain.dashboard.controller.DashboardControllerTest' \
  --tests 'com.observation.portal.domain.dashboard.service.DashboardReadModelServiceTest' \
  --tests 'com.observation.portal.domain.dashboard.service.TriageSummaryServiceTest' \
  --tests 'com.observation.portal.domain.dashboard.service.EndpointPriorityServiceTest' \
  --tests 'com.observation.portal.domain.dashboard.model.EndpointPriorityReadModelShapeTest' \
  --tests 'com.observation.portal.domain.snapshot.controller.DashboardSnapshotControllerTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotWriterServiceTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotWriterServiceIntegrationTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailProjectionParserTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailServiceTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotReadModelEnricherCutoffTest'
cd frontend && npm run guard:read-model-contract
cd frontend && npm run typecheck
git diff --check
git status --short
```

## References

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/epics.md#Epic 13. Dashboard Source of Truth Realignment`
- `planning-artifacts/stories/13-doc-1-alignment-epic-story-creation.md`
- `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`
- `planning-artifacts/stories/13-3-backend-recent-30-minutes-window-alignment.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
- `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
- `_bmad/custom/project-context.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/project-structure.md`

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- `git status --short` / `git diff --stat`: 기존 변경과 untracked `dbml-error.log` 확인 후 이어서 작업했다.
- `./gradlew :observability-portal:test --tests ...`: targeted backend dashboard/snapshot suite 통과.
- `cd frontend && npm run guard:read-model-contract`: frontend P1 read model guard 통과.
- `cd frontend && npm run typecheck`: frontend typecheck 통과.
- `git diff --check`: whitespace diff check 통과.
- `git status --short`: 변경 범위와 `dbml-error.log` 미수정/untracked 상태 확인.

### Completion Notes List

- `ApplicationDashboardReadModel`에 `dashboard_read_model.v1` canonical top-level shape를 추가하고 legacy constructor에서 live canonical field를 파생하도록 정렬했다.
- Snapshot 저장 payload는 `mode=snapshot`, `readSemantics.source=dashboard_snapshots.read_model_json`으로 저장되며, snapshot detail parser는 stored JSON의 canonical fields를 bounded `StoredReadModel` projection으로 노출한다.
- Snapshot detail public source string을 frontend guard 의미에 맞춰 `dashboard_snapshots.read_model_json.endpointPriority`, `dashboard_snapshots.read_model_json.instanceSummary.items`, `selectionPolicy=stored_read_model`로 맞췄다. 저장 JSON 내부 `instanceSummary.schemaVersion=1.0` compatibility는 유지했다.
- `bucketDistributionSource` canonical value는 backend/frontend guard 기준의 `accepted_bucket`으로 닫고, histogram bucket은 percentile source가 아니라 display/slow-share evidence source임을 test로 보호했다.
- Public `sourceWindow.baseline == null`, snapshot helper baseline column compatibility, marker/helper non-source, snapshot detail recalculation 금지 계약을 backend tests로 보강했다.
- Source of Truth 문서, 완료 story 파일, frontend code, migration/schema는 수정하지 않았다. 기존 untracked `dbml-error.log`도 수정/삭제/stage하지 않았다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/13-4-backend-application-dashboard-read-model-shape-alignment.md`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParser.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotReadModelEnricher.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/model/EndpointPriorityReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/controller/DashboardSnapshotControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailProjectionParserTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotReadModelEnricherCutoffTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterServiceIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterServiceTest.java`

## Change Log

- 2026-06-10: Story 13.4 implementation completed and moved to review.
- 2026-06-10: Added canonical Application Dashboard read model fields and live/snapshot readSemantics.
- 2026-06-10: Aligned snapshot stored payload/detail projection source strings and strengthened backend/frontend verification.
