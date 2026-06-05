---
artifactType: story
storyId: "12.4"
storyKey: "12-4-snapshot-delay-and-pipeline-lag-semantics"
epic: "Epic 12. SQS Buffered Ingest Transition"
title: "Snapshot Delay and Pipeline Lag Semantics"
architectureStyle: Traditional MVC
status: done
date: 2026-06-05
commitBoundary: "feat: add snapshot delay and pipeline lag semantics"
---

# Story 12.4 - Snapshot Delay and Pipeline Lag Semantics

## Status

done

2026-06-05: `bmad-create-story`로 Story 12.4 developer handoff를 새로 정리했다. 이 story 생성 작업은 구현을 포함하지 않으며, 후속 구현자는 아래 acceptance criteria와 guardrail을 기준으로 `bmad-dev-story`를 시작한다.

## Story

구현자로서, SQS enqueue-to-persist delay가 dashboard snapshot/history 의미를 흔들지 않도록 snapshot capture delay와 accepted bucket cutoff를 명확히 적용하고 싶다.

그래야 queue lag가 stale/down이나 host application down copy로 오해되지 않고, snapshot history/detail/trend가 저장 당시 read model의 immutable history로 유지된다.

## Source of Truth

아래 문서를 읽고 이 story를 작성했다. `planning-artifacts/prd.md`는 현재 workspace에 없어서 PRD source는 참고하지 못했다.

1. `implementation-artifacts/sprint-status.yaml`
2. `planning-artifacts/epics.md`
3. `planning-artifacts/architecture.md`
4. `planning-artifacts/contracts/read-model-contract.md`
5. `planning-artifacts/contracts/time-buckets.md`
6. `planning-artifacts/contracts/state-semantics.md`
7. `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
8. `planning-artifacts/stories/12-1-architecture-and-contract-decision.md`
9. `planning-artifacts/stories/12-2-ingest-enqueue-boundary.md`
10. `planning-artifacts/stories/12-3-spring-boot-sqs-worker-mvp-and-idempotency.md`
11. `_bmad/custom/project-context.md`

## Story 12.1~12.3에서 이미 닫힌 결정

- Consumer는 Spring Boot portal 내부 worker다. Lambda consumer, Lambda handler scaffold, Lambda event source mapping, 별도 worker service는 Epic 12 범위가 아니다.
- SQS mode는 opt-in이고 rollback은 `portal.ingest.buffer.mode=direct` 복귀다.
- `202 queued`는 enqueue 성공만 뜻하며 DB 저장 완료, dashboard freshness current, snapshot 반영 완료를 뜻하지 않는다.
- Story 12.3 worker MVP는 DB throughput 개선을 주장하지 않는다. DB batch throughput claim은 Story 12.5에서 batch writer와 측정 이후에만 가능하다.
- SQS는 `accepted_metric_buckets` insert path의 buffer일 뿐 dashboard snapshot/history source가 아니다.
- Snapshot cutoff 이후 late bucket은 accepted bucket 저장은 허용하되 이미 생성된 `dashboard_snapshots` row나 stored history/detail/trend에 backfill하지 않는다.
- stale/down은 accepted bucket freshness 기준 이름이다. queue lag, worker backlog, oldest message age, last successful persist lag는 pipeline diagnostic 이름이다.
- Story 12.3 worker MVP만 완료된 상태는 user-facing rollout ready가 아니다. Story 12.4의 lag/snapshot semantics가 함께 닫혀야 rollout 후보가 된다.

## 현재 코드 상태

- `DashboardSnapshotScheduler`는 현재 `@Scheduled(cron = "0 0 * * * *", zone = "UTC")`로 UTC 정시에 실행되고, `requestedAt.truncatedTo(HOURS)`를 `currentWindowEndUtc`로 사용한다.
- `DashboardSnapshotScheduler`의 application eligibility는 `ApplicationRepository#findActiveApplicationsWithAcceptedBucketSince(retentionCutoffUtc, targetWindowEndUtc)`를 호출한다. 현재 query는 `bucketEndUtc >= retentionCutoffUtc`와 `bucketEndUtc <= targetWindowEndUtc`만 확인하며 `acceptedAt <= snapshotCutoffAt` 조건은 없다.
- `DashboardSnapshotCaptureService`는 `DashboardReadModelService#getDashboardForSnapshot(projectId, applicationId, currentWindowEndUtc)`를 호출한 뒤 `DashboardSnapshotWriterService`에 저장을 위임한다.
- `DashboardReadModelService#getDashboardForSnapshot(...)`은 query fallback capture를 호출하지 않고 target current window end만 고정한다. 하지만 내부 `MetricBucketRepository` 조회들은 현재 window/bucket boundary 기준만 받으며 cutoff timestamp를 받지 않는다.
- `DashboardSnapshotFallbackCaptureService`는 고정 `65m` threshold를 사용한다. Story 12.4 이후에는 `60m + captureDelay + fallbackGrace`로 계산되어야 하며 기본값은 `67m`이다.
- `accepted_metric_buckets`에는 `accepted_at` column과 `idx_buckets_accepted_at` index가 이미 있다. Story 12.4 cutoff 비교 기준은 이 `accepted_metric_buckets.accepted_at`이다.
- `DashboardSnapshotReadModelEnricher`는 snapshot 저장 JSON에 `snapshotEndpointEvidence`와 `instanceSummary`를 추가하면서 instance별 `MetricBucketRepository` query를 다시 호출한다. 이 경로가 cutoff 없는 query를 쓰면 top-level dashboard와 stored JSON 사이에 모델 불일치가 생긴다.
- `OperationalEventHistoryService`, `DashboardSnapshotDetailService`, `DashboardSnapshotMarkerService`, `InstanceSnapshotTrendService`는 저장된 snapshot/read model을 projection해야 하며 current bucket, heartbeat, lifecycle service를 live join해 재판정하면 안 된다.
- `MvcLayerBoundaryTest`는 Epic 12 worker가 `domain.ingest.queue`에 머물고 Lambda surface를 만들지 않는 guard를 이미 가진다. Story 12.4 구현은 이 boundary를 유지해야 한다.

