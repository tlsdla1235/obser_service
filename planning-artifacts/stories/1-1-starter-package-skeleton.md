---
artifactType: story
storyId: "1.1"
epic: "Epic 1. Architecture Foundation"
title: "Starter Package Skeleton"
architectureStyle: Traditional MVC
status: review
date: 2026-05-10
---

# Story 1.1 - Starter Package Skeleton

## User Story

구현자로서, Epic 2의 direct ingest producer 기능을 구현하기 전에 starter module과 package foundation을 먼저 고정하고 싶다.

## Scope

이 story는 starter 구현의 시작점이다. 기능 구현을 만들지 않고, module/build/test/package marker만 마련한다.

포함:

- `observability-spring-boot-starter` module 생성
- starter base package를 `com.observation.starter`로 고정
- starter package skeleton 생성
- skeleton package를 `package-info.java` marker로 추적 가능하게 구성
- Gradle settings에 starter module 포함
- starter module test task가 실행되는 최소 smoke test
- 기존 portal MVC guard가 계속 통과하는지 확인

제외:

- Micrometer observation binding 구현
- queue flush 구현
- HTTP ingest 전송 구현
- retry/backoff 구현
- host request interception behavior 구현
- 30초 bucket rollup 구현
- portal MVC package 구조 변경
- `application`, `port`, `adapter` package 생성

## Source Artifacts

- `_bmad/custom/project-context.md`
- `implementation-artifacts/epic-1-retro-2026-05-10.md`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/epics.md`
- `planning-artifacts/project-structure.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/acceptance-traceability.md`
- `planning-artifacts/stories/1-2-portal-package-skeleton.md`
- `planning-artifacts/stories/1-3-architecture-guard-test.md`
- `planning-artifacts/stories/1-4-portal-physical-schema-foundation.md`

## Implementation Notes

- 최종 아키텍처 선택은 Traditional MVC + Service/Repository Layering 하나다.
- starter는 host Spring Boot app 안에 붙는 library/starter module이다.
- starter에는 MVC web controller를 만들지 않는다.
- 이번 story에서는 starter behavior class를 만들지 않고 package marker와 smoke test만 둔다.
- Gradle group은 기존 root build 기준인 `com.sst`를 따른다.
- starter Java package는 `com.observation.starter`다.
- Java 빈 package는 Git에서 사라질 수 있으므로 `package-info.java` marker를 둔다.
- `domain`은 portal 쪽 feature grouping namespace이며, starter에는 `domain/application/port/adapter` 경계를 만들지 않는다.
- Epic 2의 Micrometer binding, queue, HTTP client, rollup, retry/backoff 구현은 이 story 이후에 수행한다.
- Story 2.1에서는 starter module bootstrap을 다시 포함하지 않아야 한다.

## Required Package Skeleton

```text
com.observation.starter
  model
  service
  spring
  client
    http
  queue
  config
