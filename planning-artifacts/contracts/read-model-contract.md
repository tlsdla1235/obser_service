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

UI는 이 응답을 표시할 뿐 lifecycle state, zero-insight reason, insight rule, endpoint priority, p95/p99를 재계산하지 않는다. 이 계약은 `state-semantics`, `time-buckets`, `insight-rules`, starter canonical percentile, histogram bucket distribution 결과를 한 응답으로 묶는 구현 기준이다.

Application metric freshness/state/read-model의 source-of-truth는 accepted bucket이다. Starter heartbeat는 별도 connection/liveness surface이며 starter/application process liveness, portal reachability, project key validity, metadata validity의 control-plane source다. Heartbeat 성공 또는 미수신은 accepted bucket freshness, host business health/degraded 판단, operational event, dashboard snapshot 생성을 직접 만들거나 암시하지 않는다.

요청이 없어 bucket이 오지 않는 상황은 host application down이 아니다. Read model은 accepted bucket axis와 heartbeat axis를 분리해, heartbeat가 최근 수신됐지만 bucket이 없거나 오래된 경우 `starter connected but no accepted bucket`, `waiting for traffic`, `metric data idle` 계열 copy를 사용할 수 있다. 두 축이 모두 오래된 경우에도 host down 확정 대신 `starter disconnected`, `telemetry unreachable`, `unknown` 계열로 표현한다.

### 1.1 Current Response와 Snapshot 저장 경계

Dashboard current response는 query 시점 기준 `current 15분`과 직전 `baseline 15분`을 비교해 만든 현재 상태 read model이다. `state`, 기본 `metrics`, source-scoped starter percentile, `triageCards`, `endpointPriority`는 UI가 계산하지 않고 `DashboardReadModelService`와 하위 service 결과를 그대로 표시한다.

Dashboard는 같은 scope에 둘 이상의 p95/p99 source를 두지 않는다. Starter canonical percentile을 표시하는 scope에서는 histogram-derived p95/p99를 병렬 표시하지 않는다. 여러 starter instance 값이 같은 app/project/window에서 서로 다르면 service는 새 p95/p99를 평균/병합하지 않고 instance/source 단위로 노출하거나, 상위 scope에는 percentile 대신 bucket distribution을 표시한다.

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

Snapshot `read_model_json` 안에 남기는 endpoint evidence는 최대 `10개`다. 우선순위는 top triage card에 연결된 endpoint, `endpointPriority` 상위 항목, high-confidence concern endpoint 순서다. Endpoint evidence field 후보는 `method`, `route`, `endpointKey`, `rank`, `reason`, `ruleIds`, `confidence`, `requestCount`, `errorRate`, `durationBuckets`, `baselineDurationBuckets`, `bucketDistributionSource`, `freshness`, `recommendedAction`이다. raw path, query string, query key/value, trace id, per-request sample, endpoint p95/p99는 포함하지 않는다.

Snapshot `read_model_json`은 instance snapshot trend를 위해 bounded instance summary를 포함할 수 있다. 기본 cap은 snapshot당 `50개` instance summary이며, 우선순위는 application triage에 기여한 instance, stale/down/recovery freshness에 관련된 instance, request count가 높은 active instance 순서다. 이 field는 instance trend projection을 위한 stored summary이며 raw instance timeseries나 별도 metric store가 아니다.

