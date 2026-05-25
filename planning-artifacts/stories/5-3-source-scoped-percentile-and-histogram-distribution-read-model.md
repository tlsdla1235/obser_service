---
artifactType: story
storyId: "5.3"
epic: "Epic 5. Dashboard Read Model and API"
title: "Source-scoped Percentile and Histogram Distribution Read Model"
architectureStyle: Traditional MVC
status: done
date: 2026-05-25
storyKey: "5-3-source-scoped-percentile-and-histogram-distribution-read-model"
---

# Story 5.3 - Source-scoped Percentile and Histogram Distribution Read Model

## User Story

portal 구현자로서, Application Dashboard read model이 starter가 저장한 instance bucket scope p95/p99 point와 application-level histogram distribution evidence를 계약에 맞게 제공하길 원한다.

그래야 UI와 후속 Story 5.4/5.5가 p95/p99, histogram bucket, current/baseline evidence를 서로 다른 source/scope로 안전하게 표시하고, controller나 UI가 percentile, state, rule, endpoint priority를 재계산하지 않는다.

## Scope / Out of Scope

포함:

- `GET /api/projects/{projectId}/applications/{applicationId}/dashboard`의 Story 5.2 skeleton 확장
- `sourceScopedPercentiles.items`를 current 15분 window 안 instance별 latest starter canonical percentile point로 채움
- `sourceScopedPercentiles.source`와 item source를 persisted `local_percentiles_json.source` 값인 `starter_local`로 노출
- 같은 instance에 current window 안 여러 percentile point가 있으면 `bucketEndUtc`가 가장 최신인 point만 선택
- top-level `histogramDistribution` field 추가
- `histogramDistribution.current`와 `histogramDistribution.baseline`에 application-level summary histogram bucket distribution evidence 제공
- missing/insufficient/unavailable evidence를 null이 아니라 빈 배열과 `status`/`reason`으로 표현
- current/baseline window별 histogram boundary set mismatch guard
- repository/service/controller serialization contract와 recomputation guard tests

제외:

- p95/p99 신규 계산
- 여러 instance/window percentile의 평균, 최댓값, 병합, rollup
- histogram bucket 기반 p95/p99 계산
- application-level p95/p99 scalar, metrics p95/p99 scalar, avg/max latency scalar
- endpoint percentile, endpoint histogram/evidence, endpoint priority, endpoint p99 alert 기준
- triage rule, confidence, regression, current/baseline delta 판단
- dashboard UI, snapshot persistence, operational event API, migration
- starter ingest/heartbeat endpoint 동작 변경
- `application`, `port`, `adapter` package 추가

## Acceptance Criteria

