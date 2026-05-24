# Epic 4 Context: State Semantics and Time Windows

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Epic 4는 첫 화면의 상태 언어와 시간 의미를 portal service/model 계층의 단일 판단으로 고정한다. 특히 accepted metric bucket은 metric freshness, metric state, current/baseline read model의 source-of-truth로 쓰며, starter heartbeat는 starter/application process liveness, portal reachability, project key validity, metadata validity의 별도 control-plane source로 분리한다. 후속 dashboard/read-model 구현이 같은 UTC 30초 bucket, 15분 current/baseline window, stale/down 후보 기준을 재사용하되 heartbeat connection 축은 별도로 다루게 만드는 것이 목적이다.

## Stories

- Story 4.0: Starter heartbeat and instance-level ingest contract gate
- Story 4.1: Time bucket contract implementation
- Story 4.2: Lifecycle state service
- Story 4.3: Recovery guidance
- Story 4.4: State semantics tests

## Requirements & Constraints

- Starter heartbeat는 `POST /api/ingest/v1/buckets`와 분리된 `POST /api/ingest/v1/heartbeat` control-plane/liveness 신호다.
- Heartbeat 성공, 실패, 미수신은 accepted bucket, host business health, dashboard snapshot, operational event, p95/p99, rule/metric read-model calculation을 생성하거나 암시하지 않는다.
- 최근 heartbeat와 없음/오래된 accepted bucket 조합은 no recent traffic/waiting for traffic/metric data idle 계열로 표현하고 host application down으로 단정하지 않는다.
- heartbeat도 끊기고 accepted bucket도 오래된 조합은 starter disconnected/telemetry unreachable/unknown 계열로 표현하며 host application down 원인은 미확정으로 둔다.
- Heartbeat는 application/instance catalog upsert source가 아니며, 첫 accepted bucket ingest가 catalog source로 유지된다.
- Application state와 freshness는 마지막 accepted bucket `endUtc` 기준이다. Stale 후보는 90초 이상, down 후보는 180초 이상 과거일 때 표현한다.
- Story 4.3 recovery guidance는 이전 metric state가 stale/down이고 현재 freshness는 current지만 sample이 insufficient인 경우에만 `isRecovering=true`로 표현한다.
- Recovery guidance는 별도 최상위 `recovering` state를 만들지 않고 `unknown` metric state와 read model의 `recovery` field로 표현한다.
- `lastHealthyAt`은 이전 healthy/active read model 또는 snapshot에서 받은 값만 사용하며, `lastRecoveredAt`은 Story 4.3 current read model field로 쓰지 않는다.
- `retryAfterSeconds`는 recovery 중 30초이며 자동 재시도 약속이 아니라 다음 판단 대기 힌트다.
- Recovery copy와 starter connection copy는 결합하지 않으며 host application down, host process down, 앱 내려감 같은 확정 표현을 만들지 않는다.
- Bucket은 UTC 30초 boundary와 `[startUtc, endUtc)` semantics를 따른다.
- Dashboard current window는 query 시점 기준 최근 15분이며, baseline window는 그 직전 15분이다.
- p95/p99 canonical source는 starter가 보낸 `summary.localPercentiles.p95Ms`/`p99Ms`다. 여러 instance/window의 percentile 숫자를 평균/병합해 app/project/window p95/p99를 만들지 않는다.
- Endpoint는 histogram bucket display only다. Endpoint별 p95/p99, endpoint percentile rollup, endpoint percentile judgment, endpoint p99 alert 기준은 만들지 않는다.
- UI, controller, repository는 lifecycle state, insight rule, p95/p99, endpoint priority를 재계산하지 않는다.

## Technical Decisions

- Active architecture is Traditional MVC + Service/Repository layering with feature-first `com.observation.portal.domain` packages.
- Portal controller는 request/response와 HTTP status mapping만 담당하고 service를 호출한다.
- Portal service는 project key verification, metadata validation, time/freshness/state/read-model orchestration을 담당할 수 있다.
- Repository는 Spring Data JPA/Jakarta Persistence + Hibernate over PostgreSQL 기준이며 저장/조회만 담당한다.
- Raw project key 같은 secret은 DB row, migration, log, exception, response body, repository lookup surface에 남기지 않는다.
- `MetricBucketRepository`는 Story 4.1에서 application scope 마지막 accepted bucket `endUtc` timestamp 조회 메서드를 제공한다. Heartbeat response에서 accepted bucket 참고 시각이 필요하면 이 timestamp 조회만 재사용한다.
- `common.time`의 time bucket/window/freshness helper는 후속 state/read-model service가 공유하는 기준이다.
- Starter heartbeat sender는 host startup/request path를 막지 않는 background/fail-open path여야 하며, bounded timeout과 rate-limited logging/backoff 원칙을 따른다.

## Cross-Story Dependencies

- Story 4.0은 heartbeat와 local percentile 의미를 잠근 contract gate이며, heartbeat endpoint 구현 자체는 후속 작업이다.
- Story 4.1은 UTC bucket/window/freshness helper와 last accepted bucket timestamp repository method를 제공한다.
- Story 4.2는 LifecycleStateService를 구현하되 accepted bucket metric state와 starter connection/liveness를 별도 typed input/output으로 다룬다. Heartbeat를 accepted bucket freshness source나 host business health/degraded 판단 근거로 쓰면 안 된다.
- Story 4.3은 external observability product patterns와 BMAD party 검증을 근거로 닫은 `implementation-artifacts/spec-story-4-3-recovery-guidance-contract-decisions.md`의 recovery guidance 결정을 따른다.
- Epic 5 dashboard read model과 snapshot/history는 Epic 4의 accepted bucket source-of-truth와 state semantics를 소비한다.
