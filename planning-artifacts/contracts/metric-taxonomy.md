---
artifactType: contract
name: metric-taxonomy
architectureStyle: Lightweight Hexagonal
status: party-mode-fixes-applied
date: 2026-05-08
---

# Contract - Metric Taxonomy

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

## 4. Hexagonal Boundary

metric 허용 여부는 starter domain과 portal application validation에 같이 반영한다.

persistence schema가 허용한다고 해서 application contract 밖의 metric을 저장하지 않는다.
