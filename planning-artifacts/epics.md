---
artifactType: epics
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
sourcePolicy: 기존 Lightweight Hexagonal 산출물의 제품/계약 의미를 Traditional MVC로 재해석
status: epic-11-12-planning-updated
date: 2026-06-04
---

# Epics - Traditional MVC 기준 재산출

## Epic 1. Architecture Foundation

목표: starter와 portal이 Traditional MVC/Layered 구조를 일관되게 따르는 기본 뼈대를 만든다.

### Stories

1. Starter package skeleton 생성
   - `model`, `service`, `spring`, `client.http`, `queue`, `config`
   - service는 host request path에서 network call을 하지 않는다.
2. Portal MVC package skeleton 생성
   - `controller`, `service`, `repository`, `model`, `dto`, `security`, `scheduler`, `config`
   - controller는 repository를 직접 참조하지 않는다.
3. MVC layer guard test 추가
   - controller에서 repository 직접 참조를 금지한다.
   - repository에서 controller/dto 참조를 금지한다.
   - service/model 외부에서 state/rule/p95/endpoint priority 계산을 금지한다.
4. Portal physical schema foundation
   - PostgreSQL migration 도구와 local/test DB runtime 기준을 세팅한다.
   - `projects`, `applications`, `application_instances` physical schema를 만든다.
   - table/column 한국어 comment를 migration에 포함한다.
   - UUID는 application-generated UUID로 두고, project key는 `key_prefix` + BCrypt hash 검증 경계로 둔다.
   - `accepted_metric_buckets`와 `dashboard_snapshots`는 Epic 3/5에서 구현하되, migration naming과 comment convention은 Epic 1에서 고정한다.

## Epic 2. Starter Direct Ingest Producer

목표: 사용자가 starter를 추가하면 host app request path를 막지 않고 30초 bucket을 전송한다.

Operational Event History, p99/tail latency judgment, dashboard snapshot history, operational events API, recent history UI, alert/snapshot deep link는 Epic 2에 포함하지 않는다.

### Stories

1. Micrometer observation binding
   - HTTP/JVM/datasource signal을 수집한다.
2. Route normalization and low-cardinality guard
   - raw path parameter와 high-cardinality tag를 payload에 넣지 않는다.
3. Bucket rollup service
   - app summary와 endpoint histogram bucket을 30초 UTC boundary로 집계한다.
4. Async flush worker
   - bounded queue, retry/backoff, drop policy를 구현한다.
   - request thread에서 network call을 하지 않는다.
   - portal timeout/down 상황에서도 host app request path를 막지 않는다.
5. Ingest envelope builder service
   - `ingest-envelope` contract에 맞는 payload를 만든다.
6. Negative path guard
   - scrape config, pull metric query, arbitrary query UI 경로가 starter MVP path에 없음을 테스트한다.

## Epic 3. Portal Ingest Acceptance

목표: portal이 ingest envelope를 검증하고 idempotent하게 저장한다.

Epic 3은 `accepted_metric_buckets` 저장과 idempotent acceptance까지만 닫는다. Operational event 저장, 계산, API는 구현하지 않는다.

### Stories

1. Project key verification service
   - `X-OBS-Project-Key`를 검증한다.
2. Ingest acceptance service
   - schema version, bucket boundary, metric taxonomy, idempotency key를 검증한다.
3. PostgreSQL bucket repository
   - accepted bucket과 payload hash를 저장한다.
4. Duplicate handling
   - 동일 payload는 성공, 동일 key/다른 hash는 conflict로 처리한다.

## Epic 4. State Semantics and Time Windows

목표: 첫 화면 상태 언어를 portal service/model의 단일 판단으로 고정한다.

### Stories

0. Starter heartbeat and instance-level ingest contract gate
   - Story 4-0은 구현 story가 아니라 contract gate다.
   - `POST /api/ingest/v1/heartbeat`는 bucket ingest와 분리된 periodic control-plane/liveness signal로 문서화한다.
   - heartbeat 성공은 accepted bucket, host business health, dashboard snapshot, operational event, p95/p99, rule/read-model calculation을 생성하거나 암시하지 않는다.
   - heartbeat는 starter/application process liveness, portal reachability, project key validity, metadata validity의 control-plane source다.
   - heartbeat 미수신은 host application down 판정이 아니며 starter connection status와 accepted bucket freshness/application state를 분리한다.
   - `localPercentiles`는 instance-local 30초 bucket의 starter canonical p95/p99로 허용한다.
   - 여러 starter 값이 같은 app/project/window에 존재하면 임의 평균/병합으로 새 p95/p99를 만들지 않고 instance/source 단위로 노출하거나 상위 scope에는 bucket distribution을 표시한다.
1. Time bucket contract implementation
   - 30초 bucket, 15분 current, 15분 baseline, UTC 기준을 구현한다.
