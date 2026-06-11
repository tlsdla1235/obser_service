---
artifactType: product-source-of-truth
projectName: Spring Boot 운영 첫 화면 포털
status: active-alignment-baseline
date: 2026-05-25
alignmentAuthority: latest-user-intent-wins
uxBaselinePrototype: planning-artifacts/prototypes/epic5-6-dashboard-flow-prototype.html
sourceOfTruthUiMockup: planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html
---

# Current Product Source of Truth

## 1. 목적

이 문서는 최근 사용자 플로우 변경과 Story 4.x 이후의 계약 변경을 기준으로 Epic 5/6 문서 정렬의 기준점을 제공한다.

과거 PRD/UX/epic 문서가 서로 충돌하면 이 문서의 제품 흐름과 원칙을 먼저 적용하고, 그 다음 최신 contract 문서로 구현 경계를 닫는다.

### 1.1 Epic 5/6 UX Baseline

Epic 5/6 dashboard flow 판단에서는 `planning-artifacts/prototypes/epic5-6-dashboard-flow-prototype.html`을 최신 사용자 의도가 반영된 UX baseline으로 본다.

BMAD 또는 후속 문서 정렬에서 우선순위는 이 문서와 최신 contracts, 위 prototype, 재정렬된 Epic/sprint 문서, 과거 restart/context 문서 순서다.

과거 UX 문서가 application-only first screen, alert-first surface, raw explorer 중심 화면, 또는 Instance Snapshot Trend를 MVP 필수 화면으로 전제하면 이 문서와 prototype이 우선한다.

### 1.2 Source of Truth UI Mockup

`planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`은 현재 `frontend` React 화면의 Project rail, Application rail, Dashboard main grid, neutral border panel, compact typography를 디자인 reference로 삼아 source-of-truth 계약을 임시 데이터로 화면화한 목업이다.

이 목업은 production 구현 계약이 아니라 Epic 5/6 재정렬 전에 사용자 흐름을 확인하기 위한 참고 산출물이다. 특히 기존 instance detail이 오른쪽 `Sheet` 안에서 좁게 렌더링되던 문제를 피하기 위해, selected instance evidence는 넓은 modal로 분리하는 흐름을 제안한다.

목업이 따라야 하는 경계는 아래와 같다.

- Application Dashboard는 server read model을 표시하고 UI에서 lifecycle state, endpoint priority, resource pattern을 재계산하지 않는다.
- 첫 화면은 RED/USE 기반 golden signal, state strip, first look candidates, bounded endpoint/resource evidence를 우선한다.
- `p95/p99`는 instance modal 안에서 source-scoped starter local percentile 참고값으로만 표시한다.
- Snapshot 화면은 stored dashboard read model 복원을 보여주며 current metric 재계산, baseline diff, 장기 시계열 분석을 약속하지 않는다.
- Instance 상세는 application state를 대체하지 않고 selected instance evidence만 넓은 modal에서 보여준다.
- 2026-06-11 결정: Instance Summary/detail MVP 화면은 SoT `openModal`에 대응하는 단일 wide modal뿐이다. Stored trend, projection trend, InstanceTrendView, narrow Sheet, `openTrend`/`openLiveDashboard`, `snapshotTrend` surface는 MVP UI에서 제외한다. 과거 instance evidence는 Snapshot/History에서 snapshot을 선택한 뒤 snapshot-mode wide modal로 본다.

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

## 4.1 MVP Insight Alignment Baseline

이 섹션은 Epic 5/6과 후속 source-of-truth 문서가 따라야 하는 가장 거친 제품 판단 기준이다. MVP는 수집된 metric을 많이 보여주는 dashboard가 아니라, 수집된 gray data를 운영자가 바로 판단할 수 있는 evidence와 다음 확인 대상으로 바꾸는 dashboard다.

MVP에서 해결하려는 문제는 두 가지다.

1. 데이터 엔지니어링 관점에서는 수집된 metric 중 현재 개선이나 확인이 필요한 신호가 무엇인지 식별한다.
2. UX 관점에서는 운영자가 핵심 서비스 경로에서 실제로 영향을 받을 가능성이 높은 문제를 먼저 보게 한다.

이 둘이 만나는 지점은 impact 기반 우선순위화다. 단, MVP에서는 business criticality, endpoint owner, baseline, adaptive threshold, 장기 시계열 분석을 사용하지 않는다. 대신 현재 window 안에서 이미 수집된 request symptom과 resource pressure를 단순한 rule로 묶어 "어디부터 볼지"를 정한다.

