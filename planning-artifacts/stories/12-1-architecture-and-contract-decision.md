---
artifactType: story
storyId: "12.1"
storyKey: "12-1-architecture-and-contract-decision"
epic: "Epic 12. SQS Buffered Ingest Transition"
title: "Architecture and Contract Decision"
architectureStyle: Traditional MVC
status: done
date: 2026-06-05
commitBoundary: "docs: decide epic 12 sqs ingest architecture and contracts"
---

# Story 12.1 - Architecture and Contract Decision

## Status

done

2026-06-05: Epic 12 architecture-level 결정은 이 story에서 닫혔다. 후속 Story 12.2~12.5는 아래 결정의 의미를 바꾸지 않고 response shape, worker 기본값, diagnostic 위치, benchmark gate 같은 구현 세부만 세부화한다.

이 story는 Epic 12 구현을 시작하기 전에 결정 ledger와 ADR/contract gate를 닫는 문서 story다. 코드 구현, AWS 리소스 생성, Spring bean 추가, API status 변경은 이 story의 산출물이 아니다.

## Story

구현자로서, SQS buffered ingest 전환을 시작하기 전에 queue 위치, consumer runtime, message contract, enqueue failure semantics, snapshot/read-model 의미, cutover/rollback 기준을 먼저 닫고 싶다.

그래야 후속 Story 12.2~12.5가 Lambda나 별도 worker service로 범위를 넓히지 않고, Spring Boot portal 내부 worker와 기존 MVC/JPA/PostgreSQL 경계 안에서 같은 계약을 구현/검증할 수 있다.

## 목표

- SQS buffered ingest의 architecture decision과 계약 변경 범위를 후속 story의 source-of-truth로 닫는다.
- Standard queue + DLQ, Spring Boot portal 내부 worker, fake queue local/test adapter, SQS mode feature flag/config opt-in을 결정으로 고정한다.
- Lambda consumer는 이번 Epic 12의 구현 대상이 아니라 deferred/non-goal임을 명시한다.
- Phase 1 request latency 개선과 Phase 2 DB batch throughput 개선 주장을 서로 다른 claim boundary로 분리한다.
- SQS payload size, enqueue failure, duplicate/no-op, malformed/conflict DLQ, snapshot cutoff, stale/down과 queue lag 분리 기준을 계약으로 고정한다.
- Story 12.3 worker MVP가 Story 12.4 lag semantics 없이 사용자-facing rollout되지 않도록 cutover guardrail을 남긴다.

## Source of Truth

아래 문서를 근거로 하며, 충돌처럼 보이는 경우 이 story의 결정 매트릭스가 Epic 12 후속 story의 우선 계약이다.

