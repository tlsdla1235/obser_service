# UI/UX QA 체크리스트

> 범위: **순수 UI/UX만** (백엔드/read-model 계산 로직은 건드리지 않음). 카피 정리, 불필요 정보 제거, 색 강조, 일관성.
> 기준 데이터: GitHub 로그인 계정 + `final-test` 프로젝트 / `ecc-endpoint-smoke-service` (`http://127.0.0.1:8080/dashboard`).
> 확인 방식: Playwright로 직접 화면 확인(로그인은 사용자 직접).

상태 범례: `[ ]` 미착수 · `[~]` 진행중 · `[x]` 완료

---

## 1. 라이브 대시보드 헤더 — 백엔드 정보 과다
- [ ] **#1 헤더 설명문 제거/축약** — "Server read model을 표시합니다. UI는 lifecycle state, endpoint priority, resource pattern을 재계산하지 않습니다." 삭제 또는 한 줄 사용자 문구로 축약
- [ ] **#2 메타 칩/그리드 축소** — 칩(`MODE=LIVE`, `RECENT_30_MINUTES`, `ACCEPTED_METRIC_BUCKETS`, `BASELINE NOT USED`)과 8칸 그리드(mode/window/source/baseline/project·application/environment/generated/bucket boundary)에서 **꼭 필요한 것만** 남기기 → 남길 것: ① 라이브 모드 표시 ② 생성 시각(글자 잘림 `2026...` 수정) ③ 기준 시간 30분
- 위치: `frontend/src/app/components/dashboard.tsx` `DashboardContext` (L826–870), `InfoCell` 그리드

## 2. Lifecycle / StarterConnection 카드 — 백엔드 용어 helper text 제거
- [ ] **#3** StarterConnection 우측 "heartbeat는 accepted bucket freshness나 application lifecycle state를 직접 만들지 않습니다." + "starter disconnected · 앱 연결 신호" 제거
- 위치: `dashboard.tsx` `StarterConnectionStrip` (L1069–1098, 특히 L1090–1095)

## 3. First look candidates — 문제 강조 색 없음
- [ ] **#4** 사용자가 문제를 바로 인지하도록 후보 카드에 **은은한 색 강조**(배경과 조화) 추가. 심각도/타입 기반.
- 위치: `dashboard.tsx` `FirstLookCandidatesPanel` (L1039–1066)

## 4. Endpoint evidence — 색 강조 + duration buckets 히스토그램 개선
- [ ] **#5a** 행/배지에 은은한 색 강조(에러/슬로우 상태 기반, 배경 조화)
- [ ] **#5b** "duration buckets · accepted bucket 분포" 히스토그램 개선 — 빈 회색 박스 모양 정리 + hover 툴팁(`<= 50ms` 식) 사용자 친화적으로
- 위치: `dashboard.tsx` `EndpointPriorityRow` (L1301–1359), `EndpointBucketStrip` (L1386–1411)

## 5. Resource evidence / Instance summary — 설명문 개발자 친화 → 사용자 친화
- [ ] **#6a** Resource evidence: "root cause 확정이 아니라 server read model의 USE hint를 표시합니다." → 1~2줄 사용자 문구
- [ ] **#6b** Instance summary: "Application 판단을 대체하지 않고 selected instance evidence를 wide modal로 확인합니다." → 1~2줄 사용자 문구
- 위치: `dashboard.tsx` `ResourceSignalsPanel` (L1120), `InstancesPanel` (L1894)

## 6. Snapshot history — "하루 48개 SLOT" 박스 제거
- [ ] **#7** 불필요한 `하루 48개 SLOT` 배지/박스 삭제
- 위치: `frontend/src/app/components/snapshot-history-panel.tsx` (L246, 관련 문구 L205)

## 7. 스냅샷 모드 대시보드 — 라이브와 일관성
- [ ] **#8** 스냅샷 모드도 #1~#6와 동일하게 정리(라이브 컴포넌트 최대 재활용). 헤더 설명문/메타 그리드 축소 등.
- 위치: `dashboard.tsx` `DashboardContext` snapshot 분기(L844–846 등), `snapshot-detail-surface.tsx` (L162/178/208/221)

