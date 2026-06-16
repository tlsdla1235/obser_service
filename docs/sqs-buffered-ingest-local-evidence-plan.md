# SQS Buffered Ingest 로컬 증거 수집 계획

> 목적: **LocalStack SQS 기반 로컬 증거**로 SQS buffered ingest의 request-path 분리와 최종 저장 정합성을 검증한다.
> 목표 문장: “SQS buffered ingest로 DB write를 request path 밖으로 분리했고, worker batch persistence로 최종 저장 정합성을 유지했다.”

## 왜 이 테스트를 하는가

이번 테스트는 `DIRECT_DB`와 `LOCALSTACK_SQS`를 같은 fixture에서 비교해, request path에서 DB insert를 제거한 효과와 worker drain 이후의 저장 정합성을 분리해 확인하기 위한 로컬 검증이다.

단, 이 테스트도 로컬 검증이다. 따라서 다음 주장은 가능하다.

- 동일한 로컬 PostgreSQL 조건에서 direct DB ingest와 LocalStack SQS buffered ingest를 비교했다.
- SQS buffered mode는 request path에서 DB insert를 제거하고 enqueue까지만 수행했다.
- worker가 queue를 drain한 뒤 최종 저장 row 수와 unique bucket 수가 기대값과 일치했다.
- 중복 입력은 idempotency 규칙으로 no-op 처리됐다.

다음 주장은 하지 않는다.

- AWS 운영 환경에서 같은 latency가 보장된다.
- SQS 자체가 PostgreSQL보다 빠르다.
- 서비스 전체 처리량이 같은 비율로 개선됐다.
- dashboard UI 성능이 개선됐다.

## 이 테스트가 수치화하는 것

이 테스트는 “SQS가 빠르다”가 아니라 **느린 일을 요청 경로 밖으로 옮긴 효과**를 수치화한다.

| 분류 | 수치화 대상 | 의미 | 비교 대상 |
| --- | --- | --- | --- |
| Request latency | `p50`, `p95`, `p99`, `max` | ingest API 또는 service request boundary가 응답하는 데 걸린 시간 | Direct DB vs LocalStack SQS |
| Request stability | `error_rate`, `accepted_count`, `queued_count` | 동일 부하에서 요청이 실패 없이 수용되는지 | Direct DB vs LocalStack SQS |
| Persistence correctness | `accepted_unique_bucket_count`, `persisted_unique_bucket_count`, `eventual_persisted_ratio` | buffered mode가 응답 후에도 최종 저장을 정확히 완료하는지 | LocalStack SQS 중심 |
| Queue drain | `worker_drain_seconds`, `worker_poll_count` | 요청 이후 쌓인 메시지를 worker가 얼마 만에 DB에 반영하는지 | LocalStack SQS 중심 |
| Idempotency | `duplicate_sent_count`, `duplicate_suppressed_count`, `persisted_rows_after_duplicate` | SQS Standard의 at-least-once 가능성에 대비해 중복 저장을 막는지 | LocalStack SQS 중심 |
| DB pressure relief | `request_thread_db_write_count` | 요청 스레드에서 DB write가 제거됐는지 | Direct DB vs LocalStack SQS |

포트폴리오에서 가장 중요한 숫자는 3개다.

1. `DIRECT_DB p95/p99` vs `LOCALSTACK_SQS p95/p99`
2. `eventual_persisted_ratio = 100%`
3. `worker_drain_seconds`

## 추천 시나리오: 30-instance burst ingest A/B

비교군은 두 개만 둔다.

| Case | 설명 |
| --- | --- |
| `DIRECT_DB` | ingest 요청 경로에서 PostgreSQL insert까지 완료한 뒤 응답한다. |
| `LOCALSTACK_SQS` | ingest 요청 경로에서는 LocalStack SQS enqueue까지만 수행하고, worker가 batch로 PostgreSQL에 저장한다. |

### 부하 모델

30개 synthetic instance가 같은 시점에 metric bucket을 밀어 넣는 상황을 로컬 burst로 재현한다.
현재 v1 runner는 duration/RPS scheduler가 아니라 **고정 요청 수 + 동시성** 모델이다. 따라서 결과 문장에서도
`target RPS`가 아니라 `measurement request count`, `concurrency`, `worker drain seconds`를 쓴다.

```text
instances: 30
measurement_count: 3000
warmup_count: 300
concurrency: 30
duplicate_smoke_messages: 2
runs_per_case: 1부터 시작, README 대표 수치는 3회 이상 재실행 후 median 권장
postgres: Testcontainers PostgreSQL
queue: LocalStack SQS Standard queue
worker_max_messages_per_poll: 10
worker_max_batch_size: 10부터 시작, 필요하면 30으로 재실험
```