2. Lifecycle state service
   - accepted bucket 기반 metric state로 `waiting_first_data`, `unknown`, `idle`, `active`, `stale`, `down`, `degraded`를 판정한다.
   - heartbeat telemetry가 있으면 starter connection/liveness 축을 별도 입력으로 받아 `starter connected but no accepted bucket`, `waiting for traffic`, `telemetry unreachable`, `unknown` 계열 copy/recommended action을 만든다.
   - heartbeat가 최근 수신됐지만 accepted bucket이 없거나 오래된 경우 host application down으로 단정하지 않는다.
   - heartbeat도 끊기고 accepted bucket도 오래된 경우에도 host application down을 확정하지 않고 telemetry disconnected/unknown 계열로 표현한다.
   - degraded enter는 rule guard 통과, confidence `>= 0.75`, 최근 5개 30초 bucket 중 3개 이상 bad일 때만 통과한다.
   - degraded resolve는 concern absence, confidence `< 0.60`, rule별 recovery/threshold 하회 중 하나가 5 consecutive buckets 동안 유지될 때 통과한다.
   - 30초 단발 blip은 degraded state로 만들지 않는다.
3. Recovery guidance
   - stale/down 이후 sample 부족 상태를 안전하게 표현한다.
4. State semantics tests
   - freshness, minimum sample, baseline insufficient edge case를 테스트한다.

## Epic 5. Dashboard Read Model and API

목표: Project/Application navigation, Application dashboard, Instance evidence, Snapshot/history 화면이 소비할 server-computed read model/API를 닫는다.

Epic 5는 화면 구현이 아니라 API와 service-layer read model 책임을 닫는 epic이다. UI는 여기서 만든 응답을 표시할 뿐 lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority, snapshot/history event를 재계산하지 않는다.

### Stories

1. Project/Application navigation read model
   - Project entry와 Application list가 사용할 read-only navigation read model/API를 만든다.
   - Project 화면은 scope 선택 화면이며 application count, setup/connection issue 후보, recent concern 0~1개만 가볍게 보여준다.
   - Application list는 상태 스캔과 dashboard 진입을 돕지만 상세 triage, endpoint priority, p95/p99 판단을 대체하지 않는다.
   - heartbeat summary와 accepted bucket freshness는 별도 source/field로 내려오며 하나의 health 판단으로 합치지 않는다.
   - Project 생성은 public onboarding API로 열지, local/internal seed로 유지할지 open decision으로 남긴다.
2. Application dashboard read model skeleton
   - `read-model-contract`의 current dashboard response shape를 구현 skeleton으로 닫는다.
   - Application dashboard가 primary first-screen이며 project/application/environment, current/baseline window, state strip, starter connection strip, headline metrics, triage/zero insight, endpoint priority, instance summary, snapshot link를 하나의 server read model로 제공한다.
   - `triageCards=[]`이면 `zeroInsight`는 반드시 non-null이다.
   - Dashboard current response는 query 시점 기준 current 15분과 직전 baseline 15분을 사용한다.
   - UI는 state, zero-insight, recovery, rule, p95/p99, endpoint priority를 재계산하지 않는다.
3. Source-scoped percentile and histogram distribution read model
   - instance-local 30초 bucket의 `summary.localPercentiles.p95Ms`/`p99Ms`를 starter canonical percentile point로 노출한다.
   - 여러 instance/window p95/p99를 평균, 최댓값, 병합해 application p95/p99처럼 만들지 않는다.
   - histogram bucket은 distribution display와 diagnostic evidence로만 사용하고 percentile 계산 입력으로 쓰지 않는다.
   - Endpoint별 p95/p99 계산, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
   - 상위 scope에서 percentile scalar가 모호하면 source-scoped point series 또는 bucket distribution으로 표현한다.
4. Triage summary and zero-insight/recovery mapping
   - MVP rule set, guard, ranking, 최대 0~3개 triage card를 service layer에서 계산한다.
   - Dashboard triage card 기본 노출 기준은 confidence `>= 0.65`로 둔다.
   - `waiting_first_data`, `insufficient_sample`, `no_action_needed`, `metric_data_idle`, `telemetry_unreachable`, `observing_recovery` reason과 recommended action을 read model로 제공한다.
   - stale/down 이후 새 bucket이 왔지만 sample이 부족한 경우 `state=unknown`과 `recovery.isRecovering=true`로 표현한다.
   - Recovery copy와 starter connection copy를 결합해 host application down을 확정하지 않는다.
5. Endpoint priority read model
   - slow/error/comparative evidence, confidence, freshness, recommended action 기반 endpoint priority 목록을 만든다.
   - freshness가 stale/down이면 일반 비교형 rule과 endpoint ranking을 억제할 수 있다.
   - raw path, query string, query key/value, trace id, per-request sample을 endpoint evidence에 포함하지 않는다.
   - endpoint evidence는 bucket distribution을 표시할 수 있지만 endpoint별 p95/p99를 계산하지 않는다.
6. Instance evidence read model
   - Instance detail 화면이 사용할 identity, last accepted bucket, starter heartbeat, metric freshness와 starter connection 분리 표시, source-scoped p95/p99 series, histogram distribution, endpoint/resource evidence subset, application triage contribution 여부를 제공한다.
   - Instance detail은 application dashboard 판단을 대체하지 않는 evidence drill-down이다.
   - API와 UI는 instance 화면에서 application state, rule, endpoint priority, p95/p99를 새로 판정하지 않는다.
   - Instance evidence 깊이는 Epic 5 open decision으로 남기되, MVP read model은 bounded subset만 제공한다.
