---
artifactType: story
storyId: "10.6"
storyKey: "10-6-replace-legacy-dashboard-and-clean-residue"
epic: "Epic 10. Figma Make Acceptance And Frontend Hardening"
title: "Replace legacy dashboard and clean residue"
architectureStyle: "Vite SPA on Spring static root"
status: done
date: 2026-06-02
baseline_commit: "9c979f5"
baselineCommits:
  story10_1: "cc7d87a frontend: adopt figma workspace and routing"
  story10_2: "7850d88 frontend: port auth and fetch foundation"
  story10_3: "e5b3ffa frontend: wire story 10.3 read models"
  story10_4: "992af31 frontend: wire story 10.4 surfaces"
  story10_5: "9c979f5 portal: build vite spa with gradle"
commitBoundary: "portal: replace legacy static dashboard"
---

# Story 10.6 - Replace legacy dashboard and clean residue

## Status

done

이 문서는 legacy static dashboard replacement와 Figma residue cleanup을 위한 BMAD create-story 산출물이다. 구현과 검증이 완료됐다.

## Story

Figma Make 인수 프론트 구현자는 Story 10.5에서 Gradle `bootJar` 안에 포함된 새 Vite SPA를 Spring static root의 유일한 dashboard UI로 사용하고 싶다.

그래야 `/`, `/dashboard`, `/docs` 직접 진입과 새로고침이 새 SPA로 안정적으로 수렴하고, 기존 `static/dashboard/*`와 Figma-only import/guideline/build residue가 남아 후속 acceptance 검증을 흐리지 않는다.

## Source of Truth

아래 문서와 파일을 읽고 반영한 create-story context다. 충돌처럼 보이는 지점은 Story 10.5 완료 상태와 Epic 10 sprint plan의 locked decision을 우선한다.

1. `AGENTS.md`
2. `_bmad/custom/project-context.md`
3. `implementation-artifacts/sprint-status.yaml`
4. `planning-artifacts/figma-make-acceptance-sprint-plan.md`
5. `planning-artifacts/stories/10-1-adopt-workspace-and-routing.md`
6. `planning-artifacts/stories/10-2-port-auth-and-fetch-foundation.md`
7. `planning-artifacts/stories/10-3-wire-types-adapters-navigation-and-dashboard.md`
8. `planning-artifacts/stories/10-4-wire-evidence-trend-and-credential-surfaces.md`
9. `planning-artifacts/stories/10-5-integrate-frontend-build-with-gradle.md`
10. `planning-artifacts/acceptance-traceability.md`
11. `planning-artifacts/architecture.md`
12. `planning-artifacts/project-structure.md`
13. `observability-portal/src/main/java/com/observation/portal/config/DashboardStaticWebConfig.java`
14. `observability-portal/src/main/resources/static/dashboard/index.html`
15. `observability-portal/src/main/resources/static/dashboard/app.js`
16. `observability-portal/src/main/resources/static/dashboard/styles.css`
17. `frontend/vite.config.ts`
18. `frontend/src/app/App.tsx`
19. `frontend/src/app/components/figma/ImageWithFallback.tsx`
20. `frontend/src/imports/figma-make-nextjs-frontend-spec.md`
21. `frontend/guidelines/Guidelines.md`

## Current Code State

