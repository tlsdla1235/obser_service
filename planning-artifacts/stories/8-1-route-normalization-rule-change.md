---
artifactType: story
storyId: "8.1"
storyKey: "8-1-route-normalization-rule-change"
epic: "Epic 8. Route Attribution Contract Hardening"
title: "Route normalization rule change"
architectureStyle: Traditional MVC
status: review
date: 2026-06-01
---

# Story 8.1 - Route Normalization Rule Change

## Status

review

## Story

starter 구현자는 route normalization 규칙을 최신 `route-attribution-policy` source of truth에 맞게 바꾸고 싶다.

그래야 `http.route`가 제공하는 framework/custom template 후보는 최대한 살리면서도, 부정확하거나 unsafe한 후보는 allowlist exact-one match 또는 safe prefix collapse로만 bounded route에 귀속되고, raw URL/query 값이 endpoint key, ingest payload, read model, UI에 원문으로 남지 않는다.

## Source of Truth

구현 중 충돌처럼 보이는 지점은 아래 우선순위를 따른다.

1. `planning-artifacts/contracts/route-attribution-policy.md`
2. `implementation-artifacts/spec-story-8-1-route-normalization-contract-decisions.md`
3. `implementation-artifacts/prompt-story-8-1-bmad-dev-story.md`
4. `planning-artifacts/stories/2-2-route-normalization-and-low-cardinality-guard.md`
5. `planning-artifacts/contracts/metric-taxonomy.md`
6. `planning-artifacts/contracts/ingest-envelope.md`
7. `planning-artifacts/architecture.md`
8. `planning-artifacts/architecture-implementation-supplement.md`
9. `planning-artifacts/project-structure.md`
10. `planning-artifacts/epics.md`
11. `planning-artifacts/sprint-plan.md`
12. `_bmad/custom/project-context.md`
13. `AGENTS.md`

핵심 우선순위:

- route normalization 규칙은 `planning-artifacts/contracts/route-attribution-policy.md`를 최우선으로 따른다.
- 기존 Story 2.2 또는 `metric-taxonomy.md` 요약이 "invalid present `http.route`는 raw fallback 금지"라고 말하면, Story 8.1에서는 새 route attribution source of truth를 우선한다.
- `ingest-envelope.md`의 raw path/query/high-cardinality payload 금지 원칙은 계속 유효하다.
- 새 public class/method/helper와 이해하기 어려운 내부 helper에는 `AGENTS.md` 기준 한국어 Javadoc/comment를 작성한다.

## Scope / Out of Scope

포함:

- `http.route` multi-step normalization 규칙 변경
- `?...` bounded omission marker를 safe normalized template 안에서 허용
- `/...` safe prefix collapse marker를 normalized route로 허용
- `http.route` 처리 결과가 `UNKNOWN`이면 low-cardinality `uri`/`path` raw 후보 fallback을 볼 수 있게 변경
- raw 후보에서 allowlist exact-one match 우선, 실패 시 safe prefix collapse 적용
- raw `uri`와 `path`가 모두 있으면 `uri` 우선
- endpoint key를 항상 `method + normalized route`로 유지
- `http.url`, high-cardinality tag, context custom value, arbitrary tag map이 route source가 되지 않는 guard 유지
- starter route normalization, observation binding, guard, route value object, allowlist properties focused tests 보강

제외:

- Project seed 발급 UI
- public project creation API/UI
- project key issuance/rotation/revocation UI
- onboarding wizard
- browser token persistence
- raw route explorer, arbitrary query UI, raw query explorer
- annotation 기반 query dimension opt-in
- custom metric/tag 확장
- portal read model 계산 logic 변경
- endpoint priority ranking 변경
- p95/p99 계산 변경
- starter request path에서 network call을 수행하는 변경

## Acceptance Criteria

