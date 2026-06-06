---
artifactType: story
storyId: "12.5"
storyKey: "12-5-batch-writer-and-performance-verification"
epic: "Epic 12. SQS Buffered Ingest Transition"
title: "Batch Writer and Performance Verification"
architectureStyle: Traditional MVC
status: done
date: 2026-06-05
commitBoundary: "perf: add sqs batch writer benchmark evidence"
---

# Story 12.5 - Batch Writer and Performance Verification

## Status

done

2026-06-05: `bmad-create-story`로 기존 12.5 story 파일을 source-of-truth 기준에 맞춰 보강했다. 이 작업은 구현을 포함하지 않으며, 후속 구현자는 아래 acceptance criteria와 task checklist를 기준으로 batch writer와 benchmark evidence를 구현한다.

2026-06-05: `bmad-dev-story` 구현을 시작하며 상태를 in-progress로 전환했다.

2026-06-05: Batch writer path, catalog grouping, benchmark guard 구현과 전체 portal regression 검증을 완료하고 review로 전환했다.

2026-06-05: Story 12.5 변경분을 commit/push/merge했고, 후속 실제 수치 evidence run은 Story 12.6으로 분리한다.

## Story

구현자로서, Story 12.3에서 닫힌 Spring Boot portal 내부 SQS worker correctness와 Story 12.4에서 닫힌 snapshot delay/queue lag semantics를 유지한 채, worker persistence path를 bounded batch writer로 개선하고 싶다.

그래야 "SQS가 동작한다"는 correctness를 다시 증명하지 않고, README의 SQS Buffered Ingest 성능 검증 방침에 맞춰 direct mode와 SQS buffered ingest + DB batch writer path의 정량적 개선 근거를 분리해서 남길 수 있다.

## Source of Truth

아래 문서를 읽고 이 story를 정리했다. 충돌처럼 보이는 지점은 Story 12.1~12.4에서 이미 닫힌 결정과 README의 "SQS Buffered Ingest 계획과 성능 검증 방침"을 우선한다.

1. `AGENTS.md`
2. `README.md`
3. `implementation-artifacts/sprint-status.yaml`
4. `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
5. `planning-artifacts/stories/12-1-architecture-and-contract-decision.md`
6. `planning-artifacts/stories/12-2-ingest-enqueue-boundary.md`
7. `planning-artifacts/stories/12-3-spring-boot-sqs-worker-mvp-and-idempotency.md`
8. `planning-artifacts/stories/12-4-snapshot-delay-and-pipeline-lag-semantics.md`
9. `planning-artifacts/stories/12-5-batch-writer-and-performance-verification.md`
10. `_bmad/custom/project-context.md`
11. `planning-artifacts/database-schema.md`

README 정렬 기준:

- 성능 근거는 동일 코드베이스의 direct mode와 SQS mode를 local 또는 격리 benchmark profile에서 비교해 남긴다.
- request latency, enqueue-to-persist lag, DB statement/round-trip count, batch writer throughput은 분리 측정한다.
- 배포 환경은 direct-vs-SQS benchmark가 아니라 SQS queue/worker/DLQ/rollback smoke verification 중심이다.
- 결과는 portfolio evidence와 상대 추세 근거로만 표현하며 production-grade load test, autoscaling proof, cost model로 주장하지 않는다.

확인한 AWS reference:

- Amazon RDS DB instance class hardware spec 기준 `db.t4g.micro`는 2 vCPU, 1 GiB memory, EBS-optimized only, 최대 2,085 Mbps EBS bandwidth, 최대 5 Gbps network bandwidth다. <https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.DBInstanceClass.Summary.html>
- Amazon RDS gp3 storage에서 Db2/MariaDB/MySQL/PostgreSQL의 20~399 GiB 구간 baseline은 3,000 IOPS / 125 MiB/s다. <https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_Storage.html>

## Story 12.1~12.4에서 이미 닫힌 결정

- Consumer는 Spring Boot portal 내부 worker다. Lambda consumer, Lambda handler scaffold, Lambda event source mapping, 별도 worker service/deployment는 Epic 12 범위가 아니다.
- SQS mode는 opt-in이고 rollback은 `portal.ingest.buffer.mode=direct` 복귀다.
- `202 queued`는 enqueue 성공만 뜻하며 DB 저장 완료, dashboard freshness current, snapshot 반영 완료를 뜻하지 않는다.
- Story 12.3은 worker MVP correctness를 닫았다. same payload duplicate는 no-op success이고, same key/different hash와 same instance bucket/different key는 DLQ 대상이다.
- Story 12.3 worker MVP는 기존 `MetricBucketRepository#insert(...)`를 message별로 재사용하므로 DB throughput 개선을 주장하지 않는다.
- Story 12.4는 snapshot cutoff/no-backfill, queue lag diagnostic, stale/down naming guard를 닫았다.
- SQS는 `accepted_metric_buckets` insert path의 buffer일 뿐 dashboard snapshot/history source가 아니다.
- Phase 1 request latency 개선과 Phase 2 DB batch throughput 개선은 서로 다른 claim boundary다.

## 현재 코드 상태

- `MetricIngestQueueWorker#pollOnce()`는 receive page의 message를 하나씩 `MetricIngestQueueProcessor#process(...)`로 넘긴다.
- `MetricIngestQueueProcessor`는 message parse, attribute/body cross-check, payload hash 재계산, ingest validation, duplicate/conflict 분류를 수행한 뒤 valid new message를 `MetricBucketRepository#insert(...)`로 저장한다.
- `MetricBucketRepository#insert(...)`는 `ApplicationCatalogRepository#getOrCreate(...)`를 command마다 호출하고, `AcceptedMetricBucketJpaRepository#saveAndFlush(...)`로 bucket 하나를 즉시 flush한다.
- `ApplicationCatalogRepository#getOrCreate(...)`는 `applications`와 `application_instances`를 각각 find/save하고 `last_seen_at`을 command마다 갱신한다.
- `accepted_metric_buckets`에는 `uk_buckets_project_idempotency_key (project_id, idempotency_key)`와 `uk_buckets_instance_bucket_start (application_instance_id, bucket_start_utc)`가 있다.
- Story 12.3에서 `AcceptedMetricBucketIdentity` projection과 idempotency/instance bucket conflict lookup은 이미 추가됐다.
- `IngestBufferProperties.Worker`는 `maxBatchSize`와 `maxBatchAge`를 갖지만, 현재 worker MVP는 DB persistence를 batch writer로 넘기지 않는다.
- `MvcLayerBoundaryTest`는 Epic 12 SQS surface를 feature-first `domain.ingest.queue` 안에 두는 guard를 가진다.