- Story 10.5는 완료됐고 최신 관련 커밋은 `9c979f5 portal: build vite spa with gradle`이다.
- `./gradlew :observability-portal:bootJar --rerun-tasks`가 통과했고 jar에는 새 SPA 산출물 `BOOT-INF/classes/static/index.html`, `BOOT-INF/classes/static/assets/index-Bjvo8HCE.js`, `BOOT-INF/classes/static/assets/index-afIJg7Nf.css`가 포함됐다.
- 기존 `* 2.java` duplicate Java blocker는 해결됐다. 현재 `find observability-portal/src/main/java -name '* 2.java' -print` 결과는 비어 있다.
- `observability-portal/build.gradle`은 node-gradle plugin을 사용하고 `nodeProjectDir = file("$rootDir/frontend")`, `npmInstallCommand = 'ci'`, `frontendBuild`, `processResources`의 `frontend/dist -> static/` copy를 갖는다. Story 10.6은 이 build integration을 보존한다.
- `DashboardStaticWebConfig.java`는 현재 `/dashboard -> /dashboard/` redirect와 `/dashboard/ -> forward:/dashboard/index.html` legacy forward만 등록한다.
- legacy static dashboard 파일은 아직 존재한다.
  - `observability-portal/src/main/resources/static/dashboard/index.html`
  - `observability-portal/src/main/resources/static/dashboard/app.js`
  - `observability-portal/src/main/resources/static/dashboard/styles.css`
- `frontend/src/app/App.tsx`는 `BrowserRouter` 안에 `/`, `/dashboard`, `/docs` route를 둔다.
- `frontend/vite.config.ts`는 아직 `figmaAssetResolver()`와 `figma:asset/*` resolver plugin을 갖는다.
- `frontend/src/app/components/figma/ImageWithFallback.tsx`는 자기 파일 외 사용 검색 결과가 없다.
- `frontend/src/imports/figma-make-nextjs-frontend-spec.md`와 `frontend/guidelines/Guidelines.md`는 Figma Make import/guideline residue이며 build/runtime import source가 아니다.
- `find frontend -maxdepth 3 -name 'pnpm*' -print`는 현재 빈 결과다. pnpm residue는 실제 파일이 있을 때만 삭제 대상으로 삼는다.
- 현재 workspace에는 root `README.md`, `docs/`, `planning-artifacts/figma-make-nextjs-frontend-spec.md`, `planning-artifacts/stories/10-4-wire-evidence-trend-and-credential-surfaces.md`, `=` 같은 기존 unrelated/untracked 항목이 있을 수 있다. Story 10.6 구현자는 이 항목들을 수정, 이동, 삭제, stage하지 않는다.

## Scope

- 새 Vite SPA 검증 이후 legacy `observability-portal/src/main/resources/static/dashboard/*` 파일을 삭제한다.
- `DashboardStaticWebConfig`의 legacy dashboard forward를 제거하고 새 SPA client route allow-list fallback으로 교체한다.
- `/dashboard`, `/dashboard/`, `/docs`, `/docs/`는 `forward:/index.html`로 보낸다.
- `/`는 Spring Boot static resource handling이 classpath `static/index.html`을 제공하게 둔다.
- `/api/**`, `/assets/**`, 확장자가 있는 static resource request는 SPA fallback HTML로 shadow하지 않는다.
- `figmaAssetResolver()`와 `figma:asset/*` resolver를 `frontend/vite.config.ts`에서 제거한다.
- import 0건이 확인된 `frontend/src/app/components/figma/ImageWithFallback.tsx`와 비어 남는 `frontend/src/app/components/figma/` 디렉터리를 제거한다.
- Figma-only frontend residue인 `frontend/src/imports/figma-make-nextjs-frontend-spec.md`, `frontend/src/imports/` 빈 디렉터리, `frontend/guidelines/Guidelines.md`, `frontend/guidelines/` 빈 디렉터리를 제거한다.
- pnpm residue는 실제 파일이 존재하고 npm-only Story 10.1/10.5 기준과 충돌할 때만 제거한다. 현재 확인된 `pnpm-lock.yaml` 또는 `pnpm-workspace.yaml`는 없다.
- Story 10.5의 Gradle build integration, npm `ci`, Vite SPA root asset copy는 유지한다.

## Non-scope / Guardrails