7. Instance snapshot trend projection
   - 특정 application instance의 최근 관찰 흐름을 `dashboard_snapshots.read_model_json`의 bounded instance summary에서 projection한다.
   - 기본 조회는 `since=7d`, `limit=168`이고, retention 안에서 최대 `since=14d`, `limit=336`으로 clamp한다.
   - 각 point는 snapshotId, capturedAt, captureReason, stored application state, accepted bucket freshness, starter heartbeat observation, source-scoped starter percentile latest point, bounded resource hint, application triage contribution 여부를 담을 수 있다.
   - Instance trend는 instance health score, current state 재판정, raw 30초 bucket explorer, endpoint timeseries, arbitrary metric query를 제공하지 않는다.
   - 필요할 때만 후속 story에서 snapshot-derived helper table을 검토하며, 그 table도 raw metric store가 아니다.
8. Dashboard snapshot persistence and marker contract
   - `dashboard_snapshots`에 application별 1시간 scheduled read model snapshot을 저장한다.
   - 의미 있는 state-change, confidence `>= 0.82` high-confidence concern, 짧지만 강한 spike 실험값, dashboard query fallback 조건에서만 추가 snapshot row를 남긴다.
   - Snapshot `read_model_json`의 endpoint evidence는 최대 10개, bounded instance summary는 snapshot당 최대 50개로 둔다.
   - Snapshot detail은 저장 당시 read model과 bounded evidence를 보여주며 current state를 재판정하지 않는다.
   - Ingest commit마다 30초 dashboard snapshot을 refresh하지 않고, raw snapshot explorer를 만들지 않는다.
9. Operational event history API
   - `operational-event-history` contract에 따라 `dashboard_snapshots` 기반 bounded event 목록과 snapshot detail deep link를 제공한다.
   - 별도 `operational_events` table, event repository, endpoint timeseries table은 MVP에 만들지 않는다.
   - 같은 `application + endpointKey + ruleId` 반복 concern은 60분 suppression window 안에서 중복 event로 만들지 않는다.
   - `high_confidence_concern` event 승격 기준은 confidence `>= 0.82`로 둔다.
   - Snapshot/history는 raw explorer가 아니라 stored dashboard read model history다.

## Epic 6. Dashboard User Flow and Demo Hardening

목표: 사용자가 project에서 application으로, application dashboard에서 instance evidence와 bounded history로 좁혀 들어가는 실제 화면 흐름과 demo path를 닫는다.

Epic 6은 Epic 5 read model/API를 소비하는 사용자-facing UX epic이다. Application dashboard는 primary first-screen이며, Instance detail은 evidence drill-down이고, Snapshot/history는 stored read model marker/history로만 표현한다.

### Stories

1. Account/project entry and setup guide
   - Account signup/login은 GitHub OAuth only와 Bearer access token/JWT, refresh token rotation 기준을 따른다.
   - Project entry는 운영 판단 화면이 아니라 scope 선택과 minimal setup guide 진입 화면으로 둔다.
   - dependency 추가, portal base URL, project key, environment 설정만 안내한다.
   - Project creation은 public onboarding API인지 local/internal admin seed인지 open decision으로 남긴다.
   - email/password, magic link, GitHub 외 provider, anonymous flow는 MVP에 포함하지 않는다.
2. Project selection UI
   - Project 목록에서 project name, application count, setup/connection issue 후보, recent concern 0~1개를 보여준다.
   - Project 화면은 application dashboard 판단을 대신하지 않고 Application List로 scope를 좁힌다.
   - starter heartbeat와 accepted bucket 요약은 별도 축으로 표시한다.
3. Application list UI
   - Project 안 application 목록에서 application name, environment, lifecycle state badge, last accepted bucket, starter connection summary, top concern 0~1개를 빠르게 스캔하게 한다.
   - 상세 triage와 endpoint priority는 list가 아니라 Application Dashboard에서 확인한다.
   - 행 전체 클릭 또는 명확한 action으로 Application Dashboard에 진입한다.
4. Application dashboard UI integration
   - Epic 5의 dashboard read model을 그대로 렌더링해 Application Dashboard를 primary first-screen으로 구현한다.
   - Metric state strip과 starter connection strip을 분리하고, headline metric은 app scalar와 source-scoped percentile point를 구분한다.
   - triage가 없을 때도 zeroInsight reason과 next action을 빈 화면 대신 표시한다.
   - UI는 lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority를 재계산하지 않는다.
5. Instance evidence UI
   - Application Dashboard의 instance summary에서 Instance Detail evidence drill-down으로 이동한다.
   - Instance identity, accepted bucket freshness, starter heartbeat, source-scoped 30초 bucket p95/p99 series, histogram distribution, endpoint/resource evidence subset, application triage contribution 여부를 보여준다.
   - Application Dashboard로 돌아가는 흐름을 항상 노출한다.
   - Instance detail은 application 판단을 대체하지 않고 evidence만 좁혀 보여준다.
