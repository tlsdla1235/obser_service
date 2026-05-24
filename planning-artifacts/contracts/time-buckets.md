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
| dashboard snapshot cadence | application별 1시간 scheduled snapshot |
| dashboard snapshot retention | 기본 14일, config 조정 가능 |
| repeated concern suppression window | 같은 application + endpointKey + ruleId 기준 60분 |
| time zone | UTC |
| stale 후보 | 최근 accepted bucket이 90초 이상 없음 |
| down 후보 | 최근 accepted bucket이 180초 이상 없는 data-plane freshness 후보 |

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

## 5. Snapshot Cadence and Source 역할

`accepted_metric_buckets`는 UTC 30초 단위로 저장되는 계산 원천이다. 이 데이터는 current/baseline 15분 read model 계산과 raw bucket retention 범위 안의 fallback regeneration에 사용하지만, 장기 dashboard history 자체는 아니다.

`dashboard_snapshots`는 application별 1시간 scheduled cadence로 저장하는 dashboard read model history다. 이 cadence는 starter flush cadence나 bucket duration과 별개이며, ingest commit마다 snapshot을 만들지 않는다.

Dashboard query 시점에 최신 snapshot이 없거나 current response로 쓰기에 오래된 경우 `DashboardReadModelService`가 fallback으로 current read model을 재생성하고, 필요하면 snapshot으로 저장할 수 있다. 이 fallback은 query 시점 current/baseline 15분 판단을 유지하며, 30초 단위 dashboard snapshot 장기 보관으로 해석하지 않는다.

중요한 stale/down/degraded 구간과 high-confidence concern은 저장된 snapshot/read model의 `state_code`, `generated_at`, `current_window_end_utc`, `read_model_json` 또는 bounded index/search helper column에서 파생한다. 의미 있는 state-change, confidence `>= 0.82` high-confidence concern, 짧지만 강한 spike 실험값(confidence `>= 0.90` + 최근 5개 bucket 중 2개 이상 bad), fallback regeneration 조건에서만 1시간 cadence 외 추가 snapshot capture를 허용한다.

`dashboard_snapshots` 기본 retention은 `14일`이며 config로 조정 가능해야 한다. `accepted_metric_buckets` retention은 짧은 30초 raw-ish 계산 원천 보관 정책을 따르고, dashboard snapshot retention과 같은 의미로 다루지 않는다.

raw bucket retention이 지난 뒤 endpoint별 detail은 snapshot `read_model_json`에 남은 최대 10개의 bounded endpoint evidence까지만 보여준다. 이 evidence는 top triage card 연결 endpoint, `endpointPriority` 상위 항목, high-confidence concern endpoint 순서로 남기며 raw path/query/trace/per-request sample은 포함하지 않는다.

## 6. Freshness Source

freshness는 starter가 주장하는 현재 시간이나 heartbeat 시간이 아니라, portal이 수용한 마지막 bucket의 `endUtc` 기준으로 판단한다. 이 freshness는 **metric data freshness**이며 host application process liveness를 직접 뜻하지 않는다.

30초 drain grace 때문에 UI freshness가 최대 30초 늦어지는 것은 MVP에서 허용한다. 사용자 서버에 몇 분 동안 요청이 없어서 새 bucket이 오지 않는 상황도 가능하므로, accepted bucket 부재나 오래됨만으로 host application down을 확정하지 않는다.

Starter heartbeat가 구현되어 있으면 heartbeat는 starter/application process liveness, portal reachability, project key validity, metadata validity의 별도 control-plane source다. 이 값은 accepted bucket freshness age를 바꾸지 않지만, 후속 read model은 "starter connected but no accepted bucket", "waiting for traffic", "telemetry unreachable" 같은 copy를 선택하는 별도 입력으로 사용할 수 있다.

## 7. MVC Boundary

- time boundary 계산은 `DashboardReadModelService`, `LifecycleStateService`, `HistogramMergeService`가 공유하는 time model/utility에서 수행한다.
- system clock은 injectable `Clock` 또는 `UtcClock` bean으로 둔다.
- repository는 timestamp를 저장하되 freshness 의미를 판단하지 않는다.
- controller와 UI는 stale/down 기준을 재판정하지 않는다.
- Epic 5/6의 history service 후보는 별도 query horizon을 사용하되 current/baseline 15분 판단 window를 재해석하지 않는다.
