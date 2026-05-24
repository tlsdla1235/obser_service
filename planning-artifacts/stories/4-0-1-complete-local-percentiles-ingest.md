---
artifactType: story
storyId: "4.0.1"
epic: "Epic 4. State Semantics and Time Windows"
title: "Complete LocalPercentiles Ingest"
architectureStyle: Traditional MVC
status: done
date: 2026-05-24
trackingNote: "sprint-status에는 없는 Story 4.0과 4.1 사이의 구현 완료형 bridge story"
---

# Story 4.0.1 - Complete LocalPercentiles Ingest

## Story Goal

Story 4.0에서 계약으로 잠근 `summary.localPercentiles`를 starter 전송부터 portal 수신/검증/저장까지 실제 구현으로 닫는다.

이 story는 Story 4.2 `LifecycleStateService` 구현 전에 남겨둔 선행 gap을 문서화한 **구현 완료형 bridge story**다. sprint-status의 공식 backlog item은 아니지만, 후속 BMAD/dev agent가 local percentile 관련 맥락을 stories 디렉터리에서 바로 찾을 수 있도록 같은 story 양식으로 남긴다.

## User Story

운영 첫 화면을 구현하는 개발자로서, starter가 각 30초 bucket에서 직접 관측한 instance-local p95/p99를 portal이 그대로 수신하고 보존하길 원한다.

그래야 후속 instance/detail/read-model 구현에서 p95/p99의 source-of-truth를 histogram-derived 값이나 app/project/window rollup으로 오해하지 않고, starter-reported canonical percentile point로만 다룰 수 있다.

## Scope

포함:

- starter closed bucket의 HTTP duration 원자료에서 instance-local p95/p99 계산
- starter ingest envelope `summary.localPercentiles` 직렬화
- `scope=instance_bucket`, `source=starter_local`, `mergeable=false` 고정
- `bucketStartUtc`/`bucketEndUtc`가 envelope bucket boundary와 일치하도록 보존
- `requestCount`가 같은 instance bucket의 summary requestCount와 일치하도록 검증
- portal `summary.localPercentiles` validation gap 보강
- portal persistence command와 `accepted_metric_buckets.local_percentiles_json` 보존 확인
- 기존 ingest success/duplicate/idempotency 회귀 테스트 유지
- starter architecture guard가 허용된 local percentile 파일만 예외로 열도록 조정

제외:

- Story 4.2 `LifecycleStateService`
- starter heartbeat 구현
- dashboard read model/API
- UI 변경
- app/project/window p95/p99 rollup
- histogram-derived p95/p99 계산
- endpoint별 p95/p99 계산 또는 endpoint percentile rollup
- insight rule, endpoint priority, snapshot persistence

## Acceptance Criteria

1. starter closed bucket payload에는 `summary.localPercentiles`가 포함된다.
2. `summary.localPercentiles.p95Ms`/`p99Ms`는 해당 starter instance의 해당 30초 bucket에서 관측된 HTTP duration 기반 값이다.
3. `scope=instance_bucket`, `source=starter_local`, `mergeable=false`를 만족한다.
4. `bucketStartUtc`와 `bucketEndUtc`는 envelope의 `bucket.startUtc`/`bucket.endUtc`와 일치한다.
5. `requestCount`는 `summary.requestCount`와 일치한다.
6. portal validation은 localPercentiles contract 위반을 `400` validation error 후보로 처리한다.
7. portal persistence는 `accepted_metric_buckets.local_percentiles_json`에 값을 보존한다.
8. 기존 ingest success/duplicate/idempotency 동작은 깨지지 않는다.
9. app/project/window p95/p99를 histogram bucket이나 localPercentiles 숫자 병합으로 새로 만들지 않는다.
10. endpoint별 p95/p99 계산, endpoint percentile rollup, endpoint p99 alert 기준을 만들지 않는다.

## Tasks/Subtasks

- [x] starter local percentile 모델 추가 (AC: 1, 2, 3, 5)
  - [x] `LocalPercentileRollup`으로 requestCount/p95Ms/p99Ms 불변식을 표현한다.
  - [x] `AppMetricRollup`에 instance bucket local percentile point를 optional 값으로 연결한다.
