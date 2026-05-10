---
artifactType: sprint-plan
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: feature-first-mvc-aligned
date: 2026-05-10
---

# Sprint Plan - Portal MVC Foundation

## 1. Sprint Planning 결과 요약

이번 Sprint는 **Epic 1. Architecture Foundation** 중에서도 portal MVC 구현 기반을 닫는 Sprint로 고정한다.

이번 Sprint의 목적은 전체 MVP를 구현하는 것이 아니라, 이후 feature별 `repository` 구현이 Traditional MVC 경계를 지키며 시작할 수 있도록 최소 기반을 만드는 것이다.

IR 반영 후 최종 판단:

- Story 1.2는 문서 보강 후 바로 구현 가능하다.
- build system 기본값은 Gradle Groovy DSL로 고정한다.
- Gradle group은 `com.sst`, portal Java package는 `com.observation.portal`로 고정한다.
- portal package 배치는 layer-first가 아니라 feature-first MVC로 둔다.
- `domain` package는 순수 DDD domain layer가 아니라 업무 기능 묶음 namespace다.
- Story 1.2에서는 `observability-portal` module만 생성한다.
- `observability-spring-boot-starter` module은 목표 구조로 문서화하지만 Story 1.2에서는 만들지 않는다.
- 빈 Java package는 `package-info.java` marker로 추적 가능하게 만든다.
- 이번 Sprint의 DB 범위는 `projects`, `applications`, `application_instances`까지로 충분하다.
- `accepted_metric_buckets`는 Epic 3에서 구현한다.
- `dashboard_snapshots`와 read model snapshot 저장/조회는 Epic 5에서 구현한다.
- ingest idempotency conflict handling은 Epic 3에서 구현한다.
- Story 1.4는 Sprint 포함 대상이지만, 첫 구현 순서는 Story 1.2 -> Story 1.3 -> Story 1.4가 안전하다.
- Prometheus, scrape, query UI, high-cardinality custom metric, logs, traces, large tenancy는 MVP Sprint 범위 밖이다.

## 2. 이번 Sprint 목표

구현자가 다음 Sprint 단계에서 바로 portal MVC foundation을 구현할 수 있도록 아래를 닫는다.

- portal module/package skeleton 확정
- feature-first MVC boundary를 테스트로 고정
- Flyway migration 기반 확정
- local/test PostgreSQL runtime 기준 확정
- catalog physical schema foundation 구현 준비
- `projects`, `applications`, `application_instances`의 constraint/comment 검증 기준 확정

이번 Sprint는 product behavior 구현 Sprint가 아니라 architecture/persistence foundation Sprint다.

## 3. IR 반영 결정

| 항목 | 결정 |
|---|---|
| Build system | Gradle Groovy DSL 권장 기본값 |
| Root project | `observation` |
| Story 1.2 module | `observability-portal`만 생성 |
| Future starter module | `observability-spring-boot-starter`는 목표 구조로만 유지, Story 1.2 제외 |
| Gradle group / version | `com.sst` / `0.1.0-SNAPSHOT` |
| Portal Java package | `com.observation.portal` |
| Package marker | feature-first skeleton package에 `package-info.java` 사용 |
| Story 1.2 test | `:observability-portal:test` smoke test |
| Story 1.4 test runtime | PostgreSQL Testcontainers 우선 |

상세 구조 기준은 `planning-artifacts/project-structure.md`를 따른다.

## 4. 이번 Sprint에 포함할 Epic/Story

### Epic 1. Architecture Foundation

#### Story 1.2 - Portal MVC Package Skeleton

파일: `planning-artifacts/stories/1-2-portal-package-skeleton.md`

목적:

- Gradle Groovy DSL root build와 `observability-portal` module을 시작할 수 있는 최소 구조를 만든다.
- portal base package는 `com.observation.portal`로 유지하고, 업무 기능은 `domain.<feature>` 아래에 모은다.
- `domain`은 DDD layer가 아니라 feature grouping namespace다.
- feature별로 필요한 `controller`, `dto`, `service`, `repository`, `model` package marker를 둔다.
- skeleton package는 `package-info.java` marker로 남긴다.
- 아직 ingest/dashboard/service/repository 구현은 하지 않는다.
- starter module/source tree는 만들지 않는다.

#### Story 1.3 - MVC Layer Guard Test

파일: `planning-artifacts/stories/1-3-architecture-guard-test.md`

목적:

- 이번 Sprint에서는 portal MVC persistence boundary에 필요한 최소 ArchUnit guard만 둔다.
- `portal.domain..controller`가 `repository` package를 직접 참조하지 않도록 검증한다.
- `portal.domain..repository`가 `controller` package와 DTO package를 참조하지 않도록 검증한다.
- `portal.domain..service`가 `controller` package나 controller response DTO에 의존하지 않도록 검증한다.
- service/model 외부에서 state/rule/p95 계산이 생기지 않도록 guard 방향을 잡는다.
- `port`, `adapter`, `application` package가 생기지 않도록 검증한다.