## 목표

- Spring Boot portal 내부 SQS worker/consumer가 받은 ingest messages를 bounded batch로 PostgreSQL에 저장하는 batch writer path를 설계하고 구현한다.
- `accepted_metric_buckets` insert의 DB statement/round-trip count와 transaction/flush 횟수를 줄인다.
- catalog application/instance get-or-create를 batch grouping으로 줄이고 last-seen update를 group별 max seenAt 기준으로 축소한다.
- same payload duplicate, same key/different hash conflict, same instance bucket/different key conflict 의미를 batch path에서도 Story 12.3과 동일하게 유지한다.
- Direct insert path, SQS enqueue path, worker MVP path, batch writer path의 metric/evidence를 분리한다.
- README의 benchmark 방침과 어긋나지 않게 opt-in benchmark profile/config/script, environment manifest, redaction policy를 남긴다.
- Benchmark primary fixture는 하나의 synthetic application 아래 30개 synthetic application instance가 지속적으로 ingest payload를 보내는 형태로 둔다.
- AWS RDS 최저 사양급 DB constraint는 benchmark 전용 격리 테스트에서만 적용하고, 일반 개발/테스트/스모크/CI profile에는 전파하지 않는다.

## Scope / Non-Scope

포함:

- `MetricIngestQueueProcessor` 또는 후속 batch processor가 bounded batch를 `MetricBucketBatchWriter` 후보로 전달하는 내부 worker path.
- `MetricBucketRepository`/bucket repository layer 안의 batch insert method와 result model.
- `ApplicationCatalogRepository`의 batch get-or-create/grouping 후보와 last-seen update 축소.
- PostgreSQL unique constraint 기반 duplicate/no-op/conflict 분류 유지.
- DB statement/round-trip count, persist duration, batch size, throughput, enqueue-to-persist lag 측정.
- opt-in benchmark profile/config/script, environment manifest, output redaction scan.
- focused repository/worker/benchmark tests.

제외:

- Lambda consumer.
- Lambda handler scaffold.
- Lambda event source mapping.
- separate worker service/deployment.
- `application`, `port`, `adapter` package 생성.
- production load test.
- autoscaling proof.
- cloud benchmark suite.
- cost model/capacity planning.
- dashboard UI performance claim.
- raw metric explorer/long-retention analytics.
- dashboard read model, lifecycle state, p95/p99, endpoint priority 계산 변경.
- snapshot backfill/replay pipeline.

## 구현 전 결정

| 항목 | 결정 | 구현 제약 |
| --- | --- | --- |
| Architecture style | Traditional MVC + Service/Repository layering을 유지한다. | feature-first package 아래 기존 `domain.bucket.repository`, `domain.catalog.repository`, `domain.ingest.queue`를 사용한다. `application`/`port`/`adapter` package를 만들지 않는다. |
| Batch writer 위치 | `MetricBucketRepository` 또는 같은 bucket repository feature 안에 batch insert facade를 둔다. | worker/processor는 JPA entity를 직접 다루지 않고 repository result model만 받는다. |
| Worker integration | worker receive page를 message별로 완전히 독립 처리하던 MVP에서, parse/validation/classification을 통과한 valid candidates를 bounded batch로 모아 writer에 넘긴다. | malformed/conflict/transient 분류와 delete/DLQ/no-delete action 의미는 Story 12.3과 동일하다. |
| 기본 DB insert 전략 | PostgreSQL `ON CONFLICT DO NOTHING RETURNING` 기반 multi-row JDBC insert를 기본 전략으로 선택한다. | `NamedParameterJdbcTemplate` 또는 동등한 repository-internal JDBC helper를 bucket repository 내부에서 사용한다. Spring MVC architecture를 바꾸는 port/adapter 도입으로 해석하지 않는다. |
| 미선택: JPA `saveAll` | 기본 전략으로 쓰지 않는다. | 구현은 간단하지만 Hibernate/JPA batching 설정, JSONB binding, unique conflict row 분류가 DB round-trip 목표와 conflict 격리 목표를 충분히 보장하지 못한다. |
| 미선택: plain JDBC batch | `ON CONFLICT` 없는 plain batch를 기본으로 쓰지 않는다. | 한 row unique conflict가 batch transaction 전체를 실패시키고, repeated retry나 row-by-row fallback이 benchmark를 오염시킬 수 있다. |
| `ON CONFLICT` 분류 | insert 후보는 `ON CONFLICT DO NOTHING RETURNING id, idempotency_key, application_instance_id, bucket_start_utc`로 inserted rows를 확인하고, return되지 않은 rows는 identity projection으로 duplicate/no-op/conflict를 분류한다. | same key/same hash는 no-op success, same key/different hash와 same instance bucket/different key는 DLQ 대상이다. |
| Pre-read | batch 시작 전 idempotency keys와 instance bucket identities를 bounded `IN` lookup으로 조회해 명확한 duplicate/conflict를 먼저 분리한다. | pre-read와 `ON CONFLICT` post-read가 race를 함께 흡수해야 한다. |
| Catalog grouping | `projectId + applicationName + environment`를 application key로, `applicationId + instanceName`을 instance key로 group한다. | group별 `max(acceptedAt)`을 last-seen update 기준으로 사용한다. command마다 get-or-create/save를 반복하지 않는다. |
| Catalog race handling | application/instance unique constraint를 최종 guard로 유지한다. | batch get-or-create 중 race가 발생하면 re-read로 수렴한다. duplicate catalog row를 만들지 않는다. |
| Batch bounds | worker config의 `maxBatchSize`/`maxBatchAge`를 DB writer flush bound로 사용하되, 기본 local/dev/test 값을 benchmark 목적에 맞춰 낮추거나 바꾸지 않는다. | benchmark용 값은 별도 profile/config에서만 opt-in한다. |
| Result model | batch writer는 inserted, duplicateNoop, conflictDlq, transientFailure를 command별로 반환한다. | worker는 inserted/no-op은 delete, conflict/malformed는 DLQ send 성공 후 delete, transient는 no-delete를 유지한다. |
| Primary benchmark fixture | 기본 비교 fixture는 `applicationCount=1`, `instanceCount=30`으로 둔다. | 30개 synthetic instance가 같은 cadence/distribution으로 ingest payload를 반복 전송한다. 필요하면 exploratory variant를 추가해도 primary evidence는 30 instance fixture를 기준으로 한다. |
| Benchmark DB reference spec | benchmark 전용 격리 DB 기준은 AWS RDS for PostgreSQL `db.t4g.micro`, gp3 20 GiB로 둔다. | 현재 region/engine에서 `db.t4g.micro`를 쓸 수 없으면 사용 가능한 최저 micro class 후보를 쓰고, manifest에 fallback 이유와 실제 CPU/memory/storage 제약을 기록한다. |
| RDS constraint scope | RDS 최저 사양급 constraint는 benchmark profile에서만 적용한다. | 개발 단계의 default local/dev/test/smoke/CI DB 설정, Docker/PostgreSQL 설정, smoke 환경을 이 사양에 맞춰 낮추지 않는다. |
| Benchmark judgment | 기본은 evidence-only report이며, hard pass/fail threshold는 "redaction/profile guard/test 통과" 같은 correctness gate에만 둔다. | 특정 개선율을 production SLO나 autoscaling proof로 쓰지 않는다. |

