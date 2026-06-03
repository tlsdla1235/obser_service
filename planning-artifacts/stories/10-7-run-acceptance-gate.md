---
artifactType: story
storyId: "10.7"
storyKey: "10-7-run-acceptance-gate"
epic: "Epic 10. Figma Make Acceptance And Frontend Hardening"
title: "Run acceptance gate"
architectureStyle: "Vite SPA on Spring static root"
status: review
date: 2026-06-02
baselineCommits:
  story10_1: "cc7d87a frontend: adopt figma workspace and routing"
  story10_2: "7850d88 frontend: port auth and fetch foundation"
  story10_3: "e5b3ffa frontend: wire story 10.3 read models"
  story10_4: "992af31 frontend: wire story 10.4 surfaces"
  story10_5: "9c979f5 portal: build vite spa with gradle"
  story10_6: "2cf55ac portal: replace legacy static dashboard"
commitBoundary: "test: verify figma acceptance traceability"
---

# Story 10.7 - Run acceptance gate

## Status

review

## Story

Epic 10 acceptance 검증자는 Story 10.1~10.6에서 인수한 Vite SPA, auth/API foundation, server read model wiring, Gradle packaging, Spring static delivery가 하나의 Spring-served origin에서 end-to-end로 동작하는지 확인하고 싶다.

그래야 Figma Make 인수 Epic이 새 제품 동작을 추가하지 않고도 기존 API surface, memory-only token, one-time credential, no-recompute read model 경계를 지킨다는 증거를 남길 수 있다.

## Source Of Truth

아래 문서와 파일을 읽고 반영한 create-story 산출물이다. 충돌처럼 보이는 지점은 현재 Epic 10 sprint plan, Story 10.6 완료 커밋, 실제 `frontend/`와 `observability-portal` source를 우선한다.

1. `AGENTS.md`
2. `_bmad/custom/project-context.md`
3. `implementation-artifacts/sprint-status.yaml`
4. `planning-artifacts/figma-make-acceptance-sprint-plan.md`
5. `planning-artifacts/stories/10-1-adopt-workspace-and-routing.md`
6. `planning-artifacts/stories/10-2-port-auth-and-fetch-foundation.md`
7. `planning-artifacts/stories/10-3-wire-types-adapters-navigation-and-dashboard.md`
8. `planning-artifacts/stories/10-4-wire-evidence-trend-and-credential-surfaces.md`
9. `planning-artifacts/stories/10-5-integrate-frontend-build-with-gradle.md`
10. `planning-artifacts/stories/10-6-replace-legacy-dashboard-and-clean-residue.md`
11. `planning-artifacts/acceptance-traceability.md`
12. `planning-artifacts/architecture.md`
13. `planning-artifacts/project-structure.md`
14. `frontend/package.json`
15. `frontend/vite.config.ts`
16. `observability-portal/build.gradle`
17. `observability-portal/src/main/java/com/observation/portal/config/DashboardStaticWebConfig.java`
18. `observability-portal/src/test/java/com/observation/portal/config/DashboardStaticWebConfigTest.java`

## Current Baseline

- 현재 브랜치는 `codex/figma-acceptance-test`이고 Story 10.6 최신 관련 커밋은 `2cf55ac portal: replace legacy static dashboard`다.
- Story 10.5에서 Gradle `frontendBuild`와 `processResources`가 `frontend/dist`를 jar `static/` root로 복사하도록 연결됐다.
- Story 10.6에서 legacy `static/dashboard/*` source가 삭제됐고, `DashboardStaticWebConfig`는 `/dashboard`, `/dashboard/`, `/docs`, `/docs/`만 `forward:/index.html`로 보낸다.
- `frontend/package.json`은 npm-only Vite SPA이며 `build`, `dev`, `typecheck` script를 가진다.
- `frontend/vite.config.ts`는 `base: '/'`, React plugin, Tailwind plugin, `@` alias를 유지하며 Figma asset resolver residue는 제거된 상태다.
- `DashboardStaticWebConfigTest`는 exact fallback allow-list와 `/api/**`, `/assets/**`, 확장자 resource path shadow 방지를 검증한다.
- 현재 기준 known duplicate Java blocker는 해결되어야 한다. 10.7 착수 시 `find observability-portal/src/main/java -name '* 2.java' -print`가 빈 결과인지 다시 확인한다.
- 현재 workspace에는 root `README.md`, `docs/`, `planning-artifacts/figma-make-nextjs-frontend-spec.md`, `planning-artifacts/stories/10-4-wire-evidence-trend-and-credential-surfaces.md`, `=` 같은 기존 unrelated/untracked 항목이 있을 수 있다. 10.7 구현자는 이를 acceptance 변경과 섞지 않는다.

## Scope

- Epic 10 전체 acceptance gate 실행과 결과 기록.
- command verification, jar/static packaging verification, Spring-served route/API/static resource verification.
- Spring-served origin에서 GitHub OAuth, Bearer resource API, 401, Project/Application/Dashboard/Evidence/Trend/History/Credential 흐름 수동 QA.
- Network tab 기준 기존 API surface allow-list 검증.
- token, credential, provider payload, project key hash 노출 여부 정적/수동 검증.
- `planning-artifacts/acceptance-traceability.md` 또는 story Dev Agent Record에 검증 증거와 blocker를 기록한다. 문서를 갱신한다면 Epic 10 acceptance evidence에 한정한다.
- acceptance를 막는 테스트 공백이 있으면 route/static/security guard 테스트 보강은 허용된다.

