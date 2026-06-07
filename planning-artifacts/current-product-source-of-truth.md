---
artifactType: product-source-of-truth
projectName: Spring Boot 운영 첫 화면 포털
status: active-alignment-baseline
date: 2026-05-25
alignmentAuthority: latest-user-intent-wins
uxBaselinePrototype: planning-artifacts/prototypes/epic5-6-dashboard-flow-prototype.html
---

# Current Product Source of Truth

## 1. 목적

이 문서는 최근 사용자 플로우 변경과 Story 4.x 이후의 계약 변경을 기준으로 Epic 5/6 문서 정렬의 기준점을 제공한다.

과거 PRD/UX/epic 문서가 서로 충돌하면 이 문서의 제품 흐름과 원칙을 먼저 적용하고, 그 다음 최신 contract 문서로 구현 경계를 닫는다.

### 1.1 Epic 5/6 UX Baseline

Epic 5/6 dashboard flow 판단에서는 `planning-artifacts/prototypes/epic5-6-dashboard-flow-prototype.html`을 최신 사용자 의도가 반영된 UX baseline으로 본다.

BMAD 또는 후속 문서 정렬에서 우선순위는 이 문서와 최신 contracts, 위 prototype, 재정렬된 Epic/sprint 문서, 과거 restart/context 문서 순서다.

과거 UX 문서가 application-only first screen, alert-first surface, raw explorer 중심 화면, 또는 instance snapshot trend 부재를 전제하면 이 문서와 prototype이 우선한다.

## 2. 최신 사용자 의도

사용자는 `project -> application -> instance` 순서로 들어가 대시보드를 확인한다.

대시보드는 범용 query builder나 차트 탐색기가 아니라, 잘 알려진 모니터링 서비스들이 제공하는 핵심 운영 화면 패턴을 차용해 한눈에 상태와 다음 행동을 이해하게 하는 화면이다.

핵심 의도는 아래와 같다.

- 사용자는 먼저 project를 고른다.
- project 안에서 application 목록과 상태를 본다.
- application dashboard에서 현재 상태, 수집 신선도, 주요 지표, triage, endpoint 우선순위를 한눈에 본다.
- 필요할 때만 instance detail로 들어가 특정 instance의 freshness, heartbeat/connection, latency/error/resource evidence를 확인한다.
- dashboard와 instance 화면은 운영 판단을 돕는 read model을 보여주며, 사용자가 raw metric을 직접 해석하게 만들지 않는다.

## 3. 최근 변경 추적

최근 변경 흐름은 아래 순서로 이해한다.

1. Prometheus scrape/query 중심 MVP에서 Micrometer direct ingest 중심 MVP로 전환했다.
2. `accepted_metric_buckets`가 metric freshness, state, read model의 data-plane source-of-truth가 됐다.
3. starter heartbeat는 metric ingest와 분리된 control-plane/liveness signal로 추가됐다.
4. heartbeat는 starter/application process liveness, portal reachability, project key validity, metadata validity의 source지만, application health나 accepted bucket freshness를 직접 만들지 않는다.
5. `summary.localPercentiles`는 instance-local 30초 bucket의 starter canonical p95/p99로 수용한다.
6. histogram bucket은 p95/p99 계산 입력이 아니라 distribution display와 diagnostic evidence로 유지한다.
7. `LifecycleStateService`는 accepted bucket axis와 starter heartbeat axis를 분리한다.
8. recovery guidance는 stale/down 이후 새 bucket은 왔지만 sample이 부족한 구간을 `unknown + recovery.isRecovering=true`로 표현한다.
9. Epic 5/6은 이제 기존 application-only first screen이 아니라 `project -> application -> instance` 탐색 흐름을 반영해야 한다.

## 4. 제품 한 줄 정의

Spring Boot 앱에 starter를 붙이면 project/application/instance 단위로 수집 연결과 운영 상태를 확인하고, 30~60초 안에 application dashboard에서 지금 무엇을 믿을 수 있고 어디부터 볼지 알 수 있는 starter-first observability dashboard다.

## 5. 사용자 플로우

### 5.1 Project Entry

사용자는 로그인 후 project 목록 또는 현재 project context로 진입한다.

Project 화면은 운영 판단 화면이 아니라 scope 선택 화면이다.

표시 후보:

- project name
- application count
- 최근 accepted bucket 수신 여부 요약
- connection/setup issue count 후보
- 최근 high-confidence concern count 후보

### 5.2 Application List

사용자는 project 안에서 application을 고른다.

Application list는 각 application의 현재 상태를 빠르게 스캔하게 해야 한다.

표시 후보:

- application name
- environment
- lifecycle state badge
- last accepted bucket time
- starter connection summary
- request/error/latency headline 후보
- top concern 0~1개

이 목록은 상세 판단을 하지 않는다. 상세 판단은 application dashboard read model에서 온다.

### 5.3 Application Dashboard

Application dashboard가 primary first-screen이다.

이 화면은 한눈에 아래 질문에 답해야 한다.

