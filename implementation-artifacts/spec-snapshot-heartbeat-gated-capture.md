---
artifactType: quick-dev-spec
projectName: Spring Boot 운영 첫 화면 포털
status: done
date: 2026-06-06
scope: Dashboard snapshot scheduled/fallback capture eligibility policy
baseline_commit: 62b4746b2833730527cf40e6f51a69f3b8ca6375
---

# Snapshot Heartbeat-Gated Capture

## Intent

기존 5.8-a 계약은 `accepted_metric_buckets`를 한 번이라도 보낸 active application을 snapshot retention horizon 동안 hourly scheduled snapshot 대상으로 삼았다. 그 결과 smoke test처럼 일회성 bucket만 만든 application도 이후 heartbeat나 metric bucket이 전혀 없어도 매시간 `down` snapshot을 계속 만들 수 있었다.

이 동작은 backend data-plane 관점에서는 "최근 accepted bucket이 있었던 앱을 retention 동안 계속 상태 평가한다"는 의미가 있지만, 제품 경험상 사용자는 snapshot을 "포털이 실제로 계속 관찰한 기록"으로 이해한다. 새 telemetry가 없는 일회성 테스트 앱이 계속 `down` 기록을 만드는 것은 "간편한 모니터링 서비스"의 기대와 맞지 않는다.

구현된 정책은 앞으로 새로 저장되는 `hourly_scheduled` snapshot 후보와 `query_fallback` 저장 후보를 "accepted bucket 기반 관측 이력과 최근 starter heartbeat가 함께 있는 application"으로 제한한다. 단, heartbeat는 저장 허용 gate일 뿐 metric freshness, lifecycle state, read model source, recovery source, operational event source가 아니다.

## Source of Truth

이 문서는 아래 기존 계약을 후속 정책으로 보완한다.

- `implementation-artifacts/spec-story-5-8-a-dashboard-snapshot-writer-and-capture-policy-contract-decisions.md`
- `planning-artifacts/stories/5-8-a-dashboard-snapshot-writer-and-capture-policy.md`
- `planning-artifacts/contracts/starter-failure-semantics.md`
- `planning-artifacts/contracts/state-semantics.md`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotScheduler.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotFallbackCaptureService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`

충돌 시 이 문서가 Story 5.8-a의 scheduled/fallback 저장 eligibility 세부 정책보다 우선한다. 단, heartbeat를 metric freshness, host health, rule calculation, p95/p99, read model, operational event source로 합성하지 않는 기존 금지선은 유지한다.

## Policy Decisions

1. 새 `hourly_scheduled` snapshot eligibility는 accepted bucket 단독 기준이 아니다.
2. Scheduled snapshot 대상 application은 active 상태이고, snapshot retention horizon 안의 accepted bucket이 있으며, 최근 starter heartbeat도 있어야 한다.
3. "최근 heartbeat" 기준은 heartbeat row의 `interval_seconds`를 사용해 계산한다. `last_received_at_utc`가 `max(90초, interval_seconds * 3)` 안에 있는 경우만 최근 수신으로 인정한다.
4. heartbeat row가 없거나 freshness threshold를 넘은 application은 `hourly_scheduled` snapshot 대상에서 제외한다.
5. heartbeat-only application은 기존처럼 scheduled snapshot 대상이 아니다. accepted bucket도 여전히 필요하다.
6. dashboard query fallback capture도 무제한 snapshot 증가를 막기 위해 최근 heartbeat가 있는 경우에만 저장한다.
7. fallback 저장 조건에서 heartbeat가 missing/stale이면 snapshot 저장만 건너뛰고, dashboard current API는 fail-open으로 현재 read model을 계속 반환한다.
8. heartbeat는 metric freshness, lifecycle state, read model, recovery, operational event source로 합성하지 않는다.
9. state-change/high-confidence/short-strong-spike writer priority와 duplicate identity는 바꾸지 않는다.
10. `read_model_json` shape, `dashboard_snapshots` schema, OAuth/project registration/starter credential 흐름은 바꾸지 않는다.
11. 기존 snapshot row를 삭제하거나 재작성하지 않는다. 앞으로 새 scheduled/fallback 저장 조건만 제한한다.
12. 오래된 smoke/test application은 현재 화면에서 `stale/down`으로 보일 수 있지만, 새 hourly snapshot을 계속 만들지는 않는다.

## Acceptance Criteria

1. Given active application with accepted buckets and recent heartbeat, When hourly scheduler runs after capture delay, Then `hourly_scheduled` snapshot can be created.
2. Given active application with accepted buckets but no heartbeat row, When hourly scheduler runs, Then no scheduled snapshot is created.
3. Given active application with accepted buckets but stale heartbeat older than `max(90s, intervalSeconds * 3)`, When hourly scheduler runs, Then no scheduled snapshot is created.
4. Given heartbeat-only application without accepted buckets, When hourly scheduler runs, Then no scheduled snapshot is created.
5. Given dashboard current query and latest snapshot is missing/stale, When heartbeat is recent, Then query fallback may create a `query_fallback` snapshot.
6. Given dashboard current query and latest snapshot is missing/stale, When heartbeat is missing or stale, Then query fallback does not create a snapshot and the dashboard response still succeeds.
7. Given existing state-change/high-confidence/short-strong-spike capture policy, When implementing this change, Then capture reason priority and duplicate identity behavior remain unchanged.
8. Given backend contracts expose UTC timestamps, When implementing this policy, Then no frontend contract or timestamp display behavior is changed.
9. Given existing OAuth, project registration, starter credential issuance, ingest validation, and read model shape, When implementing this policy, Then those flows remain untouched.
10. Given existing dashboard snapshot rows, When this policy is deployed, Then existing rows are not deleted or rewritten.

## Implemented Anchors

- `StarterHeartbeatFreshnessPolicy` computes the recent heartbeat window as `max(90초, intervalSeconds * 3)`.
- `ApplicationRepository.findActiveApplicationsEligibleForScheduledSnapshot(...)` requires active application, accepted bucket inside retention/cutoff, and fresh `starter_heartbeat_telemetry`.
- `DashboardSnapshotScheduler` passes the scheduler requested time as the heartbeat freshness reference and dispatches only eligible applications.
- `DashboardSnapshotFallbackCaptureService` checks latest heartbeat freshness before evaluating fallback staleness and writing `query_fallback`.
- Focused tests cover recent/missing/stale heartbeat, heartbeat-only application exclusion, and fallback no-write/fail-open behavior.
- No migration was required because the policy changes service/repository eligibility only.

## Verification

- `./gradlew :observability-portal:test`
- `git diff --check`
- Optional manual check: with a DB containing old smoke buckets and stale/no heartbeat, start the portal and verify no new hourly `dashboard_snapshots` row is created after the next scheduler tick.