## Non-scope / Guardrails

- 새 backend endpoint, controller, service, repository, migration 생성 금지.
- Next.js 전환 또는 Next.js API route 생성 금지.
- frontend auth/API/read-model 계산 변경 금지.
- token, provider payload, starter credential secret을 URL, cookie, `localStorage`, `sessionStorage`, DOM attribute, log, error에 노출하는 코드 추가 금지.
- lifecycle state, starter connection diagnosis, zeroInsight reason, recovery guidance, p95/p99, endpoint priority, snapshot/history event client-side 계산 추가 금지.
- Project/Application/Dashboard/Evidence/Trend/History/Credential API endpoint shape 변경 금지.
- starter ingest API를 dashboard UI 호출 대상으로 만들지 않는다.
- root `README.md`, root `docs/`, `planning-artifacts/figma-make-nextjs-frontend-spec.md` 같은 기존 unrelated/untracked 항목을 수정, 이동, 삭제, stage하지 않는다.
- `frontend/node_modules`, `frontend/dist`, Gradle build output은 source control 대상으로 추가하지 않는다.

## Acceptance Criteria

### Command Verification

1. `git status --short`를 실행하고 기존 unrelated/untracked 항목을 기록한다.
2. 기록한 unrelated/untracked 항목은 acceptance 변경, traceability 변경, 테스트 보강과 섞지 않는다.
3. `find observability-portal/src/main/java -name '* 2.java' -print` 결과가 빈 결과임을 확인한다. 결과가 있으면 acceptance blocker로 기록하고 임의 삭제하지 않는다.
4. `cd frontend && npm run typecheck`가 통과한다.
5. `cd frontend && npm run build`가 통과한다.
6. `./gradlew :observability-portal:bootJar --rerun-tasks`가 통과하고 `npm ci`/Vite build/processResources/bootJar task chain을 거친다.
7. jar 내부에서 `BOOT-INF/classes/static/index.html`을 확인한다.
8. jar 내부에서 `BOOT-INF/classes/static/assets/*` 아래 Vite generated JS와 CSS asset을 각각 확인한다.
9. jar 내부에서 `BOOT-INF/classes/static/dashboard`가 없는지 확인한다.
10. `rg -n "figma:asset|figmaAssetResolver|ImageWithFallback|pnpm" frontend observability-portal`를 실행한다.
11. 위 `rg`가 `frontend/node_modules` noise를 포함하면 원 결과를 기록하고 `rg -n "figma:asset|figmaAssetResolver|ImageWithFallback|pnpm" frontend observability-portal -g '!frontend/node_modules/**'`로 source-only 재검증한다.
12. source-only residue 검색은 `figma:asset`, `figmaAssetResolver`, `ImageWithFallback`, pnpm workspace residue가 없음을 보여야 한다. 허용 가능한 match가 있으면 왜 runtime/source residue가 아닌지 기록한다.

### Spring-Served Route / Static Verification

13. 검증은 Vite dev server가 아니라 built jar 또는 Gradle-served Spring origin에서 수행한다.
14. `/` 직접 진입과 새로고침이 새 Vite SPA root로 수렴한다.
15. `/dashboard` 직접 진입과 새로고침이 새 Vite SPA dashboard route로 수렴한다.
16. `/dashboard/` 직접 진입과 새로고침이 새 Vite SPA dashboard route로 수렴한다.
17. `/docs` 직접 진입과 새로고침이 새 Vite SPA docs route로 수렴한다.
18. `/docs/` 직접 진입과 새로고침이 새 Vite SPA docs route로 수렴한다.
19. `/api/projects`는 SPA HTML로 shadow되지 않고 인증/API 응답으로 수렴한다. 인증 없음이면 401/API semantics가 자연스럽다.
20. jar에 포함된 실제 `/assets/*.js`는 JavaScript asset으로 반환된다.
21. jar에 포함된 실제 `/assets/*.css`는 CSS asset으로 반환된다.
22. 없는 asset path는 SPA HTML로 forward되지 않는다.
23. 삭제된 `/dashboard/app.js`와 `/dashboard/styles.css`는 SPA HTML로 forward되지 않는다.
24. `DashboardStaticWebConfig`의 exact allow-list 정책과 broad catch-all 부재가 유지된다.

### Auth / Security Verification

25. Spring-served origin에서 GitHub OAuth popup 또는 redirect fallback을 시작할 수 있다.
26. `GET /api/auth/github/authorize` 이후 callback relay가 `postMessage` 또는 meta relay fallback으로 relay id를 전달한다.
27. `POST /api/auth/github/callback/tokens` token exchange가 성공하거나, 환경 문제로 실패하면 실패 사유를 blocker로 기록한다.
28. service access token은 URL query/hash/fragment에 남지 않는다.
29. service access token, refresh token, GitHub provider token은 cookie, `localStorage`, `sessionStorage`에 저장되지 않는다.
30. DevTools Application tab 기준 starter credential raw value도 browser storage에 저장되지 않는다.
31. resource API 요청은 `Authorization: Bearer <access_token>` header를 사용한다.
32. token/credential/provider payload는 DOM attribute, hidden input, title, aria-label, log, error message에 노출되지 않는다.
33. 401을 수동 유도하면 token clear와 auth-required/stale UI로 수렴한다.
34. 401은 project absence, application health, host down, credential revoked로 오인되어 표시되지 않는다.

