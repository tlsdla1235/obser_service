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