- [x] starter rollup service 구현 (AC: 1, 2, 9, 10)
  - [x] `MetricBucketRollupService`가 app-level HTTP duration 원자료로 nearest-rank p95/p99를 계산한다.
  - [x] endpoint rollup에는 percentile field를 추가하지 않고 histogram bucket만 유지한다.
- [x] starter ingest envelope 전송 구현 (AC: 1, 3, 4, 5)
  - [x] `IngestEnvelope.Summary`에 `localPercentiles`를 추가한다.
  - [x] `IngestEnvelope.LocalPercentiles`가 fixed source/scope/mergeable, boundary, value invariant를 검증한다.
  - [x] `IngestEnvelopeBuilderService`가 closed bucket interval을 `bucketStartUtc`/`bucketEndUtc`로 그대로 넣는다.
- [x] portal validation/persistence gap 보강 (AC: 5, 6, 7, 8)
  - [x] `IngestAcceptanceService`가 localPercentiles requestCount mismatch를 validation error로 처리한다.
  - [x] `AcceptedMetricBucketWriteCommand`가 persistence 직전 fixed source/scope/mergeable/value invariant를 재확인한다.
  - [x] 기존 `MetricBucketRepository`의 `local_percentiles_json` 저장 경로를 중복 구현하지 않고 재사용한다.
- [x] 테스트 및 guard 갱신 (AC: 1-10)
  - [x] starter rollup percentile 계산과 envelope golden JSON을 검증한다.
  - [x] portal validation, command invariant, repository JSONB 보존을 검증한다.
  - [x] starter negative guard가 local percentile 허용 파일 외 percentile 역행 경로를 계속 막는지 검증한다.

## Dev Notes

- Active baseline은 Traditional MVC + Service/Repository Layering이다.
- 이 story의 p95/p99는 **starter-reported canonical value**다.
- 계산 알고리즘은 구현 시점 기준 nearest-rank percentile이다. non-empty bucket에서 duration milliseconds를 정렬한 뒤 `ceil(percentile * count) - 1` index를 사용하고, index는 valid range로 clamp한다.
- `Duration.toMillis()` 기준이므로 sub-millisecond precision은 버린다.
- zero-request bucket은 현재 rollup service가 HTTP observation 없이 flush candidate를 만들지 않는 경로가 기본이다. 모델 invariant는 empty point가 만들어질 경우 `requestCount=0`, `p95Ms=0`, `p99Ms=0`만 허용한다.
- portal은 `summary.localPercentiles`를 지원 field로 다루며, unknown field처럼 버리지 않는다.
- Story 4.0 정책에 따라 기존 `schemaVersion`, bucket, histogram, endpoint, idempotency, runtime ratio validation hardening은 새 요구사항으로 재개하지 않는다.
- `accepted_metric_buckets.local_percentiles_json` column과 기본 persistence path는 Story 4.0에서 이미 준비되어 있었으므로 중복 migration을 만들지 않는다.
- 후속 read model은 이 값을 같은 scope의 starter canonical p95/p99 point로만 사용할 수 있다.

## Source References

- `planning-artifacts/contracts/ingest-envelope.md` - `summary.localPercentiles` contract, source/scope/mergeable 정책
- `planning-artifacts/contracts/time-buckets.md` - UTC 30초 bucket boundary와 `[startUtc, endUtc)` semantics
- `planning-artifacts/contracts/state-semantics.md` - heartbeat와 accepted bucket/state source-of-truth 분리
- `planning-artifacts/stories/4-0-starter-heartbeat-and-instance-level-ingest-contract-reassessment.md` - localPercentiles 계약 gate
- `planning-artifacts/stories/4-1-time-bucket-contract-implementation.md` - 후속 Story 4.2 전 time boundary 맥락
- `implementation-artifacts/spec-complete-local-percentiles-ingest.md` - quick-dev implementation spec와 Suggested Review Order
- `_bmad/custom/project-context.md` - MVC + Spring Data JPA implementation policy

## Test Requirements

- `MetricBucketRollupServiceTest`
  - 30초 bucket 안 HTTP duration으로 starter-local nearest-rank p95/p99를 계산한다.
  - endpoint rollup model에는 percentile/p95 field가 생기지 않는다.