### Server Read-Model / No-Recompute Verification

35. Project list는 `GET /api/projects`만 entry source로 사용한다.
36. Application list는 selected project의 `links.applications`만 따른다.
37. Dashboard는 selected application의 `links.dashboard`만 따른다.
38. Instance evidence는 dashboard response의 `instances[].links.evidence`만 따른다.
39. Snapshot trend/history는 기존 endpoint template과 server-provided link만 사용한다.
40. UI/adapter는 lifecycle state를 재계산하지 않는다.
41. UI/adapter는 starter connection diagnosis를 재계산하지 않는다.
42. UI/adapter는 zeroInsight reason/action을 새로 만들지 않는다.
43. UI/adapter는 recovery guidance를 재계산하지 않는다.
44. UI/adapter는 p95/p99를 histogram bucket에서 평균, 병합, 최댓값, 보간으로 계산하지 않는다.
45. UI/adapter는 endpoint priority를 sort/rank/reduce/index 기반으로 새로 계산하지 않는다.
46. UI/adapter는 snapshot/history event, marker severity, event promotion/suppression/period folding을 만들지 않는다.
47. accepted bucket freshness와 starter heartbeat/connection은 별도 축으로 표시된다.
48. 최근 heartbeat와 없거나 오래된 accepted bucket 조합을 host application down으로 단정하지 않는다.
49. `triageCards=[]` response에서는 zero-insight reason/action이 표시된다.
50. Static review는 `frontend/src/app/lib`와 `frontend/src/app/components`에서 recompute 의심 match를 수기 분류하고, harmless server field display와 실제 계산을 구분해 기록한다.

### Credential One-Time Verification

51. Project create 성공의 `starterCredential.displayValue`는 성공 직후 한 번만 표시된다.
52. Credential rotation 성공의 `starterCredential.displayValue`는 성공 직후 한 번만 표시된다.
53. copy, confirm, close 중 하나를 수행하면 raw `displayValue`가 React UI state에서 제거된다.
54. raw credential은 다시 표시되지 않으며, 새 값을 보려면 rotation으로 새 credential을 발급해야 한다는 copy가 제공된다.
55. Metadata와 revocation response는 raw value/hash를 표시하지 않는다.
56. storage, DOM, log, error에 raw credential, token, provider payload, project key hash가 노출되지 않는다.
57. Credential lifecycle request는 `cache: "no-store"` 계열 option을 사용한다.

### Endpoint Allow-List / Network Verification

58. Network tab 기준 새 backend endpoint 없이 기존 API surface만 호출된다.
59. Auth flow 허용 endpoint는 기존 `GET /api/auth/github/authorize`, browser callback, `POST /api/auth/github/callback/tokens` 범위다.
60. Resource flow 허용 endpoint는 `GET /api/projects`, `links.applications`, `links.dashboard`, `instances[].links.evidence`, snapshot trend/history/detail, project registration, starter credential lifecycle의 기존 endpoint 범위다.
61. Dashboard UI는 `/api/ingest/v1/buckets`를 호출하지 않는다.
62. Dashboard UI는 `/api/ingest/v1/heartbeat`를 호출하지 않는다.
63. starter ingest API는 docs/setup 안내에만 남는다.
64. Direct API URL 조립이 필요한 fallback은 Story 10.4에서 허용한 기존 endpoint template과 selected context validation을 지켜야 한다.
65. Vite dev server, Next.js route, `/api` proxy를 acceptance 결과로 대체하지 않는다. 최종 기록은 Spring-served origin 기준이다.

### Scope Guard

66. 구현 diff에 새 backend endpoint, controller, service, repository, migration이 없어야 한다.
67. 구현 diff에 Next.js, `next/server`, `app/api`, `pages/api`가 없어야 한다.
68. 구현 diff에 frontend auth/API/read-model behavior 변경이 없어야 한다. 필요한 경우 테스트 또는 문서 기록 변경에 한정한다.
69. 구현 diff에 token/credential storage 정책 변경이 없어야 한다.
70. 구현 diff에 lifecycle/p95/p99/endpoint priority/snapshot/history 계산 추가가 없어야 한다.
71. `frontend/node_modules`, `frontend/dist`, Gradle build output이 source control 변경에 포함되지 않아야 한다.
72. 기존 unrelated/untracked 항목은 수정, 이동, 삭제, stage하지 않는다.
73. 테스트 보강이 필요한 경우 route/static/security guard에 집중하고 제품 동작을 바꾸지 않는다.

### Documentation / Traceability Recording

