---
artifactType: source-of-truth
projectName: Observation Portal
status: active
date: 2026-06-11
dependsOn:
  - planning-artifacts/source-of-truth/current-product-source-of-truth.md
  - planning-artifacts/source-of-truth/dashboard-snapshot-mvp-source-of-truth.md
  - planning-artifacts/source-of-truth/application-dashboard-read-model-mvp-source-of-truth.md
  - planning-artifacts/source-of-truth/instance-dashboard-read-model-mvp-source-of-truth.md
  - planning-artifacts/contracts/read-model-contract.md
---

# Dashboard Snapshot Retention Cleanup Source of Truth

## 1. 문서 목적

이 문서는 Observation Portal MVP에서 dashboard snapshot과 metric bucket retention cleanup 정책을 고정한다.

목표는 retention horizon, source boundary, 삭제 기준 timestamp, 조회 동작, 구현 방향, 테스트 기대값을 한곳에 고정하는 것이다. Story 13.10 구현 이후에도 이 문서는 정책 기준으로 유지하며, 구현 상태 메모는 정책 의미를 바꾸지 않는다.

## 2. 한 줄 결정

MVP retention cleanup은 14일 주기로 실행하는 작업이 아니라, 매일 `Asia/Seoul` 기준 01:15에 cleanup job을 실행하고 실행 기준 시각에서 14일보다 오래된 snapshot/metric 기록을 UTC timestamp 비교로 삭제하는 정책이다.

## 3. Hard Contract

아래 항목은 MVP에서 흔들지 않는다.

1. Cleanup schedule은 매일 01:15 KST다.
2. Cutoff 계산과 DB timestamp 비교는 UTC 기준으로 수행한다.
3. 기본 retention horizon은 14일이다.
4. 삭제 대상에는 최소 `dashboard_snapshots`가 포함된다.
5. MVP 결정으로 `accepted_metric_buckets`도 같은 14일 사용자-facing retention horizon에 포함하고, physical cleanup에는 30분 evidence grace를 둔다.
6. Retention 밖 snapshot detail은 404 또는 expired copy로 수렴한다.
7. Retention 밖 snapshot은 marker/history/date map에 표시하지 않는다.
8. Cleanup으로 사라진 snapshot은 live dashboard 또는 current accepted bucket으로 대체 복원하지 않는다.
9. Cleanup job은 idempotent해야 하며, 실패해도 다음 실행에서 같은 cutoff 정책으로 재시도 가능해야 한다.
10. Production cleanup job 구현은 Story 13.10에서 수행됐으며, rollout enablement/dry-run은 cleanup schedule, cutoff 계산식, 삭제 기준 컬럼의 의미를 바꾸지 않는다.

## 4. Schedule and Cutoff

Cleanup trigger는 KST wall-clock 기준으로 잡는다.

```java
@Scheduled(cron = "0 15 1 * * *", zone = "Asia/Seoul")
```

Job이 실제로 실행되면 service는 `Clock`에서 현재 시각을 얻고 UTC로 정규화한다.

```text
runAtUtc = clock.instant()
snapshotCutoffUtc = runAtUtc - retentionDays
metricEvidenceCutoffUtc = snapshotCutoffUtc - dashboardWindowDuration(30 minutes)
```

DB 삭제 조건은 "cutoff보다 엄격히 오래된 row"다.

```text
delete when retention_column < effectiveCutoffUtc
keep when retention_column >= effectiveCutoffUtc
```

예를 들어 2026-06-09 01:15 KST는 2026-06-08 16:15 UTC다. 기본 14일 retention이면 snapshot cutoff는 2026-05-25 16:15 UTC다. 30분 dashboard window를 완전히 복원하기 위해 metric evidence cutoff는 2026-05-25 15:45 UTC가 된다.

01:15 KST는 30분 scheduled snapshot slot 경계인 `:00`/`:30`과 capture delay 이후 dispatch가 몰리는 `:02`/`:32` 근처를 피하기 위한 운영 시간이다. 이 선택은 cleanup cadence만 조정하며 retention horizon, cutoff 계산식, 삭제 기준 컬럼, source boundary는 바꾸지 않는다.

