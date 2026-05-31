---
artifactType: database-schema
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
database: PostgreSQL
status: account-project-membership-updated
date: 2026-05-31
---

# Database Schema - MVC Version

## 1. 목적과 경계

PostgreSQL은 MVP에서 아래 데이터만 저장한다.

- project/application/instance metadata
- account-project authorization membership
- idempotent하게 수용된 30초 metric bucket
- coarse-grained dashboard read model history snapshot

PostgreSQL을 범용 TSDB나 rule engine으로 쓰지 않는다. lifecycle state, insight rule ranking, endpoint priority, percentile 표시 정책의 source of truth는 portal service layer다. p95/p99 값 자체의 canonical source는 starter가 ingest envelope로 보낸 `localPercentiles.p95Ms`/`p99Ms`다.

MVP에서는 별도 `operational_events` 테이블을 만들지 않는다. Operational event history는 Epic 5/6에서 `dashboard_snapshots.generated_at`, `state_code`, `read_model_json`을 기반으로 service layer가 파생한다.

Epic 5/6 dashboard UX 기준에서 Project Entry와 Application List는 metadata와 stored read model summary를 읽는 scope 선택 surface다. Application Dashboard가 primary first-screen이며, Instance Detail과 Instance Snapshot Trend는 `dashboard_snapshots.read_model_json`에 저장된 bounded evidence를 읽는 보조 projection이다.

## 2. Schema 개요

핵심 MVP 테이블은 아래 목록으로 관리한다.

| Table | 목적 |
|---|---|
| `projects` | project key 검증과 project 식별 |
| `account_project_memberships` | account 기준 project visibility와 project-scoped resource authorization |
| `applications` | project 안의 application/environment 식별 |
| `application_instances` | application instance 식별과 last seen 추적 |
| `accepted_metric_buckets` | idempotent하게 수용된 30초 app/endpoint bucket 저장 |
| `dashboard_snapshots` | 특정 시점의 dashboard read model 전체를 history snapshot으로 저장 |

endpoint별 데이터는 `accepted_metric_buckets.endpoints_json`에 bounded JSON으로 저장한다. MVP에서 endpoint 수는 이미 정규화된 route 기준의 bounded top-N 또는 allow set으로 제한되므로 별도 endpoint table 없이도 15분 current/baseline window를 service layer에서 병합할 수 있다. 이 제한은 저장/조회 cardinality cap이며 route attribution fallback이 아니다.

사용자 account/auth 테이블은 별도 account auth story에서 추가한다. Epic 1/3의 ingest acceptance schema에는 포함하지 않지만, 추가 시 `account-auth-policy.md`의 GitHub OAuth only 정책을 따른다.

별도 endpoint table은 아래 조건이 실제로 생길 때만 추가한다.

- JSON 파싱이 read model refresh 구현을 지나치게 복잡하게 만든다.
- 30분 window 조회량이 MVP demo/운영 목표를 넘는다.
- endpoint별 retention 또는 index가 반드시 필요해진다.

그 경우에도 추가 후보는 `accepted_endpoint_metric_buckets` 하나로 제한하고, endpoint explorer나 arbitrary endpoint query 기능으로 확장하지 않는다.

### 2.1 Operational Event History Physical Boundary

Operational Event History는 MVP physical schema에 새 table을 추가하지 않는다.

History 조회는 `dashboard_snapshots`의 아래 값을 기반으로 service layer에서 파생한다.

- `generated_at`
- `state_code`
- `read_model_json`
- 필요한 경우 저장된 read model에서 복사한 bounded index column

별도 `operational_events` table, event repository, materialized view는 alert acknowledgement, durable event id, 장기 event retention, event annotation, alert delivery dedupe가 필요해질 때 후속 확장으로 검토한다.

이 결정은 Epic 2와 Epic 3의 migration 범위를 변경하지 않는다.

`dashboard_snapshots`는 application별 1시간 scheduled snapshot을 기본 cadence로 둔다. 30초 단위 원천은 이미 `accepted_metric_buckets`에 저장되므로, ingest commit마다 dashboard snapshot을 생성하거나 30초 dashboard snapshot을 장기 보관하지 않는다. Dashboard query fallback regeneration은 current response 보장을 위한 service 동작이지 raw metric 복제 저장 정책이 아니다.

1시간 cadence 외 추가 snapshot row는 의미 있는 `state_code` 변화, confidence `>= 0.82` high-confidence concern, 짧지만 강한 spike 실험값(confidence `>= 0.90` + 최근 5개 30초 bucket 중 2개 이상 bad), dashboard query fallback regeneration 조건에서만 남긴다. 이 capture 정책도 `dashboard_snapshots`에 read model 전체를 저장하는 bounded history 정책이며, 별도 event table이나 endpoint timeseries 저장소를 만들기 위한 우회 경로가 아니다.

Instance snapshot trend는 이 table의 새 raw timeseries 책임이 아니다. Snapshot `read_model_json`에 저장된 bounded instance summary에서 특정 `application + instance` point만 projection하며, 기본 `since=7d`, `limit=168`, 최대 `since=14d`, `limit=336`으로 clamp한다.

### 2.2 ORM / Repository Mapping Boundary

observability-portal repository 구현 표준은 Spring Data JPA/Jakarta Persistence + Hibernate다.

Flyway SQL migration이 PostgreSQL schema의 source of truth다. JPA entity annotation은 이 문서와 Flyway migration으로 정의된 table/column/constraint를 application code에 mapping하는 구현 세부사항이며, schema 정의를 대체하지 않는다.

Hibernate DDL auto 생성/갱신은 사용하지 않는다. `create`, `create-drop`, `update`처럼 DB schema를 변경하는 모드는 금지한다. schema를 변경하지 않는 validation 모드는 Flyway migration과 JPA mapping mismatch를 조기에 발견하기 위한 보조 검증으로만 사용할 수 있다.

