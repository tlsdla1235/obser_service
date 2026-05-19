---
artifactType: sprint-plan
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: epic-3-ready-for-dev
date: 2026-05-18
---

# Sprint Plan - Epic 3 Portal Ingest Acceptance

## 1. Sprint Planning 결과 요약

이번 Sprint는 **Epic 3. Portal Ingest Acceptance**를 개발 가능한 story 단위로 준비한다.

Sprint 목표는 Epic 2가 만든 `schemaVersion: 1.0` ingest envelope를 portal이 다시 검증하고, `accepted_metric_buckets`에 idempotent하게 저장하는 것이다.

이번 계획의 기준은 active MVC 산출물이다.

- active 구현 기준은 `planning-artifacts/`와 `implementation-artifacts/`다.
- `archive/hexagonal-version/`의 Hexagonal 산출물은 구현 기준으로 사용하지 않는다.
- 최종 아키텍처 선택은 **Traditional MVC + Service/Repository Layering** 하나다.
- portal source는 `com.observation.portal.domain.<feature>`의 feature-first MVC package를 따른다.
- controller는 request/response 변환과 HTTP status mapping만 맡고 service에 위임한다.
- service는 validation, orchestration, idempotency 판단을 담당한다.
- service는 빠른 MVC 구현을 위해 필요하면 Spring Data JPA repository와 JPA entity를 직접 사용할 수 있다.
- repository는 Spring Data JPA/Jakarta Persistence + Hibernate 기반 PostgreSQL read/write와 unique constraint 처리만 담당한다.
- Flyway SQL migration이 schema source of truth이며 Hibernate DDL auto 생성/갱신은 사용하지 않는다.
- JPA entity는 persistence model이며 controller DTO, public API surface, service result/external return model로 노출하지 않는다.
- raw project key 같은 secret은 DB row, migration, log, exception, response body, repository lookup surface에 남기지 않는다.
- 사용자 account signup/login 정책은 GitHub OAuth only다. Epic 3의 project key 검증은 사용자 계정 인증과 별개이며, account auth 구현은 이 sprint 범위가 아니다.

## 2. Epic 2 Closure 상태

Epic 2는 완료됐다. `implementation-artifacts/sprint-status.yaml` 기준 `epic-2`, Story 2.1-2.6, `epic-2-retrospective`가 모두 `done`이다.

Epic 3가 재사용할 Epic 2 산출물은 아래와 같다.

| Epic 2 산출물 | Epic 3 사용 방식 |
|---|---|
| Story 2.5 golden ingest envelope | portal validation success fixture로 재사용한다. |
| `schemaVersion: 1.0` payload shape | `IngestAcceptanceService`의 유일한 MVP schema로 수용한다. |
| deterministic `Idempotency-Key` 후보 | duplicate success와 conflict 판정 입력으로 사용한다. |
| UTC 30초 bucket boundary | portal validation에서 다시 검증한다. |
| normalized route-only endpoint | raw path/query/high-cardinality tag 유입을 portal에서도 거부한다. |
| Story 2.6 negative guard | Prometheus/scrape/query UI와 read-model scope creep를 Epic 3에서도 금지한다. |

## 3. Sprint Goal

Epic 3 Sprint Goal:

> Portal이 `POST /api/ingest/v1/buckets` 입력을 project key, schema version, bucket boundary, metric taxonomy, idempotency key 기준으로 검증하고, 새 30초 bucket은 `accepted_metric_buckets`에 저장하며, 같은 payload 재전송은 duplicate success로, 같은 key의 다른 payload는 conflict로 응답한다.

완료 판단은 아래 acceptance로 닫는다.

- `X-OBS-Project-Key`는 raw key를 저장하거나 repository lookup surface에 남기지 않고 active project로 검증한다.
- Story 2.5 golden envelope는 portal acceptance fixture로 성공한다.
- invalid schema, bucket boundary, metric taxonomy, raw path/query/free tag/custom metric 후보는 거부된다.
- `accepted_metric_buckets` migration, repository, Korean DB comments, uniqueness constraints가 구현된다.
- 같은 project/idempotency key/payload hash 재전송은 중복 저장 없이 success로 응답한다.
- 같은 project/idempotency key에 다른 payload hash는 `409 Conflict`로 응답한다.
- Epic 3 완료 산출물은 accepted bucket 저장과 idempotent acceptance에 머문다.

## 4. 이번 Sprint에 포함할 범위

포함:

- `ProjectKeyVerificationService`
- project key repository lookup과 BCrypt hash verification
- portal ingest request DTO/model
- `IngestAcceptanceService` validation
- Story 2.5 golden JSON을 재사용하는 portal validation fixture
- `IngestController` status mapping
- `V003__create_accepted_metric_buckets.sql`
- `MetricBucketRepository`
- application/application instance catalog get-or-create와 last-seen update
- payload hash 저장
- duplicate success와 idempotency conflict handling
- controller-service-repository Spring context wiring test
- Testcontainers 기반 migration/repository 검증