1. Dashboard `200` response는 Story 5.2 top-level fields를 유지하고, 항상 non-null top-level `histogramDistribution` field를 추가한다.
2. `sourceScopedPercentiles`는 항상 non-null object이며 `source`, `scope`, `displayPolicy`, `aggregatePolicy`, `status`, `reason`, `items`를 포함한다.
3. `sourceScopedPercentiles.source`는 `starter_local`이다. API는 이 값을 `starter_canonical_percentile`로 번역하지 않는다.
4. `sourceScopedPercentiles.scope`는 `instance_bucket`이고 `displayPolicy`는 instance별 latest point임을 드러내는 값이어야 한다.
5. `sourceScopedPercentiles.aggregatePolicy`는 `no_average_no_max_no_merge_no_histogram_recalculation` 또는 동등하게 명시적인 금지 정책이다.
6. `sourceScopedPercentiles.items`는 current 15분 window 안 `accepted_metric_buckets.local_percentiles_json`에서 온 valid point만 포함한다.
7. valid percentile point는 `scope=instance_bucket`, `source=starter_local`, `mergeable=false`, `requestCount > 0`, bucket boundary가 persisted bucket boundary와 일치하는 point다.
8. 같은 application instance에 current window 안 여러 valid point가 있으면 `bucketEndUtc`가 가장 최신인 1개만 반환한다.
9. 여러 instance의 point가 있어도 application/window p95/p99 scalar를 만들지 않고 instance별 item 배열로만 노출한다.
10. percentile item은 최소 `source`, `application`, `environment`, `instance`, `bucketStartUtc`, `bucketEndUtc`, `requestCount`, `p95Ms`, `p99Ms`를 포함한다.
11. current window 안 valid percentile point가 없으면 `sourceScopedPercentiles.items=[]`, `status=missing`, `reason=no_percentile_points_in_current_window`를 반환한다.
12. request count가 0이거나 invalid local percentile evidence는 p95Ms/p99Ms를 0으로 보정하지 않고 item에서 제외한다. 제외 후 valid item이 없으면 missing 또는 insufficient status/reason으로 표현한다.
13. `sourceScopedPercentiles` 안에는 histogram-derived p95/p99, histogram bucket payload, application-scope fallback payload를 넣지 않는다. Story 5.2 placeholder의 `applicationScopeFallback`은 5.3에서 top-level `histogramDistribution`으로 대체한다.
14. `histogramDistribution`은 항상 non-null object이며 `source=histogram_bucket_distribution`, `scope=application`, `current`, `baseline`을 포함한다.
15. `histogramDistribution.current`는 dashboard current 15분 window의 `duration_buckets_json` application summary buckets만 합산한다.
16. `histogramDistribution.baseline`은 dashboard baseline 15분 window의 `duration_buckets_json` application summary buckets만 합산한다.
17. 각 window distribution은 독립적으로 `status`, `reason`, `totalCount`, `buckets`를 포함한다.
18. histogram bucket이 없으면 해당 window는 `buckets=[]`, `totalCount=0`, `status=missing`, `reason=no_histogram_buckets_in_current_window` 또는 baseline에 맞는 동등한 reason을 반환한다.
19. histogram bucket은 있지만 표시 가능한 sample/evidence가 부족하면 해당 window는 `status=insufficient`, 빈 배열 또는 안전한 bucket 배열, 명확한 reason을 반환할 수 있다.
20. 같은 window 안 합산 대상 bucket의 `leMs` boundary set이 하나로 일치할 때만 cumulative count를 boundary별로 합산한다.
21. current window boundary set이 mismatch이면 distribution을 만들지 않고 `histogramDistribution.current.status=unavailable`, `reason=histogram_boundary_mismatch`, `buckets=[]`를 반환한다.
22. baseline window boundary set mismatch도 같은 guard를 독립 적용한다.
23. histogram distribution에는 `p95Ms`, `p99Ms`, `avgMs`, `maxMs`, `delta`, `percentageChange`, `regression`, `confidence`, `ruleId`, `severity`를 포함하지 않는다.
24. Story 5.3은 `endpoints_json`의 endpoint별 `durationBuckets`를 dashboard response에 노출하지 않는다.
25. `DashboardReadModelService`는 dashboard response 조립을 담당하되 lifecycle state 판단은 기존처럼 `LifecycleStateService`에 맡기고, percentile/histogram evidence로 state/rule/zeroInsight를 새로 계산하지 않는다.
26. Repository는 raw persisted percentile/histogram evidence와 window 합산에 필요한 중립 projection만 반환하고, state, rule, confidence, regression, endpoint priority, p95/p99 재계산을 하지 않는다.
27. Controller는 path/status mapping과 service 위임만 담당하고 repository, JSON parsing, window merge, percentile/histogram 판단을 직접 수행하지 않는다.
28. JPA entity는 API response, controller response DTO, service external return model로 직접 노출하지 않는다.
29. 새 공개 클래스, 공개 메서드, 핵심 helper는 AGENTS.md 지침에 따라 한국어 Javadoc/docstring으로 역할과 사용 맥락을 설명한다.
30. Focused tests가 source-scoped latest-per-instance selection, empty/status/reason policy, histogram current/baseline distribution, boundary mismatch guard, no percentile recomputation, endpoint exclusion, MVC layer boundary를 검증한다.
31. `./gradlew :observability-portal:test`와 `git diff --check`가 통과한다.

## Tasks/Subtasks

- [x] Dashboard read model contract shape 확장 (AC: 1~5, 13~14, 17, 23, 28~29)
  - [x] `ApplicationDashboardReadModel`에 `histogramDistribution` top-level record를 추가한다.
  - [x] `SourceScopedPercentiles`를 Story 5.3 shape로 갱신해 `status`, `reason`, typed item list를 포함한다.
  - [x] 5.2 placeholder의 `applicationScopeFallback`은 외부 응답에서 제거하고, histogram evidence는 top-level `histogramDistribution`으로만 노출한다.
  - [x] `histogramDistribution.current`와 `histogramDistribution.baseline` window evidence record를 추가한다.
  - [x] read model record 생성자에서 top-level object와 collection이 null이 되지 않도록 검증한다.
