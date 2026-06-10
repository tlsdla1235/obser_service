---
artifactType: story
storyId: "13.8"
storyKey: "13-8-backend-instance-dashboard-live-snapshot-mode-split"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "Backend Instance Dashboard live/snapshot mode split"
architectureStyle: Traditional MVC
status: done
date: 2026-06-10
phase: P7
workType: backend
implementationScope: "Instance Dashboard backend API, service, read model, and source semantics split for live and selected Application Snapshot modes"
productionCodeChangeThisContext: false
plannedProductionCodeChange: true
sourceOfTruthMode: read-only
dependsOn:
  - P0
  - P1
  - P2
  - P3
  - P5
  - 13-2-frontend-read-model-contract-guard
  - 13-3-backend-recent-30-minutes-window-alignment
  - 13-4-backend-application-dashboard-read-model-shape-alignment
  - 13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment
blocks:
  - P8
  - P9
  - P10
rollbackBoundary: "Instance Dashboard snapshot mode route/service/readSemantics can be disabled separately from live mode and instance trend"
---

# Story 13.8 - Backend Instance Dashboard Live/Snapshot Mode Split

## Status

done

2026-06-10: P7 backend alignment story artifact를 생성하고 sprint-status를 `ready-for-dev`로 정렬했다. 이번 컨텍스트에서는 production code, backend code/test, frontend code, migration/schema, Source of Truth 문서를 구현/수정하지 않고 story artifact와 sprint-status 정렬만 수행한다.

2026-06-10: P7 backend/API/read model 구현을 완료하고 review 상태로 전환했다. 이번 구현은 Instance Dashboard live/snapshot mode backend 계약에 한정하며 frontend, migration/schema, Source of Truth 문서는 수정하지 않았다.

2026-06-10: BMAD code review 재확인 후 endpoint evidence 후보 과노출 finding을 수정하고 focused/service/full regression 검증을 통과해 done 상태로 전환했다.

## Story

backend 구현자로서, Instance Dashboard를 Application Dashboard 판단의 하위 evidence detail로 고정하고 live mode와 selected Application Snapshot 기반 snapshot mode를 API/service/read model 책임으로 분리하고 싶다.

그래야 P8 frontend instance surface split이 live detail, snapshot detail, stored instance trend를 서로 다른 source semantics로 안전하게 렌더링하고, snapshot mode가 Application Snapshot stored state/evidence를 재계산하거나 대체하지 않으면서 selected instance evidence를 현재 retention 안의 `accepted_metric_buckets` 기준으로 재구성할 수 있다.

## Source of Truth

아래 문서는 이 story의 기준이다. 이 story는 해당 문서의 의미를 재정의하지 않고 backend 구현 지침으로만 옮긴다.

1. `implementation-artifacts/sprint-status.yaml`
2. `planning-artifacts/epics.md`
3. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
4. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
5. `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
6. `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
7. `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
8. `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`
9. `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
10. `_bmad/custom/project-context.md`

## Background

Epic 13은 완료된 Epic 4/5/6/10 story를 다시 여는 작업이 아니라, 확정된 Dashboard Source of Truth를 새 alignment story 묶음으로 적용하는 tracking epic이다. Story 13.3은 Application Dashboard live와 Instance live/evidence window를 `recent_30_minutes` accepted bucket 기준으로 정렬했고, Story 13.6은 scheduled snapshot cadence와 marker/history/trend horizon을 30분 slot 및 `current_window_end_utc` 기준으로 정렬했으며, Story 13.7은 frontend snapshot history/detail을 stored Application Snapshot Dashboard 복원 surface로 격상했다.

P7의 목적은 Instance Dashboard backend 계약을 live와 snapshot으로 분리하는 것이다. Live mode는 Application Dashboard와 같은 `recent_30_minutes` window에서 selected instance evidence를 `accepted_metric_buckets`로 계산한다. Snapshot mode는 selected Application Snapshot row를 기준으로 하되, Application Snapshot stored state/evidence를 다시 계산하지 않는다. 대신 `dashboard_snapshots` row metadata의 `currentWindowStartUtc/currentWindowEndUtc` window를 읽고, 그 window 안의 selected instance metric evidence를 현재 저장소에 남아 있는 `accepted_metric_buckets`에서 cutoff 없이 재조회/재구성한다.

