# QA: Endpoint Evidence Ranking UI 개선

## 배경
- resource sampler 누락으로 CPU/Heap/DB pool evidence가 비어 보이던 버그는 starter sampler 수정으로 해결된 상태다.
- endpoint route가 `UNKNOWN`으로 저장되던 문제도 별도 커밋에서 수정된 상태다.
- 이 문서는 데이터 수집 버그가 아니라 snapshot/instance dashboard의 endpoint evidence 표현을 더 진단 친화적으로 개선하기 위한 QA 메모다.
- 원래 버그 플랜(`implementation-artifacts/plan-instance-evidence-empty-modal.md`)과 섞지 않고 독립 후속 작업으로 다룬다.

## 첨부 스크린샷
- 이미지 1: `/var/folders/nv/3z_mjb_x0zjf_2qm1xqbrby00000gn/T/codex-clipboard-98bcbd93-673f-429b-b4c0-b33d59a40a5e.png`
- 이미지 2: `/var/folders/nv/3z_mjb_x0zjf_2qm1xqbrby00000gn/T/codex-clipboard-81c25c1a-0842-4803-aca5-8b5ba33e5c58.png`
- 이미지 3: `/var/folders/nv/3z_mjb_x0zjf_2qm1xqbrby00000gn/T/codex-clipboard-2b81ec55-c869-4f87-a87a-97924502a823.png`
- 이미지 4: `/var/folders/nv/3z_mjb_x0zjf_2qm1xqbrby00000gn/T/codex-clipboard-edb26a70-850a-4d3e-bfec-a2af10c2d8a9.png`
- 이미지 5: `/var/folders/nv/3z_mjb_x0zjf_2qm1xqbrby00000gn/T/codex-clipboard-712d5d52-8f31-49da-9ab0-b57b32b3495b.png`
- 이미지 6: `/var/folders/nv/3z_mjb_x0zjf_2qm1xqbrby00000gn/T/codex-clipboard-dcfc8db3-a439-440d-83fe-7edcbed5a236.png`

## 관찰
- 이미지 1처럼 RED ERRORS가 7.619%로 존재하는데 `FIRST LOOK CANDIDATES`, `ENDPOINT EVIDENCE`가 모두 "후보가 없습니다"로 나오면 사용자 관점에서 어색하다. 에러가 있으면 어떤 endpoint가 영향을 주는지 evidence가 있어야 한다는 기대가 생긴다.
- 이미지 2의 날짜 map 영역에 있는 `전체 선택` 같은 대량 선택 버튼은 현재 흐름에서는 필요성이 낮아 보인다. snapshot을 빠르게 고르는 화면이면 대량 선택보다 날짜/slot 탐색성이 우선이다.
- 이미지 3, 4처럼 evidence 영역이 흰색과 회색 중심이라 실제 문제가 나는 endpoint가 시각적으로 잘 드러나지 않는다.
- 이미지 4처럼 에러 endpoint 하나만 테이블에 남는 표현은 진단에 부족하다. 이미지 5, 6처럼 10개 또는 20개 endpoint를 정렬 가능한 랭킹으로 보여주는 편이 더 유용하다.

## 목표
- endpoint evidence를 "단일 후보 카드"가 아니라 "현재 window에서 의미 있는 endpoint 상위 N개를 보여주는 랭킹/분포 표"로 탐색할 수 있게 한다.
- 요청량, 에러율, 느림 비율을 같은 표면에서 비교할 수 있게 한다.
- 에러와 느린 API는 기존 UI 톤을 해치지 않는 절제된 색상으로 강조한다.
- backend/read model이 제공하지 않는 값을 프론트에서 추정하거나 가짜로 만들지 않는다.

## 요구사항 후보
1. 정렬 옵션을 제공한다.
   - `requestCount desc`
   - `errorRate desc`
   - `slowShare >500ms desc`