## Batch Writer 설계

### 처리 단계

1. Worker는 source messages를 receive하고 기존 parser/validator/hash check를 적용한다.
2. Malformed message는 batch writer에 넣지 않고 Story 12.3처럼 sanitized DLQ 대상으로 분류한다.
3. Valid command 후보를 `maxBatchSize` 또는 `maxBatchAge` bound 안에서 모은다.
4. Batch writer는 idempotency key와 instance bucket identity를 bulk pre-read한다.
5. Pre-read에서 same key/same hash는 duplicate no-op으로 분리한다.
6. Pre-read에서 same key/different hash 또는 same instance bucket/different key는 conflict DLQ로 분리한다.
7. 남은 insert 후보를 catalog identity별로 group하고 application/instance catalog row를 batch get-or-create한다.
8. catalog id가 부여된 bucket rows를 PostgreSQL multi-row insert + `ON CONFLICT DO NOTHING RETURNING`으로 저장한다.
9. return되지 않은 insert 후보는 race/conflict 가능성이 있으므로 identity projection을 다시 읽어 duplicate/no-op/conflict/transient를 분류한다.
10. worker는 command별 result를 source message result로 되돌려 delete/DLQ/no-delete를 적용한다.

### DB round-trip/statement count 감소 전략

| 기존 Worker MVP | Batch writer 목표 |
| --- | --- |
| message마다 application find + save | unique application key별 lookup/upsert/update |
| message마다 instance find + save | unique instance key별 lookup/upsert/update |
| message마다 accepted bucket `saveAndFlush` | batch chunk별 multi-row insert |
| conflict 발생 시 message 단위 unique violation 후 re-read | bounded pre-read + `ON CONFLICT DO NOTHING RETURNING` 후 미삽입 row만 re-read |
| request/worker/evidence metric이 섞이기 쉬움 | direct, enqueue, worker MVP, batch writer metric을 별도 namespace/report로 기록 |

### Catalog get-or-create grouping

- Application grouping key: `projectId + applicationName + environment`.
- Instance grouping key: `applicationId + instanceName`.
- Group `seenAt`: 같은 group에 속한 commands의 `max(acceptedAt)`.
- 새 application/instance row는 firstSeen/createdAt을 group 내 earliest 또는 current creation timestamp로 두되, lastSeen/updatedAt은 group max seenAt과 일관되게 둔다. 구현자는 선택한 기준을 test에 명시한다.
- 기존 row의 `last_seen_at`은 `greatest(existing.last_seen_at, group.maxSeenAt)` 의미로 후퇴하지 않게 한다.
- catalog get-or-create race는 unique constraint catch 후 re-read로 수렴한다.

### Duplicate / Conflict 유지

| Scenario | Batch path result | Queue action | DB action |
| --- | --- | --- | --- |
| valid new message | inserted | source delete | 새 row insert |
| same idempotency key + same payload hash | duplicate no-op success | source delete | no-op |
| same idempotency key + different payload hash | conflict DLQ | DLQ send 성공 후 source delete | no new row, existing row 유지 |
| same instance bucket + different idempotency key | conflict DLQ | DLQ send 성공 후 source delete | no new row, existing row 유지 |
| malformed message | malformed DLQ | DLQ send 성공 후 source delete | batch writer 진입 금지 |
| transient DB/catalog failure | transient failure | source delete 금지 | transaction rollback 또는 미삽입 |

Batch conflict가 전체 batch를 반복 실패시키면 안 된다. 구현은 pre-read, `ON CONFLICT DO NOTHING RETURNING`, post-read classification 중 하나 이상을 사용해 deterministic conflict row만 DLQ로 분리하고, 나머지 valid rows는 같은 batch 또는 다음 safe batch에서 성공하도록 해야 한다.

## Metric / Evidence 분리