현재 backend에는 `InstanceEvidenceReadModelService` / `InstanceEvidenceController`가 bounded instance evidence bundle을 제공하고, `InstanceSnapshotTrendService`는 stored `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection으로 trend를 만든다. P7은 이 두 surface를 섞지 않는다. Instance Dashboard snapshot mode는 trend API가 아니며, `instanceSummary.items[]`를 필수 source로 삼지 않는다. Trend는 계속 stored summary projection으로 제한하고, snapshot detail은 selected snapshot window의 metric evidence 재구성 API로 분리한다.

## Aligns

- `5-6-instance-evidence-read-model`: selected instance의 metric, endpoint, histogram, resource, data quality evidence를 bounded read model로 제공하는 흐름을 유지한다.
- `5-7-instance-snapshot-trend-projection`: stored `instanceSummary.items[]` projection 기반 trend는 별도 surface로 유지하고, Instance Dashboard snapshot mode와 혼합하지 않는다.
- `5-8-b-snapshot-marker-detail-recovery-source`: marker/helper column은 detail state/evidence source가 아니며, Application Snapshot detail source는 stored `read_model_json`이다.
- `10-4-wire-evidence-trend-and-credential-surfaces`: frontend가 evidence/trend/source semantics를 구분해 소비하던 경계를 backend 계약으로 더 명확히 한다.
- `13-2-frontend-read-model-contract-guard`: P8 frontend guard가 `mode`, `windowSource`, `readSemantics`, `instanceSummary` source 경계를 fail-closed로 검증할 수 있게 backend shape를 명시한다.
- `13-3-backend-recent-30-minutes-window-alignment`: live Instance Dashboard window는 Application Dashboard와 같은 `recent_30_minutes`다.
- `13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment`: snapshot mode window source는 selected snapshot row의 `current_window_start_utc/current_window_end_utc` 30분 slot이다.
- `13-7-frontend-snapshot-history-detail-realignment`: 사용자가 선택한 Application Snapshot point에서 Instance Dashboard snapshot mode로 drill down할 backend 전제를 제공한다.

## Supersedes

아래 항목은 완료 story 본문이나 이력을 바꾸지 않고, P7 이후 backend 구현에서 좁게 대체하는 해석이다.

- Instance Dashboard 계약에서 기존 `current_15m` naming이나 15분 shape를 재사용해도 된다는 해석.
- selected Application Snapshot에서 Instance Dashboard detail을 열 때 `dashboard_snapshots.read_model_json.instanceSummary.items[]`가 필수 source라는 해석.
- snapshot mode에서 `accepted_at <= snapshotCutoffAt` cutoff를 적용해 late accepted metric을 의도적으로 숨겨야 한다는 해석.
- late accepted metric 때문에 Application Snapshot stored state/evidence를 재계산하거나 override해도 된다는 해석.
- marker bucket/helper column을 instance state/evidence 재판정 source로 써도 된다는 해석.
- Instance Dashboard가 Application Dashboard 판단을 대체하거나 endpoint priority를 새로 계산해 Application Dashboard priority/order를 바꿔도 된다는 해석.

## Hardens

- Instance Dashboard는 Application Dashboard 판단의 하위 evidence detail로 고정된다.
- Live mode source는 `accepted_metric_buckets`이며 window name은 `recent_30_minutes` 또는 동등한 30분 window 계약이다.
- Snapshot mode는 selected Application Snapshot row metadata에서 `currentWindowStartUtc/currentWindowEndUtc` window를 얻는다.
- Snapshot mode selected instance metric evidence는 현재 retention 안에 남은 `accepted_metric_buckets`에서 cutoff 없이 재구성한다.
- Snapshot mode read semantics는 `acceptedAtCutoffApplied=false`, `includesLateAcceptedMetrics=true`, `mayDifferFromStoredApplicationSnapshot=true` 또는 동등한 의미를 명시한다.
- Application Snapshot Detail의 stored state/evidence source는 계속 `dashboard_snapshots.read_model_json`이며, Instance Dashboard snapshot mode가 이를 검증/대체하지 않는다.
- `dashboard_snapshots.read_model_json.instanceSummary.items[]`는 Application Snapshot summary와 Instance Snapshot Trend source로 유지하되, Instance Dashboard snapshot mode의 필수 source가 아니다.
- metric retention 밖이거나 window 안 bucket이 없으면 live/current fallback 없이 `metric_missing` 또는 `not_observed_in_window` 계열 data quality로 수렴한다.

## Rollback

- rollback 단위는 Instance Dashboard snapshot mode route/service/read model/readSemantics 변경이다. live mode endpoint와 existing instance evidence/trend surface와 분리해 비활성화할 수 있어야 한다.
- snapshot mode route가 문제가 되면 selected Application Snapshot -> Instance Dashboard drill-down만 막고, Application Dashboard live, Application Snapshot detail, Instance Snapshot Trend는 되돌리지 않는다.
- rollback은 Source of Truth 문서, 완료 story 13.2/13.3/13.4/13.5/13.6/13.7 본문, migration/schema, snapshot retention policy를 되돌리지 않는다.
- cutoff 금지 guard가 실패할 때는 guard를 완화하지 말고 snapshot mode query path가 non-cutoff `accepted_metric_buckets` method를 쓰는지 수정한다.

## Out of Scope

- 이번 컨텍스트의 production code, backend code/test, frontend code, migration/schema 구현.
- Source of Truth 문서 수정 또는 의미 재정의.
- 완료 story 13.2/13.3/13.4/13.5/13.6/13.7 본문 수정 또는 done 상태 변경.
- frontend UI split 또는 P8 instance surface 구현.
- retention cleanup scheduler/service/physical delete 구현.
- top-level instance lifecycle `state`, instance health score, root cause, recovery proof, independent instance lifecycle state machine.
- markerBucket/helper column 기반 instance state/evidence 재판정.
- Application Snapshot stored read model 재계산, override, fallback 복원.
- endpoint priority 재계산 또는 Application Dashboard 판단 대체.
- 별도 `instance_dashboard_snapshots` table, instance marker timeline, arbitrary metric explorer.
- metric retention 밖 snapshot을 live/current accepted bucket으로 대체 복원하는 fallback.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.

## Acceptance Criteria

1. Given live Instance Dashboard endpoint를 호출할 때, When project/application/instance catalog path가 모두 맞으면, Then response는 `mode=live`, `readSemantics.source=accepted_metric_buckets`, `window.name=recent_30_minutes`, `windowSource=live_recent_30_minutes` 또는 동등한 30분 live 의미를 반환한다.
2. Given live mode read model을 계산할 때, When backend가 window를 선택하면, Then Application Dashboard live와 같은 recent 30 minutes window를 사용하고 `current_15m` naming을 Instance Dashboard 계약으로 노출하지 않는다.
3. Given live mode selected instance evidence를 계산할 때, When bucket source를 조회하면, Then source는 `accepted_metric_buckets`이고 heartbeat, marker, dashboard snapshot row, stored `instanceSummary.items[]`를 metric evidence source로 사용하지 않는다.
4. Given snapshot Instance Dashboard endpoint를 호출할 때, When project/application/snapshot/instance catalog path가 모두 맞으면, Then response는 `mode=snapshot`, selected Application Snapshot metadata, `windowSource=selected_application_snapshot`, `snapshotRowSource=dashboard_snapshots` 또는 동등한 의미를 반환한다.
5. Given snapshot mode가 selected Application Snapshot row를 읽을 때, When window를 결정하면, Then `dashboard_snapshots` row metadata의 `currentWindowStartUtc/currentWindowEndUtc`를 사용하고 `generatedAt`/`capturedAt`은 provenance와 tie-breaker로만 유지한다.
6. Given snapshot mode selected instance metric evidence를 조회할 때, When `accepted_metric_buckets`를 읽으면, Then `currentWindowStartUtc < bucket_end_utc <= currentWindowEndUtc` window만 적용하고 `accepted_at` cutoff를 적용하지 않는다.
7. Given snapshot mode implementation을 검증할 때, When `MetricBucketRepository` dependency를 mock/spying하면, Then `find*ApplicationInstanceIdAcceptedAtOrBefore` 계열 cutoff method는 호출되지 않고 non-cutoff selected instance query path만 호출된다.
8. Given snapshot mode response를 반환할 때, Then `readSemantics.acceptedAtCutoffApplied=false`, `readSemantics.includesLateAcceptedMetrics=true`, `readSemantics.mayDifferFromStoredApplicationSnapshot=true`, `readSemantics.applicationSnapshotRecalculated=false`, `readSemantics.instanceEvidenceReconstructedFromMetrics=true`, `readSemantics.markerIsStateSource=false` 또는 동등한 의미를 명시한다.
9. Given late accepted metric이 selected snapshot 저장 이후 수용되었지만 `bucket_end_utc`가 selected snapshot window 안에 있을 때, When snapshot mode evidence를 재구성하면, Then 해당 metric은 포함될 수 있고 이 차이는 read semantics로 드러난다. Application Snapshot stored state/evidence는 바뀌지 않는다.
10. Given selected Application Snapshot의 stored `read_model_json.instanceSummary.items[]`에 target instance가 없을 때, When target instance가 application catalog에 속하고 metric retention 안에 bucket이 있으면, Then Instance Dashboard snapshot mode는 `instanceSummary.items[]` 부재를 이유로 실패하지 않고 selected instance evidence를 재구성할 수 있다.
11. Given selected snapshot row는 retention 안에 있지만 해당 window의 selected instance bucket이 cleanup으로 삭제되었거나 원래 없을 때, When snapshot mode를 조회하면, Then live/current fallback 없이 `metric_missing` 또는 `not_observed_in_window` 계열 `observationStatus`/`dataQuality`로 수렴한다.
12. Given snapshot id가 없거나 retention 밖이거나 project/application mismatch일 때, When snapshot Instance Dashboard endpoint를 호출하면, Then 404 또는 expired/missing으로 수렴하고 live dashboard/current accepted bucket으로 복원하지 않는다.
13. Given Instance Dashboard response shape를 검토할 때, Then top-level instance lifecycle `state`, `stateCode`, `health`, `healthScore`, `rootCause`, `recoveryProof`, independent instance lifecycle state machine field를 만들지 않는다.
14. Given application state를 참조해야 할 때, Then `applicationStateRef`는 Application Dashboard/Application Snapshot owner state를 참조하는 block으로만 노출하고 `lifecycleOwner=application` 또는 동등한 의미를 유지한다.
15. Given endpoint/resource/attention evidence를 만들 때, Then endpoint priority, Application Dashboard state reason, marker bucket을 재계산해 Application Dashboard 판단을 대체하지 않는다. Resource evidence는 request symptom과 함께 있을 때만 shared pressure pattern으로 표현하고 root cause claim을 만들지 않는다.
16. Given Instance Snapshot Trend endpoint를 유지할 때, Then trend는 기존처럼 stored `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection으로 제한하고 current state, health score, snapshot mode evidence를 재계산하지 않는다.
17. Given implementation diff를 검토할 때, Then Source of Truth 문서, 완료 story 13.2/13.3/13.4/13.5/13.6/13.7, frontend code, migration/schema, cleanup physical delete, `dbml-error.log`가 변경되지 않았음이 확인된다.
18. Given 새 공개 type/helper/API 또는 동작을 바로 이해하기 어려운 내부 helper를 추가할 때, Then AGENTS.md 기준에 따라 한국어 Javadoc/comment를 작성하고, 테스트 display name과 fixture 설명도 한국어 의미를 유지한다.

