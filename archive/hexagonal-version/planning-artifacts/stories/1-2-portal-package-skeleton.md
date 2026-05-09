---
artifactType: story
storyId: "1.2"
epic: "Epic 1. Architecture Foundation"
title: "Portal Package Skeleton"
architectureStyle: Lightweight Hexagonal
status: ready-for-dev
date: 2026-05-08
---

# Story 1.2 - Portal Package Skeleton

## User Story

구현자로서, portal runtime을 Lightweight Hexagonal 구조로 일관되게 확장할 수 있도록 최소 module과 package skeleton을 먼저 만들고 싶다.

## Scope

이 story는 portal 구현의 시작점이다. 기능 구현보다 package boundary와 build/test 기반을 먼저 마련한다.

포함:

- repo build 기준 확정
- `observability-portal` module 생성
- portal base package 확정
- Lightweight Hexagonal package skeleton 생성
- test source set 생성
- 이후 Story 1.3, Story 1.4가 붙을 수 있는 최소 build wiring

제외:

- ingest API 구현
- dashboard query API 구현
- persistence adapter 구현
- Flyway migration 작성
- PostgreSQL runtime 설정
- project key verification 구현
- accepted bucket 저장
- dashboard snapshot 저장
- p95/state/rule/endpoint priority 계산
- starter module 구현

## Source Artifacts

- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/acceptance-traceability.md`

## Implementation Notes

- 최종 아키텍처 선택은 Lightweight Hexagonal 하나다.
- Simple MVC, layered service/repository, hybrid architecture로 되돌리지 않는다.
- repo에 기존 build system이 없으면 하나를 선택해 최소 multi-module 기반을 만든다.
- 문서의 module 이름은 `observability-portal`이다.
- 문서의 base package 예시는 `com.observation`이다. 구현 시작 시 한 번만 확정하고 이후 story들이 같은 기준을 따른다.
- portal runtime은 MVP에서 별도 worker deployable 없이 하나의 runtime으로 둔다.
- dashboard UI는 별도 backend deployable이 아니라 portal presentation adapter로 본다.

## Required Package Skeleton

아래 suffix 경계를 유지한다.

```text
com.observation.portal
  domain
  application
    port.in
    port.out
  adapter.in.web
    ingest
    dashboard
    admin
  adapter.out.persistence
    catalog
    bucket
    snapshot
  adapter.out.security
  adapter.out.time
  bootstrap
```

초기 skeleton은 빈 package를 유지하기 위한 최소 placeholder만 허용한다. 실제 use case, controller, repository, entity를 이 story에서 만들지 않는다.

## Acceptance Criteria

1. repo는 `observability-portal` module을 build/test 대상으로 인식한다.
2. portal main source tree와 test source tree가 존재한다.
3. portal base package가 한 번만 확정되어 문서의 package suffix와 일치한다.
4. `domain`, `application`, `application.port.in`, `application.port.out` package가 존재한다.
5. `adapter.in.web`, `adapter.out.persistence`, `adapter.out.security`, `adapter.out.time`, `bootstrap` package가 존재한다.
6. `adapter.out.persistence.catalog`, `adapter.out.persistence.bucket`, `adapter.out.persistence.snapshot` package가 구분되어 있다.
7. build/test command가 skeleton 상태에서 성공한다.
8. 이 story에서는 API, DB migration, persistence 구현, domain behavior를 만들지 않는다.

## Suggested Tasks

1. repo의 현재 build 상태를 확인한다.
2. 기존 build 기준이 없으면 최소 build system을 선택한다.
3. `observability-portal` module을 추가한다.
4. portal main/test source directory를 만든다.
5. required package skeleton을 만든다.
6. skeleton 상태에서 test command가 성공하도록 최소 smoke test를 추가한다.
7. 다음 story가 사용할 package 위치를 README 또는 build 파일 이름으로 추론 가능하게 둔다.

## Test Requirements

- portal module test task가 성공해야 한다.
- skeleton smoke test는 기능을 검증하지 않고 module/test wiring만 검증한다.
- architecture boundary test는 Story 1.3에서 본격 추가한다.
- migration test는 Story 1.4에서 추가한다.

## Developer Guardrails

- controller/service/repository layered MVC 구조를 만들지 않는다.
- `domain`과 `application`에 Spring framework 타입을 넣지 않는다.
- adapter package에서 core package를 직접 침범하는 편의 구조를 만들지 않는다.
- `accepted_metric_buckets`, `dashboard_snapshots` 관련 구현을 시작하지 않는다.
- Prometheus, scrape, query UI, logs, traces 관련 dependency를 추가하지 않는다.
