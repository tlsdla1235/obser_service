---
title: 'starter-heartbeat-failure-hardening'
type: 'bugfix'
created: '2026-05-24'
status: 'done'
baseline_commit: '842bc57a37e560eca107381ee1042a9968a6768a'
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/_bmad/custom/project-context.md'
  - '{project-root}/implementation-artifacts/spec-complete-starter-heartbeat.md'
  - '{project-root}/planning-artifacts/contracts/starter-failure-semantics.md'
  - '{project-root}/planning-artifacts/contracts/state-semantics.md'
---

<frozen-after-approval reason="human-owned intent - do not modify unless human renegotiates">

## Intent

**Problem:** starter heartbeat failure가 `RuntimeException -> false`로만 접혀 portal down, timeout, 401, 5xx가 운영자가 볼 수 없는 무음 실패가 된다.

**Approach:** heartbeat 전송 실패를 category가 있는 result로 유지하고, background sender 내부에서 최초 WARN, 반복 suppress, 성공 후 reset, backoff delay를 적용한다.

## Boundaries & Constraints

**Always:** host startup/request path로 heartbeat 실패를 전파하지 않는다. 최초 실패 로그에는 endpoint alias, failure category, host app 계속 실행, retry/backoff 적용을 포함한다. raw project key는 message/log/result에 담지 않는다. Bucket ingest retry/idempotency와 heartbeat retry/logging 의미를 섞지 않는다.

**Ask First:** fail-fast heartbeat mode, portal heartbeat persistence, read model/API/UI surface가 필요하면 중단한다.

**Never:** state-semantics 문서, LifecycleStateService, dashboard surface, accepted_metric_buckets 저장, portal persistence를 변경하지 않는다.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| First failure | heartbeat client throws timeout/down/401/5xx | sender continues and emits one WARN with category and backoff | failure result stays inside background sender |
| Repeated failure | same failure window keeps failing | next attempts use backoff and repeated WARN is suppressed | no log spam |
| Recovery | a success occurs after failures | failure window resets | next failure is visible again |
| Secret safety | raw project key exists in configuration or exception text | result/log/message does not include raw key | only category and endpoint alias are surfaced |

</frozen-after-approval>

## Code Map

- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/HeartbeatFailureCategory.java` -- starter-failure-semantics category vocabulary.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/HeartbeatFailureClassifier.java` -- HTTP status and network exception classifier.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/PortalHeartbeatException.java` -- raw-key-safe client exception carrying category/status.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/client/JdkPortalHeartbeatClient.java` -- HTTP status and network exception source for heartbeat failure categories.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/StarterHeartbeatService.java` -- fail-open conversion point from client exception to heartbeat result.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/StarterHeartbeatSender.java` -- background-only logging, suppression, and backoff loop.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/StarterHeartbeatResult.java` -- sanitized result passed from service to sender.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/HeartbeatFailureReporter.java` -- WARN message boundary for first failure in a window.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/HeartbeatRetryBackoff.java` -- heartbeat-only delay boundary for tests and sender.
- `observability-spring-boot-starter/src/test/java/com/observation/starter/client/HeartbeatFailureClassifierTest.java` -- network category coverage.
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/StarterHeartbeatServiceTest.java` -- result/category/secret safety unit coverage.
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/StarterHeartbeatSenderTest.java` -- WARN once, reset, backoff, non-blocking coverage.
- `observability-spring-boot-starter/src/test/java/com/observation/starter/client/JdkPortalHeartbeatClientTest.java` -- HTTP status category and raw-key-safe exception coverage.

## Tasks & Acceptance

**Execution:**
- [x] `observability-spring-boot-starter/src/main/java/com/observation/starter/client/*Heartbeat*.java` -- add category-aware heartbeat failure exceptions and HTTP/network classification.
- [x] `observability-spring-boot-starter/src/main/java/com/observation/starter/service/StarterHeartbeatService.java` -- return a structured send result while preserving boolean fail-open API.
- [x] `observability-spring-boot-starter/src/main/java/com/observation/starter/service/StarterHeartbeatSender.java` -- add first WARN, same-window suppression, success reset, and retry/backoff delay.
- [x] `observability-spring-boot-starter/src/test/java/com/observation/starter/**/*Heartbeat*Test.java` -- verify category, WARN once/rate-limit, fail-open, secret safety, and non-blocking behavior.

**Acceptance Criteria:**
- Given portal down, timeout, 401, or 5xx, when heartbeat sender runs, then the failure remains fail-open inside the sender.
- Given the first failure in a window, when it is handled, then WARN includes endpoint alias, failure category, host continues, and retry/backoff applied.
- Given repeated failures before success, when attempts continue, then repeated WARN logs are suppressed and delay backs off.
- Given success after a failure window, when a later failure occurs, then it is observable again.
- Given a raw project key, when exception/result/log text is produced, then the raw key is absent.
- Given a slow portal timeout, when sender starts, then startup/request caller is not blocked waiting for the portal.

## Verification

**Commands:**
- `./gradlew :observability-spring-boot-starter:test` -- passed.
- `./gradlew test` -- passed.
- `git diff --check` -- passed.

## Suggested Review Order

**Failure Semantics**

- Category mapping is centralized before service/sender policy reads it.
  [`HeartbeatFailureClassifier.java:23`](../observability-spring-boot-starter/src/main/java/com/observation/starter/client/HeartbeatFailureClassifier.java#L23)

- Client exceptions now carry safe category/status instead of raw messages.
  [`PortalHeartbeatException.java:29`](../observability-spring-boot-starter/src/main/java/com/observation/starter/client/PortalHeartbeatException.java#L29)

- Service preserves fail-open while returning sanitized failure metadata.
  [`StarterHeartbeatService.java:58`](../observability-spring-boot-starter/src/main/java/com/observation/starter/service/StarterHeartbeatService.java#L58)

**Sender Policy**

- Sender applies first WARN, suppression, reset, and backoff inside the daemon loop.
  [`StarterHeartbeatSender.java:68`](../observability-spring-boot-starter/src/main/java/com/observation/starter/service/StarterHeartbeatSender.java#L68)

- WARN payload includes endpoint alias, category, host-continuation, and backoff facts.
  [`HeartbeatFailureReporter.java:46`](../observability-spring-boot-starter/src/main/java/com/observation/starter/service/HeartbeatFailureReporter.java#L46)

**Verification**

- Sender tests lock WARN once, reset, backoff, secret safety, and non-blocking behavior.
  [`StarterHeartbeatSenderTest.java:62`](../observability-spring-boot-starter/src/test/java/com/observation/starter/service/StarterHeartbeatSenderTest.java#L62)

- Client/service tests lock status categories and raw-key-safe result behavior.
  [`JdkPortalHeartbeatClientTest.java:64`](../observability-spring-boot-starter/src/test/java/com/observation/starter/client/JdkPortalHeartbeatClientTest.java#L64)
