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

## Epic 12. SQS Buffered Batch Ingest Performance Path

목표: instance별 ingest payload를 SQS에 넣고, Spring Boot portal 내부 SQS consumer가 1분 단위 bounded batch insert로 PostgreSQL에 저장하는 성능 개선 path를 계획한다.

Epic 12는 배포 전 portfolio 관점의 성능 개선 근거를 남기는 epic이다. 성능 개선 검증에 한해서만 테스트 시점의 가장 작은 EC2/RDBMS service instance와 동등한 격리 benchmark 환경을 사용하고, 일반 local/dev/test 환경의 기본 설정을 바꾸지 않는다. Production rollout, autoscaling, Lambda 기본 consumer, queue replay UI, backfill pipeline, complex exactly-once processing은 만들지 않는다.

기본안은 Spring Boot portal 내부 SQS consumer다. Lambda는 ingest volume 증가, web process와 ingest worker 분리, 배포 단위 분리 필요가 생긴 뒤 재검토하는 deferred option으로만 둔다.

### Story 12.1. SQS batch ingest architecture decision

목표:

- SQS buffered batch ingest의 기본 consumer를 Spring Boot portal 내부 worker로 결정하고 Lambda 대안을 ADR 수준으로 비교한다.
- 기존 Spring Boot/JPA/PostgreSQL 중심 구조와 MVP/portfolio 범위 안에서 큐 도입 이유를 설명한다.

Acceptance criteria:

- 기본안은 Spring Boot portal 내부 bounded SQS poller/consumer다.
- Lambda consumer는 IAM, VPC, DB connection, cold start, deployment unit 증가 때문에 deferred option으로 기록한다.
- SQS는 accepted bucket data-plane insert path의 buffer이며 dashboard snapshot/history source가 아니다.
- Redis/PostgreSQL outbox와의 차이를 `infrastructure-input-notes.md` 관점에서 비교한다.
- Local/smoke 환경에서 SQS를 실제 LocalStack으로 둘지, fake queue adapter로 둘지 open decision으로 남긴다.

Non-goal:

- Production autoscaling, multi-region queue, Lambda rollout, Kafka/Kinesis comparison.
- Web process와 worker service의 즉시 분리.

주요 검증 방법:

- ADR review.
- Existing MVC/JPA/PostgreSQL boundary review.
- Infrastructure notes update checklist.

### Story 12.2. Ingest enqueue boundary and message contract

목표:

- 기존 instance ingest envelope를 SQS message로 담는 contract를 정의한다.
- Idempotency key, payload hash, payload size, redaction guard, message attribute 후보를 고정한다.

Acceptance criteria:

- Message body는 기존 `ingest-envelope` 지원 field만 포함하고 unknown/unsupported field는 semantic source로 쓰지 않는다.
- Message는 `projectId` 또는 verified project reference, application identity, bucket boundary, idempotency key, payload hash, receivedAt/enqueuedAt 후보를 포함한다.
- Raw project key, starter credential, Authorization token, webhook URL은 message body/attribute/log에 포함하지 않는다.
- SQS payload size limit을 넘는 envelope 처리 정책을 reject 또는 direct fallback 중 하나로 문서화한다.
- HTTP ingest response가 `201/200` 유지인지 `202 queued` 전환인지 구현 전 open decision으로 남긴다.
- Enqueue 성공은 DB insert 완료나 dashboard read model freshness current를 의미하지 않는다.

Non-goal:

- Starter가 SQS에 직접 쓰는 AWS credential model.
- Arbitrary raw metric import, custom metric map, queue replay UI.

주요 검증 방법:

- Message schema review.
- Secret redaction checklist.
- Idempotency duplicate/conflict scenario table.

### Story 12.3. Spring Boot SQS polling batch insert worker

목표:

- Portal 내부 scheduled/bounded poller가 SQS message를 읽고 1분 단위 또는 bounded flush window로 PostgreSQL batch insert를 수행하는 worker 계획을 닫는다.
- Request path를 DB batch insert로 막지 않고, visibility timeout/retry/delete boundary를 제한한다.

Acceptance criteria:

- Worker는 bounded batch size, max poll duration, 1분 flush cadence 후보를 가진다.
- Batch insert는 `accepted_metric_buckets`의 existing idempotency와 unique constraints를 유지한다.
- Duplicate same payload는 no-second-row success semantics를 유지하고, same key/different hash는 conflict/dead-letter 후보로 분리한다.
- Visibility timeout은 batch insert worst-case보다 길게 잡고, 실패 message는 bounded retry 후 DLQ 후보로 보낸다.
- Worker failure는 portal dashboard read API와 static UI delivery를 막지 않는다.
- Repository/service batching은 lifecycle state, p95/p99, endpoint priority, operational event를 계산하지 않는다.

Non-goal:

- Exactly-once guarantee claim, complex poison-message replay UI, DB trigger/state calculation.
- Separate worker deployment as default.

주요 검증 방법:

- Local/fake SQS worker smoke.
- Batch insert count and transaction boundary review.
- Duplicate/conflict/DLQ scenario review.

### Story 12.4. Snapshot delay and late-data discard policy

목표:

- Batch insert 여유를 위해 snapshot 생성 시점을 약간 늦추고, snapshot cutoff 이후 저장된 late data는 해당 snapshot/history에 backfill하지 않는 정책을 문서화한다.
- Snapshot은 immutable read model history로 유지한다.

Acceptance criteria:

- Snapshot scheduler는 current window cutoff 이후 configurable delay를 둔다.
- Delay 후보는 batch poll cadence보다 충분히 길게 잡되, dashboard current response의 15분 current/baseline 의미를 바꾸지 않는다.
- Snapshot cutoff 이후 DB에 저장된 late bucket은 이미 생성된 snapshot/read model history에 반영하지 않는다.
- Late data discard는 해당 snapshot/history에 대한 정책이며 accepted bucket 저장 자체를 금지한다는 뜻이 아니다.
- Replay/backfill/complex correction pipeline은 non-goal로 명시된다.
- Snapshot detail과 operational history는 저장 당시 read model을 immutable하게 보여주며 current state를 재판정하지 않는다.

Non-goal:

- Backfill UI, replay queue, snapshot recomputation job, immutable snapshot correction workflow.
- Raw snapshot explorer.

주요 검증 방법:

- Timeline example review: bucket end, enqueue, batch insert, snapshot cutoff, late arrival.
- Snapshot immutability checklist.
- History marker copy review.

### Story 12.5. Portfolio performance verification

목표:

- Direct insert path와 SQS-buffered batch insert path를 portfolio 전용 격리 benchmark 환경에서 비교해 성능 개선 근거를 남긴다.
- Benchmark 환경은 테스트 시점의 가장 작은 EC2/RDBMS service instance와 동등한 CPU, memory, database capacity 제약을 명시한다.
- 일반 개발용 Docker/PostgreSQL/local profile, smoke profile, CI 기본 설정은 benchmark 제약의 영향을 받지 않게 분리한다.

Acceptance criteria:

- 비교 항목은 insert count, DB round trip, batch size, 처리량, ingest-to-persist latency, duplicate/conflict behavior다.
- Before/after는 동일 fixture payload와 동일 idempotency distribution으로 비교한다.
- Benchmark runbook은 테스트 시점의 최소 EC2/RDBMS service instance class 또는 동등 사양을 environment manifest로 기록한다.
- Benchmark profile/config/script는 명시적으로 opt-in할 때만 동작하며 일반 개발 profile과 local smoke profile을 변경하지 않는다.
- 결과는 production-grade load test나 autoscaling claim이 아니라 portfolio 전용 constrained benchmark로 표현한다.
- Benchmark output은 secret, raw project key, token, webhook URL을 남기지 않는다.
- Performance report는 accepted bucket source-of-truth와 heartbeat separation 원칙을 재확인한다.

Non-goal:

- Production load test, cost model, autoscaling proof, cloud benchmark suite.
- 일반 개발 환경의 DB/queue/runtime 설정을 가장 작은 instance 제약에 맞춰 낮추는 것.
- Long-retention analytics나 endpoint timeseries 성능 주장.

주요 검증 방법:

- Isolated benchmark runbook과 environment manifest review.
- DB statement/round-trip count comparison.
- Latency distribution summary with bounded sample size.

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
12-1-sqs-batch-ingest-architecture-decision: backlog
12-2-ingest-enqueue-boundary-and-message-contract: backlog
12-3-spring-boot-sqs-polling-batch-insert-worker: backlog
12-4-snapshot-delay-and-late-data-discard-policy: backlog
12-5-portfolio-performance-verification: backlog
epic-12-retrospective: optional
```

## 6. 남길 Open Decision 목록

1. Epic 9/10을 중앙 `epics.md`에 full story detail로 편입할지, 별도 source 문서 링크 방식으로 둘지 결정이 필요하다.
2. Epic 11 Discord alert trigger를 current dashboard read model만 볼지, snapshot-derived operational event 후보도 볼지 결정해야 한다.
3. Epic 11 default cooldown 값을 30분, 60분, 또는 config-only로 둘지 결정해야 한다.
4. Epic 11 alert delivery suppression state를 in-memory로 둘지, lightweight persisted state로 둘지 결정해야 한다.
5. Epic 11 Discord webhook을 global portal config로 둘지, project별 config로 열지 결정해야 한다. MVP 제안은 global/local secret config다.
6. Epic 12에서 HTTP ingest response를 enqueue 성공 기준 `202 queued`로 바꿀지, 기존 `201/200 accepted` semantics를 보존할지 결정해야 한다.
7. Epic 12 local/smoke queue runtime을 LocalStack SQS로 둘지, fake queue adapter로 둘지 결정해야 한다.
8. Epic 12 batch insert worker와 web process를 같은 Spring Boot runtime에 둘지, 후속 별도 worker service 분리를 준비만 할지 결정해야 한다.
9. Epic 12 visibility timeout, max receive count, DLQ policy의 MVP 기본값을 정해야 한다.
10. Epic 12 snapshot delay 기본값과 late-data cutoff 기준을 정해야 한다.
11. Epic 12 portfolio benchmark에서 "가장 작은 EC2/RDBMS service instance와 동등한 환경"의 구체 instance class와 동등 사양 산정 방식을 테스트 시점에 확정해야 한다.

## 7. 구현 착수 전 최소 의사결정

구현 story를 만들기 전에 최소한 아래 결정은 닫아야 한다.

- Epic 11: Discord webhook config scope는 global secret config로 시작한다.
- Epic 11: Alert trigger는 `degraded`, `stale`, `down`, selected high-confidence concern 후보로 제한한다.
- Epic 11: Cooldown 기본값과 storage 방식을 정한다.
- Epic 11: Alert message copy는 host application down, 정상 확정, 복구 완료를 단정하지 않는 문구로 고정한다.
- Epic 12: Spring Boot portal 내부 SQS consumer를 기본안으로 승인한다.
- Epic 12: HTTP ingest response semantics와 DB insert 완료 전 UI/read model 표현을 결정한다.
- Epic 12: SQS message contract와 idempotency conflict 처리 방식을 확정한다.
- Epic 12: Snapshot delay 기본값과 late-data discard cutoff를 확정한다.
- Epic 12: Portfolio 전용 격리 benchmark 방식, 최소 EC2/RDBMS 동등 사양, 비교 지표를 확정한다.