1. `http.route` exact template은 normalized route로 그대로 사용한다.
2. `http.route` `?...` marker template은 raw query value가 아닌 bounded omission marker로 허용한다.
3. `http.route` 실제 query suffix는 strip하고 safe template만 normalized route로 사용한다.
4. `http.route`가 raw identifier segment를 포함하면 safe prefix collapse를 적용한다.
5. `http.route` malformed template marker는 prefix collapse 대상이 아니며 `UNKNOWN` 후 low-cardinality raw 후보 단계로 넘어갈 수 있다.
6. `http.route` absolute URL은 `http.route` 단계에서 실패하고 low-cardinality raw 후보 단계로 넘어갈 수 있다.
7. raw `uri`/`path` 후보는 allowlist exact-one match 성공 시 raw path가 아니라 allowlist template을 반환한다.
8. raw `uri`와 `path`가 모두 있으면 `uri`가 우선한다.
9. raw `uri`/`path` 후보가 여러 allowlist와 match되면 `UNKNOWN`이다.
10. raw `uri`/`path` 후보는 allowlist 실패 시 safe prefix collapse를 적용한다.
11. raw query key/value는 normalized route, endpoint key, payload, read model, UI에 남지 않는다.
12. `http.url`과 high-cardinality tag는 raw 후보로 쓰지 않는다.
13. first segment가 unsafe이면 `UNKNOWN`이다.
14. endpoint key는 항상 `method + normalized route`다.
15. allowlist는 path template만 허용하며 query key/value와 `?...` marker를 reject한다.
16. route normalization 실패는 host request failure로 전파하지 않고 `UNKNOWN`으로 수렴한다.
17. `?...` marker는 `NormalizedRoute` value object뿐 아니라 binder/input/guard runtime 경계에서도 end-to-end로 보존된다.

## Given / When / Then Acceptance Cases

1. Given low-cardinality `http.route=/orders/{orderId}`이고 `uri=/orders/33?debug=true`다. When route normalization을 수행한다. Then normalized route는 `/orders/{orderId}`이고 endpoint key는 `GET /orders/{orderId}`다. And `/orders/33`과 `debug=true`는 출력 모델에 남지 않는다.
2. Given low-cardinality `http.route=/{userId}?.../posts`다. When route template contract를 검증한다. Then normalized route는 `/{userId}?.../posts`이고 endpoint key는 `GET /{userId}?.../posts`다. And `?...`는 bounded omission marker다.
3. Given Micrometer low-cardinality `http.route=/{userId}?.../posts`다. When binder가 `HttpServerObservationInput`을 만들고 guard가 route normalization을 수행한다. Then input `routePattern`과 guard 결과 normalized route는 모두 `/{userId}?.../posts`다.
4. Given low-cardinality `http.route=/orders/{orderId}?token=abc`다. When route normalization을 수행한다. Then normalized route는 `/orders/{orderId}`이고 endpoint key는 `GET /orders/{orderId}`다. And `token=abc`는 출력에 남지 않는다.
5. Given low-cardinality `http.route=/posts/deadbeef/comments/9`다. When safe template 검증이 실패한다. Then normalized route는 `/posts/...`다. And `deadbeef/comments/9`는 출력에 남지 않는다.
6. Given low-cardinality `http.route=https://example.test/orders/{orderId}`, low-cardinality `uri=/orders/33?debug=true`, allowlist `/orders/{orderId}`가 있다. When `http.route`가 실패한다. Then raw 후보 allowlist exact-one match로 normalized route는 `/orders/{orderId}`다.
7. Given low-cardinality `http.route=/orders/{orderId`, low-cardinality `uri=/orders/33`, allowlist `/orders/{orderId}`가 있다. When malformed template marker 때문에 `http.route` 단계가 `UNKNOWN`으로 수렴한다. Then raw 후보 allowlist exact-one match로 normalized route는 `/orders/{orderId}`다.
8. Given `http.route`가 없고 low-cardinality `uri=/posts/33/comments/9?debug=true`, allowlist `/posts/{postId}/comments/{commentId}`가 있다. When raw 후보 query를 폐기하고 allowlist match를 수행한다. Then normalized route는 `/posts/{postId}/comments/{commentId}`다.
9. Given `http.route`가 없고 low-cardinality `uri=/orders/33`, low-cardinality `path=/payments/99`, allowlist `/orders/{orderId}`, `/payments/{paymentId}`가 있다. When raw 후보 선택과 allowlist match를 수행한다. Then normalized route는 `/orders/{orderId}`다.
10. Given `http.route`가 없고 low-cardinality `uri=/orders/33`, allowlist `/orders/{orderId}`, `/orders/{id}`가 있다. When allowlist match를 수행한다. Then normalized route는 `UNKNOWN`이다.
11. Given `http.route`가 없고 low-cardinality `uri=/api/v1/orders/33/items/9?debug=true`이며 allowlist match가 없다. When raw 후보 prefix collapse를 수행한다. Then normalized route는 `/api/v1/orders/...`다.
12. Given low-cardinality `uri=/orders/33?next=/payments/99&token=abc`다. When route normalization을 수행한다. Then allowlist가 있으면 `/orders/{orderId}`, allowlist가 없으면 `/orders/...` 또는 `UNKNOWN`만 가능하다. And `/payments/99`, `token=abc`, `next` 값은 route로 되살리지 않는다.
13. Given high-cardinality `http.url=https://example.test/orders/33?token=abc`만 있고 low-cardinality `uri`/`path`가 없다. When route normalization을 수행한다. Then normalized route는 `UNKNOWN`이다.
14. Given low-cardinality `uri=/33/posts`다. When allowlist match가 없고 prefix collapse를 수행한다. Then normalized route는 `UNKNOWN`이다.
15. Given allowlist 설정에 `/orders/{orderId}?debug=true` 또는 `/{userId}?.../posts`가 들어온다. When `RouteAttributionProperties`가 binding/normalization을 수행한다. Then 설정은 reject된다.