1. `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
2. `planning-artifacts/epic-11-12-operational-alerts-and-sqs-batch-ingest-plan.md`
3. `planning-artifacts/epics.md`
4. `planning-artifacts/contracts/ingest-envelope.md`
5. `planning-artifacts/contracts/read-model-contract.md`
6. `planning-artifacts/contracts/time-buckets.md`
7. `planning-artifacts/infrastructure-input-notes.md`
8. `planning-artifacts/database-schema.md`
9. `planning-artifacts/api-surface.md`
10. `_bmad/custom/project-context.md`

## ADR / Contract Decision Matrix

| 결정 항목 | 결정 | 후속 story에 주는 제약 |
| --- | --- | --- |
| Queue type | SQS Standard queue + DLQ로 고정한다. FIFO는 Epic 12 기본안이 아니다. | Standard queue의 at-least-once/out-of-order는 idempotency key, payload hash, DB unique constraint, DLQ taxonomy로 흡수한다. |
| Consumer runtime | Spring Boot portal 내부 worker로 고정한다. | Story 12.3은 portal runtime 안의 worker만 구현한다. 별도 worker service, Lambda consumer, Lambda event source mapping은 만들지 않는다. |
| Lambda scope | Lambda는 deferred/non-goal이다. | Lambda handler scaffold, IAM/VPC/RDS connection 설계, Lambda batch response, cold-start tuning은 Story 12.2~12.5 acceptance에 들어가지 않는다. |
| Local/test queue | Queue path 검증의 기본 local/test adapter는 fake queue다. LocalStack SQS는 opt-in integration 검증으로만 둔다. | 기본 unit/integration/smoke는 fake queue + PostgreSQL Testcontainers로 닫을 수 있어야 한다. LocalStack은 실제 SQS send/receive/delete/redrive 확인용 별도 profile이다. |
| Message contract | `messageVersion=1`, verified project reference, application identity, bucket boundary, idempotency key, payload hash, receivedAt/enqueuedAt, 지원 ingest envelope field만 허용한다. | Message body/attribute/log/error에는 raw project key, starter credential, Authorization token, Discord webhook URL, raw path/query/trace/per-request sample을 넣지 않는다. |
| Idempotency key + payload hash | idempotency key와 canonical payload hash를 함께 사용한다. DB unique constraint가 최종 guard다. | Same key + same hash는 no-op success, same key + different hash는 conflict DLQ 대상, same instance bucket + different key도 conflict DLQ 대상으로 구현한다. |
| SQS payload size | SQS message body + attribute size를 request boundary에서 검사하고 limit 초과는 enqueue 전에 reject한다. Direct fallback은 열지 않는다. | Story 12.2는 worker poison message가 생기지 않게 enqueue 전 size guard를 구현한다. Payload too large의 status는 `413 Payload Too Large`다. |
| Enqueue success response | enqueue가 실제로 성공했을 때만 `202 queued`를 반환한다. | `202 queued`는 DB row 생성, dashboard freshness current, snapshot 반영 완료를 뜻하지 않는다. |
| Enqueue failure response | SQS unavailable, queue config 오류, serialization/message build failure는 `202` 금지이며 starter retry 가능한 non-2xx로 돌린다. 기본 status는 `503 Service Unavailable`이다. | 실패 응답/log/error에는 raw project key, token, webhook URL, raw payload를 남기지 않는다. Payload too large는 retry해도 성공하지 않는 입력이므로 `413`으로 분리한다. |
| DLQ taxonomy | DLQ는 retry로 회복되지 않는 conflict/malformed 대상만 받는다. Duplicate same hash와 transient DB failure는 DLQ가 아니다. | Story 12.3은 duplicate/no-op, conflict, malformed, retry/visibility timeout을 분리한 test matrix를 가져야 한다. |
| Snapshot cutoff / no-backfill | snapshot cutoff 이후 late bucket은 accepted bucket 저장은 허용하되 이미 생성된 snapshot/history에 backfill하지 않는다. | Story 12.4는 이 의미를 구현/검증한다. SQS는 snapshot/history source가 아니라 accepted bucket insert buffer다. |
| stale/down naming | stale/down은 accepted bucket freshness 기준이다. | Queue lag, worker backlog, oldest message age는 pipeline diagnostic이며 host application down copy나 lifecycle state 의미로 쓰지 않는다. |
| Performance claim | Phase 1은 request latency 개선, Phase 2는 DB batch throughput 개선이다. | Story 12.5는 `202 queued` 전환만으로 DB throughput 개선을 주장하지 않는다. Batch writer 측정 후에만 throughput claim을 남긴다. |
| Cutover/rollback | SQS mode는 feature flag/config opt-in이다. Rollback은 direct mode 복귀다. | 기존 endpoint compatibility를 유지하되 mode별 response semantics를 문서화한다. Story 12.3 worker MVP는 Story 12.4 lag semantics 없이 사용자-facing rollout하지 않는다. |
| Shadow mode | Epic 12 MVP 필수 조건이 아니다. | shadow mode를 열려면 double-write나 저장 완료/queued 의미 혼선을 막는 별도 결정이 필요하다. 기본 rollout gate에는 포함하지 않는다. |

## Acceptance Criteria

1. Decision matrix가 Standard queue + DLQ, Spring Boot portal 내부 worker, Lambda deferred/non-goal, fake queue local/test adapter, LocalStack opt-in integration을 결정으로 기록한다.
2. SQS가 `accepted_metric_buckets` data-plane insert path의 buffer이며 dashboard snapshot/history source가 아니라고 명시한다.
3. `ingest-envelope` 기반 SQS message contract가 supported field, message version, idempotency key, payload hash, payload size guard, redaction guard를 포함한다.
4. SQS payload size limit 초과 envelope는 request boundary에서 enqueue 전 reject로 닫고, direct fallback은 열지 않는다고 명시한다.
5. Enqueue 성공일 때만 `202 queued`를 반환하며, SQS unavailable/config failure/serialization failure/message build failure는 `202`를 금지하고 `503`으로 분리한다.
6. Payload too large는 worker poison message가 되지 않게 enqueue 전에 닫고 `413 Payload Too Large`로 분리한다.
7. Duplicate/no-op, same key/different payload hash conflict, same instance bucket/different key conflict, malformed message, transient DB failure의 queue 처리 분류표가 후속 test matrix로 옮길 수 있을 만큼 구체적이다.
8. DLQ taxonomy가 duplicate same hash는 no-op success이며 DLQ가 아니고, transient DB failure는 retry/visibility timeout 대상이며 DLQ가 아니라고 명시한다.
9. `read-model-contract`와 `time-buckets` 기준으로 stale/down은 accepted bucket freshness이며 queue lag나 worker backlog를 host application down으로 표현하지 않는다고 정리한다.
10. Snapshot cutoff 이후 late bucket은 accepted bucket 저장은 허용하되 이미 생성된 snapshot/history에 backfill하지 않는 no-backfill 계약으로 잠근다.
11. Phase 1 request latency 개선과 Phase 2 DB batch throughput 개선의 지표와 claim boundary가 분리된다.
12. SQS mode는 feature flag/config opt-in, rollback은 direct mode 복귀로 둔다.
13. Story 12.3 worker MVP는 Story 12.4 lag semantics 없이 사용자-facing rollout하지 않는 guardrail을 둔다.
14. Architecture decision 결과가 Story 12.2~12.5의 참조 계약으로 남고, 후속 story가 Lambda/direct fallback/shadow mode를 임의로 열 수 없게 한다.

## Contract Gate Tasks

- [x] `ingest-envelope` contract gate: SQS message field, message version, idempotency key, canonical payload hash, payload size guard, secret redaction guard를 결정 매트릭스와 맞춘다. (AC: 3, 4, 5, 6)
- [x] `api-surface` contract gate: SQS mode `202 queued`, `503` enqueue failure, `413` payload too large와 direct mode compatibility를 구분한다. (AC: 5, 6, 12)
- [x] `read-model-contract` / `time-buckets` gate: snapshot cutoff, late bucket no-backfill, stale/down vs queue lag naming을 잠근다. (AC: 9, 10, 13)
- [x] Worker/DLQ gate: duplicate/no-op, conflict, malformed, transient retry/visibility timeout taxonomy를 후속 Story 12.3 test matrix로 연결한다. (AC: 7, 8)
- [x] Rollout gate: SQS mode opt-in, direct mode rollback, no mandatory shadow mode, 12.3-without-12.4 no user-facing rollout 기준을 문서화한다. (AC: 12, 13, 14)

## DLQ Taxonomy

| Scenario | Classification | Worker action | DLQ 여부 | 근거/검증 포인트 |
| --- | --- | --- | --- | --- |
| same idempotency key + same payload hash | duplicate no-op success | 저장하지 않고 ack/delete | 아님 | SQS at-least-once duplicate와 starter retry를 정상 경로로 흡수한다. Error log가 아니라 count metric 후보로 남긴다. |
| same idempotency key + different payload hash | conflict | retry로 회복되지 않는 conflict로 분류 | 대상 | `uk_buckets_project_idempotency_key` 충돌과 stored payload hash 비교가 필요하다. |
| same application instance + same bucket start + different idempotency key | conflict | retry로 회복되지 않는 conflict로 분류 | 대상 | `uk_buckets_instance_bucket_start` 충돌이며 starter duplicate flush 설계가 깨진 후보다. |
| malformed JSON/message shape | malformed | retry로 회복되지 않는 malformed로 분류 | 대상 | message body parse 실패, required field 누락, messageVersion 미지원 등이 포함된다. |
| unsupported message version | malformed | retry로 회복되지 않는 malformed로 분류 | 대상 | Epic 12 MVP는 `messageVersion=1`만 허용한다. |
| payload hash mismatch | malformed | retry로 회복되지 않는 malformed로 분류 | 대상 | message의 payloadHash와 canonical payload hash 재계산 결과가 다르면 queue message 신뢰를 중단한다. |
| invalid bucket boundary/schema validation | malformed | retry로 회복되지 않는 malformed로 분류 | 대상 | 30초 UTC boundary, schemaVersion, durationSeconds 등 기존 ingest validation을 다시 확인한다. |
| transient DB failure/timeout/deadlock | transient failure | message를 delete하지 않고 visibility timeout 후 retry | 아님 | DB 장애가 회복되면 성공할 수 있으므로 DLQ로 즉시 보내지 않는다. max receive count 도달 시 redrive 동작은 SQS 정책으로 다루되 taxonomy상 poison message가 아니다. |

DLQ 기록은 조사 가능한 failure category, message id, sanitized identity, first/last failure timestamp, receive count 후보만 남긴다. Raw project key, token, webhook URL, raw payload, unsupported raw path/query/trace/per-request sample은 DLQ 조사 log와 error에도 남기지 않는다.

## Enqueue Failure Semantics

SQS mode의 `202 queued`는 "검증된 message가 queue에 들어갔다"는 뜻이다. 아래 조건에서는 `202`를 반환하지 않는다.

| Condition | Status | Starter retry 기대 | 비고 |
| --- | --- | --- | --- |
| enqueue 성공 | `202 Accepted` + queued body | retry 불필요 | DB insert 완료, duplicate 판정 완료, snapshot freshness current를 뜻하지 않는다. |
| project key 누락/검증 실패 | `401` | retry해도 동일 key면 실패 | direct mode와 의미를 유지한다. |
| envelope validation 실패 | `400` | payload 수정 전까지 실패 | schemaVersion, bucket boundary, metric taxonomy validation 실패다. |
| payload too large | `413 Payload Too Large` | 같은 payload는 retry해도 실패 | SQS size limit을 request boundary에서 검사하고 enqueue 전에 닫는다. |
| SQS unavailable/timeout | `503 Service Unavailable` | starter retry 가능 | Retryable queue infra failure다. |
| queue URL/config missing 또는 disabled mismatch | `503 Service Unavailable` | 설정 복구 후 retry 가능 | `202` 금지. 운영 log에는 failure category만 남긴다. |
| serialization/message build failure | `503 Service Unavailable` | 구현 결함이면 retry로 즉시 회복되지 않을 수 있음 | Raw payload를 log/error에 남기지 않는다. 필요하면 internal failure metric과 sanitized category만 남긴다. |

SQS mode request path에서 같은 idempotency key가 이미 queue에만 있고 DB에는 아직 없을 수 있으므로, duplicate/conflict 최종 판정은 worker가 담당한다. Enqueue 전에 DB idempotency lookup을 유지할지는 Story 12.2의 구현 세부 선택이지만, 이 선택이 `202 queued` 의미나 worker-side conflict taxonomy를 바꾸면 안 된다.

## Payload Size Policy

- SQS message body와 message attribute 합산 크기는 SQS payload size limit 안에 있어야 한다.
- Size check는 enqueue 직전 request boundary에서 수행한다.
- Limit 초과 envelope는 worker poison message가 되지 않도록 enqueue하지 않는다.
- 기본 정책은 `413 Payload Too Large`로 reject하는 것이다.
- Direct fallback은 저장 완료/queued 의미와 성능 측정을 섞을 수 있으므로 Epic 12 path로 열지 않는다.
- 후속 story는 payload size 초과를 direct fallback으로 우회할 수 없다. 이 정책을 바꾸려면 Epic 12 계약을 변경하는 별도 ADR 또는 correct-course가 먼저 필요하다.

## Snapshot / Read-Model Semantics

- SQS는 accepted bucket 저장 전 완충 계층일 뿐 read model source가 아니다.
- Application metric freshness/state/read-model의 source-of-truth는 portal이 수용해 저장한 `accepted_metric_buckets`다.
- Snapshot scheduler는 Story 12.4에서 target current window cutoff 이후 configurable delay를 둔다.
- `persistedAt > snapshotCutoffAt`인 late bucket은 accepted bucket에는 저장될 수 있지만 이미 생성된 snapshot/history에는 backfill하지 않는다.
- Snapshot detail/history는 저장 당시 read model을 보여주며 current state를 재판정하지 않는다.
- stale/down은 accepted bucket freshness 기준 이름이다.
- queue lag, worker backlog, SQS approximate oldest message age, last successful persist lag는 pipeline diagnostic 이름이다.
- Queue lag가 있어 latest accepted bucket freshness가 오래돼 보일 수는 있지만, UI/API copy는 이를 host application down 확정으로 표현하지 않는다.

## Cutover / Rollback Gate

- Direct mode와 SQS mode는 feature flag/config로 분리한다.
- SQS mode는 opt-in이다. 기본 운영 전환은 Story 12.2~12.4 contract/test gate가 통과된 뒤에만 허용한다.
- Rollback 기준은 SQS mode flag/config를 내려 direct mode로 복귀하는 것이다.
- 기존 ingest endpoint compatibility는 유지하되, SQS mode에서는 response semantics가 `201 Created`가 아니라 `202 queued`임을 API contract에 남긴다.
- Story 12.3 worker MVP만 완료된 상태에서는 사용자-facing rollout을 하지 않는다. Story 12.4의 snapshot delay, queue lag diagnostic, stale/down naming guard가 함께 닫혀야 한다.
- Shadow mode는 Epic 12 MVP 필수 조건이 아니다. Shadow mode를 열 경우 double-write, duplicate, response semantics, performance measurement contamination을 별도 ADR로 닫아야 한다.
- Rollback 중에도 SQS에 이미 들어간 message 처리/폐기 정책은 운영 runbook 후보로 남기되, Epic 12 MVP에서는 queue replay UI나 backfill pipeline을 만들지 않는다.

## 후속 Story 책임 경계

| Story | 12.1에서 잠근 계약 | 후속 story가 구현/검증할 것 |
| --- | --- | --- |
| 12.2 Ingest Enqueue Boundary | `202 queued` only after enqueue success, `503` enqueue failure, `413` payload too large, no direct fallback, redaction guard | Controller/service boundary, queue publisher interface, fake publisher, SQS publisher 후보, status mapping tests, response body field와 SQS `messageId` 노출 여부 |
| 12.3 Spring Boot SQS Worker MVP and Idempotency | Spring Boot portal 내부 worker, DLQ taxonomy, duplicate no-op, transient retry | Poll/process/delete, identity projection, DB unique conflict handling, fake queue worker smoke, taxonomy tests, visibility timeout, max receive count, DLQ payload shape |
| 12.4 Snapshot Delay and Pipeline Lag Semantics | snapshot cutoff/no-backfill, stale/down vs queue lag naming, no user-facing rollout without lag guard | Snapshot delay exact default, fallback grace, queue lag diagnostic 위치, late bucket behavior, fallback threshold recalculation |
| 12.5 Batch Writer and Performance Verification | Phase 1 latency claim과 Phase 2 DB throughput claim 분리 | Benchmark manifest, pass/fail threshold, direct vs SQS latency comparison, worker MVP vs batch writer throughput measurement |

## Non-Goals

- Lambda consumer 구현, Lambda handler scaffold, Lambda event source mapping 설정.
- Production autoscaling, multi-region queue, separate worker deployment.
- Kafka/Kinesis/Redis queue 전체 비교나 queue technology bake-off.
- Dashboard read model 계산식, lifecycle state, p95/p99, endpoint priority, operational event history 구현 변경.
- Queue replay UI, backfill pipeline, complex exactly-once processing.
- Direct fallback 구현, mandatory shadow mode 구현.
- AWS 리소스 생성, IAM/VPC/RDS 연결 설계, Lambda cold-start tuning.

## 이번 Story에서 닫힌 결정

- Queue는 SQS Standard + DLQ다.
- Consumer는 Spring Boot portal 내부 worker다.
- Lambda consumer는 deferred/non-goal이다.
- Local/test queue path 기본 adapter는 fake queue고, LocalStack은 opt-in integration이다.
- SQS payload size limit 초과는 enqueue 전 reject로 닫고 direct fallback은 열지 않는다.
- Payload too large의 status는 `413`, SQS unavailable/config/serialization failure의 status는 `503`이다.
- Enqueue 성공일 때만 `202 queued`를 반환한다.
- SQS mode는 feature flag/config opt-in이고 rollback은 direct mode 복귀다.
- Story 12.3 worker MVP는 Story 12.4 lag semantics 없이 사용자-facing rollout하지 않는다.

## 후속 구현에서 세부화할 수 있지만 의미를 바꾸면 안 되는 항목

| Story | 후속 세부화 항목 | 12.1에서 바꿀 수 없게 잠근 의미 |
| --- | --- | --- |
| 12.2 | `202 queued` response body field와 SQS `messageId` 노출 여부 | enqueue 성공일 때만 `202`, payload too large는 `413`, enqueue failure는 `503`, direct fallback 금지 |
| 12.3 | visibility timeout, max receive count, DLQ payload shape | Spring Boot portal 내부 worker, duplicate no-op, transient retry, conflict/malformed DLQ 대상, raw payload/secret 금지 |
| 12.4 | snapshot delay exact default, fallback grace, queue lag diagnostic 위치 | snapshot cutoff/no-backfill, stale/down과 queue lag 분리, 12.3-only user-facing rollout 금지 |
| 12.5 | benchmark manifest와 pass/fail threshold | Phase 1 request latency claim과 Phase 2 DB batch throughput claim 분리 |

## 참고해야 할 코드/문서

- `planning-artifacts/tmp-sqs-ingest-transition-plan-2026-06-05.md`
- `planning-artifacts/epic-11-12-operational-alerts-and-sqs-batch-ingest-plan.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/time-buckets.md`
- `planning-artifacts/infrastructure-input-notes.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/api-surface.md`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/controller/IngestController.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestPayloadHasher.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotScheduler.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`

