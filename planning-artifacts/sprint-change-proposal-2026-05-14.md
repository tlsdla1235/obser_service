---
artifactType: sprint-change-proposal
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: approved
date: 2026-05-14
changeTrigger: MVP route attribution policy A-to-B adjustment
---

# Sprint Change Proposal - Route Attribution B안 전환

## 1. Issue Summary

현재 MVP route attribution 정책은 `http.route` 또는 framework route template이 없으면 endpoint를 안전하게 식별하지 못하고 `GET UNKNOWN` 같은 단일 fallback으로 수렴한다. 이 정책은 privacy와 cardinality safety 측면에서는 안전하지만, `http.route`가 제공되지 않는 query-string 기반 API나 일부 framework/instrumentation 조합에서 endpoint-level triage 가치가 급격히 약해진다.

대표 예시는 아래와 같다.

- `/search?q=abc`가 framework route 없이 관측되면 `GET UNKNOWN`으로만 집계되어 search endpoint 지연/오류를 식별하기 어렵다.
- `/orders/123?debug=true`가 route template 없이 관측되면 `GET UNKNOWN`으로만 집계되어 `/orders/{orderId}` 수준 triage가 불가능하다.
- 여러 raw path가 모두 `UNKNOWN`으로 묶이면 Epic 5의 endpoint priority, insight rule, read model이 “where to look first” 약속을 충분히 지원하지 못할 수 있다.

변경 방향은 기존 A안의 강한 차단 정책을 완화하되, query key/value와 raw path가 payload, rollup key, read model, 로그, metric tag에 남지 않도록 안전 경계를 유지하는 B안이다.

## 2. Impact Analysis

### Checklist Status

| ID | Status | Findings |
|---|---|---|
| 1.1 | [x] Done | 트리거는 Epic 2 Story 2.1/2.2 구현 후 확인된 route attribution 정책 조정이다. |
| 1.2 | [x] Done | 문제 유형은 MVP triage 가치와 privacy/cardinality 사이의 정책 재조정이다. |
| 1.3 | [x] Done | `/search?q=abc`, `/orders/123?debug=true` 사례가 구체적 근거다. |
| 2.1 | [x] Done | Epic 2는 계속 완료 가능하지만 Story 2.1/2.2 완료 의미와 Story 2.3 입력 계약 보정이 필요하다. |
| 2.2 | [x] Done | 새 Epic은 필요 없다. Epic 2 내 policy adjustment로 처리 가능하다. |
| 2.3 | [x] Done | Epic 3/5는 ingest/read model 계약 해석이 영향을 받는다. |
| 2.4 | [x] Done | 기존 Epic을 무효화하지 않는다. |
| 2.5 | [x] Done | Epic 순서 변경은 필요 없다. Story 2.3 착수 전 보정이 필요하다. |
| 3.1 | [!] Action-needed | 명시적 PRD 파일은 없으며 `bmad-restart-context-pack/observability_toy_spec_v0.8.md`를 제품 요구 원천으로 사용했다. MVP 목표 자체는 유지된다. |
| 3.2 | [x] Done | `architecture.md`, `architecture-implementation-supplement.md`, contract 문서에 route attribution fallback 정책을 반영해야 한다. |
| 3.3 | [x] Done | UX는 endpoint priority/slow-error endpoint 표시 가치가 높아지며, raw path 기반 목록 금지는 유지된다. |
| 3.4 | [x] Done | 테스트, sprint plan, implementation readiness review, acceptance traceability 보정이 필요하다. |
| 4.1 | [x] Viable | Direct Adjustment가 가능하다. 노력 Medium, 위험 Medium. |
| 4.2 | [x] Not viable | Story 2.1/2.2를 rollback하기보다 정책 보정과 테스트 갱신이 낫다. |
| 4.3 | [x] Not viable | MVP scope 축소가 아니라 attribution 품질 보강이다. |
| 4.4 | [x] Done | 권장 경로는 Direct Adjustment다. |
| 5.1-5.5 | [x] Done | 이 문서의 각 섹션에 반영했다. |
| 6.1-6.5 | [!] Action-needed | 사용자 승인 후 실제 문서/코드 변경과 sprint-status 갱신 여부를 결정한다. |