## 목표

- configurable snapshot capture delay를 추가하고 기본값을 `portal.dashboard-snapshots.capture-delay=120s`로 둔다.
- fallback grace를 추가하고 기본값을 `portal.dashboard-snapshots.fallback-grace=5m`로 둔다.
- `snapshotCutoffAt = currentWindowEndUtc + captureDelay`를 snapshot capture의 명시적 cutoff로 사용한다.
- delayed capture 후에도 `currentWindowEndUtc`는 UTC hourly boundary로 유지한다.
- snapshot scheduler eligibility와 snapshot read model query path에 `accepted_metric_buckets.accepted_at <= snapshotCutoffAt` cutoff를 적용한다.
- cutoff 이후 저장된 late bucket은 current dashboard에는 DB source-of-truth 기준으로 보일 수 있지만, 이미 생성된 snapshot/history/detail/trend에는 backfill되지 않게 한다.
- queue lag/backlog/worker failure diagnostic이 lifecycle stale/down, starter telemetry unreachable, host application down certainty copy로 직접 수렴하지 않게 regression guard를 둔다.

## 구현 전 결정

| 항목 | 결정 | 구현 제약 |
| --- | --- | --- |
| Capture delay property | `portal.dashboard-snapshots.capture-delay=120s` | positive duration이어야 한다. `currentWindowEndUtc`를 늦추지 않고 capture 실행 eligibility만 늦춘다. |
| Fallback grace property | `portal.dashboard-snapshots.fallback-grace=5m` | fallback threshold는 별도 magic constant가 아니라 `60m + captureDelay + fallbackGrace`로 계산한다. 기본값은 `67m`이다. |
| Snapshot cutoff | `snapshotCutoffAt = currentWindowEndUtc + captureDelay` | 이름이 `snapshotDelay`로 남아도 의미는 capture delay다. 기준 timestamp는 `accepted_metric_buckets.accepted_at`이다. |
| Scheduler eligibility | 최소 `bucketEndUtc <= currentWindowEndUtc` and `acceptedAt <= snapshotCutoffAt` | retention cutoff는 유지하되 eligibility source는 accepted bucket axis만 사용한다. heartbeat나 queue backlog로 application을 eligibility에 추가하지 않는다. |
| Minute cron | 유지 가능 | 같은 hourly target을 cutoff 이후 매 minute 반복 dispatch하면 안 된다. cutoff를 처음 지난 tick에서만 dispatch한다. writer upsert no-op에 기대어 반복 호출을 숨기는 구현은 불충분하다. |
| Fixed offset cron | 허용 | 기본 delay가 정확히 120초인 단순 구현이라면 `0 2 * * * *` 계열도 가능하다. 다만 property 변경을 지원하려면 minute scheduler + first-eligible-tick guard가 더 자연스럽다. |
| Current dashboard path | 변경 금지 | `GET /dashboard` current path는 query 시점 DB source-of-truth 기준 current/baseline 15분 semantics를 유지한다. cutoff를 current dashboard path에 적용하지 않는다. |
| Snapshot read model path | cutoff 적용 | scheduled/fallback snapshot 저장을 위해 read model을 만드는 path에만 cutoff를 적용한다. current dashboard 15분 semantics는 바꾸지 않는다. |
| Late bucket policy | accepted bucket 저장 허용, snapshot backfill 금지 | cutoff 이후 accepted bucket row insert를 막지 않는다. snapshot recomputation/backfill/replay pipeline은 만들지 않는다. |
| Queue lag diagnostic | 별도 metric/runbook/API 후보 | lifecycle state input, starter connection diagnosis, host application down certainty copy로 직접 연결하지 않는다. |

