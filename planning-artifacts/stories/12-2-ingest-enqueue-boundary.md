---
artifactType: story
storyId: "12.2"
storyKey: "12-2-ingest-enqueue-boundary"
epic: "Epic 12. SQS Buffered Ingest Transition"
title: "Ingest Enqueue Boundary"
architectureStyle: Traditional MVC
status: done
date: 2026-06-05
commitBoundary: "feat: add sqs ingest enqueue boundary"
---

# Story 12.2 - Ingest Enqueue Boundary

## Status

done

2026-06-05: Story 12.1에서 닫힌 architecture decision을 다시 열지 않고, 기존 ingest endpoint의 direct/fake/sqs mode boundary와 queue message contract를 구현하고 검증했다.

## Story

구현자로서, portal HTTP ingest endpoint가 SQS mode에서 DB insert까지 수행하지 않고 검증된 ingest candidate를 queue message로 enqueue하길 원한다.

그래야 starter의 HTTP retry 흐름은 유지하면서도 request thread가 bucket persistence round trip에 묶이지 않는다.

## Source of Truth

아래 문서를 읽고 반영한 create-story context다. 충돌처럼 보이는 지점은 Story 12.1의 닫힌 결정과 이 story의 구현 전 결정을 우선한다.

1. `planning-artifacts/stories/12-1-architecture-and-contract-decision.md`
2. `planning-artifacts/stories/12-2-ingest-enqueue-boundary.md`
3. `planning-artifacts/infrastructure-input-notes.md`
4. `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
5. `planning-artifacts/epic-11-12-operational-alerts-and-sqs-batch-ingest-plan.md`
6. `planning-artifacts/contracts/ingest-envelope.md`
7. `planning-artifacts/api-surface.md`
8. `implementation-artifacts/sprint-status.yaml`
9. `_bmad/custom/project-context.md`

확인한 외부 reference:

- Amazon SQS Standard queue는 at-least-once delivery와 out-of-order 가능성을 전제로 한다. <https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html>
- Amazon SQS message quota는 message attribute 최대 10개, message retention 기본 4일, message size 최대 1,048,576 bytes, delay 기본 0초를 문서화한다. <https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html>
- SQS long polling의 최대 wait time은 20초다. <https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html>

## Story 12.1에서 닫힌 결정

- Consumer는 Spring Boot portal 내부 worker다.
- Lambda는 deferred/non-goal이다.
- SQS mode는 opt-in이고 rollback은 direct mode 복귀다.
- enqueue 성공일 때만 `202 queued`를 반환한다.
- SQS unavailable/config/serialization/message build failure는 `503 Service Unavailable`이다.
- payload too large는 enqueue 전에 `413 Payload Too Large`로 reject한다.
- payload too large direct fallback은 금지한다.
- local/test queue path 기본은 fake queue이고 LocalStack은 opt-in integration이다.
- SQS는 read model source가 아니라 `accepted_metric_buckets` DB insert 전 buffer다.
- Story 12.3 worker MVP만으로 user-facing rollout하지 않는다.

## Current Code State

- `IngestController`는 기존 `POST /api/ingest/v1/buckets` endpoint를 제공하고 `IngestAcceptanceService#accept(...)` 결과를 `201`, `400`, `401`, `409`로 매핑한다.
- `IngestAcceptedResponse`는 direct insert 성공 receipt만 표현한다: `status`, `duplicate`, `bucketId`, `acceptedAt`.
- `IngestErrorResponse`는 `invalid_request`, `unauthorized`, `duplicate_idempotency_key`만 가진다. Story 12.2 구현 때 `payload_too_large`, `ingest_enqueue_unavailable` 계열 factory를 추가해야 한다.
- `IngestAcceptanceService`는 project key 검증, envelope validation, idempotency key validation, repository idempotency lookup, payload hash 계산, `MetricBucketRepository#insert(...)`를 한 request path에서 수행한다.
- `MetricBucketRepository#insert(...)`는 `ApplicationCatalogRepository#getOrCreate(...)`와 `accepted_metric_buckets.saveAndFlush(...)`를 수행한다. SQS mode request path에서는 이 repository path를 호출하면 안 된다.
- `ValidatedIngestCandidate`는 raw project key 없이 `VerifiedProject`, validated idempotency key, `IngestEnvelopeRequest`를 보관한다. queue message build input으로 재사용한다.
- `IngestPayloadHasher`는 validated `IngestEnvelopeRequest`를 portal `ObjectMapper`로 직렬화한 뒤 SHA-256 hex를 계산한다. unknown field는 request model에 남지 않으므로 hash에 반영되지 않는다.
- `PortalJsonConfiguration`은 `JavaTimeModule`과 ISO timestamp 직렬화를 사용하는 기본 `ObjectMapper`를 제공한다. queue message canonicalization 때문에 전역 mapper 설정을 무리하게 바꾸지 않는다.
- 현재 `IngestControllerTest`는 first success `201`, invalid payload `400`, invalid project key `401`, duplicate key `409`를 검증한다.
- 현재 `IngestAcceptanceServiceTest`는 invalid project key가 validation/hash/insert를 건너뛰고, invalid payload가 insert로 가지 않으며, duplicate pre-read가 hash/insert를 건너뛰는 direct-mode MVP 정책을 검증한다.