#### Story 1.4 - Portal Physical Schema Foundation

파일: `planning-artifacts/stories/1-4-portal-physical-schema-foundation.md`

목적:

- Flyway migration 기반을 만든다.
- local/test PostgreSQL runtime 기준을 세팅한다. 우선 기준은 Testcontainers다.
- `projects`, `applications`, `application_instances` physical schema를 구현한다.
- table/column 한국어 comment를 migration에 포함한다.
- unique/FK/comment/migration 적용 테스트를 추가한다.

## 5. 이번 Sprint에서 제외할 항목

아래 항목은 이번 Sprint에서 구현하지 않는다.

- `observability-spring-boot-starter` module/source tree
- `accepted_metric_buckets`
- `dashboard_snapshots`
- ingest idempotency conflict handling
- accepted bucket 저장 repository 구현
- snapshot 저장/조회 repository 구현
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

## 6. Story 1.4 Readiness 판단

Story 1.4는 이번 Sprint 포함 대상으로 적절하다. Scope, acceptance criteria, DB 경계가 명확하고 `database-schema.md`의 Story 분배 기준과도 맞다.

다만 repo에 아직 build/module skeleton이 없으므로 Story 1.4를 Sprint의 첫 구현으로 바로 시작하는 것은 권장하지 않는다.

권장 구현 순서:

1. Story 1.2 - Portal MVC Package Skeleton
2. Story 1.3 - MVC Layer Guard Test
3. Story 1.4 - Portal Physical Schema Foundation

Story 1.4는 Story 1.2와 Story 1.3 이후에 바로 구현 가능한 후보로 본다.

## 7. DB Schema 관련 Sprint 범위

이번 Sprint에서 구현할 DB 범위:

- `projects`
- `applications`
- `application_instances`

Story 1.4에 포함할 DB 작업:

- Flyway migration setup
- Testcontainers 기반 PostgreSQL integration test setup
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
- snapshot upsert/read repository
- retention cleanup

분리 판단:

- `accepted_metric_buckets`는 payload hash, duplicate success, conflict, bucket window query가 함께 따라오므로 Epic 3으로 넘기는 것이 맞다.
- `dashboard_snapshots`는 read model contract, state, p95, triage, endpoint priority와 결합되므로 Epic 5로 넘기는 것이 맞다.
- 이번 Sprint에서 이 둘을 당겨오면 2인 1개월 MVP 기준으로 foundation Sprint가 과해진다.

## 8. Sprint 진행 전 결정 상태

Story 1.2 시작 시 이미 닫힌 선택지:

- build system: Gradle Groovy DSL
- root project name: `observation`
- Gradle group/version: `com.sst` / `0.1.0-SNAPSHOT`
- portal Java package: `com.observation.portal`
- module 이름: `observability-portal`
- package marker: feature-first MVC `package-info.java`

Story 1.4 시작 시 이미 닫힌 선택지:

- local/test PostgreSQL은 반복 가능한 테스트를 위해 Testcontainers를 우선 기준으로 한다.
- migration tool은 Flyway로 고정한다.
- migration 위치는 `observability-portal/src/main/resources/db/migration/`이다.

이번 Sprint 전에 닫지 않아도 되는 선택지:

- Spring Boot/Gradle plugin의 정확한 patch version
- ingest duplicate response를 `200`으로 할지 `202`로 할지
- project bootstrap을 seed-only로 둘지 internal admin API로 둘지
- dashboard snapshot refresh를 ingest 직후로 둘지 query lazy refresh로 둘지
- application list API를 첫 demo에 포함할지

위 선택지는 Epic 3, Epic 5, Epic 6 직전에 닫아도 된다.

## 9. 다음 단계

다음 단계는 **Story 구현**이다.

새 컨텍스트에서는 `planning-artifacts/stories/1-2-portal-package-skeleton.md`부터 구현한다.

Story 1.2 완료 후:

1. `implementation-artifacts/sprint-status.yaml`에서 `1-2-portal-package-skeleton`을 `review` 또는 `done`으로 갱신한다.
2. Story 1.3을 구현한다.
3. Story 1.4를 구현한다.

이번 Sprint Planning 산출물:

- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/implementation-readiness-review.md`
- `planning-artifacts/project-structure.md`
- `planning-artifacts/stories/1-2-portal-package-skeleton.md`
- `planning-artifacts/stories/1-3-architecture-guard-test.md`
- `planning-artifacts/stories/1-4-portal-physical-schema-foundation.md`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/next-context-prompt.md`
