---
artifactType: traceability-matrix
scope: instance-summary
epic: "Epic 13. Dashboard Source of Truth Realignment"
relatedStory: "13-ui-dashboard-source-of-truth-surface-realignment"
sourceOfTruthMode: read-only
alignmentDecision: strict-parity-single-modal
date: 2026-06-11
owner: tlsdla1235
status: backend-row-summary-contract-in-progress
productionCodeChangeThisContext: true
---

# Instance Summary ↔ Source of Truth 추적 매트릭스

## 0. 이 문서의 목적

`planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html`(이하 **SoT HTML**)의
Instance Summary surface와, 실제 frontend 구현이 **요소 단위로 1:1 대응**하는지를 못박는다.

지금까지 instance summary 구현이 반복적으로 틀린 근본 원인은 "어떤 SoT 요소가 어떤 frontend
컴포넌트/라인에 대응하는지" 계약이 없어서 매번 재해석됐기 때문이다. 이 매트릭스는 그 계약이며,
동시에 추적 가능한 문서다. 각 행은 독립적으로 검증되고, 구현자(codex 포함)는 **`DRIFT`/`MISSING`/`EXTRA`
행만 해결하고 상태 컬럼을 갱신**한다. 완료 판정은 "모든 행이 `MATCH` 또는 `SANCTIONED`".

## 1. 정렬 결정 (Alignment Decision)

- **모드: 엄격 일치 (strict parity, modal 하나로 통합)** — 2026-06-11 사용자 확정.
- Instance summary에서 instance 상세로 가는 진입점은 **단일 wide modal 하나**다 (SoT `openModal`, HTML:2221).
- frontend에만 존재하는 추가 surface(narrow Sheet의 percentile/histogram evidence, stored trend)는
  SoT modal에 없으므로 **instance-summary 흐름에서 진입점을 제거(retire)** 한다. modal 안의 한 섹션으로
  접을 수 있으면 접고, SoT modal에 대응 섹션이 없으면 진입점을 끊는다.
- SoT 문서/완료 story의 **의미는 재정의하지 않는다**. UI는 lifecycle state, endpoint priority, p95/p99,
  instance state, contribution을 **재계산하지 않고 server read model이 준 값을 표시만** 한다.
- **과거(history) 보기 = SoT 방식만** (2026-06-11 D1 확정): 시계열 trend는 MVP에서 안 한다.
  과거 instance evidence는 **Snapshot/History에서 스냅샷을 선택 → snapshot-mode wide modal**로 본다
  (`InstanceDashboardSurface mode="snapshot"`, `dashboard.tsx:617` 배선). 즉 instance summary 행에는
  history 진입 버튼을 두지 않는다. 시계열 stored-projection trend surface는 **retire**.
- **목록 행은 SoT summary를 표시** (2026-06-12 D5 갱신): `InstanceEntry.summary`를 backend 목록 read model에
  호환 확장해 status/heartbeat/requests/slow/contribution을 행에서 표시한다. frontend는 이 값을 재계산하지 않는다.
- **Modal field는 server read model 상한을 넘지 않는다** (2026-06-11 D2/D4 확정): SoT mockup보다 더 적거나
  더 많은 provenance flag가 있더라도 frontend 재계산, N+1 조회, backend/schema 변경 없이 현재
  `InstanceDashboardReadModel`이 제공하는 값만 표시한다.

## 2. 상태 범례 (Status Legend)

| 상태 | 의미 |
|---|---|
| `MATCH` | SoT 요소와 frontend가 구조·순서·의미상 대응함 |
| `DRIFT` | 대응 컴포넌트는 있으나 필드/카피/액션이 다름 → 수정 필요 |
| `MISSING` | SoT에 있는데 frontend에 없음 → 추가 필요 |
| `EXTRA` | frontend에 있는데 SoT에 없음 → (strict parity) 진입점 제거/접기 |
| `SANCTIONED` | SoT 범위 밖이지만 의도적으로 유지하기로 명시 결정된 것 |
| `OPEN` | 의미 충돌이 있어 사람 결정이 필요 |

## 3. SoT 기준 surface 구조 (read-only)

