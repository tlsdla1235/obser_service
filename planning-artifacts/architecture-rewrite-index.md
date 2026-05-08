---
artifactType: rewrite-index
architectureStyle: Lightweight Hexagonal
status: party-mode-fixes-applied
date: 2026-05-08
---

# Architecture Rewrite Index

## 재산출 범위

PRD와 UX specification은 검증 완료 산출물로 보고 본문을 수정하지 않았다.

대신 현재 작성된 문서에서 아키텍처 결정 역할을 하던 내용을 Lightweight Hexagonal 기준으로 아래 산출물로 재분리했다.

## Generated Artifacts

| 새 산출물 | 근거가 된 기존 내용 | 역할 |
|---|---|---|
| `planning-artifacts/architecture.md` | `observability_toy_spec_v0.8.md` 9~17장, `micrometer-direct-ingest-pivot-prep.md` architecture rewrite note | 단일 Lightweight Hexagonal 아키텍처 결정 |
| `planning-artifacts/contracts/ingest-envelope.md` | spec 9.1~10.1, pivot prep contract note | starter와 portal 사이 ingest source of truth |
| `planning-artifacts/contracts/time-buckets.md` | spec 9.5~9.6 | bucket, window, freshness 기준 |
| `planning-artifacts/contracts/state-semantics.md` | spec 9.6, 10.4, UX state-first requirement | first-screen 상태 의미 |
| `planning-artifacts/contracts/read-model-contract.md` | UX first-screen contract, spec 10.2~10.4, party-mode validation | UI가 그대로 표시할 단일 read model source of truth |
| `planning-artifacts/contracts/dashboard-read-model.md` | 이전 파일명 호환 note | `read-model-contract.md`로 연결 |
| `planning-artifacts/contracts/metric-taxonomy.md` | spec 8~9, 16 risk controls | low-cardinality metric 허용 범위 |
| `planning-artifacts/contracts/insight-rules.md` | spec 10.4 | triage rule candidate, guard, ranking |
| `planning-artifacts/contracts/histogram-merge.md` | spec 9.3, party-mode validation | server-side p95 계산 계약과 golden fixture 기준 |
| `planning-artifacts/epics.md` | spec 13~14, pivot prep epics note | Lightweight Hexagonal 구현 순서 |
| `planning-artifacts/acceptance-traceability.md` | party-mode validation | Epic -> AC -> contract -> package -> test 추적 |

## 명시적 비계승

- 기존 Prometheus 중심 planning/contract 산출물은 active architecture decision으로 계승하지 않는다.
- MVC/layered service-repository를 최종 아키텍처 스타일로 삼지 않는다.
- 여러 아키텍처 장점을 섞은 hybrid 설명을 사용하지 않는다.

## 단일 선택

이번 재산출물의 단일 아키텍처 선택은 **Lightweight Hexagonal**이다.
