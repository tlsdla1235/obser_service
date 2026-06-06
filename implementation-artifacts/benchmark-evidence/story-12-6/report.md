# SQS Buffered Ingest Benchmark Evidence

This is local/isolated benchmark evidence from the explicit `benchmark-sqs-ingest` opt-in runner.
The fixture uses one synthetic application and 30 synthetic instance names in one local run; it is not
evidence for a distributed host fleet, scaling behavior, financial planning, or dashboard UI behavior.

## Manifest Summary

- runId: 20260606T045051Z-story-12-6
- gitRevision: 20b9fbf
- gitDirty: true
- fixture: applicationCount=1, instanceCount=30, synthetic local identities
- database fallback: postgres:16-alpine isolated Testcontainers PostgreSQL on local workstation
- queue mode for Phase 1 enqueue: fake queue; real SQS network latency is not claimed

## Phase 1 Request Latency Evidence

Direct insert request latency measures the request path that persists before responding.
Enqueue request latency measures the request path that returns after fake queue enqueue succeeds.
These rows are request-boundary evidence only and are not a shared end-to-end persistence latency table.

| Scenario | Count | p50 ms | p95 ms | p99 ms | Max ms | Failure count | Request-thread accepted bucket rows |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct insert | 90 | 4.154 | 5.997 | 10.553 | 10.553 | 0 | 90 |
| Fake queue enqueue | 90 | 0.108 | 0.153 | 0.409 | 0.409 | 0 | 0 |

Fake enqueue duration summary: count=90, p50=0.001 ms, p95=0.002 ms, p99=0.031 ms.
Request-thread DB write absence evidence for enqueue path: acceptedBucketRowsAfterScenario=0.

## Worker MVP Correctness/Lag Baseline

Worker MVP baseline uses the message-by-message repository persistence shape from Story 12.3.
It is a correctness and latency baseline, not a DB batch throughput improvement claim.

| Scenario | Count | Inserted | No-op | Conflict | Transient | Bucket statement count | Persist duration ms | Persisted buckets/sec |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Worker MVP message-by-message | 90 | 90 | 0 | 0 | 0 | 90 | 172.113 | 522.912 |

Same key/same hash smoke: first=INSERTED, duplicate=DUPLICATE_NOOP, no-op-success=true.

## Phase 2 DB Batch Throughput Evidence

Batch writer evidence uses the Story 12.5 PostgreSQL batch writer path and is separate from request latency.

| Scenario | Count | Batch size | Inserted | No-op | Conflict | Transient | Bucket statement count | Batch chunks | Persist duration ms | Persisted buckets/sec |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Batch writer | 90 | 30 | 90 | 0 | 0 | 0 | 9 | 3 | 51.534 | 1746.418 |

## Deployment Smoke Boundary

Deployment evidence remains separate: source queue smoke, worker receive/delete smoke, malformed/conflict
DLQ smoke, direct rollback config smoke, and snapshot delay/queue lag semantics regression.
