---
artifactType: contract
name: read-model-contract
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# Contract - Read Model MVC Version

## 1. 역할

`read-model-contract`는 first-screen UI의 단일 source of truth다.

UI는 이 응답을 표시할 뿐 lifecycle state, zero-insight reason, insight rule, endpoint priority, p95/p99를 재계산하지 않는다. 이 계약은 `state-semantics`, `time-buckets`, `insight-rules`, `histogram-merge`의 결과를 한 응답으로 묶는 구현 기준이다.

### 1.1 Current Response와 Snapshot 저장 경계

Dashboard current response는 query 시점 기준 `current 15분`과 직전 `baseline 15분`을 비교해 만든 현재 상태 read model이다. `state`, `metrics.p95Ms`, `triageCards`, `endpointPriority`는 UI가 계산하지 않고 `DashboardReadModelService`와 하위 service 결과를 그대로 표시한다.

`dashboard_snapshots`는 spike 전용 저장소가 아니라 특정 시점의 dashboard read model 전체를 저장하는 coarse-grained history 저장소다. MVP 기본 저장 cadence는 application별 `1시간 scheduled snapshot`이며, ingest로 수용된 30초 bucket마다 dashboard snapshot을 만들지 않는다.

Dashboard query 시 최신 snapshot이 없거나 current response로 쓰기에 명백히 오래된 경우 `DashboardReadModelService`가 fallback으로 현재 read model을 재생성하고, 필요하면 snapshot으로 저장할 수 있다. 이 fallback은 current 판단 기준을 바꾸지 않으며, `accepted_metric_buckets`를 dashboard snapshot으로 복제하는 경로도 아니다.

`accepted_metric_buckets`는 30초 단위 계산 원천이고 짧은 retention을 가진다. `dashboard_snapshots.read_model_json`은 그 시점에 UI가 소비한 bounded endpoint summary, evidence, `endpointPriority`를 포함할 수 있다. raw bucket retention이 지난 뒤에는 snapshot에 남은 bounded endpoint evidence까지만 history/detail에서 보여준다.

### 1.2 Snapshot Capture, Retention, and Evidence Boundary

`dashboard_snapshots` 기본 retention은 `14일`이다. 이 값은 운영 비용과 demo 요구에 맞춰 config로 조정 가능해야 하며, `accepted_metric_buckets`의 짧은 retention 정책과 의미를 섞지 않는다.

기본 저장은 application별 `1시간 scheduled snapshot`이다. 추가 snapshot row는 아래 조건에서만 남긴다.

1. `state_code`가 hysteresis 이후 의미 있게 변경됨: 예를 들어 `active -> degraded`, `degraded -> active`, stale/down/recovery 계열 변화
2. high-confidence concern: confidence `>= 0.82`, rule guard 통과, suppression window 밖
3. 짧지만 강한 spike 실험값: confidence `>= 0.90`이고 최근 5개 30초 bucket 중 2개 이상 bad
4. dashboard query fallback으로 read model을 재생성했고 최신/current response용 snapshot이 없거나 명백히 오래된 경우

이 capture 정책은 30초 dashboard snapshot 장기 보관이 아니다. 사용자가 30분~1시간 뒤 들어왔을 때 최근 운영 흐름을 이해할 수 있도록 coarse-grained read model history를 보강하는 bounded capture다.

`dashboard_snapshots`에 둘 수 있는 bounded index/search helper column 후보는 `capture_reason`, `primary_rule_id`, `primary_endpoint_key`, `max_confidence`, `state_code`, `generated_at`, `current_window_end_utc`로 고정한다. 이 값은 저장된 read model 검색 편의를 위한 복사값이며 raw metric, endpoint timeseries, 별도 event store가 아니다.

Snapshot `read_model_json` 안에 남기는 endpoint evidence는 최대 `10개`다. 우선순위는 top triage card에 연결된 endpoint, `endpointPriority` 상위 항목, high-confidence concern endpoint 순서다. Evidence field 후보는 `method`, `route`, `endpointKey`, `rank`, `reason`, `ruleIds`, `confidence`, `requestCount`, `errorRate`, `p95Ms`, `baselineP95Ms`, guard 통과 시 `p99Ms`, `tailLatencyEvidence`, `freshness`, `recommendedAction`이다. raw path, query string, query key/value, trace id, per-request sample은 포함하지 않는다.

