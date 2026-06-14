---
artifactType: implementation-orchestration-plan
projectName: Observation Portal
created: 2026-06-14
status: implemented
related:
  - planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html
  - planning-artifacts/source-of-truth/current-product-source-of-truth.md
  - planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md
  - implementation-artifacts/qa-endpoint-evidence-ranking-ui.md
  - implementation-artifacts/spec-endpoint-evidence-ranking-ui-qa.md
---

# Normalized Endpoint Evidence Table 구현 오케스트레이션 계획

## 1. 목적

이 문서는 instance open modal 안에 SOT mockup 스타일의 `NORMALIZED ENDPOINT EVIDENCE TABLE`을 구현하기 위한 작업 순서와 프롬프트 기준을 고정한다.

이번 작업은 selected instance의 endpoint evidence를 정렬하고 탐색하는 기능만 다룬다. Application Dashboard 방향 재논의, 메인 dashboard top20 추가, root cause/priority/attention reason 재판정, raw path/query/per-request sample, endpoint p95/p99, endpoint timeseries는 범위 밖이다.

## 2. 권장 진행 방식

권장 방식은 이 컨텍스트에서 Codex가 backend/read model, frontend surface, live/snapshot contract, guard, 문서 업데이트를 순서대로 오케스트레이션하는 것이다.

이유:

- backend contract와 frontend type/guard/table rendering이 한 줄로 맞물려 있다.
- live mode와 snapshot mode가 같은 `InstanceDashboardSurface`를 재사용해야 하므로 작은 contract drift도 바로 UX 회귀가 된다.
- `durationBuckets`, `slowCountOver500ms`, `slowShareOver500ms`는 frontend에서 만들면 안 되고 backend/read model 제공 여부에 따라 표시해야 한다.
- guard와 QA 문서까지 한 번에 맞춰야 후속 세션이 같은 기준으로 이어갈 수 있다.

## 3. 구현 단계

### 3.1 Backend Read Model 확장

대상:

- `observability-portal/src/main/java/com/observation/portal/domain/instance/model/InstanceDashboardReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/instance/service/InstanceDashboardReadModelService.java`
- 관련 backend test

작업:

- `EndpointEvidenceItem`에 `durationBuckets`, `slowCountOver500ms`, `slowShareOver500ms`를 nullable field로 추가한다.
- `durationBuckets`는 `accepted_metric_buckets.endpoints_json`의 `durationBuckets` 기반으로만 제공한다.
- `slowCountOver500ms`와 `slowShareOver500ms`는 duration bucket에서 계산 가능한 경우에만 제공한다.
- 계산 불가, bucket missing, malformed, 500ms boundary 없음이면 `null`로 둔다.
- endpoint evidence cap은 우선 max 10으로 올린다. max 20은 구현하지 않는다.
- 가능하면 `EndpointEvidenceAggregationService.mergeWindow()`를 재사용해 raw endpoint parser 중복을 줄인다.
- live/snapshot mode의 window/source semantics는 변경하지 않는다.
- snapshot mode는 selected snapshot row window 기준 `accepted_metric_buckets` evidence만 사용하고 live fallback을 넣지 않는다.

### 3.2 Frontend Table 구현

대상:

- `frontend/src/app/components/instance-dashboard-surface.tsx`

작업:

- instance open modal의 endpoint evidence 영역을 SOT mockup의 compact table 구조로 교체한다.
- 제목은 `NORMALIZED ENDPOINT EVIDENCE TABLE`로 둔다.
- 설명은 selected instance의 normalized route evidence 탐색 목적과 application state/endpoint priority를 새로 판정하지 않는다는 계약만 담는다.
- 컬럼은 아래로 고정한다.
  - `ENDPOINTKEY / NORMALIZED ROUTE`
  - `REQUESTCOUNT`
  - `ERRORCOUNT`
  - `ERRORRATE`
  - `SLOWCOUNT >500MS`
  - `SLOWSHARE >500MS`
  - `ENDPOINT DURATION BUCKET DISTRIBUTION`
