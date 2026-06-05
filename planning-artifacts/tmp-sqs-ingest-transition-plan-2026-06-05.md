---
artifactType: temporary-plan
projectName: Spring Boot 운영 첫 화면 포털
status: draft
date: 2026-06-05
scope: SQS buffered metric bucket ingest transition
---

# SQS Buffered Ingest 전환 임시 계획

## 1. 목적

현재 metric bucket ingest는 starter가 30초 bucket을 만든 뒤 portal HTTP API에 보내고, portal request thread가 검증과 PostgreSQL insert를 끝낸 뒤 `201 Created`를 돌려주는 구조다. 이 계획은 그 DB write 구간을 SQS 뒤로 빼서 request path를 가볍게 만들고, bucket 저장은 batch worker가 처리하도록 바꾸는 구체안을 정리한다.

이 문서는 `planning-artifacts/epic-11-12-operational-alerts-and-sqs-batch-ingest-plan.md`의 Epic 12 초안을 코드 기준으로 더 구체화한 임시 실행안이다. 정식 ADR이나 story로 승격하기 전 검토용이다.

## 2. 현재 흐름 요약

### Starter runtime

- `StarterMetricDrainScheduler`는 30초마다 `StarterMetricIngestService#drainDueBuckets()`를 호출한다.
- `MetricBucketFlushWorker`는 local `BoundedMetricQueue`를 100ms poll interval로 읽고, `IngestEnvelopeBuilderService`가 만든 envelope를 `PortalMetricBucketClient`로 전송한다.
- `JdkPortalMetricBucketClient`는 `/api/ingest/v1/buckets`에 `X-OBS-Project-Key`, `Idempotency-Key`, JSON body를 POST한다.
- starter는 이미 request path와 portal 전송 path가 분리되어 있으므로, SQS는 starter 내부 queue를 대체하기보다 portal 뒤쪽 DB write를 완충하는 위치가 자연스럽다.

### Portal ingest runtime

- `IngestController`는 POST 요청을 `IngestAcceptanceService#accept()`로 넘긴다.
- `IngestAcceptanceService`는 project key 검증, envelope validation, idempotency lookup, payload hash 계산을 수행한다.
- 현재는 같은 request 안에서 `MetricBucketRepository#insert()`를 호출한다.
- `MetricBucketRepository#insert()`는 `ApplicationCatalogRepository#getOrCreate()`로 application/instance row를 찾거나 만들고, `accepted_metric_buckets`에 `saveAndFlush()` 한다.
- `accepted_metric_buckets`에는 `uk_buckets_project_idempotency_key`, `uk_buckets_instance_bucket_start` unique constraint가 있어 최종 중복 방어선은 이미 있다.

### Read model과 snapshot 의존성

- dashboard/current read model은 `accepted_metric_buckets`를 window 기준으로 조회한다.
- `DashboardSnapshotScheduler`는 현재 UTC 정시에 capture를 dispatch하고, `targetWindowEnd`를 정시 boundary로 고정한다.
- `DashboardSnapshotFallbackCaptureService`는 latest snapshot이 65분 이상 오래되면 query path에서 fallback snapshot을 저장한다.
- SQS 도입 후에는 “HTTP enqueue 성공”과 “DB accepted bucket 저장 완료”가 분리되므로, snapshot 생성 시점과 fallback threshold가 queue lag를 감안해야 한다.

## 3. 추천 아키텍처

### 기본 추천안

1. starter는 계속 portal HTTP API로 보낸다.
2. portal request path는 project key와 envelope를 검증하고 payload hash를 계산한다.
3. DB insert 대신 검증 완료 message를 SQS Standard queue에 enqueue한다.
4. portal 내부 Spring Boot worker가 SQS를 long polling으로 읽고 batch 단위로 PostgreSQL에 저장한다.
5. Lambda consumer는 후속 옵션으로 남긴다.

이 프로젝트는 이미 Spring MVC/JPA/PostgreSQL 중심이고, local smoke와 Testcontainers 기반 검증이 잘 잡혀 있다. 그래서 첫 구현은 portal 내부 worker가 배포 단위와 DB connection 관리를 덜 늘린다. Lambda는 web process와 ingest worker를 분리해야 하거나, AWS 운영 경계까지 보여줄 필요가 생겼을 때 승격한다.

### Queue type