```

금지 package:

- `application`
- `port`
- `adapter`
- `adapter.in`
- `adapter.out`

## Acceptance Criteria

1. `observability-spring-boot-starter` module이 존재한다.
2. `settings.gradle`는 `observability-spring-boot-starter` module을 build/test 대상으로 인식한다.
3. starter main source tree와 test source tree가 존재한다.
4. starter base package는 `com.observation.starter`로 확정된다.
5. `model`, `service`, `spring`, `client.http`, `queue`, `config` package가 존재하고 marker source로 추적 가능하다.
6. starter skeleton 상태에서 module test task가 성공한다.
7. root `./gradlew test` 또는 필요한 module test에서 기존 portal MVC guard가 계속 통과한다.
8. `application`, `port`, `adapter` package를 만들지 않는다.
9. 이 story에서는 Micrometer binding, queue flush, HTTP ingest 전송, retry/backoff, host request interception, 30초 bucket rollup을 구현하지 않는다.
10. Story 2.1에서 starter bootstrap을 제외해야 한다는 후속 메모가 완료 기록에 남는다.

## Suggested Tasks

1. repo의 현재 build/story 상태를 확인한다.
2. `observability-spring-boot-starter` Gradle module을 추가한다.
3. starter main/test source directory를 만든다.
4. required package skeleton을 `package-info.java` marker로 만든다.
5. skeleton 상태에서 test command가 성공하도록 최소 smoke test를 추가한다.
6. `application/port/adapter` package가 생성되지 않았는지 확인한다.
7. 기존 portal MVC guard가 계속 통과하는지 확인한다.
8. Story 2.1 handoff에 starter bootstrap 제외 메모를 남긴다.

## Test Requirements

- starter module smoke test
- root 또는 module test task
- 기존 portal MVC architecture guard test
- 권장 실행 명령은 `./gradlew test`다.

## Developer Guardrails

- starter package foundation 이외의 runtime behavior를 만들지 않는다.
- `service` package에 network call, retry/backoff, rollup 구현 class를 만들지 않는다.
- `client.http` package에는 marker만 두고 HTTP client behavior를 만들지 않는다.
- `queue` package에는 marker만 두고 flush worker나 bounded queue 구현을 만들지 않는다.
- portal MVC package 구조와 migration을 변경하지 않는다.
- `application`, `port`, `adapter` package를 만들지 않는다.

## Tasks/Subtasks

- [x] repo의 현재 build/story 상태를 확인한다.
- [x] `observability-spring-boot-starter` Gradle module을 추가한다.
- [x] starter main/test source directory를 만든다.
- [x] required package skeleton을 `package-info.java` marker로 만든다.
- [x] skeleton 상태에서 test command가 성공하도록 최소 smoke test를 추가한다.
- [x] `application/port/adapter` package가 생성되지 않았는지 확인한다.
- [x] 기존 portal MVC guard가 계속 통과하는지 확인한다.
- [x] Story 2.1 handoff에 starter bootstrap 제외 메모를 남긴다.

## Dev Agent Record

### Implementation Plan

- 기존 Gradle Groovy DSL multi-module build를 유지한다.
- `settings.gradle`에 `observability-spring-boot-starter` module을 추가한다.
- starter module은 `java-library` 기반 skeleton으로 두고 Spring Boot application entrypoint를 만들지 않는다.
- `com.observation.starter` 아래 required package마다 Javadoc이 있는 `package-info.java` marker를 둔다.
- smoke test는 `package-info.java` source marker가 유지되는지만 검증하고 runtime behavior는 검증하지 않는다.

### Debug Log

- 2026-05-10: Story 1.1 문서가 없어 신규 생성했고 sprint status를 `in-progress`로 갱신했다.
- 2026-05-10: Red phase로 `StarterModuleSmokeTest`를 추가한 뒤 `./gradlew :observability-spring-boot-starter:test`를 실행했고, Javadoc-only `package-info.java`가 classpath marker로 로드되지 않아 smoke test가 실패함을 확인했다.
- 2026-05-10: smoke test를 source marker 파일 존재 검증으로 조정한 뒤 `./gradlew :observability-spring-boot-starter:test` 실행 결과 `BUILD SUCCESSFUL`, 3 actionable tasks 중 2 executed, 1 up-to-date.
- 2026-05-10: `./gradlew test` 실행 결과 `BUILD SUCCESSFUL`, 7 actionable tasks up-to-date.
- 2026-05-10: `./gradlew test --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, 7 actionable tasks executed.
- 2026-05-10: starter source tree에 `application`, `port`, `adapter` package가 없음을 확인했다.

### Completion Notes

- `observability-spring-boot-starter` module을 Gradle build/test 대상에 추가했다.
- `com.observation.starter` base package와 `model`, `service`, `spring`, `client.http`, `queue`, `config` skeleton marker를 추가했다.
- package marker에는 starter foundation에서 맡을 역할을 짧은 Javadoc으로 남겼다.
- starter module smoke test를 추가해 skeleton marker가 source tree에 유지되는지 검증했다.
- root `./gradlew test --rerun-tasks`로 기존 portal MVC guard와 starter smoke test가 함께 통과함을 확인했다.
- Story 2.1에서는 starter module/package bootstrap을 scope에서 제외하고 Micrometer observation binding 자체에 집중해야 한다.

## File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/1-1-starter-package-skeleton.md`
- `settings.gradle`
- `observability-spring-boot-starter/build.gradle`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/http/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/config/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/queue/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/spring/package-info.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/StarterModuleSmokeTest.java`

## Change Log

- 2026-05-10: Story 1.1 story document created and implementation started.
- 2026-05-10: Story 1.1 starter Gradle module, package markers, and smoke test added.

## Status

review