### 4.1.1 우선순위 원칙

MVP dashboard는 느린 endpoint를 전부 나열하지 않는다. 우선순위는 아래처럼 bounded evidence queue로 표현한다.

1. 운영 기준을 직접 넘긴 application-level 오류 또는 지연
2. 공유 resource pressure와 함께 관찰된 여러 endpoint 증상
3. 오류와 지연이 함께 두드러지는 endpoint
4. 500번대 서버 오류가 관찰된 endpoint
5. resource pressure는 있으나 요청 증상이 아직 함께 보이지 않는 attention evidence
6. 표본 부족, freshness 지연, malformed evidence 같은 data quality issue

`impactScore`나 `confidence`를 사용자에게 숫자로 노출하지 않는다. 필요하면 내부 정렬 또는 snapshot helper column으로만 사용한다. 사용자가 봐야 하는 것은 점수가 아니라 "지금 무엇이 문제인지", "어디부터 확인할지", "왜 그렇게 판단했는지"다.

### 4.1.2 RED/USE 멘탈 모델

MVP는 운영 신호를 RED와 USE로 나누어 설명한다.

| 모델 | MVP 의미 | 사용자 언어 |
|---|---|---|
| RED Rate | 최근 window 요청량 | 요청량 |
| RED Errors | 5xx/500번대 서버 오류 | 서버 오류 |
| RED Duration | 500ms 초과 요청 비율 | 느린 요청 |
| USE Utilization/Saturation | CPU, heap, datasource pool 사용률 | resource pressure hint |
| USE Errors | MVP에서는 별도 resource error 축으로 확장하지 않음 | Post-MVP 후보 |

`p95`와 `p99` 용어를 금지하지는 않는다. 다만 MVP에서 p95/p99는 source-scoped starter local percentile 참고값이다. 여러 instance나 여러 bucket의 p95/p99를 평균, 최댓값, 병합해 application 대표 p95/p99처럼 만들지 않는다. Application state, endpoint priority, shared resource pattern 판단은 p95/p99가 아니라 `requestCount`, `errorRate`, `slowShareOver500ms`, resource usage threshold를 사용한다.

### 4.1.3 첫 화면 정보 구조

첫 화면은 backend 내부 구조를 설명하지 않는다. `observationHandler`, ingest 세부 동작, raw metric field 이름보다 운영자가 묻는 질문을 먼저 답한다.

첫 화면의 최소 답변은 아래 순서를 따른다.

1. 지금 metric data를 믿을 수 있는가?
2. application metric state는 무엇인가?
3. 직접 상태를 바꾼 request symptom이 있는가?
4. 함께 확인할 resource pressure hint가 있는가?
5. 먼저 볼 endpoint 또는 instance evidence는 무엇인가?
6. data quality 때문에 과신하면 안 되는 부분은 무엇인가?

정보는 점진적으로 노출한다. 첫 화면에는 golden signal 요약, state 설명, 최대 0~3개의 first look candidate, bounded endpoint/resource evidence만 둔다. 상세 endpoint 분해, instance evidence, starter heartbeat/control-plane 정보는 drill-down에서 확인한다.

### 4.1.4 Rule-Based Interpretation

MVP rule은 root cause를 확정하지 않고 운영 가설을 좁힌다.

공유 resource가 threshold를 넘고 같은 window에서 application 또는 여러 endpoint의 오류/지연 증상이 함께 관찰되면, dashboard는 "공유 자원 압박과 요청 증상이 함께 관찰된다"고 표현한다. 예를 들어 datasource pool 사용률과 여러 endpoint 지연이 함께 있으면 connection wait 가능성을 확인하라는 evidence로 보여준다. 단, "DB pool이 원인"이라고 단정하지 않는다.

반대로 공유 resource pressure가 기준 이하인데 특정 endpoint만 오류 또는 지연 threshold를 넘으면, dashboard는 "특정 endpoint의 로직, query, downstream 호출 등을 먼저 확인할 후보"로 표현한다. 이것도 root cause 확정이 아니라 first look candidate다.

모든 endpoint 사이의 상관관계를 MVP에서 분해하지 않는다. MVP는 endpoint 간 causality 분석보다 공유 resource 축을 먼저 사용해 시스템 차원의 resource competition 가능성과 endpoint-local 문제 가능성을 구분한다.

