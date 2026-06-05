---
artifactType: story
storyId: "12.3"
storyKey: "12-3-spring-boot-sqs-worker-mvp-and-idempotency"
epic: "Epic 12. SQS Buffered Ingest Transition"
title: "Spring Boot SQS Worker MVP and Idempotency"
architectureStyle: Traditional MVC
status: done
date: 2026-06-05
commitBoundary: "feat: add spring boot sqs worker mvp"
---

# Story 12.3 - Spring Boot SQS Worker MVP and Idempotency

## Status

done

2026-06-05: Story 12.1/12.2에서 닫힌 SQS buffered ingest 계약과 현재 코드 상태를 기준으로 worker MVP 구현 세부를 ready-for-dev 수준으로 확정했다. 이 story는 코드 구현 산출물이 아니라, 구현자가 바로 `bmad-dev-story`로 들어갈 수 있게 하는 developer handoff 문서다.

2026-06-05: bmad-dev-story 구현을 시작하며 상태를 in-progress로 전환했다.

2026-06-05: Spring Boot portal 내부 SQS worker MVP와 idempotency/DLQ 경계 구현을 완료하고 review로 전환했다.

2026-06-05: Review follow-up으로 DLQ envelope value sanitizer와 credential-like Idempotency-Key project component reject를 추가한 뒤 done으로 전환했다.

## Story

구현자로서, Spring Boot portal 내부 worker가 SQS message를 읽어 기존 accepted bucket persistence path로 안전하게 저장하길 원한다.

그래야 SQS Standard의 at-least-once/out-of-order 전달을 정상 조건으로 받아들이면서 duplicate, conflict, malformed message를 명확하게 분류할 수 있다.

## Source of Truth

이 story는 아래 문서를 읽고 정리한 구현 기준이다. 충돌처럼 보이는 지점은 Story 12.1의 닫힌 결정, Story 12.2의 실제 queue contract, 그리고 이 story의 구현 전 결정을 우선한다.

1. `planning-artifacts/stories/12-1-architecture-and-contract-decision.md`
2. `planning-artifacts/stories/12-2-ingest-enqueue-boundary.md`
3. `planning-artifacts/stories/12-3-spring-boot-sqs-worker-mvp-and-idempotency.md`
4. `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
5. `planning-artifacts/epic-11-12-operational-alerts-and-sqs-batch-ingest-plan.md`
6. `planning-artifacts/contracts/ingest-envelope.md`
7. `planning-artifacts/contracts/time-buckets.md`
8. `planning-artifacts/database-schema.md`
9. `planning-artifacts/infrastructure-input-notes.md`
10. `implementation-artifacts/sprint-status.yaml`
11. `_bmad/custom/project-context.md`
12. `.env.example`

## Story 12.1/12.2에서 이미 닫힌 결정

- Consumer는 Spring Boot portal 내부 worker다. Lambda consumer, 별도 worker service, Lambda event source mapping은 이번 story 범위가 아니다.
- SQS mode는 opt-in이고 rollback은 `portal.ingest.buffer.mode=direct` 복귀다.
- `202 queued`는 enqueue 성공만 뜻하며 DB 저장 완료, freshness current, snapshot 반영 완료를 뜻하지 않는다.
- SQS mode request path는 repository idempotency lookup, catalog get-or-create, accepted bucket insert를 수행하지 않는다.
- Queue body는 `QueuedMetricBucketMessage`의 `messageVersion="1"` contract를 사용한다.
- Queue `payloadHash`는 기존 `IngestPayloadHasher#sha256(IngestEnvelopeRequest)` 결과이며 queue metadata를 포함하지 않는다.
- Queue attributes는 `messageVersion`, `projectId`, `schemaVersion`, `bucketStartUtc`, `bucketEndUtc`, `applicationName`, `environment`, `instanceName` 8개 allow-list다.
- Queue body/attribute/log/error에는 raw project key, starter credential, Authorization token, Discord webhook URL, raw path/query/trace/per-request sample을 넣지 않는다.
- Payload too large는 request boundary에서 `413`으로 reject되며 worker poison message가 되지 않는다.
- Story 12.3은 DB throughput 개선을 주장하지 않는다. DB batch throughput claim은 Story 12.5에서 batch writer와 측정 이후에만 가능하다.
- Story 12.3만 완료된 상태를 사용자-facing rollout ready로 보지 않는다. Story 12.4의 snapshot delay, queue lag diagnostic, stale/down naming guard가 함께 닫혀야 rollout 후보가 된다.

## 현재 코드 상태

- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/IngestBufferProperties.java`는 `mode`, `message-size-limit-bytes`, `publisher-timeout`, `sqs.queue-url`, `sqs.endpoint-override`만 가진다. worker/DLQ 설정은 아직 없다.
- `FakeMetricIngestQueuePublisher`는 enqueue된 `MetricIngestQueueMessage` snapshot을 보존하지만 receive/delete/DLQ 상태는 없다. 12.3 fake worker smoke를 위해 fake queue store 또는 fake consumer/delete boundary가 필요하다.
- `SqsMetricIngestQueuePublisher`는 `SendMessage` 전용이다. receive/delete/change-visibility/DLQ send는 별도 class로 추가해야 한다.
- `MetricIngestQueueMessageFactory`는 worker가 재검증할 body와 8개 attributes를 이미 생성한다.
- `IngestAcceptanceService`의 validation logic은 private inner class라 worker가 그대로 호출할 수 없다. 12.3은 behavior-compatible 검증 helper/service를 추출하거나 worker 전용 validator를 만들고 기존 acceptance test와 호환성을 테스트로 묶어야 한다.
- `IngestAcceptanceService` direct mode는 기존 duplicate MVP 정책상 existing idempotency key를 `duplicate_idempotency_key`로 reject한다. Queue mode에서는 duplicate/conflict 최종 판정을 하지 않는다.
- `MetricBucketRepository#insert(...)`는 `ApplicationCatalogRepository#getOrCreate(...)` 후 `accepted_metric_buckets.saveAndFlush(...)`를 수행한다. Worker MVP는 이 method를 message별로 재사용해 end-to-end를 먼저 닫는다.
- `MetricBucketRepository#findByProjectIdAndIdempotencyKey(...)`는 `AcceptedMetricBucketReceipt`만 반환하므로 stored `payloadHash`, `applicationInstanceId`, bucket boundary 비교에는 부족하다.
- `AcceptedMetricBucketJpaRepository`에는 `findByApplicationInstanceIdAndBucketStartUtc(...)`가 있지만 facade projection으로 노출되지 않는다.
- `accepted_metric_buckets`에는 `uk_buckets_project_idempotency_key`와 `uk_buckets_instance_bucket_start`가 있어 DB unique constraint가 최종 idempotency guard다.
- `MetricBucketRepositoryIntegrationTest`는 unique constraint와 overwrite 방지를 이미 검증하지만, stored hash/identity projection 조회 regression은 없다.
- `MvcLayerBoundaryTest`는 Epic 12 SQS surface를 `com.observation.portal.domain.ingest.queue` package에 한해 예외로 인정한다. Worker/processor/DLQ helper는 이 package 안에 둔다.

## 목표

- Portal 내부 Spring Boot SQS worker MVP를 구현한다.
- Worker MVP는 기존 `MetricBucketRepository#insert(...)` 재사용으로 enqueue-to-persist end-to-end를 먼저 닫는다.
- Same key + same hash duplicate는 no-op success로 ack/delete한다.
- Same key + different hash, same instance bucket + different key, malformed message는 retry로 회복되지 않는 failure로 분류하고 application-level sanitized DLQ envelope를 우선 사용한다.
- Transient DB failure는 source message를 delete하지 않고 visibility timeout 이후 retry되게 한다.
- Worker failure가 dashboard read API, static UI delivery, heartbeat endpoint, direct ingest validation path를 막지 않게 한다.

## Scope / Non-Scope

포함:

- Spring Boot portal runtime 내부 queue worker component.
- SQS receive/delete/change visibility/DLQ send 경계와 fake queue worker smoke 경계.
- `messageVersion=1` body/attribute parse, payload hash 재계산, schema/bucket/idempotency/application identity 재검증.
- 기존 repository insert path를 재사용한 MVP persistence.
- Duplicate/conflict/malformed/transient 결과 matrix와 ack/delete/DLQ 경계.
- Stored bucket identity projection 추가.
- Application-level sanitized DLQ envelope와 redaction guard.
- Worker config properties와 `.env.example` 변수명 정렬.
- Focused unit/integration/architecture tests.

제외:

- Lambda consumer, Lambda batch response, Lambda event source mapping, 별도 worker deployment.
- Exactly-once delivery guarantee claim.
- Batch writer, JDBC batch, `ON CONFLICT` 기반 throughput 최적화.
- DB throughput improvement claim.
- Queue replay UI, DLQ browser UI, poison message repair UI.
- Snapshot delay/cutoff 구현. 이는 Story 12.4 범위다.
- Lifecycle state, p95/p99, endpoint priority, dashboard snapshot/history, operational event 계산 변경.
- Starter가 AWS credential로 SQS에 직접 쓰는 모델.
- LocalStack을 기본 unit/integration test dependency로 만드는 것.

## 구현 전 결정