## Acceptance Criteria

1. Story 12.4 구현은 Traditional MVC + Service/Repository layering과 feature-first package 구조를 따른다.
2. `application`, `port`, `adapter` package를 새로 만들지 않는다.
3. `portal.dashboard-snapshots.capture-delay` property를 추가하고 기본값은 `120s`다.
4. `portal.dashboard-snapshots.fallback-grace` property를 추가하고 기본값은 `5m`다.
5. fallback staleness threshold는 `60분 + captureDelay + fallbackGrace`로 계산되며 기본값은 `67분`이다.
6. `snapshotCutoffAt = currentWindowEndUtc + captureDelay`가 code와 test에서 명확히 드러난다.
7. delayed capture 후에도 `DashboardSnapshotCaptureRequest.currentWindowEndUtc`는 UTC hourly boundary다.
8. snapshot scheduler는 cutoff 이전 tick에서는 해당 hourly target을 dispatch하지 않는다.
9. minute cron을 유지하는 경우 같은 hourly target을 cutoff 이후 매 minute 반복 dispatch하지 않는다. cutoff를 처음 지난 tick에서만 dispatch한다.
10. scheduler application eligibility는 최소 `bucketEndUtc <= currentWindowEndUtc`와 `acceptedAt <= snapshotCutoffAt`을 만족하는 accepted bucket이 있을 때만 true다.
11. scheduler eligibility는 heartbeat telemetry, queue backlog, worker oldest message age, starter connection status를 source로 사용하지 않는다.
12. snapshot read model path의 cutoff 비교 기준은 `accepted_metric_buckets.accepted_at`이다.
13. snapshot read model path는 current window, baseline window, latest bucket freshness source, local percentile evidence, histogram evidence, endpoint evidence, recent bucket evidence, runtime ratio evidence, gap/previous-state evidence가 같은 cutoff 기준을 공유한다.
14. `DashboardSnapshotReadModelEnricher`가 저장 JSON에 넣는 `instanceSummary`, endpoint evidence refs, resource/percentile/freshness evidence도 cutoff 없는 repository method를 섞지 않는다.
15. top-level dashboard read model과 stored `read_model_json`의 detail/evidence/trend source가 같은 cutoff 기준을 사용한다. 범위가 커서 한 번에 닫기 어렵다면 구현자는 reviewer가 볼 수 있게 명시적 defer decision을 story/dev notes에 남긴다.
16. cutoff 이전에 accepted된 bucket은 `bucketEndUtc <= currentWindowEndUtc`인 경우 해당 hourly snapshot에 포함될 수 있다.
17. cutoff 이후 accepted된 bucket은 accepted bucket table에는 저장될 수 있지만 이미 생성된 snapshot row, snapshot detail, marker, operational event history, instance snapshot trend에는 backfill되지 않는다.
18. snapshot writer의 identity `application_id + current_window_end_utc`는 유지한다. late bucket 때문에 같은 hourly snapshot을 재계산하거나 downgrade/overwrite하지 않는다.
19. `DashboardSnapshotFallbackCaptureService`는 computed fallback threshold를 사용하고 기존 fixed `65m` constant에 묶이지 않는다.
20. query fallback snapshot도 current dashboard response 자체의 semantics를 바꾸지 않는다. 이미 만들어진 current read model을 저장하되 threshold 계산만 delay/grace-aware로 바꾼다.
21. current dashboard path는 기존 DB source-of-truth 기준을 유지한다. queue에만 있고 아직 DB에 없는 bucket을 current로 간주하지 않는다.
22. current dashboard `current 15분` / `baseline 15분` window 계산은 변경하지 않는다.
23. snapshot cutoff는 snapshot read model path 전용이다. Instance Evidence current API, Application Dashboard current API, Project/Application navigation API의 live current semantics에 적용하지 않는다.
24. queue lag/backlog/worker failure는 lifecycle stale/down state를 직접 바꾸지 않는다.
25. queue lag/backlog/worker failure는 starter telemetry unreachable diagnosis를 직접 만들지 않는다.
26. queue lag/backlog/worker failure는 host application down, host process down, 앱 내려감 같은 확정 copy로 표현하지 않는다.
27. operational event history와 snapshot marker copy는 stored snapshot/read model을 기준으로 하되 queue lag를 host health certainty로 표현하지 않는다.
28. logs/metrics/API 후보를 추가하더라도 raw project key, starter credential, Authorization token, webhook URL, raw payload, queue URL, AWS secret을 노출하지 않는다.
29. Story 12.4 구현은 DB batch throughput 개선, JDBC batch, `ON CONFLICT` throughput optimization, batch writer benchmark claim을 포함하지 않는다.
30. 새 public class/method와 동작이 직관적이지 않은 helper에는 `AGENTS.md` 기준의 한국어 Javadoc/doc comment를 추가한다.

