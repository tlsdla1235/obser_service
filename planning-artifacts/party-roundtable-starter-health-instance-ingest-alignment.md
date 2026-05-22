---
artifactType: party-roundtable-synthesis
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: draft
date: 2026-05-22
alignmentAuthority: latest-clarification-wins
---

# BMAD Party Roundtable - Starter Heartbeat and Instance-Level Ingest Contract Reassessment

## Source Artifacts

- `bmad-restart-context-pack/ux-design-specification.md`
- `bmad-restart-context-pack/observability_toy_spec_v0.8.md`
- `planning-artifacts/architecture.md`
- `planning-artifacts/api-surface.md`
- `planning-artifacts/contracts/read-model-contract.md`
- `planning-artifacts/contracts/state-semantics.md`
- `planning-artifacts/contracts/operational-event-history.md`
- `planning-artifacts/contracts/ingest-envelope.md`
- `planning-artifacts/contracts/histogram-merge.md`
- `planning-artifacts/contracts/starter-failure-semantics.md`
- `planning-artifacts/epics.md`
- `implementation-artifacts/sprint-status.yaml`

## Current Alignment Rule

이 문서는 원 roundtable 기록과 후속 정렬 결정을 함께 보관한다. 원본 planning/contract 문서 정렬에는 아래 우선순위를 적용한다.

1. 이 섹션, `User Clarification`, `Post-Roundtable Adjustment`, `Orchestrator Synthesis`, `Recommended Direction`을 최신 기준으로 본다.
2. `Roundtable Responses`는 역사적 논의 기록이다. 최신 기준과 충돌하는 문장은 정렬 지시로 쓰지 않는다.
3. 특히 `starter p95/p99 payload 반대`, `starter sends buckets only`, `schema validation에서 거절`, `health check`, `handshake` 뉘앙스는 최신 기준으로 대체한다.
4. 최종 기준은 “starter heartbeat는 control-plane/liveness signal이고, accepted bucket만 freshness/state/read-model source-of-truth이며, `localPercentiles`는 instance-local 30초 bucket의 starter canonical percentile”이라는 문장이다.

## User Clarification - Heartbeat Intent

이 draft의 원래 roundtable 응답은 starter 연결 확인을 1회성 `handshake`/`health check` 후보로 다뤘다. 후속 사용자 clarification에 따라 의도는 **주기적 heartbeat**로 정정한다.

정정된 기준:

- 권장 endpoint 후보는 `POST /api/ingest/v1/heartbeat`다.
- heartbeat는 starter가 일정 interval로 portal 도달성, project key 유효성, `application/environment/instance` metadata shape, starter liveness를 알리는 control-plane 신호다.
- heartbeat는 gossip protocol이 아니며 peer-to-peer membership 전파를 하지 않는다.
- heartbeat 성공은 accepted bucket, host application health, dashboard snapshot, operational event, state/read-model calculation을 생성하거나 암시하지 않는다.
- heartbeat 미수신도 host application down을 의미하지 않는다. starter connection 상태와 accepted bucket 기반 application operational state는 별도로 판단한다.
- heartbeat는 별도 lightweight telemetry로 `lastHeartbeatAt`, `lastHeartbeatStatus`, failure category를 저장할 수 있지만, application operational state와 freshness source-of-truth는 계속 accepted bucket이다.
- UI가 heartbeat를 표시할 경우 “starter heartbeat/연결 상태”와 “수집 bucket freshness/application state”를 분리해서 표현한다.

## User Clarification - Local Percentile Intent

이 draft의 초기 roundtable 응답 중 일부는 starter-local p95/p99 payload에 보수적이었다. 후속 사용자 clarification에 따라 현재 정렬 기준은 **localPercentiles를 쓰는 방향**이다.

정정된 기준:

- 개별 instance 화면에서는 percentile의 구체적인 값을 보여주는 것이 제품적으로 중요하다.
- `localPercentiles.p95Ms`/`p99Ms`는 해당 starter가 해당 30초 bucket에서 보고한 starter canonical percentile value다.
- n분 화면은 “n분 전체 요청의 p95/p99”를 새로 만들지 않고 source-scoped point series 또는 bucket distribution으로 표현한다.
- 평균/중앙값/최솟값/최댓값 같은 요약 field는 canonical p95/p99처럼 만들지 않는다.
- 여러 starter 값이 있는 app/project/window에서는 값을 평균/병합하지 않고 source별 노출 또는 bucket distribution 표시를 선택한다.
- instance detail에서는 `starter-reported p95/p99`, `instance 30초 bucket p99`, `source-scoped p95/p99 series`처럼 명확히 라벨링하고 충분히 강조해서 보여줄 수 있다.
- app/project/window percentile source는 starter canonical percentile 정책을 따르며, histogram bucket은 distribution display와 진단 원자료다.
- 이 섹션과 `Post-Roundtable Adjustment - Instance-Local Percentile Trend`가 초기 roundtable의 보수적 local p95/p99 발언을 정정한다.

이 문서를 후속 BMAD 검토 컨텍스트로 사용할 때는 위 clarification들과 `Orchestrator Synthesis`를 최신 기준으로 본다. 아래 `Roundtable Responses`는 원 논의의 전체 기록이며, local p95/p99를 보수적으로 다룬 발언은 후속 clarification으로 대체된다. 원본 문서 정렬자는 `Roundtable Responses` 안의 충돌 문장을 구현/계약 기준으로 인용하지 않는다.

## Historical Roundtable Responses - Superseded Where Conflicting

> 주의: 이 섹션은 의사결정 과정을 보존하기 위한 역사 기록이다. `localPercentiles`를 거절하거나 숨기는 방향, heartbeat를 `health check`/`handshake`로 읽는 방향, heartbeat와 accepted bucket freshness/state를 결합하는 방향은 최신 기준이 아니다.

### John - Product Manager

📋 **John:**

제품 관점에서 기준은 하나입니다. MVP의 약속은 “Spring Boot 앱이 지금 괜찮은지, 왜 그렇게 판단했는지, 어디를 먼저 봐야 하는지”를 첫 화면에서 바로 알게 하는 것입니다. 그래서 확장하되, 운영자가 믿을 수 없는 숫자나 과한 탐색 UI를 넣으면 안 됩니다.

1. **Starter heartbeat API는 MVP에 넣는 게 맞습니다.**  
   사용자의 첫 번째 Job은 “설치가 제대로 됐고 계속 연결되어 있나?”입니다. 데이터가 쌓이기 전이나 끊겼을 때도 project key, portal 연결, 인증 실패, starter liveness를 확인할 수 있어야 합니다.

2. **heartbeat는 ingest bucket과 분리된 별도 endpoint가 좋습니다.**  
   예: `POST /api/ingest/v1/heartbeat`.  
   ingest는 관측 데이터의 source of truth이고, heartbeat는 starter liveness/control-plane 신호입니다. 같은 bucket endpoint에 얹으면 “데이터 수집”과 “연결/존재 신호”의 의미가 섞입니다.