- 지금 데이터가 들어오고 있나?
- starter는 연결되어 있나?
- application metric state는 무엇인가?
- 느려졌나?
- 에러가 늘었나?
- resource pressure hint가 있나?
- 어디부터 보면 되나?

화면 우선순위:

1. Application context rail: project, application, environment, current/baseline window
2. State semantic strip: metric state, freshness, explanation, next action
3. Starter connection strip: heartbeat/connection status, project key/metadata/portal reachability 후보
4. Headline metrics: request count, error rate, source-scoped starter p95/p99, resource hints
5. Triage cards: 최대 0~3개
6. Zero-insight/recovery message: triage가 없을 때도 이유와 다음 행동 제공
7. Endpoint priority list: slow/error/comparative evidence 기반
8. Instance summary: 문제가 있거나 stale한 instance를 evidence drill-down으로 연결
9. Snapshot/history markers: 저장된 read model 기반의 최근 운영 맥락

### 5.4 Instance Detail

Instance detail은 application dashboard를 대체하지 않는다.

역할은 application state를 구성한 evidence를 좁혀 보는 것이다.

표시 후보:

- instance identity
- last accepted bucket time
- starter heartbeat last seen
- metric freshness와 starter connection의 분리 표시
- 30초 bucket source-scoped starter p95/p99 point series
- histogram bucket distribution
- endpoint evidence subset
- JVM/datasource/CPU resource hint
- 이 instance가 application triage에 기여했는지 여부

### 5.4.1 Instance Snapshot Trend

Instance detail 안에는 문제가 있는 순간의 evidence만이 아니라, 선택된 instance가 최근 며칠 동안 어떻게 관찰됐는지 보는 bounded trend 진입점이 있을 수 있다.

이 화면은 외부 monitoring service의 host/resource detail처럼 특정 entity의 최근 변화를 보는 관찰 화면이다. 단, 이 제품에서는 raw metric explorer가 아니라 stored dashboard snapshot/read model에서 특정 instance summary만 projection하는 화면으로 제한한다.

표시 후보:

- 기본 horizon: 최근 7일
- 선택 후보: 24시간 / 7일 / 14일 retention 안
- hourly scheduled snapshot marker
- state-change/high-confidence concern/recovery/stale/down/fallback capture marker
- snapshot 시점의 instance identity
- snapshot 시점의 accepted bucket freshness
- snapshot 시점의 starter heartbeat/connection observation
- snapshot 시점의 source-scoped starter p95/p99 latest point
- snapshot 시점의 bounded resource hint
- snapshot 시점의 application triage contribution 여부

Instance trend 금지:

- instance trend에서 application state를 새로 판정하지 않는다.
- accepted bucket, heartbeat, resource hint를 조합해 UI/API가 instance health score를 만들지 않는다.
- 30초 raw bucket explorer, arbitrary metric query, endpoint timeseries 화면으로 확장하지 않는다.
- retention을 넘는 장기 instance analytics처럼 약속하지 않는다.

Instance detail 전체 금지:

- instance 화면에서 application state를 새로 판정하지 않는다.
- 여러 instance p95/p99를 평균/최댓값/병합해 application p95/p99처럼 표시하지 않는다.
- endpoint별 p95/p99를 새로 계산하지 않는다.

### 5.5 Snapshot / History

Snapshot과 history는 raw time-series explorer가 아니다.

역할은 사용자가 늦게 들어왔을 때 최근 상태 변화, high-confidence concern, recovery/stale/down 흐름을 이해하게 하는 bounded read model history다.

표시 후보:

- hourly scheduled snapshot marker
- state change marker
- high-confidence concern marker
- short strong spike candidate marker
- snapshot detail deep link

금지:

- 30초 dashboard snapshot 장기 보관처럼 보이게 만들지 않는다.
- 14일 p99 같은 long-window representative percentile을 만들지 않는다.
- snapshot detail에서 current state를 재판정하지 않는다.

## 6. 대시보드 UX 차용 원칙

잘 알려진 모니터링 서비스에서 차용할 것은 “기능 수”가 아니라 운영자가 빠르게 판단하는 화면 문법이다.

차용할 패턴:

- status/health strip
- no data, insufficient data, stale, recovery의 분리
- alert/incident처럼 보이는 high-confidence concern marker
- latency/error/resource headline metric
- top affected endpoint 또는 top concern
- bounded event/history timeline
- drill-down link from summary to evidence

차용하지 않을 것:

- 범용 query builder
- raw tag explorer
- unrestricted metric browser
- trace/log/full APM product
- long-retention time-series analytics
- UI-side percentile/state/rule 계산

## 7. 구현 원칙

### 7.1 Source-of-Truth

- metric freshness/state/read-model의 source-of-truth는 accepted bucket이다.
- starter heartbeat는 separate control-plane/liveness source다.
- first-screen UI의 source-of-truth는 server read model response다.
- Flyway migration은 physical schema의 source-of-truth다.

### 7.2 UI Boundary

UI는 server read model을 표시한다.

UI는 아래를 재계산하지 않는다.

