# Story 4.3 Recovery Guidance Contract Decisions

Status: decided
Date: 2026-05-24
Scope: Story 4.3 pre-story contract closure

## Purpose

Story 4.3는 stale/down 이후 회복 관찰 구간을 사용자가 과잉 진단하지 않도록 설명하는 recovery guidance 계약을 닫는다.

이 문서는 독립 story 파일을 만들기 전에 외부 observability 제품의 UX/alerting 패턴, BMAD party 검증, 그리고 이 프로젝트에 적용할 최종 결정을 한 곳에 남긴다.

## External Product Evidence

외부 솔루션들은 이름과 구현은 다르지만 공통적으로 "데이터 없음", "신호 유실", "모니터링 불가", "회복 관찰"을 실제 서비스 장애 확정과 분리한다.

| Product | Observed pattern | Project takeaway |
|---|---|---|
| Grafana Alerting | `No Data`, `Error`, `Keep Last State`, `Recovering`, state reason annotation을 분리한다. | metric data 공백과 평가/표시 이유를 별도 field로 드러낸다. |
| Datadog Monitors | missing data 동작을 `NO DATA`, `OK`, last known status 등으로 선택하고 recovery threshold로 flapping을 줄인다. | no data는 0이나 정상으로 암묵 변환하지 않고, recovery는 별도 조건으로 닫는다. |
| New Relic Alerts | loss of signal은 expiration duration과 별도 action으로 다루고, recovery period가 지나야 close한다. | accepted bucket 부재와 heartbeat 부재를 별도 signal로 보고, 회복 완료를 즉시 선언하지 않는다. |
| Sentry Uptime/Crons | uptime은 연속 실패와 `Unknown`을 사용하고, cron monitor는 failure/recovery threshold를 둔다. | 단발 신호로 장애/회복을 단정하지 않고 연속성 또는 관찰 구간을 둔다. |
| Honeycomb Triggers | triggered 이후 OK 복귀 시 resolved notification을 보내며, no data와 0 혼동이 false positive를 만들 수 있음을 경고한다. | sample 부족과 0 traffic을 같은 의미로 합치지 않는다. |
| Elastic Alerts | `active`, `flapping`, `recovered`를 분리하고 flapping recovery에는 연속 unmet run을 요구한다. | recovery 종료 조건을 명시해 흔들림을 줄인다. |
| Dynatrace | `Monitoring unavailable`과 host unavailable을 분리하고 개별 host unavailable 문제를 억제한다. | starter/portal monitoring 공백은 host application down 확정이 아니라 telemetry surface 문제로 표현한다. |
| Prometheus | `for`, `keep_firing_for`, `absent_over_time`로 pending, keep-firing, missing series를 분리한다. | state 판단과 notification/recovery guidance를 분리한다. |

Source references:

- Grafana: https://grafana.com/docs/grafana/latest/alerting/fundamentals/alert-rule-evaluation/nodata-and-error-states/
- Datadog recovery thresholds: https://docs.datadoghq.com/monitors/guide/recovery-thresholds/
- Datadog missing data: https://docs.datadoghq.com/monitors/configuration/?tabs=thresholdalert
- New Relic alert conditions: https://docs.newrelic.com/docs/alerts/create-alert/create-alert-condition/alert-conditions/
- New Relic loss of signal: https://docs.newrelic.com/docs/alerts/create-alert/create-alert-condition/create-nrql-alert-conditions/
- Sentry uptime: https://docs.sentry.dev/product/uptime-monitoring/
- Sentry cron thresholds: https://sentry.io/changelog/2023-12-20-margins-and-thresholds-for-cron-monitors/
- Honeycomb triggers: https://docs.honeycomb.io/notify/triggers/create
- Honeycomb no data vs 0: https://support.honeycomb.io/articles/3221199671-my-trigger-alerted-me-when-it-shouldn-t-have-issues-with-no-data-vs-0-values
- Elastic alert statuses: https://www.elastic.co/docs/explore-analyze/alerting/alerts/view-alerts
- Dynatrace monitoring unavailable: https://docs.dynatrace.com/docs/dynatrace-intelligence/root-cause-analysis/event-analysis-and-correlation/event-categories/monitoring-unavailable-events
- Prometheus alerting rules: https://prometheus.io/docs/prometheus/2.53/configuration/alerting_rules/