- 추천: SQS Standard queue + DLQ.
- 이유: bucket은 `bucket_start_utc`/`bucket_end_utc`로 정렬 가능한 time-series evidence라 strict FIFO 처리 자체가 필수는 아니다.
- Standard queue는 at-least-once와 out-of-order 가능성을 전제로 해야 하므로, idempotency와 unique constraint가 최종 source-of-truth가 된다.
- FIFO queue는 5분 deduplication과 message group ordering이 장점이지만, 지금은 application instance별 strict processing order보다 batch throughput과 단순성이 더 중요하다.

참고한 AWS 공식 문서:

- [Amazon SQS standard queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues.html)
- [Lambda parameters for Amazon SQS event source mappings](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-parameters.html)
- [Creating and configuring an Amazon SQS event source mapping](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-configure.html)
- [Handling errors for an SQS event source in Lambda](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-errorhandling.html)
- [Configuring scaling behavior for SQS event source mappings](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-scaling.html)

## 4. 처리 주기 제안

### Spring Boot portal 내부 worker 기준

초기 기본값:

| 항목 | 제안값 | 이유 |
| --- | --- | --- |
| Queue | SQS Standard + DLQ | 중복/out-of-order를 DB idempotency로 흡수하고 throughput을 우선한다. |
| Long polling | `WaitTimeSeconds=20` | 빈 poll 비용을 줄이고 message가 있으면 즉시 처리한다. |
| Receive 단위 | SQS API call당 최대 10개 | SQS `ReceiveMessage` API 한계에 맞춘다. worker가 여러 번 poll해 내부 batch를 만든다. |
| DB flush 조건 | 100 messages 또는 10초 중 먼저 도달 | 30초 bucket UX를 크게 늦추지 않으면서 round trip을 줄인다. |
| Worker concurrency | 1 batch worker부터 시작 | catalog get-or-create race와 unique conflict 처리를 안정화한 뒤 늘린다. |
| Visibility timeout | 2~3분 | batch insert worst-case와 retry 여유를 둔다. |
| Redrive | `maxReceiveCount >= 5`, DLQ | poison message를 무한 retry하지 않는다. |
| Snapshot delay | 120초부터 시작 | 10초 flush와 일시 retry를 흡수하는 안전 margin이다. |

“1분마다 처리”는 가능하지만 기본값으로는 추천하지 않는다. 1분 flush는 DB round trip을 더 줄일 수 있지만, dashboard freshness가 최소 수십 초씩 늦어지고 snapshot delay도 3분 이상으로 늘려야 한다. portfolio benchmark용 low-cost profile로는 둘 수 있다.

### Lambda consumer를 선택할 경우

Lambda event source mapping은 cron처럼 1분마다 도는 모델이 아니라 SQS를 계속 poll하고 batch 조건이 차면 function을 호출하는 모델이다. Lambda를 쓴다면 아래처럼 둔다.

| 항목 | 제안값 | 이유 |
| --- | --- | --- |
| BatchSize | 100~500 | payload 크기를 보고 조정한다. 작은 payload면 더 키울 수 있다. |
| MaximumBatchingWindowInSeconds | 5~10초 | low latency와 batch 효율 균형점이다. |
| MaximumConcurrency | 2~5 | DB connection과 catalog race를 보호한다. |
| Function timeout | 30초 후보 | batch insert worst-case를 실측해서 조정한다. |
| Visibility timeout | `6 * functionTimeout + batchWindow` 이상 | Lambda/SQS 권장식에 맞춘다. |
| FunctionResponseTypes | `ReportBatchItemFailures` | batch 일부 실패 시 성공 message 재처리를 줄인다. |
| DLQ redrive | `maxReceiveCount >= 5` | 반복 실패 message를 분리한다. |

Lambda를 기본안으로 미루는 이유는 IAM, VPC/RDS 접근, cold start, DB connection pooling, 배포 단위가 한 번에 늘어나기 때문이다. 단, AWS 운영 경험을 보여주는 것이 목표라면 공유 batch processor를 먼저 만들고 Lambda handler를 얇게 붙이는 식으로 확장할 수 있다.

## 5. 코드 변경 계획

### 5.1 의존성과 설정

변경 후보:

- `observability-portal/build.gradle`
  - AWS SDK v2 SQS dependency 추가.
  - LocalStack을 쓸 경우 Testcontainers LocalStack dependency는 별도 opt-in으로 추가.