| 항목 | 결정 | 구현 제약 |
| --- | --- | --- |
| Worker runtime | Spring Boot portal 내부 worker만 구현한다. | 새 module, separate worker service, Lambda package를 만들지 않는다. |
| Worker package | `com.observation.portal.domain.ingest.queue` 아래에 둔다. | 기존 architecture SQS 예외 범위 안에 둔다. `application`, `port`, `adapter` package를 만들지 않는다. |
| Worker activation | `portal.ingest.buffer.worker.enabled=false`를 default로 둔다. | `direct` mode rollback과 local 기본 실행이 worker로 막히지 않게 한다. |
| Real SQS mode | `portal.ingest.buffer.mode=sqs`와 `worker.enabled=true`를 함께 켠 환경에서 실제 receive/delete를 수행한다. | source queue URL은 기존 `portal.ingest.buffer.sqs.queue-url`을 사용한다. |
| Fake worker smoke | SQS 없이 fake queue store로 enqueue -> worker -> repository insert -> delete 상태를 검증한다. | LocalStack은 opt-in smoke 후보일 뿐 기본 test에 요구하지 않는다. |
| Message parse | `QueuedMetricBucketMessage`를 portal `ObjectMapper`로 parse한다. | raw body를 log/DLQ payload에 남기지 않는다. |
| Validation reuse | 12.2 request validation과 behavior-compatible한 validator를 worker에서 재사용 가능하게 추출하거나 worker 전용 validator를 만든다. | private inner class 복붙으로 divergence가 생기지 않게 focused test를 둔다. |
| Payload hash check | `IngestPayloadHasher#sha256(message.payload())`를 재계산해 `payloadHash`와 비교한다. | hash mismatch는 malformed로 분류한다. |
| MVP persistence | `AcceptedMetricBucketWriteCommand.from(...)`와 `MetricBucketRepository#insert(...)`를 message별로 재사용한다. | Story 12.3은 DB round trip 개선을 주장하지 않는다. |
| Duplicate lookup | stored identity projection을 추가해 same key/same hash를 no-op success로 분류한다. | 기존 `AcceptedMetricBucketReceipt`만으로 비교하지 않는다. |
| Conflict lookup | same key/different hash와 same instance bucket/different key를 deterministic conflict로 분류한다. | DB unique violation 후에도 re-read projection으로 분류하고 row overwrite는 금지한다. |
| DLQ policy | malformed/conflict는 application-level sanitized DLQ envelope를 source queue와 별도 DLQ URL로 `SendMessage` 후 source delete한다. | DLQ send가 실패하면 source delete하지 않고 native redrive safety net에 맡긴다. |
| Transient failure | DB connection/timeout/deadlock 등 transient persistence failure는 source delete하지 않는다. | visibility timeout 이후 retry되며 application DLQ로 직접 보내지 않는다. |
| Partial batch | message별 result를 독립 처리한다. inserted/no-op/app-DLQ-success만 delete하고 transient/DLQ-send-failure는 남긴다. | 한 message 실패가 같은 poll response의 성공 message delete를 막지 않게 한다. |
| Logging | failure category, source message id, receive count, sanitized identity만 남긴다. | raw payload, raw project key, Authorization token, webhook URL, starter credential, AWS secret, queue URL은 log/error/response에 남기지 않는다. |

## Worker Config Defaults

`observability-portal/src/main/resources/application.properties`에는 default safe values를 추가한다. `.env`는 읽거나 commit하지 않는다. `.env.example`의 변수명만 기준으로 삼는다.

```properties
portal.ingest.buffer.mode=direct
portal.ingest.buffer.message-size-limit-bytes=1048576
portal.ingest.buffer.publisher-timeout=3s
portal.ingest.buffer.sqs.queue-url=
portal.ingest.buffer.sqs.endpoint-override=

portal.ingest.buffer.worker.enabled=false
portal.ingest.buffer.worker.dlq-url=
portal.ingest.buffer.worker.long-poll-seconds=20
portal.ingest.buffer.worker.max-messages-per-poll=10
portal.ingest.buffer.worker.visibility-timeout=60s
portal.ingest.buffer.worker.max-receive-count=5
portal.ingest.buffer.worker.max-batch-size=10
portal.ingest.buffer.worker.max-batch-age=2s
```

환경 변수명:

| Property | Env name | Default | 비고 |
| --- | --- | --- | --- |
| `portal.ingest.buffer.mode` | `PORTAL_INGEST_BUFFER_MODE` | `direct` | real SQS path는 `sqs` opt-in |
| `portal.ingest.buffer.sqs.queue-url` | `PORTAL_INGEST_BUFFER_SQS_QUEUE_URL` | blank | source queue URL |
| `portal.ingest.buffer.sqs.endpoint-override` | `PORTAL_INGEST_BUFFER_SQS_ENDPOINT_OVERRIDE` | blank | LocalStack opt-in 전용 |
| `portal.ingest.buffer.worker.enabled` | `PORTAL_INGEST_BUFFER_WORKER_ENABLED` | `false` | default local/rollback guard |
| `portal.ingest.buffer.worker.dlq-url` | `PORTAL_INGEST_BUFFER_WORKER_DLQ_URL` | blank | malformed/conflict sanitized DLQ send 대상 |
| `portal.ingest.buffer.worker.long-poll-seconds` | `PORTAL_INGEST_BUFFER_WORKER_LONG_POLL_SECONDS` | `20` | SQS long polling 최대값 |
| `portal.ingest.buffer.worker.max-messages-per-poll` | `PORTAL_INGEST_BUFFER_WORKER_MAX_MESSAGES_PER_POLL` | `10` | SQS `ReceiveMessage` API 최대값 |
| `portal.ingest.buffer.worker.visibility-timeout` | `PORTAL_INGEST_BUFFER_WORKER_VISIBILITY_TIMEOUT` | `60s` | 현재 dev source queue 설정과 일치 |
| `portal.ingest.buffer.worker.max-receive-count` | `PORTAL_INGEST_BUFFER_WORKER_MAX_RECEIVE_COUNT` | `5` | resource redrive policy와 일치 |
| `portal.ingest.buffer.worker.max-batch-size` | `PORTAL_INGEST_BUFFER_WORKER_MAX_BATCH_SIZE` | `10` | MVP는 receive page 단위 처리 |
| `portal.ingest.buffer.worker.max-batch-age` | `PORTAL_INGEST_BUFFER_WORKER_MAX_BATCH_AGE` | `2s` | batch writer 전 MVP latency guard |
| AWS region | `AWS_REGION`, `AWS_DEFAULT_REGION` | `ap-northeast-2` in example | credential 값은 `.env` local-only |