KST는 schedule을 사람이 예측하기 쉽게 만드는 기준일 뿐이다. DB에 저장된 `timestamptz`와 cleanup 비교는 UTC timestamp 의미로만 해석한다.

## 5. Source Boundary

Cleanup은 source의 의미를 바꾸지 않는다. 오래된 row를 지울 뿐, 남아 있는 row에서 다른 read model을 재계산하거나 대체 source를 만들지 않는다.

| Source | 역할 | Cleanup 정책 |
|---|---|---|
| `dashboard_snapshots` | 저장 당시 Application Dashboard read model의 복원 source | 14일 밖 삭제 |
| `dashboard_snapshots.read_model_json` | Application Snapshot Detail의 state/evidence source | row 삭제 후 복원 금지 |
| `accepted_metric_buckets` | live dashboard와 Instance Dashboard snapshot mode의 metric evidence source | 14일 horizon + 30분 evidence grace 밖 삭제 |
| starter heartbeat telemetry | control-plane/liveness 참고 정보 | 이 문서의 삭제 대상 아님 |
| catalog tables | project/application/instance identity source | 이 문서의 삭제 대상 아님 |

Application Snapshot Detail은 `dashboard_snapshots.read_model_json`만 source of truth로 둔다. Snapshot detail을 열 때 현재 accepted bucket, current threshold, marker bucket, heartbeat를 조합해 저장 당시 dashboard를 다시 만들지 않는다.

Instance Dashboard snapshot mode는 선택한 Application Snapshot row의 window를 기준으로 selected instance evidence를 `accepted_metric_buckets`에서 재구성한다. 이 계약 때문에 accepted bucket retention은 snapshot retention과 분리하면 UX 의미가 어긋난다.

## 6. Accepted Metric Bucket Retention 결정

기존 contract는 `accepted_metric_buckets`를 "짧은 retention" source로 표현한다. 이 문서는 그 의미를 "장기 analytics store가 아니다"로 유지하되, MVP의 사용자-facing retention horizon을 snapshot과 같은 14일로 결정한다.

### 6.1 Tradeoff

`accepted_metric_buckets`를 14일 보관하면 storage 비용과 cleanup 대상 row 수가 늘어난다. 특히 30초 bucket은 application/instance 수에 비례해 빠르게 증가한다.

반대로 accepted bucket retention이 snapshot retention보다 짧으면 아래 문제가 생긴다.

- Application Snapshot Detail은 저장 JSON으로 복원되지만, Instance Dashboard snapshot mode는 같은 snapshot window의 selected instance evidence를 재구성하지 못할 수 있다.
- 사용자가 14일 horizon 안의 snapshot을 열었는데 instance detail은 `metric_missing` 또는 `not_observed_in_window`로 떨어질 수 있다.
- Snapshot UI가 14일을 약속하는 동안 하위 evidence detail은 더 짧은 기간만 의미 있는 불균형이 생긴다.

### 6.2 MVP 결정

MVP에서는 `dashboard_snapshots`와 `accepted_metric_buckets`를 모두 기본 14일 retention horizon으로 맞춘다.

이 결정은 `accepted_metric_buckets`를 장기 time-series store로 승격하지 않는다. 14일은 snapshot UI와 instance evidence 재구성을 맞추기 위한 bounded operational retention이다. 14일을 넘는 trend, baseline, adaptive threshold, endpoint long-term analytics는 여전히 Post-MVP 범위다. 2026-06-11 UI MVP 결정에 따라 Instance Snapshot Trend/Stored trend surface도 현재 MVP 화면에서 제외하며, 과거 instance evidence는 Snapshot/History에서 snapshot을 선택한 뒤 snapshot-mode wide modal로 본다.

단, 30분 snapshot slot의 가장 오래된 retained point도 selected instance evidence를 온전히 재구성할 수 있어야 하므로 physical metric cleanup은 `snapshotCutoffUtc - 30 minutes`까지 30분 evidence grace를 둔다. 이 grace는 arbitrary metric explorer나 14일 밖 analytics를 허용하는 정책이 아니다.