2. 표시 개수를 선택할 수 있게 한다.
   - `max 10`
   - `max 20`
3. 각 endpoint row에는 가능한 범위에서 다음 값을 보여준다.
   - normalized route 또는 endpoint key
   - requestCount
   - errorCount
   - errorRate
   - slowCount >500ms
   - slowShare >500ms
   - duration bucket distribution
4. 에러율이 있는데 endpoint evidence가 비어 있는 경우에는 단순 "없음"보다 원인을 분리해서 표현한다.
   - endpoint breakdown 자체가 수집되지 않은 경우
   - read model이 해당 window에서 endpoint evidence를 만들 수 없는 경우
   - source contract상 raw path/query/per-request sample이 없는 경우
5. 날짜 map의 `전체 선택` 버튼은 제거하거나 숨김 처리하는 방향을 검토한다.

## 시각 표현 방향
- 요청량은 중립색 막대 또는 숫자 중심으로 표현한다.
- 에러율은 붉은 계열 막대로 표시하되 화면 전체를 경고색으로 덮지 않는다.
- 느림 비율은 주황/갈색 계열 막대로 표시한다.
- 첫 확인 대상은 `FIRST LOOK`, 후속 확인 대상은 `ATTENTION`처럼 작고 명확한 badge로 표시한다.
- source/contract 설명은 유지하되 진단 흐름을 방해하지 않도록 보조 정보로 낮춘다.

## 조사 필요
- 현재 backend/read model이 endpoint별 `slowCount >500ms`, `slowShare >500ms`, duration bucket distribution을 제공하는지 확인한다.
- `accepted_metric_buckets.endpoints_json`에 저장된 endpoint evidence schema를 확인한다.
- instance dashboard와 snapshot dashboard가 같은 endpoint evidence 구조를 사용하는지 확인한다.
- 데이터가 부족한 경우 프론트 개선만으로 해결 가능한지, read model 확장이 필요한지 나눈다.

## 검증
- 관련 frontend typecheck/test를 실행한다.
- 가능하면 8080에서 instance/snapshot dashboard를 열고 endpoint evidence 표가 실제 데이터로 보이는지 확인한다.
- 에러가 있는 window에서 "evidence 없음"이 나오면 원인별 empty state가 충분히 설명되는지 확인한다.
- read model 확장이 필요한 경우 문서에 후속 작업으로 명확히 남긴다.

## 2026-06-13 구현 후속 메모
- 이번 구현은 frontend가 이미 받은 field만 사용해 Application Dashboard, Instance Dashboard live/snapshot, Snapshot Detail의 endpoint evidence를 같은 랭킹/막대형 visual language로 정리했다.
- Application Dashboard는 `endpointPriority[].evidence.requestCount`, `errorCount`, `errorRate`, `slowShare`, `durationBuckets`만 사용한다. backend cap이 현재 5개라 UI limit도 받은 항목 범위 안에서만 동작한다.
- Instance Dashboard live/snapshot은 `endpointEvidence.items[]`의 `requestCount`, `errorCount`, `errorRate`, `presenceOnSelectedInstance`, `localDisplayOrder`만 표시한다. endpoint별 `durationBuckets`, `slowCountOver500ms`, `slowShareOver500ms`는 read model에 없으므로 `미제공`으로 둔다.
- Snapshot Detail은 stored `snapshotEndpointEvidence.items[]`의 `requestCount`, `errorRate`, `durationBuckets`를 표시하되, 저장 projection에 없는 `errorCount`, `slowCountOver500ms`, `slowShareOver500ms`는 계산하지 않고 `미제공`으로 표시한다.
- 후속 read model 확장 후보는 별도 작업으로 분리한다: endpoint priority cap 10/20 확장, instance endpoint별 duration bucket/slow evidence 추가, snapshot stored endpoint evidence의 `errorCount`와 slow evidence 저장 확장.

