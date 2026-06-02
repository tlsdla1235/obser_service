---
artifactType: figma-make-frontend-spec
projectName: Spring Boot 운영 첫 화면 포털
targetStack: Next.js, shadcn/ui
status: draft-for-figma-make
date: 2026-06-02
sourceScope: existing-mvp-frontend-and-defined-api-reuse
---

# Figma Make Next.js Frontend Redesign Spec

## 1. 목적

현재 MVP 프론트는 `observability-portal/src/main/resources/static/dashboard/`의 정적 HTML/CSS/JavaScript로 구성되어 있으며, 제품 소개, 로그인, Project Entry, Application List, Application Dashboard, setup guide가 한 화면 안에 함께 배치되어 있다.

이번 작업의 목적은 기능과 API 계약을 새로 열지 않고, 이미 정의된 endpoint와 server read model을 전부 재사용해 Next.js + shadcn/ui 기반의 새 디자인 스펙을 만드는 것이다.

핵심 변경은 아래 세 가지다.

- 대문과 대시보드를 분리한다.
- 대문은 프로젝트 소개와 간편한 사용법, "Spring Boot 애플리케이션을 손쉽게 모니터링해준다"는 제품 메시지를 담당한다.
- 네비게이션에 `Docs`를 추가하고, docs 페이지는 기존 문서의 레퍼런스를 바탕으로 시작하기, 설정, allow list, dashboard 읽는 법을 안내한다.

## 2. Figma Make 입력용 요약 프롬프트

아래 블록을 Figma Make에 직접 전달할 수 있다.

```text
Next.js App Router와 shadcn/ui로 Spring Boot starter-first observability dashboard 프론트를 새로 디자인한다. 기존 backend endpoint를 새로 만들지 말고 모두 재사용한다. public landing page(`/`)와 authenticated dashboard(`/dashboard`)를 분리하고, navigation에는 Dashboard와 Docs(`/docs`)를 둔다.

제품명은 Observation Portal이다. 대문은 "Spring Boot 앱에 starter를 붙이면 30초 metric bucket과 heartbeat를 portal로 보내고, project/application/instance 단위로 지금 데이터가 들어오는지, starter 연결이 살아 있는지, 어디부터 확인해야 하는지 보여준다"는 메시지를 전달한다. "간편한 사용법" 섹션은 Project 등록, starter credential 설정, Dashboard 확인의 3단계로 보여준다.

디자인은 흑백/그레이 기반의 절제된 운영 도구 느낌이다. 아이콘은 lucide-react 계열의 black and white line icon만 사용한다. status 의미는 색상에만 기대지 말고 텍스트, border, label, icon 조합으로 표현한다. shadcn/ui Button, Badge, Tabs, Table, Dialog, Sheet, Tooltip, Separator, Input, Skeleton, Alert를 사용한다.

Dashboard는 marketing hero가 아니라 dense operational workspace다. Project rail, Application list, Application Dashboard, Instance evidence, Snapshot/history, Credential lifecycle을 표시한다. UI는 lifecycle state, starter connection diagnosis, p95/p99, endpoint priority, snapshot/history event를 계산하지 않고 server read model field를 그대로 표시한다.
```

## 3. 제품 정의

Observation Portal은 Spring Boot 애플리케이션에 starter를 붙이면 30초 단위 metric bucket과 heartbeat를 포털로 보내고, 포털이 project, application, instance 단위의 운영 첫 화면을 만들어 주는 starter-first observability dashboard다.

사용자가 첫 화면에서 답을 얻어야 하는 질문은 아래와 같다.

- 지금 데이터가 들어오고 있는가?
- starter 연결은 살아 있는가?
- application metric state는 무엇인가?
- 느려졌나, 에러가 늘었나?
- 어디부터 확인하면 되는가?

이 제품은 raw metric explorer가 아니다. 서버가 만든 read model을 표시해 사용자가 직접 percentile, state, endpoint priority를 해석하지 않게 한다.

## 4. 비목표와 금지선

이번 프론트 redesign에서 하지 않는다.