### Epic Impact

Epic 2. Starter Direct Ingest Producer:

- Story 2.1은 “raw path를 route candidate로 승격하지 않는다”에서 “raw path를 payload 후보로 확정하지 않되, `http.route` 부재 시 allowlist matching 전용 임시 후보로 넘길 수 있다”로 의미를 조정해야 한다.
- Story 2.2는 route normalization final policy를 B안으로 재정의해야 한다.
- Story 2.3은 여전히 normalized route만 입력받아야 한다. 다만 normalized route의 source가 `framework_template` 또는 `allowlist_path_match`일 수 있다.
- Story 2.5는 ingest envelope에 source/raw path/query를 직렬화하지 않는 guard를 추가로 명시해야 한다.
- Story 2.6은 raw path/query/high-cardinality tag가 active source/build/resource에서 payload, log, metric tag, read model로 새지 않는 회귀 테스트를 포함해야 한다.

Epic 3. Portal Ingest Acceptance:

- `IngestAcceptanceService`는 여전히 normalized route만 수용한다.
- portal은 route attribution fallback에 쓰인 raw path/query를 알 수 없어야 한다.
- route 값이 `UNKNOWN`이거나 configured allowlist template이어도 validation은 `metric-taxonomy`와 `ingest-envelope` 기준으로 bounded해야 한다.

Epic 5. Triage Summary and Endpoint Priority:

- endpoint priority의 triage 가치는 좋아질 수 있다.
- read model은 endpoint가 raw path에서 유래했는지 자체를 노출하기보다, attribution quality/source 또는 unavailable 표현을 추가할지 결정해야 한다.

### Story Impact

Story 2.1 - Micrometer Observation Binding:

- 현재 구현과 테스트는 `http.route`만 `routePattern`으로 넘긴다.
- B안에서는 `HttpServerObservationInput`에 raw path candidate를 별도 optional field로 추가할 수 있다.
- binder는 `uri` 또는 `path` 같은 low-cardinality key에서만 raw path candidate를 읽고, `http.url` 또는 high-cardinality tag는 후보로 사용하지 않는다.
- absolute URL, query-only value, blank, malformed value는 후보로 넘기지 않거나 guard에서 `UNKNOWN` 처리해야 한다.

Story 2.2 - Route Normalization and Low-Cardinality Guard:

- 현재 `LowCardinalityHttpObservationGuard`는 `routeNormalizationService.normalize(input.routePattern(), Optional.empty())`로 raw path candidate를 넘기지 않는다.
- B안에서는 `routePattern`이 없을 때만 `rawPathCandidate`를 `RouteNormalizationService`로 전달한다.
- `RouteNormalizationService`는 query string 폐기 후 allowlist와 매칭하고, 반환값은 allowlist template 또는 `UNKNOWN`이어야 한다.
- allowlist miss, ambiguous match, invalid path, absolute URL, decoding failure는 모두 `UNKNOWN`이어야 한다.
- C안의 allowlist 없는 자동 raw path 추론과 별도 custom fallback은 MVP에서는 비활성 또는 제거해야 한다.

Story 2.3 - Bucket Rollup Service:

- rollup input은 계속 `LowCardinalityHttpServerObservation` 또는 동등 모델만 받는다.
- rollup key는 `method + normalized route`만 사용한다.
- `rawPathCandidate`, query, attribution source raw detail은 rollup model에 포함하지 않는다.

### Contract and Planning Artifact Impact

`planning-artifacts/contracts/metric-taxonomy.md`:

- Tag Policy에 B안 정의를 추가한다.
- query string을 “정규화하지 않는다”는 원칙을 “query key/value를 route/tag/key/payload/log/read model로 해석하거나 보존하지 않는다. `?` 이후 폐기는 query 폐기이며 allowlist matching 전 임시 path 후보 생성에만 허용된다”로 재정의한다.
- configured allowlist template은 normalized route로 허용한다.
- raw path는 저장/전송/집계 금지임을 유지한다.

`planning-artifacts/contracts/ingest-envelope.md`:

- payload에는 route attribution source나 raw path/query가 들어가지 않는다는 validation rule을 추가한다.
- endpoint `route`는 `http.route` template 또는 allowlist template 또는 `UNKNOWN`만 허용한다고 명시한다.
- query key/value, raw path candidate, high-cardinality tag는 payload shape에 존재하지 않는다고 명시한다.

`planning-artifacts/contracts/read-model-contract.md`:

- endpoint item에 `attribution` 또는 `routeAttribution`을 추가할지 결정해야 한다.
- 권장안은 raw detail 없는 bounded enum만 추가하는 것이다.
- 예: `routeAttribution.source = "framework_route" | "allowlist_path_match" | "unavailable"`, `routeAttribution.label = "framework route" | "configured allowlist" | "unavailable"`.
- `UNKNOWN` route는 raw 후보 부재/불일치/위험 후보 때문에 attribution unavailable임을 UI가 정직하게 표현할 수 있게 한다.

`planning-artifacts/contracts/insight-rules.md`:

- endpoint-level rule confidence는 `UNKNOWN` endpoint에 낮은 actionability를 부여하거나 candidate 생성을 제한할 수 있다.
- allowlist template으로 attribution된 endpoint는 framework route보다 confidence를 낮추지 않아도 되지만, source를 evidence detail에 넣을지 여부는 read model contract와 맞춰야 한다.

`planning-artifacts/architecture.md`:

- Starter data flow의 route normalization 단계에 B안 precedence를 추가한다.
- Traditional MVC + Service/Repository Layering 선택은 유지한다.
- Hexagonal/port/adapter 구조로 되돌리지 않는다.

`planning-artifacts/architecture-implementation-supplement.md`:

- starter route allowlist 설정 위치를 `config`/auto-configuration 영역으로 추가한다.
- “starter route allowlist가 없으면 framework normalized route와 endpoint 출력 cap만 사용한다” 문구를 “allowlist가 없고 framework route도 없으면 `UNKNOWN`”으로 더 엄격히 정리한다.

`planning-artifacts/sprint-plan.md`, `planning-artifacts/implementation-readiness-review-epic-2.md`, `planning-artifacts/acceptance-traceability.md`:

- Epic 2 low-cardinality guard acceptance를 B안으로 갱신한다.
- Story 2.3 착수 전 Story 2.1/2.2 문서와 테스트가 정책 보정을 반영해야 한다.

### Current Code Impact

`MicrometerHttpServerObservationBinder`:

- 현재 `routePattern(context)`은 `http.route`만 사용한다.
- B안 구현 시 `rawPathCandidate(context)`를 추가한다.
- 후보 source는 `uri` 또는 `path` 등 framework가 low-cardinality로 제공하는 path-like key로 제한한다.
- `http.url`, high-cardinality `userId`, arbitrary tag는 계속 무시한다.
- query는 binder에서 제거하거나 guard/service에서 즉시 제거하되, 어떤 출력 모델에도 query가 남지 않게 한다.

`HttpServerObservationInput`:

- `Optional<String> rawPathCandidate` 필드를 추가할 수 있다.
- 이 필드는 long-lived payload 후보가 아니라 guard 내부 경계로만 전달되는 임시 입력임을 문서화해야 한다.
- record component 이름은 `path`보다 `rawPathCandidate`처럼 사용 맥락을 드러내는 편이 낫다.