- [x] Percentile repository read path 추가 (AC: 6~12, 26, 28)
  - [x] `MetricBucketRepository`에 application/window scope local percentile evidence 조회 method를 추가한다.
  - [x] 조회 대상은 `accepted_metric_buckets.application_id`, `bucket_end_utc > windowStart`, `bucket_end_utc <= windowEnd`, `local_percentiles_json is not null`이다.
  - [x] instance 이름은 `application_instances` row와 join하거나 기존 catalog repository를 재사용해 read model item에 채운다.
  - [x] repository projection은 persisted JSON과 bucket/application/instance identity를 전달하는 중립 모델로 두고 p95/p99 rollup을 계산하지 않는다.
  - [x] service가 instance별 latest point를 선택할 수 있도록 `applicationInstanceId`, `instanceName`, `bucketStartUtc`, `bucketEndUtc`를 포함한다.
- [x] Histogram repository read path 추가 (AC: 14~22, 24, 26)
  - [x] `MetricBucketRepository`에 application/window scope summary duration bucket evidence 조회 method를 추가한다.
  - [x] 조회 대상은 `accepted_metric_buckets.duration_buckets_json`이며 `endpoints_json`은 사용하지 않는다.
  - [x] current와 baseline window를 같은 method로 조회할 수 있게 window start/end를 parameter로 받는다.
  - [x] repository는 JSON bucket rows/projection만 반환하고 boundary mismatch, status/reason, totalCount 판단은 service/model helper에서 수행한다.
- [x] Dashboard service orchestration 확장 (AC: 1~25)
  - [x] 기존 Story 5.2 window calculation을 그대로 재사용한다. current/baseline end는 latest accepted bucket이 아니라 floor된 `evaluationAt`이다.
  - [x] current window percentile rows를 읽어 instance별 latest valid `starter_local` point만 선택한다.
  - [x] valid point가 없을 때 `sourceScopedPercentiles.items=[]`와 missing/insufficient status/reason을 만든다.
  - [x] current와 baseline summary histogram rows를 읽어 window별 boundary set 일치 여부를 독립적으로 검사한다.
  - [x] boundary set이 일치하는 window만 cumulative count를 boundary별로 합산하고, totalCount를 계산한다.
  - [x] boundary mismatch window는 partial merge 없이 `unavailable/histogram_boundary_mismatch`로 반환한다.
  - [x] percentile/histogram evidence를 `metrics`, `state`, `zeroInsight`, `recovery`, `triageCards`, `endpointPriority` 계산에 사용하지 않는다.
- [x] Controller/API serialization 유지 (AC: 1, 23~24, 27)
  - [x] `DashboardController`는 기존 route와 404 mapping을 유지한다.
  - [x] controller test fixture를 갱신해 `histogramDistribution` field와 `starter_local` source를 serialization으로 검증한다.
  - [x] controller에서 repository 또는 JSON merge helper를 직접 호출하지 않음을 테스트/리뷰로 확인한다.
- [x] Scope guard와 architecture guard 유지 (AC: 23~29)
  - [x] no `application`, `port`, `adapter` package guard를 유지한다.
  - [x] endpoint priority, triage rule, confidence, regression, snapshot/history, UI, migration을 추가하지 않는다.
  - [x] JPA entity를 dashboard read model response로 직접 반환하지 않는다.
- [x] Focused tests와 regression 실행 (AC: 30~31)
  - [x] `DashboardReadModelServiceTest`에 source-scoped percentile와 histogram distribution scenarios를 추가한다.
  - [x] 필요 시 `MetricBucketRepositoryIntegrationTest` 또는 별도 repository integration test로 JSON read projection, instance filtering, window boundary, boundary mismatch fixture를 검증한다.
  - [x] `DashboardControllerTest`에 response shape, no metrics p95/p99, no histogram percentile scalar, no endpoint histogram exposure를 추가한다.
  - [x] `MvcLayerBoundaryTest`가 계속 통과하는지 확인한다.
  - [x] `./gradlew :observability-portal:test --tests '*DashboardReadModel*'`, `./gradlew :observability-portal:test --tests '*DashboardController*'`, `./gradlew :observability-portal:test`, `git diff --check`를 실행한다.

