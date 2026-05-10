# Next Context Prompt - Epic 2 Sprint Planning

아래 프롬프트를 새 컨텍스트에 붙여넣고 시작한다.

```text
BMAD Sprint Planning을 MVC 버전 산출물 기준으로 진행해줘.

프로젝트:
- /Users/tlsdla1235/Desktop/study/observation
- BMAD 로컬 런타임은 이미 초기화되어 있음
- 현재 목표는 구현이 아니라 Epic 2 Sprint Planning이다.
- active 구현 기준은 루트의 planning-artifacts/, implementation-artifacts/ 문서다.
- archive/hexagonal-version/의 Hexagonal 산출물은 legacy 보관본이며 구현 기준으로 사용하지 않는다.
- bmad-restart-context-pack/ 문서는 제품 문제와 UX 의도 참고용이다.

이번에 계획할 Epic:
- Epic 2. Starter Direct Ingest Producer
- 목표: 사용자가 starter를 추가하면 host app request path를 막지 않고 30초 bucket을 전송한다.

현재 상태:
- Epic 1의 portal foundation stories는 완료됨.
- Story 1.1 Starter Package Skeleton은 review 상태이며, starter module/package skeleton은 구현되어 있음.
- Epic 2 story 파일은 아직 생성되어 있지 않다.
- Epic 2는 epics.md에 상위 story 목록만 있고, 개발 가능한 story 문서와 sprint plan은 아직 필요하다.

반드시 먼저 읽을 파일:
- _bmad/custom/project-context.md
- planning-artifacts/mvc-selection.md
- planning-artifacts/implementation-readiness-review.md
- planning-artifacts/project-structure.md
- planning-artifacts/sprint-plan.md
- implementation-artifacts/sprint-status.yaml
- implementation-artifacts/epic-1-retro-2026-05-10.md
- planning-artifacts/epics.md
- planning-artifacts/architecture.md
- planning-artifacts/architecture-implementation-supplement.md
- planning-artifacts/acceptance-traceability.md
- planning-artifacts/api-surface.md
- planning-artifacts/contracts/ingest-envelope.md
- planning-artifacts/contracts/metric-taxonomy.md
- planning-artifacts/contracts/time-buckets.md
- planning-artifacts/stories/1-1-starter-package-skeleton.md
- bmad-restart-context-pack/micrometer-direct-ingest-pivot-prep.md

중요한 전제:
- PRD/UX 본문은 검증 완료 산출물로 보고 다시 쓰지 않는다.
- 기존 제품/계약 의미는 유지한다.
- 최종 아키텍처 선택은 Traditional MVC + Service/Repository Layering 하나로 고정되어 있다.
- starter에는 domain/application/port/adapter package 구조를 만들지 않는다.
- starter는 host Spring Boot app 안에 붙는 library/starter module이며 MVC web controller를 만들지 않는다.
- host request path에서 portal network call을 하지 않는다.
- portal timeout/down 상황에서도 host app request path를 막지 않는 것이 Epic 2의 핵심 acceptance다.
- low-cardinality route/tag 정책을 ingest envelope 작성 전에 고정해야 한다.
- 30초 bucket boundary는 UTC 기준이며 time-buckets contract와 맞아야 한다.
- UI는 read model을 표시만 하고 state/rule/p95/endpoint priority를 재계산하지 않는다.
- Prometheus/scrape/query UI/high-cardinality custom metric/logs/traces/large tenancy는 MVP 범위 밖이다.
- 2인 1개월 MVP 기준으로 과한 구조를 경계한다.

Epic 2 계획 시 반드시 결정할 것:
- Epic 2 sprint goal
- 이번 sprint에 포함할 story 범위와 제외할 범위
- Story 2.1부터 개발 가능한 story 파일 목록과 권장 구현 순서
- Story 2.1이 starter bootstrap을 다시 포함하지 않도록 명시
- Micrometer observation binding, route normalization, rollup, bounded queue, async flush, envelope builder, negative path guard의 경계를 어떻게 나눌지
- non-blocking request path를 어떤 테스트로 증명할지
- low-cardinality guard를 어떤 acceptance criteria로 고정할지
- portal ingest acceptance 저장 구현은 Epic 3으로 넘기는 경계
- dashboard read model/p95/state/rule 계산은 Epic 4/5로 넘기는 경계

이번 작업에서 만들거나 갱신할 산출물:
- planning-artifacts/sprint-plan.md를 Epic 2 Sprint Plan으로 갱신하거나, 필요하면 기존 Epic 1 plan을 보존하고 새 Epic 2 plan 파일을 만든다.
- planning-artifacts/stories/2-1-micrometer-observation-binding.md
- planning-artifacts/stories/2-2-route-normalization-and-low-cardinality-guard.md
- planning-artifacts/stories/2-3-bucket-rollup-service.md
- planning-artifacts/stories/2-4-async-flush-worker.md
- planning-artifacts/stories/2-5-ingest-envelope-builder-service.md
- planning-artifacts/stories/2-6-negative-path-guard.md
- implementation-artifacts/sprint-status.yaml의 Epic 2 관련 상태가 planning 결과와 맞는지 확인한다.
- planning-artifacts/next-context-prompt.md를 다음 dev story 시작용으로 갱신한다.

이번 작업에서 하지 말 것:
- Epic 2 runtime code를 구현하지 않는다.
- Micrometer binding class, queue, HTTP client, retry/backoff, flush worker를 실제 코드로 만들지 않는다.
- portal ingest API나 repository를 구현하지 않는다.
- accepted_metric_buckets migration을 당겨오지 않는다.
- dashboard_snapshots, read model snapshot, p95/state/rule 계산을 당겨오지 않는다.
- Prometheus pull metric path나 query UI를 MVP 경로로 되돌리지 않는다.

권장 계획 방향:
- Epic 2 시작 전 Story 1.1 review 상태를 확인하고, 문제가 없으면 Epic 1 closure 기준을 문서에 반영한다.
- Story 2.1은 Micrometer observation binding 자체에 집중하고 starter module bootstrap은 제외한다.
- Non-blocking 보장은 Story 2.4 async flush worker의 핵심 acceptance로 두되, Story 2.1/2.3에서 request path network call 금지 전제를 계속 유지한다.
- Envelope contract는 Story 2.5에서 닫고, portal 저장/idempotency는 Epic 3으로 넘긴다.
- Negative path guard는 scrape config, pull metric query, arbitrary query UI가 starter MVP path에 없음을 테스트하도록 둔다.

완료 조건:
- Epic 2 Sprint Plan이 개발자가 바로 story 구현에 들어갈 만큼 명확하다.
- Epic 2 story 파일들이 각각 scope, acceptance criteria, tasks/subtasks, test requirements, developer guardrails를 포함한다.
- story 간 의존성과 구현 순서가 명시되어 있다.
- 이번 sprint 제외 범위가 명시되어 있다.
- 생성/수정 파일 목록과 다음에 시작할 첫 story를 보고한다.
```
