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