- lifecycle state
- starter connection diagnosis
- zero-insight reason
- recovery guidance
- insight rule
- endpoint priority
- p95/p99
- snapshot/history event selection

### 7.3 Percentile Boundary

- `summary.localPercentiles.p95Ms`와 `p99Ms`는 instance-local 30초 bucket의 starter canonical percentile이다.
- 같은 scope에서 starter-reported percentile과 histogram-derived percentile을 병렬 표시하지 않는다.
- 여러 instance/window의 p95/p99 숫자끼리 평균/최댓값/병합해 새 p95/p99를 만들지 않는다.
- 상위 scope에서 percentile scalar가 모호하면 source-scoped point series 또는 bucket distribution을 표시한다.
- histogram bucket은 distribution display와 diagnostic raw bucket source다.

### 7.4 Heartbeat Boundary

- heartbeat는 `POST /api/ingest/v1/heartbeat` control-plane signal이다.
- heartbeat 성공만으로 accepted bucket, dashboard snapshot, operational event, p95/p99, rule/read-model calculation을 생성하지 않는다. 최근 heartbeat는 새 scheduled/fallback snapshot 저장 eligibility gate로만 사용할 수 있다.
- heartbeat 미수신은 host application down 확정이 아니다.
- heartbeat와 accepted bucket freshness는 같은 화면에 보여줄 수 있지만 같은 상태축으로 합치지 않는다.

## 8. Epic 5/6 재정렬 기준

### Epic 5: Server Read Model and Dashboard Intelligence

Epic 5는 UI가 소비할 server-computed read model을 닫는다.

권장 story 흐름:

1. Project/Application navigation read model
2. Application dashboard read model skeleton
3. Source-scoped percentile and histogram distribution read model
4. Triage summary and zero-insight/recovery mapping
5. Endpoint priority read model
6. Instance evidence read model
7. Instance snapshot trend projection
8. Dashboard snapshot persistence and marker contract
9. Operational event history API

Epic 5의 definition of done:

- Application dashboard가 server read model 하나로 현재 상태와 next action을 설명할 수 있다.
- Instance detail은 application 판단을 대체하지 않는 evidence drill-down으로 닫힌다.
- Snapshot/history는 stored read model 기반 bounded history로 닫힌다.

### Epic 6: User-Facing Flow and Demo Hardening

Epic 6은 사용자가 실제로 밟는 화면과 demo path를 닫는다.

권장 story 흐름:

1. Account/project entry and setup guide
2. Project selection UI
3. Application list UI
4. Application dashboard UI integration
5. Instance evidence UI
6. Instance snapshot trend UI
7. Snapshot/history marker UI and deep link
8. Demo green path
9. Failure/recovery path demo hardening

Epic 6의 definition of done:

- 사용자는 project에서 application으로, application에서 instance evidence로 자연스럽게 좁혀 들어간다.
- 첫 화면은 데이터가 없거나 부족하거나 회복 중이어도 빈 화면처럼 보이지 않는다.
- demo는 starter setup, heartbeat, first accepted bucket, application dashboard, instance evidence, failure/recovery path를 보여준다.

## 9. 문서 정렬 규칙

문서 정렬 순서는 아래를 따른다.

1. 이 문서의 제품 흐름과 최신 사용자 의도
2. 최신 contract 문서
3. architecture/API/schema 문서
4. epics/story split
5. sprint-status tracking

과거 문서가 아래 표현을 유지하면 정렬 대상이다.

- application-only first screen처럼 project/instance 흐름을 지우는 표현
- heartbeat를 app health 또는 accepted bucket freshness로 해석하는 표현
- UI가 state/rule/p95/p99/endpoint priority를 계산하는 표현
- histogram bucket으로 p95/p99를 새로 만드는 표현
- long-window p99를 대표 지표처럼 표시하는 표현
- raw metric/query explorer를 MVP 핵심처럼 표현하는 문장

## 10. 남은 결정 질문

1. Project 생성은 MVP에서 public onboarding API로 열 것인가, local/internal admin seed로 유지할 것인가?
2. Instance detail read model을 Epic 5에서 어느 깊이까지 닫을 것인가?
3. Instance snapshot trend를 Epic 6 MVP에 포함할 것인가, demo-only로 둘 것인가?
4. Snapshot/history marker UI를 Epic 6 MVP에 포함할 것인가, demo-only로 둘 것인가?
5. Alert/Discord surface는 Epic 6 MVP에 넣을 것인가, Post-MVP로 둘 것인가?
6. Application list에서 state summary를 얼마나 계산해 보여줄 것인가?

## 11. 바로 적용할 정렬 대상

이 문서를 기준으로 먼저 정렬할 문서:

- `planning-artifacts/epics.md`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/database-schema.md`
- `planning-artifacts/project-structure.md`
- `bmad-restart-context-pack/ux-design-specification.md`

`party-roundtable-starter-health-instance-ingest-alignment.md`는 중요한 변경 추적 문서지만, 이 문서와 최신 contracts에 의해 정리된 기준을 우선한다.
