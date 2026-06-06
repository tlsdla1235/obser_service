---
artifactType: epic-alignment-plan
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: planning-source
date: 2026-06-04
sourcePolicy: latest-user-intent-wins
---

# Epic 11/12 Planning Alignment

## 1. 문서 Drift 요약

이번 정렬에서 확인한 기준 문서는 `current-product-source-of-truth.md`, `epics.md`, `sprint-plan.md`, `figma-make-acceptance-sprint-plan.md`, Epic 9 별도 문서, ingest/read-model/operational-event/state 계약, database/api/infrastructure 문서, `sprint-status.yaml`이다.

현재 중앙 `planning-artifacts/epics.md`는 Epic 1~7과 Post-MVP 후보만 담고 있다. 반면 별도 planning/source 문서와 sprint tracking에는 Epic 9와 Epic 10이 이미 존재한다. 따라서 Epic 11/12를 추가할 때는 중앙 epics 파일의 번호 공백을 "없는 epic"으로 해석하지 않고, Epic 9/10은 별도 source 문서 기준으로 존재한다고 표시해야 한다.

추가로 Story 8.1 route normalization rule change는 story artifact와 route-attribution 계약에는 남아 있지만 `epics.md`와 `sprint-status.yaml`에는 별도 Epic 8 entry가 없다. 이 작업의 핵심 범위는 Epic 11/12 추가이므로 Epic 8을 재구성하지 않고, 번호 정렬 note에만 남긴다.

Epic 9 문서는 product onboarding과 project seed issuance를 source-of-truth로 정의하고, `epics.md`와 `sprint-status.yaml`에는 직접 반영하지 않았다고 적고 있다. 그러나 `api-surface.md`와 `database-schema.md`는 이미 Story 9.2 이후의 public project registration과 starter credential lifecycle을 반영한다. 즉 project creation 관련 open decision은 오래된 `sprint-plan.md`와 `epics.md` 일부 문장에서는 아직 열려 있지만, API/schema 쪽은 Epic 9 방향으로 더 최신이다.

Epic 10은 `figma-make-acceptance-sprint-plan.md`와 `implementation-artifacts/sprint-status.yaml`에 존재하고 현재 in-progress로 추적된다. 하지만 `epics.md`에는 Epic 10 summary가 없다. Epic 11/12는 Epic 10 이후 backlog로 두되, Epic 10의 "새 backend endpoint 없음", "UI-side lifecycle/p95/p99/endpoint priority/snapshot event 계산 없음" 경계를 침범하지 않는다.

`current-product-source-of-truth.md`와 `sprint-plan.md`의 "Alert/Discord surface는 Epic 6 MVP에 넣을 것인가, Post-MVP로 둘 것인가"는 이번 사용자 의도로 갱신된다. 결론은 Epic 6이 아니라 Epic 11에서 과하지 않은 Discord Operational Alert MVP로 다룬다.

`operational-event-history.md`는 alert delivery log를 operational event history와 섞지 말라고 고정한다. Epic 11은 이 경계를 유지해야 한다. Discord alert는 current/read-model 또는 snapshot-derived operational event를 외부 webhook으로 알리는 delivery surface일 뿐, 별도 incident platform이나 event store가 아니다.

`state-semantics.md`, `read-model-contract.md`, `ingest-envelope.md`는 accepted bucket을 metric freshness/state/read model의 data-plane source-of-truth로, heartbeat를 control-plane/liveness signal로 분리한다. Epic 11의 downgrade trigger와 Epic 12의 batch ingest path는 이 원칙을 유지해야 한다.

`infrastructure-input-notes.md`에는 Redis만 queue 후보로 남아 있고 SQS는 아직 없다. Epic 12는 SQS를 새 기본 후보로 추가하되, Lambda consumer가 아니라 Spring Boot portal 내부 SQS consumer를 기본안으로 잡는 ADR story에서 시작해야 한다.