## Tasks / Subtasks

- [x] Route normalization source precedence 변경 (AC: 1~13, 16)
  - [x] `RouteNormalizationService`가 `http.route`를 safe template, `?...` marker template, safe prefix collapse 순서로 평가하게 한다.
  - [x] `http.route`가 null, blank, `UNKNOWN`이면 raw 후보 단계로 넘어가게 한다.
  - [x] `http.route` 처리 결과가 `UNKNOWN`이면 low-cardinality `uri`/`path` raw 후보 fallback을 볼 수 있게 한다.
  - [x] `http.route`가 absolute URL, invalid percent encoding, control character, route-shaped가 아닌 값이면 raw 후보 단계로 넘어갈 수 있게 한다.
  - [x] malformed template marker, wildcard, arbitrary regex 때문에 실패한 후보는 prefix collapse하지 않고 `UNKNOWN` 후 raw 후보 단계로 넘긴다.

- [x] `?...`와 `/...` route contract 보강 (AC: 2~5, 10, 17)
  - [x] `RouteTemplateContract` 또는 별도 helper가 safe route template, `?...` marker template, `/...` collapse marker를 구분해 검증하게 한다.
  - [x] `NormalizedRoute.of("/{userId}?.../posts")`가 marker를 보존하게 한다.
  - [x] `NormalizedRoute.of("/orders/{orderId}?token=abc")`가 query key/value를 보존하지 않게 한다.
  - [x] `NormalizedRoute.of("/orders/...")`와 `/api/v1/orders/...`가 valid normalized route가 되게 한다.
  - [x] `?...` marker는 allowlist에는 허용하지 않고 normalized route template 안에서만 허용한다.

- [x] raw `uri`/`path` 후보 fallback 정리 (AC: 5~13)
  - [x] raw 후보는 low-cardinality `uri`를 우선하고 없으면 low-cardinality `path`를 사용한다.
  - [x] raw 후보의 첫 `?` 뒤 query string은 폐기하고 query value 안의 path-like 값을 route로 되살리지 않는다.
  - [x] allowlist exact-one match가 성공하면 allowlist template 자체를 반환한다.
  - [x] allowlist ambiguous match는 `UNKNOWN`으로 수렴한다.
  - [x] allowlist miss 시 raw 후보에 safe prefix collapse를 적용한다.
  - [x] first segment가 숫자, UUID, long hex 등 unsafe이면 `UNKNOWN`으로 수렴한다.

