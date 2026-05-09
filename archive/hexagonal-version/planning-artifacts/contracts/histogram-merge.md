---
artifactType: contract
name: histogram-merge
architectureStyle: Lightweight Hexagonal
status: party-mode-fixes-applied
date: 2026-05-08
---

# Contract - Histogram Merge

## 1. 역할

`histogram-merge`는 app-level p95와 endpoint-level p95의 source of truth다.

Starter는 instance-local percentile 값을 최종 판단값으로 보내지 않는다. Starter는 cumulative histogram bucket을 보내고, portal application/domain이 instance bucket을 병합해 p95를 계산한다.

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

## 3. Merge Algorithm

1. 같은 project/application/environment/window/scope로 bucket을 묶는다.
2. app-level은 instance별 summary histogram bucket을 같은 `leMs` 기준으로 합산한다.
3. endpoint-level은 method + normalized route 기준으로 합산한다.
4. 합산된 cumulative bucket에서 target rank `ceil(totalCount * 0.95)`를 구한다.
5. target rank를 처음 만족하는 `leMs`를 p95로 반환한다.

MVP에서는 bucket 내부 선형 보간을 하지 않는다. 반환값은 bucket boundary p95다.

## 4. Golden Fixture Requirement

구현은 아래 fixture 유형을 포함해야 한다.

- single instance p95
- two instance merged p95
- empty bucket rejection
- decreasing cumulative count rejection
- mismatched boundary rejection
- endpoint-level merge and app-level merge 분리

## 5. Boundary Rules

- `MergeHistogramBucketsUseCase`가 계산한다.
- persistence adapter는 bucket을 저장하고 읽을 뿐 p95를 계산하지 않는다.
- UI와 dashboard controller는 p95를 계산하지 않는다.
- read model은 `p95Source = server_histogram_merge`를 포함한다.