## Tasks / Subtasks

- [x] Snapshot delay/fallback properties 추가 (AC: 3~6, 19)
  - [x] `application.properties`에 `portal.dashboard-snapshots.capture-delay=120s`와 `portal.dashboard-snapshots.fallback-grace=5m` 기본값을 추가한다.
  - [x] snapshot properties를 담는 configuration properties 또는 constructor-bound value를 추가한다. duration은 positive만 허용한다.
  - [x] fallback threshold 계산을 `60m + captureDelay + fallbackGrace`로 모으고 magic `65m` constant를 제거하거나 대체한다.

- [x] Scheduler cutoff eligibility 구현 (AC: 6~11)
  - [x] `DashboardSnapshotScheduler`가 `snapshotCutoffAt`을 계산한다.
  - [x] minute cron을 유지한다면 same hourly target repeated dispatch를 막는 first-eligible-tick guard를 구현한다.
  - [x] `ApplicationRepository` scheduler eligibility query에 `acceptedAt <= snapshotCutoffAt` 조건을 추가하거나 별도 method를 추가한다.
  - [x] eligibility의 최소 조건 `bucketEndUtc <= currentWindowEndUtc`와 `acceptedAt <= snapshotCutoffAt`을 test로 고정한다.

- [x] Snapshot read model cutoff path 추가 (AC: 12~18, 21~23)
  - [x] `DashboardReadModelService#getDashboardForSnapshot(...)` 또는 별도 snapshot query context에 `snapshotCutoffAt`을 전달한다.
  - [x] current dashboard `getDashboard(...)` path에는 cutoff를 적용하지 않는다.
  - [x] `MetricBucketRepository`/`AcceptedMetricBucketJpaRepository`에 snapshot 전용 cutoff-aware query를 추가하거나, cutoff-aware query context를 기존 method와 명확히 분리한다.
  - [x] application-level aggregate, baseline aggregate, local percentile, histogram, endpoint evidence, recent bucket evidence, runtime ratio, latest freshness/gap query가 cutoff를 공유하는지 확인한다.
  - [x] `DashboardSnapshotReadModelEnricher`의 `instanceSummary`와 endpoint evidence refs가 cutoff 없는 instance query를 쓰지 않게 한다.
  - [x] cutoff 적용 범위가 큰 경우, 구현자는 top-level read model과 stored JSON 중 무엇을 이번 story에서 닫았고 무엇을 defer하는지 명시적으로 남긴다.

