---
artifactType: story
storyId: "2.2"
epic: "Epic 2. Starter Direct Ingest Producer"
title: "Route Normalization and Low-Cardinality Guard"
architectureStyle: Traditional MVC
status: ready-for-dev
date: 2026-05-10
---

# Story 2.2 - Route Normalization and Low-Cardinality Guard

## User Story

구현자로서, starter가 bucket이나 ingest envelope를 만들기 전에 route와 tag를 low-cardinality 정책으로 고정해 raw path와 high-cardinality label이 MVP payload에 들어가지 않게 하고 싶다.

## Scope

이 story는 Epic 2의 cardinality safety boundary다. Story 2.1의 observation input을 받아 normalized route와 허용 tag만 다음 단계로 넘긴다.

포함:

- normalized route model/service 추가
- route normalization precedence 고정
- allowed tag key policy 고정
- raw path parameter, query string, high-cardinality tag 차단
- endpoint key를 `method + normalized route`로 제한
- low-cardinality guard test

제외:

- Micrometer binding 재구현
- bucket rollup implementation
- bounded queue/flush worker
- HTTP ingest client
- ingest envelope builder
- portal ingest validation/persistence
- arbitrary custom metric/tag support

## Source Artifacts

- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/acceptance-traceability.md`
- `planning-artifacts/contracts/metric-taxonomy.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/stories/2-1-micrometer-observation-binding.md`

## Dependencies

- Story 2.1 provides observation inputs.
- Story 2.3 must roll up only normalized endpoint keys.
- Story 2.5 must serialize only data that passed this guard.

## Implementation Notes

- 허용 식별자는 `application`, `environment`, `instance`, `method`, `normalized route`다.
- route source precedence는 framework route pattern/template, configured allowlist, safe bounded fallback 순서로 둔다.
- 안전한 normalized route를 얻지 못하면 raw path를 사용하지 않고 `UNKNOWN` 또는 configured bounded fallback으로 처리한다.
- query string은 route에 포함하지 않는다.
- raw path values like `/orders/12345`, `/users/alice`, `/sessions/abc` must not become endpoint keys.
- arbitrary label map을 도입하지 않는다.
- endpoint list는 bounded top-N 또는 configured allowlist로 제한할 수 있게 둔다.
- annotation 기반 query dimension, route masking, metric rename은 post-MVP 후보로만 남기고 이 story에서는 구현하지 않는다.

## Acceptance Criteria

1. route normalization service는 raw request path 대신 normalized route를 반환한다.
2. framework route template이 있으면 이를 우선 사용한다.
3. route template이 없고 allowlist match도 없으면 raw path를 payload 후보로 사용하지 않는다.
4. query string은 route와 tag에서 제거된다.
5. endpoint key는 `method + normalized route`만 사용한다.
6. `userId`, `tenantId`, `sessionId`, `traceId`, arbitrary label은 starter payload 후보에 남지 않는다.
7. low-cardinality guard를 통과하지 못한 tag/route는 drop, sanitize, or bounded fallback 중 하나로 처리되고 host request path에는 예외를 전파하지 않는다.
8. Story 2.3 rollup input은 normalized route만 받는다.
9. Story 2.5 envelope builder가 raw path/high-cardinality tag를 직렬화할 수 없도록 model 경계가 고정된다.
10. Prometheus/scrape/query UI 경로를 추가하지 않는다.

## Suggested Tasks

1. Story 2.1 observation input shape를 확인한다.
2. `NormalizedRoute` model을 추가한다.
3. route normalization service를 추가한다.
4. allowed tag key policy를 코드 또는 enum/value object로 고정한다.
5. framework route pattern/template 우선순위를 구현한다.
6. allowlist 또는 safe bounded fallback 경계를 구현한다.
7. high-cardinality tag 차단 테스트를 추가한다.
8. raw path/query string normalization 테스트를 추가한다.
9. rollup input이 normalized route만 받도록 boundary를 정리한다.
10. 기존 starter/portal tests를 실행한다.

## Test Requirements

- raw path parameter 제거 테스트
- query string 제거 테스트
- framework route template 우선순위 테스트
- high-cardinality tag rejection/sanitization 테스트
- endpoint key boundedness 테스트
- forbidden package guard
- 권장 실행 명령: `./gradlew test`

## Developer Guardrails

- raw path를 "임시로" endpoint key에 넣지 않는다.
- arbitrary tag map을 starter model 또는 envelope 후보에 추가하지 않는다.
- tenant/user/session/trace 식별자를 MVP metric tag로 허용하지 않는다.
- query parameter opt-in이나 route/display masking annotation을 MVP guard 우회 경로로 추가하지 않는다.
- route normalization 실패를 host request failure로 전파하지 않는다.
- Story 2.3보다 앞서 low-cardinality guard를 닫는다.
- portal ingest validation은 Epic 3에서 구현한다.

## Tasks/Subtasks

- [ ] Story 2.1 observation input shape를 확인한다.
- [ ] `NormalizedRoute` model을 추가한다.
- [ ] route normalization service를 추가한다.
- [ ] allowed tag key policy를 고정한다.
- [ ] framework route pattern/template 우선순위를 구현한다.
- [ ] allowlist 또는 safe bounded fallback 경계를 구현한다.
- [ ] high-cardinality tag 차단 테스트를 추가한다.
- [ ] raw path/query string normalization 테스트를 추가한다.
- [ ] rollup input이 normalized route만 받도록 boundary를 정리한다.
- [ ] 기존 starter/portal tests를 실행한다.

## Dev Agent Record

### Implementation Plan

TBD by dev-story.

### Debug Log

TBD by dev-story.

### Completion Notes

TBD by dev-story.

### File List

TBD by dev-story.

## Status

ready-for-dev