74. command verification 결과와 수동 QA 결과를 Dev Agent Record에 기록한다.
75. jar asset 검증은 실제 확인한 JS/CSS 파일명 또는 패턴과 함께 기록한다.
76. Spring-served route/static 검증은 요청 path, 기대 결과, 실제 결과를 기록한다.
77. OAuth, storage, Bearer header, 401, credential one-time, Network allow-list 검증은 수행 여부와 blocker를 명확히 기록한다.
78. `planning-artifacts/acceptance-traceability.md`를 갱신한다면 Epic 10 acceptance evidence만 추가하고 기존 MVC matrix를 불필요하게 재구성하지 않는다.
79. 검증 실패가 local DB, OAuth 계정, Spring origin, node_modules noise, manual QA 재현성 때문이면 제품 pass로 가장하지 말고 Open Risks / Blockers에 남긴다.
80. commit boundary는 `test: verify figma acceptance traceability`로 유지한다.

## Tasks / Subtasks

- [x] Pre-flight workspace 상태 기록 (AC: 1~3, 72)
  - [x] `git status --short`를 실행하고 기존 unrelated/untracked 항목과 10.7 변경 후보를 분리해 기록한다.
  - [x] `find observability-portal/src/main/java -name '* 2.java' -print`가 빈 결과인지 확인한다.
  - [x] 기존 root `README.md`, `docs/`, Next.js spec, unrelated story file, `=` 같은 항목을 건드리지 않겠다고 implementation notes에 남긴다.

- [x] Frontend/build/jar command gate 실행 (AC: 4~12)
  - [x] `cd frontend && npm run typecheck`
  - [x] `cd frontend && npm run build`
  - [x] `./gradlew :observability-portal:bootJar --rerun-tasks`
  - [x] jar 내부 `BOOT-INF/classes/static/index.html` 확인.
  - [x] jar 내부 `BOOT-INF/classes/static/assets/*.js`와 `*.css` 확인.
  - [x] jar 내부 `BOOT-INF/classes/static/dashboard` 부재 확인.
  - [x] Figma/pnpm residue `rg`를 실행하고 node_modules noise가 있으면 source-only 재검증한다.

- [x] Spring-served origin route/static 검증 (AC: 13~24)
  - [x] built jar 또는 Gradle-served Spring origin을 local DB/profile 기준으로 기동한다.
  - [x] `/`, `/dashboard`, `/dashboard/`, `/docs`, `/docs/` 직접 진입과 새로고침을 확인한다.
  - [x] `/api/projects`가 SPA HTML로 shadow되지 않는지 확인한다.
  - [x] jar 내부 실제 `/assets/*.js`, `/assets/*.css` 요청이 JS/CSS로 반환되는지 확인한다.
  - [x] 없는 asset과 `/dashboard/app.js`, `/dashboard/styles.css`가 SPA HTML로 forward되지 않는지 확인한다.

- [x] Auth/security gate 실행 (AC: 25~34)
  - [x] Spring-served origin에서 GitHub OAuth popup/relay/token exchange flow를 확인한다.
  - [x] DevTools Application tab에서 URL, cookie, `localStorage`, `sessionStorage`에 token/provider token/credential이 없는지 확인한다.
  - [x] Network tab에서 resource API 요청이 `Authorization: Bearer <access_token>` header를 쓰는지 확인한다.
  - [x] 401을 수동 유도하고 token clear, auth-required/stale UI, 오인 표시 부재를 확인한다.

- [x] Server read-model/no-recompute gate 실행 (AC: 35~50)
  - [x] Project/Application/Dashboard/Evidence request가 server-provided links를 따르는지 Network tab에서 확인한다.
  - [x] Snapshot trend/history가 기존 endpoint template과 validated context만 사용하는지 확인한다.
  - [x] accepted bucket freshness와 starter heartbeat/connection이 별도 축으로 표시되는지 확인한다.
  - [x] `triageCards=[]` fixture 또는 실제 response에서 zeroInsight reason/action 표시를 확인한다.
  - [x] recompute 의심 grep을 실행하고 각 match가 server field display인지, 금지된 계산인지 분류한다.

- [x] Credential one-time gate 실행 (AC: 51~57)
  - [x] Project create 성공 후 `starterCredential.displayValue` 1회 표시를 확인한다.
  - [x] Credential rotation 성공 후 `starterCredential.displayValue` 1회 표시를 확인한다.
  - [x] copy/confirm/close 뒤 raw value가 UI state와 storage/DOM/log/error에서 제거되는지 확인한다.
  - [x] Metadata/revocation response가 raw value/hash를 표시하지 않는지 확인한다.

- [x] Endpoint allow-list/network gate 실행 (AC: 58~65)
  - [x] Network tab을 정리한 뒤 login부터 credential lifecycle까지 실행한다.
  - [x] 호출 endpoint가 기존 API surface allow-list 안에만 있는지 기록한다.
  - [x] `/api/ingest/v1/buckets`와 `/api/ingest/v1/heartbeat`가 dashboard UI에서 호출되지 않음을 확인한다.
  - [x] docs/setup 안내에 남은 starter ingest API 언급은 runtime dashboard 호출이 아님을 기록한다.

- [x] Scope guard와 traceability 기록 정리 (AC: 66~80)
  - [x] `git diff --name-only`와 `git diff --check`를 실행한다.
  - [x] 새 backend endpoint/Next.js route/frontend behavior change/build output tracking이 없는지 확인한다.
  - [x] Dev Agent Record에 command/manual QA/Network/storage/jar evidence를 기록한다.
  - [x] 필요하면 `planning-artifacts/acceptance-traceability.md`에 Epic 10 acceptance evidence를 좁게 추가한다.
  - [x] blocker가 있으면 pass로 표시하지 않고 Open Risks / Blockers에 남긴다.

