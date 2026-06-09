---
artifactType: alignment-context
projectName: Spring Boot 운영 첫 화면 포털
status: handoff-ready
date: 2026-05-25
scope: Epic 5/6 dashboard source-of-truth, planning docs, sprint status alignment
primarySourceOfTruth: planning-artifacts/source-of-truth/current-product-source-of-truth.md
uxBaselinePrototype: planning-artifacts/prototypes/epic5-6-dashboard-flow-prototype.html
---

# Epic 5/6 Dashboard Alignment Context

## 1. Purpose

이 문서는 새 BMAD/context 작업에서 Epic 5/6 관련 planning 문서와 sprint 상태를 최신 dashboard UX 의도에 맞게 재정렬하기 위한 handoff context다.

여기서 product intent를 다시 발명하지 않는다. `current-product-source-of-truth.md`, 최신 contracts, 그리고 UX prototype을 기준으로 낡은 문서 표현과 sprint backlog를 정렬한다.

## 2. Alignment Decision

Source of truth lock은 현재 컨텍스트에서 완료한다.

대규모 문서 재정렬은 이 문서를 읽는 새 컨텍스트에서 수행한다. 이유는 정렬 대상이 여러 planning 문서, sprint 문서, restart context에 걸쳐 있어 현재 컨텍스트에서 이어서 진행하면 부분 수정과 누락 위험이 커지기 때문이다.

## 3. Read First

새 컨텍스트는 아래 순서로 읽는다.

1. `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
2. `planning-artifacts/prototypes/epic5-6-dashboard-flow-prototype.html`
3. `planning-artifacts/contracts/read-model-contract.md`
4. `planning-artifacts/contracts/state-semantics.md`
5. `planning-artifacts/contracts/operational-event-history.md`
6. `planning-artifacts/api-surface.md`
7. `planning-artifacts/party-context-instance-snapshot-trend.md`
8. `planning-artifacts/epics.md`
9. `implementation-artifacts/sprint-status.yaml`
10. `planning-artifacts/sprint-plan.md`
11. `bmad-restart-context-pack/ux-design-specification.md`

## 4. Precedence Rules

문서가 충돌하면 아래 순서를 따른다.

1. `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
2. 최신 `planning-artifacts/contracts/*`
3. `planning-artifacts/prototypes/epic5-6-dashboard-flow-prototype.html`
4. 재정렬된 `planning-artifacts/epics.md`와 `implementation-artifacts/sprint-status.yaml`
5. 과거 `bmad-restart-context-pack/*`

과거 UX 문서가 application-only first screen, alert-first surface, raw explorer 중심 화면, 또는 instance snapshot trend 부재를 전제하면 최신 source of truth와 prototype이 우선한다.

## 5. Current Product Intent

최신 dashboard 흐름은 `project -> application -> instance`다.

Application dashboard가 primary first-screen이다. Project 화면은 scope 선택, Application list는 상태 스캔과 dashboard 진입, Instance detail은 application 판단을 대체하지 않는 evidence drill-down이다.

대시보드는 raw metric/query explorer가 아니다. Datadog, New Relic, Dynatrace, Sentry, Elastic 같은 monitoring service의 운영 화면 문법을 차용하되, MVP에서는 server-computed read model과 bounded snapshot/history만 표시한다.

## 6. Locked Contract Boundaries

아래 경계는 재정렬 중 다시 열지 않는다.

