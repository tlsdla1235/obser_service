# Frontend Component Screenshot Review Workflow

이 문서는 새 컨텍스트에서 Observation Portal 프론트엔드 컴포넌트를 스크린샷 기반으로 함께 검토하기 위한 진행 지침이다.
Codex는 각 컴포넌트를 캡처하고, 화면에서 발생 가능한 상태와 조건을 설명한 뒤, 사용자가 제시한 디자인/문구 방향에 맞춰 수정한다.

## 작업 시작 지침

1. 작업 위치는 `/Users/tlsdla1235/Desktop/study/observation`이다.
2. 작업 브랜치는 `codex/frontend-component-review-workflow`이다. 새 컨텍스트에서 먼저 `git status --short --branch`로 브랜치와 변경 사항을 확인한다.
3. 프론트엔드는 `/Users/tlsdla1235/Desktop/study/observation/frontend`의 Vite React SPA다.
4. 화면 캡처가 필요하면 프론트 dev server를 실행하고, Codex in-app Browser 또는 Playwright로 `/`, `/dashboard`, `/docs`를 확인한다.
5. 이 리뷰는 서버 연결 없는 frontend-first mock review로 진행해도 된다. Dashboard처럼 backend/auth/read model에 의존하는 화면은 실제 서버를 붙이지 말고 mock API, fixture, 또는 preview 전용 harness로 상태를 재현한다.
6. mock 기반 캡처는 디자인/문구/상태 설명 검토에 적합하다. 단, backend contract 자체의 정확성이나 실제 인증 흐름 검증은 별도 통합 테스트 범위로 남긴다.
7. 사용자가 디자인 방향이나 텍스트 방향을 말하기 전에는 코드 수정으로 바로 넘어가지 않는다. 먼저 캡처, 상태 설명, 수정 후보를 제시한다.
8. 사용자의 피드백은 즉시 반영하지 말고 "의도 기록"으로 누적한다. 사용자가 반영을 승인하거나 한 컴포넌트 묶음의 리뷰가 끝났을 때만 수정한다.
9. 수정 시에는 누적된 의도 기록을 짧게 재확인한 뒤 해당 컴포넌트만 좁게 수정하고, 같은 mock 조건에서 다시 캡처한다.

## 리뷰 진행 방식

각 컴포넌트마다 아래 형식으로 진행한다.

1. 스크린샷: 현재 보이는 화면 또는 해당 패널이 보이는 뷰포트를 캡처한다.
2. 화면 목적: 사용자가 이 영역에서 무엇을 판단해야 하는지 짧게 설명한다.
3. 상태와 조건: 코드상 발생 가능한 loading, empty, error, auth, selected, data-present 상태와 그 조건을 정리한다.
4. 디자인 관찰: 밀도, 위계, 여백, 강조, 버튼/배지/테이블/카드 사용을 관찰한다.
5. 문구 관찰: 사용자가 이해하기 어려운 용어, 영어/한국어 혼용, 지나치게 내부 구현적인 표현을 표시한다.
6. 사용자 개입 요청: "디자인 방향", "텍스트 설명 방향", "유지할 점"을 사용자에게 묻는다.
7. 의도 기록: 사용자의 디자인 방향, 텍스트 설명 방향, 유지할 점, 보류할 점을 누적해서 기록한다.
8. 수정 승인 확인: 사용자가 "이제 반영", "이 묶음 수정", "마지막에 적용"처럼 명시하기 전에는 코드 수정하지 않는다.
9. 수정과 재캡처: 승인 후 코드 수정하고 같은 mock 조건에서 다시 캡처한다.

## Mock Review 원칙

- 서버 연결 없이 프론트 중심으로 작업한다.
- 실제 backend, GitHub OAuth, credential 발급, read model 저장소가 없어도 리뷰가 가능해야 한다.
- 필요한 상태는 fixture로 만든다. 예를 들어 project 없음, application 없음, dashboard loading/error/ready, recovery, triage 있음/없음, credential rotate 직후 1회 표시, snapshot detail 없음/있음 같은 상태를 각각 재현한다.
- 기존 앱에 mock 구조가 없으면 먼저 preview 전용 mock harness 또는 API mocking 레이어를 제안하고, 사용자가 동의하면 만든다.
- mock 데이터는 실제 운영 데이터처럼 보이되 secret, token, credential raw value처럼 민감한 값은 가짜 값만 사용한다.
- mock 리뷰 결과는 디자인과 설명 문구 판단을 위한 것이다. API contract, 보안, 인증, backend 연동 성공 여부는 별도 검증으로 다룬다.

## 의도 기록 방식

컴포넌트를 보며 사용자가 말한 방향은 즉시 코드로 옮기지 않고 아래 형식으로 누적한다.

```text
컴포넌트:
현재 문제:
디자인 의도:
텍스트 의도:
유지할 점:
수정 후보:
상태/색상/강조 규칙:
숨기거나 tooltip로 보낼 정보:
날짜/숫자 표시 규칙:
보류/나중:
확정 여부:
```

사용자가 여러 컴포넌트를 연속으로 검토하고 싶어 하면 각 컴포넌트별 의도 기록을 쌓는다.
마지막에 사용자가 승인하면, 기록된 의도를 기준으로 한 번에 수정한다.

## 누적 리뷰 결정 및 의도 기록

### Nav

```text
컴포넌트: Nav
현재 문제: 없음.
디자인 의도: 현재 상태 유지.
텍스트 의도: 현재 상태 유지.
유지할 점: 브랜드, Dashboard/Docs 이동, GitHub 로그인 액션의 현재 위계.
수정 후보: 없음.
상태/색상/강조 규칙: 현재 active 표시 유지.
숨기거나 tooltip로 보낼 정보: 없음.
날짜/숫자 표시 규칙: 해당 없음.
보류/나중: 없음.
확정 여부: 통과. 구현 변경 불필요.
```

### Landing

```text
컴포넌트: Landing
현재 문제: 없음.
디자인 의도: 현재 상태 유지.
텍스트 의도: 현재 상태 유지.
유지할 점: 현재 첫인상, starter-first 흐름, Dashboard/Docs 진입 구조.
수정 후보: 없음.
상태/색상/강조 규칙: 현재 상태 유지.
숨기거나 tooltip로 보낼 정보: 없음.
날짜/숫자 표시 규칙: 해당 없음.
보류/나중: 없음.
확정 여부: 통과. 구현 변경 불필요.
```

### Docs

```text
컴포넌트: Docs
현재 문제: 데스크톱에서 스크롤 위치에 따라 좌측 TOC active 항목이 자동 갱신되지 않음.
디자인 의도: 문서를 읽는 현재 위치가 좌측 TOC에 자연스럽게 반영되면 좋음.
텍스트 의도: 현재 문구는 유지.
유지할 점: 처음 쓰는 사용자가 로그인, project 등록, starter 연결, 첫 데이터 확인까지 따라가는 흐름.
수정 후보: desktop viewport에서 IntersectionObserver 등으로 visible section을 감지해 active TOC 갱신.
상태/색상/강조 규칙: 기존 active 항목 강조 스타일 유지.
숨기거나 tooltip로 보낼 정보: 없음.
날짜/숫자 표시 규칙: 해당 없음.
보류/나중: mobile TOC 동작은 구현 승인 시 재확인.
확정 여부: 의도 기록 완료. 구현은 최종 승인 전 보류.
```

### ProjectRail

