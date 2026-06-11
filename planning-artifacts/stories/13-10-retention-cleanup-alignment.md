---
artifactType: story
storyId: "13.10"
storyKey: "13-10-retention-cleanup-alignment"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "Retention cleanup alignment"
architectureStyle: Traditional MVC
status: done
date: 2026-06-11
phase: P9
workType: backend
implementationScope: "dashboard_snapshots and accepted_metric_buckets retention cleanup scheduler/service/properties, cutoff calculation, physical delete repository methods, and retention read-surface guards"
productionCodeChangeThisContext: true
plannedProductionCodeChange: true
sourceOfTruthMode: read-only
dependsOn:
  - P0
  - P5
  - P7
  - 13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment
  - 13-7-frontend-snapshot-history-detail-realignment
  - 13-8-backend-instance-dashboard-live-snapshot-mode-split
  - 13-9-frontend-instance-surface-split
blocks:
  - P10
  - 13-11-end-to-end-acceptance-and-demo-hardening
rollbackBoundary: "cleanup scheduler/property enablement, cleanup service cutoff/delete orchestration, repository bulk delete methods, and read-surface retention guards are separable rollback units"
---

# Story 13.10 - Retention Cleanup Alignment

## Status

done

2026-06-11: P9 retention cleanup alignment story artifact를 생성하고 sprint-status를 `ready-for-dev`로 정렬했다. 초기 생성 시점에는 production code, backend code/test, frontend code, migration/schema, Source of Truth 문서, 완료 story 본문/status를 구현/수정하지 않고 story artifact와 sprint-status만 최소 변경했다.

2026-06-11: cleanup schedule이 30분 scheduled snapshot slot/capture delay와 겹치지 않도록 Source of Truth와 story 기준을 매일 `01:15 KST`로 재정렬했다. 이번 후속 정렬도 production code, backend/frontend test/code, migration/schema, 완료 story 본문/status는 수정하지 않는다.

2026-06-11: BMAD dev-story workflow에 따라 Story 13.10 구현을 시작했고 sprint-status를 `in-progress`로 전환했다.

2026-06-11: BMAD dev-story 구현으로 retention cleanup scheduler/service/properties/result, UTC cutoff 계산, dashboard snapshot/accepted bucket physical delete, read-surface retention guard와 focused/full backend 검증을 완료하고 review 상태로 전환했다.

2026-06-11: BMAD code review blocker를 반영해 cleanup physical delete 기본값을 disabled rollout으로 정렬하고 packaged config/default/disabled tests와 full backend regression을 통과해 done 상태로 전환했다.

## Story

backend 구현자로서, dashboard snapshot과 accepted metric bucket cleanup을 14일 retention horizon, 매일 01:15 KST schedule, UTC cutoff 계산, `current_window_end_utc` / `bucket_end_utc` physical delete 기준으로 정렬하고 싶다.

그래야 14일 retention 안의 Application Snapshot Detail과 Instance Dashboard snapshot mode는 필요한 stored snapshot/evidence를 유지하고, retention 밖 snapshot/detail/instance evidence는 live dashboard나 current accepted bucket fallback 없이 expired/404/source absence로 일관되게 수렴한다.

## Source of Truth

아래 문서는 이 story의 기준이다. 이 story는 해당 문서의 의미를 재정의하지 않고 backend 구현 지침으로만 옮긴다.

1. `implementation-artifacts/sprint-status.yaml`
2. `planning-artifacts/epics.md`
3. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
4. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
5. `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
6. `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
7. `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
8. `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
9. `planning-artifacts/contracts/read-model-contract.md`
10. `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`
11. `planning-artifacts/stories/13-7-frontend-snapshot-history-detail-realignment.md`
12. `planning-artifacts/stories/13-8-backend-instance-dashboard-live-snapshot-mode-split.md`
13. `planning-artifacts/stories/13-9-frontend-instance-surface-split.md`
14. `_bmad/custom/project-context.md`
15. `planning-artifacts/architecture.md`
16. `planning-artifacts/architecture-implementation-supplement.md`
17. `planning-artifacts/project-structure.md`

## Background

Epic 13은 완료된 Epic 4/5/6/10 story를 다시 여는 작업이 아니라, 확정된 Dashboard Source of Truth를 새 alignment story 묶음으로 적용하는 tracking epic이다. Story 13.6은 scheduled snapshot slot과 marker/history/trend horizon을 `current_window_end_utc` 기준으로 정렬했고, Story 13.7은 Snapshot history/detail을 14일 retention 안의 30분 dashboard point 탐색과 stored `dashboard_snapshots.read_model_json` 복원 surface로 정렬했다. Story 13.8은 Instance Dashboard snapshot mode가 selected Application Snapshot row metadata와 현재 저장소에 남은 `accepted_metric_buckets`를 사용하도록 backend 계약을 분리했고, Story 13.9는 frontend surface에서 retention gap을 live/current fallback 없이 표현하도록 정렬했다.

P9의 목적은 read horizon 정렬 뒤 마지막 backend cleanup 단계를 닫는 것이다. 이 story는 "2주마다 cleanup"을 구현하지 않는다. 기본 retention horizon은 14일이지만 cleanup schedule은 매일 `Asia/Seoul` 기준 01:15 KST다. 실제 cutoff 계산과 DB timestamp 비교는 UTC 기준으로 수행한다.