### 4.1.5 Snapshot 해석

Snapshot은 flight recorder처럼 사용한다. 문제가 있거나 확인할 가치가 있는 시점의 dashboard read model을 저장해, 나중에 "그때 화면이 어떻게 판단했는지"를 복원한다.

MVP snapshot은 아래를 포함한다.

- 저장 당시 application state
- stateReason과 attentionEvidence
- 같은 window의 endpoint evidence
- 같은 window의 resource pressure hint
- instance summary와 drill-down link
- data quality limitation
- 회고나 공유에 사용할 수 있는 operator summary

MVP snapshot은 raw metric dump, incident report, 장기 시계열 분석, baseline diff가 아니다. "평소 대비 무엇이 달라졌는가" diff, p99 상승 trend, 전일/전주 비교, adaptive threshold는 MVP 이후 확장으로 둔다.

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

1. Application context rail: project, application, environment, recent 30-minute window
2. State semantic strip: metric state, freshness, explanation, next action
3. Starter connection strip: heartbeat/connection status, project key/metadata/portal reachability 후보
4. Headline metrics: request count, error rate, 500ms 초과 요청 비율, resource hints
5. Triage cards: 최대 0~3개
6. Zero-insight/recovery message: triage가 없을 때도 이유와 다음 행동 제공
7. Endpoint priority list: error/latency/server-error attention evidence 기반
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
- source-scoped starter p95/p99 latest/reference value
- histogram bucket distribution
- endpoint evidence subset
- JVM/datasource/CPU resource hint
- 이 instance가 application triage에 기여했는지 여부

### 5.4.1 Instance Snapshot Trend (Read-Model Contract / Post-MVP)

2026-06-11 UI MVP 결정: 아래 trend 설명은 저장된 projection/read-model 계약과 Post-MVP 후보를 보존하기 위한 설명이다. 현재 MVP UI에는 별도 Stored trend, projection trend, InstanceTrendView, narrow Sheet, `openTrend`/`openLiveDashboard`, `snapshotTrend` surface를 제공하지 않는다. 과거 instance evidence는 Snapshot/History에서 snapshot을 선택한 뒤 snapshot-mode wide modal로 본다.

후속에서 trend UI를 다시 열 경우 Instance detail 안에는 문제가 있는 순간의 evidence만이 아니라, 선택된 instance가 최근 며칠 동안 어떻게 관찰됐는지 보는 bounded trend 진입점이 있을 수 있다.

이 화면은 외부 monitoring service의 host/resource detail처럼 특정 entity의 최근 변화를 보는 관찰 화면이다. 단, 이 제품에서는 raw metric explorer가 아니라 stored dashboard snapshot/read model에서 특정 instance summary만 projection하는 화면으로 제한한다.

표시 후보:

- 기본 horizon: 최근 7일
- 선택 후보: 24시간 / 7일 / 14일 retention 안
- 30분 scheduled snapshot slot marker
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

- 30분 scheduled snapshot slot marker
- state change marker
- high-confidence concern marker
- short strong spike candidate marker
- snapshot detail deep link

금지:

- 30초 dashboard snapshot 장기 보관처럼 보이게 만들지 않는다.
- 14일 p99 같은 long-window representative percentile을 만들지 않는다.
- snapshot detail에서 current state를 재판정하지 않는다.

`hourly_scheduled`는 기존 persistence/API token으로 남을 수 있지만 사용자-facing UX와 문서 의미는 30분 scheduled snapshot slot을 기준으로 한다.

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
7. Instance snapshot trend projection contract 보존(Post-MVP UI 후보)
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
6. Instance snapshot-mode wide modal UI
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
- Stored trend, projection trend, InstanceTrendView, narrow Sheet, `openTrend`/`openLiveDashboard`, `snapshotTrend` surface를 MVP 필수 UI처럼 표현하는 문장

## 10. 남은 결정 질문

1. Project 생성은 MVP에서 public onboarding API로 열 것인가, local/internal admin seed로 유지할 것인가?
2. Instance detail read model을 Epic 5에서 어느 깊이까지 닫을 것인가?
3. 결정 완료(2026-06-11): Instance snapshot trend UI는 Epic 6 MVP에서 제외한다. 저장 projection/read-model 계약은 보존하되, 화면은 Snapshot/History -> snapshot-mode wide modal 흐름만 사용한다.
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
