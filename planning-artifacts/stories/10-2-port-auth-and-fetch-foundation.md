---
artifactType: story
storyId: "10.2"
storyKey: "10-2-port-auth-and-fetch-foundation"
epic: "Epic 10. Figma Make Acceptance And Frontend Hardening"
title: "Port auth and fetch foundation"
architectureStyle: "Vite SPA on Spring static root"
status: review
date: 2026-06-02
---

# Story 10.2 - Port auth and fetch foundation

## Status

review

Story 10.2 구현을 완료했고 review 대기 상태다.

## Story

Figma Make 인수 프론트 구현자는 기존 static dashboard에서 검증된 GitHub OAuth popup relay, in-memory service access token, Bearer `authFetch`, 401 인증 상실 처리, request sequence guard를 React/Vite 구조로 이식하고 싶다.

그래야 후속 Story 10.3~10.4가 같은 인증/fetch foundation 위에서 Project/Application/Dashboard/Instance/Credential read model wiring을 안전하게 진행할 수 있다.

## Source of Truth

아래 문서를 먼저 읽고 반영한 BMAD create-story context다.

1. `/Users/tlsdla1235/Desktop/study/observation/AGENTS.md`
2. `/Users/tlsdla1235/.claude/plans/synchronous-foraging-stearns.md`
3. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/figma-make-acceptance-sprint-plan.md`
4. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/stories/10-1-adopt-workspace-and-routing.md`
5. `/Users/tlsdla1235/Desktop/study/observation/implementation-artifacts/sprint-status.yaml`
6. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/api-surface.md`
7. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/acceptance-traceability.md`
8. `/Users/tlsdla1235/Desktop/study/observation/_bmad/custom/project-context.md`
9. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/epics.md`
10. `/Users/tlsdla1235/Desktop/study/observation/planning-artifacts/architecture.md`
11. `/Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/resources/static/dashboard/app.js`
12. `/Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/controller/GithubCallbackTokenRelay.java`
13. `/Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackTokenRelayRequest.java`
14. `/Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackSessionResponse.java`
15. 참고 확인: `/Users/tlsdla1235/Desktop/study/observation/observability-portal/src/main/java/com/observation/portal/domain/account/controller/AccountAuthController.java`

현재 기준:

- Story 10.1은 commit `cc7d87a frontend: adopt figma workspace and routing`로 완료됐다.
- `frontend/`는 최상위 Vite SPA workspace다.
- `frontend/package.json` 기준 주요 버전은 Vite `6.3.5`, React `18.3.1`, `react-router` `7.13.0`, TypeScript `5.8.3`이다.
- `frontend/src/app/App.tsx`는 root `BrowserRouter`를 사용하며 `/`, `/dashboard`, `/docs` route를 유지한다.
- `frontend/src/app/components/nav.tsx`는 `onGithubLogin`과 `githubLoginLabel` prop boundary를 갖는다.
- 현재 workspace에는 네 개의 `* 2.java` duplicate 파일이 남아 있으며, public class/file name 불일치로 `compileJava`를 깨는 known baseline blocker다. 이 story 구현자는 이 파일들을 삭제하거나 되돌리지 않는다.

## Background

Epic 10은 새 프론트를 처음부터 만드는 일이 아니라 Figma Make 산출물을 production-ready Vite SPA로 경화하는 인수 작업이다. 인증과 fetch foundation은 이미 기존 static dashboard에 검증된 구현이 있으므로, 이 story의 핵심은 새 React 구조에 그 동작을 동등하게 이식하는 것이다.

기존 `app.js`에서 확인한 이식 기준:

- OAuth relay 상수와 in-memory token/request sequence state: `app.js` 139~183행
- token set/clear 시 모든 stale request sequence를 무효화하는 패턴: `app.js` 185~220행, 5167~5189행
- Project/Application/Dashboard/Instance/History fetch의 401 및 stale guard 패턴: `app.js` 221~255행, 415~462행, 687~730행, 1308~1348행, 1545~1595행, 2081~2124행, 2526~2560행
- Bearer header helper: `app.js` 4728~4744행
- secret-bearing project registration과 credential lifecycle의 `cache: 'no-store'`: `app.js` 4750~4825행
- GitHub OAuth popup, redirect fallback, `postMessage`, meta fallback, relay consume: `app.js` 4988~5165행

Backend relay 기준:

- `GET /api/auth/github/authorize`는 `GithubAuthorizeResponse { provider, authorizationUrl, stateRequired }`를 `Cache-Control: no-store`로 반환한다.
- browser-facing `/api/auth/github/callback`은 token JSON을 렌더링하지 않고 relay HTML을 반환한다.
- callback HTML은 `postMessage({ type: "observation-portal.github-oauth-complete", relayId }, window.location.origin)`를 보내고, `<meta name="observation-github-callback-relay-id" content="...">`도 제공한다.
- `POST /api/auth/github/callback/tokens`는 `{ relayId }`를 받아 `GithubCallbackSessionResponse`의 service `accessToken`을 1회만 반환한다.
- `GithubCallbackTokenRelay`는 2분 TTL, 1회 consume, 일반화된 실패 메시지, URL/cookie/storage/body token 노출 회피를 담당한다.

## Scope

- `frontend/src/app/lib/auth.tsx` 또는 동등 파일에 React auth context/provider/hook을 만든다.
- `AuthProvider`를 `frontend/src/app/App.tsx`에 연결하고, `Nav`의 Story 10.1 prop boundary를 `startGithubLogin`과 인증 상태 label에 연결한다.
- GitHub OAuth 시작, popup open, redirect fallback, relay id 수신, relay consume, in-memory access token state를 구현한다.
- resource API용 `authFetch` 또는 동등 helper를 만든다.
- `frontend/src/app/lib/api.ts` 또는 endpoint helper 파일에는 이 story에서 필요한 endpoint 상수/기초 fetch helper만 둔다. read model adapter wiring은 후속 story로 남긴다.
- `frontend/src/app/lib/use-api-resource.ts` 또는 동등 hook을 만들어 loading/error/data와 request sequence guard 패턴을 제공한다.
- 빠른 Project/Application 전환, token 변경, 401 인증 상실에서 stale response가 최신 선택 상태를 덮지 않도록 foundation을 둔다.
- secret-bearing request와 credential lifecycle request에 `cache: "no-store"`를 적용할 기준을 helper/API option으로 명시한다.
- 새 파일이나 복잡한 helper에는 AGENTS.md 기준에 맞춰 한국어 주석 또는 JSDoc을 필요한 만큼만 작성한다.

## Non-scope

- 새 backend endpoint 생성
- Next.js 전환 또는 Next.js API route 생성
- API adapter/read model wiring. Project/Application/Dashboard DTO 변환과 mock data 제거는 Story 10.3~10.4 범위다.
- `dashboard-data.ts` seed 제거 또는 Dashboard 컴포넌트의 실 API 배선
- UI-side lifecycle state, starter connection diagnosis, p95/p99, endpoint priority, snapshot/history event 계산
- Credential lifecycle UI 구현 자체. 단, 이 story의 fetch foundation은 후속 credential request가 `cache: "no-store"`를 쓰게 할 수 있어야 한다.
- Gradle node build 통합
- Spring static fallback 변경
- legacy static dashboard 삭제
- `observability-portal/src/main/resources/static/dashboard/app.js` 수정 또는 삭제
- 사용자 확인 없는 `* 2.java` 파일 삭제/수정/되돌리기

## Acceptance Criteria

1. `GET /api/auth/github/authorize`를 `cache: "no-store"`와 `Accept: "application/json"`으로 호출하고, 응답의 `authorizationUrl`을 검증한다.
2. GitHub login은 우선 popup flow를 준비한다. `window.open` 실패, popup 부적합, cross-origin redirect origin 불일치 등에서는 기존 `app.js`와 동등하게 full-page redirect fallback을 준비한다.
3. Popup callback relay id 수신은 `postMessage`와 `meta[name="observation-github-callback-relay-id"]` polling fallback을 모두 보존한다.
4. `postMessage`는 `event.origin === window.location.origin`, `event.data.type === "observation-portal.github-oauth-complete"`, non-blank `relayId`를 모두 검증한 뒤에만 소비한다.
5. meta fallback은 popup document 접근이 가능한 경우에만 relay meta를 읽고, cross-origin 접근 오류는 정상 fallback 과정으로 삼아 삼킨다.
6. `POST /api/auth/github/callback/tokens`는 `{ relayId }`를 `cache: "no-store"`, `referrerPolicy: "no-referrer"`, JSON headers로 한 번 consume한다.
7. 같은 relay id를 중복 consume하지 않도록 in-flight guard 또는 동등 장치를 둔다.
8. relay consume 성공 시 response의 `accessToken`을 trim/검증하고, 비어 있으면 인증 성공으로 처리하지 않는다.
9. access token은 React memory state에만 보관한다.
10. URL query/hash/fragment, cookie, `localStorage`, `sessionStorage`에 access token, refresh token, provider token, starter credential, relay credential을 저장하거나 읽지 않는다.
11. Auth context는 최소한 authenticated 상태, login 진행 상태, 오류/상태 메시지, `startGithubLogin`, `clearAccessToken`, `authFetch` 또는 동등 기능을 제공한다.
12. `frontend/src/app/App.tsx`는 `AuthProvider`로 앱을 감싸고, `Nav`의 `onGithubLogin`을 `startGithubLogin`에 연결한다.
13. `Nav`는 authenticated/unauthenticated/login-in-progress 상태를 사용자에게 보여주는 label 또는 disabled 상태를 받되, token 값을 표시하지 않는다.
14. `authFetch`는 resource API 요청에 `Authorization: Bearer <access_token>`을 붙인다.
15. token이 없을 때 resource API 요청은 명시적인 auth-required error/state로 수렴하고, 무의미한 unauthenticated request storm을 만들지 않는다.
16. resource API의 `401`은 token을 clear하고 authenticated 상태를 잃은 것으로 처리한다.
17. 401 처리 후 Project/Application/Dashboard/Instance/History/Credential 등 dependent resource state는 stale로 남지 않게 무효화할 수 있는 boundary를 제공한다.
18. `useApiResource` 또는 동등 hook은 loading/error/data state를 제공한다.
19. `useApiResource` 또는 동등 hook은 request sequence guard를 제공해, 늦게 도착한 이전 응답이 최신 선택 상태를 덮지 않게 한다.
20. 빠른 project/application 전환, token clear, token 재설정, component unmount 상황에서 오래된 response와 error가 최신 화면 상태를 변경하지 않는다.
21. secret-bearing request와 credential lifecycle request에는 `cache: "no-store"`를 적용할 수 있는 fetch option/helper 기준이 포함된다.
22. 일반 resource read request는 `Accept: "application/json"`을 기본으로 둔다.
23. callback relay/token lifecycle request에는 `referrerPolicy: "no-referrer"`를 적용한다.
24. Vite SPA와 root `BrowserRouter` 구조를 유지한다.
25. `react-router` v7 package 경계를 존중한다. 현 코드처럼 `react-router` import를 쓰는 기준을 유지하고, 필요 없는 router library를 추가하지 않는다.
26. 새 backend endpoint, Next.js API route, server session/cookie auth, browser token persistence를 만들지 않는다.
27. API adapter/read model wiring은 Story 10.3~10.4 범위로 남긴다.
28. UI-side lifecycle state, p95/p99, endpoint priority, snapshot/history event 계산을 만들지 않는다.
29. Gradle node build 통합, Spring static fallback 변경, legacy static dashboard 삭제는 Story 10.5~10.6 범위로 남긴다.
30. `* 2.java` duplicate baseline blocker는 삭제/수정하지 않고 verification risk로만 기록한다.

## Tasks

- [x] Auth source scaffold 작성 (AC: 9~13, 24~26)
  - [x] `frontend/src/app/lib/auth.tsx`를 만들고 auth provider/context/hook을 정의한다.
  - [x] access token state는 React memory에만 둔다.
  - [x] login state, authenticated flag, status/error message, `startGithubLogin`, `clearAccessToken` boundary를 제공한다.
  - [x] `App.tsx`에 `AuthProvider`를 연결한다.
  - [x] `Nav`의 `onGithubLogin`/`githubLoginLabel` prop을 auth context state/action에 연결한다.

- [x] GitHub OAuth popup/relay flow 이식 (AC: 1~8, 23)
  - [x] `/api/auth/github/authorize` 호출 helper를 구현한다.
  - [x] popup open, popup feature, popup name, watch interval/timeout을 기존 `app.js`와 동등한 상수로 둔다.
  - [x] `authorizationUrl`의 redirect origin을 확인해 popup flow 사용 가능 여부를 판단한다.
  - [x] popup 불가 시 redirect fallback을 제공한다.
  - [x] `window.addEventListener("message", ...)` listener를 등록/해제하고 trusted callback message만 처리한다.
  - [x] popup document meta fallback polling을 구현한다.
  - [x] relay consume in-flight guard와 cleanup을 구현한다.
  - [x] callback relay consume 실패/timeout/popup close 상태를 token 없이 안전하게 표시한다.

- [x] `authFetch` foundation 작성 (AC: 14~17, 21~23)
  - [x] JSON resource read 기본 header를 구성한다.
  - [x] token이 있으면 `Authorization: Bearer <access_token>`을 부착한다.
  - [x] token이 없으면 auth-required error/state로 수렴한다.
  - [x] `401` 응답에서 `clearAccessToken`/authorization loss를 호출한다.
  - [x] secret-bearing/callback/credential lifecycle request용 `cache: "no-store"` option 경계를 제공한다.
  - [x] token, credential, provider payload, raw secret을 error message/log/UI에 노출하지 않는다.

- [x] `useApiResource` 또는 동등 hook 작성 (AC: 18~20)
  - [x] `loading`, `error`, `data`, `reload` 또는 동등 API를 제공한다.
  - [x] dependency 변경마다 request sequence를 증가시키고 이전 요청 결과를 무시한다.
  - [x] component unmount 시 state update를 막는다.
  - [x] project/application 같은 parent selection 변경 시 하위 resource 요청을 무효화할 수 있는 사용 패턴을 문서화하거나 API로 제공한다.

- [x] Scope guard 정적 검토 (AC: 24~30)
  - [x] 새 backend route가 추가되지 않았는지 확인한다.
  - [x] Next.js 관련 파일/API route가 추가되지 않았는지 확인한다.
  - [x] `dashboard-data.ts` mock seed 제거와 read model adapter wiring이 이번 story에 섞이지 않았는지 확인한다.
  - [x] `* 2.java` 파일을 수정/삭제하지 않았는지 확인한다.

- [x] Verification 수행 및 Dev Agent Record 갱신 (AC: 전체)
  - [x] `cd frontend && npm run typecheck`
  - [x] `cd frontend && npm run build`
  - [x] `rg -n "localStorage|sessionStorage|document.cookie|accessToken|refreshToken|provider token|callback/tokens" frontend/src`
  - [x] `rg -n "NextResponse|next/server|app/api|pages/api|createBrowserRouter|react-router-dom" frontend`
  - [x] `find observability-portal/src/main/java -name '* 2.java' -print`
  - [x] 가능하면 Gradle-served Spring origin에서 GitHub popup relay 수동 확인

## Verification

명령 검증:

```bash
cd frontend && npm run typecheck
cd frontend && npm run build
rg -n "localStorage|sessionStorage|document.cookie|accessToken|refreshToken|callback/tokens" frontend/src
rg -n "NextResponse|next/server|app/api|pages/api|react-router-dom" frontend
find observability-portal/src/main/java -name '* 2.java' -print
```

정적 검토 기준:

- `localStorage`/`sessionStorage`에 auth token 또는 credential을 저장하거나 읽는 코드가 없어야 한다.
- `document.cookie`는 auth/token/credential/account/project secret 저장에 쓰이면 안 된다. 기존 shadcn UI preference cookie는 auth 값이 아닌 경우만 허용된다.
- URL query/hash/fragment에서 token을 파싱하는 코드가 없어야 한다.
- `Authorization: Bearer` header는 resource API helper에서만 구성한다.
- `/api/auth/github/callback/tokens` relay consume request에는 `cache: "no-store"`와 `referrerPolicy: "no-referrer"`가 있어야 한다.
- secret-bearing request와 credential lifecycle request에 `cache: "no-store"` 적용 기준이 있어야 한다.
- 새 `/api/*` backend endpoint, Next.js API route, lifecycle/p95/p99/endpoint priority/snapshot event 계산 코드가 없어야 한다.

수동 검증:

1. 최종 auth/API 수동 확인은 Gradle-served Spring origin에서 수행한다.
2. Vite dev server에서 확인하려면 `/api` proxy와 OAuth relay origin 정책을 별도 결정해야 한다. 이 story 안에서 임의 proxy 정책을 만들지 않는다.
3. GitHub login popup이 `/api/auth/github/authorize` 응답의 `authorizationUrl`로 이동하는지 확인한다.
4. callback relay가 `postMessage` 또는 meta fallback으로 relay id를 전달하는지 확인한다.
5. relay consume 후 resource API fetch가 `Authorization: Bearer <access_token>`을 붙이는지 Network 탭에서 확인한다.
6. DevTools Application tab에서 access token, refresh token, provider token, starter credential이 URL/cookie/`localStorage`/`sessionStorage`에 없는지 확인한다.
7. 401을 수동 유도해 authenticated 상태가 해제되고 stale response가 최신 선택 상태를 덮지 않는지 확인한다.

Known verification risk:

```text
observability-portal/src/main/java/com/observation/portal/config/DashboardStaticWebConfig 2.java
observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackTokenRelayRequest 2.java
observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackSessionResponse 2.java
observability-portal/src/main/java/com/observation/portal/domain/account/controller/GithubCallbackTokenRelay 2.java
```

위 네 파일은 public class/file name 불일치로 `compileJava`를 깨는 기존 workspace baseline blocker다. Story 10.2 구현자는 삭제/수정하지 말고, Gradle 검증 실패 원인으로만 기록한다.

## Risks

- Auth popup은 origin 정책에 민감하다. Vite dev server와 Spring server가 cross-origin이면 `postMessage` origin 검증과 `/api` 호출이 깨질 수 있다.
- Relay id는 token은 아니지만 token handoff를 여는 1회용 secret에 가깝다. 로그, URL, storage에 남기지 않는다.
- React state update race는 기존 `app.js`의 global sequence guard보다 놓치기 쉽다. hook cleanup과 request id 검사를 둘 다 둔다.
- `authFetch`에서 401을 단순 fetch error로만 처리하면 후속 화면이 인증 상실 상태를 잃는다.
- Nav label만 바꾸고 실제 auth context와 연결하지 않으면 Story 10.1 prop boundary가 죽은 경계가 된다.
- `api.ts`를 과하게 키우면 Story 10.3~10.4의 adapter/read model wiring 범위를 침범한다.
- `accessToken` 문자열 검색은 response field명 때문에 match가 날 수 있다. storage, URL parsing, logging, UI 노출 여부를 수기 검토한다.
- `* 2.java` baseline blocker 때문에 Gradle Java compile/bootJar 검증은 이 story 변경과 무관하게 실패할 수 있다.

## Dependencies

- Story 10.1 완료 커밋 `cc7d87a`
- `frontend/` Vite SPA, npm-only workspace, TypeScript/typecheck, root `BrowserRouter`
- `frontend/src/app/components/nav.tsx`의 `onGithubLogin`/`githubLoginLabel` prop boundary
- Existing backend endpoints:
  - `GET /api/auth/github/authorize`
  - `POST /api/auth/github/callback/tokens`
  - existing Project resource APIs under `/api/projects/**`
- Existing backend relay:
  - `GithubCallbackTokenRelay`
  - `GithubCallbackTokenRelayRequest`
  - `GithubCallbackSessionResponse`
  - `AccountAuthController` callback relay page
- Epic 10 locked decisions: Vite SPA 유지, 새 backend endpoint 금지, token in-memory only, no UI-side read model 계산

## Notes

- React auth provider는 app 전체의 infrastructure 성격이므로 `frontend/src/app/lib/auth.tsx`가 가장 자연스럽다.
- `authFetch`가 context 내부 함수라면 hook 바깥 helper에서 직접 쓰기 어렵다. 구현자는 context hook 기반 API와 독립 helper 중 하나를 선택하되 token 전달 경계가 명확해야 한다.
- `useApiResource`는 Story 10.3~10.4가 실제 endpoint wiring에 재사용할 수 있게 generic하게 두되, 이 story에서 read model adapter까지 만들지 않는다.
- 기존 static dashboard는 삭제하지 않는다. Story 10.2 완료 후에도 `app.js`는 parity reference로 남는다.
- `AccountAuthController`의 current-window fallback은 legacy dashboard HTML을 로드하는 구조다. React SPA 전환 이후 이 fallback의 최종 정리는 Story 10.6 static replacement와 함께 다룬다. 이 story에서는 popup opener + React auth context 경로를 우선한다.
- AGENTS.md에 따라 새 파일 상단 또는 핵심 provider/helper 근처에는 역할을 짧게 설명하는 한국어 주석을 둔다. 구현을 그대로 반복하는 주석은 쓰지 않는다.

## Dev Agent Record

### Implementation Plan

1. `auth.tsx`에 AuthProvider/useAuth를 만들고 access token memory state, login state, auth loss handler를 닫는다.
2. 기존 `app.js`의 OAuth constants와 popup/watch/relay consume 흐름을 React effect cleanup과 함께 이식한다.
3. `authFetch`와 `useApiResource` foundation을 만들고 401/token clear/request sequence guard를 검증한다.
4. `App.tsx`와 `Nav`를 auth context에 연결한다.
5. typecheck/build/static grep/manual smoke 결과를 기록한다.

### Debug Log

- 작성 전 Story 10.1 commit `cc7d87a`와 Nav prop boundary를 확인했다.
- 작성 전 기존 `app.js`, `GithubCallbackTokenRelay`, relay request/response DTO, `AccountAuthController` relay page를 확인했다.
- 작성 전 `find observability-portal/src/main/java -name '* 2.java' -print` 결과 네 개의 duplicate Java baseline blocker가 남아 있음을 확인했다.
- BMAD dev-story workflow에 따라 `implementation-artifacts/sprint-status.yaml`의 `10-2-port-auth-and-fetch-foundation`을 `in-progress`로 갱신하고 구현을 시작했다.
- 구현 전 `cd frontend && npm run typecheck`는 성공했다. 별도 JS test script/framework는 package script에 없어 추가 dependency 없이 typecheck/build/static 검증으로 진행했다.
- `frontend/src/app/lib/api.ts`, `auth.tsx`, `use-api-resource.ts`를 추가하고 `App.tsx`, `nav.tsx`를 auth context에 연결했다.
- `cd frontend && npm run typecheck`: 성공.
- `cd frontend && npm run build`: 성공. Vite production build가 `dist/`를 생성했다.
- `rg -n "localStorage|sessionStorage|document.cookie|accessToken|refreshToken|callback/tokens" frontend/src`: 새 auth storage persistence는 없음. Match는 docs/import 문서, `callback/tokens` endpoint 상수/문서, `auth.tsx` memory state/response field, 기존 shadcn sidebar UI preference cookie다.
- `rg -n "NextResponse|next/server|app/api|pages/api|react-router-dom" frontend`: 0건.
- `rg -n "dashboard-data|projectsSeed|applicationsByProject|dashboardByApplication|instanceEvidenceById|snapshotTrendByInstance" frontend/src/app`: 기존 mock seed 사용 위치만 확인했고 제거/배선하지 않았다.
- `find observability-portal/src/main/java -name '* 2.java' -print`: known baseline blocker 네 개가 그대로 남아 있다. 삭제/수정하지 않았다.
- Gradle-served Spring origin에서 GitHub popup relay 수동 확인은 수행하지 않았다. 현재 story는 Vite/Spring proxy 정책을 새로 만들지 않으며 최종 auth/API smoke는 Spring-served origin에서 진행해야 한다.

### Completion Notes

- `AuthProvider`/`useAuth`를 추가해 authenticated 상태, login 진행 상태, 상태/오류 메시지, `startGithubLogin`, `clearAccessToken`, `authFetch`, `authGeneration` boundary를 제공했다.
- access token은 `AuthProvider` 내부 React memory state/ref에만 보관한다. URL, cookie, `localStorage`, `sessionStorage` 저장/읽기는 추가하지 않았다.
- GitHub OAuth는 `/api/auth/github/authorize`를 `cache: "no-store"`와 JSON Accept로 호출하고, popup 우선 흐름, redirect origin 검증, full-page redirect fallback을 구현했다.
- callback relay는 trusted `postMessage` 검증과 popup document meta polling fallback을 모두 유지하고, relay id 중복 consume guard와 `POST /api/auth/github/callback/tokens` `cache: "no-store"`/`referrerPolicy: "no-referrer"`/JSON body를 구현했다.
- `authFetch`는 기본 `Accept: "application/json"`과 `Authorization: Bearer <access_token>`을 부착한다. token이 없으면 fetch를 보내지 않고 `AuthRequiredError`로 수렴하며, `401`은 token generation guard를 통과할 때만 token을 clear해 최신 재로그인 상태를 오래된 401이 지우지 않게 했다.
- `useApiResource`는 `loading`/`error`/`data`/`reload`와 request sequence, `AbortController`, unmount cleanup을 제공한다. `authGeneration`과 caller dependencies가 바뀌면 이전 응답/error는 최신 state를 덮지 못한다.
- secret-bearing request와 credential lifecycle request가 후속 story에서 재사용할 `NO_STORE_REQUEST_OPTIONS`, `SECRET_BEARING_REQUEST_OPTIONS`, `CREDENTIAL_LIFECYCLE_REQUEST_OPTIONS` 기준을 추가했다.
- `dashboard-data.ts` mock seed, API adapter/read model wiring, backend endpoint, Next.js route, Gradle/Spring static fallback, legacy static dashboard, `* 2.java` 파일은 수정하지 않았다.

### File List

- `frontend/src/app/lib/api.ts`
- `frontend/src/app/lib/auth.tsx`
- `frontend/src/app/lib/use-api-resource.ts`
- `frontend/src/app/App.tsx`
- `frontend/src/app/components/nav.tsx`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/10-2-port-auth-and-fetch-foundation.md`

### Change Log

- 2026-06-02: Story 10.2 create-story 작성. 기존 static dashboard 인증/fetch foundation을 React/Vite 구조로 이식하기 위한 acceptance와 guardrail을 확정했다.
- 2026-06-02: Story 10.2 구현 완료. React auth context, GitHub OAuth popup relay, Bearer `authFetch`, request sequence guarded `useApiResource`, scope/static verification 기록을 추가했다.