## BMAD Party Validation

BMAD party 검증 결과는 8개 결정을 모두 채택하되, recovery 종료 조건을 acceptance constraint로 추가하라는 결론이다.

- Sally/UX: "복구됨"보다 "복구 관찰 중"이 안전하다. `retryAfterSeconds`는 자동 재시도 약속처럼 보이지 않게 "다음 판단까지 약 30초"로 표현한다.
- Winston/Architect: `RecoveryGuidance`는 `LifecycleStateDecision`/service 경계에 두고 API/UI/repository로 확장하지 않는다. recovery 종료 조건을 명시해야 flapping과 first data waiting이 섞이지 않는다.
- Amelia/Dev: 이전 상태와 이전 healthy timestamp는 service 내부 조회가 아니라 typed input으로 전달해야 테스트가 안정적이다.
- John/PM: 8개 결정은 Story 4.3 전에 닫아야 할 제품 결정이다. 첫 데이터 대기, recovery, starter liveness copy를 분리해야 한다.

## Closed Decisions

### 1. Recovery Trigger

Recovery는 아래 조건을 모두 만족할 때만 활성화한다.

1. 이전 metric state가 `stale` 또는 `down`이다.
2. 현재 accepted bucket freshness가 `current`다.
3. 현재 sample readiness가 `insufficient`다.

`waiting_first_data -> unknown`은 recovery가 아니다. `degraded -> active` 또는 `degraded` 해소는 Story 4.2의 degraded hysteresis 범위이며 Story 4.3 recovery guidance와 섞지 않는다.

### 2. Recovery End Condition

Recovery trigger가 더 이상 성립하지 않으면 `isRecovering=false`다.

특히 현재 freshness가 `current`이고 sample readiness가 `sufficient`이면 recovery는 종료된다. 현재 freshness가 다시 stale/down 후보가 되면 recovery가 아니라 stale/down metric state로 표현한다.

### 3. Recovery Field Shape

Read model 후보 field는 아래 shape로 고정한다.

```json
{
  "recovery": {
    "isRecovering": true,
    "lastHealthyAt": "2026-05-08T01:08:30Z",
    "retryAfterSeconds": 30,
    "recommendedAction": "다음 판단까지 약 30초 기다린 뒤 accepted bucket 수용과 sample 증가를 확인하세요."
  }
}
```

`lastRecoveredAt`은 Story 4.3에서 사용하지 않는다. 회복 완료 시각은 snapshot/history/event 성격이므로 Epic 5/6 후보로 남긴다.

### 4. `lastHealthyAt` Source

`lastHealthyAt`은 현재 요청에서 추론하지 않는다.

허용 source는 이전 read model/snapshot이 기록한 마지막 healthy 시각뿐이다. 값이 없으면 `null`을 허용하고 UI는 "이전 정상 시점 확인 불가"로 표시할 수 있다.

### 5. `retryAfterSeconds`

`isRecovering=true`일 때 `retryAfterSeconds=30`으로 고정한다.

이 값은 30초 bucket/drain cadence에 맞춘 "다음 판단까지 대기" 힌트다. 자동 재시도 예약이나 background job 약속이 아니다. Recovery가 아니면 `null`이다.

### 6. Zero-Insight Mapping

`triageCards`가 비어 있고 recovery가 활성화된 경우 `zeroInsight.reasonCode=observing_recovery`를 사용한다.

Starter connection diagnosis와 zero-insight reason은 아래처럼 매핑한다.

| Starter/metric situation | zeroInsight reason |
|---|---|
| starter connected but no accepted bucket | `waiting_first_data` |
| no recent traffic 또는 metric data idle | `metric_data_idle` |
| heartbeat도 없고 accepted bucket도 오래됨/없음 | `telemetry_unreachable` |
| recovery 활성화 | `observing_recovery` |

