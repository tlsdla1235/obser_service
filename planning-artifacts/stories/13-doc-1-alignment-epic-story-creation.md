---
artifactType: story
storyId: "13.doc.1"
storyKey: "13-doc-1-alignment-epic-story-creation"
epic: "Epic 13. Dashboard Source of Truth Realignment"
title: "Alignment Epic/Story Creation"
architectureStyle: Traditional MVC
status: done
date: 2026-06-09
phase: D1
productionCodeChange: false
officialSprintStatusUpdated: true
officialSprintStatusUpdateApproved: true
commitBoundary: "docs: create dashboard source of truth alignment story"
---

# Story 13.doc.1 - Alignment Epic/Story Creation

## Status

done

2026-06-09: D1 alignment epic/story creation은 완료됐다. Epic 13 tracking key는 사용자 승인에 따라 공식 `sprint-status.yaml`에 최소 반영했고, 완료 story 파일과 Source of Truth 문서는 수정하지 않았다.

이 story는 D1 문서 gate 산출물이다. production code를 구현하지 않는다. 최초 D1 원칙은 공식 `implementation-artifacts/sprint-status.yaml` 미반영이었지만, 후속 사용자 승인에 따라 Epic 13 tracking key만 최소 반영한다.

## Story

계획/스토리 작성자로서, Dashboard Source of Truth Realignment를 완료된 Epic 4/5/6/10 story를 다시 열지 않는 새 alignment epic/story 묶음으로 만들고 싶다.

그래야 P1~P10 구현과 D2/D3 문서 정렬이 확정된 Source of Truth를 참조만 하면서, 각 phase의 dependency, rollback, 완료 story 참조 관계를 추적할 수 있다.

## Source of Truth

아래 문서는 이 story의 기준이다. 이 story는 해당 문서의 의미를 재정의하지 않고, 후속 story가 참조할 추적 경계만 만든다.

