# SQS Buffered Ingest Evidence

## 목적

이 문서는 Observation Portal의 ingest 경로를 `DIRECT_DB`와 `LOCALSTACK_SQS`로 비교한 로컬 benchmark 근거를 정리한다.
포트폴리오에서는 “SQS가 DB보다 빠르다”가 아니라, **DB write를 request path 밖으로 분리하고 worker drain 이후 최종 저장 정합성을 확인했다**는 근거로 사용한다.

## 비교한 구조

| Mode | Request path | Persistence path | 응답 의미 |
| --- | --- | --- | --- |
| `DIRECT_DB` | 검증 후 PostgreSQL insert까지 수행 | request thread에서 완료 | DB 저장 완료 |
| `LOCALSTACK_SQS` | 검증 후 LocalStack SQS enqueue까지 수행 | portal 내부 worker가 queue poll 후 batch 저장 | queue 수용 완료 |

`LOCALSTACK_SQS`의 `202 queued` 응답은 DB 저장 완료, dashboard 최신 반영, snapshot 저장 완료를 뜻하지 않는다. 따라서 latency 비교는 **request-path acceptance latency** 비교이며, DB 저장 완료 시간은 worker drain과 final persistence ratio로 별도 확인한다.

## 테스트 환경

| 항목 | 값 |
| --- | --- |
| Host | macOS arm64 local machine |
| Java/Gradle | OpenJDK 21.0.11, Gradle 9.5.0 |
| Container runtime | Docker 29.2.1 |
| Database | Testcontainers PostgreSQL `postgres:16-alpine` |
| Queue | LocalStack SQS `localstack/localstack:4.8.1` |
| Runner | `scripts/benchmark/run-localstack-sqs-ingest-benchmark.py` |
| Test entrypoint | `LocalStackSqsIngestEvidenceRunTest` |

이 결과는 로컬 benchmark다. AWS 운영 환경 latency, autoscaling, 비용, dashboard UI 성능을 보장하지 않는다.

## Scenario A: 30개 인스턴스 burst

```bash
scripts/benchmark/run-localstack-sqs-ingest-benchmark.py --opt-in
```

기본 실행 조건은 30개 synthetic instance, 3,000개 measurement request, warmup 300건, concurrency 30, worker batch size 10이다.

| Mode | Requests | Error | p50 ms | p95 ms | p99 ms | Request-thread DB writes | Persisted | Drain sec |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct DB | 3,000 | 0% | 27.648 | 42.776 | 52.882 | 3,000 | 100% | 0.000 |
| LocalStack SQS | 3,000 | 0% | 23.668 | 41.839 | 63.271 | 0 | 100% | 11.712 |

해석:

- request thread의 DB write가 3,000건에서 0건으로 분리됐다.
- worker drain 이후 3,000개 bucket이 모두 저장됐다.
- p95는 소폭 낮아졌지만 p99는 개선되지 않았다.
- 따라서 이 시나리오는 p99 latency 개선 근거가 아니라, request path 분리와 final persistence correctness 근거로 사용한다.

## Scenario B: 100개 인스턴스 burst

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

| Mode | Requests | Error | p50 ms | p95 ms | p99 ms | Request-thread DB writes | Persisted | Drain sec |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct DB | 10,000 | 0% | 86.873 | 132.491 | 278.518 | 10,000 | 100% | 0.000 |
| LocalStack SQS | 10,000 | 0% | 69.209 | 119.177 | 136.400 | 0 | 100% | 34.735 |

해석:

- request thread의 DB write가 10,000건에서 0건으로 분리됐다.
- request p95는 132.491ms에서 119.177ms로 약 10.05% 낮아졌다.
- request p99는 278.518ms에서 136.400ms로 약 51.03% 낮아졌다.
- worker drain 이후 10,000개 bucket이 모두 저장됐다.
- drain time 34.735초는 queue에 맡긴 뒤 DB 반영이 비동기로 뒤따르는 비용이다.

## Idempotency 확인

두 실행 모두 duplicate smoke를 포함한다. 같은 bucket을 2번 enqueue했을 때 1건은 저장되고 1건은 idempotency 규칙으로 억제됐다. 이는 SQS Standard queue의 at-least-once 가능성을 고려한 방어 근거다.

## 포트폴리오 표현

사용 가능한 표현:

> LocalStack SQS와 Testcontainers PostgreSQL로 30개/100개 synthetic instance burst를 재현했다. SQS buffered ingest는 요청 경로에서 DB insert를 제거해 request thread DB write를 10,000건에서 0건으로 분리했고, worker drain 이후 최종 저장률 100%를 확인했다. 100-instance 실행에서는 request p95가 132.491ms에서 119.177ms, p99가 278.518ms에서 136.400ms로 낮아졌다.

피해야 할 표현:

- SQS가 PostgreSQL보다 빠르다.
- AWS 운영 환경에서도 같은 latency가 보장된다.
- API 응답 시점에 DB 저장이 완료된다.
- 서비스 전체 처리량이 같은 비율로 개선됐다.
- dashboard UI 성능이 개선됐다.

## 산출물

- `implementation-artifacts/benchmark-evidence/localstack-sqs-30-instance/summary.md`
- `implementation-artifacts/benchmark-evidence/localstack-sqs-30-instance/direct-db.json`
- `implementation-artifacts/benchmark-evidence/localstack-sqs-30-instance/localstack-sqs.json`
- `implementation-artifacts/benchmark-evidence/localstack-sqs-100-instance/summary.md`
- `implementation-artifacts/benchmark-evidence/localstack-sqs-100-instance/direct-db.json`
- `implementation-artifacts/benchmark-evidence/localstack-sqs-100-instance/localstack-sqs.json`