`01:15 KST`는 30분 scheduled snapshot slot 경계인 `:00`/`:30`과 capture delay 이후 dispatch가 몰리는 `:02`/`:32` 근처를 피하기 위한 운영 시간이다. 이 선택은 cleanup cadence만 조정하며 retention horizon, cutoff 계산식, 삭제 기준 컬럼, source boundary는 바꾸지 않는다.

Canonical cutoff 계산은 아래로 고정한다.

```text
runAtUtc = clock.instant()
snapshotCutoffUtc = runAtUtc - retentionDays
metricEvidenceCutoffUtc = snapshotCutoffUtc - 30 minutes
```

삭제 조건은 "cutoff보다 엄격히 오래된 row"다.

```text
dashboard_snapshots.current_window_end_utc < snapshotCutoffUtc
accepted_metric_buckets.bucket_end_utc < metricEvidenceCutoffUtc
```

`accepted_metric_buckets`가 snapshot과 같은 14일 horizon을 따르더라도 physical delete cutoff는 30분 더 오래 보존한다. 이 30분 evidence grace는 가장 오래된 retained snapshot의 `(currentWindowStartUtc, currentWindowEndUtc]` selected instance evidence를 보존하기 위한 것이며, raw metric explorer나 14일 밖 analytics를 허용하는 정책이 아니다.

## Aligns

- `13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment`: cleanup의 snapshot authoritative timestamp는 P5가 정렬한 `dashboard_snapshots.current_window_end_utc`다.
- `13-7-frontend-snapshot-history-detail-realignment`: marker/history/date map/detail은 14일 retention 밖 snapshot을 표시하거나 live dashboard로 복원하지 않는다.
- `13-8-backend-instance-dashboard-live-snapshot-mode-split`: Instance Dashboard snapshot mode는 selected Application Snapshot row metadata와 `accepted_metric_buckets` evidence를 분리해 읽으며, bucket retention gap은 `metric_missing` 또는 `not_observed_in_window`로 수렴한다.
- `13-9-frontend-instance-surface-split`: frontend는 selected snapshot/instance retention gap을 source absence로 표시하고 live/current fallback을 만들지 않는다.
- `5-8-dashboard-snapshot-persistence-and-marker-contract` / `5-8-b-snapshot-marker-detail-recovery-source`: Application Snapshot Detail의 state/evidence source는 계속 stored `dashboard_snapshots.read_model_json`이다.
- `12-4-snapshot-delay-and-pipeline-lag-semantics`: cleanup은 queue lag, accepted_at cutoff, snapshot capture delay 의미를 read model source boundary와 섞지 않는다.

## Supersedes

아래 항목은 완료 story 본문이나 이력을 바꾸지 않고, P9 이후 backend 구현에서 좁게 대체하는 해석이다.

- retention cleanup을 "14일마다 한 번 실행"으로 이해하는 해석.
- cleanup cutoff나 DB 비교를 KST timestamp로 수행해도 된다는 해석.
- snapshot cleanup 기준을 `generated_at`, `created_at`, `accepted_at` 또는 capture provenance timestamp로 삼아도 된다는 해석.
- metric cleanup 기준을 `accepted_at` 또는 row `created_at`으로 삼아도 된다는 해석.
- `accepted_metric_buckets`를 snapshot보다 짧은 MVP horizon으로 지워도 된다는 해석.
- cleanup으로 사라진 snapshot/detail/instance evidence를 live dashboard, current accepted bucket, heartbeat, marker helper column으로 대체 복원해도 된다는 해석.

## Hardens

- Cleanup scheduler/service/properties는 `domain.cleanup.service` namespace에 새로 추가한다.
- Daily trigger는 `@Scheduled(cron = "0 15 1 * * *", zone = "Asia/Seoul")` 또는 동등한 Spring scheduling contract로 검증한다.
- `Clock`은 UTC로 정규화해 cutoff를 계산하고, KST는 schedule wall-clock 기준으로만 사용한다.
- Cleanup result는 `runAtUtc`, `snapshotCutoffUtc`, `metricEvidenceCutoffUtc`, `retentionDays`, `deletedDashboardSnapshots`, `deletedAcceptedMetricBuckets`, dry-run/enabled 상태를 반환하거나 기록할 수 있어야 한다.
- `dashboard_snapshots` bulk delete는 `current_window_end_utc < snapshotCutoffUtc`만 사용한다.
- `accepted_metric_buckets` bulk delete는 `bucket_end_utc < metricEvidenceCutoffUtc`만 사용한다.
- 같은 cutoff로 두 번 실행하면 두 번째 result count는 0으로 수렴해야 한다.
- Snapshot detail, marker/history/date map, Instance Dashboard snapshot mode는 row가 cleanup 전에 남아 있더라도 retention cutoff 밖이면 live/current fallback 없이 expired/404/source absence로 수렴한다.

## Rollback

- Scheduler/property enablement rollback은 cleanup service와 repository delete method를 제거하지 않고 daily trigger만 비활성화할 수 있어야 한다.
- Cleanup service rollback은 physical delete orchestration을 멈추되 read-surface retention guard와 existing P5/P7/P8 source semantics를 되돌리지 않는다.
- Repository bulk delete rollback은 `dashboard_snapshots` delete와 `accepted_metric_buckets` delete를 분리할 수 있어야 한다.
- Migration/schema가 필요하다고 판단되면 이번 story 구현 diff에 바로 포함하지 말고 query plan/row volume evidence와 함께 별도 승인 또는 후속 story로 분리한다.
- Rollback은 Source of Truth 문서, 완료 story 13.2~13.9 본문/status, `hourly_scheduled` token, writer identity, Instance Dashboard snapshot mode 계약을 되돌리지 않는다.

