---
artifactType: story
storyId: "12.3"
storyKey: "12-3-spring-boot-sqs-worker-mvp-and-idempotency"
epic: "Epic 12. SQS Buffered Ingest Transition"
title: "Spring Boot SQS Worker MVP and Idempotency"
architectureStyle: Traditional MVC
status: backlog
date: 2026-06-05
commitBoundary: "feat: add spring boot sqs worker mvp"
---

# Story 12.3 - Spring Boot SQS Worker MVP and Idempotency

## Status

backlog

## Story

구현자로서, Spring Boot portal 내부 worker가 SQS message를 읽어 기존 accepted bucket persistence path로 안전하게 저장하길 원한다.

그래야 SQS Standard의 at-least-once/out-of-order 전달을 정상 조건으로 받아들이면서 duplicate, conflict, malformed message를 명확하게 분류할 수 있다.

## 목표

- Portal 내부 Spring Boot SQS worker MVP를 구현한다.
- Worker MVP는 batch writer 최적화 전이라도 end-to-end enqueue-to-persist 경로를 닫는다.
- Same payload duplicate는 no-op success로 처리하고 malformed/conflict는 DLQ 대상으로 분리한다.
- Transient DB failure는 message를 delete하지 않고 retry되게 한다.

## Acceptance Criteria

1. Worker는 Spring Boot portal runtime 안에서만 동작한다. 별도 worker service나 Lambda consumer는 만들지 않는다.
2. Worker는 worker-enabled config, queue URL, long polling seconds, max batch size, max batch age, visibility timeout, max receive count를 가진다.
3. Worker는 message version, payload hash, bucket boundary, schemaVersion, required identity field를 다시 검증한다.
4. MVP persistence는 기존 `MetricBucketRepository#insert()` 재사용을 허용해 end-to-end를 먼저 닫는다.
5. DB unique constraint는 최종 idempotency guard로 유지된다.
6. Same project/idempotency key + same payload hash duplicate는 no-op success로 처리하고 message를 ack/delete한다.
7. Same project/idempotency key + different payload hash는 conflict로 분류하고 retry로 회복되지 않는 DLQ 대상으로 처리한다.
8. Same application instance + same bucket start + different idempotency key는 deterministic identity conflict로 분류하고 DLQ 대상으로 처리한다.
9. Malformed JSON, unknown message version, hash mismatch, invalid bucket boundary, unsupported schemaVersion은 malformed DLQ 대상으로 처리한다.
10. Transient DB failure, database connection failure, transaction timeout은 message를 delete하지 않고 visibility timeout 이후 retry되게 한다.
11. Worker failure는 dashboard read API, static UI delivery, heartbeat endpoint, direct ingest validation path를 막지 않는다.
12. Duplicate/no-op은 error log로 과장하지 않고 count metric 또는 debug/info 수준으로만 남긴다.
13. Worker MVP는 DB throughput 개선을 주장하지 않는다. Throughput 개선은 Story 12.5 batch writer 이후에만 주장한다.
14. DLQ payload shape는 sanitized failure envelope로 세부화하며 raw payload, raw project key, token, webhook URL을 포함하지 않는다.

## Non-Goals

- Exactly-once delivery guarantee claim.
- Lambda consumer, Lambda batch response, separate worker deployment.
- Batch writer throughput 최적화.
- Queue replay UI, DLQ browser UI, poison message manual repair UI.
- Lifecycle state, p95/p99, endpoint priority, operational event calculation.
- Snapshot delay/cutoff 구현. 이는 Story 12.4 범위다.

## 구현 전 닫아야 할 결정

- Stored payload hash와 bucket identity를 duplicate/no-op 판정에 어떻게 조회할지.
- 기존 `AcceptedMetricBucketReceipt`만으로 부족한 identity projection을 repository에 추가할지.
- Partial batch failure 때 성공 message만 delete하고 실패 message는 남기는 전략.
- DLQ 전송을 SQS redrive policy에 맡길지, application-level explicit DLQ publish를 둘지.
- Visibility timeout과 max receive count의 exact 기본값.
- Malformed/conflict DLQ payload shape의 exact field. Raw body나 secret-bearing field는 남기지 않는다.
- Worker concurrency 기본값과 catalog get-or-create race 방지 방식.

## 참고해야 할 코드/문서

- `planning-artifacts/stories/12-1-architecture-and-contract-decision.md`
- `planning-artifacts/stories/12-2-ingest-enqueue-boundary.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/contracts/time-buckets.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommand.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketReceipt.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ValidatedIngestCandidate.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestPayloadHasher.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestAcceptanceServiceTest.java`

## 테스트/검증 방법

- Fake queue worker smoke: enqueue된 message가 worker를 거쳐 accepted bucket으로 저장되는지 확인한다.
- Duplicate matrix test: same key/same hash는 no-op success, same key/different hash는 DLQ 대상, same instance bucket/different key는 DLQ 대상으로 분류한다.
- Malformed matrix test: invalid JSON, unsupported version, hash mismatch, invalid boundary가 DLQ 대상으로 분류되는지 확인한다.
- Transient DB failure test: repository exception 시 message가 delete되지 않고 retry 대상으로 남는지 확인한다.
- Ack/delete boundary test: inserted/no-op success만 delete되고 conflict/malformed/transient failure가 각 정책대로 처리되는지 확인한다.
- Repository integration regression: unique constraint가 중복 row를 막고 기존 row를 overwrite하지 않는지 확인한다.
- Architecture boundary test: worker가 controller/dto/UI layer에 의존하지 않는지 확인한다.

## 위험과 완화책

| 위험 | 영향 | 완화책 |
| --- | --- | --- |
| SQS duplicate/out-of-order가 중복 row를 만듦 | accepted bucket source 오염 | DB unique constraint와 no-op duplicate handling을 최종 guard로 둔다. |
| Same key/different hash를 retry함 | poison message 반복 | conflict를 DLQ 대상으로 분류한다. |
| Malformed message가 무한 retry됨 | worker capacity 낭비 | malformed DLQ 분류와 max receive count를 둔다. |
| DB transient failure를 delete함 | data loss | transient failure에서는 delete하지 않는다. |
| Worker MVP가 batch throughput 개선으로 홍보됨 | 성능 주장 과장 | Story 12.3은 end-to-end decoupling까지만 claim한다. |
