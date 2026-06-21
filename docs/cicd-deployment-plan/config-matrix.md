# Epic 1 설정/시크릿 매트릭스

이 문서는 `local / ci / prod` profile에서 외부 주입 설정값을 어디서 소유하는지 정리한다.
현재 로컬에서 쓰던 `.private/github-oauth.properties`와 `.env` 값은 **local 전용**으로 유지하고,
CI와 prod는 환경변수 이름을 interface로 삼는다.

## 운영자 체크리스트

- local 값은 `.private/github-oauth.properties`, `.private/smoke-*.env`, `.env`에만 둔다.
- CI 값은 GitHub Secrets 또는 workflow env로 주입한다. 실제 운영 secret을 CI에 복사하지 않는다.
- prod 값은 AWS SSM Parameter Store와 systemd environment handoff로 주입한다.
- prod OAuth App은 local/dev OAuth App과 분리한다.
- prod에서 필수 secret이 비어 있으면 application startup이 실패해야 한다.
- secret 원문은 채팅, issue, README, log, migration, tracked property file에 붙이지 않는다.
- OAuth callback `code`/`state` query는 access log에 남기지 않는다. Nginx는 query 없는 `$uri` 기반 log format을 사용한다.

## 값 출처 요약

| 환경 | 값 출처 | 비고 |
|---|---|---|
| local | `.private/github-oauth.properties`, `.private/smoke-seed.properties`, `.private/smoke-auth.env`, `.private/smoke-project.env`, `.env` | Git ignored. 현재 로컬 값을 그대로 이식한다. |
| ci | GitHub Secrets, GitHub Actions env, Testcontainers/service container | `SPRING_PROFILES_ACTIVE=ci`를 명시한다. |
| prod | AWS SSM Parameter Store, systemd EnvironmentFile, EC2 instance profile | `SPRING_PROFILES_ACTIVE=prod`를 명시한다. |

## 설정 키 매트릭스

