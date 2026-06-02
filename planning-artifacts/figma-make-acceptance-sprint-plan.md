---
artifactType: sprint-plan
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: figma-make-acceptance-planned
date: 2026-06-02
sourceOfTruth: /Users/tlsdla1235/.claude/plans/synchronous-foraging-stearns.md
branch: codex/figma-acceptance-test
crossReviewVerdict: conditional-pass
---

# Figma Make Acceptance Sprint Plan

## 1. 결론

이번 스프린트는 새 프론트를 처음부터 만드는 작업이 아니라, Figma Make 산출물인 `figma/`를 실서비스 `frontend/` Vite SPA로 인수하고 기존 static dashboard의 검증된 인증/API/race-guard 로직을 이식하는 경화 작업이다.

핵심 성공 조건은 기존 Spring resource API와 server read model을 그대로 소비하면서 `/`, `/dashboard`, `/docs`를 Spring 정적 root에서 안정적으로 제공하는 것이다.

가장 큰 리스크는 Step 3~4의 auth/API client/adapter/data replacement이며, 특히 UI/adapter가 lifecycle state, p95/p99, endpoint priority, snapshot/history event를 재계산하지 않는 경계를 story별 acceptance로 반복 고정한다.

기존 static dashboard 삭제는 새 React/Vite dashboard의 동작 검증 이후 별도 커밋으로 분리한다.

교차 검증 결과, 이 계획은 조건부 착수 가능하다. 착수 전 또는 해당 story 진입 전 반드시 반영할 보정은 현재 빌드를 깨는 `* 2.java` 중복 파일 게이팅, 실제 lowercase 7-value `state.code` 처리, nested object 필드의 표시 매핑이다.

## 2. Sprint Goal

Figma Make export를 production-ready Vite SPA로 승격해 Spring 정적 root에서 서비스하고, 사용자가 GitHub OAuth 로그인 후 기존 API만으로 Project -> Application -> Dashboard -> Instance Evidence/Trend -> Credential Lifecycle 흐름을 사용할 수 있게 한다.

완료 시점에는 아래가 모두 참이어야 한다.

- `figma/`는 최상위 `frontend/`로 이동되고 npm/Vite/TypeScript 빌드가 가능하다.
- SPA route `/`, `/dashboard`, `/docs`는 Spring static root와 제한된 fallback으로 제공된다.
- GitHub OAuth relay -> in-memory access token -> Bearer resource API fetch 흐름이 기존 `app.js`와 동등하게 동작한다.
- 목 데이터는 제거되고 실제 server read model DTO와 adapter를 통해 UI가 렌더링된다.
- UI/adapter는 server read model 표시자 역할만 수행하고 판단/percentile/priority/history event를 만들지 않는다.
- 기존 `observability-portal/src/main/resources/static/dashboard/*`는 동작 검증 후 삭제된다.

## 3. Scope / Non-scope

### Scope

- `figma/`를 `frontend/`로 `git mv`하고 Figma Make 흔적을 production workspace로 정리한다.
- Vite SPA를 유지하고 `MemoryRouter`를 `BrowserRouter`로 교체한다.
- `react`, `react-dom`을 runtime dependencies로 두고 TypeScript/typecheck 환경을 추가한다.
- 기존 `app.js`의 GitHub OAuth popup relay, Bearer fetch, 401 처리, request sequence guard, one-time credential UX를 React 구조로 이식한다.
- Project/Application/Dashboard/Instance Evidence/Snapshot Trend/Snapshot History/Credential Lifecycle을 기존 API로 연결한다.
- Gradle node-gradle plugin으로 frontend build를 `observability-portal:processResources`에 통합한다.
- `/dashboard`, `/dashboard/`, `/docs`, `/docs/`만 SPA fallback으로 forward하고 `/api/**`와 asset path를 shadow하지 않는다.
- `acceptance-traceability.md` 기준 수기 검증을 완료한다.

### Non-scope