- 새 backend endpoint, controller, service, repository, migration을 만들지 않는다.
- `BearerResourceApiWebConfig`, resource API Bearer/membership interceptor, account auth backend를 변경하지 않는다.
- frontend auth/API/read-model behavior를 변경하지 않는다.
- token, provider payload, starter credential secret을 URL, cookie, `localStorage`, `sessionStorage`, DOM attribute, log, error에 노출하는 코드를 추가하지 않는다.
- UI-side lifecycle state, starter connection diagnosis, zeroInsight reason, recovery guidance, p95/p99, endpoint priority, snapshot/history event 계산을 추가하지 않는다.
- Story 10.7 acceptance gate 전체 수동 QA를 이 story 완료 조건으로 끌어오지 않는다. 10.6은 route/static replacement와 residue cleanup까지만 닫는다.
- root `README.md`, root `docs/`, `planning-artifacts/figma-make-nextjs-frontend-spec.md` 같은 기존 unrelated/untracked 항목은 건드리지 않는다.
- `frontend/node_modules/`, `frontend/dist/`, Gradle build output은 source control 대상으로 추가하지 않는다.

## Acceptance Criteria

1. 구현 착수 전 `git status --short`와 `find observability-portal/src/main/java -name '* 2.java' -print`를 확인하고 기존 unrelated/untracked 항목과 duplicate Java blocker 상태를 기록한다.
2. duplicate Java blocker 확인 결과는 비어 있어야 한다. 비어 있지 않으면 Story 10.6 구현 변경과 섞지 말고 blocker로 기록한다.
3. legacy static dashboard 삭제는 새 SPA가 Story 10.5 Gradle build integration으로 jar static root에 들어가는 것이 확인된 뒤 수행한다.
4. 아래 legacy dashboard 파일을 삭제한다.
   - `observability-portal/src/main/resources/static/dashboard/index.html`
   - `observability-portal/src/main/resources/static/dashboard/app.js`
   - `observability-portal/src/main/resources/static/dashboard/styles.css`
5. `observability-portal/src/main/resources/static/dashboard/` 디렉터리는 비어 있으면 제거한다.
6. `DashboardStaticWebConfig`는 더 이상 `/dashboard/`를 `forward:/dashboard/index.html`로 보내지 않는다.
7. `DashboardStaticWebConfig`는 `/dashboard`, `/dashboard/`, `/docs`, `/docs/`를 `forward:/index.html`로 보내는 allow-list fallback만 등록한다.
8. `/dashboard -> /dashboard/` redirect는 제거한다. `/dashboard`와 `/dashboard/` 모두 새 SPA index를 직접 forward해야 direct entry/refresh 검증이 같은 기준으로 수렴한다.
9. `/`는 별도 MVC fallback을 만들지 않고 Spring static root의 `index.html`이 제공해야 한다.
10. fallback 구현은 broad catch-all을 쓰지 않는다. 특히 `/**`, `/{path:[^\\.]*}`, `/**/{path:[^\\.]*}` 같은 광범위 SPA fallback은 사용하지 않는다.
11. `/api/**`는 fallback HTML로 shadow되지 않는다. `GET /api/projects`는 인증 없음/만료 시 SPA HTML이 아니라 인증/API 응답으로 수렴해야 한다.
12. `/assets/**`는 fallback HTML로 shadow되지 않는다. Vite JS/CSS asset request는 실제 static asset 또는 정적 resource 404로 수렴해야 하며 `text/html` SPA index를 반환하지 않는다.
13. 확장자가 있는 resource path, 예: `/favicon.ico`, `/assets/index-*.js`, `/assets/index-*.css`, `/dashboard/app.js`, `/dashboard/styles.css`는 fallback HTML로 shadow되지 않는다.
14. `/`, `/dashboard`, `/dashboard/`, `/docs`, `/docs/` 직접 진입과 새로고침이 새 Vite SPA를 로드한다.
15. jar 내부에는 legacy dashboard 파일이 없어야 한다. `BOOT-INF/classes/static/dashboard` match가 없어야 한다.
16. jar 내부에는 새 SPA root와 hashed asset이 있어야 한다. `BOOT-INF/classes/static/index.html`과 `BOOT-INF/classes/static/assets/*` JS/CSS가 확인돼야 한다.
17. `frontend/vite.config.ts`에서 `figmaAssetResolver()` 함수, plugin 등록, `figma:asset/` resolver 로직을 제거한다.
18. Vite `base: '/'`, React plugin, Tailwind plugin, `@` alias, `assetsInclude` 중 현재 build에 필요한 설정은 유지한다.
19. `frontend/src/app/components/figma/ImageWithFallback.tsx`는 import 0건을 확인한 뒤 삭제한다. 삭제 후 빈 `components/figma` 디렉터리도 제거한다.
20. `frontend/src/imports/figma-make-nextjs-frontend-spec.md`와 `frontend/guidelines/Guidelines.md`는 frontend 내부 Figma-only residue로 확인한 뒤 삭제한다. 삭제 후 빈 디렉터리도 제거한다.
21. pnpm residue는 실제 파일이 존재할 때만 삭제한다. `frontend/package-lock.json`과 `npm ci` 기반 Gradle integration은 유지한다.
22. residue 검증은 아래 검색 기준으로 수행한다.
    - `rg -n "figma:asset|figmaAssetResolver|ImageWithFallback|pnpm" frontend observability-portal`
