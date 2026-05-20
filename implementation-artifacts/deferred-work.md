---
artifactType: deferred-work
date: 2026-05-20
---

# Deferred Work

## Deferred from: code review of story-3.2 (2026-05-20)

- Low / Residual - Portal JSON boundary accepts Jackson scalar coercion for same-version payloads: 현재 공식 starter가 ingest envelope의 유일한 producer이고 `schemaVersion` mismatch는 reject 테스트로 커버되어 있으므로 Story 3.2 acceptance blocker로 보지 않는다. 다만 `schemaVersion: "1.0"`을 유지한 채 일부 scalar field 타입이 계약과 다르게 들어오는 malformed payload는 Jackson 기본 coercion으로 DTO에 정상값처럼 들어올 수 있다. 외부 collector/third-party SDK를 허용하거나 portal ingest endpoint를 더 엄격한 trust boundary로 취급하는 시점에는 strict JSON type handling을 재검토한다.
- Endpoint/histogram collection caps: define concrete max endpoint count and accepted histogram boundary-set policy before persistence/read-model merge hardening.
- Duplicate endpoint key rejection: decide and enforce behavior for repeated `method + route` entries.
- `ValidatedIngestCandidate` construction guard: keep candidate creation service-owned or re-check validation invariants in the candidate model before downstream persistence expands.
- Idempotency project component binding: decide whether the first `Idempotency-Key` component must match verified portal project identity or remains starter-local opaque identity.

## Deferred from: MVP accepted bucket scope decision (2026-05-20)

Source specification: `planning-artifacts/mvp-deferred-risk-spec.md`

- Full idempotent replay success: MVP는 같은 `(project_id, idempotency_key)`가 이미 있으면 same payload 여부와 관계없이 reject한다. 저장 성공 후 response loss가 발생한 starter retry가 `200 OK duplicate=true`로 수렴하지 않는 risk는 full idempotency story로 넘긴다.
- Payload hash based conflict classification: MVP는 duplicate key reject만 보장하고, same key/same payload와 same key/different payload를 `payload_hash`로 구분하지 않는다. conflict diagnosis는 full idempotency 재도입 시 구현한다.
- Insert race convergence: pre-read와 insert unique violation catch 후 re-read로 duplicate/conflict에 수렴시키는 동작은 MVP 밖이다.
- Catalog get-or-create race hardening: 같은 application/instance 최초 ingest가 동시에 들어올 때 catalog unique violation이 발생할 수 있는 risk를 residual로 둔다.
- Persistence-layer bucket window hardening: UTC 30초 boundary와 정확한 30초 interval 검증은 MVP에서 `IngestAcceptanceService` 책임으로 두고, DB/JPA/command deep check는 후속 hardening으로 넘긴다.
- Cross-FK hierarchy enforcement: accepted bucket row의 project/application/instance 계층 일관성은 repository catalog path가 보장하며, DB-level composite FK/check hardening은 future import/admin tooling 전에 재검토한다.
- Histogram boundary set cross-check: summary와 endpoint histogram boundary set 일치 검증은 dashboard p95/read model merge 구현 전에 닫는다.
- Endpoint cardinality and duplicate endpoint key guard: starter bounded top-N/allow-set 책임을 MVP 전제로 두고, portal-side endpoint cap, duplicate `method + route`, endpoint sum consistency 검증은 read model merge 전에 재검토한다.
- Idempotency key length and payload hash strict format validation: MVP는 DB column/constraint를 저장 경계로 두며 API error polish나 third-party producer 허용 전 service-level validation으로 올린다.
- Accepted bucket index/test hardening: 중복 가능 index 정리, check constraint/index column-order 회귀 테스트 보강은 query plan tuning 또는 retention cleanup 성능 테스트 전까지 deferred로 둔다.