- 새 backend endpoint 생성
- Next.js 전환 또는 Next.js API route 생성
- starter ingest API를 dashboard UI에서 호출
- token을 URL, cookie, `localStorage`, `sessionStorage`에 저장
- UI/adapter에서 lifecycle state, starter connection diagnosis, zero-insight reason, recovery guidance, p95/p99, endpoint priority, snapshot/history event 계산
- histogram bucket으로 percentile 재계산
- endpoint priority client-side sort/rank 재계산
- heartbeat를 accepted bucket freshness나 host application health로 합성
- raw metric explorer, arbitrary query UI, endpoint timeseries, long-retention analytics

## 4. Epic 10 내부 Workstreams

### Workstream A. Figma Artifact Adoption

목표: Figma Make 산출물을 production frontend workspace로 이동하고 Vite/TypeScript/route 기반을 닫는다.

### Workstream B. Auth And API Foundation

목표: 기존 static dashboard의 검증된 GitHub OAuth relay, in-memory token, Bearer fetch, race guard를 React context/hook/API client로 이식한다.

### Workstream C. Server Read Model Wiring

목표: 목 데이터를 제거하고 기존 API DTO와 adapter를 통해 Figma UI가 server read model을 표시하게 한다.

### Workstream D. Spring Static Delivery

목표: Gradle build와 Spring static root 서빙을 통합하고 기존 static dashboard를 완전히 대체한다.

### Workstream E. Acceptance Hardening

목표: no-recompute, security, empty/error/401, route fallback, one-time credential 검증을 완료한다.

## 5. Stories With Acceptance Criteria

### Story 10.1. Adopt workspace and routing

Step: 0~2
Risk: Medium
Dependencies: 없음
Commit boundary: `frontend: adopt figma export as vite workspace`

Acceptance criteria:

- 착수 전 `git status --short`와 `find observability-portal/src/main/java -name '* 2.java' -print` 결과를 확인한다.
- `* 2.java` 중복 파일이 `compileJava`를 깨는 경우, 이는 Story 10.5/10.7 검증의 선제 차단 요소로 기록하고 사용자 확인 없이 임의 삭제하지 않는다.
- `figma/`를 최상위 `frontend/`로 이동하고 npm-only workspace로 정리한다.
- `react`, `react-dom`은 runtime dependencies에 두고, `typescript`, `@types/react`, `@types/react-dom`, 필요 시 `@types/node`와 `tsconfig`/`typecheck` script를 추가한다.
- `index.html` title/description과 package metadata에서 Figma Make placeholder를 제거한다.
- `MemoryRouter`를 root-mounted `BrowserRouter`로 교체하고 `/`, `/dashboard`, `/docs` route와 Vite `base: '/'`를 유지한다.
- Nav의 GitHub 버튼은 Story 10.2의 auth action과 연결 가능한 prop/hook boundary를 갖는다.

Verification:

- `git status --short`
- `find observability-portal/src/main/java -name '* 2.java' -print`
- 가능하면 `./gradlew :observability-portal:compileJava` 또는 blocker 기록
- `cd frontend && npm install && npm run build && npm run typecheck`
- Vite dev server에서 `/`, `/dashboard`, `/docs` route 전환 확인

### Story 10.2. Port auth and fetch foundation

Step: 3
Risk: High
Dependencies: 10.1
Commit boundary: `frontend: port auth and fetch foundation`

Acceptance criteria:

- `GET /api/auth/github/authorize` -> popup 또는 redirect fallback -> relay id 수신 -> `POST /api/auth/github/callback/tokens` 흐름을 React auth context로 이식한다.
- `postMessage`와 `meta[name="observation-github-callback-relay-id"]` fallback을 모두 보존한다.
- access token은 React memory state에만 저장하고 URL, cookie, `localStorage`, `sessionStorage`에 저장하거나 읽지 않는다.
- `authFetch`는 resource API 요청에 `Authorization: Bearer <access_token>`을 붙인다.
- `401` 응답은 token을 clear하고 Project/Application/Dashboard/Instance/History/Credential request를 stale 처리한다.
- `useApiResource` 또는 동등 hook은 loading/error/data와 request sequence guard를 제공한다.
- 빠른 project/application 전환에서 이전 응답이 최신 선택 상태를 덮지 않는다.
- `cache: 'no-store'`가 필요한 secret-bearing request와 credential lifecycle request에 적용된다.