Snapshot detail의 세부 shape는 Epic 5의 `Snapshot Marker and Bounded Tail Summary Contract` 후속 story에서 확정한다. 이 계약에서는 저장 당시 read model과 bounded evidence를 보여주며 current state를 재판정하지 않는다는 원칙만 유지한다.

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
    "lastAcceptedBucketAt": "2026-05-08T01:10:30Z",
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
    "rationale": "오류율과 source-scoped starter-reported p95가 함께 증가했습니다.",
    "recommendedAction": "먼저 POST /orders endpoint의 최근 변경과 외부 의존성을 확인하세요.",
    "scope": "application"
  },
  "starterConnection": {
    "statusSource": "starter_heartbeat",
    "lastHeartbeatAt": "2026-05-08T01:10:20Z",
    "lastHeartbeatStatus": "received",
    "connectionMeaning": "starter_connected",
    "stateImpact": "none"
  },
  "zeroInsight": null,
  "recovery": {
    "isRecovering": false,
    "lastHealthyAt": null,
    "retryAfterSeconds": null,
    "recommendedAction": null
  },
  "metrics": {
    "requestCount": 42000,
    "errorRate": 0.031
  },
  "sourceScopedPercentiles": {
    "source": "starter_canonical_percentile",
    "scope": "instance_bucket",
    "displayPolicy": "source_scoped_points",
    "aggregatePolicy": "no_average_no_max_no_merge_no_histogram_recalculation",
    "items": [
      {
        "application": "orders-api",
        "environment": "prod",
        "instance": "orders-api-7f9c9c8c9d-x2p4k",
        "bucketStartUtc": "2026-05-08T01:10:00Z",
        "bucketEndUtc": "2026-05-08T01:10:30Z",
        "requestCount": 1200,
        "p95Ms": 480,
        "p99Ms": 960
      }
    ],
    "applicationScopeFallback": "bucket_distribution_only_when_multiple_sources"
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
        "durationBuckets": [
          { "leMs": 250, "count": 5200 },
          { "leMs": 500, "count": 9800 },
          { "leMs": 1000, "count": 11800 },
          { "leMs": 2000, "count": 12000 }
        ],
        "bucketDistributionSource": "histogram_bucket_distribution"
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
| `waiting_first_data` | 아직 accepted bucket이 없다 | heartbeat가 최근이면 traffic 대기, heartbeat도 없으면 starter 연결 확인 |
| `metric_data_idle` | starter heartbeat는 최근 수신됐지만 accepted bucket이 없거나 오래됐다 | 요청 없음 또는 bucket 생성 대기 |
| `telemetry_unreachable` | heartbeat도 오래됐고 accepted bucket도 오래됐거나 없다 | starter/portal/network 연결 확인. host application down은 미확정 |
| `observing_recovery` | stale/down 이후 회복 관찰 중이다 | 다음 bucket까지 대기하며 ingest 경로 확인 |

`no_recent_traffic`은 starter connection diagnosis로는 사용할 수 있지만 MVP zeroInsight reason code로는 새로 추가하지 않는다. `triageCards=[]`이고 최근 heartbeat와 오래된/없는 accepted bucket 조합이 traffic 부재를 가리키면 `metric_data_idle`로 수렴한다.

Recovery 활성화와 `triageCards=[]` 조합은 `observing_recovery`를 우선한다. 이는 "복구 완료"가 아니라 "복구 관찰 중"을 뜻한다.

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
- application metadata의 `lastAcceptedBucketAt`은 마지막 accepted bucket 기준 시각이다.
- `starterConnection`은 heartbeat telemetry를 표시할 때만 사용하는 별도 축이며 metric state를 직접 바꾸지 않는다. `stateImpact = none`은 metric state를 바꾸지 않는 diagnostic/control-plane 정보라는 뜻이다.
- 최근 heartbeat와 없음/오래된 accepted bucket 조합은 no recent traffic/waiting for traffic/metric data idle 계열로 표현한다.
- heartbeat도 없고 accepted bucket도 오래된 조합은 telemetry unreachable/unknown 계열로 표현하며 host application down을 확정하지 않는다.
- `recovery.isRecovering=true`는 이전 metric state가 `stale` 또는 `down`이고 현재 accepted bucket freshness는 current지만 sample이 insufficient인 경우에만 사용한다.
- `recovery.isRecovering=false`는 recovery trigger가 성립하지 않는 모든 경우에 사용한다. 현재 freshness가 current이고 sample이 sufficient이면 recovery는 종료된다.
- `recovery.lastHealthyAt`은 이전 healthy/active read model 또는 snapshot에서 받은 값만 사용한다. 현재 request의 accepted bucket만으로 추론하지 않고, 값이 없으면 `null`이다.
- `recovery.retryAfterSeconds`는 recovery 중일 때 `30`, recovery가 아닐 때 `null`이다. 이는 "다음 판단까지 약 30초"를 뜻하며 자동 재시도 예약이 아니다.
- `recovery.recommendedAction`은 recovery 중일 때 "다음 판단까지 약 30초 기다린 뒤 accepted bucket 수용과 sample 증가를 확인하세요." 계열의 copy를 제공하고, recovery가 아닐 때 `null`이다.
- `lastRecoveredAt`은 회복 완료 시각처럼 읽히므로 MVP current read model recovery field에 포함하지 않는다.
- `metrics`에는 request/error처럼 app/project/window scalar로 안전한 값만 둔다. p95/p99는 `sourceScopedPercentiles`에서 source/instance/bucket scope와 함께 표시한다.
- `sourceScopedPercentiles`는 `summary.localPercentiles.p95Ms` / `p99Ms`에서 온 starter canonical point를 노출한다. 이 block의 item을 평균/최댓값/병합/히스토그램 재계산으로 단일 app/project/window p95/p99로 만들지 않는다.
- stale/down/degraded state는 `state.recommendedAction`을 포함한다.
- endpoint priority item은 endpoint-level `freshness`, `evidence`, `confidence`, `recommendedAction`을 포함한다.
- `lastHealthyAt`은 stale/down/recovery 안내에 사용한다. 값이 없으면 UI는 "이전 정상 시점 없음"으로 표시한다.
- Metric recovery guidance와 starter connection guidance는 별도 field/copy로 유지한다. 두 문구를 합쳐 host application down, host process down, 앱 내려감 같은 확정 표현을 만들지 않는다.

## 4.1 Instance Detail Starter Percentile Candidate

Instance detail read model은 starter가 보낸 30초 bucket canonical p95/p99를 강조해서 보여줄 수 있다. 값의 source와 scope를 함께 표시하며, 여러 p95/p99 point를 평균/병합해 새로운 window p95/p99를 만들지 않는다.

후보 shape:

```json
{
  "starterPercentiles": {
    "scope": "instance",
    "source": "starter_canonical_percentile",
    "window": "current_15m",
    "bucketDurationSeconds": 30,
    "bucketCount": 30,
    "displayPolicy": "source_scoped_series",
    "points": [
      {
        "bucketStartUtc": "2026-05-08T00:55:30Z",
        "bucketEndUtc": "2026-05-08T00:56:00Z",
        "requestCount": 820,
        "p95Ms": 640,
        "p99Ms": 1800
      }
    ]
  }
}
```

Label은 `starter-reported p95/p99`, `instance 30초 bucket p99`, `source-scoped p95/p99 series`처럼 의미와 scope를 드러내야 한다. Application/project home에서 여러 starter 값이 섞이면 percentile scalar를 만들지 않고 source별 값 또는 bucket distribution을 표시한다.

호환성을 위해 legacy 응답이 `localPercentileSummary`라는 wrapper 이름을 유지해야 한다면, 그 안의 값도 `source=starter_canonical_percentile`과 `displayPolicy=source_scoped_series`를 포함해야 하며 `avgMs` 같은 평균 percentile field를 canonical p95/p99처럼 노출하지 않는다.

## 4.2 Instance Snapshot Trend Candidate

Instance snapshot trend는 특정 application instance의 최근 관찰 흐름을 보여주는 stored read model projection이다. 이 기능은 application dashboard를 대체하지 않고, instance detail에서 사용자가 "이 instance가 최근 며칠 동안 안정적으로 관찰됐는가"를 확인하는 보조 화면이다.

기본 조회 horizon은 `7일`이고, 선택 가능한 최대 horizon은 `dashboard_snapshots` retention 안에서 `14일`로 clamp한다. 기본 limit은 hourly scheduled snapshot 기준 `168` point이고, 최대 limit은 `336` point다. 누락된 snapshot은 보간하지 않고 누락으로 둔다.

이 projection의 source는 `dashboard_snapshots.read_model_json`에 저장된 bounded instance summary다. MVP에서는 별도 `instance_snapshot_trends` table을 만들지 않고 snapshot JSON에서 특정 instance summary를 추출할 수 있다. instance 수, 조회 빈도, JSON scan 비용이 커지면 후속 story에서 snapshot-derived helper table을 검토할 수 있지만, 그 table도 raw metric store나 endpoint timeseries store가 아니다.

후보 shape:

```json
{
  "generatedAt": "2026-05-25T12:05:35Z",
  "applicationId": "5c942671-e251-4f7f-b610-18ae6ca4ef65",
  "instanceId": "orders-api-7f9c9c8c9d-x2p4k",
  "horizon": {
    "since": "2026-05-18T12:05:35Z",
    "until": "2026-05-25T12:05:35Z",
    "defaultSince": "7d",
    "maxSince": "14d",
    "limit": 168,
    "maxLimit": 336,
    "order": "capturedAt_asc"
  },
  "points": [
    {
      "snapshotId": "018f6b9a-2e1a-7d2b-9b2f-4db69d92c241",
      "capturedAt": "2026-05-25T12:00:00Z",
      "captureReason": "hourly_scheduled",
      "currentWindowEndUtc": "2026-05-25T12:00:00Z",
      "storedApplicationStateCode": "active",
      "instance": {
        "name": "orders-api-7f9c9c8c9d-x2p4k",
        "observationStatus": "observed"
      },
      "metricData": {
        "statusSource": "accepted_bucket",
        "lastAcceptedBucketAt": "2026-05-25T11:59:30Z",
        "freshnessLabel": "current"
      },
      "starterConnection": {
        "statusSource": "starter_heartbeat",
        "lastHeartbeatAt": "2026-05-25T11:59:45Z",
        "connectionMeaning": "starter_connected",
        "stateImpact": "none"
      },
      "starterPercentilePoint": {
        "source": "starter_canonical_percentile",
        "scope": "instance_bucket",
        "bucketStartUtc": "2026-05-25T11:59:00Z",
        "bucketEndUtc": "2026-05-25T11:59:30Z",
        "requestCount": 820,
        "p95Ms": 210,
        "p99Ms": 360
      },
      "resourceHints": {
        "cpuUsage": 0.41,
        "heapUsedRatio": 0.62,
        "datasourcePoolUsage": 0.37
      },
      "applicationTriageContribution": {
        "contributed": false,
        "ruleIds": [],
        "reason": "no_action_needed"
      },
      "endpointEvidenceRefs": []
    }
  ]
}
```

Instance snapshot trend의 `storedApplicationStateCode`는 snapshot 저장 당시 application read model의 state code다. API와 UI는 이 값을 instance state로 바꾸거나, point field를 조합해 새로운 lifecycle state 또는 health score를 만들지 않는다.

`starterPercentilePoint`는 해당 snapshot 시점에 저장된 source-scoped starter canonical latest point만 담는다. 여러 point를 평균/최댓값/병합하거나 histogram bucket으로 p95/p99를 재계산하지 않는다. 값이 없으면 `null`로 둔다.

`endpointEvidenceRefs`는 snapshot detail의 bounded endpoint evidence를 가리키는 참조만 허용하며, endpoint별 장기 timeseries나 endpoint p95/p99를 포함하지 않는다.

금지:

- accepted bucket, heartbeat, resource hint를 조합해 instance state를 재계산하는 것
- raw 30초 bucket list 또는 raw snapshot list를 반환하는 것
- endpoint timeseries 또는 per-request sample을 반환하는 것
- 14일 retention을 넘는 장기 instance analytics로 약속하는 것
- heartbeat observation을 metric health 판단으로 합성하는 것

## 5. Route Attribution Epic 5 Note

Route attribution은 [route-attribution-policy.md](route-attribution-policy.md)를 source of truth로 따른다. Read model은 이미 정규화된 `method + normalized route`만 받으며 raw path, raw path candidate, 실제 query string/key/value, `http.url` raw detail을 전달받지 않는다. Epic 5에서 endpoint priority item에 attribution 설명이 필요하면 raw detail 없는 bounded enum만 검토한다.

후보 shape:

```json
"routeAttribution": {
  "source": "safe_template",
  "availability": "available"
}
```

허용 source 후보는 route attribution policy의 출력 경계를 넘지 않는 bounded enum으로 제한한다. 예시는 `safe_template`, `safe_prefix_collapse`, `allowlist_path_match`, `unavailable`이다. MVP에서 이 enum propagation이 과하다고 판단되면 Epic 5까지 보류하고, read model은 `route: "UNKNOWN"`만으로 unavailable 상태를 표현한다.

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
- p95/p99는 starter canonical percentile만 사용하고 source/instance/bucket scope를 함께 표시한다.
- histogram bucket은 distribution display source로만 사용하고 p95/p99 scalar를 만들지 않는다.
- Operational event history service 후보는 이 응답의 저장 결과를 읽어 event surface를 만들 수 있지만 current read model의 판단을 덮어쓰지 않는다.
- endpoint detail과 endpoint priority는 bucket distribution을 표시할 수 있지만 endpoint별 p95/p99를 계산하지 않는다.
