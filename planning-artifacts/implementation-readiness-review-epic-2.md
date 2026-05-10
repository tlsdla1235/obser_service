---
artifactType: implementation-readiness-review
projectName: Spring Boot 운영 첫 화면 포털
epic: "Epic 2. Starter Direct Ingest Producer"
architectureStyle: Traditional MVC
sourcePolicy: active MVC artifacts only
status: pass-with-fixes
date: 2026-05-10
stepsCompleted:
  - document-discovery
  - targeted-epic-readiness
  - story-boundary-review
  - handoff-review
---

# Implementation Readiness Review - Epic 2

## 판정

**Pass with fixes.**

Epic 2는 Story 2.1부터 구현을 시작할 수 있다. 필요한 문서 보정은 Story 2.4, Story 2.5, next context prompt에 적용했다. 남은 P0 blocker는 없다.

IR 기준은 루트의 `planning-artifacts/`와 `implementation-artifacts/` 산출물이다. `archive/hexagonal-version/`은 legacy 보관본이므로 구현 기준에서 제외했다. `bmad-restart-context-pack/`은 제품 문제와 UX 의도 참고용으로만 취급한다.

## P0/P1/P2 Findings

| Priority | Finding | 판단 | 조치 |
|---|---|---|---|
| P0 | 없음 | Epic 2 구현 착수를 막는 결함은 발견하지 못했다. | 해당 없음 |
| P1 | Story 2.4와 Story 2.5 사이에서 worker/client boundary와 final envelope serialization 경계가 구현 중 겹칠 수 있었다. | 2.4는 non-blocking worker와 retry/backoff 증명을 닫고, 2.5는 contract JSON과 idempotency header를 닫아야 한다. | Story 2.4에 testable flush command/placeholder payload까지만 허용하고 final serialization/idempotency는 2.5로 넘긴다는 보정을 적용했다. |
| P1 | Story 2.5의 idempotency key 생성에서 starter가 portal project identity를 resolve하려고 network call을 할 여지가 있었다. | host request path network call 금지와 Epic 3 handoff를 흔들 수 있다. | Story 2.5에 idempotency material은 local starter configuration에서 오며 portal lookup을 하지 않는다는 guardrail과 acceptance/test 보정을 적용했다. |
| P2 | Epic 1 retrospective에는 Story 1.1이 backlog였던 시점의 기록이 남아 있다. | 최신 `sprint-status.yaml`과 Story 1.1 파일은 review이며 starter module/package skeleton은 존재한다. | historical retrospective는 수정하지 않았다. IR 판정은 최신 sprint status와 Story 1.1 review 산출물을 기준으로 한다. |

## IR 질문별 판단

| 질문 | 판단 |
|---|---|
| Story 1.1 review 상태를 전제로 Story 2.1을 시작해도 되는가? | 가능하다. Story 1.1은 review이고 starter module/package skeleton, smoke test 기록, forbidden package 부재 기록이 있다. 단 Epic 1 전체 완료는 Story 1.1 승인 후 닫는다. |
| Story 2.1이 starter bootstrap을 다시 포함하지 않는가? | 포함하지 않는다. Scope, AC, tasks, guardrails가 모두 existing starter module에서 시작하도록 고정한다. |
| Story 2.1~2.6 순서와 의존성이 구현 가능한가? | 가능하다. observation input -> low-cardinality route/tag -> rollup -> async worker -> envelope builder -> negative guard 순서가 자연스럽다. |
| 주요 경계가 겹치거나 비어 있지 않은가? | 보정 후 충분하다. 2.4/2.5 handoff만 명시가 약했고, final serialization/idempotency를 2.5로 넘기도록 보정했다. |
| non-blocking request path를 Story 2.4 테스트로 충분히 증명할 수 있는가? | 충분하다. fake timeout/down client, queue overflow, worker thread 분리, request path direct-client-call guard가 함께 요구된다. wall-clock assertion 단독은 불충분하다는 guardrail도 있다. |
| low-cardinality guard가 raw path/high-cardinality tag 유입을 충분히 막는가? | 충분하다. 2.2가 raw path/query/high-cardinality tag를 제거하고, 2.5가 guard를 통과하지 않은 endpoint serialization을 막는다. |
| UTC 30초 bucket boundary가 time-buckets contract와 일관되는가? | 일관된다. 2.3은 UTC `:00`/`:30`, `[startUtc, endUtc)` semantics, edge case tests를 요구한다. |
| Story 2.5가 portal 저장/idempotency 구현을 Epic 3으로 넘기는가? | 넘긴다. builder는 payload/header 후보만 만들고 portal acceptance, repository, duplicate/conflict handling은 Epic 3 scope로 유지한다. |
| Story 2.6이 Prometheus/scrape/query UI 회귀를 충분히 막는가? | 충분하다. active starter source/build/resource 범위에서 forbidden package, controller absence, Prometheus dependency/resource/query path absence를 검사한다. |
| Epic 4/5 read model/p95/state/rule 계산을 Epic 2로 당길 여지가 남아 있는가? | 의미 있는 여지는 없다. 2.3, 2.5, 2.6이 p95/state/rule/endpoint priority/read model 계산을 명시적으로 제외한다. |
| 각 story가 바로 구현 가능한 scope, AC, tasks, tests, guardrails를 갖췄는가? | 갖췄다. 모든 Epic 2 story는 scope, source artifacts, dependencies, implementation notes, AC, tasks/subtasks, test requirements, guardrails를 포함한다. |

