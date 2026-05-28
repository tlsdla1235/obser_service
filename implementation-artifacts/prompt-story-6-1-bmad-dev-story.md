---
artifactType: prompt
projectName: Spring Boot 운영 첫 화면 포털
storyId: "6.1"
storyKey: "6-1-account-project-entry-and-setup-guide"
date: 2026-05-28
purpose: New-context prompt for BMAD dev-story execution
---

# New Context Prompt - Story 6.1 BMAD Dev Story

아래 프롬프트를 새 컨텍스트에 그대로 붙여 넣는다.

```text
BMAD dev story로 Story 6.1을 구현해줘.

작업 디렉터리:
/Users/tlsdla1235/Desktop/study/observation

Story file:
/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md

반드시 먼저 읽을 계약/문서:
1. /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/spec-story-6-1-account-project-entry-and-setup-guide-contract-decisions.md
2. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/6-1-account-project-entry-and-setup-guide.md
3. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/contracts/account-auth-policy.md
4. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/api-surface.md
5. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/project-structure.md
6. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/database-schema.md
7. /Users/tlsdla1235/Desktop/study/observation/planning-artifacts/epic5-6-dashboard-alignment-context.md
8. /Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/sprint-status.yaml
9. /Users/tlsdla1235/Desktop/study/observation/AGENTS.md

닫힌 계약은 다시 열지 마:
- GitHub OAuth only account entry를 구현한다.
- GitHub OAuth App credential 실제 값은 Git 추적 대상이 아닌 .private/github-oauth.properties에서 읽는다. 실제 client secret 값을 응답, 로그, 테스트 snapshot, 문서에 출력하지 마.
- 우리 서비스 access token/refresh token은 JSON response body로 전달한다.
- Resource API 인증은 Authorization: Bearer <access_token> 기준이다.
- Cookie 기반 server session, redirect fragment token 전달, browser localStorage 저장 전제는 만들지 않는다.
- Refresh token store는 Redis에 고정하지 않는다. 초기 후보는 PostgreSQL/RDBMS hashed refresh token 또는 token family metadata다.
- Public POST /api/projects, 로그인 직후 자동 project 생성, UI의 Create Project flow는 만들지 않는다.
- Project가 없으면 local/internal seed 또는 admin-only bootstrap decision이 필요하다는 safe empty state를 보여준다.
- UI는 observability-portal/src/main/resources/static/dashboard/ 아래 static asset으로 구현한다.
- 별도 frontend build, src/main/frontend, React, Vite, TypeScript는 도입하지 않는다.
- API controller는 @RestController JSON boundary를 유지한다. View resolver/template engine은 도입하지 않는다.
- Setup guide는 dependency, observation.heartbeat.portal-base-url, observation.heartbeat.project-key, observation.metric-flush.environment만 핵심 안내로 둔다.
- Setup guide에서 observation.metric-flush.project-id, application-name, instance, queue/drop/heartbeat interval, route allowlist, dashboard tuning, alert delivery, p95/p99 계산법, endpoint priority 해석, raw explorer를 핵심 안내로 만들지 않는다.
- Epic 6 UI는 Epic 5 API/read model을 표시만 한다. lifecycle state, starter connection diagnosis, rule, p95/p99, endpoint priority, snapshot/history event를 UI에서 계산하지 않는다.

BMAD dev-story workflow를 따라 끝까지 진행해줘.
Story file 수정은 BMAD dev-story 규칙에 따라 Tasks/Subtasks checkboxes, Dev Agent Record, File List, Change Log, Status 영역만 수정해.

구현 중 사용자 변경사항이나 기존 미추적 파일은 되돌리지 마.
특히 현재 존재할 수 있는 implementation-artifacts/epic-5-dbml-snapshot-2026-05-28.dbml 같은 무관한 미추적 파일은 건드리지 마.

완료 전 검증:
./gradlew :observability-portal:test --tests '*ProjectNavigation*'
./gradlew :observability-portal:test --tests '*AccountAuth*'
./gradlew :observability-portal:test --tests '*GithubOAuth*'
./gradlew :observability-portal:test --tests '*UnsupportedSignupMethod*'
./gradlew :observability-portal:test --tests '*AuthSecretExposure*'
./gradlew :observability-portal:test --tests com.observation.portal.architecture.MvcLayerBoundaryTest
./gradlew :observability-portal:test
git diff --check

테스트 일부가 아직 존재하지 않아 패턴 매칭 실패가 나면, 필요한 focused test를 추가한 뒤 다시 실행해.
완료 보고에는 어떤 파일을 바꿨는지, 어떤 검증을 통과했는지, 못 돌린 검증이 있으면 이유를 짧게 정리해줘.
```