`database-schema.md`는 `accepted_metric_buckets`와 `dashboard_snapshots` 경계를 명확히 한다. Epic 12의 batch insert는 `accepted_metric_buckets` 저장 방식을 성능상 완충하는 path일 뿐, endpoint timeseries table, raw explorer, operational event table, replay/backfill pipeline을 여는 근거가 아니다.

## 2. 반영 위치 제안

이번 정렬에서 직접 반영할 위치는 아래로 제한한다.

- `planning-artifacts/epics.md`: Epic 8~10 번호 정렬 note와 Epic 11/12 summary를 추가한다.
- `implementation-artifacts/sprint-status.yaml`: Epic 11/12 backlog tracking key를 추가한다.
- `planning-artifacts/epic-11-12-operational-alerts-and-sqs-batch-ingest-plan.md`: drift, story별 목표/acceptance/non-goal/검증/open decision을 담는 상세 planning source로 둔다.

구현 착수 전 추가로 정렬해야 할 후보 문서는 아래와 같다.

- `planning-artifacts/current-product-source-of-truth.md`: Alert/Discord open decision을 Epic 11 backlog 결정으로 갱신한다.
- `planning-artifacts/api-surface.md`: Epic 11 alert smoke/verification endpoint가 필요한지, Epic 12 enqueue 응답 semantics가 기존 ingest API status와 어떻게 다른지 결정 후 반영한다.
- `planning-artifacts/contracts/ingest-envelope.md`: Epic 12 SQS message contract, idempotency key, payload hash, redaction guard를 별도 section으로 추가한다.
- `planning-artifacts/contracts/read-model-contract.md`: Epic 11 alert trigger가 state/read model을 어떻게 읽고 무엇을 단정하지 않는지 guard를 추가한다.
- `planning-artifacts/contracts/operational-event-history.md`: Discord delivery surface와 operational event history 분리 원칙을 Epic 11 기준으로 더 명시한다.
- `planning-artifacts/database-schema.md`: Epic 12 batch insert가 schema 변경인지, repository/service batching만으로 충분한지 결정 후 반영한다.
- `planning-artifacts/infrastructure-input-notes.md`: SQS, Spring Boot consumer 기본안, Lambda deferred option, local smoke 대체 전략을 추가한다.

## 3. Epic 11 추가 제안

## Epic 11. Discord Operational Alert MVP

목표: 서버 metric state가 downgrade되거나 error/degraded concern이 발생했을 때 Discord webhook으로 짧고 안전한 운영 알림을 보낸다.

Epic 11은 incident platform이 아니다. Multi-channel escalation, PagerDuty류 on-call, acknowledgement lifecycle, alert delivery history UI, 복잡한 suppression policy, per-user notification setting은 만들지 않는다.

Downgrade 의미는 `state-semantics.md`의 lifecycle state와 degraded hysteresis를 따른다. Alert copy는 host application down, 정상 확정, 복구 완료를 단정하지 않고 accepted bucket axis와 starter heartbeat axis를 분리한다.

### Story 11.1. Alert trigger semantics and Discord MVP ADR

목표:

- Discord alert의 trigger source를 state/read model/service-layer 판단으로 고정한다.
- `degraded`, `stale`, `down`, selected high-confidence concern을 alert 후보로 정의하되, host application down을 확정하지 않는 copy boundary를 정한다.
- Operational event history와 alert delivery log가 섞이지 않는다는 결정을 남긴다.

Acceptance criteria:

- Trigger 후보는 `state-semantics.md`의 7-value state와 degraded hysteresis를 따른다.
- Heartbeat success/failure/missing 자체는 Discord alert trigger가 아니다.
- `down`은 metric data-plane outage 후보로 표현하고 host process down으로 쓰지 않는다.
- `recovery.isRecovering=true`는 복구 완료 alert가 아니라 관찰 중 상태로만 다룬다.
- Alert source와 operational event history source가 어떤 read model/snapshot field를 읽는지 문서화된다.

