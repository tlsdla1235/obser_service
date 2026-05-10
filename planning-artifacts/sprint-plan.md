---
artifactType: sprint-plan
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: epic-2-ready-for-dev
date: 2026-05-10
---

# Sprint Plan - Epic 2 Starter Direct Ingest Producer

## 1. Sprint Planning 결과 요약

이번 Sprint는 **Epic 2. Starter Direct Ingest Producer**를 개발 가능한 story 단위로 닫는다.

Sprint 목표는 사용자가 starter를 host Spring Boot app에 추가했을 때, host app request path를 portal 장애와 분리한 상태로 30초 UTC bucket을 비동기 전송할 수 있게 만드는 것이다.

이번 계획의 기준은 active MVC 산출물이다.

- active 구현 기준은 `planning-artifacts/`와 `implementation-artifacts/`다.
- `archive/hexagonal-version/`의 Hexagonal 산출물은 구현 기준으로 사용하지 않는다.
- `bmad-restart-context-pack/`는 제품 문제와 UX 의도 참고용이다.
- 최종 아키텍처 선택은 **Traditional MVC + Service/Repository Layering** 하나다.
- starter는 host app 안에 붙는 library/starter module이며 MVC web controller를 만들지 않는다.
- starter에는 `domain`, `application`, `port`, `adapter` package 구조를 만들지 않는다.

## 2. Epic 1 Closure 상태

Epic 1의 portal foundation stories는 완료됐다.

| Story | 현재 상태 | Epic 2 영향 |
|---|---|---|
| 1.1 Starter Package Skeleton | review | `observability-spring-boot-starter` module/package skeleton이 구현되어 Epic 2 선행 조건을 충족한다. |
| 1.2 Portal MVC Package Skeleton | done | portal module/build 기준이 준비됐다. |
| 1.3 MVC Layer Guard Test | done | portal MVC 경계가 테스트로 고정됐다. |
| 1.4 Portal Physical Schema Foundation | done | catalog schema foundation이 준비됐다. |

Epic 1 전체 status는 Story 1.1이 `done`으로 승인될 때 닫는다. 다만 Epic 2 Sprint Planning은 Story 1.1의 review 산출물을 전제로 진행한다. Story 2.1은 starter bootstrap을 다시 포함하지 않는다.

## 3. Sprint Goal

Epic 2 Sprint Goal:

> Starter가 Micrometer 기반 low-cardinality signal을 수집하고, UTC 30초 bucket으로 집계한 뒤, bounded queue와 background flush worker를 통해 ingest envelope를 전송할 수 있는 direct ingest producer 경로를 만든다. host app request path는 portal timeout/down 상황에서도 network timeout을 기다리지 않는다.

완료 판단은 아래 acceptance로 닫는다.

- request path에서 portal network call이 발생하지 않는다.
- portal timeout/down 또는 queue overflow가 host business request를 막지 않는다.
- route/tag는 ingest envelope 작성 전에 low-cardinality 정책을 통과한다.
- bucket boundary는 UTC 30초 기준으로 `time-buckets` contract와 일치한다.
- payload는 `ingest-envelope`와 `metric-taxonomy` contract를 따른다.
- Prometheus scrape, pull metric query, arbitrary query UI는 MVP 경로에 없다.

## 4. 이번 Sprint에 포함할 범위

포함:

- Micrometer observation binding
- route normalization and low-cardinality guard
- 30초 UTC bucket rollup service
- bounded queue, async flush worker, retry/backoff, drop policy
- ingest envelope builder service
- starter MVP negative path guard
- starter module architecture guard 보강
- request path non-blocking proof test

## 5. 이번 Sprint에서 제외할 범위

제외:

- starter module/package bootstrap 재구현
- portal ingest controller 구현
- portal ingest acceptance 저장 구현
- project key verification service 구현
- accepted bucket repository 구현
- `accepted_metric_buckets` migration
- ingest idempotency conflict 저장/검증 구현
- `dashboard_snapshots` migration
- dashboard read model snapshot 저장/조회
- p95 histogram merge service
- lifecycle state service
- insight rule service
- endpoint priority read model
- dashboard query API
- dashboard UI integration
- Prometheus pull metric path, scrape config, PromQL query, arbitrary query UI
- high-cardinality custom metric/tag ingestion
- logs, traces, span search, large tenancy control plane
- durable outbox, Kafka, Redis, 별도 worker runtime