- [x] Late-data no-backfill guard 구현 (AC: 16~18, 27)
  - [x] cutoff 이후 accepted bucket 저장을 금지하지 않는다.
  - [x] 이미 생성된 snapshot/history/detail/trend를 late bucket으로 재계산하지 않는다.
  - [x] snapshot detail, marker, operational history, instance trend projection이 stored snapshot/read model만 읽는 기존 boundary를 유지한다.

- [x] Queue lag/state semantics regression guard (AC: 24~28)
  - [x] queue lag/backlog/worker failure를 lifecycle state input으로 넣지 않는다.
  - [x] queue lag/backlog/worker failure를 starter telemetry unreachable diagnosis로 직접 변환하지 않는다.
  - [x] operational event history와 snapshot marker copy에서 host application down certainty 표현이 생기지 않게 regression test를 보강한다.
  - [x] optional diagnostic을 남긴다면 metric/runbook/API 후보로 제한하고, state/read model 의미 변경과 분리한다.

- [x] Verification 수행 (AC: 1~30)
  - [x] focused unit/integration tests를 추가하거나 기존 test를 확장한다.
  - [x] `./gradlew :observability-portal:test`를 실행한다.
  - [x] `git diff --check`를 실행한다.

## Developer Handoff

### 권장 구현 방향

- Snapshot capture request에 `snapshotCutoffAt`을 명시적으로 싣는 방향이 가장 실수 여지가 적다. `currentWindowEndUtc`는 hourly boundary, `snapshotCutoffAt`은 accepted_at cutoff라는 두 값을 타입/이름으로 분리한다.
- Current dashboard와 snapshot dashboard가 같은 `buildDashboard(...)` 내부 구현을 공유하더라도, query context를 분리한다. 예: current path는 cutoff empty, snapshot path는 cutoff present.
- cutoff-aware repository method 이름에는 `AcceptedAtOrBefore` 또는 `ForSnapshot` 같은 단어를 넣어 current path에서 실수로 쓰지 않게 한다.
- `accepted_at` index가 이미 있으므로 cutoff 조건은 새 schema 없이 query 조건으로 시작할 수 있다. 새 migration은 필요성이 분명할 때만 추가한다.
- writer upsert의 priority-aware behavior는 유지한다. late bucket을 이유로 기존 hourly snapshot을 다시 쓰거나 backfill하는 job을 만들지 않는다.
- queue lag diagnostic은 가능하면 story 범위 안에서는 copy regression test와 metric 후보까지만 남긴다. UI/API field를 추가하면 current dashboard contract가 넓어지므로 reviewer가 의도적으로 볼 수 있게 한다.

### 주의할 파일

- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotScheduler.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCaptureService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotFallbackCaptureService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotReadModelEnricher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryProjector.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerClassifier.java`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`

## Non-Goals

- Story 12.5 범위인 DB batch throughput 개선, batch writer 최적화, JDBC batch, `ON CONFLICT` throughput claim 구현 금지.
- Lambda consumer, Lambda handler scaffold, event source mapping, separate worker service 생성 금지.
- `application`, `port`, `adapter` package 생성 금지.
- Snapshot recomputation job, late-data backfill pipeline, replay queue, correction workflow 생성 금지.
- Raw snapshot explorer, raw bucket explorer, endpoint timeseries, long-retention raw bucket history 생성 금지.
- 현재 dashboard 15분 current/baseline semantics 변경 금지.
- SQS direct fallback, mandatory shadow mode, AWS resource/IaC 생성, production autoscaling 구현 금지.
- Queue lag를 lifecycle state, starter connection diagnosis, host application down 판정에 직접 섞는 것 금지.

## Regression Risks and Review Hotspots