## 목표

- 기존 HTTP ingest acceptance를 validation, payload hash, queue message 생성, enqueue boundary로 분리한다.
- Enqueue 성공이 DB insert 완료나 dashboard freshness current를 뜻하지 않는 API 의미를 고정한다.
- SQS message body/attribute가 raw secret과 unsupported semantic field를 포함하지 않게 한다.
- SQS payload size limit 초과 envelope를 worker로 보내기 전에 request path에서 닫는다.
- Direct mode rollback path를 보존하고 SQS mode/fake queue mode를 opt-in으로 구현한다.

## Scope / Non-Scope

포함:

- 기존 `POST /api/ingest/v1/buckets` endpoint의 mode별 response 분기.
- SQS/fake enqueue mode의 validation -> payload hash -> message build -> size guard -> publisher enqueue request path.
- Queue publisher interface, fake publisher, SQS publisher, enqueue receipt model, queue message DTO/record 후보.
- `202 queued`, `413 payload_too_large`, `503 ingest_enqueue_unavailable` response mapping.
- SQS mode에서 repository insert/catalog get-or-create/snapshot capture가 호출되지 않는 guard tests.
- Queue message golden JSON, payload hash, message attributes, redaction, size guard tests.

제외:

- Starter가 AWS credential로 SQS에 직접 쓰는 모델.
- SQS worker poll/process/delete 구현.
- Worker batch accumulation, visibility timeout 적용, DLQ send/delete action 구현.
- Batch writer 최적화나 DB throughput 개선.
- Queue replay UI, DLQ 조사 UI, backfill/replay pipeline.
- Lifecycle state, p95/p99, endpoint priority, dashboard snapshot/history, operational event 계산 변경.
- Lambda publisher/consumer, Lambda handler scaffold, Lambda event source mapping.
- Direct fallback 구현, mandatory shadow mode 구현.
- AWS 리소스 생성, IAM/VPC/RDS 연결 설계, production autoscaling.

## 구현 전 결정

| 항목 | 결정 | 구현 제약 |
| --- | --- | --- |
| Endpoint | 기존 `POST /api/ingest/v1/buckets`를 유지하고 config mode에 따라 direct 또는 enqueue response로 분기한다. enqueue 전용 endpoint는 만들지 않는다. | starter compatibility와 direct rollback을 보존한다. `/api/ingest/v1/buckets:enqueue` 같은 새 route를 추가하지 않는다. |
| SQS mode success response | `202 Accepted`와 queued body를 반환한다. | `Location` header, `bucketId`, `acceptedAt`, `duplicate`는 queued response에 넣지 않는다. |
| `202 queued` body | `status="queued"`, `queued=true`, `persisted=false`, `idempotencyKey`, `messageVersion`, `receivedAt`, `enqueuedAt`을 반환한다. | `persisted=false`를 반드시 포함해 DB 저장 완료가 아님을 명시한다. |
| SQS `messageId` 노출 | response body에 노출하지 않는다. | AWS/fake publisher receipt의 message id는 내부 diagnostic/log/metric 후보로만 남긴다. starter retry/correlation 계약에 넣지 않는다. |
| Message version token | body와 message attribute 모두 `messageVersion="1"`을 사용한다. | worker는 Story 12.3에서 `1`만 지원한다. unsupported version은 malformed/DLQ 대상이다. |
| Queue message JSON canonicalization | dedicated immutable DTO/record를 portal `ObjectMapper`로 compact UTF-8 JSON 직렬화한다. 필드 순서는 record component 선언 순서와 golden JSON test로 고정한다. | semantic body에는 `Map`/dynamic key를 쓰지 않는다. 전역 `ObjectMapper` property sorting 변경으로 다른 JSON persistence/hash를 흔들지 않는다. |
| Payload hash | 기존 `IngestPayloadHasher#sha256(IngestEnvelopeRequest)` 결과를 queue message의 `payloadHash`로 사용한다. | queue message 전체 body 기준으로 재계산하지 않는다. `receivedAt`/`enqueuedAt` 같은 enqueue metadata가 hash를 흔들면 worker 재검증이 불가능해진다. |
| Payload size error code | HTTP status는 `413 Payload Too Large`, response `error`는 `payload_too_large`로 한다. | body/log에는 raw payload를 넣지 않는다. 같은 payload retry는 성공하지 않는 입력으로 취급한다. |
| Enqueue/config/serialization failure code | HTTP status는 `503 Service Unavailable`, response `error`는 `ingest_enqueue_unavailable`로 한다. | queue URL, AWS exception detail, raw payload, project key, token을 response/log에 노출하지 않는다. |
| Mode config | `portal.ingest.buffer.mode=direct`를 global default로 둔다. allowed values는 `direct`, `fake`, `sqs`다. | `sqs`는 opt-in이다. queue path local/test는 `fake` mode로 SQS 없이 검증한다. |
| Publisher properties | Story 12.2 publisher에 필요한 property는 `portal.ingest.buffer.mode`, `portal.ingest.buffer.message-size-limit-bytes`, `portal.ingest.buffer.publisher-timeout`, `portal.ingest.buffer.sqs.queue-url`, `portal.ingest.buffer.sqs.endpoint-override`다. | `endpoint-override`는 LocalStack opt-in 전용이다. DLQ URL, worker batch size, visibility timeout, max receive count는 Story 12.3 범위다. |

