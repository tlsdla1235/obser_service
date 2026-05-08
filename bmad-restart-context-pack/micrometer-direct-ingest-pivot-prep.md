# Micrometer Direct Ingest Pivot Prep

## Purpose

이 문서는 기존 `Micrometer + Prometheus scrape/query` MVP 방향에서
`Micrometer + starter direct ingest` 방향으로 갈아엎기 전에,
이번 피벗의 고정 가정과 문서 영향 범위를 짧게 묶어두기 위한 준비 메모다.

아직 PRD / architecture / epics / contracts 본문을 전면 수정한 상태는 아니며,
이 문서는 "무엇을 어떻게 뒤집을지"를 먼저 잠그는 체크포인트다.

기존 Prometheus 중심 planning / contract 산출물은
`{output_folder}/planning-artifacts/legacy-archive/2026-05-06-prometheus-pivot/`
로 이동되었으며, 현재는 archive reference로만 취급한다.

## Proposed MVP Contract

- 애플리케이션 계측 계층은 `Micrometer`로 유지한다.
- `Prometheus`는 MVP 필수 의존성에서 제거한다.
- starter는 앱 내부에서 low-cardinality metric을 수집한다.
- starter는 요약 metric과 histogram bucket을 비동기로 우리 ingest API에 전송한다.
- 서버는 histogram bucket을 합산해서 멀티 인스턴스 기준 `p95`를 계산한다.
- 사용자는 내부 배치, 재시도, 큐, bucket merge 방식을 알 필요가 없다.
- 사용자 계약은 `starter 추가 + 최소 설정 + 앱 실행`으로 닫는다.

## Deliberate Constraints

- MVP는 범용 metrics platform이 아니다.
- raw unrestricted timeseries query는 범위 밖이다.
- high-cardinality tag는 허용하지 않는다.
- portal 장애가 애플리케이션 request path를 막으면 안 된다.
- push 실패 시 비동기 drop / backoff / retry 정책은 starter 내부 책임으로 둔다.
- 멀티 인스턴스 정확도는 `starter-local p95`보다 `server-side histogram merge`를 우선한다.

## Documents To Rewrite

### Core planning artifacts

- `prd.md`
  - Prometheus scrape/query 전제를 제거한다.
  - onboarding을 `starter install -> outbound ingest` 기준으로 다시 쓴다.
  - 사용자의 필수 준비물을 `Prometheus reachable endpoint`에서 `service key / outbound HTTPS` 쪽으로 바꾼다.
- `architecture.md`
  - Prometheus query lane, selector bootstrap, scrape validation 설명을 제거하거나 축소한다.
  - ingest API, payload validation, async processing, histogram merge, snapshot read model을 중심 구조로 다시 쓴다.
  - freshness / stale / recovery를 scrape timestamp가 아니라 ingest freshness 기준으로 다시 정의한다.
- `epics.md`
  - Epic 구조 freeze는 유지한다.
  - Story contract를 `Prometheus source of truth`에서 `direct ingest source of truth`로 재정렬한다.
  - Epic 3 state semantics, Epic 4 summary contract, Epic 5 endpoint comparison contract가 ingest payload 기준으로 닫히는지 다시 확인한다.
- `validation-report-2026-05-05.md`
  - 기존 검증 전제가 Prometheus 중심이면 전면 재검토가 필요하다.

### Contract documents

- `docs/contracts/ingest-envelope.md`
  - `v0.6 legacy` 표기를 제거하고, 새 MVP ingest source of truth 후보로 승격 검토한다.
  - payload shape를 summary + histogram bucket 기준으로 정제한다.
- `docs/contracts/prometheus-query-profile.md`
  - MVP 주 계약에서 제외하거나 historical note로 내린다.
- `docs/contracts/staleness-semantics.md`
  - scrape freshness 기준을 ingest freshness / processing freshness 기준으로 재작성한다.
- `docs/contracts/dashboard-read-model.md`
  - query-from-Prometheus read model이 아니라 snapshot/summary read model로 바꾼다.
- `docs/contracts/time-buckets.md`
  - starter push cadence와 server aggregation window 기준으로 다시 검토한다.
- `docs/contracts/metric-taxonomy.md`
  - low-cardinality direct-ingest 허용 metric만 남기도록 정리한다.
- `docs/contracts/insight-rules.md`
  - summary / endpoint comparison이 direct-ingest inputs만 사용하도록 다시 묶는다.

### Historical or research docs

- `observability_toy_spec_v0.7.md`
  - 현재 spec은 Prometheus 구조를 중심축으로 삼고 있어, historical reference로 돌리거나 새 버전으로 교체해야 한다.
- `planning-artifacts/research/technical-observability-collection-decision-research-2026-05-05.md`
  - 기존 결론이 Prometheus 활용형에 기울어 있으므로, 더 이상 active decision note로 두기 어렵다.

### Legacy references to keep active

- `legacy-archive/2026-05-06-prometheus-pivot/planning-artifacts/ux-design-directions.html`
  - direct ingest 전환 이후에도 여전히 유효한 정보 위계 / 레이아웃 reference다
  - 추천 방향은 `Signal Strip + Split Desk + Guided Recovery` 하이브리드다
- `legacy-archive/2026-05-06-prometheus-pivot/planning-artifacts/ux-color-themes.html`
  - direct ingest 전환 이후에도 여전히 유효한 visual tone reference다
  - 추천 테마는 `Calm Ops Desk`다

이 두 HTML은 archive에 있지만 stale artifact가 아니라,
향후 BMAD planning 문서 작성에서 계속 넣어야 하는 supporting reference로 취급한다.

## First Rewrite Sequence

1. `ADR` 1장으로 이번 피벗을 확정한다.
2. `PRD`를 새 사용자 계약 기준으로 다시 쓴다.
3. `architecture.md`를 ingest-first 구조로 다시 쓴다.
4. contracts를 `ingest-envelope` 중심으로 재편한다.
5. 그다음 `epics.md` foundation story를 새 계약에 맞게 재작성한다.
6. 마지막으로 validation 문서를 다시 만든다.

## Future BMAD Context Pack

향후 fresh context에서 아래 문서는 우선 참조 pack으로 함께 넣는다.

- `observability_toy_spec_v0.8.md`
- `planning-artifacts/micrometer-direct-ingest-pivot-prep.md`
- `legacy-archive/2026-05-06-prometheus-pivot/planning-artifacts/ux-design-directions.html`
- `legacy-archive/2026-05-06-prometheus-pivot/planning-artifacts/ux-color-themes.html`

특히 `bmad-create-prd`, `bmad-create-ux-design`, `bmad-create-architecture` 단계에서는
위 HTML 두 개를 "historical debris"가 아니라 "active visual reference"로 명시한다.

## Open Decisions To Lock Early

- ingest payload에 histogram bucket을 어디까지 포함할지
- push cadence를 `30초`, `60초` 중 어디로 고정할지
- starter가 endpoint top-N 후보를 같이 보내는지, 서버가 전부 계산하는지
- application identity / environment identity를 어떤 최소 설정으로 받을지
- portal 저장소를 snapshot 중심으로 둘지, short retention aggregated series를 둘지

## Non-Goals For This Prep Step

- 아직 기존 planning 문서를 삭제하지 않는다.
- 아직 epics 구조를 흔들지 않는다.
- 아직 post-MVP 확장 경로를 섞지 않는다.
- 아직 Prometheus integration future path를 완전히 부정하지 않는다.