## Dev Notes

### This Is A Verification Story

Story 10.7은 구현 기능 추가 story가 아니다. 이 story의 산출물은 Epic 10 전체 acceptance gate 실행 결과, traceability 기록, 필요한 경우 좁은 guard 테스트 보강이다. 제품 동작을 바꾸거나 새 API를 만들면 commit boundary를 위반한다.

### Previous Story Intelligence

- Story 10.1은 `figma/` export를 최상위 `frontend/` npm-only Vite SPA로 인수하고 root `BrowserRouter`로 `/`, `/dashboard`, `/docs`를 만들었다.
- Story 10.2는 GitHub OAuth popup relay, in-memory access token, Bearer `authFetch`, 401 token clear, request sequence guard를 React 구조로 이식했다. 10.7은 token이 memory-only인지 Spring-served origin에서 확인해야 한다.
- Story 10.3은 Project/Application/Dashboard를 `GET /api/projects` -> `links.applications` -> `links.dashboard` chain으로 연결하고 mock seed를 제거했다. 10.7은 link chain과 no-recompute 경계를 검증한다.
- Story 10.4는 Instance Evidence, Snapshot Trend, Snapshot/History, Project Registration, Starter Credential Lifecycle을 기존 endpoint로 연결했다. 10.7은 one-time credential과 endpoint allow-list를 검증한다.
- Story 10.5는 node-gradle plugin, `npm ci`, `frontendBuild`, `processResources`, jar static root packaging을 닫았다. 10.7은 `bootJar --rerun-tasks`와 jar contents를 다시 검증한다.
- Story 10.6은 legacy static dashboard와 Figma residue를 제거하고 exact SPA fallback allow-list를 적용했다. 10.7은 Spring-served direct entry, API/asset shadow 방지, legacy dashboard 부재를 검증한다.

### Architecture Guardrails

- Active architecture는 Traditional MVC + Service/Repository Layering이다.
- Portal package는 feature-first MVC이며, 이 story에서 `application`, `port`, `adapter`, `adapter.in`, `adapter.out` package를 만들지 않는다.
- UI는 server read model을 표시한다. lifecycle state, starter diagnosis, zeroInsight, recovery, p95/p99, endpoint priority, snapshot/history event는 service/read model source of truth다.
- Account auth는 GitHub OAuth only이며 resource API 인증은 `Authorization: Bearer <access_token>` header다.
- Access token은 browser persistence가 아니라 React memory state에만 있어야 한다.
- Starter heartbeat는 accepted bucket freshness/application metric state와 별도 control-plane axis다.

### Suggested Static Review Commands

```bash
rg -n "localStorage|sessionStorage|document.cookie|accessToken|refreshToken|provider token|starterCredential|displayValue|projectKeyHash" frontend/src
rg -n "sort\\(|reduce\\(|percentile|p95|p99|endpointPriority|state.code|captureReason|resolvedAt" frontend/src/app/lib frontend/src/app/components
rg -n "NextResponse|next/server|app/api|pages/api|react-router-dom" frontend observability-portal
rg -n "/api/ingest/v1/buckets|/api/ingest/v1/heartbeat|ingest/v1" frontend/src
```

정적 검색 결과는 0건만을 기대하지 않는다. 예를 들어 `accessToken`은 DTO/auth memory state field명으로, `displayValue`는 one-time credential DTO와 display surface로 남을 수 있다. 구현자는 match를 storage/DOM/log/error 노출 또는 client-side recompute인지 수기 분류해 기록해야 한다.

## Verification

### Required Commands And Expected Results

```bash
git status --short
```

기대 결과: 기존 unrelated/untracked 항목과 10.7 변경이 분리되어 기록된다.

```bash
find observability-portal/src/main/java -name '* 2.java' -print
```

기대 결과: 빈 출력.

```bash
cd frontend && npm run typecheck
```

기대 결과: TypeScript build-mode typecheck 성공.

```bash
cd frontend && npm run build
```

기대 결과: Vite production build 성공. `frontend/dist`는 생성될 수 있지만 source control 대상이 아니다.

```bash
./gradlew :observability-portal:bootJar --rerun-tasks
```

기대 결과: `npm ci`, `npm run build`, `processResources`, `bootJar` task chain 성공.

```bash
jar tf observability-portal/build/libs/observability-portal-0.1.0-SNAPSHOT.jar | rg '^BOOT-INF/classes/static/index\.html$'
jar tf observability-portal/build/libs/observability-portal-0.1.0-SNAPSHOT.jar | rg '^BOOT-INF/classes/static/assets/.+\.js$'
jar tf observability-portal/build/libs/observability-portal-0.1.0-SNAPSHOT.jar | rg '^BOOT-INF/classes/static/assets/.+\.css$'
jar tf observability-portal/build/libs/observability-portal-0.1.0-SNAPSHOT.jar | rg '^BOOT-INF/classes/static/dashboard(/|$)'
```

기대 결과: index, JS, CSS 검색은 match가 있고 dashboard 검색은 match가 없다.