Portal은 Traditional MVC + feature-first package structure를 따른다. JPA entity와 Spring Data repository는 해당 feature package 안의 persistence 책임 위치에 둔다. Service는 필요하면 Spring Data JPA repository와 JPA entity를 직접 사용할 수 있지만, JPA entity를 controller response DTO, public API surface, service result/external return model로 직접 반환하지 않는다.

Testcontainers 기반 repository integration test는 PostgreSQL container에 Flyway migration을 먼저 적용한 뒤 JPA mapping과 repository 동작을 검증한다.

### 2.3 Account/Auth Schema Boundary

Account signup과 login은 GitHub OAuth only다. Account/auth schema를 추가할 때는 아래 경계를 지킨다.

- 내부 `user/account` row와 provider identity row를 분리한다.
- 외부 identity의 stable key는 provider=`github`와 GitHub user id 또는 provider subject 조합이다.
- GitHub email은 profile metadata일 뿐 stable identity key가 아니다.
- local password hash, password reset token, email verification required for signup, magic link, GitHub 외 provider, anonymous user를 위한 column/table은 MVP schema에 만들지 않는다.
- GitHub OAuth token은 MVP에서 GitHub API 호출이 필요 없으면 저장하지 않는다.
- GitHub OAuth token 저장이 필요해지면 암호화된 token 저장 컬럼, 최소 scope, 만료/회전, 폐기 기준을 migration/story에 함께 명시한다.
- 우리 서비스 Refresh Token 저장소는 `token store` 추상으로 둔다. 초기 구현 후보는 RDBMS에 hashed refresh token 또는 token family metadata를 저장하는 방식이다.
- Redis는 account/auth schema의 필수 전제가 아니다. 고성능 revoke list, distributed token state, reuse detection 최적화가 필요해질 때 후속 선택지로 둔다.

### 2.4 Account-project Membership Authorization Boundary

`account_project_memberships`는 portal 사용자 account와 project의 N:M authorization source of truth다.

- 한 account는 여러 project membership을 가질 수 있고, 한 project도 여러 account에 노출될 수 있다.
- MVP role은 `member` 단일 값만 허용한다.
- MVP status는 `active`, `disabled`만 허용하며, Project visibility와 project-scoped resource API authorization에는 `active`만 사용한다.
- `GET /api/projects`는 authenticated Bearer account의 active membership project만 반환한다.
- `/api/projects/{projectId}/applications/**` resource API는 active membership이 없으면 project 존재 여부를 드러내지 않도록 `404`로 fail-closed한다.
- Production migration은 기존 account와 project를 자동으로 모두 연결하지 않는다. Local/dev/test fixture나 internal bootstrap path가 필요하면 명시적인 account id와 project id로 membership row를 만든다.
- 이 membership은 Epic 5의 project/application/instance catalog path 정합성과 다른 개념이다.

## 3. Tables

### 3.1 `projects`

목적: project 식별, project key 검증, ingest 권한 경계.

| Column | Type | Null | 설명 |
|---|---|---|---|
| `id` | `uuid` | no | primary key |
| `name` | `varchar(120)` | no | 사람이 읽는 project 이름 |
| `key_prefix` | `varchar(32)` | no | 운영자가 식별할 수 있는 key prefix |
| `project_key_hash` | `varchar(255)` | no | raw key를 저장하지 않는 검증용 hash |
| `status` | `varchar(32)` | no | `active`, `disabled` |
| `created_at` | `timestamptz` | no | 생성 시각 |
| `updated_at` | `timestamptz` | no | 수정 시각 |

Constraints:

- Primary key: `pk_projects (id)`
- Unique: `uk_projects_name (name)`
- Unique: `uk_projects_key_prefix (key_prefix)`
- Unique: `uk_projects_project_key_hash (project_key_hash)`

### 3.1.1 `account_project_memberships`

목적: Bearer account가 볼 수 있는 project와 project-scoped resource API 접근 가능 여부를 저장한다.

| Column | Type | Null | 설명 |
|---|---|---|---|
| `id` | `uuid` | no | primary key |
| `account_id` | `uuid` | no | `accounts.id` |
| `project_id` | `uuid` | no | `projects.id` |
| `role` | `varchar(32)` | no | MVP에서는 `member` |
| `status` | `varchar(32)` | no | `active`, `disabled` |
| `created_at` | `timestamptz` | no | 생성 시각 |
| `updated_at` | `timestamptz` | no | 수정 시각 |

Constraints:

- Primary key: `pk_account_project_memberships (id)`
- Foreign key: `fk_account_project_memberships_account_id -> accounts(id)`
- Foreign key: `fk_account_project_memberships_project_id -> projects(id)`
- Unique: `uk_account_project_memberships_account_project (account_id, project_id)`
- Check: `ck_account_project_memberships_role (role in ('member'))`
- Check: `ck_account_project_memberships_status (status in ('active', 'disabled'))`

Indexes:

- Account 기준 project list: `idx_account_project_memberships_account_status_project (account_id, status, project_id)`
- Project 기준 authorization check: `idx_account_project_memberships_project_status_account (project_id, status, account_id)`

Notes:

- Raw project key, access token, refresh token, GitHub OAuth token, provider raw payload, secret은 이 table에 저장하지 않는다.
- `projects.status='active'`와 membership `status='active'`가 모두 만족될 때만 project list와 project-scoped resource API에 노출한다.
- Role hierarchy, invite/team/org management, billing/tenant model은 후속 story에서 constraint 확장과 별도 product decision으로 다룬다.

### 3.2 `applications`

목적: project 안에서 application + environment를 안정적으로 식별한다.