| Evidence surface | 측정 대상 | 포함 지표 | Claim guard |
| --- | --- | --- | --- |
| Direct insert path | 기존 request thread DB insert baseline | HTTP request p50/p95/p99, request thread DB statement count, bucket insert duration | SQS enqueue와 같은 표에서 "저장 완료 latency"로만 비교한다. |
| SQS enqueue path | Story 12.2 request path | HTTP request p50/p95/p99, enqueue call duration, `202 queued` count, request thread DB bucket write 없음 | DB persist 완료나 throughput 개선으로 주장하지 않는다. |
| Worker MVP path | Story 12.3 message별 persistence | enqueue-to-persist lag, message processing duration, duplicate/conflict counts, per-message repository call count | DB batch throughput 개선 claim 금지. correctness baseline으로만 사용한다. |
| Batch writer path | Story 12.5 DB batch persistence | batch size, inserted/no-op/conflict counts, DB statement/round-trip count, batch persist duration, persisted buckets/sec | Phase 2 throughput evidence로만 사용한다. |

Phase 1 request latency 개선과 Phase 2 DB batch throughput 개선은 같은 표에서 섞어 주장하지 않는다. 같은 report 안에 둘 수는 있지만 section과 chart/table을 분리하고, 각 table의 denominator와 measurement window를 명시한다.

## Benchmark Profile / Manifest

### Opt-in profile/config/script

- Benchmark profile 후보: `benchmark-sqs-ingest`.
- Primary fixture:
  - `applicationCount=1`
  - `instanceCount=30`
  - 30개 synthetic application instance가 같은 benchmark run 안에서 반복 ingest payload를 보낸다.
  - direct mode와 SQS buffered mode는 같은 fixture, 같은 idempotency distribution, 같은 DB 초기 상태를 사용한다.
- Benchmark DB reference spec:
  - Engine: Amazon RDS for PostgreSQL 또는 이에 맞춰 제한한 isolated PostgreSQL.
  - Instance class: `db.t4g.micro` 기준.
  - Compute/memory: 2 vCPU, 1 GiB memory 기준.
  - Storage: gp3 20 GiB 기준.
  - Storage baseline: PostgreSQL gp3 20~399 GiB 구간의 3,000 IOPS / 125 MiB/s 기준.
  - Region: 실제 AWS run이면 `ap-northeast-2`를 우선 후보로 둔다.
  - Fallback: region/engine/account 제약 때문에 `db.t4g.micro`를 쓰지 못하면 현재 사용 가능한 최저 micro class로 대체하고 manifest에 이유를 기록한다.
  - Scope guard: 이 DB 사양 constraint는 benchmark 전용 격리 테스트에만 적용한다. default local/dev/test/smoke/CI profile은 이 constraint로 낮추거나 바꾸지 않는다.
- Benchmark config 후보:
  - `portal.ingest.buffer.mode=direct|fake|sqs`
  - `portal.ingest.buffer.worker.enabled=true`
  - `portal.ingest.buffer.worker.max-batch-size=<benchmark value>`
  - `portal.ingest.buffer.worker.max-batch-age=<benchmark value>`
  - `portal.ingest.benchmark.fixture=<fixture name>`
  - `portal.ingest.benchmark.output-dir=build/reports/ingest-benchmark`
- Script 후보:
  - `scripts/benchmark/run-sqs-ingest-benchmark.py`
  - 또는 Gradle task wrapper `:observability-portal:ingestBenchmark` 후보
- benchmark 실행은 명시적 profile/env/flag가 있을 때만 동작한다. 기본 `local`, `dev`, `test`, `smoke`, CI는 benchmark constraint나 작은 resource profile로 오염시키지 않는다.

### Environment manifest

Benchmark output에는 아래 manifest를 함께 저장한다.

| Field | 기록 내용 |
| --- | --- |
| runId | timestamp와 short random suffix |
| gitRevision | `git rev-parse --short HEAD` 값, dirty 여부 |
| mode | `direct`, `fake`, `sqs`, `worker-mvp`, `batch-writer` |
| fixture | payload count, duplicate ratio, conflict ratio, `applicationCount=1`, `instanceCount=30`, request cadence |
| appRuntime | Java version, Spring profile, worker batch size/age, worker concurrency |
| host | OS, CPU count, memory limit 또는 machine type |
| database | PostgreSQL version, `db.t4g.micro`/fallback 여부, 2 vCPU/1 GiB/gp3 20 GiB 동등 제약, connection pool size |
| queue | fake/SQS/LocalStack 여부, region과 queue type. queue URL은 저장하지 않는다. |
| timing | warmup duration, measurement duration, clock source |
| redaction | secret scan 통과 여부 |

README와 맞추기 위해 local 또는 격리 benchmark profile에서의 상대 추세 근거로만 남긴다. 배포 환경에서는 direct-vs-SQS benchmark를 돌리지 않고 SQS queue/worker/DLQ/rollback smoke verification을 별도 runbook/evidence로 남긴다.

### Output redaction policy

Benchmark fixture/output/log/report에는 아래 값을 남기지 않는다.

- raw project key
- starter credential
- Authorization token
- Discord webhook URL
- AWS access key/secret/session token
- queue URL
- raw ingest payload 전문
- raw path/query/trace/per-request sample

Report는 sanitized application/instance identity, aggregate counts, duration summary, statement/round-trip count, hashed or synthetic fixture id만 포함한다. redaction scan은 benchmark output directory를 대상으로 실행하고, 실패하면 evidence artifact를 publish하지 않는다.

## Acceptance Criteria