```text
컴포넌트: ProjectRail
현재 문제: recentConcern label/code/source가 row 안에 길게 노출되어 project 선택 목록이 내부 구현 정보처럼 보임. Project 등록 CTA 강조도 약함.
디자인 의도: Project row에서는 "문제 있음/확인 필요" 정도의 최소 정보만 굵게 전달하고, 상세 원인이나 source는 dashboard로 넘긴다. Project 등록 CTA는 색상, 배경, border 등으로 더 눈에 들어오게 한다.
텍스트 의도: recentConcern의 label/code/source를 길게 보여주지 않는다.
유지할 점: project 선택 목록의 간결한 navigation 역할.
수정 후보: recentConcern 영역을 짧은 한국어 상태 문구로 축약. 등록 CTA를 레일 하단에서 더 명확한 primary/subtle-primary 액션으로 조정.
상태/색상/강조 규칙: selected project 강조는 유지하되, 등록 CTA는 일반 row와 구분되는 강조를 둔다.
숨기거나 tooltip로 보낼 정보: recentConcern code/source는 기본 노출하지 않음. 필요 시 tooltip 후보.
날짜/숫자 표시 규칙: project count 등 현재 숫자 노출은 유지 가능.
보류/나중: 구체 색상은 구현 승인 시 시안에서 확정.
확정 여부: 의도 기록 완료. 구현은 최종 승인 전 보류.
```

### ApplicationRail

```text
컴포넌트: ApplicationRail
현재 문제: lifecycle badge와 metric/starter 카드에 source, timestamp, accepted bucket 등 내부 정보가 길게 노출됨.
디자인 의도: lifecycle badge는 한국어 label과 은은한 상태 색상을 사용한다. Metric/Starter 카드는 핵심 상태만 먼저 스캔되게 한다.
텍스트 의도: lifecycle 표시는 "[주의] application_state: degraded ?" 형태로 두되, ? tooltip에서 state 의미를 설명한다. Metric/Starter 카드는 "fresh", "alive · fresh" 같은 핵심 상태를 먼저 보여준다.
유지할 점: metric data와 starter connection을 별도 축으로 분리해서 보여주는 구조.
수정 후보: accepted_bucket, starter heartbeat source, 긴 timestamp는 hover tooltip로 이동. lifecycle state별 설명 tooltip 추가.
상태/색상/강조 규칙: active=정상/연한 초록, degraded=주의/연한 노랑·주황, stale=데이터 지연/연한 노랑, down=수집 끊김/연한 빨강, idle=트래픽 적음/연한 파랑, unknown=판단 보류/연한 회색, waiting_first_data=첫 데이터 대기/연한 보라·파랑.
숨기거나 tooltip로 보낼 정보: accepted_bucket, starter heartbeat source, 긴 timestamp, lifecycle state 의미.
날짜/숫자 표시 규칙: ISO timestamp는 "2026년 6월 4일 09:29"처럼 읽기 쉬운 날짜로 표시.
보류/나중: lifecycle tooltip의 정확한 UI 형태.
확정 여부: 의도 기록 완료. 구현은 최종 승인 전 보류.
```

ApplicationRail lifecycle state 설명 명세:

- `active`: 정상 / 연한 초록 / 최근 데이터가 충분하고 눈에 띄는 지연·오류 신호가 없습니다.
- `degraded`: 주의 / 연한 노랑·주황 / 데이터는 들어오지만 지연, 오류, 리소스 사용량 중 확인할 신호가 있습니다.
- `stale`: 데이터 지연 / 연한 노랑 / 최근 metric data가 한동안 들어오지 않았습니다. 앱 장애로 단정하지 말고 수집 경로를 확인하세요.
- `down`: 수집 끊김 / 연한 빨강 / metric data가 오래 들어오지 않았습니다. 앱이 내려갔다고 확정하지 말고 starter 연결과 트래픽을 함께 확인하세요.
- `idle`: 트래픽 적음 / 연한 파랑 / 요청 수가 적어서 이상 여부를 판단하지 않습니다.
- `unknown`: 판단 보류 / 연한 회색 / 데이터가 부족해 현재 상태를 단정할 수 없습니다.
- `waiting_first_data`: 첫 데이터 대기 / 연한 보라·파랑 / 앱은 등록됐지만 아직 첫 metric data가 들어오지 않았습니다.

### Dashboard Core

```text
컴포넌트: DashboardContext, MetricStateStrip, StarterConnectionStrip, RecoveryNotice, MetricScalars
현재 문제: `2026-06-04T09:30:00Z`처럼 ISO timestamp와 `T`, `Z`, `->`가 그대로 노출되어 사용자가 시간을 읽기 어렵다. `stale at`, `down at`, `state impact`는 기본 화면에 함께 보이면 정보량이 많고, 사용자가 현재 상태 판단과 내부 threshold를 혼동할 수 있다.
디자인 의도: 운영자가 현재 상태와 판단 구간을 빠르게 읽을 수 있도록 시간 표현을 사람이 읽는 한국어 날짜/시간으로 풀어 쓴다. 기본 화면은 현재 판단과 직접 행동에 필요한 정보만 남기고, threshold/내부 계약 정보는 tooltip로 보낸다.
텍스트 의도: `generated`, `current`, `baseline`, `last bucket`, `stale at`, `down at`, `last heartbeat` 등에 표시되는 시간은 `2026년 6월 4일 09:30`처럼 표기한다. `last bucket`과 `last heartbeat`도 ISO 원문 대신 같은 한국어 날짜/시간 규칙을 적용한다. 기간은 `2026년 6월 4일 09:00 - 09:30`처럼 같은 날짜를 불필요하게 반복하지 않는 형식을 후보로 둔다.
유지할 점: current/baseline/generation/last bucket/heartbeat의 의미와 metric data와 starter heartbeat가 서로 다른 신호라는 구조는 유지한다.
수정 후보: 공통 날짜 formatter를 통해 Dashboard Core의 사용자 노출 timestamp와 window range를 한국어 가독형으로 변환. `stale at`, `down at`, `state impact`는 기본 정보 카드에서 제거하고 `?` tooltip이나 상태 설명 tooltip 안으로 이동한다.
상태/색상/강조 규칙: 시간 표현 변경은 상태 색상 변경과 분리한다.
숨기거나 tooltip로 보낼 정보: 원본 ISO timestamp는 기본 노출하지 않는다. 정확한 원본값이 필요하면 tooltip 후보로 둔다. `stale at`, `down at`, `state impact`는 기본 화면에서 숨기고 tooltip에서만 설명한다. tooltip에서도 원문 라벨보다 "데이터 지연 기준", "수집 끊김 기준", "상태 판단 영향 없음"처럼 사용자 언어를 우선한다.
날짜/숫자 표시 규칙: `T`, `Z`, ISO 원문, `->`는 기본 화면에 쓰지 않는다. `YYYY년 M월 D일 HH:mm` 형식을 기본 후보로 한다. `last bucket`, `last heartbeat`는 기본 노출하되 같은 날짜 표시 규칙을 따른다. `stale at`, `down at` 시간값은 tooltip 안에서만 필요할 때 같은 규칙으로 표시한다.
보류/나중: timezone 표기 여부와 초 단위 노출 여부는 구현 승인 전 최종 확인한다.
확정 여부: 의도 기록 완료. 구현은 최종 승인 전 보류.
```

Dashboard Core freshness threshold 설명 명세:

- `last bucket`: 마지막으로 수집·승인된 metric bucket 시각이다. application 상태 판단의 metric data 기준점이다.
- `last heartbeat`: starter가 마지막으로 살아 있다고 알려온 시각이다. metric data 상태와 별개인 starter 연결 신호다.
- `stale at`: 기본 화면에서는 숨긴다. tooltip에서는 이 시각까지 새 metric bucket이 들어오지 않으면 "데이터 지연"으로 볼 수 있는 기준 시각이라고 설명한다. 앱 장애로 단정하지 않고 수집 경로 지연 가능성을 먼저 설명한다.
- `down at`: 기본 화면에서는 숨긴다. tooltip에서는 이 시각까지도 새 metric bucket이 들어오지 않으면 "수집 끊김"으로 볼 수 있는 기준 시각이라고 설명한다. 앱이 내려갔다고 확정하지 않고 starter 연결, 트래픽, 수집 경로를 함께 확인하도록 설명한다.
- `state impact`: 기본 화면에서는 숨긴다. tooltip에서는 starter heartbeat 신호가 현재 application metric state 판단에 직접 영향을 주는지 나타낸다고 설명한다. `none`은 starter가 살아 있는지 여부가 현재 `주의/정상/지연/끊김` 판단을 직접 바꾸지 않는다는 뜻이다.