## 테스트/검증 방법

- ADR/contract review: decision matrix의 각 행이 후속 Story 12.2~12.5의 scope와 충돌하지 않는지 확인한다.
- Contract review: ingest envelope, API surface, read model, time bucket, infrastructure notes에 반영할 항목과 후속 story 책임이 분리됐는지 확인한다.
- Scenario table review: duplicate/no-op, conflict, malformed, transient failure, DLQ 분류가 후속 구현자가 테스트로 옮길 수 있을 만큼 구체적인지 확인한다.
- Search check: Epic 12 implementation story acceptance에 Lambda 구현 작업이 들어가지 않았는지 `rg -n "Lambda" planning-artifacts/stories/12-*.md`로 확인한다.
- Payload size review: direct fallback이 열리지 않았고 payload too large가 enqueue 전 `413`으로 닫혔는지 확인한다.
- Enqueue failure review: SQS unavailable/config/serialization failure가 `202`로 매핑되지 않고 `503`과 redaction guard를 가진다.
- Cutover review: SQS mode가 opt-in이고 rollback이 direct mode 복귀이며 12.3-only rollout이 금지되어 있는지 확인한다.
- Performance claim review: request latency 개선과 DB batch throughput 개선이 같은 지표로 섞이지 않았는지 확인한다.

