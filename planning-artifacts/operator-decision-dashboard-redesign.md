---
artifactType: product-ux-redesign-note
projectName: Observation Portal
status: proposed
date: 2026-06-07
source: BMAD party mode discussion
---

> Reference-only note: 2026-06-08 기준 MVP dashboard/snapshot 관계와 구현 우선순위는 `planning-artifacts/dashboard-snapshot-mvp-source-of-truth.md`를 우선한다. 이 문서는 폐기하지 않지만, 아직 논의가 닫히지 않은 UX 확장 아이디어 참고 자료로 둔다.

# Observation Portal 운영 판단 대시보드 재설계

작성일: 2026-06-07

## 한 줄 정의

Observation Portal 대시보드는 그래프를 모아 보여주는 화면이 아니라, 운영자가 첫 3분 안에 "무엇이 문제이고 어디부터 봐야 하는지" 판단하도록 돕는 운영 판단 콘솔이다.

## 원본/상세 설명

### 1. 추천 멘탈 모델

추천 멘탈 모델은 다음 한 문장으로 둔다.

> Observation Portal은 그래프 대시보드가 아니라 운영자의 첫 3분을 돕는 운영 판단 콘솔이다.

화면의 기본 흐름은 `현재 판정 -> 영향 범위 -> 먼저 볼 지점 -> 증거 -> 다음 행동`이다. 사용자는 RED, USE, golden signals 같은 내부 관측 프레임워크를 학습하러 들어오는 것이 아니라, "지금 사용자 영향이 있는가?", "어디부터 열어야 하는가?", "왜 그렇게 말하는가?"를 빠르게 확인하러 들어온다.

### 2. 새 대시보드 정보 구조

새 정보 구조는 현재 UI 컴포넌트 배치를 유지하지 않고, 운영자의 질문 순서로 재구성한다.

1. Project/Application Overview
   - 앱별 현재 상태와 "지금 봐야 할 것"만 노출한다.
   - 정상, 저활동, 초기 수집 앱은 접거나 낮은 우선순위로 둔다.
2. Application Command Center
   - 선택한 앱의 현재 결론, golden signals, Top suspects, data quality를 보여준다.
   - 기존 `triageCards`와 `endpointPriority`가 이 화면의 핵심 원천이 된다.
3. Problem Detail
   - endpoint, instance, resource evidence를 하나의 사건으로 묶는다.
   - `current window`와 `baseline window`의 비교를 먼저 보여준다.
4. Snapshot Flight Recorder
   - 특정 시점의 판단, 증거, diff, 관련 endpoint/instance, 회고 요약을 저장된 증거 번들로 보여준다.
   - live source를 다시 조인하거나 현재 상태를 재계산하지 않는다.
5. History/Review
   - 과거 operational event, snapshot marker, 회복 흐름을 사건 목록으로 보여준다.

### 3. 첫 화면 구성안

첫 화면은 "전체 데이터를 다 보여주는 곳"이 아니라 "지금 어디부터 봐야 하는지 말하는 곳"이어야 한다.

필수 블록:

- Scope selector: project, application, environment
- 현재 결론 한 문장: 예를 들어 "`/api/orders` 지연 증가와 datasource pool 포화가 함께 발생 중입니다."
- Impact/Confidence: 영향도와 판단 신뢰도를 분리해서 표시한다.
- Look First Top 3: 가장 먼저 볼 endpoint, instance, resource hint
- Golden Signals Summary: 요청량, 실패, 느려짐, 자원 압박
- Evidence Timeline: 최근 30초 bucket 흐름과 current/baseline 비교
- Data Quality: 초기 수집, 저활동, stale/down, test traffic 여부

첫 화면에서 endpoint p95/p99를 새로 계산해 보여주려고 하지 않는다. 현재 원천은 endpoint 단위 request/error/duration histogram이며, endpoint percentile rollup은 계약상 존재하지 않는다. 따라서 MVP에서는 `error rate`, `slow share`, `baseline delta`, `request share`를 정직하게 사용한다.

### 4. 문제 상세 화면 구성안

문제 상세 화면은 사건 보고서처럼 읽혀야 한다.

권장 구성:

- 사건 제목: "POST /orders 오류와 지연이 동시에 증가"
- 현재 판단: degraded, warning, recovery observing 등
- 우선 조사 점수: impact score, confidence, data quality
- 증상 요약
  - 요청량: 현재 요청 수와 baseline 대비 변화
  - 실패: error count, error rate, error delta
  - 느려짐: 500ms 초과 slow share, baseline slow share, delta
  - 자원 압박: CPU, heap, datasource pool usage ratio