### SourceScopedPercentilesPanel

```text
컴포넌트: SourceScopedPercentilesPanel
현재 문제: `SOURCE-SCOPED PERCENTILES`, `source`, `scope`, `display policy`, `aggregate policy`, `starter_local`, `instance_bucket`, `bucket`, `AVAILABLE` 같은 용어가 처음 보는 사용자에게 read model/debug 용어처럼 보인다. 화면 목적이 "어느 instance가 느린가"인데 제목과 컬럼이 그 질문을 바로 말하지 못한다. 표의 `source` 컬럼은 모든 row에서 `starter_local`이 반복되어 사용자 관점에서 필요 없는 정보다. `bucket` 값도 ISO timestamp와 `->`가 그대로 보여 읽기 어렵다.
디자인 의도: 이 패널은 latency 근거를 보여주는 영역이므로 "인스턴스별 응답 시간" 또는 "느린 인스턴스 확인"처럼 사용자가 바로 이해하는 제목으로 바꾼다. 상단 metadata 줄은 사용자 판단에 직접 필요하지 않으므로 기본 화면에서 제거하고, 핵심 표는 instance, 요청 수, p95/p99 응답시간, 측정 구간 중심으로 스캔되게 한다.
텍스트 의도: 제목 후보는 "인스턴스별 응답 시간", 보조 설명 후보는 "최근 측정 구간에서 각 instance의 느린 요청 기준을 보여줍니다." 상태 badge `AVAILABLE`은 "데이터 있음" 또는 "측정됨"으로 바꾼다. `requests`는 "요청 수", `p95`는 "p95 응답시간", `p99`는 "p99 응답시간", `bucket`은 "측정 구간"으로 바꾼다. 표의 `source` 컬럼은 제거한다.
유지할 점: p95/p99 값 자체와 instance별 비교 구조는 유지한다. p95/p99는 운영자에게 유용한 표준 용어라 완전히 숨기지 않고, 쉬운 설명을 tooltip로 붙인다.
수정 후보: 상단 metadata grid(`source`, `scope`, `display policy`, `aggregate policy`)는 기본 화면에서 제거한다. table row의 `source` 컬럼도 제거한다. 필요한 수집 출처 설명은 패널 제목 옆 tooltip에만 둔다. `bucket` 컬럼은 "측정 구간"으로 바꾸고 날짜를 한국어 가독형 range로 표시한다.
상태/색상/강조 규칙: 상태 badge는 영어 대문자 대신 한국어 상태를 쓴다. `available`은 긍정 상태로 은은한 초록을 사용하되 너무 강한 성공 메시지처럼 보이지 않게 한다.
숨기거나 tooltip로 보낼 정보: 상단 metadata 줄 전체(`source`, `scope`, `display policy`, `aggregate policy`)와 표의 `source` 컬럼은 사용자에게 기본 노출하지 않는다. `starter_local`, `instance_bucket`, `server_order_latest_points`, "UI는 percentile을 재계산하지 않고..." 같은 policy 문장은 숨기고, 필요 시 패널 제목 옆 tooltip 또는 기술 세부 정보로만 보낸다. tooltip에서는 원문 code보다 "Starter가 보낸 instance별 최신 측정값"처럼 설명한다.
날짜/숫자 표시 규칙: bucket 시간도 ISO 원문과 `->` 대신 `2026년 6월 4일 09:29:00 - 09:29:30` 또는 초를 숨긴 `2026년 6월 4일 09:29 - 09:29`처럼 읽기 쉬운 측정 구간으로 표시한다. p95/p99는 `842 ms`처럼 단위는 유지한다.
보류/나중: p95/p99를 tooltip만으로 충분히 설명할지, 표 머리글 아래 짧은 helper text를 둘지는 구현 승인 전 재확인한다.
확정 여부: 용어 대체 후보 기록 완료. 구현은 최종 승인 전 보류.
```

SourceScopedPercentilesPanel 용어 대체 후보:

- `SOURCE-SCOPED PERCENTILES` -> "인스턴스별 응답 시간" 또는 "느린 인스턴스 확인"
- `AVAILABLE` -> "데이터 있음" 또는 "측정됨"
- `source` -> 기본 숨김. 상단 metadata 줄과 표 컬럼에서 제거. tooltip 표현은 "수집 기준" 또는 "Starter 수집값"
- `starter_local` -> "Starter 수집값"
- `scope` / `instance_bucket` -> 기본 숨김. 상단 metadata 줄에서는 제거. tooltip 표현은 "인스턴스별 최신 측정 구간"
- `display policy` -> 기본 숨김. 상단 metadata 줄에서는 제거. 필요 시 "표시 순서"
- `aggregate policy` -> 기본 숨김. 상단 metadata 줄에서는 제거. 필요 시 "계산 기준"
- `instance` -> "인스턴스"
- `requests` -> "요청 수"
- `p95` -> "p95 응답시간" tooltip: "요청 100개 중 95개가 이 시간 안에 끝났다는 뜻입니다."
- `p99` -> "p99 응답시간" tooltip: "매우 느린 일부 요청까지 포함해 tail latency를 보는 기준입니다."
- `bucket` -> "측정 구간". ISO 원문과 `->`를 쓰지 않고 한국어 날짜/시간 range로 표시

### HistogramPanel

```text
컴포넌트: HistogramPanel
현재 문제: `HISTOGRAM DISTRIBUTION EVIDENCE`, `CURRENT`, `BASELINE`, `AVAILABLE`, `accepted buckets`, `backend projection` 같은 용어가 사용자에게 낯설다. 더 중요하게는 histogram bucket이 `le` 누적 bucket이면 5ms 요청 하나가 `≤100`, `≤300`, `≤1000`, `≤3000` 모두에 반영되는 것이 원자료 동작인데, 현재 UI는 이를 독립 구간별 분포처럼 보여 혼선을 줄 수 있다.
디자인 의도: 사용자는 histogram 원자료의 누적 bucket semantics보다 "빠른 요청/보통 요청/느린 요청이 얼마나 있는지"를 이해하고 싶다. 화면은 누적 bucket 값을 그대로 막대 비교로 보여주기보다, 사람이 읽는 응답시간 구간 분포로 보여줘야 한다.
텍스트 의도: 제목 후보는 "응답 시간 분포" 또는 "느린 요청 분포". `current`는 "현재 구간", `baseline`은 "비교 기준 구간", `available`은 "데이터 있음"으로 바꾼다. `total`은 "총 요청 수"로 바꾼다. `accepted buckets`와 `backend projection` 같은 내부 용어는 기본 화면에 쓰지 않는다.
유지할 점: 현재 구간과 비교 기준 구간을 나란히 비교하는 구조는 유지한다. 응답시간 threshold 자체도 유지하되, 값이 누적값인지 구간값인지 화면에서 혼동되지 않게 한다.
수정 후보: backend가 cumulative `le` bucket을 주는 경우 표시용으로 non-overlapping bin count를 계산하거나, backend/API가 이미 구간별 count를 제공하도록 contract를 확인한다. 표시 구간 후보는 `0-100ms`, `100-300ms`, `300ms-1s`, `1s-3s`, 필요 시 `3s 초과`다. 누적값을 그대로 보여야 한다면 제목과 라벨을 "누적 요청 수"로 명확히 바꾸고 분포형 막대처럼 보이지 않게 한다.
상태/색상/강조 규칙: 현재 구간과 비교 기준 구간의 막대는 같은 기준으로 계산되어야 한다. 느린 구간은 더 눈에 들어오게 하되, 색상만으로 장애를 단정하지 않는다.
숨기거나 tooltip로 보낼 정보: raw `le` bucket semantics, backend projection/source 설명은 기본 화면에서 숨기고 tooltip 또는 기술 세부 정보로 보낸다. tooltip에는 "원자료가 누적 bucket일 수 있어 화면에서는 구간별 요청 수로 풀어 보여줍니다." 정도로 설명한다.
날짜/숫자 표시 규칙: `total 18,420`은 "총 요청 수 18,420"처럼 표시한다. ms threshold는 `100ms 이하`, `100-300ms`처럼 읽기 쉬운 구간명으로 쓴다.
보류/나중: 실제 backend 응답이 cumulative bucket인지 non-overlapping bucket인지 구현 승인 전에 contract를 확인한다. cumulative라면 frontend 표시용 변환으로 충분한지, API shape 변경이 필요한지 따로 결정한다.
확정 여부: 누적 bucket 혼선 문제 기록 완료. 구현은 최종 승인 전 보류.
```