Non-goal:

- Incident lifecycle, ack/resolve workflow, delivery history UI, user/team notification preference.
- 새 operational event table이나 durable alert event store.

주요 검증 방법:

- State transition matrix review.
- Forbidden copy checklist: "앱 다운 확정", "정상 복구 완료", "원인 확정" 표현 부재 확인.
- `operational-event-history.md`와의 source/delivery boundary 교차 검토.

### Story 11.2. Discord webhook configuration and secret redaction guard

목표:

- Discord webhook URL을 secret config로만 받는 경계를 정의한다.
- Logs, errors, API response, test fixtures에 webhook URL과 token-like value가 노출되지 않도록 redaction 기준을 정한다.

Acceptance criteria:

- Webhook URL은 환경변수 또는 secret-bearing config로만 주입된다.
- Missing/disabled config에서는 alert delivery가 no-op 또는 disabled 상태로 동작한다.
- Webhook URL, raw payload secret, auth token은 log/error/response/snapshot/history에 남지 않는다.
- Smoke/test fixture에는 실제 webhook URL을 넣지 않는다.
- Secret-bearing verification request는 `no-store` 또는 local-only manual runbook으로 제한한다.

Non-goal:

- Secret manager 통합 전체 구현, UI 기반 webhook 관리, per-project webhook 설정.
- Alert delivery history UI.

주요 검증 방법:

- Redaction static review checklist.
- Local disabled-config smoke.
- Failure log sample에서 webhook host/path/token 미노출 확인.

### Story 11.3. Minimal Discord alert delivery and payload copy

목표:

- Discord webhook으로 project/application/environment/state/summary/deep link를 담은 최소 payload를 보낸다.
- Payload는 operator가 dashboard나 snapshot/history로 들어갈 수 있는 단서를 주되, 원인/복구/정상 여부를 과하게 단정하지 않는다.

Acceptance criteria:

- Payload에는 state code/label, application identity, generatedAt/observedAt, short reason, recommended next check, dashboard 또는 snapshot deep link 후보가 포함된다.
- Alert text는 accepted bucket metric axis와 starter heartbeat/connection axis를 합쳐 host health로 표현하지 않는다.
- Error/degraded concern은 endpoint evidence가 있을 때 low-cardinality `method route`만 포함하고 raw path/query/trace/per-request sample을 포함하지 않는다.
- Discord delivery failure는 portal read model 생성이나 ingest acceptance를 막지 않는다.
- Delivery는 best-effort이며 retry boundary는 bounded로 남긴다.

Non-goal:

- Rich incident timeline, multi-message thread, delivery log UI, arbitrary payload template editor.
- Endpoint p95/p99 alert 기준.

주요 검증 방법:

- Payload snapshot/golden sample review.
- Discord disabled/failing webhook smoke.
- Raw secret/raw path/query string absence scan.

### Story 11.4. Cooldown, throttle, and alert spam guard

목표:

- 같은 application/state/primary concern에 대한 반복 Discord alert를 최소 cooldown으로 제한한다.
- Alert fatigue를 줄이되 durable incident platform 수준의 lifecycle은 만들지 않는다.

Acceptance criteria:

- 같은 `project + application + stateCode + primaryRuleId/endpointKey` 후보는 configurable cooldown 안에서 반복 전송하지 않는다.
- Default cooldown 후보는 30~60분 범위로 문서화하고 구현 전 최종 결정한다.
- Cooldown state는 in-memory 또는 lightweight persistence 중 MVP 선택지를 비교한다.
- Cooldown은 dashboard state 자체를 바꾸지 않고 delivery 여부만 제한한다.
- Suppressed alert는 UI history event로 만들지 않는다.

Non-goal:

- Durable dedupe across multi-node production, acknowledgement, escalation policy, alert owner assignment.
- Alert delivery history UI.

주요 검증 방법:

- Same trigger repeated smoke.
- Cooldown expiry smoke.
- State/read model unchanged guard.