| SoT 영역 | HTML 라인 | 구성 |
|---|---|---|
| Instance Summary 패널 헤더 | 1294–1296, 1299 | "Instance Summary" 라벨 + subtitle + `#instanceList` |
| Instance row 템플릿 (`instanceTemplate`) | 1756–1772 | `id` / `status · heartbeat` / requests·slow 2칸 / contribution 배지 / **단일 "Open modal" 버튼** |
| Wide modal (`openModal`) | 2221–2340+ | 아래 §5 섹션들 |

## 4. 매트릭스 A — Summary Row (목록 행)

기준 frontend: `frontend/src/app/components/dashboard.tsx` `InstancesPanel` (1598–1651, 행 1628–1644).

| # | SoT 요소 (HTML:line) | 요구 동작 | 현재 frontend (file:line) | 상태 | 해결 액션 |
|---|---|---|---|---|---|
| A1 | 패널 헤더 "Instance Summary" + subtitle (1294–1296) | 라벨/subtitle 표시 | `dashboard.tsx:1610-1613` | `MATCH` | — |
| A2 | row: `instance id` 표시 (1759) | instance 식별자 | `dashboard.tsx:1622` `instanceName` | `MATCH` | 목록 표시명은 `InstanceEntry.instanceName`, modal target은 `instanceId`를 함께 유지 |
| A3 | row: `status · heartbeat` (1760) | observationStatus + heartbeat 상대시각 | `dashboard.tsx:1627-1629` `InstanceEntry.summary.observationStatus` + `starterConnection` | `MATCH` | D5 갱신: backend row summary 값을 표시만 함 |
| A4 | row: `requests` 2칸 그리드 (1764) | instance 요청수 | `dashboard.tsx:1632` `summary.red.requestCount` | `MATCH` | frontend 계산 없음 |
| A5 | row: `slow >500ms` 2칸 그리드 (1765) | slow share | `dashboard.tsx:1633` `summary.red.slowShareOver500ms` | `MATCH` | server 산출 nullable ratio만 표시 |
| A6 | row: `contribution` 배지 (1768) | contributing/supporting/attention/insufficient 배지 | `dashboard.tsx:1636-1638` `summary.applicationContribution.level` | `MATCH` | contribution badge는 server code 그대로 표시 |
| A7 | row: **단일 "Open modal" 버튼** (1769) | 액션 1개 → **live** wide modal | `dashboard.tsx:1628-1631` 단일 `Open modal` 버튼 | `MATCH` | "Stored trend" 버튼 제거 완료. 과거는 Snapshot/History → snapshot-mode wide modal |

> 2026-06-12 갱신: A3~A6는 `InstanceEntry.summary` backend read model 확장으로 해소한다.
> backend/schema 변경 없이 기존 `accepted_metric_buckets`, `application_instances`, `starter_heartbeat_telemetry`
> source에서 row summary 값을 산출하며 frontend는 행 단위 재계산이나 per-row dashboard fetch를 하지 않는다.

## 5. 매트릭스 B — Wide Modal (instance 상세)

기준 frontend: `frontend/src/app/components/instance-dashboard-surface.tsx` `InstanceDashboardSurface`
render body (161–176). **섹션 순서는 SoT `openModal`과 이미 일치** — 주 작업은 필드/카피/진입점 정리.