Portal ingest acceptance와 persistence는 Epic 3에서 닫는다. Dashboard read model, p95, state, rule, endpoint priority 계산은 Epic 4/5에서 닫는다.

## 6. Story 범위와 권장 구현 순서

권장 구현 순서는 아래와 같다.

1. Story 2.1 - Micrometer Observation Binding
2. Story 2.2 - Route Normalization and Low-Cardinality Guard
3. Story 2.3 - Bucket Rollup Service
4. Story 2.4 - Async Flush Worker
5. Story 2.5 - Ingest Envelope Builder Service
6. Story 2.6 - Negative Path Guard

| Story | 파일 | 핵심 경계 | 선행 조건 |
|---|---|---|---|
| 2.1 | `planning-artifacts/stories/2-1-micrometer-observation-binding.md` | Micrometer/Spring signal을 starter 내부 observation input으로 변환한다. Starter bootstrap은 제외한다. | Story 1.1 review 산출물 |
| 2.2 | `planning-artifacts/stories/2-2-route-normalization-and-low-cardinality-guard.md` | raw path와 high-cardinality tag를 차단하고 normalized route만 다음 단계로 넘긴다. | 2.1 |
| 2.3 | `planning-artifacts/stories/2-3-bucket-rollup-service.md` | app summary와 endpoint histogram bucket을 UTC 30초 boundary로 집계한다. | 2.2 |
| 2.4 | `planning-artifacts/stories/2-4-async-flush-worker.md` | bounded queue와 background worker로 request path와 portal network call을 분리한다. | 2.3 |
| 2.5 | `planning-artifacts/stories/2-5-ingest-envelope-builder-service.md` | `ingest-envelope` contract payload와 idempotency header를 만든다. | 2.2, 2.3, 2.4 |
| 2.6 | `planning-artifacts/stories/2-6-negative-path-guard.md` | Prometheus/scrape/query UI/high-cardinality MVP 역행 경로가 없음을 테스트한다. | 2.1-2.5 |

## 7. Story Split 결정

### 7.1 Micrometer Observation Binding

Story 2.1은 binding 자체만 담당한다.

- `observability-spring-boot-starter` module은 Story 1.1 결과를 사용한다.
- package bootstrap, Gradle skeleton, smoke test 재작업은 scope 밖이다.
- Spring/Micrometer 객체는 `spring` package 경계에서 starter model/service input으로 변환한다.
- request path에서 portal HTTP client, queue flush, envelope serialization을 호출하지 않는다.

### 7.2 Route Normalization and Low-Cardinality Guard

Story 2.2는 ingest envelope보다 먼저 low-cardinality 정책을 고정한다.

- 허용 tag는 `application`, `environment`, `instance`, `method`, `normalized route`로 제한한다.
- raw path parameter, query string, user id, tenant id, session id, trace id, arbitrary label은 payload 후보에 남기지 않는다.
- framework route pattern 또는 configured allowlist를 우선하고, 안전한 route를 얻지 못하면 bounded fallback을 사용한다.

### 7.3 Bucket Rollup Service

Story 2.3은 local rollup만 담당한다.

- bucket duration은 30초다.
- start/end는 UTC 30초 boundary에 맞춘다.
- app summary와 endpoint histogram bucket은 normalized route 기준으로만 집계한다.
- portal network call과 ingest envelope serialization은 하지 않는다.

### 7.4 Async Flush Worker

Story 2.4는 non-blocking acceptance의 핵심 story다.

- request path는 bounded queue enqueue 또는 local record만 수행한다.
- HTTP timeout, retry, backoff는 background worker에서만 실행한다.
- queue overflow는 configured drop policy로 처리하고 host business flow를 계속 진행한다.
- durable outbox/Kafka/별도 runtime은 만들지 않는다.

### 7.5 Ingest Envelope Builder Service