HistogramPanel bucket 표시 명세 후보:

- raw cumulative bucket을 독립 분포 막대로 보여주지 않는다.
- 5ms 요청이 여러 `le` bucket에 동시에 들어가는 raw 동작은 누적 histogram에서는 정상일 수 있다.
- 사용자 화면에서는 가능하면 non-overlapping 구간으로 표시한다: `0-100ms`, `100-300ms`, `300ms-1s`, `1s-3s`, `3s 초과`.
- 누적값을 그대로 표시해야 한다면 라벨을 `≤ 100ms 누적`, `≤ 300ms 누적`처럼 명확히 하고, "분포"라는 제목을 쓰지 않는다.

### Latency Spike Visibility

```text
컴포넌트: MetricStateStrip, SourceScopedPercentilesPanel, HistogramPanel, TriagePanel, EndpointPriorityPanel
현재 문제: Metric state rationale에서는 p95/p99 지연 증가 또는 spike를 말하지만, 사용자가 "p99가 spike된 것은 어디에서 확인하나"를 바로 찾기 어렵다. 현재 SourceScopedPercentilesPanel은 p95/p99 현재값을 보여주지만 baseline 대비 spike 여부를 직접 보여주지는 않는다. HistogramPanel은 분포 변화를 추정하게 하지만 p99 spike 자체를 명시하지 않는다. Triage/EndpointPriorityPanel은 원인 후보를 말하지만 p99 current/baseline/delta가 눈에 보이지 않는다.
디자인 의도: 상태 요약에서 지연 spike를 언급하면, 바로 아래 근거 패널에서 "어떤 endpoint/instance의 p95/p99가 얼마나 올랐는지"를 확인할 수 있어야 한다.
텍스트 의도: "p99 증가", "기준 대비 +350ms", "현재 1.32s / 기준 970ms"처럼 사용자가 spike의 크기를 읽을 수 있는 문구를 후보로 둔다. `spike`만 쓰지 말고 "급증" 또는 "기준 대비 증가"를 함께 쓴다.
유지할 점: server/read model이 계산한 판단을 UI가 임의로 재계산하지 않는 원칙은 유지한다.
수정 후보: TriageCard 또는 EndpointPriorityRow에 latency 근거 요약을 추가한다. 가능한 표시 후보는 `p95 현재/기준/차이`, `p99 현재/기준/차이`, `느린 요청 비율 현재/기준/차이`다. backend가 baseline p99를 제공하지 않는다면 UI에서는 "현재 p99"와 "분포상 느린 요청 증가"로만 표현하고, 정확한 p99 spike 수치 제공은 API/read model 보강 후보로 둔다.
상태/색상/강조 규칙: spike 근거는 Metric state의 주의 상태와 연결되도록 같은 계열의 은은한 강조를 사용한다. 숫자 delta는 과장하지 않고 current/baseline 비교가 보이게 한다.
숨기거나 tooltip로 보낼 정보: raw histogram bucket, rule id, selection policy는 기본 화면에서 숨긴다. tooltip에는 "이 값은 서버가 계산한 현재 구간과 기준 구간 비교입니다." 정도로 설명한다.
날짜/숫자 표시 규칙: p95/p99는 ms 또는 s 단위로 읽기 쉽게 표시한다. 큰 값은 `1.32초` 후보를 둔다. baseline/current window 날짜도 한국어 가독형으로 표시한다.
보류/나중: 현재 API가 p99 baseline/delta를 제공하는지 확인 필요. 제공하지 않으면 API/read model 보강 story 후보로 남긴다.
확정 여부: p99 spike 찾기 어려움 기록 완료. 구현은 최종 승인 전 보류.
```

### Sustained High Error Rate Visibility

```text
컴포넌트: MetricStateStrip, TriagePanel, EndpointPriorityPanel
현재 문제: smoke test에서 current 15분 44 requests / 4 errors = 9.09%, baseline 15분 66 requests / 6 errors = 9.09%처럼 오류율이 계속 높아도, baseline 대비 spike/regression 조건을 만족하지 않아 "문제 없음"처럼 보일 수 있다. 현재 정책은 "갑자기 나빠진 것"을 잡는 detector에 가깝고, "계속 나쁜 것"을 별도 문제로 보여주는 경로가 약하다.
디자인 의도: 사용자는 에러율이 이미 높은 상태가 지속될 때도 "확인할 API"와 "왜 문제인지"를 볼 수 있어야 한다. spike가 아니더라도 절대 오류율이 높으면 운영 판단에서는 주의 신호로 보여야 한다.
텍스트 의도: 상태 rationale 후보는 "오류율이 기준 구간과 비슷하지만 절대값이 높습니다." 또는 "최근 구간의 오류율이 9.09%로 높게 유지되고 있습니다."로 둔다. Triage card 후보 제목은 "오류율 높음" 또는 "지속적인 오류율 확인 필요"로 둔다. 추천 행동은 "오류가 많이 발생한 API를 먼저 확인하세요."처럼 다음 행동을 직접 말한다.
유지할 점: baseline 대비 증가 spike/regression detector는 유지한다. 다만 별도 absolute high detector를 추가해 regression이 없어도 높은 오류율을 놓치지 않게 한다.
수정 후보: backend/read model에 `absolute_error_rate_high` 또는 `sustained_error_rate_high` triage rule을 추가하는 후보를 둔다. 조건 후보는 current request count가 최소 표본 이상이고 current error rate가 절대 threshold 이상인 경우다. baseline과 비슷하면 "spike"가 아니라 "지속적인 높은 오류율"로 표현한다. EndpointPriorityPanel은 spike가 없더라도 current error count/error rate가 높은 endpoint를 후보로 보여준다.
상태/색상/강조 규칙: `degraded` 또는 `주의` 상태로 표시하되, 문구에서 "급증"과 "지속적 높음"을 구분한다. absolute high는 노랑/주황 계열 주의로 충분하며, baseline 대비 급격한 악화와 같은 강한 경고 문구는 피한다.
숨기거나 tooltip로 보낼 정보: rule id, threshold 조건, baseline multiplier 같은 내부 판정식은 기본 화면에서 숨긴다. tooltip에는 "기준 구간과 비슷하더라도 오류율 자체가 높으면 표시됩니다." 정도로 설명한다.
날짜/숫자 표시 규칙: 오류율은 `9.09%`처럼 표시하고, current/baseline 비교가 필요하면 `현재 9.09% / 기준 9.09%`처럼 같이 보여준다. 요청 수와 오류 수는 `44건 중 4건 오류`처럼 사람이 읽는 표현 후보를 둔다.
보류/나중: 절대 오류율 threshold, 최소 요청 수, endpoint ranking 기준은 구현 승인 전에 backend/read model 정책으로 확정한다. 예시 후보는 request count >= 30, error rate >= 5%다.
확정 여부: 지속적 높은 오류율 보강 의도 기록 완료. 구현은 최종 승인 전 보류.
```