- [x] Micrometer binder/input/guard boundary 갱신 (AC: 6, 8, 11~12, 16~17)
  - [x] `MicrometerHttpServerObservationBinder`는 source 추출과 high-cardinality 차단만 담당하고, invalid present `http.route`가 raw fallback을 막는 최종 결정을 하지 않게 한다.
  - [x] low-cardinality `http.route=/{userId}?.../posts`가 `HttpServerObservationInput.routePattern`에서 잘리지 않게 한다.
  - [x] low-cardinality `uri`/`path` raw 후보를 service/guard가 판단할 수 있게 input boundary에 전달한다.
  - [x] `http.url`, high-cardinality `uri`/`path`/`http.route`, context custom value, arbitrary tag map은 route source로 승격하지 않는다.
  - [x] `LowCardinalityHttpObservationGuard`는 routePattern과 rawPathCandidate를 함께 service에 넘기고, service 결과가 성공하면 routePattern 우선순위를 유지한다.
  - [x] route normalization 실패는 host request path에 예외를 전파하지 않고 `UNKNOWN`으로 수렴한다.

- [x] Allowlist path-template-only 원칙 유지 (AC: 7, 9, 15)
  - [x] `RouteAttributionProperties` namespace는 `observation.route-attribution.allowlist`로 유지한다.
  - [x] allowlist 항목은 `/orders/{orderId}`, `/api/v1/orders/{orderId}`, `/search` 같은 path template만 허용한다.
  - [x] allowlist에 query key/value, `?...` marker, absolute URL, trailing slash, double slash, concrete identifier segment를 거부한다.

- [x] Focused tests와 regression guard 보강 (AC: 1~17)
  - [x] `RouteNormalizationServiceTest`에 source precedence, `?...`, query suffix strip, malformed marker fallback, absolute URL fallback, raw `uri` 우선, raw prefix collapse, first segment unsafe, raw query value 폐기 case를 추가/갱신한다.
  - [x] `MicrometerHttpServerObservationBinderTest`에 invalid present `http.route` raw candidate 보존, `?...` marker routePattern 보존, `uri` before `path`, high-cardinality source 미사용 case를 추가/갱신한다.
  - [x] `LowCardinalityHttpObservationGuardTest`에 `?...` marker end-to-end 보존, routePattern 실패 후 raw fallback, routePattern 성공 우선, fail-open `UNKNOWN` case를 추가/갱신한다.
  - [x] `RouteAttributionPropertiesTest`에 allowlist query와 `?...` marker reject case를 추가/갱신한다.
  - [x] `StarterObservationArchitectureTest`, `NoPrometheusMvpPathTest`를 실행해 request path network boundary와 negative path guard를 유지한다.

- [x] BMAD story bookkeeping (AC: 1~17)
  - [x] 구현 시작 시 이 story의 `Status`를 `in-progress`로 갱신한다.
  - [x] 구현 완료 시 Tasks/Subtasks 체크박스, Dev Agent Record, File List, Change Log를 실제 변경 내용에 맞게 갱신한다.
  - [x] 현재 `implementation-artifacts/sprint-status.yaml`에는 Story 8.1 key가 없으므로 임의로 새 sprint-status key를 만들지 않는다. 별도 sprint-status entry가 추가된 경우에만 BMAD 규칙에 따라 갱신한다.

## Dev Notes

### Current Code State

- `RouteNormalizationService`는 현재 framework route 후보를 먼저 보고, framework 후보가 present하면 raw path candidate를 사실상 보지 않는다.
- `RouteNormalizationService.normalizeFrameworkRoute`는 현재 query suffix를 strip한 뒤 safe template 검증만 한다. `?...` marker와 `/...` prefix collapse는 아직 contract로 통과하지 않는다.
- `RouteNormalizationService.normalizeAllowlistMatch`는 현재 raw 후보 allowlist exact-one match만 수행하고 raw prefix collapse fallback은 없다.
- `MicrometerHttpServerObservationBinder.rawPathCandidate`는 현재 usable `http.route`가 없거나 blank/`UNKNOWN`일 때만 low-cardinality `uri`/`path`를 넘긴다. Story 8.1에서는 invalid present `http.route`도 service에서 실패 후 raw fallback을 볼 수 있어야 한다.
- `MicrometerHttpServerObservationBinder.routePattern`은 현재 absolute URL을 usable route에서 제외한다. 새 규칙에서는 `http.route` 후보 실패와 raw 후보 fallback 판단을 service/guard 중심으로 둔다.
- `HttpServerObservationInput`은 현재 `routePattern`과 `rawPathCandidate` 양쪽에서 첫 `?` 뒤를 strip한다. `routePattern=/{userId}?.../posts`는 실제 query string이 아니므로 이 marker를 보존해야 한다.
- `LowCardinalityHttpObservationGuard`는 현재 `input.routePattern().isPresent()`이면 rawPathCandidate를 service에 넘기지 않는다. Story 8.1에서는 routePattern 처리 결과가 `UNKNOWN`일 때 raw fallback을 볼 수 있게 함께 전달해야 한다.
- `RouteTemplateContract`는 현재 `?`를 모두 reject한다. Story 8.1에서는 normalized route template 안의 `?...` marker와 `/...` collapse marker를 허용해야 한다.
- `NormalizedRoute`는 현재 첫 `?` 뒤를 모두 strip한다. Story 8.1에서는 `?...` marker는 보존하고 실제 query key/value suffix는 보존하지 않아야 한다.
- `RouteAttributionProperties` allowlist는 path template only 원칙을 계속 유지한다. `?...` marker는 normalized route에는 허용될 수 있지만 allowlist에는 허용하지 않는다.

