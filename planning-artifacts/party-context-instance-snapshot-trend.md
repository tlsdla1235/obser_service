---
artifactType: party-context
topic: instance-snapshot-trend
status: draft
date: 2026-05-25
---

# Instance Snapshot Trend Party Context

## 사용자 의도

사용자는 application dashboard와 instance evidence drill-down 흐름은 유지하되, 문제가 있을 때만이 아니라 특정 instance의 변화 추이를 일정 기간 동안 보는 탭이 UX적으로 필요하다고 본다.

현재 prototype의 `Instance 7d Snapshots` 탭은 후보였고, 사용자는 이 탭을 외부 monitoring service들의 일반 패턴에 맞춰 타당성을 조사한 뒤, 타당하면 계약에 반영하길 원한다.

## 현재 닫힌 계약

- accepted bucket은 metric freshness/state/read model의 source-of-truth다.
- starter heartbeat는 accepted bucket과 분리된 control-plane/liveness source다.
- UI는 lifecycle state, rule, p95/p99, endpoint priority, snapshot/history event를 재계산하지 않는다.
- application dashboard가 primary first-screen이다.
- instance detail은 application 판단을 대체하지 않는 evidence drill-down이다.
- `dashboard_snapshots`는 application별 1시간 scheduled snapshot을 기본으로 하는 coarse-grained read model history다.
- `dashboard_snapshots` 기본 retention은 14일이다.
- snapshot detail은 저장 당시 read model을 보여주며 current state를 재판정하지 않는다.
- raw bucket explorer, arbitrary time-series query, endpoint timeseries table은 MVP non-goal이다.
- p95/p99는 source/scope가 있는 starter canonical percentile point로만 표시하고, 여러 point를 평균/병합해 상위 scope percentile을 만들지 않는다.

## 계약 공백

- 별도 instance snapshot/history API 또는 cadence는 아직 잠겨 있지 않다.
- current contracts는 application dashboard snapshot에 bounded endpoint evidence를 남길 수 있다고 명시하지만, bounded instance evidence history는 명시하지 않는다.
- instance detail read model 후보는 current 15분 evidence에 가까우며, 7일/1주일 horizon의 trend page는 아직 없다.

## 외부 서비스 조사 요약

- Datadog은 service/resource page에서 requests, errors, latency를 selected time frame 기준 timeseries로 보여주고, resource 목록에서 request/latency/error 기준으로 비교/드릴다운한다. Infrastructure List는 host detail panel에서 metrics, containers, logs 등을 확인하게 한다.
- New Relic Hosts UI는 host 성능 overview와 chart/table/time picker를 제공하며, host table에서 특정 host detail로 들어가는 흐름을 둔다.
- Elastic Observability Hosts는 host list와 host detail overlay를 제공하고, CPU/load/memory 같은 key metric overview와 host details를 시간 범위 기준으로 본다. host limit과 query performance caveat를 명시한다.
- Dynatrace는 process group/process instance 개념을 제공하고, process instance snapshot은 event-triggered로 10분 전후 metrics report를 cluster로 보낼 수 있다.
- Sentry는 infra instance monitoring보다는 release/session/transaction health 중심이지만, release health statistics도 time-series API, interval, statsPeriod, point limit을 둔다.

공식 source:

- Datadog Service Page: https://docs.datadoghq.com/tracing/services/service_page/
- Datadog Infrastructure List: https://docs.datadoghq.com/infrastructure/list/
- New Relic Infrastructure Hosts UI: https://docs.newrelic.com/docs/infrastructure/infrastructure-data/infrastructure-ui-pages/infra-hosts-ui-page/
- Elastic Hosts: https://www.elastic.co/docs/solutions/observability/infra-and-hosts/analyze-compare-hosts/
- Dynatrace Host-level settings / Process instance snapshots: https://docs.dynatrace.com/docs/observe/infrastructure-observability/hosts/configuration
- Sentry Release Health Session Statistics: https://docs.sentry.io/api/releases/retrieve-release-health-session-statistics/

## 검토할 후보

`Instance Snapshot Trend`를 Epic 5/6 후보로 추가한다.

권장 의미:

- URL 후보: `GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/snapshot-trend?since=7d&limit=168`
- source: 저장된 application dashboard snapshot read model 또는 그 안의 bounded instance summary
- default horizon: 7일
- max horizon: `dashboard_snapshots` retention 안에서 clamp, MVP는 최대 14일
- default cadence: hourly scheduled snapshot plus meaningful event capture
- response item: snapshotId, capturedAt, captureReason, stored stateCode, instance identity, lastAcceptedBucketAt, lastHeartbeatAt, metric freshness status, starter connection status, contributionToApplicationTriage, source-scoped starter percentile latest point, resource hints, bounded endpoint evidence reference
- UI: instance dashboard가 아니라 stored evidence trend. current state를 재판정하지 않음.

## 검증 질문

1. UX 관점에서 문제가 없을 때도 instance 변화 추이를 보여주는 탭이 필요한가?
2. 이 기능이 raw explorer/non-goal과 충돌하지 않으려면 어떤 제약이 필요할까?
3. 기존 `dashboard_snapshots`를 확장하는 방식이 MVP 구현 가능성이 있는가?
4. 7일 horizon은 적절한 default인가, 아니면 time range selector로 둬야 하는가?
5. 계약 문서에 넣는다면 read-model/API/history 중 어디에 어떤 수준으로 넣어야 하는가?
