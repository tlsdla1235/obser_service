---
title: 'complete-local-percentiles-ingest'
type: 'feature'
created: '2026-05-24'
status: 'done'
baseline_commit: 'ec94169c61d12baf2b75c85f47658ccb39bb14d2'
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/_bmad/custom/project-context.md'
  - '{project-root}/planning-artifacts/contracts/ingest-envelope.md'
  - '{project-root}/planning-artifacts/contracts/time-buckets.md'
  - '{project-root}/planning-artifacts/stories/4-0-starter-heartbeat-and-instance-level-ingest-contract-reassessment.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 4.0에서 portal의 `summary.localPercentiles` 수신/저장 경계는 대부분 구현됐지만, starter가 닫힌 30초 bucket에서 instance-local p95/p99를 실제 payload로 보내지 않는다. Story 4.2 LifecycleStateService에 들어가기 전에 starter canonical percentile 전송과 portal contract gap을 닫아야 한다.

**Approach:** starter rollup은 HTTP observation duration 원자료에서 해당 instance bucket의 p95/p99 point만 산출하고, ingest envelope builder는 이를 `summary.localPercentiles`로 직렬화한다. portal은 기존 구현을 유지하되 requestCount/bucket/source/scope/mergeable contract 위반을 400 validation error로 수렴시키고 저장 JSON 보존을 검증한다.

## Boundaries & Constraints

**Always:** p95/p99는 starter-reported canonical value다. `localPercentiles.scope=instance_bucket`, `source=starter_local`, `mergeable=false`, `bucketStartUtc/endUtc`는 envelope bucket boundary와 같아야 한다. `requestCount`는 같은 instance bucket의 HTTP request count와 일치해야 한다. 기존 duplicate/idempotency success 경로는 유지한다.

**Ask First:** exact percentile 알고리즘을 외부 라이브러리/근사 sketch로 바꾸거나, zero-request bucket의 percentile 표현 정책을 계약 문서와 다르게 바꿔야 하면 중단하고 확인한다.

**Never:** Story 4.2 LifecycleStateService, heartbeat, dashboard read model/API, UI, snapshot, app/project/window p95/p99 rollup, histogram-derived percentile, endpoint별 p95/p99 계산, insight rule, endpoint priority를 만들지 않는다.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Starter request bucket | 한 30초 bucket에 HTTP durations가 기록되고 grace 후 drain됨 | `summary.localPercentiles`가 포함되고 requestCount/p95Ms/p99Ms가 해당 bucket duration에서 산출됨 | N/A |
| Portal valid payload | `localPercentiles`가 fixed scope/source/mergeable, matching boundary/count를 가짐 | accepted command와 `accepted_metric_buckets.local_percentiles_json`에 원형 보존 | N/A |
| Portal invalid payload | scope/source/mergeable/boundary/count/value contract 위반 | `400` validation result, repository insert 없음 | validation error field가 `summary.localPercentiles.*`를 가리킴 |
| Duplicate replay | 같은 project/idempotency key가 이미 저장됨 | 기존 duplicate/idempotency behavior 유지 | 기존 duplicate result 유지 |

</frozen-after-approval>

## Code Map

- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricBucketRollupService.java` -- HTTP duration을 30초 bucket별 app/endpoint histogram으로 집계하는 starter-local 집계 지점.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/AppMetricRollup.java` -- app summary에 starter-local percentile point를 싣는 모델.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/ingest/IngestEnvelope.java` -- starter가 portal로 직렬화하는 schemaVersion 1.0 payload 모델.
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/IngestEnvelopeBuilderService.java` -- closed bucket을 `summary.localPercentiles` payload로 변환하는 지점.
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java` -- portal validation gap을 메우는 service.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommand.java` -- validation 완료 후 local percentiles를 persistence command로 고정하는 모델.
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/repository/MetricBucketRepository.java` -- `local_percentiles_json` 저장 경로. 기존 구현을 재사용한다.

## Tasks & Acceptance

**Execution:**
- [x] `starter model/metric` -- instance bucket local percentile rollup 모델 추가 -- requestCount/p95/p99 불변식과 app summary 연결.
- [x] `MetricBucketRollupService` -- app-level duration percentile accumulator 추가 -- endpoint percentile 계산 없이 starter-local point만 산출.
- [x] `IngestEnvelope`/`IngestEnvelopeBuilderService` -- `summary.localPercentiles` payload 생성/검증 -- fixed scope/source/mergeable와 bucket boundary 보존.
- [x] `IngestAcceptanceService`/`AcceptedMetricBucketWriteCommand` -- localPercentiles requestCount mismatch validation 추가 -- portal gap만 보강.
- [x] starter/portal tests -- payload 생성, golden JSON, validation, persistence, duplicate/idempotency 회귀 검증.

**Acceptance Criteria:**
- Given a starter closed bucket with HTTP durations, when the envelope is built, then `summary.localPercentiles` contains `instance_bucket`, `starter_local`, matching bucket boundaries, request count, p95Ms, p99Ms, and `mergeable=false`.
- Given invalid portal `summary.localPercentiles`, when accepted, then result is invalid request and no insert occurs.
- Given valid portal ingest with localPercentiles, when persisted, then `accepted_metric_buckets.local_percentiles_json` preserves the JSON object.
- Given existing duplicate/idempotency scenarios, when tests run, then their behavior remains unchanged.
- Given this implementation, no app/project/window percentile rollup, histogram-derived percentile, endpoint percentile, heartbeat, lifecycle state, dashboard API, or UI code is added.

## Spec Change Log

## Design Notes

Use nearest-rank percentile over the starter-observed request durations in a single instance bucket. For non-empty buckets: sort duration milliseconds ascending, `ceil(percentile * count) - 1`, clamp to the valid index. Endpoint rollups keep only histogram buckets.

## Verification

**Commands:**
- `./gradlew :observability-spring-boot-starter:test` -- expected: success.
- `./gradlew :observability-portal:test` -- expected: success.
- `./gradlew test` -- expected: success if runtime allows full suite.
- `git diff --check` -- expected: no whitespace errors.

## Suggested Review Order

**Starter Production Path**

- Start here: request duration enters the single instance-bucket percentile accumulator.
  [`MetricBucketRollupService.java:199`](../observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricBucketRollupService.java#L199)

- Nearest-rank p95/p99 is computed only from this starter bucket.
  [`MetricBucketRollupService.java:291`](../observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricBucketRollupService.java#L291)

- Payload model fixes source/scope/mergeable and boundary/value invariants.
  [`IngestEnvelope.java:122`](../observability-spring-boot-starter/src/main/java/com/observation/starter/model/ingest/IngestEnvelope.java#L122)

- Builder attaches envelope bucket boundaries to the starter-local point.
  [`IngestEnvelopeBuilderService.java:65`](../observability-spring-boot-starter/src/main/java/com/observation/starter/service/IngestEnvelopeBuilderService.java#L65)

**Portal Ingest Path**

- Portal validation now rejects localPercentiles requestCount mismatches.
  [`IngestAcceptanceService.java:233`](../observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java#L233)

- Persistence command keeps localPercentiles aligned with accepted summary count.
  [`AcceptedMetricBucketWriteCommand.java:74`](../observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommand.java#L74)

**Tests And Guards**

- Golden JSON proves starter payload includes summary.localPercentiles.
  [`IngestEnvelopeContractJsonTest.java:45`](../observability-spring-boot-starter/src/test/java/com/observation/starter/service/IngestEnvelopeContractJsonTest.java#L45)

- Repository integration verifies JSONB preservation.
  [`MetricBucketRepositoryIntegrationTest.java:279`](../observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java#L279)

- Starter guard allows only the scoped local percentile files.
  [`NoPrometheusMvpPathTest.java:608`](../observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/NoPrometheusMvpPathTest.java#L608)
