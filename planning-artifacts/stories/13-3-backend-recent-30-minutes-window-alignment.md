---
artifactType: story
storyId: "13.3"
storyKey: "13-3-backend-recent-30-minutes-window-alignment"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "Backend Recent 30 Minutes Window Alignment"
architectureStyle: Traditional MVC
status: done
date: 2026-06-10
phase: P2
workType: backend
implementationScope: "backend common window/read model naming alignment"
productionCodeChangeThisContext: true
sourceOfTruthMode: read-only
dependsOn:
  - P0
  - P1
  - 13-2-frontend-read-model-contract-guard
blocks:
  - P3
  - P5
  - P7
  - P10
rollbackBoundary: "common dashboard window calculator and response naming compatibility unit"
---

# Story 13.3 - Backend Recent 30 Minutes Window Alignment

## Status

done

2026-06-10: P2 backend alignment story artifact를 생성했다. 이번 컨텍스트에서는 backend/frontend/source code/test/migration을 구현하지 않고, story artifact와 sprint-status 정렬만 수행한다.
2026-06-10: BMAD dev-story 구현을 시작했고 backend recent 30 minutes window alignment를 진행한다.
2026-06-10: Backend implementation과 verification을 완료했고 story를 review로 전환한다.
2026-06-10: BMAD code review 재검토를 마치고 남은 항목은 후속 정합성/테스트 보강으로 분리해 story를 done으로 전환한다.

## Story

backend 구현자로서, Application Dashboard live와 Instance Dashboard live/evidence가 같은 `recent_30_minutes` accepted bucket window를 사용하도록 공통 window 계산과 public read model naming을 정렬하고 싶다.

그래야 Source of Truth가 정한 최근 30분 판단 기준을 backend에서 먼저 안정화하고, P3/P5/P7/P10 후속 story가 15분 current/baseline 해석이나 Instance `current_15m` naming에 기대지 않게 된다.

## Source of Truth

아래 문서는 이 story의 기준이다. 이 story는 해당 문서의 의미를 재정의하지 않고 backend 구현 지침으로만 옮긴다.

1. `implementation-artifacts/sprint-status.yaml`
2. `planning-artifacts/stories/13-2-frontend-read-model-contract-guard.md`
3. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
4. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
5. `planning-artifacts/contracts/read-model-contract.md`
6. `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
7. `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
8. `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
9. `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
10. `_bmad/custom/project-context.md`

## Background

Epic 13은 완료된 Epic 4/5/6/10 story를 다시 여는 작업이 아니라, 확정된 Dashboard Source of Truth를 새 alignment story 묶음으로 적용하는 tracking epic이다. Story 13.2는 P1 frontend read model contract guard를 완료했고, `npm run guard:read-model-contract`가 downstream backend shape 변경을 fail-closed로 감시한다.

P2의 목적은 backend 공통 window/read model naming 정렬이다. 현재 backend는 `TimeBucketWindowCalculator`와 `DashboardTimeWindow`가 15분 current + 직전 15분 baseline을 만들고, `DashboardReadModelService`, `TriageSummaryService`, `EndpointPriorityService`, `InstanceEvidenceReadModelService`가 그 window를 따라 aggregate/histogram/endpoint evidence를 조회한다. Source of Truth 기준에서는 Application Dashboard live와 Instance Dashboard live가 모두 최근 30분 accepted bucket window를 사용해야 하며, MVP primary 판단은 baseline 비교를 사용하지 않는다.

Repository 계층은 이미 `bucket_end_utc > windowStartUtc and bucket_end_utc <= windowEndUtc` 형태의 start/end 인자를 받는다. 따라서 이 story의 기본 가정은 migration 없음이다. 단, implementation 조사에서 legacy snapshot baseline column compatibility나 public DTO nullability 때문에 schema 변경이 절대 필요하다고 증명되는 경우에만 별도 승인/후속 story로 분리한다.

## Aligns

