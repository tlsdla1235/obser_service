---
artifactType: story
storyId: "2.1"
epic: "Epic 2. Starter Direct Ingest Producer"
title: "Micrometer Observation Binding"
architectureStyle: Traditional MVC
status: done
date: 2026-05-10
---

# Story 2.1 - Micrometer Observation Binding

## User Story

구현자로서, starter가 host Spring Boot app의 Micrometer/Spring observation signal을 받아 이후 rollup 단계가 사용할 내부 observation input으로 변환하길 원한다.

## Scope

이 story는 Epic 2의 첫 기능 story다. Story 1.1에서 만든 starter module/package skeleton을 사용하며, starter bootstrap을 다시 구현하지 않는다.

포함:

- `observability-spring-boot-starter` 기존 module 사용
- Micrometer/Spring observation binding에 필요한 starter dependency 추가
- HTTP server observation을 starter service input으로 변환하는 binding 경계 추가
- JVM과 datasource app-level metric sample을 받을 수 있는 최소 collection 경계 추가
- request path에서 portal network call을 하지 않는 구조 유지
- synthetic observation 기반 binding test

제외:

- starter module/package skeleton 재생성
- route normalization policy 완성
- 30초 bucket rollup
- bounded queue와 async flush worker
- HTTP ingest client
- retry/backoff
- ingest envelope builder
- portal ingest API/controller/repository
- Prometheus scrape/export/query path

## Source Artifacts

