---
artifactType: story
storyId: "12.4"
storyKey: "12-4-snapshot-delay-and-pipeline-lag-semantics"
epic: "Epic 12. SQS Buffered Ingest Transition"
title: "Snapshot Delay and Pipeline Lag Semantics"
architectureStyle: Traditional MVC
status: backlog
date: 2026-06-05
commitBoundary: "feat: add snapshot delay and pipeline lag semantics"
---

# Story 12.4 - Snapshot Delay and Pipeline Lag Semantics

## Status

backlog

## Story

구현자로서, SQS enqueue-to-persist delay가 dashboard snapshot/history 의미를 흔들지 않도록 snapshot delay와 late-data cutoff를 명확히 하고 싶다.

그래야 queue lag가 stale/down이나 host application down copy로 오해되지 않고, snapshot history가 immutable read model history로 유지된다.

## 목표

- SQS pipeline lag를 흡수할 configurable snapshot delay를 정의한다.
- Snapshot cutoff 이후 persisted late bucket의 no-backfill 정책을 고정한다.
- Queue lag/backlog diagnostic과 accepted bucket freshness 기반 stale/down semantics를 분리한다.
- Fallback snapshot staleness threshold를 snapshot delay와 grace 기준으로 재정의한다.

## Acceptance Criteria

1. Snapshot scheduler는 target current window cutoff 이후 configurable delay를 둔다.
2. Snapshot target window end는 기존 hourly boundary를 유지하고, delay는 capture 실행 시점만 늦춘다.
3. Delay 기본값은 worker batch cadence와 retry margin을 고려해 결정되며, current/baseline 15분 window 의미를 바꾸지 않는다.
4. `snapshotCutoffAt = currentWindowEndUtc + snapshotDelay` 후보가 문서화되거나 구현된다.
5. `persistedAt > snapshotCutoffAt` late bucket은 accepted bucket에는 저장될 수 있지만 이미 생성된 snapshot/read model history에는 backfill되지 않는다.
6. Late-data no-backfill은 snapshot/history 정책이며 accepted bucket 저장 금지가 아니라고 명시된다.
7. Fallback staleness threshold는 `60분 + snapshotDelay + grace` 후보로 재계산된다.
8. Queue lag, oldest message age, enqueue-to-persist lag는 lifecycle stale/down state를 직접 바꾸지 않고 별도 metric/runbook/API diagnostic 후보로 남는다.
9. stale/down은 계속 accepted bucket data freshness 기준이며, queue backlog나 worker failure를 host application down으로 표현하지 않는다.
10. Snapshot detail/history는 저장 당시 read model을 immutable하게 보여주며 current state를 재판정하지 않는다.
11. Backlog guard를 둘 경우 무한 skip을 막는 max delay 또는 incomplete-risk marker 후보가 함께 문서화된다.
12. Operational event history와 snapshot marker copy는 queue lag를 host health certainty로 표현하지 않는다.

## Non-Goals

- Snapshot recomputation job.
- Late-data backfill pipeline, replay queue, correction workflow.
- Raw snapshot explorer, endpoint timeseries, long-retention raw bucket history.
- Queue lag를 lifecycle state, starter connection diagnosis, host application down 판정에 직접 섞는 것.
- Batch writer throughput 최적화. 이는 Story 12.5 범위다.

## 구현 전 닫아야 할 결정

- Snapshot delay exact 기본값: 120초, 180초, 또는 batch cadence 기반 formula.
- Fallback threshold grace exact 값.
- Backlog guard를 둘지, 둔다면 oldest message age와 max snapshot delay 기준.
- Queue lag diagnostic을 Micrometer metric, API field, operator runbook 중 어디에 먼저 남길지.
- Late bucket의 `ingest_lag_seconds`, `late_for_snapshot_count` metric 이름.
- Snapshot cutoff 비교에 사용할 timestamp: `persistedAt`, `acceptedAt`, worker processed time 중 무엇인지.

## 참고해야 할 코드/문서

- `planning-artifacts/stories/12-1-architecture-and-contract-decision.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/time-buckets.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotScheduler.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCaptureService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotFallbackCaptureService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotCapturePolicy.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/main/java/com/observation/portal/common/time/AcceptedBucketFreshnessEvaluator.java`
- `observability-portal/src/main/java/com/observation/portal/domain/state/service/LifecycleStateService.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotSchedulerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotFallbackCaptureServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/state/service/LifecycleStateSemanticsRegressionTest.java`

## 테스트/검증 방법

- Timeline test: bucket end, enqueue, DB persist, snapshot cutoff, late arrival 시나리오를 검증한다.
- Scheduler test: delay 이후 capture하더라도 `targetWindowEnd`는 hourly boundary로 유지되는지 확인한다.
- Cutoff test: cutoff 이전 persisted bucket은 snapshot에 포함되고 cutoff 이후 persisted bucket은 기존 snapshot에 backfill되지 않는지 확인한다.
- Fallback test: fallback staleness threshold가 `60분 + delay + grace` 후보로 동작하는지 확인한다.
- Semantics regression: accepted bucket freshness 없는 상태, queue lag 있는 상태, heartbeat 있는 상태가 host down 확정 copy로 수렴하지 않는지 확인한다.
- Metric/runbook review: queue lag diagnostic이 lifecycle state와 다른 축으로 기록되는지 확인한다.

## 위험과 완화책

| 위험 | 영향 | 완화책 |
| --- | --- | --- |
| Snapshot이 너무 일찍 생성됨 | late bucket이 history에 누락됨 | configurable delay와 cutoff test를 둔다. |
| Queue backlog를 stale/down으로 오해함 | operator copy가 잘못됨 | queue lag diagnostic을 lifecycle state와 분리한다. |
| Delay가 너무 길어짐 | history freshness 저하 | max delay/backlog guard와 incomplete-risk marker 후보를 둔다. |
| Late bucket backfill 요구가 커짐 | replay/correction pipeline으로 scope 확장 | no-backfill을 Epic 12 policy로 명시한다. |
| Fallback threshold가 기존 65분에 묶임 | delayed scheduler가 불필요한 fallback을 만듦 | `60분 + delay + grace`로 재계산한다. |