## 위험과 완화책

| 위험 | 영향 | 완화책 |
| --- | --- | --- |
| SQS가 read model source처럼 해석됨 | snapshot/history 의미가 흔들림 | SQS는 data-plane write buffer로만 정의하고 accepted bucket을 source-of-truth로 유지한다. |
| Lambda가 구현 scope로 재진입함 | IAM/VPC/DB connection/cold start 부담이 Epic 12에 유입됨 | 모든 story에서 Lambda를 deferred/non-goal로만 표기한다. |
| 성능 개선 주장이 과장됨 | portfolio 신뢰도 저하 | Phase 1 request latency와 Phase 2 DB throughput 지표를 분리한다. |
| Payload size 정책이 다시 열림 | large envelope가 poison message나 의미 혼선으로 이어짐 | enqueue 전에 size guard와 `413` reject로 닫고 direct fallback을 금지한다. |
| Queue lag가 host health로 오인됨 | stale/down copy가 잘못됨 | queue lag diagnostic을 lifecycle state와 분리한다. |
| `202 queued`가 저장 완료로 오해됨 | starter/UI/operator 기대가 틀어짐 | enqueue 성공만 `202`로 두고 DB 저장, freshness, snapshot 반영 의미를 분리한다. |
| 12.3 worker MVP가 먼저 노출됨 | lag 때문에 dashboard state와 operator copy가 흔들림 | 12.4 lag semantics가 닫히기 전 사용자-facing rollout을 금지한다. |
| Direct fallback이 몰래 열림 | 성능 측정과 API 의미가 섞임 | payload too large는 enqueue 전 reject로 잠그고 fallback은 별도 ADR 없이는 금지한다. |
