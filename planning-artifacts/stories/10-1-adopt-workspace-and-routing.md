---
artifactType: story
storyId: "10.1"
storyKey: "10-1-adopt-workspace-and-routing"
epic: "Epic 10. Figma Make Acceptance And Frontend Hardening"
title: "Adopt workspace and routing"
architectureStyle: "Vite SPA on Spring static root"
status: review
date: 2026-06-02
---

# Story 10.1 - Adopt workspace and routing

## Status

review

Story 10.1 구현을 완료했고 review 대기 상태다.

`implementation-artifacts/sprint-status.yaml`은 이 story 구현 작업에서 수정하지 않았다. 현재 sprint tracking의 `epic-10`과 `10-1-adopt-workspace-and-routing`은 여전히 `backlog`이므로, tracking에 반영하려면 별도 확인 후 상태 갱신이 필요하다.

## Story

Figma Make 산출물을 인수하는 구현자는 `figma/` 프로토타입을 최상위 `frontend/` Vite SPA 작업공간으로 정리하고 싶다.

그래야 후속 Story 10.2~10.7이 같은 npm-only workspace, TypeScript/typecheck 기반, root-mounted BrowserRouter 라우팅 위에서 auth/API 이식과 Spring 정적 서빙 통합을 안전하게 진행할 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 BMAD create-story context다. 충돌처럼 보이는 지점은 1번 문서의 "Codex 실행용 콜드스타트 노트"와 locked decision을 우선한다.

