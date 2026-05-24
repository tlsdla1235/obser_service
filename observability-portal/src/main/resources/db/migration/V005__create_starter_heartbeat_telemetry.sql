create table starter_heartbeat_telemetry (
  id uuid not null,
  project_id uuid not null,
  application_name varchar(160) not null,
  environment varchar(80) not null,
  instance_name varchar(200) not null,
  starter_version varchar(80) not null,
  last_sent_at_utc timestamptz not null,
  last_received_at_utc timestamptz not null,
  last_sequence bigint not null,
  interval_seconds integer not null,
  metadata_status varchar(32) not null,
  heartbeat_status varchar(32) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint pk_starter_heartbeat_telemetry primary key (id),
  constraint fk_starter_heartbeat_project_id
    foreign key (project_id) references projects (id),
  constraint uk_starter_heartbeat_identity
    unique (project_id, application_name, environment, instance_name),
  constraint ck_starter_heartbeat_sequence_non_negative
    check (last_sequence >= 0),
  constraint ck_starter_heartbeat_interval_positive
    check (interval_seconds > 0),
  constraint ck_starter_heartbeat_metadata_status
    check (metadata_status in ('valid')),
  constraint ck_starter_heartbeat_status
    check (heartbeat_status in ('received'))
);

create index idx_starter_heartbeat_project_received
  on starter_heartbeat_telemetry (project_id, last_received_at_utc desc);

comment on table starter_heartbeat_telemetry is 'starter heartbeat의 최신 control-plane 수신 상태를 저장한다.';
comment on column starter_heartbeat_telemetry.id is 'heartbeat telemetry row의 application-generated UUID 기본키.';
comment on column starter_heartbeat_telemetry.project_id is 'heartbeat가 속한 프로젝트의 UUID 외래키.';
comment on column starter_heartbeat_telemetry.application_name is 'heartbeat payload의 application.name 값. catalog upsert source가 아니다.';
comment on column starter_heartbeat_telemetry.environment is 'heartbeat payload의 application.environment 값. catalog upsert source가 아니다.';
comment on column starter_heartbeat_telemetry.instance_name is 'heartbeat payload의 application.instance 값. catalog upsert source가 아니다.';
comment on column starter_heartbeat_telemetry.starter_version is 'heartbeat를 보낸 starter version.';
comment on column starter_heartbeat_telemetry.last_sent_at_utc is 'starter가 마지막 heartbeat를 보냈다고 보고한 UTC 시각.';
comment on column starter_heartbeat_telemetry.last_received_at_utc is 'portal이 마지막 valid heartbeat를 수신한 UTC 시각.';
comment on column starter_heartbeat_telemetry.last_sequence is '마지막 valid heartbeat의 starter sequence 값.';
comment on column starter_heartbeat_telemetry.interval_seconds is 'starter가 보고한 heartbeat interval seconds 값.';
comment on column starter_heartbeat_telemetry.metadata_status is 'portal이 heartbeat application metadata를 검증한 결과.';
comment on column starter_heartbeat_telemetry.heartbeat_status is 'portal의 heartbeat 수신 결과.';
comment on column starter_heartbeat_telemetry.created_at is 'heartbeat telemetry row 생성 시각.';
comment on column starter_heartbeat_telemetry.updated_at is 'heartbeat telemetry row 마지막 갱신 시각.';
