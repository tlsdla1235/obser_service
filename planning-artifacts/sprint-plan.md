---
artifactType: sprint-plan
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: epic-7-real-smoke-planning
date: 2026-06-01
supersedes: Epic 3 Portal Ingest Acceptance sprint plan dated 2026-05-18
---

# Sprint Plan - Epic 5/6 Dashboard Alignment and Epic 7 Real Smoke Verification

## 1. Sprint Planning 결과 요약

이번 Sprint Plan은 Epic 5/6을 최신 dashboard UX 의도에 맞춰 개발 가능한 backlog 순서로 정렬한다.

2026-06-01 follow-up으로 Epic 6 완료 이후 실제 GitHub OAuth 계정과 starter 포함 Spring Boot smoke service를 사용하는 Epic 7 검증 backlog를 추가한다.

기준 source of truth는 아래 순서다.

1. `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
2. 최신 `planning-artifacts/contracts/*`
3. `planning-artifacts/prototypes/epic5-6-dashboard-flow-prototype.html`
4. 재정렬된 `planning-artifacts/epics.md`
5. `implementation-artifacts/sprint-status.yaml`

과거 sprint plan은 Epic 3 ingest acceptance를 준비하기 위한 문서였으므로, Epic 5/6 실행 기준으로는 이 문서가 우선한다.

## 2. Dashboard Alignment Sprint Goal

> Application Dashboard를 primary first-screen으로 유지하면서, Project -> Application -> Dashboard -> Instance Evidence -> Instance Snapshot Trend -> Snapshot/History 흐름을 server-computed read model/API와 UI integration backlog로 닫는다.

완료 판단은 아래 기준으로 한다.

- Epic 5는 Project/Application navigation, Application dashboard, Instance evidence, Instance snapshot trend, Snapshot/history API를 server-computed read model로 제공한다.
- Epic 6은 사용자가 실제로 밟는 화면 흐름과 demo hardening을 구현한다.
- Instance detail은 application 판단을 대체하지 않는 evidence drill-down으로 유지한다.
- Instance snapshot trend는 `dashboard_snapshots.read_model_json`의 bounded instance summary projection으로만 설명한다.
- Snapshot/history는 raw explorer가 아니라 stored dashboard read model history로 설명한다.

## 3. 포함 범위

포함:

- Project/Application navigation read model과 read-only application list surface
- Application dashboard read model skeleton과 dashboard query API
- source-scoped starter canonical p95/p99 point와 histogram distribution display payload
- triage summary, zeroInsight, recovery guidance mapping
- endpoint priority read model
- instance evidence read model
- instance snapshot trend projection
- dashboard snapshot persistence, marker, bounded endpoint/instance summary contract
- operational event history API
- Project selection UI, Application list UI, Application dashboard UI integration
- Instance evidence UI, Instance snapshot trend UI
- Snapshot/history marker UI와 snapshot deep link
- green path와 failure/recovery demo hardening

## 4. 제외 범위

제외:

- Java/Kotlin/Spring production source 수정 없이 planning 문서와 sprint tracking만 정렬한다.
- 닫힌 contracts 재논의 또는 뒤집기
- Epic 1~4 완료 상태 되돌리기
- Prometheus scrape/query UI, arbitrary metric query, dashboard builder, raw explorer
- UI-side lifecycle state, rule, p95/p99, endpoint priority, snapshot/history event 계산
- heartbeat를 accepted bucket freshness, host application health, dashboard snapshot, operational event source로 합성
- endpoint별 p95/p99 계산, endpoint timeseries table, long-retention time-series analytics
- 별도 `operational_events` table, event repository, materialized view, Redis/outbox를 MVP history source로 도입
- Alert/Discord surface를 Epic 6 MVP 필수로 확정

## 5. Epic 5 Story 순서

| Story | 핵심 결과 | 주요 계약 |
|---|---|---|
| 5.1 Project/Application navigation read model | Project Entry와 Application List가 사용할 scope 선택/read-only list read model | `current-product-source-of-truth.md`, `api-surface.md` |
| 5.2 Application dashboard read model skeleton | Application Dashboard primary first-screen 응답 skeleton | `read-model-contract.md` |
| 5.3 Source-scoped percentile and histogram distribution read model | starter canonical p95/p99 point와 bucket distribution 표시 경계 | `read-model-contract.md`, `histogram-merge.md` |
| 5.4 Triage summary and zero-insight/recovery mapping | triage 0~3개, zeroInsight, recovery guidance | `state-semantics.md`, `insight-rules.md` |
| 5.5 Endpoint priority read model | slow/error/comparative evidence 기반 next-check surface | `read-model-contract.md` |
| 5.6 Instance evidence read model | application 판단을 대체하지 않는 instance evidence drill-down | `read-model-contract.md` |
| 5.7 Instance snapshot trend projection | stored snapshot/read model 기반 bounded instance trend | `read-model-contract.md`, `operational-event-history.md` |
| 5.8 Dashboard snapshot persistence and marker contract | snapshot persistence, bounded endpoint/instance summary, marker contract | `database-schema.md`, `read-model-contract.md` |
| 5.9 Operational event history API | stored read model history와 snapshot deep link API | `operational-event-history.md`, `api-surface.md` |

## 6. Epic 6 Story 순서

| Story | 핵심 결과 | UX 기준 |
|---|---|---|
| 6.1 Account/project entry and setup guide | GitHub OAuth-only account boundary, project entry, minimal setup guide | Project Entry |
| 6.2 Project selection UI | Project scope 선택 화면 | Project Entry |
| 6.3 Application list UI | application 상태 스캔과 dashboard 진입 | Application List |
| 6.4 Application dashboard UI integration | primary first-screen dashboard | Application Dashboard |
| 6.5 Instance evidence UI | instance-level evidence drill-down | Instance Detail |
| 6.6 Instance snapshot trend UI | 24h/7d/14d bounded stored trend | Instance Snapshot Trend |
| 6.7 Snapshot/history marker UI and deep link | marker timeline과 stored snapshot detail | Snapshot / History |
| 6.8 Demo green path | heartbeat -> first bucket -> dashboard -> instance evidence demo | First data waiting, insufficient sample, no triage |
| 6.9 Failure/recovery path demo hardening | stale/down, telemetry unreachable, recovery observed demo | degraded, stale/down, recovery, unreachable |
| 6.10 Account-project membership and scoped project entry | authenticated account 기준 Project visibility와 project-scoped resource authorization | Project Entry / Resource API authorization |

## 7. Epic 7 Story 순서

Epic 7은 Epic 6 완료 후 실제 local/operator smoke를 위한 검증 sprint다. 제품 onboarding surface를 새로 여는 epic이 아니라, 닫힌 계약을 실제 GitHub OAuth 계정, local seed, starter 포함 Spring Boot smoke service로 재현하는 데 집중한다.

| Story | 핵심 결과 | 검증 기준 |
|---|---|---|
| 7.1 Real GitHub OAuth smoke seed and operator runbook | 실제 GitHub OAuth 로그인, local-only access token memo, smoke project/account membership seed, operator runbook | `GET /api/projects`가 `.private/smoke-auth.env`의 Bearer access token으로 로그인 account의 active membership project를 반환한다. |
| 7.2 Starter bucket ingest HTTP client | starter의 기본 bucket ingest HTTP client 또는 auto-configuration | custom smoke bean 없이 `POST /api/ingest/v1/buckets`가 bounded background worker에서 전송된다. |
| 7.3 Smoke Spring Boot service and portal communication verification | starter dependency가 붙은 smoke service, traffic generation, portal read API 검증 | heartbeat, accepted bucket, Dashboard, Instance Evidence까지 같은 Bearer/project key 계약 위에서 확인된다. |

## 8. Workflow Notes

- Application Dashboard는 primary first-screen이다.
- Project 화면은 운영 판단 화면이 아니라 scope 선택과 setup guide 진입 화면이다.
- Application List는 상세 판단을 대체하지 않고 dashboard 진입 전 상태 스캔만 돕는다.
- Instance Detail은 application 판단을 대체하지 않는 evidence drill-down이다.
- Instance Snapshot Trend는 stored dashboard snapshot/read model projection이다.
- Snapshot/history는 bounded stored read model history이며 raw explorer가 아니다.
- `dashboard_snapshots` 기본 cadence는 application별 1시간 scheduled snapshot이다.
- 1시간 cadence 외 capture는 state-change, high-confidence concern, 짧지만 강한 spike 실험값, query fallback 조건에만 허용한다.
- Snapshot `read_model_json`에는 endpoint evidence 최대 10개와 bounded instance summary 최대 50개를 둘 수 있다.
- UI는 lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority, snapshot/history event를 재계산하지 않는다.
- `GET /api/projects`와 `/api/projects/{projectId}/applications/**`는 Bearer account의 active account-project membership scope 안에서만 project를 노출한다.
- Account-project membership mismatch는 `403` 대신 `404`로 fail-closed한다.
- Epic 7 smoke seed는 local/internal path로만 둔다. Public project creation, login 직후 자동 project 생성, project key issuance/rotation UI는 열지 않는다.
- Epic 7 smoke verification은 GitHub provider token이 아니라 portal service access token과 starter project key를 각각의 경계에서 사용한다.
- Epic 7 token memo는 `.private/smoke-auth.env`의 `OBSERVATION_SMOKE_ACCESS_TOKEN`처럼 local smoke 자동화가 읽는 ephemeral file로 제한한다. Refresh token, GitHub provider token, provider raw payload, OAuth credential은 저장하지 않는다.
- Smoke service traffic은 accepted bucket metric axis와 starter heartbeat axis를 분리해 확인하고, host application down/정상 확정/복구 완료를 단정하지 않는다.

## 9. 권장 검증

문서 정렬 후 확인할 항목:

- `planning-artifacts/epics.md`의 Epic 5/6 story 순서가 이 sprint plan과 일치한다.
- `implementation-artifacts/sprint-status.yaml`의 Epic 5/6 backlog key가 story 목록과 일치한다.
- `planning-artifacts/epics.md`의 Epic 7 story 순서가 이 sprint plan과 일치한다.
- `implementation-artifacts/sprint-status.yaml`의 Epic 7 backlog key가 story 목록과 일치한다.
- `database-schema.md`가 `dashboard_snapshots.read_model_json`의 bounded instance summary와 instance trend projection 제약을 설명한다.
- `architecture.md`와 `architecture-implementation-supplement.md`가 Project/Application/Instance dashboard read model service 책임을 설명한다.
- `project-structure.md`가 dashboard/history/snapshot package 책임을 최신 Epic 5/6 흐름과 맞춘다.
- restart context UX 문서가 최신 source of truth/prototype보다 우선하지 않는다는 표시를 가진다.

## 10. 남은 Open Decisions

아래 결정은 이번 정렬에서 닫지 않는다.

1. Project 생성은 MVP에서 public onboarding API로 열 것인가, local/internal admin seed로 유지할 것인가? Account-project visibility와 resource authorization은 Story 6.10 membership으로 닫았지만 project creation/product onboarding은 여전히 열지 않는다.
2. Instance detail read model을 Epic 5에서 어느 깊이까지 닫을 것인가?
3. Instance snapshot trend를 Epic 6 MVP에 포함할 것인가, demo-only로 둘 것인가?
4. Snapshot/history marker UI를 Epic 6 MVP에 포함할 것인가, demo-only로 둘 것인가?
5. Alert/Discord surface는 Epic 6 MVP에 넣을 것인가, Post-MVP로 둘 것인가?
6. Application list에서 state summary를 얼마나 계산해 보여줄 것인가?
7. Epic 7 smoke seed는 SQL/runbook 중심으로 둘 것인가, local-only bootstrap command로 구현할 것인가?
8. Starter bucket ingest HTTP client 설정은 heartbeat portal connection 설정을 공유할 것인가, metric-flush 전용 설정으로 분리할 것인가?
9. Smoke Spring Boot service는 repo module로 둘 것인가, 외부 sample로 둘 것인가?

## 11. Sprint Status 기대값

2026-05-25 Epic 5/6 alignment 정렬 당시 기대 status:

- `epic-5`: `backlog`
- Story 5.1 -> 5.9: `backlog`
- `epic-5-retrospective`: `optional`
- `epic-6`: `backlog`
- Story 6.1 -> 6.9: `backlog`
- `epic-6-retrospective`: `optional`

2026-05-31 follow-up으로 추가된 Story 6.10은 account-project membership open decision을 닫기 위한 ready-for-dev story다.

Epic 1~4의 완료 또는 진행 상태는 이번 정렬에서 되돌리지 않는다.

2026-06-01 Epic 7 real smoke planning 직후 기대 status:

- `epic-7`: `backlog`
- Story 7.1 -> 7.3: `backlog`
- `epic-7-retrospective`: `optional`

다음 create-story 대상은 `7-1-real-github-oauth-smoke-seed-and-operator-runbook`이다.
