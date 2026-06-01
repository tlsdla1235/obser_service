---
artifactType: story
storyId: "2.2"
epic: "Epic 2. Starter Direct Ingest Producer"
title: "Route Normalization and Low-Cardinality Guard"
architectureStyle: Traditional MVC
status: done
date: 2026-05-10
---

# Story 2.2 - Route Normalization and Low-Cardinality Guard

## User Story

구현자로서, starter가 bucket이나 ingest envelope를 만들기 전에 route와 tag를 low-cardinality 정책으로 고정해 raw path와 high-cardinality label이 MVP payload에 들어가지 않게 하고 싶다.

## Scope

이 story는 Epic 2의 cardinality safety boundary다. Story 2.1의 observation input을 받아 normalized route와 허용 tag만 다음 단계로 넘긴다.

포함:

- normalized route model/service 추가
- route normalization precedence 고정
- allowed tag key policy 고정
- raw path parameter, query string, high-cardinality tag 차단
- endpoint key를 `method + normalized route`로 제한
- low-cardinality guard test

제외:

- Micrometer binding 재구현
- bucket rollup implementation
- bounded queue/flush worker
- HTTP ingest client
- ingest envelope builder
- portal ingest validation/persistence
- arbitrary custom metric/tag support

## Source Artifacts

- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/acceptance-traceability.md`
- `planning-artifacts/contracts/metric-taxonomy.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/stories/2-1-micrometer-observation-binding.md`

## Dependencies

- Story 2.1 provides observation inputs.
- Story 2.3 must roll up only normalized endpoint keys.
- Story 2.5 must serialize only data that passed this guard.

## Story 8.1 Route Policy Note

현재 route normalization source of truth는 `planning-artifacts/contracts/route-attribution-policy.md`다. 이 story의 2026-05-14 B안 세부 규칙이 Story 8.1 정책과 충돌하면 Story 8.1 source-of-truth를 우선한다.

## Implementation Notes

- 허용 식별자는 `application`, `environment`, `instance`, `method`, `normalized route`다.
- route source precedence와 fallback 허용 범위는 `route-attribution-policy.md`를 따른다. low-cardinality `http.route`를 먼저 정규화하고, 그 결과가 `UNKNOWN`이면 low-cardinality `uri`/`path` raw 후보를 볼 수 있다.
- 안전한 normalized route를 얻지 못하면 raw path 원문을 사용하지 않고 `UNKNOWN`으로 처리한다. 단 policy가 허용한 safe prefix collapse 결과는 bounded normalized route로 사용할 수 있다.
- 실제 query key/value는 matcher 입력 전에 폐기하며 route/tag/key/payload/log/read model에 남기지 않는다. `?...`는 policy가 허용한 bounded omission marker일 때만 보존한다.
- raw path values like `/orders/12345`, `/users/alice`, `/sessions/abc` must not become endpoint keys.
- arbitrary label map을 도입하지 않는다.
- endpoint list의 bounded top-N은 이미 정규화된 endpoint 출력 수 제한이며 route attribution fallback으로 사용하지 않는다.
- annotation 기반 query dimension, route masking, metric rename은 post-MVP 후보로만 남기고 이 story에서는 구현하지 않는다.

## Acceptance Criteria

1. route normalization service는 raw request path 대신 normalized route를 반환한다.
2. framework/custom `http.route` 후보를 항상 먼저 정규화한다.
3. `http.route` 처리 결과가 `UNKNOWN`이면 low-cardinality raw path candidate를 configured allowlist matcher와 safe prefix collapse의 입력으로 사용할 수 있다.
4. raw path candidate의 query string은 matcher 입력 전에 폐기한다. query key/value는 route, tag, metric key, payload, 로그, rollup key, read model에 남기지 않는다.
5. allowlist에 정확히 하나의 template이 매칭되는 경우에만 해당 allowlist template을 normalized route로 사용한다.
6. allowlist miss는 safe prefix collapse를 시도한 뒤 policy가 허용한 bounded route 또는 `UNKNOWN`으로 수렴한다. ambiguous match, absolute URL raw 후보, decoding failure, 첫 segment unsafe 후보는 `UNKNOWN`으로 수렴한다.
7. `userId`, `tenantId`, `sessionId`, `traceId`, arbitrary label은 starter payload 후보에 남지 않는다.
8. Story 2.3 rollup input은 normalized route만 받는다.
9. Story 2.5 envelope builder가 raw path/query/high-cardinality tag를 직렬화할 수 없도록 model 경계가 고정된다.
10. Prometheus/scrape/query UI 경로를 추가하지 않는다.

## Suggested Tasks

1. Story 2.1 observation input shape를 확인한다.
2. `NormalizedRoute` model을 추가한다.
3. route normalization service를 추가한다.
4. allowed tag key policy를 코드 또는 enum/value object로 고정한다.
5. framework route pattern/template 우선순위를 구현한다.
6. allowlist exact-one match와 `UNKNOWN` fallback 경계를 구현한다.
7. high-cardinality tag 차단 테스트를 추가한다.
8. raw path candidate query 폐기와 allowlist exact-one matching 테스트를 추가한다.
9. rollup input이 normalized route만 받도록 boundary를 정리한다.
10. 기존 starter/portal tests를 실행한다.

## Test Requirements

- raw path candidate query key/value 폐기 테스트
- allowlist exact-one match와 ambiguous match `UNKNOWN` 테스트
- safe prefix collapse, invalid path, absolute URL, decoding failure `UNKNOWN` 테스트
- framework route template 우선순위 테스트
- high-cardinality tag rejection/sanitization 테스트
- endpoint key boundedness 테스트
- forbidden package guard
- 권장 실행 명령: `./gradlew test`

## Developer Guardrails

- raw path를 endpoint key, payload, rollup key, metric tag, read model, 로그에 넣지 않는다.
- raw path candidate는 route attribution source-of-truth가 허용한 low-cardinality `uri`/`path` 후보로만 사용하며, payload/rollup/read model에 원문을 남기지 않는다.
- query string은 정규화하지 않는다. `?` 이후 key/value는 어떤 산출물에도 남기지 않으며, `?...`는 bounded omission marker일 때만 허용한다.
- allowlist 없는 임의 template 추론은 MVP에서 구현하지 않는다. Safe prefix collapse는 raw 값을 template 변수로 추론하지 않고 unsafe suffix를 `/...`로 접는 bounded fallback이다.
- arbitrary tag map을 starter model 또는 envelope 후보에 추가하지 않는다.
- tenant/user/session/trace 식별자를 MVP metric tag로 허용하지 않는다.
- query parameter opt-in이나 route/display masking annotation을 MVP guard 우회 경로로 추가하지 않는다.
- route normalization 실패를 host request failure로 전파하지 않는다.
- Story 2.3보다 앞서 low-cardinality guard를 닫는다.
- portal ingest validation은 Epic 3에서 구현한다.

## Tasks/Subtasks

- [x] Story 2.1 observation input shape를 확인한다.
- [x] `NormalizedRoute` model을 추가한다.
- [x] route normalization service를 추가한다.
- [x] allowed tag key policy를 고정한다.
- [x] framework route pattern/template 우선순위를 구현한다.
- [x] allowlist exact-one match와 `UNKNOWN` fallback 경계를 구현한다.
- [x] high-cardinality tag 차단 테스트를 추가한다.
- [x] raw path candidate query 폐기와 allowlist exact-one matching 테스트를 추가한다.
- [x] rollup input이 normalized route만 받도록 boundary를 정리한다.
- [x] 기존 starter/portal tests를 실행한다.

## Dev Agent Record

### Implementation Plan

- Story 2.1의 `HttpServerObservationInput` shape를 유지하고, 그 다음 단계 모델을 새로 만들어 raw path/high-cardinality tag가 이어지지 않게 한다.
- `model.route`에 `NormalizedRoute`를 추가하고, `service.RouteNormalizationService`에서 source-of-truth route attribution policy에 맞춰 `http.route`, low-cardinality raw 후보 fallback, allowlist, safe prefix collapse, `UNKNOWN` 경계를 닫는다.
- `LowCardinalityHttpObservationGuard`가 `HttpServerObservationInput`을 받아 `LowCardinalityHttpServerObservation`으로 변환하게 하여 Story 2.3 rollup input과 Story 2.5 envelope builder 후보를 normalized route 경계로 고정한다.
- `LowCardinalityTagKey` enum과 `EndpointKey` value object로 허용 식별자와 endpoint key 구성을 코드에 고정한다.

### Debug Log

- 2026-05-13: 필수 문서와 Story 2.1 완료 산출물을 읽고, Story 2.2 및 sprint-status를 `in-progress`로 갱신했다.
- 2026-05-13: Red phase로 route normalization/low-cardinality guard 테스트를 먼저 추가했고, 아직 구현되지 않은 `NormalizedRoute`, `RouteNormalizationService`, guard/model 타입 때문에 `./gradlew :observability-spring-boot-starter:test --rerun-tasks`가 compile failure로 실패함을 확인했다.
- 2026-05-13: `NormalizedRoute`, `EndpointKey`, `LowCardinalityTagKey`, `LowCardinalityHttpServerObservation`, `RouteNormalizationService`, `LowCardinalityHttpObservationGuard`를 추가했다.
- 2026-05-13: `./gradlew :observability-spring-boot-starter:test --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, 3 actionable tasks executed.
- 2026-05-13: `./gradlew test --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, 7 actionable tasks executed.
- 2026-05-13: `git diff --check` 실행 결과 문제 없음.

### Completion Notes

- raw request path를 endpoint key나 starter payload 후보로 넘기지 않도록 `LowCardinalityHttpServerObservation` 출력 모델에는 `NormalizedRoute`만 보관한다.
- Story 8.1 이후 framework/custom `http.route` 후보는 최우선으로 정규화하되, 처리 결과가 `UNKNOWN`이면 low-cardinality raw 후보 fallback을 볼 수 있다.
- allowlist match는 raw path candidate를 query key/value 폐기 후 template에 매칭하며, 정확히 하나가 매칭된 경우에만 allowlist template을 반환한다.
- allowlist miss는 safe prefix collapse를 시도할 수 있고, ambiguous match, unsafe 후보, absolute URL, decoding failure는 `UNKNOWN`으로 수렴한다.
- endpoint key와 metric tag 식별자는 `method + normalized route`로만 생성되며 user/tenant/session/trace/arbitrary label은 key나 출력 모델에 포함하지 않는다.
- `LowCardinalityHttpServerObservation` 출력 모델은 `statusCode`, `error`, `errorType`을 endpoint 식별자가 아닌 bounded metric signal로 보관한다.
- route normalization 실패 또는 untrusted URL/raw path 후보는 host request failure로 전파하지 않고 `UNKNOWN` route로 sanitize한다.
- Prometheus/scrape/query UI 경로, bucket rollup, queue/flush worker, HTTP ingest client, envelope builder, portal ingest validation/persistence는 추가하지 않았다.
- 2026-05-14 B안 보정 기록은 Story 8.1 source-of-truth note로 superseded됐다.

### Review Closure - 2026-05-18

- Route Attribution B안 기준 review closure 기록은 당시 구현 결과와 재대조한 historical record다. 현재 정책 판단은 Story 8.1 source-of-truth note를 따른다.
- 남은 review finding은 없다. 현재 `RouteNormalizationService` 정책 설명은 `planning-artifacts/contracts/route-attribution-policy.md`와 Story 8.1 기록을 따른다.
- `LowCardinalityHttpObservationGuard`, `MetricBucketRollupService`, `IngestEnvelopeBuilderService` 경계는 raw path/query/high-cardinality tag를 다음 산출 모델, rollup key, envelope payload로 승격하지 않고 normalized route만 소비한다.
- starter main source에는 Prometheus/scrape/query UI 경로, portal ingest validation/persistence, idempotency repository를 추가하지 않았다.

검증 명령/결과:

- `./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.service.RouteNormalizationServiceTest --tests com.observation.starter.service.LowCardinalityHttpObservationGuardTest --tests com.observation.starter.spring.observation.MicrometerHttpServerObservationBinderTest --rerun-tasks` → `BUILD SUCCESSFUL`
- `./gradlew :observability-spring-boot-starter:test --rerun-tasks` → `BUILD SUCCESSFUL`
- `./gradlew :observability-spring-boot-starter:test --rerun-tasks --continue` → `BUILD SUCCESSFUL`
- `./gradlew test --rerun-tasks --continue` → `observability-portal:CatalogSchemaMigrationIntegrationTest` initializationError로 실패. 원인은 Testcontainers가 유효한 Docker 환경을 찾지 못함(`/var/run/docker.sock` 없음)이며 Story 2.2 starter 구현 실패는 아니다.
- `git diff --check` → 문제 없음

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/2-2-route-normalization-and-low-cardinality-guard.md`
- `observability-spring-boot-starter/build.gradle`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionAutoConfiguration.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionProperties.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/HttpServerObservationInput.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/EndpointKey.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/LowCardinalityHttpServerObservation.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/LowCardinalityTagKey.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/route/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/route/NormalizedRoute.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/LowCardinalityHttpObservationGuard.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/RouteNormalizationService.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinder.java`
- `observability-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/config/RouteAttributionPropertiesTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/LowCardinalityHttpObservationGuardTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/RouteNormalizationServiceTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinderTest.java`

## Change Log

- 2026-05-13: Story 2.2 implementation started and completed; route normalization, low-cardinality guard model/service, tag policy, endpoint key, and guard tests added.
- 2026-05-14: Route Attribution B안 승인 기준에 맞춰 `http.route` 우선, allowlist exact-one fallback, query 폐기, `UNKNOWN` 수렴 정책과 starter allowlist 설정을 반영했다.
- 2026-06-01: Story 8.1 route normalization source-of-truth 기준으로 current planning/AC/guardrail 문구를 정렬했다.

## Status

done