3. **Project -> Application -> Instance navigation은 필요하지만, MVP에서는 얕게 넣어야 합니다.**  
   현재 application-centered 첫 화면은 유지합니다. 다만 상단이나 좌측 rail에서 project 선택, application 선택, instance drill-down이 가능해야 합니다. 핵심 흐름은 “프로젝트를 고른다 → 앱 상태를 본다 → 이상한 인스턴스만 들어간다”입니다.

4. **Instance-level 화면은 MVP에 넣되, 판단 보조 화면이어야 합니다.**  
   instance detail은 독립 대시보드가 아니라 application 판단의 evidence drill-down입니다. 포함 범위는 state, freshness, latency hint, error hint, resource hint, last snapshot/detail 정도가 적절합니다. endpoint priority나 app-level concern을 instance UI에서 다시 계산하면 안 됩니다.

5. **starter ingest schema의 `localPercentiles`는 source와 scope를 붙여야 합니다.**
   p95/p99는 여러 값의 병합 불가능성이 사용자 신뢰를 깨기 쉽습니다. “인스턴스 percentile을 합쳐 앱 percentile처럼 보이는” 순간 제품의 설명 가능성이 무너집니다.

6. **2026-05-22 최신 기준은 starter canonical percentile + bucket distribution입니다.**
   Histogram은 계속 보내되 distribution display와 진단 원자료로 둡니다. Starter가 보낸 p95/p99는 해당 source/scope의 canonical percentile로 표시하고, 서버는 histogram에서 p95/p99를 다시 만들지 않습니다. Endpoint 화면은 percentile이 아니라 bucket display only로 둡니다.

7. **hourly snapshots + spike/error markers는 MVP에 포함할 가치가 있습니다.**  
   단, “장기 시계열 분석”이 아니라 “최근 14일 동안 어떤 상태 변화와 사건 후보가 있었는지”를 보여주는 운영 기억장치로 포지셔닝해야 합니다. hourly scheduled, state change, high-confidence concern, spike candidate 저장은 현재 문서 방향과 잘 맞습니다.

8. **long-window p99는 제품에서 조심스럽게 다뤄야 합니다.**  
   snapshot detail에는 저장 당시의 read model과 marker를 보여주고, 14일 전체 p99 같은 숫자는 대표 지표처럼 노출하지 않는 게 좋습니다. tail latency는 starter-reported source/scope 또는 bucket distribution marker로 표현하고, 긴 기간 비교는 spike/error marker의 발생 시점과 빈도로 풀어야 합니다.

스토리 배치로는 이렇게 자르는 게 좋겠습니다.

1. Starter heartbeat story
2. Project/Application selector story
3. Instance list with freshness/state hints story
4. Instance detail evidence story
5. Server-side instance percentile from histograms story
6. Snapshot marker persistence/display story
7. Snapshot detail read-model story
8. Copy/UX guardrails for p99 and non-mergeable metrics story

제품적으로 가장 중요한 선은 이겁니다: starter는 histogram bucket과 canonical percentile summary를 보내고, portal이 source/scope를 보존해 read model을 만든다. UI는 판단 결과와 근거를 보여주되 재계산하지 않는다. p99는 같은 scope에서 starter-reported 값 하나만 보여야 하며, histogram-derived 값을 병렬로 두지 않습니다.

### Sally - UX Designer

🎨 **Sally:**

UX 관점 결론부터 말하면, **project → application → instance 내비게이션은 기존 application-centered 첫 화면과 충돌하지 않고, 오히려 그것을 구체화합니다.** 단, 첫 화면의 주어는 계속 “애플리케이션 상태”여야 합니다. 프로젝트는 범위 선택이고, 인스턴스는 원인 확인과 조치 단서입니다. 인스턴스 화면이 앱 판단을 대체하는 두 번째 대시보드처럼 보이면 흔들리고, “이 앱 상태를 구성한 증거”로 보이면 단단해집니다.

Starter heartbeat API는 UX상 아주 좋습니다. 다만 이름과 표현이 중요합니다. 사용자는 이것을 “앱이 건강하다”로 오해하면 안 되고, **“starter heartbeat/수집 연결 상태”** 로 이해해야 합니다. Setup 화면에서는 `프로젝트 키 유효`, `포털 도달 가능`, `메타데이터 인식됨`, `아직 ingest 데이터 없음`, `마지막 heartbeat 수신`을 분리해서 보여주는 게 좋습니다. 예를 들어 “starter heartbeat는 수신됐지만 아직 30초 요약 데이터가 도착하지 않았습니다”처럼 말해야 사용자가 다음 행동을 압니다. 또한 포털 쪽에는 “마지막 heartbeat 수신 시각”과 “마지막 실제 데이터 수신 시각”을 별도로 보여줘야 합니다.

인스턴스 레벨 UX는 이렇게 잡고 싶습니다.

- Application Home: 앱 상태, 의미 스트립, 인사이트, 요약 메트릭이 중심
- Instance section: “상태를 흔드는 인스턴스”를 우선 노출
- Instance Detail: freshness, latency hint, error hint, resource hint, 최근 snapshot, 관련 endpoint evidence
- Instance state: 독립 판정처럼 보이기보다 “앱 상태에 기여한 신호”로 표현
- Freshness: 가장 강하게 시각화해야 함. 오래된 인스턴스의 latency/error는 흐리게 처리하고 “판단 근거에서 약화됨”을 명확히 표시

p95/p99 ingest schema는 2026-05-22 기준으로 **starter canonical percentile + histogram bucket distribution**으로 정렬합니다. 스타터가 보낸 p95/p99는 source/scope가 붙은 canonical 값이며, 서버는 histogram bucket에서 병렬 percentile 값을 만들지 않습니다. 여러 starter 값이 한 app/project/window에 섞이면 instance/source 단위로 보여주거나 상위 scope에는 bucket distribution을 표시해야 UX 언어가 깨끗합니다.

특히 p99는 화면에서 조심스럽게 다뤄야 합니다. p99를 큰 숫자 카드로 올리면 사용자는 즉시 SLA 지표처럼 받아들입니다. 최신 문서 방향대로라면 p99는 starter-reported source/scope를 함께 보여야 하고, “긴 기간 평균 p99”처럼 보이면 안 됩니다. UI에서는 source-scoped percentile 또는 bucket distribution marker가 먼저 와야 합니다.

Snapshot/spike marker는 사용자가 “그 시점에 무슨 일이 있었는지”를 읽게 해야지, “14일짜리 정밀 시계열”로 착각하게 하면 안 됩니다. 그래서 느낌은 다음이 좋습니다.

- hourly snapshot은 얇고 듬성한 히스토리 레일
- spike/error marker는 작은 사건 배지
- hover/detail에서 “저장된 read model 기준”이라고 명시
- p99 장기 추세선 금지
- marker 클릭 시 재판정하지 않고 당시 저장된 상태, concern, evidence만 표시
- “정확한 초 단위 원인 분석”이 아니라 “운영자가 다시 볼 수 있는 상태 변화 기록”처럼 느껴지게 설계