1. Story 12.5 구현은 Traditional MVC + Service/Repository layering과 feature-first package 구조를 따른다.
2. `application`, `port`, `adapter` package를 새로 만들지 않는다.
3. Lambda consumer, Lambda handler scaffold, Lambda event source mapping, separate worker service/deployment를 만들지 않는다.
4. Spring Boot portal 내부 worker/consumer가 받은 ingest messages를 bounded batch로 PostgreSQL에 저장하는 batch writer path가 설계되고 구현된다.
5. Worker MVP의 message parse/validation/hash check/malformed DLQ boundary는 유지된다.
6. Batch writer는 `accepted_metric_buckets` DB write round-trip/statement count 감소 전략을 코드와 benchmark report에 드러낸다.
7. 기본 insert 전략은 PostgreSQL `ON CONFLICT DO NOTHING RETURNING` 기반 multi-row JDBC insert이며, JPA `saveAll`과 plain JDBC batch를 기본 전략으로 선택하지 않은 이유가 dev notes 또는 story update에 기록된다.
8. Batch writer는 catalog application get-or-create를 `projectId + applicationName + environment` grouping으로 줄인다.
9. Batch writer는 catalog instance get-or-create를 `applicationId + instanceName` grouping으로 줄인다.
10. Catalog last-seen update는 group별 `max(acceptedAt)` 또는 명시적으로 선택한 monotonic 기준으로 축소되며, 기존 lastSeen을 과거로 되돌리지 않는다.
11. same idempotency key + same payload hash duplicate는 batch path에서도 no-op success로 source delete된다.
12. same idempotency key + different payload hash conflict는 batch path에서도 application DLQ 대상이다.
13. same instance bucket + different idempotency key conflict는 batch path에서도 application DLQ 대상이다.
14. Batch conflict가 전체 batch를 반복 실패시키지 않도록 pre-read, `ON CONFLICT` post-read classification, conflict row 재처리 중 구현된 전략이 있다.
15. Inserted/no-op success, application DLQ send success, transient failure의 source delete/no-delete semantics는 Story 12.3과 동일하다.
16. Direct insert path metric/evidence는 SQS enqueue path metric/evidence와 분리된다.
17. SQS enqueue path metric/evidence는 worker MVP path와 batch writer path의 DB persistence metric/evidence와 분리된다.
18. Worker MVP path는 correctness/lag baseline으로 남고 DB throughput improvement claim을 하지 않는다.
19. Batch writer path만 DB batch throughput improvement evidence로 사용된다.
20. Phase 1 request latency 개선과 Phase 2 DB batch throughput 개선은 같은 표에서 섞어 주장하지 않는다.
21. Primary benchmark fixture는 `applicationCount=1`, `instanceCount=30`이며, 30개 synthetic application instance가 반복 ingest payload를 보내는 형태다.
22. Benchmark는 동일 fixture payload, 동일 idempotency distribution, 동일 project/application/instance cardinality, 동일 DB 초기 상태로 direct mode와 SQS buffered path를 비교한다.
23. Benchmark 전용 격리 DB reference spec은 AWS RDS for PostgreSQL `db.t4g.micro`, gp3 20 GiB, 2 vCPU, 1 GiB memory, PostgreSQL gp3 20~399 GiB baseline 3,000 IOPS / 125 MiB/s 기준이다.
24. 실제 AWS run에서 `db.t4g.micro`를 사용할 수 없으면 현재 region/engine/account에서 사용 가능한 최저 micro class로 대체할 수 있지만, manifest에 fallback 이유와 실제 사양을 기록한다.
25. RDS 최저 사양급 constraint는 benchmark 전용 격리 테스트에만 적용된다.
26. request latency, enqueue latency, enqueue-to-persist lag, DB statement/round-trip count, batch writer throughput을 분리 측정한다.
27. Benchmark profile/config/script는 opt-in으로만 동작한다.
28. Default local/dev/test/smoke/CI profile은 benchmark resource constraint, batch size, DB pool, queue mode 설정으로 낮추거나 오염시키지 않는다.
29. Benchmark environment manifest는 CPU/memory/DB capacity, PostgreSQL version, runtime profile, worker bounds, fixture shape를 기록한다.
30. Benchmark output과 logs는 secret, raw project key, token, webhook URL, AWS credential, queue URL, raw payload를 남기지 않는다.
31. Benchmark output directory에 대한 redaction scan 또는 동등한 guard가 verification에 포함된다.
32. 결과 문구는 portfolio evidence와 상대 추세 근거로 제한되며 production-grade load test, autoscaling proof, cloud benchmark suite, cost model/capacity planning으로 표현하지 않는다.
33. 배포 환경 검증 문구는 direct-vs-SQS benchmark가 아니라 SQS queue/worker/DLQ/rollback smoke verification 중심으로 남긴다.
34. 새 public class/method와 동작이 직관적이지 않은 helper에는 `AGENTS.md` 기준의 한국어 Javadoc/doc comment를 추가한다.

## Tasks / Subtasks

- [x] Pre-flight와 source-of-truth 정렬 (AC: 1~3, 28~30)
  - [x] `git status --short`로 기존 dirty/untracked 상태를 기록하고 unrelated 변경은 되돌리지 않는다.
  - [x] Story 12.1~12.4의 done contract와 README 성능 검증 방침을 다시 읽는다.
  - [x] `MvcLayerBoundaryTest`에 Lambda, separate worker service, `application`/`port`/`adapter` package 금지 guard가 유지되는지 확인한다.
  - [x] 새 public class/method와 non-obvious helper의 한국어 Javadoc/doc comment 기준을 구현 checklist에 반영한다.

- [x] Batch result model과 worker handoff 설계 (AC: 4, 5, 11~15)
  - [x] command별 result를 표현하는 batch writer result model을 추가한다.
  - [x] `MetricIngestQueueProcessor` 또는 새 batch processor가 malformed message는 writer에 넣지 않고 기존 DLQ path로 분류하게 한다.
  - [x] valid messages만 bounded batch로 모아 batch writer에 전달한다.
  - [x] batch result를 source message별 delete/DLQ/no-delete action으로 되돌리는 mapping을 테스트한다.

- [x] Catalog batch get-or-create 구현 (AC: 8~10)
  - [x] `ApplicationCatalogRepository`에 batch get-or-create facade를 추가한다.
  - [x] application grouping key를 `projectId + applicationName + environment`로 구현한다.
  - [x] instance grouping key를 `applicationId + instanceName`으로 구현한다.
  - [x] group별 seenAt 기준과 lastSeen monotonic update를 test로 고정한다.
  - [x] unique race 발생 시 re-read로 수렴하는 boundary를 구현하거나 명시적으로 테스트 가능한 fallback을 둔다.

