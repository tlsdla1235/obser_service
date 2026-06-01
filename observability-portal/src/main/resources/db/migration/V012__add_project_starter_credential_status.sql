alter table projects
  add column starter_credential_status varchar(32) not null default 'active',
  add column starter_credential_issued_at timestamptz,
  add column starter_credential_rotated_at timestamptz,
  add column starter_credential_revoked_at timestamptz;

update projects
set starter_credential_issued_at = created_at
where starter_credential_issued_at is null;

alter table projects
  alter column starter_credential_issued_at set not null,
  alter column starter_credential_issued_at set default now(),
  add constraint ck_projects_starter_credential_status
    check (starter_credential_status in ('active', 'revoked'));

comment on column projects.starter_credential_status is
  'starter ingest credential의 사용 상태. project 표시 상태와 분리해 active 또는 revoked를 저장한다.';
comment on column projects.starter_credential_issued_at is
  '현재 starter credential metadata가 처음 발급된 시각.';
comment on column projects.starter_credential_rotated_at is
  'starter credential이 마지막으로 회전된 시각. 아직 회전되지 않았으면 null.';
comment on column projects.starter_credential_revoked_at is
  'starter credential이 폐기된 시각. active 상태이면 null.';