## 5. 이번 Sprint에서 제외할 범위

제외:

- Epic 2 starter 구현 되돌리기 또는 재설계
- starter durable outbox, Kafka, Redis, 별도 worker runtime
- Prometheus install/scrape/export/query dependency나 query UI
- pull metric backend, arbitrary query API, chart explorer
- `dashboard_snapshots` migration/repository/read model
- dashboard query API와 UI integration
- lifecycle state service
- p95/histogram merge service
- insight rule service
- endpoint priority read model
- operational event table/repository/API
- p99/tail latency judgment
- Post-MVP runtime aggregate schemaVersion 1.1, max/avg/sampleCount persistence
- high-cardinality custom metric/tag ingestion
- account signup/login 구현
- email/password signup, local account registration, password reset, email verification required for signup
- magic link, multiple OAuth providers, Google/Kakao/Naver OAuth, anonymous user flow
- cookie 기반 server session 인증 구현

`accepted_metric_buckets` persistence는 Epic 3 범위다. `dashboard_snapshots`와 dashboard read model은 Epic 5 범위다.

## 6. Story 범위와 권장 구현 순서

권장 구현 순서는 story 번호 순서를 유지한다.

1. Story 3.1 - Project Key Verification Service
2. Story 3.2 - Ingest Acceptance Service
3. Story 3.3 - PostgreSQL Bucket Repository
4. Story 3.4 - Duplicate Handling

| Story | 파일 | 핵심 경계 | 선행 조건 |
|---|---|---|---|
| 3.1 | `planning-artifacts/stories/3-1-project-key-verification-service.md` | `X-OBS-Project-Key`를 active project로 검증한다. Raw key는 저장/반환/로그하지 않는다. | Epic 1 catalog schema, Epic 2 closure |
| 3.2 | `planning-artifacts/stories/3-2-ingest-acceptance-service.md` | Story 2.5 golden envelope를 포함해 schema, bucket, metric taxonomy, idempotency key를 portal service에서 mirror validation한다. | 3.1 |
| 3.3 | `planning-artifacts/stories/3-3-postgresql-bucket-repository.md` | `accepted_metric_buckets` migration과 repository를 추가하고 validated bucket을 저장한다. | 3.1, 3.2 |
| 3.4 | `planning-artifacts/stories/3-4-duplicate-handling.md` | 같은 payload duplicate success와 같은 key/different payload conflict를 HTTP contract로 닫는다. | 3.1, 3.2, 3.3 |

## 7. Story Split 결정

### 7.1 Project Key Verification Service

Story 3.1은 ingest 권한 경계만 담당한다.

- `X-OBS-Project-Key` header가 없거나 비어 있으면 검증 실패다.
- raw key에서 `key_prefix` 후보를 찾아 `projects` row를 조회한다.
- `project_key_hash`는 BCrypt 검증만 수행하고 raw key를 저장하지 않는다.
- `status = active` project만 성공한다.
- public onboarding API, application catalog 생성, bucket persistence는 포함하지 않는다.

### 7.2 Ingest Acceptance Service

Story 3.2는 portal-side mirror validation을 담당한다.

- request DTO/model은 `ingest-envelope.md`의 `schemaVersion: 1.0` shape만 허용한다.
- Story 2.5 golden JSON은 success fixture로 사용한다.
- `bucket.durationSeconds = 30`, UTC 30초 boundary, `[startUtc, endUtc)` semantics를 검증한다.
- app/endpoint count, cumulative histogram monotonicity, latest runtime ratio range를 검증한다.
- endpoint route는 normalized route 또는 `UNKNOWN`이어야 하며 query string, absolute URL, raw identifier 후보를 거부한다.
- DTO는 free tag map, arbitrary custom metric map, raw timeseries array, Post-MVP aggregate field를 허용하지 않는다.
- persistence success path는 Story 3.3에서 연결한다.

### 7.3 PostgreSQL Bucket Repository

Story 3.3은 validated bucket의 persistence를 담당한다.

- `V003__create_accepted_metric_buckets.sql`을 추가한다.
- `MetricBucketRepository`는 Spring Data JPA/Jakarta Persistence + Hibernate 기반으로 구현한다.
- JPA entity와 Spring Data repository는 feature-first package 안에서 실제 구현 기준에 맞춰 둔다.
- Service는 필요하면 Spring Data repository/JPA entity를 직접 사용할 수 있지만, entity를 controller DTO나 service result/external return model로 반환하지 않는다.
- Hibernate DDL auto 생성/갱신은 사용하지 않고 Flyway migration을 schema source of truth로 유지한다.
- table/column Korean comments를 포함한다.
- `uk_buckets_project_idempotency_key`와 `uk_buckets_instance_bucket_start`를 포함한다.
- application/application instance catalog를 get-or-create하고 last-seen을 갱신한다.
- payload hash, bucket boundary, app summary, endpoint JSON, latest runtime ratios를 저장한다.
- p95, state, insight rule, endpoint priority, dashboard snapshot은 계산/저장하지 않는다.

