create table accounts (
  id uuid not null,
  status varchar(32) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint pk_accounts primary key (id),
  constraint ck_accounts_status check (status in ('active', 'disabled'))
);

comment on table accounts is 'GitHub OAuth 검증 후 생성되는 내부 account row를 저장한다.';
comment on column accounts.id is '내부 account의 application-generated UUID 기본키.';
comment on column accounts.status is 'account 사용 상태. active 또는 disabled.';
comment on column accounts.created_at is 'account row 생성 시각.';
comment on column accounts.updated_at is 'account row 마지막 수정 시각.';

create table external_identities (
  id uuid not null,
  account_id uuid not null,
  provider varchar(32) not null,
  provider_subject varchar(160) not null,
  email varchar(320),
  display_name varchar(160),
  avatar_url varchar(512),
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint pk_external_identities primary key (id),
  constraint fk_external_identities_account_id
    foreign key (account_id) references accounts (id),
  constraint uk_external_identities_provider_subject
    unique (provider, provider_subject),
  constraint ck_external_identities_provider check (provider in ('github'))
);

create index idx_external_identities_account
  on external_identities (account_id);

comment on table external_identities is '외부 OAuth provider subject와 내부 account 연결을 저장한다.';
comment on column external_identities.id is 'external identity row의 application-generated UUID 기본키.';
comment on column external_identities.account_id is '연결된 내부 account UUID.';
comment on column external_identities.provider is 'MVP에서 허용하는 OAuth provider. github만 허용한다.';
comment on column external_identities.provider_subject is 'GitHub user id 또는 provider subject stable key.';
comment on column external_identities.email is 'profile metadata email. stable identity key로 사용하지 않는다.';
comment on column external_identities.display_name is 'profile metadata display name.';
comment on column external_identities.avatar_url is 'profile metadata avatar URL.';
comment on column external_identities.created_at is 'external identity row 생성 시각.';
comment on column external_identities.updated_at is 'external identity row 마지막 수정 시각.';

create table refresh_token_families (
  id uuid not null,
  account_id uuid not null,
  status varchar(32) not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint pk_refresh_token_families primary key (id),
  constraint uk_refresh_token_families_id_account unique (id, account_id),
  constraint fk_refresh_token_families_account_id
    foreign key (account_id) references accounts (id),
  constraint ck_refresh_token_families_status
    check (status in ('active', 'revoked', 'reuse_detected'))
);

create index idx_refresh_token_families_account
  on refresh_token_families (account_id);

comment on table refresh_token_families is 'refresh token rotation/revoke/reuse detection family metadata를 저장한다.';
comment on column refresh_token_families.id is 'refresh token family UUID 기본키.';
comment on column refresh_token_families.account_id is 'family 소유 account UUID.';
comment on column refresh_token_families.status is 'family 상태. active, revoked, reuse_detected.';
comment on column refresh_token_families.created_at is 'family 생성 시각.';
comment on column refresh_token_families.updated_at is 'family 마지막 수정 시각.';

create table refresh_tokens (
  id uuid not null,
  family_id uuid not null,
  account_id uuid not null,
  token_hash varchar(64) not null,
  status varchar(32) not null,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  revoked_at timestamptz,
  reuse_detected_at timestamptz,
  created_at timestamptz not null,
  constraint pk_refresh_tokens primary key (id),
  constraint fk_refresh_tokens_account_id
    foreign key (account_id) references accounts (id),
  constraint fk_refresh_tokens_family_account
    foreign key (family_id, account_id) references refresh_token_families (id, account_id),
  constraint uk_refresh_tokens_token_hash unique (token_hash),
  constraint ck_refresh_tokens_status
    check (status in ('active', 'rotated', 'revoked', 'reuse_detected')),
  constraint ck_refresh_tokens_hash_hex
    check (token_hash ~ '^[0-9a-f]{64}$')
);

create index idx_refresh_tokens_family
  on refresh_tokens (family_id);

create index idx_refresh_tokens_account_created
  on refresh_tokens (account_id, created_at desc);

comment on table refresh_tokens is 'refresh token 원문 대신 SHA-256 hash와 rotation 상태만 저장한다.';
comment on column refresh_tokens.id is 'refresh token metadata row UUID 기본키.';
comment on column refresh_tokens.family_id is '연결된 refresh token family UUID.';
comment on column refresh_tokens.account_id is 'token 소유 account UUID.';
comment on column refresh_tokens.token_hash is 'refresh token 원문을 저장하지 않기 위한 SHA-256 hex hash.';
comment on column refresh_tokens.status is 'token 상태. active, rotated, revoked, reuse_detected.';
comment on column refresh_tokens.expires_at is 'refresh token 만료 시각.';
comment on column refresh_tokens.consumed_at is '정상 rotation으로 소비된 시각.';
comment on column refresh_tokens.revoked_at is 'logout/revoke 처리 시각.';
comment on column refresh_tokens.reuse_detected_at is '재사용 감지 시각.';
comment on column refresh_tokens.created_at is 'refresh token metadata row 생성 시각.';
