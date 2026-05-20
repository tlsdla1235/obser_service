# Epic 3 Context: Portal Ingest Acceptance

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Portal이 starter가 보낸 `schemaVersion: 1.0` ingest envelope를 외부 입력으로 다시 검증하고, 유효한 30초 bucket만 idempotent하게 수용/저장할 수 있게 한다. Epic 3은 accepted bucket 저장과 idempotent acceptance까지만 닫으며, dashboard read model, p95/state/rule 계산, operational event 저장/API는 포함하지 않는다.

## Stories

- Story 3.1: Project key verification service
- Story 3.2: Ingest acceptance service
- Story 3.3: PostgreSQL bucket repository
- Story 3.4: Duplicate handling

## Requirements & Constraints

Portal ingest boundary는 starter payload를 신뢰하지 않고 `ingest-envelope`, `metric-taxonomy`, `time-buckets` 계약을 mirror validation한다. `schemaVersion`은 MVP에서 `1.0`만 허용하고, bucket은 UTC 30초 boundary와 `[startUtc, endUtc)` semantics를 따라야 한다. application name, environment, instance는 비어 있으면 안 되고, idempotency key는 project/application/environment/instance/bucket-start component와 payload identity가 일치해야 한다.

Metric payload는 bounded shape만 허용한다. HTTP summary와 endpoint histogram bucket은 cumulative count여야 하며, 음수 count, `errorCount > requestCount`, non-monotonic histogram, request count를 초과하는 histogram count는 거부한다. endpoint route는 framework route template, configured allowlist template, 또는 `UNKNOWN`이어야 하며 raw path, query string, raw identifier candidate, high-cardinality tag, free tag map, arbitrary custom metric map, raw timeseries array는 payload에 존재할 수 없다. framework route candidate의 query discard는 starter-side normalization 정책이며, portal은 final payload route에 query string이 남아 있으면 계속 거부한다.

Post-MVP runtime aggregate schemaVersion `1.1` 후보는 Epic 3 MVP ingest path에 섞지 않는다. JVM/datasource runtime ratio는 `schemaVersion: 1.0` latest scalar shape만 다룬다.

## Technical Decisions

Portal은 Traditional MVC + service/repository layering을 따른다. `IngestController`는 header/body를 받아 service에 위임하고, `IngestAcceptanceService`가 project key 검증, schema/bucket/metric/idempotency validation, persistence orchestration을 담당한다. Repository/JPA 구현은 저장과 조회만 담당하며 controller/dto에 의존하지 않는다.

Accepted bucket persistence는 PostgreSQL/Flyway schema를 source of truth로 사용하고, Spring Data JPA/Jakarta Persistence + Hibernate 기준으로 구현한다. Duplicate handling은 동일 idempotency key와 동일 payload hash 재전송을 성공으로, 동일 key와 다른 payload hash를 conflict로 처리한다.

## Cross-Story Dependencies

Story 3.1의 project key verification이 Story 3.2 acceptance boundary의 인증 전제다. Story 3.2의 validated candidate는 Story 3.3 persistence와 Story 3.4 duplicate handling의 입력이 된다. Story 2.5 golden ingest envelope와 deterministic idempotency key는 Story 3.2 success fixture로 계속 유지한다.