23. 로컬 `frontend/node_modules`가 검색 noise를 만들면, 원 명령 결과를 기록한 뒤 source 기준 재검증으로 `rg -n "figma:asset|figmaAssetResolver|ImageWithFallback|pnpm" frontend observability-portal -g '!frontend/node_modules/**'`도 실행한다. 추적 소스 기준으로는 match가 없어야 한다.
24. `cd frontend && npm run typecheck`가 통과한다.
25. `cd frontend && npm run build`가 통과한다.
26. `./gradlew :observability-portal:bootJar --rerun-tasks`가 통과하고 `npm ci` integration이 lockfile churn을 만들지 않는다.
27. `git diff --check`가 통과한다.
28. 구현 diff에 새 backend API, auth/token storage, frontend read-model 계산, endpoint priority/history calculation 변경이 포함되지 않았음을 scope guard로 확인한다.

## Tasks / Subtasks

- [x] Pre-flight 상태 확인 (AC: 1, 2)
  - [x] `git status --short`로 기존 unrelated/untracked 항목과 구현 대상 파일을 분리해 기록한다.
  - [x] `find observability-portal/src/main/java -name '* 2.java' -print`가 빈 결과인지 확인한다.
  - [x] Story 10.5 Gradle integration 파일이 변경 대상에 섞이지 않았는지 확인한다.

- [x] Legacy static dashboard 삭제 (AC: 3~5, 15)
  - [x] `observability-portal/src/main/resources/static/dashboard/index.html` 삭제.
  - [x] `observability-portal/src/main/resources/static/dashboard/app.js` 삭제.
  - [x] `observability-portal/src/main/resources/static/dashboard/styles.css` 삭제.
  - [x] `static/dashboard/`가 비면 디렉터리까지 제거한다.
  - [x] 삭제 후 `rg -n "static/dashboard|dashboard/index.html" observability-portal/src/main/java observability-portal/src/main/resources`로 남은 legacy forward/reference를 점검한다.

- [x] Spring MVC SPA fallback allow-list 적용 (AC: 6~14)
  - [x] `DashboardStaticWebConfig`의 역할 주석을 새 SPA fallback 역할에 맞춰 한국어로 갱신한다.
  - [x] `/dashboard`, `/dashboard/`, `/docs`, `/docs/`에만 `forward:/index.html` view controller를 등록한다.
  - [x] `/dashboard -> /dashboard/` redirect와 `/dashboard/index.html` forward를 제거한다.
  - [x] broad catch-all fallback이 들어가지 않았는지 코드 리뷰한다.
  - [x] `/api/**`, `/assets/**`, 확장자 path를 fallback 대상으로 추가하지 않았는지 확인한다.