| property key | env var 이름 | local 출처 | ci 출처 | prod 출처 | secret | 기본값 가능 | prod fail-closed | 현재 사용 위치 |
|---|---|---|---|---|---|---|---|---|
| `spring.config.import` | `SPRING_CONFIG_IMPORT` | `application-local.properties`의 `.private` optional import | 사용 안 함 | 사용 안 함 | no | yes | no | `application-local.properties` |
| `spring.jpa.hibernate.ddl-auto` | `SPRING_JPA_HIBERNATE_DDL_AUTO` | 공통 `none` | 공통 `none` | 공통 `none` | no | yes | no | Spring JPA |
| `server.address` | `SERVER_ADDRESS` | 비움 | 비움 | systemd 기본 `127.0.0.1`, SSM/env override 가능 | no | `127.0.0.1` 가능 | no | Spring Boot server bind address |
| `server.port` | `SERVER_PORT` | `.private/github-oauth.properties` 또는 env | CI env | SSM/systemd env | no | `8080` 가능 | no | Spring Boot server |
| `server.forward-headers-strategy` | `SERVER_FORWARD_HEADERS_STRATEGY` | 비움 | 비움 | SSM/systemd env | no | `framework` 가능 | no | Spring Boot/Nginx proxy |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `.private/github-oauth.properties` | GitHub Secrets/env 또는 Testcontainers | SSM String | semi | no | yes | Spring datasource, Flyway, JPA, JdbcTemplate |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `.private/github-oauth.properties` | GitHub Secrets/env | SSM String | semi | no | yes | Spring datasource |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | `.private/github-oauth.properties` | GitHub Secrets | SSM SecureString | yes | no | yes | Spring datasource |
| `portal.auth.github.client-id` | `PORTAL_AUTH_GITHUB_CLIENT_ID` | local GitHub OAuth App | GitHub Secrets/env | SSM String | semi | no | yes | `GithubOAuthAppProperties`, `HttpGithubOAuthClient` |
| `portal.auth.github.client-secret` | `PORTAL_AUTH_GITHUB_CLIENT_SECRET` | local GitHub OAuth App | GitHub Secrets | SSM SecureString | yes | no | yes | `GithubOAuthAppProperties`, `HttpGithubOAuthClient` |
| `portal.auth.github.redirect-uri` | `PORTAL_AUTH_GITHUB_REDIRECT_URI` | `http://localhost:8080/api/auth/github/callback` | CI env | SSM String | no | no | yes | `HttpGithubOAuthClient` authorize/token exchange |
| `portal.auth.github.homepage-url` | `PORTAL_AUTH_GITHUB_HOMEPAGE_URL` | `http://localhost:8080/dashboard/` | CI env | SSM String | no | no | yes | `GithubOAuthAppProperties` |
| `portal.auth.github.authorize-uri` | `PORTAL_AUTH_GITHUB_AUTHORIZE_URI` | 기본 GitHub endpoint | 기본 GitHub endpoint | 기본 GitHub endpoint | no | yes | no | `GithubOAuthAppProperties` |
| `portal.auth.github.token-uri` | `PORTAL_AUTH_GITHUB_TOKEN_URI` | 기본 GitHub endpoint | 기본 GitHub endpoint | 기본 GitHub endpoint | no | yes | no | `GithubOAuthAppProperties` |
| `portal.auth.github.user-uri` | `PORTAL_AUTH_GITHUB_USER_URI` | 기본 GitHub endpoint | 기본 GitHub endpoint | 기본 GitHub endpoint | no | yes | no | `GithubOAuthAppProperties` |
| `portal.auth.service-token.signing-key` | `PORTAL_AUTH_SERVICE_TOKEN_SIGNING_KEY` | `.private/github-oauth.properties` | GitHub Secrets | SSM SecureString | yes | local만 가능 | yes | `ServiceTokenIssuer` |
| `portal.auth.service-token.access-token-ttl` | `PORTAL_AUTH_SERVICE_TOKEN_ACCESS_TOKEN_TTL` | 기본 `PT15M` | 기본 `PT15M` | 기본 `PT15M` 또는 SSM | no | yes | no | `ServiceTokenIssuer` |
| `portal.auth.service-token.refresh-token-ttl` | `PORTAL_AUTH_SERVICE_TOKEN_REFRESH_TOKEN_TTL` | 기본 `P30D` | 기본 `P30D` | 기본 `P30D` 또는 SSM | no | yes | no | `ServiceTokenIssuer` |
| `portal.auth.oauth-state.signing-key` | `PORTAL_AUTH_OAUTH_STATE_SIGNING_KEY` | `.private/github-oauth.properties` | GitHub Secrets | SSM SecureString | yes | local만 가능 | yes | `OAuthStateSigner` |
| `portal.auth.oauth-state.ttl` | `PORTAL_AUTH_OAUTH_STATE_TTL` | 기본 `PT5M` | 기본 `PT5M` | 기본 `PT5M` 또는 SSM | no | yes | no | `OAuthStateSigner` |
| `portal.dashboard-snapshots.capture-delay` | `PORTAL_DASHBOARD_SNAPSHOTS_CAPTURE_DELAY` | 공통 `120s` | 공통 `120s` | SSM/env override 가능 | no | yes | no | `DashboardSnapshotProperties` |
| `portal.dashboard-snapshots.fallback-grace` | `PORTAL_DASHBOARD_SNAPSHOTS_FALLBACK_GRACE` | 공통 `5m` | 공통 `5m` | SSM/env override 가능 | no | yes | no | `DashboardSnapshotProperties` |
| `portal.dashboard-snapshots.retention-days` | `PORTAL_DASHBOARD_SNAPSHOTS_RETENTION_DAYS` | 기본 `14` | 기본 `14` | SSM/env override 가능 | no | yes | no | snapshot/history/cleanup services |
| `portal.retention.cleanup.enabled` | `PORTAL_RETENTION_CLEANUP_ENABLED` | 공통 `false` | 공통 `false` | SSM/env | no | yes | no | `RetentionCleanupProperties` |
| `portal.retention.cleanup.dry-run` | `PORTAL_RETENTION_CLEANUP_DRY_RUN` | 공통 `false` | 공통 `false` | SSM/env | no | yes | no | `RetentionCleanupProperties` |
| `portal.ingest.buffer.mode` | `PORTAL_INGEST_BUFFER_MODE` | `.env`, 기본 `direct` | 기본 `direct` | SSM/env, 후보 `sqs` | no | yes | conditional | `IngestBufferProperties`, queue publisher/consumer |
| `portal.ingest.buffer.message-size-limit-bytes` | `PORTAL_INGEST_BUFFER_MESSAGE_SIZE_LIMIT_BYTES` | `.env` 또는 `1048576` | 기본 `1048576` | SSM/env | no | yes | no | `IngestBufferProperties` |
| `portal.ingest.buffer.publisher-timeout` | `PORTAL_INGEST_BUFFER_PUBLISHER_TIMEOUT` | `.env` 또는 `3s` | 기본 `3s` | SSM/env | no | yes | no | SQS publisher |
| `portal.ingest.buffer.sqs.queue-url` | `PORTAL_INGEST_BUFFER_SQS_QUEUE_URL` | `.env` 또는 blank | blank | SSM String | semi | direct mode만 가능 | `mode=sqs`면 request path fail-closed | SQS publisher/consumer |
| `portal.ingest.buffer.sqs.endpoint-override` | `PORTAL_INGEST_BUFFER_SQS_ENDPOINT_OVERRIDE` | LocalStack 전용 | blank | blank | no | yes | no | SQS clients |
| `portal.ingest.buffer.worker.enabled` | `PORTAL_INGEST_BUFFER_WORKER_ENABLED` | `.env`, 기본 `false` | 기본 `false` | SSM/env, 후보 `true` | no | yes | no | `MetricIngestQueueWorker` |
| `portal.ingest.buffer.worker.dlq-url` | `PORTAL_INGEST_BUFFER_WORKER_DLQ_URL` | `.env` 또는 blank | blank | SSM String | semi | worker off만 가능 | worker+SQS면 worker no-op/fail-safe | SQS DLQ publisher |
| `portal.ingest.buffer.worker.long-poll-seconds` | `PORTAL_INGEST_BUFFER_WORKER_LONG_POLL_SECONDS` | `.env` 또는 `20` | 기본 `20` | SSM/env | no | yes | no | SQS consumer |
| `portal.ingest.buffer.worker.max-messages-per-poll` | `PORTAL_INGEST_BUFFER_WORKER_MAX_MESSAGES_PER_POLL` | `.env` 또는 `10` | 기본 `10` | SSM/env | no | yes | no | SQS consumer |
| `portal.ingest.buffer.worker.visibility-timeout` | `PORTAL_INGEST_BUFFER_WORKER_VISIBILITY_TIMEOUT` | `.env` 또는 `60s` | 기본 `60s` | SSM/env | no | yes | no | SQS consumer |
| `portal.ingest.buffer.worker.max-receive-count` | `PORTAL_INGEST_BUFFER_WORKER_MAX_RECEIVE_COUNT` | `.env` 또는 `5` | 기본 `5` | SSM/env | no | yes | no | queue processor |
| `portal.ingest.buffer.worker.max-batch-size` | `PORTAL_INGEST_BUFFER_WORKER_MAX_BATCH_SIZE` | `.env` 또는 `10` | 기본 `10` | SSM/env | no | yes | no | queue worker |
| `portal.ingest.buffer.worker.max-batch-age` | `PORTAL_INGEST_BUFFER_WORKER_MAX_BATCH_AGE` | `.env` 또는 `2s` | 기본 `2s` | SSM/env | no | yes | no | queue worker |
| `portal.smoke.seed.enabled` | `PORTAL_SMOKE_SEED_ENABLED` | `.private/smoke-seed.properties` | 사용 안 함 | 사용 금지 | no | `false` | yes, 실행 금지 | `SmokeProjectSeedRunner` |
| `portal.smoke.seed.account-provider-subject` | `PORTAL_SMOKE_SEED_ACCOUNT_PROVIDER_SUBJECT` | `.private/smoke-seed.properties` | 사용 안 함 | 사용 금지 | semi | no | yes, 실행 금지 | `SmokeProjectSeedProperties` |
| `portal.smoke.seed.account-display-name` | `PORTAL_SMOKE_SEED_ACCOUNT_DISPLAY_NAME` | `.private/smoke-seed.properties` | 사용 안 함 | 사용 금지 | semi | optional | yes, 실행 금지 | `SmokeProjectSeedProperties` |
| `portal.smoke.seed.project-name` | `PORTAL_SMOKE_SEED_PROJECT_NAME` | `.private/smoke-seed.properties` | 사용 안 함 | 사용 금지 | no | no | yes, 실행 금지 | `SmokeProjectSeedService` |
| `portal.smoke.seed.project-id` | `PORTAL_SMOKE_SEED_PROJECT_ID` | `.private/smoke-seed.properties` | 사용 안 함 | 사용 금지 | no | optional | yes, 실행 금지 | `SmokeProjectSeedProperties` |
| `portal.smoke.seed.raw-project-key` | `PORTAL_SMOKE_SEED_RAW_PROJECT_KEY` | `.private/smoke-seed.properties` | 사용 안 함 | 사용 금지 | yes | no | yes, 실행 금지 | `SmokeProjectSeedService` |
| `observation.metric-flush.project-key` | `OBSERVATION_SMOKE_PROJECT_KEY`, `ECC_ENDPOINT_SMOKE_PROJECT_KEY` | shell 또는 `.private/smoke-project.env` | 사용 안 함 | host app secret store | yes | no | host app 기준 yes | smoke service starter config |
| `observation.heartbeat.project-key` | `OBSERVATION_SMOKE_PROJECT_KEY`, `ECC_ENDPOINT_SMOKE_PROJECT_KEY` | shell 또는 `.private/smoke-project.env` | 사용 안 함 | host app secret store | yes | no | host app 기준 yes | smoke service starter config |
| service access token memo | `OBSERVATION_SMOKE_ACCESS_TOKEN` | `.private/smoke-auth.env` | 사용 안 함 | 사용 금지 | yes | no | yes, 사용 금지 | smoke verification scripts |
| AWS region | `AWS_REGION`, `AWS_DEFAULT_REGION` | `.env` | CI env if needed | SSM/systemd env | no | `ap-northeast-2` 가능 | SQS 사용 시 yes | AWS SDK SQS |
| AWS static credential | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | local `.env` only if needed | 권장하지 않음 | 사용 금지, EC2 role 사용 | yes | no | yes, 사용 금지 | AWS SDK credential provider chain |

