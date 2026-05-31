create table account_project_memberships (
  id uuid not null,
  account_id uuid not null,
  project_id uuid not null,
  role varchar(32) not null,
  status varchar(32) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint pk_account_project_memberships primary key (id),
  constraint fk_account_project_memberships_account_id
    foreign key (account_id) references accounts (id),
  constraint fk_account_project_memberships_project_id
    foreign key (project_id) references projects (id),
  constraint uk_account_project_memberships_account_project
    unique (account_id, project_id),
  constraint ck_account_project_memberships_role
    check (role in ('member')),
  constraint ck_account_project_memberships_status
    check (status in ('active', 'disabled'))
);

create index idx_account_project_memberships_account_status_project
  on account_project_memberships (account_id, status, project_id);

create index idx_account_project_memberships_project_status_account
  on account_project_memberships (project_id, status, account_id);

comment on table account_project_memberships is
  'account와 project의 N:M authorization membership을 저장한다.';
comment on column account_project_memberships.id is
  'account-project membership row의 application-generated UUID 기본키.';
comment on column account_project_memberships.account_id is
  'membership을 가진 내부 account UUID. accounts.id를 참조한다.';
comment on column account_project_memberships.project_id is
  'membership으로 접근 가능한 project UUID. projects.id를 참조한다.';
comment on column account_project_memberships.role is
  'MVP membership role. Story 6.10에서는 member만 허용한다.';
comment on column account_project_memberships.status is
  'membership 사용 상태. active 또는 disabled이며 active만 authorization에 사용한다.';
comment on column account_project_memberships.created_at is
  'membership row 생성 시각.';
comment on column account_project_memberships.updated_at is
  'membership row 마지막 수정 시각.';
