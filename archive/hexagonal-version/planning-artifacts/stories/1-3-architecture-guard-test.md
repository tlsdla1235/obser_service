---
artifactType: story
storyId: "1.3"
epic: "Epic 1. Architecture Foundation"
title: "Architecture Guard Test"
architectureStyle: Lightweight Hexagonal
status: ready-for-dev
date: 2026-05-08
---

# Story 1.3 - Architecture Guard Test

## User Story

구현자로서, portal foundation이 구현 초기에 Lightweight Hexagonal 경계를 잃지 않도록 최소 architecture boundary test를 먼저 고정하고 싶다.

## Scope

이 story는 Epic 1의 architecture guard 중 이번 Sprint에 필요한 portal persistence boundary subset만 구현한다.

포함:

- portal module에 architecture test 기반 추가
- `portal.domain`이 Spring, persistence framework, web framework, adapter package를 참조하지 않는지 검증
- `portal.application`이 adapter package와 web/persistence framework를 참조하지 않는지 검증
- `portal.adapter.in.web`이 persistence adapter를 직접 참조하지 않는지 검증
- `portal.adapter.out.persistence`가 web/controller DTO를 참조하지 않는지 검증
- Story 1.4의 persistence migration 작업이 붙어도 core boundary가 깨지지 않도록 guard 준비

제외:

- starter module architecture guard
- starter non-blocking ingest guard
- UI/read model 재계산 금지 정적 검사
- No Prometheus MVP path test
- dashboard controller behavior test
- persistence migration test
- business use case test

## Source Artifacts

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
- package naming은 Story 1.2에서 확정한 base package를 따른다.
- test는 구현 상세보다 dependency direction을 검증한다.
- empty package만 있을 때 의미 없는 테스트가 되지 않도록, 최소 placeholder class 또는 package marker가 필요하면 Story 1.2의 skeleton과 충돌하지 않는 방식으로 둔다.

## Acceptance Criteria

1. portal architecture boundary test가 test suite에 포함된다.
2. `..portal.domain..`은 `org.springframework..`, persistence framework, web framework, `..adapter..`를 참조하지 않는다.
3. `..portal.application..`은 `..adapter..`를 참조하지 않는다.
4. `..portal.application..`은 web framework와 persistence framework 타입을 참조하지 않는다.
5. `..portal.adapter.in.web..`은 `..portal.adapter.out.persistence..`를 직접 참조하지 않는다.
6. `..portal.adapter.out.persistence..`는 `..portal.adapter.in.web..` 또는 controller DTO를 참조하지 않는다.
7. architecture test command가 성공한다.
8. 이 story에서는 API behavior, DB migration, persistence adapter behavior를 구현하지 않는다.

## Suggested Tasks

1. Story 1.2의 portal module/package skeleton을 확인한다.
2. architecture test dependency를 추가한다.
3. portal boundary test class를 만든다.
4. domain/application outbound dependency 금지 규칙을 추가한다.
5. adapter 간 직접 참조 금지 규칙을 추가한다.
6. test command를 실행해 skeleton 상태에서 통과하는지 확인한다.
7. Story 1.4 구현자가 같은 test suite를 계속 실행할 수 있게 둔다.

## Test Requirements

- architecture boundary test
- portal module unit test task
- CI가 없더라도 local test command로 반복 가능해야 한다.

## Developer Guardrails

- ArchUnit test를 과하게 넓히지 않는다. 이번 Sprint는 portal persistence boundary만 고정한다.
- starter boundary는 starter module이 생성되는 story에서 추가한다.
- UI가 read model을 재계산하지 않는 guard는 dashboard UI/API story에서 추가한다.
- `adapter.out.persistence`는 저장/조회 adapter이지 lifecycle state, rule ranking, p95 계산 위치가 아니다.
- Simple MVC 또는 service/repository layered architecture를 정당화하는 test naming을 사용하지 않는다.