- `observability-portal/src/main/resources/application.properties`
  - 기본 local profile은 기존 direct insert를 유지하거나 fake queue를 사용한다.

새 설정 후보:

```properties
portal.ingest.buffer.mode=direct
portal.ingest.buffer.queue-url=
portal.ingest.buffer.worker-enabled=false
portal.ingest.buffer.batch-size=100
portal.ingest.buffer.max-batch-age=10s
portal.ingest.buffer.long-poll-seconds=20
portal.ingest.buffer.visibility-timeout=180s
portal.ingest.buffer.max-receive-count=5
portal.dashboard-snapshots.capture-delay=120s
portal.dashboard-snapshots.fallback-staleness-threshold=67m
```

`mode=direct`를 남기면 local/smoke와 기존 테스트를 깨지 않고 SQS path를 단계적으로 켤 수 있다. `mode=sqs`에서는 enqueue 성공이 DB insert 성공이 아니라는 점을 API와 DTO에 반영한다.

### 5.2 Ingest service 분리

현재 `IngestAcceptanceService`는 validation과 persistence orchestration을 함께 한다. SQS 전환을 위해 아래처럼 분리한다.

- `IngestValidationService`
  - project key 검증, envelope validation, idempotency key format validation을 담당한다.
  - `ValidatedIngestCandidate`와 payload hash를 반환한다.
- `MetricBucketDirectAcceptanceService`
  - 기존 direct DB insert path를 보존한다.
  - local/test profile 또는 fallback path에서 사용한다.
- `MetricBucketEnqueueService`
  - 검증 완료 candidate를 SQS message로 변환하고 enqueue한다.
  - enqueue 실패 시 `503 Service Unavailable` 후보로 매핑한다. starter worker는 non-2xx를 retry하므로 기존 재시도 흐름과 맞는다.
- `IngestAcceptanceService`
  - mode에 따라 direct 또는 enqueue use case를 선택하는 facade로 축소한다.

Controller 응답 후보:

- direct mode: 기존 `201 Created` 유지.
- sqs mode: `202 Accepted`와 `queued=true`, `receivedAt`, `idempotencyKey`, `messageId` 후보를 반환.

추천은 SQS mode에서 `202 Accepted`를 쓰는 것이다. `201 Created`는 DB row가 이미 만들어졌다는 의미로 읽히기 쉽다.

### 5.3 SQS message contract

새 record 후보:

- `QueuedMetricBucketMessage`
  - `messageVersion`: `1`
  - `projectId`
  - `projectName`
  - `idempotencyKey`
  - `payloadHash`
  - `receivedAt`
  - `enqueuedAt`
  - `payload`: 기존 `IngestEnvelopeRequest` shape

Message attribute 후보:

- `messageVersion`
- `projectId`
- `schemaVersion`
- `bucketStartUtc`
- `bucketEndUtc`
- `applicationName`
- `environment`
- `instanceName`

금지:

- raw project key
- starter credential
- Authorization token
- Discord webhook URL
- raw path/query/trace/per-request sample

Worker는 queue를 신뢰하지 않고 message version, payload hash, bucket boundary, schemaVersion을 다시 확인한다. 검증 실패 message는 retry해도 성공하지 않으므로 DLQ 대상으로 분류한다.

### 5.4 Queue publisher와 local adapter

새 interface 후보:

- `MetricIngestQueuePublisher`
  - `MetricIngestEnqueueReceipt enqueue(QueuedMetricBucketMessage message)`

구현 후보:

- `SqsMetricIngestQueuePublisher`
  - AWS SDK SQS client로 `SendMessage` 호출.
  - 실패 시 project key나 payload 원문을 log에 남기지 않고 failure category만 남긴다.
- `InMemoryMetricIngestQueuePublisher`
  - unit/local smoke용 fake queue.
  - worker logic을 SQS 없이 검증할 수 있게 한다.

LocalStack은 SQS 자체 integration을 확인할 때만 사용한다. 기본 unit/integration 테스트는 fake queue와 PostgreSQL Testcontainers로 충분하다.

### 5.5 Worker와 batch processor

새 component 후보:

- `MetricIngestQueueWorker`
  - Spring Boot runtime 안에서 long polling으로 SQS message를 읽는다.
  - internal accumulator가 `batch-size` 또는 `max-batch-age`에 도달하면 processor를 호출한다.
- `MetricBucketBatchProcessor`
  - queue message를 parse/validate/hash-check한다.
  - valid message를 `AcceptedMetricBucketWriteCommand`로 변환한다.
  - duplicate/no-op/conflict를 분류한다.
- `MetricBucketBatchWriter`
  - batch 단위 transaction으로 PostgreSQL에 저장한다.
  - 최종 idempotency 판단은 DB unique constraint와 lookup 결과를 따른다.

처리 결과:

| 결과 | Queue action | DB action | 비고 |
| --- | --- | --- | --- |
| inserted | delete/ack | 새 row insert | 정상 저장 |
| duplicate same payload | delete/ack | no-op | SQS at-least-once와 starter retry 흡수 |
| duplicate different payload | DLQ 후보 | no-op | 같은 idempotency key에 다른 payload |
| same instance bucket different key | DLQ 후보 | no-op | `uk_buckets_instance_bucket_start` 충돌 |
| transient DB failure | delete하지 않음 | rollback | visibility timeout 후 retry |
| malformed message | DLQ 후보 | no-op | retry로 회복 불가 |

### 5.6 Repository batch 저장

1차 구현:

- 기존 `MetricBucketRepository#insert()`를 batch processor에서 message별로 호출한다.
- 장점: 변화가 작고 현재 repository/test를 재사용한다.
- 단점: DB round trip 개선 폭이 작다. request path 분리 효과만 먼저 얻는다.

2차 구현:

- `MetricBucketRepository#insertBatch(List<AcceptedMetricBucketWriteCommand>)` 추가.
- `ApplicationCatalogRepository`에 batch get-or-create를 추가한다.
- 같은 `projectId + applicationName + environment + instanceName` 그룹을 모아 application/instance lookup과 last-seen update를 줄인다.
- `accepted_metric_buckets` insert는 JDBC batch 또는 JPA `saveAll` + flush once로 처리한다.
- unique conflict 발생 시 batch 전체 rollback 후 conflict row만 개별 재처리하거나, PostgreSQL `ON CONFLICT` 기반 JDBC insert로 duplicate/no-op를 분리한다.

권장 순서:

1. enqueue/worker path를 먼저 end-to-end로 닫는다.
2. 중복/충돌 semantics를 테스트로 고정한다.
3. batch writer 최적화로 넘어간다.

## 6. 로직 변경 계획

### 6.1 API 의미 변경

SQS mode에서 portal 응답은 “저장 완료”가 아니라 “검증 후 queue에 들어감”이다.

- `400`: envelope validation 실패.
- `401`: project key 검증 실패.
- `202`: enqueue 성공.
- `503`: SQS enqueue 실패 또는 queue config 장애.

기존 `409 duplicate idempotency key`는 SQS mode request path에서는 항상 알 수 없다. 같은 key가 아직 queue에만 있고 DB에 없을 수 있기 때문이다. 따라서 SQS mode에서는 duplicate/conflict 최종 판정을 worker로 넘기는 것이 현실적이다.

필요하면 enqueue 전에 DB idempotency lookup을 유지할 수 있지만, 이것은 이미 저장된 duplicate만 잡아주며 queue 내부 duplicate는 막지 못한다. 성능 목표가 크다면 request path DB lookup은 제거하거나 project key verification cache와 함께 재검토한다.

### 6.2 중복과 멱등성

SQS Standard queue는 같은 message가 한 번 이상 도착할 수 있고 순서가 바뀔 수 있다. 이 전제를 정상 경로로 받아들인다.

원칙:

- DB unique constraint가 최종 멱등성 방어선이다.
- same idempotency key + same payload hash는 no-op success로 처리한다.
- same idempotency key + different payload hash는 conflict로 DLQ에 보낸다.
- same application instance + same bucket start + different idempotency key도 conflict로 본다.
- worker는 duplicate/no-op를 error log로 크게 남기지 않는다. 운영 metric으로 count만 남긴다.

이를 위해 `AcceptedMetricBucketReceipt`만으로는 부족하므로 stored `payloadHash`, `applicationInstanceId`, `bucketStartUtc`를 읽는 identity projection이 필요하다.

### 6.3 늦게 도착하는 bucket 처리