| # | SoT 섹션 (HTML:line) | frontend 컴포넌트 (file:line) | 상태 | 해결 액션 |
|---|---|---|---|---|
| B0 | modal 진입 = 단일 wide modal (2221) | `InstancePanels` Dialog (`instance-panels.tsx:50-68`) → `InstanceDashboardSurface` | `MATCH` | 단일 Dialog. `DialogContent`는 `w-[min(1120px,calc(100vw-2rem))] max-w-none sm:max-w-none` 유지 |
| B1 | context note (live/snapshot copy, 2232–2234) | `InstanceContextNote` (`instance-dashboard-surface.tsx:177-216`) | `MATCH` | live/snapshot 카피, late metric 가능성, stored Application Snapshot override 금지 표시. "Stored trend" 버튼 없음 |
| B2 | Application state reference: applicationStateRef / lifecycleOwner / **contribution** / instance top-level state=없음 (2237–2247) | `ApplicationStateReferencePanel` (`instance-dashboard-surface.tsx:218-238`) | `SANCTIONED` | SoT 핵심 필드(contribution, instance top-level state=없음) 표시. `source`/`snapshotId`는 D2에 따라 server provenance 중복 노출로 허용 |
| B3 | Read semantics: mode / windowSource / source / snapshot row / includesLateAcceptedMetrics / mayDifferFromStoredApplicationSnapshot (2249–2261) | `ReadSemanticsPanel` (`instance-dashboard-surface.tsx:240-262`) | `SANCTIONED` | SoT 의미 필드 포함. `acceptedAtCutoffApplied`, `applicationSnapshotRecalculated`, `instanceEvidenceReconstructedFromMetrics`, `markerIsStateSource`는 D4에 따라 server read-model provenance flag로 허용. SoT 하단 no-override 카피는 B1 context note에 위치 |
| B4 | metric grid: Instance requests / Server errors / Slow>500ms / p95·p99 (2263–2267) | `MetricGrid` (`instance-dashboard-surface.tsx:264-274`) | `SANCTIONED` | requests/errors/slow는 server `signals.red` 표시. `p95/p99`는 현재 `InstanceDashboardReadModel`에 없으므로 frontend 재계산 없이 `observationStatus`를 표시(D4) |
| B5 | Endpoint evidence on selected instance (상위 2개, 2269–2272) | `EndpointEvidencePanel` (`instance-dashboard-surface.tsx:276-328`) | `SANCTIONED` | SoT endpoint evidence 섹션과 카피 대응. production은 read model `items`의 server order/limit을 그대로 표시하고 client slice/sort를 만들지 않음(D4) |
| B6 | Resource evidence (badge + 카피, two-grid 좌, 2273–2280) | `ResourceEvidencePanel` (`instance-dashboard-surface.tsx:330-369`) | `SANCTIONED` | resource 섹션 대응. production은 server `resourceEvidence.items[]`의 usage/threshold/observedAt/request symptom을 표시하고 root cause로 승격하지 않음(D4) |
| B7 | Starter connection (heartbeat badge + 카피, two-grid 우, 2281–2287) | `StarterConnectionPanel` (`instance-dashboard-surface.tsx:371-390`) | `MATCH` | heartbeat/source/freshness/stateImpact와 "heartbeat는 state를 바꾸지 않음" 카피 대응 |
| B8 | Normalized endpoint evidence table (sort controls + source note + table, 2289+) | `NormalizedEndpointEvidenceTable` (`instance-dashboard-surface.tsx:392-442`) | `SANCTIONED` | endpointKey/requestCount/errorCount/errorRate/source note 대응. slowCount/slowShare/duration bucket distribution은 현재 `InstanceDashboardEndpointEvidenceItem`에 없으므로 표시하지 않음(D4). mockup sort/limit controls는 prototype runtime이라 production 요구 아님 |

> B3~B8 필드 감사 완료. strict parity 기준의 차이는 남아 있지만, backend/schema 무변경과 "server read model 값만 표시"
> 원칙 때문에 현재 MVP에서는 D2/D4 `SANCTIONED`로 추적한다.

## 6. 매트릭스 C — Extra Surfaces (strict parity 정리 대상)

| # | frontend surface (file:line) | SoT 대응 | 상태 | 해결 액션 |
|---|---|---|---|---|
| C1 | `InstanceTrendView` narrow Sheet, stored `instanceSummary.items[]` 시계열 projection | SoT modal에 trend 섹션 없음 | `MATCH` | retired 완료. Sheet·`InstanceTrendView`·`openTrend`·`trend` view kind 없음. 과거 보기는 snapshot-mode wide modal(B0 snapshot 경로) |
| C2 | `InstanceEvidenceView` narrow Sheet: AxisGrid/percentile series/histogram/resource hints | SoT modal에 percentile·histogram 섹션 없음 | `MATCH` | 제거 완료. Instance detail은 단일 wide modal만 렌더 |
| C3 | row "Stored trend" 버튼 | SoT row는 단일 버튼 | `MATCH` | 제거 완료(A7) |
| C4 | modal context note "Stored trend" 버튼 | SoT note에 버튼 없음 | `MATCH` | 제거 완료(B1) |

## 7. Decision Register

