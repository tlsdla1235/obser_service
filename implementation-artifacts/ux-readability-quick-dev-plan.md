# Observation UI Readability Quick Dev Plan

작성일: 2026-06-06  
브랜치: `codex/observation-ux-readability`  
목표: 백엔드 read model 용어가 그대로 드러나는 화면을, 처음 쓰는 사람도 바로 이해하는 간편 모니터링 화면으로 바꾼다.

## 1. 문제 인식

현재 화면은 기능적으로는 많은 정보를 보여주지만, `accepted_bucket`, `data-plane freshness`, `dashboard_snapshots`, `capturedAt_asc`, `query_fallback`, `starter_canonical_percentile` 같은 내부 구현 용어가 그대로 노출된다. 이 표현은 만든 사람이 읽기에는 정확하지만, 사용자는 "지금 서비스가 괜찮은지", "어디를 먼저 봐야 하는지", "과거 기록이 어떤 의미인지"를 한 번에 이해하기 어렵다.

Observation Portal의 제품 철학은 복잡한 APM 용어를 몰라도 Spring Boot 서비스의 상태를 빠르게 파악하게 하는 것이다. 따라서 이번 quick dev는 데이터 계약을 바꾸기보다, 화면 표현과 정보 구조를 사용자 언어로 재구성하는 데 집중한다.

## 2. 수정 범위

주요 대상 파일:

- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/components/instance-panels.tsx`
- `frontend/src/app/components/snapshot-detail-surface.tsx`
- `frontend/src/app/lib/read-model-adapters.ts`

필요하면 추가할 helper:

- 시간 표시 전용 helper: `formatLocalDateTime`, `formatLocalDateTimeRange`
- severity 표시 helper: `severityDisplayText`, `severityTextClassName`
- 내부 코드 번역 helper: `humanizeStatusCode`, `humanizeSourceCode`, `humanizeCaptureReason`
- histogram 라벨 helper: `formatLatencyBucketRange`

## 3. 요구사항

### 3.1 내부 용어를 사용자 언어로 번역한다

화면 제목, 보조 설명, badge, 표 label, 빈 상태 문구에서 백엔드 필드명과 계약명을 직접 노출하지 않는다.

예시 번역 기준:

| 현재 표현 | 변경 표현 |
| --- | --- |
| `accepted_bucket` | 최근 수집 데이터 |
| `starter_heartbeat` | 앱 연결 신호 |
| `starter_connected` | 앱과 포털이 연결됨 |
| `data-plane freshness` | 수집 데이터 최신성 |
| `metric freshness` | 데이터 상태 |
| `dashboard_snapshots` | 저장된 상태 기록 |
| `capturedAt_asc` | 오래된 기록 먼저 / 최신 기록 먼저 |
| `query_fallback` | 정기 확인 기록 |
| `hourly_scheduled` | 정기 저장 |
| `state_change` | 상태 변화 |
| `stored read model` | 저장된 화면 기록 |
| `raw JSON exposed` | 원본 데이터 노출 여부 |
| `anchor missing` | 연결된 상세 근거 없음 |
| `rule 없음` | 적용된 판단 기준 없음 |

Acceptance:

- Given 사용자가 dashboard, instance evidence, snapshot/history 화면을 본다.
- When 화면에 상태/근거/기록 정보가 표시된다.
- Then snake_case 또는 backend source identifier가 주요 텍스트로 노출되지 않고, 사람이 이해할 수 있는 한국어 label과 설명으로 표시된다.

### 3.2 최신순 정렬을 기본으로 한다

스냅샷/이벤트/트렌드 목록은 최신 기록이 먼저 보여야 한다.

구현 기준:

- API 응답 metadata가 `capturedAt_asc`여도, 화면 렌더링 직전에 `capturedAt` 또는 `occurredAt` 기준 내림차순으로 정렬한다.
- 정렬 metadata는 사용자에게 그대로 보여주지 않는다.
- 화면에는 "최신 기록 먼저"처럼 의미 중심으로 표시한다.
- `validateSnapshotHistoryResponse`, `validateTrendContext`는 기존 contract 검증은 유지하되, 렌더링 배열만 별도 정렬한다.

Acceptance:

- Given snapshot marker나 instance trend point가 여러 개 있다.
- When 화면이 렌더링된다.
- Then 가장 최근 `capturedAt` 또는 `occurredAt` 항목이 목록 상단에 표시된다.

### 3.3 시간은 로컬 시간으로 맞춘다

현재 화면 일부는 UTC timestamp를 그대로 보여줘서 실제보다 9시간 느리게 보인다. 사용자는 한국 시간 기준으로 보는 상황이므로, 모든 주요 시각은 `Asia/Seoul` 기준으로 표시한다.

구현 기준:

- `2026-06-06T09:51:52Z` 같은 ISO 원문을 화면에 그대로 표시하지 않는다.
- `Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", ... })` 또는 동등 helper를 사용한다.
- 날짜/시간 range도 같은 time zone helper를 통과한다.
- 필요하면 보조 label에 `KST`를 붙인다.

