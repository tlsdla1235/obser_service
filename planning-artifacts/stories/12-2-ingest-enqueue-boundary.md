---
artifactType: story
storyId: "12.2"
storyKey: "12-2-ingest-enqueue-boundary"
epic: "Epic 12. SQS Buffered Ingest Transition"
title: "Ingest Enqueue Boundary"
architectureStyle: Traditional MVC
status: backlog
date: 2026-06-05
commitBoundary: "feat: add sqs ingest enqueue boundary"
---

# Story 12.2 - Ingest Enqueue Boundary

## Status

backlog

## Story

구현자로서, portal HTTP ingest endpoint가 SQS mode에서 DB insert까지 수행하지 않고 검증된 ingest candidate를 queue message로 enqueue하길 원한다.

그래야 starter의 HTTP retry 흐름은 유지하면서도 request thread가 bucket persistence round trip에 묶이지 않는다.

## 목표

- 기존 HTTP ingest acceptance를 validation, payload hash, queue message 생성, enqueue boundary로 분리한다.
- Enqueue 성공이 DB insert 완료나 dashboard freshness current를 뜻하지 않는 API 의미를 고정한다.
- SQS message body/attribute가 raw secret과 unsupported semantic field를 포함하지 않게 한다.
- SQS payload size limit 초과 envelope를 worker로 보내기 전에 request path에서 닫는다.

## Acceptance Criteria

1. SQS mode request path는 project key verification, envelope validation, idempotency key format validation, payload hash 계산, enqueue까지만 수행한다.
2. SQS mode request path는 `accepted_metric_buckets` insert, catalog get-or-create, dashboard snapshot capture를 수행하지 않는다.
3. Queue message는 message version, verified project reference, application identity, bucket boundary, idempotency key, payload hash, receivedAt, enqueuedAt 후보를 포함한다.
4. Queue message body/attribute/log에는 raw project key, starter credential, Authorization token, Discord webhook URL, raw path/query/trace/per-request sample을 포함하지 않는다.
5. Message body는 `ingest-envelope`가 지원하는 field만 semantic source로 사용하고 unknown/unsupported field는 저장/aggregation/input identity에 반영하지 않는다.
6. SQS payload size limit 초과 envelope는 Story 12.1 결정에 따라 enqueue 전에 `413 Payload Too Large`로 reject하며 direct fallback을 열지 않는다.
7. Enqueue 성공 response는 실제 queue enqueue 성공 이후에만 `202 queued`를 반환하며, `201 Created`처럼 DB row 생성 완료로 읽히지 않게 한다.
8. Enqueue 실패나 queue configuration failure는 starter retry가 가능한 non-2xx status로 매핑한다. 기본 status는 `503 Service Unavailable`이다.
9. Direct mode가 유지된다면 기존 direct path의 `201/200/409` semantics는 SQS mode response와 명확히 분리된다.
10. Enqueue success/failure log와 error response는 payload 원문이나 secret-bearing field를 출력하지 않는다.
11. Fake queue publisher를 통해 SQS 없이 controller/service enqueue flow를 검증할 수 있다.
12. Story 12.3 worker가 message version, payload hash, bucket boundary를 재검증할 수 있도록 message contract가 충분한 정보를 제공한다.

## Non-Goals

- Starter가 AWS credential로 SQS에 직접 쓰는 모델.
- SQS worker poll/process/delete 구현.
- Batch writer 최적화나 DB throughput 개선.
- Queue replay UI, DLQ 조사 UI.
- Lifecycle state, p95/p99, endpoint priority, operational event 계산.
- Lambda publisher/consumer 또는 Lambda handler.

## 구현 전 닫아야 할 결정

- Existing `POST /api/ingest/v1/buckets`를 mode별로 `202 queued`로 바꿀지, enqueue 전용 endpoint를 둘지.
- SQS mode response body field: `queued`, `receivedAt`, `idempotencyKey`, `messageId`, `persisted=false` 후보 중 무엇을 노출할지.
- Message schema version token과 serialized JSON canonicalization 기준.
- Payload hash를 기존 `IngestPayloadHasher` 결과 그대로 사용할지, queue message canonical body 기준으로 재계산할지.
- Payload size 초과의 error body code. HTTP status는 Story 12.1 결정에 따라 `413`으로 고정한다.
- Direct mode, fake queue mode, SQS mode를 나누는 config 이름과 default.

## 참고해야 할 코드/문서

- `planning-artifacts/stories/12-1-architecture-and-contract-decision.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestAcceptedResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/dto/IngestErrorResponse.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/model/IngestEnvelopeRequest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/ValidatedIngestCandidate.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestPayloadHasher.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/controller/IngestControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestAcceptanceServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/PortalIngestValidationFixture.java`

## 테스트/검증 방법

- Controller test: SQS mode enqueue success가 실제 enqueue 성공 뒤 `202 queued` response로 수렴하는지 확인한다.
- Controller test: invalid payload `400`, invalid project key `401`, payload too large `413`, enqueue failure `503` mapping을 확인한다.
- Service test: SQS mode에서 repository insert가 호출되지 않는지 fake repository 또는 mock으로 확인한다.
- Message contract test: golden envelope가 expected queue message로 직렬화되는지 확인한다.
- Payload size test: configured size limit 초과 envelope가 enqueue 전에 reject되는지 확인한다.
- Redaction test: queue message/log/error response에 raw project key, token, webhook URL, raw path/query가 없는지 확인한다.
- Direct mode regression: direct mode가 유지된다면 기존 `201/200/409` tests가 깨지지 않는지 확인한다.

## 위험과 완화책

| 위험 | 영향 | 완화책 |
| --- | --- | --- |
| `202 queued`가 저장 완료로 오해됨 | dashboard freshness 기대가 틀어짐 | response/body/API 문서에서 queued semantics를 명시한다. |
| Payload size 초과가 worker poison message가 됨 | 반복 retry와 DLQ noise 증가 | enqueue 전에 size guard로 닫는다. |
| Raw secret이 queue/log에 남음 | 보안 사고 | message body/attribute allow-list와 redaction tests를 둔다. |
| Direct mode와 SQS mode status가 혼재됨 | starter retry behavior 혼란 | mode별 response semantics와 config default를 문서화한다. |
| Enqueue 후 DB insert 전 duplicate를 request path에서 판단하려 함 | request path DB dependency가 다시 커짐 | duplicate final decision은 worker/DB unique constraint로 넘긴다. |
