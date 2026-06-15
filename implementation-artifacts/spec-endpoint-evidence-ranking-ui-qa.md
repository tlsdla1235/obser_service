---
title: 'Endpoint Evidence Ranking UI QA'
type: 'feature'
created: '2026-06-13'
status: 'done'
baseline_commit: '1938f5a5c6798e2bf64d98a4e4f0a99216b2f25d'
context:
  - '{project-root}/implementation-artifacts/qa-endpoint-evidence-ranking-ui.md'
  - '{project-root}/_bmad/custom/project-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** RED error/slow signal이 존재해도 endpoint evidence 영역이 단순히 "없음"으로 보이거나 단일 카드처럼 보여 운영자가 어떤 endpoint부터 비교해야 하는지 판단하기 어렵다. 기존 sampler/route bug fix는 완료된 상태로 유지하고, 이번 작업은 endpoint evidence UI/UX QA 개선만 독립 후속 작업으로 다룬다.

**Approach:** frontend가 이미 받은 read model field만 사용해 application endpoint priority, live/snapshot instance endpoint evidence, stored snapshot endpoint evidence를 랭킹 표 형태로 읽기 쉽게 표시한다. backend/read model이 제공하지 않는 `errorCount`, `slowCount >500ms`, `slowShare >500ms`는 프론트에서 가짜로 만들지 않고 빈 상태/후속 작업으로 명확히 분리한다.

## Boundaries & Constraints

**Always:**
- `accepted_metric_buckets.endpoints_json` 원천 저장 shape는 `method`, `route`, `requestCount`, `errorCount`, `durationBuckets`이며 raw path/query/per-request sample은 표시하지 않는다.
- live/snapshot Instance Dashboard는 같은 `InstanceDashboardReadModel.endpointEvidence`와 `InstanceDashboardSurface` 구조를 사용한다.
- Snapshot detail은 `DashboardSnapshotDetailReadModel.snapshotEndpointEvidence`를 사용하며, 저장 projection에는 현재 `errorCount`와 slow count/share가 없다.
- UI sort는 가능한 field만 대상으로 한다: `requestCount desc`, `errorRate desc`, slow sort는 server-provided `slowShare`가 있는 application endpoint priority에서만 활성화한다.
- duration bucket distribution은 bucket 배열이 read model에 있을 때만 표시하고, p95/p99나 원인 판단을 계산하지 않는다.
- `.codex-run/` 같은 로컬 실행 로그는 수정하거나 커밋 대상으로 삼지 않는다.

**Ask First:**
- backend DTO, snapshot stored evidence, contract guard를 확장해 `errorCount` 또는 `slowCount >500ms`를 새 public field로 추가해야 할 때.
- application endpoint priority cap을 5에서 10/20으로 늘리는 backend/read model 변경이 필요할 때.
- endpoint evidence를 root cause, action priority, raw explorer, endpoint timeseries처럼 보이게 하는 UX 변경이 필요할 때.

**Never:**
- 기존 sampler/resource fix, route normalization/UNKNOWN fix, 빈 modal bug fix를 되돌리지 않는다.
- `implementation-artifacts/plan-instance-evidence-empty-modal.md`를 이번 작업의 주 기준으로 삼지 않는다.
- frontend에서 cumulative duration bucket을 이용해 `slowCount`를 새 field처럼 계산하거나, snapshot에 없는 `errorCount`를 복원하지 않는다.
- endpoint p95/p99, baseline diff, long-window trend를 새로 만들지 않는다.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Application endpoint evidence available | `endpointPriority[]` has request/error/rate/duration buckets | Endpoint evidence table shows rank, route, requestCount, errorCount, errorRate, slowShare when provided, duration buckets, restrained warning accents | Missing duration buckets show unavailable copy, not inferred values |
| Instance endpoint evidence available | `InstanceDashboardReadModel.endpointEvidence.items[]` has request/error/rate only | Live and snapshot instance dashboards render the same table structure and disclose that slow/bucket item evidence is not provided | Empty items use reason/status/source to distinguish missing/malformed/insufficient |
| Snapshot endpoint evidence available | `snapshotEndpointEvidence.items[]` has requestCount/errorRate/durationBuckets but no errorCount | Snapshot detail renders stored ranking evidence and leaves unavailable columns explicit | No synthetic errorCount/slowCount is displayed |
| RED errors but no endpoint items | RED signal has errorCount or errorRate, endpoint evidence block is empty | Empty state explains whether endpoint breakdown is missing, unavailable, malformed, or filtered by read model status as far as supplied fields allow | Do not claim "no endpoint affected" |

</frozen-after-approval>

## Code Map