## Tasks / Subtasks

- [x] 현재 Instance backend surface와 dependency 경계를 조사한다. (AC: 1~8, 13~16)
  - [x] `InstanceEvidenceReadModelService` / `InstanceEvidenceController`가 이미 제공하는 `recent_30_minutes` live evidence shape와 endpoint path를 확인한다.
  - [x] `InstanceSnapshotTrendService`가 stored `instanceSummary.items[]` projection으로만 trend를 만드는 경계를 확인하고 snapshot mode와 섞지 않는다.
  - [x] `MetricBucketRepository`의 non-cutoff selected instance query와 `AcceptedAtOrBefore` cutoff query를 분리해 snapshot mode 금지 path를 식별한다.
  - [x] `DashboardSnapshotRepository`에서 selected snapshot row metadata를 읽는 가장 작은 method가 있는지 확인한다.

- [x] Instance Dashboard API/read model shape를 live/snapshot mode로 정의한다. (AC: 1~5, 8, 13~14)
  - [x] 권장 endpoint `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/dashboard`를 live mode로 제공한다.
  - [x] 권장 endpoint `GET /api/projects/{projectId}/applications/{applicationId}/snapshots/{snapshotId}/instances/{instanceId}/dashboard`를 selected Application Snapshot 기반 snapshot mode로 제공한다.
  - [x] response에 `schemaVersion`, `mode`, `generatedAt`, `application`, `instance`, `window`, `applicationStateRef`, `observationStatus`, `applicationContribution`, `dataQuality`, `starterConnection`, `signals`, `endpointEvidence`, `resourceEvidence`, `patterns`, `snapshot`, `readSemantics`, `links`, `excludedCapabilities` 또는 Source of Truth와 동등한 bounded block을 둔다.
  - [x] top-level instance lifecycle `state`와 health/rootCause/recoveryProof 계열 field가 들어가지 않게 record validation 또는 shape test를 둔다.