late arrival을 두 층으로 나눈다.

1. 일반 late arrival
   - queue/batch 때문에 `bucketEndUtc`보다 10~120초 늦게 DB에 저장되는 정상 지연.
   - dashboard current query는 DB에 저장된 뒤부터 해당 bucket을 window에 포함한다.
   - snapshot은 `currentWindowEndUtc + snapshotDelay` 이후 생성하므로 대부분 흡수한다.

2. 심한 late arrival
   - snapshot delay 이후에 도착한 오래된 bucket.
   - accepted bucket row 저장은 허용한다.
   - 이미 생성된 `dashboard_snapshots` read model은 immutable하게 유지하고 backfill하지 않는다.
   - operational event history도 stored snapshot/read model 기반이므로 과거 snapshot을 재계산하지 않는다.

권장 cutoff:

- `snapshotCutoffAt = currentWindowEndUtc + snapshotDelay`
- `persistedAt > snapshotCutoffAt`이면 해당 hourly snapshot에는 반영하지 않는다.
- 이 경우 `late_for_snapshot_count`, `ingest_lag_seconds` metric을 남긴다.

예시:

```text
11:59:30~12:00:00 bucket
12:00:05 portal enqueue
12:00:12 DB persist
12:02:00 snapshot targetWindowEnd=12:00:00 capture
=> snapshot 포함

11:59:30~12:00:00 bucket
12:00:05 portal enqueue
12:03:30 DB persist
12:02:00 snapshot targetWindowEnd=12:00:00 capture 완료
=> accepted bucket에는 저장하지만 12:00 snapshot에는 backfill하지 않음
```

### 6.4 Snapshot 스케줄 변경

현재:

- cron: UTC 정시
- target window end: `requestedAt.truncatedTo(HOURS)`

변경:

- configurable `portal.dashboard-snapshots.capture-delay` 추가.
- 기본값 후보: 120초.
- 구현 방식 후보:
  - 간단 버전: `@Scheduled(cron = "0 2 * * * *", zone = "UTC")`처럼 minute offset 고정.
  - 설정 가능 버전: 매분 scheduler가 돌고, `now >= boundary + delay`인 boundary만 capture.

추천은 설정 가능 버전이다. batch window를 10초에서 60초로 바꾸거나 backlog guard를 넣을 때 cron expression을 매번 바꾸지 않아도 된다.

Fallback threshold:

- 현재 65분.
- 변경 후보: `60분 + snapshotDelay + 5분 grace`.
- delay 120초면 67분 또는 68분 후보.

Backlog guard:

- SQS approximate oldest message age 또는 worker가 기록한 last successful persist lag가 threshold를 넘으면 scheduled snapshot을 한 번 skip하고 warn metric을 남긴다.
- 무한 skip은 피해야 하므로 `max-snapshot-delay` 후보를 둔다. 예: 10분을 넘으면 “incomplete risk” metric을 남기고 capture한다.

### 6.5 Dashboard current semantics

Dashboard API의 current/baseline window 계산은 바꾸지 않는다.

- queue lag가 있으면 latest accepted bucket freshness가 더 오래된 것처럼 보일 수 있다.
- 이것은 실제 DB source-of-truth 기준으로는 맞는 표현이다.
- 다만 운영자가 “앱이 stale”인지 “ingest queue가 밀림”인지 구분할 수 있도록 후속 UI/API에 `ingestPipelineLag` 같은 read-side diagnostic을 추가할 수 있다.

MVP에서는 dashboard state 계산에 queue backlog를 직접 섞지 않는다. accepted bucket data-plane과 starter heartbeat axis 분리 원칙을 유지한다.

## 7. 운영 지표와 알림

추가 metric 후보:

- `ingest.enqueue.success.count`
- `ingest.enqueue.failure.count`
- `ingest.queue.message.age.seconds`
- `ingest.queue.visible.count`
- `ingest.worker.batch.size`
- `ingest.worker.batch.persist.duration`
- `ingest.worker.persist.success.count`
- `ingest.worker.persist.duplicate.count`
- `ingest.worker.persist.conflict.count`
- `ingest.worker.dlq.count`
- `ingest.lag.seconds`: `persistedAt - bucketEndUtc`
- `snapshot.capture.delay.seconds`
- `snapshot.skipped.queue_backlog.count`
- `snapshot.late_bucket.count`

