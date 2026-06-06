---
artifactType: story
storyId: "12.6"
storyKey: "12-6-benchmark-evidence-run-and-report"
epic: "Epic 12. SQS Buffered Ingest Transition"
title: "Benchmark Evidence Run and Report"
architectureStyle: Traditional MVC
status: done
date: 2026-06-05
commitBoundary: "test: record sqs ingest benchmark evidence"
---

# Story 12.6 - Benchmark Evidence Run and Report

## Status

done

2026-06-05: Story 12.5의 batch writer와 benchmark guard가 main에 merge된 뒤, 실제 정량 수치 evidence를 생성하는 후속 story로 새로 추가했다.

2026-06-05: opt-in Story 12.6 benchmark runner로 local/isolated Testcontainers PostgreSQL evidence run을 수행했고, sanitized numeric artifact를 `implementation-artifacts/benchmark-evidence/story-12-6/`에 생성했다. Redaction scan 통과 후 review로 전환한다.

2026-06-06: Review follow-up으로 benchmark scenario test의 class-level opt-in guard, pretty JSON key redaction scan, stale artifact 방지용 rerun guard를 보강했다. 최종 benchmark artifact 수치와 Completion Notes를 동기화하고 done으로 전환한다.

## Story

구현자로서, Story 12.5에서 만든 opt-in benchmark profile/script/manifest/redaction guard를 사용해 SQS buffered ingest 전환의 정량 evidence를 실제 수치로 남기고 싶다.

그래야 request latency 개선과 DB batch throughput 개선을 같은 주장으로 섞지 않으면서, README의 "local 또는 격리 benchmark profile 기준 portfolio evidence" 방침에 맞는 보수적인 report를 남길 수 있다.

## Source of Truth

아래 문서를 먼저 읽고 구현한다. 충돌처럼 보이는 경우 Story 12.1의 architecture decision, Story 12.5의 benchmark guard, README의 SQS Buffered Ingest 방침을 우선한다.

1. `AGENTS.md`
2. `README.md`
3. `_bmad/custom/project-context.md`
4. `implementation-artifacts/sprint-status.yaml`
5. `planning-artifacts/stories/12-1-architecture-and-contract-decision.md`
6. `planning-artifacts/stories/12-2-ingest-enqueue-boundary.md`
7. `planning-artifacts/stories/12-3-spring-boot-sqs-worker-mvp-and-idempotency.md`
8. `planning-artifacts/stories/12-4-snapshot-delay-and-pipeline-lag-semantics.md`
9. `planning-artifacts/stories/12-5-batch-writer-and-performance-verification.md`
10. `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
11. `planning-artifacts/epic-11-12-operational-alerts-and-sqs-batch-ingest-plan.md`
12. `planning-artifacts/database-schema.md`

## Story 12.1~12.5에서 이미 닫힌 결정

- Consumer는 Spring Boot portal 내부 worker다. Lambda consumer, Lambda handler scaffold, Lambda event source mapping, 별도 worker service/deployment는 Epic 12 범위가 아니다.
- SQS mode는 opt-in이고 rollback은 `portal.ingest.buffer.mode=direct` 복귀다.
- `202 queued`는 enqueue 성공만 뜻하며 DB 저장 완료, dashboard freshness current, snapshot 반영 완료를 뜻하지 않는다.
- Story 12.3 worker correctness는 유지한다. same key/same hash duplicate는 no-op success, same key/different hash와 same instance bucket/different key는 application DLQ 대상이다.
- Story 12.4 snapshot cutoff/no-backfill, queue lag diagnostic, stale/down naming guard는 변경하지 않는다.
- Story 12.5는 bounded batch writer, catalog grouping, `ON CONFLICT DO NOTHING RETURNING`, benchmark manifest/report skeleton, redaction guard를 구현했다.
- Phase 1 request latency 개선과 Phase 2 DB batch throughput 개선은 서로 다른 claim boundary다.

## Current Code State

- `MetricIngestQueueWorker#pollOnce()`는 source queue receive page를 `worker.maxBatchSize` 단위로 잘라 `MetricIngestQueueProcessor#processBatch(...)`에 전달한다.
- `MetricIngestQueueProcessor#processBatch(...)`는 malformed message를 writer에 넣지 않고, valid command만 `MetricBucketRepository#insertBatch(...)`로 넘긴다.
- `MetricBucketRepository#insertBatch(...)`는 pre-read, catalog grouping, PostgreSQL multi-row `ON CONFLICT DO NOTHING RETURNING`, post-read classification으로 command별 result를 반환한다.
- `ApplicationCatalogRepository#getOrCreateBatch(...)`는 application/instance grouping과 PostgreSQL upsert로 first/lastSeen을 monotonic하게 갱신한다.
- `IngestBenchmarkProperties`, `IngestBenchmarkManifest`, `IngestBenchmarkReportTemplate`, `IngestBenchmarkRedactionScanner`, `application-benchmark-sqs-ingest.properties`, `scripts/benchmark/run-sqs-ingest-benchmark.py`는 opt-in benchmark guard를 제공한다.
- Story 12.6 구현 후 benchmark script는 `IngestBenchmarkScenarioRunTest`를 호출해 실제 request/persist 측정 loop, DB reset/seed, scenario별 metric collection, final numeric report generation을 수행한다.
- 실제 정량 수치 artifact는 `implementation-artifacts/benchmark-evidence/story-12-6/`에 생성됐다. 수치 없는 개선율, production 성능, autoscaling, cost, dashboard UI 성능 claim을 하면 안 된다.