- [x] Live mode service path를 Application Dashboard recent 30 minutes contract에 맞춘다. (AC: 1~3, 13~15)
  - [x] `accepted_metric_buckets` selected instance query를 사용해 `recent_30_minutes` evidence를 만든다.
  - [x] starter heartbeat는 control-plane block으로만 표시하고 metric state/observation status/application state를 바꾸지 않는다.
  - [x] 기존 `/evidence` compatibility를 유지할지 새 `/dashboard` facade로 감쌀지 결정하되, P8 frontend가 mode/readSemantics를 명확히 소비할 수 있게 한다.

- [x] Snapshot mode service path를 selected Application Snapshot row metadata와 non-cutoff metric query로 분리한다. (AC: 4~12)
  - [x] snapshot row는 project/application/snapshot path 정합성을 검증한 뒤 metadata window만 source로 삼는다.
  - [x] selected instance는 application catalog membership을 검증한다.
  - [x] selected instance metric evidence는 `accepted_metric_buckets`에서 `(currentWindowStartUtc, currentWindowEndUtc]` bucket boundary로 조회한다.
  - [x] `accepted_at` cutoff를 적용하는 repository method, `DashboardReadModelService` snapshot cutoff path, Application Snapshot stored read model recalculation path를 호출하지 않는다.
  - [x] late accepted metric 포함 가능성과 stored Application Snapshot과의 차이를 `readSemantics`에 명시한다.
  - [x] bucket 없음/retention 밖/malformed evidence를 live fallback이 아니라 data quality limitation으로 수렴시킨다.