`LowCardinalityHttpObservationGuard`:

- `routePattern`이 존재하면 raw path candidate를 무시한다.
- `routePattern`이 없을 때만 `routeNormalizationService.normalize(Optional.empty(), input.rawPathCandidate())`를 호출한다.
- guard output에는 `NormalizedRoute`와 optional bounded attribution source만 남긴다.

`RouteNormalizationService`:

- 이미 allowlist matching과 query strip 구현이 있으나 ambiguous match 처리가 없다.
- B안에서는 allowlist match가 정확히 하나일 때만 해당 template을 반환한다.
- 0개 또는 2개 이상 match는 `UNKNOWN`으로 수렴한다.
- absolute URL, invalid path, decoding failure는 `UNKNOWN`으로 수렴한다.
- C안의 allowlist 없는 자동 raw path 추론은 제거하거나 framework route template에만 제한해야 한다. raw path candidate에는 적용하지 않는다.

관련 테스트:

- `MicrometerHttpServerObservationBinderTest`의 “uri raw path를 route candidate로 승격하지 않는다” 기대를 B안에 맞게 수정한다.
- `LowCardinalityHttpObservationGuardTest`에 routePattern 우선순위, raw path allowlist exact-one match, raw path query 폐기, miss/ambiguous/absolute URL `UNKNOWN` 테스트를 추가한다.
- `RouteNormalizationServiceTest`에 ambiguous allowlist, invalid path, decoding failure, allowlist 없는 자동 raw path 추론 부재 테스트를 추가한다.
- Story 2.3/2.5 구현 시 normalized route only rollup/envelope 테스트에 query/raw path 부재 assertion을 포함한다.

## 3. Recommended Approach

권장 접근은 B안 채택이다.

B안은 `http.route`가 있을 때는 기존처럼 framework template을 최우선으로 사용하고, 없을 때만 raw path 후보를 configured allowlist matcher의 일시 입력으로 허용한다. allowlist match가 성공하면 반환값은 raw path가 아니라 allowlist template이다. miss, ambiguous match, invalid path, absolute URL, decoding failure는 모두 `UNKNOWN`으로 수렴한다.

MVP allowlist 선언 방식은 starter configuration으로 고정한다. Spring Boot properties 또는 YAML의 전용 namespace `observation.route-attribution.allowlist`에 route template 목록을 두고, starter는 이 목록만 allowlist matcher 입력으로 사용한다. Annotation 기반 endpoint 표시명, route masking, query dimension opt-in은 post-MVP developer convenience 후보로만 둔다. Annotation을 나중에 도입하더라도 동일한 allowlist/cardinality validation을 통과해야 하며, MVP에서 query/raw path/high-cardinality 정책을 우회하는 경로로 사용하지 않는다.

C안은 폐기한다. allowlist 없이 단일 segment raw path를 자동 route로 인정하는 방식은 `/health`, `/search` 같은 일부 endpoint를 편하게 살릴 수 있지만, MVP에서 정책 예외가 커지고 사용자 기대가 “어디까지 자동 attribution되는가”로 흔들린다. B안만으로도 안전하게 triage 가치를 회복할 수 있다.

D안은 임시 UX 표현 보강으로만 사용할 수 있다. 예를 들어 `UNKNOWN` endpoint에 대해 “route unavailable” 또는 “configured allowlist 없음” 같은 표현을 read model/UI에서 보여줄 수 있다. 단 D안은 attribution 자체를 해결하지 않으며 raw path/query를 보여주는 방향으로 확장하면 안 된다.

아키텍처는 기존 선택을 유지한다.

- Traditional MVC + Service/Repository Layering 유지
- starter는 `spring` → `service` → `model` 경계 유지
- portal은 feature-first MVC 유지
- Hexagonal/port/adapter 구조로 되돌리지 않음
- `application`, `port`, `adapter` package 재도입 금지

Effort estimate: Medium

Risk level: Medium

