---
artifactType: contract
name: route-attribution-policy
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-14
---

# Contract - Route Attribution Policy

## 1. 목적

Route attribution은 HTTP endpoint를 `method + normalized route`로 집계하기 위한 starter 경계다.

이 경계는 raw path, query string, user id, tenant id, session id, trace id 같은 high-cardinality 값을 endpoint key, metric tag, ingest payload, read model로 넘기지 않는다. 안전한 route를 결정하지 못하면 원본 path 대신 `UNKNOWN`을 사용한다.

## 2. Runtime Precedence

Route normalization은 아래 순서로만 결정한다.

1. framework가 제공한 `http.route` 또는 route template이 있으면 최우선으로 사용한다.
2. `http.route`가 없을 때만 raw path candidate를 query 폐기 후 allowlist matcher의 임시 입력으로 사용한다.
3. raw path candidate가 configured allowlist template과 정확히 하나만 매칭되면 해당 template을 사용한다.
4. 그 외 모든 경우 `UNKNOWN`을 사용한다.

`http.route`가 present인 경우에는 raw path fallback을 적용하지 않는다. `http.route` 값이 absolute URL, slash 없는 path, invalid percent encoding 등 invalid shape이면 allowlist로 우회하지 않고 `UNKNOWN`으로 수렴한다.

`http.route`가 비어 있거나 명시적으로 `UNKNOWN`이면 후보 없음으로 취급할 수 있다. 이 경우에만 raw path candidate가 allowlist matching으로 넘어갈 수 있다.

## 3. Allowlist Configuration

Starter allowlist namespace는 아래로 고정한다.

```yaml
observation:
  route-attribution:
    allowlist:
      - /orders/{orderId}
      - /search
```

Allowlist 항목은 route template만 허용한다.

- 허용: `/orders/{orderId}`, `/api/v1/orders/{orderId}`, `/search`
- 거부: `/orders/12345`
- 거부: `/orders/{orderId}?debug=true`
- 거부: `https://example.test/orders/{orderId}`
- 거부: `/orders/`
- 거부: `/orders//{orderId}`

실제 요청 path를 allowlist에 넣으면 안 된다. 실제 요청 path는 `http.route`가 없을 때 일시적인 matching 입력으로만 사용되고, 최종 route 값으로 반환되지 않는다.

## 4. Concrete Identifier Heuristic

Allowlist literal segment는 실제 식별자처럼 보이면 거부한다.

현재 starter는 아래 segment를 concrete identifier로 본다.

- 숫자만 있는 segment: `12345`
- UUID shape: `550e8400-e29b-41d4-a716-446655440000`
- 8자 이상 long hex segment: `deadbeef`, `abcdef12`, `12345678`

Long hex 규칙은 "길기만 하면 거부"가 아니다. 8자 이상이면서 모든 문자가 hex 문자(`0-9`, `a-f`, 대소문자 무시)일 때 거부한다. 그래서 `customer-profile`, `recommendation-engine`, `hello123world` 같은 긴 literal segment는 허용될 수 있다.

다만 `/assets/deadbeef`처럼 실제로는 고정 literal인데 long hex처럼 보이는 segment는 오탐으로 거부될 수 있다. 그 segment가 진짜 ID라면 `/assets/{assetId}`처럼 template variable을 써야 한다. 반대로 프로젝트가 통제하는 고정 literal이라면 `/assets/dead-beef`, `/assets/deadbeef-marker`처럼 의미 있는 non-identifier slug를 사용한다.

Hyphen은 long hex heuristic을 깨는 데 도움이 될 수 있지만, UUID shape 자체는 hyphen이 있어도 여전히 identifier로 거부된다. 이 규칙은 real ID를 우회하기 위한 방법이 아니라, 고정 literal 이름이 우연히 long hex처럼 생겼을 때 이름을 더 명확하게 만드는 authoring guidance다.

## 5. Matching Semantics

Allowlist match는 정확히 하나의 template이 매칭될 때만 성공한다.

| 입력 상태 | allowlist | 결과 |
| --- | --- | --- |
| `http.route=/orders/{orderId}`, raw path `/orders/123` | 없음 | `/orders/{orderId}` |
| `http.route=https://example.test/orders/{orderId}`, raw path `/orders/123` | `/orders/{orderId}` | `UNKNOWN` |
| `http.route` 없음, raw path `/orders/123?debug=true` | `/orders/{orderId}` | `/orders/{orderId}` |
| `http.route` 없음, raw path `/orders/123` | `/orders/{orderId}`, `/orders/{id}` | `UNKNOWN` |
| `http.route` 없음, raw path `/sessions/abc` | `/orders/{orderId}` | `UNKNOWN` |
| `http.route` 없음, raw path `/search?q=abc` | `/search` | `/search` |

Ambiguous match를 `UNKNOWN`으로 처리하는 이유는 같은 raw path가 여러 endpoint template으로 귀속될 수 있으면 rollup key가 비결정적이 되기 때문이다.

## 6. Implementation References

- `RouteAttributionProperties`: starter allowlist 설정을 binding하고 route template shape를 검증한다.
- `RouteNormalizationService`: `http.route` 우선, allowlist exact-one fallback, `UNKNOWN` 수렴 정책을 적용한다.
- `LowCardinalityHttpObservationGuard`: raw path candidate를 다음 모델로 넘기지 않고 `LowCardinalityHttpServerObservation`에 normalized route만 남긴다.
- `NormalizedRoute`: query string과 unsafe route 값을 보관하지 않는 route value object다.