- [x] `accepted_metric_buckets` batch insert 구현 (AC: 6, 7, 11~15)
  - [x] `MetricBucketRepository` 또는 동등 repository facade에 `insertBatch(...)`를 추가한다.
  - [x] idempotency key와 instance bucket identity bulk pre-read를 추가한다.
  - [x] same key/same hash duplicate를 no-op success로 분리한다.
  - [x] same key/different hash와 same instance bucket/different key를 conflict DLQ result로 분리한다.
  - [x] PostgreSQL multi-row insert + `ON CONFLICT DO NOTHING RETURNING`을 repository-internal JDBC helper로 구현한다.
  - [x] return되지 않은 rows를 post-read classification으로 duplicate/conflict/transient에 분류한다.
  - [x] plain JDBC batch 또는 JPA `saveAll`로 바꾸지 않은 이유를 dev notes에 남긴다.

- [x] Metrics/evidence instrumentation 추가 (AC: 16~22)
  - [x] Direct insert path request latency와 DB statement/round-trip count 측정 hook 또는 benchmark wrapper를 추가한다.
  - [x] SQS enqueue path request/enqueue latency와 request thread DB bucket write 없음 evidence를 분리한다.
  - [x] Worker MVP path enqueue-to-persist lag와 per-message persistence baseline을 분리한다.
  - [x] Batch writer path batch size, insert duration, inserted/no-op/conflict count, persisted buckets/sec, statement/round-trip count를 기록한다.
  - [x] Phase 1과 Phase 2 report table/chart를 별도 section으로 생성한다.

- [x] Opt-in benchmark profile/config/script 작성 (AC: 21~33)
  - [x] benchmark profile/config는 명시적으로 opt-in한 경우에만 로드되게 한다.
  - [x] primary fixture를 `applicationCount=1`, `instanceCount=30`으로 만들고, 30개 synthetic instance가 반복 ingest payload를 보내게 한다.
  - [x] fixture payload, idempotency distribution, duplicate/conflict ratio, application/instance cardinality, request cadence를 manifest에 기록한다.
  - [x] benchmark DB reference spec을 `db.t4g.micro`, gp3 20 GiB, 2 vCPU, 1 GiB memory, 3,000 IOPS / 125 MiB/s baseline으로 manifest에 기록한다.
  - [x] 실제 AWS RDS를 쓰지 않는 경우 isolated PostgreSQL이 어떤 CPU/memory/storage/connection 제약으로 RDS 최저 사양급을 대체했는지 manifest에 기록한다.
  - [x] direct mode, SQS enqueue path, worker MVP, batch writer path를 동일 fixture/DB 초기 상태로 실행하는 script 또는 Gradle task 후보를 구현한다.
  - [x] default local/dev/test/smoke/CI profile이 benchmark 설정에 의해 바뀌지 않는지 test 또는 config assertion을 둔다.
  - [x] benchmark output directory redaction scan을 추가한다.
  - [x] report 문구가 portfolio evidence/relative trend로 제한되는지 fixture template 또는 generated summary를 검증한다.

- [x] Verification 수행 (AC: 1~30)
  - [x] focused repository integration tests를 실행한다.
  - [x] focused worker/batch processor tests를 실행한다.
  - [x] benchmark profile guard/redaction tests를 실행한다.
  - [x] `./gradlew :observability-portal:test`를 실행한다.
  - [x] `git diff --check`를 실행한다.

## 테스트 / 검증 방법

필수 또는 강력 권장 검증:

- `MetricBucketRepositoryBatchIntegrationTest`
  - batch insert가 new rows를 저장하고 command별 receipt/result를 반환한다.
  - same key/same hash duplicate는 no-op success다.
  - same key/different hash는 conflict result이며 기존 row를 overwrite하지 않는다.
  - same instance bucket/different key는 conflict result이며 기존 row를 overwrite하지 않는다.
  - `ON CONFLICT DO NOTHING RETURNING`에서 return되지 않은 row가 post-read로 정확히 분류된다.

- `ApplicationCatalogRepositoryBatchIntegrationTest`
  - 같은 application/environment group은 한 catalog row로 수렴한다.
  - 같은 instance group은 한 instance row로 수렴한다.
  - group별 lastSeen은 max seenAt 기준으로 갱신되고 과거 timestamp로 후퇴하지 않는다.
  - unique race fallback 또는 re-read path가 duplicate row를 만들지 않는다.

- `MetricIngestQueueBatchProcessorTest`
  - malformed message는 batch writer에 들어가지 않고 DLQ result로 남는다.
  - inserted/no-op/conflict/transient result가 source delete/DLQ/no-delete action으로 매핑된다.
  - partial batch에서 성공 row는 성공 처리되고 deterministic conflict row만 DLQ로 분리된다.
  - transient DB failure는 source delete와 DLQ send를 하지 않는다.

- `IngestBatchBenchmarkProfileTest`
  - benchmark profile/config/script는 opt-in flag 없이는 실행되지 않는다.
  - primary fixture가 1개 synthetic application과 30개 synthetic instance를 생성한다.
  - default local/dev/test/smoke/CI properties는 benchmark batch size, DB pool, queue mode 값으로 변경되지 않는다.
  - generated environment manifest에 fixture shape, 30 instance cardinality, RDS reference spec, fallback reason, runtime capacity가 포함된다.
  - generated output/log/report에 secret, raw project key, token, webhook URL, AWS credential, queue URL, raw payload가 없다.

- Benchmark evidence run:
  - Direct insert path: request p50/p95/p99, request thread DB statement/round-trip count.
  - SQS enqueue path: request p50/p95/p99, enqueue duration, DB bucket write 없음 evidence.
  - Worker MVP path: enqueue-to-persist lag, message별 persistence duration/statement baseline.
  - Batch writer path: batch size, persist duration, inserted/no-op/conflict counts, DB statement/round-trip count, persisted buckets/sec.

권장 실행 명령:

```bash
./gradlew :observability-portal:test --tests '*MetricBucketRepositoryBatch*'
./gradlew :observability-portal:test --tests '*ApplicationCatalogRepositoryBatch*'
./gradlew :observability-portal:test --tests '*MetricIngestQueueBatch*'
./gradlew :observability-portal:test --tests '*BenchmarkProfile*'
./gradlew :observability-portal:test
git diff --check
```

Benchmark script/Gradle task 이름은 구현 중 확정할 수 있다. 다만 실행 명령은 반드시 opt-in profile/flag를 요구해야 하며, 일반 test task에 장시간 benchmark를 섞지 않는다.

## Benchmark Report Guardrails

Report에는 아래 문구 원칙을 적용한다.

- 허용: "local/isolated benchmark profile에서 동일 fixture 기준 SQS enqueue request p95는 direct insert baseline보다 낮았다."
- 허용: "batch writer path는 worker MVP보다 DB statement/round-trip count가 감소했고 persisted buckets/sec가 증가했다."
- 금지: "production에서 동일 비율로 빨라진다."
- 금지: "autoscaling이 검증됐다."
- 금지: "운영 비용이 절감된다."
- 금지: "dashboard UI 성능이 개선됐다."
- 금지: "cloud 환경 부하 테스트를 통과했다."

배포 환경 evidence는 아래처럼 분리한다.

- SQS source queue smoke.
- worker receive/delete smoke.
- malformed/conflict DLQ smoke.
- direct rollback config smoke.
- snapshot delay/queue lag semantics regression.

## 참고해야 할 코드/문서

문서:

- `README.md`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
- `planning-artifacts/stories/12-1-architecture-and-contract-decision.md`
- `planning-artifacts/stories/12-2-ingest-enqueue-boundary.md`
- `planning-artifacts/stories/12-3-spring-boot-sqs-worker-mvp-and-idempotency.md`
- `planning-artifacts/stories/12-4-snapshot-delay-and-pipeline-lag-semantics.md`
- `planning-artifacts/database-schema.md`
- `_bmad/custom/project-context.md`

코드:

- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueWorker.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessor.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/IngestBufferProperties.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommand.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketIdentity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/entity/AcceptedMetricBucketEntity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationCatalogRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationInstanceRepository.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessorDuplicateMatrixTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessorRetryBoundaryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueWorkerFakeSmokeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`

## 위험과 완화책

| 위험 | 영향 | 완화책 |
| --- | --- | --- |
| Request latency 개선을 DB throughput 개선처럼 주장함 | 성능 evidence 신뢰도 저하 | Phase 1/Phase 2 report를 section/table/chart 단위로 분리한다. |
| Batch conflict가 전체 transaction을 반복 실패시킴 | retry/DLQ noise 증가, throughput 왜곡 | pre-read + `ON CONFLICT DO NOTHING RETURNING` + post-read classification으로 deterministic conflict row만 분리한다. |
| Same payload duplicate가 batch에서 error로 처리됨 | SQS at-least-once duplicate가 불필요한 DLQ로 이동 | stored payload hash projection과 duplicate no-op test를 유지한다. |
| Same instance bucket/different key를 놓침 | accepted bucket identity source가 오염됨 | instance bucket identity bulk lookup과 post-read conflict classification을 둔다. |
| Catalog get-or-create race가 커짐 | duplicate catalog row 또는 lock contention | unique constraint, group max seenAt, race 후 re-read fallback을 테스트한다. |
| Catalog lastSeen이 과거로 후퇴함 | dashboard/navigation freshness evidence가 흔들림 | `greatest(existing.last_seen_at, group.maxSeenAt)` 의미를 test로 고정한다. |
| JDBC helper가 MVC/JPA boundary를 흐림 | architecture guard 약화 | repository-internal implementation으로 제한하고 feature-first package 및 architecture test를 유지한다. |
| Benchmark가 일반 개발 profile을 오염시킴 | local/dev/CI 불안정 | opt-in profile/config/script와 profile guard test를 둔다. |
| RDS 최저 사양급 constraint가 개발/CI 기본값으로 번짐 | 일반 개발과 regression test가 느려지거나 불안정해짐 | RDS constraint는 benchmark 전용 격리 profile에만 적용하고 default profile guard를 테스트한다. |
| 30 synthetic instance fixture가 실제 분산 환경 증명으로 오해됨 | portfolio 성능 주장 과장 | synthetic instance cardinality와 request cadence를 manifest에 기록하고, production distributed load test가 아니라고 report에 명시한다. |
| Benchmark output에 secret/raw payload가 남음 | 보안 사고 | output allow-list, generated report template 제한, redaction scan을 gate로 둔다. |
| Production 성능 주장으로 과장됨 | portfolio 신뢰도 저하 | report 문구를 portfolio evidence/relative trend로 제한하고 README 문구와 맞춘다. |

## Dev Agent Handoff Notes

1. 먼저 worker MVP correctness tests를 깨뜨리지 않는 batch result model을 추가한다. 12.5는 correctness를 다시 정의하는 story가 아니다.
2. Batch insert는 repository layer 내부 성능 구현이다. architecture package를 바꾸거나 port/adapter 구조를 만들지 않는다.
3. `ON CONFLICT DO NOTHING RETURNING`은 conflict를 숨기기 위한 장치가 아니라 batch 전체 실패를 막고 미삽입 rows를 post-read로 분류하기 위한 장치다.
4. Catalog grouping은 throughput만이 아니라 DB statement count evidence의 핵심이다. `getOrCreate` per command를 그대로 두면 Phase 2 claim이 약해진다.
5. Benchmark는 correctness test가 아니다. 같은 fixture, 같은 DB 초기 상태, 같은 idempotency distribution을 보장하고 measurement surface를 분리하는 evidence artifact다.
6. Primary benchmark fixture는 30개 synthetic instance를 쓰되, 이것은 실제 EC2 30대나 production distributed load test를 뜻하지 않는다.
7. RDS 최저 사양급 DB constraint는 benchmark 전용 격리 테스트에만 적용한다. 일반 개발/테스트/스모크/CI 기본 DB 사양이나 profile을 낮추지 않는다.
8. Benchmark report에 raw payload나 secret이 들어가지 않도록 fixture id와 aggregate metric만 저장한다.
9. 배포 환경 smoke evidence와 local/isolated benchmark evidence를 같은 conclusion으로 합치지 않는다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Plan

1. Story 12.3 worker correctness와 Story 12.4 snapshot/lag semantics를 변경하지 않고, receive page valid messages만 batch writer로 넘긴다.
2. `MetricBucketRepository#insertBatch(...)`를 repository-internal JDBC helper로 구현해 pre-read, catalog grouping, multi-row `ON CONFLICT DO NOTHING RETURNING`, post-read classification을 수행한다.
3. `ApplicationCatalogRepository#getOrCreateBatch(...)`로 application/environment와 instance catalog get-or-create를 grouping하고 lastSeen은 group max acceptedAt 기준으로 갱신한다.
4. Benchmark는 실제 장시간 부하를 기본 테스트에 넣지 않고 opt-in profile, sanitized manifest/report skeleton, redaction scanner, script guard로 분리한다.