- request는 중립색, error는 붉은 계열, slow는 절제된 주황/갈색 계열로 표시한다.
- 카드 안 카드처럼 보이지 않게 table/compact row로 구현한다.
- 없는 값은 0 또는 0%가 아니라 `미제공` 또는 `확인할 수 없음`으로 표시한다.

### 3.3 Live/Snapshot 재사용 유지

대상:

- `frontend/src/app/components/instance-dashboard-surface.tsx`
- instance modal open flow가 필요하면 최소 범위만 확인
- `frontend/scripts/read-model-contract-guard.ts`

작업:

- live mode는 `buildLiveInstanceDashboardPath`를 계속 사용한다.
- snapshot mode는 `buildSnapshotInstanceDashboardPath`를 계속 사용한다.
- snapshot mode는 selected snapshot row의 window 기준 read model을 보여준다.
- snapshot modal이 live 데이터를 섞어 쓰지 않도록 기존 guard를 유지하거나 보강한다.
- sort/limit/table rendering은 live와 snapshot에서 같은 component/state를 사용한다.
- stored Application Snapshot state/evidence를 override, 검증, 대체한다는 문구나 동작을 넣지 않는다.

### 3.4 Sort/Limit/Unavailable 동작 고정

작업:

- sort 옵션은 정확히 3개만 제공한다.
  - `requestCount desc`
  - `errorRate desc`
  - `slowShareOver500ms desc`
- `server order`, `errorCount desc`, `slowCountOver500ms desc`는 제공하지 않는다.
- `slowShareOver500ms desc` 정렬에서 `null` row는 아래로 보낸다.
- tie-breaker는 `localDisplayOrder` 또는 기존 server order를 보존하는 방식으로 둔다.
- limit control은 max 10 기준으로 둔다.
- max 20은 후속 확장 후보로만 문서화한다.
- `durationBuckets`가 없거나 500ms boundary 판단이 불가능하면 slow count/share와 bucket distribution을 frontend에서 만들지 않는다.

### 3.5 Guard, 문서, 최종 QA

대상:

- `frontend/src/app/lib/read-model-types.ts`
- `frontend/src/app/lib/read-model-contract-guard.ts`
- `frontend/src/app/lib/read-model-contract-fixtures.ts`
- `frontend/scripts/read-model-contract-guard.ts`
- `implementation-artifacts/qa-endpoint-evidence-ranking-ui.md`
- 필요하면 `implementation-artifacts/spec-endpoint-evidence-ranking-ui-qa.md`

작업:

- Instance Dashboard endpoint evidence item의 nullable duration/slow contract를 type, fixture, guard에 반영한다.
- 기존 `duration buckets 미제공` 고정 검사처럼 새 contract와 충돌하는 guard는 SOT table 기준으로 갱신한다.
- guard는 live/snapshot path 재사용, SOT table anchor, 금지 필드/root cause/state 재판정 금지를 확인한다.
- QA 문서에는 구현된 것, 검증 결과, 후속 후보를 정리한다.

## 4. 검증 기준

필수:

```bash
npm --prefix frontend run typecheck
npm --prefix frontend run guard:read-model-contract
```

backend 변경 후 추가 권장:

```bash
./gradlew :observability-portal:test --tests '*InstanceDashboardReadModel*'
```

가능하면 8080에서 `ecc-test2` 기준으로 확인한다.

- live mode instance open modal
- snapshot mode에서 특정 시간대 복원 후 instance open modal
- snapshot modal이 복원된 시간대 정보를 보여주는지
- endpoint evidence sort 3종
- max 10
- duration bucket/slow 값이 있을 때와 없을 때 표시

`ecc-test` 등 예전 데이터는 이전 버그 수정 전 데이터일 수 있으므로 판단 기준으로 삼지 않는다.

