# Next Context Prompt - Story 2.1 Dev

> Superseded as active handoff: 이 prompt는 Epic 2 Story 2.1 구현용 historical next-context다. 2026-05-25 이후 Epic 5/6 dashboard planning은 `planning-artifacts/epic5-6-dashboard-alignment-context.md`, `planning-artifacts/current-product-source-of-truth.md`, 최신 `planning-artifacts/contracts/*`, `planning-artifacts/prototypes/epic5-6-dashboard-flow-prototype.html`, 재정렬된 `planning-artifacts/epics.md`, `implementation-artifacts/sprint-status.yaml`을 우선한다.

아래 프롬프트를 새 컨텍스트에 붙여넣고 시작한다.

```text
BMAD dev-story를 MVC 버전 산출물 기준으로 진행해줘.

프로젝트:
- /Users/tlsdla1235/Desktop/study/observation
- BMAD 로컬 런타임은 이미 초기화되어 있음
- active 구현 기준은 루트의 planning-artifacts/, implementation-artifacts/ 문서다.
- archive/hexagonal-version/의 Hexagonal 산출물은 legacy 보관본이며 구현 기준으로 사용하지 않는다.
- bmad-restart-context-pack/ 문서는 제품 문제와 UX 의도 참고용이다.

이번에 구현할 Story:
- planning-artifacts/stories/2-1-micrometer-observation-binding.md
- Epic 2. Starter Direct Ingest Producer
- Story 2.1. Micrometer Observation Binding

현재 상태:
- Epic 1 portal foundation stories는 완료됨.
- Story 1.1 Starter Package Skeleton은 review 상태이며 starter module/package skeleton은 구현되어 있음.
- observability-spring-boot-starter module은 이미 존재한다.
- Story 2.1은 starter bootstrap을 다시 포함하지 않는다.
- implementation-artifacts/sprint-status.yaml 기준 Story 2.1은 ready-for-dev다.
- Epic 2 targeted IR 기준 첫 dev target은 Story 2.1이며, Story 2.4/2.5의 worker-envelope handoff와 idempotency key no-portal-lookup guard가 문서에 보정되어 있다.

반드시 먼저 읽을 파일:
- _bmad/custom/project-context.md
- planning-artifacts/mvc-selection.md
- planning-artifacts/sprint-plan.md
- planning-artifacts/implementation-readiness-review-epic-2.md
- implementation-artifacts/sprint-status.yaml
- planning-artifacts/epics.md
- planning-artifacts/project-structure.md
- planning-artifacts/architecture.md
- planning-artifacts/architecture-implementation-supplement.md
- planning-artifacts/acceptance-traceability.md
- planning-artifacts/contracts/metric-taxonomy.md
- planning-artifacts/contracts/ingest-envelope.md
- planning-artifacts/contracts/time-buckets.md
- planning-artifacts/stories/1-1-starter-package-skeleton.md
- planning-artifacts/stories/2-1-micrometer-observation-binding.md

중요한 전제:
- 최종 아키텍처 선택은 Traditional MVC + Service/Repository Layering 하나로 고정되어 있다.
- starter는 host Spring Boot app 안에 붙는 library/starter module이며 MVC web controller를 만들지 않는다.
- starter에는 domain/application/port/adapter package 구조를 만들지 않는다.
- Story 2.1은 Micrometer observation binding 자체에 집중한다.
- Story 2.1에서는 route normalization policy 완성, bucket rollup, bounded queue, async flush worker, HTTP ingest client, retry/backoff, ingest envelope builder를 구현하지 않는다.
- host request path에서 portal network call을 하지 않는다.
- Prometheus/scrape/query UI/high-cardinality custom metric/logs/traces/large tenancy는 MVP 범위 밖이다.

구현 목표:
- existing observability-spring-boot-starter module에서 시작한다.
- Micrometer/Spring observation binding에 필요한 최소 dependency를 추가한다.
- HTTP server observation을 starter internal observation input으로 변환한다.
- JVM CPU/heap과 datasource pool usage sample을 받을 수 있는 collection boundary를 만든다.
- request path에서 portal network call을 하지 않는 구조를 유지한다.
- synthetic observation 기반 binding test를 추가한다.
- forbidden package(application/port/adapter)가 생기지 않았음을 확인한다.

이번 작업에서 하지 말 것:
- starter module/package skeleton 재구현
- route normalization final policy 구현
- 30초 bucket rollup 구현
- bounded queue/flush worker 구현
- HTTP ingest client 구현
- retry/backoff 구현
- ingest envelope builder 구현
- portal ingest API/controller/repository 구현
- accepted_metric_buckets migration 구현
- dashboard read model/p95/state/rule 계산 구현
- Prometheus pull metric path나 query UI 구현

완료 조건:
- Story 2.1 acceptance criteria가 충족된다.
- Story 2.1 story file의 Tasks/Subtasks, Dev Agent Record, File List, Status가 구현 결과에 맞게 갱신된다.
- implementation-artifacts/sprint-status.yaml에서 Story 2.1 상태가 review로 갱신된다.
- 권장 검증 명령은 ./gradlew test 이며, 실행 불가 시 사유를 기록한다.
```
