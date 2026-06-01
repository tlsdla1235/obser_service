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

## 2. Percentile and Histogram Boundary

`summary.localPercentiles.p95Ms`와 `summary.localPercentiles.p99Ms`는 starter가 ingest envelope로 보내는 canonical p95/p99 값이다. 필드명은 호환성 때문에 `localPercentiles`로 유지하지만, 의미는 약한 local hint가 아니라 `starter-reported percentile`이다.

HTTP server duration cumulative histogram bucket은 계속 수집한다. 다만 이 bucket은 canonical p95/p99 계산 입력이 아니라 distribution visualization, endpoint bucket display, diagnostic raw bucket의 source다.

p95/p99를 위해 per-request sample, trace id, arbitrary latency distribution payload를 추가하지 않는다. 같은 scope에서 starter-reported p95/p99와 histogram-derived p95/p99를 병렬 표시하지 않는다.

## 3. Tag Policy

허용 식별자는 아래로 제한한다.

- application
- environment
- instance
- method
- normalized route

raw path, user id, tenant id, session id, trace id, arbitrary label은 MVP ingest payload에 넣지 않는다.

Portal ingest request에 `tags` 같은 unknown field가 포함되더라도 forward compatibility를 위해 JSON boundary에서 무시한다. 무시된 tag/custom field는 metric taxonomy를 확장하지 않고, aggregation key, route attribution, read model, persisted accepted metric 후보에 반영하지 않는다.

## 4. Route Attribution Policy

Route normalization의 source of truth는 [route-attribution-policy.md](route-attribution-policy.md)다. 다른 요약이 이 문서와 충돌하면 route attribution policy를 우선한다.

현재 route attribution 요약은 아래와 같다.

1. low-cardinality `http.route`를 최우선 source로 사용하고, safe template, `?...` bounded omission marker, 실제 query suffix strip, safe prefix collapse를 순서대로 시도한다.
2. `http.route` 처리 결과가 `UNKNOWN`이면 low-cardinality `uri`/`path` raw 후보를 볼 수 있다.
3. raw 후보는 첫 `?` 뒤 query key/value를 버린 뒤 allowlist exact-one match를 먼저 시도하고, 실패하면 safe prefix collapse를 시도한다.
4. normalized route는 route attribution policy가 허용한 safe route template, safe prefix collapse 결과, allowlist template, `UNKNOWN` 중 하나다.
5. `http.url`, high-cardinality `http.route`/`uri`/`path`, context custom value, arbitrary tag map은 route 후보가 될 수 없다.

query string은 정규화하지 않는다. 이는 query key/value를 route, tag, metric key, payload, 로그, rollup key, read model로 해석하거나 보존하지 않는다는 뜻이다. `?...`는 route attribution policy가 허용한 bounded omission marker일 때만 normalized route 안에 남을 수 있으며, raw query 값 보존이 아니다.

MVP route allowlist는 starter configuration으로 선언하며 namespace는 `observation.route-attribution.allowlist`다. Allowlist 항목은 `/orders/{orderId}` 같은 route template이며 query string, absolute URL, 실제 사용자/주문/세션 식별자 값을 포함할 수 없다. Annotation 기반 endpoint 표시명, route/display masking, query dimension opt-in은 post-MVP 후보이며 MVP attribution guard를 우회할 수 없다.

final ingest payload에는 raw path, 실제 query key/value, high-cardinality tag, `http.url`에서 되살린 path가 남을 수 없다. Portal validation은 final payload route에 실제 query suffix, raw identifier, unsupported marker가 남아 있으면 reject한다.

Allowlist 작성 규칙, concrete identifier heuristic, ambiguous match 처리, invalid `http.route` 이후 raw 후보 fallback 허용 범위는 [route-attribution-policy.md](route-attribution-policy.md)를 따른다.

## 5. Extension Policy

새 metric은 아래 조건을 모두 만족할 때만 추가한다.

- first-screen state, triage, endpoint priority 중 하나를 직접 개선한다.
- low-cardinality 조건을 만족한다.
- dashboard read model 또는 insight rule contract에 소비 지점이 있다.
- starter와 portal 양쪽에 bounded validation을 추가할 수 있다.

### Post-MVP Runtime Aggregate Candidates

MVP의 JVM/datasource runtime ratio는 30초 bucket 안에서 관측된 latest valid sample만 payload와 저장소에 남긴다. Post-MVP에서는 순간 포화와 지속 압력을 구분하기 위해 아래 app-level aggregate를 별도 story로 추가할 수 있다.

- `latest`: bucket 안에서 가장 늦게 관측된 valid sample. 현재 상태에 가까운 UI 표시와 freshness 해석에 사용한다.
- `max`: bucket 안의 valid sample 중 최댓값. 짧은 DB pool, CPU, heap spike를 놓치지 않기 위한 saturation evidence로 사용한다.
- `avg`: bucket 안의 valid sample 산술 평균. 일시 spike가 아니라 지속적인 압력인지 판단하는 보조 evidence로 사용한다.
- `sampleCount`: `avg`와 multi-instance merge를 검증하기 위한 valid sample 수. 평균 병합은 단순 average-of-averages가 아니라 sampleCount 기반 weighted average를 사용한다.

대상 metric은 app-level `CPU usage ratio`, `heap used ratio`, `datasource pool usage ratio`로 제한한다. 이 확장은 raw timeseries, per-request sample, arbitrary metric map, high-cardinality tag ingestion을 허용하지 않는다. Starter는 bucket 안에서 bounded aggregate만 계산해 보내고, portal은 같은 ratio range와 aggregate 일관성(`0 <= avg <= max <= 1`, `latest <= max`, `sampleCount > 0`)을 다시 검증해야 한다.

`customMetrics`, `rawTimeseries`, 또는 future unsupported field가 unknown JSON field로 도착하면 portal은 이를 무시한다. 무시된 field는 metric 추가나 저장 근거가 아니며, 지원 envelope field가 유효한 request를 거부하지 않기 위한 lenient acceptance 정책일 뿐이다.

이 후보를 구현하려면 `ingest-envelope` schema version을 `1.0`과 분리하고, accepted bucket persistence, dashboard read model, saturation hint rule의 evidence shape를 함께 갱신한다.

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