Verification:

- GitHub login popup 수동 확인은 Gradle-served Spring origin에서 수행한다. Vite dev server에서 확인하려면 `/api` proxy와 OAuth relay origin 정책을 별도 결정한다.
- 401 수동 유도 후 auth-required 상태와 stale request 미반영 확인
- DevTools Application tab에서 token storage 부재 확인
- `rg -n "localStorage|sessionStorage|document.cookie|accessToken" frontend/src`
- `cd frontend && npm run typecheck`

### Story 10.3. Wire types, adapters, navigation, and dashboard

Step: 4-A~4-C
Risk: High
Dependencies: 10.2
Commit boundary: `frontend: wire adapters navigation and dashboard`

Acceptance criteria:

- DTO type은 실제 Java record와 최신 API surface를 기준으로 작성한다.
- `ProjectNavigationReadModel.ProjectItem`은 `setupConnectionIssueCount`, `recentConcern`, `links.applications`를 가진다.
- `ProjectApplicationNavigationReadModel.ApplicationItem`은 `metricData`, `starterConnection`, `lifecycleBadge`, `topConcern`, `links.dashboard`를 가진다.
- `recentConcern`과 `topConcern`은 `ConcernSummary{code,label,source}`, `lifecycleBadge`는 `LifecycleBadge{source,code,label}`로 취급하고 `[object Object]`가 렌더링되지 않게 표시 field를 닫는다.
- Adapter는 rename, null/optional 방어, 표시용 formatting만 수행하고 lifecycle state, starter diagnosis, p95/p99, endpoint priority, snapshot/history event를 계산하지 않는다.
- Project list는 `GET /api/projects`, Application list는 현재 project id와 맞는 `links.applications`, Dashboard는 현재 project/application id와 맞는 `links.dashboard`만 사용한다.
- Project/Application/Dashboard의 loading, empty, filtered empty, error, 401, 404 상태를 같은 story 안에서 처리한다.
- no-token/401은 project absence나 application health로 표현하지 않는다.
- Application list는 accepted bucket freshness와 starter heartbeat summary를 별도 field/band로 표시한다.
- Dashboard 착수 전에 Figma UI에 반드시 표시할 read model field와 의도적으로 표시하지 않을 field를 짧게 목록화한다.
- Dashboard의 `state.code`는 backend lowercase 7-value `waiting_first_data`, `unknown`, `idle`, `active`, `stale`, `down`, `degraded` 기준으로 처리하고, Figma mock의 대문자 state 분기는 제거하거나 실제 code 매핑으로 대체한다.
- 표시 텍스트는 `state.label`과 `lifecycleBadge.label`을 우선하고, code 기반 스타일링은 graceful default를 갖는다.
- `RECOVERING`은 state code가 아니라 `recovery.isRecovering` 별도 축으로만 표현한다.
- p95/p99는 `sourceScopedPercentiles.items[]`에서만 표시하고, `triageCards=[]`는 `zeroInsight`를 렌더링한다.
- Endpoint priority는 server order/rank를 그대로 사용하고 client-side ranking을 만들지 않는다.
- `projectsSeed`, `applicationsByProject`, `dashboardByApplication` 기반 초기화가 제거된다.

Verification:

- API 연동 수동 확인은 Gradle-served Spring origin에서 수행한다. Vite dev server에서 확인하려면 `/api` proxy가 선행되어야 한다.
- Network 탭에서 `/api/projects`, `links.applications`, `links.dashboard` 호출 확인
- 실제 또는 fixture state code `active`, `stale`, `down`, `degraded`, `unknown`, `idle`, `waiting_first_data`에서 badge, label, recovery panel이 깨지지 않는지 확인
- concern/badge가 `[object Object]`로 렌더링되지 않는지 수동 확인
- `rg -n "sort\\(|reduce\\(|percentile|p95|p99|endpointPriority|state.code" frontend/src/app/lib frontend/src/app/components` 후 수기 리뷰
- `cd frontend && npm run typecheck && npm run build`