- [x] Application Snapshot detail, marker, trend 경계를 보호한다. (AC: 8~10, 14~16)
  - [x] `dashboard_snapshots.read_model_json`은 Application Snapshot Detail의 stored state/evidence source로 유지한다.
  - [x] `instanceSummary.items[]`는 snapshot summary와 Instance Snapshot Trend projection source로 유지하되 Instance Dashboard snapshot mode의 필수 source로 쓰지 않는다.
  - [x] marker/helper column은 snapshot metadata 또는 navigation hint로만 사용하고 instance state/evidence source로 승격하지 않는다.

- [x] Tests와 verification guard를 추가한다. (AC: 1~18)
  - [x] live mode response shape, source, window, top-level state absence를 검증한다.
  - [x] snapshot mode가 row metadata window를 쓰고 non-cutoff metric query만 호출함을 검증한다.
  - [x] late accepted metric fixture, target instance outside `instanceSummary.items[]` cap fixture, bucket missing/retention gap fixture를 추가한다.
  - [x] forbidden field/static grep guard로 `healthScore`, `rootCause`, `instance_dashboard_snapshots`, marker-as-state, `current_15m` 재도입을 막는다.
  - [x] `git diff --check`와 `git status --short`로 보호 대상 변경이 없는지 확인한다.

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. Portal package는 `com.observation.portal.domain` feature-first MVC 구조를 따른다.
- 문서, Javadoc, test display name, 핵심 구현 주석은 프로젝트 `AGENTS.md` 지침에 맞춰 한국어로 작성한다. 외부 API 이름, 클래스명, persisted token, field name처럼 원문 유지가 더 명확한 표현만 필요한 범위에서 영어를 함께 쓴다.
- Controller는 request/response/status mapping을 맡고 service를 호출한다. Repository/JPA entity는 public API response DTO나 service external return model로 직접 노출하지 않는다.
- Flyway migration이 schema source of truth다. 이번 story의 기본 scope는 migration/schema 변경 없음이다. 별도 instance snapshot table이나 helper table을 만들지 않는다.
- Live mode는 `accepted_metric_buckets` selected instance evidence source다. window는 Application Dashboard와 같은 `recent_30_minutes`이고 기존 `current_15m` naming을 Instance Dashboard 계약으로 재사용하지 않는다.
- Snapshot mode는 selected Application Snapshot row의 `currentWindowStartUtc/currentWindowEndUtc`를 window source로 삼는다. `generatedAt`/`capturedAt`은 provenance/tie-breaker이며 retention/window boundary source가 아니다.
- Snapshot mode selected instance metric evidence는 현재 저장소에 남은 `accepted_metric_buckets`에서 cutoff 없이 재조회한다. `accepted_at` cutoff는 Application Snapshot writer/read model 저장 시점의 application snapshot semantics에만 관련되고, Instance Dashboard snapshot mode selected instance detail에는 적용하지 않는다.
- `DashboardReadModelService`에는 snapshot cutoff를 적용하는 `BucketQueryContext.snapshot(...)` path가 있다. P7 snapshot mode는 Application Dashboard read model을 재계산하지 않으므로 이 path를 우회하거나 호출 금지 test로 보호한다.
- `MetricBucketRepository`에는 selected instance non-cutoff query와 `AcceptedAtOrBefore` cutoff query가 모두 있다. P7 snapshot mode는 non-cutoff query만 사용해야 한다.
- `DashboardSnapshotDetailService`는 stored Application Snapshot Detail을 `dashboard_snapshots.read_model_json`에서만 조립한다. P7 snapshot mode는 이를 대체하지 않고, selected instance evidence 하위 detail로 별도 service/route를 둔다.
- `InstanceSnapshotTrendService`와 `InstanceSnapshotTrendParser`는 stored `instanceSummary.items[]` projection을 trend로 제한한다. P7 snapshot mode response가 trend source를 live/snapshot evidence source로 혼동하지 않게 read semantics와 links를 분리한다.
- Resource ratio field는 latest valid sample 성격의 hint다. Resource threshold 단독 hit는 root cause가 아니며, request symptom과 함께 있을 때만 shared resource pressure pattern으로 표현한다.
- Endpoint `not_observed`는 "정상"이 아니라 "selected instance에서는 해당 endpoint evidence가 관찰되지 않음"이다.