| # | 충돌 | 선택지 | 영향 |
|---|---|---|---|
| D1 | ~~AC#10 trend vs SoT modal에 trend 없음~~ | **확정 (2026-06-11): (a) 시계열 trend retire, AC#10 MVP 범위 밖.** 과거는 Snapshot/History → snapshot-mode wide modal. | `RESOLVED` → C1/A7/B1 액션 확정됨 |
| D2 | B2에서 frontend가 SoT에 없는 `source`/`snapshotId`를 추가 노출 | **확정 (2026-06-11): `SANCTIONED`.** 새 surface/계산이 아니라 server provenance 중복 노출이다. `source`는 B3 read semantics에도 있고, `snapshotId`는 snapshot mode provenance 확인용이다. | B2를 `SANCTIONED`로 고정. 후속 strict HTML-only pass가 필요하면 축소 후보로 남기되 MVP blocker는 아님 |
| D3 | SoT row는 status/heartbeat/requests/slow/contribution을 표시하지만 `InstanceEntry` 목록 read model에는 없음 | **대체됨 (2026-06-12 D5).** 2026-06-11에는 row thin을 `SANCTIONED`했으나, 후속 목표에서 backend row summary 계약 보강으로 변경했다. | historical decision. 현재 완료 판정에는 D5를 따른다 |
| D4 | SoT modal demo field와 production `InstanceDashboardReadModel` field가 일부 다름(p95/p99, endpoint slow/duration bucket, 추가 read semantics flags 등) | **확정 (2026-06-11): `SANCTIONED`.** production은 현재 server read model 값만 표시한다. SoT에 있어도 DTO에 없는 값을 frontend에서 만들지 않고, DTO의 provenance/guard flag는 modal 안에서 허용한다. | B3/B4/B5/B6/B8를 `SANCTIONED`으로 고정. backend/schema 변경 금지 |
| D5 | D3의 row thin 허용과 SoT row parity 목표가 충돌 | **확정 (2026-06-12): backend `InstanceEntry.summary`를 호환 확장한다.** schema/migration 없이 기존 source data에서 observation/heartbeat/request/slow/contribution scalar를 산출하고 frontend는 표시만 한다. | A3~A6를 `MATCH`로 갱신. trend/narrow sheet 금지와 wide modal 계약은 유지 |

## 8. 정렬 결과 (Alignment Result)

1. **D1/D2/D4/D5 확정** (§7) — 시계열 trend retire, B2 provenance 허용, row summary backend 확장, modal field는 server read model 상한으로 제한.
2. **Summary row** — status/heartbeat/requests/slow/contribution과 단일 "Open modal" 버튼을 `InstanceEntry.summary` 기반으로 표시한다.
3. **Instance detail surface** — 단일 wide Dialog만 유지. narrow Sheet, `InstanceTrendView`, `openTrend`, `openLiveDashboard`, `snapshotTrend` surface 없음.
4. **Modal section order** — SoT `openModal` 순서와 대응. B3~B8의 field-level 차이는 D4로 추적.
5. **Guard/QA** — `guard:read-model-contract`, `typecheck`, live DOM/Playwright wide modal 검증 통과. `sm:max-w-none`은 shadcn `sm:max-w-lg` 회귀 방지 필수 클래스다.
6. **문서 정렬** — 13.UI/Epic 14/QA 문서에서 Stored trend, projection trend, narrow Sheet, AC#10 MVP 필수 표현을 제거하거나 MVP 밖으로 이관한다.

## 9. 추적 운영 규칙 (codex가 매번 틀리지 않게)

- 구현자는 작업 전 이 문서를 읽고, **해결한 행의 상태 컬럼을 PR/커밋에서 함께 갱신**한다.
- 새 surface/버튼/섹션을 추가하면 SoT 대응이 없는 한 `EXTRA`이며, `SANCTIONED`로 승격하려면 §7에
  결정 행을 추가해 근거를 남긴다 (임의 추가 금지).
- 완료 정의(Definition of Done): §4·§5·§6의 모든 행이 `MATCH` 또는 `SANCTIONED`, §7에 `OPEN` 없음.
- 이 문서는 SoT가 아니다. SoT(HTML/`*-source-of-truth.md`)와 충돌하면 SoT가 우선이고 이 문서를 고친다.

## 10. 구현 로그 (2026-06-11, frontend-only, 백엔드 무변경)

