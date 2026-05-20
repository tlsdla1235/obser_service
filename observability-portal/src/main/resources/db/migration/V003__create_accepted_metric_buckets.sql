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
