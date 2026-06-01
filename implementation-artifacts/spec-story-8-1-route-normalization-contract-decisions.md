---
artifactType: contract-decisions
projectName: Spring Boot 운영 첫 화면 포털
storyId: "8.1"
storyKey: "8-1-route-normalization-contract-decisions"
status: closed
date: 2026-06-01
scope: Story 8.1 route normalization rule change source-of-truth and dev-story preparation
---

# Story 8.1 Route Normalization Contract Decisions

## Purpose

Story 8.1은 starter route normalization 규칙을 새 source of truth에 맞춰 바꾸기 전에, 계약과 구현 지침을 닫는다.

이 문서는 바로 구현을 시작하기 위한 파일이 아니라 구현 전 결정 문서다. 목표는 raw URL/query 값이 endpoint key, payload, read model, UI에 남지 않는 원칙을 유지하면서도 `http.route`가 제공하는 template 후보를 최대한 살리고, 부정확한 후보는 allowlist 또는 safe prefix collapse로만 bounded route에 귀속시키는 것이다.

Project seed 발급 UI는 배포 목표에 포함될 수 있지만 Story 8.1 범위가 아니다. public project creation, project key issuance/rotation UI, onboarding UI는 별도 story로 분리한다.

## Authority and Source of Truth

Story 8.1 dev-story는 아래 문서를 기준으로 한다.

1. `planning-artifacts/contracts/route-attribution-policy.md`
2. `planning-artifacts/stories/2-2-route-normalization-and-low-cardinality-guard.md`
3. `planning-artifacts/contracts/metric-taxonomy.md`
4. `planning-artifacts/contracts/ingest-envelope.md`
5. `planning-artifacts/architecture.md`
6. `planning-artifacts/architecture-implementation-supplement.md`
7. `planning-artifacts/project-structure.md`
8. `planning-artifacts/epics.md`
9. `planning-artifacts/sprint-plan.md`
10. `_bmad/custom/project-context.md`
11. `AGENTS.md`

우선순위:

- route normalization 규칙은 `planning-artifacts/contracts/route-attribution-policy.md`를 최우선으로 따른다.
- 기존 Story 2.2나 `metric-taxonomy.md`의 요약 문구가 "invalid present `http.route`는 raw fallback 금지"라고 말하면 Story 8.1에서는 route attribution source of truth를 우선한다.
- `ingest-envelope.md`의 raw path/query/high-cardinality payload 금지 원칙은 계속 유효하다.
- AGENTS.md의 한국어 주석/Javadoc 지침은 새 public class/method/helper 및 이해하기 어려운 내부 helper에 적용한다.

## Scope

포함:

- `http.route` multi-step normalization 규칙 구현 준비
- `?...` bounded omission marker 허용 계약 정리
- `/...` safe prefix collapse marker 허용 계약 정리
- raw `uri`/`path` 후보 fallback 조건 변경
- allowlist exact-one fallback 유지
- endpoint key/display/query marker 경계 정리
- starter route normalization, observation binding, guard, route value object, focused tests에 필요한 구현 지침 정리

제외:

- Project seed 발급 UI
- public project creation API
- project key issuance/rotation/revocation UI
- onboarding wizard
- browser token persistence
- raw route explorer 또는 query explorer
- arbitrary query UI
- annotation 기반 query dimension opt-in
- custom metric/tag 확장
- portal read model 계산 logic 변경
- endpoint priority ranking 변경
- p95/p99 계산 변경

## Non-Reopen Rules

Story 8.1 dev-story는 아래 결정을 다시 열지 않는다.