## Out of Scope

- 이번 create-story 컨텍스트의 production code, backend code/test, frontend code, migration/schema 구현.
- Source of Truth 의미 재정의. 2026-06-11에 승인 정렬된 cleanup schedule `01:15 KST`는 기준으로 삼는다.
- 완료 story 13.2/13.3/13.4/13.5/13.6/13.7/13.8/13.9 본문 수정 또는 done 상태 변경.
- raw metric explorer, endpoint timeseries, long-term analytics, baseline/adaptive threshold, incident folding cleanup 확장.
- 14일 밖 snapshot을 current dashboard/live accepted bucket으로 재생성하는 fallback.
- 별도 snapshot-derived helper table, `instance_dashboard_snapshots` table, operational event table, endpoint long-term projection table.
- heartbeat telemetry, catalog/archive table retention 정책.
- `accepted_metric_buckets`를 장기 analytics store로 승격하는 구현.
- migration/schema 변경. 구현 조사에서 cleanup delete 성능 때문에 index가 필요하다고 증명되면 별도 승인/후속 story로 분리한다.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.
- 기존 modified `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md` 수정, 삭제, stage.

## Acceptance Criteria

1. Given P9 implementation을 시작할 때, Then 구현자는 `dashboard-snapshot-retention-cleanup-source-of-truth.md`를 기준으로 읽고 "14일 retention horizon"과 "매일 01:15 KST cleanup schedule"을 같은 개념으로 합치지 않는다.
2. Given cleanup scheduler를 등록할 때, Then scheduler는 매일 `Asia/Seoul` 기준 `01:15 KST`에 실행되도록 `0 15 1 * * *` cron과 `Asia/Seoul` zone 또는 동등한 Spring scheduling contract를 사용한다.
3. Given cleanup service가 실행될 때, Then `runAtUtc = clock.instant()`를 UTC로 정규화하고 `snapshotCutoffUtc = runAtUtc - retentionDays`, `metricEvidenceCutoffUtc = snapshotCutoffUtc - 30 minutes`를 계산한다.
4. Given `retentionDays` 설정이 0 이하일 때, Then cleanup properties/service는 application startup 또는 bean 생성 단계에서 빠르게 실패한다.
5. Given cleanup retention-days config를 정렬할 때, Then 기본값은 14일이며 `dashboard_snapshots`와 `accepted_metric_buckets`의 MVP retention-days가 서로 달라지지 않는다.
6. Given cleanup scheduler enablement/dry-run property를 둘 때, Then enablement나 dry-run은 operational rollout control일 뿐 schedule cadence와 cutoff 계산 의미를 바꾸지 않는다.
7. Given `dashboard_snapshots` row의 `current_window_end_utc < snapshotCutoffUtc`이면, When physical cleanup이 실행되면, Then 해당 row는 bulk delete 대상이 된다.
8. Given `dashboard_snapshots` row의 `current_window_end_utc >= snapshotCutoffUtc`이면, When physical cleanup이 실행되면, Then 해당 row는 유지된다.
9. Given dashboard snapshot cleanup repository method를 구현할 때, Then `generated_at`, `created_at`, `accepted_at`, `last_observed_at`은 delete predicate에 사용하지 않는다.
10. Given `accepted_metric_buckets` row의 `bucket_end_utc < metricEvidenceCutoffUtc`이면, When physical cleanup이 실행되면, Then 해당 row는 bulk delete 대상이 된다.
11. Given `accepted_metric_buckets` row의 `bucket_end_utc >= metricEvidenceCutoffUtc`이면, When physical cleanup이 실행되면, Then 해당 row는 유지된다.
12. Given metric cleanup repository method를 구현할 때, Then `accepted_at`이나 row `created_at`은 delete predicate에 사용하지 않는다.
13. Given 가장 오래된 retained snapshot의 30분 window가 `(snapshotCutoffUtc - 30 minutes, snapshotCutoffUtc]` 경계에 걸릴 때, Then 해당 window의 bucket evidence는 `metricEvidenceCutoffUtc` grace 때문에 유지된다.
14. Given 같은 `runAtUtc` 또는 같은 cutoff로 cleanup service를 두 번 실행할 때, Then 두 번째 실행은 실패하지 않고 deleted count 0으로 끝난다.
15. Given cleanup 대상 row가 일부 이미 삭제되어 있을 때, Then cleanup은 idempotent하게 count/result를 반환하고 다음 실행에서 재시도 가능한 상태를 유지한다.
16. Given cleanup result를 반환하거나 log로 남길 때, Then result는 최소 `runAtUtc`, `snapshotCutoffUtc`, `metricEvidenceCutoffUtc`, `deletedDashboardSnapshots`, `deletedAcceptedMetricBuckets`를 포함한다.
17. Given cleanup service transaction boundary를 정할 때, Then table별 부분 삭제 가능성과 retry idempotency를 설명하고 test로 검증한다. 하나의 transaction으로 묶든 table별 transaction으로 나누든 cutoff predicate는 동일해야 한다.
18. Given Application Snapshot Detail이 snapshot row를 찾았지만 `current_window_end_utc < snapshotCutoffUtc`이면, Then detail은 stored JSON을 렌더링하지 않고 404/expired로 수렴한다.
19. Given Application Snapshot Detail이 retention 안의 snapshot row를 찾으면, Then state/evidence source는 계속 `dashboard_snapshots.read_model_json`이며 current dashboard, accepted bucket, heartbeat를 조인하지 않는다.
20. Given Snapshot marker/history/date map을 조회할 때, Then retention 밖 snapshot은 row가 남아 있어도 표시되지 않고 `current_window_end_utc >= snapshotCutoffUtc` horizon을 따른다.
21. Given Instance Dashboard snapshot mode가 selected Application Snapshot row를 읽을 때, Then selected row의 `currentWindowStartUtc/currentWindowEndUtc` metadata를 사용하고 Application Snapshot stored state/evidence를 재계산하거나 override하지 않는다.
22. Given selected snapshot row는 retention 안에 있지만 해당 window의 selected instance bucket이 cleanup으로 삭제됐거나 원래 없을 때, Then Instance Dashboard snapshot mode는 live/current metric fallback 없이 `metric_missing` 또는 `not_observed_in_window` data quality/observation UX로 수렴한다.
23. Given selected snapshot row가 retention 밖이면, Then Instance Dashboard snapshot mode는 selected instance metric evidence를 조회하지 않고 404/expired/source absence로 수렴한다.
24. Given cleanup implementation diff를 검토할 때, Then Source of Truth 문서, 완료 story 13.2~13.9, frontend source, migration/schema, `dbml-error.log`가 변경되지 않았음이 확인된다. 단, 이 story 구현 자체에서 승인된 backend production/test file은 후보 파일 목록 범위에서만 변경한다.
25. Given 새 공개 class/method/API/result model 또는 동작을 바로 이해하기 어려운 내부 helper를 추가할 때, Then AGENTS.md 기준에 따라 한국어 Javadoc/comment와 한국어 테스트 display name을 사용한다.