- accepted bucket은 metric freshness, lifecycle state, dashboard read model의 data-plane source-of-truth다.
- starter heartbeat는 accepted bucket과 분리된 control-plane/liveness source다.
- heartbeat 성공/미수신은 host application health나 accepted bucket freshness를 직접 만들지 않는다.
- UI는 lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority, snapshot/history event를 재계산하지 않는다.
- `summary.localPercentiles.p95Ms`와 `p99Ms`는 instance-local 30초 bucket의 starter canonical percentile이다.
- histogram bucket은 percentile 계산 입력이 아니라 distribution display와 diagnostic evidence다.
- 여러 instance/window p95/p99를 평균, 최댓값, 병합해 application p95/p99처럼 표시하지 않는다.
- snapshot/history는 raw time-series explorer가 아니라 stored dashboard read model 기반 bounded history다.
- snapshot detail은 current state를 재판정하지 않는다.
- instance snapshot trend는 저장된 application snapshot/read model에서 특정 instance summary만 projection한다.

## 7. UX Baseline Screens

Prototype 기준 화면은 아래와 같다.

1. Project Entry
2. Application List
3. Application Dashboard
4. Instance Detail
5. Instance Snapshot Trend
6. Snapshot / History

반드시 유지할 상태 시나리오는 아래와 같다.

- First data waiting
- Insufficient sample
- No triage worth surfacing
- Degraded/anomaly
- Stale/down candidate
- Recovery observed
- Telemetry unreachable

## 8. Document Drift Map

### 8.1 Already Close To Target

- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`: 최신 baseline 역할을 하도록 상단에 prototype 우선순위가 추가됐다.
- `planning-artifacts/contracts/read-model-contract.md`: instance snapshot trend 후보와 read model 경계가 반영돼 있다.
- `planning-artifacts/contracts/operational-event-history.md`: snapshot/history와 instance trend projection 경계가 반영돼 있다.
- `planning-artifacts/api-surface.md`: instance snapshot trend endpoint 후보가 반영돼 있다.

### 8.2 Needs Realignment

- `planning-artifacts/epics.md`: Epic 5/6 story 순서를 prototype 중심으로 재정렬해야 한다.
- `implementation-artifacts/sprint-status.yaml`: Epic 5/6 backlog가 낡은 story 목록이다. Epic 4 note도 실제 status와 불일치한다.
- `planning-artifacts/sprint-plan.md`: Epic 3 기준의 오래된 sprint plan이므로 current Epic 5/6 planning 문서로 대체하거나 superseded 표시가 필요하다.
- `planning-artifacts/database-schema.md`: `dashboard_snapshots.read_model_json`의 bounded instance summary와 instance trend projection 제약을 반영해야 한다.
- `planning-artifacts/architecture.md`: dashboard read model, snapshot projection, project/application/instance flow의 server-side responsibility를 반영해야 한다.
- `planning-artifacts/architecture-implementation-supplement.md`: 구현 보조 설계가 최신 read model/API 경계와 맞아야 한다.
- `planning-artifacts/project-structure.md`: Epic 5/6 서비스/API/UI 책임 위치를 최신 구조로 정렬해야 한다.
- `bmad-restart-context-pack/ux-design-specification.md`: 과거 UX 의도 문서로 유지하되, 상단에 최신 source of truth/prototype override 경고를 넣거나 새 baseline으로 정리해야 한다.

### 8.3 Likely Superseded Or Historical

- `planning-artifacts/next-context-prompt.md`: 오래된 다음 컨텍스트 prompt다. 현재 BMAD 기준으로 읽히지 않도록 superseded 표시가 필요할 수 있다.
- 과거 sprint change proposal 문서들: 결정 이력으로 유지하되, 최신 source of truth와 contracts가 우선한다.

## 9. Target Epic Shape

### Epic 5 Target

Epic 5는 사용자 화면이 소비할 server-computed read model/API를 닫는다.

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

### Epic 6 Target

Epic 6은 사용자가 실제로 밟는 화면, UX flow, demo hardening을 닫는다.

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

## 10. Sprint Status Alignment

`implementation-artifacts/sprint-status.yaml`을 수정할 때는 Epic 1~4의 완료 상태를 되돌리지 않는다.

필요한 조정:

- `last_updated`를 최신 작업 시점으로 갱신한다.
- Epic 4 workflow note에서 Story 4.2~4.4가 backlog라고 남아 있는 표현을 실제 status에 맞게 고친다.
- Epic 5 backlog를 Target Epic 5 story 목록으로 교체한다.
- Epic 6 backlog를 Target Epic 6 story 목록으로 교체한다.
- Epic 5/6 story id는 기존 파일명 관례를 따르되, 낡은 이름이 최신 의도와 충돌하면 새 이름을 우선한다.
- 기존 구현 코드나 Java/Kotlin/Spring production source는 수정하지 않는다.

## 11. Contract-Safe Language

문서 정렬 중 아래 표현은 피한다.

- heartbeat가 application health를 증명한다.
- heartbeat가 accepted bucket freshness를 만든다.
- UI가 state, rule, p95/p99, endpoint priority를 계산한다.
- histogram으로 p95/p99를 새로 계산한다.
- instance trend가 instance health score를 제공한다.
- snapshot/history가 raw time-series explorer다.
- long-window p99를 대표 지표로 제공한다.
- instance detail이 application dashboard를 대체한다.

권장 표현:

- accepted bucket 기반 metric freshness
- starter heartbeat 기반 control-plane/liveness observation
- server-computed read model
- source-scoped starter canonical p95/p99 point
- bounded snapshot/history
- stored read model projection
- evidence drill-down
- no current-state rejudgement

## 12. Open Decisions To Preserve

아래는 문서 정렬 중 닫지 말고 open decision으로 유지한다.

1. Project 생성은 MVP에서 public onboarding API로 열 것인가, local/internal admin seed로 유지할 것인가?
2. Instance detail read model을 Epic 5에서 어느 깊이까지 닫을 것인가?
3. Instance snapshot trend를 Epic 6 MVP에 포함할 것인가, demo-only로 둘 것인가?
4. Snapshot/history marker UI를 Epic 6 MVP에 포함할 것인가, demo-only로 둘 것인가?
5. Alert/Discord surface는 Epic 6 MVP에 넣을 것인가, Post-MVP로 둘 것인가?
6. Application list에서 state summary를 얼마나 계산해 보여줄 것인가?

## 13. Suggested New Context Prompt

새 컨텍스트를 시작할 때는 아래 요청을 그대로 사용해도 된다.

```text
프로젝트 루트는 /Users/tlsdla1235/Desktop/study/observation 이야.