### Implementation Guardrails

- 사용자 설정을 늘리지 않는다.
- allowlist는 유지하되 `http.route`가 없는 환경 또는 `http.route` 처리 결과가 `UNKNOWN`인 환경에서 raw path를 declared template으로 귀속시키는 fallback으로만 둔다.
- `http.route`가 정확하면 framework/custom route template을 그대로 살린다.
- `http.route`가 부정확하면 raw 값을 저장하지 않고 safe prefix까지만 살린 뒤 `/...`로 접을 수 있다.
- raw URL/query 값은 normalized route, endpoint key, payload, read model, UI에 원문으로 남기지 않는다.
- 실제 URL의 첫 `?` 뒤 값은 어떤 경우에도 route로 되살리지 않는다.
- `?...`는 raw query 보존이 아니라 query/omitted 구간이 있었다는 bounded marker다.
- `/...`는 unsafe/untrusted suffix를 접었다는 marker다. 뒤에 원본 suffix를 붙이지 않는다.
- `http.url` 같은 전체 URL/high-cardinality 값은 raw 후보로 쓰지 않는다.
- allowlist 없이 raw `/posts/33/comments/9`를 `/posts/{id}/comments/{id}`로 임의 추론하지 않는다.
- starter request path에서 network call을 하지 않는 existing non-blocking boundary를 약화하지 않는다.

### Expected Implementation Touchpoints

- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/RouteNormalizationService.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinder.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/LowCardinalityHttpObservationGuard.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/HttpServerObservationInput.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/route/RouteTemplateContract.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/route/NormalizedRoute.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionProperties.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionAutoConfiguration.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/EndpointKey.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/LowCardinalityHttpServerObservation.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/ingest/IngestEnvelope.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/IngestEnvelopeBuilderService.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricBucketRollupService.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/RouteNormalizationServiceTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinderTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/LowCardinalityHttpObservationGuardTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/config/RouteAttributionPropertiesTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/StarterObservationArchitectureTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/NoPrometheusMvpPathTest.java`

### Testing

권장 검증 명령:

```bash
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.service.RouteNormalizationServiceTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.spring.observation.MicrometerHttpServerObservationBinderTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.service.LowCardinalityHttpObservationGuardTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.config.RouteAttributionPropertiesTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.architecture.StarterObservationArchitectureTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.architecture.NoPrometheusMvpPathTest
./gradlew :observability-spring-boot-starter:test
git diff --check
```

완료 선언 전 확인:

- `planning-artifacts/contracts/route-attribution-policy.md`의 source precedence와 구현이 일치한다.
- `http.route` success, `http.route` failure 후 raw fallback, raw allowlist fallback, raw prefix collapse가 모두 테스트로 고정된다.
- `?...`와 `/...` marker가 bounded marker로만 동작한다.
- `?...` marker는 `NormalizedRoute` 단위 검증뿐 아니라 binder/input/guard runtime 경계에서도 보존되는 테스트가 있다.
- `http.route` 실제 query suffix strip과 malformed template marker 후 raw fallback 기대값이 테스트로 고정된다.
- raw `uri`와 `path`가 모두 있을 때 `uri` 우선순위가 테스트로 고정된다.
- raw query value와 `http.url` source가 normalized route, endpoint key, payload, read model, UI에 남지 않는다.
- allowlist가 path template only 원칙을 유지한다.
- 기존 starter non-blocking/request-path guard가 깨지지 않는다.
- Project seed 발급 UI나 public project creation surface를 열지 않는다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-06-01: 이 planning story 생성을 위해 BMAD create-story workflow, route attribution source of truth, Story 8.1 계약 결정 문서, implementation prompt, Story 2.2 이전 route guard story, 현재 starter route code boundary를 확인했다.
- 2026-06-01: `implementation-artifacts/sprint-status.yaml`에는 Epic 8 또는 Story 8.1 항목이 없다. implementation prompt 지침에 따라 story 파일 생성 중 sprint-status key를 만들거나 갱신하지 않았다.
- 2026-06-01: BMAD dev-story workflow와 프로젝트 컨텍스트를 확인했고, Story 8.1 항목이 sprint-status에 없어 story 파일 Status만 `in-progress`로 갱신한 뒤 구현을 시작했다.
- 2026-06-01: RED 단계에서 route normalization, binder/input/guard, allowlist, NormalizedRoute focused 테스트를 Story 8.1 기대값으로 갱신했고 기존 구현의 실패를 확인했다.
- 2026-06-01: GREEN/REFACTOR 단계에서 source precedence, `?...` marker 보존, `/...` prefix collapse, raw fallback, allowlist path-template-only 검증을 구현했다.
- 2026-06-01: focused route 테스트, architecture/negative-path guard 테스트, starter 모듈 전체 테스트를 통과했다. sprint-status에는 Story 8.1 entry가 없어 변경하지 않았다.

### Completion Notes List

- `RouteNormalizationService`가 `http.route` 성공을 우선하되 실패 시 low-cardinality raw `uri/path` allowlist exact-one과 safe prefix collapse fallback을 수행하도록 변경했다.
- `RouteTemplateContract`, `NormalizedRoute`, `HttpServerObservationInput`에서 `?...` bounded omission marker를 보존하고 실제 query key/value suffix는 폐기하도록 분리했다.
- `MicrometerHttpServerObservationBinder`와 `LowCardinalityHttpObservationGuard`가 routePattern과 rawPathCandidate를 함께 service에 넘기도록 바꿔 invalid present `http.route`가 raw fallback을 막지 않게 했다.
- allowlist는 path-template-only 원칙을 유지하며 query, `?...`, absolute URL, trailing/double slash, concrete identifier segment를 계속 거부한다.
- raw query value, `http.url`, high-cardinality tag, context custom value, arbitrary tag map이 endpoint key/payload/read model/UI 경계로 승격되지 않는 focused/regression 테스트를 통과했다.

### File List

- `implementation-artifacts/prompt-story-8-1-bmad-dev-story.md`
- `implementation-artifacts/spec-story-8-1-route-normalization-contract-decisions.md`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionAutoConfiguration.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionProperties.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/HttpServerObservationInput.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/route/NormalizedRoute.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/route/RouteTemplateContract.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/LowCardinalityHttpObservationGuard.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/RouteNormalizationService.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinder.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/config/RouteAttributionPropertiesTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/model/route/NormalizedRouteTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/LowCardinalityHttpObservationGuardTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/RouteNormalizationServiceTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinderTest.java`
- `planning-artifacts/contracts/route-attribution-policy.md`
- `planning-artifacts/stories/8-1-route-normalization-rule-change.md`

### Change Log

- 2026-06-01: Story 8.1 strict BMAD planning story file을 `ready-for-dev` 상태로 생성했다. 구현은 시작하지 않았고, Story 8.1 sprint-status 항목이 없어서 sprint-status는 변경하지 않았다.
- 2026-06-01: Story 8.1 route normalization rule change를 구현하고 focused/starter regression 테스트 통과 후 Status를 `review`로 갱신했다. Story 8.1 sprint-status 항목이 없어 sprint-status는 변경하지 않았다.

## Status

review