- `4-1-time-bucket-contract-implementation`: UTC 30초 bucket boundary와 공통 time component를 재사용하되 dashboard 판단 window를 Source of Truth의 30분으로 맞춘다.
- `4-2-lifecycle-state-service`: accepted bucket metric axis와 starter heartbeat control-plane axis 분리 원칙을 유지한다.
- `4-4-state-semantics-tests`: heartbeat/accepted bucket/resource hint 조합으로 host application down, 정상 확정, 복구 완료를 단정하지 않는 guard를 유지한다.
- `5-2-application-dashboard-read-model-skeleton`: Application Dashboard current response를 server-computed read model로 유지하되 public window naming/source semantics를 30분으로 정렬한다.
- `5-6-instance-evidence-read-model`: Instance Evidence는 Application Dashboard 판단을 대체하지 않는 selected instance evidence detail로 유지하되 live window를 Application Dashboard와 같은 30분으로 맞춘다.
- `12-4-snapshot-delay-and-pipeline-lag-semantics`: queue/ingest lag와 metric freshness 판단을 섞지 않는 axis 분리를 유지한다.
- `13-2-frontend-read-model-contract-guard`: P1 guard가 요구하는 server-computed source/order/value 보존 계약을 깨지 않는다.

## Supersedes

아래 항목은 완료 story 본문이나 이력을 바꾸지 않고, P2 이후 backend 구현에서 좁게 대체하는 해석이다.

- Dashboard MVP primary 판단을 15분 current + 직전 15분 baseline 비교로 읽는 해석.
- Application Dashboard response에서 `sourceWindow.current`가 사실상 `current_15m`를 뜻하고 baseline이 lifecycle state, triage, endpoint priority, recovery 판단에 필수라는 해석.
- Instance Evidence API의 `current_15m`, `selected_instance_current_15m`, `instance_current_15m` naming을 Instance Dashboard live 계약으로 계속 재사용해도 된다는 해석.
- endpoint priority나 triage card를 baseline delta, comparative regression, insufficient baseline guard 중심으로 primary 판단해도 된다는 해석.
- histogram bucket을 p95/p99 계산 source로 사용하거나 starter canonical percentile source/scope를 application/endpoint rollup으로 병합해도 된다는 해석.

## Hardens

- Application Dashboard live source는 `accepted_metric_buckets`와 `recent_30_minutes` window로 드러난다.
- Instance live/evidence surface도 Application Dashboard와 같은 30분 accepted bucket source semantics를 사용한다.
- baseline이 legacy field나 snapshot compatibility metadata로 남더라도 MVP primary 판단 path와 UI 판단 근거에서 분리된다.
- starter heartbeat는 starter 연결/control-plane surface로만 남고 accepted bucket freshness/state 판단 source가 되지 않는다.
- p95/p99는 starter canonical percentile point/series source scope를 유지하고, histogram은 distribution display와 slow-share evidence에만 사용된다.
- P1 frontend guard가 30분 shape를 UI-side recomputation 없이 소비하는지 계속 검증한다.

## Rollback

- rollback 단위는 공통 dashboard window calculator와 response naming compatibility 변경이다.
- window duration 변경, DTO/window naming 변경, baseline primary 판단 제거는 가능한 한 작은 commit/subtask로 나눈다.
- 필요하면 legacy 15분/30분 compatibility switch를 한시적으로 둘 수 있지만, public Source of Truth naming은 `recent_30_minutes` 또는 동등한 30분 의미여야 한다.
- rollback은 Source of Truth 문서, 완료 story 파일, Epic 13 planning status, frontend guard 완화로 처리하지 않는다.
- migration은 기본 scope가 아니므로 rollback 대상에도 넣지 않는다. migration 필요성이 증명되면 별도 story/승인 경계로 분리한다.

## Out of scope

- 이번 컨텍스트의 backend/frontend/source code/test/migration 구현.
- Source of Truth 문서 수정 또는 의미 재정의.
- 완료 story 파일 본문 수정 또는 done 상태 변경.
- Epic 11 backlog 자동 탐색 또는 상태 변경.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.
- P3 Application Dashboard full read model shape alignment.
- P4/P6/P8 frontend realignment.
- P5 30-minute scheduled snapshot scheduler/slot change.
- P7 Instance Dashboard live/snapshot mode split.
- retention cleanup.
- frontend code 수정.
- migration/schema 변경. 단, implementation 조사에서 절대 필요성이 증명되면 별도 승인/후속 story로 분리한다.