D1 확정(시계열 trend retire, 과거는 snapshot-mode modal) 기준으로 frontend 정렬 1차 구현. `npm run typecheck`,
`npm run guard:read-model-contract` 모두 통과.

### 변경 파일
- `frontend/src/app/components/instance-panels.tsx` — **재작성**. Sheet/`InstanceTrendView`/`InstanceEvidenceView`/`openTrend`/`trend` view kind/`openLiveDashboard` 제거. 단일 wide modal(Dialog)만 남김. `useInstanceView`는 `openEvidence`(live)·`openSnapshotDashboard`·`close`만 노출.
- `frontend/src/app/components/instance-dashboard-surface.tsx` — context note의 "Stored trend" 버튼·`onOpenTrend` prop 제거(B1). `ApplicationStateReferencePanel`에 `contribution` 셀 추가(B2). 미사용 `History` import 제거.
- `frontend/src/app/components/dashboard.tsx` — `InstancesPanel` 행을 단일 "Open modal" 버튼으로(A7). `onOpenTrend` 배선 전부 제거(DashboardMain/InstancePanels/InstancesPanel).
- `frontend/scripts/read-model-contract-guard.ts` — stale했던 trend-projection copy 단언을 **trend surface 부재 회귀 가드**(`/InstanceTrendView|Stored trend|SheetContent|snapshotTrend/` == false)로 교체. trend **read model** 계약(types/adapters/guard/fixtures)은 백엔드 contract라 그대로 유지.
- `frontend/src/app/components/instance-panels.tsx` (modal width 버그 수정) — `DialogContent`에 **`sm:max-w-none` 추가**. **이유(gotcha): 절대 지우지 말 것.** shadcn 베이스 `DialogContent`(`ui/dialog.tsx`)에 `sm:max-w-lg`(512px)가 있어서, `max-w-none`(base variant)만으로는 데스크톱(sm+)에서 안 덮어써져 wide modal이 512px로 캡되고 2단 그리드가 좌우로 잘렸음. 라이브 측정 확진: maxWidth 512px·scrollWidth 830px(오버플로). `sm:max-w-none`이 그 캡을 풀어 `w-[min(1120px,calc(100vw-2rem))]`=1120px 적용 → AC#12(visual QA) 충족.

### 행 상태 갱신 (이전 → 현재)
| 행 | 이전 | 현재 | 비고 |
|---|---|---|---|
| A2 id/name | MATCH | `MATCH` | instanceName 표시 |
| A3 status·heartbeat | DRIFT | **`SANCTIONED`** | `InstanceEntry`(목록 read model)에 미포함 → D3. 행은 얇게 유지 |
| A4 requests | MISSING | **`SANCTIONED`** | D3. modal `signals.red`에서만 표시 |
| A5 slow>500ms | MISSING | **`SANCTIONED`** | D3. modal `signals.red`에서만 표시 |
| A6 contribution(행) | DRIFT | **`SANCTIONED`** | D3. modal `applicationContribution`에서만 표시 |
| A7 단일 버튼 | DRIFT | `MATCH` | "Open modal" 단일 버튼 |
| B0 단일 modal | MATCH | `MATCH` | — |
| B1 context note | DRIFT | `MATCH` | "Stored trend" 버튼 제거. live/snapshot 카피 유지 |
| B2 app state ref | DRIFT | **`SANCTIONED`** | contribution 추가 완료. source/snapshotId는 D2로 허용 |
| B3 read semantics | DRIFT? | **`SANCTIONED`** | SoT 의미 필드 + 추가 server provenance flags(D4) |
| B4 metric grid | DRIFT? | **`SANCTIONED`** | p95/p99 DTO 부재. requests/errors/slow + observationStatus 표시(D4) |
| B5 endpoint evidence | DRIFT? | **`SANCTIONED`** | server order/limit 표시(D4) |
| B6 resource evidence | DRIFT? | **`SANCTIONED`** | server resource evidence 상세 표시(D4) |
| B7 starter connection | DRIFT? | `MATCH` | heartbeat/stateImpact copy 대응 |
| B8 normalized endpoint table | DRIFT? | **`SANCTIONED`** | slow/duration bucket DTO 부재. 현재 endpoint fields만 표시(D4) |
| C1 trend Sheet | OPEN | `MATCH` | 제거 완료, guard로 회귀 잠금 |
| C2 evidence Sheet | EXTRA | `MATCH` | 고아 코드 제거 |
| C3 행 trend 버튼 | EXTRA | `MATCH` | — |
| C4 modal trend 버튼 | EXTRA | `MATCH` | — |

