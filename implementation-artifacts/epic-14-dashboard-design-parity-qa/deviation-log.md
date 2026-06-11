# Epic 14 Deviation Log

Mockup과 실제 Vite Dashboard가 다른 모든 구조, 밀도, color/visual grammar, ordering, modal/surface form, Snapshot/History interaction 차이는 이 파일에 기록한다. "더 나은 디자인" 또는 "더 현대적인 디자인"은 허용 사유가 아니다.

## Allowed Categories

| Category | 허용 사유 |
|---|---|
| 1 | Production data/read model 때문에 mockup demo data를 그대로 쓸 수 없는 경우 |
| 2 | Responsive/mobile에서 물리적으로 동일 배치가 불가능한 경우 |
| 3 | Prototype controls, hard-coded JS demo data, temporary mockup runtime처럼 production에 넣으면 안 되는 목업 전용 요소 |
| 4 | 접근성/키보드/focus/ARIA를 위해 시각 구조를 해치지 않는 보강 |

위 1~4에 속하지 않는 차이는 blocker다.

## Current Deviation Status

14.1에서는 production UI를 수정하지 않았고 authenticated dashboard DOM을 열 수 없어 실제 conformance deviation을 승인하지 않았다. 현재 남은 것은 evidence gap이며, 아래 "Known Evidence Gap"에 따로 둔다.

14.2에서는 `DashboardMain`의 tab split을 해소해 live surface와 Snapshot/History handoff를 같은 main flow 안에 두었다. 이 변경으로 tab split deviation은 별도 승인 없이 제거됐으며, 새 allowed deviation은 추가하지 않았다.

## Known Evidence Gap

| Gap | Evidence | Disposition |
|---|---|---|
| Full authenticated browser path 없음 | `/dashboard`는 desktop/tablet/mobile에서 HTTP 200이지만 token fixture가 없어 Project rail auth error까지만 렌더링됨 | Deviation이 아니라 QA coverage gap. 후속 fixture/runbook으로 닫아야 하며 "authenticated dashboard conformant"라고 쓰지 않는다 |
| Mobile auth-blocked baseline horizontal overflow | 14.1 `browser-baseline-observations.json`에서 mobile `bodyWidth=504`, `viewportWidth=390` | 14.2에서 global nav wrapping을 조정한 뒤 `browser-14-2-dashboard-shell-rails-and-live-surface-realignment-observations.json` 기준 mobile `bodyScrollWidth=390`, `viewportWidth=390`으로 auth-blocked overflow는 해소됐다. Authenticated dashboard/mobile conformance는 여전히 fixture 부재로 미검증이다 |

## Deviation Entry Template

| ID | Mockup element | Production element | Reason | Allowed category | Reviewer decision | Follow-up owner |
|---|---|---|---|---|---|---|
| D-YYYYMMDD-001 | HTML mockup 기준 element/section/interaction. 가능하면 class/id/visible label 포함 | 실제 Vite Dashboard component/surface | 동일하게 이식할 수 없거나 이식하지 않아야 하는 구체적 이유 | 1/2/3/4 또는 `blocker` | Approved / rejected / needs follow-up + reviewer note | 14.2 / 14.3 / 14.4 / named owner |

## Responsive Deviation Template

| ID | Viewport | Mockup element | Physical constraint | Chosen adaptation | Allowed category | Reviewer decision | Follow-up owner |
|---|---|---|---|---|---|---|---|
| R-YYYYMMDD-001 | `390x844` | 예: `app-grid` 3-column | 390px width에서 3-column rail/main 동시 배치 불가 | Rail/main을 순서 보존 stack으로 전환 | 2 | Approved / rejected / needs follow-up | 14.2 |

## Blocker Examples

| Mockup target | Blocker example |
|---|---|
| `app-grid` 3-column hierarchy | Desktop에서 Project rail 또는 Application rail을 숨기고 card carousel로 대체 |
| `rail-item` compact row | Rail row를 큰 marketing card로 재설계 |
| `section-label` small uppercase grammar | Hero-scale heading과 decorative illustration으로 대체 |
| Snapshot/History marker-first picker | Operational event feed 또는 raw snapshot list를 primary surface로 승격 |
| Snapshot detail stored source | Current dashboard/current accepted bucket으로 fallback |
| Instance wide modal | Live/snapshot detail을 narrow right Sheet에 유지 |
| Retention expired/source absence | "현재 dashboard 보기" CTA로 복원 유도 |