- 새로운 backend endpoint 생성
- API response shape 확장
- UI에서 lifecycle state 계산
- UI에서 starter connection diagnosis 계산
- UI에서 p95/p99 계산, 평균, 최댓값, 병합
- histogram bucket으로 percentile 재계산
- endpoint priority 재정렬 또는 client-side ranking
- heartbeat 성공을 application health success로 표현
- heartbeat 누락을 host application down으로 단정
- snapshot detail에서 current state 재계산
- raw query explorer, trace/log/APM full product 확장
- browser localStorage/sessionStorage/cookie token persistence를 새 계약으로 고정

## 5. 기술 스택

### 5.1 필수 스택

- Next.js App Router
- TypeScript
- shadcn/ui
- Tailwind CSS
- lucide-react icon
- React client state for authenticated dashboard workspace

### 5.2 API 호출 원칙

- `/api/*`는 기존 Spring portal endpoint를 그대로 호출한다.
- Next.js API route를 새 기능 endpoint로 만들지 않는다.
- deployment에서 Next.js와 Spring portal이 분리된다면 `/api/*`는 reverse proxy 또는 environment API base URL로 기존 portal에 연결한다.
- Resource API는 `Authorization: Bearer <access_token>`을 사용한다.
- Starter ingest API는 dashboard UI가 직접 호출하지 않고 docs/setup 안내에서만 설명한다.

## 6. 전역 정보 구조

### 6.1 Public route

| Route | 목적 | API 사용 |
| --- | --- | --- |
| `/` | 제품 대문, 소개, 사용법, dashboard/docs 진입 | 없음 |
| `/docs` | 기존 docs/reference 기반 안내 | 없음 또는 static content |

### 6.2 Authenticated dashboard route

| Route | 목적 | API 사용 |
| --- | --- | --- |
| `/dashboard` | Project 선택, Application 선택, Dashboard current read model, credential lifecycle | 기존 resource API |

선택 상태를 URL로 보조 표현할 수는 있다. `links.*`가 제공되는 navigation surface에서는 서버가 내려준 link를 우선 사용한다. 단, `snapshot-markers`, `operational-events`처럼 현재 read model이 link를 내려주지 않는 기존 endpoint는 새 API를 만들지 않고 §11의 endpoint template과 현재 `projectId`/`applicationId`로 호출해도 된다.

## 7. 공통 레이아웃

### 7.1 Public navigation

Navigation items:

- Observation Portal 로고/브랜드
- Dashboard
- Docs
- GitHub 로그인 또는 Dashboard 열기

Icon candidates:

- Dashboard: `LayoutDashboard`
- Docs: `BookOpen`
- Login: `Github`
- Product/monitoring: `Activity`, `ScanLine`, `ServerCog`

모든 아이콘은 black/white/gray stroke만 사용한다. status icon도 채도 있는 색으로 의미를 만들지 않는다.

### 7.2 Dashboard app shell

Desktop layout:

- 상단 bar: breadcrumb, selected project/application, auth 상태, reload action, Docs link
- 왼쪽 rail: Project list와 project registration
- 중간 rail: Application list
- main area: Application Dashboard
- 오른쪽 contextual panel: Credential lifecycle, Snapshot/history, Instance handoff summary

Mobile layout:

- 상단 bar 고정
- `Tabs` 또는 `Sheet`로 Projects, Applications, Dashboard, Evidence를 전환
- Project/Application 선택 후 main dashboard를 우선 노출
- 긴 table은 card list로 변환하되 text truncation과 tooltip을 제공

## 8. Landing Page Spec

### 8.1 역할

대문은 dashboard와 분리된 public product intro다. 로그인 전에도 제품이 무엇을 하는지, 얼마나 쉽게 시작하는지, starter가 애플리케이션을 모니터링해 준다는 약속을 이해할 수 있어야 한다.

### 8.2 First viewport

H1:

- `Observation Portal`

Supporting copy:

- `Spring Boot 앱에 starter를 붙이면 30초 metric bucket과 heartbeat를 모아, 지금 데이터가 들어오는지와 어디부터 확인할지 한 화면에서 보여줍니다.`

Primary CTA:

- `Dashboard 열기`

Secondary CTA:

- `Docs 보기`