### 남은 작업
- OPEN/BLOCKED-BACKEND 없음. 남은 차이는 D2/D3/D4에 따라 `SANCTIONED`로 추적한다.
- 후속에 SoT HTML field-level strict parity를 다시 요구하면 별도 backend/read model story가 필요하다. 이 MVP 문서 정렬에서는 backend/schema 변경을 금지한다.
- Visual QA 결과: Open modal 1개, Stored trend 없음, wide modal 섹션 존재, width 1120px 적용 확인.

## 10.1 문서 정렬 로그 (2026-06-11, docs-only)

- traceability A3~A6를 `BLOCKED-BACKEND`에서 `SANCTIONED`으로 갱신했다(D3: row thin 유지).
- B3~B8 field-level 감사를 완료하고 `MATCH`/`SANCTIONED`으로 확정했다.
- D2(source/snapshotId provenance)와 D4(modal field/read-model 상한)를 decision register에 추가했다.
- 13.UI/Epic 14/QA 문서의 Stored trend, projection trend, InstanceTrendView, narrow Sheet, AC#10 MVP 필수 표현을 현재 MVP 계약에 맞게 정리했다.
- 사용자 확인 후 SoT Markdown 5종(`current-product`, `application-dashboard-read-model`, `instance-dashboard-read-model`, `dashboard-snapshot`, `dashboard-snapshot-retention-cleanup`)에도 동일한 경계를 반영했다. Trend read-model/source 설명은 보존하되, MVP UI에는 Stored/projection trend surface가 없고 과거 instance evidence는 Snapshot/History -> snapshot-mode wide modal로 본다고 명시했다.

## 10.2 구현 로그 (2026-06-12, backend row summary 계약 보강)

- 새 계획 문서 `implementation-artifacts/epic-13-instance-summary-sot-api-plan.md`를 추가했다.
- D3 row thin 결정을 D5로 대체했다. `ApplicationDashboardReadModel.InstanceEntry.summary`가
  observation/heartbeat/request/slow/contribution scalar를 제공하고, frontend는 값을 표시만 한다.
- snapshot capture 경로에서는 selected snapshot window와 `snapshotCutoffAt` 기준으로 instance row summary를 산출한다.
  Snapshot/History에서 여는 snapshot-mode wide modal은 계속 selected snapshot row의 window 기준으로 동작한다.
- schema/migration은 변경하지 않았다.

## 11. Resolved Read Model Decisions

| # | 충돌 | 선택지 | 영향 |
|---|---|---|---|
| D3 | SoT row는 status·heartbeat·requests·slow·contribution을 행에 표시하지만, 이전 `InstanceEntry` 목록 read model에는 `instanceId/instanceName/lastSeenAt/links.evidence`만 있었다. | **대체됨: D5가 현재 계약이다.** `InstanceEntry.summary`를 backend read model에 추가해 A3~A6를 row에서 표시한다. | frontend 계산 금지와 schema/migration 무변경 원칙은 유지 |
| D4 | SoT modal은 p95/p99와 endpoint slow/duration bucket을 보여주지만 현재 `InstanceDashboardReadModel`은 `signals.red`와 bounded endpoint evidence만 제공한다. 반대로 production DTO에는 SoT보다 많은 read semantics provenance flag가 있다. | **확정: current DTO 상한 유지**. DTO에 없는 p95/p99·duration bucket은 frontend에서 계산하지 않고, DTO의 provenance flags는 modal 안에서 허용한다. | B3/B4/B5/B6/B8 최종 상태는 `SANCTIONED`. backend/schema 변경 없음 |

## 12. Snapshot mode = live surface 재사용 (2026-06-12)

### 결정
Snapshot/History에서 slot을 클릭하면 별도 detail 화면으로 redirect하지 않고, **live dashboard와 동일한 컴포넌트 트리**로
같은 자리에서 mode=snapshot 전환한 뒤 상단으로 scroll한다(SoT "snapshot-mode wide modal / B0 snapshot 경로"와 정렬).

