# LocalStack SQS Buffered Ingest Evidence

이 결과는 LocalStack SQS buffered ingest의 request-path와 final persistence 경계를 확인하기 위한 로컬 evidence다.
AWS 운영 환경 latency, autoscaling, 비용, dashboard UI 성능을 보장하지 않는다.

## Summary

| Case | Instances | Requests | Error % | p50 ms | p95 ms | p99 ms | Persisted % | Drain sec |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct DB | 100 | 10000 | 0.00 | 86.873 | 132.491 | 278.518 | 100.00 | 0.000 |
| LocalStack SQS | 100 | 10000 | 0.00 | 69.209 | 119.177 | 136.400 | 100.00 | 34.735 |

## Portfolio Claim Boundary

- Request p95 delta vs Direct DB (positive is faster): 10.05%
- Request p99 delta vs Direct DB (positive is faster): 51.03%
- LocalStack SQS eventual persisted ratio: 100.00%
- Duplicate suppressed count: 1

LocalStack SQS buffered mode는 DB insert를 request path 밖으로 분리해 request latency를 낮추는지 확인하기 위한 비교다.
Worker drain 이후 최종 저장률과 duplicate no-op 성격을 함께 확인해, request-path 개선과 persistence correctness를 분리해서 기록한다.

## Manifest

- runId: 20260616T142354Z-localstack-sqs-evidence
- gitRevision: 8227dc2
- gitDirty: true