### Story 11.5. Discord alert smoke verification and operator runbook

목표:

- Local/smoke 환경에서 Discord alert config, downgrade/degraded trigger, cooldown, redaction을 한 번에 검증하는 runbook을 남긴다.

Acceptance criteria:

- Disabled config, valid webhook config, failing webhook config가 각각 기대 동작을 가진다.
- Degraded 또는 stale/down 후보 fixture로 Discord message가 생성되는지 확인한다.
- Message copy는 host application down, 정상 확정, 복구 완료를 단정하지 않는다.
- Webhook URL과 project/starter credential은 repository 문서, log, fixture에 남지 않는다.
- Smoke는 production-grade incident drill이 아니라 portfolio/demo verification으로 정의된다.

Non-goal:

- Production on-call rehearsal, synthetic monitor fleet, multi-channel alert QA.

주요 검증 방법:

- Operator runbook checklist.
- Local smoke trigger.
- Log/fixture redaction scan.

## 4. Epic 12 추가 제안

## Epic 12. SQS Buffered Ingest Transition

목표: portal HTTP ingest request path에서는 검증과 enqueue까지만 수행하고, Spring Boot portal 내부 SQS worker가 bounded batch로 `accepted_metric_buckets` 저장을 처리하도록 전환한다.

Epic 12는 배포 전 portfolio 관점의 성능 개선 근거를 남기는 epic이다. 성능 개선 주장은 두 단계로 분리한다.

- Phase 1 request latency 개선: HTTP request thread에서 bucket DB insert와 flush를 제거하고 SQS enqueue까지로 줄이는 개선이다. DB batch throughput 개선을 주장하지 않는다.
- Phase 2 DB batch throughput 개선: worker 쪽 batch writer가 DB round trip, transaction, catalog lookup/last-seen update를 줄인 뒤에만 주장한다.

기본 consumer는 Spring Boot portal 내부 worker로 고정한다. Lambda consumer는 이번 Epic 12 구현 범위에 넣지 않고, IAM/VPC/RDS connection/cold start/deployment unit 분리가 필요해진 뒤 검토할 deferred/non-goal로만 남긴다.

Epic 12의 공통 guardrail:

- SQS는 accepted bucket data-plane insert path의 buffer이며 dashboard snapshot/history source가 아니다.
- SQS Standard의 at-least-once/out-of-order 처리는 duplicate/no-op, conflict, DLQ 분류와 DB unique constraint로 흡수한다.
- SQS payload size limit 초과 envelope는 enqueue 전에 `413 Payload Too Large`로 reject한다. Direct fallback은 성능/의미 혼선을 만들 수 있어 Epic 12 path로 열지 않는다.
- Malformed message, same idempotency key/different payload hash, same instance bucket/different idempotency key conflict는 retry로 회복되지 않는 DLQ 대상으로 둔다.
- Snapshot cutoff 이후 도착한 late bucket은 accepted bucket 저장은 허용하되 이미 생성된 snapshot/history에는 backfill하지 않는다.
- stale/down은 accepted bucket data freshness이며 queue lag나 worker backlog를 host application down으로 표현하지 않는다.

### Story 12.1. Architecture and Contract Decision

목표:

- SQS buffered ingest의 architecture decision과 계약 변경 범위를 후속 story의 ADR/contract gate로 닫는다.
- Spring Boot portal 내부 worker를 기본 consumer로 확정하고 Lambda는 deferred/non-goal로만 기록한다.
- Enqueue failure, payload size, DLQ taxonomy, snapshot cutoff, queue lag naming, cutover/rollback 기준을 후속 story가 임의 해석하지 못하게 결정 ledger로 남긴다.

Acceptance criteria:

- Standard queue + DLQ, Spring Boot portal 내부 worker, fake queue 기본 local/test adapter, LocalStack opt-in integration 전략이 결정으로 문서화된다.
- `ingest-envelope`, `read-model-contract`, `time-buckets`, `infrastructure-input-notes`에서 Epic 12가 손대는 계약과 손대지 않는 계약이 분리된다.
- Phase 1 request latency 개선과 Phase 2 DB batch throughput 개선 주장이 분리된다.
- SQS payload size limit 초과는 enqueue 전 reject로 닫고 direct fallback은 열지 않으며, payload too large는 `413`, SQS unavailable/config/serialization failure는 `503`으로 분리된다.
- `202 queued`는 enqueue 성공일 때만 허용되고, redaction guard, queue lag diagnostic, snapshot cutoff, DLQ 분류가 후속 story의 source-of-truth로 연결된다.
- SQS mode는 feature flag/config opt-in, rollback은 direct mode 복귀로 두며, Story 12.3 worker MVP는 Story 12.4 lag semantics 없이 사용자-facing rollout하지 않는다.
- Lambda consumer는 구현 story나 acceptance criteria에 들어가지 않고 deferred/non-goal로만 남는다.

Non-goal:

- Lambda handler, separate worker deployment, production autoscaling, multi-region queue, Kafka/Kinesis 비교.
- Dashboard/read-model/source semantics 변경 구현.

12.1에서 닫힌 결정:

- Spring Boot portal 내부 worker를 기본 consumer로 둔다.
- Lambda consumer는 deferred/non-goal이다.
- Local smoke는 fake queue adapter를 기본으로 두고, LocalStack SQS는 opt-in integration으로 둔다.
- SQS payload size 초과는 enqueue 전 `413` reject로 닫고 direct fallback을 열지 않는다.
- Enqueue 성공일 때만 `202 queued`를 허용하고 SQS unavailable/config/serialization failure는 `503`으로 둔다.
- SQS mode는 opt-in이고 rollback은 direct mode 복귀다.
- Story 12.3 worker MVP만으로는 사용자-facing rollout하지 않는다.

주요 검증 방법:

- ADR/contract review.
- MVC/JPA/PostgreSQL boundary review.
- Lambda deferred/non-goal 문구 검색.

위험과 완화책:

- 큐 도입이 read model source 변경처럼 해석될 위험은 SQS를 data-plane write buffer로만 정의해 완화한다.
- 성능 개선이 과장될 위험은 Phase 1/Phase 2 지표를 분리해 완화한다.

### Story 12.2. Ingest Enqueue Boundary

목표:

- 기존 HTTP ingest acceptance를 validation/payload hash/enqueue boundary로 분리한다.
- Enqueue 성공이 DB insert 완료나 dashboard freshness current를 뜻하지 않는 API 의미를 고정한다.

Acceptance criteria:

- Request path는 project key verification, envelope validation, idempotency key format check, payload hash 계산, SQS message 생성까지만 포함한다.
- Message body/attribute에는 verified project reference, application identity, bucket boundary, idempotency key, payload hash, receivedAt/enqueuedAt 후보만 포함하고 raw project key, starter credential, Authorization token, webhook URL은 포함하지 않는다.
- SQS payload size limit 초과 envelope는 enqueue 전에 `413 Payload Too Large`로 reject하며 direct fallback을 열지 않는다.
- SQS mode 응답은 실제 enqueue 성공 이후 `202 queued`로 반환하며, `201 Created`와 혼동되지 않도록 response body field를 Story 12.2에서 세부화한다.
- Enqueue 실패는 starter retry가 가능한 non-2xx로 매핑한다.

Non-goal:

- Starter가 SQS에 직접 쓰는 AWS credential model.
- Worker poll/insert 구현, batch writer 최적화, queue replay UI.

구현 전 닫아야 할 결정:

- 기존 `/api/ingest/v1/buckets` 응답을 mode별로 바꿀지, enqueue 전용 endpoint를 둘지.
- `202 queued` response body field와 `messageId` 노출 여부.
- Queue message schema version과 canonical payload hash 기준.

