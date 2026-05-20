---
artifactType: deferred-work
date: 2026-05-20
---

# Deferred Work

## Deferred from: code review of story-3.2 (2026-05-20)

- Strict JSON scalar type hardening: decide whether supported ingest fields must reject Jackson scalar coercion by JSON token type.
- Endpoint/histogram collection caps: define concrete max endpoint count and accepted histogram boundary-set policy before persistence/read-model merge hardening.
- Duplicate endpoint key rejection: decide and enforce behavior for repeated `method + route` entries.
- `ValidatedIngestCandidate` construction guard: keep candidate creation service-owned or re-check validation invariants in the candidate model before downstream persistence expands.
- Idempotency project component binding: decide whether the first `Idempotency-Key` component must match verified portal project identity or remains starter-local opaque identity.
