---
artifactType: sprint-change-proposal
name: heartbeat-state-semantics-correction
architectureStyle: Traditional MVC
status: applied
date: 2026-05-24
---

# Sprint Change Proposal - Heartbeat State Semantics Correction

## 1. Issue Summary

Starter heartbeat 구현은 완료됐지만, 기존 state semantics 문구가 "heartbeat는 state/freshness source가 아니다"라고 너무 넓게 잠겨 있었다.

실제 의도는 사용자 서버에 몇 분 동안 요청이 없어 accepted bucket이 오지 않는 상황을 host application down으로 오판하지 않는 것이다. 따라서 accepted bucket과 heartbeat는 서로 다른 축으로 분리한다.

- accepted bucket: metric data freshness, state metric input, dashboard/read-model 계산 source-of-truth
- heartbeat: starter/application process liveness, portal reachability, project key validity, metadata validity의 control-plane source

## 2. Impact Analysis

- Epic 4: Story 4.2 LifecycleStateService는 accepted bucket freshness와 starter connection/liveness를 별도 입력으로 다룰 수 있어야 한다.
- Story 4.0/4.1: heartbeat를 metric freshness source로 쓰지 않는 guardrail은 유지하되, heartbeat가 starter connection source라는 의미를 추가한다.
- API/read model contract: `starterConnection`은 metric state를 직접 바꾸지 않는 diagnostic/control-plane field로 분리한다.
- Failure semantics: heartbeat 미수신과 오래된 accepted bucket 조합도 host application down 확정이 아니라 telemetry unreachable/unknown 계열로 남긴다.

MVP scope는 늘리지 않는다. LifecycleStateService, heartbeat persistence, dashboard API/UI 구현은 이번 변경 범위 밖이다.

## 3. Recommended Approach

Minor direct adjustment로 처리한다.

기존 Epic 4 구조는 유지하고, 계약/스토리 문구만 두 축 semantics로 정렬한다. `down` enum은 즉시 제거하지 않지만, host application process down처럼 읽히는 경우 후속 rename을 deferred item으로 남긴다.

## 4. Detailed Change Proposals

- `state-semantics.md`: accepted bucket axis와 starter connection axis를 분리하고 two-axis interpretation matrix를 추가한다.
- `starter-failure-semantics.md`: heartbeat 실패/미수신/성공 조합을 host down 확정으로 보지 않는 failure copy를 고정한다.
- `ingest-envelope.md`: heartbeat가 metric ingest가 아니라 control-plane source임을 명확히 한다.
- `time-buckets.md`: accepted bucket freshness가 metric data freshness일 뿐 host liveness가 아님을 명시한다.
- `api-surface.md`와 `read-model-contract.md`: `starterConnection`을 diagnostic/control-plane field로 정렬한다.
- `operational-event-history.md`: heartbeat telemetry가 operational event source가 아님을 유지하되, starter connection surface와 분리한다.
- `architecture.md`와 `epics.md`: Story 4.2가 두 축을 별도 입력으로 사용할 수 있게 service 책임을 정리한다.
- `epic-4-context.md`와 `sprint-status.yaml`: 다음 BMAD/dev handoff가 같은 semantics를 읽도록 현재 sprint context를 갱신한다.
- `deferred-work.md`: heartbeat telemetry persistence, two-axis implementation, `down` rename 검토를 후속 작업으로 남긴다.

## 5. Implementation Handoff

Scope classification: Minor planning adjustment.

후속 구현 권장 순서:

1. Heartbeat telemetry persistence를 lightweight control-plane 저장소로 추가한다.
2. Story 4.2 LifecycleStateService에서 accepted bucket metric state와 starter connection/liveness를 별도 typed input/output으로 다룬다.
3. Story 4.3 recovery guidance에서 no recent traffic, waiting for traffic, telemetry unreachable copy를 정리한다.
4. Story 4.4 state semantics tests에 two-axis fixture를 추가한다.
5. UI/API 구현 시 `down` label이 host process down으로 읽히면 data-plane 기준 rename 또는 copy 제한을 적용한다.