정리하면, 이 방향은 MVP 원칙과 잘 맞습니다. 첫 화면은 계속 앱 중심으로 유지하고, 프로젝트와 인스턴스는 사용자가 범위를 좁히고 증거를 확인하는 구조로 붙이면 됩니다. 핵심은 UI가 절대 계산자처럼 행동하지 않는 것, 그리고 p99/spike를 정밀 장기 지표가 아니라 “주의해야 할 사건 신호”로 다루는 것입니다.

### Winston - System Architect

🏗️ **Winston:**

제 추천은 명확합니다. **heartbeat는 ingest와 분리하고, percentile은 histogram을 서버에서만 계산하며, 화면은 저장된 read model만 읽게 둡니다.** 이게 가장 지루하지만 운영 제품에는 제일 오래 버팁니다.

**1. Starter → Portal heartbeat**

`POST /api/ingest/v1/buckets`를 heartbeat 겸용으로 쓰지 않는 쪽이 좋습니다.

추천 API:

```http
POST /api/ingest/v1/heartbeat
X-OBS-Project-Key: ...
```

역할은 네 가지입니다.

- project key 유효성 확인
- portal 연결 가능 여부 확인
- starter가 보낸 `application/environment/instance` 메타데이터가 수용 가능한지 검증
- starter liveness와 heartbeat cadence 확인

응답은 ingest 성공처럼 보이면 안 됩니다. 예를 들면:

```json
{
  "status": "received",
  "projectId": "...",
  "serverTime": "...",
  "supportedIngestVersion": "v1",
  "heartbeatStatus": "recorded"
}
```

비즈니스 가치는 단순합니다. 사용자가 starter 붙였는데 “왜 안 보이지?” 하는 첫 실패와 “수집이 끊겼는데 starter는 살아 있나?”라는 운영 질문을 줄입니다. ingest API는 데이터 쓰기 경로이고, heartbeat는 주기적 control-plane 신호입니다. 둘을 섞으면 운영 로그, 실패 알림, 재시도 정책이 지저분해집니다.

**2. Project → Application → Instance 내비게이션**

내비게이션은 이렇게 두는 게 안정적입니다.

```http
GET /api/projects/{projectId}/applications
GET /api/projects/{projectId}/applications/{applicationId}/dashboard
GET /api/projects/{projectId}/applications/{applicationId}/instances
GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/dashboard
```

중요한 경계는 이것입니다.

- Controller: 요청 파라미터 검증, 인증/권한, DTO 반환
- Service: read model 조회 조합, 상태 해석, 기간 선택
- Repository: 저장된 read model 조회
- UI: 계산 금지, 정렬/표시만 수행

특히 UI가 `p95`, `p99`, rule state, endpoint priority를 재계산하면 안 됩니다. MVC라도 화면이 얇아야 운영 판단이 흔들리지 않습니다.

**2-1. Instance p95/p99 ingest schema**

2026-05-22 기준 최신 결정은 **histogram은 유지하고 starter가 보낸 p95/p99를 source/scope 단위 canonical 값으로 사용**하는 것입니다.

Percentile 수학은 엄격해야 합니다.

- p95/p99는 평균낼 수 없습니다.
- instance percentile들을 평균해서 application percentile을 만들 수 없습니다.
- endpoint percentile들을 평균해서 application percentile을 만들 수도 없습니다.
- 최신 계약에서 histogram bucket은 quantile 재계산 입력이 아니라 distribution display와 진단 원자료입니다.

그래서 최신 방향은 “starter가 cumulative histogram buckets와 canonical percentile을 보내고, 서버는 bucket distribution과 source-scoped percentile read model을 만든다”입니다.

과거에는 starter가 instance p95/p99를 함께 보내는 방식이 두 개의 진실을 만들 수 있다고 보았다. 최신 기준에서는 `localPercentiles.p95Ms`/`p99Ms`를 해당 `instance_bucket` scope의 starter canonical percentile로 수용하고, 같은 scope에 histogram-derived percentile을 만들지 않는 방식으로 혼란을 제거한다.

Histogram을 줄이고 p95/p99만 남기는 방식은 피해야 합니다. 저장량은 줄지만 distribution display와 진단 원자료를 잃습니다. Endpoint detail도 percentile이 아니라 bucket display로 유지합니다.

정리하면 ingest schema에는 `instanceP95`, `instanceP99`를 추가하지 않습니다. 대신 서버 read model에 계산 결과를 둡니다.

```http
GET /api/projects/{projectId}/applications/{applicationId}/instances/{instanceId}/dashboard
```

여기에서 서버 계산값으로:

```json
{
  "latency": {
    "p95Ms": 420,
    "p99Ms": 980,
    "sampleCount": 1240,
    "bucketResolution": "configured"
  }
}
```

다만 p99는 조심해서 보여야 합니다. 낮은 traffic의 15분 p99처럼 source/scope가 모호한 숫자는 만들지 않습니다. 화면에서는 starter-reported p99의 source/scope를 드러내거나 bucket distribution으로 표현합니다.

**3. Hourly snapshots와 spike/error marker**

`dashboard_snapshots`는 application-level coarse read model history로 유지하는 게 좋습니다.

저장 대상:

- hourly scheduled snapshot
- state change
- high-confidence concern
- spike candidate
- error marker

하지만 snapshot detail은 절대 raw time-series 재판정 화면이 아니어야 합니다. 저장 당시 read model을 보여주는 화면이어야 합니다.

추천 API:

```http
GET /api/projects/{projectId}/applications/{applicationId}/snapshots
GET /api/projects/{projectId}/applications/{applicationId}/snapshots/{snapshotId}
```

snapshot 목록은 마커 중심:

```json
{
  "snapshotId": "...",
  "capturedAt": "...",
  "state": "DEGRADED",
  "markers": ["LATENCY_SPIKE", "ERROR_RATE_HIGH"],
  "p95Ms": 730,
  "errorRate": 0.042
}
```

long-window p99는 특히 조심해야 합니다. 14일 p99 같은 숫자는 운영자에게 “이 기간 동안 항상 나빴다”는 착시를 줍니다. p99는 window가 길어질수록 극단값 설명에 가까워지고, 현재 상태 판단에는 부적합해집니다.

그래서 snapshot history에서는:

- 기본 추세는 p95, error rate, request count, state marker 중심
- p99는 snapshot detail의 보조 지표로만 노출
- 긴 기간 차트에는 “14d p99” 같은 단일 집계값을 만들지 않음
- 필요하면 “tail latency observed” marker와 sample count를 함께 표시

최종 권고안은 이렇습니다.

1. heartbeat는 `POST /api/ingest/v1/heartbeat`로 분리한다.
2. ingest는 계속 `POST /api/ingest/v1/buckets` 하나의 데이터 쓰기 경로로 유지한다.
3. instance p95/p99는 `localPercentiles`로 starter payload에 포함할 수 있다.
4. 서버는 동일 histogram bucket을 병합해 distribution display payload를 만들고 p95/p99는 starter canonical percentile만 사용한다.
5. UI는 read model만 표시하고 percentile/state/rule/priority를 재계산하지 않는다.
6. hourly snapshot은 저장 당시 판단의 기록으로 다루고, 긴 기간 p99 집계는 만들지 않는다.