- `_bmad/custom/project-context.md`
- `planning-artifacts/sprint-plan.md`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/epics.md`
- `planning-artifacts/project-structure.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/architecture-implementation-supplement.md`
- `planning-artifacts/acceptance-traceability.md`
- `planning-artifacts/contracts/metric-taxonomy.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/stories/1-1-starter-package-skeleton.md`

## Dependencies

- Story 1.1 review 산출물: `observability-spring-boot-starter` module, `com.observation.starter` base package, starter package markers.
- Story 2.2가 route normalization과 low-cardinality guard를 닫는다.
- Story 2.3이 30초 bucket rollup을 닫는다.
- Story 2.4가 non-blocking flush proof를 닫는다.

## Implementation Notes

- starter는 host app 안에 붙는 library/starter module이다.
- 사용자 host app과의 호환성을 위해 프로젝트 Java baseline은 17로 둔다.
- starter에는 MVC web controller를 만들지 않는다.
- starter에는 `domain`, `application`, `port`, `adapter` package를 만들지 않는다.
- Spring/Micrometer 객체는 `com.observation.starter.spring.observation` 경계에서 starter 내부 model 또는 service input으로 변환한다.
- long-lived model에는 servlet request/response 객체나 raw path를 저장하지 않는다.
- Story 2.1의 binding은 observation signal을 local service로 넘기는 단계까지만 담당한다.
- route normalization 최종 정책은 Story 2.2에서 구현한다.
- request path에서는 portal HTTP client, queue flush, envelope serialization을 호출하지 않는다.

## Acceptance Criteria

1. Story 2.1은 기존 `observability-spring-boot-starter` module에서 시작하고 starter bootstrap을 다시 포함하지 않는다.
2. starter binding은 HTTP server request method, status/error signal, duration sample을 internal observation input으로 변환한다.
3. JVM CPU/heap, datasource pool usage sample을 app-level metric input으로 받을 수 있는 collection 경계가 있다.
4. binding layer는 servlet request/response 객체를 starter model에 장기 보관하지 않는다.
5. raw path parameter와 arbitrary tag를 payload 후보로 확정하지 않는다. 최종 route/tag guard는 Story 2.2로 위임한다.
6. request path에서 portal network call을 수행하지 않는다.
7. 이 story에서는 bounded queue, async flush worker, HTTP ingest client, ingest envelope builder를 구현하지 않는다.
8. Micrometer/Spring binding test는 synthetic observation 또는 framework test fixture로 signal 변환을 검증한다.
9. `application`, `port`, `adapter` package가 생성되지 않는다.
10. 기존 portal test와 starter smoke test가 계속 통과한다.

## Suggested Tasks

1. Story 1.1의 starter module 상태와 package skeleton을 확인한다.
2. starter module에 Micrometer/Spring observation binding에 필요한 최소 dependency를 추가한다.
3. HTTP observation input model 또는 command shape를 정의한다.
4. JVM/datasource app-level sample input shape를 정의한다.
5. Spring/Micrometer binding component를 `spring.observation` 아래에 추가한다.
6. binding component가 internal service boundary에 sample을 넘기도록 구성한다.
7. request path에서 `client.http`나 network client를 호출하지 않는지 테스트 또는 architecture guard로 확인한다.
8. synthetic observation binding test를 추가한다.
9. forbidden package가 없는지 확인한다.
10. `./gradlew test` 또는 관련 module test를 실행한다.

## Test Requirements

- Micrometer/Spring observation binding unit or slice test
- JVM/datasource metric sample mapping test
- starter module smoke test
- forbidden package guard
- 기존 portal MVC architecture guard
- 권장 실행 명령: `./gradlew test`

## Developer Guardrails

- 이 story에서 starter module bootstrap을 다시 하지 않는다.
- `spring.observation` binding이 portal HTTP client를 직접 참조하지 않게 한다.
- service/model에 servlet request/response 객체를 보관하지 않는다.
- route normalization 최종 결정을 임의로 우회하지 않는다.
- queue, flush worker, retry/backoff, envelope builder를 당겨오지 않는다.
- Prometheus dependency, scrape endpoint, query UI dependency를 추가하지 않는다.
- 구현 시점의 Spring Boot/Micrometer API는 현재 project build와 호환되는 공식 dependency 기준으로 확인한다.

## Tasks/Subtasks

- [x] Story 1.1 starter module 상태와 package skeleton을 확인한다.
- [x] starter module에 필요한 Micrometer/Spring observation dependency를 추가한다.
- [x] HTTP observation input model 또는 command shape를 정의한다.
- [x] JVM/datasource app-level sample input shape를 정의한다.
- [x] `spring.observation` binding component를 추가한다.
- [x] binding component와 starter service boundary를 연결한다.
- [x] request path network call 금지 guard를 추가하거나 기존 test로 증명한다.
- [x] synthetic observation binding test를 추가한다.
- [x] forbidden package가 없는지 확인한다.
- [x] `./gradlew test` 또는 관련 module test를 실행한다.

## Dev Agent Record

### Implementation Plan

- Story 1.1 review 산출물인 기존 `observability-spring-boot-starter` module과 package skeleton을 그대로 사용한다.
- starter main dependency는 Micrometer observation API만 최소 추가하고, Spring Boot app/bootstrap이나 MVC controller를 만들지 않는다.
- `spring.observation` package에는 Micrometer `ObservationHandler` binding을 두고, HTTP server observation을 internal model로 변환한다.
- `model.metric`에는 HTTP, JVM, datasource app-level sample input을 정의하고, `service`에는 local collector boundary를 둔다.
- request path network call 금지는 `spring.observation` binding이 `client.http`에 의존하지 않는 architecture test로 고정한다.

### Debug Log

- 2026-05-10: `git status --short --branch`로 기존 변경이 없고 `main...origin/main` 상태임을 확인한 뒤 `codex/story-2-1-micrometer-observation-binding` 브랜치를 생성했다.
- 2026-05-10: 필수 MVC 산출물과 Story 1.1/2.1 문서를 읽고, Story 2.1 sprint status를 `in-progress`로 갱신했다.
- 2026-05-10: Red phase로 synthetic Micrometer HTTP observation test와 starter architecture guard test를 추가한 뒤 `./gradlew :observability-spring-boot-starter:test`를 실행했고, 아직 구현되지 않은 model/binder/service boundary 때문에 compile failure가 발생함을 확인했다.
- 2026-05-10: `micrometer-observation` dependency, HTTP/JVM/datasource input model, `ObservationSampleCollector`, `MicrometerHttpServerObservationBinder`를 추가했다.
- 2026-05-10: `./gradlew :observability-spring-boot-starter:test` 실행 결과 `BUILD SUCCESSFUL`, 3 actionable tasks executed.
- 2026-05-10: starter source directory와 source search로 `application`, `port`, `adapter` package가 없고 binding code가 portal HTTP client, queue flush, envelope builder를 구현하지 않았음을 확인했다.
- 2026-05-10: `./gradlew test` 실행 결과 `BUILD SUCCESSFUL`, 7 actionable tasks up-to-date.
- 2026-05-10: `./gradlew test --rerun-tasks` 실행 결과 `BUILD SUCCESSFUL`, 7 actionable tasks executed.

### Completion Notes

- 기존 starter module에서 시작했고 module/package bootstrap은 다시 구현하지 않았다.
- Micrometer `ObservationHandler` 기반 HTTP server observation binding을 추가해 method, status/error signal, duration, framework route pattern candidate를 internal `HttpServerObservationInput`으로 변환한다.
- raw `path`와 arbitrary/high-cardinality tag는 route candidate로 사용하지 않고, 최종 route/tag guard는 Story 2.2로 남겼다.
- JVM CPU/heap과 datasource pool usage를 받을 수 있는 app-level sample model과 collector boundary를 추가했다.
- request path에서 portal network call, bounded queue/flush worker, HTTP ingest client, retry/backoff, envelope builder를 구현하지 않았고 architecture guard로 binding의 `client.http` 의존을 금지했다.
- forbidden package(`application`, `port`, `adapter`)가 생성되지 않았고 starter/portal 전체 테스트가 통과했다.

### File List

- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/stories/2-1-micrometer-observation-binding.md`
- `observability-spring-boot-starter/build.gradle`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/HttpServerObservationInput.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/JvmMetricSample.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/DatasourcePoolMetricSample.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/ObservationSampleCollector.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/spring/observation/package-info.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinder.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/StarterObservationArchitectureTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/spring/observation/MicrometerHttpServerObservationBinderTest.java`

## Change Log

- 2026-05-10: Story 2.1 implementation started from existing starter module.
- 2026-05-10: Micrometer observation dependency, HTTP/JVM/datasource input models, collector boundary, binding component, and tests added.
- 2026-05-13: Java 17 baseline 문서 정합성만 반영했으며 구현 내용과 status는 변경하지 않았다.

## Status

done