## API Contract

### Direct Mode

`portal.ingest.buffer.mode=direct`는 현재 direct insert path를 보존한다.

- New accepted bucket은 기존처럼 `201 Created`와 `IngestAcceptedResponse`를 반환한다.
- 현재 active MVP duplicate policy는 Story 3.4 완료 기록 기준으로 duplicate key를 `409 duplicate_idempotency_key`로 reject한다. `api-surface.md`에는 `200 duplicate=true` full idempotency 후보가 남아 있으므로, 구현자는 12.2에서 direct duplicate policy를 새로 바꾸지 않는다.
- Direct mode regression은 현재 코드 baseline의 `201/400/401/409`를 반드시 유지하고, 기존 test suite 또는 contract fixture에 `200 duplicate same payload`가 존재한다면 그 semantics도 깨지지 않게 한다. 신규 `200 duplicate success` 구현은 Story 12.2 범위가 아니다.

### Fake / SQS Enqueue Mode

`portal.ingest.buffer.mode=fake`와 `portal.ingest.buffer.mode=sqs`는 같은 API semantics를 가진다.

성공:

```http
202 Accepted
Content-Type: application/json
```

```json
{
  "status": "queued",
  "queued": true,
  "persisted": false,
  "idempotencyKey": "project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z",
  "messageVersion": "1",
  "receivedAt": "2026-05-08T01:00:31Z",
  "enqueuedAt": "2026-05-08T01:00:31.120Z"
}
```

Failure mapping:

| Condition | Status | Error code | Notes |
| --- | --- | --- | --- |
| project key 누락/검증 실패 | `401 Unauthorized` | `unauthorized` | direct mode와 동일하게 세부 project/key 상태를 노출하지 않는다. |
| envelope/idempotency validation 실패 | `400 Bad Request` | `invalid_request` | 기존 validation error shape를 유지한다. |
| queue message serialized size 초과 | `413 Payload Too Large` | `payload_too_large` | enqueue 전에 reject한다. direct fallback 금지. |
| SQS unavailable/timeout | `503 Service Unavailable` | `ingest_enqueue_unavailable` | starter retry 가능한 infrastructure failure다. |
| queue URL/config missing 또는 mode mismatch | `503 Service Unavailable` | `ingest_enqueue_unavailable` | `202` 금지. |
| serialization/message build failure | `503 Service Unavailable` | `ingest_enqueue_unavailable` | raw payload를 log/error에 남기지 않는다. |

## SQS Message Contract

Queue message body는 `messageVersion=1`에서 아래 shape를 사용한다. 구현 중 field name을 바꾸면 golden JSON과 worker handoff도 함께 갱신해야 한다. 아래 예시는 supported payload field를 줄인 유효 예시이며, 실제 golden fixture는 `PortalIngestValidationFixture`의 full payload를 기준으로 만든다.

```json
{
  "messageVersion": "1",
  "projectId": "00000000-0000-0000-0000-000000003201",
  "projectName": "checkout",
  "idempotencyKey": "project-123:orders-api:prod:orders-api-7f9c9c8c9d-x2p4k:20260508T010000Z",
  "payloadHash": "<sha256-hex-from-IngestPayloadHasher>",
  "receivedAt": "2026-05-08T01:00:31Z",
  "enqueuedAt": "2026-05-08T01:00:31.120Z",
  "payload": {
    "schemaVersion": "1.0",
    "application": {
      "name": "orders-api",
      "environment": "prod",
      "instance": "orders-api-7f9c9c8c9d-x2p4k"
    },
    "bucket": {
      "startUtc": "2026-05-08T01:00:00Z",
      "endUtc": "2026-05-08T01:00:30Z",
      "durationSeconds": 30
    },
    "summary": {
      "requestCount": 3,
      "errorCount": 1,
      "httpServerDurationBuckets": [
        {
          "leMs": 50,
          "count": 1
        },
        {
          "leMs": 100,
          "count": 3
        }
      ],
      "jvm": {
        "cpuUsage": 0.64,
        "heapUsedRatio": 0.71
      },
      "datasource": {
        "poolUsageRatio": 0.82
      },
      "localPercentiles": null
    },
    "endpoints": [
      {
        "method": "GET",
        "route": "/orders/{orderId}",
        "requestCount": 3,
        "errorCount": 1,
        "durationBuckets": [
          {
            "leMs": 50,
            "count": 1
          },
          {
            "leMs": 100,
            "count": 3
          }
        ]
      }
    ]
  }
}
```

Message body rules:

- `payload`는 validation을 통과한 `IngestEnvelopeRequest` supported field만 포함한다.
- Jackson unknown field는 기존 request model에서 이미 무시되므로 queue body, payload hash, persistence 후보, worker identity에 반영하지 않는다.
- body에는 raw project key, starter credential, Authorization token, Discord webhook URL, raw path/query/trace/per-request sample을 넣지 않는다.
- `projectName`은 현재 `AcceptedMetricBucketWriteCommand` 변환 재사용을 위한 verified project metadata다. raw credential이나 hash가 아니다.
- `receivedAt`은 request가 SQS mode 검증 경계에 들어온 UTC 시각, `enqueuedAt`은 publisher enqueue 호출 직전 또는 enqueue 성공 직후 UTC 시각으로 둔다. 한 구현 안에서 일관되게 쓰고 response와 message가 같은 값을 공유해야 한다.
- Worker는 Story 12.3에서 `messageVersion`, `payloadHash`, `payload.schemaVersion`, `payload.bucket.*`, `payload.application.*`, `idempotencyKey`를 재검증할 수 있어야 한다.

Message attributes:

| Attribute | Type | Source | 용도 |
| --- | --- | --- | --- |
| `messageVersion` | String | `"1"` | worker contract version guard |
| `projectId` | String | verified project UUID | project reference |
| `schemaVersion` | String | `payload.schemaVersion` | ingest envelope schema guard |
| `bucketStartUtc` | String | `payload.bucket.startUtc` | bucket boundary 재검증 |
| `bucketEndUtc` | String | `payload.bucket.endUtc` | bucket boundary 재검증 |
| `applicationName` | String | `payload.application.name` | sanitized application identity |
| `environment` | String | `payload.application.environment` | sanitized environment identity |
| `instanceName` | String | `payload.application.instance` | sanitized instance identity |

Attribute rules:

- Story 12.2는 8개 attributes만 사용한다. SQS quota상 10개까지 가능하지만 여유 2개는 후속 확장용으로 남긴다.
- Attribute에는 raw project key, token, webhook URL, raw route/query/trace/per-request sample을 넣지 않는다.
- Attribute source는 이미 validation을 통과한 field만 사용한다.

## SQS Resource Spec

이 spec은 Story 12.2 publisher와 Story 12.3 worker 구현이 공유할 resource option spec이다. Story 12.2는 실제 AWS 리소스를 만들지 않는다.

| 항목 | 값 | Story 12.2 적용 |
| --- | --- | --- |
| Main queue type | SQS Standard queue | `sqs` publisher가 `SendMessage` 대상 queue로 사용한다. |
| Main queue name 후보 | `observation-metric-ingest-{env}` | property는 `queue-url`을 우선 사용한다. name은 IaC/runbook 후보다. |
| DLQ type | SQS Standard queue | Story 12.2는 DLQ send/receive를 구현하지 않는다. |
| DLQ name 후보 | `observation-metric-ingest-dlq-{env}` | Story 12.3 redrive spec에서 사용한다. |
| Long polling | `ReceiveMessageWaitTimeSeconds=20` | publisher에는 직접 필요 없다. Story 12.3 worker receive에서 사용한다. |
| Visibility timeout | `180s` 후보 | exact 기본값은 Story 12.3에서 확정한다. |
| `maxReceiveCount` | `5` 후보 | exact 기본값은 Story 12.3에서 확정한다. |
| Message retention | AWS 기본 4일 유지 후보 | Story 12.2 publisher에는 직접 필요 없다. |
| `DelaySeconds` | `0` | `SendMessage`에서 별도 delay를 주지 않는다. |
| Message size guard | app-level configurable, 기본 `1048576` | body+attribute serialized size가 limit을 넘으면 enqueue 전 `413`으로 reject한다. |
| Encryption | SSE-SQS 기본 후보, KMS opt-in | publisher code가 encryption 설정을 만들지 않는다. queue resource/IaC 범위다. |
| IAM publisher | `sqs:SendMessage`, `sqs:GetQueueAttributes` | `GetQueueAttributes`는 size/health diagnostic 후보이며 필수 runtime call로 강제하지 않는다. |
| Local/test 기본 | fake queue publisher | queue-mode unit/controller/service flow는 SQS 없이 검증한다. |
| LocalStack | opt-in integration | `portal.ingest.buffer.sqs.endpoint-override`와 별도 profile/test로만 사용한다. |

Config properties:

```properties
portal.ingest.buffer.mode=direct
portal.ingest.buffer.message-size-limit-bytes=1048576
portal.ingest.buffer.publisher-timeout=3s
portal.ingest.buffer.sqs.queue-url=
portal.ingest.buffer.sqs.endpoint-override=
```

Implementation notes:

- `portal.ingest.buffer.mode=fake`는 enqueue semantics와 fake publisher를 사용한다. DB insert를 수행하지 않고 `202 queued`로 수렴한다.
- `portal.ingest.buffer.mode=sqs`는 `queue-url`이 비어 있으면 `503 ingest_enqueue_unavailable`로 fail closed한다.
- `portal.ingest.buffer.mode=direct`는 publisher bean을 호출하지 않고 기존 direct acceptance path를 사용한다.
- `dlq-url`, `worker-enabled`, `batch-size`, `max-batch-age`, `long-poll-seconds`, `visibility-timeout`, `max-receive-count`는 Story 12.3 worker 구현에서 확정한다.