6. Instance snapshot trend UI
   - Instance Detail에서 selected instance의 최근 24h/7d/14d bounded trend로 이동할 수 있게 한다.
   - 기본 화면은 7일 horizon의 stored dashboard snapshot/read model point와 marker overlay를 보여준다.
   - 문제가 없는 기간도 "no concern observed"로 표시해 instance 안정성 확인을 지원한다.
   - starter connection lane과 accepted bucket metric lane을 분리해 보여준다.
   - UI는 state, health score, rule, p95/p99, endpoint priority, operational event를 재계산하지 않는다.
7. Snapshot/history marker UI and deep link
   - Bounded operational event 목록과 snapshot detail deep link를 marker timeline으로 표시한다.
   - 허용 marker는 hourly snapshot, state change, high-confidence concern, recovery/stale/down 계열로 제한한다.
   - Snapshot detail은 저장 당시 dashboard read model을 보여주며 current state를 재판정하지 않는다.
   - Raw snapshot explorer, arbitrary time-series query UI, alert delivery log 병합은 non-goal이다.
8. Demo green path
   - starter 추가 후 heartbeat, first accepted bucket, insufficient sample, active/no-triage baseline, Application Dashboard 진입을 30~60초 내에 보여준다.
   - Project -> Application List -> Application Dashboard -> Instance Evidence 흐름이 끊기지 않도록 demo fixture와 UI 상태를 맞춘다.
9. Failure/recovery path demo hardening
   - portal down, duplicate ingest, stale/down candidate, telemetry unreachable, recovery observed 시나리오를 prototype의 상태 시나리오와 맞춰 시연한다.
   - failure/recovery demo copy는 host application down을 확정하지 않고 accepted bucket axis와 heartbeat axis를 분리한다.
   - Snapshot/history marker와 instance snapshot trend가 demo-only인지 MVP 포함인지 open decision으로 남기되, 어느 쪽이든 raw explorer로 보이지 않게 한다.
10. Account-project membership and scoped project entry
   - Account와 Project를 N:M membership으로 연결해 authenticated account 기준 Project visibility를 닫는다.
   - MVP membership role은 `member` 단일 role로 시작하고, status는 `active`, `disabled`만 둔다.
   - `GET /api/projects`는 현재 Bearer access token의 account가 active membership을 가진 project만 반환한다.
   - `/api/projects/{projectId}/applications` 및 후속 `/api/projects/{projectId}/applications/**` resource API는 active account-project membership이 없으면 정보 노출을 줄이기 위해 `404`로 fail-closed한다.
   - Epic 5의 project/application/instance membership은 catalog path 정합성이고, Story 6.10의 membership은 account-project authorization임을 분리한다.
   - Project creation, project key issuance/rotation, invite/team/org management, billing/tenant model은 포함하지 않는다.

## Epic 7. Real GitHub and Starter Smoke Verification

목표: Epic 6 완료 후 실제 GitHub OAuth 계정, local smoke project seed, starter가 포함된 Spring Boot smoke service로 portal ingest와 dashboard read flow가 같은 계약 위에서 통신되는지 검증한다.

Epic 7은 production onboarding/product feature를 여는 epic이 아니라 local/operator smoke validation epic이다. Project creation UI, public project creation API, browser token persistence, invite/admin/team/billing surface는 열지 않는다.

### Stories

1. Real GitHub OAuth smoke seed and operator runbook
   - `.private/github-oauth.properties` 기반 local GitHub OAuth App 설정과 portal 실행 절차를 문서화한다.
   - 사용자는 브라우저에서 실제 GitHub OAuth 로그인을 수동으로 완료하고, portal이 발급한 service `accessToken`/`refreshToken` JSON만 smoke 검증에 사용한다.
   - 테스트 자동화를 위해 local smoke profile 또는 operator script는 로그인 완료 후 service `accessToken`만 `.private/smoke-auth.env`의 `OBSERVATION_SMOKE_ACCESS_TOKEN`으로 메모할 수 있다.
   - Token memo file은 Git 추적 대상이 아닌 `.private/` 아래에만 두고, 가능하면 owner-only 권한으로 만들며, token 만료 시 다시 로그인해 갱신하는 ephemeral helper로 취급한다.
   - GitHub provider token, provider raw payload, OAuth credential, service access/refresh token은 repository 문서, log, response snapshot, test fixture에 남기지 않는다.
   - `refreshToken`, GitHub provider token, provider raw payload, OAuth credential은 token memo file에 저장하지 않는다.
   - local-only smoke seed는 로그인으로 생성된 `external_identities` row의 `display_name` 또는 `provider_subject`를 기준으로 내부 account를 찾고, smoke project, BCrypt project key hash, active `account_project_memberships` row를 명시적으로 연결한다.
   - Seed는 raw project key를 migration, DB row, log, exception, response body, repository lookup surface에 남기지 않고, `.private/` 또는 operator shell env에만 둔다.
   - `GET /api/projects`는 Bearer account의 active membership project를 반환하고, membership mismatch project-scoped API는 기존처럼 `404` fail-closed를 유지한다.
   - 이 story는 public project creation, project key issuance/rotation UI, login 직후 자동 project 생성, GitHub 외 provider, local password, token browser persistence를 만들지 않는다.
