---
artifactType: implementation-plan
scope: instance-summary-sot-api
epic: "Epic 13. Dashboard Source of Truth Realignment"
sourceOfTruth: planning-artifacts/source-of-truth/source-of-truth-dashboard-mockup.html
date: 2026-06-12
status: in-progress
schemaMigrationRequired: false
---

# Epic 13 Instance Summary SoT API 계획

## 1. SoT Instance Summary 요구사항

SoT HTML의 Instance Summary row는 `instanceTemplate`에서 단일 행 안에 운영자가 바로 훑어볼 수 있는 compact summary를 표시한다. row 표시 항목은 다음과 같다.

- `instance name`: selected instance 식별자.
- `observed/last heartbeat text`: metric 관측 상태와 starter heartbeat 최신성을 함께 읽을 수 있는 보조 문구.
- `requests`: selected instance의 같은 dashboard window 요청 수.
- `slow >500ms 비율`: selected instance duration bucket evidence에서 서버가 산출한 500ms 초과 비율.
- `contribution/state badge`: `CONTRIBUTING`, `SUPPORTING`, `ATTENTION`, `INSUFFICIENT` 등 application 판단을 설명하는 서버 산출 contribution/state badge.
- `Open modal` 진입점: 기존 wide modal 하나만 연다.

## 2. 현재 실제 앱/read model 차이

현재 frontend `InstancesPanel`은 `ApplicationDashboardReadModel.instances[]`의 `InstanceEntry`를 읽고, `instanceName`, `lastSeenAt`, `links.evidence`만 표시할 수 있다. `requests`, `slow >500ms`, `observationStatus`, `applicationContribution`, `starterConnection`은 per-instance modal의 `InstanceDashboardReadModel`에 존재하지만 목록 DTO에는 없다.

현재 API/read model에 없는 값은 row용 서버 summary block이다. 따라서 frontend가 row에서 metric bucket을 직접 계산하거나, 행별로 modal API를 N+1 호출해 값을 합성하면 안 된다. 특히 lifecycle state, contribution, slow ratio, heartbeat 의미는 서버 read model이 준 값을 표시만 해야 한다.

기존 `implementation-artifacts/epic-13-instance-summary-sot-traceability.md`의 D3는 row를 얇게 유지하는 `SANCTIONED` 결정을 기록했지만, 이번 작업은 그 차이를 해소하기 위해 목록 API를 호환 확장한다. 기존 필드는 유지하고 새 optional/nested field를 추가해 소비자를 깨지 않는다.

## 3. 백엔드 API/read model 계약안

`ApplicationDashboardReadModel.InstanceEntry`에 `summary` field를 추가한다. 기존 `instanceId`, `instanceName`, `lastSeenAt`, `links`는 유지한다.

| 필드 | 의미 | nullable/down/empty 처리 | 서버 산출 근거 |
|---|---|---|---|
| `summary.observationStatus.code` | selected instance metric이 current window에 관측됐는지 | `observed`, `not_observed_in_window`, `metric_missing` 중 하나 | `accepted_metric_buckets` latest bucket end와 dashboard window |
| `summary.observationStatus.reason` | 관측 상태 reason code | 값이 없으면 `null` | 관측 상태 산출 branch |
| `summary.observationStatus.lastObservedBucketEndUtc` | 마지막 instance bucket end | 없으면 `null` | `MetricBucketRepository.findLatestBucketEndUtcByApplicationInstanceIdAtOrBefore` |
| `summary.starterConnection.lastHeartbeatAt` | 해당 instance starter heartbeat 수신 시각 | heartbeat row 없으면 `null` | `StarterHeartbeatTelemetryRepository.findByIdentity` |
| `summary.starterConnection.lastHeartbeatStatus` | heartbeat 상태 code | heartbeat row 없으면 `missing` | starter heartbeat row |
| `summary.starterConnection.freshnessLabel` | row 보조 표시용 heartbeat freshness | heartbeat row 없으면 `missing` | starter heartbeat row, 현재 MVP는 row-level semantic code만 전달 |
| `summary.red.requestCount` | selected instance window request count | 관측 row가 없어도 `0` | `findWindowAggregateByApplicationInstanceId` |
| `summary.red.slowCountOver500ms` | 500ms 초과 request count | duration bucket evidence 없거나 malformed이면 `null` | instance summary duration bucket JSON |
| `summary.red.slowShareOver500ms` | 500ms 초과 비율 | duration bucket evidence 없거나 malformed이면 `null` | 서버 histogram parser 산출 |
| `summary.applicationContribution.level` | application 판단 설명 수준 | metric 미관측이면 `insufficient`; symptom 없으면 `supporting`; request/error/slow symptom이면 `attention` 또는 `contributing` | observation status와 RED signal |
| `summary.applicationContribution.reason` | contribution reason code | 값이 없으면 `null` | contribution 산출 branch |