## 2. Response Shape

```json
{
  "generatedAt": "2026-05-08T01:10:35Z",
  "snapshot": {
    "snapshotId": "018f6b9a-2e1a-7d2b-9b2f-4db69d92c241",
    "links": {
      "selfSnapshot": "/api/projects/{projectId}/applications/{applicationId}/dashboard/snapshots/{snapshotId}"
    }
  },
  "application": {
    "name": "orders-api",
    "environment": "prod",
    "lastAcceptedIngestAt": "2026-05-08T01:10:30Z",
    "lastHealthyAt": "2026-05-08T01:08:30Z",
    "sourceWindow": {
      "current": {
        "startUtc": "2026-05-08T00:55:30Z",
        "endUtc": "2026-05-08T01:10:30Z"
      },
      "baseline": {
        "startUtc": "2026-05-08T00:40:30Z",
        "endUtc": "2026-05-08T00:55:30Z"
      }
    },
    "freshness": {
      "lastObservedAt": "2026-05-08T01:10:30Z",
      "staleAt": "2026-05-08T01:12:00Z",
      "downAt": "2026-05-08T01:13:30Z"
    }
  },
  "state": {
    "code": "degraded",
    "label": "확인이 필요합니다",
    "rationale": "오류율과 p95가 함께 증가했습니다.",
    "recommendedAction": "먼저 POST /orders endpoint의 최근 변경과 외부 의존성을 확인하세요.",
    "scope": "application"
  },
  "zeroInsight": null,
  "recovery": {
    "isRecovering": false,
    "lastRecoveredAt": null,
    "retryAfterSeconds": null,
    "recommendedAction": null
  },
  "metrics": {
    "requestCount": 42000,
    "errorRate": 0.031,
    "p95Ms": 480,
    "p95Source": "server_histogram_merge"
  },
  "triageCards": [
    {
      "ruleId": "endpoint_error_spike",
      "severity": "warning",
      "title": "POST /orders 오류율 증가",
      "summary": "baseline 대비 오류율이 증가했습니다.",
      "recommendation": "이 endpoint의 최근 배포와 외부 의존성을 먼저 확인해보세요.",
      "confidence": 0.82,
      "affectedEndpoint": "POST /orders",
      "evidence": {
        "currentErrorRate": 0.064,
        "baselineErrorRate": 0.018,
        "requestCount": 12000
      }
    }
  ],
  "endpointPriority": [
    {
      "method": "POST",
      "route": "/orders",
      "endpointKey": "POST /orders",
      "rank": 1,
      "reason": "error_and_latency",
      "confidence": 0.84,
      "freshness": {
        "lastObservedAt": "2026-05-08T01:10:30Z",
        "sourceWindow": "current"
      },
      "evidence": {
        "requestCount": 12000,
        "errorRate": 0.064,
        "p95Ms": 720,
        "baselineP95Ms": 250
      },
      "recommendedAction": "이 endpoint의 error log와 dependency latency를 먼저 확인하세요."
    }
  ]
}
```

## 3. Zero-Insight Contract

`triageCards`가 빈 배열이면 `zeroInsight`는 반드시 non-null이다.

허용 reason code:

| Code | 의미 | UI 문구 방향 |
|---|---|---|
| `no_action_needed` | freshness와 sample은 충분하지만 노출할 concern이 없다 | 현재 우선 조치 없음 |
| `insufficient_sample` | 판단에 필요한 요청 수가 부족하다 | 더 많은 요청 또는 다음 bucket 대기 |
| `waiting_first_data` | 아직 accepted bucket이 없다 | starter 설정과 앱 실행 상태 확인 |
| `observing_recovery` | stale/down 이후 회복 관찰 중이다 | 다음 bucket까지 대기하며 ingest 경로 확인 |

예시:

```json
{
  "zeroInsight": {
    "reasonCode": "no_action_needed",
    "message": "현재 우선 조치가 필요한 신호는 없습니다.",
    "recommendedAction": "트래픽이 유지되는지 다음 bucket까지 관찰하세요."
  },
  "triageCards": []
}
```