이 방향이 Traditional MVC + Service/Repository layering에도 가장 잘 맞습니다. 복잡한 수학은 service/read-model 생성 경계 안에 가두고, controller와 UI는 운영자가 믿고 읽을 수 있는 결과만 전달하게 됩니다.

### Amelia - Senior Software Engineer

💻 **Amelia:**

결론: **Story 4-0은 적절합니다.** 다만 제목의 범위가 조금 위험합니다. `Starter Heartbeat and Instance-Level Ingest Contract Reassessment`는 “구현 + 계약 재검토”가 섞여 있으니, 4-0은 **계약을 잠그는 선행 스토리**로 두고 navigation/snapshot은 별도 스토리로 분리하는 게 맞습니다.

제가 나누면 이렇게 갑니다.

1. **Story 4-0: Starter Heartbeat and Instance-Level Ingest Contract Reassessment**
   - starter가 portal에 project key, 연결 가능성, liveness를 주기적으로 알리는 API 추가
   - instance-level p95/p99를 starter payload에 넣을지 여부 결정
   - ingest contract 문서와 테스트 고정

2. **Story 5-x: Project/Application/Instance Navigation Read Model**
   - project -> application -> instance 탐색 API/UI
   - UI는 read model만 표시
   - 상태, p95/p99, endpoint priority 재계산 금지

3. **Story 5-y or 6-x: Hourly Snapshot Persistence with Spike/Error Markers**
   - 30초 raw bucket 장기 보관이 아니라 hourly summary 저장
   - spike/error marker 중심
   - 장기 p99처럼 오해를 부르는 지표는 비노출 또는 명확히 제한

**4-0 핵심 결정**

Heartbeat API는 찬성입니다. 예: `POST /api/ingest/v1/heartbeat`.

Acceptance Criteria:

- `X-OBS-Project-Key`가 유효하면 200 응답을 반환한다.
- 응답에는 project 식별 결과, accepted ingest contract version, server time, application/environment/instance metadata 검증 결과가 포함된다.
- 잘못된 project key는 401/403으로 실패한다.
- metadata 누락/형식 오류는 400으로 실패한다.
- heartbeat는 metric bucket, histogram, state, snapshot 데이터를 생성하지 않는다.
- starter는 앱 실행 중 background schedule로 이 API를 주기적으로 호출할 수 있다.
- 실패 응답은 starter 로그에서 운영자가 조치 가능한 메시지로 남는다.

Instance-level p95/p99 ingest는 2026-05-22 기준으로 **starter가 `localPercentiles`를 보내고 portal이 source/scope를 보존해 표시**하는 방향으로 고정한다. Histogram bucket은 계속 보내되 percentile 계산 입력이 아니라 distribution display와 진단 원자료다.

Tests:

- valid key heartbeat 성공
- invalid/missing key 실패
- invalid metadata 실패
- heartbeat 호출 후 bucket/read model/snapshot row가 생성되지 않음
- ingest contract version mismatch 처리
- histogram bucket distribution merge와 starter canonical percentile source/scope 테스트
- starter-local p95/p99 필드가 들어와도 무시하거나 schema validation에서 거절  
  - Superseded: 최신 기준에서는 `localPercentiles`를 거절하지 않고, `mergeable=false` starter canonical percentile로 수용하되 여러 값의 평균/병합 rollup은 금지한다.
- UI/API 응답이 저장된 read model 값을 그대로 반환하는지 검증

Non-goals:

- heartbeat에서 application/instance 자동 생성하지 않음
- starter payload에 raw path, query string, high-cardinality tag 추가하지 않음
- starter canonical p95/p99를 source/scope 없이 app/project/window rollup으로 쓰지 않음
- UI에서 상태/rule/p95/p99/endpoint priority 재계산하지 않음
- Prometheus scrape/query UI, per-request trace/sample, endpoint timeseries table 추가하지 않음
- hourly snapshot으로 “장기 p99”를 표현하지 않음

요약하면 4-0은 필요합니다. 단, “heartbeat API 계약 + ingest contract 최종 결정”까지만. navigation과 hourly snapshot은 같은 방향의 기능이지만 blast radius가 달라서 별도 스토리로 빼야 합니다.

### Paige - Technical Writer

📚 **Paige:**

정렬 기준은 하나로 잡겠습니다: **서버가 수집·병합·판정하고, UI는 read model만 표시한다.** 문서 전반에서 이 문장을 기준선으로 삼으면 충돌이 거의 정리됩니다.

**8개 답변**

1. **문서 수정 순서**  
   `ingest-envelope.md` → `state-semantics.md` → `read-model-contract.md` → `operational-event-history.md` → `architecture.md` → `api-surface.md` → `ux-design-specification.md` → `observability_toy_spec_v0.8.md` → `epics.md` / `sprint-status.yaml`.

2. **정의의 원천**  
   계약 문서가 우선입니다. 특히 계층, 상태, p95/p99, endpoint evidence는 `planning-artifacts/contracts/*`가 canonical source입니다.

3. **계층 표현**  
   전 문서에서 `project -> application -> environment -> instance`로 통일합니다. “application home”은 유지하되, “project 범위 안의 application 목록/상세”로 설명을 보강합니다.

4. **UI 책임 범위**  
   UI는 상태, rule, p95, p99, endpoint priority를 재계산하지 않습니다. 모든 화면 문서에 “서버 read model 표시” 문구를 넣습니다.

5. **히스토그램/p95/p99 정렬**  
   Starter는 buckets와 `localPercentiles`를 보냅니다. Server는 bucket distribution을 만들고 p95/p99는 starter canonical percentile source/scope를 따릅니다.

6. **MVP 제외 항목**  
   `raw path`, `query string`, `high-cardinality tags`, `trace/per-request samples`, `Prometheus scrape/query UI`, `endpoint timeseries table`, `30s dashboard snapshot long retention`은 MVP 제외로 모든 문서에서 같은 표현을 씁니다.

7. **API 표면**  
   현재 `api-surface.md`에 heartbeat endpoint가 없으므로 새로 끼워 넣을지 별도 결정이 필요합니다. 넣는다면 “metric ingest가 아닌 control-plane endpoint”로 둡니다.

8. **Epic 4 위치**  
   Epic 2/3 완료 이후의 backlog로 유지합니다. Story 4-0은 구현 착수용이 아니라 **문서 정렬 선행 스토리**로 작성합니다.

**제거할 충돌 표현**

- “UI computes p95/p99/state”
- “frontend derives endpoint priority”
- “starter sends p95/p99”
- “server exposes Prometheus query UI”
- “raw endpoint path as primary identifier”
- “per-request trace sample”
- “30s snapshot retained long-term”
- “application-only hierarchy”처럼 project/instance를 지우는 표현

**문서별 편집 지점**

`observability_toy_spec_v0.8.md`  
“starter-first direct ingest”는 유지. 단, 최신 기준에서는 p95/p99가 starter canonical percentile에서 오고 histogram bucket은 distribution 표시용이라고 정리합니다. “user should not need to know histogram merge”는 “UI와 사용자는 bucket distribution 병합 방식을 몰라도 된다”로 명확히 합니다.