### 배경
- `DashboardSnapshotWriterService`/`DashboardSnapshotReadModelEnricher`는 capture 시 **full** `ApplicationDashboardReadModel`을
  `read_model_json`으로 저장한다(`objectMapper.valueToTree(readModel)` 후 `mode=snapshot`, `readSemantics=snapshot()`만 덮어쓰고
  `snapshotEndpointEvidence`·`instanceSummary` block 추가). 즉 instances·histogramDistribution 포함 전체가 보존된다.
- 기존 `/dashboard/snapshots/{id}` detail endpoint는 **bounded projection**만 노출했다(`application:{name}`만, endpointPriority/
  sourceScopedPercentiles null). 이 projection만으로는 live 컴포넌트(`toDashboardPresentation`, GoldenSignals/Endpoint/Resource/
  Instances)를 렌더할 수 없다.

### 변경
- **backend**: `GET /dashboard/snapshots/{id}/read-model` 추가. `DashboardSnapshotDetailService.getStoredReadModelJson`이
  detail row의 retention/path 정합성을 `getDetail`과 동일하게 검증한 뒤 stored `read_model_json` **문자열을 그대로**
  `application/json`으로 내려준다. capture 시 enricher가 이미 mode=snapshot·readSemantics=snapshot으로 full read model을
  저장하므로 추가 가공이 없다. bounded detail endpoint는 provenance/marker용으로 그대로 유지한다.
  - **gotcha(절대 record로 역직렬화하지 말 것)**: 초기엔 `objectMapper.readValue(json, ApplicationDashboardReadModel.class)`로
    역직렬화 후 재직렬화를 시도했으나, `ApplicationDashboardReadModel`이 **다중 생성자**를 가져 Jackson record creator가
    모호해지며 `state` 등 일부 필드가 null로 복원됐다(재직렬화 시 `state:null` → frontend guard 실패). live endpoint는
    serialize만 하므로 안 드러났던 문제. raw passthrough가 충실하고 안전하다.
- **frontend**: `dashboard.tsx`의 snapshot 분기를 `SnapshotModeSurface`로 교체. live와 공유하는 `DashboardPanels`에
  snapshot presentation을 주입하고, 상단 `SnapshotModeBanner`(provenance + "라이브로 돌아가기")와 scroll-to-top을 둔다.
  fetch는 `guardApplicationDashboardReadModel(..., { expectedMode: "snapshot" })` → `toDashboardPresentation`으로 live와
  동일 경로를 탄다. 기존 `SnapshotDetailSurface` 커스텀 레이아웃은 main flow에서 retire(파일/타입은 유지).
- **contract guard**: `guardApplicationDashboardReadModel`에 `expectedMode: "live" | "snapshot"`를 추가. snapshot은 동일한 구조
  검증을 하되 `mode=snapshot`·`readSemantics.source=dashboard_snapshots.read_model_json`만 다르게 허용한다.

### 과거 snapshot 스키마 drift 허용 (D6)
- stored snapshot은 **immutable historical artifact**라 capture 당시 schema를 따른다. D5에서 추가된 `InstanceEntry.summary`
  이전에 저장된 snapshot은 `instances[]` 행에 `summary`가 없다. 현재 live contract를 그대로 강제하면 옛 snapshot 복원이 통째로
  실패한다.
- **결정**: snapshot mode에서 `instances[]` 행에 `summary`가 없으면 core fields(instanceId/instanceName/lastSeenAt/links.evidence)만
  검증하고 summary asserts는 건너뛴다(`assertDashboardInstanceEntry(item, "snapshot")`). live mode는 기존대로 strict 유지.
  `InstancesPanel`은 summary가 없으면 "이 시점 snapshot에는 instance summary가 저장되지 않았습니다"로 defensive 렌더한다.
- backfill/recalculation은 하지 않는다(저장본 부재를 새로 판정하지 않는 원칙 유지).

### read semantics
- snapshot mode도 현재 metric으로 재계산하지 않는다. 저장된 read model을 표시만 하며 `mode` 배지/`Application Dashboard / Snapshot`
  라벨로 live와 구분한다.