## Acceptance Criteria

1. Given Application Dashboard live read model을 생성할 때, When backend가 source bucket을 조회하면, Then `bucket_end_utc > windowStartUtc and bucket_end_utc <= windowEndUtc` 조건의 최근 30분 accepted bucket range를 사용한다.
2. Given lifecycle state, triage card, endpoint priority, recovery guidance를 계산할 때, When baseline aggregate/histogram/endpoint evidence가 존재하거나 누락되어도, Then baseline comparison은 MVP primary 판단, state 승격, endpoint priority, recovery 판단에 사용되지 않는다.
3. Given Application Dashboard current response를 직렬화할 때, Then public response/window naming은 `recent_30_minutes` 또는 Source of Truth와 호환되는 30분 이름을 노출하고, legacy `current_15m` 의미를 primary response name으로 유지하지 않는다.
4. Given Instance Dashboard live/evidence surface를 생성할 때, Then selected instance metric, percentile, histogram, endpoint, resource evidence는 Application Dashboard와 같은 recent 30 minutes source semantics를 사용한다.
5. Given starter heartbeat가 최근 수신되거나 누락되었고 accepted bucket이 없거나 오래된 경우, Then heartbeat axis와 accepted bucket freshness axis는 분리되고 host application down, instance down, 정상 확정, 복구 완료를 확정하지 않는다.
6. Given histogram duration bucket evidence가 제공될 때, Then histogram은 distribution display source 또는 500ms 초과 slow-share evidence로만 사용되고 p95/p99 계산, 평균/최댓값 latency, percentile rollup, regression 판단 source가 되지 않는다.
7. Given starter canonical percentile `local_percentiles_json`이 제공될 때, Then p95/p99는 source-scoped starter percentile point/series로만 노출되며 여러 bucket/instance를 평균, max, merge하거나 histogram에서 재계산하지 않는다.
8. Given backend implementation이 완료되면, Then `cd frontend && npm run guard:read-model-contract`, `cd frontend && npm run typecheck`, relevant backend tests, `git diff --check`, `git status --short`가 통과해야 한다.
9. Given story implementation diff를 검토할 때, Then Source of Truth 문서, 완료 story 파일, frontend code, migration file은 기본 diff에 없어야 한다. migration이 포함되면 story investigation evidence와 별도 승인 이유가 completion notes에 있어야 한다.
10. Given P1 frontend guard가 `baseline=null` 또는 compatibility limitation을 safe display로 다루는 경우, Then backend는 baseline field가 남더라도 `null`, limitation, compatibility metadata로만 표현하고 UI가 baseline을 판단 근거로 쓰게 만들지 않는다.

## Tasks / Subtasks

- [x] 공통 dashboard window 계산을 recent 30 minutes로 정렬한다. (AC: 1, 3)
  - [x] `TimeBucketWindowCalculator`의 dashboard window duration과 Javadoc/Korean comments를 Source of Truth의 최근 30분으로 정렬한다.
  - [x] `DashboardTimeWindow`가 baseline을 primary invariant로 강제하는 현재 구조를 검토하고, recent 30분 current/evaluation window를 표현할 수 있게 조정한다.
  - [x] `TimeBucketWindowCalculatorTest`에 30분 start/end와 `bucket_end_utc > start && <= end` repository boundary expectation을 추가/수정한다.
- [x] Application Dashboard live service를 baseline-free primary 판단으로 정렬한다. (AC: 1, 2, 3, 5, 6, 7)
  - [x] `DashboardReadModelService`가 current/recent 30분 aggregate, histogram, endpoint, runtime ratio, percentile rows를 같은 window로 조회하는지 검증한다.
  - [x] baseline aggregate/histogram/endpoint rows를 `TriageSummaryService`, `EndpointPriorityService`, recovery/state decision의 primary 판단 input에서 제거하거나 diagnostic/compatibility로 격리한다.
  - [x] public DTO에 baseline field가 남아야 하면 `null` 또는 limitation/compatibility metadata로만 노출하고, lifecycle/triage/endpoint priority/recovery 판단에는 연결하지 않는다.
  - [x] starter heartbeat lookup과 accepted bucket freshness lookup이 기존처럼 분리되어 있음을 regression test로 유지한다.