## Tasks / Subtasks

- [x] 현재 retention/read-surface/backend boundary를 조사한다. (AC: 1, 18~24)
  - [x] `DashboardSnapshotRepository` / `DashboardSnapshotJpaRepository`가 `current_window_end_utc` horizon으로 marker/history/trend를 조회하는 현재 상태를 확인한다.
  - [x] `MetricBucketRepository` / `AcceptedMetricBucketJpaRepository`에 cleanup delete method가 없고 read query가 `(windowStart, windowEnd]` bucket boundary를 쓰는 현재 상태를 확인한다.
  - [x] `DashboardSnapshotDetailService`, `DashboardSnapshotMarkerService`, `InstanceSnapshotTrendService`, `InstanceDashboardReadModelService`의 retention guard와 no-live-fallback tests를 확인한다.
  - [x] `PortalApplication`의 `@EnableScheduling`과 `domain.cleanup` package marker가 이미 존재함을 확인한다.

- [x] Cleanup properties와 cutoff calculation을 추가한다. (AC: 1~6, 13, 16)
  - [x] `RetentionCleanupProperties` 또는 동등한 config class를 `domain.cleanup.service`에 추가한다.
  - [x] 기본 retention-days는 14일로 두고 existing `portal.dashboard-snapshots.retention-days`와 diverge하지 않도록 reuse/alias/test 중 하나로 정렬한다.
  - [x] Scheduler enablement/dry-run/count-first property를 둘 경우 schedule/cutoff 의미와 분리한다.
  - [x] `Clock` 기반 UTC cutoff calculation helper를 만들고 KST는 schedule wall-clock 기준으로만 사용한다.
  - [x] `retentionDays <= 0` fast-fail test를 추가한다.

- [x] Cleanup scheduler/service/result를 구현한다. (AC: 2~6, 14~17, 25)
  - [x] `RetentionCleanupScheduler`를 추가하고 daily `01:15 KST` trigger를 검증한다.
  - [x] Scheduler는 cron trigger와 enablement/dry-run orchestration만 담당하고 cutoff/delete는 `RetentionCleanupService`에 위임한다.
  - [x] `RetentionCleanupService`는 snapshot cleanup과 metric cleanup count/result를 반환한다.
  - [x] 같은 cutoff 재실행, 일부 row missing, dry-run/count-first behavior를 service test로 검증한다.
  - [x] scheduler/service public method와 result model에는 한국어 Javadoc/comment를 작성한다.

- [x] Repository physical delete method를 추가한다. (AC: 7~15, 17, 24)
  - [x] `DashboardSnapshotRepository` facade에 `deleteDashboardSnapshotsWindowEndedBefore(OffsetDateTime snapshotCutoffUtc)` 또는 동등한 method를 추가한다.
  - [x] `DashboardSnapshotJpaRepository`에 `currentWindowEndUtc < :snapshotCutoffUtc` bulk delete를 추가하고 delete count를 반환한다.
  - [x] `MetricBucketRepository` facade에 `deleteAcceptedMetricBucketsEndedBefore(OffsetDateTime metricEvidenceCutoffUtc)` 또는 동등한 method를 추가한다.
  - [x] `AcceptedMetricBucketJpaRepository`에 `bucketEndUtc < :metricEvidenceCutoffUtc` bulk delete를 추가하고 delete count를 반환한다.
  - [x] delete predicate가 `generated_at`, `created_at`, `accepted_at`을 쓰지 않음을 integration test와 static grep으로 확인한다.
  - [x] cleanup delete 성능 index가 필요하면 query plan/evidence를 남기고 migration은 별도 승인/후속 story로 분리한다.