## Dev Notes

### Contract Priority

- `implementation-artifacts/spec-story-5-3-percentile-histogram-contract-decisions.md`가 Story 5.3 pre-story source of truth다.
- 이 결정 문서와 `read-model-contract.md`, `histogram-merge.md`, `api-surface.md`, Story 5.2 placeholder가 충돌하면 5.3 결정 문서를 우선한다.
- 특히 `sourceScopedPercentiles.source`는 기존 예시의 `starter_canonical_percentile`이 아니라 persisted value와 같은 `starter_local`이다.
- `starter_local`은 약한 local hint가 아니라 starter가 해당 `instance_bucket` 30초 bucket에서 직접 산출해 보낸 canonical percentile point다.

### Suggested Response Shape

```json
{
  "sourceScopedPercentiles": {
    "source": "starter_local",
    "scope": "instance_bucket",
    "displayPolicy": "latest_starter_point_per_instance_in_current_window",
    "aggregatePolicy": "no_average_no_max_no_merge_no_histogram_recalculation",
    "status": "available",
    "reason": null,
    "items": [
      {
        "source": "starter_local",
        "application": "orders-api",
        "environment": "prod",
        "instance": "pod-a",
        "bucketStartUtc": "2026-05-25T10:31:30Z",
        "bucketEndUtc": "2026-05-25T10:32:00Z",
        "requestCount": 1200,
        "p95Ms": 480,
        "p99Ms": 960
      }
    ]
  },
  "histogramDistribution": {
    "source": "histogram_bucket_distribution",
    "scope": "application",
    "displayPolicy": "bucket_distribution_evidence",
    "aggregatePolicy": "sum_cumulative_counts_only_when_boundary_set_matches",
    "current": {
      "status": "available",
      "reason": null,
      "totalCount": 42000,
      "buckets": [
        { "leMs": 50, "count": 12000 },
        { "leMs": 100, "count": 22000 },
        { "leMs": 250, "count": 36000 },
        { "leMs": 500, "count": 41000 },
        { "leMs": 1000, "count": 42000 }
      ]
    },
    "baseline": {
      "status": "missing",
      "reason": "no_histogram_buckets_in_baseline_window",
      "totalCount": 0,
      "buckets": []
    }
  }
}
```

Notes:

- `generatedAt`, `application`, `state`, `starterConnection`, `zeroInsight`, `recovery`, `metrics`, `triageCards`, `endpointPriority`, `snapshot`는 Story 5.2 shape와 의미를 유지한다.
- `histogramDistribution.current`와 `baseline`은 evidence-only다. 이 둘을 비교한 delta/regression/confidence/rule 판단은 Story 5.4 범위다.
- `histogramDistribution`에는 percentile/latency scalar를 두지 않는다.

### Existing Code State

- `ApplicationDashboardReadModel.SourceScopedPercentiles.empty()`는 Story 5.2 placeholder로 `items=[]`를 반환한다.
- `DashboardReadModelService.buildDashboard()`는 current/baseline window를 계산하고, accepted bucket freshness, current request/error aggregate, heartbeat source를 조립한다.
- `DashboardReadModelService`는 현재 `ApplicationDashboardReadModel.SourceScopedPercentiles.empty()`를 반환한다. Story 5.3은 이 지점을 contract-safe read model evidence로 채운다.
- `DashboardReadModelService`의 current window end는 `clock.instant()`를 UTC 30초 boundary로 floor한 `evaluationAt`이다. latest accepted bucket `endUtc`로 바꾸지 않는다.
- `MetricBucketRepository.findWindowAggregateByApplicationId()`는 request/error aggregate만 반환하는 중립 모델이다. 5.3 새 repository read path도 이 패턴처럼 의미 판단을 service에 남겨야 한다.
- `AcceptedMetricBucketEntity`는 `durationBucketsJson`, `localPercentilesJson`, `endpointsJson`을 JSONB string field로 매핑한다.
- `V004__add_local_percentiles_to_accepted_metric_buckets.sql`이 `local_percentiles_json` column을 이미 추가했다. Story 5.3은 schema migration을 추가하지 않는다.
- `MetricBucketRepositoryIntegrationTest`는 Testcontainers PostgreSQL과 Flyway migration을 사용한다. JSON projection 검증이 필요하면 이 테스트 스타일을 재사용한다.