| Column | Type | Null | 설명 |
|---|---|---|---|
| `id` | `uuid` | no | primary key |
| `project_id` | `uuid` | no | `projects.id` |
| `name` | `varchar(160)` | no | ingest envelope의 `application.name` |
| `environment` | `varchar(80)` | no | ingest envelope의 `application.environment` |
| `status` | `varchar(32)` | no | `active`, `disabled` |
| `first_seen_at` | `timestamptz` | yes | 첫 accepted bucket 시각 |
| `last_seen_at` | `timestamptz` | yes | 마지막 accepted bucket 수용 시각 |
| `created_at` | `timestamptz` | no | 생성 시각 |
| `updated_at` | `timestamptz` | no | 수정 시각 |

Constraints:

- Primary key: `pk_applications (id)`
- Foreign key: `fk_applications_project_id -> projects(id)`
- Unique: `uk_applications_project_name_env (project_id, name, environment)`

Indexes:

- `idx_applications_project_status (project_id, status)`

### 3.3 `application_instances`

목적: instance별 bucket idempotency와 freshness 입력을 추적한다.

| Column | Type | Null | 설명 |
|---|---|---|---|
| `id` | `uuid` | no | primary key |
| `application_id` | `uuid` | no | `applications.id` |
| `instance_name` | `varchar(200)` | no | ingest envelope의 `application.instance` |
| `first_seen_at` | `timestamptz` | no | 첫 accepted bucket 수용 시각 |
| `last_seen_at` | `timestamptz` | no | 마지막 accepted bucket 수용 시각 |
| `created_at` | `timestamptz` | no | 생성 시각 |
| `updated_at` | `timestamptz` | no | 수정 시각 |

Constraints:

- Primary key: `pk_application_instances (id)`
- Foreign key: `fk_application_instances_application_id -> applications(id)`
- Unique: `uk_application_instances_app_instance (application_id, instance_name)`

Indexes:

- `idx_application_instances_app_last_seen (application_id, last_seen_at desc)`

### 3.4 `accepted_metric_buckets`

목적: portal이 검증 후 수용한 30초 metric bucket을 저장한다. 이 테이블은 source event store에 가깝지만 범용 raw timeseries 저장소는 아니다.

| Column | Type | Null | 설명 |
|---|---|---|---|
| `id` | `uuid` | no | primary key |
| `project_id` | `uuid` | no | `projects.id` |
| `application_id` | `uuid` | no | `applications.id` |
| `application_instance_id` | `uuid` | no | `application_instances.id` |
| `schema_version` | `varchar(16)` | no | MVP는 `1.0` |
| `idempotency_key` | `varchar(300)` | no | request header의 `Idempotency-Key` |
| `payload_hash` | `varchar(128)` | no | 동일 key/다른 payload 감지용 hash |
| `bucket_start_utc` | `timestamptz` | no | 30초 UTC boundary 시작 |
| `bucket_end_utc` | `timestamptz` | no | 30초 UTC boundary 끝 |
| `duration_seconds` | `integer` | no | MVP는 `30` |
| `accepted_at` | `timestamptz` | no | portal 수용 시각 |
| `request_count` | `bigint` | no | app-level request count |
| `error_count` | `bigint` | no | app-level error count |
| `duration_buckets_json` | `jsonb` | no | app-level cumulative histogram buckets |
| `cpu_usage_ratio` | `numeric(6,5)` | yes | nullable runtime sample |
| `heap_used_ratio` | `numeric(6,5)` | yes | nullable runtime sample |
| `datasource_pool_usage_ratio` | `numeric(6,5)` | yes | nullable runtime sample |
| `local_percentiles_json` | `jsonb` | yes | instance bucket scope의 starter canonical percentile point |
| `endpoints_json` | `jsonb` | no | bounded endpoint bucket 배열 |
| `created_at` | `timestamptz` | no | row 생성 시각 |

Constraints:

- Primary key: `pk_accepted_metric_buckets (id)`
- Foreign key: `fk_buckets_project_id -> projects(id)`
- Foreign key: `fk_buckets_application_id -> applications(id)`
- Foreign key: `fk_buckets_application_instance_id -> application_instances(id)`
- Unique idempotency: `uk_buckets_project_idempotency_key (project_id, idempotency_key)`
- Unique bucket identity: `uk_buckets_instance_bucket_start (application_instance_id, bucket_start_utc)`
- Check: `ck_buckets_duration_30 (duration_seconds = 30)`
- Check: `ck_buckets_window_positive (bucket_end_utc > bucket_start_utc)`
- Check: `ck_buckets_counts_non_negative (request_count >= 0 and error_count >= 0 and error_count <= request_count)`

Indexes:

- Bucket window 조회: `idx_buckets_app_window (application_id, bucket_start_utc, bucket_end_utc)`
- Freshness 조회: `idx_buckets_app_last_end (application_id, bucket_end_utc desc)`
- Instance window 조회: `idx_buckets_instance_window (application_instance_id, bucket_start_utc)`
- Retention cleanup: `idx_buckets_accepted_at (accepted_at)`

Notes:

- `duration_buckets_json`과 `endpoints_json`의 histogram monotonicity, boundary set 일치 여부는 DB check가 아니라 `IngestAcceptanceService`에서 검증한다.
- p95/p99는 이 테이블에서 SQL view로 계산하지 않는다. `local_percentiles_json`으로 보존한 starter canonical percentile만 같은 scope의 p95/p99로 사용한다.
- `duration_buckets_json`과 endpoint `durationBuckets`는 distribution display와 진단용 bucket 원자료다. DB view, service, UI가 이 bucket에서 p95/p99 scalar를 만들지 않는다.
- MVP의 `cpu_usage_ratio`, `heap_used_ratio`, `datasource_pool_usage_ratio`는 bucket 안의 latest valid sample을 저장한다.
- `local_percentiles_json`은 optional이다. 존재할 때만 `summary.localPercentiles`의 source/scope/required field를 보존하며, histogram이나 endpoint field를 다시 검증하는 근거로 삼지 않는다.