`ux-design-specification.md`  
첫 화면 설명에 “Project 선택 또는 project key 기준의 application 목록”을 추가합니다. Application home/list에는 “application -> environment -> instance drill-down”을 명시합니다. 모든 지표 표시는 “DashboardReadModel에서 받은 값”이라고 적습니다.

`architecture.md`  
MVC 기준은 유지. `ApplicationCatalogService`, `HistogramMergeService`, `DashboardReadModelService`, `DashboardSnapshotRepository` 설명에 책임 경계를 추가합니다. 특히 `DashboardReadModelService`에 “UI 계산 금지 값을 조립한다”를 넣습니다.

`api-surface.md`  
ingest buckets endpoint와 dashboard endpoint는 현 상태를 유지합니다. Candidate 영역에 heartbeat를 넣는다면 “candidate control-plane endpoint, not metric ingest”라고 표시합니다. Dashboard response 설명에는 “state와 endpoint priority는 service read model에서 오며, p95/p99는 starter canonical percentile source를 따른다”를 추가합니다.

`read-model-contract.md`  
hourly application snapshots 설명에 project/application/environment/instance 식별자를 명확히 둡니다. p99는 starter canonical percentile source/scope를 보존하고, histogram-derived p99를 병렬로 만들지 않는다고 명시합니다.

`operational-event-history.md`  
이벤트와 snapshot detail이 endpoint evidence를 얼마나 보여주는지 “bounded evidence” 문구로 통일합니다. raw path/query string을 저장·노출하지 않는다는 제약을 반복합니다.

`ingest-envelope.md`  
`project key header`, `application`, `environment`, `instance`를 계층 설명으로 묶습니다. Endpoint histogram buckets는 “bucket distribution display source”라고 설명하고 endpoint p95/p99 payload 필드는 추가하지 않습니다.

`state-semantics.md`  
상태 기준은 “accepted bucket freshness”로 유지합니다. application 상태는 instance bucket freshness를 기반으로 서버가 판정하고, UI는 결과만 표시한다고 명시합니다.

`epics.md` / `sprint-status.yaml`  
Epic 4는 backlog 유지. Epic 4 앞에 “Story 4-0: documentation alignment gate”를 추가하고, Epic 2/3 완료 뒤 구현 전 정렬 작업으로 둡니다.

**Story 4-0 Draft**

Title: Documentation Alignment Gate for Epic 4

As a maintainer,  
I want the planning, contract, architecture, API, and UX documents aligned around the same ingestion/read-model boundaries,  
so that Epic 4 implementation starts from one consistent product and technical baseline.

Acceptance Criteria:

1. All relevant docs consistently describe the hierarchy as `project -> application -> environment -> instance`.
2. Docs state that starter sends histogram buckets and starter canonical p95/p99, while server builds read models without histogram-derived p95/p99.
3. Docs state that UI displays server read models only and does not recompute state, rules, p95, p99, or endpoint priority.
4. MVP exclusions are listed with identical wording across spec, UX, API, and contracts.
5. `api-surface.md` does not treat heartbeat as metric ingest or dashboard state source.
6. Epic 4 remains backlog after Epic 2/3 completion in `epics.md` and `sprint-status.yaml`.
7. Conflicting language listed above is removed or rewritten.
8. A reviewer can trace each screen field and API field back to contract or service ownership without ambiguity.

Definition of Done:

- Contract docs are updated first.
- Architecture and API docs reference contract ownership.
- UX spec reflects read-model-only presentation.
- Restart context pack no longer contradicts planning artifacts.
- Epic 4 has a clean documentation baseline before implementation stories begin.

### Mary - Business Analyst

📊 **Mary:**

가장 먼저 할 일은 **starter heartbeat API + ingest 계약 확정**입니다. 이유는 단순합니다. 이 제품의 약속은 “starter 붙이고 5분 안에 연결, 30~60초 안에 운영 첫 화면”인데, 사용자가 처음 막히는 지점은 대시보드가 아니라 **starter가 계속 살아 있는지, project key가 맞는지, 데이터가 들어오는지**입니다.

우선순위는 이렇게 보는 게 맞습니다.

1. **Starter heartbeat API**
   - 가치: 매우 높음
   - 위험: 낮음
   - 실행 중 “heartbeat 수신 / project key 유효 / 최근 ingest 수신 / 서버 시간 차이 / 권한 문제”를 바로 확인하게 해줍니다.
   - MVP 약속을 가장 직접적으로 강화합니다.

2. **Project → Application → Instance 탐색**
   - 가치: 높음
   - 위험: 중간
   - 운영자는 “어느 앱의 어느 인스턴스가 문제인가?”를 먼저 봅니다.
   - 단, UI는 read model만 표시해야 합니다. 상태, freshness, latency, error, resource hint, priority를 UI가 재계산하면 기준이 흔들립니다.

3. **Instance detail / snapshot**
   - 가치: 높음
   - 위험: 중간
   - 인스턴스 단위의 상태, 신선도, 지연, 오류, 리소스 힌트, 최근 snapshot은 운영 첫 화면의 핵심입니다.
   - 여기까지가 “첫 화면 포털”의 정체성과 잘 맞습니다.

4. **Persisted hourly snapshots + spike/error markers**
   - 가치: 중간~높음
   - 위험: 중간~높음
   - 이건 유용하지만 범위를 조심해야 합니다. “bounded read model history”로 제한해야지, 원시 time-series 제품처럼 보이면 MVP 경계가 무너집니다.
   - hourly snapshot은 “최근 운영 상태의 맥락”이지, Prometheus 대체재가 아닙니다.

p95/p99는 가장 위험한 부분입니다. **로컬 p95/p99 값을 받아서 app/project 레벨 p95/p99처럼 보여주면 통계적으로 틀린 숫자를 권위 있게 보여주는 제품 리스크**가 생깁니다. 이 리스크 이름은 명확히 말하면 **misleading percentile aggregation risk**, 즉 “잘못 합산된 백분위 지표를 운영 판단 근거로 제공하는 위험”입니다. 장애 판단, SLO 판단, 병목 판단을 틀리게 만들 수 있습니다.

제 추천은:

- **A: p95/p99 값만 ingest**는 탈락. 인스턴스 참고값으로도 제한적으로만 쓸 수 있고, app/project rollup에는 절대 쓰면 안 됩니다.
- **B: histogram bucket ingest**는 distribution display와 진단 원자료로 유지합니다.
- **C: histogram + starter canonical p95/p99 필드**를 채택하되, source/scope를 명확히 하고 여러 값의 평균/병합 rollup은 금지합니다.

따라서 첫 번째 실행 순서는 **heartbeat API를 만들면서 ingest schema를 B 또는 C로 확정**하는 것입니다. 그다음 project/application/instance read model을 잡고, 마지막에 hourly snapshot marker를 얹는 흐름이 가치 대비 리스크가 가장 좋습니다.

## Post-Roundtable Adjustment - Snapshot Tail Summary Follow-up Boundary

후속 논의에서 snapshot persistence의 p99 표현 원칙은 더 구체화됐지만, Story 4-0의 정렬 기준은 **상세 schema 확정이 아니라 후속 story가 지켜야 할 경계**로 낮춘다.

