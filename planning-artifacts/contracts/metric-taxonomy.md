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

## 2. Tag Policy

허용 식별자는 아래로 제한한다.

- application
- environment
- instance
- method
- normalized route

raw path, user id, tenant id, session id, trace id, arbitrary label은 MVP ingest payload에 넣지 않는다.

## 3. Extension Policy

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

## 4. MVC Boundary

metric 허용 여부는 starter service와 portal `IngestAcceptanceService` validation에 같이 반영한다.

persistence schema가 허용한다고 해서 service contract 밖의 metric을 저장하지 않는다.