Post-MVP runtime aggregate migration 후보:

| Candidate Column | Type | Null | 설명 |
|---|---|---|---|
| `cpu_usage_max_ratio` | `numeric(6,5)` | yes | bucket 안 JVM CPU usage ratio 최댓값 |
| `cpu_usage_avg_ratio` | `numeric(6,5)` | yes | bucket 안 JVM CPU usage ratio 산술 평균 |
| `cpu_usage_sample_count` | `integer` | yes | CPU 평균 계산에 사용한 valid sample 수 |
| `heap_used_max_ratio` | `numeric(6,5)` | yes | bucket 안 JVM heap used ratio 최댓값 |
| `heap_used_avg_ratio` | `numeric(6,5)` | yes | bucket 안 JVM heap used ratio 산술 평균 |
| `heap_used_sample_count` | `integer` | yes | heap 평균 계산에 사용한 valid sample 수 |
| `datasource_pool_usage_max_ratio` | `numeric(6,5)` | yes | bucket 안 datasource pool usage ratio 최댓값 |
| `datasource_pool_usage_avg_ratio` | `numeric(6,5)` | yes | bucket 안 datasource pool usage ratio 산술 평균 |
| `datasource_pool_usage_sample_count` | `integer` | yes | datasource 평균 계산에 사용한 valid sample 수 |

이 후보 migration은 MVP `V003`에 포함하지 않는다. 별도 schema version에서 `latest` 의미의 기존 ratio column을 유지하면서 max/avg/sample count를 추가한다. Portal validation은 각 aggregate에 대해 ratio range, `avg <= max`, `latest <= max`, `sample_count > 0`을 검증해야 하며, multi-instance app-level 평균은 sample count 기반 weighted average로 계산한다.

### 3.5 `dashboard_snapshots`

목적: 특정 시점의 dashboard read model 전체를 coarse-grained history snapshot으로 저장한다.

| Column | Type | Null | 설명 |
|---|---|---|---|
| `id` | `uuid` | no | primary key |
| `project_id` | `uuid` | no | `projects.id` |
| `application_id` | `uuid` | no | `applications.id` |
| `generated_at` | `timestamptz` | no | read model 생성 시각 |
| `current_window_start_utc` | `timestamptz` | no | current window 시작 |
| `current_window_end_utc` | `timestamptz` | no | current window 끝 |
| `baseline_window_start_utc` | `timestamptz` | no | baseline window 시작 |
| `baseline_window_end_utc` | `timestamptz` | no | baseline window 끝 |
| `last_accepted_ingest_at` | `timestamptz` | yes | 마지막 accepted ingest 수용 시각 |
| `last_observed_at` | `timestamptz` | yes | 마지막 bucket end 시각 |
| `state_code` | `varchar(40)` | no | read model의 state code |
| `read_model_json` | `jsonb` | no | `read-model-contract` 전체 응답 |
| `created_at` | `timestamptz` | no | row 생성 시각 |

Constraints:

- Primary key: `pk_dashboard_snapshots (id)`
- Foreign key: `fk_snapshots_project_id -> projects(id)`
- Foreign key: `fk_snapshots_application_id -> applications(id)`
- Unique latest-window upsert: `uk_snapshots_app_current_end (application_id, current_window_end_utc)`

Indexes:

- Dashboard latest 조회: `idx_snapshots_app_generated_desc (application_id, generated_at desc)`
- Dashboard window 조회: `idx_snapshots_app_current_window (application_id, current_window_start_utc, current_window_end_utc)`
- State 필터/운영 확인: `idx_snapshots_project_state_generated (project_id, state_code, generated_at desc)`
- Retention cleanup: `idx_snapshots_created_at (created_at)`

Notes:

- `read_model_json`은 UI response source로 저장하되, 계산 source는 service layer다.
- `read_model_json`은 state, starter canonical p95/p99, triage cards, bounded endpoint bucket evidence, bounded instance summary, `endpointPriority`를 포함할 수 있다. raw bucket retention이 지난 뒤 endpoint별 history/detail과 instance trend는 이 bounded evidence까지만 제공한다.
- `state_code`는 조회/운영 편의를 위한 복사 컬럼이다. DB trigger나 view가 state를 계산하지 않는다.
- `generated_at`, `state_code`, `current_window_end_utc`, `read_model_json`이 operational history 파생의 기본 source다. JSON 검색 성능이 부족하면 아래 bounded index/search helper column을 사용하지만, raw metric이나 endpoint timeseries를 저장하는 column으로 확장하지 않는다.
- snapshot cadence는 service scheduler 정책으로 관리한다. DB unique key는 중복 방지와 조회 편의를 돕지만 1시간 cadence 자체를 business rule로 계산하지 않는다.

#### 3.5.1 Bounded Index/Search Helper Column 후보

아래 후보 목록은 저장된 read model 검색 편의를 위한 복사값으로 고정한다. Service layer가 read model 생성 시 함께 채우며, DB trigger/view가 rule이나 state를 다시 계산하지 않는다.

| Candidate Column | Type | 의미 |
|---|---|---|
| `capture_reason` | `varchar(48)` | `scheduled`, `state_change`, `high_confidence_concern`, `short_strong_spike`, `query_fallback` 같은 snapshot row 생성 사유 |
| `primary_rule_id` | `varchar(80)` | 가장 행동 가능한 primary concern의 rule id 복사값 |
| `primary_endpoint_key` | `varchar(240)` | primary concern endpoint의 low-cardinality key 복사값 |
| `max_confidence` | `numeric(4,3)` | snapshot 안 concern confidence 최댓값 |
| `state_code` | `varchar(40)` | read model lifecycle state code 복사값. 기본 table column으로 둔다 |
| `generated_at` | `timestamptz` | read model 생성 시각. 기본 table column으로 둔다 |
| `current_window_end_utc` | `timestamptz` | query/current 판단 window 끝. 기본 table column으로 둔다 |

