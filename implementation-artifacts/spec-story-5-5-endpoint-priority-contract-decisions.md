---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "5.5"
storyKey: "5-5-endpoint-priority-read-model"
status: closed
date: 2026-05-26
scope: Story 5.5 pre-story contract closure
---

# Story 5.5 Endpoint Priority Contract Decisions

## Purpose

Story 5.5는 Application Dashboard read model의 `endpointPriority`를 empty placeholder에서 server-computed typed list로 바꾸기 전에 endpoint ranking, evidence, freshness, privacy, snapshot handoff 경계를 닫는다.

이 문서는 Story 5.5 story 파일을 만들기 전에 사용자가 직접 결정한 계약을 기록한다. 목표는 endpoint priority가 root cause 확정이나 raw explorer가 아니라, 운영자가 다음 확인 지점을 고를 수 있는 bounded next-check surface로 유지되게 하는 것이다.

## 1. Ranking Reason and Ordering Contract

Story 5.5의 endpoint priority는 MVP에서 작은 reason enum과 결정적인 정렬 기준을 사용한다.

결정 내용:

- MVP `reason` enum은 아래 값으로 제한한다.
  - `error_spike`
  - `latency_spike`
  - `error_and_latency`
  - `comparative_regression`
- 기본 ranking priority는 아래 순서를 따른다.
  1. `error_and_latency`
  2. `error_spike`
  3. `latency_spike`
  4. `comparative_regression`
- 같은 reason priority 안에서는 아래 tie-breaker를 순서대로 적용한다.
  1. `confidence` descending
  2. `score` descending
  3. `requestCount` descending
  4. `endpointKey` ascending
- Dashboard current response의 `endpointPriority`는 최대 `5개` item만 반환한다.

결정 이유:

- `endpointPriority`는 사용자가 "어디부터 확인할지"를 고르는 surface이므로, MVP에서는 reason을 error/latency/comparative 축으로 작게 유지한다.
- error와 latency가 동시에 관찰된 endpoint는 단일 신호보다 더 actionable하므로 최상위 reason으로 둔다.
- deterministic tie-breaker를 고정해 같은 입력에서 UI 순서가 흔들리지 않게 한다.
- 최대 5개는 first-screen에서 스캔 가능한 범위를 유지하면서도 단일 endpoint에 과도하게 의존하지 않는 균형점이다.

금지:

- reason enum을 구현 중 임의로 추가하지 않는다.
- UI나 controller가 endpoint priority를 재정렬하지 않는다.
- endpoint priority ordering을 root cause 확정 순위처럼 표현하지 않는다.

## 2. Freshness Suppression and Snapshot Handoff Contract

Story 5.5의 `endpointPriority`는 current dashboard response에서 "지금 확인할 endpoint 우선순위"만 표현한다.

결정 내용:

- application metric freshness가 `current`일 때만 일반 endpoint ranking을 계산한다.
- application metric freshness가 `stale`, `down`, `unknown`이거나 current sample이 endpoint 판단에 부족하면 current response의 `endpointPriority`는 `[]`로 둔다.
- stale/down 직전 spike나 high-confidence endpoint concern은 Story 5.5 current ranking에 재사용하지 않고, Story 5.8/5.9의 snapshot/history surface에서 "last recorded endpoint evidence" 또는 snapshot detail/history marker로 표현한다.
- Story 5.5는 snapshot persistence나 snapshot lookup을 runtime dependency로 만들지 않는다.

결정 이유:

- stale/down 상태에서는 current window endpoint 비교가 현재 문제를 설명한다고 보장할 수 없다.
- 오래된 endpoint ranking을 current priority처럼 보여주면 사용자가 "지금도 이 endpoint가 원인"이라고 오해할 수 있다.
- spike가 stale/down의 직전 원인 후보라면 stored dashboard read model과 bounded snapshot/history가 다루는 편이 더 정확하다.
- Story 5.8 snapshot persistence가 아직 backlog이므로 Story 5.5가 snapshot 존재를 전제로 구현되면 story 순서와 책임이 꼬인다.

금지:

- stale/down freshness에서 과거 endpoint evidence를 current `endpointPriority`에 그대로 복사하지 않는다.
- Story 5.5에서 snapshot repository, snapshot persistence, history marker API를 새로 만들지 않는다.
- `endpointPriority=[]`를 host application down 또는 endpoint 문제 없음으로 해석하지 않는다.

## 3. Endpoint Threshold and Confidence Seed Contract

Story 5.5의 endpoint-level rule은 app-level rule보다 작은 표본 변동에 민감하므로 MVP seed threshold를 명시적으로 고정한다.

