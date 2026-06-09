---
artifactType: epic-source-of-truth
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: planning-source
date: 2026-06-01
sourcePolicy: latest-user-intent-wins
---

# Epic 9 - Product Onboarding and Project Seed Issuance UI

## Status

planning-source

## 목적

Epic 9는 기존 Project Entry와 dashboard 흐름 앞에 제품 onboarding 진입면을 추가하고, local/operator seed로만 가능했던 project 등록과 starter 연결용 credential 발급을 사용자-facing UI/API로 닫는 planning epic이다.

이 epic은 production onboarding product surface를 여는 방향으로 Story 6/7의 open decision을 정리하지만, smoke/admin seed와 public UI를 섞지 않는다. Story 9.1은 제품 소개와 기존 dashboard/project navigation 진입만 담당하고, project seed 발급은 Story 9.2에서만 다룬다.

## Source of Truth

구현 중 충돌처럼 보이는 지점은 아래 우선순위를 따른다.

1. 이 Epic 9 source-of-truth 문서
2. `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
3. `planning-artifacts/api-surface.md`
4. `planning-artifacts/contracts/account-auth-policy.md`
5. `planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md`
6. `planning-artifacts/stories/6-10-account-project-membership-and-scoped-project-entry.md`
7. `planning-artifacts/stories/7-1-real-github-oauth-smoke-seed-and-operator-runbook.md`
8. `implementation-artifacts/real-github-oauth-smoke-runbook.md`
9. `planning-artifacts/architecture.md`
10. `planning-artifacts/architecture-implementation-supplement.md`
11. `planning-artifacts/project-structure.md`
12. `_bmad/custom/project-context.md`
13. `AGENTS.md`

## Epic Goal

사용자는 첫 화면에서 이 제품이 무엇인지 이해하고, GitHub OAuth로 로그인한 뒤 project를 등록하고, starter에 넣을 1회 표시 credential을 발급받고, 기존 Project Entry -> Application List -> Application Dashboard 흐름으로 들어갈 수 있어야 한다.

## Product Flow

1. 사용자는 첫 화면에서 "Spring Boot 앱에 starter를 붙이고, project를 등록하고, dashboard에서 상태를 본다"는 흐름을 이해한다.
2. 사용자는 GitHub OAuth only account boundary를 통해 로그인한다.
3. 사용자는 기존 Project Entry에서 membership project를 보거나, project가 없으면 registration flow로 이동한다.
4. 사용자는 project를 등록하고 starter credential을 생성 시 1회만 확인한다.
5. 사용자는 credential을 starter 설정에 복사한 뒤 heartbeat/metric bucket ingest를 통해 project dashboard 흐름으로 들어간다.
6. 이후 사용자는 기존 Project -> Application List -> Application Dashboard -> Instance Evidence/History read model surface를 그대로 사용한다.

## Stories

### Story 9.1 - Product introduction and entry screen

목표:

- 사이트 첫 화면에서 이 프로젝트가 무엇인지 설명한다.
- 사용자가 starter 추가, project 등록, dashboard 상태 확인의 제품 흐름을 이해하게 한다.
- 기존 dashboard/project navigation으로 들어갈 수 있는 진입점을 제공한다.
- 단순 marketing landing이 아니라 실제 onboarding entry screen으로 설계한다.
- project seed 발급 자체는 포함하지 않는다.

Story artifact:

- `planning-artifacts/stories/9-1-product-introduction-and-entry-screen.md`

### Story 9.2 - Project registration and seed issuance UI

목표:

- 사용자가 project를 등록하고 starter 연결에 필요한 seed/service token 성격의 starter credential을 발급받는 UI/API를 설계한다.
- Credential 원문 저장 금지, 생성 시 1회 표시, 복사 UX, 재발급/폐기 흐름, 권한 확인, smoke/admin seed와 public UI 분리를 닫는다.
- 발급된 credential이 dashboard/read model/UI에 raw secret으로 새지 않도록 한다.
- 구현 story가 backend/API/UI/test 범위를 명확히 나눌 수 있게 한다.

Story artifact:

- `planning-artifacts/stories/9-2-project-registration-and-seed-issuance-ui.md`

## Closed Decisions

- Story 9.1은 project seed 발급, rotation, revocation, public `POST /api/projects` 구현을 포함하지 않는다.
- Story 9.2는 public onboarding product surface로 project registration과 starter credential lifecycle을 연다.
- Account signup/login은 계속 GitHub OAuth only다.
- Resource API 인증은 계속 `Authorization: Bearer <access_token>` service token 기준이다.
- Starter ingest 인증은 계속 `X-OBS-Project-Key` 계열 starter credential 기준이며, account service access/refresh token과 섞지 않는다.
- Starter credential raw value는 create/rotate 성공 응답과 해당 UI의 1회 표시 상태에서만 나타난다.
- DB row, migration, log, exception, dashboard/read model response, snapshot/history, test fixture에는 raw credential을 남기지 않는다.
- Public UI는 Story 7의 local-only smoke seed runner, `.private/smoke-*` 파일, `portal.smoke.seed.*` 설정을 사용하지 않는다.
- Existing Project -> Application -> Dashboard read model은 credential을 표시하지 않는다. 표시 가능한 것은 prefix/status/issuedAt/rotatedAt/revokedAt 같은 non-secret metadata뿐이다.

## Out of Scope

- email/password, magic link, GitHub 외 provider, anonymous flow
- invite/team/org/admin role management
- billing/tenant model
- browser localStorage/sessionStorage/cookie/URL token persistence
- raw metric explorer, arbitrary query UI, dashboard builder
- starter heartbeat/accepted bucket 의미 변경
- Story 7 local smoke seed/runbook 재작성
- `implementation-artifacts/sprint-status.yaml` 임의 갱신

## Existing Epics Update Proposal

`planning-artifacts/epics.md`에는 아래 block을 Epic 8 이후 또는 product onboarding 흐름을 다루는 위치에 추가하는 것을 제안한다. 이 작업에서는 사용자의 지시대로 기존 `epics.md`를 직접 수정하지 않는다.

```markdown
## Epic 9. Product Onboarding and Project Seed Issuance UI

