---
artifactType: contract
name: time-buckets
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# Contract - Time Buckets MVC Version

## 1. 고정값

| 항목 | 값 |
|---|---|
| starter flush cadence | 30초 |
| bucket duration | 30초 |
| starter drain grace window | 30초 / 1 bucket duration |
| current window | 최근 15분 |
| baseline window | current 직전 15분 |
| time zone | UTC |
| stale 후보 | 최근 accepted bucket이 90초 이상 없음 |
| down 후보 | 최근 accepted bucket이 180초 이상 없음 |

`starter flush cadence`는 drain check/tick 주기이며, drain eligibility의 grace 조건을 대체하지 않는다.

## 2. Bucket Boundary

모든 bucket은 UTC 30초 boundary에 정렬한다. bucket interval은 `[startUtc, endUtc)` semantics를 유지한다.

예시:

- `01:00:00Z` to `01:00:30Z`
- `01:00:30Z` to `01:01:00Z`

boundary에 맞지 않는 bucket은 ingest validation에서 거부한다.

drain eligibility는 bucket boundary와 별도로 판단한다. starter는 `bucket.endUtc + grace <= nowUtc`일 때만 해당 bucket을 drain/flush candidate로 만들 수 있다. MVP에서 grace는 `bucketDuration`과 같은 30초이므로, `drainClosedBuckets(nowUtc)`는 `bucket.endUtc + bucketDuration <= nowUtc`인 interval만 반환한다.

grace window 안에 도착한 HTTP/JVM/datasource late sample은 기존 bucket에 포함된다. drain 이후 interval은 sealed로 간주하며, 이후 같은 interval sample은 drop하고 duplicate flush candidate를 만들지 않는다. sealed watermark는 단조 증가해야 한다.

## 3. Window Semantics

`current`는 dashboard query 시점 기준 최근 15분 accepted bucket 묶음이다.

`baseline`은 current 바로 이전 15분 accepted bucket 묶음이다.

baseline이 충분하지 않으면 변화율 기반 rule은 꺼지고, absolute threshold 기반 rule만 허용한다.

## 4. History Query Horizon

Operational event history의 조회 horizon은 current/baseline 판단 window와 다르다.

current 15분과 baseline 15분은 현재 상태 판단 전용이다. Recent history는 최근 24시간 또는 limit 기반으로 이미 생성된 dashboard snapshot/read model 결과를 bounded event로 요약한다.

history horizon은 현재 상태 판단을 대체하지 않으며, stale/down/degraded 판정 기준을 다시 정의하지 않는다.

## 5. Freshness Source

freshness는 starter가 주장하는 현재 시간이 아니라, portal이 수용한 마지막 bucket의 `endUtc` 기준으로 판단한다. 30초 drain grace 때문에 UI freshness가 최대 30초 늦어지는 것은 MVP에서 허용한다.

## 6. MVC Boundary

- time boundary 계산은 `DashboardReadModelService`, `LifecycleStateService`, `HistogramMergeService`가 공유하는 time model/utility에서 수행한다.
- system clock은 injectable `Clock` 또는 `UtcClock` bean으로 둔다.
- repository는 timestamp를 저장하되 freshness 의미를 판단하지 않는다.
- controller와 UI는 stale/down 기준을 재판정하지 않는다.
- Epic 5/6의 history service 후보는 별도 query horizon을 사용하되 current/baseline 15분 판단 window를 재해석하지 않는다.