| 위험 | 영향 | 리뷰 포인트 |
| --- | --- | --- |
| Top-level dashboard read model만 cutoff를 적용하고 stored JSON의 `instanceSummary`, detail, evidence, trend 쪽 query가 cutoff 없는 method를 사용함 | snapshot row metadata와 `read_model_json` 내용이 서로 다른 시간 의미를 갖는다 | `DashboardSnapshotReadModelEnricher`와 instance-level repository calls까지 cutoff propagation을 확인한다. 범위가 크면 구현자가 명시적 defer decision을 남겨야 한다. |
| minute scheduler가 cutoff 이후 같은 hourly target을 반복 dispatch함 | 매 minute read model generation/write no-op이 발생하고 운영 log/metric이 오염된다 | capture service 호출 횟수 test로 scheduler-level first-eligible-tick guard를 확인한다. |
| cutoff 기준을 `bucket_end_utc`나 `created_at`처럼 다른 timestamp로 구현함 | queue delay를 흡수하지 못하거나 late bucket 정책이 흔들린다 | `accepted_metric_buckets.accepted_at <= snapshotCutoffAt` 조건을 query/test에서 확인한다. |
| current dashboard path에도 cutoff를 적용함 | query 시점 DB source-of-truth와 current/baseline 15분 semantics가 바뀐다 | `getDashboard(...)`와 `getDashboardForSnapshot(...)` path 분리를 확인한다. |
| late bucket을 보고 기존 snapshot을 update/backfill함 | immutable history/detail/trend 계약이 깨진다 | snapshot writer identity와 update reason이 late data backfill로 쓰이지 않는지 확인한다. |
| queue backlog를 stale/down 원인으로 직접 표현함 | host application down certainty copy가 재발한다 | lifecycle, marker, operational event history copy regression을 확인한다. |
| fallback threshold가 기존 65분에 남음 | capture delay 도입 후 fallback이 너무 이르게 발생한다 | 기본 `120s + 5m`에서 threshold가 67분인지 확인한다. |
| 12.5 성능 작업이 12.4에 섞임 | scope가 커지고 review 기준이 흐려진다 | batch writer, JDBC batch, ON CONFLICT throughput claim, benchmark profile 작업이 없는지 확인한다. |

## Test Guidance

필수 또는 강력 권장 검증:

- `DashboardSnapshotSchedulerTest`
  - cutoff 전 tick은 dispatch하지 않는다.
  - cutoff를 처음 지난 tick에서만 dispatch한다.
  - delayed capture 후에도 `currentWindowEndUtc`는 UTC hourly boundary다.
  - eligibility query에 `bucketEndUtc <= currentWindowEndUtc`와 `acceptedAt <= snapshotCutoffAt`이 전달된다.
- `DashboardSnapshotFallbackCaptureServiceTest`
  - 기본 threshold가 `60m + 120s + 5m = 67m`로 동작한다.
  - fallback capture는 이미 만들어진 current read model semantics를 바꾸지 않는다.
- `DashboardSnapshot*Cutoff*`
  - cutoff 이전 accepted bucket은 snapshot read model에 포함된다.
  - cutoff 이후 accepted bucket은 accepted bucket에는 저장되지만 기존 snapshot에는 backfill되지 않는다.
  - top-level aggregate와 stored JSON `instanceSummary`/evidence가 같은 cutoff를 쓴다.
- `LifecycleStateSemanticsRegressionTest`
  - queue lag/backlog/worker failure가 stale/down, telemetry unreachable, host application down certainty copy로 직접 수렴하지 않는다.
- `OperationalEventHistoryProjectorTest`
  - queue lag나 delayed pipeline copy가 `host application down`, `복구 완료`, `앱 정상 확정` 같은 단정 표현을 만들지 않는다.
- `DashboardSnapshotMarkerClassifierTest`
  - marker severity/copy가 stored snapshot state/evidence를 기준으로 하며 queue lag를 host health certainty로 표현하지 않는다.
- `MvcLayerBoundaryTest`
  - Lambda surface, application/port/adapter package, batch/job/raw explorer surface가 추가되지 않았는지 확인한다.
- 전체 regression:
  - `./gradlew :observability-portal:test`
  - `git diff --check`

## Open Questions / Defer Decisions

