# Story 5.4 Triage, Zero-Insight, and Recovery Contract Decisions

Status: in-progress
Date: 2026-05-25
Scope: Story 5.4 pre-story contract closure

## Purpose

Story 5.4는 Application Dashboard read model의 `triageCards`, `zeroInsight`, `recovery`, degraded 판단 입력을 구현하기 전에 rule 범위와 상태 언어 경계를 닫는다.

이 문서는 Story 5.4 story 파일을 만들기 전에 사용자가 직접 결정한 계약을 기록한다. 목표는 dashboard가 첫 확인 지점을 제안하되 원인을 확정하지 않고, recovery와 starter connection copy를 결합해 host application down을 단정하지 않도록 하는 것이다.

## Closed Decisions

### 1. Recovery Source Contract

Story 5.4는 **accepted bucket gap 기반 lightweight previous context**를 사용해 recovery를 표현한다.

결정 내용:

- 최신 accepted bucket은 current freshness인데 그 직전 metric data 공백이 stale/down threshold 이상이었다면, 이전 metric state를 `stale` 또는 `down` 후보로 본다.
- 이 상황에서 current window sample이 insufficient이면 `state=unknown`, `recovery.isRecovering=true`, `zeroInsight.reasonCode=observing_recovery`를 반환한다.
- `lastHealthyAt`은 현재 요청의 accepted bucket만으로 추론하지 않는다.
- snapshot 또는 이전 read model source가 아직 없으면 `recovery.lastHealthyAt=null`로 둔다.
- recovery copy는 "회복 완료"가 아니라 "회복 관찰 중"으로 표현한다.
- recovery copy와 starter connection copy를 합쳐 host application down, host process down, 앱 내려감 같은 확정 표현을 만들지 않는다.

결정 이유:

- Story 5.4는 stale/down 이후 첫 bucket이 돌아온 순간을 빈 화면처럼 보이지 않게 설명해야 한다.
- 아직 Story 5.8 snapshot persistence가 없으므로 이전 healthy 시각을 억지로 만들면 `read-model-contract`의 `lastHealthyAt` source 계약을 흐릴 수 있다.
- accepted bucket gap은 recovery 후보를 감지하는 최소 근거로 사용하되, 정교한 이전 state/read model source는 snapshot 기반 후속 story로 남긴다.

Story seed:

1. `DashboardReadModelService` 또는 하위 helper는 latest/current bucket 이전의 accepted bucket gap을 조회해 lightweight previous metric state 후보를 만들 수 있다.
2. gap이 `AcceptedBucketFreshnessEvaluator.STALE_AFTER` 이상이면 previous state 후보는 `stale`, `DOWN_AFTER` 이상이면 `down`으로 본다.
3. recovery trigger는 `previousState in [stale, down]` + `freshness=current` + `sampleReadiness=insufficient` 조합에서만 켠다.
4. `lastHealthyAt`은 snapshot/이전 read model source가 없으면 `null`이며, 현재 bucket 수용만으로 생성하지 않는다.
5. `observing_recovery`는 `triageCards=[]`일 때 다른 zeroInsight insufficient-sample reason보다 우선한다.
6. Story 5.8에서 snapshot 기반 `previousState`, `lastHealthyAt`, recovery marker source를 재정교화할 수 있도록 이 결정 문서를 명시적으로 참조한다.

Follow-up for Story 5.8:

- Dashboard snapshot persistence가 구현되면 recovery source는 저장된 previous dashboard read model 또는 snapshot marker를 우선 사용하도록 재검토한다.
- Story 5.8 story 작성 시 이 문서의 "Recovery Source Contract"를 읽고, lightweight gap 기반 fallback과 snapshot 기반 source의 우선순위를 명세한다.
- Snapshot source가 생기더라도 current request의 accepted bucket만으로 `lastHealthyAt`을 추론하지 않는 원칙은 유지한다.

### 2. Story 5.4 Rule Scope Contract

Story 5.4는 **application-level triage와 zeroInsight/recovery mapping**을 닫는다.

결정 내용:

- Story 5.4는 application-level `global_error_spike`, `global_latency_spike`, saturation hint 계열 triage card를 계산할 수 있다.
- Availability 계열 `service_down`, `service_stale`, `service_idle`은 state/zeroInsight/recovery copy와 중복되지 않도록 first-screen explanation에만 제한적으로 사용한다.
- Endpoint-level rule을 본격적인 ranking/list로 구현하지 않는다.
- Triage card에 원인을 좁히는 데 도움이 되는 `affectedEndpoint` 0~1개를 optional summary field로 둘 수 있다.
- `endpointPriority` top-level list는 Story 5.4에서 계속 empty placeholder로 유지한다.
- Endpoint별 rank, reason, endpoint freshness, endpoint evidence subset, recommended action 목록은 Story 5.5에서 닫는다.

결정 이유:

- Story 5.4는 Application Dashboard가 "지금 우선 확인할 앱 단위 신호가 있는가"를 설명하는 단계다.
- Endpoint별 우선순위는 `endpointPriority` 전용 story에서 rank/evidence/freshness/recommended action을 함께 닫는 편이 계약 경계가 선명하다.
- 5.4에서 endpoint ranking까지 앞당기면 Story 5.5와 scope가 겹치고, endpoint p95/p99 계산 금지 경계가 흐려질 수 있다.

Story seed:

1. `TriageSummaryService`는 current/baseline application aggregate, application summary histogram distribution, source-scoped starter percentile latest points, runtime ratio latest sample을 bounded input으로 받을 수 있다.
2. Story 5.4의 triage card는 application-level rule result를 최대 0~3개까지 반환한다.
3. `affectedEndpoint`는 optional hint이며, endpoint priority ranking이 아니다.
4. Story 5.4는 `endpointPriority=[]`를 유지하고, endpoint priority item shape와 ordering은 Story 5.5에서 구현한다.
5. Story 5.4는 endpoint별 p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.

Follow-up for Story 5.5:

- Story 5.5 story 작성 시 이 문서의 "Story 5.4 Rule Scope Contract"를 읽고, 5.4가 남긴 optional `affectedEndpoint`와 5.5의 ranked `endpointPriority` 사이의 관계를 명시한다.
- 5.5는 endpoint-level slow/error/comparative evidence, confidence, freshness, recommended action을 `endpointPriority` item으로 닫는다.
- 5.5도 endpoint p95/p99 계산, raw path/query string/query key/value/trace id/per-request sample 노출 금지 원칙을 유지한다.

### 3. Triage Card Typed Shape Contract

Story 5.4는 `triageCards`를 `List<Object>` placeholder에서 **typed read model record list**로 바꾼다.

결정 내용:

- Triage card 기본 shape는 `ruleId`, `severity`, `title`, `summary`, `recommendation`, `confidence`, `score`, `affectedEndpoint`, `evidence`를 포함한다.
- `affectedEndpoint`는 optional field이며, endpoint priority ranking이 아니다.
- `evidence`는 rule별 bounded object로 둔다.
- 허용 evidence 후보는 request count, error rate/current error count, baseline error rate/count, histogram distribution window summary, source-scoped starter percentile point summary, runtime ratio latest sample, freshness/status reason이다.
- `severity`는 MVP에서 `info`, `warning`, `critical` 같은 제한된 문자열 또는 enum으로 고정한다.
- `confidence`는 `0.0 <= confidence <= 1.0`, `score`는 정렬용 bounded numeric value로 검증한다.

금지:

- raw path, query string, query key/value, trace id, per-request sample을 triage evidence에 포함하지 않는다.
- endpoint p95/p99, endpoint percentile rollup, histogram-derived percentile을 evidence에 포함하지 않는다.
- `evidence` 안에 raw accepted bucket JSON, raw `endpoints_json`, raw histogram JSON string을 그대로 노출하지 않는다.
- `affectedEndpoint`가 있다고 해서 `endpointPriority` item이나 endpoint ranking으로 해석하지 않는다.

결정 이유:

- UI가 server-computed card를 그대로 렌더링할 수 있어야 한다.
- Rule별 evidence를 너무 크게 고정하면 Story 5.5 endpoint priority와 Story 5.8 snapshot evidence 경계를 침범한다.
- Bounded evidence만 허용하면 dashboard가 "왜 이 card가 나왔는지"를 설명하면서도 raw metric explorer처럼 보이지 않는다.

Story seed:

1. `ApplicationDashboardReadModel`은 `List<TriageCard>`를 top-level `triageCards`로 가진다.
2. `TriageCard` record는 기본 shape를 typed field로 고정한다.
3. `TriageEvidence`는 nested record 또는 rule-specific bounded map/record로 둘 수 있지만, public response에서 raw persistence JSON을 그대로 반환하지 않는다.
4. `triageCards`는 confidence `>= 0.65` candidate 중 ranking 기준으로 최대 3개만 포함한다.
5. `triageCards`가 비어 있으면 `zeroInsight`는 반드시 non-null이다.

### 4. ZeroInsight Precedence Contract

Story 5.4는 `triageCards=[]`일 때 `zeroInsight.reasonCode`를 아래 우선순위로 결정한다.

우선순위:

1. `observing_recovery`: stale/down 이후 새 accepted bucket이 들어왔지만 sample이 아직 부족한 경우
2. `telemetry_unreachable`: starter heartbeat와 accepted bucket freshness가 모두 최근 상태가 아닌 경우
3. `waiting_first_data`: 아직 accepted bucket이 없고 starter heartbeat는 최근 수신된 경우
4. `insufficient_sample`: current accepted bucket은 있으나 판단 sample이 부족한 경우
5. `metric_data_idle`: traffic idle 또는 recent heartbeat + 오래된/없는 accepted bucket 조합이 traffic 부재를 가리키는 경우
6. `no_action_needed`: freshness와 sample은 충분하고 노출할 triage card가 없는 경우

결정 내용:

- `triageCards`가 하나라도 있으면 `zeroInsight=null` 또는 이에 준하는 "zero insight 없음" 상태로 둔다. public response에서 nullable을 유지할지 non-null empty marker를 둘지는 story 작성 시 기존 API 호환성을 확인해 결정한다.
- `recovery.isRecovering=true`와 `triageCards=[]` 조합은 항상 `observing_recovery`를 우선한다.
- `observing_recovery`는 "복구 완료"가 아니라 "복구 관찰 중"이라는 message/recommendedAction을 제공한다.
- `no_recent_traffic`은 zeroInsight reason code로 새로 추가하지 않고 `metric_data_idle`로 수렴한다.
- `telemetry_unreachable` copy는 host application down을 확정하지 않는다.

결정 이유:

- 같은 response에서 여러 reason 후보가 동시에 성립할 때 UI가 임의로 선택하지 않게 한다.
- Recovery 맥락은 단순 sample 부족보다 사용자에게 더 중요한 상태 설명이다.
- Telemetry unreachable은 setup/network 확인 action이 필요하므로 first-data/idle보다 먼저 드러내는 편이 안전하다.

Story seed:

1. zeroInsight mapping helper는 위 우선순위를 테스트로 고정한다.
2. `observing_recovery` scenario는 `insufficient_sample` scenario보다 먼저 평가한다.
3. recent heartbeat + stale/down accepted bucket 조합은 host down copy 없이 `metric_data_idle` 또는 recovery 조건 충족 시 `observing_recovery`로 표현한다.
4. missing/stale heartbeat + missing/stale accepted bucket 조합은 `telemetry_unreachable`로 표현한다.
5. sufficient active sample + no triage card는 `no_action_needed`로 표현한다.

### 5. Degraded and Triage Relationship Contract

Story 5.4는 **triage card 노출과 degraded state 진입을 분리**한다.

결정 내용:

- confidence `0.65` 이상인 candidate는 triage card로 노출될 수 있다.
- triage card가 있다고 해서 `state=degraded`가 되는 것은 아니다.
- `state=degraded`는 rule guard 통과, confidence `>= 0.75`, 최근 5개 30초 bucket 중 3개 이상 bad 조건을 모두 만족한 대표 concern이 있을 때만 가능하다.
- confidence `0.65~0.74` candidate는 triage card로 노출될 수 있지만 state는 `active`로 유지될 수 있다.
- confidence `>= 0.75` candidate라도 최근 5개 중 3개 bad 조건을 통과하지 못하면 state는 `active`로 유지될 수 있다.
- `TriageSummaryService`는 대표 concern을 바탕으로 `DegradedHysteresisInput`을 만들고, `LifecycleStateService`가 최종 metric state를 결정한다.
- UI는 triage card 존재 여부로 degraded state를 다시 계산하지 않는다.

결정 이유:

- Dashboard triage card는 "첫 확인 지점"이고, degraded state는 더 강한 운영 상태 판단이다.
- 30초 단발 blip이나 낮은 confidence 신호로 state strip이 흔들리면 사용자가 dashboard를 신뢰하기 어렵다.
- 기존 state-semantics의 degraded hysteresis 계약을 유지하면서도, confidence `>= 0.65`의 유용한 확인 신호는 숨기지 않는다.

Story seed:

1. `TriageSummaryService`는 노출 candidate 목록과 degraded hysteresis 대표 입력을 분리해서 반환한다.
2. `DashboardReadModelService`는 triage summary 결과의 degraded input을 `MetricLifecycleInput`에 전달한다.
3. `LifecycleStateService`는 기존 `DegradedHysteresisInput.canEnterDegraded()` / `canResolveDegraded()` 기준으로 state를 결정한다.
4. `triageCards` 최대 3개 노출 기준과 `state=degraded` 진입 기준을 각각 테스트한다.
5. card가 있지만 degraded state가 아닌 scenario를 회귀 테스트로 고정한다.

### 6. Rule Guard Seed Threshold Contract

Story 5.4는 MVP rule guard 숫자를 **보수적인 seed threshold**로 고정한다.

결정 내용:

- 이 숫자는 MVP seed이며, 운영 데이터가 쌓이면 후속 story에서 조정할 수 있다.
- Story 5.4 구현과 테스트는 아래 값을 기준으로 한다.

Seed thresholds:

| 항목 | 값 |
|---|---|
| Current minimum request count | `>= 30` requests in current 15m window |
| Baseline sufficiency | `>= 30` requests in baseline 15m window |
| Global error spike absolute threshold | current error rate `>= 0.05` |
| Global error spike absolute delta | current error rate - baseline error rate `>= 0.03` |
| Global error spike relative delta | current error rate >= baseline error rate `* 2` |
| Global latency slow bucket | duration bucket `> 500ms` |
| Global latency slow share threshold | current slow share `>= 0.20` |
| Global latency slow share delta | current slow share - baseline slow share `>= 0.10` |
| Bad bucket count for degraded candidate | recent 5 buckets 중 `>= 3` bad |
| Triage card exposure | confidence `>= 0.65` |
| Degraded enter | confidence `>= 0.75` + guard 통과 + bad bucket count 통과 |
| Degraded resolve | confidence `< 0.60` 또는 rule recovery condition이 5 consecutive buckets |

결정 이유:

- MVP demo에서 error/latency concern이 보이려면 너무 높은 threshold는 피해야 한다.
- 동시에 운영 첫 화면의 state strip이 단발 noise로 흔들리지 않도록 minimum sample, baseline sufficiency, bad bucket count를 함께 둔다.
- 숫자를 story 작성자에게 위임하지 않고 테스트로 고정해야 후속 story와 UI가 같은 계약을 따른다.

Story seed:

1. `TriageSummaryService`는 current/baseline request count가 각각 30 미만이면 비교형 error/latency rule candidate를 만들지 않거나 confidence를 노출 기준 아래로 낮춘다.
2. `global_error_spike`는 absolute threshold, absolute delta, relative delta를 모두 통과해야 candidate가 된다.
3. `global_latency_spike`는 histogram distribution에서 `>500ms` slow share를 계산해 current threshold와 baseline delta를 모두 통과해야 candidate가 된다.
4. Confidence 계산은 implementation detail로 둘 수 있지만, card exposure/degraded enter threshold는 위 표를 따른다.
5. Seed threshold 값은 상수 이름과 테스트명에 드러나게 둔다.

### 7. Saturation Hint Contract

Story 5.4는 CPU, heap, datasource pool ratio를 **동반 신호가 있을 때의 확인 힌트**로만 사용한다.

결정 내용:

- MVP의 runtime ratio는 30초 bucket 안의 latest sample에 가까운 값이며, sustained pressure나 peak를 증명하지 않는다.
- Resource ratio latest sample 단독으로는 `state=degraded`를 만들지 않는다.
- Resource ratio latest sample 단독으로는 high-confidence 원인 확정 card를 만들지 않는다.
- `db_pool_high_with_latency`는 datasource pool usage ratio가 `>= 0.85`이고 latency spike guard를 함께 통과할 때 card 후보가 될 수 있다.
- `cpu_high_with_latency`는 CPU usage ratio가 `>= 0.85`이고 latency spike guard를 함께 통과할 때 card 후보가 될 수 있다.
- `heap_high_hint`는 heap used ratio가 `>= 0.90`이고 latency 또는 error 신호가 함께 있을 때 card 후보가 될 수 있다.
- Saturation card copy는 "가능성을 먼저 확인"하는 문구로 작성하고 원인 확정 표현을 쓰지 않는다.
- Post-MVP runtime aggregate가 도입되면 `latest`, `max`, `avg`, `sampleCount` 의미를 사용해 confidence와 rule guard를 재정의할 수 있다.

