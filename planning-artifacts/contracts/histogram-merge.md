---
artifactType: contract
name: histogram-merge
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# Contract - Histogram Bucket Distribution MVC Version

## 1. 역할

`histogram-merge`는 기존 파일명과 구현 후보 이름을 유지하는 compatibility contract다. 최신 정책에서 이 계약의 역할은 canonical p95/p99 산출이 아니라 HTTP server duration cumulative histogram bucket을 검증, 병합, 표시 가능한 distribution 자료로 만드는 것이다.

Canonical p95/p99의 source는 starter가 ingest envelope의 `summary.localPercentiles.p95Ms`/`p99Ms`로 보낸 값이다. 이 값은 `starter-reported percentile` 또는 `starter canonical percentile`로 부른다.

Histogram bucket은 계속 수집한다. 다만 bucket 병합 결과에서 p95/p99 scalar를 만들지 않고, distribution visualization, endpoint bucket display, diagnostic raw bucket에 사용한다.

같은 화면/같은 scope에서는 p95/p99 source가 하나여야 한다. Starter-reported p95/p99를 표시하는 scope에서는 histogram-derived p95/p99를 병렬 표시하지 않는다.

## 2. Bucket Schema

Bucket은 cumulative count를 사용한다.

```json
[
  { "leMs": 50, "count": 500 },
  { "leMs": 100, "count": 850 },
  { "leMs": 250, "count": 1120 },
  { "leMs": 500, "count": 1190 },
  { "leMs": 1000, "count": 1200 }
]
```

Rules:

- `leMs`는 정렬된 positive number다.
- `count`는 cumulative count다.
- 같은 bucket set 안에서 count는 감소하면 안 된다.
- 같은 aggregation 대상은 같은 `leMs` boundary set을 사용한다.
- boundary set이 다르면 portal은 payload를 거부하거나 contract version을 분리한다. MVP에서는 거부한다.

## 3. Bucket Distribution Merge Algorithm

1. 같은 project/application/environment/window/scope로 bucket을 묶는다.
2. app-level은 instance별 summary histogram bucket을 같은 `leMs` 기준으로 합산한다.
3. endpoint-level은 method + normalized route 기준으로 합산한다.
4. 합산된 cumulative bucket을 distribution display payload로 반환한다.
5. bucket별 cumulative count, bucket boundary, total count, missing/insufficient 상태를 함께 노출해 UI가 bucket distribution을 그대로 표시할 수 있게 한다.

MVP에서는 이 계약으로 p95/p99를 계산하지 않는다. bucket 내부 선형 보간, target quantile rank 계산, endpoint percentile rollup은 모두 범위 밖이다.

## 4. Starter Canonical Percentile Boundary

`ingest-envelope`는 `localPercentiles.p95Ms`/`p99Ms`를 받는다. 이 값들은 해당 instance의 해당 30초 bucket에 대해 starter가 산출한 canonical point value다.

필드명은 `localPercentiles`지만 의미는 약한 local hint가 아니다. 호환성을 위해 이름만 유지하고, 문서/응답 라벨은 `starter-reported p95/p99`, `starter canonical p95/p99`를 사용한다.

- `localPercentiles.mergeable=false`는 여러 p95/p99 숫자끼리 평균/병합해 새로운 상위 scope p95/p99를 만들지 않는다는 뜻이다.
- 같은 scope에 starter-reported p95/p99와 histogram-derived p95/p99를 함께 두지 않는다.
- 여러 starter instance가 같은 app/project/window 안에 있고 서로 다른 p95/p99를 보내면 app/project/window p95/p99를 임의로 만들지 않는다.
- 이 경우 read model은 instance/source 단위로 percentile 값을 분리하거나, 상위 scope에는 percentile 대신 bucket distribution만 표시한다.

## 4.1 Endpoint Bucket Display Boundary

Endpoint scope에서는 p95/p99를 계산하지 않는다.

- endpoint detail 화면은 `durationBuckets`를 그대로 bucket distribution으로 표시한다.
- endpoint percentile rollup, endpoint percentile judgment, endpoint p99 alert 기준은 만들지 않는다.
- endpoint priority가 latency evidence를 보여야 할 때도 percentile scalar가 아니라 bucket distribution, request count, error rate, freshness 같은 bounded evidence를 사용한다.
- endpoint별 p95/p99 값을 app/project p95/p99로 평균내거나 병합하는 경로는 없다.

## 4.2 Dashboard Display Policy

Dashboard read model은 같은 scope에서 하나의 percentile source만 사용한다.

- Starter-reported p95/p99를 표시하는 scope에서는 histogram-derived p95/p99를 만들거나 병렬 표시하지 않는다.
- 여러 starter 값이 존재해 상위 scope scalar가 모호하면 instance/source 단위로 값의 소유자를 드러낸다.
- 상위 scope에서 하나의 p95/p99로 설명할 수 없으면 percentile 대신 histogram bucket distribution을 표시한다.
- p95/p99 값을 평균, 합산, 최댓값 선택, bucket 재계산으로 새로 만들지 않는다.

## 5. Golden Fixture Requirement

구현은 아래 fixture 유형을 포함해야 한다.

- empty bucket rejection
- decreasing cumulative count rejection
- mismatched boundary rejection
- app-level bucket distribution merge와 endpoint-level bucket distribution merge 분리
- endpoint detail이 bucket distribution만 노출하고 p95/p99를 만들지 않음
- `localPercentiles`가 해당 `instance_bucket` scope의 starter canonical p95/p99로 저장/노출됨
- `localPercentiles` 숫자끼리 app/project/window p95/p99를 평균/병합하지 않음
- 같은 scope에 starter-reported p99와 histogram-derived p99가 동시에 존재하지 않음

## 6. MVC Boundary Rules

- `HistogramMergeService`라는 이름을 유지하더라도 책임은 bucket distribution merge와 validation이다.
- repository는 bucket을 저장하고 읽을 뿐 percentile을 계산하지 않는다.
- UI와 dashboard controller는 p95/p99를 계산하지 않는다.
- read model이 p95/p99를 포함하면 source는 `starter_canonical_percentile` 또는 동등한 starter-reported source enum이어야 한다.
- read model이 histogram bucket을 포함하면 source는 `histogram_bucket_distribution` 또는 동등한 bucket display source enum이어야 한다.
