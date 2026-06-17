# LocalStack SQS Buffered Ingest Evidence

이 결과는 LocalStack SQS buffered ingest의 request-path와 final persistence 경계를 확인하기 위한 로컬 evidence다.
AWS 운영 환경 latency, autoscaling, 비용, dashboard UI 성능을 보장하지 않는다.

## Summary

| Case | Instances | Requests | Error % | p50 ms | p95 ms | p99 ms | Persisted % | Drain sec |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct DB | 30 | 3000 | 0.00 | 27.648 | 42.776 | 52.882 | 100.00 | 0.000 |
| LocalStack SQS | 30 | 3000 | 0.00 | 23.668 | 41.839 | 63.271 | 100.00 | 11.712 |

## Portfolio Claim Boundary

- Request p95 delta vs Direct DB (positive is faster): 2.19%
- Request p99 delta vs Direct DB (positive is faster): -19.65%
- LocalStack SQS eventual persisted ratio: 100.00%
- Duplicate suppressed count: 1

LocalStack SQS buffered mode는 DB insert를 request path 밖으로 분리해 request latency를 낮추는지 확인하기 위한 비교다.
Worker drain 이후 최종 저장률과 duplicate no-op 성격을 함께 확인해, request-path 개선과 persistence correctness를 분리해서 기록한다.

## Manifest

- runId: 20260616T141607Z-localstack-sqs-evidence
- gitRevision: 8227dc2
- gitDirty: true