Sustained High Error Rate rule 후보:

- `absolute_error_rate_high`: current error rate가 절대 threshold 이상이면 baseline delta가 작아도 triage card를 만든다.
- `sustained_error_rate_high`: current와 baseline 모두 오류율이 높으면 "계속 높은 오류율"로 설명한다.
- endpoint priority는 error spike가 없어도 current error count/error rate가 높은 API를 보여준다.
- "spike"와 "지속적 높음"을 화면 문구에서 분리한다.

### Error Endpoint Visibility Even When Active

```text
컴포넌트: MetricStateStrip, EndpointPriorityPanel, TriagePanel
현재 문제: application state가 `active`로 판단되더라도, current window에서 500 error를 반환한 endpoint가 dashboard에 전혀 노출되지 않으면 사용자가 "문제는 없지만 어떤 API에서 에러가 있었는지"를 확인할 수 없다. 상태 판단과 endpoint 후보 노출이 너무 강하게 묶이면 낮은 빈도의 에러 endpoint가 사라질 수 있다.
디자인 의도: application state는 전체 상태 판단이고, endpoint priority는 "확인할 만한 API 후보" 목록으로 분리한다. state가 `active`여도 500을 보낸 endpoint는 우선순위 후보군에 들어가야 한다. 화면에 3개만 노출한다면 더 높은 우선순위 후보가 있을 때 밀릴 수는 있지만, 후보 생성 단계에서 제외하지 않는다.
텍스트 의도: active 상태에서는 과한 경고가 아니라 "최근 오류가 있었던 API" 또는 "확인 후보"처럼 낮은 강도의 문구로 표현한다. 예: "전체 상태는 정상 범위지만, 최근 오류를 반환한 API가 있습니다."
유지할 점: `active` 상태 자체를 무리하게 `degraded`로 올리지 않는다. 낮은 빈도 error는 상태를 바꾸지 않을 수 있지만, endpoint visibility에서는 추적 가능해야 한다.
수정 후보: EndpointPriority 후보 생성 규칙에 `current_error_count > 0` 또는 5xx response 존재 endpoint를 포함한다. 정렬은 기존 severity/score가 우선하되, error endpoint는 최소 후보군에 포함한다. 최대 3개 노출 정책은 유지하되, 후보군에는 error endpoint가 들어가도록 명세한다.
상태/색상/강조 규칙: active 상태에서 error endpoint를 보여줄 때는 "장애"처럼 강하게 보이지 않게 한다. endpoint row에는 중립 또는 연한 노랑 수준의 "오류 발생" 표시를 둔다. degraded/spike/sustained high error endpoint가 있으면 그쪽이 더 강한 강조와 우선순위를 갖는다.
숨기거나 tooltip로 보낼 정보: rule id, scoring detail, 후보 탈락 이유는 기본 화면에 노출하지 않는다. 필요하면 tooltip에서 "전체 상태를 바꾸지는 않았지만 최근 오류가 있어 후보에 포함했습니다."라고 설명한다.
날짜/숫자 표시 규칙: 오류 수는 `최근 구간 1건 오류`, `500 응답 1건`처럼 사람이 읽는 표현으로 표시한다. 비율이 낮으면 error rate보다 count 중심 표현을 우선한다.
보류/나중: "500만 포함할지, 4xx/5xx 전체를 포함할지", "최소 요청 수 없이 1건도 후보에 넣을지", "상위 3개 밖 후보를 접힌 목록으로 보여줄지"는 구현 승인 전 확정한다.
확정 여부: active 상태에서도 500 endpoint를 후보군에 포함하는 의도 기록 완료. 구현은 최종 승인 전 보류.
```

EndpointPriority 후보 포함 규칙 후보:

- application state와 별개로 current window에서 5xx를 반환한 endpoint는 후보군에 포함한다.
- 화면 노출은 상위 3개로 제한하되, 후보 생성 단계에서는 `current_error_count > 0` endpoint를 제외하지 않는다.
- 낮은 빈도의 에러는 state를 `degraded`로 만들지 않을 수 있지만, "최근 오류가 있었던 API"로 확인 가능해야 한다.
- endpoint priority 정렬은 `degraded/spike/sustained high error`를 먼저 두고, 낮은 빈도 error endpoint는 그 다음 후보로 둔다.

### CredentialLifecyclePanel

```text
컴포넌트: CredentialLifecyclePanel
현재 문제: `STARTER CREDENTIAL`, `key prefix`, `status`, `issued`, `rotated`, `revoked`, `metadata response`, `raw value/hash`, `Rotate`, `Revoke` 같은 표현이 처음 보는 사용자에게 무엇을 관리하는 화면인지 바로 설명하지 못한다. `source 없음`도 이 맥락에서는 부자연스럽다.
디자인 의도: 이 패널은 Starter가 portal로 데이터를 보낼 때 쓰는 연결 키 관리 영역으로 읽혀야 한다. 사용자가 "현재 키가 사용 중인가", "언제 발급됐나", "교체/중지할 수 있나", "전체 비밀값은 다시 볼 수 없다"를 바로 이해하도록 한다.
텍스트 의도: 제목 후보는 "Starter 연결 키" 또는 "데이터 수집 키". `ACTIVE`/`active`는 "사용 중"으로 표시한다. `key prefix`는 "키 식별값" 또는 "키 앞부분"으로 바꾸고, 전체 secret이 아니라 식별용 일부 값임을 tooltip로 설명한다. `issued`는 "발급일", `rotated`는 "마지막 교체", `revoked`는 "사용 중지일" 또는 "폐기일"로 바꾼다. 값이 없을 때는 `source 없음` 대신 "아직 없음" 또는 "해당 없음"을 쓴다.
유지할 점: 전체 credential raw value나 hash를 metadata 화면에서 노출하지 않는 보안 원칙은 유지한다. rotate/revoke 기능 자체도 유지한다.
수정 후보: 설명 문구는 "보안을 위해 전체 키 값은 생성 직후 한 번만 표시됩니다."처럼 사용자 언어로 바꾼다. `metadata response는 raw value/hash를 포함하지 않는다...` 같은 내부 계약 문장은 제거하거나 tooltip/보안 세부 설명으로 보낸다. 버튼은 `Rotate` -> "새 키 발급" 또는 "키 교체", `Revoke` -> "키 사용 중지"로 바꾼다.
상태/색상/강조 규칙: `사용 중`은 은은한 초록. `새 키 발급/키 교체`는 흰 배경에 이질적이지 않은 연한 파랑 또는 연한 초록 계열의 subtle button으로 강조한다. `키 사용 중지/키 폐기`는 연한 빨강 또는 rose 계열의 subtle-danger button으로 구분하되, 너무 강한 solid red는 피한다. 두 버튼 모두 배경색, border, text color로 목적을 드러내고 흰 카드 안에서 튀지 않게 낮은 채도와 얇은 border를 사용한다. 새 키 발급은 일반 관리 액션, 키 사용 중지는 위험 액션으로 시각적 위계를 분리한다.
숨기거나 tooltip로 보낼 정보: `raw value`, `hash`, `metadata response`, `key prefix` 원문, 내부 status code는 기본 노출하지 않는다. key prefix의 의미와 전체 키를 다시 볼 수 없는 이유는 tooltip 또는 짧은 보안 안내로 설명한다.
날짜/숫자 표시 규칙: `issued`, `rotated`, `revoked` 날짜도 ISO 원문 대신 `2026년 6월 3일 12:00`처럼 표시한다. 값이 없으면 "아직 없음" 또는 "해당 없음"을 사용한다.
보류/나중: 버튼 최종 문구는 "새 키 발급/키 교체", "키 사용 중지/키 폐기" 중 구현 승인 전에 확정한다. revoke confirm 단계의 문구도 별도 확인한다.
확정 여부: 직관적 용어 전환 의도 기록 완료. 구현은 최종 승인 전 보류.
```

