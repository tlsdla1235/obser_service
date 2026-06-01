---
artifactType: contract
name: route-attribution-policy
architectureStyle: Traditional MVC
status: story-8-1-source-of-truth
date: 2026-06-01
---

# Contract - Route Attribution Policy

## 1. 목적

Route attribution은 HTTP endpoint를 `method + normalized route`로 집계하기 위한 starter 경계다.

이 경계는 raw path, query string, user id, tenant id, session id, trace id, token, redirect URL 같은 high-cardinality 값을 endpoint key, metric tag, ingest payload, read model, UI로 넘기지 않는다. 안전한 route를 결정하지 못하면 원본 path 대신 `UNKNOWN`을 사용한다.

이 문서는 route normalization 규칙의 source of truth다. 다른 요약 문서가 이 정책과 충돌하면 이 문서를 우선한다.

## 2. 용어

| 용어 | 의미 |
| --- | --- |
| `normalized route` | endpoint key와 ingest payload에 남길 수 있는 bounded route 문자열이다. safe route template, safe prefix collapse 결과, allowlist template, `UNKNOWN` 중 하나다. |
| `endpointKey` | 항상 `method + " " + normalized route`로 만든다. raw query value는 포함하지 않는다. |
| `http.route` source | framework 또는 custom instrumentation이 low-cardinality tag로 제공한 route 후보다. 가장 먼저 본다. |
| raw 후보 | `http.route` 처리 결과가 없거나 `UNKNOWN`일 때만 보는 low-cardinality `uri` 또는 `path` 후보다. `http.url`은 raw 후보가 아니다. |
| allowlist | `http.route`가 없는 환경에서 raw path를 declared route template으로 귀속시키는 fallback 목록이다. |
| `?...` | query 또는 생략된 구간이 있었다는 bounded omission marker다. raw query 값을 보존한다는 뜻이 아니다. |
| `/...` | 여기서부터 unsafe/untrusted suffix를 접었다는 safe prefix collapse marker다. 뒤에 원본 suffix를 붙이지 않는다. |
| `queryStringPresent` | raw URL에 query가 있었다는 boolean/display-only 신호다. route 값 자체를 바꾸지 않는다. |

## 3. Runtime Precedence

Route normalization은 아래 순서로만 결정한다.

1. low-cardinality `http.route`를 최우선 source로 사용한다.
2. `http.route`에서 여러 단계 정규화를 시도한다.
3. `http.route` 처리 결과가 `UNKNOWN`이면 low-cardinality `uri`/`path` raw 후보를 본다.
4. raw 후보는 allowlist exact-one match를 먼저 시도한다.
5. allowlist 실패 시 raw 후보에도 safe prefix collapse를 적용한다.
6. safe prefix도 없으면 `UNKNOWN`을 사용한다.
7. `http.url` 같은 전체 URL 또는 high-cardinality 값은 raw 후보로 쓰지 않는다.

기존 Story 2.2 정책과 달리, present하지만 malformed 또는 unsafe한 `http.route`가 `UNKNOWN`으로 수렴한 뒤에는 raw 후보 fallback 단계를 볼 수 있다. 단, raw 후보는 low-cardinality `uri`/`path`에서만 오며, query 뒤 값이나 `http.url`에서 path를 되살리는 방식은 금지한다.

## 4. `http.route` Normalize Flow

`http.route`는 아래 순서로 처리한다.

1. `http.route`가 null, blank, `UNKNOWN`이면 `http.route` source 없음으로 보고 raw 후보 단계로 넘어간다.
2. `http.route`가 route-shaped 값인지 먼저 확인한다.
   - 허용 후보는 single slash로 시작하는 route-like 값이다.
   - absolute URL, slash 없는 값, `//` 시작, control character, invalid percent encoding은 `http.route` 단계에서 실패로 본다.
3. safe route template 검증을 시도한다.
   - 예: `/orders/{orderId}` -> `/orders/{orderId}`
   - 예: `/search` -> `/search`
4. `?...` omission marker가 포함된 safe template 검증을 시도한다.
   - 예: `/{userId}?.../posts` -> `/{userId}?.../posts`
   - `?...`는 query key/value가 아니라 custom instrumentation이 명시한 bounded omission marker일 때만 허용한다.
