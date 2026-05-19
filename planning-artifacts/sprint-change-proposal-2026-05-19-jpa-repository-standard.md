---
artifactType: sprint-change-proposal
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: proposed-and-applied
date: 2026-05-19
changeTrigger: Story 3.1 구현 중 repository 구현 기술 표준 부재 확인
---

# Sprint Change Proposal - JPA Repository Standard

## 1. Issue Summary

Story 3.1 구현 중 `ProjectRepository`가 JDBC 구현으로 추가되었지만, active BMAD 산출물에는 observability-portal repository 구현 표준이 Spring Data JPA, Jakarta Persistence, Hibernate 중 어느 것인지 고정되어 있지 않았다.

기존 active 문서는 아래 결정을 이미 고정하고 있다.

- Traditional MVC + Service/Repository Layering
- PostgreSQL
- Flyway SQL migration
- Testcontainers 기반 repository integration test
- controller -> service -> repository 방향과 DTO/controller 의존 금지

이번 변경은 persistence framework 결정을 채우는 문서 정렬이며, 런타임 코드 구현 변경은 하지 않는다.

## 2. Impact Analysis

### Epic Impact

Epic 1의 physical schema foundation과 Epic 3의 ingest persistence 범위는 유지한다. Epic 순서, MVP scope, DB table 범위는 변경하지 않는다.

### Story Impact

- Story 3.1: Spring Data JPA repository를 service가 직접 사용하는 구현은 Traditional MVC 기준에서 허용한다. Review는 JPA 노출 자체가 아니라 entity/API/result 누수와 raw key lookup surface를 기준으로 판단한다.
- Story 3.3: `MetricBucketRepository`는 처음부터 Spring Data JPA/Jakarta Persistence + Hibernate 기반으로 구현해야 한다.
- Story 3.4: duplicate/idempotency 처리는 Spring Data repository 또는 feature repository 구현을 통해 수행할 수 있다. controller/dto/entity 외부 누수는 금지한다.

### Artifact Conflicts

JPA 도입은 기존 PostgreSQL/Flyway/Testcontainers/MVC boundary 결정과 충돌하지 않는다. 다만 아래 문서에는 repository 구현 표준이 비어 있어 보강이 필요하다.

- `_bmad/custom/project-context.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/project-structure.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/stories/3-1-project-key-verification-service.md`
- `planning-artifacts/stories/3-3-postgresql-bucket-repository.md`
- `implementation-artifacts/sprint-status.yaml`

### Technical Impact

- `observability-portal` repository 구현 표준은 Spring Data JPA/Jakarta Persistence + Hibernate다.
- DB는 PostgreSQL을 유지한다.
- schema source of truth는 Flyway SQL migration이다.
- Hibernate DDL auto 생성/갱신은 사용하지 않는다. `validate`처럼 schema를 변경하지 않는 검증 모드는 허용 후보지만 DDL 작성 권한은 Flyway에만 있다.
- JPA entity는 persistence 구현 세부사항이며 controller response DTO, public API surface, service result/external return model, product model로 새지 않는다.
- Service는 빠른 MVC 구현을 위해 필요하면 Spring Data JPA repository와 JPA entity를 직접 사용할 수 있다.
- JPA entity와 Spring Data repository 위치는 feature-first package 안에서 실제 코드 기준에 맞춘다. `domain.<feature>.repository.entity` 또는 `domain.<feature>.repository.jpa`를 필수 구조로 요구하지 않는다.
- raw project key 같은 secret은 DB row, migration, log, exception, response body, repository lookup surface에 남기지 않는다.
- Testcontainers 기반 repository integration test는 Flyway migration과 JPA mapping/repository 동작을 함께 검증한다.

## 3. Recommended Approach

권장 경로는 Direct Adjustment다.

이 변경은 제품 요구사항이나 epic scope를 바꾸지 않고 구현 기술 표준을 명확히 하는 수준이다. 빠른 MVC 기준에서는 service가 Spring Data repository와 JPA entity를 직접 아는 것이 결함이 아니며, 검토 기준은 외부 반환 모델/API surface/entity 누수와 secret lookup surface 차단이다.

Effort: Low to Medium. Story 3.1 review 기준 재분류와 Story 3.3 JPA 기반 구현 기준 정렬이 필요하다.

Risk: Medium. Flyway schema와 JPA entity mapping이 어긋날 수 있으므로 Testcontainers repository integration test에서 migration + mapping + repository behavior를 함께 검증해야 한다.

## 4. Checklist Execution Summary