planning-artifacts/epic5-6-dashboard-alignment-context.md 를 먼저 읽고, 그 문서의 precedence와 drift map에 따라 Epic 5/6 관련 planning 문서와 implementation-artifacts/sprint-status.yaml을 정렬해줘.

중요:
- current-product-source-of-truth.md와 최신 contracts를 우선한다.
- planning-artifacts/prototypes/epic5-6-dashboard-flow-prototype.html이 Epic 5/6 UX baseline이다.
- 기존 Java/Kotlin/Spring production 코드는 수정하지 마.
- 닫힌 계약을 재논의하지 마.
- sprint-status.yaml은 Epic 1~4 상태를 되돌리지 말고 Epic 5/6 backlog와 낡은 workflow note만 정렬해줘.
- 문서가 서로 충돌하면 alignment context의 precedence rule을 따른다.
```

## 14. Exit Criteria

정렬 작업이 끝났다고 볼 수 있는 조건은 아래와 같다.

- Epic 5/6 문서가 project -> application -> instance dashboard flow를 같은 방식으로 설명한다.
- Application dashboard가 primary first-screen으로 유지된다.
- Instance detail과 instance snapshot trend가 evidence drill-down/projection으로만 설명된다.
- Snapshot/history가 bounded stored read model history로만 설명된다.
- Sprint status의 Epic 5/6 backlog가 최신 target epic shape와 일치한다.
- 과거 restart UX 문서가 BMAD를 낡은 UX 방향으로 끌고 가지 않도록 override 또는 superseded 표시가 있다.
