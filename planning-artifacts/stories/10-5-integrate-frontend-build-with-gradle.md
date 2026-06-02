---
artifactType: story
storyId: "10.5"
storyKey: "10-5-integrate-frontend-build-with-gradle"
epic: "Epic 10. Figma Make Acceptance And Frontend Hardening"
title: "Integrate frontend build with Gradle"
architectureStyle: "Vite SPA on Spring static root"
status: done
date: 2026-06-02
baselineCommits:
  story10_1: "cc7d87a frontend: adopt figma workspace and routing"
  story10_2: "7850d88 frontend: port auth and fetch foundation"
  story10_3: "e5b3ffa frontend: wire story 10.3 read models"
  story10_4: "992af31 frontend: wire story 10.4 surfaces"
commitBoundary: "portal: build vite spa with gradle"
---

# Story 10.5 - Integrate frontend build with Gradle

## Status

done

Gradle/Vite build integration implementation and final `bootJar`/jar content verification are complete. The previous `* 2.java` duplicate Java file blocker was resolved by deleting untracked duplicate copies that were byte-for-byte identical to their original `.java` files.

## Story

Gradle 기반 portal 구현자는 Story 10.1~10.4에서 완성된 `frontend/` Vite SPA를 `observability-portal` 빌드 생명주기에 통합하고 싶다.

그래야 `./gradlew :observability-portal:bootJar` 한 번으로 React/Vite production build가 실행되고, 생성된 `index.html`과 hashed assets가 Spring Boot jar의 classpath `static/` root에 포함되어 Story 10.6의 legacy static dashboard replacement와 Story 10.7 acceptance gate를 안전하게 진행할 수 있다.

## Source of Truth

아래 문서를 읽고 반영한 create-story context다. 충돌처럼 보이는 지점은 사용자 요청의 Story 10.5 범위와 Epic 10 sprint plan의 locked decision을 우선한다.

1. `AGENTS.md`
2. `_bmad/custom/project-context.md`
3. `implementation-artifacts/sprint-status.yaml`
4. `planning-artifacts/figma-make-acceptance-sprint-plan.md`
5. `planning-artifacts/stories/10-1-adopt-workspace-and-routing.md`
6. `planning-artifacts/stories/10-2-port-auth-and-fetch-foundation.md`
7. `planning-artifacts/stories/10-3-wire-types-adapters-navigation-and-dashboard.md`
8. `planning-artifacts/stories/10-4-wire-evidence-trend-and-credential-surfaces.md`
9. `planning-artifacts/acceptance-traceability.md`
10. `planning-artifacts/architecture.md`
11. `planning-artifacts/project-structure.md`
12. `frontend/package.json`
13. `frontend/vite.config.ts`
14. `settings.gradle`
15. `build.gradle`
16. `observability-portal/build.gradle`

확인한 외부 기술 reference:

- Gradle Plugin Portal의 `com.github.node-gradle.node` 최신 버전은 2026-06-02 기준 `7.1.0`이다. plugin DSL 예시는 `id("com.github.node-gradle.node") version "7.1.0"`이다. <https://plugins.gradle.org/plugin/com.github.node-gradle.node>
- node-gradle plugin 7.1.0 문서는 Node/npm/Yarn/pnpm을 Gradle build에 통합하며, `npmInstall` task가 `package.json`, lockfile, `node_modules` 변화를 기준으로 `npm install`을 실행한다고 설명한다. `nodeProjectDir`는 package.json과 node_modules가 있는 Node project directory다. <https://raw.githubusercontent.com/node-gradle/gradle-node-plugin/7.1.0/docs/usage.md>

## Current Code State

- 현재 브랜치 HEAD는 Story 10.4 구현 커밋 `992af31 frontend: wire story 10.4 surfaces`다.
- Story 10.4 커밋은 아래 frontend 파일을 수정/추가했다.
  - `frontend/src/app/components/dashboard.tsx`
  - `frontend/src/app/components/instance-panels.tsx`
  - `frontend/src/app/components/snapshot-detail-surface.tsx`
  - `frontend/src/app/lib/read-model-adapters.ts`
  - `frontend/src/app/lib/read-model-types.ts`