## Candidate Implementation File List

- `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceDashboardController.java`
  - Candidate new file: live mode와 selected Application Snapshot snapshot mode endpoint를 HTTP로 노출한다.
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceDashboardReadModelService.java`
  - Candidate new file: project/application/instance/snapshot path validation, live/snapshot window selection, metric evidence 조립을 담당한다.
- `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceDashboardReadModel.java`
  - Candidate new file: `mode`, `window`, `applicationStateRef`, `observationStatus`, `readSemantics`, `excludedCapabilities`를 포함한 bounded API response record다.
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelService.java`
  - Candidate update or read-only reference: existing recent 30분 selected instance evidence 조립 로직을 재사용하거나 dashboard service로 추출한다.
- `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModel.java`
  - Candidate update or read-only reference: top-level state absence, `recent_30_minutes`, endpoint/resource/data quality validation pattern을 참고한다.
- `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceEvidenceController.java`
  - Candidate update only if existing `/evidence` compatibility link or deprecation path를 dashboard route와 연결해야 한다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
  - Candidate update: snapshot mode가 non-cutoff selected instance query를 명확히 쓰도록 helper naming/comment/test를 보강한다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
  - Candidate update only if selected instance window query에 필요한 projection이 부족할 때 최소 query를 추가한다.
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
  - Candidate update: selected snapshot row metadata를 읽는 metadata-only method가 필요하면 추가한다.
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
  - Candidate update only if metadata-only snapshot row query가 필요할 때 추가한다.
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotDetailRow.java`
  - Candidate read-only reference or minimal update: current window metadata field naming을 dashboard snapshot mode에서 참고한다.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
  - Candidate read-only reference: project/application path 정합성 검증 source.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationInstanceRepository.java`
  - Candidate read-only reference: selected instance가 application catalog에 속하는지 검증한다.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/repository/StarterHeartbeatTelemetryRepository.java`
  - Candidate read-only reference: starter heartbeat를 metric state와 분리된 control-plane block으로 표시할 때만 사용한다.

## Candidate Test File List

- `observability-portal/src/test/java/com/observation/portal/domain/instance/controller/InstanceDashboardControllerTest.java`
  - Candidate new file: live/snapshot route, 404/mismatch, response mode/source/window semantics를 검증한다.
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceDashboardReadModelServiceTest.java`
  - Candidate new file: live recent 30분, snapshot row window, non-cutoff query, late accepted metric, missing bucket/data quality를 검증한다.