- 사용자 설정은 최대한 줄인다.
- allowlist는 유지하되, `http.route`가 없는 환경에서 raw path를 declared template으로 귀속시키는 fallback으로만 둔다.
- `http.route`가 정확하면 framework/custom route template을 그대로 살린다.
- `http.route`가 부정확하면 raw 값을 저장하지 않고 safe prefix까지만 살린 뒤 `/...`로 접을 수 있다.
- raw URL/query 값은 normalized route, endpoint key, payload, read model, UI에 원문으로 남기지 않는다.
- 실제 URL의 첫 `?` 뒤 값은 어떤 경우에도 route로 되살리지 않는다.
- `?...`는 query/omitted 구간이 있었다는 bounded marker이지 raw query 보존이 아니다.
- `/...`는 unsafe/untrusted suffix collapse marker이지 원본 suffix 보존이 아니다.
- `http.url` 또는 high-cardinality tag는 raw 후보가 아니다.
- allowlist 없이 raw `/posts/33/comments/9`를 `/posts/{id}/comments/{id}`로 임의 추론하지 않는다.

## Closed Decisions

### 1. Source Precedence Contract

최종 normalize source 우선순위는 아래로 고정한다.

1. `http.route`를 최우선 source로 사용한다.
2. `http.route`에서 safe template, `?...` marker template, safe prefix collapse 순서로 정규화를 시도한다.
3. `http.route` 처리 결과가 `UNKNOWN`이면 low-cardinality `uri`/`path` raw 후보를 본다.
4. raw 후보는 allowlist exact-one match를 먼저 시도한다.
5. allowlist 실패 시 raw 후보에도 safe prefix collapse를 적용한다.
6. safe prefix도 없으면 `UNKNOWN`이다.
7. `http.url` 같은 전체 URL/high-cardinality 값은 raw 후보로 쓰지 않는다.

결정 이유:

- framework/custom `http.route`는 사용자가 의도한 template일 가능성이 가장 높다.
- malformed `http.route`가 들어오는 환경에서도 low-cardinality `uri`/`path`와 allowlist가 있으면 안전한 template fallback이 가능하다.
- fallback을 허용하더라도 high-cardinality source와 raw query 값은 계속 차단한다.

### 2. `http.route` Multi-Step Normalization Contract

`http.route` 처리 상세는 아래로 고정한다.

- null, blank, `UNKNOWN`이면 source 없음으로 보고 raw 후보 단계로 넘어간다.
- absolute URL, control character, invalid percent encoding, route-shaped가 아닌 값은 `http.route` 단계에서 실패로 본다.
- safe route template이면 그대로 normalized route로 사용한다.
- `?...` omission marker가 포함된 safe template이면 그대로 normalized route로 사용한다.
- 실제 query key/value처럼 보이는 `?token=abc`, `?debug=true`, `?redirect=...`는 normalized route로 직접 허용하지 않는다.
- `http.route=/orders/{orderId}?token=abc`처럼 safe template 뒤에 실제 query suffix가 붙은 경우 query suffix를 버리고 `/orders/{orderId}`만 normalized route로 사용한다.
- template 검증이 실패했지만 route-shaped prefix가 있으면 safe prefix collapse를 시도한다.
- `/orders/{orderId`처럼 malformed template marker 때문에 실패한 값은 prefix collapse 대상이 아니다. 이 경우 `http.route` 단계는 `UNKNOWN`으로 수렴하고 low-cardinality raw 후보 fallback으로 넘어간다.
- prefix collapse도 실패하면 `http.route` 처리 결과는 `UNKNOWN`이다.

결정 이유:

- `http.route` 후보를 무조건 버리면 custom instrumentation이 제공한 bounded template을 잃는다.
- 반대로 raw query key/value를 허용하면 endpoint cardinality와 secret exposure 경계가 깨진다.

### 3. Raw Candidate Contract

raw 후보는 low-cardinality `uri`를 우선 보고 없으면 low-cardinality `path`를 본다.

결정 내용:

- raw 후보는 `http.route`가 없거나 blank/`UNKNOWN`이거나, `http.route` 처리 결과가 `UNKNOWN`일 때만 normalization decision에 사용한다.
- raw 후보 추출은 low-cardinality `uri`/`path`에 한정한다.
- `uri`와 `path`가 둘 다 있으면 `uri`를 `path`보다 우선한다.
- raw 후보의 첫 `?` 뒤 query string은 폐기한다.
- raw 후보와 allowlist template이 정확히 하나 match되면 allowlist template을 반환한다.
- allowlist match가 없으면 safe prefix collapse를 시도한다.
- raw 후보 첫 segment부터 unsafe하면 `UNKNOWN`이다.

금지:

- `http.url`에서 path를 파싱해 raw 후보로 쓰기
- high-cardinality `uri`, `path`, `http.route`를 raw 후보로 쓰기
- context custom value 또는 arbitrary tag map을 raw 후보로 쓰기
- query value 안의 path-like 값을 route로 되살리기

### 4. Allowlist Contract

allowlist는 path template만 선언한다.

결정 내용:

- `{postId}` 같은 변수 segment는 한 path segment matcher로 동작한다.
- match 결과로 raw path가 아니라 allowlist template 자체를 반환한다.
- query key/value와 `?...` marker는 allowlist에 넣지 않는다.
- ambiguous match는 `UNKNOWN`이다.

예:

- raw `/posts/33/comments/9`, allowlist `/posts/{postId}/comments/{commentId}` -> `/posts/{postId}/comments/{commentId}`
- raw `/orders/33`, allowlist `/orders/{orderId}`, `/orders/{id}` -> `UNKNOWN`

### 5. Safe Prefix Collapse Contract

prefix collapse는 normalize가 깨지는 첫 segment부터 원본 suffix를 버리고 `/...`로 접는다.

결정 내용:

- 안전한 prefix가 최소 1개 이상 있어야 한다.
- `/...` 뒤에 원본 path/query suffix를 붙이지 않는다.
- first segment가 숫자, UUID, long hex, malformed segment 등 unsafe면 `UNKNOWN`이다.
- prefix collapse는 raw path를 template 변수로 자동 변환하지 않는다.
- malformed template marker, wildcard, arbitrary regex 때문에 깨진 후보는 prefix collapse로 일부를 살리지 않고 `UNKNOWN`으로 수렴한다.

예:

- `/posts/deadbeef/comments/9` -> `/posts/...`
- `/api/v1/orders/33/items/9` -> `/api/v1/orders/...`
- `/orders/{orderId` -> `UNKNOWN` 후 raw 후보 fallback
- `/33/posts` -> `UNKNOWN`

### 6. Query and Omission Marker Contract

실제 raw URL의 첫 `?` 뒤는 모두 query string이다.

결정 내용:

- query key/value, token, redirect URL, search term은 저장하지 않는다.
- `?...` marker는 safe normalized template 안에서만 허용한다.
- `http.route=/{userId}?.../posts`처럼 application/custom instrumentation이 명시한 template이면 endpoint로 허용할 수 있다.
- `http.route=/orders/{orderId}?token=abc`처럼 실제 query suffix가 붙은 route 후보는 query suffix를 strip하고 `/orders/{orderId}`만 사용한다.
- raw URL에 query가 있었음을 표시해야 하면 route 자체를 바꾸지 않고 `queryStringPresent=true` 또는 display-only label을 사용한다.
- display-only label은 `/orders/{orderId}?...`처럼 만들 수 있지만 endpoint key와 payload route는 `/orders/{orderId}`를 유지한다.

금지:

- `/orders/{orderId}?token=abc` 저장
- `/orders/{orderId}?redirect=https://example.com/a` 저장
- raw `/orders/33?next=/payments/99`에서 `/payments/99`를 route로 되살리기

### 7. Endpoint Key and UI Contract

endpoint key는 항상 `method + normalized route`다.

결정 내용:

- raw query value는 endpoint key에 포함하지 않는다.
- `http.route=/{userId}?.../posts`가 통과하면 endpoint key는 `GET /{userId}?.../posts`다.
- UI는 raw path/query/token/URL을 노출하지 않는다.
- display-only query marker가 필요하면 bounded marker 또는 boolean signal을 사용한다.