5. 실제 query key/value처럼 보이는 `?token=abc`, `?debug=true`, `?redirect=https://example.com/a`는 normalized route로 직접 허용하지 않는다.
   - `http.route=/orders/{orderId}?token=abc`처럼 safe template 뒤에 실제 query suffix가 붙은 경우 query suffix를 버리고 `/orders/{orderId}`만 normalized route로 사용한다.
   - `?...` marker만 canonical omission marker로 보존하며, bare `?` 또는 query key/value는 보존하지 않는다.
6. template 검증이 실패했지만 route-shaped prefix가 있으면 safe prefix collapse를 시도한다.
   - 예: `/posts/deadbeef/comments/9` -> `/posts/...`
   - 예: `/api/v1/orders/33/items/9` -> `/api/v1/orders/...`
   - 단, `/orders/{orderId`처럼 malformed template marker 때문에 실패한 값은 prefix collapse 대상이 아니다. 이 경우 `http.route` 단계는 `UNKNOWN`으로 수렴한 뒤 raw 후보 단계로 넘어간다.
7. prefix collapse도 실패하면 `http.route` 처리 결과는 `UNKNOWN`이다.
8. `http.route` 처리 결과가 `UNKNOWN`이면 low-cardinality `uri`/`path` raw 후보 단계로 넘어간다.

`http.route`에 실제 query key/value가 포함되어도 query key/value 자체는 저장하지 않는다. prefix나 template만 안전하게 확정할 수 있을 때만 bounded route로 줄이고, 확정할 수 없으면 `UNKNOWN` 또는 raw 후보 단계로 넘어간다.

## 5. Raw `uri`/`path` Candidate Flow

raw 후보는 아래 경우에만 본다.

- `http.route`가 없거나 blank/`UNKNOWN`이다.
- 새 규칙상 `http.route` 처리 결과가 `UNKNOWN`이다.

raw 후보 처리 순서:

1. low-cardinality `uri`를 우선 보고, 없으면 low-cardinality `path`를 본다.
   - 예: `uri=/orders/33`, `path=/payments/99`, allowlist `/orders/{orderId}`, `/payments/{paymentId}` -> `/orders/{orderId}`
2. 첫 `?` 뒤 query string은 폐기한다.
   - 예: `/posts/33/comments/9?debug=true` -> `/posts/33/comments/9`
3. query가 있었는지 표시해야 한다면 raw 값을 저장하지 말고 `queryStringPresent=true` 또는 display-only `?...` label만 사용한다.
4. raw 후보와 allowlist template이 정확히 하나 match되면 allowlist template을 normalized route로 사용한다.
   - 예: raw `/posts/33/comments/9`, allowlist `/posts/{postId}/comments/{commentId}` -> `/posts/{postId}/comments/{commentId}`
5. 여러 allowlist가 동시에 match되면 ambiguous라서 `UNKNOWN`이다.
6. allowlist가 없거나 match 실패하면 raw 후보 prefix collapse를 시도한다.
   - 예: `/api/v1/orders/33/items/9` -> `/api/v1/orders/...`
7. raw 후보 첫 segment부터 unsafe하면 `UNKNOWN`이다.
   - 예: `/33/posts` -> `UNKNOWN`
8. raw 후보에서도 query 뒤의 `/posts`, URL, path-like value는 route로 되살리지 않는다.

`http.url`, high-cardinality `uri`, high-cardinality `path`, high-cardinality `http.route`, context custom value, arbitrary tag map은 raw 후보가 될 수 없다.

## 6. Allowlist Configuration

Starter allowlist namespace는 아래로 고정한다.

```yaml
observation:
  route-attribution:
    allowlist:
      - /orders/{orderId}
      - /posts/{postId}/comments/{commentId}
      - /search
```

Allowlist 항목은 path template만 허용한다.

- 허용: `/orders/{orderId}`, `/api/v1/orders/{orderId}`, `/search`
- 거부: `/orders/12345`
- 거부: `/orders/{orderId}?debug=true`
- 거부: `/{userId}?.../posts`
- 거부: `https://example.test/orders/{orderId}`
- 거부: `/orders/`
- 거부: `/orders//{orderId}`