- 의심 endpoint
  - method, route, endpointKey
  - rule ids, reason, recommended action
  - current/baseline evidence
- 의심 instance
  - affected instance count
  - instance별 freshness, request count, resource hints
  - endpoint evidence ref
- 타임라인
  - 최근 30초 bucket 흐름
  - bad bucket count
  - 관련 snapshot marker
- 다음 행동
  - 로그 확인, 배포 변경 확인, DB pool 대기 확인, 외부 의존성 확인 같은 구체적 action

### 5. Snapshot / Flight Recorder 구성안

Snapshot은 단순 과거 화면 저장소가 아니라 flight recorder가 되어야 한다. 장애 후에 "그때 시스템이 무엇을 보고 그렇게 판단했나"를 재현할 수 있어야 한다.

필수 데이터 묶음:

- snapshot metadata
  - snapshotId, capturedAt, generatedAt
  - current window, baseline window
  - captureReason, stateCode
- primary finding
  - primaryRuleId
  - primaryEndpointKey
  - primarySuspectType, primarySuspectKey
  - maxConfidence, impactScore
- scoring metadata
  - scoringVersion
  - evidenceVersion
  - metadataVersion
  - dataQualityState
- evidence bundle
  - endpoint evidence 최대 10개
  - instance summary 최대 50개
  - resource hints
  - rule hits
  - confidence breakdown
  - missing data notes
- baseline diff
  - request delta
  - error rate delta
  - slow share delta
  - affected request share
- review summary
  - 공유 가능한 회고 요약
  - recommended next look
  - related operational events

현재 `dashboard_snapshots.read_model_json`과 `snapshotEndpointEvidence`, `instanceSummary` 구조는 이 방향에 이미 가깝다. 따라서 원천 metric bucket schema를 갈아엎기보다 snapshot JSON evidence bundle과 낮은 cardinality helper column을 강화하는 편이 좋다.

### 6. 기존 영속화 데이터 재사용 가능성 평가

재사용 가능성은 높다.

현재 확인된 핵심 원천:

- `accepted_metric_buckets`
  - 30초 bucket
  - request_count, error_count
  - duration_buckets_json
  - cpu_usage_ratio, heap_used_ratio, datasource_pool_usage_ratio
  - local_percentiles_json
  - endpoints_json
- `applications`, `application_instances`
  - application/environment identity
  - instance identity
  - first/last seen
- `dashboard_snapshots`
  - stored read_model_json
  - current/baseline window
  - state_code, capture_reason
  - primary_rule_id, primary_endpoint_key, max_confidence

기존 `TriageSummaryService`는 application-level error spike, sustained error, latency spike, datasource pool/CPU/heap saturation hint를 이미 계산한다. 기존 `EndpointPriorityService`도 error spike, latency spike, error+latency, comparative regression, recent error를 기준으로 Top 5 endpoint를 만든다.

따라서 재설계의 중심은 persistence 교체가 아니라 read model의 "운영자 언어 번역"이다.

### 7. 추가하면 좋은 영속화 / read model / projection

우선 read model에 추가하면 좋은 필드:

- `operatorSummary`
- `primaryFindingText`
- `recommendedFirstLook`
- `lookFirst`
- `impactScore`
- `confidenceBreakdown`
- `signalBreakdown`
- `affectedScope`
- `affectedEndpointCount`
- `affectedInstanceCount`
- `baselineComparisonSummary`
- `dataQuality`
- `metadataBadges`
- `evidenceBundleId`
- `evidenceVersion`

최소 영속화 추가 후보:

- `endpoint_metadata`
  - project_id
  - application_id
  - endpoint_key
  - display_name
  - business_criticality
  - journey_name
  - owner_team
  - runbook_url
  - slo_target_ms
  - slo_error_rate
  - is_core
  - is_synthetic_or_test
  - tags_json
- `dashboard_snapshots` helper 후보
  - impact_score
  - primary_suspect_type
  - primary_suspect_key
  - data_quality_state
  - scoring_version
  - evidence_version

성능 문제가 확인되기 전에는 별도 rollup 테이블을 먼저 만들지 않는다. 필요해지면 `dashboard_incident_projection` 또는 `dashboard_endpoint_evidence_projection` 같은 read-side materialized projection을 추가한다.

### 8. 저활동 / 초기 / 테스트 상태 표현 전략