### 7.4 Duplicate Handling

Story 3.4는 idempotent acceptance contract를 닫는다.

- 새 bucket은 `201 Created`, `duplicate: false`다.
- 같은 project/idempotency key와 같은 payload hash는 중복 저장 없이 `200 OK`, `duplicate: true`다.
- 같은 project/idempotency key와 다른 payload hash는 `409 Conflict`다.
- unique constraint race는 insert 실패 후 재조회하여 duplicate/conflict로 안정적으로 수렴한다.
- same instance/bucket start collision은 deterministic idempotency key 계약 위반으로 보고 conflict 계열로 처리한다.

## 8. Epic 2 Retrospective Action 반영

| 회고 Action | Sprint Planning 반영 |
|---|---|
| Epic 3 story가 schemaVersion 1.0, bucket boundary, metric taxonomy, idempotency, duplicate/conflict를 나누는지 확인 | Story 3.1-3.4로 각각 인증, validation, persistence, duplicate/idempotency를 분리했다. |
| portal validation이 starter contract를 mirror하도록 service boundary 고정 | Story 3.2에서 Story 2.5 golden fixture와 `ingest-envelope`, `metric-taxonomy`, `time-buckets` validation을 명시했다. |
| Story 2.5 golden payload를 portal acceptance fixture로 재사용 | Story 3.2 Test Requirements와 Sprint Goal에 success fixture로 고정했다. |
| Docker/Testcontainers 전제와 환경 실패 판별 기준을 handoff에 추가 | Story 3.3 Test Requirements에 Testcontainers 전제와 fallback 검증 명령 기록을 포함했다. |
| dashboard snapshot/read model, p95/state/rule/endpoint priority 계산을 Epic 3에 넣지 않음 | Sprint scope exclusions와 각 story guardrail에 명시했다. |

## 9. Test Strategy

| Test | Story | 증명할 내용 |
|---|---|---|
| `ProjectKeyVerificationServiceTest` | 3.1 | missing/invalid/disabled/active project key 검증과 raw key 비노출/lookup surface 차단 |
| `IngestAcceptanceServiceTest` | 3.2 | schemaVersion 1.0, bucket boundary, metric taxonomy, idempotency key validation |
| `PortalIngestValidationFixtureTest` | 3.2 | Story 2.5 golden JSON success와 invalid schema/bucket/route reject |
| `MetricBucketRepositoryIntegrationTest` | 3.3 | V003 migration, Korean comments, constraints, JPA mapping, repository insert/read |
| `IngestControllerStatusMappingTest` | 3.3, 3.4 | 201/200/400/401/409/500 mapping |
| `DuplicateIngestAcceptanceTest` | 3.4 | same key/hash duplicate success, same key/different hash conflict, race convergence |
| `MvcLayerBoundaryTest` | 3.1-3.4 | controller-service-repository dependency 방향 유지 |

권장 실행 명령:

- `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.ProjectKeyVerificationServiceTest`
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.ingest.service.IngestAcceptanceServiceTest`
- `./gradlew :observability-portal:test --tests com.observation.portal.domain.metric.repository.MetricBucketRepositoryIntegrationTest`
- `./gradlew :observability-portal:test`
- `./gradlew test`

Testcontainers 기반 repository test가 Docker socket 부재로 실패하면 코드 실패와 환경 실패를 구분해 story Debug Log에 남긴다. 이 경우 migration SQL static 검증과 service/controller unit test를 fallback evidence로 남기되, repository integration test는 Docker 가능 환경에서 재실행해야 한다.

## 10. Sprint Status 기대값

이 Sprint Planning 완료 후 기대 status:

- `epic-3`: `in-progress`
- `3-1`부터 `3-4`: story file 생성으로 `ready-for-dev`
- `epic-3-retrospective`: `optional`

Story 3.1이 첫 구현 대상이다.

## 11. 다음 단계

다음 dev context에서는 **Story 3.1 - Project Key Verification Service**부터 구현한다.

첫 story에서 다시 확인할 사항:

- Epic 2 starter producer path는 완료됐으며 되돌리거나 재설계하지 않는다.
- Project key verification은 raw key를 저장/반환/로그하거나 repository lookup surface에 남기지 않는다.
- Story 3.1은 Spring Data JPA repository를 service가 직접 사용하는 Traditional MVC 기준으로 review 중이다. 남은 승인 차단 항목은 key 길이와 prefix lookup 안전성이다.
- public onboarding/product API는 열지 않는다.
- 사용자 account signup/login을 언급해야 한다면 `account-auth-policy.md`의 GitHub OAuth only 기준을 따른다. Email/password, magic link, GitHub 외 provider, anonymous flow를 MVP 필수처럼 쓰지 않는다.
- Application catalog 생성과 accepted bucket persistence는 Story 3.3에서 연결한다.
- dashboard snapshot/read model과 p95/state/rule/endpoint priority 계산은 Epic 3 범위가 아니다.