2. Starter bucket ingest HTTP client
   - `PortalMetricBucketClient`의 기본 HTTP 구현 또는 auto-configuration을 추가해 smoke host app이 custom bean 없이 `POST /api/ingest/v1/buckets`로 accepted bucket을 전송할 수 있게 한다.
   - Client는 `X-OBS-Project-Key`, `Idempotency-Key`, JSON body, bounded timeout을 사용하고 raw project key를 log/error/exception message에 노출하지 않는다.
   - Bucket flush는 기존 `MetricBucketFlushWorker` background boundary 안에서만 수행되며 host request path에서 network call을 하지 않는다.
   - Heartbeat `POST /api/ingest/v1/heartbeat`와 bucket ingest `POST /api/ingest/v1/buckets`는 의미와 retry/idempotency를 계속 분리한다.
   - Client configuration은 local smoke service와 일반 host app 모두에서 명확하게 설정 가능해야 하며, generic default identity로 portal flush가 시작되지 않도록 기존 guard를 유지한다.
   - Tests는 success, non-2xx, timeout/transport failure, idempotency header, secret redaction, no request-path blocking boundary를 검증한다.
3. Smoke Spring Boot service and portal communication verification
   - repo 안에 smoke 전용 Spring Boot service module 또는 sample runtime을 추가하되 production portal/starter runtime과 배포 artifact를 혼동하지 않게 한다.
   - Smoke service는 `observability-spring-boot-starter`를 실제 dependency로 사용하고, smoke application/environment/instance identity와 portal URL/project key를 local config 또는 env로 받는다.
   - `/smoke/ok`, `/smoke/slow`, `/smoke/error-candidate` 같은 최소 traffic endpoint로 accepted bucket을 만들 수 있게 하되, primary completion path는 green/no-triage baseline이다.
   - Operator runbook 또는 script는 portal, smoke service, traffic generation, 30초 bucket closure wait, heartbeat 확인, bucket accepted 확인, `Project -> Application List -> Dashboard -> Instance Evidence` read API 확인 순서로 실행된다.
   - Dashboard/UI 확인은 service Bearer access token의 in-memory use만 허용하고 `localStorage`, `sessionStorage`, cookie token 저장, URL token parsing을 만들지 않는다.
   - Verification은 heartbeat-only, first accepted bucket/insufficient sample, active/no-triage baseline 중 관찰 가능한 단계와 실패 시 troubleshooting evidence를 남긴다.
   - Smoke artifacts는 host application down, 앱 정상 확정, 복구 완료를 단정하지 않고 accepted bucket metric axis와 starter heartbeat axis를 분리해 설명한다.

## Epic Numbering Alignment Note

Epic 8~10은 이 파일보다 별도 planning artifact와 story/status tracking이 더 최신이다. 이번 정렬에서는 Epic 8~10을 다시 분해하지 않고, Epic 11/12 추가를 위해 아래 pointer만 둔다.

- Epic 8 후보: `planning-artifacts/stories/8-1-route-normalization-rule-change.md`와 `planning-artifacts/contracts/route-attribution-policy.md`에 route normalization rule change가 남아 있다.
- Epic 9: `planning-artifacts/epic-9-product-onboarding-and-project-seed-issuance-ui.md`가 product onboarding과 project seed issuance UI의 source-of-truth다.
- Epic 10: `planning-artifacts/figma-make-acceptance-sprint-plan.md`와 `implementation-artifacts/sprint-status.yaml`이 Figma Make acceptance/frontend hardening을 추적한다.

Epic 11/12의 상세 drift, story별 acceptance, sprint-status backlog proposal은 `planning-artifacts/epic-11-12-operational-alerts-and-sqs-batch-ingest-plan.md`를 기준으로 한다.

## Epic 11. Discord Operational Alert MVP

목표: 서버 metric state가 downgrade되거나 error/degraded concern이 발생했을 때 Discord webhook으로 짧고 안전한 운영 알림을 보낸다.

Epic 11은 incident platform이 아니다. Multi-channel escalation, PagerDuty류 on-call, acknowledgement lifecycle, alert delivery history UI, 복잡한 suppression policy, per-user notification setting은 만들지 않는다.

Downgrade 의미는 `state-semantics.md`의 lifecycle state와 degraded hysteresis를 따른다. Alert copy는 host application down, 정상 확정, 복구 완료를 단정하지 않고 accepted bucket axis와 starter heartbeat axis를 분리한다.

### Stories

1. Alert trigger semantics and Discord MVP ADR
   - Discord alert trigger source를 state/read model/service-layer 판단으로 고정한다.
   - `degraded`, `stale`, `down`, selected high-confidence concern을 alert 후보로 제한한다.
   - Heartbeat success/failure/missing 자체는 alert trigger가 아니며 operational event history와 alert delivery log를 섞지 않는다.
2. Discord webhook configuration and secret redaction guard
   - Discord webhook URL을 secret config로만 받고 missing/disabled config에서는 no-op 또는 disabled 상태로 둔다.
   - Webhook URL, raw token, starter credential, project key는 log/error/response/snapshot/history/test fixture에 남기지 않는다.
