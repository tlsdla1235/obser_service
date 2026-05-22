---
artifactType: rewrite-index
architectureStyle: Traditional MVC
status: mvc-version-generated
date: 2026-05-09
---

# Architecture Rewrite Index - MVC Version

## 재산출 범위

PRD와 UX specification은 검증 완료 산출물로 보고 본문을 수정하지 않았다. 단, 2026-05-19 account signup/login 정책 정렬에서는 제품 명세의 사용자 인증 문구를 GitHub OAuth only 기준으로 보정했다.

이 폴더의 산출물은 기존 Lightweight Hexagonal 기준 산출물의 제품 의미와 계약을 유지하되, 구현 구조를 **Traditional MVC + Service/Repository Layering**으로 다시 정의한다.

## Generated Artifacts

| 새 산출물 | 근거가 된 기존 내용 | MVC 버전 역할 |
|---|---|---|
| `planning-artifacts/architecture.md` | 기존 `architecture.md`, PRD/UX 의도 | 단일 Traditional MVC 아키텍처 결정 |
| `planning-artifacts/contracts/ingest-envelope.md` | 기존 ingest contract | starter와 portal controller 사이 ingest source of truth |
| `planning-artifacts/contracts/time-buckets.md` | 기존 time bucket contract | service layer의 bucket/window/freshness 기준 |
| `planning-artifacts/contracts/state-semantics.md` | 기존 state semantics | dashboard service가 결정할 first-screen 상태 의미 |
| `planning-artifacts/contracts/read-model-contract.md` | 기존 read model contract | controller가 반환하고 UI가 그대로 표시할 read model |
| `planning-artifacts/contracts/dashboard-read-model.md` | 이전 파일명 호환 note | `read-model-contract.md`로 연결 |
| `planning-artifacts/contracts/metric-taxonomy.md` | 기존 metric taxonomy | starter/portal service validation 기준 |
| `planning-artifacts/contracts/starter-failure-semantics.md` | starter 장애 전파 정책 | portal 연결 실패가 host app build/startup/request path로 전파되지 않는 기준 |
| `planning-artifacts/contracts/account-auth-policy.md` | 2026-05-19 account auth 정책 정렬 | GitHub OAuth only signup/login, Bearer JWT/refresh token, provider token 비노출 기준 |
| `planning-artifacts/contracts/insight-rules.md` | 기존 insight rules | triage service rule 기준 |
| `planning-artifacts/contracts/histogram-merge.md` | 기존 histogram merge 파일명 호환 | bucket distribution merge와 endpoint bucket display 기준 |
| `planning-artifacts/epics.md` | 기존 epics | MVC 구현 순서 |
| `planning-artifacts/acceptance-traceability.md` | 기존 traceability | AC -> MVC layer -> test 추적 |
| `planning-artifacts/architecture-implementation-supplement.md` | 기존 implementation supplement | repo/module/package/service/repository/test 보조 설계 |
| `planning-artifacts/database-schema.md` | 기존 database schema | MVC repository가 사용할 PostgreSQL logical/physical schema |
| `planning-artifacts/api-surface.md` | 기존 API surface | MVC controller endpoint rough surface |
| `planning-artifacts/stories/1-4-portal-physical-schema-foundation.md` | 기존 Story 1.4 | MVC repository foundation 구현 story |

## 명시적 변환

- `adapter.in.web` -> `controller`
- `application use case` -> `service`
- `application.port.out` -> `repository` 또는 infrastructure client dependency
- `adapter.out.persistence` -> `repository`
- `adapter.out.security` -> `security` component
- `adapter.out.time` 또는 `ClockPort` -> `time` utility/component 또는 injectable clock bean
- `domain` -> `model` 및 service 내부 policy object
- `bootstrap` -> `config`

## 명시적 비계승

- 기존 Prometheus 중심 planning/contract 산출물은 active architecture decision으로 계승하지 않는다.
- Hexagonal port/adapter package 구조를 MVC 버전에 유지하지 않는다.
- 여러 아키텍처 장점을 섞은 hybrid 설명을 사용하지 않는다.

## 단일 선택

이번 MVC 재산출물의 단일 아키텍처 선택은 **Traditional MVC + Service/Repository Layering**이다.