저활동, 초기, 테스트 상태는 "정상"으로 묶지 않는다. 이는 운영자에게 과신을 만든다.

권장 상태:

- Initializing
  - 아직 기준선이나 첫 accepted bucket이 충분하지 않다.
  - 문구: "기준선을 만들기 위해 데이터를 수집 중입니다."
- Low traffic
  - 요청 수가 부족해 판단 신뢰가 낮다.
  - 문구: "표본이 적어 장애 여부를 단정하지 않습니다."
- Quiet
  - 관측은 정상이나 트래픽이 낮다.
  - 문구: "관측은 정상이며 현재 요청량이 낮습니다."
- No starter connection
  - heartbeat/control-plane 신호가 없다.
  - 문구: "starter 연결 또는 project key를 확인하세요."
- Test/Synthetic
  - 운영 영향 점수에서 제외하거나 감산한다.
  - 문구: "테스트성 트래픽으로 운영 영향 산정에서 제외되었습니다."
- Recovering
  - 증상은 완화됐지만 회복 관찰 중이다.
  - 문구: "최근 문제가 있었고 다음 bucket까지 회복을 관찰 중입니다."

### 9. 구현 우선순위

1. 기존 데이터만으로 첫 화면을 운영 판단 콘솔로 재구성한다.
2. `impactScore`, `confidence`, `dataQuality`를 read model에 추가한다.
3. snapshot evidence bundle을 flight recorder처럼 강화한다.
4. endpoint/application metadata를 작게 추가한다.
5. 성능이 필요해질 때만 materialized read projection을 추가한다.

### 10. 최종 추천안

원천 persistence는 유지한다. 화면은 새로 설계한다.

첫 MVP는 "차트를 잘 보여주는 화면"이 아니라 "지금 어디부터 봐야 하는지 말해주는 화면"이어야 한다. 이 방향이 포트폴리오에서도 가장 강하다. 단순 모니터링 SaaS가 아니라, 원인 후보와 근거를 제시하는 observability product로 보이기 때문이다.

## 500자 내외 요약

Observation Portal 대시보드는 현재 UI 레이아웃을 유지하기보다 운영자의 질문 순서에 맞춰 다시 설계하는 편이 좋다. 핵심 멘탈 모델은 "그래프 모음"이 아니라 "첫 3분 운영 판단 콘솔"이다. 첫 화면은 현재 결론, 영향도, 판단 신뢰도, 먼저 볼 endpoint/instance/resource hint, golden signals, data quality만 보여준다. 기존 accepted metric bucket, endpoint evidence, triage card, snapshot 구조는 재사용 가능성이 높다. 부족한 것은 비즈니스 중요도, 핵심 여정, owner, runbook, scoring/data quality 같은 read-side 맥락이다. 원천 schema를 크게 바꾸지 말고 read model과 snapshot evidence bundle을 강화하는 방향을 추천한다.

## 핵심 포인트

- RED/USE/golden signals는 내부 용어로 두고, 화면에서는 요청 증상, 자원 압박, 서비스 생존 신호로 번역한다.
- Top N은 단순 느림이나 error rate가 아니라 요청량, 악화 정도, 최근성, 동시성, confidence, criticality를 함께 봐야 한다.
- Snapshot은 과거 화면이 아니라 장애 당시 판단 근거를 재현하는 flight recorder가 되어야 한다.
- 저활동, 초기 수집, 테스트성 트래픽은 정상과 분리해 "판단 보류" 또는 "운영 영향 제외"로 표현한다.

## 헷갈리기 쉬운 점

- Impact score와 confidence는 다르다. Impact는 영향 규모이고 confidence는 판단 신뢰도다.
- endpoint p95/p99 rollup은 현재 원천에서 안전하게 만들 수 있는 값이 아니다. MVP에서는 histogram slow-share와 error delta가 더 정직하다.
- starter heartbeat는 control-plane 신호이고 accepted bucket freshness는 data-plane 신호다. 두 축을 섞어 host application down을 확정하지 않는다.
- metadata는 원천 관측값을 대체하지 않는다. 같은 기술 신호라도 결제, 로그인, 내부 배치의 운영 우선순위를 다르게 해석하기 위한 보조 맥락이다.

## 관련 개념

- RED: Rate, Errors, Duration
- USE: Utilization, Saturation, Errors
- Golden Signals
- Impact Score
- Read-side Projection
- Snapshot Evidence Bundle
- Flight Recorder UX
- Data Quality State