### Story 10.4. Wire evidence, trend, and credential surfaces

Step: 4-D~4-F
Risk: High
Dependencies: 10.3
Commit boundary: `frontend: wire evidence trend and credential surfaces`

Acceptance criteria:

- Instance evidence는 dashboard response의 `instances[].links.evidence`가 현재 project/application/instance context와 일치할 때만 호출한다.
- Instance evidence는 application 판단을 대체하지 않는 drill-down으로 표시하고, accepted bucket metric axis와 starter heartbeat/connection axis를 분리한다.
- Instance 화면에서 application state, health score, p95/p99, endpoint priority를 새로 계산하지 않는다.
- Snapshot trend는 instance evidence 응답의 `links.snapshotTrend`를 우선하고, 없으면 기존 endpoint template만 사용한다.
- Snapshot trend horizon은 기본 `7d`, 허용 `7d|14d`, max limit `336` 경계를 지킨다.
- Snapshot markers는 기존 `/dashboard/snapshot-markers`, operational events는 기존 `/operational-events` endpoint template만 사용한다.
- Trend/history UI는 stored snapshot projection과 event/marker/deep link 표시만 하고 current state나 history event를 client-side로 만들지 않는다.
- Project registration은 기존 `POST /api/projects`, credential lifecycle은 기존 `GET /starter-credential`, `POST /rotations`, `POST /revocations`만 사용한다.
- Create/rotation success의 `starterCredential.displayValue`는 한 번만 표시하고 copy/confirm 이후 UI state에서 지운다.
- Metadata/revocation response는 raw value/hash를 표시하지 않는다.
- Evidence/Trend/History/Credential의 loading, empty, error, 400, 401, 404, 409, 500 상태를 같은 story 안에서 처리한다.
- `instanceEvidenceById`, `snapshotTrendByInstance` 목 초기화가 제거된다.
- Dashboard UI는 starter ingest endpoint를 호출하지 않는다.

Verification:

- Instance evidence, trend `7d`/`14d`, snapshot marker/event empty/404/400 상태 수동 확인
- Project create, credential metadata, rotate, revoke 수동 확인
- DevTools storage에 credential/token 없음 확인
- Network 탭에서 새 endpoint 없이 기존 endpoint만 호출되는지 확인
- `rg -n "instanceEvidenceById|snapshotTrendByInstance|dashboard-data|displayValue|projectKeyHash|starter-credential|ingest/v1" frontend/src`
- `cd frontend && npm run typecheck && npm run build`

### Story 10.5. Integrate frontend build with Gradle

Step: 5
Risk: High
Dependencies: 10.4
Commit boundary: `portal: build vite spa with gradle`

Acceptance criteria:

- Story 10.1에서 확인한 Gradle baseline blocker가 해결되었거나, 사용자 승인 대기 중인 known blocker로 명시되어 있어야 한다.
- `settings.gradle` plugin management는 node-gradle plugin resolve가 가능하다.
- `observability-portal/build.gradle`에 node-gradle plugin을 적용한다.
- `nodeProjectDir = file("$rootDir/frontend")` 기준으로 npm install/build task를 구성한다.
- `processResources`는 `frontend/dist`를 jar `static/` root로 복사한다.
- frontend build failure는 portal `bootJar` failure로 드러난다.
- Gradle integration은 새 backend endpoint를 만들지 않는다.

Verification:

- `./gradlew :observability-portal:bootJar`
- jar 내용에서 `static/index.html`과 hashed asset 확인

### Story 10.6. Replace legacy dashboard and clean residue

Step: 5~6
Risk: Medium-High
Dependencies: 10.5, 수동 app verification
Commit boundary: `portal: replace legacy static dashboard`

Acceptance criteria:

- 기존 `observability-portal/src/main/resources/static/dashboard/index.html`, `app.js`, `styles.css`는 새 SPA 검증 이후 삭제한다.
- `DashboardStaticWebConfig`는 `/dashboard`, `/dashboard/`, `/docs`, `/docs/` 같은 알려진 client route만 `forward:/index.html`로 보낸다.
- `/api/**`, asset 경로, 확장자 있는 static resource request는 SPA fallback이 shadow하지 않는다.
- `/`는 static root index.html로 제공된다.
- 기존 `app.js`는 삭제 전까지 인증/fetch/race-guard 이식 parity 체크리스트로만 사용한다.
- `figmaAssetResolver`, unused `ImageWithFallback`, Figma-only guideline/pnpm residue를 제거한다.

Verification:

- 앱 기동 후 `/`, `/dashboard`, `/dashboard/`, `/docs`, `/docs/` 직접 진입/새로고침
- `/api/projects`가 SPA HTML이 아니라 401/JSON resource response를 반환하는지 확인
- `/assets/*.js`, `/assets/*.css` 같은 확장자 asset request가 fallback HTML로 forward되지 않는지 확인
- `rg -n "figma:asset|figmaAssetResolver|ImageWithFallback|pnpm" frontend observability-portal`
- `cd frontend && npm run build && npm run typecheck`
- `./gradlew :observability-portal:bootJar`

### Story 10.7. Run acceptance gate

Step: 7
Risk: High
Dependencies: 10.6
Commit boundary: `test: verify figma acceptance traceability`

Acceptance criteria:

- UI는 lifecycle state, starter connection diagnosis, p95/p99, endpoint priority, snapshot/history event를 재계산하지 않는다.
- accepted bucket freshness와 starter heartbeat/connection은 별도 축으로 표시된다.
- `triageCards=[]`는 zero-insight reason/action을 표시한다.
- Starter credential raw value는 create/rotation success 직후 1회만 표시된다.
- Token은 in-memory only이며 URL/cookie/localStorage/sessionStorage에 저장되지 않는다.
- starter ingest API는 docs/setup 안내에만 남고 dashboard UI에서 호출하지 않는다.
- 기존 API surface 외 endpoint 호출이 없다.

Verification:

- `cd frontend && npm run typecheck && npm run build`
- `./gradlew :observability-portal:bootJar`
- Browser 수동 QA: login, project, application, dashboard, evidence, trend/history, credential lifecycle
- 실제 state code별 렌더 QA와 concern/badge nested object 렌더 QA
- DevTools storage/token/credential 확인
- Network 탭 endpoint allow-list 확인

## 6. Suggested Order And Commit Boundaries

1. 10.1: `frontend: adopt figma export as vite workspace`
2. 10.2: `frontend: port auth and fetch foundation`
3. 10.3: `frontend: wire adapters navigation and dashboard`
4. 10.4: `frontend: wire evidence trend and credential surfaces`
5. 10.5: `portal: build vite spa with gradle`
6. 10.6: `portal: replace legacy static dashboard`
7. 10.7: `test: verify figma acceptance traceability`

Parallel 가능 후보:

- 10.3의 DTO/adapter 일부는 10.2 auth UI 작업과 병행 가능하다.
- 10.4의 evidence/trend/credential 내부 surface는 10.3 dashboard context가 닫힌 뒤 병행 가능하다.
- 10.6 cleanup은 10.5 build와 route/auth/API smoke verification 이후에만 수행한다.

Sequential 고정 후보:

- 기존 static dashboard 삭제는 10.5 build와 실제 route/auth/API 검증 이후에만 수행한다.
- Acceptance pass는 cleanup 이후 최종 검증으로 수행한다.

## 7. Verification Checklist

### Command verification

- `git status --short`
- `find observability-portal/src/main/java -name '* 2.java' -print`
- `cd frontend && npm install`
- `cd frontend && npm run typecheck`
- `cd frontend && npm run build`
- `./gradlew :observability-portal:bootJar`

### Manual verification