후속에서 storage 비용이 문제가 되면 선택지는 둘 중 하나다.

1. Snapshot UI/Instance snapshot detail horizon을 accepted bucket retention에 맞춰 줄인다.
2. Instance snapshot detail을 current bucket 재구성이 아니라 stored `instanceSummary.items[]` 또는 snapshot-derived helper table 기반으로 바꾼다.

## 7. 삭제 기준 컬럼

### 7.1 `dashboard_snapshots`

MVP authoritative cleanup cutoff column은 `current_window_end_utc`다.

이유는 아래와 같다.

- UX가 고르는 단위는 저장 작업 시각이 아니라 30분 dashboard slot이다.
- `current_window_end_utc`는 writer duplicate identity와 marker slot의 기준이다.
- `generated_at`은 capture delay, retry, fallback으로 slot end보다 늦어질 수 있으므로 retention 경계에서 UX slot 의미를 흔들 수 있다.

따라서 cleanup delete 조건은 아래로 둔다.

```sql
delete from dashboard_snapshots
where current_window_end_utc < :snapshotCutoffUtc
```

`generated_at`은 capturedAt/provenance와 deterministic ordering 보조값으로 유지한다. Marker/history 조회와 보존된 trend read-model 조회가 후속에서 필요하면 retention horizon을 `current_window_end_utc` 기준으로 맞춘다.

### 7.2 `accepted_metric_buckets`

MVP authoritative cleanup cutoff column은 `bucket_end_utc`다.

이유는 아래와 같다.

- Live dashboard와 Instance Dashboard snapshot mode는 관측 window를 `bucket_end_utc`로 자른다.
- Retention은 "언제 수용했는가"보다 "어느 관측 window의 metric인가"를 기준으로 이해해야 한다.
- Late-arriving metric이 늦게 `accepted_at`을 받았더라도 관측 window가 14일 밖이면 retention 밖 metric이다.

따라서 cleanup delete 조건은 아래로 둔다.

```sql
delete from accepted_metric_buckets
where bucket_end_utc < :metricEvidenceCutoffUtc
```

여기서 `metricEvidenceCutoffUtc = snapshotCutoffUtc - 30 minutes`다. 이 30분 grace는 가장 오래된 retained snapshot slot의 `current_window_start_utc < bucket_end_utc <= current_window_end_utc` evidence를 보존하기 위한 것이다.

`accepted_at`은 snapshot capture 시 late metric cutoff, ingest ordering, audit/debug 의미에 가깝다. Cleanup 기준으로 쓰면 오래된 관측 window가 늦게 도착했다는 이유만으로 더 오래 남을 수 있으므로 MVP cleanup cutoff에는 사용하지 않는다.

## 8. Read Surface 동작

### 8.1 Snapshot Detail

Retention 밖 snapshot detail은 404 또는 expired copy로 수렴한다.

MVP 기본 API 동작은 404다. UI에서 더 친절한 안내가 필요하면 "보관 기간이 지나 만료된 snapshot입니다" 같은 expired copy를 표시할 수 있지만, 그 경우에도 live dashboard나 current accepted bucket으로 snapshot detail을 복원하지 않는다.

### 8.2 Marker/History/Date Map

Marker, history, date map은 retention 밖 snapshot을 표시하지 않는다.

Marker/history/date map의 retention horizon은 `current_window_end_utc >= snapshotCutoffUtc` 기준이다. Cleanup 구현 이후에도 marker/history service는 retention 밖 row가 남아 있더라도 표시하지 않는 guard를 유지해야 한다.

### 8.3 Instance Dashboard Snapshot Mode

14일 안의 Application Snapshot row가 있고 해당 window의 `accepted_metric_buckets`가 남아 있으면 selected instance evidence를 재구성할 수 있다. 기본 cleanup은 가장 오래된 retained snapshot의 30분 window까지 보존하도록 metric evidence grace를 둔다.

Bucket이 cleanup으로 삭제됐거나 window 안에 원래 bucket이 없으면 `metric_missing` 또는 `not_observed_in_window`와 data quality limitation으로 표현한다. 이 경우에도 Application Snapshot stored state/evidence를 다시 계산하거나 current metric으로 보정하지 않는다.