### Source Documents Read

- `implementation-artifacts/spec-story-5-3-percentile-histogram-contract-decisions.md`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/epics.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/histogram-merge.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/contracts/metric-taxonomy.md`
- `planning-artifacts/contracts/time-buckets.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/stories/5-2-application-dashboard-read-model-skeleton.md`
- `_bmad/custom/project-context.md`

## Existing Code / Documents To Reuse

- `_bmad/custom/project-context.md`
  - Traditional MVC + Service/Repository Layering, feature-first MVC, Spring Data JPA/Flyway 기준을 따른다.
- `implementation-artifacts/spec-story-5-3-percentile-histogram-contract-decisions.md`
  - Story 5.3 percentile/histogram source, scope, fallback, status/reason, boundary mismatch, current/baseline evidence 경계의 source of truth다.
- `planning-artifacts/contracts/ingest-envelope.md`
  - `summary.localPercentiles` shape와 `source=starter_local`, `scope=instance_bucket`, `mergeable=false` 의미를 따른다.
- `planning-artifacts/contracts/histogram-merge.md`
  - histogram bucket은 cumulative distribution display source이며 p95/p99 계산 source가 아니다.
- `planning-artifacts/contracts/read-model-contract.md`
  - first-screen UI source-of-truth와 UI/controller recomputation 금지 원칙을 따른다. 단, 5.3 source naming과 histogram top-level 위치는 5.3 결정 문서를 우선한다.
- `planning-artifacts/contracts/time-buckets.md`
  - current 15분, baseline 15분, UTC, 30초 bucket boundary, freshness source 기준을 따른다.
- `planning-artifacts/api-surface.md`
  - dashboard API path, applicationId path scope, 404 mapping, MVC boundary를 유지한다.
- `planning-artifacts/stories/5-2-application-dashboard-read-model-skeleton.md`
  - Story 5.2 dashboard service/controller/model/test 패턴과 zeroInsight/state source axis separation을 재사용한다.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
  - dashboard response model 확장 대상이다.
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
  - Story 5.3 read model evidence를 조립할 orchestration 지점이다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
  - accepted bucket JSON evidence read projection을 추가할 repository facade 후보이다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
  - application/window scoped JPA/native query 추가 후보이다.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/entity/AcceptedMetricBucketEntity.java`
  - JSONB field mapping을 재사용한다. entity를 API response로 직접 반환하지 않는다.
- `observability-portal/src/main/java/com/observation/portal/domain/catalog/entity/ApplicationInstanceEntity.java`
  - percentile item의 instance name 조회에 필요한 catalog identity source다.
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`
  - service-level source separation과 window contract test 패턴을 확장한다.
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
  - serialization/status mapping test fixture를 갱신한다.
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
  - PostgreSQL JSONB/Flyway/Testcontainers repository test 패턴을 재사용한다.
- `observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java`
  - controller/repository/dto dependency guard와 no hexagonal package guard를 유지한다.

## Previous Story Intelligence

- Story 5.2는 Application Dashboard current read model endpoint와 service skeleton을 구현했고, `sourceScopedPercentiles.items=[]` placeholder를 남겼다.
- Story 5.2는 `DashboardReadModelService`에서 response assembly를 수행하고 `LifecycleStateService`에 state 판단을 위임하는 패턴을 확정했다.
- Story 5.2 tests는 current/baseline window floor, source axis separation, no metrics p95/p99, controller delegation, 404 mapping을 이미 검증한다.
- Story 4.0/4.0.1은 `localPercentiles` ingest/persistence 의미를 starter canonical percentile로 고정했다.
- Story 4.4는 heartbeat가 metric state를 current/active로 만들지 못하고 host down을 단정하지 않도록 회귀 기준을 고정했다.
- 최근 git history는 Story 5.2 application dashboard read model merge가 마지막 주요 변경이다. 5.3은 catalog/navigation을 재작성하지 않고 dashboard/bucket read path를 확장한다.

## Architecture Constraints

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC 구조를 따른다.
- 이 프로젝트에서 `domain`은 business feature grouping namespace이며 DDD pure domain layer가 아니다.
- 구현 위치는 기존 `domain.dashboard`, `domain.bucket`, `domain.catalog` feature packages를 우선 재사용한다.
- `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 새로 만들지 않는다.
- Controller는 service를 호출하고 repository를 직접 호출하지 않는다.
- Service는 빠른 MVC 구현을 위해 필요하면 Spring Data repository/JPA entity를 직접 사용할 수 있지만, JPA entity를 external return model로 노출하지 않는다.
- Repository는 Spring Data JPA/Jakarta Persistence + Hibernate 기반 read-only 조회만 담당한다.
- Flyway SQL migration이 schema source of truth다. Story 5.3은 새 migration을 기대하지 않는다.
- DB view, materialized view, trigger, stored procedure에 lifecycle state, starter connection diagnosis, percentile rollup, histogram-derived p95/p99, rule, endpoint priority 계산을 숨기지 않는다.
- raw project key, access token, refresh token, GitHub OAuth token, provider raw payload, secret은 response/log/error에 노출하지 않는다.
- 새 공개 클래스/메서드/핵심 helper의 Javadoc/comment는 프로젝트 지침에 따라 한국어로 작성한다.

