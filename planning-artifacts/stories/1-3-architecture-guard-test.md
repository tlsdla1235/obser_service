---
artifactType: story
storyId: "1.3"
epic: "Epic 1. Architecture Foundation"
title: "MVC Layer Guard Test"
architectureStyle: Traditional MVC
status: ready-for-dev
date: 2026-05-09
---

# Story 1.3 - MVC Layer Guard Test

## User Story

구현자로서, portal foundation이 구현 초기에 Traditional MVC 경계를 잃지 않도록 최소 architecture boundary test를 먼저 고정하고 싶다.

## Scope

이 story는 Epic 1의 architecture guard 중 이번 Sprint에 필요한 portal MVC persistence boundary subset만 구현한다.

포함:

- portal module에 architecture test 기반 추가
- `portal.controller`가 repository를 직접 참조하지 않는지 검증
- `portal.repository`가 controller package와 DTO package를 참조하지 않는지 검증
- `portal.service`가 controller package와 DTO package에 의존하지 않는지 검증
- service/model 외부에서 state/rule/p95/endpoint priority 계산 class가 생기지 않도록 guard 방향 고정
- Story 1.4의 persistence migration 작업이 붙어도 MVC boundary가 깨지지 않도록 guard 준비

제외:

- starter module architecture guard
- starter non-blocking ingest guard
- UI/read model 재계산 금지 정적 검사
- No Prometheus MVP path test
- dashboard controller behavior test
- persistence migration test
- business service test

## Source Artifacts

- `planning-artifacts/implementation-readiness-review.md`
- `planning-artifacts/project-structure.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/acceptance-traceability.md`
- `planning-artifacts/stories/1-2-portal-package-skeleton.md`

## Implementation Notes

- 이 story는 Story 1.2 이후에 수행한다.
- ArchUnit 또는 동등한 JVM architecture test 도구를 사용한다.
- test 대상은 이번 Sprint에서 생성된 `observability-portal` module이다.
- package naming은 Story 1.2에서 확정한 `com.observation.portal` 기준을 따른다.
- Story 1.2의 `package-info.java` marker 덕분에 skeleton package도 test 대상 package로 남아야 한다.
- test는 구현 상세보다 dependency direction을 검증한다.
- DTO는 controller boundary의 external API shape다. service와 repository의 내부 source of truth가 아니다.

## Acceptance Criteria

1. portal architecture boundary test가 test suite에 포함된다.
2. `..portal.controller..`는 `..portal.repository..`를 직접 참조하지 않는다.
3. `..portal.repository..`는 `..portal.controller..`를 참조하지 않는다.
4. `..portal.repository..`는 `..portal.dto..`를 참조하지 않는다.
5. `..portal.service..`는 `..portal.controller..`를 참조하지 않는다.
6. `..portal.service..`는 `..portal.dto..`를 참조하지 않는다.
7. lifecycle state, insight rule, endpoint priority, p95 계산 class는 `..portal.service..` 또는 `..portal.model..` 아래에만 존재한다는 guard 방향이 문서화되거나 테스트로 고정된다.
8. architecture test command가 성공한다.
9. 이 story에서는 API behavior, DB migration, repository behavior를 구현하지 않는다.

## Suggested Tasks

1. Story 1.2의 portal module/package skeleton을 확인한다.
2. architecture test dependency를 추가한다.
3. `com.observation.portal.architecture.MvcLayerBoundaryTest`를 만든다.
4. controller -> service -> repository direction 규칙을 추가한다.
5. repository isolation 규칙을 추가한다.
6. service가 controller/dto에 의존하지 않는 규칙을 추가한다.
7. state/rule/p95/endpoint priority 계산 위치 guard를 최소 이름 패턴 또는 package rule로 고정한다.
8. test command를 실행해 skeleton 상태에서 통과하는지 확인한다.
9. Story 1.4 구현자가 같은 test suite를 계속 실행할 수 있게 둔다.

## Test Requirements

- MVC layer boundary test
- portal module unit test task
- 권장 실행 명령은 `./gradlew :observability-portal:test`다.
- CI가 없더라도 local test command로 반복 가능해야 한다.

## Developer Guardrails

- ArchUnit test를 과하게 넓히지 않는다. 이번 Sprint는 portal MVC persistence boundary만 고정한다.
- starter boundary는 starter module이 생성되는 story에서 추가한다.
- UI가 read model을 재계산하지 않는 guard는 dashboard UI/API story에서 추가한다.
- repository는 저장/조회 layer이지 lifecycle state, rule ranking, p95 계산 위치가 아니다.
- MVC라는 이유로 controller에 orchestration과 계산을 몰아넣지 않는다.