Visual:

- 실제 dashboard workspace를 연상시키는 product UI preview 또는 generated bitmap background를 사용한다.
- gradient orb, 추상 SVG 장식, 마케팅 카드만 있는 hero는 사용하지 않는다.
- hero 아래 다음 섹션의 일부가 첫 viewport에 보여야 한다.

### 8.3 간편한 사용법 섹션

3단계로 구성한다.

| Step | 제목 | 설명 |
| --- | --- | --- |
| 1 | Project 등록 | GitHub OAuth로 로그인하고 project를 만든다. starter credential은 생성/회전 성공 직후 1회만 표시된다. |
| 2 | Starter 설정 | Spring Boot 앱에 starter dependency와 portal base URL, project key, environment를 설정한다. |
| 3 | Dashboard 확인 | Project -> Application -> Dashboard로 들어가 accepted bucket freshness와 starter heartbeat를 분리해서 확인한다. |

### 8.4 제품 가치 섹션

필수 메시지:

- 설치는 starter 중심으로 간단하다.
- metric data-plane과 starter heartbeat control-plane을 분리해 보여준다.
- 서버 read model이 state, triage, endpoint priority를 계산하고 UI는 이를 그대로 표시한다.
- 작은 팀이 처음 운영 판단을 빠르게 할 수 있다.

## 9. Dashboard Page Spec

### 9.1 Auth entry

사용자 흐름:

1. `GET /api/auth/github/authorize` 호출
2. 응답의 `authorizationUrl`로 popup 또는 redirect 시작
3. backend callback page가 relay id를 dashboard opener에 전달
4. dashboard가 `POST /api/auth/github/callback/tokens`로 service access token을 1회 회수
5. 이후 resource API에 `Authorization: Bearer <access_token>` 부여

UI 상태:

- 로그아웃 상태: GitHub login action, resource API disabled
- 로그인 진행 중: loading/skeleton
- 로그인 완료: account id/provider/token expiry summary
- 401: "GitHub 로그인 후 다시 시도하세요"
- token 없음: API health나 project 없음으로 표현하지 않는다

### 9.2 Project Entry

Source endpoint:

- `GET /api/projects`

Required UI:

- Project 검색/필터
- Project item list
- application count
- setup/connection issue candidate count
- recent concern 최대 1개
- Applications 진입 action
- Credential metadata/rotate/revoke action
- Project registration form

Project registration:

- `POST /api/projects`
- request: `{ "name": "<project name>" }`
- success: project summary와 `starterCredential.displayValue`를 1회 표시
- raw credential은 copy action 이후에도 장기 상태로 보존하지 않는다.
- "다시 볼 수 없음, 필요하면 rotate" 안내를 명확히 표시한다.

### 9.3 Application List

Source endpoint:

- `GET /api/projects/{projectId}/applications`

Required UI:

- Application 검색/필터
- application name, environment
- server-computed lifecycle badge
- metric data summary: `statusSource=accepted_bucket`, `lastAcceptedBucketAt`, `freshnessLabel`
- starter connection summary: `statusSource=starter_heartbeat`, `lastHeartbeatAt`, `heartbeatStatus`, `freshnessLabel`, `connectionMeaning`, `stateImpact`
- top concern 최대 1개
- Dashboard action

의미 경계:

- Application List는 상세 판단 화면이 아니다.
- accepted bucket freshness와 starter heartbeat를 하나의 health로 합치지 않는다.
- Dashboard action은 API item의 `links.dashboard`를 사용한다.

### 9.4 Application Dashboard

Source endpoint:

- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard`

Above the fold priority:

1. Application context rail: project, application, environment, current/baseline window
2. Metric state strip: `state.code`, `state.label`, `state.rationale`, `state.recommendedAction`, `application.freshness`
3. Starter connection strip: `starterConnection.statusSource`, `lastHeartbeatAt`, `lastHeartbeatStatus`, `connectionMeaning`, `stateImpact`
4. Headline metrics: request count, error count, error rate
5. Source-scoped percentile points: source/scope/p95/p99 with instance and bucket boundary
6. Triage cards or zero insight
7. Endpoint priority next-check list
8. Instance summary handoff
9. Snapshot marker/history handoff

Required visual treatment:

- Metric state strip and starter connection strip must be separate bands.
- `stateImpact=none` must be visible in a compact details row or tooltip.
- `triageCards=[]` must render `zeroInsight`, not an empty panel.
- `recovery.isRecovering=true` copy means "회복 관찰 중", not "복구 완료".
- Endpoint priority label should be "먼저 확인할 endpoint" or "Next check", not root cause ranking.

### 9.5 Instance Evidence

Source endpoint:

- `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence`

Required UI:

- Back to Application Dashboard
- instance identity: name/id, first seen, last seen
- Metric data axis: accepted bucket source, freshness, sample readiness, request/error count/rate
- Starter connection axis: starter heartbeat source, last heartbeat, state impact none
- Starter percentile series: 30초 bucket, max 30 points, source-scoped p95/p99
- Histogram distribution as evidence, not percentile source
- Resource hints
- Application triage contribution
- Endpoint evidence subset

Meaning boundary:

- Instance detail does not calculate application state.
- Instance detail does not create instance health score.
- Endpoint evidence is bounded evidence, not raw explorer.

### 9.6 Instance Snapshot Trend

Source endpoint:

- `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend?since=7d&limit=168`

Required controls:

- Horizon segmented control: 7d, 14d
- Default horizon: 7d
- Max horizon: 14d
- Default limit: 168
- Max limit: 336
- Limit can be hidden unless advanced mode is needed
- Trend list/timeline ordered by `capturedAt_asc`

Required UI:

- source badge: `dashboard_snapshots.read_model_json.instanceSummary.items`
- stored application state code
- capturedAt/currentWindowEndUtc
- metric data freshness
- starter connection
- starter percentile point if present
- resource hints
- triage contribution
- endpoint evidence refs

Meaning boundary:

- Trend is stored snapshot projection.
- No current state recalculation.
- No instance health score.

### 9.7 Snapshot Markers, Snapshot Detail, Operational Events

Marker endpoint:

- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers?since=24h&limit=50`

Detail endpoint:

- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}`

Operational event endpoint:

- `GET /api/projects/{projectId}/applications/{applicationId}/operational-events?since=24h&limit=50`

Required UI:

- timeline or table view with since presets
- marker/event type, severity, title, summary, captured/occurred time
- snapshot detail drawer or page
- source badge: `dashboard_snapshots`
- read semantics block: stored snapshot detail, current state recalculated false, raw JSON not exposed

Meaning boundary:

- Snapshot/history is not raw time-series explorer.
- Operational event history is compact projection from dashboard snapshots.
- Snapshot detail does not join live sources.

### 9.8 Starter Credential Lifecycle

Endpoints:

- `GET /api/projects/{projectId}/starter-credential`
- `POST /api/projects/{projectId}/starter-credential/rotations`
- `POST /api/projects/{projectId}/starter-credential/revocations`

Required UI:

- Metadata: key prefix, status, issued/rotated/revoked time
- Rotate dialog with destructive confirmation copy
- Rotation success one-time raw display with copy action
- Revoke confirmation dialog
- Revoked state disables starter connection expectation copy

Security boundary:

- Raw credential appears only in project creation/rotation response.
- Metadata screens never show raw value/hash.
- `Cache-Control: no-store` semantics should be respected.

## 10. Docs Page Spec

### 10.1 역할

Docs 페이지는 새 기능 문서를 창작하는 곳이 아니라 기존 reference를 읽기 쉬운 제품 문서 UI로 감싸는 곳이다. 초기 Figma/Next.js 작업에서는 아래 reference를 content source로 삼는다.

Reference sources:

- `README.md`
- `docs/project-owner-guide.html`
- `planning-artifacts/current-product-source-of-truth.md`
- `planning-artifacts/api-surface.md`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionProperties.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/MetricDrainProperties.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/HeartbeatProperties.java`

### 10.2 Docs IA

필수 섹션:

| 순서 | 섹션 | 내용 |
| --- | --- | --- |
| 1 | 시작하기 | 프로젝트가 무엇인지, starter-first 흐름, 가장 짧은 실행 순서 |
| 2 | Portal 설정 | PostgreSQL, GitHub OAuth, service token signing key, dashboard 접속 |
| 3 | Starter 연결 | dependency, metric flush 설정, heartbeat 설정, project key 사용 |
| 4 | Route allow list | `observation.route-attribution.allowlist`의 목적과 안전한 route template 규칙 |
| 5 | Dashboard 읽는 법 | accepted bucket과 starter heartbeat 분리, state/zeroInsight/recovery 의미 |
| 6 | API Reference | 기존 endpoint 목록과 인증 방식 |
| 7 | Troubleshooting | 401, 404, no data, insufficient sample, telemetry unreachable |

### 10.3 Route Allow List 안내

Docs에서 반드시 설명할 내용:

- 설정 key: `observation.route-attribution.allowlist`
- raw path가 아니라 `/orders/{orderId}` 같은 route template만 허용한다.
- framework `http.route`가 있으면 그것을 우선 사용한다.
- allowlist는 `http.route`가 없거나 실패했을 때 raw path candidate를 안전한 template으로 귀속시키는 fallback이다.
- query string, absolute URL, collapse marker, 실제 ID처럼 보이는 concrete segment는 허용하지 않는다.
- ambiguous match는 `UNKNOWN`으로 수렴한다.
- `UNKNOWN` route는 endpoint priority에 노출하지 않는다.

Example copy:

```yaml
observation:
  route-attribution:
    allowlist:
      - /orders/{orderId}
      - /inventory/{sku}
```

### 10.4 Starter 설정 안내

Metric flush:

```yaml
observation:
  metric-flush:
    portal-base-url: http://localhost:8080
    project-key: <starter credential>
    project-id: <stable local project identity>
    application-name: orders-api
    environment: prod
    instance: orders-api-local
```

Heartbeat:

```yaml
observation:
  heartbeat:
    enabled: true
    portal-base-url: http://localhost:8080
    project-key: <starter credential>
    starter-version: 0.1.0-SNAPSHOT
    interval-seconds: 30
```

Docs 문구는 아래를 명확히 설명해야 한다.

- `project-key`는 `X-OBS-Project-Key` 인증 header에 사용할 raw starter credential이며 로그, 화면, 오류 메시지에 노출하면 안 된다.
- `project-id`는 starter가 bucket ingest `Idempotency-Key`를 만들 때 사용하는 stable local project identity다.
- `project-id`와 `project-key`는 서로 다른 값이다. 설정, 문서, 테스트에서 둘을 혼동하지 않는다.
- metric flush와 heartbeat는 서로 별도의 portal connection 설정을 가진다.
- heartbeat는 accepted bucket, dashboard state, snapshot, operational event를 만들지 않는다.

## 11. Endpoint Reuse Contract

### 11.1 Auth endpoints

| Method | Endpoint | Auth | UI usage |
| --- | --- | --- | --- |
| GET | `/api/auth/github/authorize` | none | GitHub OAuth 시작 URL 요청 |
| GET | `/api/auth/github/callback` | GitHub callback | popup callback relay page |
| POST | `/api/auth/github/callback/tokens` | relay id | dashboard memory로 access token 1회 회수 |
| GET | `/api/auth/github/callback/token` | GitHub callback | tool/test용 token pair JSON, 기본 dashboard flow에서는 사용하지 않음 |
| POST | `/api/auth/token/refresh` | refresh token body | token rotation 후보, 기본 dashboard persistence 계약으로 고정하지 않음 |
| POST | `/api/auth/logout` | refresh token body | logout/revoke |

### 11.2 Project and catalog endpoints

| Method | Endpoint | Auth | UI usage |
| --- | --- | --- | --- |
| GET | `/api/projects` | Bearer | Project Entry list |
| POST | `/api/projects` | Bearer | Project registration, starter credential 1회 표시 |
| GET | `/api/projects/{projectId}/applications` | Bearer + membership | Application List |
| GET | `/api/projects/{projectId}/starter-credential` | Bearer + membership | credential metadata |
| POST | `/api/projects/{projectId}/starter-credential/rotations` | Bearer + membership | credential rotate, raw value 1회 표시 |
| POST | `/api/projects/{projectId}/starter-credential/revocations` | Bearer + membership | credential revoke |