공통 guard:

- application metric freshness가 `current`여야 한다.
- current endpoint `requestCount >= 30`이어야 한다.
- baseline endpoint `requestCount >= 30`이어야 한다.
- endpoint histogram latency 판단은 current/baseline bucket boundary set이 일치할 때만 수행한다.
- `UNKNOWN` route의 actionability 정책은 별도 계약에서 닫는다.

`error_spike` 조건:

- current endpoint error rate `>= 0.05`
- error rate absolute delta `>= 0.03`
- current endpoint error rate `>= baseline endpoint error rate * 2`

`latency_spike` 조건:

- endpoint duration bucket distribution에서 `> 500ms` slow share를 사용한다.
- current endpoint slow share `>= 0.20`
- slow share delta `>= 0.10`
- histogram bucket은 percentile scalar로 변환하지 않는다.

`error_and_latency` 조건:

- 같은 endpoint가 `error_spike`와 `latency_spike` 조건을 모두 만족할 때 사용한다.

`comparative_regression` 조건:

- absolute alert threshold는 아직 넘지 않았지만 baseline 대비 악화가 뚜렷한 낮은 confidence 후보로만 사용한다.
- current endpoint `requestCount >= 100`이어야 한다.
- baseline endpoint `requestCount >= 100`이어야 한다.
- error rate delta `>= 0.02` 또는 slow share delta `>= 0.08`이어야 한다.
- `comparative_regression` confidence는 최대 `0.64`로 제한한다.

결정 이유:

- endpoint 단위는 application aggregate보다 표본 수가 작아 흔들리기 쉽다.
- app-level Story 5.4 seed threshold와 같은 방향을 유지하되, comparative 후보는 사용자에게 과한 확신을 주지 않도록 confidence cap을 둔다.
- request count guard를 명시해 low-volume endpoint가 우연한 1~2개 오류로 first-screen priority를 독점하지 않게 한다.

금지:

- endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
- histogram bucket에서 percentile scalar를 계산해 threshold에 사용하지 않는다.
- `comparative_regression`을 high-confidence warning 또는 degraded source처럼 표현하지 않는다.

## 4. Endpoint Evidence Source and Merge Contract

Story 5.5는 accepted bucket에 저장된 endpoint summary를 current/baseline window 기준으로 합산해 bounded endpoint evidence를 만든다.

source:

- `accepted_metric_buckets.endpoints_json`을 endpoint evidence source로 사용한다.
- current 15분 window와 baseline 15분 window를 각각 조회한다.
- window membership은 bucket `bucketEndUtc` 기준 `(start, end]`를 따른다.

group key:

- endpoint grouping key는 `method + route`다.
- API response의 `endpointKey`는 `method + " " + route`로 만든다.

merge:

- 같은 `endpointKey` 안에서 `requestCount`와 `errorCount`를 합산한다.
- `durationBuckets`는 같은 `leMs` boundary set일 때만 cumulative count를 boundary별로 합산한다.
- current/baseline 중 한쪽 endpoint histogram boundary set이 endpoint 내부에서 mismatch이면 해당 window의 latency evidence는 `unavailable`로 둔다.
- latency evidence가 `unavailable`이어도 request/error count가 유효하면 error evidence는 계속 사용할 수 있다.

evidence fields:

- `requestCount`
- `errorCount`
- `errorRate`
- `baselineRequestCount`
- `baselineErrorCount`
- `baselineErrorRate`
- `errorRateDelta`
- `durationBuckets`
- `baselineDurationBuckets`
- `slowShare`
- `baselineSlowShare`
- `slowShareDelta`
- `bucketDistributionSource = histogram_bucket_distribution`

status:

- current endpoint row가 없으면 `missing`으로 둔다.
- baseline endpoint row가 없으면 `insufficient_baseline`으로 둔다.
- endpoint bucket JSON이 invalid하면 `insufficient`로 둔다.
- endpoint histogram boundary mismatch는 `unavailable`로 둔다.

결정 이유:

- Story 5.5는 endpoint priority를 처음 채우는 read model story이므로 `endpoints_json`을 raw explorer가 아니라 bounded projection source로만 사용한다.
- latency evidence와 error evidence를 분리해 histogram 문제가 있더라도 오류율 기반 priority는 계산할 수 있게 한다.
- app-level histogram merge와 같은 cumulative bucket 원칙을 endpoint scope에 적용하되, percentile scalar는 만들지 않는다.

금지:

- raw `endpoints_json` string을 API response에 그대로 노출하지 않는다.
- endpoint p95/p99나 histogram-derived percentile을 계산하지 않는다.
- repository가 endpoint rule, confidence, rank, recommended action을 계산하지 않는다.
- `UNKNOWN` route actionability 정책은 이 계약에서 임의 처리하지 않고 별도 계약으로 닫는다.