Index 후보:

- `idx_snapshots_app_capture_generated (application_id, capture_reason, generated_at desc)`
- `idx_snapshots_app_rule_generated (application_id, primary_rule_id, generated_at desc)`
- `idx_snapshots_app_endpoint_generated (application_id, primary_endpoint_key, generated_at desc)`
- `idx_snapshots_app_confidence_generated (application_id, max_confidence desc, generated_at desc)`

이 helper column과 index는 operational history 조회를 빠르게 하기 위한 bounded search surface다. raw path, query string, query key/value, trace id, per-request sample, endpoint별 장기 timeseries 값은 넣지 않는다.

#### 3.5.2 Snapshot Endpoint Evidence Boundary

`read_model_json` 안에 장기 보존할 endpoint evidence는 최대 `10개`다. 우선순위는 top triage card에 연결된 endpoint, `endpointPriority` 상위 항목, high-confidence concern endpoint 순서다.

허용 field 후보는 `method`, `route`, `endpointKey`, `rank`, `reason`, `ruleIds`, `confidence`, `requestCount`, `errorRate`, `durationBuckets`, `baselineDurationBuckets`, `bucketDistributionSource`, `freshness`, `recommendedAction`이다. raw path, query string, query key/value, trace id, per-request sample, endpoint p95/p99는 snapshot JSON에 남기지 않는다.

#### 3.5.3 Snapshot Bounded Instance Summary Boundary

`read_model_json` 안에 instance snapshot trend projection을 위한 bounded instance summary를 둘 수 있다. 기본 cap은 snapshot당 `50개` instance summary다.

우선순위는 application triage에 기여한 instance, stale/down/recovery freshness에 관련된 instance, request count가 높은 active instance 순서다.

허용 field 후보는 `instanceName`, `observationStatus`, `lastAcceptedBucketAt`, `freshnessLabel`, `lastHeartbeatAt`, `connectionMeaning`, `starterPercentilePoint`, `resourceHints`, `applicationTriageContribution`, `endpointEvidenceRefs`다.

이 summary는 stored read model projection source이며 raw instance timeseries, endpoint timeseries, instance health score source가 아니다. API나 UI는 이 field를 조합해 lifecycle state, rule, p95/p99, endpoint priority, operational event를 재계산하지 않는다.

## 4. Retention 정책

MVP 기본 retention:

- `accepted_metric_buckets`: 7일 보관
- `dashboard_snapshots`: 14일 보관
- `projects`, `applications`, `application_instances`: 자동 삭제하지 않음

운영 방식:

- portal 내부 scheduled cleanup service가 retention 기준보다 오래된 bucket/snapshot을 삭제한다.
- cleanup은 application semantics를 계산하지 않고 단순 timestamp 기준으로만 동작한다.
- retention 값은 config로 조정 가능하게 하되, MVP 테스트와 UX는 무제한 history에 의존하지 않는다.
- `accepted_metric_buckets` retention은 30초 raw-ish 계산 원천의 짧은 보관 정책이고, `dashboard_snapshots` retention은 저장된 read model history 보관 정책이다. 두 값을 같은 의미로 해석하지 않는다.

## 5. DB에서 하지 않는 계산

아래 계산은 DB view, trigger, materialized view, stored procedure에 숨기지 않는다.

- lifecycle state 계산 금지
- insight rule ranking 금지
- endpoint priority 재계산 금지
- p95/p99 계산을 DB view/materialized view에 숨기는 것 금지
- histogram bucket에서 percentile scalar를 만드는 DB view/materialized view 금지
- operational event history 계산/deduplication 금지
- zero insight reason 생성 금지
- recovery guidance 문구 생성 금지

DB는 window bucket을 효율적으로 읽고 snapshot을 저장하는 repository의 persistence target 역할만 맡는다.

## 6. Migration 순서 초안

1. `V001__create_projects.sql`
   - `projects` 생성
2. `V002__create_applications_and_instances.sql`
   - `applications`, `application_instances` 생성
   - project/application/instance unique constraint 추가
3. `V003__create_accepted_metric_buckets.sql`
   - accepted bucket 테이블, idempotency unique constraint, bucket window index 추가
4. `V004__add_local_percentiles_to_accepted_metric_buckets.sql`
   - Story 4.0의 `summary.localPercentiles` starter canonical percentile point를 optional JSON으로 보존
5. `V005__create_dashboard_snapshots.sql`
   - snapshot 테이블, latest 조회 index, current window unique upsert key 추가
   - operational history 조회가 필요하면 bounded index/search helper column 후보를 같은 story 또는 보강 migration에서 추가
   - instance snapshot trend projection을 위해 snapshot `read_model_json`의 bounded instance summary shape를 Epic 5 story에서 닫음
6. `V006__seed_local_project.sql`
   - local/demo project key seed
   - raw key는 migration 파일에 평문으로 고정하지 않고 local profile 또는 dev-only seed script에서 주입
   - raw key 전체나 secret 성격의 project key material은 repository lookup surface에 남기지 않음
7. `V007__retention_cleanup_support.sql`
   - retention cleanup에 필요한 index 확인
   - 별도 stored procedure는 만들지 않고 scheduled cleanup service에서 사용
8. Account/auth migration 후보
   - account auth story에서 별도 version으로 추가
   - `users/accounts`, `external_identities`, `refresh_token_families` 또는 동등한 token store metadata 후보
   - provider는 MVP에서 `github`만 허용
   - raw access token, refresh token, GitHub OAuth token, provider raw payload, secret은 평문 저장하지 않음