결정 이유:

- latest runtime ratio 한 점만으로 DB pool 고갈, CPU 병목, heap pressure를 확정하면 오탐 위험이 크다.
- Latency/error와 함께 나타난 resource ratio는 첫 확인 지점으로는 충분히 유용하다.
- MVP scope에서는 runtime aggregate schema를 새로 열지 않고, 이미 저장된 ratio field를 bounded hint로만 사용한다.

Story seed:

1. `TriageSummaryService`는 current window의 latest 또는 대표 accepted bucket runtime ratio를 bounded evidence로 읽을 수 있다.
2. Resource hint rule은 latency/error guard와 결합될 때만 triage candidate를 만든다.
3. Resource hint evidence에는 ratio value와 source window만 담고 raw runtime sample 배열을 만들지 않는다.
4. Saturation hint는 confidence가 높더라도 단독으로 degraded hysteresis 대표 concern이 되지 않는다.
5. Copy 금지 예: "DB pool 고갈로 장애가 발생했습니다."
6. Copy 허용 예: "DB pool 사용률이 높고 응답 지연도 함께 증가했습니다. DB 연결 대기 가능성을 먼저 확인해보세요."

### 8. Story 5.5 Endpoint Priority Boundary Contract

Story 5.4는 endpoint에 대한 **optional hint**만 남기고, Story 5.5가 endpoint에 대한 ranked priority 정보를 처음으로 채운다.

결정 내용:

- Story 5.4의 triage card는 `affectedEndpoint` optional hint를 가질 수 있다.
- Story 5.4는 top-level `endpointPriority=[]`를 유지한다.
- Story 5.4는 endpoint priority ranking, endpoint-specific freshness, endpoint evidence list, endpoint recommended action을 만들지 않는다.
- Story 5.5는 `endpointPriority` typed list를 구현한다.
- Story 5.5의 endpoint priority item은 `rank`, `method`, `route`, `endpointKey`, `reason`, `confidence`, `freshness`, `evidence`, `recommendedAction`을 포함한다.
- Story 5.5는 accepted bucket의 endpoint summary, histogram/error/comparative evidence를 사용해 "어느 endpoint부터 확인할지"에 대한 신뢰 가능한 server-computed ranked evidence를 제공한다.
- 여기서 "확실한 정보"는 root cause 확정이 아니라, 사용자가 다음 확인 지점을 고를 수 있는 bounded evidence와 ranking을 뜻한다.

금지:

- Story 5.4는 endpoint top 1 priority를 임시로 만들지 않는다.
- Story 5.5도 raw path, query string, query key/value, trace id, per-request sample을 노출하지 않는다.
- Story 5.5도 endpoint p95/p99, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.
- Story 5.5의 endpoint priority는 UI나 controller가 계산하지 않고 service layer read model로 제공한다.

결정 이유:

- Application dashboard는 5.4에서 "앱 단위로 볼 만한 신호가 있는가"를 먼저 설명한다.
- Endpoint priority는 사용자가 실제로 "어디부터 확인할지"를 정하는 더 구체적인 surface이므로 5.5에서 freshness/evidence/recommended action과 함께 닫아야 한다.
- 5.4가 endpoint top 1 ranking을 먼저 만들면 5.5의 핵심 책임과 중복된다.

Story seed for 5.5:

1. Story 5.5 story 작성 시 이 결정 문서의 "Story 5.5 Endpoint Priority Boundary Contract"를 명시적으로 참조한다.
2. `endpointPriority`는 empty placeholder에서 typed list로 바뀐다.
3. Endpoint priority item은 slow/error/comparative evidence, confidence, freshness, recommended action을 포함한다.
4. Stale/down freshness에서는 일반 비교형 endpoint ranking을 억제하거나 낮은 confidence로 표현한다.
5. Endpoint evidence는 raw explorer가 아니라 bounded next-check surface로 유지한다.