| Checklist | Status | Notes |
|---|---|---|
| 1.1 Trigger story 확인 | Done | Story 3.1 Project Key Verification Service 구현 중 persistence framework 표준 부재와 JDBC 구현이 확인되었다. |
| 1.2 Core problem 정의 | Done | 기술 스택 누락에 따른 repository 구현 표준 불일치 위험이다. |
| 1.3 Evidence 수집 | Done | active 문서는 MVC/PostgreSQL/Flyway/Testcontainers를 고정했고, Story 3.1 진행 중 repository 구현 표준과 package 기준을 더 명확히 할 필요가 확인되었다. |
| 2.1-2.5 Epic impact | Done | Epic 범위와 순서는 유지한다. Story 3.1/3.3 구현 세부 기준만 조정한다. |
| 3.1 PRD conflict | N/A | 제품 목표와 MVP scope는 변경하지 않는다. |
| 3.2 Architecture conflict | Done | JPA는 MVC/Flyway/PostgreSQL 결정과 충돌하지 않는다. 문서에 구현 표준을 추가한다. |
| 3.3 UI/UX conflict | N/A | UI/API shape 변경은 없다. |
| 3.4 Other artifacts | Done | sprint status와 active story handoff에 후속 구현 항목을 기록한다. |
| 4.1 Direct adjustment | Viable | 문서 보강과 Story 3.1 follow-up이면 충분하다. |
| 4.2 Rollback | Not viable | 현재 구현을 문서 작업에서 되돌릴 필요는 없다. |
| 4.3 MVP review | Not viable | MVP scope 축소나 재정의가 필요하지 않다. |
| 5.1-5.5 Proposal components | Done | issue, impact, approach, detailed changes, handoff를 작성했다. |
| 6.1-6.2 Final review | Done | 적용 문서와 후속 구현 항목을 함께 검토한다. |
| 6.3 Approval | N/A | 사용자가 이번 turn에서 문서 갱신을 요청했으므로 batch 적용으로 처리했다. |
| 6.4 Sprint status update | Done | status 값은 바꾸지 않고 workflow note와 last_updated만 갱신한다. |

## 5. Detailed Change Proposals

### Architecture / Project Structure

OLD:

- Repository는 PostgreSQL 저장/조회와 query 최적화만 맡는다.
- Persistence framework는 명시되어 있지 않다.

NEW:

- Repository 구현 표준은 Spring Data JPA/Jakarta Persistence + Hibernate다.
- Repository는 Flyway migration이 만든 PostgreSQL schema에 mapping한다.
- Service는 필요하면 Spring Data JPA repository와 JPA entity를 직접 사용할 수 있다.
- JPA entity와 Spring Data repository는 feature-first package 안에서 실제 코드 기준에 맞춰 둔다.
- JPA entity는 controller response DTO, public API surface, service result/external return model로 노출하지 않는다.
- raw project key 같은 secret은 repository lookup surface를 포함한 외부/영속 표면에 남기지 않는다.

Rationale: MVC layering은 유지하면서 repository 구현 방식과 package 경계를 구현자가 동일하게 해석하도록 고정한다.

### Database Schema

OLD:

- Physical DDL은 Flyway migration으로 옮길 수 있는 초안이다.
- Hibernate DDL auto 정책은 명시되어 있지 않다.

NEW:

- Flyway SQL migration이 schema source of truth다.
- Hibernate DDL auto 생성/갱신은 사용하지 않는다.
- JPA annotations는 Flyway schema를 설명하고 mapping을 검증하기 위한 구현 세부사항이다.

Rationale: DB schema drift와 ORM 주도 schema 변경을 막고, migration review와 Testcontainers 검증을 유지한다.

### Stories

OLD:

- Story 3.1 구현 기록은 JDBC repository를 포함한다.
- Story 3.3은 PostgreSQL repository라고만 명시한다.

NEW:

- Story 3.1에는 Spring Data JPA repository 직접 사용이 허용되는 MVC 기준과 entity/API/result/raw key 누수 금지 기준을 함께 남긴다.
- Story 3.3은 Spring Data JPA/Jakarta Persistence + Hibernate 기반 `MetricBucketRepository`와 feature-first persistence package 기준을 따른다.

Rationale: 이미 진행 중인 구현을 문서상 지우지 않고, 새 표준과 맞추는 후속 작업을 명시한다.

## 6. Implementation Handoff

Scope classification: Minor.

Developer agent handoff:

- `observability-portal/build.gradle`은 후속 구현에서 `spring-boot-starter-data-jpa`를 사용하도록 조정한다. Flyway/PostgreSQL/Testcontainers 의존성은 유지한다.
- JPA entity와 Spring Data repository는 feature-first package 안에서 실제 코드 기준에 맞춰 둔다.
- Service가 Spring Data repository/JPA entity를 직접 사용하는 것은 허용하되, entity를 controller/dto/public API/service result/external return model로 노출하지 않는다.
- Story 3.3의 `MetricBucketRepository`는 처음부터 JPA entity + Spring Data repository 기준으로 구현한다.
- raw project key 같은 secret은 DB row, migration, log, exception, response body, repository lookup surface에 남기지 않는다.

Success criteria:

- active 문서가 Spring Data JPA/Jakarta Persistence + Hibernate repository 표준을 동일하게 말한다.
- Hibernate DDL auto 생성/갱신 금지가 문서화되어 있다.
- Flyway migration이 계속 schema source of truth다.
- Testcontainers repository integration test 기준이 유지된다.
- Story 3.1 review는 JPA direct usage 자체가 아니라 key handling과 외부 노출 금지 기준으로 판단한다.