Acceptance:

- Given backend가 UTC timestamp를 반환한다.
- When dashboard, evidence, snapshot, history 화면에 시간이 표시된다.
- Then 사용자는 UTC보다 9시간 빠른 한국 시간 기준 값을 본다.

### 3.4 스냅샷 영역을 독립 탭으로 분리한다

현재 snapshot/history 정보가 오른쪽 영역에 과밀하게 들어가 줄바꿈과 카드 배치가 불편하다. 스냅샷은 현재 상태 판단과 성격이 다르므로 별도 탭으로 분리한다.

구현 방향:

- dashboard 본문에 `현재 상태`, `스냅샷 기록` 탭을 둔다.
- `현재 상태` 탭에는 status, starter 연결, p95/p99, histogram, endpoint priority, instance list를 둔다.
- `스냅샷 기록` 탭에는 operational events, snapshot markers, snapshot detail을 둔다.
- 오른쪽 좁은 column 안에 snapshot list/detail을 모두 넣지 않는다.
- snapshot detail은 선택 시 같은 탭 안에서 충분한 폭으로 보여준다.

Acceptance:

- Given 사용자가 application dashboard에 진입한다.
- When 스냅샷 기록을 확인한다.
- Then 별도 탭에서 최신순 기록과 상세 근거를 읽을 수 있고, 카드 내부 텍스트가 좁은 column 때문에 부자연스럽게 줄바꿈되지 않는다.

### 3.5 severity는 색과 문구가 함께 보여야 한다

`critical`, `warning`, `info`는 badge border만으로는 의미가 약하다. 글자 색과 쉬운 한국어 문구를 함께 제공한다.

표시 기준:

| severity | 표시 문구 | 텍스트 색 예시 | 배경/테두리 예시 |
| --- | --- | --- | --- |
| critical | 긴급 | `text-red-700` | `border-red-300 bg-red-50` |
| warning | 주의 | `text-amber-700` | `border-amber-300 bg-amber-50` |
| info | 참고 | `text-sky-700` | `border-sky-300 bg-sky-50` |

Acceptance:

- Given triage, event, marker, endpoint 상태 badge가 표시된다.
- When severity가 critical 또는 warning이다.
- Then badge 문구와 글자 색만으로도 심각도를 구분할 수 있다.

### 3.6 p95/p99 histogram bucket 라벨을 구간형으로 바꾼다

현재 histogram bucket이 `<=5ms`, `<=100ms`처럼 각각의 상한만 보여서 사용자가 "어느 응답시간 범위에 요청이 몰렸는지" 이해하기 어렵다.

표시 기준:

- 첫 bucket: `응답시간 <= 5ms`
- 두 번째 이후 bucket: `5ms <= 응답시간 <= 100ms`
- 더 큰 구간도 같은 패턴으로 표시: `100ms <= 응답시간 <= 300ms`
- 무한대 또는 상한 없음 bucket이 있으면 `1000ms 이상`처럼 표시한다.

데이터 기준:

- backend bucket이 누적 histogram이면, 화면 count도 구간 count로 변환해야 한다.
- 예: `<=5ms count=3`, `<=100ms count=8`이면 두 번째 구간 count는 `5ms~100ms count=5`로 표시한다.
- count 변환은 음수가 나오지 않도록 `Math.max(0, current - previous)`로 방어한다.
- p95/p99 값 자체는 기존 표시를 유지하되, histogram bucket label과 함께 "응답시간 분포"로 설명한다.

Acceptance:

- Given histogram buckets가 `[5, 100, 300]` 상한으로 온다.
- When histogram을 렌더링한다.
- Then label은 `응답시간 <= 5ms`, `5ms <= 응답시간 <= 100ms`, `100ms <= 응답시간 <= 300ms`로 표시된다.
- And count/bar는 누적값이 아니라 해당 구간에 속한 요청 수를 나타낸다.