- `observability-portal/src/main/resources/db/migration/V003__create_accepted_metric_buckets.sql` -- `endpoints_json` column stores bounded endpoint method/route/count/histogram JSON.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/model/IngestEnvelopeRequest.java` -- ingest contract accepts endpoint `requestCount`, `errorCount`, `durationBuckets`.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/EndpointPriorityService.java` -- application endpoint priority computes server rank, `errorCount`, `errorRate`, `slowShare`, duration bucket evidence with 5-item cap.
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceDashboardReadModelService.java` -- live/snapshot instance dashboard builds shared endpoint evidence with request/error/rate only and 5-item cap.
- `observability-portal/src/main/java/com/observation/portal/domain/snapshot/service/DashboardSnapshotReadModelEnricher.java` -- snapshot stores up to 10 endpoint evidence items but intentionally omits `errorCount`.
- `frontend/src/app/lib/read-model-types.ts` -- frontend DTO types show which fields can be displayed without invention.
- `frontend/src/app/components/dashboard.tsx` -- application dashboard endpoint priority panel currently renders card/list rows.
- `frontend/src/app/components/instance-dashboard-surface.tsx` -- live/snapshot instance endpoint evidence UI currently uses a simple list.
- `frontend/src/app/components/snapshot-detail-surface.tsx` -- stored snapshot endpoint evidence UI currently uses a simple list.
- `frontend/src/app/components/snapshot-history-panel.tsx` -- date map contains the low-value `전체 선택` button.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/app/components/dashboard.tsx` -- replace endpoint priority card list with compact ranking table, sort/limit controls, duration bucket display, and RED-aware empty copy.
- [x] `frontend/src/app/components/instance-dashboard-surface.tsx` -- render live/snapshot instance endpoint evidence through the same ranking-table visual pattern while preserving server order/status and unavailable slow/bucket fields.
- [x] `frontend/src/app/components/snapshot-detail-surface.tsx` -- render stored snapshot endpoint evidence with the same table pattern, showing requestCount/errorRate/duration buckets and explicit unavailable fields.
- [x] `frontend/src/app/components/snapshot-history-panel.tsx` -- remove the date map `전체 선택` button and unused props/state path if no broader selection workflow depends on it.
- [x] `implementation-artifacts/qa-endpoint-evidence-ranking-ui.md` -- append a short follow-up note for read model extensions that were intentionally not implemented.

**Acceptance Criteria:**
- Given application `endpointPriority[]` contains multiple endpoints, when the dashboard renders, then the operator can sort by request count, error rate, and server-provided slow share without frontend-invented values.
- Given instance live or snapshot endpoint evidence is available, when either mode renders, then both use the same visual table structure and preserve source/status/reason semantics.
- Given snapshot detail endpoint evidence lacks `errorCount` or slow count/share, when the table renders, then those values are marked unavailable rather than calculated.
- Given RED errors exist but endpoint items are empty, when the evidence panel renders, then copy distinguishes unavailable/missing/insufficient evidence from "no problem".
- Given the snapshot history date map renders, when the header is shown, then no `전체 선택` bulk button appears.

## Design Notes

Use restrained neutral table styling with small red accents for `errorCount > 0` or non-zero/high `errorRate`, and amber/brown accents for server-provided slow share. Keep cards out of cards: tables can live inside the existing bordered panel, but repeated endpoint rows should not become nested cards. Top 20 cannot be guaranteed until backend caps expand; UI may offer 10/20 limits but must show only received items and disclose API cap when applicable.

## Verification

**Commands:**
- `npm --prefix frontend run typecheck` -- expected: TypeScript build completes.
- `npm --prefix frontend run guard:read-model-contract` -- expected: read model guard still passes.
- If local portal is available, open `http://localhost:8080` and verify application dashboard, live/snapshot instance dashboard, snapshot detail, and snapshot history date map visually.

## Suggested Review Order

**Endpoint Ranking UI**

- Application endpoint evidence entry point, sort/limit, RED-aware empty copy.
  [`dashboard.tsx:1221`](../frontend/src/app/components/dashboard.tsx#L1221)

- Application row visual language: rank, route, badge, request/error/slow bars.
  [`dashboard.tsx:1299`](../frontend/src/app/components/dashboard.tsx#L1299)

- Display sorting stays inside received endpointPriority items.
  [`dashboard.tsx:1428`](../frontend/src/app/components/dashboard.tsx#L1428)

**Instance And Snapshot Surfaces**

- Instance live/snapshot endpoint rows preserve server order and unavailable slow/bucket fields.
  [`instance-dashboard-surface.tsx:275`](../frontend/src/app/components/instance-dashboard-surface.tsx#L275)

- Snapshot detail renders stored endpoint evidence without inventing errorCount or slowShare.
  [`snapshot-detail-surface.tsx:332`](../frontend/src/app/components/snapshot-detail-surface.tsx#L332)

- Snapshot history date map removes the bulk select path.
  [`snapshot-history-panel.tsx:79`](../frontend/src/app/components/snapshot-history-panel.tsx#L79)

**Guards And Follow-Up**

- Contract guard allows dashboard display sorting while preserving source-order guarantees elsewhere.
  [`read-model-contract-guard.ts:709`](../frontend/scripts/read-model-contract-guard.ts#L709)

- Read model extension gaps are documented as follow-up work.
  [`qa-endpoint-evidence-ranking-ui.md:70`](qa-endpoint-evidence-ranking-ui.md#L70)
