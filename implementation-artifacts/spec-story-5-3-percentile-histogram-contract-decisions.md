# Story 5.3 Percentile and Histogram Contract Decisions

Status: in-progress
Date: 2026-05-25
Scope: Story 5.3 pre-story contract closure

## Purpose

Story 5.3은 Application Dashboard read model의 `sourceScopedPercentiles`와 histogram distribution evidence를 채우기 전에 source, scope, fallback, hardening 경계를 닫는다.

이 문서는 Story 5.3 story 파일을 만들기 전에 사용자가 직접 결정한 계약을 기록한다. 목표는 starter canonical p95/p99를 application p95/p99처럼 오해시키지 않고, histogram bucket을 percentile 계산 source가 아닌 distribution evidence로 유지하는 것이다.

## Closed Decisions

### 1. Percentile Exposure Unit

Application Dashboard의 `sourceScopedPercentiles.items`는 current 15분 window 안에서 **instance별 latest starter canonical percentile point**만 노출한다.

결정 이유:

- 각 point는 `accepted_metric_buckets.local_percentiles_json`에 저장된 `instance_bucket` scope의 starter canonical p95/p99를 그대로 옮긴다.
- 여러 30초 bucket이나 여러 instance의 p95/p99를 평균, 최댓값, 병합, histogram 재계산으로 합성하지 않는다.
- Application Dashboard는 headline evidence만 제공하고, full point series와 추세 탐색은 후속 instance evidence story로 남긴다.
- 구현은 application scope에서 current window 안의 최신 instance별 point를 고르는 조회/정렬에 가깝고, percentile 자체를 새로 계산하지 않는다.

Story seed:

1. `sourceScopedPercentiles.items`는 instance별 최대 1개의 latest point만 포함한다.
2. item은 application, environment, instance, bucketStartUtc, bucketEndUtc, requestCount, p95Ms, p99Ms를 포함한다.
3. item의 p95/p99 source는 persisted `local_percentiles_json`이며 histogram bucket에서 계산하지 않는다.
4. 같은 instance에 current window 안 여러 bucket이 있으면 `bucketEndUtc`가 가장 최신인 point만 선택한다.
5. 여러 instance point가 존재해도 application/window p95/p99 scalar를 만들지 않는다.

### 2. Histogram Distribution Response Location

Histogram bucket distribution은 `sourceScopedPercentiles` 안의 fallback field가 아니라 별도 top-level field인 `histogramDistribution`으로 노출한다.

결정 이유:

- `sourceScopedPercentiles`는 starter canonical p95/p99 point의 source와 scope만 표현한다.
- `histogramDistribution`은 HTTP server duration cumulative histogram bucket을 합산한 distribution evidence를 표현한다.
- 두 field를 분리해 histogram bucket이 p95/p99 계산 source처럼 보이는 오해를 막는다.
- Story 5.3은 5.2 dashboard skeleton의 top-level response shape를 확장하되, UI가 percentile과 distribution을 같은 계산 축으로 합치지 않도록 이름과 source를 분리한다.

Story seed:

1. Dashboard response는 `histogramDistribution` top-level field를 포함한다.
2. `histogramDistribution.source`는 `histogram_bucket_distribution` 또는 동등한 bucket display source enum/string을 사용한다.
3. `sourceScopedPercentiles`에는 histogram-derived p95/p99나 histogram bucket payload를 넣지 않는다.
4. `histogramDistribution`에는 p95Ms, p99Ms, avgMs, maxMs 같은 percentile/latency scalar를 포함하지 않는다.
5. Histogram distribution은 UI 표시와 diagnostic evidence 용도이며 lifecycle state, triage, endpoint priority를 Story 5.3에서 새로 계산하지 않는다.

### 3. Missing and Insufficient Evidence Policy

Percentile point와 histogram distribution은 field를 null로 없애지 않고, 빈 배열과 `status`/`reason`을 함께 제공한다.

결정 이유:

- top-level field가 항상 존재한다는 dashboard read model skeleton의 방향을 유지한다.
- 값 없음, 트래픽 없음, sample 부족을 `0ms`, 정상, 빠름으로 오해하지 않는다.
- UI는 server가 제공한 `status`와 `reason`을 표시할 수 있고, percentile이나 histogram 의미를 재계산하지 않는다.

Story seed:

1. current window 안 percentile point가 없으면 `sourceScopedPercentiles.items=[]`, `status=missing`, `reason=no_percentile_points_in_current_window`를 반환한다.
2. percentile source가 없거나 requestCount가 0인 경우에도 p95Ms/p99Ms를 0으로 만들지 않는다.
3. histogram bucket이 없으면 `histogramDistribution.buckets=[]`, `status=missing`, `reason=no_histogram_buckets_in_current_window`를 반환한다.
4. histogram bucket은 있지만 request/sample이 표시 판단에 부족하면 `status=insufficient`와 reason을 반환할 수 있다.
5. evidence가 있으면 `status=available`을 사용하고, reason은 null 또는 명확한 available reason으로 둔다.
6. `sourceScopedPercentiles`와 `histogramDistribution`은 null이 아니라 항상 존재하는 object다.