목표: 사용자가 사이트 첫 화면에서 제품 흐름을 이해하고, GitHub OAuth로 로그인한 뒤 project를 등록해 starter 연결용 credential을 1회 표시로 발급받고, 기존 Project -> Application -> Dashboard 흐름으로 들어가게 한다.

Epic 9는 production onboarding product surface를 여는 epic이다. Story 7의 local/operator smoke seed는 그대로 local-only 검증 경로로 유지하며 public UI/API와 섞지 않는다.

### Stories

1. Product introduction and entry screen
   - 제품 첫 화면은 "starter를 앱에 붙이고, project를 등록하고, dashboard에서 상태를 본다"는 흐름을 설명한다.
   - 기존 Project Entry/Application List/Dashboard navigation 진입점을 제공한다.
   - 단순 marketing landing이나 raw explorer가 아니라 onboarding entry screen으로 둔다.
   - Project seed 발급, project creation API, credential rotation/revocation UI는 포함하지 않는다.
2. Project registration and seed issuance UI
   - authenticated account가 project를 등록하고 active membership을 얻는다.
   - starter credential은 원문 저장 없이 hash/prefix metadata만 저장하고, raw value는 생성/회전 응답에서 1회만 표시한다.
   - 복사 UX, 다시 볼 수 없음 copy, rotation/revocation flow, membership authorization, secret redaction guard를 구현한다.
   - Dashboard/read model/UI/snapshot/history에는 raw credential이 새지 않는다.
   - Local smoke/admin seed는 public onboarding UI/API와 분리한다.
```

## Sprint Status Update Proposal

`implementation-artifacts/sprint-status.yaml`은 이 작업에서 수정하지 않는다.

추후 sprint planning 시 아래와 같은 tracking entry를 추가하는 것을 제안한다.

```yaml
epic-9: backlog
9-1-product-introduction-and-entry-screen: ready-for-dev
9-2-project-registration-and-seed-issuance-ui: ready-for-dev
```

## Open Questions

1. Public project registration에 rate limit, CAPTCHA, organization allow-list 같은 abuse guard를 이번 MVP에 포함할지 별도 security/backoffice story로 분리할지 결정이 필요하다.
2. Starter credential의 product-facing 이름을 `project seed`, `starter credential`, `service token` 중 무엇으로 고정할지 copy decision이 필요하다. 구현 경계에서는 account access/refresh token과 섞이지 않는 이름을 우선한다.
3. Rotation 시 기존 starter credential을 즉시 폐기할지, 짧은 grace period를 둘지 운영 정책 결정이 필요하다. 이 epic의 기본 제안은 단순하고 안전한 즉시 폐기다.
