---
artifactType: story
storyId: "12.5"
storyKey: "12-5-batch-writer-and-performance-verification"
epic: "Epic 12. SQS Buffered Ingest Transition"
title: "Batch Writer and Performance Verification"
architectureStyle: Traditional MVC
status: backlog
date: 2026-06-05
commitBoundary: "perf: add sqs batch writer benchmark evidence"
---

# Story 12.5 - Batch Writer and Performance Verification

## Status

backlog

## Story

구현자로서, SQS worker MVP가 end-to-end로 닫힌 뒤 batch writer를 도입해 DB write round trip과 throughput을 실제 측정으로 검증하고 싶다.

그래야 request path decoupling으로 얻은 Phase 1 latency 개선과 DB batch writer로 얻은 Phase 2 throughput 개선을 섞지 않고 portfolio evidence로 남길 수 있다.

## 목표

- Batch writer 전략을 구현하거나 선택하고 DB write throughput 개선 근거를 측정한다.
- Direct insert path, SQS enqueue path, worker MVP, batch writer path의 지표를 분리한다.
- Portfolio 전용 opt-in benchmark profile과 environment manifest를 남긴다.
- 일반 local/dev/test profile, smoke profile, CI 기본 설정은 benchmark 제약의 영향을 받지 않게 한다.

## Acceptance Criteria

1. Batch writer는 batch command grouping, catalog get-or-create/last-seen update 축소, insert flush/transaction 축소 전략을 문서화하고 구현 후보를 선택한다.
2. JDBC batch, JPA `saveAll`, PostgreSQL `ON CONFLICT` 기반 insert 중 선택한 전략과 선택하지 않은 전략의 이유가 기록된다.
3. Same payload duplicate는 no-op success로 유지되고, same key/different hash와 same instance bucket/different key conflict는 batch path에서도 DLQ 대상으로 유지된다.
4. Batch conflict가 전체 batch를 불필요하게 반복 실패시키지 않도록 conflict row 재처리 또는 `ON CONFLICT` 기반 분류 전략을 둔다.
5. Phase 1 report는 HTTP request p50/p95/p99, enqueue latency, request thread DB write 제거 여부를 측정한다.
6. Phase 2 report는 DB statement/round-trip count, batch size, persist duration, throughput, enqueue-to-persist lag를 측정한다.
7. Phase 1 request latency 개선과 Phase 2 DB batch throughput 개선은 같은 표에서 섞어 주장하지 않는다.
8. Benchmark는 동일 fixture payload, 동일 idempotency distribution, 동일 project/application/instance cardinality로 direct path와 SQS-buffered path를 비교한다.
9. Benchmark environment manifest는 테스트 시점의 가장 작은 EC2/RDBMS service instance 또는 동등한 CPU, memory, DB capacity 제약을 기록한다.
10. Benchmark profile/config/script는 명시적으로 opt-in할 때만 동작한다.
11. 일반 local Docker/PostgreSQL, developer profile, smoke profile, CI 기본 설정은 benchmark constraint로 낮추거나 바꾸지 않는다.
12. Benchmark output은 secret, raw project key, token, webhook URL, raw payload를 남기지 않는다.
13. 결과는 production-grade load test, autoscaling proof, cost model이 아니라 portfolio 전용 constrained benchmark로 표현한다.
14. Benchmark manifest와 pass/fail threshold 또는 evidence-only 판정 기준을 명시한다.

## Non-Goals

- Production load test, autoscaling proof, cloud benchmark suite.
- Cost model, capacity planning, multi-node distributed benchmark.
- Lambda benchmark, Lambda concurrency tuning.
- Long-retention analytics, endpoint timeseries, raw metric explorer.
- 일반 개발 환경의 DB/queue/runtime 설정을 가장 작은 instance 제약에 맞춰 낮추는 것.
- Dashboard UI 성능 claim.

## 구현 전 닫아야 할 결정

- JDBC batch, JPA `saveAll`, PostgreSQL `ON CONFLICT` 중 batch writer 기본 전략.
- Catalog application/instance get-or-create grouping key와 last-seen update 기준.
- Batch conflict 처리: batch rollback 후 row 재처리, `ON CONFLICT` 분류, 또는 pre-read grouping.
- Benchmark fixture 크기, duplicate/conflict 비율, application/instance cardinality.
- Benchmark manifest의 최소 EC2/RDBMS 동등 사양 산정 방식.
- Pass/fail threshold를 둘지, evidence-only report로 둘지.
- Benchmark artifact 저장 위치와 secret redaction policy.

## 참고해야 할 코드/문서

- `planning-artifacts/stories/12-3-spring-boot-sqs-worker-mvp-and-idempotency.md`
- `planning-artifacts/stories/12-4-snapshot-delay-and-pipeline-lag-semantics.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommand.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/entity/AcceptedMetricBucketEntity.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommandTest.java`
- `observability-portal/build.gradle`
- `planning-artifacts/infrastructure-input-notes.md`

## 테스트/검증 방법

- Repository integration test: batch insert가 unique constraints와 no-overwrite semantics를 유지하는지 확인한다.
- Batch duplicate/conflict test: same payload duplicate, same key/different hash, same instance bucket/different key가 batch path에서도 Story 12.3 분류와 일치하는지 확인한다.
- DB statement count comparison: direct insert, worker MVP, batch writer path의 statement/round-trip count를 비교한다.
- Latency distribution summary: request latency, enqueue latency, enqueue-to-persist lag를 분리해 기록한다.
- Throughput benchmark: constrained environment manifest에서 accepted bucket persist throughput을 기록한다.
- Secret scan: benchmark output과 fixture에 raw project key, token, webhook URL, raw payload가 남지 않는지 확인한다.
- Profile guard: benchmark profile이 opt-in이고 default local/smoke/CI profile을 변경하지 않는지 확인한다.

## 위험과 완화책

| 위험 | 영향 | 완화책 |
| --- | --- | --- |
| Request latency 개선을 DB throughput 개선처럼 주장함 | 성능 evidence 신뢰도 저하 | Phase 1/Phase 2 report를 분리한다. |
| Batch conflict가 전체 transaction을 반복 실패시킴 | DLQ와 retry noise 증가 | conflict row 재처리 또는 `ON CONFLICT` 기반 분류를 둔다. |
| Catalog get-or-create race가 커짐 | duplicate catalog row 또는 lock contention | worker concurrency 1부터 시작하고 grouping/unique constraint를 검증한다. |
| Benchmark가 일반 개발 profile을 오염시킴 | local/dev/CI 불안정 | opt-in profile과 environment manifest를 분리한다. |
| Benchmark output에 secret이 남음 | 보안 사고 | generated artifact redaction scan을 gate로 둔다. |