## 목표

- 같은 primary fixture로 Phase 1 request latency와 Phase 2 DB batch throughput을 각각 측정한다.
- Direct insert path, SQS enqueue path, Worker MVP path, Batch writer path evidence를 분리해서 저장한다.
- 실제 수치가 들어간 sanitized benchmark report artifact를 생성한다.
- Benchmark output directory redaction scan을 gate로 유지한다.
- README/portfolio에 옮길 수 있는 보수적인 summary 문구를 작성하되, production/autoscaling/cost/dashboard UI 성능 claim은 하지 않는다.

## Scope / Non-Scope

포함:

- `benchmark-sqs-ingest` opt-in profile 또는 explicit `--opt-in` flag로만 실행되는 local/isolated benchmark runner 보강.
- Primary fixture `applicationCount=1`, `instanceCount=30` 유지.
- 동일 fixture payload, 동일 idempotency distribution, 동일 DB 초기 상태를 scenario별로 재현하는 seed/reset.
- Phase 1 direct request latency vs SQS enqueue request latency 측정.
- Phase 2 worker MVP message-by-message persistence baseline vs batch writer persistence 측정.
- p50/p95/p99, enqueue duration, enqueue-to-persist lag, DB statement/round-trip count, batch duration, persisted buckets/sec 기록.
- `implementation-artifacts/benchmark-evidence/story-12-6/` 또는 `build/reports/ingest-benchmark` 산출물을 sanitized report로 정리. Git 추적 여부는 아래 Acceptance Criteria 기준을 따른다.
- benchmark output redaction scan, report claim guard tests.

제외:

- Lambda consumer, Lambda handler scaffold, event source mapping, separate worker service/deployment.
- production load test, autoscaling proof, cloud benchmark suite, cost model/capacity planning.
- dashboard UI performance claim.
- dashboard read model, lifecycle state, p95/p99, endpoint priority 계산 변경.
- raw metric explorer, endpoint timeseries, long-retention analytics.
- snapshot backfill/replay pipeline.
- default local/dev/test/smoke/CI profile의 DB pool, queue mode, batch size 변경.

## Benchmark Scenario Design

### Primary Fixture

- `applicationCount=1`
- `instanceCount=30`
- synthetic application name/environment는 고정한다. 예: `orders-api`, `prod`.
- 30개 synthetic instance는 `orders-api-bench-001`부터 `orders-api-bench-030` 같은 synthetic name을 사용한다.
- fixture는 실제 EC2 30대, production distributed load test, autoscaling proof가 아니다.
- duplicate ratio와 conflict ratio는 manifest에 기록한다. Primary evidence는 conflict 없는 normal path와 same key/same hash duplicate no-op을 포함할 수 있지만, deterministic conflict는 별도 correctness smoke로 분리해도 된다.

### Phase 1 - Request Latency

비교 대상:

1. Direct insert path
   - `portal.ingest.buffer.mode=direct`
   - HTTP ingest request가 DB insert 완료 후 응답한다.
   - 측정 지표: request p50/p95/p99, bucket write duration, request thread DB statement/round-trip count 후보.