주요 검증 방법:

- Message schema/golden JSON review.
- Secret redaction checklist.
- Controller status mapping review.

위험과 완화책:

- `202`가 저장 완료로 오해될 위험은 response/copy/API 문서에서 queued semantics를 분리해 완화한다.
- Payload size 초과가 worker poison message가 될 위험은 enqueue 전 size guard로 완화한다.

### Story 12.3. Spring Boot SQS Worker MVP and Idempotency

목표:

- Portal 내부 Spring Boot worker가 SQS message를 poll/process/delete하는 MVP path를 닫는다.
- Duplicate/no-op/conflict/malformed/DLQ semantics를 DB unique constraint와 함께 고정한다.

Acceptance criteria:

- Worker는 bounded batch size, max batch age, long polling, visibility timeout, max receive count, DLQ policy를 가진다.
- MVP writer는 기존 `MetricBucketRepository#insert()` 재사용을 허용해 end-to-end를 먼저 닫되, DB unique constraint를 최종 idempotency guard로 유지한다.
- Same idempotency key + same payload hash는 no-op success로 ack/delete한다.
- Same idempotency key + different payload hash, same instance/bucket start + different key, malformed message는 retry로 회복되지 않는 conflict/malformed DLQ 대상으로 분류한다.
- Transient DB failure는 message를 delete하지 않고 visibility timeout 후 retry되게 한다.
- Worker failure는 dashboard read API, static UI delivery, heartbeat endpoint를 막지 않는다.

Non-goal:

- Exactly-once guarantee claim, Lambda consumer, separate worker deployment, replay UI.
- Lifecycle state, p95/p99, endpoint priority, operational event 계산.

구현 전 닫아야 할 결정:

- Same payload duplicate를 판정하기 위해 필요한 stored payload hash/identity projection API.
- Visibility timeout, max receive count, sanitized DLQ payload shape의 exact 기본값과 field.
- Partial batch failure ack/delete 전략.

주요 검증 방법:

- Fake queue worker smoke.
- Duplicate/no-op/conflict/DLQ matrix test.
- Transient DB failure retry/delete boundary test.

위험과 완화책:

- SQS duplicate/out-of-order로 중복 row가 생길 위험은 DB unique constraint와 no-op success로 완화한다.
- Poison message가 무한 retry될 위험은 malformed/conflict DLQ 분류와 max receive count로 완화한다.

### Story 12.4. Snapshot Delay and Pipeline Lag Semantics

목표:

- SQS enqueue-to-persist delay를 흡수할 snapshot delay와 cutoff 정책을 고정한다.
- stale/down과 queue lag/worker backlog를 혼동하지 않는 read-model semantics를 문서화한다.

Acceptance criteria:

- Snapshot scheduler는 target current window cutoff 이후 configurable delay를 둔다.
- Delay는 worker batch cadence보다 충분히 길게 잡되 dashboard current/baseline 15분 의미를 바꾸지 않는다.
- `persistedAt > snapshotCutoffAt` late bucket은 accepted bucket에는 저장될 수 있지만 이미 생성된 snapshot/history에는 backfill하지 않는다.
- Queue lag diagnostic은 stale/down state를 직접 바꾸지 않고 별도 operational metric 또는 diagnostic으로 남는다.
- Fallback staleness threshold는 `60분 + snapshotDelay + grace` 후보로 재계산된다.
- Snapshot detail/history는 저장 당시 read model을 immutable하게 보여주며 current state를 재판정하지 않는다.

Non-goal:

- Snapshot recomputation job, late-data backfill pipeline, replay queue, raw snapshot explorer.
- Queue lag를 host application down 또는 telemetry unreachable로 표시하는 UI 의미 변경.

구현 전 닫아야 할 결정:

- Snapshot delay 기본값과 max snapshot delay/backlog guard.
- Late bucket metric 이름과 operator-facing diagnostic 노출 위치.
- Fallback threshold grace 값.