- `/` loads product entry.
- `/docs` loads docs route and explains starter setup without calling ingest API.
- `/dashboard` loads after refresh through SPA fallback.
- `/api/projects` is not shadowed by SPA fallback.
- Auth/API checks run on the Gradle-served Spring origin unless a Vite `/api` proxy decision is explicitly made.
- GitHub OAuth popup completes relay token exchange.
- Token is absent from URL, cookie, `localStorage`, `sessionStorage`.
- Project list calls `GET /api/projects`.
- Application list follows `links.applications`.
- Dashboard follows `links.dashboard`.
- Evidence follows `instances[].links.evidence`.
- Snapshot trend/history uses only existing endpoint templates.
- Credential create/rotation shows raw display value once and clears it after copy/confirm.
- 401 clears auth and dependent UI state.
- 404 does not reveal hidden project/application details or infer app health.
- `triageCards=[]` renders `zeroInsight`.
- accepted bucket freshness and starter heartbeat/connection remain separate.

### Static review checklist

- No `localStorage`/`sessionStorage` token persistence.
- No `document.cookie` auth persistence introduced by auth code.
- Existing shadcn sidebar UI preference cookie is allowed only if it does not store token, credential, account, project secret, or authorization state.
- No UI-side lifecycle state calculation.
- No UI-side p95/p99 calculation from histogram buckets.
- No endpoint priority sorting/ranking in UI.
- No snapshot/history event construction in UI.
- No dashboard UI call to `/api/ingest/v1/buckets` or `/api/ingest/v1/heartbeat`.
- No new `/api/*` backend route or Next.js API route.

## 8. Open Risks / Decisions

1. Current workspace has untracked duplicate Java files such as `DashboardStaticWebConfig 2.java`. Sprint kickoff should establish whether these are accidental local artifacts before Gradle build failures are attributed to frontend integration.
2. DTO shape in planning docs may lag actual Java records. Story 10.3 must verify real code for dashboard, evidence, trend, snapshot, credential DTOs immediately before implementation.
3. Figma dashboard components are built around seed state. Removing `dashboard-data.ts` seed may require careful prop boundary extraction to avoid a risky whole-component rewrite.
4. Auth popup behavior must be verified on the final Spring-served origin, not only Vite dev server.
5. SPA fallback must stay route allow-listed. A broad fallback can shadow `/api/**` or static assets.
6. Existing shadcn/ui sidebar code may use `document.cookie` for UI preference. This is not auth token persistence, but static review should confirm no auth/security value uses it.
7. If Gradle node plugin version or Node v25 compatibility causes build instability, pinning plugin/node behavior may need a small build-tooling decision.
8. Snapshot trend/history endpoint availability should be confirmed against current backend before UI implementation; if an endpoint is absent, do not create a new one inside this sprint without explicit product decision.
9. Vite dev server와 Spring server가 cross-origin으로 분리되면 `/api` 호출과 OAuth popup `postMessage` origin 검증이 깨질 수 있다. Auth/API 수동 검증은 Gradle-served build에서 수행하거나, 별도 Vite proxy 결정을 명시해야 한다.
10. Figma가 그리지 않는 dashboard read-model field가 있을 수 있다. 의도적으로 표시하지 않는 field와 반드시 surface에 포함할 field를 Story 10.3 착수 전에 목록화해야 한다.

## 9. Sprint Status Proposal

이 문서는 기존 `planning-artifacts/epics.md`나 `implementation-artifacts/sprint-status.yaml`을 직접 수정하지 않는다.

추후 별도 sprint tracking에 반영한다면 아래 key를 제안한다.

```yaml
epic-10: backlog
10-1-adopt-workspace-and-routing: backlog
10-2-port-auth-and-fetch-foundation: backlog
10-3-wire-types-adapters-navigation-and-dashboard: backlog
10-4-wire-evidence-trend-and-credential-surfaces: backlog
10-5-integrate-frontend-build-with-gradle: backlog
10-6-replace-legacy-dashboard-and-clean-residue: backlog
10-7-run-acceptance-gate: backlog
epic-10-retrospective: optional
```