필수 log guard:

- raw project key를 남기지 않는다.
- payload 전체를 warn/error에 남기지 않는다.
- DLQ message 조사 runbook에서도 secret-bearing field를 출력하지 않는다.

## 8. 테스트 계획

### Unit test

- `IngestValidationService`가 기존 validation 결과를 유지하는지 확인한다.
- `MetricBucketEnqueueService`가 raw project key 없이 message를 만드는지 확인한다.
- `MetricBucketBatchProcessor` duplicate/no-op/conflict matrix를 검증한다.
- malformed message가 retry 대상이 아니라 DLQ 대상인지 검증한다.

### MVC/controller test

- direct mode는 기존 `201 Created`를 유지한다.
- sqs mode는 enqueue 성공 시 `202 Accepted`를 반환한다.
- SQS enqueue 실패 시 `503`을 반환하고 secret이 body/log에 남지 않는다.

### Repository integration test

- batch insert가 기존 unique constraint를 유지한다.
- same idempotency/same hash는 no-op 처리된다.
- same idempotency/different hash는 conflict 처리된다.
- same instance/bucket start conflict가 DLQ 후보로 분류된다.
- catalog application/instance last-seen update가 batch에서 max seenAt 기준으로 유지된다.

### Worker integration test

- fake queue로 poll -> process -> ack/delete 흐름을 검증한다.
- transient DB failure 시 message를 delete하지 않는다.
- partial failure가 성공 message 재처리를 최소화하는지 확인한다.

### Optional LocalStack test

- 실제 SQS queue URL, send/receive/delete, visibility timeout, DLQ redrive를 opt-in profile로 검증한다.
- 기본 CI나 local smoke에는 강제하지 않는다.

### Snapshot test

- delayed scheduler가 `targetWindowEnd`를 원래 hourly boundary로 유지하는지 확인한다.
- delay 전 도착한 bucket은 snapshot에 포함된다.
- delay 후 도착한 bucket은 accepted bucket에는 저장되지만 기존 snapshot에는 backfill되지 않는다.
- fallback threshold가 `60분 + delay + grace`로 동작한다.

## 9. 성능 개선 예상

이 수치는 현재 코드 구조에서 유도한 예상이며, 최종 수치는 benchmark story에서 측정해야 한다.

| 항목 | 현재 direct insert | SQS + worker 예상 |
| --- | --- | --- |
| HTTP ingest latency | project key 검증 + validation + idempotency DB lookup + catalog get-or-create + bucket insert/flush | project key 검증 + validation + SQS enqueue |
| Request thread DB 부담 | bucket마다 DB read/write 수행 | project key 검증 DB read는 남고 bucket insert는 worker로 이동 |
| DB write round trip | bucket마다 `saveAndFlush` 중심 | batch size 100 기준 flush/transaction 횟수 크게 감소 |
| Burst 흡수 | DB가 느리면 starter retry/drop으로 이어질 수 있음 | SQS가 backlog로 흡수, read model freshness만 지연 |
| 중복 처리 | request path에서 일부 duplicate를 `409` 처리 | worker가 same payload duplicate를 no-op success 처리 |
| Dashboard freshness | insert 직후 반영 | enqueue-to-persist latency만큼 늦게 반영 |

예상 개선 범위:

- HTTP ingest p95 latency: DB insert가 병목인 환경에서는 30~70% 개선 가능. 다만 BCrypt project key 검증과 SQS network latency가 남으므로 무조건 한 자리 ms가 되지는 않는다.
- DB ingest throughput: 진짜 batch writer까지 구현하면 3~10배 개선 가능성이 있다. 특히 `saveAndFlush` per bucket을 flush once 또는 JDBC batch로 줄일 때 효과가 크다.
- DB round trip: batch size 100이 안정적으로 채워지는 부하에서는 bucket insert 관련 round trip을 60~90% 줄일 수 있다. catalog get-or-create batch 최적화 여부에 따라 편차가 크다.
- 장애 흡수력: 짧은 DB slowdown은 SQS backlog로 흡수할 수 있다. 대신 backlog가 길어지면 dashboard current/freshness와 snapshot 품질이 지연된다.
- 비용: SQS/Lambda 비용은 추가된다. 반대로 DB peak write pressure와 request thread 점유는 줄어든다.

