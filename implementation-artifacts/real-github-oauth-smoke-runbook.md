# Real GitHub OAuth Local Smoke Runbook

## 목적과 경계

이 runbook은 실제 GitHub OAuth 로그인을 완료한 local portal account를 local smoke project와 active membership으로 연결해 `GET /api/projects`를 검증하는 절차다.

- 제품용 project creation API, Create Project UI, project key 발급/회전 workflow는 열지 않는다.
- GitHub provider token과 portal service token은 다른 인증 경계다.
- Starter ingest의 `X-OBS-Project-Key`와 portal resource API의 `Authorization: Bearer <access_token>`도 다른 경계다.
- 실제 credential, access token, refresh token, provider token, raw project key 값은 이 문서나 Git 추적 파일에 적지 않는다.

## 1. GitHub OAuth App 준비

GitHub OAuth App을 만들고 local portal URL을 맞춘다.

- Homepage URL: `http://localhost:8080/dashboard/`
- Product/dashboard Authorization callback URL: `http://localhost:8080/api/auth/github/callback`
- Operator smoke token capture URL, only when a JSON token pair is intentionally needed: `http://localhost:8080/api/auth/github/callback/token`

기본 dashboard callback은 token pair JSON을 화면에 렌더링하지 않는 HTML relay flow다. Smoke 자동화가 service access token을 local-only 파일에 넣어야 할 때만 GitHub OAuth App redirect URI를 explicit JSON endpoint로 별도 설정한다.

## 2. local secret 파일 작성

`.private/`는 `.gitignore`에 포함되어 있으므로 local-only secret은 이 디렉터리 아래에 둔다.

`.private/github-oauth.properties`:

```properties
portal.auth.github.client-id=<github-oauth-client-id>
portal.auth.github.client-secret=<github-oauth-client-secret>
portal.auth.github.redirect-uri=http://localhost:8080/api/auth/github/callback
portal.auth.github.homepage-url=http://localhost:8080/dashboard/
portal.auth.service-token.signing-key=<local-service-token-signing-key>
portal.auth.oauth-state.signing-key=<local-oauth-state-signing-key>
```

Smoke token capture가 필요한 짧은 작업에서는 `portal.auth.github.redirect-uri`를 `http://localhost:8080/api/auth/github/callback/token`으로 바꾸고, 작업 후 다시 dashboard callback URL로 되돌린다.

`.private/smoke-seed.properties`:

```properties
portal.smoke.seed.enabled=true
portal.smoke.seed.account-provider-subject=<github-user-id>
portal.smoke.seed.project-id=<optional-stable-project-uuid>
portal.smoke.seed.project-name=local-smoke
portal.smoke.seed.raw-project-key=<key_prefix>.<secret>
```

`portal.smoke.seed.account-provider-subject`가 가장 안전한 selector다. display name selector가 필요하면 `portal.smoke.seed.account-display-name=<github-login-if-unique>`를 쓰되, 정확히 하나의 GitHub identity와 매칭될 때만 seed가 진행된다.

## 3. local PostgreSQL과 portal 실행

local PostgreSQL 접속 정보를 환경 변수나 local-only 설정으로 준비한 뒤 portal을 local smoke profile로 실행한다.

```bash
./gradlew :observability-portal:bootRun --args='--spring.profiles.active=local-smoke'
```

일반 실행에서는 `portal.smoke.seed.enabled`를 켜지 않는다. seed는 `local-smoke` profile에서만 row를 쓰며 production-like profile에서는 실패한다.

## 4. GitHub OAuth authorize 시작

기존 GitHub OAuth start endpoint를 호출해 `authorizationUrl`을 얻는다.

```bash
AUTH_URL="$(curl -sS http://localhost:8080/api/auth/github/authorize | jq -r '.authorizationUrl')"
open "${AUTH_URL}"
```

브라우저에서 GitHub OAuth를 완료하면 기본 dashboard callback에서는 token JSON이 화면에 보이지 않는다. Smoke token capture URL을 명시적으로 사용한 경우에만 JSON endpoint가 응답한다.

## 5. service access token만 memo

explicit JSON endpoint response에는 portal service token pair가 포함된다. smoke 자동화 파일에는 service access token만 저장한다.

```bash
scripts/smoke/write-smoke-auth-env.sh
```

helper prompt에는 explicit JSON endpoint response의 `accessToken` 값만 붙여 넣는다. `.private/smoke-auth.env`에는 아래 한 줄만 남아야 한다.