CredentialLifecyclePanel 용어 대체 후보:

- `STARTER CREDENTIAL` -> "Starter 연결 키" 또는 "데이터 수집 키"
- `ACTIVE` / `active` -> "사용 중"
- `key prefix` -> "키 식별값" 또는 "키 앞부분"
- `status` -> "키 상태"
- `issued` -> "발급일"
- `rotated` -> "마지막 교체"
- `revoked` -> "사용 중지일" 또는 "폐기일"
- `source 없음` -> "아직 없음" 또는 "해당 없음"
- `metadata response는 raw value/hash를 포함하지...` -> "보안을 위해 전체 키 값은 생성 직후 한 번만 표시됩니다."
- `Rotate` -> "새 키 발급" 또는 "키 교체"
- `Revoke` -> "키 사용 중지" 또는 "키 폐기"

CredentialLifecyclePanel 버튼 강조 의도:

- `새 키 발급/키 교체`: 연한 파랑 또는 연한 초록 배경, 같은 계열 border/text. 보안 키를 갱신하는 관리 액션으로 보이게 한다.
- `키 사용 중지/키 폐기`: 연한 빨강/rose 배경, 같은 계열 border/text. 위험 액션임을 알리되 흰색 dashboard 카드 안에서 과하게 튀는 solid red는 피한다.
- 두 버튼은 동일한 크기와 정렬을 유지하되 색상으로 의미를 나눈다.

### InstancesPanel

```text
컴포넌트: InstancesPanel
현재 문제: `Instances`, `last seen`, raw evidence link, `evidence`, `trend` 표현이 처음 보는 사용자에게 무엇을 눌러야 하는지 바로 설명하지 못한다. 날짜도 ISO timestamp라 읽기 어렵고, evidence handoff link는 사용자 판단에 필요 없는 내부 링크다.
디자인 의도: 이 패널은 "어떤 실행 인스턴스를 더 확인할 수 있는가"를 보여주는 보조 진입점으로 읽혀야 한다. 사용자는 각 instance의 마지막 관측 시각을 확인하고, 상세 근거 또는 시간 흐름으로 자연스럽게 들어갈 수 있어야 한다.
텍스트 의도: `Instances`는 "인스턴스" 또는 "실행 인스턴스"로 바꾼다. `last seen`은 "마지막 관측" 또는 "마지막 확인"으로 바꾼다. `evidence`는 "상세 근거 보기" 또는 "근거 보기", `trend`는 "시간 흐름 보기" 또는 "변화 보기"로 바꾼다. 빈 상태의 `bounded instance handoff entry가 없습니다.`는 "표시할 인스턴스가 없습니다."처럼 사용자 언어로 바꾼다.
유지할 점: instance별로 상세 근거 drawer와 trend drawer에 진입하는 구조는 유지한다. evidence와 trend가 서로 다른 목적이라는 구분도 유지한다.
수정 후보: raw evidence link는 기본 화면에서 제거한다. 필요하면 버튼 tooltip에만 "이 인스턴스의 상세 근거를 불러옵니다." 정도로 설명한다. `evidence`/`trend`는 underline text link보다 버튼 또는 icon+text action으로 바꿔 클릭 가능한 액션임을 분명히 한다.
상태/색상/강조 규칙: 두 액션은 보조 액션으로 조용하게 보이되, 링크 텍스트만 있는 현재보다 클릭 가능한 버튼성이 드러나야 한다. "상세 근거 보기"는 중립, "시간 흐름 보기"는 히스토리/흐름 성격의 은은한 색 또는 아이콘을 사용할 수 있다.
숨기거나 tooltip로 보낼 정보: raw evidence link, `handoff link`, `Story 10.4` 같은 내부 개발 맥락은 사용자에게 기본 노출하지 않는다. 필요하면 tooltip에서도 사용자 언어로 "서버가 제공한 안전한 상세 근거 링크" 정도로만 설명한다.
날짜/숫자 표시 규칙: `last seen` 날짜는 ISO 원문 대신 `2026년 6월 4일 09:29`처럼 표시한다. 너무 긴 날짜가 좁은 패널을 밀면 `6월 4일 09:29`처럼 현재 연도 생략 후보를 둔다.
보류/나중: 버튼 최종 문구는 "상세 근거 보기/근거 보기", "시간 흐름 보기/변화 보기" 중 구현 승인 전에 확정한다.
확정 여부: evidence/trend 직관화 및 날짜 표시 의도 기록 완료. 구현은 최종 승인 전 보류.
```

InstancesPanel 용어 대체 후보:

- `Instances` -> "인스턴스" 또는 "실행 인스턴스"
- `last seen` -> "마지막 관측" 또는 "마지막 확인"
- `evidence` -> "상세 근거 보기" 또는 "근거 보기"
- `trend` -> "시간 흐름 보기" 또는 "변화 보기"
- raw evidence link -> 기본 숨김
- `Story 10.4 evidence handoff link` -> 사용자에게 노출하지 않음. 필요 시 "이 인스턴스의 상세 근거를 불러오는 링크입니다."
- `bounded instance handoff entry가 없습니다.` -> "표시할 인스턴스가 없습니다."

### Evidence And Trend Drawer Cleanup

```text
컴포넌트: Instance Evidence Drawer, Instance Trend Drawer
현재 문제: evidence/trend drawer는 server link, source, scope, policy, rule id, status source, fixed query, projection, handoff 같은 내부 구현 정보가 많아질 수 있다. 처음 보는 사용자는 "무엇을 확인해야 하는지"보다 "이 데이터가 어떤 계약으로 만들어졌는지"를 먼저 보게 되어 판단 흐름이 끊길 수 있다.
디자인 의도: evidence는 "이 인스턴스가 왜 문제 후보인지 확인하는 상세 근거", trend는 "시간에 따라 상태와 신호가 어떻게 변했는지 보는 흐름"으로 바로 이해되어야 한다. 사용자 판단에 필요한 요약, 수치, 변화, 다음 행동을 위에 두고, 기술 세부 정보는 숨기거나 접는다.
텍스트 의도: `Evidence`는 "상세 근거", `Trend`는 "시간 흐름" 또는 "변화 추이"로 표현한다. `source`, `scope`, `selection policy`, `display ordering policy`, `fixed query`, `snapshot projection` 같은 용어는 기본 화면에 쓰지 않는다. 필요하면 tooltip에서 "서버가 저장해 둔 스냅샷 기준으로 본 변화입니다."처럼 사용자 언어로 설명한다.
유지할 점: evidence와 trend가 현재 dashboard 판단을 대체하지 않는 보조 drill-down이라는 역할은 유지한다. metric data axis와 starter connection axis가 다른 신호라는 구분도 유지한다.
수정 후보: drawer 상단에 한 줄 요약을 둔다. Evidence 후보: "orders-api-1에서 결제 확인 API의 지연과 오류 근거를 확인합니다." Trend 후보: "최근 7일 동안 이 인스턴스의 상태 변화와 저장된 스냅샷을 보여줍니다." 내부 세부 정보는 "기술 세부 정보" 접힘 영역이나 tooltip로 이동한다.
상태/색상/강조 규칙: 중요한 판단 근거는 시각적으로 먼저 보이게 한다. 예: 오류 수, 오류율, p95/p99, 느린 요청 비율, 마지막 관측 시각, 상태 변화. raw source/policy는 낮은 위계나 숨김 처리한다. 현재/기준/변화는 같은 행에서 비교되게 해 시선 이동을 줄인다.
숨기거나 tooltip로 보낼 정보: raw link, server-provided link, handoff link, source path, rule id, selection policy, display ordering policy, aggregate policy, fixed query, max limit, order, raw status source, backend projection 설명은 기본 화면에서 숨긴다. 필요한 경우 "기술 세부 정보" 접힘 영역 또는 `?` tooltip로만 제공한다.
날짜/숫자 표시 규칙: evidence/trend의 generated, first seen, last seen, captured at, since/until, bucket range는 ISO 원문 대신 한국어 가독형 날짜/시간으로 표시한다. trend point는 `6월 4일 09:30`처럼 스캔 가능한 짧은 날짜 후보를 둔다. 오류 수는 `44건 중 4건 오류`, 비율은 `9.09%`, latency는 `842ms` 또는 `1.32초`처럼 읽기 쉽게 표시한다.
보류/나중: 어떤 내부 항목을 접힘 영역에도 남길지, 아예 숨길지는 구현 승인 전에 실제 drawer 스크린샷을 보고 확정한다.
확정 여부: evidence/trend 정보 정리 원칙 기록 완료. 구현은 최종 승인 전 보류.
```

