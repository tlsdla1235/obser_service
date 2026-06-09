# Next Context Prompt - Epic 4 Closure

아래 프롬프트를 새 컨텍스트에 붙여넣고 시작한다.

```text
BMAD retrospective 흐름으로 Epic 4를 완전히 닫아줘.

프로젝트:
- /Users/tlsdla1235/Desktop/study/observation
- BMAD 로컬 런타임은 이미 초기화되어 있음
- active 구현 기준은 루트의 planning-artifacts/, implementation-artifacts/ 문서다.
- archive/hexagonal-version/의 Hexagonal 산출물은 legacy 보관본이며 구현 기준으로 사용하지 않는다.
- bmad-restart-context-pack/ 문서는 제품 문제와 UX 의도 참고용이다.

이번 작업:
- Epic 4. State Semantics and Time Windows 종료 회고를 작성한다.
- `implementation-artifacts/epic-4-retro-2026-05-25.md`를 생성한다.
- `implementation-artifacts/sprint-status.yaml`에서 `epic-4-retrospective: done`, `epic-4: done`으로 닫는다.
- `sprint-status.yaml`의 `last_updated`와 workflow note를 Epic 4 종료 상태에 맞게 갱신한다.

현재 상태:
- `implementation-artifacts/sprint-status.yaml` 기준 Story 4.0 -> 4.4는 모두 `done`이다.
- sprint-status에는 없는 bridge story `4.0.1 Complete LocalPercentiles Ingest`, `4.0.2 Complete Starter Heartbeat`도 story 파일 기준 `done`이다.
- 닫히지 않은 항목은 `epic-4: in-progress`, `epic-4-retrospective: optional`이다.
- Epic 4 closure는 heartbeat/control-plane axis와 accepted bucket/data-plane axis 분리, UTC 30초 bucket/time window, lifecycle state semantics, recovery guidance, state semantics regression을 기준으로 판단한다.

반드시 먼저 읽을 파일:
- `_bmad/custom/project-context.md`
- `implementation-artifacts/sprint-status.yaml`
- `implementation-artifacts/epic-3-retro-2026-05-21.md`
- `implementation-artifacts/epic-4-context.md`
- `implementation-artifacts/spec-story-4-3-recovery-guidance-contract-decisions.md`
- `implementation-artifacts/spec-complete-local-percentiles-ingest.md`
- `implementation-artifacts/spec-complete-starter-heartbeat.md`
- `planning-artifacts/epics.md`
- `planning-artifacts/sprint-plan.md`
- `planning-artifacts/epic5-6-dashboard-alignment-context.md`
- `planning-artifacts/source-of-truth/current-product-source-of-truth.md`
- `planning-artifacts/contracts/state-semantics.md`
- `planning-artifacts/contracts/time-buckets.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/starter-failure-semantics.md`
- `planning-artifacts/stories/4-0-starter-heartbeat-and-instance-level-ingest-contract-reassessment.md`
- `planning-artifacts/stories/4-0-1-complete-local-percentiles-ingest.md`
- `planning-artifacts/stories/4-0-2-complete-starter-heartbeat.md`
- `planning-artifacts/stories/4-1-time-bucket-contract-implementation.md`
- `planning-artifacts/stories/4-2-lifecycle-state-service.md`
- `planning-artifacts/stories/4-3-recovery-guidance.md`
- `planning-artifacts/stories/4-4-state-semantics-tests.md`

회고에서 반드시 정리할 closure 기준:
- Story 4.0은 heartbeat와 local percentile 의미를 잠근 contract gate다.
- Story 4.0.1은 `summary.localPercentiles`를 starter 전송, portal 수신/검증/저장 path까지 구현 완료한 bridge story다.
- Story 4.0.2는 starter heartbeat sender/client와 portal heartbeat endpoint/response를 구현 완료한 bridge story다.
- Story 4.1은 UTC 30초 bucket, current/baseline 15분 window, accepted bucket freshness helper, last accepted bucket timestamp repository method를 닫았다.
- Story 4.2는 `LifecycleStateService`에서 metric state와 starter connection axis를 분리해 판정한다.
- Story 4.3은 stale/down 이후 current freshness + insufficient sample 구간을 `state=unknown`, `recovery.isRecovering=true`로 표현한다.
- Story 4.4는 freshness, minimum sample, baseline insufficient, two-axis interpretation, recovery semantics, scope guard를 regression test로 고정했다.

중요한 전제:
- Active architecture는 Traditional MVC + Service/Repository Layering이다.
- `LifecycleStateService` 또는 동등한 service/model 계층만 lifecycle state를 계산한다.
- UI, controller, repository는 lifecycle state, insight rule, p95/p99, endpoint priority를 재계산하지 않는다.
- accepted bucket은 metric freshness/state/read-model source-of-truth다.
- heartbeat는 starter/application process liveness, portal reachability, project key validity, metadata validity의 별도 control-plane source다.
- heartbeat 성공/실패/미수신은 accepted bucket freshness, host business health, snapshot/event, p95/p99, rule/read-model calculation source가 아니다.
- 최근 heartbeat와 없음/오래된 accepted bucket 조합을 host application down으로 단정하지 않는다.
- recovery copy와 starter connection copy를 결합해 host application down, host process down, 앱 내려감 같은 확정 표현을 만들지 않는다.
- localPercentiles는 source-scoped starter canonical percentile point다. 여러 instance/window의 p95/p99를 평균/병합해 application percentile scalar로 만들지 않는다.
- endpoint histogram은 distribution/evidence 표시용이며 endpoint별 p95/p99 계산 source가 아니다.

회고 문서 권장 구조:
- front matter: `artifactType: retrospective`, `projectName`, `epic`, `retrospectiveType: review-closure`, `architectureStyle: Traditional MVC`, `date: 2026-05-25`, `status: done`
- 1. 회고 범위
- 2. 참여 관점
- 3. Epic 4 목표 달성 여부
- 4. Epic 3 회고 액션 Follow-through
- 5. 잘 된 점
- 6. 어려웠던 점과 시스템/프로세스 관점의 원인
- 7. Deferred / Residual Risk 정리
- 8. Epic 5/6 Action Items
- 9. Next Epic Preparation
- 10. Epic 4 Closure 판단
- 11. sprint-status.yaml 종료 처리
- 12. Team Agreements
- 13. Closure

Epic 5/6 준비에 남길 핵심 action item:
- Epic 5 read model/API는 Epic 4의 state semantics를 소비하고 재계산하지 않는다.
- dashboard current response는 accepted bucket axis와 starter connection axis를 별도 field/copy로 내려준다.
- zeroInsight/recovery mapping은 `observing_recovery`, `metric_data_idle`, `telemetry_unreachable` 의미를 계약과 맞춘다.
- percentile 표시는 source-scoped localPercentiles와 histogram distribution을 구분한다.
- snapshot/history는 stored dashboard read model history이며 raw explorer나 per-ingest snapshot refresh로 만들지 않는다.
- Epic 6 UI는 Application Dashboard를 primary first-screen으로 두고 server-computed read model을 렌더링한다.

완료 조건:
- `implementation-artifacts/epic-4-retro-2026-05-25.md`가 저장된다.
- `implementation-artifacts/sprint-status.yaml`에서 `epic-4: done`, `epic-4-retrospective: done`이 반영된다.
- workflow note에서 Epic 4가 종료됐고 Story 4.0 -> 4.4 및 bridge story 4.0.1/4.0.2가 done임을 표현한다.
- 문서만 수정했다면 Gradle 테스트는 필수 실행 대상이 아니다. 단, 코드 변경이 생기면 `./gradlew test` 또는 focused test를 실행하고 결과를 회고나 final에 남긴다.
```