## 8. 인스턴스 대시보드 — 정보 과다 + 상태 색 강조
- [ ] **#9a** Context note / Application state reference / Read semantics 의 개발자용 정보·문구 정리(사용자 친화)
- [ ] **#9b** `applicationStateCode`(예: "서비스 성능 저하")에 **상태별 색 강조** 적용 (정상/주의/지연/저하/끊김 등)
- 위치: `instance-dashboard-surface.tsx` `InstanceContextNote` (L179–217), `ApplicationStateReferencePanel` (L220–239), `ReadSemanticsPanel` (L242–263)

## 9. Normalized endpoint evidence table — 과다 설명문 제거
- [ ] **#10** 다음 문구 삭제:
  - "selected instance의 normalized route evidence를 탐색합니다. application state나 endpoint priority를 새로 판정하지 않습니다." (L295)
  - "source: accepted_metric_buckets.endpoints_json · raw path/query/per-request sample 없음 · endpoint p95/p99 rollup 없음" (L329)
  - "정렬: requestCount desc · server 제공 순서가 이미 requestCount desc 결과와 같습니다." (L535 생성)
- 위치: `instance-dashboard-surface.tsx` `EndpointEvidencePanel` (L289–)

---

## 진행 상태 (2026-06-16)

모든 항목 코드 반영 완료. `npm run typecheck` · `guard:read-model-contract` · `npm run build` 3중 통과.

- [x] #1 헤더 설명문 제거 — `DashboardContext`
- [x] #2 메타 그리드 축소(모드/기준시간 배지 + 생성시각 1줄), generated 잘림 해소
- [x] #3 StarterConnection 백엔드 helper text 제거
- [x] #4 First look candidates reasonCode 기반 은은한 색 강조 (`evidenceAccentClassName`)
- [x] #5a Endpoint evidence 행 오류/지연 좌측 보더 강조 (`endpointRowAccentClassName`)
- [x] #5b duration buckets: 표본 0이면 안내 문구, 툴팁 "≤100ms 이내 · N건"으로 (`formatLatencyBound`)
- [x] #6a/#6b Resource evidence·Instance summary 설명문 사용자 친화화
- [x] #7 "하루 48개 SLOT" 배지 + 미사용 StatusBadge 제거
- [x] #8 스냅샷 모드: 공통 컴포넌트 재사용으로 자동 일관화 + 배너 문구 정리
- [x] #9a 인스턴스 대시보드 Context note/State ref/Read semantics 사용자 친화 단순화
- [x] #9b `applicationStateCode` 상태별 색 (`metricStateClassName` 재사용)
- [x] #10 Normalized endpoint evidence table 과다 설명문 3종 제거 + 동일 성격 메타 그리드 제거

### ⚠️ 계약 가드 관련 결정 (중요)
`read-model-contract-guard`는 인스턴스 대시보드 소스에 특정 면책 문구·라벨(`Application Snapshot 자체는 dashboard_snapshots.read_model_json...`, `InfoCell label="mode"/"source"`, `instance top-level state="없음"`, `NORMALIZED ENDPOINT EVIDENCE TABLE`)이 **존재할 것**을 strict-parity 계약으로 강제함.
→ **결정**: 기본 화면은 단순화하되, 계약 문구·원본 값은 접이식 `InstanceReadModelDetails`(기본 접힘) 섹션으로 이동해 보존. 가드 통과 + UX 정리 둘 다 충족. 엔드포인트 테이블 제목은 원복(다른 ALLCAPS 섹션 라벨과 일관).

### 시각 검증 (완료)
- [x] 프론트 재빌드 + 백엔드(`:8080` bootRun) 재시작 후 라이브 대시보드 Playwright/육안 확인 — #1~#7 반영 확인.
- [x] 사용자 육안 검토 OK ("그냥 이대로 써도 될듯").
- 참고: 백엔드 재시작 시 서버 인메모리 세션 초기화 → 기존 GitHub 로그인 세션 만료됨(코드 무관, 재로그인 필요).
