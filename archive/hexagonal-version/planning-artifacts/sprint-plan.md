---
artifactType: sprint-plan
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Lightweight Hexagonal
status: ready-for-dev-sequencing
date: 2026-05-08
---

# Sprint Plan - Portal Foundation

## 1. Sprint Planning 결과 요약

이번 Sprint는 **Epic 1. Architecture Foundation** 중에서도 portal 구현 기반을 닫는 Sprint로 고정한다.

이번 Sprint의 목적은 전체 MVP를 구현하는 것이 아니라, 이후 `adapter.out.persistence` 구현이 Lightweight Hexagonal 경계를 지키며 시작할 수 있도록 최소 기반을 만드는 것이다.

최종 판단:

- 이번 Sprint의 DB 범위는 `projects`, `applications`, `application_instances`까지로 충분하다.
- `accepted_metric_buckets`는 Epic 3에서 구현한다.
- `dashboard_snapshots`와 read model snapshot 저장/조회는 Epic 5에서 구현한다.
- ingest idempotency conflict handling은 Epic 3에서 구현한다.
- Story 1.4는 Sprint 포함 대상이지만, 첫 구현 순서는 Story 1.2 -> Story 1.3 -> Story 1.4가 안전하다.
- Prometheus, scrape, query UI, high-cardinality custom metric, logs, traces, large tenancy는 MVP Sprint 범위 밖이다.

## 2. 이번 Sprint 목표

구현자가 다음 Sprint 단계에서 바로 portal foundation을 구현할 수 있도록 아래를 닫는다.

- portal module/package skeleton 확정
- Lightweight Hexagonal package boundary를 테스트로 고정
- Flyway migration 기반 확정
- local/test PostgreSQL runtime 기준 확정
- catalog physical schema foundation 구현 준비
- `projects`, `applications`, `application_instances`의 constraint/comment 검증 기준 확정

이번 Sprint는 product behavior 구현 Sprint가 아니라 architecture/persistence foundation Sprint다.

## 3. 이번 Sprint에 포함할 Epic/Story

### Epic 1. Architecture Foundation

#### Story 1.2 - Portal Package Skeleton

파일: `planning-artifacts/stories/1-2-portal-package-skeleton.md`

목적:

- `observability-portal` module을 시작할 수 있는 최소 구조를 만든다.
- portal package suffix를 `domain`, `application`, `application.port.in`, `application.port.out`, `adapter.in.web`, `adapter.out.persistence`, `adapter.out.security`, `adapter.out.time`, `bootstrap`으로 고정한다.
- 아직 ingest/dashboard/use case 구현은 하지 않는다.

#### Story 1.3 - Architecture Guard Test

파일: `planning-artifacts/stories/1-3-architecture-guard-test.md`

목적:

- 이번 Sprint에서는 portal persistence boundary에 필요한 최소 ArchUnit guard만 둔다.
- `portal.domain`과 `portal.application`이 adapter, Spring web, persistence framework에 의존하지 않도록 검증한다.
- starter guard, UI/read model guard, non-blocking ingest guard는 후속 story에서 확장한다.

#### Story 1.4 - Portal Physical Schema Foundation

파일: `planning-artifacts/stories/1-4-portal-physical-schema-foundation.md`

목적:

- Flyway migration 기반을 만든다.
- local/test PostgreSQL runtime 기준을 세팅한다.
- `projects`, `applications`, `application_instances` physical schema를 구현한다.
- table/column 한국어 comment를 migration에 포함한다.
- unique/FK/comment/migration 적용 테스트를 추가한다.

## 4. 이번 Sprint에서 제외할 항목

아래 항목은 이번 Sprint에서 구현하지 않는다.

- `accepted_metric_buckets`
- `dashboard_snapshots`
- ingest idempotency conflict handling
- `MetricBucketStorePort`의 accepted bucket 저장 구현
- `SnapshotStorePort`의 snapshot 저장/조회 구현
- histogram merge, p95 계산
- lifecycle state 계산
- insight rule ranking
- endpoint priority 계산
- dashboard read model 생성/조회 API
- public onboarding API
- internal admin project creation API
- application list API
- local/demo seed migration
- retention cleanup schedule
- Redis, outbox, materialized view, PostgreSQL view 기반 계산
- Prometheus 설치, scrape config, PromQL query, query UI
- logs/traces/span search
- multi-tenant billing/control plane