Validation constraints:

- `long-poll-seconds`는 `0..20` 범위만 허용한다.
- `max-messages-per-poll`은 `1..10` 범위만 허용한다.
- `visibility-timeout`, `max-batch-age`는 positive duration이어야 한다.
- `max-receive-count`는 `1` 이상이어야 한다.
- `max-batch-size`는 `1` 이상이며 MVP에서는 `10`을 기본으로 둔다.
- `worker.enabled=true`이고 mode가 `sqs`인데 source queue URL 또는 DLQ URL이 blank면 worker는 message를 처리하지 않고 sanitized disabled/unavailable 상태로 fail closed한다.

## SQS / DLQ Resource Assumptions

최근 AWS smoke 확인 기준으로 아래 리소스를 Story 12.3 dev default로 사용한다. 이 값은 secret이 아니지만 `.env`의 credential 값은 읽거나 출력하지 않는다.

| 항목 | 값 |
| --- | --- |
| Region | `ap-northeast-2` |
| Source queue name | `observation-dev-metric-bucket-ingest` |
| Source queue URL | `https://sqs.ap-northeast-2.amazonaws.com/491013322019/observation-dev-metric-bucket-ingest` |
| DLQ name | `observation-dev-metric-bucket-ingest-dlq` |
| DLQ URL | `https://sqs.ap-northeast-2.amazonaws.com/491013322019/observation-dev-metric-bucket-ingest-dlq` |
| Source queue type | Standard |
| DLQ type | Standard |
| Encryption | SSE-SQS |
| Source visibility timeout | `60s` |
| Source receive wait time | `20s` |
| Source retention | `4 days` |
| Source max message size | `1048576 bytes` |
| Redrive | source -> DLQ, `maxReceiveCount=5` |
| DLQ retention | `14 days` |
| DLQ max message size | `1048576 bytes` |
| Redrive allow policy | byQueue, source queue ARN limited |

Smoke verification evidence already completed:

- `GetQueueAttributes` for source queue succeeded.
- `GetQueueAttributes` for DLQ succeeded.
- `SendMessage` to source queue succeeded.
- `ReceiveMessage` from source queue succeeded.
- `DeleteMessage` for the same probe message succeeded.

Implementation consequence:

- Worker tests must not require real AWS by default.
- Optional AWS smoke can reuse the above queue URLs through `.env` local-only values.
- Application-level DLQ send should produce sanitized envelope messages. Native SQS redrive remains the safety net when worker cannot classify/delete safely or DLQ send itself fails.

## Result Matrix

| Scenario | Classification | Persistence action | Queue action | DLQ action | Delete source? | Test focus |
| --- | --- | --- | --- | --- | --- | --- |
| valid new message | inserted | `MetricBucketRepository#insert(...)` creates row | ack/delete after insert | none | yes | fake queue worker smoke |
| same project/idempotency key + same payload hash | duplicate no-op success | no new row, no overwrite | ack/delete | none | yes | duplicate matrix |
| same project/idempotency key + different payload hash | conflict | no new row, existing row unchanged | application DLQ then ack/delete | sanitized `conflict` envelope | only after DLQ send success | duplicate/conflict matrix |
| same application instance + same bucket start + different idempotency key | conflict | no new row, existing row unchanged | application DLQ then ack/delete | sanitized `conflict` envelope | only after DLQ send success | repository identity lookup regression |
| malformed JSON | malformed | none | application DLQ then ack/delete | sanitized `malformed` envelope | only after DLQ send success | malformed matrix |
| unsupported `messageVersion` | malformed | none | application DLQ then ack/delete | sanitized `malformed` envelope | only after DLQ send success | version guard |
| missing required body/attribute identity | malformed | none | application DLQ then ack/delete | sanitized `malformed` envelope | only after DLQ send success | message shape guard |
| body/attribute identity mismatch | malformed | none | application DLQ then ack/delete | sanitized `malformed` envelope | only after DLQ send success | attribute cross-check |
| payload hash mismatch | malformed | none | application DLQ then ack/delete | sanitized `malformed` envelope | only after DLQ send success | hash recomputation |
| invalid schemaVersion/bucket boundary/idempotency key | malformed | none | application DLQ then ack/delete | sanitized `malformed` envelope | only after DLQ send success | worker validation parity |
| transient DB failure/timeout/deadlock | transient failure | transaction rollback | do not delete | none from application | no | transient retry/delete boundary |
| SQS receive failure | transient worker failure | none | no delete | none | no | worker resilience |
| source delete failure after success | retry-risk / operational warning | row already inserted/no-op | leave message visible after timeout | none | no | delete failure idempotency |
| application DLQ send failure | transient DLQ failure | no new row for malformed/conflict | do not delete | native redrive safety net | no | DLQ publish failure boundary |

Duplicate/no-op must not be logged as error. Use debug/info or a count metric candidate such as `ingest.worker.persist.duplicate.count`.

## Sanitized DLQ Envelope

Application-level DLQ message body uses a new versioned envelope. It is not the original SQS source body and must not include the raw ingest payload.

Required fields:

