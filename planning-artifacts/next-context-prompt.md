# Next Context Prompt - MVC Version

아래 프롬프트를 새 컨텍스트에 붙여넣고 시작한다.

```text
BMAD Dev Story를 MVC 버전 산출물 기준으로 진행해줘.

프로젝트:
- /Users/tlsdla1235/Desktop/study/observation
- BMAD 로컬 런타임은 이미 초기화되어 있음
- 현재 목표는 구현이다.
- active 구현 기준은 루트의 planning-artifacts/, implementation-artifacts/ 문서다.
- archive/hexagonal-version/의 Hexagonal 산출물은 legacy 보관본이며 구현 기준으로 사용하지 않는다.
- bmad-restart-context-pack/ 문서는 제품 문제와 UX 의도 참고용이다.

이번에 구현할 첫 Story:
- planning-artifacts/stories/1-2-portal-package-skeleton.md

반드시 먼저 읽을 파일:
- _bmad/custom/project-context.md
- planning-artifacts/mvc-selection.md
- planning-artifacts/implementation-readiness-review.md
- planning-artifacts/project-structure.md
- planning-artifacts/sprint-plan.md
- implementation-artifacts/sprint-status.yaml
- planning-artifacts/epics.md
- planning-artifacts/architecture.md
- planning-artifacts/architecture-implementation-supplement.md
- planning-artifacts/acceptance-traceability.md
- planning-artifacts/stories/1-2-portal-package-skeleton.md

중요한 전제:
- PRD/UX 본문은 검증 완료 산출물로 보고 다시 쓰지 않는다.
- 기존 제품/계약 의미는 유지한다.
- 최종 아키텍처 선택은 Traditional MVC + Service/Repository Layering 하나로 고정되어 있다.
- domain/application/port/adapter package 구조를 만들지 않는다.
- controller는 repository를 직접 참조하지 않는다.
- repository는 state/rule/p95/endpoint priority를 계산하지 않는다.
- service는 controller package나 dto package에 의존하지 않는다.
- DTO는 controller boundary의 request/response shape로 둔다.
- UI는 read model을 표시만 하고 state/rule/p95/endpoint priority를 재계산하지 않는다.
- Prometheus/scrape/query UI/high-cardinality custom metric/logs/traces/large tenancy는 MVP 범위 밖이다.
- 2인 1개월 MVP 기준으로 과한 구조를 경계한다.

Story 1.2에서 확정된 선택:
- Build system: Gradle Groovy DSL
- Root project name: observation
- Module: observability-portal
- Gradle group/version: com.sst / 0.1.0-SNAPSHOT
- Portal Java package: com.observation.portal
- Package marker: package-info.java
- 권장 test command: ./gradlew :observability-portal:test

이번 Story에서 할 일:
- Story 1.2만 구현한다.
- Gradle Groovy DSL root build skeleton을 만든다.
- settings.gradle과 build.gradle을 사용한다.
- observability-portal module/package skeleton을 만든다.
- Traditional MVC package suffix를 문서 기준으로 만든다.
- package-info.java marker로 required package가 추적 가능하게 만든다.
- skeleton 상태에서 build/test가 통과하도록 최소 smoke test만 둔다.
- Gradle wrapper 생성이 가능하면 포함한다. 불가능하면 대체 실행 명령과 이유를 보고한다.

이번 Story에서 하지 말 것:
- Story 1.3이나 Story 1.4를 같이 구현하지 않는다.
- observability-spring-boot-starter module/source tree를 만들지 않는다.
- accepted_metric_buckets, dashboard_snapshots, idempotency conflict, read model snapshot 저장을 만들지 않는다.
- ingest API, dashboard API, admin API를 구현하지 않는다.
- Flyway migration, PostgreSQL/Testcontainers runtime을 추가하지 않는다.
- service behavior, repository implementation, DTO payload class를 만들지 않는다.
- static dashboard UI asset을 만들지 않는다.

완료 조건:
- Story 1.2 acceptance criteria를 만족한다.
- ./gradlew :observability-portal:test 또는 보고한 대체 명령을 실행하고 결과를 보고한다.
- 생성/수정 파일 목록을 보고한다.
- 완료 후 implementation-artifacts/sprint-status.yaml에서 1-2-portal-package-skeleton 상태를 review 또는 done으로 갱신할 수 있도록 변경 요약을 남긴다.
```