### 4. Histogram Boundary Mismatch Policy

Story 5.3 read model merge 단계에서는 current window 안 histogram boundary set이 서로 다르면 distribution을 만들지 않고 unavailable 상태를 반환한다.

결정 이유:

- 서로 다른 `leMs` boundary set을 임의로 합치면 cumulative distribution 의미가 깨진다.
- 일부 bucket만 골라 합치면 표시된 distribution이 current window 전체를 대표하는 것처럼 오해될 수 있다.
- Story 5.3은 ingest validation 전면 재검토가 아니라 dashboard read model merge guard를 닫는 범위다.

Story seed:

1. application-level summary histogram distribution은 같은 `leMs` boundary set을 가진 bucket만 합산할 수 있다.
2. current window 안 합산 대상 bucket의 boundary set이 하나로 일치하지 않으면 `histogramDistribution.status=unavailable`을 반환한다.
3. boundary mismatch reason은 `histogram_boundary_mismatch`로 고정한다.
4. boundary mismatch 상황에서 p95/p99를 계산하거나 일부 bucket만 선택해 distribution을 만들지 않는다.
5. Story 5.3은 이 guard를 read model/repository projection 경계에서 검증하고, 기존 ingest envelope 전체 hardening을 다시 열지 않는다.

### 5. Application-Level and Endpoint-Level Scope

Story 5.3은 application-level summary histogram distribution만 구현하고, endpoint-level histogram distribution은 후속 endpoint priority story로 미룬다.

결정 이유:

- Story 5.3은 Application Dashboard headline evidence를 채우는 범위로 제한한다.
- endpoint별 histogram은 rank, reason, confidence, freshness, recommended action과 함께 5.5 endpoint priority read model에서 다루는 편이 자연스럽다.
- endpoint histogram을 5.3에서 먼저 합치면 endpoint priority/rule 경계를 앞당길 위험이 있다.

Story seed:

1. `histogramDistribution`은 `summary.httpServerDurationBuckets` 기반 application-level distribution만 포함한다.
2. Story 5.3은 `endpoints_json`의 endpoint별 `durationBuckets`를 dashboard response에 노출하지 않는다.
3. Story 5.3은 endpoint별 p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
4. Endpoint-level histogram distribution과 endpoint evidence는 Story 5.5 endpoint priority read model에서 닫는다.

### 6. Current and Baseline Evidence Boundary

Story 5.3은 current window와 baseline window의 histogram distribution evidence를 모두 제공하되, 두 window를 비교한 판단은 만들지 않는다.

결정 이유:

- Story 5.4가 latency 변화와 triage rule을 판단할 수 있도록 current/baseline evidence를 server read model 안에 준비한다.
- Story 5.3은 "느려졌다", "개선됐다", "regression", "confidence" 같은 판단값을 만들지 않는다.
- UI가 current/baseline bucket을 직접 비교해 state, rule, p95/p99, endpoint priority를 계산하지 않도록 response field 이름과 dev note에서 evidence-only 성격을 고정한다.

Story seed:

1. `histogramDistribution`은 current 15분 window와 baseline 15분 window의 application-level summary bucket distribution을 구분해서 담는다.
2. 각 window distribution은 독립적으로 `status`, `reason`, `buckets`, `totalCount` 같은 evidence field만 가진다.
3. Story 5.3은 current/baseline delta, percentage change, slower/faster label, regression flag, rule id, confidence를 계산하지 않는다.
4. Story 5.4 triage summary가 current/baseline evidence를 소비해 판단을 만들 수 있다.
5. `sourceScopedPercentiles.items`는 Decision 1에 따라 current window 안 instance별 latest point만 유지한다. Baseline percentile 비교 판단은 Story 5.3에서 만들지 않는다.

### 7. Percentile Source Naming

Dashboard API의 `sourceScopedPercentiles.source`와 item source는 persisted `local_percentiles_json.source` 값인 `starter_local`을 그대로 노출한다.

결정 이유:

- API response와 저장된 ingest payload source 값을 1:1로 추적할 수 있다.
- Story 5.3 구현에서 persistence value와 read model value 사이의 별도 source-name translation을 만들지 않는다.
- 단, `starter_local`은 약한 local hint가 아니라 starter가 해당 instance 30초 bucket에서 직접 산출해 보낸 canonical p95/p99 point라는 설명을 story/dev note에 명시한다.

Story seed:

1. `sourceScopedPercentiles.source`는 `starter_local`을 사용한다.
2. 각 percentile item이 source field를 가진다면 동일하게 `starter_local`을 사용한다.
3. `starter_local` source의 의미는 `instance_bucket` scope starter canonical percentile point로 문서화한다.
4. API는 `starter_canonical_percentile`로 source 값을 번역하지 않는다.
5. `starter_local` 값을 사용하더라도 p95/p99 평균, 최댓값, 병합, histogram 재계산은 금지한다.
