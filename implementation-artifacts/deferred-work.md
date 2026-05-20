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