- `frontend/package.json`은 npm workspace 기준이며 script는 `build: vite build`, `typecheck: tsc -b --noEmit`, `dev: vite`다.
- `frontend/vite.config.ts`는 `base: '/'`, React plugin, Tailwind plugin, `@` alias, `figmaAssetResolver()`를 가진다. `figmaAssetResolver()` 제거는 Story 10.6 cleanup 범위다.
- `frontend/dist/`는 로컬에 이미 존재할 수 있지만 `.gitignore`에 포함되어 있다. Story 10.5 구현자는 `dist/`를 커밋하지 않고 Gradle task output으로만 다룬다.
- `settings.gradle`의 `pluginManagement.repositories`에는 이미 `gradlePluginPortal()`과 `mavenCentral()`이 있다. node-gradle plugin resolve를 위해 이 repository 구성을 보존해야 한다.
- root `build.gradle`은 `org.springframework.boot` plugin version을 `apply false`로 pin하고, Java 17 toolchain/test config를 subproject 공통으로 둔다.
- `observability-portal/build.gradle`은 `java`, `org.springframework.boot` plugin만 적용하고 `bootJar.archiveBaseName = 'observability-portal'`만 설정한다. 아직 node-gradle plugin, npm task, `processResources` copy integration은 없다.
- `observability-portal/src/main/resources/static/dashboard/`의 legacy `index.html`, `app.js`, `styles.css`는 아직 존재한다. 삭제/대체는 Story 10.6 범위다.
- 현재 `find observability-portal/src/main/java -name '* 2.java' -print`는 네 개의 duplicate Java file 후보를 반환한다. 이 파일들은 compile/bootJar blocker가 될 수 있으며 사용자 승인 없이 삭제/수정/되돌리지 않는다.

```text
observability-portal/src/main/java/com/observation/portal/config/DashboardStaticWebConfig 2.java
observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackTokenRelayRequest 2.java
observability-portal/src/main/java/com/observation/portal/domain/account/dto/GithubCallbackSessionResponse 2.java
observability-portal/src/main/java/com/observation/portal/domain/account/controller/GithubCallbackTokenRelay 2.java
```

## Scope

- Gradle에서 `frontend/` Vite SPA production build를 실행하도록 통합한다.
- node-gradle plugin resolve를 위해 plugin management repository와 plugin version pin을 확인/구성한다.
- `observability-portal/build.gradle`에 node-gradle plugin을 적용한다.
- `nodeProjectDir = file("$rootDir/frontend")` 기준으로 npm install/build task를 구성한다.
- `processResources`가 `frontend/dist`를 `static/` classpath root로 복사하게 한다.
- `bootJar` 실행 시 frontend build failure가 portal build failure로 드러나게 task dependency를 연결한다.
- `bootJar` 결과 jar 안에 `static/index.html`과 Vite hashed asset이 포함되는지 검증한다.

## Non-scope

- 새 backend endpoint, controller, service, repository, migration 생성
- Next.js 전환, Next.js API route 생성, Vite dev proxy 정책 추가
- legacy static dashboard 삭제, `/dashboard` fallback 교체, `DashboardStaticWebConfig` 정리
- `observability-portal/src/main/resources/static/dashboard/*` 수정 또는 삭제
- `frontend/` UI/read-model/auth/credential 기능 변경
- token/provider/starter credential secret storage 또는 URL 노출 surface 추가
- UI-side lifecycle state, starter connection diagnosis, p95/p99, endpointPriority, snapshot/history event 계산 추가
- `frontend/dist/`, `frontend/node_modules/`, Gradle build output 커밋
- 사용자 승인 없는 `* 2.java` duplicate Java file 후보 삭제/수정/되돌리기

## Acceptance Criteria

