---
artifactType: prompt
projectName: Spring Boot 운영 첫 화면 포털
storyId: "6.2"
storyKey: "6-2-project-selection-ui"
date: 2026-05-28
purpose: New-context prompt for BMAD create-story execution
---

# New Context Prompt - Story 6.2 BMAD Create Story

아래 프롬프트를 새 컨텍스트에 그대로 붙여 넣는다.

```text
BMAD create story로 Story 6.2를 생성해줘.

작업 디렉터리:
/Users/tlsdla1235/Desktop/study/observation

대상 story:
6.2 Project selection UI

예상 story key:
6-2-project-selection-ui

목표:
planning-artifacts/stories/6-2-project-selection-ui.md story file을 생성하고, BMAD create-story workflow에 따라 sprint-status에서 Story 6.2를 ready-for-dev로 전환해줘.

반드시 먼저 읽을 계약/문서:
1. /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/spec-story-6-2-project-selection-ui-contract-decisions.md
2. /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/sprint-status.yaml
3. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/epics.md
4. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/sprint-plan.md
5. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/current-product-source-of-truth.md
6. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md
7. /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/spec-story-6-1-account-project-entry-and-setup-guide-contract-decisions.md
8. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/5-1-project-application-navigation-read-model.md
9. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/api-surface.md
10. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/project-structure.md
11. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/account-auth-policy.md
12. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/read-model-contract.md
13. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/dashboard-read-model.md
14. /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/epic-5-retro-2026-05-27.md
15. /Users/tlsdla1235/Desktop/study/observation/AGENTS.md

반드시 확인할 현재 코드:
1. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/catalog/controller/ProjectNavigationController.java
2. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/catalog/model/ProjectNavigationReadModel.java
3. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/catalog/service/ProjectApplicationNavigationService.java
4. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/resources/static/dashboard/index.html
5. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/resources/static/dashboard/app.js
6. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/resources/static/dashboard/styles.css
7. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java
8. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/domain/catalog/controller/ProjectNavigationControllerTest.java
9. /Users/tlsdla1235/Desktop/study/observation/observability-portal/src/test/java/com/observation/portal/architecture/MvcLayerBoundaryTest.java

닫힌 계약은 다시 열지 마:
- Story 6.2는 기존 GET /api/projects read-only navigation read model을 소비해 Project scope 선택 UI를 완성한다.
- Story 6.2는 Application List, Application Dashboard, Project creation/onboarding, project ownership/key issuance flow를 구현하지 않는다.
- Project selection UI는 GET /api/projects current shape만 소비한다.
- 표준 field는 generatedAt, projects[].projectId, name, applicationCount, setupConnectionIssueCount, recentConcern, links.applications다.
- setupIssueCount, recentConcernCount 같은 legacy alias fallback을 만들지 않는다.
- projectHealth, status, priority, severity 같은 project-level 판단 field를 만들거나 요구하지 않는다.
- Project selection UI의 primary navigation은 각 project item의 links.applications를 그대로 사용해 Application List로 이동하는 것이다.
- Application Dashboard direct link, 첫 application 자동 선택, application id 추론, dashboard shortcut은 만들지 않는다.
- Story 6.2는 public POST /api/projects, 로그인 직후 자동 project 생성, Create Project flow를 만들지 않는다.
- Project가 없으면 local/internal seed 또는 admin bootstrap decision이 필요하다는 safe empty state를 보여준다.
- Project selection UI copy는 project/application health를 단정하지 않는다.
- setupConnectionIssueCount는 setup/connection issue candidate count로만 표현한다.
- "정상", "문제 없음", "앱 다운", "장애", "복구 완료", healthy/unhealthy, degraded/critical, Project health 같은 copy를 쓰지 않는다.
- Story 6.2는 observability-portal/src/main/resources/static/dashboard/ static asset을 개선한다.
- src/main/frontend, package.json, React, Vite, TypeScript, 별도 frontend build/deploy, view resolver/template engine은 도입하지 않는다.
- Epic 6 완료와 retrospective 이후 Post-MVP SPA 전환을 검토할 수 있지만, Story 6.2에서 static HTML/CSS/JS를 장기 architecture로 확정하지 않는다.
- Client-side code는 formatting, escaping, loading/empty/error rendering, auth header 구성, optional project name filter, links.applications navigation 같은 presentation helper만 둘 수 있다.
- lifecycle state, setup/connection diagnosis, project health/status/priority/severity, recentConcern, p95/p99, endpoint priority, snapshot/history event, project ranking을 UI에서 계산하지 않는다.
- Candidate count가 0보다 큰 경우의 시각 강조는 운영 severity가 아니라 neutral attention style로만 취급한다.
- Story 6.2는 browser token persistence 정책을 닫지 않는다.
- Static UI는 in-memory access token hook, Authorization: Bearer <access_token> header 구성, 401 safe state까지만 다룬다.
- localStorage, sessionStorage, cookie, URL fragment/query token parsing, refresh token browser storage, logout/session persistence policy는 Epic 6 이후 SPA/auth frontend decision으로 남긴다.
- Story 6.2 completion은 static UI contract test 중심으로 검증한다.

Story file에 꼭 포함할 Acceptance Criteria:
- Project selection UI가 GET /api/projects current shape만 소비한다.
- 표준 field 이름만 사용하고 legacy alias를 사용하지 않는다.
- links.applications navigation만 사용한다.
- dashboard shortcut과 first application auto-select를 만들지 않는다.
- Project creation flow를 만들지 않는다.
- project/application health를 단정하는 copy를 쓰지 않는다.
- static dashboard asset boundary를 유지하고 frontend build stack을 도입하지 않는다.
- browser token persistence와 URL token parsing을 만들지 않는다.
- UI-side operational recomputation helper를 만들지 않는다.
- setup guide는 Story 6.1의 dependency, observation.heartbeat.portal-base-url, observation.heartbeat.project-key, observation.metric-flush.environment 안내에 머문다.
- static UI contract test가 위 금지선을 검증한다.

Story file의 Tasks/Subtasks는 UI polish와 test guard 중심으로 작성해줘:
- Project selection list/card polish
- reload와 optional project name filter
- loading/empty/auth/error state
- links.applications navigation
- setup guide aside 유지
- responsive layout
- ProjectEntryUiContractTest 확장 또는 ProjectSelectionUiContractTest 추가
- ProjectNavigation focused regression
- no frontend stack/no token persistence/no recomputation/no project creation guard

BMAD create-story workflow를 따라 끝까지 진행해줘.
Story 생성만 하고 dev-story 구현은 하지 마.

구현 중 사용자 변경사항이나 기존 미추적 파일은 되돌리지 마.
완료 보고에는 생성/수정한 파일, sprint-status 변경 여부, Story 6.2가 ready-for-dev가 되었는지, create-story 중 발견한 open risk를 짧게 정리해줘.
```