### 11.3 Dashboard read model endpoints

| Method | Endpoint | Auth | UI usage |
| --- | --- | --- | --- |
| GET | `/api/projects/{projectId}/applications/{applicationId}/dashboard` | Bearer + membership | Application Dashboard current read model |
| GET | `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/evidence` | Bearer + membership | Instance Evidence |
| GET | `/api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend` | Bearer + membership | Instance Snapshot Trend |
| GET | `/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshot-markers` | Bearer + membership | Snapshot marker timeline |
| GET | `/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}` | Bearer + membership | Snapshot detail |
| GET | `/api/projects/{projectId}/applications/{applicationId}/operational-events` | Bearer + membership | Operational event history |

Supported query presets:

- snapshot trend: `since=7d|14d`, default `7d`, max `14d`, default limit `168`, max limit `336`
- snapshot markers: default `24h`, max `14d`, default limit `50`, max `336`
- operational events: default `24h`, max `14d`, default limit `50`, max `100`

### 11.4 Starter ingest endpoints

| Method | Endpoint | Auth | UI usage |
| --- | --- | --- | --- |
| POST | `/api/ingest/v1/buckets` | `X-OBS-Project-Key`, `Idempotency-Key` | starter only, docs/setup reference |
| POST | `/api/ingest/v1/heartbeat` | `X-OBS-Project-Key` | starter only, docs/setup reference |

Bucket ingest status semantics:

- `201`: 새 bucket accepted. Response body는 `status=accepted`, `duplicate=false`, `bucketId`, `acceptedAt`을 포함한다.
- `400`: request validation 실패. Error body는 `error=invalid_request`와 validation error 목록을 포함할 수 있다.
- `401`: project key 누락 또는 검증 실패. Error body는 `error=unauthorized`다.
- `409`: 이미 사용된 idempotency key. Error body는 `error=duplicate_idempotency_key`다.
- `200 duplicate=true`는 현재 구현 계약으로 사용하지 않는다.

## 12. Response Field Mapping

### 12.1 Project navigation

`GET /api/projects`

Display fields:

- `generatedAt`
- `projects[].projectId`
- `projects[].name`
- `projects[].applicationCount`
- `projects[].setupConnectionIssueCount`
- `projects[].recentConcern`
- `projects[].links.applications`

### 12.2 Application navigation

`GET /api/projects/{projectId}/applications`

Display fields:

- `generatedAt`
- `project.projectId`, `project.name`
- `applications[].applicationId`
- `applications[].name`
- `applications[].environment`
- `applications[].metricData.statusSource`
- `applications[].metricData.lastAcceptedBucketAt`
- `applications[].metricData.freshnessLabel`
- `applications[].starterConnection.statusSource`
- `applications[].starterConnection.lastHeartbeatAt`
- `applications[].starterConnection.heartbeatStatus`
- `applications[].starterConnection.freshnessLabel`
- `applications[].starterConnection.connectionMeaning`
- `applications[].starterConnection.stateImpact`
- `applications[].lifecycleBadge`
- `applications[].topConcern`
- `applications[].links.dashboard`

### 12.3 Dashboard current read model

`GET /api/projects/{projectId}/applications/{applicationId}/dashboard`

Top-level blocks:

- `generatedAt`
- `application`
- `state`
- `starterConnection`
- `zeroInsight`
- `recovery`
- `metrics`
- `sourceScopedPercentiles`
- `histogramDistribution`
- `triageCards[]`
- `endpointPriority[]`
- `instances[]`
- `snapshot`

Display rules:

- `metrics` has request/error scalar only.
- p95/p99 appears only from `sourceScopedPercentiles.items[]`.
- Histogram appears as distribution evidence only.
- `instances[].links.evidence` drives instance evidence entry.

### 12.4 Error states

UI interpretation:

- Error body shape는 endpoint마다 다르며 일부 `400`/`404`는 body가 없을 수 있다.
- UI는 status code를 먼저 처리하고, response body가 있을 때만 endpoint-specific shape를 점진적으로 파싱한다.
- `401`: auth required or expired token, not project absence.
- `404`: resource scope mismatch or membership failure; do not infer application health.
- `400`: invalid query or invalid request; show query/action correction.
- `409`: duplicate project name or duplicate idempotency depending endpoint.
- `500`: backend/projection failure; show retry copy.

## 13. shadcn/ui Component Plan

| UI need | shadcn/ui component |
| --- | --- |
| Primary/secondary actions | `Button` |
| Status/source labels | `Badge` |
| Project/application lists | `ScrollArea`, `Table`, item cards |
| Auth/project forms | `Input`, `Label`, `Button`, `Form` |
| Dashboard sections | unframed sections, `Separator`, repeated item cards |
| Detail overlays | `Sheet`, `Dialog` |
| Horizon presets | `Tabs` or segmented `ToggleGroup` |
| Confirm rotate/revoke | `AlertDialog` |
| Loading | `Skeleton` |
| Errors/no data | `Alert` |
| Dense metadata | `Table`, `Tooltip` |

Design note:

- Do not nest cards inside cards.
- Use cards only for repeated items, dialogs, and genuinely framed tools.
- Dashboard sections should feel like an operational workspace, not a marketing page.

## 14. Visual System

### 14.1 Palette

- Background: white or near-white
- Text: black and neutral gray
- Borders: neutral gray
- Status accents: grayscale patterns, labels, icon shape, border weight
- Avoid bright color-coded alert language in the base design

### 14.2 Typography

- Korean/English mixed UI: Pretendard or system sans-serif
- Compact headings inside panels
- Hero-scale type only on landing hero
- Dashboard labels are small, scannable, and dense

### 14.3 Icons

Use lucide-react, monochrome stroke:

- `Activity`: metric data
- `Radio`: heartbeat/starter connection
- `Gauge`: metrics
- `ListChecks`: endpoint next check
- `Server`: instance
- `History`: snapshot/history
- `KeyRound`: credential
- `BookOpen`: docs
- `Github`: auth

## 15. Accessibility and Responsive Rules

- Every icon-only button must have tooltip and accessible label.
- Status is never color-only.
- Tables must collapse into readable item lists on mobile.
- Long route names and UUIDs must wrap, truncate, or use tooltip without overflowing.
- Loading and empty states use `aria-live` where state changes affect the workspace.
- Focus states must be visible in black/gray palette.

## 16. Acceptance Checklist

- Landing `/` and Dashboard `/dashboard` are visually and structurally separate.
- Nav includes `Docs`.
- Docs page has at least 시작하기, Portal 설정, Starter 연결, Route allow list, Dashboard 읽는 법, API Reference sections.
- All dashboard data fetches use existing endpoints.
- Project/Application navigation uses server-provided `links.*`.
- Access token is sent as Bearer header for resource API.
- Starter credential raw value is visible only immediately after project create/rotation.
- Metric state strip and starter connection strip are visually separate.
- UI never computes lifecycle state, p95/p99, endpoint priority, snapshot/history event.
- Triage empty state renders `zeroInsight`.
- Recovery copy says observing/recovering, not complete/resolved.
- Icons are black/white/gray line icons.
- Dashboard is dense operational UI, not a landing page.

## 17. Source References

- Current static MVP frontend: `observability-portal/src/main/resources/static/dashboard/index.html`
- Current static runtime: `observability-portal/src/main/resources/static/dashboard/app.js`
- Current static styles: `observability-portal/src/main/resources/static/dashboard/styles.css`
- API surface: `planning-artifacts/api-surface.md`
- Product source of truth: `planning-artifacts/current-product-source-of-truth.md`
- Project owner guide: `docs/project-owner-guide.html`
- Dashboard model: `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
- Project navigation model: `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectNavigationReadModel.java`
- Application navigation model: `observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectApplicationNavigationReadModel.java`
- Instance evidence model: `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceEvidenceReadModel.java`
- Instance trend model: `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceSnapshotTrendReadModel.java`
- Route allowlist config: `observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionProperties.java`