- [x] Figma/Vite residue cleanup (AC: 17~23)
  - [x] `frontend/vite.config.ts`에서 `figmaAssetResolver()`와 `figma:asset/` resolver plugin을 제거한다.
  - [x] `react()`와 `tailwindcss()` plugin, `base: '/'`, `@` alias, 필요한 `assetsInclude`는 유지한다.
  - [x] `ImageWithFallback` import 사용이 자기 파일 외 0건임을 확인하고 파일을 삭제한다.
  - [x] `frontend/src/app/components/figma/`가 비면 제거한다.
  - [x] `frontend/src/imports/figma-make-nextjs-frontend-spec.md`와 빈 `frontend/src/imports/`를 제거한다.
  - [x] `frontend/guidelines/Guidelines.md`와 빈 `frontend/guidelines/`를 제거한다.
  - [x] 실제 pnpm residue가 생겨 있으면 npm-only 기준으로만 제거하고, `package-lock.json`은 유지한다.

- [x] Build, jar, route verification 수행 (AC: 14~16, 24~27)
  - [x] `cd frontend && npm run typecheck`
  - [x] `cd frontend && npm run build`
  - [x] `./gradlew :observability-portal:bootJar --rerun-tasks`
  - [x] jar 내부 새 SPA root/assets 포함 여부 확인.
  - [x] jar 내부 legacy dashboard 부재 확인.
  - [x] Spring MVC route policy test로 `/`, `/dashboard`, `/dashboard/`, `/docs`, `/docs/` fallback 정책을 확인한다.
  - [x] `/api/projects`와 `/assets/*.js`, `/assets/*.css`가 fallback HTML로 shadow되지 않는지 확인한다.

- [x] Scope guard와 기록 정리 (AC: 28)
  - [x] `git diff --check`
  - [x] `git diff --name-only`로 변경 파일이 Story 10.6 범위인지 확인한다.
  - [x] auth/API/read-model behavior 변경이 없음을 Dev Agent Record에 기록한다.
  - [x] root `README.md`, `docs/`, root planning spec 같은 unrelated/untracked 항목을 건드리지 않았음을 기록한다.

## Dev Notes

### Architecture Context

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- Story 10.6은 static resource delivery와 frontend residue cleanup story다. MVC controller/service/repository/persistence behavior를 추가하지 않는다.
- Dashboard UI는 server read model을 표시하는 계층이다. 이번 story는 UI behavior를 바꾸지 않고 delivery path와 obsolete files만 정리한다.
- `AGENTS.md` 기준으로 `DashboardStaticWebConfig` 주석을 갱신할 경우 한국어 Javadoc으로 역할과 fallback 정책을 짧게 설명한다.

### Recommended Spring Shape

`DashboardStaticWebConfig`는 정확한 route allow-list만 등록하는 단순 config로 유지한다.

```java
registry.addViewController("/dashboard").setViewName("forward:/index.html");
registry.addViewController("/dashboard/").setViewName("forward:/index.html");
registry.addViewController("/docs").setViewName("forward:/index.html");
registry.addViewController("/docs/").setViewName("forward:/index.html");
```

위 예시는 구현 방향을 설명하기 위한 것이다. 핵심은 `/dashboard/index.html` legacy forward 제거, broad fallback 금지, `/api/**`/asset/resource shadow 방지다.

### Previous Story Intelligence