2. SQS enqueue path
   - `portal.ingest.buffer.mode=fake` 또는 명시 opt-in real/local SQS mode.
   - HTTP ingest request가 enqueue 성공 후 `202 queued`로 응답한다.
   - 측정 지표: request p50/p95/p99, enqueue duration, `202 queued` count, request thread accepted bucket DB write 없음 evidence.

주의:

- Direct response는 저장 완료 latency이고 SQS response는 queued latency다. 같은 표에서 "end-to-end persist latency"처럼 비교하지 않는다.
- real SQS를 쓰지 않고 fake queue로 실행하면 report에 fake queue라고 명시하고 AWS SQS network latency를 주장하지 않는다.

### Phase 2 - DB Batch Throughput

비교 대상:

1. Worker MVP message-by-message baseline
   - Story 12.3 path의 `MetricIngestQueueProcessor#process(...)` 또는 동등한 message별 repository insert loop를 benchmark-only harness에서 사용한다.
   - 측정 지표: enqueue-to-persist lag 후보, per-message persist duration, per-message repository/statement baseline.

2. Batch writer path
   - Story 12.5 path의 `MetricIngestQueueProcessor#processBatch(...)` 또는 `MetricBucketRepository#insertBatch(...)`를 사용한다.
   - 측정 지표: batch size, batch persist duration, inserted/no-op/conflict counts, `bucketStatementCount`, persisted buckets/sec.

주의:

- Phase 2는 DB persistence throughput evidence이며 HTTP request latency claim이 아니다.
- Worker MVP path는 correctness/lag baseline으로만 표현하고 DB batch throughput improvement claim을 하지 않는다.

## Evidence Artifact Contract

Benchmark run은 최소 아래 파일을 생성한다.

- `manifest.json`
  - runId, gitRevision, dirty 여부
  - fixture: payload count, duplicate ratio, conflict ratio, `applicationCount=1`, `instanceCount=30`, cadence
  - runtime: Java version, Spring profile, worker batch size/age, scenario names
  - host: OS, CPU count, memory limit 또는 machine type
  - database: PostgreSQL version, RDS reference spec, fallback reason
  - queue: fake/SQS/LocalStack 여부, region과 queue type. queue URL은 저장하지 않는다.
  - timing: warmup duration, measurement duration, clock source
  - redaction: scan status
- `phase-1-request-latency.json`
  - direct and enqueue scenario의 count, p50, p95, p99, min/max, failure count
- `phase-2-db-throughput.json`
  - worker MVP and batch writer scenario의 count, duration, statement count, buckets/sec, inserted/no-op/conflict/transient count
- `report.md`
  - Phase 1, Worker MVP baseline, Phase 2를 별도 section으로 표현
  - README/portfolio에 옮길 수 있는 conservative summary 포함

Git 추적 정책:

- raw run log, raw payload, raw request/response body, secret-bearing env dump는 commit하지 않는다.
- sanitized `report.md`와 summary JSON은 implementation artifact로 commit할 수 있다.
- 수치가 machine-dependent이면 report에 local/isolated environment와 fallback reason을 반드시 기록한다.

## Acceptance Criteria

