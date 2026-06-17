# UI Polish Deploy Prep 추적 문서

> 대상 브랜치: `codex/ui-polish-deploy-prep`
> 대상 커밋: `5ba71e7` (`feat: 사용자 친화적으로 대시보드/문서 UI 정리`)
> 작성일: 2026-06-16

## 범위와 불변 조건

이번 커밋은 **순수 UI/UX 정리**만 포함한다. 백엔드, read-model 생성, read-model 계산 로직, lifecycle state 판정, endpoint priority 산정, resource pattern 판정은 변경하지 않았다.

UI는 서버가 내려준 read model을 더 읽기 쉬운 형태로 표시하도록 바뀌었고, 기존 계약상 필요한 원본 문구와 라벨은 접이식 상세 영역으로 보존했다. 즉 화면의 기본 노출 정보는 줄였지만, read-model contract guard가 기대하는 계약 단서는 삭제하지 않았다.

## 근거 자료

- 의도, 항목별 근거, 계약 가드 결정: `docs/ui-qa-checklist.md`
- 실제 변경 diff: `git show 5ba71e7` 또는 `git diff 5ba71e7^ 5ba71e7`
- 대상 파일:
  - `frontend/src/app/components/dashboard.tsx`
  - `frontend/src/app/components/instance-dashboard-surface.tsx`
  - `frontend/src/app/components/snapshot-history-panel.tsx`
  - `frontend/src/app/components/docs.tsx`

## 항목별 Before → After

| 항목 | Before | After | 주요 위치 |
| --- | --- | --- | --- |
| 헤더 정리 | 라이브/스냅샷 대시보드 헤더에 `Server read model`, `lifecycle state`, `endpoint priority`, `resource pattern` 등 개발자용 설명과 `mode/source/baseline/window` 중심의 메타 그리드가 노출됐다. 생성 시각도 좁은 셀에서 잘릴 수 있었다. | 프로젝트명, 환경, 생성 시각을 한 줄로 보여주고, 모드와 기준 시간 배지만 남겼다. 스냅샷/라이브 모두 같은 헤더 구조를 사용한다. | `dashboard.tsx` `DashboardContext` |
| StarterConnection 문구 제거 | `heartbeat는 accepted bucket freshness나 application lifecycle state를 직접 만들지 않습니다`, `starter disconnected · 앱 연결 신호`처럼 내부 read-model 의미를 설명하는 helper text가 보였다. | 마지막 연결 신호만 간단히 표시한다. 사용자가 당장 읽을 필요 없는 계약 설명은 기본 화면에서 제거했다. | `dashboard.tsx` `StarterConnectionStrip` |
| 상태 색 강조 | First look 후보, endpoint evidence 행, 인스턴스 상태가 텍스트와 배지만으로 표시되어 문제 유형이 한눈에 들어오지 않았다. | 오류 계열은 red, 지연 계열은 amber 계열의 은은한 배경/좌측 보더로 강조한다. 인스턴스의 `applicationStateCode`도 기존 metric state 색상 클래스를 재사용해 상태별로 보이게 했다. | `dashboard.tsx`, `instance-dashboard-surface.tsx` |
| 히스토그램·툴팁 개선 | `duration buckets · accepted bucket 분포`처럼 내부 source 중심 문구가 보였고, 표본이 없을 때도 빈 회색 막대처럼 보였다. 툴팁도 `<= 50ms` 형식이었다. | `응답 시간 분포`, `빠름 → 느림`으로 의미를 바꿔 표시한다. 표본이 0이면 안내 문구를 보여주고, 툴팁은 `50ms 이내 · N건`, `1초 이내 · N건`처럼 읽히게 했다. | `dashboard.tsx` `EndpointBucketStrip`, `formatLatencyBound` |
| 설명문 친화화 | Resource evidence, Endpoint evidence, Instance summary가 `server read model`, `USE hint`, `wide modal`, `p95/p99`, `raw path` 같은 내부 구현 단어를 많이 드러냈다. | 사용자가 볼 행동 단위로 바꿨다. 예: 리소스는 DB·CPU·메모리 임계치 접근 여부, 엔드포인트는 요청량·오류·지연 기준 확인 대상, 인스턴스는 요청·오류·지연 요약으로 설명한다. | `dashboard.tsx` |
| 48-slot 제거 | Snapshot history 헤더에 `하루 48개 SLOT` 배지가 크게 노출됐다. | 배지를 제거하고, 미사용 `StatusBadge` 헬퍼도 함께 삭제했다. | `snapshot-history-panel.tsx` |
| 스냅샷 일관화 | 스냅샷 모드가 `read model`, `현재 metric으로 재계산하지 않음` 같은 내부 설명을 별도로 노출했다. | 라이브 대시보드와 같은 기본 surface를 사용하면서 `저장된 시점의 대시보드를 그대로 복원했습니다`처럼 짧은 사용자 문구로 정리했다. | `dashboard.tsx` `SnapshotModeBanner`, `DashboardContext` |
| 인스턴스 대시보드 단순화 | Context note, Application state reference, Read semantics가 원본 계약 값과 긴 면책 문구를 기본 화면에 직접 노출했다. | 기본 화면은 인스턴스 ID, 앱/환경, 생성 시각, 기준 구간, 애플리케이션 상태, 조회 기준만 보여준다. 원본 계약 값은 `InstanceReadModelDetails` 접이식 상세 영역으로 이동했다. | `instance-dashboard-surface.tsx` |
| endpoint table 설명문 제거 | `source: accepted_metric_buckets.endpoints_json`, `raw path/query/per-request sample 없음`, `정렬: requestCount desc` 같은 검증자용 설명이 테이블 위에 노출됐다. | 기본 설명은 `이 인스턴스의 엔드포인트별 요청·오류·지연 기록입니다`로 줄이고, source/scope/selection/display order 메타 그리드와 정렬 설명을 제거했다. 단, 계약 가드가 요구하는 `NORMALIZED ENDPOINT EVIDENCE TABLE` 제목은 유지했다. | `instance-dashboard-surface.tsx` `EndpointEvidencePanel` |
| Docs scroll-spy 수정 | 문서 TOC의 활성 섹션은 클릭 상태에 의존해, 스크롤만 했을 때 현재 섹션 표시가 따라오지 않았다. | `IntersectionObserver`로 보이는 섹션을 감지해 TOC active 상태를 갱신한다. 섹션에는 `scroll-mt-24`를 적용해 상단 nav와 겹치지 않게 했다. | `docs.tsx` |
| Docs 내용 개편 | Spring Boot 연결 설정 설명이 하나의 목록으로 나열되어 필수값과 선택값의 우선순위가 덜 명확했다. route attribution allowlist 안내도 없었다. | 꼭 채워야 하는 값과 채워 두면 좋은 값을 분리하고, endpoint evidence를 깔끔하게 보기 위한 route-attribution allowlist 예시와 제약을 추가했다. | `docs.tsx` |