1. `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
2. `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
3. `implementation-artifacts/sprint-status.yaml`
4. `planning-artifacts/epics.md`
5. `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
6. `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
7. `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
8. `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
9. `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
10. `planning-artifacts/contracts/read-model-contract.md`
11. `_bmad/custom/project-context.md`

## Background

D0와 P0는 draft sequence YAML 안에서 정렬되어 있다. D0는 문서 정렬 시점과 금지 범위를 고정했고, P0는 alignment epic key, story split, 공식 status 반영 시점, 완료 story 참조 정책을 제안했다.

권장 alignment epic key는 `epic-13-dashboard-source-of-truth-realignment`다. 이 epic은 완료된 Epic 4/5/6/10의 구현 이력을 되돌리는 것이 아니라, 확정된 dashboard Source of Truth를 후속 vertical slice로 적용하기 위한 새 추적 단위다.

이번 D1에서는 새 alignment epic/story artifact, `epics.md`의 최소 pointer, 공식 `sprint-status.yaml`의 Epic 13 tracking key만 만든다. 완료 story 파일, Source of Truth 문서, production code는 수정하지 않는다.

## Aligns

- Epic 4의 accepted bucket metric axis와 starter heartbeat control-plane axis 분리 원칙을 유지한다.
- Epic 5의 server-computed Application Dashboard read model, snapshot persistence, marker/detail 이력을 후속 Source of Truth 적용 순서에 맞춘다.
- Epic 6의 Project -> Application -> Dashboard -> Instance/Snapshot 사용자 흐름을 새 Application Dashboard IA, snapshot history/detail, instance surface split로 정렬한다.
- Epic 10의 Vite SPA 인수 결과를 유지하면서 UI-side lifecycle, p95/p99, endpoint priority, snapshot event 재계산 금지 guard를 강화한다.

## Supersedes

아래 항목은 완료 story의 이력을 삭제하거나 본문을 바꾸지 않고, 새 alignment story에서 좁게 대체하는 해석이다.

- Dashboard MVP primary 판단을 15분 current/baseline 비교로 읽는 해석.
- `hourly_scheduled` persisted token을 사용자-facing hourly cadence로 읽는 해석.
- marker bucket을 lifecycle state 또는 snapshot detail evidence source처럼 읽는 해석.
- Snapshot history를 operational event surface와 섞고 client에서 재정렬하는 흐름.
- Instance trend를 독립 instance health timeline이나 instance state machine처럼 읽는 UI/API 해석.

## Hardens

- UI는 server-computed state, endpoint priority, marker/trend order, instance evidence를 재계산하거나 재정렬하지 않는다.
- Application Dashboard live source는 `accepted_metric_buckets`와 recent 30 minutes window로 드러난다.
- Snapshot detail은 `dashboard_snapshots.read_model_json` 저장본을 복원하며 current metric fallback처럼 보이지 않는다.
- Instance Dashboard snapshot mode는 selected Application Snapshot window 기준의 selected instance evidence detail이며, Application Snapshot state/evidence를 대체하지 않는다.
- Retention cleanup은 `dashboard_snapshots.current_window_end_utc`, `accepted_metric_buckets.bucket_end_utc`, 30분 evidence grace 기준을 따른다.
- P10 acceptance gate는 live dashboard, snapshot detail/history, instance live/snapshot/trend, retention expired path를 Source of Truth 기준으로 검증한다.

## Rollback

- D1 rollback은 `planning-artifacts/stories/13-doc-1-alignment-epic-story-creation.md` 추가, `planning-artifacts/epics.md`의 Epic 13 최소 섹션, `implementation-artifacts/sprint-status.yaml`의 Epic 13 tracking key 추가분만 되돌린다.
- `implementation-artifacts/sprint-status.yaml` rollback은 Epic 13 block과 Epic 13 workflow note만 대상으로 하며, 기존 Epic 1~12 상태는 건드리지 않는다.
- Source of Truth 문서와 완료 story 파일은 이번 D1에서 수정하지 않으므로 rollback 대상이 아니다.
- 후속 P1~P10 story는 `dashboard-source-of-truth-realignment-sequence.yaml`에 적힌 각 phase의 `rollback_unit`을 기본 rollback 경계로 사용한다.

## Out of scope

- Source of Truth 문서의 의미, 범위, 우선순위 재정의.
- production code, test code, migration, build 설정 구현.
- Epic 13 tracking key 외의 공식 `sprint-status.yaml` 상태 변경.
- 완료된 Epic 4/5/6/10 story 본문 수정 또는 done 상태 변경.
- 기존 untracked `dbml-error.log` 수정, 삭제, stage.
- Epic 11/12 scope 재분해 또는 기존 backlog status 변경.

## Acceptance Criteria

1. 새 alignment epic key가 `epic-13-dashboard-source-of-truth-realignment`로 기록되어 있다.
2. D1 story artifact가 Background / Aligns / Supersedes / Hardens / Rollback / Out of scope 섹션을 포함한다.
3. D1 story는 Source of Truth 문서를 참조만 하고 의미를 재정의하지 않는다.
4. D1 story는 완료된 Epic 4/5/6/10 story를 Background / Aligns / Supersedes / Hardens 관계로만 참조한다.
5. D1 story는 P1~P10 및 D2/D3로 이어지는 split과 dependency를 추적할 수 있게 한다.
6. `epics.md` 수정은 Epic 13 후보와 story split을 보이게 하는 최소 범위로 제한한다.
7. 이번 D1에서는 `implementation-artifacts/sprint-status.yaml`에 Epic 13 tracking key만 최소 추가한다.
8. 이번 D1에서는 완료 story 파일과 Source of Truth 문서를 수정하지 않는다.
9. production code 구현은 이 story의 산출물이 아니다.

## Story Split Traceability

### Documentation Tracking

| Key | Phase | Intent | Status policy |
| --- | --- | --- | --- |
| `13-doc-0-documentation-alignment-plan` | D0 | 문서 정렬 phase, 금지 문서, just-in-time/final consolidation 원칙 고정 | draft sequence에만 반영됨 |
| `13-doc-1-alignment-epic-story-creation` | D1 | 새 alignment epic/story artifact와 최소 planning/status 문서 정렬 | 이번 story, 사용자 승인으로 official tracking key 반영 |
| `13-doc-2-per-slice-documentation-updates` | D2 | 각 vertical slice 구현 직후 필요한 contract/architecture/story 문서만 정렬 | P4/P6/P8/P9 이후 반복 |
| `13-doc-3-final-planning-status-consolidation` | D3 | P10 acceptance 이후 공식 planning/status 문서 최종 정리 | `sprint-status.yaml` 반영 후보 |

### Planning And Production Alignment

| Key | Phase | Depends on | Intent | Rollback boundary |
| --- | --- | --- | --- | --- |
| `13-1-alignment-story-status-planning` | P0 | D0 | alignment epic key, story split, status 반영 시점, 완료 story 참조 정책 확정 | draft sequence 문서 |
| `13-2-frontend-read-model-contract-guard` | P1 | D1, P0 | Frontend adapter/type/fixture guard로 server-computed state/order/source semantics 고정 | frontend adapter/type/fixture guard commit |
| `13-3-backend-recent-30-minutes-window-alignment` | P2 | P0, P1 | Application/Instance live 판단 window를 recent_30_minutes로 정렬 | 공통 window calculator와 response naming 변경 |
| `13-4-backend-application-dashboard-read-model-shape-alignment` | P3 | P0, P1, P2 | Application Dashboard API/read model shape를 Source of Truth contract에 맞춤 | response DTO/mapper/service shape alignment |
| `13-5-frontend-application-dashboard-ia-realignment` | P4 | P0, P1, P2, P3 | Application Dashboard live surface를 운영자 질문 순서로 재배치 | live surface component/layout |
| `13-6-backend-30-minute-scheduled-snapshot-and-slot-horizon-alignment` | P5 | P0, P1, P2, P3 | 30분 scheduled snapshot slot과 current_window_end_utc horizon 정렬 | scheduler cadence와 repository horizon query 분리 |
| `13-7-frontend-snapshot-history-detail-realignment` | P6 | P0, P1, P3, P4, P5 | Snapshot history/detail을 marker-first 30분 point 탐색과 stored read model 복원 surface로 정렬 | snapshot history/detail route, loader, component surface |
| `13-8-backend-instance-dashboard-live-snapshot-mode-split` | P7 | P0, P1, P2, P3, P5 | Instance Dashboard live/snapshot mode를 API/service 책임으로 분리 | snapshot mode route/service 비활성화 가능 단위 |
| `13-9-frontend-instance-surface-split` | P8 | P0, P1, P6, P7 | Instance live detail, snapshot mode, snapshot trend UI 책임 분리 | instance live/detail/snapshot/trend route와 component split |
| `13-10-retention-cleanup-alignment` | P9 | P0, P5, P7 | 14일 retention UX와 physical cleanup 기준 정렬 | cleanup scheduler/property와 delete service/repository 분리 |
| `13-11-end-to-end-acceptance-and-demo-hardening` | P10 | D2, P1~P9 | Application live, snapshot, instance, retention path end-to-end 검증 | acceptance fixture, demo route, smoke data, verification script |

### Documentation Consolidation

| Key | Phase | Depends on | Intent | Status boundary |
| --- | --- | --- | --- | --- |
| `13-doc-2-per-slice-documentation-updates` | D2 | D1, P4, P6, P8, P9 | 각 vertical slice 구현 직후 문서를 just-in-time으로 정렬 | Source of Truth read-only, 완료 story bulk edit 금지 |
| `13-doc-3-final-planning-status-consolidation` | D3 | D2, P10 | acceptance evidence 이후 `epics.md`, `sprint-status.yaml`, architecture/index 문서 최종 정리 | 공식 status는 실제 완료/리뷰 상태만 반영 |

## Tasks / Subtasks

- [x] D1 story artifact 생성 (AC: 1~5, 7~9)
  - [x] `dashboard-source-of-truth-realignment-sequence.yaml`의 D0/P0/D1 scope를 반영한다.
  - [x] Source of Truth 문서를 재정의하지 않는 참조 목록을 남긴다.
  - [x] Background / Aligns / Supersedes / Hardens / Rollback / Out of scope 섹션을 포함한다.
- [x] `epics.md`와 `sprint-status.yaml`에 Epic 13 최소 tracking 추가 (AC: 1, 5, 6, 7)
  - [x] Epic 13의 목적과 guardrail을 짧게 기록한다.
  - [x] `13-doc-0`~`13-doc-3`, `13-1`~`13-11` split을 추적 가능하게 둔다.
  - [x] 공식 `sprint-status.yaml`에는 Epic 13 tracking key만 추가하고 기존 완료 상태는 바꾸지 않는다.
- [x] D1 보호 대상 확인 (AC: 7~9)
  - [x] `sprint-status.yaml`에서 Epic 13 tracking key 외 기존 상태가 수정되지 않았는지 확인한다.
  - [x] 완료 story 파일이 수정되지 않았는지 확인한다.
  - [x] Source of Truth 문서가 수정되지 않았는지 확인한다.

## Dev Notes

- 이 story는 production implementation guide가 아니라 alignment story creation guide다. 후속 P1부터 production code 변경 story가 시작된다.
- BMAD create-story 기본 흐름은 현재 story key만 `ready-for-dev`로 갱신하려 하지만, 이번 D1 후속 승인에서는 Epic 13 전체 tracking key를 최소 추가하고 이후 story는 backlog로 둔다.
- P1은 fail-closed frontend guard story로 시작한다. backend shape 변경 전에 UI adapter/type/fixture가 server-computed order/value를 보존하는지 먼저 고정해야 한다.

## References

- `planning-artifacts/dashboard-source-of-truth-realignment-sequence.yaml`
- `planning-artifacts/dashboard-source-of-truth-realignment-roadmap.md`
- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md`
- `planning-artifacts/source-of-truth/dashboard-snapshot-retention-cleanup-source-of-truth.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `implementation-artifacts/sprint-status.yaml`
- `planning-artifacts/epics.md`
- `_bmad/custom/project-context.md`

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

N/A

### Completion Notes List

- D1 후속 승인으로 Epic 13 공식 tracking key를 최소 반영한다.
- D1 story는 Source of Truth를 참조만 하고 재정의하지 않는다.
- D1 story는 production code 구현을 포함하지 않는다.
- D1 story status를 `done`으로 닫았다.

### File List

- `planning-artifacts/stories/13-doc-1-alignment-epic-story-creation.md`
- `planning-artifacts/epics.md`
- `implementation-artifacts/sprint-status.yaml`