Boundary:

- 긴 기간 snapshot/history surface는 `hourP99`, `dayP99`, `14dP99` 같은 단일 대표 p99를 만들지 않는다.
- 후속 snapshot story는 저장 당시 read model, starter canonical percentile, bucket distribution evidence를 분리해 정의한다.
- 후속 `trendSlices`는 p95/p99 값을 만들지 않고 해당 subwindow의 bucket distribution으로 표시한다.
- 후속 `worstBuckets`는 전체 raw bucket list가 아니라 top-N representative bucket이어야 한다.
- 후속 `badBucketCount`는 rule/sample guard를 통과한 bounded count여야 한다.
- snapshot detail은 저장 당시 read model과 bounded tail evidence를 보여줄 뿐 current state를 재판정하지 않는다.
- 평균/중앙값/최솟값/최댓값은 `localPercentileSummary.p99.avgMs`처럼 “30초 bucket percentile 값들의 요약 통계”라는 맥락 안에서만 허용한다.

## Post-Roundtable Adjustment - Instance-Local Percentile Trend

후속 논의에서 instance detail UX의 p95/p99 요구가 더 구체화됐다.

개별 instance에 대해서는 starter가 각 30초 bucket에서 직접 관측한 p95/p99를 payload에 포함한다. 이 값은 해당 `instance_bucket` scope의 **starter canonical percentile**이다. app/project/window rollup은 여러 값을 평균/병합해 만들지 않고, 사용자는 instance detail에서 source/scope가 붙은 starter-reported p95/p99 값을 볼 수 있다.

핵심 정렬은 “local percentile을 숨긴다”가 아니라 “정확한 뜻으로 이름 붙여 보여준다”다. n분 summary는 n분 전체 요청을 다시 계산한 p95/p99가 아니라, n분 동안 존재한 30초 bucket p95/p99 point들의 평균, 중앙값, 최솟값, 최댓값이다.

권장 ingest 후보:

```json
{
  "localPercentiles": {
    "scope": "instance_bucket",
    "source": "starter_local",
    "bucketStartUtc": "2026-05-22T10:12:00Z",
    "bucketEndUtc": "2026-05-22T10:12:30Z",
    "requestCount": 820,
    "p95Ms": 640,
    "p99Ms": 1800,
    "mergeable": false
  }
}
```

Instance detail headline 후보:

```json
{
  "localPercentileSummary": {
    "scope": "instance",
    "source": "starter_local_instance_bucket",
    "window": "current_15m",
    "bucketDurationSeconds": 30,
    "bucketCount": 30,
    "aggregation": "summary_statistics_over_30s_bucket_percentile_values",
    "p95": {
      "avgMs": 540,
      "medianMs": 510,
      "minMs": 320,
      "maxMs": 890
    },
    "p99": {
      "avgMs": 1180,
      "medianMs": 970,
      "minMs": 620,
      "maxMs": 2100
    }
  }
}
```

Boundary:

- `localPercentiles.p95Ms`와 `localPercentiles.p99Ms`는 해당 instance의 해당 30초 bucket에 대한 starter canonical value다.
- 평균 percentile field는 canonical n분 p95/p99로 만들지 않는다. current/baseline/snapshot window 안에서는 source-scoped point series나 bucket distribution을 우선한다.
- `medianMs`, `minMs`, `maxMs`도 canonical p95/p99처럼 보이면 피하고, 필요하면 source-scoped point series의 보조 설명으로만 둔다.
- 평균 percentile field를 도입하려면 별도 필드와 라벨로 명시하고, “15분 p95/p99”처럼 부르지 않는다.
- instance detail은 source-scoped point series를 크게 보여줄 수 있지만 label은 “starter-reported p95/p99”, “instance 30초 bucket p99”처럼 의미가 분명해야 한다.
- 피해야 할 라벨은 “15분 p99”, “current p99”, “canonical p99”, “application p99”, “서비스 p99”다.
- app/project p95/p99는 starter canonical percentile 정책을 따른다. Histogram bucket은 distribution display와 진단 원자료다.
- UI는 trend/summary를 표시할 뿐 app/project rollup이나 rule 판단을 직접 계산하지 않는다.

## Orchestrator Synthesis

### 합의점

1. 세 사용자 흐름은 제품 방향과 맞다. 단, 같은 story에 모두 구현하면 범위가 커진다.
2. starter heartbeat는 ingest bucket endpoint와 분리해야 한다. 이 API는 “앱 건강”이 아니라 “starter heartbeat/수집 연결 상태”로 표현한다.
3. project -> application -> instance는 기존 first-screen UX와 충돌하지 않는다. 기존 application-centered first screen을 더 명확한 탐색 구조로 감싸는 것이다.
4. instance 화면은 application 판단을 대체하지 않고 evidence drill-down으로 둔다.
5. histogram bucket은 계속 유지한다.
6. 사용자 제시 옵션 기준으로는 **histogram 유지 + starter canonical percentile 표시**가 권장된다.
7. starter ingest schema에는 app/project/window rollup source-of-truth 필드로 `instanceP95`/`instanceP99`를 추가하지 않는다. 대신 `localPercentiles` 같은 instance-local 30초 bucket starter canonical percentile field는 명시적으로 허용한다.
8. p99는 starter canonical percentile source/scope를 보존해서 표시한다. Instance detail에서는 starter-reported bucket p99 point를 구체적인 값으로 강조할 수 있고, 상위 scope에서는 임의 병합하지 않는다.
9. snapshot은 저장 당시 read model의 coarse-grained history다. raw time-series, raw snapshot explorer, 30초 dashboard snapshot 장기 보관으로 확장하지 않는다.
10. snapshot에서 p99를 다룰 때는 단일 long-window 대표값을 만들지 않고 starter canonical percentile source/scope 또는 bucket distribution marker로 제한한다.
11. 개별 instance detail은 30초 bucket p95/p99 point series를 current/baseline/snapshot window별로 보여줄 수 있다.

### 이견

1. Paige는 별도 connectivity API를 MVP에 넣는 것에 보수적이었다. 하지만 John, Sally, Winston, Amelia, Mary는 heartbeat가 설치/운영 연결 상태 확인에 직접 기여한다고 판단했다.
2. Sally와 Amelia가 응답에서 옵션 라벨을 다르게 사용했다. 당시 둘 다 app/project rollup에는 bucket 병합이 필요하다고 보았지만, 이 판단은 최신 기준으로 대체된다. 현재 기준은 bucket distribution merge만 허용하고 p95/p99는 starter canonical percentile source/scope를 보존한다.
3. 초기 roundtable은 starter-local p95/p99 payload에 보수적이었지만, 후속 논의에서 개별 instance tail latency UX를 위해 `localPercentiles`를 명시적으로 사용하는 쪽으로 조정했다.

### 결정 필요사항