```bash
rg -n "figma:asset|figmaAssetResolver|ImageWithFallback|pnpm" frontend observability-portal
rg -n "figma:asset|figmaAssetResolver|ImageWithFallback|pnpm" frontend observability-portal -g '!frontend/node_modules/**'
```

기대 결과: 첫 명령에 node_modules noise가 있으면 기록한다. source-only 명령은 runtime/source residue가 없음을 보여야 한다.

```bash
git diff --check
git diff --name-only
```

기대 결과: whitespace error가 없고 변경 파일은 10.7 story/traceability/test 기록 범위로 제한된다.

### Manual QA Expected Results

- Spring-served origin에서 `/`, `/dashboard`, `/dashboard/`, `/docs`, `/docs/`가 새 SPA로 수렴한다.
- `/api/projects`는 SPA HTML이 아니라 401 또는 API JSON semantics로 수렴한다.
- 실제 jar asset `/assets/*.js`, `/assets/*.css`는 JS/CSS로 반환된다.
- 없는 asset, `/dashboard/app.js`, `/dashboard/styles.css`는 SPA HTML로 forward되지 않는다.
- GitHub OAuth popup/relay/token exchange가 Spring-served origin에서 동작한다.
- token은 URL/cookie/`localStorage`/`sessionStorage`에 저장되지 않는다.
- resource API 요청은 Bearer header를 사용한다.
- 401은 auth-required/stale UI로 수렴하고 health/absence로 오인되지 않는다.
- Project/Application/Dashboard/Evidence/Trend/History/Credential flow는 기존 API surface만 호출한다.
- create/rotation raw credential은 한 번만 표시되고 copy/close 뒤 UI state에서 제거된다.
- no-recompute, two-axis freshness/heartbeat, zeroInsight, endpoint allow-list 결과가 기록된다.

## Open Risks / Blockers

