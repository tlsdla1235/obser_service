# 플랜: Instance Summary "Open modal"이 빈 정보만 보여주는 버그

> 새 컨텍스트(플랜모드)용 hand-off. 코드 수정 전에 조사부터 하고, 가설을 확정한 뒤 플랜을 제시·승인받고 구현한다.

## 증상
Application Dashboard의 Instance summary 패널에서 instance 행의 "Open modal"을 클릭하면
wide modal은 열리지만 metric/evidence가 전부 비어 있음:
- INSTANCE REQUESTS 0, SERVER ERRORS 0, SLOW>500ms "확인할 수 없음",
  OBSERVATION "window 안에서 관찰되지 않음 / latest metric bucket outside selected window".
- modal 상단 badge가 MODE=LIVE이고 window가 "현재 시각 기준 recent_30_minutes"임.
- 재현 instance: `ecc-endpoint-smoke-local-1` (instanceId `e75c2604-f09b-4ec0-8e94-23b7d0af5fd5`),
  app `ecc-endpoint-smoke-service`. 이 instance는 06/11 smoke 테스트라 현재 30분 window엔 metric이 없음.

사용자는 "복원된 snapshot에서도, 아마 live 모드에서도 똑같이 빈 정보가 나온다"고 함.

## 직전 컨텍스트 (이미 머지된 작업)
- branch `codex/user-friendly-ui-bugfixes`, commit `4202f32`에서 "snapshot mode = live surface 재사용"을 구현함.
- snapshot slot 클릭 시 `dashboard.tsx`의 `SnapshotModeSurface`가 저장된 full read model을 받아
  공유 컴포넌트 `DashboardPanels`(→ `InstancesPanel`)로 렌더함.
- 이때 `InstancesPanel`에는 `onOpenEvidence`로 **live** 핸들러(`useInstanceView().openEvidence`)가 그대로 전달됨.

## 가설 (조사로 확인할 것)
1. **snapshot mode인데 live evidence를 연다(주요 의심).** restored snapshot(06/11)에서 Open modal을 누르면
   live instance dashboard(현재 window)를 조회 → 그 instance는 지금 관찰 안 됨 → 전부 0/not-observed.
   snapshot mode에서는 `openSnapshotDashboard`(mode=snapshot, snapshot window/snapshotId anchor)로
   열어 저장 시점 window 기준 evidence를 재구성해야 함.
2. **live mode의 "not observed in window"는 사실 정상 동작일 수 있음** — 그 instance가 현재 window에
   metric이 없으면 0/not-observed가 맞는 표현. 이 경우는 버그가 아니라 (a) seed/data가 오래됨,
   또는 (b) empty-state copy가 "정보 없음"처럼 보여 오해를 주는 것. 진짜 버그(쿼리/조인/read model 누락)인지
   data 문제인지 구분해야 함.

## 조사 포인트 (read-only)
### 프론트
- `frontend/src/app/components/instance-panels.tsx` — `useInstanceView` (`openEvidence` = live-dashboard,
  `openSnapshotDashboard` = snapshot-dashboard), modal 렌더(`InstanceDashboardSurface mode="live"|"snapshot"`).
- `frontend/src/app/components/instance-dashboard-surface.tsx` — mode별 path
  (`buildLiveInstanceDashboardPath` vs `buildSnapshotInstanceDashboardPath`), `SnapshotInstanceDashboardTarget`
  (snapshotId/window anchor 필요), empty/not-observed 렌더.
- `frontend/src/app/components/dashboard.tsx` — `InstancesPanel`의 Open modal이 만드는 target
  (`{ applicationId, evidenceLink, instanceId, instanceName, projectId }`), `SnapshotModeSurface`가
  `onOpenEvidence`를 어떻게 넘기는지. snapshot mode에서 snapshot-aware 핸들러로 바꿔야 하는지 판단.
- `frontend/src/app/lib/read-model-adapters.ts`의 `buildLiveInstanceDashboardPath` /
  `buildSnapshotInstanceDashboardPath`, `validateSnapshotInstanceDashboardPath`.

### 백엔드
- `observability-portal/.../instance/controller/InstanceDashboardController.java`
  (live: `/instances/{instanceId}/dashboard`, 그리고 snapshot 변형 @GetMapping).
- `observability-portal/.../instance/service/InstanceDashboardReadModelService.java` — live vs snapshot
  instance evidence를 어떤 window/cutoff/source로 조회하는지. snapshot은
  selected snapshot row window + `accepted_metric_buckets`로 재구성한다고 read-model-contract/SoT에 적혀 있음.
- 실제 DB 확인: 이 instance가 06/11 snapshot window에 accepted bucket이 있는지
  (`docker exec gwanp-portal-postgres-1 psql -U portal -d portal`). 있으면 snapshot-mode 조회로 데이터가 나와야 함.

## snapshot mode wiring 시 주의
- 과거 snapshot은 `InstanceEntry.summary`(D5) 이전이라 summary가 없을 수 있음(직전 작업의 D6 결정).
  이 경우 snapshot instance evidence anchor(snapshotId/window)를 어디서 얻을지 확인 필요.
  snapshot read model의 window 또는 `SnapshotModeSurface`가 들고 있는 snapshot provenance를 사용.
- live mode wiring은 건드리지 말고(정상일 수 있음), snapshot mode에서만 snapshot evidence로 열도록 하는 게
  최소 변경일 가능성이 높음. 단, 가설2(live가 진짜 버그)면 별도 처리.

## 검증
- OAuth2가 8080으로 redirect하므로 포트 8080 권장: `./gradlew :observability-portal:bootRun`
  (postgres는 docker `gwanp-portal-postgres-1`가 15432에서 이미 떠 있음).
- 로그인 필요하면 브라우저 띄우고 사용자에게 요청.
- 재현: 06/11 22:00 "scheduled" snapshot 복원 → Instance summary → Open modal.
  snapshot window 기준으로 evidence가 채워지는지 확인. live mode도 별도로 확인.
- `npm run typecheck` + `npm run guard:read-model-contract` + 관련 백엔드 테스트 green 유지.

## 수용 기준
- restored snapshot에서 Open modal이 그 instance의 **저장 시점 window** evidence를 보여줌(mode=snapshot 표시).
- live mode에서 "관찰되지 않음"이 진짜 정상 동작이면 그대로 두되, 오해를 주는 빈 화면이면 명확한 empty-state copy로 개선.
- 계약/문서(`read-model-contract`, `dashboard-snapshot` SoT, `epic-13`)와의 정합성 확인 후 필요한 doc만 갱신.

---
먼저 조사해서 가설1/2 중 무엇인지 확정하고, 플랜을 제시한 뒤 승인받고 구현한다.

## 후속 QA 분리

Endpoint evidence 랭킹/강조 UI 개선은 이 버그 플랜에서 분리했다.
별도 문서: `implementation-artifacts/qa-endpoint-evidence-ranking-ui.md`