schema/migration 없이 가능하다. 필요한 source는 이미 `application_instances`, `accepted_metric_buckets`, `starter_heartbeat_telemetry`에 있고, 기존 `InstanceDashboardReadModelService`가 같은 source에서 값을 산출한다. 이번 변경은 DTO/service 조립 계층 확장이다.

## 4. SoT 항목 ↔ API 필드 ↔ 프론트 표시 위치 추적표

| SoT 항목 | API 필드 | 프론트 표시 위치 |
|---|---|---|
| instance name | `instances[].instanceName` | `InstancesPanel` row 좌측 title |
| observed text | `instances[].summary.observationStatus.code`, `lastObservedBucketEndUtc` | row 좌측 subtitle |
| last heartbeat text | `instances[].summary.starterConnection.lastHeartbeatAt`, `lastHeartbeatStatus` | row 좌측 subtitle |
| requests | `instances[].summary.red.requestCount` | row 중앙 `requests` cell |
| slow >500ms | `instances[].summary.red.slowShareOver500ms` | row 중앙 `slow >500ms` cell |
| contribution/state badge | `instances[].summary.applicationContribution.level` | row 우측 outline badge |
| Open modal | `instances[].links.evidence`, live dashboard target ids | row 우측 `Open modal` button |

## 5. 구현 계획

### backend 단계

1. `ApplicationDashboardReadModel.InstanceEntry`에 row summary nested records를 추가한다.
2. `DashboardReadModelService.instanceEntries`가 current dashboard window를 받아 instance별 summary를 서버에서 산출하도록 확장한다.
3. slow ratio parser는 frontend가 재계산하지 않도록 backend에서만 수행한다.
4. schema/migration은 변경하지 않는다.

### frontend 단계

1. `frontend/src/app/lib/read-model-types.ts`에 `InstanceEntry.summary` 타입을 추가한다.
2. read model guard/fixture를 새 계약에 맞춘다.
3. `InstancesPanel` row를 SoT 구조에 맞게 좌측 identity/observed, 중앙 2-cell metric grid, 우측 badge/button으로 재배치한다.
4. null/unavailable은 의미를 바꾸지 않고 `확인 불가`, `미관측`, `missing` 등 표시용 copy로만 변환한다.

### Chrome MCP 비교 단계

1. SoT HTML과 실제 앱을 desktop `1440x1000`에서 연다.
2. Instance Summary header, row border/radius/shadow, compact grid, typography, requests/slow cell, badge, `Open modal`, wide modal 폭을 비교한다.
3. narrow Sheet, trend modal, stored/projection trend 진입점이 되살아나지 않았는지 확인한다.

### 테스트/검증 단계

1. `./gradlew :observability-portal:test`
2. `cd frontend && npm run typecheck`
3. `cd frontend && npm run guard:read-model-contract`
4. `git diff --check`

## 6. 비목표

- Stored trend/projection trend/`InstanceTrendView` surface를 복구하지 않는다.
- narrow Sheet를 복구하지 않는다.
- frontend에서 metric, lifecycle state, contribution, slow ratio를 합성하지 않는다.
- `DialogContent` wide modal의 `sm:max-w-none`을 제거하지 않는다.
- backend schema/migration은 사용자 승인 없이 변경하지 않는다.

## 7. 남은 리스크/확인 필요사항

- row별 metric summary 조회가 instance 수만큼 추가된다. 현재 목록 cap은 50개이므로 기능적으로 안전하지만, 운영 규모가 커지면 batch aggregate query가 후속 최적화 후보가 된다.
- `contributing/supporting/attention`의 정확한 product wording은 SoT demo data와 production reason code가 다를 수 있다. 이번 구현은 서버 `applicationContribution.level`을 그대로 badge로 표시한다.
- heartbeat freshness는 현재 heartbeat row의 존재와 상태를 표시한다. 별도 stale/down 시간 정책을 row summary에 더 세밀하게 넣으려면 backend 정책 결정이 추가로 필요하다.