주요 검증 방법:

- Timeline example review.
- Snapshot immutability checklist.
- stale/down vs queue lag copy/static review.

위험과 완화책:

- Snapshot이 너무 일찍 생성돼 late bucket이 누락될 위험은 delay/cutoff/backlog guard로 완화한다.
- Queue backlog를 host health 문제로 오해할 위험은 read-model contract의 accepted bucket axis와 queue diagnostic axis를 분리해 완화한다.

### Story 12.5. Batch Writer and Performance Verification

목표:

- Phase 2 batch writer 최적화와 portfolio 성능 검증을 한 story로 묶어 DB throughput 개선 근거를 남긴다.
- 일반 local/dev/test 기본 설정은 benchmark 제약의 영향을 받지 않게 분리한다.

Acceptance criteria:

- Batch writer는 command grouping, catalog get-or-create/last-seen update 축소, JDBC batch 또는 `ON CONFLICT` 후보를 비교해 선택한다.
- DB batch throughput 개선 주장은 batch writer 적용 후 DB statement/round-trip, persist duration, throughput 지표로만 제시한다.
- Phase 1 request latency 지표와 Phase 2 DB throughput 지표는 report에서 분리된다.
- Benchmark는 동일 fixture, 동일 idempotency distribution, 동일 constrained environment manifest로 direct path와 SQS-buffered path를 비교한다.
- Benchmark output은 secret, raw project key, token, webhook URL, raw payload를 남기지 않는다.
- 결과는 production-grade load test, autoscaling proof, cost model이 아니라 portfolio 전용 constrained benchmark로 표현한다.

Non-goal:

- Production load test, autoscaling proof, cloud benchmark suite, long-retention analytics, endpoint timeseries.
- 일반 개발 환경의 DB/queue/runtime 설정을 가장 작은 instance 제약에 맞춰 낮추는 것.

구현 전 닫아야 할 결정:

- JDBC batch vs JPA `saveAll` vs PostgreSQL `ON CONFLICT` 전략.
- Benchmark 환경 manifest의 최소 EC2/RDBMS 동등 사양 산정 방식.
- 성능 report에 남길 지표와 pass/fail threshold.

주요 검증 방법:

- DB statement/round-trip count comparison.
- Latency distribution and ingest-to-persist lag summary.
- Duplicate/conflict behavior under batch load.

위험과 완화책:

- Request latency 개선을 DB throughput 개선으로 오해할 위험은 report에서 Phase 1/Phase 2를 분리해 완화한다.
- Batch conflict가 transaction 전체를 흔들 위험은 conflict row 재처리 또는 `ON CONFLICT` 기반 분류 전략으로 완화한다.

### Story 12.6. Benchmark Evidence Run and Report

목표:

- Story 12.5의 benchmark harness/guard를 사용해 실제 local 또는 격리 benchmark 수치와 sanitized report artifact를 남긴다.
- Phase 1 request latency evidence와 Phase 2 DB batch throughput evidence를 같은 결론이나 단일 개선율로 합치지 않는다.

Acceptance criteria:

- Primary fixture는 `applicationCount=1`, `instanceCount=30`이며, synthetic instance임을 report에 명시한다.
- Direct insert path와 SQS enqueue path는 같은 fixture, 같은 idempotency distribution, 같은 DB 초기 상태로 Phase 1 request latency를 측정한다.
- Worker MVP message-by-message persistence와 batch writer persistence는 같은 fixture와 DB 초기 상태로 Phase 2 DB throughput을 측정한다.
- Report는 실제 p50/p95/p99, statement count, persist duration, persisted buckets/sec 같은 정량 수치를 포함한다.
- Benchmark output은 raw project key, starter credential, token, AWS credential/session token, queue URL, raw payload를 남기지 않는다.
- 결과는 production-grade load test, autoscaling proof, cost model, dashboard UI performance claim이 아니라 local/isolated benchmark evidence로 표현한다.