- [x] Snapshot detail/marker/history/date map retention guard를 보강한다. (AC: 18~20, 24)
  - [x] `DashboardSnapshotDetailService`가 row 존재 여부와 별개로 `current_window_end_utc >= snapshotCutoffUtc`를 확인하게 한다.
  - [x] `DashboardSnapshotMarkerService` / operational history / date map source가 `current_window_end_utc` retention horizon 밖 row를 표시하지 않는지 guard test를 보강한다.
  - [x] 404/expired/missing response는 live dashboard/current accepted bucket fallback 없이 유지한다.
  - [x] detail service constructor forbidden dependency test가 `MetricBucketRepository`, heartbeat, lifecycle/rule/read model service를 계속 거부하는지 유지한다.

- [x] Instance Dashboard snapshot mode retention gap guard를 보강한다. (AC: 21~23, 24)
  - [x] `InstanceDashboardReadModelService` snapshot mode가 selected snapshot row retention 밖이면 metric evidence query를 호출하지 않는지 검증한다.
  - [x] selected snapshot row는 retention 안이지만 `accepted_metric_buckets`가 cleanup으로 사라진 fixture를 추가해 `metric_missing` / `not_observed_in_window` 수렴을 검증한다.
  - [x] snapshot mode가 `accepted_at` cutoff query, Application Snapshot stored read model recalculation, current/live fallback을 호출하지 않는 기존 guard를 유지한다.

- [x] Verification과 보호 대상 확인을 수행한다. (AC: 1~25)
  - [x] focused cleanup/scheduler/repository/read-surface tests를 실행한다.
  - [x] `./gradlew :observability-portal:test`를 실행한다.
  - [x] static grep으로 `generated_at`/`created_at`/`accepted_at` cleanup predicate, 14일마다 cleanup copy, live fallback copy가 없는지 확인한다.
  - [x] `git diff --check`를 실행한다.
  - [x] `git status --short`로 Source of Truth 문서, 완료 story 13.2~13.9, frontend source, migration/schema, `dbml-error.log`, 기존 modified 13.6 보호 상태를 확인한다.

### Review Findings

- [x] [Review][Decision] Protected planning artifacts are still modified in the implementation diff — handled as separate 01:15 KST planning rebaseline context; this blocker-fix updated only the Story 13.10 review section among planning artifacts.
- [x] [Review][Patch] Cleanup job starts with physical delete enabled instead of disabled/dry-run rollout [observability-portal/src/main/resources/application.properties:8] — fixed by defaulting cleanup to disabled in config and `@Value` fallback, with packaged config/default/disabled tests.
- [x] [Review][Patch] Retained snapshot detail/marker can still expose expired previous snapshot metadata [observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailService.java:122]
- [x] [Review][Patch] Operational history still clamps to hard-coded 14 days instead of the configured retention-days horizon [observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryQuery.java:25]
- [x] [Review][Patch] Partial table delete retry idempotency is asserted in comments but not covered by a failure/retry test [observability-portal/src/main/java/com/observation/portal/domain/cleanup/service/RetentionCleanupService.java:16]

## Dev Notes

### Current Code State

- Active implementation baseline은 Traditional MVC + Service/Repository Layering이다. Portal package는 `com.observation.portal.domain` feature-first MVC 구조를 따른다.
- 문서, Javadoc, test display name, 핵심 구현 주석은 프로젝트 `AGENTS.md` 지침에 맞춰 한국어로 작성한다. 외부 API 이름, 클래스명, persisted token, field name처럼 원문 유지가 더 명확한 표현만 필요한 범위에서 영어를 함께 쓴다.
- `PortalApplication`은 이미 `@EnableScheduling`을 켜고 있다.
- `observability-portal/src/main/java/com/observation/portal/domain/cleanup/package-info.java`와 `domain/cleanup/service/package-info.java`는 존재하지만 cleanup scheduler/service class는 아직 없다.
- `DashboardSnapshotRepository`와 `DashboardSnapshotJpaRepository`는 marker/history/trend horizon을 `currentWindowEndUtc` / `current_window_end_utc`로 조회한다.
- `DashboardSnapshotDetailService`는 stored snapshot detail을 `dashboard_snapshots.read_model_json`에서만 조립하고 current dashboard, accepted bucket, heartbeat, lifecycle/rule/endpoint priority dependency를 받지 않는다.
- `DashboardSnapshotMarkerService`, `InstanceSnapshotTrendService`, `InstanceDashboardReadModelService`는 `portal.dashboard-snapshots.retention-days:14`를 사용해 14일 horizon clamp 또는 snapshot retention guard를 수행한다.
- `InstanceDashboardReadModelService` snapshot mode는 selected Application Snapshot row metadata window를 쓰고 selected instance metric evidence는 non-cutoff `accepted_metric_buckets` query로 재구성한다. 기존 tests는 retention 밖 snapshot row가 current metric fallback 없이 empty로 수렴하는지 검증한다.
- `AcceptedMetricBucketJpaRepository`에는 많은 window read query와 `AcceptedAtOrBefore` snapshot read query가 있지만 cleanup bulk delete method는 아직 없다.
- `application.properties`에는 `portal.dashboard-snapshots.retention-days`가 명시돼 있지 않고 code-level default `14`가 쓰인다. cleanup story는 이 default와 config naming을 엇갈리게 만들지 않아야 한다.
- 현재 작업트리에는 기존 modified `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`와 untracked `dbml-error.log`가 있다. P9 구현자는 두 파일을 되돌리거나 삭제하거나 stage하지 않는다.