1. Story 12.6 구현은 Traditional MVC + Service/Repository layering과 feature-first package 구조를 따른다.
2. `application`, `port`, `adapter` package를 새로 만들지 않는다.
3. Lambda consumer, Lambda handler scaffold, Lambda event source mapping, separate worker service/deployment를 만들지 않는다.
4. Benchmark runner는 opt-in flag/profile 없이는 실행되지 않는다.
5. Default local/dev/test/smoke/CI profile은 benchmark queue mode, DB pool, worker batch size, resource constraint로 오염되지 않는다.
6. Primary fixture는 `applicationCount=1`, `instanceCount=30`으로 고정된다.
7. 30 synthetic instance가 실제 EC2 30대 또는 production distributed load test처럼 표현되지 않는다.
8. Direct insert path와 SQS enqueue path는 같은 fixture, 같은 idempotency distribution, 같은 DB 초기 상태로 Phase 1에서 비교된다.
9. Phase 1 report는 request p50/p95/p99, enqueue duration, request-thread DB bucket write 없음 evidence를 포함한다.
10. Phase 1 report는 DB persist 완료 latency와 queued latency를 같은 의미로 주장하지 않는다.
11. Worker MVP path와 batch writer path는 같은 fixture, 같은 DB 초기 상태로 Phase 2에서 비교된다.
12. Phase 2 report는 batch size, inserted/no-op/conflict/transient count, DB statement/round-trip count, persist duration, persisted buckets/sec를 포함한다.
13. Worker MVP path는 DB batch throughput improvement claim 없이 correctness/lag baseline으로만 표현된다.
14. Batch writer path만 DB batch throughput evidence로 표현된다.
15. Phase 1 request latency 개선과 Phase 2 DB batch throughput 개선은 report의 별도 section/table로 분리된다.
16. 실제 정량 수치 없이 개선율, production 성능, autoscaling, cost, dashboard UI performance claim을 하지 않는다.
17. Benchmark DB reference spec은 `db.t4g.micro`, gp3 20 GiB, 2 vCPU, 1 GiB, 3,000 IOPS / 125 MiB/s를 benchmark manifest에만 기록한다.
18. 실제 AWS RDS를 쓰지 않으면 isolated PostgreSQL fallback reason과 실제 host/runtime 제약을 manifest에 기록한다.
19. Queue URL, raw project key, starter credential, Authorization token, AWS credential/session token, Discord webhook URL, raw payload는 output/log/report에 남지 않는다.
20. Benchmark output directory redaction scan이 verification에 포함되고, 실패 시 report artifact publish/commit이 중단된다.
21. SQS enqueue path가 fake queue로 측정된 경우 fake queue라고 명시하고 real SQS latency evidence로 주장하지 않는다.
22. Scenario별 DB reset/seed가 구현되어 direct/SQS/worker/batch 비교가 이전 scenario row의 영향을 받지 않는다.
23. Same key/same hash duplicate는 no-op success로 유지되고 benchmark result에서 error로 계산되지 않는다.
24. Same key/different hash와 same instance bucket/different key conflict는 correctness smoke로 검증하되 primary throughput 수치가 conflict 반복 실패로 오염되지 않는다.
25. Benchmark runner 또는 report generator test가 fixture shape, opt-in guard, redaction guard, claim guard를 잠근다.
26. 생성된 public class/method와 동작이 직관적이지 않은 helper에는 `AGENTS.md` 기준 한국어 Javadoc/doc comment를 추가한다.

## Tasks / Subtasks

- [x] Pre-flight와 source-of-truth 정렬 (AC: 1~7, 16~21)
  - [x] `git status --short`로 기존 dirty 상태를 확인한다.
  - [x] Story 12.1~12.5와 README benchmark 방침을 다시 읽는다.
  - [x] 12.6은 실제 수치 evidence run이며, 12.5 batch writer correctness를 다시 정의하지 않는다고 dev notes에 남긴다.

- [x] Benchmark runner 확장 (AC: 4~15, 22)
  - [x] `scripts/benchmark/run-sqs-ingest-benchmark.py` 또는 Gradle task 후보를 실제 scenario runner로 확장한다.
  - [x] `--opt-in` 또는 `PORTAL_INGEST_BENCHMARK_OPT_IN=true` 없이는 실행되지 않게 유지한다.
  - [x] scenario별 DB reset/seed를 구현한다.
  - [x] primary fixture generator를 `applicationCount=1`, `instanceCount=30`으로 고정한다.
  - [x] warmup/measurement duration, payload count, duplicate ratio, cadence를 manifest에 기록한다.

- [x] Phase 1 request latency 측정 구현 (AC: 8~10, 15~16, 21)
  - [x] direct mode request scenario를 실행하고 p50/p95/p99를 계산한다.
  - [x] SQS/fake enqueue mode request scenario를 실행하고 p50/p95/p99와 enqueue duration을 계산한다.
  - [x] request thread에서 accepted bucket DB write가 없는 evidence를 별도 field로 남긴다.
  - [x] fake queue 측정이면 real SQS latency가 아니라고 report에 명시한다.

- [x] Phase 2 DB throughput 측정 구현 (AC: 11~15, 22~24)
  - [x] worker MVP message-by-message persistence baseline scenario를 구현한다.
  - [x] batch writer persistence scenario를 구현한다.
  - [x] inserted/no-op/conflict/transient count, statement count, persist duration, buckets/sec를 기록한다.
  - [x] deterministic conflict smoke는 primary throughput 수치와 분리한다.