## 9. 현재 코드와의 정렬

현재 코드 기준으로 이미 정렬된 부분은 아래와 같다.

- `PortalApplication`은 `@EnableScheduling`을 켜고 있다.
- Story 13.10에서 `domain.cleanup.service` 아래 `RetentionCleanupProperties`, `RetentionCleanupService`, `RetentionCleanupScheduler`, `RetentionCleanupResult`를 추가했다.
- `RetentionCleanupScheduler`는 매일 `0 15 1 * * *` / `Asia/Seoul` trigger를 사용하되, physical delete rollout은 `portal.retention.cleanup.enabled=false` 기본값과 dry-run control로 분리한다.
- `RetentionCleanupProperties`는 `portal.dashboard-snapshots.retention-days:14`를 retention horizon source로 사용하고, `portal.retention.cleanup.enabled`와 `portal.retention.cleanup.dry-run`은 운영 제어로만 둔다.
- `DashboardSnapshotRepository`의 marker/history 조회와 보존된 trend read-model contract는 `current_window_end_utc` horizon과 slot order를 우선 사용한다.
- `DashboardSnapshotRepository`는 `current_window_end_utc < snapshotCutoffUtc` bulk delete를 제공한다.
- `MetricBucketRepository`는 `bucket_end_utc < metricEvidenceCutoffUtc` bulk delete를 제공한다.
- `DashboardSnapshotDetailService`, marker/history/date map source, 보존된 Instance Snapshot Trend read-model contract, Instance Dashboard snapshot mode는 retention 밖 row를 live/current fallback 없이 404/empty/source absence 또는 metric data-quality limitation으로 수렴시키는 guard를 갖는다.

아직 닫히지 않은 부분은 아래와 같다.

- Production 환경에서 physical delete를 실제로 켜는 운영 rollout decision은 별도다. 기본값은 disabled다.
- Cleanup predicate 전용 index 필요성은 row 규모와 query plan evidence가 생기면 별도 story에서 검토한다.
- P10 end-to-end acceptance에서 live dashboard, snapshot detail/history, instance snapshot mode, retention expired path를 한 흐름으로 재검증해야 한다.

## 10. 구현 방향

이 섹션은 Story 13.10에서 구현된 방향을 기록한다. 정책 기준은 위 Hard Contract와 삭제 기준 컬럼을 따른다.

### 10.1 Package and Service

Cleanup orchestration은 `com.observation.portal.domain.cleanup.service` 아래에 둔다.

구현 class:

- `RetentionCleanupScheduler`
- `RetentionCleanupService`
- `RetentionCleanupProperties`

Scheduler는 cron trigger만 담당하고, cutoff 계산과 repository 호출은 service에 위임한다.

### 10.2 Scheduler

Scheduler annotation:

```java
@Scheduled(cron = "0 15 1 * * *", zone = "Asia/Seoul")
```

Scheduler method는 cron trigger와 enablement/dry-run orchestration만 담당하고, cutoff 계산과 repository 호출은 service에 위임한다. Physical delete는 기본 disabled로 시작한다. 다중 instance 배포에서는 DB lock 또는 distributed lock이 후속 검토 대상이다.

### 10.3 Repository Delete Method

Repository delete는 bulk delete/count 반환 형태로 둔다.

구현 method:

```java
long deleteDashboardSnapshotsWindowEndedBefore(OffsetDateTime snapshotCutoffUtc);
long deleteAcceptedMetricBucketsEndedBefore(OffsetDateTime metricEvidenceCutoffUtc);
```

Service result는 최소 아래 count를 반환한다.

- `deletedDashboardSnapshots`
- `deletedAcceptedMetricBuckets`
- `snapshotCutoffUtc`
- `metricEvidenceCutoffUtc`
- `runAtUtc`

Cleanup failure는 부분 삭제 가능성을 고려해 table별 transaction 경계를 명확히 정해야 한다. MVP에서는 하나의 service transaction으로 묶거나, table별 transaction으로 나누되 다음 실행에서 재시도 가능한 idempotent delete 조건을 유지한다.