1. heartbeat endpoint 이름: `POST /api/ingest/v1/heartbeat`를 권장하되, UX 문구는 “starter heartbeat/수집 연결 상태”로 둔다.
2. heartbeat가 application/instance catalog를 upsert할지 여부: 권장 결정은 **upsert하지 않음**이다. 첫 accepted bucket이 계속 catalog upsert source다.
3. heartbeat 결과를 portal에 저장할지 여부: MVP에서는 별도 lightweight telemetry로 `lastHeartbeatAt`, `lastHeartbeatStatus`, failure category만 검토하고, state/read-model source로 사용하지 않는다.
4. instance list/detail read model을 Epic 5 안에 둘지, Epic 6 UI delivery 쪽에 둘지 결정해야 한다.
5. snapshot tail summary의 `sliceDurationSeconds`, `worstBuckets` top-N, `badBucketCount` 기준, marker label을 계약에 명시해야 한다.
6. `localPercentileSummary.avgMs`는 기본 산술 평균으로 둔다. request-count weighted average가 필요하면 후속 별도 필드로 추가하되, 어떤 경우에도 “n분 p95/p99”라고 부르지 않는다.
7. Story 4-0을 sprint-status에 backlog로 추가할지, 별도 planning artifact로 먼저 둘지 결정해야 한다.

## Recommended Direction

1. Story 4-0을 Epic 4 선행 gate로 연다.
2. Story 4-0 범위는 starter heartbeat API + ingest percentile contract 재확정 + 관련 문서 정렬 gate로 제한한다.
3. API는 `POST /api/ingest/v1/heartbeat`로 별도 정의한다.
4. heartbeat는 주기적으로 project key, portal reachability, schema version, application/environment/instance metadata shape, starter liveness를 알린다.
5. heartbeat는 accepted metric bucket, dashboard snapshot, operational event, p95/state/rule/read-model 계산을 생성하지 않는다.
6. first accepted ingest가 application/instance catalog upsert source라는 현 계약을 유지한다.
7. ingest schema에는 app/project/window rollup source로 쓰일 percentile value 필드를 추가하지 않는다. 다만 instance-local bucket starter canonical percentile로 `localPercentiles`를 추가한다.
8. `HistogramMergeService`가 존재하더라도 responsibility는 bucket distribution merge이며, p95/p99는 starter canonical percentile만 사용한다.
9. project/application/instance navigation은 first-screen UX를 구체화하되, application dashboard를 첫 판단 화면으로 유지한다.
10. snapshot history는 후속 story에서 marker와 bounded distribution summary 중심으로 표현한다. Story 4-0에서는 긴 시간 범위 단일 p99 금지와 starter canonical percentile/source-scope 원칙만 고정한다.
11. instance detail dashboard는 starter-reported 30초 bucket p95/p99 point series를 headline으로 보여준다.

## Answers To Required Questions

1. **세 흐름에 제품적으로 동의하는가?**  
   동의한다. heartbeat는 starter 연결/liveness 확인, project/application/instance는 운영 탐색, snapshot marker는 늦게 들어온 사용자의 맥락 회복을 강화한다.

2. **project -> application -> instance 흐름이 기존 first-screen UX와 충돌하는가?**  
   충돌하지 않는다. 기존 UX의 “Application Home”을 project 범위 안의 application first screen으로 재정의하고, instance는 evidence drill-down으로 둔다.

3. **instance별 p95/p99를 ingest schema에 추가하는 것이 맞는가?**  
   app/project/window rollup source-of-truth 필드로는 추가하지 않는다. 그러나 개별 instance의 30초 bucket starter canonical percentile로 `localPercentiles.p95Ms`/`p99Ms`를 추가하는 것은 권장한다.

4. **histogram을 계속 유지해야 하는가? p95/p99는 어떤 역할인가?**  
   유지해야 한다. Histogram은 distribution display와 진단 원자료다. p95/p99는 starter canonical percentile이며, 여러 point를 평균/병합해 새 p95/p99를 만들지 않는다.

5. **시간 단위 snapshot persistence에서 p99/p95를 어떻게 표현해야 하는가?**  
   snapshot detail에는 저장 당시 read model 값, starter canonical percentile source/scope, bucket distribution summary를 표현한다. 긴 기간 summary에는 단일 p99를 대표값처럼 노출하지 않고 `trendSlices`, `worstBuckets`, `badBucketCount`, marker를 우선한다.

6. **starter heartbeat API는 어떤 story로 분리해야 하는가?**  
   Story 4-0으로 분리한다. heartbeat API와 ingest percentile contract gate를 한 story로 두되, project/application/instance navigation과 snapshot UI는 후속 story로 뺀다.

7. **이 변경을 Story 4-0 같은 별도 starter-side story로 여는 것이 맞는가?**  
   맞다. Epic 4 state semantics 전에 heartbeat와 percentile source-of-truth를 잠가야 후속 state/read model 구현이 흔들리지 않는다.

8. **어떤 문서를 어떤 순서로 정렬해야 하는가?**  
   `ingest-envelope.md`와 `histogram-merge.md`를 먼저 닫고, `state-semantics.md`, `read-model-contract.md`, `operational-event-history.md`, `architecture.md`, `api-surface.md`, `ux-design-specification.md`, `observability_toy_spec_v0.8.md`, `epics.md`/`sprint-status.yaml` 순서로 정렬한다.

## Document Alignment Plan

1. **Contract baseline first**
   - `ingest-envelope.md`: heartbeat와 bucket ingest의 경계를 추가하고 `localPercentiles`의 starter canonical percentile/source-scope 의미를 명시한다.
   - `histogram-merge.md`: bucket distribution display source와 starter canonical percentile source를 분리한다.
   - `state-semantics.md`: application state와 instance evidence의 관계를 명시한다.
   - `read-model-contract.md`: project/application/instance navigation이 소비할 server-computed read model boundary와 후속 snapshot bounded tail evidence 원칙을 추가한다.
   - `operational-event-history.md`: snapshot marker, bounded tail summary, p99 장기 노출 금지를 정렬한다.

2. **Architecture/API second**
   - `architecture.md`: `StarterHeartbeatService`, portal `IngestHeartbeatController`, `IngestHeartbeatService` 후보를 MVC 경계에 추가한다.
   - `api-surface.md`: `POST /api/ingest/v1/heartbeat`를 ingest write path와 분리된 periodic control-plane endpoint로 추가한다.

3. **UX/spec third**
   - `ux-design-specification.md`: project -> application -> instance navigation을 screen map에 반영하되 application first-screen contract를 유지한다.
   - `observability_toy_spec_v0.8.md`: starter-first promise에 “heartbeat/연결 상태” 단계를 추가하고, p95/p99 역할을 current contracts와 맞춘다.

4. **Planning tracking last**
   - `epics.md`: Epic 4 앞에 Story 4-0을 추가하거나 Epic 4 첫 story로 삽입한다.
   - `sprint-status.yaml`: 사용자가 승인하면 `4-0-starter-heartbeat-and-instance-level-ingest-contract-reassessment: backlog`를 추가한다.

## Document-Specific Revision Points

### `bmad-restart-context-pack/ux-design-specification.md`