## 계약 가드 충돌 우회 결정

`read-model-contract-guard`는 인스턴스 대시보드 소스에 특정 read-model 계약 문구와 라벨이 존재하는지 검사한다. 특히 다음 성격의 값이 strict-parity 계약으로 묶여 있었다.

- `Application Snapshot 자체는 dashboard_snapshots.read_model_json...`로 시작하는 스냅샷 면책 문구
- `InfoCell label="mode"`와 `InfoCell label="source"` 같은 원본 라벨
- `instance top-level state="없음"` 라벨
- `NORMALIZED ENDPOINT EVIDENCE TABLE` 섹션 제목
- read semantics 플래그와 source/window 관련 원본 값

기본 화면에서 이 문구를 모두 제거하면 사용성은 좋아지지만 `guard:read-model-contract`가 실패한다. 그래서 커밋 `5ba71e7`은 **기본 화면은 사용자 친화적으로 단순화하고, 계약상 필요한 원본 값은 기본 접힘 상태의 `InstanceReadModelDetails`에 보존**하는 방식으로 우회했다.

이 결정으로 두 조건을 동시에 만족한다.

- 사용자는 처음 보는 화면에서 내부 계약 문구에 압도되지 않는다.
- 검증자는 접이식 상세 영역에서 read-model 계약 문구와 원본 라벨을 확인할 수 있고, 계약 가드도 통과한다.

## 검증 기록

커밋 `5ba71e7` 기준으로 다음 검증이 통과했다. 추적 문서 작성 후 현재 작업트리에서도 동일 명령을 다시 실행해 통과를 재확인했다.

- `npm run typecheck`
- `npm run guard:read-model-contract`
- `npm run build`

위 검증은 `docs/ui-qa-checklist.md`의 진행 상태와 커밋 메시지에도 기록되어 있다.