1. Story 10.1~10.4에서 이어진 `* 2.java` Gradle baseline blocker가 해결되었거나, Story 10.5 구현 기록에 known blocker로 명시되어 있어야 한다.
2. 구현자는 `find observability-portal/src/main/java -name '* 2.java' -print` 결과를 확인하고, duplicate Java file 후보를 사용자 승인 없이 삭제/수정/되돌리지 않는다.
3. `settings.gradle`의 `pluginManagement.repositories`는 `gradlePluginPortal()`을 유지해 `com.github.node-gradle.node` plugin resolve가 가능해야 한다.
4. node-gradle plugin version은 Gradle Plugin Portal 기준 최신 안정 버전 `7.1.0` 또는 구현 시점에 확인한 명시 버전으로 pin한다. 암묵적 latest, dynamic version, unpinned buildscript classpath를 쓰지 않는다.
5. 기존 root `build.gradle`의 plugin pin pattern을 따르면 `id 'com.github.node-gradle.node' version '7.1.0' apply false`를 추가하고, `observability-portal/build.gradle`에서는 version 없이 plugin을 적용한다.
6. 또는 `observability-portal/build.gradle` plugins block에서 직접 version을 지정할 수 있지만, 같은 plugin version이 여러 파일에 중복 pin되지 않아야 한다.
7. `observability-portal/build.gradle`에 `id 'com.github.node-gradle.node'` plugin이 적용된다.
8. node-gradle `node` extension은 `nodeProjectDir = file("$rootDir/frontend")`를 사용한다.
9. npm install은 plugin-provided `npmInstall` task를 사용한다. `pnpm`, `yarn`, shell-only `Exec` 기반 install로 우회하지 않는다.
10. frontend build task는 `npmInstall`에 의존하고 `npm run build`를 실행한다.
11. frontend build task는 `frontend/package.json`, `frontend/package-lock.json`, `frontend/index.html`, `frontend/vite.config.ts`, `frontend/tsconfig*.json`, `frontend/src/**`, `frontend/default_shadcn_theme.css`, `frontend/postcss.config.mjs` 변화를 입력으로 보고 `frontend/dist`를 output으로 둔다.
12. `processResources`는 frontend build task에 의존한다.
13. `processResources`는 `frontend/dist` 내용을 `static/` classpath root로 복사한다.
14. `processResources` copy는 `frontend/dist/index.html`을 classpath `static/index.html`로, `frontend/dist/assets/*`를 classpath `static/assets/*`로 배치한다.
15. `bootJar`는 Java lifecycle상 `processResources`를 거치며, frontend build 실패 시 `:observability-portal:bootJar`도 실패해야 한다.
16. `frontend/dist`가 없거나 오래된 경우에도 `bootJar`가 먼저 frontend build를 실행해야 한다.
17. jar 검증은 Spring Boot executable jar 내부 경로가 `BOOT-INF/classes/static/index.html` 및 `BOOT-INF/classes/static/assets/...`로 보일 수 있음을 감안해 확인한다. 이는 classpath root 기준 `static/index.html`과 hashed assets가 포함된 상태다.
18. Vite hashed asset 이름은 정확한 hash 문자열에 의존하지 않고 `static/assets/` 아래의 `*.js`, `*.css` 등 generated asset 존재와 `index.html` reference로 확인한다.
19. legacy `static/dashboard/*`는 jar에 남아도 된다. 이 story는 새 SPA를 `static/` root에 추가할 뿐 legacy dashboard replacement/cleanup을 수행하지 않는다.
20. Gradle integration은 새 backend endpoint, route fallback, controller, service, repository, migration을 만들지 않는다.
21. Gradle integration은 `frontend/` source code의 auth/API/read-model behavior를 바꾸지 않는다.
22. `frontend/` token/credential storage 정책은 Story 10.2~10.4 그대로 유지된다. build integration 때문에 token, provider token, starter credential raw value를 URL/storage/log/resource에 남기는 코드를 추가하지 않는다.
23. `cd frontend && npm run typecheck`가 통과해야 한다. Typecheck를 Gradle `bootJar` dependency로 추가할지는 별도 결정이며, 이 story의 최소 Gradle integration requirement는 `npm run build`다.
24. `cd frontend && npm run build`가 통과해야 한다.
25. `./gradlew :observability-portal:bootJar`가 통과해야 한다. `* 2.java` 때문에 실패하면 implementation notes에 known blocker로 명확히 기록하고, frontend integration 성공을 가장하지 않는다.
26. jar contents에서 `static/index.html`과 hashed asset inclusion을 확인한다.
27. `git diff --check`가 통과해야 한다.