```bash
OBSERVATION_SMOKE_ACCESS_TOKEN=<service-access-token>
```

Access token이 만료되면 refresh token을 memo하지 말고 GitHub OAuth 로그인을 다시 완료하거나 operator가 수동으로 access token을 갱신한다.

## 6. provider subject 확인

로그인 후 local DB에서 GitHub provider subject를 확인한다.

```sql
select provider_subject, display_name, account_id
from external_identities
where provider = 'github'
order by created_at desc;
```

확인한 `provider_subject`를 `.private/smoke-seed.properties`의 `portal.smoke.seed.account-provider-subject`에 넣는다.

## 7. local-only smoke seed 실행

portal을 seed enabled 상태로 다시 시작한다.

```bash
./gradlew :observability-portal:bootRun --args='--spring.profiles.active=local-smoke'
```

Seed runner는 다음 조건을 모두 만족할 때만 실행된다.

- `portal.smoke.seed.enabled=true`
- active profile에 `local-smoke` 포함
- GitHub identity가 active account에 연결됨
- raw project key가 `<key_prefix>.<secret>` shape이고 BCrypt 72 byte 입력 한계 안에 있음
- existing project가 있으면 name, key prefix, BCrypt hash가 configured value와 일치함
- existing membership이 없거나 이미 active임

성공 출력에는 project id/name, membership 상태, 다음 verification command만 포함된다. raw project key나 token 값은 출력하지 않는다.

## 8. `GET /api/projects` 검증

helper가 `.private/smoke-auth.env`를 shell로 source하지 않고 데이터로 파싱한 뒤 Bearer header로 resource API를 호출한다.

```bash
OBSERVATION_SMOKE_PROJECT_NAME=local-smoke scripts/smoke/verify-smoke-projects.sh
```

성공 기준:

- HTTP status가 `200`이다.
- `projects[]`에 `local-smoke` project가 있다.
- 해당 item의 `links.applications`가 `/api/projects/{projectId}/applications` shape다.

`projects=[]`이면 seed 또는 active membership 누락으로 보고 public Create Project flow로 연결하지 않는다. No-token, invalid-token, expired-token은 기존 Bearer boundary처럼 `401 Unauthorized`와 `WWW-Authenticate: Bearer`로 닫혀야 한다. Membership이 없는 account의 project-scoped API는 body 없는 `404` fail-closed가 유지되어야 한다.

## 9. Story 7.2/7.3 handoff

Starter ingest smoke가 raw project key를 필요로 할 때는 Git 추적 대상이 아닌 `.private/smoke-project.env`나 shell env만 사용한다.

```bash
OBSERVATION_SMOKE_PROJECT_KEY=<key_prefix>.<secret>
```

이 값은 starter `X-OBS-Project-Key` 인증에만 사용한다. Portal resource API 호출에는 `.private/smoke-auth.env`의 service Bearer access token만 사용한다. 반대로 `.private/smoke-auth.env`의 `OBSERVATION_SMOKE_ACCESS_TOKEN`은 bucket ingest client 설정에 넣지 않는다.

Story 7.2 이후 starter dependency가 붙은 host app은 metric bucket flush 전용 설정을 별도로 둔다. 예시는 placeholder만 사용한다.

```properties
observation.metric-flush.project-id=local-smoke-project-id
observation.metric-flush.application-name=orders-api
observation.metric-flush.environment=local-smoke
observation.metric-flush.instance=orders-api-local-1
observation.metric-flush.portal-base-url=http://localhost:8080
observation.metric-flush.project-key=${OBSERVATION_SMOKE_PROJECT_KEY}
observation.metric-flush.timeout-millis=1000
```

Heartbeat를 함께 켠 host app은 heartbeat 전용 설정을 따로 둔다. Bucket ingest client는 아래 값을 metric flush fallback으로 읽지 않는다.

```properties
observation.heartbeat.portal-base-url=http://localhost:8080
observation.heartbeat.project-key=${OBSERVATION_SMOKE_PROJECT_KEY}
```

## 10. Story 7.3 smoke service 실행

Story 7.3 smoke service는 repo-local `observability-smoke-service` module이다. 이 module은 production portal이나 starter artifact가 아니라 local operator 검증용 host app이다.

`.private/smoke-project.env`에는 starter `X-OBS-Project-Key` 인증에만 사용할 raw project key를 둔다.

```bash
OBSERVATION_SMOKE_PROJECT_KEY=<key_prefix>.<secret>
```

