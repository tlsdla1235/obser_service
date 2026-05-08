---
artifactType: contract
name: read-model-contract
architectureStyle: Lightweight Hexagonal
status: party-mode-fixes-applied
date: 2026-05-08
---

# Contract - Read Model

## 1. 역할

`read-model-contract`는 first-screen UI의 단일 source of truth다.

UI는 이 응답을 표시할 뿐 lifecycle state, zero-insight reason, insight rule, endpoint priority, p95를 재계산하지 않는다. 이 계약은 `state-semantics`, `time-buckets`, `insight-rules`, `histogram-merge`의 결과를 한 응답으로 묶는 구현 기준이다.

## 2. Response Shape

```json
{
  "generatedAt": "2026-05-08T01:10:35Z",
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

## 5. Boundary Rules

- `QueryDashboardSnapshotUseCase`가 이 응답을 구성한다.
- PostgreSQL view는 이 응답을 계산하지 않는다.
- dashboard REST controller는 serialization만 담당한다.
- frontend는 이 응답을 기준으로 화면을 구성하고, 별도 rule engine을 갖지 않는다.
- p95는 `histogram-merge` contract의 server-side 결과만 사용한다.