- Queue lag/backlog diagnostic을 metric만으로 둘지, read-side API/runbook 후보까지 남길지는 구현 중 선택 가능하다. 단, 어떤 선택도 lifecycle state나 starter connection diagnosis를 직접 바꾸면 안 된다.
- Top-level dashboard read model과 stored JSON cutoff propagation 범위가 예상보다 크면, 구현자는 이번 story에서 닫은 범위와 defer할 범위를 dev notes에 명시해야 한다. 무언가를 조용히 cutoff 없는 live query로 남기는 것은 허용하지 않는다.
- Severe late bucket max-lateness나 retention 제한은 이번 story의 필수 구현이 아니다. accepted bucket 저장 허용과 snapshot no-backfill 정책만 고정한다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- RED 확인: `./gradlew :observability-portal:test --tests '*DashboardSnapshotPropertiesTest' --tests '*DashboardSnapshotSchedulerTest' --tests '*DashboardSnapshotFallbackCaptureServiceTest' --tests '*DashboardReadModelServiceTest' --tests '*DashboardSnapshotReadModelEnricherCutoffTest' --tests '*DashboardSnapshotCapturePolicyTest'`
- Focused/required tests: `./gradlew :observability-portal:test --tests '*DashboardSnapshot*' --tests '*LifecycleStateSemanticsRegressionTest' --tests '*OperationalEventHistoryProjectorTest' --tests '*MvcLayerBoundaryTest' --tests '*ApplicationRepositoryIntegrationTest' --tests '*MetricBucketRepositoryIntegrationTest'`
- Full regression: `./gradlew :observability-portal:test`
- Whitespace check: `git diff --check`

### Completion Notes List

- Story 생성 단계에서는 구현하지 않았다.
- `portal.dashboard-snapshots.capture-delay=120s`, `portal.dashboard-snapshots.fallback-grace=5m` 기본값과 positive duration 검증을 추가했다.
- fallback threshold를 `60m + captureDelay + fallbackGrace`로 계산해 기본 67분으로 동작하게 했다.
- scheduler를 UTC minute tick으로 바꾸고 `snapshotCutoffAt = currentWindowEndUtc + captureDelay` 이전에는 dispatch하지 않으며, 같은 hourly target은 cutoff를 처음 지난 tick에서만 한 번 시도하게 했다.
- scheduler eligibility와 snapshot read model bucket query는 `accepted_metric_buckets.accepted_at <= snapshotCutoffAt` 조건을 공유한다.
- `DashboardReadModelService#getDashboardForSnapshot(...)`, `DashboardSnapshotCaptureRequest`, `DashboardSnapshotWriteCommand`, `DashboardSnapshotReadModelEnricher`, `DashboardSnapshotCapturePolicy`까지 cutoff를 전달해 top-level read model과 stored `read_model_json`의 `instanceSummary`/evidence가 같은 cutoff 기준을 사용한다.
- current dashboard path와 Instance Evidence current API는 기존 DB source-of-truth/current 15분 semantics를 유지한다.
- cutoff 이후 accepted bucket 저장은 막지 않고, snapshot writer identity와 stored snapshot projection boundary를 유지해 late-data backfill/recompute pipeline을 만들지 않았다.
- queue lag/backlog/worker failure가 lifecycle input, telemetry unreachable diagnosis, host application down certainty copy로 수렴하지 않도록 regression guard를 보강했다.
- Defer decision: 없음. Story 12.4 cutoff propagation 범위는 top-level dashboard read model과 stored JSON enrichment까지 닫았다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotCaptureRequest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/model/DashboardSnapshotWriteCommand.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCapturePolicy.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCaptureService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotFallbackCaptureService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotProperties.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotReadModelEnricher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotScheduler.java`
- `observability-portal/src/main/resources/application.properties`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/ApplicationRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryProjectorTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCapturePolicyTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotFallbackCaptureServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerClassifierTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotPropertiesTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotReadModelEnricherCutoffTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotSchedulerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterServiceIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotWriterServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/state/service/LifecycleStateSemanticsRegressionTest.java`
- `planning-artifacts/stories/12-4-snapshot-delay-and-pipeline-lag-semantics.md`

### Change Log

- 2026-06-05: Snapshot capture delay, fallback grace, accepted_at cutoff propagation, scheduler first-eligible-tick guard, late-data no-backfill and queue/state semantics regression tests implemented. Status moved to review.
- 2026-06-05: Code review follow-up closed heartbeat snapshot boundary and long capture-delay scheduler edge cases. Full portal test and diff check passed. Status moved to done.
