---
artifactType: ux-pitch
projectName: Observation Portal
status: proposed
date: 2026-06-07
relatedMockup: planning-artifacts/mockups/operator-decision-dashboard-mockup.html
---

# Observation Portal 운영 판단 대시보드 UX 제안

## 한 줄 PR

Observation Portal 대시보드는 "관측 데이터를 나열하는 화면"에서 "운영자가 지금 어디부터 봐야 하는지 말해주는 판단 콘솔"로 바뀌어야 한다.

## 왜 바꾸는가

현재 대시보드는 인스턴스, 트렌드, 스냅샷, 수집 상태, 앱 연결, 히스토그램, endpoint priority가 한 화면에 함께 드러난다. 각각의 정보는 의미가 있지만, 처음 보는 사용자는 "그래서 지금 무엇이 문제인가?", "내가 먼저 눌러야 하는 것은 무엇인가?"를 바로 알기 어렵다.

이번 UX 제안은 데이터를 줄이는 것이 아니라, 운영자가 묻는 순서대로 다시 배열하는 것이다.

기존 질문:

- 어떤 metric이 있지?
- 어떤 endpoint가 느리지?
- 어떤 instance가 있지?
- snapshot은 어디 있지?

새 질문:

- 지금 사용자 영향이 있는가?
- 가장 먼저 볼 문제 후보는 무엇인가?
- 그 판단의 근거는 무엇인가?
- endpoint 문제인가, resource 문제인가, instance 문제인가?
- 이 시점의 증거를 나중에 다시 설명할 수 있는가?

## 제안하는 화면의 멘탈 모델

새 대시보드의 멘탈 모델은 다음과 같다.

> 운영자의 첫 3분을 돕는 상황실 화면

화면은 `현재 판정 -> 영향 범위 -> 먼저 볼 지점 -> 증거 -> 다음 행동` 순서로 읽혀야 한다. RED, USE, golden signals 같은 관측 프레임워크는 내부 계산 언어로 두고, 사용자가 보는 화면에서는 요청 증상, 자원 압박, 서비스 생존 신호, 운영 우선순위로 번역한다.

## 목업

목업 파일:

- HTML: `planning-artifacts/mockups/operator-decision-dashboard-mockup.html`
- Desktop preview: `planning-artifacts/mockups/operator-decision-dashboard-mockup-desktop.png`
- Mobile preview: `planning-artifacts/mockups/operator-decision-dashboard-mockup-mobile.png`

현재 앱의 UX 톤은 유지했다.

- 흰 배경과 얇은 neutral border
- 좌측 Project/Application rail
- 작은 uppercase section label
- 조밀한 운영 정보 패널
- 카드 과잉이나 마케팅형 hero 대신 실제 반복 사용 화면에 가까운 밀도

## 화면 구성

### 1. 좌측 Project / Application Rail

좌측 rail은 기존 구조를 유지한다. 다만 각 application row의 역할을 더 명확하게 한다.

기존에는 application 상태와 freshness가 주로 보였다면, 새 화면에서는 application row가 "지금 볼 가치가 있는가"를 먼저 말한다.

예시:

- `orders-api · degraded`
- `POST /orders 지연 + datasource pool 압박`
- `impact 84 · checkout journey`

이 rail의 목적은 상세 분석이 아니라 triage entry다. 정상 앱, 저활동 앱, 테스트성 앱은 같은 무게로 경쟁하지 않는다.

### 2. Operator Judgment

중앙 상단의 핵심 영역이다. 사용자가 화면에 들어오면 가장 먼저 읽는 문장은 metric 이름이 아니라 운영 판단이어야 한다.

예시:

> POST /orders 지연 증가와 datasource pool 포화가 함께 발생 중입니다.

이 문장 아래에는 판단의 이유를 짧게 붙인다.

- checkout 핵심 여정
- 오류율과 500ms 초과 요청 비중 동시 증가
- 같은 window의 datasource pool 사용률 91%
- DB connection wait 가능성 우선 확인