## 5. 분리 실행용 프롬프트

단계를 여러 세션으로 나눌 경우 아래 순서대로 사용한다. 단, 가능하면 한 컨텍스트에서 오케스트레이션하는 편이 안전하다.

### 5.1 Backend Read Model 확장

```text
/Users/tlsdla1235/study/obser_service 에서 bmad-quick-dev로 진행해줘.

목표:
InstanceDashboardReadModel의 selected instance endpointEvidence item에 SOT형 NORMALIZED ENDPOINT EVIDENCE TABLE이 필요한 backend 값을 추가한다.

요구:
- EndpointEvidenceItem에 durationBuckets, slowCountOver500ms, slowShareOver500ms를 nullable field로 추가한다.
- durationBuckets는 accepted_metric_buckets.endpoints_json의 durationBuckets 기반으로만 제공한다.
- slowCountOver500ms / slowShareOver500ms는 durationBuckets에서 계산 가능할 때만 제공한다.
- 계산 불가, bucket missing, malformed, 500ms boundary 없음이면 null로 둔다.
- endpoint item cap은 max 10으로 올린다. max 20은 구현하지 않는다.
- 가능하면 EndpointEvidenceAggregationService.mergeWindow()를 재사용하고 raw path/query/per-request sample은 노출하지 않는다.
- live/snapshot mode의 window/source semantics는 변경하지 않는다.
- snapshot mode는 selected snapshot row window 기준 accepted_metric_buckets evidence만 사용하고 live fallback을 넣지 않는다.

검증:
- 관련 InstanceDashboardReadModelServiceTest / shape test를 추가 또는 갱신한다.
- 기존 sampler/resource fix, route UNKNOWN fix, empty modal fix는 되돌리지 않는다.
- .codex-run/ 및 unrelated preview HTML은 건드리지 않는다.
```

### 5.2 Frontend Table 구현

```text
/Users/tlsdla1235/study/obser_service 에서 bmad-quick-dev로 진행해줘.

목표:
frontend/src/app/components/instance-dashboard-surface.tsx의 instance open modal endpoint evidence 영역을 SOT mockup 스타일의 NORMALIZED ENDPOINT EVIDENCE TABLE로 바꾼다.

요구:
- 제목은 NORMALIZED ENDPOINT EVIDENCE TABLE로 둔다.
- 컬럼은 ENDPOINTKEY / NORMALIZED ROUTE, REQUESTCOUNT, ERRORCOUNT, ERRORRATE, SLOWCOUNT >500MS, SLOWSHARE >500MS, ENDPOINT DURATION BUCKET DISTRIBUTION으로 둔다.
- sort 옵션은 requestCount desc, errorRate desc, slowShareOver500ms desc 세 개만 제공한다.
- limit control은 max 10만 제공한다.
- duration bucket mini bar는 read model에서 가능한 경우에만 표시한다.
- 없는 값은 미제공 또는 확인할 수 없음으로 표시한다.
- frontend에서 slowShareOver500ms, slowCountOver500ms, p95/p99를 새로 만들지 않는다.
- 카드 안 카드처럼 보이지 않게 compact table/row로 구현한다.

검증:
- npm --prefix frontend run typecheck
- 기존 live/snapshot InstanceDashboardSurface 구조를 깨지 않는다.
```

### 5.3 Live/Snapshot 재사용 검증

```text
/Users/tlsdla1235/study/obser_service 에서 bmad-quick-dev로 진행해줘.

목표:
Instance Dashboard live mode와 snapshot mode가 같은 InstanceDashboardSurface/table rendering을 재사용하고, 데이터 소스만 올바르게 갈라지는지 점검하고 필요한 최소 수정만 한다.

확인할 것:
- live mode는 buildLiveInstanceDashboardPath를 사용한다.
- snapshot mode는 buildSnapshotInstanceDashboardPath를 사용한다.
- snapshot mode에서 selected snapshot row의 window 기준 read model을 보여준다.
- snapshot modal이 live 데이터를 섞어 쓰지 않는다.
- snapshot mode에서 sort/limit이 live와 동일하게 동작한다.
- selected instance evidence는 application state나 endpoint priority를 새로 판정하지 않는다.

검증:
- npm --prefix frontend run typecheck
- npm --prefix frontend run guard:read-model-contract
```