## Current Code Impact

Story 8.1 구현자는 최소한 아래 현재 코드 경계를 읽고 수정 필요성을 판단해야 한다.

1. `observability-spring-boot-starter/src/main/java/com/observation/starter/service/RouteNormalizationService.java`
   - 현재 framework route를 먼저 처리하고, 그 결과가 invalid이면 곧바로 `UNKNOWN`으로 수렴한다.
   - `?...` marker와 `/...` prefix collapse를 아직 route contract로 허용하지 않는다.
   - raw 후보 prefix collapse가 없다.
2. `observability-spring-boot-starter/src/main/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinder.java`
   - 현재 non-blank `http.route`가 있으면 raw path candidate 저장을 막는다.
   - 새 규칙에서는 low-cardinality `uri`/`path` 후보를 임시 경계에 보관하되 normalization decision은 service/guard가 해야 한다.
   - `http.url`과 high-cardinality tag 무시는 계속 유지한다.
3. `observability-spring-boot-starter/src/main/java/com/observation/starter/service/LowCardinalityHttpObservationGuard.java`
   - 현재 `routePattern().isPresent()`이면 raw 후보를 service에 넘기지 않는다.
   - 새 규칙에서는 `http.route` 처리 결과가 `UNKNOWN`일 때 raw 후보 fallback을 볼 수 있게 raw 후보를 함께 넘겨야 한다.
4. `observability-spring-boot-starter/src/main/java/com/observation/starter/model/route/RouteTemplateContract.java`
   - 현재 `?`를 모두 reject한다.
   - 새 규칙은 `?...` bounded omission marker를 safe template 안에서만 허용해야 한다.
   - `/...` collapse marker를 normalized route로 허용해야 한다.
5. `observability-spring-boot-starter/src/main/java/com/observation/starter/model/route/NormalizedRoute.java`
   - 현재 첫 `?` 뒤를 모두 strip한다.
   - 새 규칙에서는 `?...` marker가 safe template으로 들어온 경우 보존해야 한다.
6. `observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionProperties.java`
   - allowlist는 계속 path template only다.
   - allowlist에는 `?...` marker를 허용하지 않는다.
7. 현재 route 관련 테스트
   - `RouteNormalizationServiceTest`
   - `MicrometerHttpServerObservationBinderTest`
   - `LowCardinalityHttpObservationGuardTest`
   - `RouteAttributionPropertiesTest`

## Implementation Guidance

권장 구현 방향:

- `RouteNormalizationService`가 source precedence의 중심이 되게 한다.
- `MicrometerHttpServerObservationBinder`는 source 추출과 high-cardinality 차단만 담당하고, invalid present `http.route`가 raw fallback을 막는 결정을 하지 않는다.
- `LowCardinalityHttpObservationGuard`는 routePattern과 rawPathCandidate를 함께 `RouteNormalizationService`에 넘긴다.
- `RouteTemplateContract` 또는 별도 helper에 아래 검증을 모은다.
  - safe route template
  - `?...` marker template
  - `/...` collapse marker
  - raw path segment safety
  - percent encoding/control character guard
- prefix collapse helper는 원본 suffix를 반환하지 않는다.
- allowlist matcher는 기존 exact-one 원칙을 유지한다.
- raw query가 있었는지 별도 표시가 필요하면 route value object가 아니라 display/read model 전용 bounded signal로 둔다. Story 8.1에서 실제 display surface가 없으면 signal 구현은 만들지 않아도 된다.
- `HttpServerObservationInput` 같은 runtime input boundary가 routePattern의 `?...` marker를 실제 query string으로 오인해 자르지 않는지 확인한다.
- 새 public class/method/helper와 복잡한 내부 helper에는 AGENTS.md 기준 한국어 Javadoc/comment를 작성한다.

주의:

- `NormalizedRoute.of("/{userId}?.../posts")`는 marker를 보존해야 한다.
- `MicrometerHttpServerObservationBinder`에서 추출한 low-cardinality `http.route=/{userId}?.../posts`도 input boundary와 guard를 통과한 뒤 marker를 보존해야 한다.
- `NormalizedRoute.of("/orders/{orderId}?token=abc")`는 query key/value를 보존하면 안 된다.
- `RouteNormalizationService` 결과에서 `http.route=/orders/{orderId}?token=abc`는 `/orders/{orderId}`가 되어야 한다.
- `NormalizedRoute.of("/orders/...")` 또는 service 결과 `/orders/...`는 valid normalized route여야 한다.
- allowlist setter는 `/orders/{orderId}?debug=true`와 `/{userId}?.../posts`를 계속 reject해야 한다.

## Acceptance Criteria

1. `http.route` exact template은 normalized route로 그대로 사용한다.
2. `http.route` `?...` marker template은 bounded marker로 허용한다.
3. `http.route` 실제 query suffix는 strip하고 safe template만 normalized route로 사용한다.
4. `http.route`가 raw identifier segment를 포함하면 safe prefix collapse를 적용한다.
5. `http.route` malformed template marker는 prefix collapse 대상이 아니며 `UNKNOWN` 후 low-cardinality raw 후보 단계로 넘어갈 수 있다.
6. `http.route` absolute URL은 `http.route` 단계에서 실패하고 low-cardinality raw 후보 단계로 넘어갈 수 있다.
7. raw `uri`/`path` 후보는 allowlist exact-one match 성공 시 allowlist template을 반환한다.
8. raw `uri`와 `path`가 모두 있으면 `uri`가 우선한다.
9. raw `uri`/`path` 후보가 여러 allowlist와 match되면 `UNKNOWN`이다.
10. raw `uri`/`path` 후보는 allowlist 실패 시 safe prefix collapse를 적용한다.
11. raw query key/value는 normalized route, endpoint key, payload, read model, UI에 남지 않는다.
12. `http.url`과 high-cardinality tag는 raw 후보로 쓰지 않는다.
13. first segment가 unsafe이면 `UNKNOWN`이다.
14. endpoint key는 항상 `method + normalized route`다.
15. allowlist는 path template만 허용하며 query key/value와 `?...` marker를 reject한다.
16. route normalization 실패는 host request failure로 전파하지 않고 `UNKNOWN`으로 수렴한다.
17. `?...` marker는 단위 value object뿐 아니라 binder/input/guard runtime 경계에서도 end-to-end로 보존된다.

## Given / When / Then Acceptance Cases

### 1. `http.route` exact template 성공

Given low-cardinality `http.route=/orders/{orderId}`이고 `uri=/orders/33?debug=true`다.

When route normalization을 수행한다.

Then normalized route는 `/orders/{orderId}`다.

And endpoint key는 `GET /orders/{orderId}`다.

And `/orders/33`과 `debug=true`는 출력 모델에 남지 않는다.

### 2. `http.route` `?...` marker template 성공

Given low-cardinality `http.route=/{userId}?.../posts`다.

When route template contract를 검증한다.

Then normalized route는 `/{userId}?.../posts`다.

And endpoint key는 `GET /{userId}?.../posts`다.

And `?...`는 raw query value가 아니라 bounded omission marker로 해석한다.

### 2-1. `?...` marker runtime boundary end-to-end 보존

Given Micrometer low-cardinality `http.route=/{userId}?.../posts`다.

When binder가 `HttpServerObservationInput`을 만들고 guard가 route normalization을 수행한다.

Then input `routePattern`은 `/{userId}?.../posts`를 보존한다.

And guard 결과 normalized route는 `/{userId}?.../posts`다.

### 2-2. `http.route` 실제 query suffix strip 성공

Given low-cardinality `http.route=/orders/{orderId}?token=abc`다.

When route normalization을 수행한다.

Then normalized route는 `/orders/{orderId}`다.

And endpoint key는 `GET /orders/{orderId}`다.