## 5. Story 1.4 Readiness 판단

Story 1.4는 이번 Sprint 포함 대상으로 적절하다. Scope, acceptance criteria, DB 경계가 명확하고 `database-schema.md`의 Story 분배 기준과도 맞다.

다만 repo에 아직 build/module skeleton이 없으므로 Story 1.4를 Sprint의 첫 구현으로 바로 시작하는 것은 권장하지 않는다.

권장 구현 순서:

1. Story 1.2 - Portal Package Skeleton
2. Story 1.3 - Architecture Guard Test
3. Story 1.4 - Portal Physical Schema Foundation

Story 1.4는 Story 1.2와 Story 1.3 이후에 바로 구현 가능한 후보로 본다.

## 6. DB Schema 관련 Sprint 범위

이번 Sprint에서 구현할 DB 범위:

- `projects`
- `applications`
- `application_instances`

Story 1.4에 포함할 DB 작업:

- Flyway migration setup
- local/test PostgreSQL runtime setup
- `V001__create_projects.sql`
- `V002__create_applications_and_instances.sql`
- 모든 table/column에 한국어 `COMMENT ON`
- migration clean database 적용 테스트
- unique constraint 테스트
- foreign key constraint 테스트
- table/column comment 존재 테스트

이번 Sprint에서 당겨오지 않을 DB 작업:

- `V003__create_accepted_metric_buckets.sql`
- `V004__create_dashboard_snapshots.sql`
- idempotency conflict 처리
- snapshot upsert/read adapter
- retention cleanup

분리 판단:

- `accepted_metric_buckets`는 payload hash, duplicate success, conflict, bucket window query가 함께 따라오므로 Epic 3으로 넘기는 것이 맞다.
- `dashboard_snapshots`는 read model contract, state, p95, triage, endpoint priority와 결합되므로 Epic 5로 넘기는 것이 맞다.
- 이번 Sprint에서 이 둘을 당겨오면 2인 1개월 MVP 기준으로 foundation Sprint가 과해진다.

## 7. Sprint 진행 전 결정해야 할 최소 선택지

Story 1.2 시작 시 닫아야 할 최소 선택지:

- build system: Gradle 또는 Maven 중 하나
- Java/Spring Boot baseline
- base package/group id
- module 이름: 문서 기준 `observability-portal`
- test runtime: local PostgreSQL 직접 실행 또는 Testcontainers

권장 기본값:

- repo에 기존 build 기준이 없으면 Gradle 기반으로 시작한다.
- base package는 문서 예시와 같이 `com.observation`으로 시작한다.
- local/test PostgreSQL은 반복 가능한 테스트를 위해 Testcontainers를 우선 검토한다.
- migration tool은 Flyway로 고정한다.

이번 Sprint 전에 닫지 않아도 되는 선택지:

- ingest duplicate response를 `200`으로 할지 `202`로 할지
- project bootstrap을 seed-only로 둘지 internal admin API로 둘지
- dashboard snapshot refresh를 ingest 직후로 둘지 query lazy refresh로 둘지
- application list API를 첫 demo에 포함할지

위 선택지는 Epic 3, Epic 5, Epic 6 직전에 닫아도 된다.

## 8. 다음 단계

다음 단계는 **Story 구현**이다.

새 컨텍스트에서는 `planning-artifacts/stories/1-2-portal-package-skeleton.md`부터 구현한다.

Story 1.2 완료 후:

1. `implementation-artifacts/sprint-status.yaml`에서 `1-2-portal-package-skeleton`을 `review` 또는 `done`으로 갱신한다.
2. Story 1.3을 구현한다.
3. Story 1.4를 구현한다.

이번 Sprint Planning 산출물:

- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/stories/1-2-portal-package-skeleton.md`
- `planning-artifacts/stories/1-3-architecture-guard-test.md`
- `planning-artifacts/stories/1-4-portal-physical-schema-foundation.md`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/next-context-prompt.md`