- [x] Evidence artifact와 report 생성 (AC: 15~21)
  - [x] `manifest.json`, `phase-1-request-latency.json`, `phase-2-db-throughput.json`, `report.md`를 생성한다.
  - [x] report section을 Phase 1, Worker MVP baseline, Phase 2, Deployment smoke boundary로 분리한다.
  - [x] README/portfolio 후보 문구를 "local/isolated benchmark evidence"로 제한한다.
  - [x] 수치 없는 claim 또는 production/autoscaling/cost/dashboard UI claim을 금지한다.

- [x] Redaction / claim guard 검증 (AC: 16, 19~20, 25)
  - [x] benchmark output directory redaction scan을 실행한다.
  - [x] raw project key, starter credential, token, AWS credential/session token, queue URL, raw payload marker를 포함한 negative test를 유지/확장한다.
  - [x] report template/test가 Phase 1/Phase 2를 같은 표 또는 단일 개선율 claim으로 합치지 않는지 검증한다.

- [x] Verification 수행 (AC: 1~26)
  - [x] `git diff --check`
  - [x] `./gradlew :observability-portal:test --tests '*BenchmarkProfile*'`
  - [x] `./gradlew :observability-portal:test --tests '*MetricIngestQueue*'`
  - [x] `./gradlew :observability-portal:test --tests '*MetricBucketRepositoryBatch*'`
  - [x] `./gradlew :observability-portal:test --tests '*ApplicationCatalogRepositoryBatch*'`
  - [x] `./gradlew :observability-portal:test --tests '*MvcLayerBoundaryTest'`
  - [x] `./gradlew :observability-portal:test`
  - [x] opt-in benchmark command를 실행하고 generated output redaction scan을 통과한다.

## Tests To Add / Update

- `IngestBenchmarkRunnerTest` 또는 동등 test
  - opt-in flag 없이는 runner가 실행되지 않는다.
  - primary fixture는 application 1개와 instance 30개를 만든다.
  - scenario별 DB seed/reset이 분리된다.

- `IngestBenchmarkReportTest`
  - Phase 1과 Phase 2 section/table이 분리된다.
  - production/autoscaling/cost/dashboard UI 성능 claim이 없다.
  - fake queue 측정이면 fake queue라고 명시한다.

- `IngestBenchmarkRedactionScannerTest` 또는 기존 `IngestBatchBenchmarkProfileTest` 확장
  - raw project key, starter credential, Authorization token, AWS access key/session token, Discord webhook URL, queue URL, raw payload marker를 reject한다.

- `IngestBenchmarkScenarioSmokeTest`
  - 작은 payload count로 direct, enqueue, worker MVP, batch writer scenario가 모두 completion status와 summary metric을 생성한다.
  - 이 smoke는 장시간 benchmark가 아니라 correctness-level harness test여야 한다.

## Dev Agent Handoff Notes

1. 12.6의 핵심 산출물은 "실제 수치가 들어간 sanitized evidence report"다. 12.5처럼 skeleton만 만들면 부족하다.
2. 같은 fixture와 같은 DB 초기 상태를 보장하지 못하면 비교 수치를 report에 넣지 말고 blocker로 남긴다.
3. Phase 1은 request latency, Phase 2는 DB persistence throughput이다. 단일 개선율로 합치지 않는다.
4. fake queue로 측정한 SQS enqueue path는 "SQS mode enqueue semantics with fake queue"라고 표현한다. AWS SQS latency로 주장하지 않는다.
5. generated raw logs는 commit하지 않는다. commit할 artifact는 sanitized summary/report만 허용한다.
6. benchmark가 느려질 수 있으므로 기본 unit/integration test task에 장시간 measurement를 섞지 않는다.
7. `application`, `port`, `adapter` package를 만들지 않는다. benchmark helper도 feature-first package나 `scripts/benchmark` 안에 둔다.

## Create-Story Seed Prompt

아래 prompt는 별도 세션에서 다시 `bmad-create-story`를 실행해야 할 때 사용할 수 있는 축약 입력이다. 현재 파일이 이미 12.6 story source-of-truth이므로, 보통은 이 파일을 그대로 사용하면 된다.