Allowlist 원리:

- allowlist는 path template만 선언한다.
- `{postId}` 같은 변수 segment는 한 path segment matcher로 동작한다.
- match 결과로 raw path를 반환하지 않고 allowlist template 자체를 반환한다.
- allowlist에 query key/value 또는 `?...` marker를 넣지 않는다.
- allowlist는 `http.route`가 성공하는 환경에서는 덜 필요하지만, `http.route`가 없는 환경의 정확한 fallback으로 유지한다.

Ambiguous match를 `UNKNOWN`으로 처리하는 이유는 같은 raw path가 여러 endpoint template으로 귀속될 수 있으면 rollup key가 비결정적이 되기 때문이다.

## 7. Safe Template Rules

safe route template은 아래 조건을 만족해야 한다.

- `/`로 시작한다.
- absolute URL이 아니다.
- control character와 invalid percent encoding을 포함하지 않는다.
- `//` double slash와 trailing slash를 포함하지 않는다.
- literal segment는 bounded slug다.
- variable segment는 `{orderId}`처럼 한 segment 전체를 차지한다.
- malformed template marker, wildcard, arbitrary regex는 허용하지 않으며 safe prefix collapse 대상도 아니다.
- 실제 identifier처럼 보이는 literal segment는 허용하지 않는다.
- `?...`는 custom route template의 bounded omission marker로만 허용한다.

Concrete identifier heuristic은 아래 segment를 unsafe로 본다.

- 숫자만 있는 segment: `12345`
- UUID shape: `550e8400-e29b-41d4-a716-446655440000`
- 8자 이상 long hex segment: `deadbeef`, `abcdef12`, `12345678`

Long hex 규칙은 "길기만 하면 거부"가 아니다. 8자 이상이면서 모든 문자가 hex 문자(`0-9`, `a-f`, 대소문자 무시)일 때 거부한다. 그래서 `customer-profile`, `recommendation-engine`, `hello123world`, `v1` 같은 bounded literal segment는 허용될 수 있다.

## 8. Safe Prefix Collapse Rules

safe prefix collapse는 template 검증 또는 allowlist match가 실패했을 때 마지막 방어선으로만 사용한다.

규칙:

- normalize가 깨지는 첫 segment부터 원본 suffix를 버리고 `/...`로 접는다.
- `/...` 뒤에 원본 path/query suffix를 붙이지 않는다.
- 안전한 prefix가 최소 1개 이상 있어야 한다.
- 첫 segment부터 숫자, UUID, long hex, malformed segment 등 unsafe면 `UNKNOWN`이 낫다.
- prefix collapse는 raw 값을 template 변수로 임의 추론하지 않는다.
- malformed template marker, wildcard, arbitrary regex 때문에 깨진 후보는 prefix collapse로 일부를 살리지 않고 `UNKNOWN`으로 수렴한다.

예:

| 후보 | 결과 | 이유 |
| --- | --- | --- |
| `/posts/deadbeef/comments/9` | `/posts/...` | `posts`는 safe literal이고 `deadbeef`부터 unsafe다. |
| `/api/v1/orders/33/items/9` | `/api/v1/orders/...` | `api/v1/orders`까지 safe prefix이고 `33`부터 unsafe다. |
| `/orders/550e8400-e29b-41d4-a716-446655440000` | `/orders/...` | UUID suffix를 보존하지 않는다. |
| `/orders/{orderId` | `UNKNOWN` | malformed template marker는 prefix collapse 대상이 아니다. |
| `/33/posts` | `UNKNOWN` | 첫 segment부터 unsafe라 safe prefix가 없다. |
| `/deadbeef/assets` | `UNKNOWN` | 첫 segment가 long hex라 safe prefix가 없다. |

## 9. Query and Omission Marker Rules

실제 raw URL에서 첫 `?` 뒤는 모두 query string으로 본다.

규칙:

- query 값은 저장하지 않는다.
- query key/value, token, redirect URL, search term, path-like query value는 normalized route에 포함하지 않는다.
- `http.route=/orders/{orderId}?token=abc`처럼 실제 query suffix가 붙은 route 후보는 query suffix를 strip하고 `/orders/{orderId}`만 사용한다.
- raw URL `/orders/33?next=/payments/99`는 `/orders/{orderId}` 또는 `/orders/...`까지만 가능하고, `/payments/99`를 route로 되살리지 않는다.
- `?...` marker는 safe normalized template 안에서만 허용되는 생략 marker다.
- `http.route = /{userId}?.../posts`처럼 application/custom instrumentation이 명시한 template이면 endpoint로 허용할 수 있다.
- raw URL에 query가 있었음을 표시해야 한다면 route 자체를 바꾸지 말고 `queryStringPresent` 또는 display-only label을 사용한다.
- bare `?`보다 `?...`를 canonical marker로 사용한다.

`?...`는 raw query 값을 보존한다는 뜻이 아니라 "query/omitted 구간이 있었다"는 bounded marker다.

## 10. Endpoint Key, Payload, Read Model, UI

endpoint key는 항상 `method + normalized route`다.

예:

- `http.route = /{userId}?.../posts`, method `GET` -> endpointKey `GET /{userId}?.../posts`
- raw `/orders/33?debug=true`, allowlist `/orders/{orderId}`, method `GET` -> endpointKey `GET /orders/{orderId}`
- raw `/api/v1/orders/33/items/9`, method `GET` -> endpointKey `GET /api/v1/orders/...`

raw URL에 query가 있었음을 표시만 해야 한다면 route 자체를 바꾸지 않는다.

- route: `/orders/{orderId}`
- display-only label: `/orders/{orderId}?...`
- optional signal: `queryStringPresent=true`

UI는 raw path, raw query, token, URL, redirect target, search term을 노출하지 않는다.

## 11. 금지 사례

아래 값은 normalized route, endpoint key, payload, read model, UI에 원문으로 남기지 않는다.

- `/orders/{orderId}?token=abc`
- `/orders/{orderId}?redirect=https://example.com/a`
- raw `/posts/deadbeef/comments/9`
- raw query value
- `http.url`에서 파싱한 path
- allowlist 없이 raw `/posts/33/comments/9`를 `/posts/{id}/comments/{id}`로 임의 추론한 값
- query value 안의 path-like 값: `/orders/33?next=/payments/99`의 `/payments/99`

## 12. Acceptance Examples

### `http.route` exact template 성공

Given `http.route=/orders/{orderId}`이고 raw `uri=/orders/33?debug=true`다.

When route normalization을 수행한다.

Then normalized route는 `/orders/{orderId}`이고 endpoint key는 `GET /orders/{orderId}`다.

And raw path `/orders/33`과 query value `debug=true`는 payload/read model/UI에 남지 않는다.

### `http.route` `?...` marker template 성공

Given `http.route=/{userId}?.../posts`다.

When safe template 검증을 수행한다.

Then normalized route는 `/{userId}?.../posts`이고 endpoint key는 `GET /{userId}?.../posts`다.

And `?...`는 query value가 아니라 bounded omission marker로만 해석한다.

### `http.route` 실제 query suffix strip 성공

Given `http.route=/orders/{orderId}?token=abc`다.

When route normalization을 수행한다.

Then normalized route는 `/orders/{orderId}`이고 endpoint key는 `GET /orders/{orderId}`다.

And `token=abc`는 payload/read model/UI에 남지 않는다.

### `http.route` raw segment prefix collapse 성공

Given `http.route=/posts/deadbeef/comments/9`다.

When safe template 검증이 실패하고 prefix collapse를 수행한다.

Then normalized route는 `/posts/...`다.

And `deadbeef/comments/9`는 어떤 출력에도 남지 않는다.

### `http.route` absolute URL 실패 후 raw 후보 단계

Given `http.route=https://example.test/orders/{orderId}`이고 low-cardinality `uri=/orders/33`이며 allowlist에 `/orders/{orderId}`가 있다.

When `http.route`가 absolute URL이라 실패한다.

Then raw 후보 allowlist exact-one match로 normalized route는 `/orders/{orderId}`다.

