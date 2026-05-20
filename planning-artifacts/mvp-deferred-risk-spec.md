---
artifactType: scope-spec
title: MVP Deferred Risk Specification
status: active
date: 2026-05-20
---

# MVP Deferred Risk Specification

## 1. Purpose

이 문서는 `accepted_metric_buckets` 구현 리뷰 이후 MVP에서 의도적으로 범위 밖으로 둔 hardening 항목과 residual risk를 정의한다.

MVP의 우선순위는 starter가 생성한 정상 `schemaVersion: 1.0` bucket을 portal이 검증하고 저장해 first-screen read model의 입력을 만들 수 있게 하는 것이다. starter와 portal service validation이 지키는 계약을 DB/JPA/repository 모든 경계에 중복 구현하는 일은 후속 hardening으로 분리한다.

이 문서는 기존 문서에 남아 있는 full idempotent duplicate success/conflict 요구를 후속 정리 전까지 다음 결정으로 보정한다.

## 2. MVP Scope Decision

MVP accepted bucket persistence는 아래를 포함한다.

- `POST /api/ingest/v1/buckets`의 project key 검증
- `schemaVersion: 1.0` request shape validation
- service layer의 UTC 30초 bucket boundary validation
- service layer의 metric taxonomy, count, ratio, histogram monotonicity validation
- `accepted_metric_buckets` 저장
- `(project_id, idempotency_key)` unique constraint로 같은 idempotency key의 두 번째 저장 방지
- `(application_instance_id, bucket_start_utc)` unique constraint로 같은 instance bucket start의 두 번째 저장 방지
- catalog application/application instance serial get-or-create path

MVP duplicate policy는 다음으로 고정한다.

- 이미 같은 `(project_id, idempotency_key)`가 존재하면 payload hash 비교 없이 duplicate key로 reject한다.
- 같은 key/same payload retry를 `200 OK duplicate=true`로 수렴시키는 동작은 MVP 밖이다.
- 같은 key/different payload를 payload hash 비교로 별도 `idempotency_conflict`로 분류하는 동작은 MVP 밖이다.
- 기존 row를 overwrite하거나 두 번째 row를 만들지 않는다.

## 3. Deferred Items

| ID | Deferred item | MVP behavior | Residual risk | Revisit trigger |
|---|---|---|---|---|
| D1 | Full idempotent replay success | 같은 `(project_id, idempotency_key)`가 있으면 same payload 여부와 관계없이 reject한다. | 저장 성공 후 response loss가 발생하면 starter retry가 success로 수렴하지 않고 duplicate failure로 보일 수 있다. | starter retry UX를 제품 품질 기준으로 보장해야 하거나 external producer를 허용하기 전 |
| D2 | Payload hash 기반 conflict 분류 | `payload_hash`는 저장할 수 있지만 duplicate 판정의 필수 입력으로 사용하지 않는다. | 같은 key/different payload 원인을 `same payload retry`와 구분해 진단하기 어렵다. | Story 3.4를 full idempotency story로 되살릴 때 |
| D3 | Insert race convergence | unique violation 후 re-read로 duplicate/conflict에 수렴시키지 않는다. | 동시 retry 또는 동시 최초 요청이 persistence error로 보일 수 있다. | ingest retry가 빈번한 운영 환경 또는 load/concurrency test 도입 시 |
| D4 | Catalog get-or-create race hardening | serial happy path를 우선한다. | 같은 application/instance의 첫 ingest가 동시에 들어오면 catalog unique violation이 bucket duplicate 처리보다 먼저 발생할 수 있다. | multi-instance starter rollout 또는 parallel ingest load test 전 |
| D5 | DB/JPA layer 30초 window deep check | service validation이 UTC 30초 boundary와 `[startUtc, endUtc)` 30초 interval을 검증한다. DB는 기본 positive window와 duration value만 둔다. | service를 우회한 내부 저장 경로나 future batch import가 invalid bucket interval을 넣을 수 있다. | repository를 ingest service 외 경로에서 재사용하거나 batch import를 만들 때 |
| D6 | Cross-FK hierarchy enforcement | repository catalog path가 일관된 project/application/instance id를 넣는다. DB는 각 FK 존재만 확인한다. | 직접 insert나 future import bug가 project/application/instance 계층을 섞은 row를 만들 수 있다. | import/admin tooling 또는 raw SQL maintenance path 추가 시 |
| D7 | Histogram boundary set cross-check | 각 histogram의 positive `leMs`, cumulative count, count upper bound를 service에서 검증한다. boundary set 전체 일치 검증은 보류한다. | starter bug로 summary와 endpoint boundary set이 달라지면 future p95 merge가 부정확해질 수 있다. | dashboard p95/read model merge 구현 전 |
| D8 | Endpoint cardinality and duplicate endpoint key guard | starter의 bounded top-N/allow-set 생성 책임을 신뢰한다. portal은 endpoint array shape와 각 endpoint metric만 검증한다. | 과도한 endpoint JSON, 동일 `method + route` 중복, endpoint 합계 초과가 저장될 수 있다. | read model merge 성능 기준을 닫거나 endpoint priority 계산을 구현하기 전 |
| D9 | Idempotency key length and hash format strict validation | DB column length와 unique constraint를 최종 저장 경계로 둔다. | 긴 idempotency key나 비표준 hash가 service validation이 아닌 persistence error로 드러날 수 있다. | API error polish 또는 third-party producer 허용 전 |
| D10 | Accepted bucket index/test hardening | 현재 migration index와 repository happy path tests를 유지한다. | 중복 index storage/write overhead, check/index column-order 회귀를 테스트가 놓칠 수 있다. | query plan tuning 또는 retention cleanup 성능 테스트 전 |

## 4. Non-Goals For MVP

- full exactly-once delivery guarantee
- same payload retry를 성공으로 돌려주는 full idempotency API
- payload hash 기반 semantic conflict diagnosis
- Redis/idempotency side table/outbox 도입
- DB trigger, stored procedure, materialized view 기반 validation
- arbitrary raw metric explorer 또는 JSONB 내부 ad-hoc query API

## 5. Follow-Up Specification Notes

Full idempotency를 재도입할 때는 아래 조건을 한 story 안에서 함께 닫는다.

- existing bucket lookup result에 `payload_hash`, `bucketId`, `acceptedAt`을 포함한다.
- service result status에 `DUPLICATE`와 `IDEMPOTENCY_CONFLICT`를 분리한다.
- pre-read와 insert unique violation catch 후 re-read를 모두 구현한다.
- same key/same hash는 기존 bucket receipt로 `200 OK duplicate=true`를 반환한다.
- same key/different hash는 overwrite 없이 `409 Conflict`를 반환한다.
- same instance/bucket start collision with different idempotency material도 conflict 계열로 분류한다.

기존 `api-surface.md`, `ingest-envelope.md`, `sprint-plan.md`, `stories/3-4-duplicate-handling.md`에 남아 있는 duplicate success/conflict 표현은 이 명세에 맞춰 후속 문서 정리 대상이다.