```text
Create Story 12.6 for Epic 12 SQS Buffered Ingest Transition.

Title: Benchmark Evidence Run and Report.

Goal: Use the benchmark harness/guard built in Story 12.5 to run actual local/isolated benchmark scenarios and produce sanitized numeric evidence. Measure Phase 1 request latency and Phase 2 DB batch throughput in the same story, but keep scenarios, tables, denominators, and claims separate.

Must preserve:
- Traditional MVC + Service/Repository layering and feature-first packages.
- No application/port/adapter packages.
- No Lambda consumer, handler scaffold, event source mapping, or separate worker service.
- Story 12.3 worker correctness and Story 12.4 snapshot/queue lag semantics.
- SQS mode opt-in and direct rollback.
- Benchmark output redaction for raw project key, starter credential, token, AWS credential/session token, queue URL, raw payload.

Required evidence:
- Primary fixture applicationCount=1, instanceCount=30.
- Direct insert path vs SQS enqueue path for Phase 1 request p50/p95/p99.
- Worker MVP message-by-message persistence vs batch writer persistence for Phase 2 statement count, batch duration, persisted buckets/sec.
- Manifest with RDS reference spec db.t4g.micro, gp3 20 GiB, 2 vCPU, 1 GiB, 3,000 IOPS / 125 MiB/s, plus fallback reason if using isolated PostgreSQL.
- Sanitized report with no production/autoscaling/cost/dashboard UI performance claim.
```

## Risk / Review Hotspots

| 위험 | 영향 | 리뷰 포인트 |
| --- | --- | --- |
| Phase 1 queued latency와 direct persist latency를 같은 end-to-end latency로 주장함 | 성능 evidence가 오해를 만든다 | report table 이름과 denominator를 확인한다. |
| Phase 2 batch throughput을 SQS enqueue 효과로 표현함 | SQS 도입 근거가 과장된다 | Worker MVP vs batch writer section만 throughput claim을 갖는지 확인한다. |
| fixture/DB 초기 상태가 scenario별로 다름 | 비교 수치가 무의미해진다 | seed/reset code와 manifest를 확인한다. |
| fake queue 결과를 real SQS latency처럼 표현함 | 운영 성능 주장으로 오해된다 | queue mode label과 report wording을 확인한다. |
| raw payload/secret이 benchmark output에 남음 | 보안 사고 | redaction scanner와 generated artifact를 모두 확인한다. |
| benchmark resource constraint가 default profile로 번짐 | CI/local 개발이 느려지거나 불안정해진다 | `application-benchmark-sqs-ingest.properties` 외 기본 properties 변경 여부를 확인한다. |

## Done Definition

- Story status는 implementation 시작 전 `ready-for-dev`다.
- 구현 완료 시 dev agent는 실제 benchmark output/report 위치, 실행 명령, 환경 fallback reason, redaction scan 결과, focused/full test 결과를 Dev Agent Record에 남긴다.
- 실제 수치가 없거나 redaction scan이 실패하면 Story 12.6은 review로 넘기지 않는다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Plan

1. Story 12.5 skeleton script를 opt-in Gradle scenario launcher로 바꾸고, 기본 test task가 benchmark를 실행하지 않게 유지한다.
2. `IngestBenchmarkScenarioRunTest`를 추가해 Testcontainers PostgreSQL에서 scenario별 DB reset/seed 후 direct, fake enqueue, worker MVP, batch writer path를 측정한다.
3. Phase 1 request latency와 Phase 2 DB persistence throughput artifact를 별도 JSON/report section으로 생성하고, output directory redaction scan을 publish gate로 둔다.

### Debug Log References

- 2026-06-05: 최초 `*BenchmarkProfile*` compile에서 enqueue scenario record 인자가 하나 부족해 compile fail했고, `requestThreadAcceptedBucketRows=0`을 명시해 수정했다.
- 2026-06-05: opt-in runner를 한 차례 작은 count로 실행해 `manifest.json`, `phase-1-request-latency.json`, `phase-2-db-throughput.json`, `report.md` 생성과 redaction scan 통과를 확인했다.
- 2026-06-05: 최종 evidence run에서 Gradle test task가 up-to-date로 skip되어 artifact가 생성되지 않았고, benchmark script에 `--rerun-tasks`를 추가해 opt-in run이 항상 새 artifact를 쓰게 했다.
- 2026-06-06: opt-in guard를 class-level로 올린 뒤, opt-in 없이 `*IngestBenchmarkScenarioRunTest`를 필터링해도 Spring/Testcontainers context가 뜨지 않는 것을 확인했다.

### Completion Notes