- [x] Triage와 endpoint priority rule을 Source of Truth 절대 기준으로 정렬한다. (AC: 2, 6, 7)
  - [x] error/latency 판단은 최근 30분 request count, error rate, 500ms 초과 slow share, resource threshold 기준을 사용한다.
  - [x] `global_error_spike`, `global_latency_spike`, `endpoint_error_spike`, `endpoint_latency_spike`, `comparative_regression`, `insufficient_baseline`처럼 baseline 비교를 암시하는 rule/copy/status가 primary MVP 판단에 남지 않도록 정리한다.
  - [x] endpoint `errorCount > 0`은 5xx/500번대 server error attention evidence로 보존하되 state 승격과 분리한다.
- [x] Instance live/evidence read model을 recent 30 minutes로 정렬한다. (AC: 4, 5, 6, 7)
  - [x] `InstanceEvidenceReadModelService`가 selected instance aggregate, percentile rows, histogram rows, runtime ratio, endpoint evidence를 Application Dashboard와 같은 30분 window로 조회하는지 검증한다.
  - [x] `InstanceEvidenceReadModel`의 `MetricWindow.name`, percentile `window`, histogram `scope`, endpoint evidence `scope` 등 `current_15m`/`instance_current_15m` naming을 `recent_30_minutes` 또는 Source of Truth 호환 이름으로 정렬한다.
  - [x] Instance response에 top-level lifecycle `state`, `healthScore`, `rootCause`, host down copy가 생기지 않도록 기존 guard를 유지한다.
- [x] p95/p99와 histogram source boundary를 보호한다. (AC: 6, 7)
  - [x] starter canonical p95/p99는 `local_percentiles_json` source/scope validation을 유지한다.
  - [x] histogram bucket merge는 distribution/slow-share evidence로만 사용하고 percentile scalar를 만들지 않는다.
- [x] Verification과 보호 범위를 확인한다. (AC: 8, 9, 10)
  - [x] P1 frontend guard와 typecheck를 실행한다.
  - [x] backend 관련 unit/integration tests를 실행한다.
  - [x] Source of Truth 문서, 완료 story 파일, frontend code, migration file, `dbml-error.log`가 의도치 않게 변경되지 않았는지 `git status --short`로 확인한다.

## Dev Notes

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. Portal package는 `com.observation.portal.domain` feature-first MVC 구조를 따른다.
- 현재 공통 time component는 `observability-portal/src/main/java/com/observation/portal/common/time/TimeBucketWindowCalculator.java`와 `DashboardTimeWindow.java`다. 둘 다 15분 current + 직전 15분 baseline을 주석과 invariant로 표현한다.
- `UtcTimeInterval`은 start-inclusive/end-exclusive helper지만 repository query는 Source of Truth와 같이 `bucket_end_utc > windowStartUtc and bucket_end_utc <= windowEndUtc`를 사용한다. 구현자는 interval helper 의미와 repository boundary 의미를 혼동하지 말고 테스트에 명시한다.
- `DashboardReadModelService`는 `dashboardWindow.current()`와 `dashboardWindow.baseline()`으로 application aggregate, histogram, endpoint evidence를 각각 조회하고 `TriageSummaryService`, `EndpointPriorityService`에 baseline rows를 전달한다.
- `TriageSummaryService`는 baseline request count, baseline error rate, baseline slow share, delta, relative threshold를 사용해 `global_error_spike`/`global_latency_spike` 계열 card를 만든다. P2에서는 baseline comparison을 primary 판단에서 제거하고 recent 30분 절대 threshold 중심으로 정렬해야 한다.
- `EndpointPriorityService`는 baseline endpoint aggregate를 merge하고 `endpoint_error_spike`, `endpoint_latency_spike`, `endpoint_comparative_regression` 후보를 만든다. P2에서는 MVP endpoint priority가 baseline regression이 아니라 recent 30분 accepted bucket evidence와 bounded attention evidence를 따르도록 guard한다.
- `ApplicationDashboardReadModel.Application.SourceWindow`는 baseline non-null을 강제한다. 13.3은 P3 full shape alignment가 아니므로 최소 호환 변경으로 recent 30분 naming/source semantics를 닫고, 큰 shape 재편은 13.4로 넘긴다.
- `InstanceEvidenceReadModelService`는 selected instance live evidence를 같은 `TimeBucketWindowCalculator`에서 받은 current window로 조회하지만, 현재 window 이름과 scope 문자열은 `current_15m`, `selected_instance_current_15m`, `instance_current_15m`로 남아 있다.
- `InstanceEvidenceReadModel`은 `MetricWindow.name == current_15m`, starter percentile `window == current_15m`, endpoint evidence `scope == instance_current_15m`를 record validation으로 강제한다. 해당 validation과 controller/shape tests를 함께 정렬해야 한다.
- `MetricBucketRepository`와 `AcceptedMetricBucketJpaRepository`는 application/instance aggregate, percentile, histogram, endpoint, runtime ratio 조회에 start/end 인자를 받는다. 기본 migration 없이 30분 window 인자만 전달해도 되는 구조로 보인다.
- `dashboard_snapshots.baseline_window_start_utc`와 `baseline_window_end_utc`는 V006 migration에서 not null이다. Source of Truth는 이 column을 compatibility metadata로만 보고, 후속 migration에서 nullable/semantic rename이 필요한지는 별도 검토한다고 한다. 이 story의 기본 scope는 migration 없음이다.
- Story 13.2 P1 guard는 frontend가 server state/order/source semantics를 재계산하지 않게 막는다. 13.3 구현 후 `npm run guard:read-model-contract`가 실패하면 guard 완화가 아니라 backend response/source semantics 정렬을 우선 검토한다.
- Source of Truth 문서, 완료 story 파일, frontend code는 이번 backend implementation story에서 수정하지 않는다.

