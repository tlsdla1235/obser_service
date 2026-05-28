# Epic 6 Context: Dashboard User Flow and Demo Hardening

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Epic 6은 Epic 5에서 닫은 server read model/API를 사용자가 실제로 밟는 화면 흐름으로 연결한다. 사용자는 project에서 application으로, application dashboard에서 instance evidence와 bounded history로 좁혀 들어가며, demo는 starter setup, heartbeat, first accepted bucket, insufficient sample, no-triage baseline, failure/recovery path를 빈 화면 없이 설명해야 한다. 이 epic의 핵심은 UI가 새 판단 engine이 되는 것이 아니라 이미 계산된 read model을 안전하게 렌더링하는 것이다.

## Stories

- Story 6.1: Account/project entry and setup guide
- Story 6.2: Project selection UI
- Story 6.3: Application list UI
- Story 6.4: Application dashboard UI integration
- Story 6.5: Instance evidence UI
- Story 6.6: Instance snapshot trend UI
- Story 6.7: Snapshot/history marker UI and deep link
- Story 6.8: Demo green path
- Story 6.9: Failure/recovery path demo hardening

## Requirements & Constraints

Epic 6 화면 순서는 Project Entry, Application List, Application Dashboard, Instance Detail, Instance Snapshot Trend, Snapshot/History로 둔다. Project Entry와 Project selection은 scope 선택과 setup guide 진입 화면이며 Application Dashboard 판단을 대신하지 않는다. Application List는 scan과 dashboard 진입을 돕되 상세 판단은 dashboard read model에서 온다. Instance Detail은 evidence drill-down이고 application 판단을 대체하지 않는다. Trend/history는 stored dashboard snapshot/read model projection이며 raw explorer나 arbitrary query UI가 아니다.

Account signup/login은 GitHub OAuth only이고, resource API 인증은 `Authorization: Bearer <access_token>` header 기준이다. GitHub OAuth token과 서비스 access/refresh token은 구분하며, token, secret, provider raw payload는 response/log/error surface에 노출하지 않는다. Browser token persistence, cookie server session, redirect fragment token 전달은 이 epic의 개별 story에서 명시적으로 닫기 전까지 전제하지 않는다.

Project creation 방식, ownership, role model, project key 발급/회전/재발급은 open decision으로 남아 있다. Story 구현 중 public onboarding API, 로그인 직후 자동 project 생성, Create Project flow가 필요해 보이면 구현하지 말고 contract decision으로 올린다.

## Technical Decisions

MVP dashboard UI asset은 `observability-portal/src/main/resources/static/dashboard/`의 static HTML/CSS/JS를 사용한다. `src/main/frontend`, React, Vite, TypeScript, 별도 frontend build/deploy, view resolver/template engine은 Epic 6 story 안에서 도입하지 않는다.

UI는 server read model을 표시한다. Frontend는 lifecycle state, starter diagnosis, zeroInsight, recovery, rule, p95/p99, endpoint priority, marker/event, project ranking을 재계산하지 않는다. Metric data axis와 starter connection axis는 섞지 않는다. Percentile은 source-scoped starter canonical point이며, UI가 instance/window p95/p99 평균, 최댓값, 병합을 만들지 않는다. Snapshot/detail/history는 stored read model source를 유지한다.

Application List API는 read-only `GET /api/projects/{projectId}/applications` surface이며 Application Dashboard 판단을 대체하지 않는다. 필요할 때에도 lifecycle state badge, last accepted bucket, starter connection summary, top concern 0~1개 수준의 server-computed light summary만 사용한다. First-screen 판단은 dashboard query API 책임이다.

## UX & Interaction Patterns

첫 화면은 데이터가 없거나 부족하거나 회복 중이어도 빈 화면처럼 보이지 않아야 한다. Project와 Application 목록은 사용자가 scope를 좁히는 화면으로 설계하고, dashboard/detail/history 화면과 같은 운영 확정 표현을 앞당겨 쓰지 않는다. Copy는 setup/connection 후보, source 부재, next action을 구분해 설명하고, host down이나 project/application health를 UI에서 단정하지 않는다.

## Cross-Story Dependencies

Story 6.1은 GitHub OAuth only account entry, Bearer token 기준, minimal setup guide, static dashboard asset boundary를 마련한다. Story 6.2는 `GET /api/projects` project navigation read model을 소비해 Project selection을 닫고, Story 6.3이 인증 fetch 가능한 Application List UI를 구현할 때 사용할 `links.applications` 경계를 보존해야 한다. Story 6.4 이후 화면은 Epic 5 dashboard/snapshot/history read model 계약을 그대로 렌더링해야 한다.