- `observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceDashboardReadModelShapeTest.java`
  - Candidate new file: top-level `state`/`healthScore`/`rootCause` 부재와 readSemantics required flag를 검증한다.
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelServiceTest.java`
  - Candidate update only if existing service를 dashboard live mode에 재사용하거나 추출한다.
- `observability-portal/src/test/java/com/observation/portal/domain/instance/controller/InstanceEvidenceControllerTest.java`
  - Candidate update only if existing `/evidence` compatibility behavior가 dashboard route와 연결된다.
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
  - Candidate update: selected instance non-cutoff window query와 `AcceptedAtOrBefore` cutoff query 차이를 fixture로 증명한다.
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepositoryIntegrationTest.java`
  - Candidate update only if metadata-only selected snapshot row query를 추가한다.
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailServiceTest.java`
  - Candidate guard reference: Application Snapshot Detail stored read model source를 P7에서 재계산하지 않는 경계를 유지한다.
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendServiceTest.java`
  - Candidate guard reference: trend가 stored `instanceSummary.items[]` projection으로 제한되고 snapshot mode evidence와 섞이지 않음을 보호한다.
- `observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceSnapshotTrendReadModelShapeTest.java`
  - Candidate guard reference: trend가 health score/current state를 만들지 않음을 유지한다.

## Suggested Verification Commands

```bash
./gradlew :observability-portal:test \
  --tests 'com.observation.portal.domain.instance.controller.InstanceDashboardControllerTest' \
  --tests 'com.observation.portal.domain.instance.service.InstanceDashboardReadModelServiceTest' \
  --tests 'com.observation.portal.domain.instance.model.InstanceDashboardReadModelShapeTest' \
  --tests 'com.observation.portal.domain.instance.service.InstanceEvidenceReadModelServiceTest' \
  --tests 'com.observation.portal.domain.instance.controller.InstanceEvidenceControllerTest' \
  --tests 'com.observation.portal.domain.bucket.repository.MetricBucketRepositoryIntegrationTest' \
  --tests 'com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepositoryIntegrationTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailServiceTest' \
  --tests 'com.observation.portal.domain.instance.service.InstanceSnapshotTrendServiceTest' \
  --tests 'com.observation.portal.domain.instance.model.InstanceSnapshotTrendReadModelShapeTest'

./gradlew :observability-portal:test

rg -n "current_15m|healthScore|rootCause|recoveryProof|instance_dashboard_snapshots|instance marker|marker.*state|acceptedAtCutoffApplied|includesLateAcceptedMetrics|mayDifferFromStoredApplicationSnapshot" \
  observability-portal/src/main/java/com/observation/portal/domain/instance \
  observability-portal/src/test/java/com/observation/portal/domain/instance

rg -n "find.*ApplicationInstanceIdAcceptedAtOrBefore|BucketQueryContext\\.snapshot|read_model_json\\.instanceSummary|instanceSummary\\.items|accepted_at" \
  observability-portal/src/main/java/com/observation/portal/domain/instance \
  observability-portal/src/test/java/com/observation/portal/domain/instance \
  observability-portal/src/main/java/com/observation/portal/domain/bucket \
  observability-portal/src/test/java/com/observation/portal/domain/bucket

git diff --check
git status --short
```