And `http.url` 또는 absolute URL source는 raw 후보로 쓰지 않는다.

### `http.route` malformed 실패 후 raw 후보 단계

Given `http.route=/orders/{orderId`이고 low-cardinality `uri=/orders/33`이며 allowlist에 `/orders/{orderId}`가 있다.

When malformed template marker 때문에 `http.route` 단계가 `UNKNOWN`으로 수렴한다.

Then raw 후보 allowlist exact-one match로 normalized route는 `/orders/{orderId}`다.

And malformed template marker는 prefix collapse 대상이 아니다.

### raw `uri/path` allowlist exact-one match 성공

Given `http.route`가 없고 raw `uri=/posts/33/comments/9?debug=true`다.

And allowlist에 `/posts/{postId}/comments/{commentId}`가 있다.

When raw 후보 query를 폐기하고 allowlist match를 수행한다.

Then normalized route는 `/posts/{postId}/comments/{commentId}`다.

### raw `uri`가 `path`보다 우선

Given `http.route`가 없고 low-cardinality `uri=/orders/33`이며 low-cardinality `path=/payments/99`다.

And allowlist에 `/orders/{orderId}`와 `/payments/{paymentId}`가 있다.

When raw 후보를 선택하고 allowlist match를 수행한다.

Then normalized route는 `/orders/{orderId}`다.

And `path=/payments/99`는 `uri`가 없을 때만 후보가 된다.

### raw `uri/path` allowlist ambiguous -> `UNKNOWN`

Given `http.route`가 없고 raw `uri=/orders/33`이다.

And allowlist에 `/orders/{orderId}`와 `/orders/{id}`가 있다.

When allowlist match를 수행한다.

Then normalized route는 `UNKNOWN`이다.

### raw `uri/path` prefix collapse 성공

Given `http.route`가 없고 raw `uri=/api/v1/orders/33/items/9?debug=true`다.

And allowlist match가 없다.

When raw 후보 prefix collapse를 수행한다.

Then normalized route는 `/api/v1/orders/...`다.

### raw query value 폐기

Given raw `uri=/orders/33?next=/payments/99&token=abc`다.

When route normalization을 수행한다.

Then normalized route는 allowlist가 있으면 `/orders/{orderId}`, 없으면 `/orders/...` 또는 `UNKNOWN`이다.

And `/payments/99`, `token=abc`, `next` 값은 route로 되살리지 않는다.

### `http.url`/high-cardinality tag 미사용

Given low-cardinality `uri`/`path`가 없고 high-cardinality `http.url=https://example.test/orders/33?token=abc`만 있다.

When route normalization을 수행한다.

Then normalized route는 `UNKNOWN`이다.

And `http.url`에서 path를 파싱하지 않는다.

### first segment unsafe -> `UNKNOWN`

Given raw `uri=/33/posts`다.

When allowlist match가 없고 prefix collapse를 수행한다.

Then normalized route는 `UNKNOWN`이다.

And `/33` 또는 `/33/posts`를 endpoint key로 남기지 않는다.

## 13. Implementation References

- `RouteAttributionProperties`: starter allowlist 설정을 binding하고 allowlist path template shape를 검증한다.
- `RouteTemplateContract`: safe template, `?...` omission marker, `/...` collapse marker shape를 검증한다.
- `RouteNormalizationService`: `http.route` multi-step normalization, raw 후보 allowlist exact-one match, safe prefix collapse, `UNKNOWN` 수렴 정책을 적용한다.
- `MicrometerHttpServerObservationBinder`: low-cardinality `http.route`, `uri`, `path` 후보만 추출하고 `http.url`/high-cardinality/custom value를 무시한다.
- `HttpServerObservationInput`: raw path/query를 장기 보관하지 않는 starter 내부 샘플 경계다.
- `LowCardinalityHttpObservationGuard`: route normalization 실패를 host request failure로 전파하지 않고 `UNKNOWN`으로 수렴시킨다.
- `EndpointKey`: method와 normalized route만 endpoint key로 사용한다.
- `NormalizedRoute`: query key/value와 unsafe route 값을 보관하지 않는 route value object다.
