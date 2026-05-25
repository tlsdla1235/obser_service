---
artifactType: epics
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
sourcePolicy: 기존 Lightweight Hexagonal 산출물의 제품/계약 의미를 Traditional MVC로 재해석
status: dashboard-alignment-updated
date: 2026-05-25
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
| Demo promise | Epic 6 | `read-model-contract.md` | starter + portal e2e | `FirstBucketToAliveE2ETest` |
