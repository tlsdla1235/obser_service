---
artifactType: mvc-version-validation
architectureStyle: Traditional MVC
status: completed
date: 2026-05-09
---

# MVC Version Validation

## Result

조건부 통과. 기존 Lightweight Hexagonal 산출물의 제품/계약 의미를 유지하면서, 구현 구조만 Traditional MVC로 옮긴 버전으로 읽힌다.

## Architecture View

Pass:

- controller-service-repository 방향이 명확하다.
- UI는 read model 소비만 하고, repository는 lifecycle/rule semantics 계산을 하지 않는 경계가 유지된다.
- dashboard UI는 별도 backend deployable이 아니라 portal static view로 명시되어 있다.

Required Fix reflected:

- read model 생성 책임을 `DashboardReadModelService`로 고정했다.
- controller가 repository를 직접 참조하지 않는 guard를 추가했다.
- repository가 controller DTO를 참조하지 않는 guard를 추가했다.

## Engineering View

Pass:

- package split은 Spring MVC 구현자가 바로 시작할 수 있는 구조다.
- 기존 port/adapter 용어가 MVC layer 이름으로 치환되어 있다.

Required Fix reflected:

- starter는 web controller가 없는 library임을 명시하고, `model/service/spring/client/queue/config`로 따로 잡았다.
- no pull metric MVP path negative AC를 유지했다.
- host request path non-blocking AC와 timeout/drop policy를 유지했다.
- histogram bucket distribution contract와 starter canonical percentile 기준을 유지했다.

## UX View

Pass:

- UI를 read model consumer로 둔 점은 UX 의도와 맞다.
- state enum은 alive/slow/error 판단의 출발점으로 충분하다.

Required Fix reflected:

- `triageCards=[]`일 때 `zeroInsight`를 필수로 유지했다.
- `generatedAt`, freshness, endpoint freshness를 read model에 유지했다.
- stale/down/recovery 안내를 위한 recommended action과 recovery field를 유지했다.

## Product View

Pass:

- direct ingest와 epics 순서는 제품 약속과 정렬된다.

Required Fix reflected:

- 첫 화면 성공 기준 `alive / slow / error / where to look first`를 architecture AC로 유지했다.
- generic platform, arbitrary query, high-cardinality 확장 냄새를 MVP 금지 경로로 고정했다.
- MVC 버전에서도 controller 비대화와 repository 의미 계산을 금지했다.
