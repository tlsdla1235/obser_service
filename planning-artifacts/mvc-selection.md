# MVC Architecture Selection

이 프로젝트의 active 구현 기준은 **Traditional MVC + Service/Repository Layering**이다. Portal 내부 package 배치는 feature-first MVC로 둔다.

기존 Lightweight Hexagonal 산출물은 `archive/hexagonal-version/` 아래에 legacy 버전으로 보존했다. 구현자는 archive 문서를 기준으로 작업하지 않는다.

## Folder Map

| Folder | 역할 |
|---|---|
| `planning-artifacts/` | MVC 기준 아키텍처, 계약, 에픽, 스프린트, 스토리 산출물 |
| `planning-artifacts/contracts/` | starter와 portal 사이의 데이터 계약 및 read model 계약 |
| `planning-artifacts/stories/` | MVC 기준 첫 구현 스프린트 story |
| `implementation-artifacts/` | MVC 기준 sprint status |
| `archive/hexagonal-version/` | Lightweight Hexagonal legacy 산출물 보존 |

## Conversion Rule

- 제품 약속과 UX 의도는 유지한다.
- direct ingest, 30초 bucket, 15분 current/baseline window, starter canonical percentile, read model source of truth는 유지한다.
- 기존 Hexagonal의 `domain/application/port/adapter` 경계는 복원하지 않는다.
- Portal의 `domain` package는 순수 DDD domain layer가 아니라 catalog, ingest, dashboard 같은 업무 기능 묶음 namespace다.
- `controller/service/repository/model/dto` 책임은 최상위 layer package가 아니라 `domain.<feature>` 아래에 모아 둔다.
- lifecycle state, insight rule, endpoint priority, starter canonical percentile 표시 정책은 feature-first MVC의 `service` 또는 `model` package에 둔다.
- controller, repository, DB, frontend는 판단 로직의 source of truth가 아니다.