### Retention vs Schedule Contract

- `retentionDays=14`는 보관 horizon이다.
- cleanup schedule은 매일 `Asia/Seoul` 기준 `01:15 KST`다.
- `01:15 KST`는 30분 scheduled snapshot slot 경계인 `:00`/`:30`과 capture delay 이후 dispatch가 몰리는 `:02`/`:32` 근처를 피하기 위한 운영 시간이다.
- "14일 retention"은 "14일마다 cleanup 실행"이 아니다.
- KST는 사람이 예측 가능한 wall-clock trigger 기준이다.
- cutoff 계산과 DB timestamp 비교는 UTC 기준이다.
- `snapshotCutoffUtc = runAtUtc - retentionDays`
- `metricEvidenceCutoffUtc = snapshotCutoffUtc - 30 minutes`
- delete predicate는 cutoff보다 엄격히 오래된 row만 지운다: `< effectiveCutoffUtc`

### Source Semantics

- Application Dashboard live source는 `accepted_metric_buckets`다.
- Application Snapshot Detail source는 `dashboard_snapshots.read_model_json`이다.
- Snapshot marker/history/date map source는 `dashboard_snapshots` helper/index row이며 state/evidence source가 아니다.
- Instance Dashboard snapshot mode source는 selected Application Snapshot row metadata + 현재 저장소에 남은 `accepted_metric_buckets`다.
- Cleanup은 오래된 row를 지우는 작업이지 source boundary를 바꾸거나 남은 source에서 다른 read model을 재계산하는 작업이 아니다.
- Cleanup으로 사라진 snapshot/detail/instance evidence는 live dashboard, current accepted bucket, heartbeat, marker helper column으로 복원하지 않는다.

### Database / Migration Notes

- `accepted_metric_buckets`에는 `bucket_end_utc` column과 `idx_buckets_app_last_end (application_id, bucket_end_utc desc)`가 있다. cleanup은 global `bucket_end_utc < cutoff` delete라 query plan 확인이 필요할 수 있다.
- `dashboard_snapshots`에는 `current_window_end_utc` column과 `uk_dashboard_snapshots_application_current_window_end` unique constraint가 있다. 기존 `idx_dashboard_snapshots_project_app_generated_desc`, `idx_dashboard_snapshots_created_at`, `idx_dashboard_snapshots_app_capture_generated`는 cleanup predicate 전용 index가 아니다.
- 이 story의 기본 구현은 migration/schema 변경 없음이다. delete 성능 때문에 index가 필요하다고 증명되면 migration은 별도 승인 또는 후속 story로 분리한다.
- Flyway migration이 schema source of truth다. Hibernate DDL auto create/update는 사용하지 않는다.

## Candidate Implementation File List

- `observability-portal/src/main/java/com/observation/portal/domain/cleanup/service/RetentionCleanupScheduler.java`
  - Candidate new file: daily `01:15 KST` trigger와 enablement/dry-run orchestration만 담당한다.
- `observability-portal/src/main/java/com/observation/portal/domain/cleanup/service/RetentionCleanupService.java`
  - Candidate new file: UTC cutoff 계산, repository delete 호출, idempotent result/count를 담당한다.
- `observability-portal/src/main/java/com/observation/portal/domain/cleanup/service/RetentionCleanupProperties.java`
  - Candidate new file: retention-days, enabled/dry-run/count-first property validation과 default alignment를 담당한다.