Timeline impact:

- Story 2.3 착수 전 Story 2.1/2.2 문서와 테스트를 보정해야 한다.
- 코드 변경은 starter route attribution boundary에 집중된다.
- Epic 3/5 구현 전 contract 보정을 끝내면 후속 비용은 낮다.

## 4. Detailed Change Proposals

### Story 2.1 Acceptance Criteria

OLD:

```markdown
5. raw path parameter와 arbitrary tag를 payload 후보로 확정하지 않는다. 최종 route/tag guard는 Story 2.2로 위임한다.
```

PROPOSED:

```markdown
5. raw path parameter와 arbitrary tag를 payload 후보로 확정하지 않는다. 단 `http.route`가 없을 때 configured allowlist matching에만 사용할 임시 `rawPathCandidate`는 starter 내부 input boundary로 전달할 수 있다. query string은 즉시 폐기되어야 하며, query key/value와 high-cardinality tag는 어떤 후보에도 포함하지 않는다.
```

Rationale:

Story 2.1이 route attribution fallback을 위해 필요한 최소 입력을 전달하되, payload/rollup/read model 후보로 확정하지 않는 경계를 명확히 한다.

### Story 2.1 Developer Guardrails

OLD:

```markdown
- route normalization 최종 결정을 임의로 우회하지 않는다.
```

PROPOSED:

```markdown
- route normalization 최종 결정을 임의로 우회하지 않는다.
- `http.route`는 framework route template 후보로 유지하고, `uri`/`path` 같은 raw path 후보는 `http.route` 부재 시 allowlist matching 전용 임시 입력으로만 넘긴다.
- `http.url`, query key/value, high-cardinality tag, arbitrary tag는 raw path 후보로 승격하지 않는다.
- binder/input/test 로그에 query string 또는 raw path candidate 값을 남기지 않는다.
```

### Story 2.2 Acceptance Criteria

OLD:

```markdown
2. framework route template이 있으면 이를 우선 사용한다.
3. route template이 없고 allowlist match도 없으면 raw path를 payload 후보로 사용하지 않는다.
4. query string은 route와 tag에서 제거된다.
```

PROPOSED:

```markdown
2. framework route template이 있으면 이를 항상 최우선으로 사용하고 raw path candidate는 무시한다.
3. framework route template이 없을 때만 raw path candidate를 configured allowlist matcher의 일시 입력으로 사용할 수 있다.
4. raw path candidate의 query string은 matcher 입력 전에 폐기한다. query key/value는 route, tag, metric key, payload, 로그, rollup key, read model에 남기지 않는다.
5. allowlist에 정확히 하나의 template이 매칭되는 경우에만 해당 allowlist template을 normalized route로 사용한다.
6. allowlist miss, ambiguous match, invalid path, absolute URL, decoding failure는 모두 `UNKNOWN`으로 수렴한다.
```

Rationale:

B안의 안전 조건을 acceptance로 고정한다.

### Story 2.2 Developer Guardrails

OLD:

```markdown
- raw path를 "임시로" endpoint key에 넣지 않는다.
- query parameter opt-in이나 route/display masking annotation을 MVP guard 우회 경로로 추가하지 않는다.
```

PROPOSED:

```markdown
- raw path를 endpoint key, payload, rollup key, metric tag, read model, 로그에 넣지 않는다.
- raw path candidate는 `http.route` 부재 시 configured allowlist matching의 일시 입력으로만 사용한다.
- query string은 정규화하지 않고 폐기한다. `?` 이후 key/value는 어떤 산출물에도 남기지 않는다.
- allowlist 없는 자동 raw path 추론은 MVP에서 구현하지 않는다.
- query parameter opt-in이나 route/display masking annotation은 MVP guard 우회 경로로 추가하지 않는다.
```

### Story 2.3 Acceptance Criteria

OLD:

```markdown
7. rollup service는 raw path 또는 high-cardinality tag를 입력으로 받지 않는다.
```

PROPOSED:

```markdown
7. rollup service는 `method + normalized route`만 endpoint key로 사용하며 raw path, raw path candidate, query string, high-cardinality tag, attribution raw detail을 입력이나 key로 받지 않는다.
```

Rationale:

Story 2.3은 B안 fallback의 존재를 몰라도 되며 normalized route boundary만 신뢰해야 한다.

### Contract Changes

`metric-taxonomy.md` proposed addition:

```markdown
## Route Attribution Policy

MVP route attribution precedence는 아래 순서다.

1. framework가 제공한 `http.route` 또는 route template
2. `http.route`가 없을 때만 raw path candidate를 query 폐기 후 configured allowlist matcher에 적용
3. 정확히 하나의 allowlist template이 매칭되면 해당 template
4. 그 외 모든 경우 `UNKNOWN`

query string은 정규화하지 않는다. 이는 query key/value를 route, tag, metric key, payload, 로그, 집계 단위로 해석하거나 보존하지 않는다는 뜻이다. `?` 이후를 버려 path 후보만 남기는 것은 query 정규화가 아니라 query 폐기이며, 이 path 후보는 configured allowlist matching의 일시 입력으로만 사용할 수 있다.

MVP route allowlist는 starter configuration으로 선언한다. Allowlist 항목은 `/orders/{orderId}` 같은 route template이며 query string, absolute URL, 실제 사용자/주문/세션 식별자 값을 포함할 수 없다. Annotation 기반 endpoint 표시명, route/display masking, query dimension opt-in은 post-MVP 후보이며 MVP attribution guard를 우회할 수 없다.
```

`ingest-envelope.md` proposed validation rule:

```markdown
- endpoint `route`는 framework route template, configured allowlist template, 또는 `UNKNOWN`이어야 한다.
- raw path candidate, query string, query key/value, high-cardinality tag, attribution source raw detail은 payload shape에 존재할 수 없다.
```

`read-model-contract.md` proposed bounded addition:

```json
"routeAttribution": {
  "source": "framework_route",
  "availability": "available"
}
```

Allowed source values:

| Source | Meaning |
|---|---|
| `framework_route` | framework route template에서 온 normalized route |
| `allowlist_path_match` | raw path candidate가 configured allowlist template에 정확히 매칭되어 생성된 normalized route |
| `unavailable` | safe route attribution을 얻지 못해 `UNKNOWN`으로 수렴 |

Recommendation:

- MVP read model에는 `source`와 `availability` 같은 bounded enum만 추가한다.
- raw path, allowlist miss reason detail, query key/value는 추가하지 않는다.
- UI copy는 `unavailable`일 때 “route attribution unavailable” 수준으로 표현하고 raw path를 보여주지 않는다.

`insight-rules.md` proposed addition:

```markdown
Endpoint-level rule은 `UNKNOWN` route의 actionability를 낮게 보거나 candidate 생성을 제한할 수 있다. `allowlist_path_match`는 configured policy를 통과한 normalized route이므로 endpoint evidence로 사용할 수 있으나, raw path/query detail을 evidence에 포함하지 않는다.
```

`architecture.md` proposed data-flow addition:

```markdown
starter service는 route normalization 시 `http.route`/framework route template을 최우선으로 사용한다. route template이 없을 때만 raw path candidate를 query 폐기 후 configured allowlist matcher의 일시 입력으로 사용한다. 반환값은 framework template, allowlist template, 또는 `UNKNOWN`뿐이며 raw path/query/high-cardinality tag는 ingest envelope와 rollup key에 남지 않는다.
```

### Read Model Decision

권장 결정:

- read model에 `routeAttribution` bounded enum을 추가한다.
- payload/envelope에는 추가하지 않는다.
- portal 저장소에는 raw path나 query를 저장하지 않는다.
- accepted bucket에서 attribution source가 필요하다면 endpoint item의 bounded metadata로만 검토한다. MVP에서 source propagation이 과하다고 판단되면 read model은 `route: "UNKNOWN"`만으로 unavailable을 표현하고, UI copy는 `UNKNOWN`을 “route unavailable”로 렌더링한다.

판단:

- PM/UX 관점에서는 endpoint priority가 왜 `UNKNOWN`인지 설명할 최소 source가 유용하다.
- Privacy/cardinality 관점에서는 raw detail 없는 enum이면 안전하다.
- 구현 부담을 줄이려면 1차 구현에서는 `UNKNOWN` 표현만 보강하고, Epic 5에서 `routeAttribution` enum 추가 여부를 결정해도 된다.

### Test Change List

Starter unit tests:

- `MicrometerHttpServerObservationBinderTest`
  - `http.route`가 있으면 `routePattern`만 채워지고 raw path candidate는 무시되는지 검증
  - `http.route`가 없고 `uri=/orders/123?debug=true`가 있으면 raw path candidate가 query 없이 guard boundary로 전달되는지 검증
  - `http.url`, high-cardinality `userId`, arbitrary tag는 raw path candidate가 되지 않는지 검증

- `HttpServerObservationInputTest` 또는 기존 binder/guard test
  - `rawPathCandidate`는 optional이며 query key/value를 보관하지 않는지 검증
  - record에 arbitrary tag map이 없는지 검증

- `LowCardinalityHttpObservationGuardTest`
  - framework route template 우선순위
  - raw path candidate + allowlist `/search` → `GET /search`
  - raw path candidate `/orders/123?debug=true` + allowlist `/orders/{orderId}` → `GET /orders/{orderId}`
  - allowlist miss → `GET UNKNOWN`
  - ambiguous allowlist match → `GET UNKNOWN`
  - absolute URL / invalid path / decoding failure → `GET UNKNOWN`

- `RouteNormalizationServiceTest`
  - query 폐기 후 allowlist match
  - query key/value가 반환 route에 남지 않음
  - allowlist 없는 자동 raw path 추론 없음
  - allowlist match는 정확히 하나일 때만 성공

Rollup/envelope tests:

- Story 2.3 normalized route only input test에 raw path/query field 부재 assertion 추가
- Story 2.5 JSON golden fixture에 raw path/query/high-cardinality tag 부재 assertion 추가
- Story 2.6 negative path guard에 arbitrary query UI와 high-cardinality custom tag ingestion 부재뿐 아니라 raw path/query serialization path 부재 검사를 추가

## 5. Implementation Handoff

### Scope Classification

Change scope: Moderate

이 변경은 MVP 목표와 아키텍처 스타일을 바꾸지 않지만, 완료/리뷰 상태인 Story 2.1/2.2의 acceptance와 테스트 의미를 조정한다. Story 2.3 이후 구현 전 backlog/document 보정이 필요하므로 Developer 단독 코드 변경 전에 PO/DEV 승인 흐름이 적합하다.

### Handoff Recipients

Product Owner / Developer:

- Story 2.1/2.2/2.3 acceptance와 guardrail 보정 승인
- sprint status에서 Story 2.2 review 상태를 policy-adjustment-needed로 볼지 결정
- Story 2.3 착수 전 문서 보정 완료 확인

Developer:

- starter route attribution config allowlist 추가
- binder/input/guard 내부 경계 변경
- route normalization allowlist fallback 연결
- 회귀 테스트 추가

Architect:

- Traditional MVC + Service/Repository Layering 유지 확인
- Hexagonal/port/adapter 회귀 없음 확인
- contract/read model에 attribution source enum 추가 여부 최종 판단

### Implementation Order

1. 문서/AC 보정
   - Story 2.1/2.2/2.3 AC와 Developer Guardrails 갱신
   - `metric-taxonomy.md`, `ingest-envelope.md`, `read-model-contract.md`, `insight-rules.md`, `architecture.md`, `architecture-implementation-supplement.md` 갱신