## 2026-06-14 Instance Dashboard normalized table 구현 메모
- Instance Dashboard live/snapshot read model의 `endpointEvidence.items[]`에 endpoint별 `durationBuckets`, `slowCountOver500ms`, `slowShareOver500ms` nullable field를 추가했다.
- `slowCountOver500ms`와 `slowShareOver500ms`는 endpoint duration bucket에 500ms boundary가 있을 때만 계산한다. bucket이 없거나 100ms/500ms boundary 기반 분포를 만들 수 없으면 frontend는 `미제공` 또는 `확인할 수 없음`으로 표시한다.
- Instance Dashboard endpoint evidence cap은 max 10으로 확장했다. max 20은 아직 UI/API 계약에 넣지 않고 후속 후보로 둔다.
- `InstanceDashboardSurface`의 endpoint evidence 영역은 SOT mockup의 `NORMALIZED ENDPOINT EVIDENCE TABLE`에 맞춰 compact table로 변경했다.
- sort 옵션은 `requestCount desc`, `errorRate desc`, `slowShareOver500ms desc` 세 개만 제공한다. `server order`, `errorCount desc`, `slowCountOver500ms desc`는 제공하지 않는다.
- live mode와 snapshot mode는 기존처럼 같은 `InstanceDashboardSurface`를 재사용한다. snapshot mode는 selected snapshot row window 기준 read model만 사용하고 live fallback을 섞지 않는다.
- 이번 작업은 Instance Dashboard selected instance evidence 탐색만 다루며 Application Dashboard top20, snapshot stored endpoint evidence의 `errorCount`/slow 저장 확장, endpoint p95/p99, root cause/priority 재판정은 포함하지 않는다.

## 2026-06-14 1차 QA 반영 메모
- 8080 확인에서 `requestCount desc`가 선택돼도 오류 endpoint 1개만 보이는 문제가 확인됐다. 원인은 backend가 application anchor가 없는 selected instance endpoint를 오류/느림 후보 위주로만 승격해, 정상 고호출 endpoint가 read model 후보에 들어오지 못하던 것이다.
- endpoint table 후보 선정은 application anchor를 보존한 뒤 selected instance의 호출량 상위, 느림 상위, 오류율 상위 endpoint를 균형 있게 섞는 방식으로 변경했다.
- 호출량 상위 후보는 `requestCount`가 minimum sample 미만이어도 포함한다. 표 전체 data quality는 sample-limited일 수 있지만, 사용자가 "어느 API가 가장 많이 호출됐는지"를 확인하는 탐색 표에서는 수집된 endpoint 자체를 숨기지 않는 쪽이 더 일관적이다.
- frontend 정렬 옵션은 그대로 `requestCount desc`, `errorRate desc`, `slowShareOver500ms desc` 세 개만 유지한다. 이번 QA 반영은 정렬 UI가 아니라 backend 후보 집합을 넓히는 변경이다.

## 2026-06-14 2차 QA 반영 메모
- Instance Dashboard endpoint table에서 sort option을 바꿔도 화면이 변하지 않는 것처럼 보이는 문제가 확인됐다.
- 코드상 정렬 state는 적용되고 있었지만, `errorRate desc`는 오류 endpoint가 이미 첫 번째이고 `slowShareOver500ms desc`는 모든 row가 0%인 데이터에서는 결과 순서가 server 제공 순서와 같아 보일 수 있었다.
- 정렬 tie-breaker를 보강해 `errorRate desc`는 errorCount/requestCount, `slowShareOver500ms desc`는 slowCount/requestCount를 보조 기준으로 사용하도록 바꿨다. 완전 동률일 때만 기존 `localDisplayOrder`를 보존한다.
- 그래도 값이 모두 같거나 이미 같은 순서일 때는 table 상단에 `동일값` 또는 `동일 순서` 상태와 현재 정렬 설명을 표시해, sort control이 동작하지 않는 것처럼 보이지 않게 했다.