- `dlqEnvelopeVersion`: `"1"`
- `failureCategory`: `malformed` or `conflict`
- `failureCode`: allow-list value such as `invalid_json`, `unsupported_message_version`, `payload_hash_mismatch`, `idempotency_payload_conflict`, `instance_bucket_identity_conflict`
- `sourceMessageId`: SQS/fake source message id
- `receiveCount`: SQS approximate receive count if available, otherwise fake/test attempt count
- `occurredAt`: worker UTC timestamp
- `messageVersion`: parsed value when safe, otherwise `null`
- `projectId`: UUID when parsed from trusted field/attribute, otherwise `null`
- `applicationName`, `environment`, `instanceName`: sanitized identity when parsed, otherwise `null`
- `bucketStartUtc`, `bucketEndUtc`: sanitized bucket boundary when parsed, otherwise `null`
- `idempotencyKey`: parsed idempotency key when present
- `payloadHash`: incoming payload hash when present
- `storedPayloadHash`: only for same-key conflict, no raw payload
- `storedBucketId`: only when identity projection found an existing row
- `workerAction`: `sent_to_application_dlq`

Forbidden fields/content:

- original `payload` object
- original source `bodyJson`
- raw project key
- starter credential
- Authorization token
- Discord webhook URL
- AWS credential, AWS exception detail, queue URL
- raw path/query/trace/per-request sample
- arbitrary exception message

If a classified malformed/conflict message cannot be published to the application DLQ, the worker must not delete the source message. Native SQS redrive remains the safety net, and operator runbooks must avoid printing native DLQ raw bodies.

## Acceptance Criteria

1. Worker runs only inside the Spring Boot portal runtime and is disabled by default.
2. No Lambda consumer, Lambda handler scaffold, event source mapping, separate worker service, `application`, `port`, or `adapter` package is added.
3. Worker config properties and env names match the Worker Config Defaults section.
4. Real SQS worker reads from `portal.ingest.buffer.sqs.queue-url` and sends classified malformed/conflict envelopes to `portal.ingest.buffer.worker.dlq-url`.
5. Fake queue worker path supports enqueue -> process -> ack/delete verification without SQS or LocalStack.
6. Worker uses `messageVersion="1"` only. Unsupported version is malformed.
7. Worker reparses message body and cross-checks allow-list attributes where present.
8. Worker recomputes `IngestPayloadHasher#sha256(payload)` and rejects hash mismatch as malformed.
9. Worker validates schema version, UTC 30초 bucket boundary, bucket duration, application identity, idempotency key consistency, summary/endpoints, and supported `localPercentiles` semantics behavior-compatibly with existing ingest acceptance.
10. Worker converts valid message into `AcceptedMetricBucketWriteCommand` and uses `MetricBucketRepository#insert(...)` for MVP persistence.
11. Repository exposes enough stored identity projection to compare idempotency key, payload hash, application instance id, bucket start/end, accepted bucket id, and acceptedAt without returning JPA entity to service.
12. Same project/idempotency key + same payload hash is no-op success and source message is deleted.
13. Same project/idempotency key + different payload hash is conflict, application DLQ envelope is sent, and source message is deleted only after DLQ send succeeds.
14. Same application instance + same bucket start + different idempotency key is conflict, application DLQ envelope is sent, and source message is deleted only after DLQ send succeeds.
15. Malformed JSON, missing required field, unsupported message version, payload hash mismatch, invalid bucket boundary, unsupported schema version, and body/attribute mismatch are malformed DLQ cases.
16. Transient DB failure, database connection failure, transaction timeout, lock timeout, and deadlock do not delete source messages and do not send application DLQ envelopes.
17. Inserted/no-op success is ack/delete; transient failure is not delete; application DLQ send success is ack/delete; application DLQ send failure is not delete.
18. Partial batch handling deletes only messages whose result safely reached inserted, duplicate no-op, or application DLQ sent.
19. Worker failure does not block dashboard read API, static UI delivery, heartbeat endpoint, direct mode ingest validation, or portal startup when worker is disabled.
20. Logs, exceptions, result objects, and DLQ envelopes do not contain raw payload, raw project key, Authorization token, Discord webhook URL, starter credential, AWS credential, or queue URL.
21. Duplicate/no-op is not emitted as error log.
22. Story 12.3 implementation notes do not claim DB throughput improvement.
23. Story 12.3 remains non-rollout-ready without Story 12.4 lag/snapshot semantics.
24. New public classes/methods and non-obvious helpers include Korean Javadoc/doc comments per `AGENTS.md`.

## Tasks / Subtasks