Operational Event History를 위한 별도 migration은 MVP에 없다.

## 7. IR 직전 고정 결정

- UUID는 application-generated UUID로 고정한다. PostgreSQL `pgcrypto` extension에 의존하지 않는다.
- project key 검증은 `key_prefix`로 project 후보를 찾고, `project_key_hash`에 저장된 BCrypt hash로 검증한다.
- raw project key 같은 secret은 DB row, migration, log, exception, response body, repository lookup surface에 남기지 않는다.
- account signup/login은 GitHub OAuth only이며, local password/password reset/email verification/magic link/다른 OAuth provider/anonymous user schema를 MVP에 만들지 않는다.
- GitHub provider subject는 external identity stable key로 사용한다.
- 우리 서비스 refresh token은 fully stateless 전제로 두지 않고 hashed token 또는 token family metadata를 저장할 수 있는 token store 기준을 유지한다.
- repository 구현 표준은 Spring Data JPA/Jakarta Persistence + Hibernate이며, JPA entity UUID도 application-generated UUID를 사용한다.
- Flyway SQL migration이 schema source of truth다. Hibernate DDL auto 생성/갱신은 사용하지 않는다.
- MVP endpoint bucket은 `accepted_metric_buckets.endpoints_json`에 bounded JSON으로 저장한다. 별도 endpoint table은 만들지 않는다.
- dashboard snapshot 저장은 application별 1시간 scheduled snapshot을 기본으로 한다. ingest transaction commit 직후 30초 bucket마다 snapshot refresh를 수행하지 않는다.
- dashboard query 시점에 snapshot이 없거나 current response로 쓰기에 명백히 오래된 경우 `DashboardReadModelService`가 fallback으로 재생성하고 필요하면 snapshot으로 저장할 수 있다.
- `dashboard_snapshots` retention은 기본 14일이며 config로 조정 가능하게 둔다.
- 의미 있는 state-change, confidence `>= 0.82` high-confidence concern, 짧지만 강한 spike 실험값, dashboard query fallback regeneration 조건에서만 1시간 cadence 외 추가 snapshot capture를 허용한다.
- snapshot endpoint evidence는 최대 10개만 `read_model_json`에 남기고 raw path/query/trace/per-request sample은 저장하지 않는다.
- snapshot bounded instance summary는 최대 50개만 `read_model_json`에 남기고 raw instance timeseries나 endpoint timeseries로 확장하지 않는다.
- instance snapshot trend는 stored dashboard snapshot/read model projection이며 기본 7일, 최대 14일 retention 안에서 bounded 조회한다.
- operational event history는 별도 table 없이 `dashboard_snapshots` 기반 service-layer 파생으로 둔다.
- 별도 Redis queue, PostgreSQL outbox, materialized view는 MVP physical schema에 포함하지 않는다.

## 8. Physical DDL 초안

아래 DDL은 Flyway 기준 migration으로 옮길 수 있는 초안이다. 실제 구현 시 파일은 `V001`~`V004`로 나누되, table/column comment는 각 테이블 생성 migration에 함께 둔다.

### 8.1 `V001__create_projects.sql`

```sql
create table projects (
  id uuid not null,
  name varchar(120) not null,
  key_prefix varchar(32) not null,
  project_key_hash varchar(255) not null,
  status varchar(32) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint pk_projects primary key (id),
  constraint uk_projects_name unique (name),
  constraint uk_projects_key_prefix unique (key_prefix),
  constraint uk_projects_project_key_hash unique (project_key_hash),
  constraint ck_projects_status check (status in ('active', 'disabled'))
);

comment on table projects is '프로젝트 키와 애플리케이션 묶음의 최상위 식별자를 저장한다.';
comment on column projects.id is '프로젝트의 application-generated UUID 기본키.';
comment on column projects.name is '사람이 읽을 수 있는 프로젝트 이름.';
comment on column projects.key_prefix is '프로젝트 키 후보 조회에 사용하는 짧은 접두사.';
comment on column projects.project_key_hash is '원문 프로젝트 키를 저장하지 않기 위한 BCrypt 해시.';
comment on column projects.status is '프로젝트 사용 상태. active 또는 disabled.';
comment on column projects.created_at is '프로젝트 row 생성 시각.';
comment on column projects.updated_at is '프로젝트 row 마지막 수정 시각.';
```

### 8.2 `V002__create_applications_and_instances.sql`

```sql
create table applications (
  id uuid not null,
  project_id uuid not null,
  name varchar(160) not null,
  environment varchar(80) not null,
  status varchar(32) not null,
  first_seen_at timestamptz,
  last_seen_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint pk_applications primary key (id),
  constraint fk_applications_project_id
    foreign key (project_id) references projects (id),
  constraint uk_applications_project_name_env
    unique (project_id, name, environment),
  constraint ck_applications_status check (status in ('active', 'disabled'))
);

create index idx_applications_project_status
  on applications (project_id, status);

comment on table applications is '프로젝트 안에서 애플리케이션과 환경 조합을 식별한다.';
comment on column applications.id is '애플리케이션의 application-generated UUID 기본키.';
comment on column applications.project_id is '소속 프로젝트의 UUID 외래키.';
comment on column applications.name is 'ingest envelope의 application.name 값.';
comment on column applications.environment is 'ingest envelope의 application.environment 값.';
comment on column applications.status is '애플리케이션 사용 상태. active 또는 disabled.';
comment on column applications.first_seen_at is '첫 accepted metric bucket이 수용된 시각.';
comment on column applications.last_seen_at is '마지막 accepted metric bucket이 수용된 시각.';
comment on column applications.created_at is '애플리케이션 row 생성 시각.';
comment on column applications.updated_at is '애플리케이션 row 마지막 수정 시각.';

create table application_instances (
  id uuid not null,
  application_id uuid not null,
  instance_name varchar(200) not null,
  first_seen_at timestamptz not null,
  last_seen_at timestamptz not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint pk_application_instances primary key (id),
  constraint fk_application_instances_application_id
    foreign key (application_id) references applications (id),
  constraint uk_application_instances_app_instance
    unique (application_id, instance_name)
);

create index idx_application_instances_app_last_seen
  on application_instances (application_id, last_seen_at desc);

comment on table application_instances is '애플리케이션의 실행 인스턴스 식별자와 마지막 수신 시각을 저장한다.';
comment on column application_instances.id is '애플리케이션 인스턴스의 application-generated UUID 기본키.';
comment on column application_instances.application_id is '소속 애플리케이션의 UUID 외래키.';
comment on column application_instances.instance_name is 'ingest envelope의 application.instance 값.';
comment on column application_instances.first_seen_at is '해당 인스턴스의 첫 accepted metric bucket 수용 시각.';
comment on column application_instances.last_seen_at is '해당 인스턴스의 마지막 accepted metric bucket 수용 시각.';
comment on column application_instances.created_at is '애플리케이션 인스턴스 row 생성 시각.';
comment on column application_instances.updated_at is '애플리케이션 인스턴스 row 마지막 수정 시각.';
```