### Candidate Implementation File List

- `observability-portal/src/main/java/com/observation/portal/common/time/TimeBucketWindowCalculator.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/DashboardTimeWindow.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/UtcTimeInterval.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/TriageSummaryService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointPriorityService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointEvidenceAggregationService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/controller/DashboardController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/controller/InstanceEvidenceController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`

### Candidate Test File List

- `observability-portal/src/test/java/com/observation/portal/common/time/TimeBucketWindowCalculatorTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/TriageSummaryServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/EndpointPriorityServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/model/EndpointPriorityReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/controller/InstanceEvidenceControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendParserTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceSnapshotTrendReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/controller/InstanceSnapshotTrendControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`

### Suggested Verification Commands

```bash
./gradlew :observability-portal:test \
  --tests 'com.observation.portal.common.time.TimeBucketWindowCalculatorTest' \
  --tests 'com.observation.portal.domain.dashboard.service.DashboardReadModelServiceTest' \
  --tests 'com.observation.portal.domain.dashboard.service.TriageSummaryServiceTest' \
  --tests 'com.observation.portal.domain.dashboard.service.EndpointPriorityServiceTest' \
  --tests 'com.observation.portal.domain.dashboard.controller.DashboardControllerTest' \
  --tests 'com.observation.portal.domain.dashboard.model.EndpointPriorityReadModelShapeTest' \
  --tests 'com.observation.portal.domain.instance.service.InstanceEvidenceReadModelServiceTest' \
  --tests 'com.observation.portal.domain.instance.model.InstanceEvidenceReadModelShapeTest' \
  --tests 'com.observation.portal.domain.instance.controller.InstanceEvidenceControllerTest' \
  --tests 'com.observation.portal.domain.instance.service.InstanceSnapshotTrendServiceTest' \
  --tests 'com.observation.portal.domain.instance.service.InstanceSnapshotTrendParserTest' \
  --tests 'com.observation.portal.domain.instance.model.InstanceSnapshotTrendReadModelShapeTest' \
  --tests 'com.observation.portal.domain.instance.controller.InstanceSnapshotTrendControllerTest' \
  --tests 'com.observation.portal.domain.bucket.repository.MetricBucketRepositoryIntegrationTest'
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
- `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
- `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
- `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
- `_bmad/custom/project-context.md`
- `planning-artifacts/stories/4-1-time-bucket-contract-implementation.md`
- `planning-artifacts/stories/4-2-lifecycle-state-service.md`
- `planning-artifacts/stories/4-4-state-semantics-tests.md`
- `planning-artifacts/stories/5-2-application-dashboard-read-model-skeleton.md`
- `planning-artifacts/stories/5-6-instance-evidence-read-model.md`
- `planning-artifacts/stories/12-4-snapshot-delay-and-pipeline-lag-semantics.md`

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- RED: `./gradlew :observability-portal:test --tests 'com.observation.portal.common.time.TimeBucketWindowCalculatorTest'`가 `recent30Minutes()` 미구현 compile failure로 실패했다.
- GREEN: `./gradlew :observability-portal:testClasses`가 production/test compile을 통과했다.
- GREEN: `./gradlew :observability-portal:test --tests 'com.observation.portal.common.time.TimeBucketWindowCalculatorTest' --tests 'com.observation.portal.domain.dashboard.service.TriageSummaryServiceTest' --tests 'com.observation.portal.domain.dashboard.service.EndpointPriorityServiceTest'`가 통과했다.
- GREEN: story Suggested Verification Commands의 backend test 전체 목록이 통과했다.
- GREEN: `cd frontend && npm run guard:read-model-contract`가 통과했다.
- GREEN: `cd frontend && npm run typecheck`가 통과했다.
- GREEN: BMAD code review 후 quick-dev 보정으로 snapshot writer baseline compatibility와 Instance endpoint source semantics를 정렬했고, Story backend test 목록에 snapshot writer/capture policy/enricher cutoff regression tests를 더해 통과했다.