Evidence/Trend 정보 정리 원칙:

- 기본 화면에는 사용자 판단에 필요한 정보만 남긴다: 상태 요약, 오류/지연 수치, 마지막 관측, endpoint/instance 이름, 시간 흐름.
- 내부 계약 정보는 기본 노출하지 않는다: raw link, source/scope/policy, rule id, fixed query, projection, handoff.
- 시각적으로 약한 정보는 위계를 다시 잡는다: 중요한 수치와 변화는 강조하고, 보조 설명은 작게 또는 tooltip로 보낸다.
- 날짜는 모두 한국어 가독형으로 바꾼다.
- evidence는 "왜 이 인스턴스를 봐야 하는지", trend는 "시간에 따라 어떻게 변했는지"를 첫 화면에서 말해야 한다.

## 권장 리뷰 순서

1. `Nav`
2. `Landing`
3. `Docs`
4. `Dashboard` 전체 레이아웃
5. `Dashboard` 선택 흐름: `ProjectRail`, `ProjectRegistrationPanel`, `ApplicationRail`
6. `Dashboard` 핵심 상태: `DashboardContext`, `MetricStateStrip`, `StarterConnectionStrip`, `RecoveryNotice`, `MetricScalars`
7. `Dashboard` 근거 패널: `SourceScopedPercentilesPanel`, `HistogramPanel`, `TriagePanel`, `EndpointPriorityPanel`
8. `Dashboard` 오른쪽 보조 패널: `CredentialLifecyclePanel`, `InstancesPanel`, `SnapshotHistoryPanel`
9. `InstancePanels` evidence drawer
10. `InstancePanels` trend drawer
11. `SnapshotDetailSurface`

## 컴포넌트별 상태 체크리스트

### Nav

파일: `/Users/tlsdla1235/Desktop/study/observation/frontend/src/app/components/nav.tsx`

- 목적: 브랜드 홈 이동, Dashboard/Docs 이동, GitHub 로그인 액션 제공.
- 상태:
  - 현재 route가 `/dashboard` 또는 `/docs`이면 해당 링크가 active border로 표시된다.
  - `githubLoginDisabled=true`이면 GitHub 로그인 버튼이 비활성화된다.
  - `githubLoginLabel`은 인증 진행/상태에 따라 바뀔 수 있다.
- 리뷰 포인트:
  - 상단 내비게이션이 제품 성격을 즉시 전달하는가.
  - 로그인 버튼 문구가 사용자의 다음 행동을 명확히 말하는가.

### Landing

파일: `/Users/tlsdla1235/Desktop/study/observation/frontend/src/app/components/landing.tsx`

- 목적: Observation Portal의 첫인상, starter-first 흐름, Dashboard/Docs 진입을 보여준다.
- 큰 섹션:
  - Hero와 product preview
  - 첫 화면에서 답을 얻는 질문
  - 3단계 사용법
  - 제품 가치
  - CTA
- 상태:
  - 정적 페이지라 API loading/error 상태는 없다.
  - Dashboard/Docs 링크는 라우팅 상태에만 의존한다.
- 리뷰 포인트:
  - 제품 프리뷰가 실제 Dashboard의 사고방식을 잘 예고하는가.
  - "starter", "read model", "bucket", "heartbeat" 같은 용어를 어느 정도 유지할지 정한다.

### Docs

파일: `/Users/tlsdla1235/Desktop/study/observation/frontend/src/app/components/docs.tsx`

- 목적: 처음 쓰는 사용자가 로그인, project 등록, starter 연결, 첫 데이터 확인까지 따라가게 한다.
- 상태:
  - 좌측 TOC는 클릭한 section id를 active로 표시한다.
  - API loading/error 상태는 없다.
- 리뷰 포인트:
  - 운영자/개발자가 실제로 따라 할 수 있는 순서인지 본다.
  - 보안 관련 문구가 충분히 강하지만 과하게 겁주지는 않는지 본다.

### Dashboard 전체 레이아웃

파일: `/Users/tlsdla1235/Desktop/study/observation/frontend/src/app/components/dashboard.tsx`

- 목적: Project -> Application -> Dashboard link chain을 따라 운영 첫 화면을 구성한다.
- 주요 영역:
  - 상단 breadcrumb/status/reload bar
  - Project rail
  - Application rail
  - Main dashboard
  - 오른쪽 보조 패널
  - Instance drawer
- 전역 상태:
  - 인증 필요: auth context에서 인증이 없거나 만료된 경우 resource별 auth error를 표시한다.
  - loading: projects/applications/dashboard resource가 호출 중일 때 영역별 loading 문구를 표시한다.
  - context mismatch: 서버 link나 응답 context가 현재 선택과 다르면 fail-closed error로 처리한다.
  - reload: 상단 Reload 버튼이 projects, applications, dashboard resource reload를 호출한다.
- 리뷰 포인트:
  - 3-column 콘솔 밀도가 적절한지 본다.
  - 사용자가 "지금 무엇을 선택했고 무엇을 봐야 하는지"를 잃지 않는지 본다.

### ProjectRail

- 목적: 계정에 연결된 project navigation 목록에서 project를 선택한다.
- 상태:
  - loading: project navigation read model 로딩 중.
  - error: project 목록 API 실패 또는 인증 실패.
  - empty: active membership project가 없음.
  - filter-empty: 검색어와 일치하는 loaded project가 없음.
  - selected: 선택된 project가 좌측 border와 배경으로 강조된다.
- 리뷰 포인트:
  - empty 문구가 "장애"로 오해되지 않게 설명하는가.
  - recent concern 정보가 너무 내부 용어처럼 보이지 않는가.

### ProjectRegistrationPanel

- 목적: 새 Project를 만들고 starter credential을 1회 표시한다.
- 상태:
  - closed/open: 등록 폼 접힘/펼침.
  - validation error: project name이 비어 있거나 400.
  - conflict: 409.
  - auth expired: 401.
  - loading: 등록 요청 중.
  - one-time credential: 생성 직후 raw credential 표시.
  - copied/cleared: Copy and clear 또는 Close 후 credential을 화면 state에서 제거했다는 메시지.
- 리뷰 포인트:
  - credential의 일회성/비밀성 문구가 충분히 명확한가.
  - 등록 UI가 사이드 레일 안에서 너무 좁거나 무겁지 않은가.

### ApplicationRail

- 목적: 선택한 project의 application 목록을 보여주고 dashboard 진입 application을 선택한다.
- 상태:
  - no project: project 선택 대기.
  - loading: applications link 호출 중.
  - error: applications resource 실패.
  - empty: catalog application 또는 첫 accepted bucket source가 없음.
  - filter-empty: 검색 결과 없음.
  - selected: 선택된 application row 강조.
  - lifecycle badge: application lifecycle/state badge 표시.