- Screen Map을 `Project Selector -> Application Home -> Instance Evidence Detail` 흐름으로 보강한다.
- First-Screen Contract는 application-centered로 유지한다.
- Starter Setup / First Data Prep에 “starter heartbeat/수집 연결 상태”를 추가한다.
- “project health context”처럼 app health로 읽힐 수 있는 표현은 “project scope/connection context” 또는 “project key/heartbeat context”로 바꾼다.
- Instance section은 앱 판단의 evidence drill-down이라고 명시한다.
- Instance detail에서는 starter-reported 30초 bucket p95/p99 point series를 큰 headline card로 표시하되, source/scope를 라벨에 드러낸다.
- App/project dashboard에서는 여러 starter p99를 임의 병합하지 않고 source별 값 또는 bucket distribution으로 표시한다.
- Snapshot/spike marker는 “저장된 read model 기준”으로 클릭/상세를 연다.
- Snapshot detail은 후속 story에서 다루며, 이 draft에서는 단일 long-window p99 금지와 bounded evidence 원칙까지만 정렬한다.

### `bmad-restart-context-pack/observability_toy_spec_v0.8.md`

- 제품 약속에 “starter가 주기적 heartbeat로 portal 연결/키/liveness를 알릴 수 있다”를 추가한다.
- 9.3 p95/p99 책임에 canonical percentile은 starter-reported `localPercentiles`에서 오고, histogram bucket은 distribution display source라고 추가한다.
- 9.7 최소 사용자 설정에 heartbeat가 검증/전송하는 `portal base url`, `project key`, `environment`, `application`, `instance`를 연결한다.
- 성공 기준에 “heartbeat 수신과 실제 accepted bucket 수신을 구분한다”를 추가한다.
- 리스크 4에 “percentile value rollup 오도 위험”을 추가한다.

### `planning-artifacts/architecture.md`

- Starter services에 `StarterHeartbeatService` 후보를 추가한다.
- Portal controllers에 `IngestHeartbeatController`를 추가한다.
- Portal services에 `IngestHeartbeatService`를 추가하고 `ProjectKeyVerificationService`와 metadata validator를 재사용한다고 명시한다.
- Ingest flow와 별도 “Heartbeat Flow”를 추가한다.
- Dashboard/read flow에 instance read model은 service-computed 결과만 제공한다고 명시한다.
- Traditional MVC + Service/Repository boundary와 UI 재계산 금지를 반복한다.

### `planning-artifacts/api-surface.md`

- Ingest API 섹션 뒤에 `POST /api/ingest/v1/heartbeat`를 추가한다.
- Header는 `X-OBS-Project-Key`, body는 `schemaVersion`, `starterVersion`, `heartbeat.sentAtUtc`, `heartbeat.sequence`, `heartbeat.intervalSeconds`, `application.name`, `environment`, `instance` 정도로 제한한다.
- 200 응답은 `status`, `projectId`, `serverTimeUtc`, `supportedSchemaVersions`, `metadataStatus`, `heartbeatStatus`를 포함한다. accepted bucket 참고 시각이 필요하면 heartbeat 최상위가 아니라 `ingestBoundary.lastAcceptedBucketAt`처럼 별도 namespace에 둔다.
- 400/401/403/500 status mapping을 추가한다.
- heartbeat는 bucket/snapshot/operational event/catalog upsert를 만들지 않는다고 명시한다.
- heartbeat telemetry를 저장하더라도 dashboard operational state/freshness source로 쓰지 않는다고 명시한다.
- Instance list/detail API는 후속 candidate로만 둔다.

### `planning-artifacts/contracts/read-model-contract.md`

- `scope` 개념에 application과 instance detail의 관계를 추가한다.
- instance read model은 starter canonical p95/p99 point series와 histogram bucket distribution을 구분해서 담을 수 있다고 명시한다.
- Snapshot detail의 p95/p99가 “stored window read model value”이지 long-range recomputation이 아니라고 명시한다.
- 긴 기간 summary에서 p99 단일값을 만들지 않는 UX/API 경계를 추가한다.
- Snapshot `read_model_json` 또는 bounded evidence 후보는 후속 story에서 확정하고, 이 정렬에서는 starter canonical percentile과 bucket distribution evidence를 분리해야 한다는 원칙만 추가한다.

### `planning-artifacts/contracts/state-semantics.md`

- application state는 instance buckets의 freshness/sample/concern을 service layer가 평가해 만든다고 설명한다.
- instance 상태는 application state를 대체하지 않고 evidence/detail scope로 둔다.
- heartbeat 성공은 accepted bucket이 아니므로 `waiting_first_data`, `stale`, `down` 판단을 직접 바꾸지 않는다고 명시한다.
- heartbeat 미수신도 host application down 판정이 아니며 starter connection 상태로만 표현한다고 명시한다.

### `planning-artifacts/contracts/operational-event-history.md`

- snapshot marker 용어를 명확히 한다: state change, high-confidence concern, short strong spike, stale/down/recovery.
- p99는 snapshot detail에서 starter canonical percentile source/scope를 보존하고, long-range p99 rollup을 금지한다.
- “worst bucket”, “worst slice”, “bad bucket count”는 raw retention 범위에서 계산하거나 snapshot에 저장된 bounded summary에서만 표현한다고 제한한다.
- `trendSlices`는 percentile 값을 새로 만들지 않고 subwindow bucket distribution이라고 명시한다.
- Instance marker가 필요하면 application snapshot 안의 bounded instance evidence 또는 후속 instance snapshot contract로 분리한다.

### `planning-artifacts/contracts/ingest-envelope.md`

- `POST /api/ingest/v1/buckets`와 heartbeat endpoint를 분리한다.
- `schemaVersion: "1.0"` 또는 후속 schema에 `localPercentiles` 후보를 추가하되, scope는 `instance_bucket`, source는 `starter_local`, `mergeable=false`로 고정하고 source-scoped point series로 표시한다고 명시한다.
- application/environment/instance metadata는 bucket ingest와 heartbeat가 같은 validation vocabulary를 쓰되, heartbeat는 catalog/read-model persistence source가 아니라고 명시한다.
- Unknown field handling에서 지원되지 않는 percentile field는 semantic acceptance나 persisted metric input이 아니라고 확인한다.

### `planning-artifacts/epics.md`

- Epic 4 stories 앞에 Story 4-0을 추가한다.
- Story 4-0의 목적은 starter heartbeat API와 percentile contract gate라고 명시한다.
- Epic 5의 Histogram merge service에 canonical percentile 계산을 추가하고, read model story에 instance-local bucket percentile avg/median/min/max summary를 추가한다.
- Epic 6의 UI integration에 project -> application -> instance navigation, snapshot markers, bounded tail summary display를 후속으로 배치한다.

### `implementation-artifacts/sprint-status.yaml` 또는 후속 planning artifact

- draft 단계에서는 sprint-status를 바로 바꾸지 않는다.
- 승인 후 `development_status`의 Epic 4 아래에 `4-0-starter-heartbeat-and-instance-level-ingest-contract-reassessment: backlog`를 삽입한다.
- 또는 이 파일처럼 후속 planning artifact로 먼저 검토하고, 문서 정렬 승인 후 sprint-status를 갱신한다.
