---
artifactType: database-schema
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
database: PostgreSQL
status: mvc-version-generated
date: 2026-05-09
---

# Database Schema - MVC Version

## 1. 목적과 경계

PostgreSQL은 MVP에서 아래 데이터만 저장한다.

- project/application/instance metadata
- idempotent하게 수용된 30초 metric bucket
- dashboard first-screen read model snapshot

PostgreSQL을 범용 TSDB나 rule engine으로 쓰지 않는다. lifecycle state, insight rule ranking, endpoint priority, p95 계산의 source of truth는 portal service layer다.

MVP에서는 별도 `operational_events` 테이블을 만들지 않는다. Operational event history는 Epic 5/6에서 `dashboard_snapshots.generated_at`, `state_code`, `read_model_json`을 기반으로 service layer가 파생한다.

## 2. Schema 개요

초기 MVP 테이블은 아래 5개로 시작한다.

| Table | 목적 |
|---|---|
| `projects` | project key 검증과 project 식별 |
| `applications` | project 안의 application/environment 식별 |
| `application_instances` | application instance 식별과 last seen 추적 |
| `accepted_metric_buckets` | idempotent하게 수용된 30초 app/endpoint bucket 저장 |
| `dashboard_snapshots` | UI가 그대로 소비할 read model snapshot 저장 |

endpoint별 데이터는 `accepted_metric_buckets.endpoints_json`에 bounded JSON으로 저장한다. MVP에서 endpoint 수는 이미 정규화된 route 기준의 bounded top-N 또는 allow set으로 제한되므로 별도 endpoint table 없이도 15분 current/baseline window를 service layer에서 병합할 수 있다. 이 제한은 저장/조회 cardinality cap이며 route attribution fallback이 아니다.

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

별도 `operational_events` table, event repository, materialized view는 alert acknowledgement, durable event id, 장기 event retention, event annotation, alert delivery dedupe가 필요해질 때 후속 확장으로 검토한다.

이 결정은 Epic 2와 Epic 3의 migration 범위를 변경하지 않는다.

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
- p95는 이 테이블에서 SQL view로 계산하지 않는다. `HistogramMergeService`가 window bucket을 읽어 `histogram-merge` contract에 따라 계산한다.

### 3.5 `dashboard_snapshots`

목적: dashboard first-screen이 그대로 소비할 read model snapshot을 저장한다.

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
- `state_code`는 조회/운영 편의를 위한 복사 컬럼이다. DB trigger나 view가 state를 계산하지 않는다.

## 4. Retention 정책

MVP 기본 retention:

- `accepted_metric_buckets`: 7일 보관
- `dashboard_snapshots`: 7일 보관
- `projects`, `applications`, `application_instances`: 자동 삭제하지 않음

운영 방식:

- portal 내부 scheduled cleanup service가 retention 기준보다 오래된 bucket/snapshot을 삭제한다.
- cleanup은 application semantics를 계산하지 않고 단순 timestamp 기준으로만 동작한다.
- retention 값은 config로 조정 가능하게 하되, MVP 테스트와 UX는 장기 history에 의존하지 않는다.

## 5. DB에서 하지 않는 계산

아래 계산은 DB view, trigger, materialized view, stored procedure에 숨기지 않는다.

- lifecycle state 계산 금지
- insight rule ranking 금지
- endpoint priority 재계산 금지
- p95 계산을 DB view/materialized view에 숨기는 것 금지
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
4. `V004__create_dashboard_snapshots.sql`
   - snapshot 테이블, latest 조회 index, current window unique upsert key 추가
5. `V005__seed_local_project.sql`
   - local/demo project key seed
   - raw key는 migration 파일에 평문으로 고정하지 않고 local profile 또는 dev-only seed script에서 주입
6. `V006__retention_cleanup_support.sql`
   - retention cleanup에 필요한 index 확인
   - 별도 stored procedure는 만들지 않고 scheduled cleanup service에서 사용

Operational Event History를 위한 별도 migration은 MVP에 없다.

## 7. IR 직전 고정 결정

- UUID는 application-generated UUID로 고정한다. PostgreSQL `pgcrypto` extension에 의존하지 않는다.
- project key 검증은 `key_prefix`로 project 후보를 찾고, `project_key_hash`에 저장된 BCrypt hash로 검증한다.
- MVP endpoint bucket은 `accepted_metric_buckets.endpoints_json`에 bounded JSON으로 저장한다. 별도 endpoint table은 만들지 않는다.
- dashboard snapshot refresh는 ingest transaction commit 후 portal in-process service 작업으로 수행한다.
- dashboard query 시점에 snapshot이 없거나 명백히 오래된 경우 `DashboardReadModelService`가 fallback으로 재생성할 수 있다.
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

### 8.4 `V004__create_dashboard_snapshots.sql`

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

comment on table dashboard_snapshots is 'dashboard first-screen UI가 그대로 소비할 read model snapshot을 저장한다.';
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
- Epic 5/6의 operational event history는 `dashboard_snapshots`를 재사용하며 별도 `operational_events` migration을 만들지 않는다.
- Epic 6에서는 retention cleanup schedule과 local/demo seed를 정리한다.
