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