- `scripts/benchmark/run-sqs-ingest-benchmark.py`는 `--opt-in` 또는 `PORTAL_INGEST_BENCHMARK_OPT_IN=true` 없이는 실행되지 않으며, opt-in 시 `IngestBenchmarkScenarioRunTest`를 호출해 sanitized numeric artifact를 생성한다.
- Scenario runner는 primary fixture를 `applicationCount=1`, `instanceCount=30`으로 고정하고, 각 scenario마다 Flyway clean/migrate와 project seed를 수행해 이전 scenario row 영향을 제거한다.
- Phase 1 direct insert request path와 fake queue enqueue request path를 같은 synthetic fixture로 측정했고, fake queue 결과가 real SQS network latency evidence가 아니라고 manifest/report에 명시했다.
- Phase 1 최종 수치: direct request p50/p95/p99 = `4.154/5.997/10.553 ms`, fake enqueue request p50/p95/p99 = `0.108/0.153/0.409 ms`, enqueue duration p50/p95/p99 = `0.001/0.002/0.031 ms`, enqueue path request-thread accepted bucket rows = `0`.
- Phase 2 최종 수치: worker MVP message-by-message persistence는 `90` inserted, bucket statement count `90`, duration `172.113 ms`, `522.912 buckets/sec`; batch writer는 batch size `30`, `90` inserted, bucket statement count `9`, batch chunks `3`, duration `51.534 ms`, `1746.418 buckets/sec`.
- Same key/same hash duplicate smoke는 first `INSERTED`, duplicate `DUPLICATE_NOOP`, `noopSuccess=true`로 통과했고 primary throughput 수치는 conflict 반복 실패로 오염시키지 않았다.
- Benchmark manifest는 RDS reference spec `db.t4g.micro`, gp3 20 GiB, 2 vCPU, 1 GiB, 3,000 IOPS / 125 MiB/s와 isolated PostgreSQL fallback reason을 기록한다.
- Generated artifact redaction scan은 `passed`이며 queue endpoint value, raw project key, starter credential, token, AWS credential/session value, webhook URL, request body contents를 output에 저장하지 않았다. Pretty JSON key 형태의 `"payload" :`, `"schemaVersion" :`, `"token" :` marker도 redaction guard가 차단한다.

### Evidence Artifacts

- `implementation-artifacts/benchmark-evidence/story-12-6/manifest.json`
- `implementation-artifacts/benchmark-evidence/story-12-6/phase-1-request-latency.json`
- `implementation-artifacts/benchmark-evidence/story-12-6/phase-2-db-throughput.json`
- `implementation-artifacts/benchmark-evidence/story-12-6/report.md`

### Verification

- `scripts/benchmark/run-sqs-ingest-benchmark.py --opt-in --fallback-reason 'postgres:16-alpine isolated Testcontainers PostgreSQL on local workstation' --measurement-count 90 --warmup-count 30 --batch-size 30 --output-dir implementation-artifacts/benchmark-evidence/story-12-6`
- `./gradlew :observability-portal:cleanTest :observability-portal:test --tests '*IngestBenchmarkScenarioRunTest'`
- `./gradlew :observability-portal:test --tests '*BenchmarkProfile*'`
- `./gradlew :observability-portal:test --tests '*MetricIngestQueue*'`
- `./gradlew :observability-portal:test --tests '*MetricBucketRepositoryBatch*'`
- `./gradlew :observability-portal:test --tests '*ApplicationCatalogRepositoryBatch*'`
- `./gradlew :observability-portal:test --tests '*MvcLayerBoundaryTest'`
- `./gradlew :observability-portal:test`
- `git diff --check`

### File List

- `implementation-artifacts/benchmark-evidence/story-12-6/manifest.json`
- `implementation-artifacts/benchmark-evidence/story-12-6/phase-1-request-latency.json`
- `implementation-artifacts/benchmark-evidence/story-12-6/phase-2-db-throughput.json`
- `implementation-artifacts/benchmark-evidence/story-12-6/report.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/benchmark/IngestBenchmarkScenarioRunTest.java`
- `planning-artifacts/stories/12-6-benchmark-evidence-run-and-report.md`
- `scripts/benchmark/run-sqs-ingest-benchmark.py`

### Change Log

- 2026-06-05: Story 12.6 opt-in benchmark scenario runner, Testcontainers PostgreSQL evidence run, sanitized numeric artifact generation, redaction publish gate를 구현했다.
- 2026-06-05: 최종 local/isolated benchmark evidence artifact를 생성하고 Story 12.6 status를 `review`로 전환했다.
