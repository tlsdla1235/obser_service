---
artifactType: contract
name: metric-taxonomy
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# Contract - Metric Taxonomy MVC Version

## 1. 허용 Metric

MVP는 first-screen triage에 필요한 metric만 허용한다.

### App-Level

- request count
- error count
- HTTP server duration histogram bucket
- CPU usage ratio
- heap used ratio
- datasource pool usage ratio

### Endpoint-Level

- method
- normalized route
- request count
- error count
- duration histogram bucket

## 2. Tail Latency Evidence Boundary

Tail latency evidence는 별도 starter metric 추가 없이 HTTP server duration cumulative histogram bucket에서 portal service layer가 계산한다.

p99를 위해 raw percentile, per-request sample, trace id, arbitrary latency distribution payload를 추가하지 않는다. p99는 `histogram-merge.md`와 `operational-event-history.md`의 auxiliary evidence 정책을 따르며 starter ingest payload 확장 근거가 아니다.

## 3. Tag Policy

허용 식별자는 아래로 제한한다.

- application
- environment
- instance
- method
- normalized route

raw path, user id, tenant id, session id, trace id, arbitrary label은 MVP ingest payload에 넣지 않는다.

## 4. Route Attribution Policy

MVP route attribution precedence는 아래 순서다.

1. framework가 제공한 `http.route` 또는 route template
2. `http.route`가 없을 때만 raw path candidate를 query 폐기 후 configured allowlist matcher에 적용
3. 정확히 하나의 allowlist template이 매칭되면 해당 template
4. 그 외 모든 경우 `UNKNOWN`

query string은 정규화하지 않는다. 이는 query key/value를 route, tag, metric key, payload, 로그, rollup key, read model로 해석하거나 보존하지 않는다는 뜻이다. `?` 이후를 버려 path 후보만 남기는 것은 query 정규화가 아니라 query 폐기이며, 이 path 후보는 configured allowlist matching의 일시 입력으로만 사용할 수 있다.

MVP route allowlist는 starter configuration으로 선언하며 namespace는 `observation.route-attribution.allowlist`다. Allowlist 항목은 `/orders/{orderId}` 같은 route template이며 query string, absolute URL, 실제 사용자/주문/세션 식별자 값을 포함할 수 없다. Annotation 기반 endpoint 표시명, route/display masking, query dimension opt-in은 post-MVP 후보이며 MVP attribution guard를 우회할 수 없다.

Allowlist 작성 규칙, concrete identifier heuristic, ambiguous match 처리, invalid `http.route` fallback 금지는 [route-attribution-policy.md](route-attribution-policy.md)를 따른다.

## 5. Extension Policy

새 metric은 아래 조건을 모두 만족할 때만 추가한다.

- first-screen state, triage, endpoint priority 중 하나를 직접 개선한다.
- low-cardinality 조건을 만족한다.
- dashboard read model 또는 insight rule contract에 소비 지점이 있다.
- starter와 portal 양쪽에 bounded validation을 추가할 수 있다.

### Post-MVP Annotation Candidates

MVP에서는 annotation 기반 query dimension, route masking, metric rename, custom tag를 열지 않는다.
다만 아래 후보는 core ingest path가 안정화된 뒤 별도 story로 검토할 수 있다.

- query parameter opt-in: 사용자가 명시한 query parameter만 bounded dimension으로 허용한다. query string 전체나 검색어, user id, token류 값은 route/tag가 될 수 없다.
- route/display masking: 특정 API를 원본 route 대신 고정된 normalized route 또는 display name으로 집계한다. masking은 민감 데이터를 수집하지 않기 위한 집계 이름 정책이지 payload 확장 경로가 아니다.
- annotation metadata validation: starter에서 annotation metadata를 읽더라도 portal ingest validation이 같은 allowlist와 cardinality 정책을 다시 검증한다.

이 후보들은 `method + normalized route`라는 MVP endpoint key 계약을 깨지 않는 방식으로만 추가할 수 있다.

## 6. MVC Boundary

metric 허용 여부는 starter service와 portal `IngestAcceptanceService` validation에 같이 반영한다.

persistence schema가 허용한다고 해서 service contract 밖의 metric을 저장하지 않는다.