1. Local DB 또는 Spring profile이 준비되지 않으면 Spring-served origin 기동과 `/api/projects`/OAuth/resource API 수동 QA가 막힐 수 있다. 이 경우 DB/profile blocker로 기록하고 pass로 가장하지 않는다.
2. GitHub OAuth 실제 계정, callback URL, client secret, redirect origin 환경이 없으면 popup/relay/token exchange 전체 검증이 막힐 수 있다. 이 경우 수행 가능한 authorize/callback 전 단계와 막힌 지점을 분리해 기록한다.
3. `rg -n "figma:asset|figmaAssetResolver|ImageWithFallback|pnpm" frontend observability-portal`는 `frontend/node_modules`가 있으면 pnpm 문자열 noise를 만들 수 있다. 반드시 source-only 재검증을 남긴다.
4. Manual QA는 브라우저 상태, token 만료, local seed data, network timing에 따라 재현성이 흔들릴 수 있다. 실행 origin, 계정, seed/project/application, 대략적 시간, 실패 재현 조건을 기록한다.
5. Credential create/rotation은 실제 project/account 권한과 secret lifecycle을 요구한다. 실패 시 endpoint/권한/environment blocker인지 frontend acceptance failure인지 구분한다.
6. OAuth와 credential 검증 중 secret을 screenshot, log, traceability 문서에 그대로 남기지 않는다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-06-02: BMAD dev-story workflow customization, global config, persistent project context를 확인했다.
- 2026-06-02: 요청된 필수 파일 19개를 읽고 Story 10.7을 검증/traceability story로만 수행하기로 경계를 확정했다.
- 2026-06-02: `implementation-artifacts/sprint-status.yaml`에서 `10-7-run-acceptance-gate`를 `in-progress`로 전환했다.
- 2026-06-02: Pre-flight `git status --short` 결과, 현재 10.7 변경 후보는 `implementation-artifacts/sprint-status.yaml`과 untracked `planning-artifacts/stories/10-7-run-acceptance-gate.md`다.
- 2026-06-02: 기존 unrelated/untracked 항목으로 `=`, root `README.md`, root `docs/`, `planning-artifacts/figma-make-nextjs-frontend-spec.md`, untracked `planning-artifacts/stories/10-4-wire-evidence-trend-and-credential-surfaces.md`를 확인했고 수정/이동/삭제하지 않는다.
- 2026-06-02: `find observability-portal/src/main/java -name '* 2.java' -print` 결과가 비어 있어 duplicate Java blocker 재발은 없다.
- 2026-06-02: `cd frontend && npm run typecheck` 성공.
- 2026-06-02: `cd frontend && npm run build` 성공. Vite output은 `dist/index.html`, `dist/assets/index-132Tghla.js`, `dist/assets/index-C6P2CycU.css`다.
- 2026-06-02: `./gradlew :observability-portal:bootJar --rerun-tasks` 성공. `compileJava`, `npmInstall`(`npm ci`), `frontendBuild`, `processResources`, `bootJar`가 실행됐다. `recharts@2.15.2` deprecated warning, npm audit high severity 1건은 보고됐지만 acceptance blocker는 아니며 dependency upgrade는 범위 밖이다.
- 2026-06-02: jar contents에서 `BOOT-INF/classes/static/index.html`, `BOOT-INF/classes/static/assets/index-132Tghla.js`, `BOOT-INF/classes/static/assets/index-C6P2CycU.css`를 확인했다.
- 2026-06-02: jar contents에서 `BOOT-INF/classes/static/dashboard` match가 없음을 확인했다.
- 2026-06-02: `rg -n "figma:asset|figmaAssetResolver|ImageWithFallback|pnpm" frontend observability-portal` 결과는 match 없음이다. 추가 source-only `-g '!frontend/node_modules/**'` 검색도 match 없음이다.
- 2026-06-02: local acceptance용 PostgreSQL 컨테이너를 15432에 띄우고 built jar를 `http://127.0.0.1:8080` Spring-served origin으로 기동했다. Flyway V001~V012 migration 적용과 Spring startup이 성공했다.
- 2026-06-02: HTTP 검증에서 `/`, `/dashboard`, `/dashboard/`, `/docs`, `/docs/`는 `200 text/html` SPA shell로 수렴했고, 실제 `/assets/index-132Tghla.js`는 `200 text/javascript`, `/assets/index-C6P2CycU.css`는 `200 text/css`로 반환됐다.
- 2026-06-02: HTTP 검증에서 `/api/projects`는 unauthenticated `401 application/json`으로 반환되어 SPA HTML shadow가 없고, `/assets/not-found.js`, `/dashboard/app.js`, `/dashboard/styles.css`는 `404 application/json`으로 반환되어 HTML fallback shadow가 없다.
- 2026-06-02: Chrome 자동화로 `/`, `/dashboard`, `/dashboard/`, `/docs`, `/docs/` 직접 진입과 reload를 확인했다. 각 route는 `Observation Portal` title, 새 SPA root text, jar asset JS/CSS를 렌더링했고 dashboard/docs route reload도 SPA 상태를 유지했다.
- 2026-06-02: Chrome 초기 storage 검증에서 URL, cookie, `localStorage`, `sessionStorage`가 비어 있었고 `/api/projects` 직접 fetch는 `401 application/json`으로 수렴했다.
- 2026-06-02: `GET /api/auth/github/authorize`는 `200 application/json`, `Cache-Control: no-store`, provider `github`, GitHub authorization URL present로 확인했다. Login button click은 GitHub sign-in popup까지 열렸다.
- 2026-06-02: GitHub OAuth callback relay와 `POST /api/auth/github/callback/tokens` 성공 exchange는 headless browser에 interactive GitHub login session이 없어 완료하지 못했다. 이 지점은 environment blocker로 기록하고 pass로 가장하지 않는다.
- 2026-06-02: 기존 React memory auth bridge에 fake invalid in-memory token을 넣어 `GET /api/projects` runtime request가 `Authorization: Bearer <test-token>` header를 사용함을 확인했다. backend `401` 후 UI는 "인증이 만료되었습니다"와 auth-required state로 수렴했고 storage/cookie는 비어 있었다.
- 2026-06-02: Local-only active account row와 service JWT를 사용해 Project registration UI를 실제 실행했다. `POST /api/projects`는 `201`, `Cache-Control: no-store`, `starterCredential.displayValue` 1회 표시 field present로 응답했고 raw 값은 기록하지 않았다.
- 2026-06-02: Project create one-time credential panel은 성공 직후 표시됐고 close 뒤 panel/code가 제거됐다. close 뒤 URL, cookie, `localStorage`, `sessionStorage`는 비어 있었다.
- 2026-06-02: Local DB에 acceptance application/instance row를 seed하고 Chrome에서 `GET /api/projects` -> selected project `links.applications` -> selected application `links.dashboard` chain을 확인했다. 모든 resource request는 Bearer header를 사용했다.
- 2026-06-02: Dashboard JSON은 `state.code=waiting_first_data`, `starterConnection.statusSource=starter_heartbeat`, `starterConnection.stateImpact=none`, `sourceScopedPercentiles.aggregatePolicy=no_average_no_max_no_merge_no_histogram_recalculation`, `triageCards.length=0`, `zeroInsight.reasonCode=telemetry_unreachable`와 message/recommendedAction present를 반환했다.
- 2026-06-02: Evidence JSON은 clicked instance `links.evidence`에서 `metricData.statusSource=accepted_bucket`, `starterConnection.statusSource=starter_heartbeat`, `starterConnection.stateImpact=none`, starter percentile aggregate policy `no_average_no_max_no_merge_no_histogram_recalculation`, `links.snapshotTrend` present를 반환했다.
- 2026-06-02: Trend request는 `/snapshot-trend?since=7d&limit=168`로 호출됐고 response source는 `dashboard_snapshots.read_model_json.instanceSummary.items`, order는 `capturedAt_asc`, points는 0건이었다. 이는 empty projection으로 기록하며 current state 재계산으로 해석하지 않는다.
- 2026-06-02: Credential metadata/rotation/revocation UI를 실행했다. Metadata와 revocation response는 `displayValue`/hash field 없이 `keyPrefix/status/issuedAt/rotatedAt/revokedAt` 계열 field만 반환했고, rotation response만 `displayValue` 1회 표시 field를 반환했다.
- 2026-06-02: Rotation one-time credential panel은 표시 후 close로 제거됐고 raw credential code가 DOM에서 사라졌으며 URL, cookie, `localStorage`, `sessionStorage`는 비어 있었다.
- 2026-06-02: Network allow-list에서 runtime resource calls는 `/api/projects`, `links.applications`, `links.dashboard`, `instances[].links.evidence`, `/snapshot-trend?since=7d&limit=168`, snapshot markers/history `24h`, project registration, starter credential metadata/rotation/revocation으로 제한됐다. `/api/ingest/v1/buckets`와 `/api/ingest/v1/heartbeat` 호출은 없었다.
- 2026-06-02: Static auth/security grep 결과 `accessToken`은 React memory/ref와 Bearer header helper, `displayValue`는 DTO/one-time panel, `document.cookie`는 sidebar UI preference cookie에 한정된다. token/credential storage persistence match는 없다.
- 2026-06-02: Static recompute grep 결과 `sort(`/`reduce(` match는 없고, `p95`/`p99`/`endpointPriority`/`captureReason`/`resolvedAt` match는 DTO field 또는 server-provided value display에 한정된다. source-only Next.js/API route/router drift grep은 match 없음이다.
- 2026-06-02: Static ingest grep 결과 `/api/ingest/v1/*`는 `frontend/src/app/components/docs.tsx` setup/API reference 안내에만 남아 있고 runtime dashboard fetch source에는 없다.
- 2026-06-02: `planning-artifacts/acceptance-traceability.md`에 Epic 10 acceptance evidence 섹션만 좁게 추가했다. 기존 MVC matrix는 재구성하지 않았다.
- 2026-06-02: `git diff --name-only` tracked diff는 `implementation-artifacts/sprint-status.yaml`, `planning-artifacts/acceptance-traceability.md`다. Story 10.7 파일은 현재 untracked story 산출물로 `git status --short`에 표시된다.
- 2026-06-02: `git diff --check` 성공.
- 2026-06-02: 새 backend endpoint/controller/service/repository/migration, Next.js route, frontend auth/API/read-model behavior 변경, token/credential storage 변경, lifecycle/p95/p99/endpoint priority/snapshot/history 계산 변경은 없다.
- 2026-06-02: `./gradlew :observability-portal:test` 성공. 모든 관련 task는 up-to-date였고 regression failure는 없다.

