---
artifactType: prompt
projectName: Spring Boot 운영 첫 화면 포털
storyId: "8.1"
storyKey: "8-1-route-normalization-contract-decisions"
date: 2026-06-01
purpose: New-context prompt for BMAD dev-story execution
---

# New Context Prompt - Story 8.1 BMAD Dev Story

아래 프롬프트를 새 컨텍스트에 그대로 붙여 넣는다.

```text
BMAD dev story로 Story 8.1 route normalization 규칙 변경을 구현해줘.

작업 디렉터리:
/Users/tlsdla1235/Desktop/study/observation

Story/spec file:
/Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/spec-story-8-1-route-normalization-contract-decisions.md

공식 source of truth:
/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/route-attribution-policy.md

목표:
starter route normalization 규칙을 새 source of truth에 맞게 바꿔줘. 구현은 사용자 설정을 늘리는 방향이 아니라 `http.route`가 제공하는 framework/custom template 후보를 최대한 살리고, 부정확한 후보는 allowlist exact-one match 또는 safe prefix collapse로만 bounded route에 귀속시키는 방향이어야 해. raw URL/query 값은 normalized route, endpoint key, ingest payload, read model, UI에 원문으로 남기면 안 돼.

주의:
Project seed 발급 UI, public project creation, project key issuance/rotation UI, onboarding UI는 이 story에서 구현하지 마. 배포 목표에 포함될 수 있어도 별도 story로 분리한다.

반드시 먼저 읽을 계약/문서:
1. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/route-attribution-policy.md
2. /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/spec-story-8-1-route-normalization-contract-decisions.md
3. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/2-2-route-normalization-and-low-cardinality-guard.md
4. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/metric-taxonomy.md
5. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/ingest-envelope.md
6. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/architecture.md
7. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/architecture-implementation-supplement.md
8. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/project-structure.md
9. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/epics.md
10. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/sprint-plan.md
11. /Users/tlsdla1235/Desktop/study/observation/_bmad/custom/project-context.md
12. /Users/tlsdla1235/Desktop/study/observation/AGENTS.md

반드시 확인할 현재 코드:
1. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/service/RouteNormalizationService.java
2. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinder.java
3. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/service/LowCardinalityHttpObservationGuard.java
4. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/HttpServerObservationInput.java
5. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/model/route/RouteTemplateContract.java
6. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/model/route/NormalizedRoute.java
7. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionProperties.java
8. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/config/RouteAttributionAutoConfiguration.java
9. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/EndpointKey.java
10. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/LowCardinalityHttpServerObservation.java
11. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/model/ingest/IngestEnvelope.java
12. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/service/IngestEnvelopeBuilderService.java
13. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricBucketRollupService.java
14. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/test/java/com/observation/starter/service/RouteNormalizationServiceTest.java
15. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/test/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinderTest.java
16. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/test/java/com/observation/starter/service/LowCardinalityHttpObservationGuardTest.java
17. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/test/java/com/observation/starter/config/RouteAttributionPropertiesTest.java
18. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/StarterObservationArchitectureTest.java
19. /Users/tlsdla1235/Desktop/study/observation/observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/NoPrometheusMvpPathTest.java

닫힌 계약은 다시 열지 마:
- route normalization 규칙은 /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/route-attribution-policy.md를 최우선으로 따른다.
- 기존 Story 2.2나 metric-taxonomy 요약이 "invalid present http.route는 raw fallback 금지"라고 말하면 Story 8.1에서는 새 route attribution source of truth를 우선한다.
- 사용자 설정을 늘리지 않는다.
- allowlist는 유지하되 `http.route`가 없는 환경에서 raw path를 declared template으로 귀속시키는 fallback으로만 둔다.
- allowlist에는 path template만 허용한다. query key/value와 `?...` marker를 allowlist에 넣지 않는다.
- `http.route`가 정확하면 framework/custom route template을 그대로 살린다.
- `http.route`가 부정확하면 raw 값을 저장하지 않고 safe prefix까지만 살린 뒤 `/...`로 접을 수 있다.
- raw URL/query 값은 normalized route, endpoint key, payload, read model, UI에 원문으로 남기지 않는다.
- 실제 URL의 첫 `?` 뒤 값은 어떤 경우에도 route로 되살리지 않는다.
- `?...`는 raw query 보존이 아니라 query/omitted 구간이 있었다는 bounded marker다.
- `/...`는 unsafe/untrusted suffix를 접었다는 marker다. 뒤에 원본 suffix를 붙이지 않는다.
- endpointKey는 항상 `method + normalized route`다.
- raw query value는 endpointKey에 포함하지 않는다.
- `http.url` 같은 전체 URL/high-cardinality 값은 raw 후보로 쓰지 않는다.
- allowlist 없이 raw `/posts/33/comments/9`를 `/posts/{id}/comments/{id}`로 임의 추론하지 않는다.
- Project seed 발급 UI, public project creation, project key issuance/rotation UI, onboarding UI는 만들지 않는다.
- raw route explorer, arbitrary query UI, annotation 기반 query dimension opt-in, custom metric/tag 확장은 만들지 않는다.
- starter request path에서 network call을 하지 않는 기존 non-blocking boundary를 약화하지 않는다.

최종 normalize source 우선순위:
1. `http.route`를 최우선 source로 사용한다.
2. `http.route`에서 safe template, `?...` marker template, safe prefix collapse 순서로 정규화를 시도한다.
3. `http.route` 처리 결과가 `UNKNOWN`이면 low-cardinality `uri`/`path` raw 후보를 본다.
4. raw 후보는 allowlist exact-one match를 먼저 시도한다.
5. allowlist 실패 시 raw 후보에도 safe prefix collapse를 적용한다.
6. safe prefix도 없으면 `UNKNOWN`.
7. `http.url` 같은 전체 URL/high-cardinality 값은 raw 후보로 쓰지 않는다.

권장 구현 방향:
- `RouteNormalizationService`가 source precedence의 중심이 되게 한다.
- `MicrometerHttpServerObservationBinder`는 source 추출과 high-cardinality 차단만 담당한다.
- present invalid `http.route`가 raw fallback을 막는 결정을 binder에서 하지 않는다.
- low-cardinality `uri`/`path` raw 후보는 임시 input boundary에 보관할 수 있지만, normalization decision은 `RouteNormalizationService`에서 한다.
- `LowCardinalityHttpObservationGuard`는 `routePattern`과 `rawPathCandidate`를 함께 `RouteNormalizationService`에 넘긴다. routePattern이 성공하면 service가 raw 후보를 무시하고, routePattern 결과가 `UNKNOWN`이면 raw 후보 fallback을 볼 수 있어야 한다.
- `RouteTemplateContract` 또는 별도 helper에 safe route template, `?...` marker template, `/...` collapse marker, raw path segment safety, percent/control-character guard를 모은다.
- `NormalizedRoute.of("/{userId}?.../posts")`는 marker를 보존해야 한다.
- `HttpServerObservationInput` 같은 runtime input boundary가 routePattern의 `?...` marker를 실제 query string으로 오인해 자르지 않는지 확인한다.
- `MicrometerHttpServerObservationBinder`에서 추출한 low-cardinality `http.route=/{userId}?.../posts`도 input boundary와 guard를 통과한 뒤 marker를 보존해야 한다.
- `NormalizedRoute.of("/orders/{orderId}?token=abc")`는 query key/value를 보존하면 안 된다.
- `http.route=/orders/{orderId}?token=abc`는 실제 query suffix를 strip하고 `/orders/{orderId}`로 normalize한다.
- service 결과 `/orders/...`, `/api/v1/orders/...`는 valid normalized route여야 한다.
- allowlist setter는 `/orders/{orderId}?debug=true`와 `/{userId}?.../posts`를 reject해야 한다.
- prefix collapse helper는 원본 suffix를 반환하지 않는다.
- `/orders/{orderId`처럼 malformed template marker 때문에 실패한 값은 prefix collapse 대상이 아니다. `http.route` 단계는 `UNKNOWN`으로 수렴한 뒤 low-cardinality `uri`/`path` raw 후보 fallback으로 넘어간다.
- low-cardinality `uri`와 `path`가 둘 다 있으면 raw 후보는 `uri`를 우선한다.
- raw query가 있었는지 별도 표시가 필요하면 route value object가 아니라 display/read model 전용 bounded signal로 둔다. Story 8.1에 실제 display surface가 없으면 signal 구현은 만들지 않아도 된다.
- 새 public class/method/helper와 이해하기 어려운 내부 helper에는 AGENTS.md 기준 한국어 Javadoc/comment를 작성한다.

구현해야 하는 핵심 동작:
- `http.route=/orders/{orderId}` -> `/orders/{orderId}`
- `http.route=/{userId}?.../posts` -> `/{userId}?.../posts`
- Micrometer low-cardinality `http.route=/{userId}?.../posts` -> input `routePattern=/{userId}?.../posts` -> guard 결과 `/{userId}?.../posts`
- `http.route=/orders/{orderId}?token=abc` -> `/orders/{orderId}`
- `http.route=/posts/deadbeef/comments/9` -> `/posts/...`
- `http.route=/api/v1/orders/33/items/9` -> `/api/v1/orders/...`
- `http.route=https://example.test/orders/{orderId}`, low-cardinality `uri=/orders/33`, allowlist `/orders/{orderId}` -> `/orders/{orderId}`
- `http.route=/orders/{orderId`, low-cardinality `uri=/orders/33`, allowlist `/orders/{orderId}` -> `/orders/{orderId}`. 단 malformed template marker는 prefix collapse하지 않는다.
- `http.route` 없음, raw `uri=/posts/33/comments/9?debug=true`, allowlist `/posts/{postId}/comments/{commentId}` -> `/posts/{postId}/comments/{commentId}`
- `http.route` 없음, raw `uri=/orders/33`, raw `path=/payments/99`, allowlist `/orders/{orderId}`, `/payments/{paymentId}` -> `/orders/{orderId}`
- `http.route` 없음, raw `uri=/orders/33`, allowlist `/orders/{orderId}`와 `/orders/{id}` -> `UNKNOWN`
- `http.route` 없음, raw `uri=/api/v1/orders/33/items/9?debug=true`, allowlist miss -> `/api/v1/orders/...`
- `http.route` 없음, raw `uri=/33/posts`, allowlist miss -> `UNKNOWN`
- low-cardinality `uri=/orders/33?next=/payments/99&token=abc`는 `/payments/99`, `token=abc`, `next` 값을 route로 되살리지 않는다.
- high-cardinality `http.url=https://example.test/orders/33?token=abc`만 있고 low-cardinality `uri`/`path`가 없으면 `UNKNOWN`.

Given/When/Then acceptance:
1. Given `http.route=/orders/{orderId}`와 `uri=/orders/33?debug=true`가 있다. When normalize를 수행한다. Then route는 `/orders/{orderId}`, endpoint key는 `GET /orders/{orderId}`다. And raw path/query는 출력에 남지 않는다.
2. Given `http.route=/{userId}?.../posts`가 있다. When template 검증을 수행한다. Then route는 `/{userId}?.../posts`다. And marker는 bounded omission marker다.
3. Given Micrometer low-cardinality `http.route=/{userId}?.../posts`가 있다. When binder가 input을 만들고 guard를 통과한다. Then input `routePattern`과 normalized route는 모두 `/{userId}?.../posts`다.
4. Given `http.route=/orders/{orderId}?token=abc`가 있다. When normalize를 수행한다. Then route는 `/orders/{orderId}`, endpoint key는 `GET /orders/{orderId}`다. And `token=abc`는 출력에 남지 않는다.
5. Given `http.route=/posts/deadbeef/comments/9`가 있다. When safe template 검증이 실패한다. Then route는 `/posts/...`다. And unsafe suffix는 출력에 남지 않는다.
6. Given `http.route=https://example.test/orders/{orderId}`, low-cardinality `uri=/orders/33`, allowlist `/orders/{orderId}`가 있다. When `http.route`가 실패한다. Then raw allowlist fallback은 `/orders/{orderId}`를 반환한다.
7. Given `http.route=/orders/{orderId`, low-cardinality `uri=/orders/33`, allowlist `/orders/{orderId}`가 있다. When malformed template marker 때문에 `http.route` 단계가 `UNKNOWN`으로 수렴한다. Then raw allowlist fallback은 `/orders/{orderId}`를 반환한다. And malformed template marker는 prefix collapse 대상이 아니다.
8. Given `http.route`가 없고 raw `uri=/posts/33/comments/9?debug=true`, allowlist `/posts/{postId}/comments/{commentId}`가 있다. When normalize를 수행한다. Then route는 `/posts/{postId}/comments/{commentId}`다.
9. Given `http.route`가 없고 raw `uri=/orders/33`, raw `path=/payments/99`, allowlist `/orders/{orderId}`, `/payments/{paymentId}`가 있다. When normalize를 수행한다. Then route는 `/orders/{orderId}`다.
10. Given `http.route`가 없고 raw `uri=/orders/33`, allowlist `/orders/{orderId}`, `/orders/{id}`가 있다. When normalize를 수행한다. Then route는 `UNKNOWN`이다.
11. Given `http.route`가 없고 raw `uri=/api/v1/orders/33/items/9?debug=true`가 있으며 allowlist match가 없다. When normalize를 수행한다. Then route는 `/api/v1/orders/...`다.
12. Given raw `uri=/orders/33?next=/payments/99&token=abc`가 있다. When normalize를 수행한다. Then query value는 폐기되고 route로 되살아나지 않는다.
13. Given high-cardinality `http.url`만 있다. When normalize를 수행한다. Then route는 `UNKNOWN`이다.
14. Given raw `uri=/33/posts`가 있다. When allowlist match가 없다. Then route는 `UNKNOWN`이다.
15. Given allowlist 값 `/orders/{orderId}?debug=true` 또는 `/{userId}?.../posts`가 있다. When properties binding을 수행한다. Then reject한다.

Test requirements:
- Add/update focused tests in `RouteNormalizationServiceTest` for all source precedence and prefix collapse cases, including `http.route` actual query suffix strip, malformed template marker raw fallback, and raw `uri` before `path` precedence.
- Add/update focused tests in `MicrometerHttpServerObservationBinderTest` to show invalid present `http.route` no longer blocks low-cardinality `uri`/`path` temporary raw candidate extraction.
- Add/update focused tests in `MicrometerHttpServerObservationBinderTest` to show low-cardinality `http.route=/{userId}?.../posts` is preserved in `HttpServerObservationInput.routePattern` and low-cardinality `uri` wins over `path` when both exist.
- Keep tests that prove `http.url`, high-cardinality tags, context custom values, arbitrary labels cannot become route sources.
- Add/update focused tests in `LowCardinalityHttpObservationGuardTest` to show `routePattern=/{userId}?.../posts` is preserved end-to-end, raw fallback is available when routePattern normalizes to `UNKNOWN`, while successful routePattern still wins.
- Add/update `RouteAttributionPropertiesTest` so allowlist remains path-template-only and rejects query or `?...` marker.
- Keep architecture/negative-path tests passing.

Story/spec file 수정 규칙:
- /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/spec-story-8-1-route-normalization-contract-decisions.md는 닫힌 계약 문서다. 구현 중 요구사항/AC/closed decisions를 마음대로 바꾸지 마.
- 별도 planning story file이 이미 추가되어 있다면 BMAD dev-story 규칙에 따라 Tasks/Subtasks checkboxes, Dev Agent Record, File List, Change Log, Status 영역만 수정해.
- 현재 sprint-status에 Story 8.1 key가 없다면 임의로 새 key를 만들거나 갱신하지 마. 별도 planning story와 sprint-status entry가 추가된 경우에만 해당 BMAD 규칙을 따른다.
- strict `bmad-dev-story` workflow가 체크박스/Dev Agent Record가 있는 planning story file을 요구하는 환경이라면, 이 파일을 planning story로 오인하지 말고 implementation prompt로 사용한다. 필요하면 별도 Story 8.1 planning story 생성을 먼저 권장하되, 이 prompt만으로 sprint-status나 planning story를 임의 생성하지 않는다.

구현 중 사용자 변경사항이나 기존 미추적 파일은 되돌리지 마.
현재 worktree에는 이미 Epic 7 prompt artifact나 runtime docs 같은 미추적 파일이 있을 수 있다. 무관한 변경은 건드리지 말고, 충돌이 있으면 먼저 읽고 맞춰서 작업해.

완료 전 검증:
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.service.RouteNormalizationServiceTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.spring.observation.MicrometerHttpServerObservationBinderTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.service.LowCardinalityHttpObservationGuardTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.config.RouteAttributionPropertiesTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.architecture.StarterObservationArchitectureTest
./gradlew :observability-spring-boot-starter:test --tests com.observation.starter.architecture.NoPrometheusMvpPathTest
./gradlew :observability-spring-boot-starter:test
git diff --check

테스트 일부가 아직 존재하지 않아 패턴 매칭 실패가 나면, 필요한 focused test를 추가한 뒤 다시 실행해.

완료 보고에는 다음만 짧게 정리해줘:
- Story 8.1 구현 상태와 sprint-status 변경 여부
- 구현/테스트/문서 파일 목록
- `http.route` success, `http.route` failure 후 raw fallback, raw allowlist fallback, raw prefix collapse가 어떻게 동작하는지
- `?...`와 `/...` marker가 raw value 보존이 아니라 bounded marker임을 어떻게 검증했는지, 그리고 `?...` marker가 binder/input/guard 경계에서 보존됨을 어떻게 검증했는지
- `http.route` 실제 query suffix strip, malformed template marker raw fallback, raw `uri` 우선순위를 어떻게 검증했는지
- raw query value, `http.url`, high-cardinality tag 미사용이 어떻게 검증됐는지
- allowlist path-template-only 원칙이 어떻게 유지됐는지
- 통과한 검증 명령
- 못 돌린 검증이 있으면 이유
```