- `observability-portal/src/main/java/com/observation/portal/domain/cleanup/service/RetentionCleanupResult.java`
  - Candidate new file or nested record: cleanup run metadata와 deleted counts를 운반한다.
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
  - Candidate update: `current_window_end_utc < snapshotCutoffUtc` bulk delete facade를 추가한다.
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
  - Candidate update: Spring Data JPA bulk delete query와 count return을 추가한다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
  - Candidate update: `bucket_end_utc < metricEvidenceCutoffUtc` bulk delete facade를 추가한다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
  - Candidate update: Spring Data JPA bulk delete query와 count return을 추가한다.
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailService.java`
  - Candidate update: row 존재와 별개인 `current_window_end_utc` retention guard를 추가한다.
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerService.java`
  - Candidate update: repository horizon과 service-level row guard가 같은 cutoff 의미를 유지하는지 보강한다.
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryQuery.java`
  - Candidate read-only or minimal update: history source horizon이 `current_window_end_utc` retention clamp를 유지하는지 확인한다.
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryService.java`
  - Candidate read-only or minimal update: retained snapshot source만 event projection에 들어가는지 확인한다.
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceDashboardReadModelService.java`
  - Candidate update: snapshot mode retention guard를 cleanup cutoff helper와 정렬한다.
- `observability-portal/src/main/resources/application.properties`
  - Candidate update only if cleanup enablement/dry-run/retention property default를 명시해야 한다.

## Candidate Test File List

- `observability-portal/src/test/java/com/observation/portal/domain/cleanup/service/RetentionCleanupPropertiesTest.java`
  - Candidate new file: retentionDays positive validation, default 14일, enablement/dry-run property meaning을 검증한다.
- `observability-portal/src/test/java/com/observation/portal/domain/cleanup/service/RetentionCleanupServiceTest.java`
  - Candidate new file: UTC cutoff calculation, 30분 evidence grace, idempotent result/count, partial missing rows를 검증한다.
- `observability-portal/src/test/java/com/observation/portal/domain/cleanup/service/RetentionCleanupSchedulerTest.java`
  - Candidate new file: `0 15 1 * * *` + `Asia/Seoul` schedule, scheduler->service delegation, disabled/dry-run behavior를 검증한다.
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepositoryIntegrationTest.java`
  - Candidate update: `current_window_end_utc < snapshotCutoffUtc` delete와 boundary keep/delete를 PostgreSQL/Flyway schema로 검증한다.
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
  - Candidate update: `bucket_end_utc < metricEvidenceCutoffUtc` delete, 30분 evidence grace, `accepted_at` non-predicate를 검증한다.
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailServiceTest.java`
  - Candidate update: retention 밖 row가 stored JSON render 없이 empty/404 후보로 수렴하고 forbidden current recalculation dependency가 없음을 검증한다.
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerServiceTest.java`
  - Candidate update: retention 밖 marker row exclusion, empty-state safe copy, `currentWindowEndUtc` order를 검증한다.
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryServiceTest.java`
  - Candidate update only if history service needs explicit retention guard beyond repository horizon.
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceDashboardReadModelServiceTest.java`
  - Candidate update: retention outside snapshot no metric query, retained snapshot with missing bucket -> `metric_missing`/`not_observed_in_window`, no current fallback.
- `observability-portal/src/test/java/com/observation/portal/domain/instance/model/InstanceDashboardReadModelShapeTest.java`
  - Candidate guard reference: snapshot mode semantics remain `applicationSnapshotRecalculated=false`, `markerIsStateSource=false`.

## Suggested Verification Commands