2. starter route attribution config allowlist 추가
   - starter config에 route allowlist property 추가
   - 전용 namespace: `observation.route-attribution.allowlist`
   - allowlist 항목은 route template만 허용하고 query string, absolute URL, 실제 ID 값은 거부
   - default는 empty allowlist
   - allowlist empty + `http.route` absent는 `UNKNOWN`
   - annotation 기반 allowlist/표시명 등록은 MVP에서 구현하지 않고 post-MVP 후보로 유지

3. raw path candidate를 binder/input/guard 내부 경계로만 전달
   - `MicrometerHttpServerObservationBinder`에서 `http.route`와 raw path candidate를 분리
   - `HttpServerObservationInput`에 optional `rawPathCandidate` 추가
   - query string은 즉시 폐기하거나 guard 직전 폐기하되 query key/value 저장 금지

4. `RouteNormalizationService` allowlist fallback 연결
   - guard가 `routePattern` 부재 시에만 raw path candidate 전달
   - allowlist exact-one match만 normalized route로 반환
   - miss/ambiguous/invalid/absolute URL/decoding failure는 `UNKNOWN`
   - C안 자동 raw path 추론 제거

5. rollup/envelope에는 normalized route만 유지
   - Story 2.3 rollup model은 raw path candidate를 알 수 없게 유지
   - Story 2.5 envelope builder는 normalized route만 serialize
   - payload, idempotency key, endpoint key, read model key에 query/raw path 없음 확인

6. 회귀 테스트 추가
   - binder/guard/normalization unit tests
   - rollup/envelope negative serialization tests
   - Story 2.6 negative path guard 확장

### Success Criteria

- query 값은 어떤 산출물에도 남지 않는다.
- query key/value는 route, tag, metric key, payload, 로그, rollup key, read model에 남지 않는다.
- `http.route` 또는 framework route template이 있으면 항상 최우선으로 사용한다.
- `http.route`가 없을 때만 raw path candidate를 allowlist matcher의 일시 입력으로 사용한다.
- allowlist match가 정확히 하나일 때만 fallback attribution이 가능하다.
- allowlist miss, ambiguous match, invalid path, absolute URL, decoding failure는 `UNKNOWN`으로 수렴한다.
- C안 자동 raw path 추론은 구현하지 않는다.
- rollup/envelope/read model에는 normalized route 또는 bounded attribution enum만 남는다.
- endpoint priority/read model은 attribution source 또는 unavailable 상태를 정직하게 표현한다.
- Traditional MVC + Service/Repository Layering을 유지하고 Hexagonal/port/adapter 구조로 돌아가지 않는다.

### Resolved Decisions and Follow-up Questions

1. Resolved: allowlist configuration namespace는 `observation.route-attribution.allowlist`로 확정한다.
2. Follow-up: `read-model-contract.md`에 `routeAttribution` bounded enum을 Epic 5 전에 미리 추가할지, 아니면 `UNKNOWN` UX 표현만 보강하고 Epic 5에서 결정할지 판단한다.
3. Follow-up: Story 2.2 status를 review 유지 + policy adjustment follow-up으로 둘지, 다시 in-progress로 되돌릴지 판단한다.

## 6. Final Recommendation

B안으로 진행한다. 이 변경은 MVP scope를 넓히기보다, 이미 약속한 first-screen triage 가치가 `http.route` 부재 환경에서도 최소한 작동하도록 route attribution 안전 경계를 조정하는 것이다.

구현은 곧바로 코드 변경으로 들어가지 말고, 이 제안서 승인 후 문서/AC/계약 보정을 먼저 적용한다. 그 다음 Story 2.3 착수 전에 Story 2.1/2.2의 완료 의미와 테스트를 B안 기준으로 다시 맞춘다.
