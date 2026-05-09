# Next Context Prompt

아래 프롬프트를 새 컨텍스트에 붙여넣고 시작한다.

```text
BMAD Dev Story를 진행해줘.

프로젝트:
- /Users/tlsdla1235/Desktop/study/observation
- BMAD 로컬 런타임은 이미 초기화되어 있음
- 현재 목표는 구현이다.

이번에 구현할 첫 Story:
- planning-artifacts/stories/1-2-portal-package-skeleton.md

반드시 먼저 읽을 파일:
- _bmad/custom/project-context.md
- planning-artifacts/sprint-plan.md
- implementation-artifacts/sprint-status.yaml
- planning-artifacts/epics.md
- planning-artifacts/architecture.md
- planning-artifacts/architecture-implementation-supplement.md
- planning-artifacts/acceptance-traceability.md
- planning-artifacts/stories/1-2-portal-package-skeleton.md

중요한 전제:
- PRD/UX 본문은 검증 완료 산출물로 보고 다시 쓰지 않는다.
- 기존 아키텍처 결정은 계승하지 않는다.
- 최종 아키텍처 선택은 Lightweight Hexagonal 하나로 고정되어 있다.
- Simple MVC, layered service/repository, hybrid architecture로 되돌아가면 안 된다.
- Prometheus/scrape/query UI/high-cardinality custom metric/logs/traces/large tenancy는 MVP 범위 밖이다.
- 2인 1개월 MVP 기준으로 과한 구조를 경계한다.

이번 Story에서 할 일:
- Story 1.2만 구현한다.
- observability-portal module/package skeleton을 만든다.
- Lightweight Hexagonal package suffix를 문서 기준으로 만든다.
- skeleton 상태에서 build/test가 통과하도록 최소 smoke test만 둔다.
- API, DB migration, persistence adapter, domain behavior는 구현하지 않는다.

이번 Story에서 하지 말 것:
- Story 1.3이나 Story 1.4를 같이 구현하지 않는다.
- accepted_metric_buckets, dashboard_snapshots, idempotency conflict, read model snapshot 저장을 만들지 않는다.
- ingest API나 dashboard API를 구현하지 않는다.
- starter module을 구현하지 않는다.

완료 조건:
- Story 1.2 acceptance criteria를 만족한다.
- 테스트를 실행하고 결과를 보고한다.
- 완료 후 implementation-artifacts/sprint-status.yaml에서 1-2-portal-package-skeleton 상태를 적절히 갱신할 수 있도록 변경 요약을 남긴다.
```