여기서 중요한 점은 "원인 확정"이 아니라 "먼저 확인할 가설"이라는 태도다.

### 3. Impact / Confidence 분리

목업에서는 운영 우선순위 점수 `84`를 크게 보여준다. 이 점수는 장애 심각도 확정값이 아니라 먼저 조사할 가치다.

Impact score에 들어갈 후보:

- 요청량 또는 request share
- 오류율과 오류 증가량
- slow share와 baseline 대비 증가량
- endpoint가 핵심 여정에 속하는지
- affected instance 범위
- 최근성

Confidence는 별도로 둔다.

- evidence가 충분한가?
- baseline 비교가 가능한가?
- 같은 시간대 resource hint가 있는가?
- low traffic penalty가 있는가?

Impact와 confidence를 분리하면 "영향은 커 보이지만 근거가 약함", "근거는 강하지만 영향은 작음"을 UI에서 정직하게 표현할 수 있다.

### 4. Look First Top 3

기존 endpoint priority를 그대로 테이블에만 두지 않고, 운영자가 실제로 확인할 순서로 만든다.

목업의 Top 3:

1. `POST /orders`
   - 오류와 지연이 동시에 증가
   - 최근 배포와 5xx 로그 확인
2. `Datasource pool`
   - 같은 window에서 pool 사용률 91%
   - DB connection wait 가능성 확인
3. `orders-api-7f9c-x2p4k`
   - endpoint evidence ref와 p99 상승이 연결된 instance

이 구조의 장점은 endpoint, resource, instance가 따로 흩어지지 않는다는 점이다. 운영자는 "endpoint 탭", "JVM 탭", "snapshot 탭"을 오가며 스스로 조합하지 않아도 된다.

### 5. Endpoint Priority

Endpoint priority는 계속 필요하다. 다만 역할은 "느린 API 목록"이 아니라 "현재 악화 기여도 순위"다.

정렬 기준은 다음 조합을 추천한다.

- error spike
- latency spike
- error + latency 동시 발생
- baseline regression
- recent error
- request share
- low sample penalty
- optional criticality metadata

현재 persistence에서 endpoint p95/p99 rollup은 안전하게 만들 수 없다. 따라서 MVP에서는 endpoint별 request/error/duration histogram 기반의 slow share를 사용한다.

### 6. Evidence Timeline

Timeline은 화려한 chart가 아니라 "사건이 언제부터 나빠졌는지"를 보여주는 작은 증거 흐름이다.

목업에서는 최근 30분을 30초 또는 몇 분 단위 evidence tile로 보여준다.

- ok
- slow
- err
- pool
- snapshot
- now

이 방식은 운영자가 current window와 baseline window를 더 쉽게 이해하게 한다.

### 7. RED / USE 번역

화면에서는 RED와 USE를 그대로 노출하지 않는다.

RED는 요청 증상으로 번역한다.

- 요청량
- 실패
- 느린 요청

USE는 자원 압박으로 번역한다.

- CPU
- Heap
- DB pool

이 번역은 초보 사용자에게 친절할 뿐 아니라, 포트폴리오 관점에서도 제품의 해석 능력을 보여준다.

### 8. Data Quality

저활동, 초기, 테스트 데이터는 정상처럼 보여주면 안 된다. 그래서 목업에는 별도의 Data Quality 영역을 둔다.

상태 예시:

- `sufficient`: 판단 가능한 표본
- `initializing`: 기준선 학습 중
- `low traffic`: 표본 부족
- `test/synthetic`: 운영 영향 산정 제외
- `stale/down`: 수집 freshness 문제

이 영역은 사용자에게 "지금 화면의 판단을 얼마나 믿어도 되는가"를 알려준다.

### 9. Flight Recorder

Snapshot은 과거 화면이 아니라 사건 봉투다.

목업의 Flight Recorder 영역은 다음을 암시한다.