이 모델은 “운영 평균 부하”가 아니라 DB write spike를 의도적으로 드러내기 위한 포트폴리오용 압박 조건이다.
나중에 sustained RPS까지 주장하려면 별도 rate limiter를 붙인 duration 기반 runner를 추가한다.

## 실행 전 확인할 현재 코드 상태

현재 `scripts/benchmark/run-sqs-ingest-benchmark.py`와 `IngestBenchmarkScenarioRunTest`는 Story 12.6의 기존 in-memory queue evidence runner다. 이 runner는 실제 SQS endpoint를 통과하지 않으므로, 공개 포트폴리오용 LocalStack SQS 근거에는 그대로 쓰지 않는다.

새 컨텍스트에서는 다음 중 하나로 진행한다.

1. 기존 runner를 확장해 `LOCALSTACK_SQS` case를 추가한다.
2. 별도 runner를 만든다. 예: `scripts/benchmark/run-localstack-sqs-ingest-benchmark.py`

이번 작업에서는 2번으로 별도 runner를 추가했다. 기존 in-memory evidence와 새 LocalStack evidence가 섞이지 않게 하기 위한 결정이다.

## 현재 추가된 runner

```text
scripts/benchmark/run-localstack-sqs-ingest-benchmark.py
observability-portal/src/test/java/com/observation/portal/domain/ingest/benchmark/LocalStackSqsIngestEvidenceRunTest.java
```

이 runner는 명시 opt-in일 때만 실행된다.

### Smoke 실행

runner wiring과 artifact redaction scan을 빠르게 확인하는 용도다. README/포트폴리오 대표 수치로 쓰지 않는다.

```bash
scripts/benchmark/run-localstack-sqs-ingest-benchmark.py \
  --opt-in \
  --instance-count 30 \
  --measurement-count 30 \
  --warmup-count 3 \
  --concurrency 5 \
  --worker-batch-size 10 \
  --drain-timeout-seconds 30 \
  --output-dir observability-portal/build/reports/localstack-sqs-ingest-evidence-smoke
```

### 포트폴리오 대표 실행

```bash
scripts/benchmark/run-localstack-sqs-ingest-benchmark.py --opt-in
```

기본 산출물 위치:

```text
implementation-artifacts/benchmark-evidence/localstack-sqs-30-instance/
  manifest.json
  direct-db.json
  localstack-sqs.json
  summary.md
```

대표 수치를 README에 옮길 때는 `summary.md`의 p95/p99 delta, `localstack-sqs.json`의
`eventualPersistedRatio`, `workerDrainSeconds`, `duplicateSuppressedCount`를 함께 확인한다.

### 100-instance 압박 실행

30개 instance 기준보다 더 강한 burst 수용 근거가 필요할 때 사용한다.

```bash
scripts/benchmark/run-localstack-sqs-ingest-benchmark.py \
  --opt-in \
  --instance-count 100 \
  --measurement-count 10000 \
  --warmup-count 1000 \
  --concurrency 100 \
  --worker-batch-size 10 \
  --drain-timeout-seconds 90 \
  --output-dir implementation-artifacts/benchmark-evidence/localstack-sqs-100-instance
```

2026-06-16 로컬 대표 실행에서는 100개 synthetic instance, 10,000개 measurement request, concurrency 100 조건에서
LocalStack SQS buffered mode가 request p95를 132.491ms에서 119.177ms로 낮췄고, p99를 278.518ms에서
136.400ms로 낮췄다. Worker drain 이후 최종 저장률은 100%였고 drain time은 34.735초였다. 이 수치는 로컬
Testcontainers PostgreSQL + LocalStack SQS 기준이며 AWS 운영 환경 latency 보장은 아니다.

## 새 runner 요구사항

새 runner는 최소한 다음 산출물을 생성한다.

```text
implementation-artifacts/benchmark-evidence/localstack-sqs-30-instance/
  manifest.json
  direct-db.json
  localstack-sqs.json
  summary.md
```

### `manifest.json`

필수 필드:

```json
{
  "runId": "...",
  "generatedAtUtc": "...",
  "gitRevision": "...",
  "gitDirty": true,
  "purpose": "document LocalStack SQS buffered ingest local evidence",
  "environment": {
    "postgres": "local Docker or Testcontainers PostgreSQL",
    "queue": "LocalStack SQS Standard queue",
    "awsProductionClaim": false
  },
  "load": {
    "instances": 30,
    "measurementCount": 3000,
    "warmupCount": 300,
    "concurrency": 30,
    "duplicateSmokeMessages": 2
  },
  "worker": {
    "maxMessagesPerPoll": 10,
    "maxBatchSize": 10,
    "drainTimeoutSeconds": 30
  },
  "claimBoundary": [
    "request-path latency only",
    "eventual DB persistence correctness",
    "local benchmark with LocalStack SQS; not AWS production latency"
  ]
}
```

### `direct-db.json`

필수 필드:

```json
{
  "case": "DIRECT_DB",
  "requestLatencyMs": {
    "p50": 0,
    "p95": 0,
    "p99": 0,
    "max": 0
  },
  "requestCount": 0,
  "errorRate": 0,
  "requestThreadDbWriteCount": 0,
  "persistedUniqueBucketCount": 0
}
```

### `localstack-sqs.json`

필수 필드:

```json
{
  "case": "LOCALSTACK_SQS",
  "requestLatencyMs": {
    "p50": 0,
    "p95": 0,
    "p99": 0,
    "max": 0
  },
  "requestCount": 0,
  "errorRate": 0,
  "queuedCount": 0,
  "requestThreadDbWriteCount": 0,
  "acceptedUniqueBucketCount": 0,
  "persistedUniqueBucketCount": 0,
  "eventualPersistedRatio": 1.0,
  "workerDrainSeconds": 0,
  "workerPollCount": 0,
  "duplicateSentCount": 0,
  "duplicateSuppressedCount": 0,
  "persistedRowsAfterDuplicate": 0
}
```

### `summary.md`

필수 표:

| Case | Instances | Requests | Error % | p50 ms | p95 ms | p99 ms | Persisted % | Drain sec |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct DB | 30 | 3000 |  |  |  |  | 100 | 0 |
| LocalStack SQS | 30 | 3000 |  |  |  |  | 100 |  |

필수 문장:

> 이 결과는 로컬 PostgreSQL과 LocalStack SQS Standard queue 기준의 request-path 비교다. AWS 운영 환경 latency, autoscaling, 비용, dashboard UI 성능을 보장하지 않는다.

## Acceptance Gate

포트폴리오 문장에 사용하려면 아래 기준을 통과해야 한다.

```text
AC-01 LOCALSTACK_SQS p95 latency < DIRECT_DB p95 latency
AC-02 p99 개선은 선택 claim이다. LOCALSTACK_SQS p99 latency >= DIRECT_DB p99 latency이면 README에 p99 개선 표현을 쓰지 않는다.
AC-03 LOCALSTACK_SQS error_rate == 0%
AC-04 LOCALSTACK_SQS eventual_persisted_ratio == 100%
AC-05 LOCALSTACK_SQS worker_drain_seconds <= 30
AC-06 duplicate_suppressed_count > 0
AC-07 persisted_unique_bucket_count == accepted_unique_bucket_count
AC-08 summary.md에 AWS 운영 환경 latency를 보장하지 않는다는 경계 문구가 포함됨
```

AC-01의 개선 폭은 처음부터 과하게 고정하지 않는다. 로컬 장비와 DB 상태에 따라 수치가 흔들릴 수 있으므로,
1차 목표는 p95에서 “명확히 낮다”를 확인하는 것이다. p99는 tail latency라 흔들림이 크므로, 3회 이상 재실행한 median에서도
개선될 때만 README에서 퍼센트 감소를 추가한다.

## README에 쓸 수 있는 문장

수치가 나온 뒤에는 다음 형태로만 쓴다.

```md
30개 synthetic instance의 burst ingest를 로컬에서 재현해 Direct DB ingest와 LocalStack SQS buffered ingest를 비교했다.
대표 실행은 3,000개 measurement request, concurrency 30 조건으로 수행했다.

동일한 PostgreSQL 조건에서 buffered mode는 DB insert를 request path 밖으로 분리해 request p95 latency를 {N}%
낮췄다. Worker drain 이후 최종 저장률은 100%였고, 중복 bucket 입력은 idempotency
규칙으로 no-op 처리됐다.

이 결과를 근거로 배포 구성은 direct ingest가 아니라 SQS buffered ingest로 선택했다. 이 수치는 LocalStack 기반
로컬 검증이며 AWS 운영 환경 latency 보장은 아니다.
```

## 실패했을 때 해석

SQS case가 direct보다 느리게 나오면 실패가 아니라 다음 중 하나를 확인한다.

- 부하가 너무 낮아서 direct DB insert 비용이 드러나지 않았는가?
- LocalStack/SQS client overhead가 request path를 지배하고 있는가?
- DB connection pool이 너무 커서 direct mode가 압박을 받지 않는가?
- worker가 너무 느려 drain은 성공하지만 queue lag가 길어지는가?
- payload generation 또는 validation 비용이 두 case 모두를 지배하고 있는가?

이 경우 claim은 “latency 개선”이 아니라 “정합성 있는 buffered path 구현과 worker drain 검증”까지만 사용한다.

## 새 컨텍스트 인계 메모

다음 작업자는 이 문서를 기준으로 기존 in-memory benchmark를 덮어쓰지 말고, 별도 LocalStack evidence runner와 산출물 디렉터리를 유지한다. 목표는 공개 README/포트폴리오에서 `LocalStack SQS buffered ingest local benchmark`를 재현 가능한 근거로 제시하는 것이다.