## Acceptance Criteria

1. Story 12.2는 Java/Spring production code를 지금 수정하는 산출물이 아니라, 이 story 파일과 sprint tracking만 생성된 상태에서 `ready-for-dev`가 된다.
2. 구현은 Traditional MVC + Service/Repository Layering과 feature-first package 구조를 따른다.
3. `application`, `port`, `adapter` package를 새로 만들지 않는다.
4. 기존 `POST /api/ingest/v1/buckets` endpoint를 유지하고 enqueue 전용 endpoint를 만들지 않는다.
5. Config `portal.ingest.buffer.mode`는 `direct`, `fake`, `sqs`만 허용한다.
6. Global default mode는 `direct`다. `sqs` mode는 opt-in이다.
7. Queue-mode local/test path는 fake publisher로 SQS 없이 검증할 수 있다.
8. SQS mode request path는 project key verification, envelope validation, idempotency key format validation, payload hash 계산, size guard, queue message 생성, enqueue까지만 수행한다.
9. SQS mode request path는 `accepted_metric_buckets` insert를 수행하지 않는다.
10. SQS mode request path는 `ApplicationCatalogRepository#getOrCreate(...)` 또는 application/instance catalog upsert를 수행하지 않는다.
11. SQS mode request path는 dashboard snapshot capture, dashboard read model refresh, operational event creation을 수행하지 않는다.
12. SQS mode request path는 repository idempotency lookup으로 duplicate/conflict 최종 판정을 하지 않는다. queue 안에만 있는 duplicate를 request path에서 알 수 없기 때문이다.
13. SQS mode validation은 현재 `IngestAcceptanceService`의 schema version, UTC 30초 bucket boundary, application identity, summary/endpoints, localPercentiles, idempotency key consistency rule을 재사용하거나 behavior-compatible하게 추출한다.
14. Invalid project key는 validation/hash/message build/publisher 호출 없이 `401 Unauthorized`로 끝난다.
15. Invalid payload 또는 invalid idempotency key는 hash/message build/publisher 호출 없이 `400 Bad Request`와 `invalid_request`로 끝난다.
16. Payload hash는 기존 `IngestPayloadHasher` 결과를 사용한다.
17. Queue message body의 `payloadHash`는 `payload`만 기준으로 한 hash이며 queue metadata를 포함하지 않는다.
18. Queue message body와 attributes는 `messageVersion="1"`을 포함한다.
19. Queue message body는 verified project reference, application identity, bucket boundary, idempotency key, payload hash, `receivedAt`, `enqueuedAt`, supported ingest payload만 포함한다.
20. Queue message attributes는 `messageVersion`, `projectId`, `schemaVersion`, `bucketStartUtc`, `bucketEndUtc`, `applicationName`, `environment`, `instanceName`만 포함한다.
21. Queue message body/attribute/log/error response에는 raw project key, starter credential, Authorization token, Discord webhook URL, raw path/query/trace/per-request sample을 포함하지 않는다.
22. Unsupported unknown field는 queue message, payload hash, idempotency identity, persistence candidate에 반영되지 않는다.
23. Serialized queue message body와 attributes의 app-level size guard 기본값은 `1048576` bytes다.
24. Size guard는 publisher enqueue 호출 전에 수행한다.
25. Size limit 초과 request는 enqueue하지 않고 `413 Payload Too Large`와 `payload_too_large` response로 reject한다.
26. Size limit 초과 request는 direct fallback으로 우회하지 않는다.
27. Enqueue 성공 후에만 `202 Accepted`와 queued response body를 반환한다.
28. Queued response body는 `status="queued"`, `queued=true`, `persisted=false`, `idempotencyKey`, `messageVersion`, `receivedAt`, `enqueuedAt`을 포함한다.
29. Queued response body는 SQS `messageId`, `bucketId`, `acceptedAt`, `duplicate`를 포함하지 않는다.
30. SQS unavailable, timeout, config missing, serialization failure, message build failure는 `202`를 반환하지 않고 `503 Service Unavailable`과 `ingest_enqueue_unavailable`로 끝난다.
31. Enqueue failure response와 log는 queue URL, raw payload, project key, credential, Authorization token, webhook URL을 노출하지 않는다.
32. Direct mode는 기존 direct insert path와 response semantics를 보존한다.
33. Direct mode regression tests는 현재 code baseline의 `201/400/401/409`를 유지하고, 기존 suite나 contract fixture에 `200 duplicate same payload`가 있다면 그 `201/200/409` direct semantics도 깨지지 않게 한다.
34. Fake queue publisher는 enqueue receipt를 반환하고, test가 enqueued message body/attributes를 inspect할 수 있게 한다.
35. SQS publisher는 AWS SDK v2 SQS `SendMessage`를 사용한다. dependency 추가가 필요하면 `observability-portal/build.gradle`에 명시 버전/BOM-compatible dependency로 추가한다.
36. `sqs` mode에서 `queue-url`이 없거나 blank이면 publisher를 호출하지 않고 `503 ingest_enqueue_unavailable`로 fail closed한다.
37. LocalStack integration은 opt-in profile/test로만 둔다. 기본 unit/integration test는 LocalStack을 요구하지 않는다.
38. Queue message golden JSON test는 golden fixture와 exact serialized JSON을 비교한다.
39. Payload hash test는 worker가 Story 12.3에서 같은 `IngestPayloadHasher`로 hash를 재계산할 수 있음을 보장한다.
40. Message contract는 Story 12.3 worker가 message version, payload hash, bucket boundary, schemaVersion, idempotency key를 재검증할 수 있을 만큼 충분한 정보를 남긴다.