### Completion Notes List

- Application Dashboard와 Instance Evidence live read model이 공통 recent 30 minutes accepted bucket window를 사용하도록 `TimeBucketWindowCalculator`와 `DashboardTimeWindow`를 정렬했다.
- baseline aggregate/histogram/endpoint comparison은 lifecycle state, triage, endpoint priority, recovery primary 판단 input에서 제거했고 public baseline field는 `null` 또는 `baseline_comparison_not_used_for_mvp` compatibility metadata로만 남겼다.
- triage와 endpoint priority는 recent 30분 error rate, 500ms 초과 slow share, low-sample server error attention 기준으로 정렬했고 baseline 비교 rule/copy/status를 primary path에서 제거했다.
- starter canonical percentile은 `starter_canonical_percentile` public source와 `instance_bucket` scope를 유지하고, histogram은 distribution/slow-share evidence로만 사용하도록 보호했다.
- Instance Evidence의 metric window, percentile window, histogram/resource/endpoint source/scope naming을 `recent_30_minutes`와 accepted bucket semantics로 정렬했다.
- Snapshot writer는 public baseline `null` response를 유지하면서 기존 not-null baseline helper column에는 직전 30분 compatibility window를 저장하도록 정렬했다.
- Instance endpoint evidence는 Source of Truth의 `accepted_metric_buckets.endpoints_json` / `instance_recent_30_minutes` source semantics를 유지하고, application endpoint priority는 selection/order reference로만 사용하도록 정렬했다.
- Source of Truth 문서, 완료 story 파일, frontend code, migration/schema file은 수정하지 않았다. 기존 untracked `dbml-error.log`도 수정/삭제/stage하지 않았다.

### File List

- `planning-artifacts/stories/13-3-backend-recent-30-minutes-window-alignment.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/common/time/DashboardTimeWindow.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/TimeBucketWindowCalculator.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointPriorityService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/TriageSummaryService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterService.java`
- `observability-portal/src/test/java/com/observation/portal/common/time/TimeBucketWindowCalculatorTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/model/EndpointPriorityReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/EndpointPriorityServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/TriageSummaryServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/controller/InstanceEvidenceControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModelShapeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceEvidenceReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCapturePolicyTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotReadModelEnricherCutoffTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterServiceIntegrationTest.java`

### Change Log

- 2026-06-10: Backend recent 30 minutes accepted bucket window alignment를 구현하고 verification 통과 후 status를 `review`로 전환했다.
- 2026-06-10: BMAD code review findings를 quick-dev로 반영해 snapshot baseline compatibility와 Instance endpoint evidence source semantics를 보정했다.
- 2026-06-10: BMAD code review 재검토 후 story status를 `done`으로 전환했다.