## 절대 채팅에 붙이지 않는 값

- `PORTAL_AUTH_GITHUB_CLIENT_SECRET`
- `SPRING_DATASOURCE_PASSWORD`
- `PORTAL_AUTH_SERVICE_TOKEN_SIGNING_KEY`
- `PORTAL_AUTH_OAUTH_STATE_SIGNING_KEY`
- `AWS_SECRET_ACCESS_KEY`
- `portal.smoke.seed.raw-project-key`
- `OBSERVATION_SMOKE_PROJECT_KEY`
- `ECC_ENDPOINT_SMOKE_PROJECT_KEY`
- `OBSERVATION_SMOKE_ACCESS_TOKEN`
- GitHub provider access token, refresh token, raw OAuth payload

## IAM handoff

E1은 IAM 리소스를 만들지 않는다. 다만 E3/E4에서 아래 항목을 확정해야 한다.

| 항목 | 권장 소유 위치 | 용도 |
|---|---|---|
| EC2 instance profile / role ARN | AWS IAM | prod portal process가 SSM Parameter와 SQS에 접근한다. |
| GitHub Actions OIDC deploy role ARN | AWS IAM + GitHub Actions | 장기 AWS access key 없이 배포 role을 assume한다. |
| SSM read policy | AWS IAM | `/observation/prod/` 같은 prefix만 읽도록 제한한다. |
| SQS access policy | AWS IAM | source queue send/receive/delete/change visibility, DLQ send 권한만 부여한다. |