- [x] Pre-flight and existing contract alignment (AC: 1~4, 22~24)
  - [x] `git status --short`로 기존 dirty/untracked 상태를 기록하되 unrelated 변경은 되돌리지 않는다.
  - [x] 아래 파일을 다시 읽고 현재 code baseline이 이 story와 맞는지 확인한다.
    - `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/*`
    - `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java`
    - `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestPayloadHasher.java`
    - `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
    - `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommand.java`
  - [x] Story 12.1/12.2의 `202 queued`, no direct fallback, no Lambda, no 12.3-only rollout guard를 구현 notes에 유지한다.

- [x] Worker properties 추가 (AC: 1, 3, 19)
  - [x] `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/IngestBufferProperties.java`에 `Worker` nested properties를 추가한다.
  - [x] `observability-portal/src/main/resources/application.properties`에 worker default values를 추가한다.
  - [x] property validation test를 `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/IngestBufferPropertiesTest.java`에 추가한다.
  - [x] `worker.enabled=false` default에서 worker bean이 receive loop를 시작하지 않는지 test한다.

- [x] Queue receive/delete/DLQ boundary 추가 (AC: 4, 5, 17, 18)
  - [x] `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueConsumer.java` 또는 동등한 receive/delete/change-visibility interface를 추가한다.
  - [x] `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestReceivedMessage.java`에 source message id, receipt handle, body bytes/json, attributes, receive count를 담는다.
  - [x] `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/SqsMetricIngestQueueConsumer.java`는 AWS SDK v2 `ReceiveMessage`, `DeleteMessage`, 필요 시 `ChangeMessageVisibility`를 사용한다.
  - [x] `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestDlqPublisher.java`와 SQS/fake 구현을 추가한다.
  - [x] fake queue는 publisher가 넣은 message를 worker가 receive하고 delete state를 검사할 수 있게 확장하거나 별도 fake store를 둔다.

- [x] Worker orchestration 구현 (AC: 1, 5, 17~19)
  - [x] `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueWorker.java`를 추가한다.
  - [x] `@ConditionalOnProperty(name = "portal.ingest.buffer.worker.enabled", havingValue = "true")`로 worker activation을 제한한다.
  - [x] receive page 또는 bounded batch를 `MetricIngestQueueProcessor`에 넘기고 message별 result에 따라 delete/DLQ/no-delete를 적용한다.
  - [x] worker loop 예외는 portal startup/read endpoints를 죽이지 않도록 catch하고 sanitized category만 남긴다.

- [x] Message parse/validation/command conversion 구현 (AC: 6~10, 15, 20)
  - [x] `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueMessageParser.java` 또는 processor 내부 parser를 추가한다.
  - [x] `QueuedMetricBucketMessage` body와 8개 attributes를 cross-check한다.
  - [x] `IngestPayloadHasher`로 payload hash를 재계산한다.
  - [x] 기존 `IngestAcceptanceService` validation과 behavior-compatible한 검증 helper를 추출하거나 worker validator를 추가한다.
  - [x] valid message를 `VerifiedProject` + idempotency key + payload로 `ValidatedIngestCandidate` 후보를 구성하고 `AcceptedMetricBucketWriteCommand.from(...)`으로 변환한다.

- [x] Stored identity projection과 duplicate/conflict 분류 구현 (AC: 10~14, 16)
  - [x] `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketIdentity.java` 또는 동등 projection record를 추가한다.
  - [x] `MetricBucketRepository`에 `findIdentityByProjectIdAndIdempotencyKey(...)`를 추가한다.
  - [x] instance bucket conflict 분류를 위해 `findIdentityByApplicationInstanceIdAndBucketStartUtc(...)` 또는 application identity 기반 facade method를 추가한다.
  - [x] `AcceptedMetricBucketJpaRepository`는 JPQL constructor projection이나 entity-to-projection mapping으로 JPA entity를 service에 노출하지 않는다.
  - [x] insert 전 pre-read와 insert unique violation 후 re-read를 모두 고려해 race에서도 same hash no-op/conflict 분류가 안정적으로 나오게 한다.
  - [x] transient DB failure와 deterministic conflict를 constraint name/re-read result로 분리한다.

- [x] Application-level sanitized DLQ envelope 구현 (AC: 13~15, 17, 20)
  - [x] `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestDlqEnvelope.java`를 추가한다.
  - [x] `failureCategory`, `failureCode`, source message id, receive count, sanitized identity, incoming/stored hash 후보만 담는다.
  - [x] original `payload`, body JSON, raw project key, token, webhook URL, queue URL, arbitrary exception message는 field와 `toString()`에 포함하지 않는다.
  - [x] DLQ send success 후에만 source delete를 수행한다.
  - [x] DLQ send failure는 source delete 없이 retry/native redrive에 맡긴다.

- [x] Metrics/logging 최소 guard (AC: 20, 21)
  - [x] duplicate/no-op은 error log가 아니라 debug/info 또는 counter 후보로만 남긴다.
  - [x] warning/error log는 failure category, source message id, receive count, sanitized identity만 포함한다.
  - [x] raw payload/secret redaction test를 추가한다.

- [x] Verification 수행 (AC: 1~24)
  - [x] focused queue worker/service/repository tests를 실행한다.
  - [x] `MetricBucketRepositoryIntegrationTest`를 실행한다.
  - [x] architecture boundary tests를 실행한다.
  - [x] 영향 범위에 따라 `./gradlew :observability-portal:test`를 실행한다.
  - [x] `git diff --check`를 실행한다.

## Tests To Add / Update

필수 신규/수정 test:

- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueWorkerFakeSmokeTest.java`
  - fake queue에 enqueue된 golden message가 worker를 거쳐 `accepted_metric_buckets` 저장 후보로 전달되고 source message가 delete되는지 확인한다.

- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessorDuplicateMatrixTest.java`
  - same key/same hash -> no-op success + delete.
  - same key/different hash -> conflict DLQ + delete after DLQ send.
  - same instance bucket/different key -> conflict DLQ + delete after DLQ send.
  - duplicate/no-op이 error log/result가 아닌지 확인한다.

- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessorMalformedMatrixTest.java`
  - invalid JSON, unsupported version, missing required body field, body/attribute mismatch, hash mismatch, invalid bucket boundary, unsupported schemaVersion, invalid idempotency key를 malformed DLQ로 분류한다.

- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessorRetryBoundaryTest.java`
  - repository transient exception 시 delete와 DLQ send가 호출되지 않는다.
  - DLQ send failure 시 source delete가 호출되지 않는다.
  - source delete failure after success가 다음 retry에서 duplicate no-op으로 안전하게 수렴한다.

- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/SqsMetricIngestQueueConsumerTest.java`
  - receive request가 queue URL, wait time 20, max number 10, visibility timeout 60s, required attributes를 사용한다.
  - delete request가 receipt handle만 사용하고 raw body를 log하지 않는다.
  - AWS SDK failure가 sanitized exception으로 수렴한다.

- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestDlqEnvelopeTest.java`
  - malformed/conflict envelope가 allow-list field만 직렬화한다.
  - raw payload, body JSON, raw project key, Authorization token, webhook URL, queue URL, arbitrary exception message를 포함하지 않는다.

- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
  - stored identity projection이 `payloadHash`, `applicationInstanceId`, `bucketStartUtc`, `idempotencyKey`, `bucketId`를 반환한다.
  - same idempotency key/same hash projection으로 no-op 판정이 가능하다.
  - same idempotency key/different hash와 same instance bucket/different key가 기존 row를 overwrite하지 않는다.

- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestAcceptanceServiceTest.java`
  - 필요 시 validation helper 추출 후 기존 direct/fake/sqs request path behavior가 바뀌지 않았는지 regression을 추가한다.

- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
  - worker/processor/DLQ classes가 controller/dto/UI/static delivery layer에 의존하지 않는지 확인한다.
  - `Lambda`, `event source mapping`, separate worker package, `application`/`port`/`adapter` package가 추가되지 않았는지 확인한다.

권장 실행 명령:

```bash
./gradlew :observability-portal:test --tests '*MetricIngestQueue*'
./gradlew :observability-portal:test --tests '*MetricBucketRepositoryIntegrationTest'
./gradlew :observability-portal:test --tests '*IngestAcceptanceServiceTest'
./gradlew :observability-portal:test --tests '*MvcLayerBoundaryTest'
./gradlew :observability-portal:test
git diff --check
```

Gradle test discovery가 `Could not execute test class ... 2` 또는 `wrong name`으로 실패하면 먼저 build output duplicate를 확인한다. `observability-portal/build/classes` 아래 duplicate만 있으면 `observability-portal/build`는 재생성 가능한 산출물이므로 삭제 후 다시 테스트한다. `planning-artifacts`, `docs`, `src` 아래 duplicate 파일은 사용자 작업물일 수 있으므로 자동 삭제하지 않는다.

## Dev Agent Handoff Notes

1. 12.3의 첫 목표는 throughput이 아니라 correctness다. 기존 `MetricBucketRepository#insert(...)`를 message별로 재사용해 end-to-end를 닫고, Story 12.5 전까지 DB batch throughput 개선을 주장하지 않는다.
2. `IngestAcceptanceService` validator는 private이므로 그대로 재사용할 수 없다. 추출한다면 기존 tests가 깨지지 않게 하고, 추출하지 않는다면 worker validator parity tests를 반드시 둔다.
3. Duplicate/conflict 분류에는 `AcceptedMetricBucketReceipt`가 부족하다. stored identity projection을 먼저 추가한 뒤 processor를 구현한다.
4. Application-level DLQ envelope는 조사용 metadata만 담는다. source queue message body를 그대로 DLQ message body에 복사하지 않는다.
5. Classified malformed/conflict는 DLQ send 성공 후 source delete한다. DLQ send 실패나 transient DB failure는 delete하지 않는다.
6. Source delete 실패는 data loss보다 duplicate retry가 낫다. 다음 delivery가 same hash duplicate no-op으로 수렴해야 한다.
7. `worker.enabled=false` default와 direct mode rollback path를 유지한다.
8. Real AWS smoke는 `.env` local-only credential로 수행한다. `.env`를 읽거나 출력하거나 commit하지 않는다.
9. 모든 새 public class/method와 non-obvious helper의 Javadoc/doc comment는 `AGENTS.md` 원칙에 맞춰 한국어로 작성한다.

## 위험과 완화책

| 위험 | 영향 | 완화책 |
| --- | --- | --- |
| SQS duplicate/out-of-order가 중복 row를 만듦 | accepted bucket source 오염 | DB unique constraint, stored identity projection, same hash no-op success를 함께 둔다. |
| Same key/different hash를 transient로 오분류함 | poison message 반복 | idempotency projection과 conflict DLQ matrix test를 둔다. |
| Same instance bucket/different key를 놓침 | starter duplicate flush 설계 위반이 숨겨짐 | application instance identity + bucket start projection과 repository regression을 추가한다. |
| Malformed message가 무한 retry됨 | worker capacity 낭비 | application-level sanitized DLQ 후 source delete를 기본으로 둔다. |
| DB transient failure를 delete함 | data loss | transient failure는 delete/DLQ 없음으로 고정하고 retry boundary test를 둔다. |
| DLQ payload/log에 raw payload/secret이 남음 | 보안 사고 | DLQ envelope allow-list와 redaction tests를 둔다. |
| Worker startup failure가 portal을 막음 | read API/static UI/heartbeat 영향 | worker disabled default, conditional activation, loop-level exception guard를 둔다. |
| Worker MVP가 batch throughput 개선으로 홍보됨 | 성능 주장 과장 | 12.3은 end-to-end decoupling/correctness만 claim하고 Story 12.5로 throughput을 분리한다. |
| 12.3만으로 rollout됨 | queue lag 때문에 dashboard state/copy가 흔들림 | Story 12.4 lag/snapshot semantics 전 user-facing rollout 금지를 유지한다. |