- `IngestEnvelopeBuilderServiceTest`
  - closed bucket interval과 app summary local percentile을 `summary.localPercentiles`로 변환한다.
  - `scope/source/mergeable/bucketStartUtc/bucketEndUtc/requestCount/p95Ms/p99Ms` contract를 만족한다.
- `IngestEnvelopeContractJsonTest`
  - golden JSON에 `summary.localPercentiles`가 포함된다.
- `IngestAcceptanceServiceTest`
  - invalid localPercentiles field는 invalid request로 처리된다.
  - requestCount mismatch는 `summary.localPercentiles.requestCount` validation error로 처리된다.
  - duplicate/idempotency behavior는 기존대로 유지된다.
- `AcceptedMetricBucketWriteCommandTest`
  - validated envelope가 persistence command로 localPercentiles를 복사한다.
  - persistence command가 requestCount mismatch를 거부한다.
- `MetricBucketRepositoryIntegrationTest`
  - `local_percentiles_json`에 scope/source/requestCount/p95Ms/p99Ms/mergeable이 보존된다.
- `NoPrometheusMvpPathTest`
  - 허용된 starter local percentile 파일 외에 percentile/read-model/priority 역행 경로가 열리지 않는다.

## Developer Guardrails

- app/project/window p95/p99를 만들지 않는다.
- histogram bucket으로 p95/p99를 재계산하지 않는다.
- localPercentiles 숫자끼리 평균/최댓값/가중 평균으로 병합하지 않는다.
- endpoint별 p95/p99 계산을 만들지 않는다.
- dashboard read model/API, LifecycleStateService, heartbeat, snapshot, UI를 이 story 범위에 끌어오지 않는다.
- portal 쪽 기존 구현이 있으면 중복 구현하지 않고 gap만 보강한다.
- `localPercentiles` validation을 이유로 기존 ingest field hardening을 재개하지 않는다.

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Implementation Summary

- starter가 closed 30초 bucket에서 HTTP duration 기반 instance-local p95/p99를 계산하고 `summary.localPercentiles`로 전송하도록 구현했다.
- portal은 기존 localPercentiles 모델/저장 경로를 재사용하면서 requestCount mismatch와 persistence command invariant를 보강했다.
- architecture guard는 허용된 local percentile 파일만 예외로 열고, 그 외 percentile/read-model/priority 역행 경로는 계속 막도록 조정했다.

### Commit

- `ace0c59060b29ead5b4bea0ba9843318039c7bf0` - `feat: complete local percentiles ingest`

### Verification

- `./gradlew :observability-spring-boot-starter:test` - 성공
- `./gradlew :observability-portal:test` - 성공
- `./gradlew test` - 성공
- `git diff --check` - 성공

### Review Notes

- BMAD quick-dev review 단계에서 diff self-review를 수행했고, 허용 범위를 넓힌 starter percentile guard를 더 좁은 파일 allowlist 방식으로 보강했다.
- 별도 human review나 독립 sub-agent review는 수행하지 않았다. “review 성공”은 구현자 self-review와 automated test verification이 성공했다는 의미다.

### File List

- `implementation-artifacts/spec-complete-local-percentiles-ingest.md`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/LocalPercentileRollup.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/AppMetricRollup.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/metric/ClosedMetricBucket.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/model/ingest/IngestEnvelope.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/MetricBucketRollupService.java`
- `observability-spring-boot-starter/src/main/java/com/observation/starter/service/IngestEnvelopeBuilderService.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/MetricBucketRollupServiceTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/IngestEnvelopeBuilderServiceTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/service/IngestEnvelopeContractJsonTest.java`
- `observability-spring-boot-starter/src/test/java/com/observation/starter/architecture/NoPrometheusMvpPathTest.java`
- `observability-portal/src/main/java/com/observation/portal/domain/ingest/service/IngestAcceptanceService.java`
- `observability-portal/src/main/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommand.java`
- `observability-portal/src/test/java/com/observation/portal/domain/ingest/service/IngestAcceptanceServiceTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/model/AcceptedMetricBucketWriteCommandTest.java`
- `observability-portal/src/test/java/com/observation/portal/domain/bucket/repository/MetricBucketRepositoryIntegrationTest.java`

### Change Log

- 2026-05-24: Story 4.0과 4.1 사이의 localPercentiles ingest completion 구현 내용을 BMAD 참조용 story로 기록했다.

## Status

done
