---
artifactType: contract
name: histogram-merge
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# Contract - Histogram Merge MVC Version

## 1. 역할

`histogram-merge`는 server-side quantile merge의 source of truth다.

Starter는 instance-local percentile 값을 최종 판단값으로 보내지 않는다. Starter는 cumulative histogram bucket을 보내고, portal service layer가 instance bucket을 병합해 quantile을 계산한다.

MVP primary judgment quantile은 p95다. p99는 같은 cumulative histogram merge 기반 auxiliary evidence로만 사용할 수 있으며, p99 단독으로 degraded/down 판단을 만들지 않는다.

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
4. 합산된 cumulative bucket에서 target quantile의 rank를 구한다. p95는 `ceil(totalCount * 0.95)`를 사용한다.
5. target rank를 처음 만족하는 `leMs`를 quantile 값으로 반환한다.

MVP에서는 bucket 내부 선형 보간을 하지 않는다. 반환값은 bucket boundary quantile이다.

## 4. p95 Primary and p99 Auxiliary

p95는 latency 판단과 first-screen triage의 primary judgment다.

p99는 충분한 sample guard를 통과한 경우에만 auxiliary evidence로 노출할 수 있다. p99는 p95, error rate, saturation hint와 함께 confidence를 보조하는 근거이며 단독 rule, 단독 event, 단독 endpoint rank reason이 될 수 없다.

p99 계산을 위해 starter payload를 확장하지 않는다. 이미 수집한 HTTP server duration cumulative histogram bucket을 service layer에서 병합한다.

## 5. Golden Fixture Requirement

구현은 아래 fixture 유형을 포함해야 한다.

- single instance p95
- two instance merged p95
- empty bucket rejection
- decreasing cumulative count rejection
- mismatched boundary rejection
- endpoint-level merge and app-level merge 분리
- p99 auxiliary evidence는 sample guard를 통과한 경우에만 노출

## 6. MVC Boundary Rules

- `HistogramMergeService`가 계산한다.
- repository는 bucket을 저장하고 읽을 뿐 quantile을 계산하지 않는다.
- UI와 dashboard controller는 p95/p99를 계산하지 않는다.
- read model은 `p95Source = server_histogram_merge`를 포함한다.
- p99를 read model이나 operational event evidence에 포함하더라도 `tailLatencyEvidence = auxiliary` 또는 `insufficient_sample` 같은 보조 의미를 함께 표현한다.