## References

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/epics.md#Epic 13. Dashboard Source of Truth Realignment`
- `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
- `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
- `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`
- `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
- `_bmad/custom/project-context.md`

## Dev Agent Record

### Debug Log

- 2026-06-10T17:13:58+0900: `git status --short`, sprint-status, story, Source of Truth 문서, roadmap/sequence YAML, project context를 확인했다. 13-8은 `ready-for-dev`였고 story 파일은 존재했다.
- 2026-06-10T17:13:58+0900: BMAD dev-story workflow에 따라 `implementation-artifacts/sprint-status.yaml`의 13-8 상태를 `in-progress`로 전환했다.
- 기존 `InstanceEvidenceReadModelService`/`InstanceEvidenceController`는 `recent_30_minutes` live evidence shape를 제공하고, `InstanceSnapshotTrendService`/parser는 stored `dashboard_snapshots.read_model_json.instanceSummary.items[]` projection으로 제한됨을 확인했다.
- `MetricBucketRepository`에는 selected instance non-cutoff query와 `AcceptedAtOrBefore` cutoff query가 함께 있어, P7 snapshot mode service test에서 cutoff method 미호출 guard를 추가했다.
- `DashboardSnapshotRepository.findDetailRow(projectId, applicationId, snapshotId)`가 selected snapshot row metadata를 읽는 최소 surface로 충분함을 확인했다.

### Implementation Plan

- 새 `/dashboard` facade endpoint를 추가해 기존 `/evidence` compatibility와 `/snapshot-trend` surface는 유지한다.
- live mode는 Application Dashboard와 같은 `TimeBucketWindowCalculator` recent 30분 window를 사용하고 selected instance accepted bucket evidence만 읽는다.
- snapshot mode는 selected `dashboard_snapshots` row의 `currentWindowStartUtc/currentWindowEndUtc`를 window source로 사용하고, selected instance metric evidence는 non-cutoff `accepted_metric_buckets` query로만 재구성한다.
- `InstanceDashboardReadModel` record validation으로 `readSemantics`, `windowSource`, top-level instance lifecycle/health/root-cause 금지 계약을 fail-closed로 둔다.
- Source of Truth 문서, 완료 story 13.2~13.7, frontend, migration/schema, `dbml-error.log`는 수정하지 않는다.

### Completion Notes

- `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/dashboard` live endpoint를 추가했다. 응답은 `mode=live`, `window.name=recent_30_minutes`, `windowSource=live_recent_30_minutes`, `readSemantics.source=accepted_metric_buckets`를 노출한다.
- `GET /api/projects/{projectId}/applications/{applicationId}/snapshots/{snapshotId}/instances/{instanceId}/dashboard` snapshot endpoint를 추가했다. 응답은 selected Application Snapshot metadata, `windowSource=selected_application_snapshot`, `snapshotRowSource=dashboard_snapshots`를 노출한다.
- snapshot mode는 `accepted_at` cutoff 계열 repository method, `DashboardReadModelService` snapshot path, Application Snapshot stored read model recalculation path를 호출하지 않는다.
- snapshot mode read semantics에 `acceptedAtCutoffApplied=false`, `includesLateAcceptedMetrics=true`, `mayDifferFromStoredApplicationSnapshot=true`, `applicationSnapshotRecalculated=false`, `instanceEvidenceReconstructedFromMetrics=true`, `markerIsStateSource=false`를 명시했다.
- selected snapshot의 stored `instanceSummary.items[]`는 Instance Dashboard snapshot mode 필수 source로 사용하지 않는다. target instance가 summary cap 밖이어도 catalog membership과 retained bucket evidence가 있으면 재구성할 수 있게 했다.
- bucket 없음은 live/current fallback 없이 `metric_missing` 또는 `not_observed_in_window` 계열 observation/data quality로 수렴한다.
- top-level `state`/`stateCode`와 `healthScore`, `rootCause`, `recoveryProof` 계열 필드를 만들지 않고, `applicationStateRef.lifecycleOwner=application`으로 application owner state 참조만 둔다.

## File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/13-8-backend-instance-dashboard-live-snapshot-mode-split.md`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceDashboardController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceDashboardReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceDashboardReadModelService.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/controller/InstanceDashboardControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceDashboardReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceDashboardReadModelServiceTest.java`

## Change Log

- 2026-06-10: Story 13.8 P7 Backend Instance Dashboard live/snapshot mode split 구현을 완료하고 review 상태로 전환했다.
- 2026-06-10: live/snapshot `/dashboard` endpoint, bounded `InstanceDashboardReadModel`, non-cutoff snapshot reconstruction service, focused controller/service/shape tests를 추가했다.