## Tasks / Subtasks

- [x] Pre-flight와 baseline blocker 확인 (AC: 1, 2, 25)
  - [x] `git status --short`로 기존 modified/untracked 상태를 기록한다.
  - [x] `find observability-portal/src/main/java -name '* 2.java' -print` 결과를 기록한다.
  - [x] duplicate Java file 후보가 여전히 있으면 삭제/수정하지 않고 `bootJar` 검증 risk로 둔다.

- [x] node-gradle plugin resolve와 version pin 구성 (AC: 3~7)
  - [x] `settings.gradle`의 `pluginManagement.repositories`가 `gradlePluginPortal()`을 유지하는지 확인한다.
  - [x] root `build.gradle`에 `com.github.node-gradle.node` plugin version을 `apply false`로 pin하는 방식을 우선 검토한다.
  - [x] `observability-portal/build.gradle`에 node-gradle plugin을 적용한다.
  - [x] plugin version이 root와 subproject에 중복 선언되지 않게 한다.

- [x] frontend npm install/build Gradle task 구성 (AC: 8~11, 15, 16, 23, 24)
  - [x] `node { nodeProjectDir = file("$rootDir/frontend") }`를 설정한다.
  - [x] plugin-provided `npmInstall` task를 그대로 사용한다.
  - [x] `npm run build`를 실행하는 명시적 task를 구성한다. 권장 이름은 `frontendBuild` 또는 `npmRunBuild`다.
  - [x] task type은 node-gradle의 `NpmTask`를 사용한다. Groovy DSL에서 class 해석이 흔들리면 `com.github.gradle.node.npm.task.NpmTask`처럼 fully-qualified class를 사용한다.
  - [x] frontend build task inputs/outputs를 선언해 Gradle up-to-date 판단이 `frontend/` source와 `frontend/dist`에 맞게 동작하게 한다.
  - [x] `pnpm`, `yarn`, direct shell install/build, unrelated dependency upgrade를 추가하지 않는다.

- [x] `processResources` static root copy 연결 (AC: 12~19)
  - [x] `tasks.named('processResources')`가 frontend build task에 의존하게 한다.
  - [x] `from(file("$rootDir/frontend/dist")) { into 'static' }` 또는 동등 copy spec으로 `dist` 전체를 classpath `static/` root에 복사한다.
  - [x] legacy `src/main/resources/static/dashboard/*`는 그대로 둔다.
  - [x] `frontend/dist`를 source control 대상으로 추가하지 않는다.

- [x] Scope guard 정적 검토 (AC: 20~22)
  - [x] Java source, controller, service, repository, migration이 변경되지 않았는지 확인한다.
  - [x] Spring fallback/static dashboard replacement가 Story 10.5에 섞이지 않았는지 확인한다.
  - [x] frontend behavior 변경, token/credential storage, UI-side recomputation이 추가되지 않았는지 확인한다.

- [x] Verification 수행 및 기록 (AC: 23~27)
  - [x] `cd frontend && npm run typecheck`
  - [x] `cd frontend && npm run build`
  - [x] `./gradlew :observability-portal:bootJar`
  - [x] jar contents에서 `BOOT-INF/classes/static/index.html`과 `BOOT-INF/classes/static/assets/...` hashed asset을 확인한다.
  - [x] `find observability-portal/src/main/java -name '* 2.java' -print`
  - [x] `git diff --check`

## Dev Notes

### Architecture And Build Boundary

- Active architecture는 Traditional MVC + Service/Repository Layering이지만 Story 10.5는 build integration story다.
- `observability-portal`은 Spring Boot runtime deployable이며 dashboard UI를 portal static resource로 제공한다.
- UI는 server read model 표시자다. 이 story에서 UI 판단 계산, API 계약, persistence, auth 정책을 바꾸면 안 된다.
- `processResources` output은 Java classpath resource root다. Spring Boot executable jar에서는 `BOOT-INF/classes/static/...` 경로로 보일 수 있다.
- `bootJar`가 `classes`/`processResources` lifecycle을 거치므로 `processResources.dependsOn(frontendBuild)`가 frontend failure를 `bootJar` failure로 연결하는 핵심이다.