Story 2.5는 starter-side payload contract를 닫는다.

- `schemaVersion`은 `1.0`이다.
- `bucket.durationSeconds`는 `30`이다.
- endpoint route는 normalized route만 허용한다.
- free tag map, arbitrary custom metric map, raw timeseries array는 만들지 않는다.
- `Idempotency-Key` 후보를 만든다.
- portal 저장, 중복 판정, conflict 응답은 Epic 3 scope다.

### 7.6 Negative Path Guard

Story 2.6은 MVP 경로 역행을 테스트로 막는다.

- starter에 web controller를 만들지 않는다.
- Prometheus registry/scrape/export/query UI dependency나 resource를 MVP 경로에 추가하지 않는다.
- `application`, `port`, `adapter` package를 만들지 않는다.
- arbitrary metric query나 high-cardinality custom tag ingestion 경로를 만들지 않는다.

## 8. Non-Blocking 증명 테스트

Epic 2의 핵심 acceptance는 Story 2.4에서 아래 테스트로 증명한다.

| Test | 증명할 내용 |
|---|---|
| `StarterNonBlockingIngestTest` | fake portal client가 timeout/down 상태여도 request path 호출이 network timeout을 기다리지 않는다. |
| `BoundedMetricQueueOverflowTest` | queue full 상태에서 drop policy가 적용되고 host request path가 예외/대기 없이 반환된다. |
| `MetricBucketFlushWorkerTest` | retry/backoff는 background worker thread에서만 실행된다. |
| starter architecture guard | request path integration component가 `client.http` 구현을 직접 호출하지 않는다. |

테스트는 wall-clock 임계값에만 의존하지 않는다. fake client의 blocking delay보다 request path 반환이 먼저 일어나는지, HTTP client 호출 thread가 request thread와 분리되는지, enqueue overflow가 host flow에 전파되지 않는지를 함께 확인한다.

## 9. Low-Cardinality Guard Acceptance

Story 2.2와 Story 2.5는 아래 acceptance를 공유한다.

- `route`는 normalized route만 가능하다.
- raw path parameter 값은 payload 후보에 남지 않는다.
- query string은 route/tag에 포함되지 않는다.
- `userId`, `tenantId`, `sessionId`, `traceId`, arbitrary label은 starter payload 후보에서 제거되거나 거부된다.
- endpoint key는 `method + normalized route`만 사용한다.
- endpoint 목록은 bounded top-N 또는 configured allowlist 안에서만 생성된다.
- envelope builder는 low-cardinality guard를 통과하지 않은 endpoint를 직렬화하지 않는다.

## 10. Epic 3/4/5 Handoff Boundary

Epic 3으로 넘기는 것:

- `POST /api/ingest/v1/buckets` portal controller
- `X-OBS-Project-Key` verification
- `IngestAcceptanceService`
- `accepted_metric_buckets` migration and repository
- payload hash 저장
- duplicate success와 idempotency conflict handling

Epic 4/5로 넘기는 것:

- current/baseline window service
- freshness, stale, down, degraded state 판단
- histogram merge 기반 p95 계산
- insight rule ranking
- endpoint priority read model
- dashboard snapshot 저장/조회
- dashboard query API
- UI read model 표시

Epic 2는 starter producer까지만 닫는다.

## 11. Sprint Status 기대값

이 Sprint Planning 완료 후 기대 status:

- `epic-2`: `in-progress`
- `2-1`부터 `2-6`: story file 생성으로 `ready-for-dev`
- `epic-2-retrospective`: `optional`

Story 2.1이 첫 구현 대상이다.

## 12. 다음 단계

다음 dev context에서는 **Story 2.1 - Micrometer Observation Binding**부터 구현한다.

첫 story에서 다시 확인할 사항:

- Story 1.1 starter skeleton이 review 상태이며 module/package skeleton은 이미 존재한다.
- Story 2.1은 starter bootstrap을 다시 포함하지 않는다.
- Micrometer binding은 route normalization, bucket rollup, queue, HTTP client, envelope builder를 구현하지 않는다.
- request path network call 금지 전제를 Story 2.1부터 유지한다.
