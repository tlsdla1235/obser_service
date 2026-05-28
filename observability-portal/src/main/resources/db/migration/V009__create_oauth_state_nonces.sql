create table oauth_state_nonces (
  id uuid not null,
  nonce_hash varchar(64) not null,
  status varchar(32) not null,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null,
  constraint pk_oauth_state_nonces primary key (id),
  constraint uk_oauth_state_nonces_nonce_hash unique (nonce_hash),
  constraint ck_oauth_state_nonces_status
    check (status in ('active', 'consumed')),
  constraint ck_oauth_state_nonces_hash_hex
    check (nonce_hash ~ '^[0-9a-f]{64}$')
);

create index idx_oauth_state_nonces_expires
  on oauth_state_nonces (expires_at);

comment on table oauth_state_nonces is 'OAuth state nonce 원문 대신 SHA-256 hash와 1회성 사용 상태를 저장한다.';
comment on column oauth_state_nonces.id is 'OAuth state nonce row의 application-generated UUID 기본키.';
comment on column oauth_state_nonces.nonce_hash is 'state payload nonce 원문을 저장하지 않기 위한 SHA-256 hex hash.';
comment on column oauth_state_nonces.status is 'state nonce 상태. active 또는 consumed.';
comment on column oauth_state_nonces.expires_at is 'state nonce 만료 시각.';
comment on column oauth_state_nonces.consumed_at is 'callback에서 1회 소비된 시각.';
comment on column oauth_state_nonces.created_at is 'state nonce row 생성 시각.';