And `token=abc`는 endpoint key, payload, read model, UI에 남지 않는다.

### 3. `http.route` raw segment prefix collapse 성공

Given low-cardinality `http.route=/posts/deadbeef/comments/9`다.

When safe template 검증이 실패한다.

Then service는 safe prefix collapse를 적용한다.

And normalized route는 `/posts/...`다.

And `deadbeef/comments/9`는 endpoint key, payload, read model, UI에 남지 않는다.

### 4. `http.route` absolute URL 실패 후 raw 후보 단계

Given low-cardinality `http.route=https://example.test/orders/{orderId}`다.

And low-cardinality `uri=/orders/33?debug=true`다.

And allowlist에 `/orders/{orderId}`가 있다.

When `http.route`가 absolute URL이라 실패한다.

Then raw 후보 allowlist exact-one match로 normalized route는 `/orders/{orderId}`다.

And absolute URL source는 normalized route로 저장되지 않는다.

### 5. `http.route` malformed 실패 후 raw 후보 단계

Given low-cardinality `http.route=/orders/{orderId`다.

And low-cardinality `uri=/orders/33`이다.

And allowlist에 `/orders/{orderId}`가 있다.

When malformed template marker 때문에 `http.route` 단계가 `UNKNOWN`으로 수렴한다.

Then raw 후보 allowlist exact-one match로 normalized route는 `/orders/{orderId}`다.

And malformed template marker는 prefix collapse 대상이 아니다.

### 6. raw `uri/path` allowlist exact-one match 성공

Given `http.route`가 없다.

And low-cardinality `uri=/posts/33/comments/9?debug=true`다.

And allowlist에 `/posts/{postId}/comments/{commentId}`가 있다.

When raw 후보 query를 폐기하고 allowlist match를 수행한다.

Then normalized route는 `/posts/{postId}/comments/{commentId}`다.

### 6-1. raw `uri`가 `path`보다 우선

Given `http.route`가 없다.

And low-cardinality `uri=/orders/33`이다.

And low-cardinality `path=/payments/99`이다.

And allowlist에 `/orders/{orderId}`와 `/payments/{paymentId}`가 있다.

When raw 후보 선택과 allowlist match를 수행한다.

Then normalized route는 `/orders/{orderId}`다.

And `path=/payments/99`는 route로 사용하지 않는다.

### 7. raw `uri/path` allowlist ambiguous -> `UNKNOWN`

Given `http.route`가 없다.

And low-cardinality `uri=/orders/33`이다.

And allowlist에 `/orders/{orderId}`와 `/orders/{id}`가 있다.

When allowlist match를 수행한다.

Then normalized route는 `UNKNOWN`이다.

### 8. raw `uri/path` prefix collapse 성공

Given `http.route`가 없다.

And low-cardinality `uri=/api/v1/orders/33/items/9?debug=true`다.

And allowlist match가 없다.

When raw 후보 prefix collapse를 수행한다.

Then normalized route는 `/api/v1/orders/...`다.

And `33/items/9`와 `debug=true`는 출력 모델에 남지 않는다.

### 9. raw query value 폐기

Given low-cardinality `uri=/orders/33?next=/payments/99&token=abc`다.

When route normalization을 수행한다.

Then allowlist가 있으면 `/orders/{orderId}`, allowlist가 없으면 `/orders/...` 또는 `UNKNOWN`만 가능하다.

And `/payments/99`, `token=abc`, `next` 값은 route로 되살리지 않는다.

### 10. `http.url`/high-cardinality tag 미사용

Given high-cardinality `http.url=https://example.test/orders/33?token=abc`만 있고 low-cardinality `uri`/`path`가 없다.

When route normalization을 수행한다.

Then normalized route는 `UNKNOWN`이다.

And `http.url`에서 path를 파싱하지 않는다.

### 11. first segment unsafe -> `UNKNOWN`

Given low-cardinality `uri=/33/posts`다.

And allowlist match가 없다.

When prefix collapse를 수행한다.