## Story별 Readiness

| Story | Readiness | 근거 | 구현 직전 주의점 |
|---|---|---|---|
| 2.1 Micrometer Observation Binding | Ready | Story 1.1 review 산출물에 starter module이 있고, 2.1은 binding boundary만 구현한다. | starter bootstrap, route policy, rollup, queue, HTTP client, envelope builder를 당겨오지 않는다. |
| 2.2 Route Normalization and Low-Cardinality Guard | Ready after 2.1 | 2.1 observation input을 받아 normalized route와 allowed tag만 다음 단계로 넘긴다. | raw path fallback을 만들지 말고 `UNKNOWN` 또는 bounded fallback을 사용한다. |
| 2.3 Bucket Rollup Service | Ready after 2.2 | UTC 30초 boundary, `[start, end)`, cumulative histogram, normalized route-only input이 정의되어 있다. | starter에서 p95/state/rule 계산을 하지 않는다. |
| 2.4 Async Flush Worker | Ready after 2.3 | bounded queue, drop policy, worker-local retry/backoff, non-blocking proof tests가 정의되어 있다. | final contract JSON과 idempotency header는 2.5로 넘긴다. |
| 2.5 Ingest Envelope Builder Service | Ready after 2.2-2.4 | ingest-envelope, metric-taxonomy, time-buckets contract를 payload/header builder로 닫는다. | project identity lookup을 위해 portal network call을 하지 않는다. storage/idempotency conflict는 Epic 3이다. |
| 2.6 Negative Path Guard | Ready after 2.1-2.5 | direct ingest producer 구현 후 MVP 역행 경로를 active source/build/resource에서 검사한다. | planning/archive 문서에 남은 legacy 표현은 test 대상에서 제외한다. |

## 필요한 문서 보정 사항

적용 완료:

- `planning-artifacts/stories/2-4-async-flush-worker.md`: Story 2.4가 final envelope serialization과 idempotency header generation을 구현하지 않도록 handoff를 명시했다.
- `planning-artifacts/stories/2-5-ingest-envelope-builder-service.md`: idempotency key builder가 portal lookup을 하지 않고 local starter configuration만 사용하도록 acceptance/test/guardrail을 보정했다.
- `planning-artifacts/next-context-prompt.md`: Story 2.1 dev 시작 전에 이 IR 문서를 읽고, 보정된 2.4/2.5 boundary를 인지하도록 갱신했다.

변경하지 않음:

- `implementation-artifacts/sprint-status.yaml`: Epic 2와 Story 2.1~2.6이 이미 `ready-for-dev`/`in-progress` 기대 상태와 일치하므로 상태 변경이 필요하지 않다.
- `implementation-artifacts/epic-1-retro-2026-05-10.md`: Story 1.1 backlog 기록은 당시 회고의 historical note로 유지한다. 최신 상태 판단은 `sprint-status.yaml`과 Story 1.1 파일을 따른다.

## 구현 직전 권장 순서

1. Story 2.1 - Micrometer Observation Binding
2. Story 2.2 - Route Normalization and Low-Cardinality Guard
3. Story 2.3 - Bucket Rollup Service
4. Story 2.4 - Async Flush Worker
5. Story 2.5 - Ingest Envelope Builder Service
6. Story 2.6 - Negative Path Guard

## 첫 Dev Target

첫 구현 대상은 **Story 2.1 - Micrometer Observation Binding**이다.

Story 2.1 dev 시작 시 먼저 확인할 것:

- Story 1.1은 review 상태이며 `observability-spring-boot-starter` module/package skeleton은 이미 존재한다.
- Story 2.1은 starter bootstrap을 다시 하지 않는다.
- request path에서 portal HTTP client, queue flush, envelope serialization을 호출하지 않는다.
- 구현 후 `./gradlew test`를 권장 검증 명령으로 사용한다. 실행 불가 시 Story 2.1 Dev Agent Record에 사유를 남긴다.