- Story 10.1은 `frontend/`를 npm-only Vite SPA로 인수하고 root `BrowserRouter`로 `/`, `/dashboard`, `/docs`를 만들었다.
- Story 10.2는 GitHub OAuth popup relay, in-memory access token, Bearer `authFetch`, 401 token clear, request sequence guard를 React 구조로 이식했다.
- Story 10.3은 Project/Application/Dashboard를 server-provided `links.*` 기반 read model로 연결하고 mock seed를 제거했다.
- Story 10.4는 Instance Evidence, Trend, Snapshot/History, Project Registration, Credential Lifecycle을 기존 endpoint로 연결했다.
- Story 10.5는 Gradle node-gradle integration을 닫고 `bootJar --rerun-tasks`와 jar content 검증을 통과했다. Story 10.6은 이 integration을 유지해야 한다.
- 기존 `app.js`는 앞선 story들의 parity source였고 더 이상 runtime source가 아니다. Story 10.6에서 삭제하되, auth/fetch/read-model behavior를 다시 이식하거나 수정하지 않는다.

### File Candidates

수정/삭제 후보:

- `observability-portal/src/main/java/com/observation/portal/config/DashboardStaticWebConfig.java`
- `observability-portal/src/main/resources/static/dashboard/index.html` 삭제
- `observability-portal/src/main/resources/static/dashboard/app.js` 삭제
- `observability-portal/src/main/resources/static/dashboard/styles.css` 삭제
- `frontend/vite.config.ts`
- `frontend/src/app/components/figma/ImageWithFallback.tsx` 삭제
- `frontend/src/app/components/figma/` 빈 디렉터리 삭제
- `frontend/src/imports/figma-make-nextjs-frontend-spec.md` 삭제
- `frontend/src/imports/` 빈 디렉터리 삭제
- `frontend/guidelines/Guidelines.md` 삭제
- `frontend/guidelines/` 빈 디렉터리 삭제

수정하지 않을 후보:

- `observability-portal/build.gradle`, root `build.gradle`의 Story 10.5 build integration
- `observability-portal/src/main/java/com/observation/portal/domain/**`
- `observability-portal/src/main/resources/db/migration/**`
- `frontend/src/app/lib/auth.tsx`
- `frontend/src/app/lib/api.ts`
- `frontend/src/app/lib/read-model-types.ts`
- `frontend/src/app/lib/read-model-adapters.ts`
- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/components/instance-panels.tsx`
- root `README.md`
- root `docs/`
- `planning-artifacts/figma-make-nextjs-frontend-spec.md`

### Route Verification Hints

- Spring-served origin에서 검증한다. Vite dev server만으로는 Spring MVC fallback과 `/api/**` shadow 여부를 검증할 수 없다.
- `/api/projects`는 인증 token이 없으면 대체로 401/JSON 또는 API error semantics로 수렴해야 한다. 응답 `Content-Type`이 `text/html`이고 SPA shell이 내려오면 fallback이 너무 넓다.
- `/assets/index-*.js`와 `/assets/index-*.css`는 jar에 들어간 실제 파일명으로 요청한다. 누락 asset의 404도 fallback HTML이면 안 된다.
- 삭제된 `/dashboard/app.js`, `/dashboard/styles.css` 요청은 static 404가 자연스럽다. SPA HTML이 내려오면 확장자 path shadow 방지 AC를 위반한 것이다.

## Verification

필수 command:

```bash
git status --short
find observability-portal/src/main/java -name '* 2.java' -print
cd frontend && npm run typecheck
cd frontend && npm run build
./gradlew :observability-portal:bootJar --rerun-tasks
jar tf observability-portal/build/libs/observability-portal-0.1.0-SNAPSHOT.jar | rg 'BOOT-INF/classes/static/(index\.html|assets/.+)'
jar tf observability-portal/build/libs/observability-portal-0.1.0-SNAPSHOT.jar | rg 'BOOT-INF/classes/static/dashboard'
rg -n "figma:asset|figmaAssetResolver|ImageWithFallback|pnpm" frontend observability-portal
git diff --check
```

기대 결과:

- `find ... '* 2.java'`는 빈 결과다.
- `npm run typecheck`, `npm run build`, `bootJar --rerun-tasks`가 통과한다.
- jar `static/index.html`과 `static/assets/*`는 존재한다.
- jar `BOOT-INF/classes/static/dashboard` 검색은 결과가 없어야 한다.
- residue `rg`는 추적 소스 기준 결과가 없어야 한다. 로컬 `node_modules` noise가 있으면 `-g '!frontend/node_modules/**'`로 source 기준 재검증한다.
- `git diff --check`가 통과한다.

Manual verification, Spring-served origin:

1. `/` 직접 진입과 새로고침이 새 SPA landing을 렌더링한다.
2. `/dashboard` 직접 진입과 새로고침이 새 SPA dashboard route를 렌더링한다.
3. `/dashboard/` 직접 진입과 새로고침이 새 SPA dashboard route로 수렴한다.
4. `/docs` 직접 진입과 새로고침이 새 SPA docs route를 렌더링한다.
5. `/docs/` 직접 진입과 새로고침이 새 SPA docs route로 수렴한다.
6. `/api/projects`가 SPA HTML이 아니라 인증/API 응답을 반환한다.
7. jar에 있는 `/assets/*.js`, `/assets/*.css`가 실제 JS/CSS로 반환된다.
8. 없는 asset 또는 삭제된 `/dashboard/app.js`, `/dashboard/styles.css`가 SPA HTML로 forward되지 않는다.
9. GitHub OAuth, Project/Application/Dashboard, Evidence/Trend/Credential behavior가 Story 10.2~10.4 대비 변경되지 않았음을 smoke 수준으로 확인한다. 전체 Story 10.7 acceptance gate는 후속 story에서 수행한다.

## Open Risks / Blockers

1. `DashboardStaticWebConfig`를 broad SPA fallback으로 바꾸면 `/api/**`와 static asset request를 HTML로 shadow할 수 있다. exact allow-list 방식으로 막아야 한다.
2. `/dashboard/`는 SPA route path와 trailing slash 처리에 민감할 수 있다. direct entry/refresh 검증에서 실제 React Router 렌더를 확인해야 한다.
3. `rg -n "figma:asset|figmaAssetResolver|ImageWithFallback|pnpm" frontend observability-portal`는 로컬 `frontend/node_modules`가 있으면 dependency package의 `pnpm` 문자열을 잡을 수 있다. 추적 소스 기준 결과를 별도로 확인한다.
4. Figma-only 문서 삭제는 `frontend/` 내부 residue에 한정한다. root planning artifacts나 기존 untracked docs를 함께 정리하면 scope가 깨진다.
5. Story 10.5의 `npmInstallCommand = 'ci'`를 건드리면 lockfile churn 또는 Gradle build instability가 재발할 수 있다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-06-02: `git status --short`로 기존 unrelated/untracked `=`, root `README.md`, `docs/`, root planning spec, Story 10.4/10.6 story file을 확인했다.
- 2026-06-02: `find observability-portal/src/main/java -name '* 2.java' -print` 결과가 비어 있음을 확인했다.
- 2026-06-02: legacy static dashboard 파일과 Figma-only frontend residue를 삭제했다.
- 2026-06-02: `DashboardStaticWebConfig`를 `/dashboard`, `/dashboard/`, `/docs`, `/docs/` exact allow-list `forward:/index.html`로 교체했다.
- 2026-06-02: OAuth callback fallback이 삭제된 `/dashboard/index.html` 대신 새 SPA root `/index.html`을 읽도록 조정했다. token relay, storage, response policy는 바꾸지 않았다.
- 2026-06-02: `processResources`가 이전 빌드의 legacy dashboard output을 jar에 다시 넣지 않도록 stale static dashboard output을 삭제한 뒤 frontend `dist`를 복사하게 했다.
- 2026-06-02: legacy static dashboard `app.js`를 직접 실행하던 Java UI contract tests를 제거하고, secret exposure guard는 React dashboard source 기준으로 갱신했다.
- 2026-06-02: `DashboardStaticWebConfigTest`로 exact fallback allow-list, `/api/**`, `/assets/**`, 확장자 path, `/` shadow 방지 정책을 검증했다.
- 2026-06-02: `npm run typecheck`, `npm run build`, `bootJar --rerun-tasks`, jar contents, residue search, `git diff --check`, `:observability-portal:test`를 검증했다.

### Implementation Plan

1. Story 10.6 review findings의 legacy dashboard, fallback, Figma residue gap을 닫는다.
2. 새 SPA root/static asset packaging을 유지하면서 legacy `static/dashboard` jar residue를 제거한다.
3. 삭제된 legacy static UI에 묶인 테스트 residue를 정리하고 fallback 정책 테스트를 추가한다.
4. frontend/Gradle/Java verification으로 Story 10.5 integration과 scope guard를 확인한다.

### Completion Notes List

- legacy `observability-portal/src/main/resources/static/dashboard/*` source와 jar output이 제거됐다.
- `DashboardStaticWebConfig`는 broad catch-all 없이 `/dashboard`, `/dashboard/`, `/docs`, `/docs/`만 SPA index로 forward한다.
- `/`, `/api/**`, `/assets/**`, 확장자 resource path는 MVC SPA fallback에 등록하지 않는다.
- Figma-only `figmaAssetResolver`, `ImageWithFallback`, `frontend/src/imports`, `frontend/guidelines` residue를 제거했다.
- `frontend/package-lock.json` churn은 없다.
- 새 backend endpoint/controller/service/repository/migration은 추가하지 않았다.
- auth/API/read-model 계산, token/credential storage, lifecycle/p95/p99/endpoint priority/snapshot/history 계산은 변경하지 않았다.
- root `README.md`, `docs/`, root planning spec 같은 기존 unrelated/untracked 항목은 건드리지 않았다.
- 로컬 browser 수동 QA는 수행하지 않았고, Spring MVC fallback 정책은 `DashboardStaticWebConfigTest`로 검증했다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/10-6-replace-legacy-dashboard-and-clean-residue.md`
- `frontend/vite.config.ts`
- `frontend/src/app/components/figma/ImageWithFallback.tsx` 삭제
- `frontend/src/imports/figma-make-nextjs-frontend-spec.md` 삭제
- `frontend/guidelines/Guidelines.md` 삭제
- `observability-portal/build.gradle`
- `observability-portal/src/main/java/com/observation/portal/config/DashboardStaticWebConfig.java`
- `observability-portal/src/main/java/com/observation/portal/domain/account/controller/AccountAuthController.java`
- `observability-portal/src/main/resources/static/dashboard/index.html` 삭제
- `observability-portal/src/main/resources/static/dashboard/app.js` 삭제
- `observability-portal/src/main/resources/static/dashboard/styles.css` 삭제
- `observability-portal/src/test/java/com/observation/portal/config/DashboardStaticWebConfigTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/account/controller/AuthSecretExposureGuardTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationDashboardUiContractTest.java` 삭제
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ApplicationListUiContractTest.java` 삭제
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceEvidenceUiContractTest.java` 삭제
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/InstanceSnapshotTrendUiContractTest.java` 삭제
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectEntryUiContractTest.java` 삭제
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/ProjectSelectionUiContractTest.java` 삭제
- `observability-portal/src/test/java/com/observation/portal/domain/dashboard/RecentHistoryUiContractTest.java` 삭제

### Change Log

- 2026-06-02: Story 10.6 create-story 산출물을 생성했다. Legacy static dashboard replacement, exact SPA fallback allow-list, jar/static verification, Figma residue cleanup, scope guard를 ready-for-dev 기준으로 확정했다.
- 2026-06-02: Story 10.6 구현을 완료했다. Legacy static dashboard와 Figma residue를 제거하고, exact SPA fallback allow-list와 jar/static verification을 review 기준으로 닫았다.