### Recommended Gradle Shape

기존 root `build.gradle`이 Spring Boot plugin version을 `apply false`로 pin하므로, node-gradle도 같은 방식이 가장 일관적이다.

```gradle
// root build.gradle
plugins {
    id 'org.springframework.boot' version '4.0.6' apply false
    id 'com.github.node-gradle.node' version '7.1.0' apply false
}
```

```gradle
// observability-portal/build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot'
    id 'com.github.node-gradle.node'
}

node {
    nodeProjectDir = file("$rootDir/frontend")
}

tasks.register('frontendBuild', com.github.gradle.node.npm.task.NpmTask) {
    dependsOn tasks.named('npmInstall')
    args = ['run', 'build']
    inputs.files(file("$rootDir/frontend/package.json"), file("$rootDir/frontend/package-lock.json"))
    inputs.files(fileTree("$rootDir/frontend/src"))
    inputs.files(fileTree("$rootDir/frontend") {
        include 'index.html'
        include 'vite.config.ts'
        include 'tsconfig*.json'
        include 'postcss.config.mjs'
        include 'default_shadcn_theme.css'
    })
    outputs.dir(file("$rootDir/frontend/dist"))
}

tasks.named('processResources') {
    dependsOn tasks.named('frontendBuild')
    from(file("$rootDir/frontend/dist")) {
        into 'static'
    }
}
```

위 snippet은 구현 방향을 명확히 하기 위한 후보이며, 실제 구현자는 Gradle 9.5.0/Groovy DSL에서 동작하도록 필요하면 task 이름과 input declaration을 조정할 수 있다. 핵심은 node-gradle plugin, `nodeProjectDir`, npm install/build dependency, `processResources` copy, `bootJar` failure propagation이다.

### Previous Story Intelligence

- Story 10.1은 `frontend/`를 npm-only Vite SPA workspace로 만들고 `frontend/dist/`를 build output으로 생성했다. `dist/`는 `.gitignore` 대상이다.
- Story 10.1부터 `* 2.java` duplicate Java file 후보가 compile blocker로 기록됐다. Story 10.5는 이 blocker를 해결하거나 known blocker로 명시해야 하지만 사용자 승인 없이 만지지 않는다.
- Story 10.2는 token을 React memory state에만 두고 URL/cookie/localStorage/sessionStorage에 저장하지 않는 auth/fetch foundation을 만들었다. Build integration은 이 정책을 바꾸지 않는다.
- Story 10.3은 Project/Application/Dashboard를 server-provided links로 연결하고 mock seed를 제거했다. Build integration은 frontend source behavior를 재배선하지 않는다.
- Story 10.4는 evidence/trend/history/credential surfaces를 기존 endpoint로 연결했고, `displayValue` one-time credential handling을 유지했다. Build integration은 credential display/storage 코드를 건드리지 않는다.
- Story 10.6은 legacy dashboard 삭제와 Spring route fallback replacement를 담당한다. Story 10.5가 legacy cleanup까지 하면 commit boundary가 깨진다.

### File Candidates

수정 후보:

- `settings.gradle`
  - 이미 `gradlePluginPortal()`이 있으므로 보존 확인이 주된 작업이다. plugin resolve에 문제가 있을 때만 최소 수정한다.
- `build.gradle`
  - `com.github.node-gradle.node` plugin version `apply false` pin 후보.
- `observability-portal/build.gradle`
  - node-gradle plugin 적용, `nodeProjectDir`, frontend build task, `processResources` copy integration.

검증/참조 후보:

- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/vite.config.ts`
- `frontend/dist/index.html`
- `observability-portal/build/libs/observability-portal-0.1.0-SNAPSHOT.jar`

수정하지 않을 파일/영역:

- `observability-portal/src/main/java/**`
- `observability-portal/src/main/resources/static/dashboard/**`
- `observability-portal/src/main/resources/db/migration/**`
- `frontend/src/**` behavior files
- `frontend/dist/**` generated output
- `frontend/node_modules/**`

### Jar Verification Hints

권장 jar 검증 command 예시:

```bash
jar tf observability-portal/build/libs/observability-portal-0.1.0-SNAPSHOT.jar | rg 'BOOT-INF/classes/static/(index\.html|assets/.+)'
```

기대 결과:

- `BOOT-INF/classes/static/index.html`
- `BOOT-INF/classes/static/assets/index-<hash>.js` 또는 동등 Vite hashed JS asset
- `BOOT-INF/classes/static/assets/index-<hash>.css` 또는 동등 Vite hashed CSS asset

Vite hash는 build마다 달라질 수 있으므로 exact filename을 AC로 고정하지 않는다.

### Guardrails

- `* 2.java` duplicate Java file 후보는 build blocker일 수 있다. 해결되었거나 known blocker로 명시되어야 하며, 사용자 승인 없이 삭제/수정/되돌리지 않는다.
- `processResources` copy는 generated `frontend/dist`만 대상으로 한다. `frontend/src`, `node_modules`, package metadata를 jar에 복사하지 않는다.
- `frontend/dist`를 Git에 추가하지 않는다.
- 새 backend endpoint나 route fallback을 만들지 않는다.
- legacy static dashboard 삭제/대체는 Story 10.6으로 넘긴다.
- token/provider/starter credential secret storage나 URL 노출을 새로 만들지 않는다.
- UI-side lifecycle state, p95/p99, endpointPriority, snapshot/history event 계산을 만들지 않는다.
- unrelated modified/untracked 파일은 건드리지 않는다.
- AGENTS.md 기준으로 새 주석이 필요하면 한국어로 작성하되, 이 story의 구현은 build.gradle 중심이라 과도한 주석은 피한다.

## Verification

필수 command:

```bash
cd frontend && npm run typecheck
cd frontend && npm run build
./gradlew :observability-portal:bootJar
jar tf observability-portal/build/libs/observability-portal-0.1.0-SNAPSHOT.jar | rg 'BOOT-INF/classes/static/(index\.html|assets/.+)'
find observability-portal/src/main/java -name '* 2.java' -print
git diff --check
```

검증 기준:

- `npm run typecheck`와 `npm run build`가 각각 통과한다.
- `bootJar`가 frontend build를 선행 실행한다.
- frontend build 실패를 의도적으로 만들면 `bootJar`도 실패해야 한다. 수동 failure injection은 구현자가 필요하다고 판단할 때만 수행하고 반드시 되돌린다.
- jar content에는 classpath static root 기준 `static/index.html`과 Vite hashed asset이 있어야 한다.
- `find ... '* 2.java'` 결과가 남아 있으면 known blocker로 기록한다.
- `git diff --check`는 whitespace error 없이 통과한다.

## Open Risks / Blockers

1. `implementation-artifacts/sprint-status.yaml`은 Story 10.4 상태 전환 때문에 이미 modified 상태였다. Story 10.5 구현자는 unrelated status 변경을 되돌리지 않는다.
2. node-gradle plugin 7.1.0은 공식 최신 안정 버전이지만, 로컬 Gradle은 9.5.0이고 Node/npm은 Node v25.8.2/npm 11.11.1이다. plugin resolve 또는 npm execution 문제가 있으면 version pin, local/global Node 사용, `download=true` 여부를 implementation notes에 명시한다.
3. Story 10.6 전까지 legacy `static/dashboard/*`와 새 `static/index.html`이 jar에 함께 들어간다. 이는 의도된 중간 상태다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- 2026-06-02: BMAD dev-story workflow customization, config, persistent project context를 확인했다.
- 2026-06-02: 요청된 AGENTS.md, project-context.md, sprint-status.yaml, Story 10.5, Epic 10 sprint plan, Story 10.1~10.4, acceptance traceability, architecture, project structure, frontend package/Vite config, Gradle settings/build files를 읽었다.
- 2026-06-02: `implementation-artifacts/sprint-status.yaml`에서 `10-5-integrate-frontend-build-with-gradle`을 `in-progress`로 전환했다.
- 2026-06-02: Pre-flight `git status --short`에서 기존 unrelated/untracked `=`, root `README.md`, `docs/`, `planning-artifacts/figma-make-nextjs-frontend-spec.md`, Story 10.4/10.5 story file, 네 개의 `* 2.java` 후보를 확인했다. 이 작업이 만든 변경은 sprint-status 갱신뿐이었다.
- 2026-06-02: Pre-flight `find observability-portal/src/main/java -name '* 2.java' -print`에서 duplicate Java file 후보 네 개가 그대로 확인됐다.
- 2026-06-02: `DashboardStaticWebConfig 2.java`, `GithubCallbackTokenRelayRequest 2.java`, `GithubCallbackSessionResponse 2.java`, `GithubCallbackTokenRelay 2.java`는 사용자 승인 없이 삭제/수정/되돌리지 않고 `bootJar` known risk로 둔다.
- 2026-06-02: RED check `./gradlew :observability-portal:frontendBuild --dry-run`은 task 미존재로 실패했다.
- 2026-06-02: root `build.gradle`에 `com.github.node-gradle.node` version `7.1.0`을 `apply false`로 pin하고, `observability-portal/build.gradle`에는 version 없이 plugin을 적용했다.
- 2026-06-02: `nodeProjectDir = file("$rootDir/frontend")`, `frontendBuild` `NpmTask`, `npmInstall` 의존성, frontend source/input과 `frontend/dist` output 선언을 추가했다.
- 2026-06-02: `processResources`가 `frontendBuild`에 의존하고 `frontend/dist`를 classpath `static/` root로 복사하도록 연결했다.
- 2026-06-02: `./gradlew :observability-portal:frontendBuild --dry-run` 성공. task graph에서 `nodeSetup`, `npmSetup`, `npmInstall`, `frontendBuild` 순서를 확인했다.
- 2026-06-02: `./gradlew :observability-portal:processResources --dry-run` 성공. `processResources`가 frontend build task 뒤에 실행되는 task graph를 확인했다.
- 2026-06-02: `./gradlew :observability-portal:frontendBuild` 성공. plugin-provided `npmInstall` 후 Vite build가 `dist/index.html`, `dist/assets/index-afIJg7Nf.css`, `dist/assets/index-Bjvo8HCE.js`를 생성했다.
- 2026-06-02: `./gradlew :observability-portal:processResources` 성공. build resource output에서 `static/index.html`, `static/assets/index-afIJg7Nf.css`, `static/assets/index-Bjvo8HCE.js`와 legacy `static/dashboard/*`가 함께 확인됐다.
- 2026-06-02: `npmInstall`이 `frontend/package-lock.json`의 extraneous `yaml` 항목을 제거하는 churn을 만들었으나 Story 10.5 dependency 변경 범위가 아니므로 해당 lockfile 변경은 원상 복구했다.
- 2026-06-02: `git diff --name-only` 기준 추적 파일 diff는 `build.gradle`, `implementation-artifacts/sprint-status.yaml`, `observability-portal/build.gradle`뿐이다. Java source, controller/service/repository, migration, frontend source, legacy `static/dashboard/*`는 변경하지 않았다.
- 2026-06-02: `cd frontend && npm run typecheck` 성공.
- 2026-06-02: `cd frontend && npm run build` 성공. Vite build output은 `dist/index.html`, `dist/assets/index-afIJg7Nf.css`, `dist/assets/index-Bjvo8HCE.js`다.
- 2026-06-02: `./gradlew :observability-portal:bootJar`는 `:observability-portal:compileJava`에서 기존 `* 2.java` duplicate Java files의 public class/file name mismatch와 duplicate class errors로 실패했다. 이 파일들은 사용자 승인 없이 수정하지 않았다.
- 2026-06-02: `./gradlew :observability-portal:bootJar --dry-run`에서 `compileJava`, `npmInstall`, `frontendBuild`, `processResources`, `classes`, `bootJar` task graph를 확인했다. 실제 bootJar는 compile blocker 때문에 `frontendBuild/processResources` 실행 전 중단된다.
- 2026-06-02: jar content 확인 명령은 `observability-portal/build/libs/observability-portal-0.1.0-SNAPSHOT.jar`가 생성되지 않아 `NoSuchFileException`으로 실패했다.
- 2026-06-02: `find observability-portal/src/main/java -name '* 2.java' -print`는 known blocker 네 개를 그대로 반환했다.
- 2026-06-02: `git diff --check` 성공.
- 2026-06-02: 사용자 승인 후 네 개의 untracked `* 2.java` duplicate Java file 후보가 원본과 byte-for-byte 동일함을 확인하고 삭제했다.
- 2026-06-02: node-gradle plugin-provided `npmInstall`이 `package-lock.json` churn을 만들지 않도록 `npmInstallCommand = 'ci'`를 설정했다.
- 2026-06-02: `./gradlew :observability-portal:bootJar --rerun-tasks` 성공. `npm ci`, `npm run build`, `processResources`, `bootJar`가 모두 실행됐다.
- 2026-06-02: jar contents에서 `BOOT-INF/classes/static/index.html`, `BOOT-INF/classes/static/assets/index-Bjvo8HCE.js`, `BOOT-INF/classes/static/assets/index-afIJg7Nf.css`를 확인했다.
- 2026-06-02: `find observability-portal/src/main/java -name '* 2.java' -print` 결과가 비어 있음을 확인했다.
- 2026-06-02: `frontend/package-lock.json` diff가 비어 있고 `git diff --check`가 통과했다.

### Implementation Plan

1. Pre-flight로 git status와 `* 2.java` blocker를 기록한다.
2. node-gradle plugin version pin과 `observability-portal` plugin 적용을 구성한다.
3. `nodeProjectDir = file("$rootDir/frontend")`, npm install, npm run build task를 연결한다.
4. `processResources`에 `frontend/dist -> static/` copy를 추가한다.
5. typecheck/build/bootJar/jar content/diff guard를 검증하고 known blocker 여부를 기록한다.

### Completion Notes List

- Pre-flight에서 기존 workspace 변경과 duplicate Java baseline blocker를 분리해 기록했다.
- `* 2.java` duplicate Java file 후보 네 개는 여전히 남아 있으며 이번 story에서 수정하지 않는다.
- node-gradle plugin version은 root `build.gradle`에서 `7.1.0`으로 한 번만 pin하고, portal subproject에서는 version 없이 적용했다.
- `frontendBuild` Gradle task는 plugin-provided `npmInstall`에 의존하며 `npm run build`를 실행한다.
- `processResources`는 `frontendBuild`에 의존하고 `frontend/dist` 전체를 classpath `static/` root로 복사한다.
- Scope guard 결과, Java/backend endpoint/fallback/legacy dashboard/frontend behavior/token/credential/read-model 계산 변경은 없다.
- 필수 frontend verification인 `npm run typecheck`와 `npm run build`는 통과했다.
- 기존 duplicate Java baseline blocker는 사용자 승인 후 untracked duplicate copies를 삭제해 해결했다.
- `npmInstall`은 plugin-provided task를 유지하되 `npm ci`로 실행해 lockfile churn을 막는다.
- `./gradlew :observability-portal:bootJar --rerun-tasks`와 jar content 확인이 통과했다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/10-5-integrate-frontend-build-with-gradle.md`
- `build.gradle`
- `observability-portal/build.gradle`

### Change Log

- 2026-06-02: Story 10.5 create-story 산출물을 생성했다. Gradle/node-gradle/Vite build integration 범위, AC, task, verification, duplicate Java blocker guardrail을 확정했다.
- 2026-06-02: Story 10.5 구현을 시작하고 pre-flight workspace 상태와 duplicate Java known risk를 기록했다.
- 2026-06-02: node-gradle plugin pin, portal frontend build task, processResources static copy integration을 구현했다.
- 2026-06-02: frontend verification과 Gradle task graph/static resource copy를 확인했다.
- 2026-06-02: 기존 untracked `* 2.java` duplicate Java blocker를 사용자 승인 후 제거하고 final bootJar/jar verification을 완료해 story를 done으로 전환했다.