3. Minimal Discord alert delivery and payload copy
   - Project/application/environment/state/summary/deep link를 담은 최소 Discord payload를 보낸다.
   - Payload는 원인, host application down, 정상 확정, 복구 완료를 단정하지 않고 next check 중심으로 작성한다.
   - Endpoint evidence가 있으면 low-cardinality `method route`만 포함하고 raw path/query/trace/per-request sample은 포함하지 않는다.
4. Cooldown, throttle, and alert spam guard
   - 같은 `project + application + stateCode + primaryRuleId/endpointKey` 후보의 반복 alert를 configurable cooldown으로 제한한다.
   - Cooldown은 state/read model을 바꾸지 않고 delivery 여부만 제한한다.
   - Durable incident dedupe, acknowledgement, escalation, delivery history UI는 범위 밖이다.
5. Discord alert smoke verification and operator runbook
   - Disabled config, valid webhook config, failing webhook config를 local/smoke로 검증하는 runbook을 남긴다.
   - Smoke는 portfolio/demo verification이며 production incident drill이 아니다.

## Epic 12. SQS Buffered Ingest Transition

목표: portal HTTP ingest request path에서는 검증과 enqueue까지만 수행하고, Spring Boot portal 내부 SQS worker가 bounded batch로 `accepted_metric_buckets` 저장을 처리하도록 전환한다.

Epic 12의 기본 consumer는 Spring Boot portal 내부 worker다. Lambda consumer는 이번 구현 범위에 넣지 않고 IAM/VPC/RDS connection/cold start/deployment unit 분리가 필요해진 뒤 검토할 deferred/non-goal로만 남긴다.

성능 개선 주장은 두 단계로 분리한다. Phase 1은 request path에서 DB bucket insert를 제거해 HTTP ingest latency를 줄이는 개선이다. Phase 2는 batch writer가 DB round trip, transaction, catalog lookup/last-seen update를 줄였을 때만 DB batch throughput 개선으로 주장한다.

공통 guardrail:

- SQS는 accepted bucket data-plane insert path의 buffer이며 dashboard snapshot/history source가 아니다.
- SQS payload size limit, duplicate/no-op, malformed/conflict DLQ, snapshot cutoff, stale/down과 queue lag 분리는 Epic 12 story acceptance에 포함한다.
- Snapshot cutoff 이후 late bucket은 accepted bucket 저장은 허용하되 이미 생성된 snapshot/history에는 backfill하지 않는다.
- Production rollout, autoscaling, queue replay UI, backfill pipeline, complex exactly-once processing은 만들지 않는다.

### Stories

1. Architecture and Contract Decision
   - SQS buffered ingest architecture와 계약 변경 범위를 닫는다.
   - Standard queue + DLQ, Spring Boot portal 내부 worker, local fake queue 기본/LocalStack opt-in, SQS payload size limit, Lambda deferred/non-goal 경계를 닫힌 결정으로 문서화한다.
2. Ingest Enqueue Boundary
   - 기존 HTTP ingest acceptance를 validation/payload hash/enqueue boundary로 분리한다.
   - Enqueue 성공이 DB insert 완료나 dashboard freshness current를 뜻하지 않도록 `202 queued`와 message contract/redaction guard를 고정한다.
3. Spring Boot SQS Worker MVP and Idempotency
   - Portal 내부 worker가 SQS message를 poll/process/delete하는 MVP path를 닫는다.
   - Same payload duplicate는 no-op success, malformed/conflict는 DLQ 대상, transient DB failure는 retry 대상으로 분류한다.
4. Snapshot Delay and Pipeline Lag Semantics
   - Snapshot delay, snapshot cutoff, late-data no-backfill 정책을 정한다.
   - Queue lag diagnostic은 stale/down state나 host application down copy와 섞지 않는다.
5. Batch Writer and Performance Verification
   - Batch writer 최적화와 portfolio 성능 검증을 한 story로 묶는다.
   - Phase 1 request latency와 Phase 2 DB batch throughput 지표를 분리하고, 일반 local/dev/test 기본값과 benchmark profile을 분리한다.
6. Benchmark Evidence Run and Report
   - Story 12.5의 benchmark harness/guard를 사용해 실제 local/isolated benchmark 수치와 sanitized report artifact를 남긴다.
   - Phase 1 request latency와 Phase 2 DB batch throughput evidence를 같은 결론이나 단일 개선율로 합치지 않는다.

## Post-MVP Candidate Backlog

### Runtime Gauge Aggregate Extension

목표: JVM CPU, JVM heap, datasource pool usage의 latest-only 한계를 보완해 짧은 saturation spike와 지속 압력을 함께 표현한다.

후보 stories:

1. Runtime aggregate contract/schema version
   - `ingest-envelope` schema를 MVP `1.0`과 분리한다.
   - `latest`, `max`, `avg`, `sampleCount` 의미와 validation rule을 고정한다.
2. Starter runtime aggregate rollup
   - 30초 bucket 안 JVM/datasource valid sample에서 latest/max/avg/sampleCount를 계산한다.
   - raw sample 배열이나 arbitrary custom metric map은 만들지 않는다.