## Tasks / Subtasks

- [x] Pre-flight와 mode boundary 확인 (AC: 2~7, 32~33)
  - [x] `git status --short`로 기존 modified/untracked 상태를 기록한다.
  - [x] 현재 `IngestController`, `IngestAcceptanceService`, `IngestPayloadHasher`, `ValidatedIngestCandidate`, controller/service tests를 다시 읽는다.
  - [x] direct mode default와 rollback path를 보존하는 config shape를 구현한다.
  - [x] direct mode의 현재 duplicate MVP policy를 12.2에서 바꾸지 않는다.

- [x] Validation/direct/enqueue orchestration 분리 (AC: 8~17)
  - [x] 기존 `IngestAcceptanceService`의 validation logic을 behavior-compatible하게 재사용 또는 추출한다.
  - [x] direct mode는 기존 repository insert path를 호출한다.
  - [x] fake/sqs mode는 repository idempotency lookup, catalog upsert, bucket insert를 호출하지 않는다.
  - [x] SQS mode에서 invalid project key/payload/idempotency key가 hash/message build/publisher로 넘어가지 않는지 guard한다.

- [x] Queue message contract 구현 (AC: 18~22, 28~29, 38~40)
  - [x] `QueuedMetricBucketMessage` 또는 동등 immutable DTO/record를 만든다.
  - [x] `MetricIngestEnqueueReceipt` 또는 동등 receipt를 만든다.
  - [x] `messageVersion="1"`, project metadata, idempotency key, payload hash, timestamps, payload를 body에 담는다.
  - [x] Message attribute builder를 allow-list로 구현한다.
  - [x] Golden JSON fixture를 추가한다.

- [x] Payload size guard와 redaction guard 구현 (AC: 21~26, 31)
  - [x] Compact UTF-8 body bytes와 attribute name/type/value bytes를 포함하는 conservative size estimator를 구현한다.
  - [x] `portal.ingest.buffer.message-size-limit-bytes` 기본값 `1048576`을 적용한다.
  - [x] 초과 시 publisher 호출 전 `413 payload_too_large`로 응답한다.
  - [x] error response/log/toString에 secret/raw payload가 남지 않는지 테스트한다.

- [x] Publisher 구현 (AC: 27, 30, 34~37)
  - [x] `MetricIngestQueuePublisher` interface를 feature-first ingest package 안에 둔다.
  - [x] Fake publisher는 test가 message를 검증할 수 있게 in-memory receipt/message capture를 제공한다.
  - [x] SQS publisher는 AWS SDK v2 `SendMessage`를 사용하고 config/timeout/failure를 sanitized `503` path로 매핑한다.
  - [x] LocalStack은 opt-in integration으로만 둔다.

- [x] Controller/DTO response mapping 구현 (AC: 27~33)
  - [x] `202 queued` response DTO를 추가한다.
  - [x] `IngestErrorResponse`에 `payload_too_large`, `ingest_enqueue_unavailable` factory를 추가한다.
  - [x] Controller가 direct accepted result와 queued result를 명확히 분리한다.
  - [x] queued response에는 `Location`, `bucketId`, `acceptedAt`, `messageId`가 없음을 검증한다.

- [x] Verification 수행 (AC: 8~40)
  - [x] focused controller/service/message contract tests를 실행한다.
  - [x] direct mode regression tests를 실행한다.
  - [x] full portal test 또는 영향 범위에 맞는 Gradle test를 실행한다.
  - [x] `git diff --check`를 실행한다.

## 테스트 / 검증 방법

필수 focused tests:

- Controller test: `mode=fake` 또는 SQS-mode service result에서 enqueue 성공 후에만 `202 Accepted`와 queued body를 반환한다.
- Controller test: queued response body는 `status`, `queued`, `persisted=false`, `idempotencyKey`, `messageVersion`, `receivedAt`, `enqueuedAt`만 포함하고 `messageId`, `bucketId`, `acceptedAt`, `duplicate`를 포함하지 않는다.
- Controller test: invalid payload `400`, invalid project key `401`, payload too large `413`, enqueue/config/serialization failure `503` mapping을 확인한다.
- Service test: SQS/fake mode request path는 project key verification, envelope validation, idempotency key format validation, payload hash 계산, size guard, message build, enqueue까지만 수행한다.
- Service test: SQS/fake mode에서 `MetricBucketRepository.findByProjectIdAndIdempotencyKey`, `MetricBucketRepository.insert`, catalog get-or-create, snapshot capture dependency가 호출되지 않는지 mock/constructor boundary로 확인한다.
- Direct mode regression: 현재 direct baseline의 `201 Created`, `400`, `401`, `409 duplicate_idempotency_key`가 유지되는지 확인한다. 기존 suite나 contract fixture에 `200 duplicate same payload`가 있으면 `201/200/409` contract도 함께 보존한다.
- Fake queue publisher test: SQS 없이 controller/service enqueue flow와 enqueued message capture를 검증한다.
- Message contract golden JSON test: golden ingest fixture가 expected queue message JSON으로 직렬화되는지 확인한다.
- Message attribute test: 8개 allow-list attribute만 생성되고 SQS attribute quota 여유가 남는지 확인한다.
- Payload hash test: queue message의 `payloadHash`가 기존 `IngestPayloadHasher` 결과와 같고 queue metadata 변경으로 바뀌지 않는지 확인한다.
- Payload size test: configured limit 초과 body+attribute가 publisher 호출 전에 `413 payload_too_large`로 reject되는지 확인한다.
- Redaction test: queue message/log/error response에 raw project key, starter credential, Authorization token, Discord webhook URL, raw path/query/trace/per-request sample이 없는지 확인한다.
- Serialization/config failure test: message build failure, queue URL missing, SQS publisher failure가 `503 ingest_enqueue_unavailable`로 수렴하고 `202`를 반환하지 않는지 확인한다.
- Worker handoff contract test: Story 12.3 worker가 message version, payload hash, bucket boundary, schemaVersion, idempotency key를 재검증할 수 있는 field가 모두 있는지 확인한다.

권장 실행 명령:

```bash
./gradlew :observability-portal:test --tests '*IngestControllerTest'
./gradlew :observability-portal:test --tests '*IngestAcceptanceServiceTest'
./gradlew :observability-portal:test --tests '*IngestPayloadHasherTest'
./gradlew :observability-portal:test --tests '*MetricIngest*'
./gradlew :observability-portal:test
git diff --check
```

## 참고해야 할 코드/문서

문서:

- `planning-artifacts/stories/12-1-architecture-and-contract-decision.md`
- `planning-artifacts/stories/12-2-ingest-enqueue-boundary.md`
- `planning-artifacts/infrastructure-input-notes.md`
- `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
- `planning-artifacts/epic-11-12-operational-alerts-and-sqs-batch-ingest-plan.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/api-surface.md`
- `implementation-artifacts/sprint-status.yaml`
- `_bmad/custom/project-context.md`

코드:

- `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestAcceptedResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestErrorResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/model/IngestEnvelopeRequest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ValidatedIngestCandidate.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestPayloadHasher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/VerifiedProject.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommand.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestAcceptanceServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/PortalIngestValidationFixture.java`

## 위험과 완화책

| 위험 | 영향 | 완화책 |
| --- | --- | --- |
| `202 queued`가 저장 완료로 오해됨 | dashboard freshness 기대가 틀어짐 | queued response에 `persisted=false`를 넣고 `bucketId`/`acceptedAt`/`Location`을 제외한다. |
| SQS mode에서 repository lookup/insert가 남음 | request latency 개선이 사라지고 duplicate 의미가 queue와 충돌함 | SQS/fake mode service test에서 repository/catalog/snapshot dependency 호출 금지를 검증한다. |
| Payload size 초과가 worker poison message가 됨 | 반복 retry와 DLQ noise 증가 | enqueue 전에 body+attribute size guard로 `413` reject한다. |
| Raw secret이 queue/log/error에 남음 | 보안 사고 | message body/attribute allow-list와 redaction tests를 둔다. |
| Direct mode와 SQS mode status가 혼재됨 | starter retry behavior 혼란 | mode별 result/DTO/status mapping을 분리하고 direct regression tests를 유지한다. |
| SQS `messageId`가 public API contract가 됨 | AWS/fake implementation detail이 starter contract로 고정됨 | message id는 internal receipt/log/metric 후보로만 두고 response에 노출하지 않는다. |
| Payload hash를 queue body 전체 기준으로 계산함 | `enqueuedAt` 같은 metadata 때문에 worker 재검증이 흔들림 | 기존 `IngestPayloadHasher` 결과만 `payloadHash`로 사용한다. |
| LocalStack이 기본 테스트 의존성이 됨 | local/CI 복잡도 증가 | fake publisher를 기본 queue-mode test adapter로 두고 LocalStack은 opt-in integration으로 제한한다. |
| Story 12.3 worker MVP만 먼저 노출됨 | queue lag 때문에 dashboard state/copy가 흔들림 | 이 story는 rollout guard를 유지하고 Story 12.4 lag semantics 전 user-facing rollout을 금지한다. |

## Dev Agent Handoff Notes

1. 먼저 `IngestAcceptanceService`의 validation logic을 behavior-compatible하게 추출하거나 재사용하고, direct mode facade와 enqueue mode flow를 분리한다.
2. `portal.ingest.buffer.mode` properties를 추가하고 default `direct`, queue-test default `fake`, real SQS opt-in `sqs`를 구현한다.
3. `QueuedMetricBucketMessage`, message attributes builder, enqueue receipt, `MetricIngestQueuePublisher` interface, fake publisher를 만든 뒤 golden JSON/redaction/size tests부터 작성한다.
4. 기존 `IngestPayloadHasher` 결과를 queue message `payloadHash`로 넣고, queue message 전체 body hash를 새로 만들지 않는다.
5. SQS/fake mode는 repository idempotency lookup, `accepted_metric_buckets` insert, catalog get-or-create, snapshot capture를 호출하지 않게 service tests로 잠근다.
6. Controller는 direct success와 queued success를 다른 DTO/status로 매핑하고, `413 payload_too_large`, `503 ingest_enqueue_unavailable` error factory를 추가한다.
7. SQS publisher는 마지막에 붙인다. `queue-url` missing/config/timeout/serialization failure는 sanitized `503`으로 수렴시키고, LocalStack은 opt-in test로만 남긴다.
8. focused ingest tests, direct regression tests, full portal tests, `git diff --check`를 실행한 뒤 Story 12.3 worker가 쓸 message contract를 handoff notes에 남긴다.

## Dev Agent Record

### Implementation Plan

- 기존 `POST /api/ingest/v1/buckets` controller endpoint를 유지하고, `IngestAcceptanceService`에서 `direct`/`fake`/`sqs` mode를 분기한다.
- direct mode는 기존 repository duplicate lookup과 insert path를 보존하고, queue mode는 validation 이후 message build, size guard, publisher enqueue까지만 수행한다.
- queue body/attribute는 allow-list DTO와 focused contract tests로 고정하고, public response에는 `messageId`, `bucketId`, `acceptedAt`, `duplicate`를 노출하지 않는다.

### Debug Log

- 2026-06-05: `git status --short` 확인 시 README, infrastructure notes, Story 12.2, sprint-status 등 기존 dirty 파일과 미추적 복제 파일이 있었다. 관련 없는 변경은 유지했다.
- 2026-06-05: RED 단계에서 신규 queue/controller/service/SQS publisher tests가 missing production class와 AWS SDK dependency로 compile fail함을 확인했다.
- 2026-06-05: Gradle build output에 `* 2.class` 복제 산출물이 남아 test discovery를 방해해 `observability-portal/build`만 정리한 뒤 재실행했다.
- 2026-06-05: 기존 Story 5.8 architecture guard의 전역 `sqs` 금지를 Epic 12 ingest queue package 예외로 좁혀 Story 12.2 범위와 맞췄다.

### Completion Notes

- `portal.ingest.buffer.mode=direct`를 default로 추가하고, `fake`/`sqs` queue mode를 opt-in으로 구현했다.
- fake/sqs mode는 project key verification, envelope/idempotency validation, payload hash, queue message build, size guard, enqueue까지만 수행하며 repository duplicate lookup/insert/catalog path를 호출하지 않는다.
- queue message body는 `messageVersion=1`, verified project reference, idempotency key, payload hash, `receivedAt`, `enqueuedAt`, supported ingest payload만 담고, attributes는 8개 allow-list로 제한했다.
- payload size 초과는 publisher 호출 전 `413 payload_too_large`, queue config/message build/publisher failure는 sanitized `503 ingest_enqueue_unavailable`로 매핑했다.
- SQS publisher는 AWS SDK v2 `SendMessage`를 사용하며, AWS client build를 enqueue 시점으로 늦춰 queue config failure가 startup failure가 아니라 503 path로 수렴하게 했다.
- Review hardening으로 SQS publisher client를 lazy single instance로 재사용하고 shutdown close를 보장했으며, app-level message size limit이 SQS hard limit을 넘지 않게 막았다.
- Story 12.3 worker handoff: worker는 message body의 `messageVersion`, `payloadHash`, `payload.schemaVersion`, `payload.bucket.*`, `payload.application.*`, `idempotencyKey`와 attributes의 schema/bucket/application identity를 재검증하면 된다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/12-2-ingest-enqueue-boundary.md`
- `observability-portal/build.gradle`
- `observability-portal/src/main/resources/application.properties`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestErrorResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestQueuedResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/FakeMetricIngestQueuePublisher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/IngestBufferMode.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/IngestBufferProperties.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestEnqueueReceipt.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestMessageAttribute.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestMessageBuildException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueMessage.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueMessageFactory.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueuePublishException.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/MetricIngestQueuePublisher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/QueuedMetricBucketMessage.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/queue/SqsMetricIngestQueuePublisher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceResult.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestQueuedResult.java`
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/IngestBufferPropertiesTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/MetricIngestQueueMessageContractTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/queue/SqsMetricIngestQueuePublisherTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestAcceptanceServiceTest.java`

### Change Log

- 2026-06-05: Story 12.2 ingest enqueue boundary 구현 및 focused/full verification 완료. Status를 `review`로 변경.
- 2026-06-05: SQS publisher lifecycle과 SQS hard size limit review finding을 반영하고 full verification 후 Status를 `done`으로 변경.