## Developer Guardrails

- p95/p99를 새로 계산하지 않는다.
- `local_percentiles_json`의 여러 point를 평균, 최댓값, 합산, 병합해 application/window p95/p99를 만들지 않는다.
- histogram bucket에서 p95/p99, avg, max latency를 계산하지 않는다.
- `metrics` field에 p95/p99, avg/max latency, health score, availability score를 추가하지 않는다.
- `sourceScopedPercentiles.source`를 `starter_canonical_percentile`로 번역하지 않는다. persisted `starter_local`을 그대로 사용한다.
- `starter_local`의 의미는 dev note와 Javadoc에서 `instance_bucket` scope starter canonical percentile point로 설명한다.
- current window percentile items는 instance별 latest 1개만 반환한다. full series는 Story 5.6 instance evidence 범위다.
- baseline percentile 비교나 baseline percentile item은 Story 5.3에서 만들지 않는다.
- `histogramDistribution`은 top-level field다. `sourceScopedPercentiles` 안 fallback/payload로 넣지 않는다.
- histogram boundary mismatch 시 일부 bucket만 골라 합치지 않는다.
- `endpoints_json`의 endpoint duration buckets를 Story 5.3 dashboard response에 노출하지 않는다.
- current/baseline histogram evidence를 비교해 delta, regression, confidence, rule, severity, recommended action을 만들지 않는다.
- endpoint priority, triageCards, zeroInsight reason을 percentile/histogram evidence로 새로 계산하지 않는다.
- heartbeat를 percentile/histogram evidence source로 사용하지 않는다.
- dashboard UI, snapshot persistence, operational event API, endpoint timeseries table, raw instance timeseries table, Redis/outbox를 추가하지 않는다.
- unrelated refactor, package 재배치, 기존 ingest/heartbeat behavior 변경을 피한다.

## Test Expectations

Focused test 대상 후보:

- `DashboardReadModelServiceTest`
- `DashboardControllerTest`
- `MetricBucketRepositoryIntegrationTest` 또는 `MetricBucketRepositoryDashboardEvidenceIntegrationTest`
- `MvcLayerBoundaryTest`

필수 scenario:

- dashboard response가 top-level `histogramDistribution`을 항상 포함한다.
- `sourceScopedPercentiles.source`가 `starter_local`로 serialize된다.
- `sourceScopedPercentiles.scope=instance_bucket`, `aggregatePolicy=no_average_no_max_no_merge_no_histogram_recalculation`이다.
- current window 안 같은 instance의 여러 local percentile point 중 latest `bucketEndUtc` point만 반환한다.
- current window 밖 local percentile point는 제외된다.
- 서로 다른 instance의 latest point는 각각 1개씩 반환된다.
- `local_percentiles_json`이 없으면 `sourceScopedPercentiles.items=[]`, `status=missing`, `reason=no_percentile_points_in_current_window`다.
- `requestCount=0` evidence는 p95/p99 0으로 보정하지 않고 valid item으로 노출하지 않는다.
- response 어디에도 application/window p95/p99 scalar가 없다.
- histogram current window bucket boundary set이 일치하면 boundary별 cumulative count를 합산한다.
- histogram baseline window도 current와 독립적으로 distribution evidence를 반환한다.
- current histogram bucket이 없으면 current distribution은 empty buckets + missing reason이다.
- baseline histogram bucket이 없으면 baseline distribution은 empty buckets + baseline missing reason이다.
- histogram boundary mismatch는 해당 window를 `status=unavailable`, `reason=histogram_boundary_mismatch`, `buckets=[]`로 만든다.
- boundary mismatch 시 partial merge, p95/p99, avg/max, delta/regression/confidence/rule field가 생성되지 않는다.
- `endpoints_json.durationBuckets`는 dashboard response에 노출되지 않는다.
- `metrics` record/component와 JSON에 p95/p99, avg/max latency가 없다.
- `DashboardController`는 service mock/stub 결과를 serialization하고 404 mapping만 검증한다.
- repository query가 추가되면 PostgreSQL Testcontainers에서 application scope, window boundary `(start, end]`, JSONB projection, instance join, boundary mismatch fixture를 검증한다.
- no `application`, `port`, `adapter` package가 추가되지 않았음을 `MvcLayerBoundaryTest`로 확인한다.

Suggested commands:

```bash
./gradlew :observability-portal:test --tests '*DashboardReadModel*'
./gradlew :observability-portal:test --tests '*DashboardController*'
./gradlew :observability-portal:test --tests '*MetricBucketRepository*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check
```

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-05-25: bmad-create-story workflow 설정, project-context, sprint status, Epic 5, Story 5.3 결정 문서, read model/histogram/ingest/time/api contracts, Story 5.2 산출물과 현재 dashboard/bucket code를 확인했다.
- 2026-05-25: Story 5.3 create-story 산출물을 `planning-artifacts/stories/5-3-source-scoped-percentile-and-histogram-distribution-read-model.md`에 생성했다.
- 2026-05-25: bmad-dev-story 실행으로 Story 5.3 전체와 계약 결정 문서를 재확인하고 sprint status를 `in-progress`로 전환했다.
- 2026-05-25: focused test를 먼저 갱신한 뒤 `./gradlew :observability-portal:test --tests '*DashboardReadModel*'`에서 새 계약 미구현으로 인한 compile failure를 확인했다.
- 2026-05-25: source-scoped percentile/histogram read model, repository projection, service orchestration, controller serialization test를 구현했다.

### Completion Notes List

- `sourceScopedPercentiles`를 `starter_local`/`instance_bucket` source-scoped shape로 확장하고, current 15분 window에서 instance별 latest valid starter point만 반환하도록 구현했다.
- `histogramDistribution` top-level field를 추가하고 current/baseline application summary duration bucket distribution을 독립적으로 합산했다.
- histogram boundary set mismatch는 partial merge 없이 `unavailable/histogram_boundary_mismatch`로 반환하며, missing/invalid evidence는 empty arrays와 status/reason으로 표현했다.
- p95/p99 신규 계산, 여러 percentile 평균/최댓값/병합, histogram-derived percentile, endpoint histogram/priority, triage/confidence/regression 판단은 추가하지 않았다.
- focused tests와 전체 `:observability-portal:test`가 통과했다.

### File List

- `planning-artifacts/stories/5-3-source-scoped-percentile-and-histogram-distribution-read-model.md`
- `implementation-artifacts/sprint-status.yaml`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/HistogramBucketEvidenceRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/LocalPercentileEvidenceRow.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/AcceptedMetricBucketJpaRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/model/ApplicationDashboardReadModel.java`
- `observability-portal/src/main/java/com/observation/portal/domain/dashboard/service/DashboardReadModelService.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/controller/DashboardControllerTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/service/DashboardReadModelServiceTest.java`

### Change Log

- 2026-05-25: Story 5.3 create-story 산출물을 생성하고 sprint status를 ready-for-dev로 전환했다.
- 2026-05-25: Source-scoped percentile와 application-level histogram distribution read model을 구현하고 Story 5.3을 review 상태로 전환했다.
- 2026-05-25: Review blocking Major 수정과 regression 검증 후 Story 5.3을 done 상태로 전환했다.

## Status

done
