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
  capture_reason varchar(64),
  read_model_json jsonb not null,
  created_at timestamptz not null,
  constraint pk_dashboard_snapshots primary key (id),
  constraint fk_dashboard_snapshots_project_id
    foreign key (project_id) references projects (id),
  constraint fk_dashboard_snapshots_application_id
    foreign key (application_id) references applications (id),
  constraint ck_dashboard_snapshots_current_window_positive
    check (current_window_end_utc > current_window_start_utc),
  constraint ck_dashboard_snapshots_baseline_window_positive
    check (baseline_window_end_utc > baseline_window_start_utc),
  constraint ck_dashboard_snapshots_read_model_object
    check (jsonb_typeof(read_model_json) = 'object'),
  constraint ck_dashboard_snapshots_state_code
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

create index idx_dashboard_snapshots_project_app_generated_desc
  on dashboard_snapshots (project_id, application_id, generated_at desc, id desc);

create index idx_dashboard_snapshots_created_at
  on dashboard_snapshots (created_at);

comment on table dashboard_snapshots is '특정 시점의 dashboard read model 전체를 저장하는 coarse-grained snapshot read-side 원천이다.';
comment on column dashboard_snapshots.id is 'dashboard snapshot의 application-generated UUID 기본키.';
comment on column dashboard_snapshots.project_id is 'snapshot이 속한 프로젝트의 UUID 외래키.';
comment on column dashboard_snapshots.application_id is 'snapshot이 속한 애플리케이션의 UUID 외래키.';
comment on column dashboard_snapshots.generated_at is 'portal service가 dashboard read model을 생성한 UTC 시각.';
comment on column dashboard_snapshots.current_window_start_utc is '저장 당시 dashboard current window 시작 시각.';
comment on column dashboard_snapshots.current_window_end_utc is '저장 당시 dashboard current window 종료 시각.';
comment on column dashboard_snapshots.baseline_window_start_utc is '저장 당시 dashboard baseline window 시작 시각.';
comment on column dashboard_snapshots.baseline_window_end_utc is '저장 당시 dashboard baseline window 종료 시각.';
comment on column dashboard_snapshots.last_accepted_ingest_at is 'snapshot 생성 시점 기준 마지막 accepted ingest 수용 시각. 없으면 null.';
comment on column dashboard_snapshots.last_observed_at is 'snapshot 생성 시점 기준 마지막 관측 bucket end 시각. 없으면 null.';
comment on column dashboard_snapshots.state_code is '저장된 application-level lifecycle state code 복사 값. instance state나 health score가 아니다.';
comment on column dashboard_snapshots.capture_reason is 'snapshot 저장 사유를 해석하지 않고 복사하는 nullable opaque metadata.';
comment on column dashboard_snapshots.read_model_json is '저장된 dashboard read model JSON object. Story 5.7 trend source는 instanceSummary.items 배열로 제한한다.';
comment on column dashboard_snapshots.created_at is 'dashboard snapshot row 생성 시각.';