## 5. UNKNOWN Route Actionability Contract

Story 5.5는 route attribution이 `UNKNOWN`인 endpoint를 current `endpointPriority` ranking에서 제외한다.

결정 내용:

- `route = UNKNOWN` 또는 `endpointKey = <method> UNKNOWN`인 endpoint는 current `endpointPriority` item 후보에서 제외한다.
- 모든 endpoint가 `UNKNOWN`이고 error/latency spike가 명확하더라도 Story 5.5 current response의 `endpointPriority`는 `[]`로 둔다.
- `UNKNOWN`이 current request count의 큰 비중을 차지하는 상황은 endpoint priority가 아니라 route attribution diagnostic 또는 setup guidance 후보로 후속 처리한다.
- Story 5.5는 route attribution diagnostic을 위한 새 top-level surface를 만들지 않고 follow-up으로 남긴다.

결정 이유:

- `UNKNOWN`은 확인할 endpoint가 아니라 route attribution 품질 또는 instrumentation 설정 문제에 가깝다.
- `UNKNOWN`을 priority 1위로 올리면 사용자는 실제 endpoint를 찾기 어렵고, first-screen의 next-check surface가 흐려진다.
- low-cardinality/privacy 원칙상 raw path/query detail로 `UNKNOWN`을 보완하지 않는다.

금지:

- `UNKNOWN` endpoint를 ranking score만 낮춰서 current `endpointPriority`에 포함하지 않는다.
- `UNKNOWN`을 보완하기 위해 raw path, query string, query key/value, trace id, per-request sample을 노출하지 않는다.
- Story 5.5에서 route attribution 설정 UI나 diagnostic API를 새로 만들지 않는다.

Follow-up:

- route attribution diagnostic surface가 필요하면 후속 story에서 `UNKNOWN` request share, affected method count, route attribution setup guidance를 별도 bounded read model로 검토한다.

## 6. Endpoint Priority Item Shape Contract

Story 5.5는 `ApplicationDashboardReadModel.endpointPriority`를 `List<Object>` placeholder에서 typed read model list로 바꾼다.

`EndpointPriorityItem` 필수 field:

- `rank`
- `method`
- `route`
- `endpointKey`
- `reason`
- `confidence`
- `score`
- `freshness`
- `evidence`
- `recommendedAction`

`freshness` shape:

- `status`는 필수이며 Story 5.5 current ranking item에서는 `current`로 둔다.
- `lastObservedAt`은 필수다.
- `sourceWindow`는 필수이며 Story 5.5 current ranking item에서는 `current`로 둔다.
- `reason`은 nullable이다.

`evidence` 필수 field:

- `requestCount`
- `errorCount`
- `errorRate`
- `bucketDistributionSource`
- `errorEvidenceStatus`
- `latencyEvidenceStatus`

`evidence` nullable 또는 status-dependent field:

- `baselineRequestCount`
- `baselineErrorCount`
- `baselineErrorRate`
- `errorRateDelta`
- `durationBuckets`
- `baselineDurationBuckets`
- `slowShare`
- `baselineSlowShare`
- `slowShareDelta`

`recommendedAction`:

- server-computed copy로 제공한다.
- reason별로 사용자가 다음에 확인할 행동을 제안한다.
- root cause 확정 표현을 사용하지 않는다.

결정 이유:

- item과 evidence object를 항상 존재하게 하면 UI가 null object 방어 대신 status 기반 렌더링을 할 수 있다.
- latency evidence는 histogram 상태에 따라 unavailable/insufficient일 수 있으므로 field nullability와 status를 함께 둔다.
- endpoint priority가 first-screen next-check surface임을 유지하기 위해 action copy는 서버에서 계산한다.

금지:

- `endpointPriority`를 `List<Object>` placeholder로 유지하지 않는다.
- UI나 controller가 item shape를 보완하거나 recommended action을 계산하지 않는다.
- raw JSON string, unbounded map, raw path/query/trace/per-request sample을 evidence에 포함하지 않는다.

## 7. Triage Affected Endpoint Relationship Contract

Story 5.5는 Story 5.4의 `triageCards[].affectedEndpoint`를 endpoint ranking source로 승격하지 않는다.

결정 내용:

- `triageCards[].affectedEndpoint`는 계속 optional hint다.
- `endpointPriority`가 endpoint ranking의 canonical source다.
- `triageCards[].affectedEndpoint`와 `endpointPriority[0].endpointKey`가 반드시 같을 필요는 없다.
- `EndpointPriorityItem`은 관련 rule 추적을 위해 `ruleIds`를 포함한다.
- `triageCards[].affectedEndpoint`와 같은 endpointKey item이 `endpointPriority`에 있을 수 있지만, 없더라도 오류가 아니다.

우선권:

- UI가 endpoint 순위를 표시할 때는 `endpointPriority`만 사용한다.
- triage card는 "앱 단위로 감지된 신호"를 설명한다.
- endpoint priority는 "다음 확인 endpoint"를 설명한다.

허용되는 불일치:

- app-level `global_error_spike` 또는 `global_latency_spike` card는 `affectedEndpoint = null`일 수 있다.
- triage card에는 optional `affectedEndpoint`가 있지만 freshness, threshold, `UNKNOWN` route 정책 때문에 `endpointPriority`에는 없을 수 있다.
- endpointPriority에는 triage card 하나와 직접 1:1 대응되지 않는 여러 endpoint item이 있을 수 있다.

결정 이유:

- 5.4의 `affectedEndpoint`는 endpoint rank가 아니라 단일 확인 힌트로 남긴 계약이다.
- 5.5에서 typed ranked list가 생기면 endpoint 순위의 source는 하나여야 한다.
- `ruleIds`를 두면 triage, snapshot/history, endpoint evidence 사이의 추적성은 유지하면서도 강한 동기화 의존을 만들지 않는다.

금지:

- `affectedEndpoint`를 사용해 UI가 endpoint 순위를 계산하거나 보정하지 않는다.
- `affectedEndpoint`와 top-ranked endpoint가 다르다는 이유로 response를 invalid로 보지 않는다.
- `affectedEndpoint`를 raw path나 query detail로 확장하지 않는다.

## 8. Snapshot and History Handoff Contract

Story 5.5는 current dashboard response의 endpoint priority item이 Story 5.8/5.9에서 bounded snapshot/history evidence로 저장될 수 있는 shape를 제공한다.

결정 내용:

- Story 5.5의 current `EndpointPriorityItem`은 Story 5.8 `dashboard_snapshots.read_model_json`에 bounded evidence로 저장 가능해야 한다.
- Story 5.5는 snapshot persistence를 구현하지 않고, 저장 가능한 field 경계만 맞춘다.

snapshot handoff field 후보:

- `method`
- `route`
- `endpointKey`
- `rank`
- `reason`
- `ruleIds`
- `confidence`
- `score`
- `requestCount`
- `errorRate`
- `durationBuckets`
- `baselineDurationBuckets`
- `bucketDistributionSource`
- `freshness`
- `recommendedAction`

cap:

- Story 5.5 current `endpointPriority`는 최대 `5개` item이다.
- Story 5.8 snapshot endpoint evidence는 최대 `10개` item까지 저장할 수 있다.
- snapshot 저장 시 endpoint evidence 우선순위는 아래 순서를 따른다.
  1. current `endpointPriority` 상위 item
  2. high-confidence concern endpoint
  3. triage `affectedEndpoint`와 연결된 endpoint

Story 5.5 non-goals:

- `dashboard_snapshots` table 또는 Flyway migration을 추가하지 않는다.
- snapshot write/read repository를 만들지 않는다.
- operational event promotion을 구현하지 않는다.
- endpoint timeseries table을 만들지 않는다.
- snapshot/history marker API를 만들지 않는다.

결정 이유:

- Story 5.8/5.9가 endpoint evidence shape를 다시 발명하지 않도록 current read model과 저장 후보 field를 미리 맞춘다.
- current endpoint priority와 historical endpoint evidence를 구분해 stale/down 상태에서 오래된 ranking을 current priority처럼 보여주지 않는다.
- snapshot/history는 raw explorer가 아니라 stored dashboard read model 기반 bounded history로 유지한다.

금지:

- Story 5.5에서 snapshot 존재를 runtime dependency로 삼지 않는다.
- Story 5.5에서 stale/down 직전 endpoint evidence를 current `endpointPriority`로 되살리지 않는다.
- snapshot handoff를 이유로 raw `endpoints_json`, raw path/query, endpoint timeseries, per-request sample을 저장하거나 노출하지 않는다.

## Closure Summary

Story 5.5 pre-story 계약은 아래 8개 결정을 닫은 상태다.

1. Ranking reason and ordering
2. Freshness suppression and snapshot handoff
3. Endpoint threshold and confidence seed
4. Endpoint evidence source and merge
5. `UNKNOWN` route actionability
6. Endpoint priority item shape
7. Triage affected endpoint relationship
8. Snapshot and history handoff