3. Portal acceptance and persistence update
   - aggregate ratio range, `avg <= max`, `latest <= max`, `sampleCount > 0`을 검증한다.
   - accepted bucket persistence에 max/avg/sampleCount를 추가한다.
4. Saturation hint read model update
   - `max`는 peak evidence, `avg`는 sustained pressure evidence, `latest`는 current-state evidence로 분리해 read model과 insight rule evidence에 노출한다.
   - multi-instance 평균은 sampleCount 기반 weighted average로 계산한다.

이 후보는 MVP 필수 경로가 아니다. 구현 전 `metric-taxonomy`, `ingest-envelope`, `database-schema`, `insight-rules`, dashboard read model contract를 함께 갱신해야 한다.

### Starter Flush Worker Wake-up Strategy

목표: MVP의 bounded in-memory queue + background flush worker 구조를 유지하되, worker idle 대기와 shutdown wake-up 방식을 운영 관측 결과에 맞춰 더 정교하게 조정한다.

현재 MVP는 `BlockingQueue.poll(timeout)` 기반 timeout 있는 blocking wait를 사용한다. 이는 CPU busy waiting은 아니며, 주기적으로 `running` 상태를 확인하고 `close()` interrupt에 반응하기 위한 단순한 구현이다.

후보 stories:

1. Worker wait strategy comparison
   - `poll(timeout)`, `take() + interrupt`, poison pill, executor/lifecycle 기반 종료 방식을 비교한다.
   - idle CPU wake-up, shutdown latency, 테스트 결정성, Spring bean lifecycle 연동 비용을 함께 본다.
2. Configurable worker lifecycle
   - poll interval, shutdown join timeout, retry/backoff를 starter configuration으로 열지 검토한다.
   - 기본값은 host app safety와 단순성을 우선하고, 잘못된 설정이 request path blocking으로 이어지지 않게 guard를 둔다.
3. Queue drain and worker observability
   - queue depth, dropped count, flush success/failure, last successful flush time, worker running state를 starter internal metric/log로 노출할지 검토한다.
   - durable outbox, Kafka/Redis, 별도 worker runtime 도입 여부와는 분리해서 판단한다.

이 후보는 MVP 필수 경로가 아니다. 구현 전 `starter-failure-semantics`, Story 2.4 Async Flush Worker, starter auto-configuration/lifecycle 설계를 함께 갱신해야 한다.

## Cross-Epic Acceptance Criteria

- MVP 필수 경로에 Prometheus 설치, scrape config, selector 등록, PromQL query가 없다.
- host app build/startup/request path는 portal 장애에 의해 막히지 않는다.
- starter heartbeat는 bucket ingest와 분리된 periodic control-plane/liveness signal이다.
- heartbeat 성공 또는 미수신은 accepted bucket, host business health, dashboard snapshot, operational event, p95/p99, rule/read-model calculation을 생성하거나 암시하지 않는다.
- accepted bucket은 application metric freshness/state/read-model source-of-truth다.
- heartbeat는 starter/application process liveness, portal reachability, project key validity, metadata validity의 control-plane source다.
- 최근 heartbeat와 없음/오래된 accepted bucket 조합은 no recent traffic/waiting for traffic/metric data idle 계열로 표현하고 host application down으로 단정하지 않는다.
- heartbeat도 끊기고 accepted bucket도 오래된 조합은 starter disconnected/telemetry unreachable/unknown 계열로 표현하고 host application down 원인은 확정하지 않는다.
- p95/p99 source of truth는 starter-reported canonical percentile이다.
- `localPercentiles`는 instance-local 30초 bucket의 starter canonical p95/p99다.
- 여러 starter p95/p99 값이 섞이는 app/project/window에서는 임의 평균/병합으로 단일 p95/p99를 만들지 않는다.
- Histogram bucket은 distribution/bucket display/diagnostic raw bucket source다.
- Endpoint는 bucket display only이며 endpoint별 p95/p99 계산, endpoint percentile judgment, endpoint p99 alert 기준을 만들지 않는다.
- first-screen state와 triage 문구는 dashboard read model에서 온다.
- 아키텍처 스타일은 Traditional MVC 하나다.
- `triageCards=[]`이면 zero-insight reason과 recommended action이 반드시 있다.
- endpoint priority는 rank, reason, evidence, confidence, freshness를 포함한다.
- `accepted_metric_buckets`는 30초 계산 원천이고, `dashboard_snapshots`는 1시간 기본 cadence의 coarse-grained dashboard history다.
- `dashboard_snapshots` 기본 retention은 14일이며 config로 조정 가능하다.
- 같은 `application + endpointKey + ruleId` concern은 60분 안에 중복 operational history event로 승격하지 않는다.
- degraded enter/resolve hysteresis는 `state-semantics.md`의 확정 기준을 따르며 30초 단발 blip을 degraded로 만들지 않는다.
- Dashboard triage card 노출 기준(confidence `>= 0.65`)과 operational history event 승격 기준(confidence `>= 0.82`)을 분리한다.
- Snapshot `read_model_json`의 endpoint evidence는 최대 10개까지 보존하고 raw path/query/trace/per-request sample은 포함하지 않는다.
- Snapshot `read_model_json`의 bounded instance summary는 instance snapshot trend projection을 위해 snapshot당 최대 50개까지 보존할 수 있다.
- Instance snapshot trend는 기본 `since=7d`, `limit=168`이며 최대 `since=14d`, `limit=336`으로 bounded 조회한다.
- Instance snapshot trend는 stored dashboard snapshot/read model projection이며 raw bucket explorer, raw snapshot list, endpoint timeseries, arbitrary metric query가 아니다.
- 30초 단위 dashboard snapshot 장기 보관, endpoint timeseries table, materialized view, Redis/outbox는 MVP 범위 밖이다.
- Operational Event History는 Epic 5/6에서 dashboard snapshot/read model 기반 bounded surface로 다루며 Epic 2/3에는 포함하지 않는다.
- Snapshot detail shape는 Epic 5의 `Dashboard snapshot persistence and marker contract`에서 닫는다.
- MVP에서는 별도 `operational_events` table을 만들지 않는다.
- Account signup과 login은 GitHub OAuth only다.
- MVP는 cookie 기반 server session을 사용하지 않고, API 요청은 `Authorization: Bearer <access_token>` 기준으로 인증한다.
- Project Entry와 `/api/projects/{projectId}/applications/**` resource API는 Bearer token account의 active account-project membership 안에서만 project를 노출한다.
- Account-project membership mismatch는 project 존재 여부 노출을 줄이기 위해 `404`로 fail-closed한다.
- Refresh Token은 rotation, 만료, revoke, reuse detection을 갖춘 token store 기준을 따른다.
- Local password, password reset, email verification required for signup, magic link, Google/Kakao/Naver OAuth, anonymous flow는 MVP 범위 밖이다.
- Controller/API response, log, error에는 GitHub OAuth token, provider raw payload, secret을 노출하지 않는다. 일반 resource API response/log/error에는 access token, refresh token도 노출하지 않는다.