```bash
./gradlew :observability-portal:test \
  --tests 'com.observation.portal.domain.cleanup.service.RetentionCleanupPropertiesTest' \
  --tests 'com.observation.portal.domain.cleanup.service.RetentionCleanupServiceTest' \
  --tests 'com.observation.portal.domain.cleanup.service.RetentionCleanupSchedulerTest' \
  --tests 'com.observation.portal.domain.snapshot.repository.DashboardSnapshotRepositoryIntegrationTest' \
  --tests 'com.observation.portal.domain.bucket.repository.MetricBucketRepositoryIntegrationTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotDetailServiceTest' \
  --tests 'com.observation.portal.domain.snapshot.service.DashboardSnapshotMarkerServiceTest' \
  --tests 'com.observation.portal.domain.history.service.OperationalEventHistoryServiceTest' \
  --tests 'com.observation.portal.domain.instance.service.InstanceDashboardReadModelServiceTest' \
  --tests 'com.observation.portal.domain.instance.model.InstanceDashboardReadModelShapeTest'

./gradlew :observability-portal:test

rg -n "14일마다|every 14 days|generated_at <|created_at <|accepted_at <|delete.*generated|delete.*created|delete.*accepted" \
  observability-portal/src/main/java \
  observability-portal/src/test/java

rg -n "current dashboard.*fallback|current accepted.*fallback|live.*fallback|read_model_json|current_window_end_utc|bucket_end_utc|metricEvidenceCutoffUtc|snapshotCutoffUtc" \
  observability-portal/src/main/java/com/observation/portal/domain/cleanup \
  observability-portal/src/main/java/com/observation/portal/domain/snapshot \
  observability-portal/src/main/java/com/observation/portal/domain/instance \
  observability-portal/src/test/java/com/observation/portal/domain

git diff --check
git status --short
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Plan

- `domain.cleanup.service`에 cleanup properties/result/service/scheduler를 추가하고 retention-days는 기존 `portal.dashboard-snapshots.retention-days` 기본 14일을 재사용한다.
- Scheduler는 매일 `0 15 1 * * *` / `Asia/Seoul` trigger와 enabled/dry-run rollout control만 담당하고 cutoff/delete는 service에 위임한다.
- Cleanup service는 UTC `runAtUtc` 기준으로 `snapshotCutoffUtc = runAtUtc - retentionDays`, `metricEvidenceCutoffUtc = snapshotCutoffUtc - 30 minutes`를 계산하고 table별 repository delete를 idempotent하게 호출한다.
- Physical delete predicate는 `dashboard_snapshots.current_window_end_utc < snapshotCutoffUtc`, `accepted_metric_buckets.bucket_end_utc < metricEvidenceCutoffUtc`로 제한한다.
- Snapshot detail/marker/history/trend/Instance Dashboard snapshot mode는 retention 밖 row를 live/current fallback 없이 empty/source absence 또는 metric data-quality limitation으로 수렴하게 보강한다.

### Debug Log References

- 2026-06-11T12:40:33+0900: `git status --short`에서 기존 modified `planning-artifacts/stories/13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment.md`와 untracked `dbml-error.log`를 확인했다.
- 2026-06-11T12:40:33+0900: `implementation-artifacts/sprint-status.yaml`, dashboard realignment roadmap/sequence, retention/snapshot/instance Source of Truth, Story 13.6~13.9, `_bmad/custom/project-context.md`를 read-only context로 확인했다.
- 2026-06-11T12:40:33+0900: cleanup package marker만 존재하고 actual cleanup scheduler/service/delete methods는 아직 없음을 확인했다.
- 2026-06-11T12:44:13+0900: cleanup schedule을 30분 scheduled snapshot slot/capture delay와 겹치지 않도록 `01:15 KST`로 Source of Truth와 story 기준을 정렬했다.
- 2026-06-11T12:50:41+0900: BMAD dev-story workflow에 따라 Story 13.10과 sprint-status를 `in-progress`로 전환했다.
- 2026-06-11T13:00:00+0900: RED 확인으로 cleanup type/delete method/detail constructor 미구현 compile failure를 확인했다.
- 2026-06-11T13:01:00+0900: cleanup service/scheduler/properties/result, repository delete, read-surface retention guard를 구현한 뒤 cleanup/detail/marker/history/trend/instance focused tests가 통과했다.
- 2026-06-11T13:01:29+0900: `DashboardSnapshotRepositoryIntegrationTest`, `MetricBucketRepositoryIntegrationTest`가 통과해 physical delete predicate와 idempotent second run을 확인했다.
- 2026-06-11T13:02:33+0900: `./gradlew :observability-portal:test` 전체 regression이 통과했다.
- 2026-06-11T13:03:00+0900: static grep으로 `0 15 1`/`Asia/Seoul`, cleanup delete predicate의 `currentWindowEndUtc`/`bucketEndUtc`, accepted/generated/created cleanup predicate 부재, live/current fallback 미도입을 확인했다.
- 2026-06-11T13:03:00+0900: `git diff --check`가 통과했고 `git status --short`로 기존 protected modified SoT/13.6 및 untracked `dbml-error.log`가 여전히 보호 대상으로 남아 있음을 확인했다.

### Completion Notes List

- Story 13.10 artifact를 ready-for-dev 상태로 생성했다.
- Sprint status의 `13-10-retention-cleanup-alignment`를 backlog에서 ready-for-dev로 정렬했다.
- Cleanup schedule을 `01:15 KST`로 정렬하기 위해 retention cleanup Source of Truth와 alignment sequence/story 기준을 갱신했다.
- 이번 컨텍스트에서는 production code, backend/frontend test/code, migration/schema, 완료 story 본문/status를 수정하지 않았다.
- 기존 modified Story 13.6과 untracked `dbml-error.log`는 보호 대상으로 유지한다.
- `RetentionCleanupProperties`, `RetentionCleanupService`, `RetentionCleanupScheduler`, `RetentionCleanupResult`를 추가해 14일 retention horizon, 매일 01:15 KST schedule, UTC cutoff, enabled/dry-run rollout control을 구현했다.
- `dashboard_snapshots` cleanup은 `current_window_end_utc < snapshotCutoffUtc`, `accepted_metric_buckets` cleanup은 `bucket_end_utc < metricEvidenceCutoffUtc`만 사용하도록 repository facade/JPA bulk delete를 추가했다.
- Cleanup result는 `runAtUtc`, `snapshotCutoffUtc`, `metricEvidenceCutoffUtc`, `retentionDays`, table별 delete count, enabled/dry-run 상태를 반환한다.
- Snapshot detail, marker/history/date map source, Instance Snapshot Trend, Instance Dashboard snapshot mode retention guard를 보강해 retention 밖 row를 live/current fallback 없이 source absence로 수렴시킨다.
- Migration/schema, frontend, Source of Truth 문서, 완료 story 13.2~13.9 본문/status, 기존 modified 13.6, `dbml-error.log`는 이번 구현 diff로 수정하지 않았다.

### File List

- `planning-artifacts/stories/13-10-retention-cleanup-alignment.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/cleanup/service/RetentionCleanupProperties.java`
- `observability-portal/src/main/java/com/observation/portal/domain/cleanup/service/RetentionCleanupResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/cleanup/service/RetentionCleanupScheduler.java`
- `observability-portal/src/main/java/com/observation/portal/domain/cleanup/service/RetentionCleanupService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/history/service/OperationalEventHistoryService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceDashboardReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerService.java`
- `observability-portal/src/main/resources/application.properties`
- `observability-portal/src/test/java/com/observation/portal/domain/cleanup/service/RetentionCleanupPropertiesTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/cleanup/service/RetentionCleanupSchedulerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/cleanup/service/RetentionCleanupServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/history/service/OperationalEventHistoryServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceDashboardReadModelServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/instance/service/InstanceSnapshotTrendServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/repository/DashboardSnapshotRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotDetailServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotMarkerServiceTest.java`

### Change Log

- 2026-06-11: P9 retention cleanup alignment story artifact 생성 및 sprint-status ready-for-dev 정렬.
- 2026-06-11: cleanup schedule을 snapshot slot/capture delay와 겹치지 않는 `01:15 KST`로 Source of Truth와 story 기준에 반영했다.
- 2026-06-11: Retention cleanup scheduler/service/properties/result, UTC cutoff calculation, physical delete repository methods, read-surface retention guards, focused/full backend tests를 구현하고 story/sprint-status를 review로 전환했다.