## 4. Recovery and Freshness Requirements

- 모든 response는 `generatedAt`을 포함한다.
- application freshness는 `lastObservedAt`, `staleAt`, `downAt`을 포함한다.
- stale/down/degraded state는 `state.recommendedAction`을 포함한다.
- endpoint priority item은 endpoint-level `freshness`, `evidence`, `confidence`, `recommendedAction`을 포함한다.
- `lastHealthyAt`은 stale/down/recovery 안내에 사용한다. 값이 없으면 UI는 "이전 정상 시점 없음"으로 표시한다.

## 5. Route Attribution Epic 5 Note

Epic 2의 B안 route attribution은 read model에 raw path, raw path candidate, query string, query key/value를 전달하지 않는다. Epic 5에서 endpoint priority item에 attribution 설명이 필요하면 raw detail 없는 bounded enum만 검토한다.

후보 shape:

```json
"routeAttribution": {
  "source": "framework_route",
  "availability": "available"
}
```

허용 source 후보는 `framework_route`, `allowlist_path_match`, `unavailable`로 제한한다. MVP에서 이 enum propagation이 과하다고 판단되면 Epic 5까지 보류하고, read model은 `route: "UNKNOWN"`만으로 unavailable 상태를 표현한다.

## 6. Post-MVP Runtime Aggregate Evidence Shape

MVP read model은 JVM/datasource runtime ratio를 latest sample 기반 saturation hint로만 사용할 수 있다. Post-MVP runtime aggregate가 ingest/persistence에 추가되면 read model evidence는 `latest`, `max`, `avg`, `sampleCount`를 구분해서 노출할 수 있다.

후보 shape:

```json
"runtimeSaturation": {
  "datasourcePoolUsage": {
    "latest": 0.82,
    "max": 0.97,
    "avg": 0.88,
    "sampleCount": 6,
    "evidenceRole": "peak_and_sustained_pressure"
  },
  "cpuUsage": {
    "latest": 0.64,
    "max": 0.91,
    "avg": 0.70,
    "sampleCount": 6,
    "evidenceRole": "supporting_saturation_hint"
  }
}
```

이 후보 field는 `TriageSummaryService` 또는 `EndpointPriorityService`가 만든 evidence를 UI가 그대로 표시하기 위한 것이다. UI는 `max`나 `avg`로 degraded/down을 재판정하지 않는다.

## 7. Operational Event History Boundary

Dashboard read model은 현재 상태 source of truth다.

Operational event history는 current dashboard response 안에서 재계산하지 않는다. 큰 history 배열을 current dashboard response에 넣지 않고, 별도 history read model/API가 저장된 dashboard snapshot 또는 read model 결과를 기반으로 bounded event 목록을 제공한다.

History service는 `dashboard_snapshots.state_code`, `generated_at`, `current_window_end_utc`, `read_model_json`을 우선 사용한다. 필요한 경우 `capture_reason`, `primary_rule_id`, `primary_endpoint_key`, `max_confidence` 같은 bounded index/search helper column을 사용한다. 그 column은 저장된 read model의 검색 편의를 위한 복사 값이어야 하며 raw metric이나 별도 endpoint timeseries를 대체 저장하는 용도가 아니다.

Current response가 snapshot deep link를 지원해야 할 때는 `snapshot.snapshotId`와 `snapshot.links.selfSnapshot` 같은 식별자 경계만 둔다. Snapshot detail은 저장된 read model을 보여주는 경로이며 current state를 재판정하지 않는다.

UI는 operational event를 표시하더라도 state/rule/p95/p99/endpoint priority를 계산하지 않는다.

## 8. MVC Boundary Rules

- `DashboardReadModelService`가 이 응답을 구성한다.
- `DashboardController`는 serialization과 HTTP status mapping만 담당한다.
- PostgreSQL view는 이 응답을 계산하지 않는다.
- frontend는 이 응답을 기준으로 화면을 구성하고, 별도 rule engine을 갖지 않는다.
- p95/p99는 `histogram-merge` contract의 server-side service 결과만 사용한다.
- Operational event history service 후보는 이 응답의 저장 결과를 읽어 event surface를 만들 수 있지만 current read model의 판단을 덮어쓰지 않는다.