## AC Traceability Matrix

| AC | Epic | Contract | MVC Layer Boundary | Planned Test |
|---|---|---|---|---|
| Traditional MVC only | Epic 1 | `architecture.md` | controller -> service -> repository direction | `MvcLayerBoundaryTest` |
| No pull metric MVP path | Epic 1, 2, 6 | `metric-taxonomy.md`, `ingest-envelope.md` | starter direct ingest service only | `NoPrometheusMvpPathTest` |
| Host build/startup/request path not blocked | Epic 2 | `architecture.md`, `ingest-envelope.md`, `starter-failure-semantics.md` | spring integration -> service -> bounded queue | `StarterNonBlockingIngestTest` |
| Ingest idempotency | Epic 3 | `ingest-envelope.md` | `IngestAcceptanceService` + `MetricBucketRepository` | `IngestAcceptanceServiceTest` |
| Starter canonical percentile source | Epic 5 | `ingest-envelope.md`, `histogram-merge.md` | `DashboardReadModelService` | `StarterCanonicalPercentileReadModelTest` |
| First-screen state source | Epic 4, 5 | `state-semantics.md`, `read-model-contract.md` | `DashboardReadModelService` | `DashboardReadModelSnapshotTest` |
| 0-insight is explicit | Epic 5 | `read-model-contract.md` | `TriageSummaryService` | `ZeroInsightReadModelTest` |
| Endpoint priority is explainable | Epic 5 | `insight-rules.md`, `read-model-contract.md` | `EndpointPriorityService` | `EndpointPriorityReadModelTest` |
| Project/application dashboard navigation | Epic 5, 6 | `read-model-contract.md`, `api-surface.md` | `DashboardReadModelService`, navigation read model service candidate | `DashboardNavigationReadModelTest` |
| Instance evidence drill-down | Epic 5, 6 | `read-model-contract.md` | `DashboardReadModelService` / instance evidence service candidate | `InstanceEvidenceReadModelTest` |
| Bounded operational event history | Epic 5, 6 | `operational-event-history.md`, `read-model-contract.md` | `OperationalEventHistoryService` candidate + `DashboardSnapshotRepository` | `OperationalEventHistoryReadModelTest` |
| Instance snapshot trend projection | Epic 5, 6 | `read-model-contract.md`, `operational-event-history.md`, `api-surface.md` | `InstanceSnapshotTrendService` candidate + `DashboardSnapshotRepository` | `InstanceSnapshotTrendProjectionTest` |
| GitHub OAuth only account signup/login | Epic 6 | `account-auth-policy.md`, `api-surface.md` | `AccountAuthService`, `AccountAuthController` | `AccountAuthPolicyTest` |
| Bearer JWT/refresh token session policy | Epic 6 | `account-auth-policy.md`, `architecture.md` | `ServiceTokenService`, `RefreshTokenStore` | `ServiceTokenPolicyTest` |
| Account-scoped project visibility | Epic 6 | `account-auth-policy.md`, `api-surface.md`, Story 6.10 contract | account-project membership service + project resource guard | `AccountProjectMembershipAuthorizationTest` |
| Demo promise | Epic 6 | `read-model-contract.md` | starter + portal e2e | `FirstBucketToAliveE2ETest` |