### Debug Log References

- 2026-06-05: `git status --short` 확인 시 sprint-status와 Story 12.5 문서가 이미 modified였고, 이 두 파일은 이번 story 대상 변경으로 이어서 작업했다.
- 2026-06-05: RED 단계에서 batch result model, `insertBatch`, catalog batch facade, processor `processBatch`, benchmark manifest/redaction class 부재로 compile fail을 확인했다.
- 2026-06-05: 첫 GREEN 실행에서 JDBC insert가 JPA catalog save flush 전 실행되어 FK violation이 발생했고, catalog batch path를 `saveAndFlush`로 조정했다.
- 2026-06-05: benchmark report guard가 금지 claim 단어를 부정문 안에서도 포함하는 문제를 확인해 skeleton 문구를 scaling/financial planning 표현으로 바꿨다.
- 2026-06-05: 병렬 Gradle test 실행으로 test-result binary가 일시적으로 꼬여 이후 Gradle 검증은 순차 실행으로 진행했다.

### Completion Notes

- Spring Boot portal 내부 worker는 receive page를 `MetricIngestQueueProcessor#processBatch(...)`로 넘기고, malformed는 기존 DLQ result로 분리하며 valid command만 batch writer에 전달한다.
- Batch writer result model은 command별 `INSERTED`, `DUPLICATE_NOOP`, `IDEMPOTENCY_PAYLOAD_CONFLICT`, `INSTANCE_BUCKET_IDENTITY_CONFLICT`, `TRANSIENT_FAILURE`를 반환해 기존 source delete/DLQ/no-delete semantics를 유지한다.
- `MetricBucketRepository#insertBatch(...)`는 bounded idempotency/instance identity pre-read, same-batch duplicate election, catalog grouping, PostgreSQL multi-row `ON CONFLICT DO NOTHING RETURNING`, post-read classification으로 conflict row가 전체 batch를 반복 실패시키지 않게 했다.
- Same key/same hash duplicate는 batch path에서도 no-op success이고, same key/different hash와 same instance bucket/different key는 application DLQ 대상 result로 유지된다.
- Catalog batch get-or-create는 `projectId + applicationName + environment`, `applicationId + instanceName` 기준으로 grouping하고 group 내 earliest firstSeen, max lastSeen을 반영하며 과거 acceptedAt batch가 lastSeen을 되돌리지 않게 했다.
- Benchmark profile은 `benchmark-sqs-ingest` opt-in properties와 `scripts/benchmark/run-sqs-ingest-benchmark.py` skeleton으로 분리했고, primary fixture는 `applicationCount=1`, `instanceCount=30`으로 고정했다.
- Benchmark manifest/report는 RDS reference spec(`db.t4g.micro`, gp3 20 GiB, 2 vCPU, 1 GiB, 3,000 IOPS / 125 MiB/s)과 local isolated PostgreSQL fallback reason을 기록하며 queue URL/raw payload/secret redaction guard를 갖는다.
- Phase 1 request latency, Worker MVP lag/correctness baseline, Phase 2 DB batch throughput evidence section을 분리해 같은 표에서 섞어 주장하지 않게 했다.
- Review follow-up으로 worker receive page를 `maxBatchSize` 단위로 chunking하고, catalog batch get-or-create를 PostgreSQL upsert로 바꿔 race 시 transaction rollback-only 오염을 피하며, benchmark redaction marker를 raw project key/starter credential/token/AWS session/raw schema payload까지 확장했다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketBatchItemResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketBatchItemStatus.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketBatchWriteResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/repository/ApplicationCatalogRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/benchmark/IngestBenchmarkManifest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/benchmark/IngestBenchmarkProperties.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/benchmark/IngestBenchmarkRedactionScanner.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/benchmark/IngestBenchmarkReportTemplate.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessor.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueWorker.java`
- `observability-portal/src/main/resources/application-benchmark-sqs-ingest.properties`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryBatchIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/catalog/repository/ApplicationCatalogRepositoryBatchIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/benchmark/IngestBatchBenchmarkProfileTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueBatchProcessorTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueWorkerBoundaryTest.java`
- `planning-artifacts/stories/12-5-batch-writer-and-performance-verification.md`
- `scripts/benchmark/run-sqs-ingest-benchmark.py`

### Change Log

- 2026-06-05: Story 12.5 batch writer path, catalog grouping, PostgreSQL `ON CONFLICT DO NOTHING RETURNING` multi-row insert, worker batch handoff, benchmark profile/manifest/redaction guard를 구현했다.
- 2026-06-05: focused repository/worker/benchmark tests, benchmark script opt-in smoke, 전체 `:observability-portal:test`, `git diff --check`를 통과하고 Status를 `review`로 변경했다.
- 2026-06-05: Review follow-up으로 worker `maxBatchSize` bound 적용, catalog upsert race hardening, benchmark redaction coverage 보강을 완료했다.