성능 개선을 크게 만들려면 아래 최적화가 중요하다.

- project key verification cache: raw key는 저장하지 않고 key prefix 후보와 hash 검증 결과를 짧은 TTL로 cache할지 검토한다.
- batch catalog get-or-create: application/instance lookup/save를 command마다 반복하지 않는다.
- JDBC batch insert 또는 PostgreSQL `ON CONFLICT` 기반 insert: duplicate/no-op를 DB 왕복 적게 처리한다.
- worker concurrency 제한: DB connection pool과 unique conflict 폭주를 막는다.

## 10. 가능성과 리스크

### 가능성

구현 가능성은 높다.

이유:

- starter는 이미 local queue와 background flush로 request path가 분리되어 있다.
- portal은 `IngestController`, `IngestAcceptanceService`, `MetricBucketRepository` 경계가 비교적 선명하다.
- accepted bucket table에는 idempotency와 instance bucket unique constraint가 이미 있다.
- dashboard/read model은 accepted bucket table만 source로 보기 때문에 queue 도입 후에도 read side 계약을 크게 바꾸지 않아도 된다.

### 주요 리스크

| 리스크 | 영향 | 완화 |
| --- | --- | --- |
| `201 Created` 의미 상실 | API 계약 혼란 | SQS mode는 `202 Accepted`로 분리 |
| Queue duplicate/out-of-order | 중복 row 또는 conflict | DB unique constraint + same hash no-op |
| Batch catalog race | application/instance 중복 생성 또는 lock contention | worker concurrency 1부터 시작, catalog unique/index 검토 |
| Snapshot 조기 생성 | late bucket 누락 | capture delay + backlog guard + no-backfill 정책 |
| Local/test 복잡도 증가 | 개발 속도 저하 | direct mode/fake queue 기본, LocalStack은 opt-in |
| Lambda 조기 도입 | IAM/VPC/DB connection/cold start 부담 | Spring Boot worker 우선, Lambda는 shared processor 후속 |
| 성능 개선 과장 | portfolio 신뢰 저하 | constrained benchmark로 before/after 측정 |

## 11. 구현 순서 제안

1. ADR/contract 정리
   - Standard vs FIFO, Spring worker vs Lambda, `202` response, late arrival/no-backfill 정책을 닫는다.

2. Enqueue boundary 추가
   - validation과 persistence를 분리한다.
   - `QueuedMetricBucketMessage`, publisher interface, fake publisher를 추가한다.
   - sqs mode controller가 `202`를 반환하게 한다.

3. Worker MVP
   - fake queue 기반 worker와 processor를 만든다.
   - 처음에는 기존 `MetricBucketRepository#insert()`를 재사용해 end-to-end를 닫는다.
   - duplicate/conflict/DLQ matrix를 테스트로 고정한다.

4. Batch writer 최적화
   - batch catalog get-or-create와 batch insert를 구현한다.
   - DB round trip과 insert duration metric을 추가한다.

5. Snapshot delay와 late policy
   - `capture-delay`, fallback threshold, backlog guard를 추가한다.
   - late arrival timeline test를 추가한다.

6. Optional AWS/LocalStack integration
   - 실제 SQS send/receive/delete/visibility/DLQ를 opt-in으로 확인한다.
   - Lambda handler는 필요할 때 shared `MetricBucketBatchProcessor`만 호출하게 얇게 만든다.

7. Performance verification
   - 동일 fixture로 direct mode와 sqs mode를 비교한다.
   - 지표: request p95, enqueue-to-persist latency, DB statement count, batch size, insert throughput, duplicate/conflict 처리.

## 12. 남은 결정

- SQS mode에서 기존 endpoint path를 그대로 쓰고 `202`만 바꿀지, `/api/ingest/v1/buckets:enqueue` 같은 별도 path를 둘지.
- 기본 local profile을 direct로 유지할지, fake queue worker까지 기본으로 켤지.
- batch flush 기본값을 10초로 할지, README에 남은 1분 flush 방향을 유지할지.
- snapshot delay 기본값을 120초로 시작할지, 180초로 더 보수적으로 둘지.
- severe late bucket을 제한 없이 저장할지, retention/max-lateness를 둘지.
- project key verification cache를 이번 범위에 포함할지.
- Lambda를 portfolio demo에서 꼭 보여줄지, Spring worker로 충분한지.