Non-goal:

- Production load test, autoscaling proof, cloud benchmark suite, Lambda consumer, separate worker deployment.
- 일반 local/dev/test/smoke/CI profile을 benchmark resource constraint로 낮추는 것.

주요 검증 방법:

- Opt-in benchmark command smoke.
- Benchmark output redaction scan.
- Phase 1/Phase 2 report section separation test.
- Fixture shape and DB reset/seed test.

## 5. sprint-status.yaml Backlog Entry 제안

`implementation-artifacts/sprint-status.yaml`에는 Epic 10 이후 아래 entry를 backlog로 둔다.

```yaml
epic-11: backlog
11-1-alert-trigger-semantics-and-discord-mvp-adr: backlog
11-2-discord-webhook-configuration-and-secret-redaction-guard: backlog
11-3-minimal-discord-alert-delivery-and-payload-copy: backlog
11-4-cooldown-throttle-and-alert-spam-guard: backlog
11-5-discord-alert-smoke-verification-and-operator-runbook: backlog
epic-11-retrospective: optional

epic-12: backlog
12-1-architecture-and-contract-decision: done
12-2-ingest-enqueue-boundary: backlog
12-3-spring-boot-sqs-worker-mvp-and-idempotency: backlog
12-4-snapshot-delay-and-pipeline-lag-semantics: backlog
12-5-batch-writer-and-performance-verification: backlog
12-6-benchmark-evidence-run-and-report: backlog
epic-12-retrospective: optional
```

## 6. 남길 Open Decision 목록

1. Epic 9/10을 중앙 `epics.md`에 full story detail로 편입할지, 별도 source 문서 링크 방식으로 둘지 결정이 필요하다.
2. Epic 11 Discord alert trigger를 current dashboard read model만 볼지, snapshot-derived operational event 후보도 볼지 결정해야 한다.
3. Epic 11 default cooldown 값을 30분, 60분, 또는 config-only로 둘지 결정해야 한다.
4. Epic 11 alert delivery suppression state를 in-memory로 둘지, lightweight persisted state로 둘지 결정해야 한다.
5. Epic 11 Discord webhook을 global portal config로 둘지, project별 config로 열지 결정해야 한다. MVP 제안은 global/local secret config다.
6. Story 12.2에서 `202 queued` response body field와 SQS `messageId` 노출 여부를 정해야 한다.
7. Story 12.3에서 visibility timeout, max receive count, sanitized DLQ payload shape의 exact 기본값과 field를 정해야 한다.
8. Story 12.4에서 snapshot delay exact default, fallback grace, queue lag diagnostic 위치를 정해야 한다.
9. Story 12.5에서 benchmark manifest와 pass/fail threshold 또는 evidence-only 판정 기준을 정해야 한다.

## 7. 구현 착수 전 최소 의사결정

구현 story를 만들기 전에 최소한 아래 결정은 닫아야 한다.

- Epic 11: Discord webhook config scope는 global secret config로 시작한다.
- Epic 11: Alert trigger는 `degraded`, `stale`, `down`, selected high-confidence concern 후보로 제한한다.
- Epic 11: Cooldown 기본값과 storage 방식을 정한다.
- Epic 11: Alert message copy는 host application down, 정상 확정, 복구 완료를 단정하지 않는 문구로 고정한다.
- Epic 12: Story 12.1에서 Spring Boot portal 내부 worker, Lambda deferred/non-goal, SQS mode opt-in/direct rollback, enqueue success only `202`, enqueue failure `503`, payload too large `413`, no direct fallback, fake queue 기본/LocalStack opt-in, 12.3-only rollout 금지를 닫았다.
- Epic 12: 후속 story에서는 12.2 response body/messageId, 12.3 visibility timeout/max receive count/DLQ payload shape, 12.4 snapshot delay/fallback grace/queue lag diagnostic 위치, 12.5 benchmark manifest/pass-fail threshold만 세부화한다.