### Completion Notes List

- Pre-flight workspace 상태를 기록했고 기존 unrelated/untracked 항목을 10.7 변경과 분리했다.
- `* 2.java` duplicate Java blocker는 재발하지 않았다.
- Frontend typecheck/build와 Gradle `bootJar --rerun-tasks` command gate가 통과했다.
- jar static root에 새 SPA index, generated JS/CSS asset이 포함되고 legacy `static/dashboard`는 포함되지 않음을 확인했다.
- Figma asset resolver, `ImageWithFallback`, pnpm residue 검색은 runtime/source 모두 match 없음이다.
- Spring-served route/static QA는 통과했다. `/api/projects`, missing asset, deleted legacy dashboard asset은 SPA HTML로 shadow되지 않는다.
- GitHub OAuth authorize와 popup open은 확인했다. Callback relay/token exchange 성공은 interactive GitHub login session 부재로 완료하지 못했으며 environment blocker로 남긴다.
- Resource API Bearer header와 401 token clear/auth-required UI는 runtime에서 확인했다.
- Project registration, Project/Application/Dashboard, Instance Evidence, Snapshot Trend, Snapshot/History, Credential lifecycle은 Spring origin에서 기존 endpoint surface만 호출했다.
- accepted bucket metric axis와 starter heartbeat/connection axis는 화면과 JSON에서 별도 field/section으로 확인됐다. 최근 heartbeat/없는 bucket 조합을 host application down으로 단정하는 copy는 보이지 않았다.
- `triageCards=[]` dashboard response는 `zeroInsight` reason/action을 포함했고 UI에 zero-insight guidance가 표시됐다.
- Project create와 credential rotation의 raw `displayValue`는 성공 직후 1회 panel에만 표시됐고 close 뒤 DOM/UI state와 browser storage에서 제거됐다.
- Credential metadata/revocation response에는 raw value/hash field가 없다.
- Runtime dashboard UI는 `/api/ingest/v1/buckets` 또는 `/api/ingest/v1/heartbeat`를 호출하지 않았다.
- Open blocker: 실제 GitHub 로그인 완료, callback relay `postMessage`/meta fallback, `POST /api/auth/github/callback/tokens` 성공 exchange는 headless test browser의 GitHub login session 부재로 끝까지 확인하지 못했다.
- `planning-artifacts/acceptance-traceability.md`에는 Epic 10 acceptance evidence만 추가했고 기존 traceability matrix는 유지했다.
- Scope guard 결과 source/product code 변경은 없다. 변경은 sprint tracking, Story 10.7 record, acceptance traceability 기록에 한정된다.
- Full portal regression test task `:observability-portal:test`가 통과했다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/acceptance-traceability.md`
- `planning-artifacts/stories/10-7-run-acceptance-gate.md`

### Change Log

- 2026-06-02: Story 10.7 create-story 산출물을 생성했다. Epic 10 acceptance gate, command/jar/route/auth/security/no-recompute/credential/network/traceability 검증 범위를 ready-for-dev 기준으로 확정했다.
- 2026-06-02: Story 10.7 acceptance gate 실행을 시작하고 pre-flight workspace 상태를 기록했다.
- 2026-06-02: Epic 10 acceptance gate command/manual/browser/static 검증을 실행하고 traceability evidence를 기록한 뒤 review 상태로 전환했다.