Smoke helper scripts는 이 파일을 shell `source`하지 않고 단일 key/value 데이터로만 확인한다. 이 값은 portal resource API의 `Authorization` header로 사용하지 않는다. Portal read API 검증은 계속 `.private/smoke-auth.env`의 JWT-like 3 segment `OBSERVATION_SMOKE_ACCESS_TOKEN`만 사용한다.

Portal을 local smoke profile로 실행한다.

```bash
./gradlew :observability-portal:bootRun --args='--spring.profiles.active=local-smoke'
```

다른 터미널에서 smoke service를 local smoke profile로 실행한다.

```bash
OBSERVATION_PORTAL_BASE_URL=http://localhost:8080 \
OBSERVATION_SMOKE_PROJECT_KEY=<key_prefix>.<secret> \
./gradlew :observability-smoke-service:bootRun --args='--spring.profiles.active=local-smoke'
```

Smoke traffic은 primary green path인 `/smoke/ok`로 만든다. `/smoke/slow`와 `/smoke/error-candidate`는 troubleshooting 후보이며 completion은 이 endpoint들에 의존하지 않는다.

```bash
OBSERVATION_SMOKE_SERVICE_BASE_URL=http://localhost:8081 \
OBSERVATION_SMOKE_TRAFFIC_COUNT=12 \
OBSERVATION_SMOKE_CURL_CONNECT_TIMEOUT=2 \
OBSERVATION_SMOKE_CURL_MAX_TIME=10 \
scripts/smoke/run-smoke-traffic.sh
```

그 다음 30초 bucket closure와 scheduler initial delay를 기다린 뒤 Project -> Application -> Dashboard -> Instance Evidence read API 흐름을 검증한다. 기본 wait는 45초다.

```bash
OBSERVATION_PORTAL_BASE_URL=http://localhost:8080 \
OBSERVATION_SMOKE_PROJECT_NAME=local-smoke \
OBSERVATION_SMOKE_APPLICATION_NAME=observation-smoke-service \
OBSERVATION_SMOKE_APPLICATION_ENVIRONMENT=local-smoke \
OBSERVATION_SMOKE_WAIT_SECONDS=45 \
OBSERVATION_SMOKE_CURL_CONNECT_TIMEOUT=2 \
OBSERVATION_SMOKE_CURL_MAX_TIME=10 \
scripts/smoke/verify-smoke-portal-flow.sh
```

검증 script는 `.private/smoke-auth.env`와 `.private/smoke-project.env`를 shell source하지 않고 단일 key/value 파일로 파싱한다. `.private/smoke-auth.env`가 없거나 access token이 service JWT-like 3 segment shape가 아니면 Bearer 요청을 만들지 않는다. `OBSERVATION_SMOKE_PROJECT_KEY` 또는 `.private/smoke-project.env`가 없으면 starter project key 누락으로 fail-fast하며 raw key 값을 출력하지 않는다. 실패 시 response body 전체를 출력하지 않고 project, application, dashboard, evidence 단계별 shape mismatch만 일반화해서 보고한다.

관찰 단계는 아래처럼 해석한다.

- heartbeat-only 단계에서는 application catalog row가 아직 없으면 read API에 보이지 않을 수 있다. heartbeat를 accepted bucket 생성이나 application health source로 해석하지 않는다.
- 첫 accepted bucket 이후에는 sample이 부족해 `waiting_first_data`, `unknown`, `idle`, `active` 중 관찰 가능한 초기 상태가 내려올 수 있다.
- Dashboard는 `application.lastAcceptedBucketAt`, `starterConnection.statusSource=starter_heartbeat`, `starterConnection.lastHeartbeatStatus=received`, `starterConnection.lastHeartbeatAt`, `starterConnection.stateImpact=none`, 계약된 `zeroInsight.reasonCode` 또는 bounded `triageCards` shape를 확인한다.
- Instance Evidence는 `metricData.statusSource=accepted_bucket`, `starterConnection.statusSource=starter_heartbeat`, `starterConnection.lastHeartbeatStatus=received`, `starterConnection.lastHeartbeatAt`, `starterConnection.stateImpact=none`을 확인한다.
- Instance Evidence의 starter percentile status는 `missing`, `insufficient`, `available` 중 하나면 충분하다. 이 flow는 새 p95/p99 계산을 만들지 않는다.
- 성공 문구는 accepted bucket metric axis와 starter heartbeat axis가 같은 local 계약에서 관찰됐다는 뜻이다. 앱 정상 확정, host application down 확정, 복구 완료 확정으로 번역하지 않는다.