Then normalized route는 `UNKNOWN`이다.

And `/33/posts`는 endpoint key로 남지 않는다.

### 12. allowlist는 path template만 허용

Given allowlist 설정에 `/orders/{orderId}?debug=true` 또는 `/{userId}?.../posts`가 들어온다.

When `RouteAttributionProperties`가 binding/normalization을 수행한다.

Then 설정은 reject된다.

And allowlist fallback은 path template exact-one match에만 사용된다.

## Test Requirements

Focused test를 최소 아래 범위로 보강한다.

- `RouteNormalizationServiceTest`
  - `http.route` exact template 성공
  - `http.route` `?...` marker template 성공
  - `http.route=/orders/{orderId}?token=abc` 실제 query suffix strip 성공
  - `http.route` raw segment prefix collapse 성공
  - `http.route` absolute URL 실패 후 raw 후보 allowlist 성공
  - `http.route` malformed template marker는 prefix collapse하지 않고 `UNKNOWN` 후 raw 후보 allowlist 성공
  - raw allowlist exact-one 성공
  - raw `uri`와 `path`가 모두 있으면 `uri` 우선
  - raw allowlist ambiguous `UNKNOWN`
  - raw prefix collapse 성공
  - first segment unsafe `UNKNOWN`
  - raw query value 폐기
- `MicrometerHttpServerObservationBinderTest`
  - present invalid `http.route`가 있어도 low-cardinality `uri`/`path` raw 후보를 임시 경계에 보존한다.
  - low-cardinality `http.route=/{userId}?.../posts`가 `HttpServerObservationInput.routePattern`까지 잘리지 않고 보존된다.
  - low-cardinality `uri`와 `path`가 모두 있으면 raw 후보는 `uri`를 우선한다.
  - high-cardinality `http.url`/`uri`/`path`/`http.route`는 후보가 되지 않는다.
  - context custom value와 arbitrary tag는 route source가 되지 않는다.
- `LowCardinalityHttpObservationGuardTest`
  - `routePattern=/{userId}?.../posts`가 guard 통과 후 normalized route로 end-to-end 보존된다.
  - routePattern이 present but normalize result `UNKNOWN`일 때 raw 후보 fallback을 service에 넘긴다.
  - routePattern이 성공하면 raw 후보가 있어도 routePattern 결과를 우선한다.
  - normalization failure는 host request path에 예외를 전파하지 않는다.
- `RouteAttributionPropertiesTest`
  - allowlist는 path template만 허용한다.
  - allowlist는 query key/value와 `?...` marker를 reject한다.
- 기존 architecture/negative-path guard
  - starter request path가 network call을 하지 않는 guard를 약화하지 않는다.
  - arbitrary label/custom metric/raw explorer 경로를 열지 않는다.

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

## Story 8.1 Completion Definition

Story 8.1 구현 완료는 아래가 모두 만족될 때만 선언한다.

- `planning-artifacts/contracts/route-attribution-policy.md`의 source precedence와 구현이 일치한다.
- `http.route` success, `http.route` failure 후 raw fallback, raw allowlist fallback, raw prefix collapse가 모두 테스트로 고정된다.
- `?...`와 `/...` marker가 bounded marker로만 동작한다.
- `?...` marker는 `NormalizedRoute` 단위 검증뿐 아니라 binder/input/guard runtime 경계에서도 보존되는 테스트가 있다.
- `http.route` 실제 query suffix strip과 malformed template marker 후 raw fallback 기대값이 테스트로 고정된다.
- raw `uri`와 `path`가 모두 있을 때 `uri` 우선순위가 테스트로 고정된다.
- raw query value와 `http.url` source가 normalized route, endpoint key, payload, read model, UI에 남지 않는 테스트가 있다.
- allowlist가 path template only 원칙을 유지한다.
- 기존 starter non-blocking/request-path guard가 깨지지 않는다.
- Project seed 발급 UI나 public project creation surface를 열지 않는다.