`no_recent_traffic`은 starter connection diagnosis로는 유지할 수 있지만, MVP zeroInsight reason code로는 새로 추가하지 않는다.

### 7. Copy Precedence

Metric recovery guidance와 starter connection guidance는 별도 copy surface로 유지한다.

Metric recovery copy는 accepted bucket freshness/sample 부족만 설명한다. Starter connection copy는 heartbeat freshness/reachability만 설명한다. 두 copy를 합쳐 host application down, host process down, 앱 내려감 같은 확정 표현을 만들지 않는다.

### 8. Implementation Boundary

Story 4.3 구현 범위는 `domain.state.model`과 `domain.state.service.LifecycleStateService` 주변의 typed model/service logic으로 제한한다.

허용:

- `RecoveryGuidance` 또는 동등 record 추가
- `LifecycleStateDecision`에 recovery field 추가
- `LifecycleStateService`가 typed input만 사용해 recovery guidance 생성
- focused unit test와 MVC boundary regression test

비허용:

- repository 조회 또는 persistence 추가
- dashboard API/controller/UI 구현
- dashboard snapshot/history/event 저장 구현
- heartbeat를 accepted bucket freshness source로 사용
- `lastHealthyAt`을 현재 accepted bucket만으로 추론

### 9. User-Facing Copy

외부 code 값은 snake_case 영어를 유지한다. 사용자-facing `label`, `rationale`, `recommendedAction`은 한국어를 사용한다.

권장 copy:

| Code/context | Label | Rationale direction | Action direction |
|---|---|---|---|
| `observing_recovery` | 복구 관찰 중 | 현재 metric data는 다시 수신됐지만 sample이 부족해 안정 여부를 판단할 수 없다. | 다음 판단까지 약 30초 기다린 뒤 accepted bucket 수용과 sample 증가를 확인한다. |
| `waiting_first_data` | 첫 데이터 대기 중 | starter가 연결됐더라도 아직 accepted bucket이 없다. | 요청 traffic을 발생시키고 첫 bucket 수용 여부를 확인한다. |
| `metric_data_idle` | 최근 트래픽 없음 | 최근 처리량이 없어 상태 판단을 보류한다. | traffic 유입 또는 bucket 생성 조건을 확인한다. |
| `down` | 메트릭 수집 경로 확인 불가 | metric data-plane 기준의 미도달 상태이며 host application down 확정이 아니다. | starter 연결, accepted bucket 수용 경로, 최근 traffic 여부를 함께 확인한다. |

## Story 4.3 Acceptance Seed

Story 4.3 story 파일은 아래 seed를 acceptance criteria로 가져갈 수 있다.

1. 이전 metric state가 `stale` 또는 `down`이고 현재 freshness가 current지만 sample이 insufficient이면 recovery guidance는 `isRecovering=true`, `retryAfterSeconds=30`, `recommendedAction`을 제공한다.
2. 이전 state가 없거나 `waiting_first_data`이면 recovery가 아니다.
3. 현재 freshness가 current이고 sample이 sufficient이면 recovery는 종료된다.
4. `lastHealthyAt`은 이전 healthy read model/snapshot에서 받은 값만 보존하고, 없으면 `null`이다.
5. `lastRecoveredAt` field는 Story 4.3 read model/model에 추가하지 않는다.
6. recovery 활성화와 triage card 없음 조합은 `zeroInsight.reasonCode=observing_recovery`로 표현한다.
7. `no_recent_traffic` diagnosis는 zeroInsight reason으로 새 code를 만들지 않고 `metric_data_idle`로 수렴한다.
8. Metric recovery guidance와 starter connection guidance는 서로의 source-of-truth를 침범하지 않는다.
9. 어떤 label/rationale/action도 host application down을 확정하지 않는다.
10. Controller, repository, UI, persistence에는 recovery 재판정 로직을 추가하지 않는다.