### 8.3 `V003__create_accepted_metric_buckets.sql`

```sql
create table accepted_metric_buckets (
  id uuid not null,
  project_id uuid not null,
  application_id uuid not null,
  application_instance_id uuid not null,
  schema_version varchar(16) not null,
  idempotency_key varchar(300) not null,
  payload_hash varchar(128) not null,
  bucket_start_utc timestamptz not null,
  bucket_end_utc timestamptz not null,
  duration_seconds integer not null,
  accepted_at timestamptz not null,
  request_count bigint not null,
  error_count bigint not null,
  duration_buckets_json jsonb not null,
  cpu_usage_ratio numeric(6,5),
  heap_used_ratio numeric(6,5),
  datasource_pool_usage_ratio numeric(6,5),
  endpoints_json jsonb not null,
  created_at timestamptz not null,
  constraint pk_accepted_metric_buckets primary key (id),
  constraint fk_buckets_project_id
    foreign key (project_id) references projects (id),
  constraint fk_buckets_application_id
    foreign key (application_id) references applications (id),
  constraint fk_buckets_application_instance_id
    foreign key (application_instance_id) references application_instances (id),
  constraint uk_buckets_project_idempotency_key
    unique (project_id, idempotency_key),
  constraint uk_buckets_instance_bucket_start
    unique (application_instance_id, bucket_start_utc),
  constraint ck_buckets_schema_version check (schema_version = '1.0'),
  constraint ck_buckets_duration_30 check (duration_seconds = 30),
  constraint ck_buckets_window_positive check (bucket_end_utc > bucket_start_utc),
  constraint ck_buckets_counts_non_negative
    check (request_count >= 0 and error_count >= 0 and error_count <= request_count),
  constraint ck_buckets_duration_buckets_array
    check (jsonb_typeof(duration_buckets_json) = 'array'),
  constraint ck_buckets_endpoints_array
    check (jsonb_typeof(endpoints_json) = 'array'),
  constraint ck_buckets_cpu_ratio_range
    check (cpu_usage_ratio is null or (cpu_usage_ratio >= 0 and cpu_usage_ratio <= 1)),
  constraint ck_buckets_heap_ratio_range
    check (heap_used_ratio is null or (heap_used_ratio >= 0 and heap_used_ratio <= 1)),
  constraint ck_buckets_datasource_ratio_range
    check (datasource_pool_usage_ratio is null or (datasource_pool_usage_ratio >= 0 and datasource_pool_usage_ratio <= 1))
);

create index idx_buckets_app_window
  on accepted_metric_buckets (application_id, bucket_start_utc, bucket_end_utc);

create index idx_buckets_app_last_end
  on accepted_metric_buckets (application_id, bucket_end_utc desc);

create index idx_buckets_instance_window
  on accepted_metric_buckets (application_instance_id, bucket_start_utc);

create index idx_buckets_accepted_at
  on accepted_metric_buckets (accepted_at);

comment on table accepted_metric_buckets is '검증을 통과해 수용된 30초 단위 애플리케이션 및 endpoint metric bucket을 저장한다.';
comment on column accepted_metric_buckets.id is 'accepted metric bucket의 application-generated UUID 기본키.';
comment on column accepted_metric_buckets.project_id is 'bucket이 속한 프로젝트의 UUID 외래키.';
comment on column accepted_metric_buckets.application_id is 'bucket이 속한 애플리케이션의 UUID 외래키.';
comment on column accepted_metric_buckets.application_instance_id is 'bucket을 보낸 애플리케이션 인스턴스의 UUID 외래키.';
comment on column accepted_metric_buckets.schema_version is 'ingest envelope schema version. MVP에서는 1.0만 허용한다.';
comment on column accepted_metric_buckets.idempotency_key is 'starter가 보낸 멱등성 키.';
comment on column accepted_metric_buckets.payload_hash is '동일 멱등성 키에 다른 payload가 들어왔는지 감지하기 위한 payload hash.';
comment on column accepted_metric_buckets.bucket_start_utc is '30초 UTC bucket 시작 시각.';
comment on column accepted_metric_buckets.bucket_end_utc is '30초 UTC bucket 종료 시각.';
comment on column accepted_metric_buckets.duration_seconds is 'bucket 길이. MVP에서는 30초로 고정한다.';
comment on column accepted_metric_buckets.accepted_at is 'portal이 bucket을 수용한 시각.';
comment on column accepted_metric_buckets.request_count is 'bucket window의 애플리케이션 전체 요청 수.';
comment on column accepted_metric_buckets.error_count is 'bucket window의 애플리케이션 전체 오류 수.';
comment on column accepted_metric_buckets.duration_buckets_json is '애플리케이션 전체 HTTP server duration cumulative histogram bucket 배열.';
comment on column accepted_metric_buckets.cpu_usage_ratio is 'JVM process CPU 사용률 샘플. 없으면 null.';
comment on column accepted_metric_buckets.heap_used_ratio is 'JVM heap 사용률 샘플. 없으면 null.';
comment on column accepted_metric_buckets.datasource_pool_usage_ratio is 'datasource connection pool 사용률 샘플. 없으면 null.';
comment on column accepted_metric_buckets.endpoints_json is 'bounded endpoint별 method, normalized route, request/error count, duration histogram bucket 배열.';
comment on column accepted_metric_buckets.created_at is 'accepted metric bucket row 생성 시각.';
```

