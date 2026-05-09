---
artifactType: story
storyId: "1.4"
epic: "Epic 1. Architecture Foundation"
title: "Portal Physical Schema Foundation"
architectureStyle: Traditional MVC
status: ready-for-dev
date: 2026-05-09
---

# Story 1.4 - Portal Physical Schema Foundation

## User Story

구현자로서, portal repository layer가 Traditional MVC 경계를 지키며 시작할 수 있도록 PostgreSQL migration 기반과 catalog physical schema를 먼저 마련하고 싶다.

## Scope

이 story는 Epic 1의 foundation 작업이다. DB 전체를 한 번에 구현하지 않는다.

포함:

- portal module의 Flyway migration 도구 세팅
- Testcontainers 기반 PostgreSQL integration test 기준 확정
- `projects`, `applications`, `application_instances` physical schema 구현
- table/column 한국어 `COMMENT ON` 추가
- catalog repository가 붙을 수 있는 package와 test 위치 확정

제외:

- `accepted_metric_buckets` 구현
- `dashboard_snapshots` 구현
- ingest idempotency conflict handling
- p95, lifecycle state, insight rule, endpoint priority 계산
- Redis, outbox, materialized view, Prometheus/scrape/query UI

## Source Artifacts

- `planning-artifacts/implementation-readiness-review.md`
- `planning-artifacts/project-structure.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/stories/1-2-portal-package-skeleton.md`
- `planning-artifacts/stories/1-3-architecture-guard-test.md`

## Implementation Notes

- 이 story는 Story 1.2와 Story 1.3 이후에 수행한다.
- Gradle Kotlin DSL과 `observability-portal` module은 Story 1.2 결과를 사용한다.
- Flyway dependency와 PostgreSQL/Testcontainers dependency는 이 story에서 추가한다.
- migration 파일 위치는 `observability-portal/src/main/resources/db/migration/`이다.
- UUID는 application-generated UUID로 만든다. PostgreSQL `pgcrypto` extension을 요구하지 않는다.
- project key 검증은 `key_prefix`로 project 후보를 조회한 뒤 `project_key_hash`에 저장된 BCrypt hash로 검증하는 경계로 둔다.
- raw project key는 DB에 저장하지 않는다.
- migration naming은 `V001__create_projects.sql`, `V002__create_applications_and_instances.sql`를 따른다.
- table과 column에는 모두 한국어 `COMMENT ON`을 추가한다.
- catalog repository package는 `com.observation.portal.repository.catalog` 아래에 둔다.
- controller package는 repository를 직접 참조하지 않는다.

## Acceptance Criteria

1. `V001__create_projects.sql`이 `database-schema.md`의 physical DDL과 일치한다.
2. `V002__create_applications_and_instances.sql`이 `applications`, `application_instances` physical DDL과 일치한다.
3. `projects` table이 생성된다.
4. `applications` table이 생성된다.
5. `application_instances` table이 생성된다.
6. 세 table의 모든 column에 한국어 `COMMENT ON COLUMN`이 존재한다.
7. 세 table에 한국어 `COMMENT ON TABLE`이 존재한다.
8. `applications(project_id, name, environment)` unique constraint가 존재한다.
9. `application_instances(application_id, instance_name)` unique constraint가 존재한다.
10. `projects.key_prefix`와 `projects.project_key_hash` unique constraint가 존재한다.
11. MVC layer boundary test는 portal controller가 repository를 직접 참조하지 않음을 계속 검증한다.
12. 이 story에서는 `accepted_metric_buckets`, `dashboard_snapshots`, p95/state/rule 계산을 구현하지 않는다.

## Suggested Tasks

1. portal module에 Flyway dependency를 추가한다.
2. Testcontainers 기반 PostgreSQL test runtime을 추가한다.
3. `V001__create_projects.sql`을 작성한다.
4. `V002__create_applications_and_instances.sql`을 작성한다.
5. migration 실행 테스트를 추가한다.
6. unique constraint 검증 테스트를 추가한다.
7. foreign key 검증 테스트를 추가한다.
8. table/column comment 존재 테스트를 추가한다.
9. catalog repository package skeleton을 유지한다.
10. MVC layer boundary test가 계속 통과하는지 확인한다.

## Test Requirements

- migration clean database 적용 테스트
- unique constraint 검증 테스트
- foreign key 검증 테스트
- table/column comment 존재 검증 테스트
- MVC layer boundary test
- 권장 실행 명령은 `./gradlew :observability-portal:test`다.

## Developer Guardrails

- DB trigger, view, materialized view로 lifecycle state를 계산하지 않는다.
- catalog schema 안에 endpoint metric, bucket payload, dashboard read model을 섞지 않는다.
- public project onboarding API를 이 story에 포함하지 않는다.
- project seed는 local/demo story에서 다룬다.
- controller에서 repository를 직접 호출하는 shortcut을 만들지 않는다.