## Completion Note

Ultimate context engine analysis completed - comprehensive developer guide created.

## Dev Agent Record

### Implementation Plan

1. Story 12.1/12.2의 닫힌 계약을 유지해 Spring Boot portal 내부 worker만 추가하고 Lambda, 별도 worker service, `application`/`port`/`adapter` package는 만들지 않는다.
2. `portal.ingest.buffer.worker.*` safe defaults와 fake/SQS receive-delete-DLQ 경계를 추가한다.
3. `MetricIngestQueueProcessor`에서 messageVersion, body/attribute identity, payload hash, 기존 ingest validation, duplicate/conflict/transient matrix를 분류한다.
4. `MetricBucketRepository`에 stored identity projection을 추가해 same key/same hash no-op과 conflict를 JPA entity 노출 없이 판정한다.
5. sanitized DLQ envelope와 ack/delete boundary를 테스트로 잠그고, Story 12.3이 DB throughput 개선 또는 rollout-ready claim을 하지 않게 유지한다.

### Debug Log

- `git status --short` 확인 시 `AGENTS.md`, `implementation-artifacts/sprint-status.yaml`, Story 12.3 문서가 이미 modified였고, duplicate 형태의 unrelated untracked 파일들이 있었다. unrelated 변경은 되돌리지 않았다.
- Red phase에서 `./gradlew :observability-portal:test --tests '*MetricIngestQueue*'`가 새 worker/processor/projection 타입 부재로 compile fail했다.
- Green phase 중 Jackson `ObjectMapper#readValue(byte[])`의 `IOException` 선언을 catch하도록 parser를 수정했다.
- Worker boundary test는 `MetricIngestReceivedMessage`의 `byte[]` record equality 특성 때문에 Mockito stub 매칭이 어긋났고, messageId 기반 answer로 고쳐 worker 동작을 검증했다.
- `.env`는 읽거나 출력하지 않았다. `.env.example`은 변수명이 이미 Story 12.3 worker 설정과 맞아 추가 변경하지 않았다.

### Completion Notes

- Worker는 `worker.enabled=false` default와 conditional activation을 사용하며, fake mode에서는 in-memory queue store로 enqueue -> poll -> persist -> delete smoke를 검증한다.
- Real SQS boundary는 source queue URL로 receive/delete/change visibility를 수행하고, malformed/conflict는 worker DLQ URL로 sanitized envelope를 전송한다.
- Same idempotency key + same payload hash는 duplicate no-op success로 delete되고, same key/different hash와 same instance bucket/different key는 DLQ send 성공 후에만 delete된다.
- Malformed JSON, unsupported version, missing/mismatched attributes, hash mismatch, invalid validation cases는 raw body 없이 application DLQ envelope로 분류된다.
- Transient DB/DataAccess failure와 DLQ send failure는 source delete를 수행하지 않아 visibility retry/native redrive에 남긴다.
- Review follow-up으로 secret-like parsed identity/idempotency/hash 값이 DLQ envelope/result에 남지 않도록 sanitizer를 보강하고, credential-like `Idempotency-Key.project` component를 invalid request로 차단했다.
- Story 12.3 완료 노트는 DB throughput 개선이나 rollout-ready 상태를 주장하지 않는다. Story 12.4 lag/snapshot semantics 전까지 user-facing rollout 후보가 아니다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketIdentity.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/FakeMetricIngestQueuePublisher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/IngestBufferProperties.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestDlqEnvelope.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestDlqPublishException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestDlqPublisher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueConsumer.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueConsumerException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessStatus.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessor.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueWorker.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestReceivedMessage.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/SqsMetricIngestDlqPublisher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/SqsMetricIngestQueueConsumer.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java`
- `observability-portal/src/main/resources/application.properties`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/IngestBufferPropertiesTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestDlqEnvelopeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessorDuplicateMatrixTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessorMalformedMatrixTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueProcessorRetryBoundaryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueWorkerBoundaryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueWorkerConditionalTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueWorkerFakeSmokeTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/SqsMetricIngestQueueConsumerTest.java`
- `planning-artifacts/stories/12-3-spring-boot-sqs-worker-mvp-and-idempotency.md`

### Change Log

- 2026-06-05: Spring Boot portal 내부 SQS worker MVP, fake/SQS receive-delete-DLQ 경계, idempotency identity projection, sanitized DLQ envelope, duplicate/conflict/malformed/transient 처리 matrix를 구현했다.
- 2026-06-05: fake queue smoke, duplicate/malformed/retry/ack boundary, SQS consumer request/delete, DLQ redaction, repository identity lookup, architecture boundary tests를 추가하고 전체 `:observability-portal:test`와 `git diff --check`를 통과했다.
- 2026-06-05: Review follow-up으로 DLQ envelope value sanitizer와 credential-like Idempotency-Key project component reject를 추가하고 전체 `:observability-portal:test`와 `git diff --check`를 재통과했다.