### 8.4 `V004__add_local_percentiles_to_accepted_metric_buckets.sql`

```sql
alter table accepted_metric_buckets
  add column local_percentiles_json jsonb;

alter table accepted_metric_buckets
  add constraint ck_buckets_local_percentiles_object
    check (local_percentiles_json is null or jsonb_typeof(local_percentiles_json) = 'object');

comment on column accepted_metric_buckets.local_percentiles_json is
  'starter가 보낸 instance_bucket scope의 canonical p95/p99 point. 없으면 null.';
```

### 8.5 `V005__create_dashboard_snapshots.sql`

```sql
create table dashboard_snapshots (
  id uuid not null,
  project_id uuid not null,
  application_id uuid not null,
  generated_at timestamptz not null,
  current_window_start_utc timestamptz not null,
  current_window_end_utc timestamptz not null,
  baseline_window_start_utc timestamptz not null,
  baseline_window_end_utc timestamptz not null,
  last_accepted_ingest_at timestamptz,
  last_observed_at timestamptz,
  state_code varchar(40) not null,
  read_model_json jsonb not null,
  created_at timestamptz not null,
  constraint pk_dashboard_snapshots primary key (id),
  constraint fk_snapshots_project_id
    foreign key (project_id) references projects (id),
  constraint fk_snapshots_application_id
    foreign key (application_id) references applications (id),
  constraint uk_snapshots_app_current_end
    unique (application_id, current_window_end_utc),
  constraint ck_snapshots_current_window_positive
    check (current_window_end_utc > current_window_start_utc),
  constraint ck_snapshots_baseline_window_positive
    check (baseline_window_end_utc > baseline_window_start_utc),
  constraint ck_snapshots_read_model_object
    check (jsonb_typeof(read_model_json) = 'object'),
  constraint ck_snapshots_state_code
    check (state_code in (
      'waiting_first_data',
      'unknown',
      'idle',
      'active',
      'stale',
      'down',
      'degraded'
    ))
);

create index idx_snapshots_app_generated_desc
  on dashboard_snapshots (application_id, generated_at desc);

create index idx_snapshots_app_current_window
  on dashboard_snapshots (application_id, current_window_start_utc, current_window_end_utc);

create index idx_snapshots_project_state_generated
  on dashboard_snapshots (project_id, state_code, generated_at desc);

create index idx_snapshots_created_at
  on dashboard_snapshots (created_at);

comment on table dashboard_snapshots is '특정 시점의 dashboard read model 전체를 coarse-grained history snapshot으로 저장한다.';
comment on column dashboard_snapshots.id is 'dashboard snapshot의 application-generated UUID 기본키.';
comment on column dashboard_snapshots.project_id is 'snapshot이 속한 프로젝트의 UUID 외래키.';
comment on column dashboard_snapshots.application_id is 'snapshot이 속한 애플리케이션의 UUID 외래키.';
comment on column dashboard_snapshots.generated_at is 'portal service가 read model을 생성한 시각.';
comment on column dashboard_snapshots.current_window_start_utc is 'read model current window 시작 시각.';
comment on column dashboard_snapshots.current_window_end_utc is 'read model current window 종료 시각.';
comment on column dashboard_snapshots.baseline_window_start_utc is 'read model baseline window 시작 시각.';
comment on column dashboard_snapshots.baseline_window_end_utc is 'read model baseline window 종료 시각.';
comment on column dashboard_snapshots.last_accepted_ingest_at is 'snapshot 생성 시점 기준 마지막 accepted ingest 수용 시각.';
comment on column dashboard_snapshots.last_observed_at is 'snapshot 생성 시점 기준 마지막 bucket end 시각.';
comment on column dashboard_snapshots.state_code is 'read model에 포함된 lifecycle state code의 조회용 복사 값.';
comment on column dashboard_snapshots.read_model_json is 'read-model-contract 전체 응답 JSON. UI 표시의 source of truth.';
comment on column dashboard_snapshots.created_at is 'dashboard snapshot row 생성 시각.';
```

## 9. Story 분배 기준

- Epic 1에서는 `projects`, `applications`, `application_instances`와 migration/test 기반시설을 구현한다.
- Epic 3에서는 `accepted_metric_buckets`와 idempotency conflict 처리를 구현한다.
- Epic 5에서는 `dashboard_snapshots`와 read model snapshot 저장/조회 repository를 구현한다.
- Epic 5에서는 Project/Application navigation, Application Dashboard, Instance evidence, Instance snapshot trend, Snapshot/history read model이 사용할 snapshot 저장/조회 경계를 닫는다.
- Epic 5/6의 operational event history와 instance snapshot trend는 `dashboard_snapshots`를 재사용하며 별도 `operational_events` migration이나 raw instance timeseries migration을 만들지 않는다.
- Epic 6 Story 6.10에서는 `account_project_memberships`로 account-project authorization boundary를 닫는다.
- Epic 6에서는 retention cleanup schedule과 local/demo seed를 정리한다.
