---
artifactType: sprint-change-proposal
name: runtime-gauge-aggregate-extension
architectureStyle: Traditional MVC
status: proposed
date: 2026-05-17
---

# Sprint Change Proposal - Runtime Gauge Aggregate Extension

## 1. Issue Summary

Story 2.3의 MVP rollup은 JVM CPU/heap과 datasource pool usage를 bucket 안의 latest valid sample로만 저장한다. 이 방식은 current-state 표시에는 단순하지만, 30초 bucket 안에서 잠깐 발생한 CPU spike, heap pressure, DB pool saturation peak를 놓칠 수 있다.

이 변경은 MVP `schemaVersion: 1.0`과 현재 구현을 바꾸지 않는다. Post-MVP에서 latest-only 한계를 보완하기 위한 runtime gauge aggregate 후보를 명세한다.

## 2. Impact Analysis

- Epic 2: 현재 Story 2.3/2.5는 latest-only MVP 경계를 유지한다. Post-MVP aggregate는 별도 schema version과 story로만 추가한다.
- Epic 3: Portal acceptance와 persistence는 aggregate validation과 저장 컬럼 추가가 필요하다.
- Epic 5: Saturation hint rule과 dashboard read model은 `latest`, `max`, `avg` 의미를 분리해서 evidence로 사용해야 한다.
- Database: MVP `accepted_metric_buckets` migration은 유지한다. Post-MVP migration 후보로 max/avg/sampleCount 컬럼을 추가한다.

## 3. Recommended Approach

Minor direct adjustment로 처리한다. 현재 sprint 구현 범위를 늘리지 않고, active planning artifacts에 Post-MVP candidate backlog와 contract 후보를 추가한다.

성공 기준:

- MVP payload와 persistence는 latest-only로 유지된다.
- Post-MVP 후보는 `latest`, `max`, `avg`, `sampleCount` 의미를 명확히 가진다.
- raw runtime sample 배열, arbitrary metric map, high-cardinality tag ingestion은 계속 금지된다.
- multi-instance 평균은 sampleCount 기반 weighted average로 계산한다.

## 4. Detailed Change Proposals

- `metric-taxonomy.md`: Runtime aggregate 후보와 metric 추가 조건을 명세한다.
- `ingest-envelope.md`: `schemaVersion: "1.1"` 후보 payload shape와 validation rule을 명세한다.
- `database-schema.md`: Post-MVP persistence 후보 컬럼과 validation 의미를 명세한다.
- `insight-rules.md`: `latest`, `max`, `avg`를 saturation evidence에서 어떻게 해석할지 명세한다.
- `read-model-contract.md`: UI가 재계산하지 않고 표시할 runtime saturation evidence 후보 shape를 명세한다.
- `epics.md`: Post-MVP Runtime Gauge Aggregate Extension backlog를 추가한다.
- Story 2.3/2.5: MVP `schemaVersion: 1.0`에는 aggregate를 섞지 않는 guardrail을 추가한다.

## 5. Implementation Handoff

Scope classification: Minor planning adjustment.

후속 구현은 Developer agent가 별도 story로 수행한다. 코드 구현 전에는 `dashboard-read-model` contract와 portal persistence migration을 함께 보정해야 한다.