### 10.4 Configuration

현재 read side는 `portal.dashboard-snapshots.retention-days=14`를 사용한다. MVP cleanup도 이 값을 retention horizon source로 사용한다. 이 값과 다른 horizon을 쓰면 marker/detail/보존된 trend contract 의미가 갈라진다.

구현 방향은 아래와 같다.

1. `portal.dashboard-snapshots.retention-days=14`를 snapshot UI clamp와 cleanup retention의 단일 horizon으로 재사용한다.
2. `portal.retention.cleanup.enabled=false`를 기본값으로 둬 physical delete rollout은 명시적으로 켠 경우에만 실행한다.
3. `portal.retention.cleanup.dry-run=false`는 enabled 상태에서 delete 호출 여부를 제어하는 운영 옵션이다.

어느 쪽이든 MVP에서는 `dashboard_snapshots`와 `accepted_metric_buckets`의 기본 retention-days가 서로 달라지면 안 된다.

## 11. 테스트 기대값

구현/후속 검증은 최소 아래 테스트를 만족해야 한다.

### 11.1 Cleanup Service

- `snapshotCutoffUtc`보다 오래된 `dashboard_snapshots.current_window_end_utc` row를 삭제한다.
- `snapshotCutoffUtc` 이상인 `dashboard_snapshots.current_window_end_utc` row는 유지한다.
- `accepted_metric_buckets`를 삭제 대상에 포함하므로, `metricEvidenceCutoffUtc`보다 오래된 `bucket_end_utc` row를 삭제한다.
- `metricEvidenceCutoffUtc` 이상인 `accepted_metric_buckets.bucket_end_utc` row는 유지한다.
- 가장 오래된 retained snapshot의 30분 window에 필요한 bucket은 evidence grace로 유지한다.
- 같은 cutoff로 cleanup service를 두 번 실행해도 두 번째 실행은 삭제 count 0으로 안전하게 끝난다.
- 일부 row가 이미 없어도 실패하지 않는다.

### 11.2 Scheduler

- Scheduler는 `0 15 1 * * *` cron과 `Asia/Seoul` zone으로 등록된다.
- Scheduler는 `Clock` 기준 현재 시각을 UTC로 정규화해 cutoff를 계산한다.
- Scheduler disabled 기본값에서는 physical delete를 호출하지 않는다.
- Dry-run은 cutoff 의미를 유지하되 physical delete를 호출하지 않는다.
- Retention-days가 0 이하이면 설정 오류로 빠르게 실패한다.

### 11.3 Read Surface Guard

- Snapshot detail은 retention 밖 snapshot을 live dashboard나 current accepted bucket으로 복원하지 않는다.
- Snapshot marker/history/date map은 retention 밖 snapshot을 표시하지 않는다.
- Instance Dashboard snapshot mode는 cleanup으로 bucket이 사라진 경우 current bucket으로 대체하지 않고 `metric_missing` 또는 `not_observed_in_window` 계열 data quality로 수렴한다.
- Application Snapshot Detail의 stored state/evidence는 cleanup 이후에도 남아 있는 row에 대해서만 `read_model_json` 기준으로 복원된다.

## 12. Non-goals

이 문서는 아래를 약속하지 않는다.

- physical delete를 기본 enabled로 배포하는 rollout decision
- 장기 time-series analytics 저장소
- endpoint long-term projection table
- snapshot-derived helper table
- operational event folding 또는 incident period cleanup
- heartbeat telemetry retention 정책
- catalog/archive 정책
- 14일 밖 snapshot을 current dashboard로 재생성하는 fallback

## 13. 후속 확인 항목

1. P10에서 retention expired path를 live dashboard, snapshot detail/history/date map, Instance Dashboard snapshot mode 흐름으로 end-to-end 검증한다.
2. Production rollout 전에 `portal.retention.cleanup.enabled` enablement와 dry-run 운영 절차를 별도 runbook 또는 acceptance note로 확인한다.
3. Cleanup predicate 전용 index가 필요한지는 실제 row 규모와 query plan evidence가 생긴 뒤 판단한다.