## 4. 화면별 작업 계획

### 4.1 Dashboard 현재 상태 탭

대상: `frontend/src/app/components/dashboard.tsx`

- `SnapshotHistoryPanel`을 기본 dashboard right column에서 분리하고 탭 구조로 이동한다.
- 상태 판단 카드에서 `METRIC DATA-PLANE UNREACHABLE` 같은 내부 code badge는 "수집 데이터가 끊긴 것 같아요"처럼 사용자 문구로 바꾼다.
- endpoint priority detail label의 `bucket source`, `freshness at`, `source window`, `error evidence`, `latency evidence`를 쉬운 문구로 바꾼다.
- histogram 설명에서 "server가 보낸 bucket" 같은 구현 설명을 제거하고 "응답시간별 요청 수" 중심으로 바꾼다.

### 4.2 Instance Evidence

대상: `frontend/src/app/components/instance-panels.tsx`

- `Metric data axis` → `수집 데이터 상태`
- `Starter connection axis` → `앱 연결 상태`
- `Application triage contribution` → `전체 상태 판단에 반영된 내용`
- `Starter percentile series` → `최근 응답시간 p95/p99`
- `Histogram distribution evidence` → `응답시간 분포`
- `Resource hints` → `리소스 참고 신호`
- `Endpoint evidence` → `엔드포인트별 근거`
- raw link/source/order/max-limit/source scope 등은 기본 화면에서 숨기고, 필요하면 접을 수 있는 "기술 세부 정보" 영역으로 보낸다.

### 4.3 Instance Snapshot Trend

대상: `frontend/src/app/components/instance-panels.tsx`

- header 설명을 "저장된 dashboard snapshot projection..."에서 "과거에 저장된 상태 기록입니다. 현재 상태와 다를 수 있습니다."로 바꾼다.
- trend metadata card에서 `source`, `order`, `default since`, `max limit`을 숨기거나 쉬운 문구로 번역한다.
- trend point title은 ISO 원문 대신 KST formatted time으로 표시한다.
- `stored application state active`는 "저장 당시 상태: 정상"처럼 바꾼다.
- `capture reason`은 "저장 이유"로 표시하고 code를 한국어로 번역한다.
- point 목록은 최신순으로 표시한다.

### 4.4 Snapshot History / Detail 탭

대상:

- `frontend/src/app/components/dashboard.tsx`
- `frontend/src/app/components/snapshot-detail-surface.tsx`

작업:

- `Operational events`, `Snapshot markers`를 각각 "상태 변화 기록", "저장된 스냅샷"으로 바꾼다.
- `Snapshot detail`은 "스냅샷 상세"로 바꾼다.
- detail 상단에서 self link, source, raw JSON exposed 등 내부 검증 정보를 기본 노출하지 않는다.
- `readSemantics` 계열 정보는 "기술 세부 정보" 접힘 영역으로 이동한다.
- detail/marker/event severity badge에 한국어 문구와 색을 적용한다.

## 5. 검증 계획

명령 검증:

```bash
npm --prefix frontend run typecheck
npm --prefix frontend run build
```

시각 검증:

- dashboard 첫 화면에서 snapshot 영역이 별도 탭으로 분리됐는지 확인한다.
- snapshot/event/trend 목록이 최신순인지 확인한다.
- `2026-06-06T09:51:52Z`가 화면에서 `2026년 6월 6일 18:51` 또는 동등 KST로 보이는지 확인한다.
- critical/warning badge가 색과 한국어 문구를 함께 갖는지 확인한다.
- histogram bucket label이 `응답시간 <= 5ms`, `5ms <= 응답시간 <= 100ms` 형태로 보이는지 확인한다.

권장 브라우저 검증:

- local portal 실행 후 Playwright 또는 브라우저 스크린샷으로 desktop/mobile 폭을 확인한다.
- 좁은 폭에서 스냅샷 상세 카드의 텍스트가 불필요하게 한 글자 단위로 줄바꿈되지 않는지 확인한다.

## 6. 비범위

- backend read model contract 변경은 이번 quick dev의 필수 범위가 아니다.
- snapshot 저장 정책, heartbeat 판단 정책, metric freshness 계산 정책은 바꾸지 않는다.
- OAuth, project registration, starter credential 발급 흐름은 건드리지 않는다.