- 리뷰 포인트:
  - metric data와 starter 상태가 두 축으로 분리되어 보이는가.
  - row 안의 정보량이 스캔 가능한 수준인지 본다.

### Dashboard Core

대상: `DashboardContext`, `MetricStateStrip`, `StarterConnectionStrip`, `RecoveryNotice`, `MetricScalars`

- 목적: 선택된 application의 현재 운영 상태를 가장 먼저 판단하게 한다.
- 상태:
  - no project: Project 선택 안내.
  - no application: Application 선택 대기.
  - loading: dashboard link 호출 중.
  - error: dashboard resource 실패.
  - ready: dashboard presentation 표시.
  - recovery notice: `dashboard.recovery.isRecovering`이 true일 때만 표시.
- 리뷰 포인트:
  - metric state와 starter connection이 서로 다른 의미로 읽히는가.
  - 상태 rationale/recommended action 문구가 실제 행동으로 이어지는가.
  - requests/errors/error rate 숫자가 충분히 눈에 들어오는가.

### Dashboard Evidence Panels

대상: `SourceScopedPercentilesPanel`, `HistogramPanel`, `TriagePanel`, `EndpointPriorityPanel`

- 목적: 현재 상태 판단의 근거와 다음 확인 순서를 보여준다.
- 상태:
  - percentiles: source status가 missing/insufficient이면 reason을 표시하고, items가 있으면 table 표시.
  - histogram: current/baseline window별 status, reason, buckets 표시. buckets가 없으면 source absence 문구 표시.
  - triage: triage card가 있으면 severity/score/evidence 표시. 없으면 zero insight block 표시.
  - endpoint priority: 후보가 없으면 empty 문구, 있으면 server order로 ranked list 표시.
- 리뷰 포인트:
  - "근거"와 "다음 행동"의 위계가 분명한가.
  - 테이블과 리스트의 라벨이 사용자 언어에 가까운가.
  - server/read model 정책 문구를 어디까지 노출할지 결정한다.

### CredentialLifecyclePanel

- 목적: 선택한 project의 starter credential metadata 조회, rotation, revocation을 제공한다.
- 상태:
  - no project: project 선택 후 조회 가능하다는 안내.
  - loading: credential metadata 조회 중.
  - error: 404/401/기타 lifecycle 요청 실패.
  - ready: key prefix/status/issued/rotated/revoked metadata 표시.
  - rotate loading: 새 credential 발급 중.
  - revoke confirm: 첫 클릭 후 confirm revoke로 변경.
  - one-time credential: rotation 직후 raw credential 표시.
- 리뷰 포인트:
  - secret raw value와 metadata의 차이가 분명한가.
  - Rotate/Revoke 위험도가 UI에서 충분히 구분되는가.

### InstancesPanel

- 목적: application instance 목록과 evidence/trend drawer 진입점을 제공한다.
- 상태:
  - empty: bounded instance handoff entry 없음.
  - ready: instance name, last seen, evidence link, evidence/trend 버튼 표시.
- 리뷰 포인트:
  - evidence/trend가 어떤 차이인지 버튼만으로 이해되는가.
  - raw link 노출이 필요한지, tooltip이나 축약으로 충분한지 본다.

### SnapshotHistoryPanel

- 목적: stored operational events, snapshot markers, snapshot detail 진입을 제공한다.
- 상태:
  - preset: 24h/7d/14d.
  - no current handoff: current dashboard snapshot handoff 없음 안내.
  - loading: event와 marker 병렬 로딩 중.
  - error: auth/404/400/기타 history 실패.
  - events empty: retention/source absence 또는 후보 없음.
  - markers empty: marker source 없음.
  - ready: events/markers 목록과 Detail 버튼 표시.
  - detail target none: detail 선택 안내.
- 리뷰 포인트:
  - history가 현재 상태가 아니라 stored snapshot이라는 점이 자연스럽게 전달되는가.
  - event와 marker의 차이를 UI에서 이해할 수 있는가.

### Instance Evidence Drawer

파일: `/Users/tlsdla1235/Desktop/study/observation/frontend/src/app/components/instance-panels.tsx`

- 목적: 특정 instance의 bounded evidence bundle을 보여준다.
- 상태:
  - loading: evidence link 검증 후 호출 중.
  - error: auth/404/400/link validation/기타 실패.
  - ready: identity, metric data axis, starter connection axis, triage contribution, percentile series, histogram, resource hints, endpoint evidence 표시.
  - trend action disabled: `evidence.links.snapshotTrend`가 없으면 Trend 버튼 비활성화.
- 리뷰 포인트:
  - drawer 안에서도 "metric data axis"와 "starter connection axis"가 분리되어 보이는가.
  - 정보량이 많아도 흐름이 끊기지 않는가.

### Instance Trend Drawer

- 목적: stored dashboard snapshot projection 기반 instance trend를 보여준다.
- 상태:
  - preset: 7d/14d.
  - loading: fixed query 호출 중.
  - error: auth/404/400/context mismatch/기타 실패.
  - empty: trend point 없음. 정상/복구 완료로 표현하지 않는다.
  - ready: trend points, percentile point, endpoint refs, detail 진입 표시.
- 리뷰 포인트:
  - trend가 현재 health가 아니라 저장된 snapshot projection이라는 점이 이해되는가.
  - detail 버튼과 endpoint ref 버튼의 역할이 헷갈리지 않는가.

### SnapshotDetailSurface

파일: `/Users/tlsdla1235/Desktop/study/observation/frontend/src/app/components/snapshot-detail-surface.tsx`

- 목적: stored snapshot detail API의 bounded projection만 렌더링한다.
- 상태:
  - no target: event/marker/trend point에서 detail 선택 안내.
  - loading: stored detail 로딩 중.
  - error: auth/404/400/contract mismatch/기타 실패.
  - ready: snapshot metadata, read semantics, recovery marker, active anchor, endpoint evidence, instance summary refs 표시.
  - active anchor resolved/missing: 선택된 anchor가 endpoint evidence에 있는지 표시.
- 리뷰 포인트:
  - read semantics/raw JSON/live joins 같은 내부 계약 정보를 사용자에게 얼마나 보여줄지 결정한다.
  - active anchor 강조가 충분히 눈에 들어오는가.

## Backend/Read Model TODO

- p95/p99 spike 근거: 현재 backend read model은 `sourceScopedPercentiles.items[].p95Ms/p99Ms`와 triage evidence의 `sourcePercentilePoint`로 current instance bucket point만 제공한다. app/endpoint 단위의 `current/baseline/delta` p95/p99 scalar는 제공하지 않는다. UI에서 "p99 기준 대비 증가"를 직접 표시하려면 API shape를 별도 확장해야 한다.
- Histogram bucket 표시: 현재 backend read model과 endpoint evidence는 `leMs` cumulative bucket을 유지한다. 사용자 화면에서 독립 구간 분포를 안정적으로 보여주려면 `0-100ms`, `100-300ms`, `300ms-1s` 같은 non-overlapping bin field를 API에 추가하는 후속 정책 결정이 필요하다.

## 새 컨텍스트 첫 메시지 예시

아래처럼 요청하면 바로 이어가기 좋다.

```text
브랜치 codex/frontend-component-review-workflow에서
implementation-artifacts/frontend-component-review-workflow.md 지침대로
서버 연결 없이 mock/fixture로 프론트만 띄워서 Nav부터 스크린샷 리뷰를 시작해줘.
각 컴포넌트마다 스크린샷, 가능한 상태와 조건, 디자인/문구 관찰을 먼저 설명하고
내가 말하는 의도는 컴포넌트별로 기록만 해줘.
바로 수정하지 말고, 내가 마지막에 승인하면 그때 누적된 의도를 반영해줘.
```