- high-confidence concern으로 capture됨
- evidence version과 score version 보존
- endpoint evidence와 instance summary를 함께 저장
- 회고와 공유에 사용할 수 있음

이 기능은 제품 차별점이 될 수 있다. 단순 monitoring은 현재 상태를 보여주지만, Observation Portal은 "그때 왜 그렇게 판단했는가"를 설명할 수 있다.

## 기존 데이터와의 연결

이 UX는 새로운 원천 데이터를 크게 요구하지 않는다. 현재 구조를 상당 부분 재사용할 수 있다.

재사용 가능한 원천:

- `accepted_metric_buckets.request_count`
- `accepted_metric_buckets.error_count`
- `accepted_metric_buckets.duration_buckets_json`
- `accepted_metric_buckets.endpoints_json`
- `accepted_metric_buckets.cpu_usage_ratio`
- `accepted_metric_buckets.heap_used_ratio`
- `accepted_metric_buckets.datasource_pool_usage_ratio`
- `dashboardSnapshots.read_model_json`
- `snapshotEndpointEvidence`
- `instanceSummary`
- `triageCards`
- `endpointPriority`

추가하면 좋은 것은 원천 metric이 아니라 운영 맥락이다.

- endpoint display name
- business criticality
- journey name
- owner team
- runbook URL
- scoring version
- evidence version
- data quality state

## 사용자에게 주는 가치

### 신규 사용자

처음 들어와도 "무엇을 먼저 봐야 하는지"가 보인다. 복잡한 observability 용어를 몰라도 요청 실패, 느려짐, 자원 압박이라는 언어로 이해할 수 있다.

### 운영자

탭을 오가며 증거를 수동으로 조합하지 않아도 된다. endpoint, instance, resource hint가 하나의 판단 카드로 연결된다.

### 포트폴리오 평가자

단순 CRUD 또는 chart dashboard가 아니라, 관측 데이터를 해석해 원인 후보를 제시하는 제품으로 보인다. 특히 impact score, data quality, flight recorder는 제품적 사고와 backend read model 설계를 함께 보여준다.

## 구현 단계 제안

### Phase 1. UX Shell

목표: 현재 데이터만으로 운영 판단 화면을 구성한다.

- 기존 dashboard API 응답을 adapter에서 새 presentation model로 재배열
- Operator Judgment 영역 추가
- Look First Top 3 구성
- RED/USE 번역 label 적용
- Data quality placeholder 추가

Schema 변경 없음.

### Phase 2. Scoring Read Model

목표: impact와 confidence를 서버가 명시적으로 제공한다.

- `operatorSummary`
- `impactScore`
- `confidenceBreakdown`
- `affectedScope`
- `signalBreakdown`
- `baselineComparisonSummary`
- `dataQuality`

### Phase 3. Metadata

목표: 기술 신호에 비즈니스 중요도를 반영한다.

- endpoint metadata 추가
- journey, owner, runbook 노출
- criticality 기반 impact weighting

### Phase 4. Flight Recorder

목표: snapshot을 회고 가능한 사건 보고서로 만든다.

- evidence bundle version
- scoring version
- data completeness
- missing bucket count
- review summary
- shareable snapshot detail view

## PR 리뷰 때 강조할 포인트

- 기존 UI를 조금 꾸민 것이 아니라, 사용자의 질문 순서로 정보 구조를 바꾼다.
- 기존 persistence는 대부분 유지하고 read model과 presentation model을 강화한다.
- endpoint p95/p99를 억지로 만들지 않고, 현재 데이터에서 정직하게 만들 수 있는 slow share와 baseline delta를 사용한다.
- 저활동/초기/테스트 상태를 정상으로 뭉개지 않고 data quality로 표현한다.
- snapshot을 "과거 화면"이 아니라 "판단 증거 번들"로 제품화한다.

## 최종 문장

이 UX 개편의 핵심은 더 많은 그래프가 아니다. 핵심은 운영자가 화면을 열자마자 "아, 여기부터 보면 되겠네"라고 말할 수 있게 만드는 것이다.