1. `/Users/tlsdla1235/.claude/plans/synchronous-foraging-stearns.md`
2. `AGENTS.md`
3. `planning-artifacts/figma-make-acceptance-sprint-plan.md`
4. `implementation-artifacts/sprint-status.yaml`
5. `planning-artifacts/figma-make-nextjs-frontend-spec.md`
6. `planning-artifacts/api-surface.md`
7. `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
8. `planning-artifacts/acceptance-traceability.md`
9. `_bmad/custom/project-context.md`

확인한 현재 workspace/code 상태:

1. `git status --short` 기준 `figma/`, `README.md`, `docs/`, `planning-artifacts/figma-make-nextjs-frontend-spec.md`, 네 개의 `* 2.java` 파일이 untracked다.
2. `find observability-portal/src/main/java -name '* 2.java' -print`는 아래 네 개를 반환한다.
   - `observability-portal/src/main/java/com/observation/portal/config/DashboardStaticWebConfig 2.java`
   - `observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackTokenRelayRequest 2.java`
   - `observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackSessionResponse 2.java`
   - `observability-portal/src/main/java/com/observation/portal/domain/account/controller/GithubCallbackTokenRelay 2.java`
3. `./gradlew :observability-portal:compileJava`는 위 네 파일이 public class/file name 불일치로 컴파일을 깨서 실패한다. 이 실패는 Story 10.1 구현 전 workspace baseline blocker이며 Figma 인수 변경 때문이 아니다.
4. 현재 `figma/package.json`은 `@figma/my-make-file` 이름, `peerDependencies`의 `react`/`react-dom`, `pnpm.overrides`, `build`/`dev` script만 가진다.
5. 현재 `figma/src/app/App.tsx`는 `MemoryRouter initialEntries={["/"]}`와 `/`, `/dashboard`, `/docs` route를 사용한다.
6. 현재 `figma/src/app/components/nav.tsx`는 Dashboard/Docs `Link`와 GitHub 로그인 버튼을 렌더링하지만 auth action prop/hook boundary는 없다.
7. 현재 `figma/vite.config.ts`는 `figmaAssetResolver()`와 `@` alias를 가지며 `base`는 명시하지 않는다.
8. 현재 `figma/index.html` title/description은 Figma Make placeholder다.

## Background

Epic 10은 새 프론트를 처음부터 만드는 작업이 아니라 Figma Make export를 실서비스 프론트로 경화하는 인수 작업이다. `figma/`는 Vite + React 18 + Tailwind v4 + shadcn/ui 기반 독립 실행 프로토타입이며, 실 API 호출 없이 mock data를 사용한다.

이번 story는 인수 기반만 닫는다. 후속 story가 auth/API/read model/Gradle/Spring replacement를 이어받을 수 있도록 workspace 위치, package manager 기준, typecheck, root-mounted routing, nav action 경계를 정리한다.

`planning-artifacts/figma-make-nextjs-frontend-spec.md`는 제품/UX와 route 의도를 참고하되, 이번 인수의 locked decision은 Vite SPA 유지다. Next.js 전환, Next.js API route, 새 backend endpoint는 제안하거나 구현하지 않는다.

`planning-artifacts/project-structure.md`의 오래된 `observability-portal/src/main/frontend/` 후보보다 이번 Figma 인수 계획의 최상위 `frontend/` 결정이 우선한다.

## Scope

- 구현 전 pre-flight workspace risk check를 수행하고 결과를 implementation notes에 남긴다.
- `figma/`를 repo 최상위 `frontend/`로 인수한다. 현재 `figma/`가 untracked일 수 있으므로 `git mv` 가능 여부를 확인하고, tracked가 아니면 일반 move 후 새 경로를 stage 대상으로 삼는다.
- npm-only workspace로 정리한다.
- Vite SPA를 유지한다.
- TypeScript/typecheck 기반을 추가한다.
- `MemoryRouter`를 root-mounted `BrowserRouter`로 바꾸는 routing 준비를 완료한다.
- route `/`, `/dashboard`, `/docs`를 유지한다.
- Vite `base: '/'`를 명시한다.
- Navigation의 Dashboard/Docs link가 BrowserRouter route와 일치하도록 유지한다.
- Nav의 GitHub 버튼은 Story 10.2의 auth context action과 연결 가능한 prop 또는 hook boundary만 준비한다.

## Non-scope

- Auth 구현, GitHub OAuth popup/relay 구현, token state 구현
- API fetch 구현, `authFetch`, API client, adapter, `useApiResource` 구현
- mock data 제거 또는 real API wiring
- Gradle node build 통합
- Spring static route fallback 변경
- 기존 `observability-portal/src/main/resources/static/dashboard/*` 삭제
- `figmaAssetResolver`, `ImageWithFallback`, guideline residue의 최종 cleanup
- 새 backend endpoint 생성
- Next.js 전환 또는 Next.js API route 생성
- UI-side lifecycle state, starter connection diagnosis, zeroInsight, recovery, p95/p99, endpoint priority, snapshot/history event 계산
- 사용자 확인 없는 `* 2.java` 파일 삭제

## Acceptance Criteria

### Pre-flight AC

P1. 구현 착수 전 `git status --short`를 실행하고, workspace의 기존 untracked/modified 상태를 implementation notes에 기록한다.

P2. 구현 착수 전 `find observability-portal/src/main/java -name '* 2.java' -print`를 실행하고, 중복 Java 파일 후보 목록을 implementation notes에 기록한다.

P3. `* 2.java` 중복 Java 파일 후보가 `compileJava`를 깨는 경우, Story 10.5/10.7 검증의 선제 차단 요소로 기록한다.

P4. 구현자는 사용자 확인 없이 `* 2.java` 후보를 삭제하거나 되돌리지 않는다.

P5. 가능하면 Story 10.1 구현 전 또는 완료 검증 때 `./gradlew :observability-portal:compileJava` 또는 `./gradlew :observability-portal:test`로 baseline을 확인하고, 실패가 Figma 인수 변경 때문인지 기존 workspace 상태 때문인지 구분해 기록한다.

### Core AC

1. `figma/`가 최상위 `frontend/`로 이동된다.
2. `pnpm-workspace.yaml` 등 pnpm/Figma-only workspace 흔적은 npm-only 기준으로 정리된다.
3. `package.json` 이름/설명은 Observation Portal frontend에 맞게 갱신된다.
4. `react`, `react-dom`은 runtime dependencies에 둔다.
5. `typescript`, `@types/react`, `@types/react-dom`, 필요 시 `@types/node`와 `tsconfig.json`, `tsconfig.node.json`, `typecheck` script가 추가된다.
6. `index.html` title/description에서 Figma Make placeholder를 제거한다.
7. `MemoryRouter`는 `BrowserRouter`로 교체된다.
8. route `/`, `/dashboard`, `/docs`는 유지된다.
9. Vite `base: '/'`가 명시된다.
10. Navigation의 Dashboard/Docs link가 BrowserRouter route와 일치한다.
11. Nav의 GitHub 버튼은 Story 10.2의 auth context action과 연결 가능한 prop/hook boundary를 갖는다.
12. 이 story에서는 auth 구현, API fetch 구현, adapter 구현, Gradle node build 통합, legacy static dashboard 삭제를 하지 않는다.

## Tasks

- [x] Pre-flight workspace risk check 수행 (AC: P1~P5)
  - [x] `git status --short` 결과를 implementation notes에 기록한다.
  - [x] `find observability-portal/src/main/java -name '* 2.java' -print` 결과를 implementation notes에 기록한다.
  - [x] 가능하면 `./gradlew :observability-portal:compileJava` 또는 `./gradlew :observability-portal:test`를 실행하고 baseline failure 여부를 기록한다.
  - [x] 현재 알려진 compile blocker가 `* 2.java` public class/file name 불일치라면 Story 10.5/10.7의 known blocker로 남긴다.

- [x] `figma/`를 `frontend/`로 인수 (AC: 1, 2)
  - [x] `figma/`가 tracked인지 확인한다.
  - [x] tracked이면 `git mv figma frontend`를 사용한다.
  - [x] untracked이면 일반 move 후 `frontend/`만 새 workspace source로 남긴다.
  - [x] `frontend/pnpm-workspace.yaml`을 제거한다.
  - [x] `package.json`의 `pnpm` override처럼 pnpm-only metadata를 제거한다.
  - [x] `npm install`이 `frontend/package-lock.json`을 생성하도록 npm-only 기준을 따른다.

- [x] package metadata와 dependency boundary 정리 (AC: 3~5)
  - [x] `frontend/package.json` 이름을 예: `observation-portal-frontend`로 바꾼다.
  - [x] `description`을 Observation Portal frontend/Vite SPA 목적에 맞게 추가한다.
  - [x] `react`와 `react-dom`을 `dependencies`에 둔다.
  - [x] 더 이상 필요 없는 optional `peerDependencies`/`peerDependenciesMeta`의 React 항목을 제거하거나 runtime dependency 기준과 충돌하지 않게 정리한다.
  - [x] `typescript`, `@types/react`, `@types/react-dom`을 `devDependencies`에 추가한다.
  - [x] `vite.config.ts`에서 Node `path`/`__dirname` typing이 필요하므로 `@types/node`도 추가한다.
  - [x] `typecheck` script를 추가한다. 권장 command는 `tsc -b --noEmit`이다.

- [x] TypeScript project config 추가 (AC: 5)
  - [x] `frontend/tsconfig.json`을 추가하고 app/node config를 참조하게 구성한다.
  - [x] `frontend/tsconfig.node.json`을 추가해 `vite.config.ts` typecheck를 포함한다.
  - [x] `@` -> `src` path alias와 Vite/React JSX 설정이 typecheck에서 일관되게 동작하게 한다.
  - [x] `npm run build`와 `npm run typecheck`를 분리한다. Vite build가 typecheck에 암묵적으로 의존한다고 가정하지 않는다.

- [x] Figma Make placeholder 제거 (AC: 2, 3, 6)
  - [x] `frontend/index.html` title을 `Observation Portal`로 바꾼다.
  - [x] `meta[name="description"]`을 Spring Boot starter-first observability dashboard에 맞게 갱신한다.
  - [x] Figma Make README/ATTRIBUTIONS/guideline residue는 Story 10.1에서 npm workspace를 방해하는 수준만 정리한다. 최종 Figma residue cleanup은 Story 10.6 범위다.

- [x] Root-mounted BrowserRouter routing 준비 (AC: 7~10)
  - [x] `frontend/src/app/App.tsx`에서 `MemoryRouter initialEntries={["/"]}`를 제거하고 `BrowserRouter`를 root-mounted로 사용한다.
  - [x] `basename`은 두지 않는다.
  - [x] `/`, `/dashboard`, `/docs` route element를 유지한다.
  - [x] `frontend/vite.config.ts`에 `base: '/'`를 명시한다.
  - [x] React Router import 위치는 현재 package/version에서 지원되는 export를 확인하고 최소 변경한다. 새 router library는 필요성이 확인될 때만 추가한다.
  - [x] Navigation의 Dashboard/Docs `Link to` 값과 active state가 `/dashboard`, `/docs` route와 일치하는지 확인한다.

- [x] Nav GitHub action boundary 준비 (AC: 11, 12)
  - [x] `Nav`가 Story 10.2에서 `useAuth().startGithubLogin` 또는 동등 action을 주입할 수 있는 prop/hook boundary를 갖게 한다.
  - [x] Story 10.1에서는 `/api/auth/github/authorize` 호출, popup open, relay consume, token state, 401 처리, storage policy 구현을 하지 않는다.
  - [x] button label/status는 추후 auth state와 연결 가능하게 하되, 실제 authenticated/unauthenticated behavior를 가장하지 않는다.

- [x] Verification 수행 및 기록 (AC: 전체)
  - [x] `cd frontend && npm install`
  - [x] `cd frontend && npm run build`
  - [x] `cd frontend && npm run typecheck`
  - [x] Vite dev server에서 `/`, `/dashboard`, `/docs` route 전환을 확인한다.
  - [x] `git status --short`로 Story 10.1 변경과 기존 workspace blocker를 분리해 기록한다.

## Verification

명령 검증:

```bash
git status --short
find observability-portal/src/main/java -name '* 2.java' -print
./gradlew :observability-portal:compileJava
cd frontend && npm install
cd frontend && npm run build
cd frontend && npm run typecheck
```

`./gradlew :observability-portal:compileJava`가 현재처럼 `* 2.java` public class/file name 불일치로 실패하면, 구현자는 이를 기존 workspace baseline blocker로 기록한다. Story 10.1의 frontend 변경으로 생긴 실패와 혼동하지 않는다.

수동 검증:

1. Vite dev server를 실행하고 `/`가 Landing을 렌더링하는지 확인한다.
2. Navigation의 Dashboard link가 `/dashboard`로 이동하고 Dashboard 화면을 렌더링하는지 확인한다.
3. Navigation의 Docs link가 `/docs`로 이동하고 Docs 화면을 렌더링하는지 확인한다.
4. BrowserRouter 전환 후 dev server에서 `/dashboard`, `/docs` 직접 진입 또는 새로고침이 깨지지 않는지 확인한다.
5. GitHub 버튼이 클릭 가능한 UI boundary를 갖되, Story 10.1에서 실제 auth/API 호출을 하지 않는지 확인한다.

정적 검토:

```bash
rg -n "MemoryRouter|initialEntries|pnpm|@figma/my-make-file|MD 파일 기반 앱 만들기" frontend
rg -n "fetch\\(|/api/auth|callback/tokens|localStorage|sessionStorage|document.cookie" frontend/src
```

첫 번째 검색은 남은 workspace/routing/Figma placeholder residue를 찾기 위한 것이다. 두 번째 검색은 Story 10.1에서 auth/API/storage 구현이 새로 들어오지 않았는지 확인하기 위한 것이다. 기존 shadcn UI preference cookie가 있다면 auth/token/credential 저장이 아닌지 수기 확인한다.

## Risks

- 현재 `* 2.java` 중복 파일 후보 네 개가 `compileJava`를 깨고 있다. 이 blocker는 Story 10.5 Gradle build integration과 Story 10.7 acceptance gate 전에 해결되거나 known blocker로 명시돼야 한다.
- `figma/`가 현재 untracked라면 `git mv`가 바로 동작하지 않을 수 있다. 구현자는 tracked 여부를 확인하고 기존 사용자 변경을 되돌리지 않는다.
- `figma/package.json`은 React를 peer dependency로 두고 pnpm override를 갖는다. npm install/build 기준으로 정리하지 않으면 후속 CI/Gradle integration에서 package manager 경계가 흔들릴 수 있다.
- TypeScript를 추가하면 기존 `.tsx` mock/prototype code의 latent type issue가 드러날 수 있다. build와 typecheck를 분리해 type issue를 명확히 보고한다.
- BrowserRouter root mount는 후속 Spring static fallback과 맞물린다. Story 10.1은 Vite dev route만 확인하고, Spring fallback 변경은 Story 10.6에서 다룬다.
- `planning-artifacts/figma-make-nextjs-frontend-spec.md`의 Next.js 표현을 그대로 따르면 locked decision을 위반한다. 이 story는 Vite SPA 유지가 우선이다.
- Nav auth boundary를 준비하면서 실제 auth를 일부 구현하면 Story 10.2의 책임과 겹친다. 이 story에서는 action 연결 자리만 둔다.

## Dependencies

- Epic 10 locked decision: Vite SPA 유지, 최상위 `frontend/`, npm 사용, Spring static root mount.
- Existing Figma export: `figma/` directory.
- Node/npm availability: 콜드스타트 노트 기준 Node v25, npm v11 사용 가능, pnpm 없음.
- Existing React Router usage: `figma/src/app/App.tsx`, `figma/src/app/components/nav.tsx`, `figma/src/app/components/landing.tsx`.
- Story 10.2 dependency: Nav GitHub button boundary는 후속 auth context action과 연결 가능해야 한다.
- Story 10.5/10.7 dependency: `* 2.java` compile blocker는 후속 build/acceptance 검증 전에 해결 또는 known blocker 처리되어야 한다.

## Notes

- 이 story에서 새 backend API, Next.js API route, UI-side read model 계산을 만들지 않는다.
- Existing static dashboard의 auth/fetch/race-guard 이식 reference는 후속 Story 10.2~10.4에서 사용한다. Story 10.1은 해당 로직을 읽어 구현하지 않는다.
- `frontend/vite.config.ts`의 `figmaAssetResolver` 제거는 Story 10.6 cleanup 범위로 남겨도 된다. Story 10.1에서는 `base: '/'`, TypeScript config, npm-only build에 필요한 최소 변경을 우선한다.
- `frontend/src/app/components/figma/ImageWithFallback.tsx` 삭제도 Story 10.6 cleanup 범위다. Story 10.1에서 삭제가 필요해 보이면 import 0건을 확인하고 범위 초과 여부를 implementation notes에 남긴다.
- 후속 Story 10.3~10.4에서 adapter는 rename/null 방어/표시용 formatting만 수행해야 하며 lifecycle state, p95/p99, endpoint priority, snapshot/history event를 계산하지 않는다.
- AGENTS.md 기준으로 새 public class/method/helper 또는 동작이 바로 드러나지 않는 내부 helper에 주석을 쓸 경우 한국어로 작성한다.
- 기존 사용자 변경 또는 untracked 파일을 임의로 삭제하지 않는다. 특히 `* 2.java` 후보는 반드시 사용자 확인 후 처리한다.

## Dev Agent Record

### Implementation Plan

1. Pre-flight로 기존 workspace 상태, `* 2.java` 후보, Gradle Java compile baseline을 확인한다.
2. untracked `figma/` export를 최상위 `frontend/`로 일반 이동하고 npm-only workspace metadata로 정리한다.
3. Vite SPA를 유지하면서 TypeScript build-mode typecheck와 BrowserRouter root mount를 추가한다.
4. Nav의 GitHub 버튼은 Story 10.2에서 auth action을 주입할 수 있는 prop boundary만 만든다.
5. npm install/build/typecheck와 Vite dev server route 전환을 확인한다.

### Debug Log

- Pre-flight `git status --short`: `figma/`, root `README.md`, `docs/`, `planning-artifacts/figma-make-nextjs-frontend-spec.md`, story file, 네 개의 `* 2.java`, `=` 파일이 untracked로 확인됐다.
- Pre-flight `find observability-portal/src/main/java -name '* 2.java' -print`: `DashboardStaticWebConfig 2.java`, `GithubCallbackTokenRelayRequest 2.java`, `GithubCallbackSessionResponse 2.java`, `GithubCallbackTokenRelay 2.java` 네 개가 확인됐다.
- Pre-flight `./gradlew :observability-portal:compileJava`: 위 네 파일의 public class/file name 불일치로 실패했다. 이 실패는 Story 10.1 frontend 인수 변경 전부터 존재한 workspace baseline blocker다.
- Red check `cd frontend && npm run typecheck`: 구현 전에는 `typecheck` script가 없어 실패했다.
- `git ls-files figma`: 출력 없음. `figma/`는 tracked가 아니므로 `git mv` 대신 일반 `mv figma frontend`로 인수했다.
- `cd frontend && npm install`: 성공했고 `package-lock.json`을 생성했다. `recharts@2.15.2` deprecated warning과 npm audit high severity 1건이 보고됐지만 Story 10.1 범위의 dependency upgrade는 수행하지 않았다.
- `cd frontend && npm run build`: Vite build 성공, `dist/` 생성.
- `cd frontend && npm run typecheck`: `tsc -b --noEmit` 성공.
- Vite dev server `http://127.0.0.1:5173/`: `/` 진입, Dashboard link 클릭 후 `/dashboard`, Docs link 클릭 후 `/docs`, 직접 `/dashboard`, `/docs` 진입을 확인했다.
- 정적 검토 `rg -n "MemoryRouter|initialEntries|pnpm|@figma/my-make-file|MD 파일 기반 앱 만들기" frontend`: source 기준 0건이다.
- 정적 검토 `rg -n "fetch\\(|/api/auth|callback/tokens|localStorage|sessionStorage|document.cookie" frontend/src`: 실행 auth/API 구현은 추가되지 않았다. 잔여 match는 미사용 `src/imports` 스펙 문서, Docs의 API reference 표, shadcn sidebar UI preference cookie였다.
- `frontend/src/imports/figma-make-nextjs-frontend-spec.md`와 `frontend/src/app/components/figma/ImageWithFallback.tsx`는 import 0건이지만 Story 10.6 cleanup 범위로 남겼다.
- 완료 후 `git status --short`: Story 10.1 변경은 `.gitignore`, `frontend/`, story file이다. root `README.md`, `docs/`, `planning-artifacts/figma-make-nextjs-frontend-spec.md`, `=`, 네 개의 `* 2.java`는 기존/범위 밖 untracked 상태로 남아 있다.
- 완료 후 `find observability-portal/src/main/java -name '* 2.java' -print`: pre-flight와 같은 네 개가 남아 있다. 사용자 확인 없이 삭제하거나 되돌리지 않았다.

### Completion Notes

- `figma/` export를 최상위 `frontend/` Vite SPA workspace로 인수했다.
- npm-only 기준으로 `pnpm-workspace.yaml`과 `package.json`의 pnpm/peer React metadata를 제거하고, `react`/`react-dom` runtime dependencies와 npm lockfile을 추가했다.
- `typescript`, React/DOM/Node type packages, `tsconfig.json`, `tsconfig.app.json`, `tsconfig.node.json`, `typecheck` script를 추가했다.
- `index.html`과 `README.md`의 Figma Make placeholder를 Observation Portal frontend 설명으로 바꿨다.
- `MemoryRouter initialEntries`를 root-mounted `BrowserRouter`로 교체했고, `/`, `/dashboard`, `/docs` route와 Nav link를 유지했다.
- `vite.config.ts`에 `base: '/'`를 명시했다.
- `Nav`에 Story 10.2 auth context action 연결용 `onGithubLogin`/`githubLoginLabel` prop boundary를 추가했으며 실제 auth/API/fetch/token/storage 동작은 구현하지 않았다.
- `frontend/node_modules/`와 `frontend/dist/`가 실수로 추적되지 않도록 `.gitignore`에 추가했다.
- `implementation-artifacts/sprint-status.yaml`은 수정하지 않았다. 현재 tracking의 `epic-10`과 `10-1-adopt-workspace-and-routing`은 `backlog`라 별도 확인 후 상태 반영이 필요하다.

## File List

- `.gitignore`
- `frontend/` (untracked Figma export를 최상위 workspace로 이동)
- `frontend/README.md`
- `frontend/index.html`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/tsconfig.json`
- `frontend/tsconfig.app.json`
- `frontend/tsconfig.node.json`
- `frontend/vite.config.ts`
- `frontend/src/app/App.tsx`
- `frontend/src/app/components/nav.tsx`
- `frontend/pnpm-workspace.yaml` (삭제)
- `planning-artifacts/stories/10-1-adopt-workspace-and-routing.md`

## Change Log

- 2026-06-02: Story 10.1 구현 완료. Figma export를 `frontend/`로 인수하고 npm/typecheck/BrowserRouter/Nav action boundary를 추가했다.