### 5.4 Sort/Limit/Unavailable 동작 고정

```text
/Users/tlsdla1235/study/obser_service 에서 bmad-quick-dev로 진행해줘.

목표:
NORMALIZED ENDPOINT EVIDENCE TABLE의 정렬, limit, missing-value 동작을 명확히 고정한다.

요구:
- sort 옵션은 requestCount desc, errorRate desc, slowShareOver500ms desc 세 개만 제공한다.
- server order, errorCount desc, slowCountOver500ms desc는 제공하지 않는다.
- slowShareOver500ms desc 정렬에서 null/미제공 row는 아래로 보낸다.
- tie-breaker는 localDisplayOrder 또는 기존 server order를 보존하는 방식으로 둔다.
- limit은 max 10 기준이다.
- read model이 제공하지 않는 값은 0, 0%, 빈 bar로 속이지 않고 미제공/확인할 수 없음으로 표시한다.
```

### 5.5 Guard / Docs / Final QA

```text
/Users/tlsdla1235/study/obser_service 에서 bmad-quick-dev로 진행해줘.

목표:
이번 NORMALIZED ENDPOINT EVIDENCE TABLE 구현 결과를 guard와 문서에 반영하고 최종 검증한다.

요구:
- InstanceDashboard endpointEvidence item의 durationBuckets, slowCountOver500ms, slowShareOver500ms nullable contract를 guard/fixture에 반영한다.
- guard는 live/snapshot path 재사용, SOT table anchor, 금지 필드/root cause/state 재판정 금지를 확인한다.
- 기존 duration buckets 미제공 고정 같은 오래된 guard 문구는 새 table 계약에 맞게 갱신한다.
- QA 문서에는 구현된 것, 검증 결과, 후속 후보를 정리한다.

검증:
- npm --prefix frontend run typecheck
- npm --prefix frontend run guard:read-model-contract
- 가능한 backend test 실행 결과도 함께 정리한다.

최종 보고:
- 변경 파일
- 검증 결과
- 구현된 정렬 기준
- live/snapshot 재사용 구조
- 아직 못 한/후속 read model 확장 항목
- 커밋 여부
```

## 6. 작업 제외 항목

- 기존 sampler/resource fix, route `UNKNOWN` fix, 빈 modal bug fix 되돌리기
- `.codex-run/` 로컬 실행 로그 수정 또는 커밋
- unrelated preview HTML 수정
- application dashboard top20 추가
- endpoint p95/p99, root cause, raw explorer, endpoint timeseries
- frontend에서 backend가 제공하지 않는 slow/error 값을 합성

## 7. 구현 완료 메모

- backend Instance Dashboard read model은 endpoint item당 `durationBuckets`, `slowCountOver500ms`, `slowShareOver500ms` nullable field를 제공한다.
- endpoint item cap은 10으로 확장했다.
- frontend Instance Dashboard live/snapshot modal은 같은 `InstanceDashboardSurface`에서 `NORMALIZED ENDPOINT EVIDENCE TABLE`을 렌더링한다.
- sort 옵션은 `requestCount desc`, `errorRate desc`, `slowShareOver500ms desc`만 제공한다.
- read model이 제공하지 않거나 100ms/500ms boundary로 분해할 수 없는 duration evidence는 `미제공` 또는 `확인할 수 없음`으로 표시한다.
- 검증 명령은 `npm --prefix frontend run typecheck`, `npm --prefix frontend run guard:read-model-contract`, `./gradlew :observability-portal:test --tests '*InstanceDashboardReadModel*'`를 사용했다.
