import { useCallback, useMemo } from "react";
import type { LucideIcon } from "lucide-react";
import { Activity, AlertCircle, FileSearch, ListChecks, RefreshCw, Server } from "lucide-react";
import { ApiRequestError, AuthRequiredError, NO_STORE_REQUEST_OPTIONS, readJsonResource } from "../lib/api";
import { type AuthFetch } from "../lib/auth";
import { guardSnapshotDetailReadModel } from "../lib/read-model-contract-guard";
import { useApiResource } from "../lib/use-api-resource";
import {
  buildSnapshotDetailPath,
  formatCount,
  formatDateRange,
  formatNullableRatio,
  formatOptionalDateTime,
  formatRatio,
  humanizeAnchorStatus,
  humanizeCaptureReason,
  humanizeSourceCode,
  humanizeStatusCode,
  severityBadgeClassName,
  severityDisplayText,
  snapshotIdFromDetailPath,
  validateSnapshotDetailPath,
  type ApplicationPresentationItem,
  type ProjectPresentationItem,
} from "../lib/read-model-adapters";
import type { DashboardSnapshotDetailReadModel, JsonValue, SnapshotEndpointEvidenceItem } from "../lib/read-model-types";
import { Alert, AlertDescription, AlertTitle } from "./ui/alert";
import { Button } from "./ui/button";
import type { SnapshotInstanceDashboardTarget } from "./instance-dashboard-surface";

export type SnapshotDetailTarget = {
  activeAnchor?: string | null;
  snapshotId?: string;
  snapshotLink?: string;
  // snapshot mode banner provenance. read model에 없는 captureReason과, slot 위치 표시는 marker에서 전달한다.
  captureReason?: string;
  currentWindowEndUtc?: string;
};

function StatusBadge({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <span className={`inline-flex items-center border px-1.5 py-0.5 text-[11px] uppercase ${className || "border-neutral-400 text-neutral-800"}`}>
      {children}
    </span>
  );
}

function SectionLabel({ icon: Icon, children }: { icon: LucideIcon; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-1.5 text-[11px] uppercase text-neutral-500">
      <Icon className="h-3.5 w-3.5" strokeWidth={1.5} />
      {children}
    </div>
  );
}

/**
 * stored snapshot detail API를 읽고 bounded projection만 렌더링하는 공용 surface다.
 * live dashboard 보완 조회나 raw read_model_json 덤프 없이 readSemantics/self link 계약을 검증한다.
 */
export function SnapshotDetailSurface({
  applicationId,
  compact = false,
  onBackToLive,
  onOpenSnapshotInstanceDashboard,
  projectId,
  selectedApplication,
  selectedProject,
  target,
}: {
  applicationId: string;
  compact?: boolean;
  onBackToLive?: () => void;
  onOpenSnapshotInstanceDashboard?: (target: SnapshotInstanceDashboardTarget) => void;
  projectId: string;
  selectedApplication?: ApplicationPresentationItem;
  selectedProject?: ProjectPresentationItem;
  target: SnapshotDetailTarget | null;
}) {
  const detailResourceKey = useMemo(() => {
    if (!target) {
      return "snapshot-detail:none";
    }
    return `${projectId}|${applicationId}|${target.snapshotLink ?? target.snapshotId ?? "missing"}|${target.activeAnchor ?? "no-anchor"}`;
  }, [applicationId, projectId, target]);

  const requestDetail = useCallback(
    async ({ authFetch, signal }: { authFetch: AuthFetch; signal: AbortSignal }) => {
      if (!target) {
        throw new ApiRequestError("snapshot_detail_not_selected");
      }

      const detailPath = target.snapshotLink
        ? validateSnapshotDetailPath(target.snapshotLink, projectId, applicationId, target.snapshotId)
        : buildSnapshotDetailPath(projectId, applicationId, target.snapshotId ?? "");
      const requestedSnapshotId = target.snapshotId ?? snapshotIdFromDetailPath(detailPath);
      validateSnapshotDetailPath(detailPath, projectId, applicationId, requestedSnapshotId);

      const response = await authFetch(detailPath, {
        ...NO_STORE_REQUEST_OPTIONS,
        signal,
      });
      const model = guardSnapshotDetailReadModel(await readJsonResource<DashboardSnapshotDetailReadModel>(response), {
        snapshotId: requestedSnapshotId,
      });
      validateSnapshotDetailPath(model.links.self, projectId, applicationId, requestedSnapshotId);
      return model;
    },
    [applicationId, projectId, target],
  );

  const resource = useApiResource<DashboardSnapshotDetailReadModel>({
    dependencies: [detailResourceKey],
    enabled: Boolean(target),
    request: requestDetail,
    resourceKey: detailResourceKey,
  });

  const current = resource.resourceKey === detailResourceKey;
  const loading = Boolean(target) && (!current || resource.loading);
  const error = current ? resource.error : null;
  const detail = current ? resource.data : null;

  if (!target) {
    return (
      <div className="border border-neutral-200 bg-white p-3 text-[12px] text-neutral-500">
        상태 변화 기록이나 저장된 스냅샷을 선택하면 당시의 상세 근거를 불러옵니다.
      </div>
    );
  }
  if (loading) {
    return <SnapshotDetailMessage title="스냅샷 상세 로딩 중" body="저장된 상태 기록의 상세 근거를 불러오는 중입니다." compact={compact} />;
  }
  if (error) {
    return <SnapshotDetailError error={error} onBackToLive={onBackToLive} onReload={resource.reload} compact={compact} />;
  }
  if (!detail) {
    return <SnapshotDetailMessage title="스냅샷 상세 선택 대기" body="선택한 스냅샷 상세를 아직 불러오지 않았습니다." compact={compact} />;
  }

  const activeAnchor = target.activeAnchor ?? null;
  const anchorExists = activeAnchor
    ? detail.snapshotEndpointEvidence.items.some((item) => item.anchorId === activeAnchor)
    : false;
  const storedDashboard = toStoredDashboardView(detail);
  const applicationTitle = storedDashboard.applicationName !== "저장본에 application 이름 없음"
    ? storedDashboard.applicationName
    : selectedApplication?.name ?? "Application";
  const projectApplicationText = `${selectedProject?.name ?? storedDashboard.projectId} / ${applicationTitle}`;

  return (
    <div className={`space-y-6 ${compact ? "text-[12px]" : "text-[13px]"}`}>
      <section className="border border-neutral-900 bg-white">
        <div className="flex flex-wrap items-start justify-between gap-6 border-b border-neutral-200 p-6">
          <div className="min-w-0 max-w-4xl">
            <SectionLabel icon={FileSearch}>Application Dashboard / Snapshot</SectionLabel>
            <h1 className="mt-3 text-[32px] font-semibold leading-tight text-neutral-950 md:text-[38px]">
              {applicationTitle} · Application Snapshot Dashboard
            </h1>
            <p className="mt-4 max-w-3xl text-[18px] leading-7 text-neutral-600">
              dashboard_snapshots.read_model_json에 저장된 과거 dashboard surface를 같은 surface로 복원합니다.
              현재 metric으로 state/evidence/resource를 재계산하지 않습니다.
            </p>
          </div>
          <div className="flex max-w-full flex-wrap items-start justify-end gap-3">
            <StatusBadge className="border-amber-400 bg-white px-3 py-2 text-[16px] text-amber-700">MODE: SNAPSHOT</StatusBadge>
            <StatusBadge className="border-blue-200 bg-blue-50 px-3 py-2 text-[16px] text-blue-700">RECENT 30 MINUTES</StatusBadge>
            <StatusBadge className="border-neutral-300 bg-neutral-50 px-3 py-2 text-[16px] text-neutral-700">BASELINE NOT USED</StatusBadge>
            {onBackToLive && (
              <Button className="h-14 bg-emerald-700 px-6 text-[18px] font-semibold text-white hover:bg-emerald-800" onClick={onBackToLive}>
                Live Dashboard로 돌아가기
              </Button>
            )}
          </div>
        </div>
        <div className="border-b border-violet-200 bg-violet-50 px-6 py-5 text-[18px] leading-8 text-violet-900">
          이 화면은 {formatOptionalDateTime(detail.snapshot.capturedAt)}에 저장된 Application Snapshot Dashboard입니다.
          현재 accepted metric으로 state, endpoint priority, resource evidence를 재계산하지 않습니다.
          markerBucket={detail.marker.type}은 탐색 색인일 뿐 state source가 아닙니다.
        </div>
        <div className="grid grid-cols-1 gap-3 p-6 text-[14px] md:grid-cols-2 xl:grid-cols-4">
          <InfoCell label="mode" value={storedDashboard.mode} />
          <InfoCell label="window" value={storedDashboard.windowLabel} />
          <InfoCell label="source" value={detail.readSemantics.source} />
          <InfoCell label="capturedAt" value={formatOptionalDateTime(detail.snapshot.capturedAt)} />
          <InfoCell label="captureReason" value={humanizeCaptureReason(detail.snapshot.captureReason)} />
          <InfoCell label="snapshotDetailRecalculates" value={String(detail.readSemantics.snapshotDetailRecalculates)} />
          <InfoCell label="markerIsStateSource" value={String(detail.readSemantics.markerIsStateSource)} />
          <InfoCell label="project / application" value={projectApplicationText} />
          <InfoCell label="currentWindowStartUtc" value={formatOptionalDateTime(detail.snapshot.currentWindow.startUtc)} />
          <InfoCell label="currentWindowEndUtc" value={formatOptionalDateTime(detail.snapshot.currentWindow.endUtc)} />
          <InfoCell label="snapshotId" value={detail.snapshot.snapshotId} />
          <InfoCell label="generatedAt" value={formatOptionalDateTime(detail.snapshot.generatedAt)} />
        </div>
      </section>

      <section className={`grid gap-6 border border-neutral-900 border-l-4 ${snapshotStateAccentClassName(storedDashboard.stateCode)} bg-white p-6 md:grid-cols-[minmax(220px,0.55fr)_minmax(0,1.35fr)_minmax(260px,0.8fr)] md:items-center`}>
        <div>
          <StatusBadge className={`${snapshotStateBadgeClassName(storedDashboard.stateCode)} px-3 py-2 text-[16px]`}>
            {snapshotStateDisplayText(storedDashboard.stateCode)}
          </StatusBadge>
          <h2 className="mt-4 text-[24px] font-semibold leading-tight text-neutral-950">
            {storedDashboard.stateLabel}
          </h2>
        </div>
        <div className="text-[20px] leading-8 text-neutral-950">{storedDashboard.stateRationale}</div>
        <div className="text-[17px] leading-8 text-neutral-600">
          해당 30분 slot의 저장된 dashboard read model을 복원합니다. 현재 metric으로 재계산하지 않습니다.
        </div>
      </section>

      <section className="grid gap-6 border border-neutral-900 border-l-4 border-l-emerald-700 bg-white p-6 md:grid-cols-[minmax(220px,0.55fr)_minmax(0,1.35fr)_minmax(260px,0.8fr)] md:items-center">
        <div>
          <StatusBadge className="border-blue-200 bg-blue-50 px-3 py-2 text-[16px] text-blue-700">STARTERCONNECTION</StatusBadge>
          <h2 className="mt-4 text-[24px] font-semibold leading-tight text-neutral-950">Control-plane only</h2>
        </div>
        <div className="text-[20px] leading-8 text-neutral-950">
          heartbeat {storedDashboard.starterLastHeartbeatAt}, metric state 변경 없음
        </div>
        <div className="text-[17px] leading-8 text-neutral-600">
          heartbeat는 accepted bucket freshness나 application lifecycle state를 직접 만들지 않습니다.
          <p className="mt-2 text-[14px] text-neutral-500">
            {humanizeStatusCode(storedDashboard.starterConnectionMeaning)} · {snapshotStateImpactText(storedDashboard.starterStateImpact)}
          </p>
        </div>
      </section>

      <section className="grid grid-cols-1 gap-0 border border-neutral-200 bg-white md:grid-cols-4" aria-label="snapshot golden signals">
        <SnapshotMetricCell label="RED Rate" note="최근 30분 요청량" value={storedDashboard.redRequestCount} />
        <SnapshotMetricCell label="RED Errors" note={storedDashboard.redErrorSemantic} value={storedDashboard.redErrorRate} />
        <SnapshotMetricCell label="RED Duration" note="500ms 초과 요청 비율" value={storedDashboard.redSlowShareOver500ms} />
        <SnapshotMetricCell label="USE Hint" note="window 내 observed maxUsage hint" value={storedDashboard.datasourcePoolMax} last />
      </section>

      <details className="border border-neutral-200 bg-white">
        <summary className="cursor-pointer border-b border-neutral-100 px-6 py-4 text-[16px] font-medium uppercase text-neutral-600">
          저장 read model 세부 정보
        </summary>
        <div className="grid gap-3 border-b border-neutral-100 p-3 lg:grid-cols-3">
          <section className="border border-neutral-200 bg-white p-3">
            <SectionLabel icon={Activity}>Stored state</SectionLabel>
            <p className="mt-2 text-neutral-900">{storedDashboard.recommendedAction}</p>
            <p className="mt-1 text-[12px] text-neutral-500">{storedDashboard.headline}</p>
          </section>
          <section className="border border-neutral-200 bg-white p-3">
            <SectionLabel icon={ListChecks}>Operator summary</SectionLabel>
            <p className="mt-2 text-neutral-900">{storedDashboard.headline}</p>
            <p className="mt-1 text-[12px] text-neutral-500">{storedDashboard.firstLookText}</p>
          </section>
          <section className="border border-neutral-200 bg-white p-3">
            <SectionLabel icon={Server}>Stored data quality</SectionLabel>
            <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500">
              <InfoCell label="quality state" value={storedDashboard.dataQualityState} />
              <InfoCell label="requestCount" value={storedDashboard.requestCount} />
              <InfoCell label="minimum" value={storedDashboard.minimumRequestCount} />
              <InfoCell label="lastObservedAt" value={storedDashboard.lastObservedAt} />
            </div>
          </section>
        </div>
        <div className="grid gap-3 border-b border-neutral-100 p-3 lg:grid-cols-3">
          <StoredDashboardList title="State reasons" items={storedDashboard.stateReasons} emptyText="저장된 stateReason이 없습니다." />
          <StoredDashboardList title="Attention evidence" items={storedDashboard.attentionEvidence} emptyText="저장된 attention evidence가 없습니다." />
          <StoredDashboardList title="First look candidates" items={storedDashboard.firstLookCandidates} emptyText="저장된 first look 후보가 없습니다." />
        </div>
      {detail.recoveryMarker && (
        <Alert className="m-3 border-amber-300">
          <AlertCircle className="h-4 w-4" strokeWidth={1.5} />
          <AlertTitle className="flex items-center gap-2">
            {detail.recoveryMarker.title}
            <StatusBadge className={severityBadgeClassName(detail.recoveryMarker.severity)}>
              {severityDisplayText(detail.recoveryMarker.severity)}
            </StatusBadge>
          </AlertTitle>
          <AlertDescription>{detail.recoveryMarker.summary} {detail.recoveryMarker.recommendedAction}</AlertDescription>
        </Alert>
      )}
      {activeAnchor && (
        <div className="mx-3 mt-3 border border-neutral-200 bg-neutral-50 p-2 text-[11px] text-neutral-600">
          상세 근거 {activeAnchor}: {humanizeAnchorStatus(anchorExists ? "resolved" : "missing")}
        </div>
      )}
      <SnapshotEndpointEvidencePanel activeAnchor={activeAnchor} evidence={detail.snapshotEndpointEvidence} />
      <div className="border-t border-neutral-100 p-3">
        <div className="text-[11px] uppercase text-neutral-500">인스턴스 요약</div>
        <div className="mt-1 text-[12px] text-neutral-600">
          저장 당시 인스턴스 {formatCount(detail.instanceSummary.items.length)}개 · source={detail.instanceSummary.source}
        </div>
        <p className="mt-1 text-[12px] text-neutral-500">
          instanceSummary.items[]는 stored summary/trend projection source입니다. Instance Dashboard snapshot detail의 필수 source로 사용하지 않습니다.
        </p>
        {onOpenSnapshotInstanceDashboard && (
          <SnapshotInstanceDrillDownList
            detail={detail}
            onOpenSnapshotInstanceDashboard={onOpenSnapshotInstanceDashboard}
            projectId={projectId}
            applicationId={applicationId}
          />
        )}
      </div>
      <details className="border-t border-neutral-100 p-3 text-[11px] text-neutral-500">
        <summary className="cursor-pointer text-neutral-700">기술 세부 정보</summary>
        <div className="mt-2 grid grid-cols-2 gap-2 md:grid-cols-4">
          <InfoCell label="상세 링크" value={detail.links.self} />
          <InfoCell label="읽기 방식" value={humanizeStatusCode(detail.readSemantics.mode)} />
          <InfoCell label="현재 상태 재계산" value={detail.readSemantics.currentStateRecalculated ? "예" : "아니오"} />
          <InfoCell label="실시간 데이터 결합" value={formatCount(detail.readSemantics.liveSourcesJoined.length)} />
          <InfoCell label="원본 데이터 노출" value={detail.readSemantics.rawReadModelJsonExposed ? "예" : "아니오"} />
          <InfoCell label="stored readModel mode" value={storedDashboard.mode} />
          <InfoCell label="stored source" value={storedDashboard.source} />
          <InfoCell label="이전 상태 근거" value={humanizeSourceCode(detail.previousState.source)} />
          <InfoCell label="마지막 정상 근거" value={humanizeSourceCode(detail.lastHealthyAt.source)} />
          <InfoCell label="인스턴스 요약 근거" value={humanizeSourceCode(detail.instanceSummary.source)} />
        </div>
      </details>
      </details>
    </div>
  );
}

type StoredDashboardItem = {
  body: string;
  key: string;
  meta: string;
  title: string;
};

type SnapshotDurationBucket = {
  count: number;
  leMs: number;
};

function SnapshotEndpointEvidencePanel({
  activeAnchor,
  evidence,
}: {
  activeAnchor: string | null;
  evidence: DashboardSnapshotDetailReadModel["snapshotEndpointEvidence"];
}) {
  const maxRequestCount = Math.max(0, ...evidence.items.map((item) => item.requestCount ?? 0));
  const maxErrorRate = Math.max(0, ...evidence.items.map((item) => item.errorRate ?? 0));

  return (
    <div className="border-t border-neutral-100 p-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-[11px] uppercase text-neutral-500">엔드포인트 근거</div>
          <p className="mt-1 text-[12px] text-neutral-500">
            저장된 snapshotEndpointEvidence만 표시합니다. errorCount와 slow count/share는 저장 projection에 없으면 계산하지 않습니다.
          </p>
        </div>
        <StatusBadge>max {evidence.maxItems}</StatusBadge>
      </div>
      {evidence.items.length === 0 ? (
        <div className="mt-3 text-[12px] leading-5 text-neutral-500">
          {evidence.unavailableReason
            ? `${humanizeStatusCode(evidence.unavailableReason)} · 저장된 endpoint evidence가 없습니다.`
            : "이 스냅샷에 연결된 엔드포인트 근거가 없습니다."}
        </div>
      ) : (
        <ol className="mt-3 divide-y divide-neutral-100 border border-neutral-200">
          {evidence.items.map((item) => (
            <SnapshotEndpointEvidenceRow
              active={activeAnchor === item.anchorId}
              item={item}
              key={item.anchorId}
              maxErrorRate={maxErrorRate}
              maxRequestCount={maxRequestCount}
            />
          ))}
        </ol>
      )}
    </div>
  );
}

function SnapshotEndpointEvidenceRow({
  active,
  item,
  maxErrorRate,
  maxRequestCount,
}: {
  active: boolean;
  item: SnapshotEndpointEvidenceItem;
  maxErrorRate: number;
  maxRequestCount: number;
}) {
  const durationBuckets = snapshotDurationBuckets(item.durationBuckets);
  return (
    <li className={`p-3 ${active ? "bg-neutral-50 ring-1 ring-inset ring-neutral-900" : "bg-white"}`} id={item.anchorId}>
      <div className="grid gap-3 md:grid-cols-[32px_minmax(0,1fr)_minmax(220px,0.9fr)] md:items-start">
        <span className="grid h-8 w-8 place-items-center border border-neutral-900 bg-neutral-900 text-[12px] text-white tabular-nums">
          {item.rank ?? "-"}
        </span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <div className="truncate font-medium text-neutral-950">{item.endpointKey}</div>
            <StatusBadge className={snapshotEndpointBadgeClassName(item)}>{snapshotEndpointBadgeText(item)}</StatusBadge>
            {active && <StatusBadge className="border-neutral-900 text-neutral-900">선택됨</StatusBadge>}
          </div>
          <div className="mt-0.5 text-[11px] text-neutral-500">
            상세 근거 {item.anchorId} · {item.reason ? humanizeStatusCode(item.reason) : "추가 설명 없음"}
          </div>
        </div>
        <div className="grid gap-1.5 text-[11px] text-neutral-500">
          <EndpointMetricBar
            label="request"
            tone="neutral"
            unavailable={item.requestCount === null}
            value={item.requestCount === null ? "미제공" : formatCount(item.requestCount)}
            width={ratioWidth(item.requestCount ?? 0, maxRequestCount)}
          />
          <EndpointMetricBar
            label="errorRate"
            tone="danger"
            unavailable={item.errorRate === null}
            value={formatNullableRatio(item.errorRate)}
            width={ratioWidth(item.errorRate ?? 0, maxErrorRate)}
          />
          <EndpointMetricBar label="slow >500ms" tone="hot" unavailable value="미제공" width={0} />
        </div>
      </div>
      <div className="mt-3 grid gap-2 text-[11px] text-neutral-500 md:grid-cols-[minmax(0,1fr)_minmax(220px,0.55fr)]">
        <SnapshotBucketStrip buckets={durationBuckets} source={item.bucketDistributionSource} />
        <div className="grid grid-cols-2 gap-2">
          <InfoCell label="errorCount" value="미제공" />
          <InfoCell label="slowShare" value="미제공" />
          <InfoCell label="confidence" value={formatNullableRatio(item.confidence)} />
          <InfoCell label="recommended" value={item.recommendedAction ?? "저장값 없음"} />
        </div>
      </div>
    </li>
  );
}

function EndpointMetricBar({
  label,
  tone,
  unavailable = false,
  value,
  width,
}: {
  label: string;
  tone: "danger" | "hot" | "neutral";
  unavailable?: boolean;
  value: string;
  width: number;
}) {
  const fillClassName = tone === "danger" ? "bg-red-500" : tone === "hot" ? "bg-amber-700" : "bg-neutral-500";
  return (
    <div className="grid grid-cols-[68px_minmax(72px,1fr)_72px] items-center gap-2">
      <span className="truncate uppercase">{label}</span>
      <span className={`h-1.5 bg-neutral-100 ${unavailable ? "border border-dashed border-neutral-300 bg-white" : ""}`}>
        <span className={`block h-full ${fillClassName}`} style={{ width: `${unavailable ? 0 : width}%` }} />
      </span>
      <span className="truncate text-right text-neutral-700">{unavailable ? "미제공" : value}</span>
    </div>
  );
}

function SnapshotBucketStrip({ buckets, source }: { buckets: SnapshotDurationBucket[]; source: string | null }) {
  if (buckets.length === 0) {
    return (
      <div className="border border-neutral-200 bg-neutral-50 p-2">
        duration buckets 미제공 · snapshot slowShare로 해석하지 않습니다.
      </div>
    );
  }
  const maxCount = Math.max(0, ...buckets.map((bucket) => bucket.count));
  return (
    <div className="border border-neutral-200 bg-neutral-50 p-2">
      <div className="mb-1 text-neutral-500">duration buckets · {humanizeSourceCode(source)}</div>
      <div className="flex h-8 items-end gap-1">
        {buckets.slice(0, 8).map((bucket) => (
          <span
            aria-label={`<= ${bucket.leMs}ms: ${bucket.count}`}
            className="min-w-3 flex-1 bg-neutral-300"
            key={`${bucket.leMs}-${bucket.count}`}
            style={{ height: `${Math.max(8, ratioWidth(bucket.count, maxCount))}%` }}
            title={`<= ${bucket.leMs}ms · ${formatCount(bucket.count)}`}
          />
        ))}
      </div>
    </div>
  );
}

function snapshotEndpointBadgeText(item: SnapshotEndpointEvidenceItem): string {
  return item.rank === 1 ? "FIRST LOOK" : "ATTENTION";
}

function snapshotEndpointBadgeClassName(item: SnapshotEndpointEvidenceItem): string {
  if (item.rank === 1) {
    return "border-neutral-900 bg-neutral-900 text-white";
  }
  if ((item.errorRate ?? 0) > 0) {
    return "border-red-200 bg-red-50 text-red-700";
  }
  return "border-neutral-300 bg-neutral-50 text-neutral-700";
}

function snapshotDurationBuckets(value: JsonValue | null): SnapshotDurationBucket[] {
  return jsonArray(value).flatMap((bucket) => {
    const record = jsonRecord(bucket);
    const leMs = nullableNumberField(record, "leMs");
    const count = nullableNumberField(record, "count");
    return leMs === null || count === null ? [] : [{ count, leMs }];
  });
}

function ratioWidth(value: number, max: number): number {
  if (!Number.isFinite(value) || !Number.isFinite(max) || value <= 0 || max <= 0) {
    return 0;
  }
  return Math.min(100, Math.max(6, (value / max) * 100));
}

type StoredDashboardView = {
  applicationName: string;
  attentionEvidence: StoredDashboardItem[];
  dataQualityState: string;
  datasourcePoolMax: string;
  firstLookCandidates: StoredDashboardItem[];
  firstLookText: string;
  headline: string;
  lastObservedAt: string;
  minimumRequestCount: string;
  mode: string;
  projectId: string;
  redErrorRate: string;
  redErrorSemantic: string;
  redRequestCount: string;
  redSlowShareOver500ms: string;
  recommendedAction: string;
  requestCount: string;
  source: string;
  stateCode: string;
  stateLabel: string;
  stateRationale: string;
  stateReasons: StoredDashboardItem[];
  starterConnectionMeaning: string;
  starterLastHeartbeatAt: string;
  starterStateImpact: string;
  windowLabel: string;
};

/**
 * dashboard_snapshots.read_model_json 저장본에서 화면에 필요한 bounded field만 꺼낸다.
 * 값이 없으면 current dashboard를 조회하거나 조합하지 않고 저장본 부재 copy로 표시한다.
 */
function toStoredDashboardView(detail: DashboardSnapshotDetailReadModel): StoredDashboardView {
  const readModel = detail.readModel;
  const application = jsonRecord(readModel.application);
  const state = jsonRecord(readModel.state);
  const operatorSummary = jsonRecord(readModel.operatorSummary);
  const dataQuality = jsonRecord(readModel.dataQuality);
  const readSemantics = jsonRecord(readModel.readSemantics);
  const signals = jsonRecord(readModel.signals);
  const redSignals = jsonRecord(signals?.red);
  const useSignals = jsonRecord(signals?.use);
  const datasourcePoolUsage = jsonRecord(useSignals?.datasourcePoolUsage);
  const starterConnection = jsonRecord(readModel.starterConnection);
  const window = jsonRecord(readModel.window);

  const stateCode = stringField(state, "code", "저장본에 state code 없음");
  const stateLabel = stringField(state, "label", humanizeStatusCode(stateCode));
  return {
    applicationName: stringField(application, "name", "저장본에 application 이름 없음"),
    attentionEvidence: storedReasonItems(readModel.attentionEvidence, "attention"),
    dataQualityState: stringField(dataQuality, "state", "저장본에 dataQuality state 없음"),
    datasourcePoolMax: formatNullableRatio(nullableNumberField(datasourcePoolUsage, "max")),
    firstLookCandidates: firstLookItems(readModel.firstLookCandidates),
    firstLookText: stringField(operatorSummary, "firstLookText", "저장된 first look 안내가 없습니다."),
    headline: stringField(operatorSummary, "headline", "저장된 operator summary가 없습니다."),
    lastObservedAt: formatOptionalDateTime(nullableStringField(dataQuality, "lastObservedAt")),
    minimumRequestCount: numberField(dataQuality, "minimumRequestCount"),
    mode: stringJsonValue(readModel.mode, "snapshot"),
    projectId: stringField(application, "projectId", detail.snapshot.snapshotId),
    redErrorRate: formatRatio(nullableNumberField(redSignals, "errorRate")),
    redErrorSemantic: humanizeStatusCode(stringField(redSignals, "errorSemantic", "5xx / server_error semantic")),
    redRequestCount: numberField(redSignals, "requestCount"),
    redSlowShareOver500ms: formatNullableRatio(nullableNumberField(redSignals, "slowShareOver500ms")),
    recommendedAction: stringField(state, "recommendedAction", "저장된 권장 조치가 없습니다."),
    requestCount: numberField(dataQuality, "requestCount"),
    source: stringField(readSemantics, "source", detail.readSemantics.source),
    stateCode,
    stateLabel,
    stateRationale: stringField(state, "rationale", "저장된 state rationale이 없습니다."),
    stateReasons: storedReasonItems(readModel.stateReasons, "state-reason"),
    starterConnectionMeaning: stringField(starterConnection, "connectionMeaning", "저장된 starter 연결 의미 없음"),
    starterLastHeartbeatAt: formatOptionalDateTime(nullableStringField(starterConnection, "lastHeartbeatAt")),
    starterStateImpact: stringField(starterConnection, "stateImpact", "저장된 starter 영향 없음"),
    windowLabel: storedWindowLabel(window, detail),
  };
}

function storedWindowLabel(
  window: Record<string, JsonValue> | null,
  detail: DashboardSnapshotDetailReadModel,
): string {
  const startUtc = nullableStringField(window, "startUtc") ?? detail.snapshot.currentWindow.startUtc;
  const endUtc = nullableStringField(window, "endUtc") ?? detail.snapshot.currentWindow.endUtc;
  return formatDateRange(startUtc, endUtc);
}

function StoredDashboardList({ emptyText, items, title }: { emptyText: string; items: StoredDashboardItem[]; title: string }) {
  return (
    <section className="border border-neutral-200 bg-white p-3">
      <div className="text-[11px] uppercase text-neutral-500">{title}</div>
      {items.length === 0 ? (
        <div className="mt-2 text-[12px] text-neutral-500">{emptyText}</div>
      ) : (
        <ul className="mt-2 space-y-2">
          {items.map((item) => (
            <li key={item.key} className="border border-neutral-100 p-2">
              <div className="text-[12px] text-neutral-900">{item.title}</div>
              <div className="mt-0.5 text-[11px] text-neutral-500">{item.meta}</div>
              <div className="mt-1 text-[12px] text-neutral-600">{item.body}</div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function storedReasonItems(value: JsonValue | null, prefix: string): StoredDashboardItem[] {
  const items: StoredDashboardItem[] = [];
  const sourceItems = jsonArray(value);
  for (let index = 0; index < sourceItems.length; index += 1) {
    const item = jsonRecord(sourceItems[index]);
    if (!item) {
      continue;
    }
    const reasonCode = stringField(item, "reasonCode", "reason 없음");
    const type = stringField(item, "type", "type 없음");
    const severity = stringField(item, "severity", "severity 없음");
    const scope = stringField(item, "scope", "scope 없음");
    const target = nullableStringField(item, "target") ?? "target 없음";
    items.push({
      body: stringField(item, "operatorText", "저장된 operator text가 없습니다."),
      key: `${prefix}-${index}-${reasonCode}`,
      meta: `${humanizeStatusCode(type)} · ${humanizeStatusCode(severity)} · ${humanizeStatusCode(scope)} · ${target}`,
      title: reasonCode,
    });
  }
  return items;
}

function firstLookItems(value: JsonValue | null): StoredDashboardItem[] {
  const items: StoredDashboardItem[] = [];
  const sourceItems = jsonArray(value);
  for (let index = 0; index < sourceItems.length; index += 1) {
    const item = jsonRecord(sourceItems[index]);
    if (!item) {
      continue;
    }
    const rank = numberField(item, "rank");
    const type = stringField(item, "type", "type 없음");
    const target = nullableStringField(item, "target") ?? "target 없음";
    const reasonCode = stringField(item, "reasonCode", "reason 없음");
    items.push({
      body: stringField(item, "operatorText", "저장된 first look 설명이 없습니다."),
      key: `first-look-${index}-${reasonCode}`,
      meta: `rank ${rank} · ${humanizeStatusCode(type)} · ${target}`,
      title: reasonCode,
    });
  }
  return items;
}

function jsonRecord(value: JsonValue | null | undefined): Record<string, JsonValue> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value) ? value : null;
}

function jsonArray(value: JsonValue | null | undefined): JsonValue[] {
  return Array.isArray(value) ? value : [];
}

function stringJsonValue(value: JsonValue | null | undefined, fallback: string): string {
  return typeof value === "string" && value.trim() ? value : fallback;
}

function stringField(record: Record<string, JsonValue> | null, key: string, fallback: string): string {
  if (!record) {
    return fallback;
  }
  return stringJsonValue(record[key], fallback);
}

function nullableStringField(record: Record<string, JsonValue> | null, key: string): string | null {
  if (!record) {
    return null;
  }
  const value = record[key];
  return typeof value === "string" && value.trim() ? value : null;
}

function numberField(record: Record<string, JsonValue> | null, key: string): string {
  if (!record) {
    return "저장값 없음";
  }
  const value = record[key];
  return typeof value === "number" && Number.isFinite(value) ? formatCount(value) : "저장값 없음";
}

function nullableNumberField(record: Record<string, JsonValue> | null, key: string): number | null {
  if (!record) {
    return null;
  }
  const value = record[key];
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function booleanField(record: Record<string, JsonValue> | null, key: string): boolean | null {
  if (!record) {
    return null;
  }
  const value = record[key];
  return typeof value === "boolean" ? value : null;
}

function SnapshotMetricCell({
  label,
  last = false,
  note,
  value,
}: {
  label: string;
  last?: boolean;
  note: string;
  value: string;
}) {
  return (
    <div className={`min-h-[190px] p-7 ${last ? "" : "border-b border-neutral-200 md:border-b-0 md:border-r"}`}>
      <div className="text-[16px] uppercase text-neutral-500">{label}</div>
      <div className="mt-5 break-words text-[40px] font-semibold leading-tight text-neutral-950">{value}</div>
      <div className="mt-4 text-[17px] leading-6 text-neutral-600">{note}</div>
    </div>
  );
}

function snapshotStateAccentClassName(stateCode: string): string {
  switch (stateCode.toLowerCase()) {
    case "active":
    case "normal":
      return "border-l-emerald-700";
    case "degraded":
      return "border-l-red-700";
    case "attention":
    case "warning":
      return "border-l-amber-700";
    case "down":
    case "critical":
      return "border-l-red-700";
    default:
      return "border-l-neutral-700";
  }
}

function snapshotStateBadgeClassName(stateCode: string): string {
  switch (stateCode.toLowerCase()) {
    case "active":
    case "normal":
      return "border-emerald-300 bg-emerald-50 text-emerald-700";
    case "degraded":
      return "border-red-300 bg-red-50 text-red-700";
    case "attention":
    case "warning":
      return "border-amber-300 bg-amber-50 text-amber-700";
    case "down":
    case "critical":
      return "border-red-300 bg-red-50 text-red-700";
    default:
      return "border-neutral-300 bg-neutral-50 text-neutral-700";
  }
}

function snapshotStateDisplayText(stateCode: string): string {
  switch (stateCode.toLowerCase()) {
    case "active":
    case "normal":
      return "ACTIVE";
    case "degraded":
      return "DEGRADED";
    case "attention":
    case "warning":
      return "ATTENTION";
    case "down":
    case "critical":
      return "CRITICAL";
    default:
      return humanizeStatusCode(stateCode).toUpperCase();
  }
}

function snapshotStateImpactText(stateImpact: string): string {
  if (stateImpact === "none" || stateImpact === "does_not_change_metric_state") {
    return "metric state와 분리";
  }
  if (stateImpact === "control_plane_only") {
    return "control-plane 참고";
  }
  return humanizeStatusCode(stateImpact);
}

function SnapshotInstanceDrillDownList({
  applicationId,
  detail,
  onOpenSnapshotInstanceDashboard,
  projectId,
}: {
  applicationId: string;
  detail: DashboardSnapshotDetailReadModel;
  onOpenSnapshotInstanceDashboard: (target: SnapshotInstanceDashboardTarget) => void;
  projectId: string;
}) {
  if (detail.instanceSummary.items.length === 0) {
    return (
      <div className="mt-3 border border-neutral-200 bg-neutral-50 p-2 text-[12px] text-neutral-500">
        stored instance summary에 표시된 row가 없어 이 surface에서는 instance action을 만들 수 없습니다. target instance id가 있는 다른 navigation은 selected snapshot id로 snapshot Instance Dashboard endpoint를 호출할 수 있습니다.
      </div>
    );
  }

  return (
    <ul className="mt-3 space-y-2">
      {detail.instanceSummary.items.map((item) => {
        const metricData = jsonRecord(item.metricData);
        const starterConnection = jsonRecord(item.starterConnection);
        const resourceHints = jsonRecord(item.resourceHints);
        const contribution = jsonRecord(item.applicationTriageContribution);
        const contributed = booleanField(contribution, "contributed");
        const relatedRuleCount = jsonArray(contribution?.relatedRuleIds).length;
        return (
          <li key={item.instanceId} className="border border-neutral-200 bg-white p-3">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="truncate text-[14px] font-medium text-neutral-950">{item.instanceName}</div>
                <div className="mt-0.5 truncate text-[11px] text-neutral-500">
                  {item.instanceId} · stored observation {humanizeStatusCode(item.observationStatus)}
                </div>
              </div>
              <Button
                variant="outline"
                size="sm"
                className="gap-2 border-neutral-300"
                onClick={() =>
                  onOpenSnapshotInstanceDashboard({
                    applicationId,
                    instanceId: item.instanceId,
                    instanceName: item.instanceName,
                    projectId,
                    snapshotDetailLink: detail.links.self,
                    snapshotId: detail.snapshot.snapshotId,
                  })
                }
              >
                <Server className="h-3.5 w-3.5" strokeWidth={1.5} /> Instance snapshot dashboard
              </Button>
            </div>
            <div className="mt-3 grid grid-cols-1 gap-2 text-[11px] text-neutral-500 md:grid-cols-4">
              <InfoCell label="metric freshness" value={humanizeStatusCode(stringField(metricData, "freshnessLabel", "저장값 없음"))} />
              <InfoCell label="last accepted" value={formatOptionalDateTime(nullableStringField(metricData, "lastAcceptedBucketAt"))} />
              <InfoCell label="heartbeat" value={`${humanizeStatusCode(stringField(starterConnection, "lastHeartbeatStatus", "저장값 없음"))} ${formatOptionalDateTime(nullableStringField(starterConnection, "lastHeartbeatAt"))}`} />
              <InfoCell label="connection" value={humanizeStatusCode(stringField(starterConnection, "connectionMeaning", "저장값 없음"))} />
              <InfoCell label="DB pool hint" value={formatNullableRatio(nullableNumberField(resourceHints, "datasourcePoolUsageRatio"))} />
              <InfoCell label="CPU hint" value={formatNullableRatio(nullableNumberField(resourceHints, "cpuUsageRatio"))} />
              <InfoCell label="heap hint" value={formatNullableRatio(nullableNumberField(resourceHints, "heapUsedRatio"))} />
              <InfoCell
                label="triage"
                value={`${contributed === null ? "저장값 없음" : contributed ? "contributed" : "not contributed"} · rules ${formatCount(relatedRuleCount)}`}
              />
            </div>
          </li>
        );
      })}
    </ul>
  );
}

function SnapshotDetailMessage({ body, compact, title }: { body: string; compact: boolean; title: string }) {
  return (
    <div className={`border border-neutral-200 bg-white p-3 ${compact ? "text-[12px]" : "text-[13px]"}`}>
      <div className="text-neutral-900">{title}</div>
      <div className="mt-1 text-neutral-500">{body}</div>
    </div>
  );
}

function SnapshotDetailError({
  compact,
  error,
  onBackToLive,
  onReload,
}: {
  compact: boolean;
  error: Error;
  onBackToLive?: () => void;
  onReload: () => void;
}) {
  const copy = snapshotDetailErrorCopy(error);
  return (
    <div className={`border border-neutral-200 bg-white p-3 ${compact ? "text-[12px]" : "text-[13px]"}`}>
      <div className="text-neutral-900">{copy.title}</div>
      <div className="mt-1 text-neutral-500">{copy.body}</div>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button variant="outline" size="sm" className="gap-2 border-neutral-300" onClick={onReload}>
          <RefreshCw className="h-3.5 w-3.5" strokeWidth={1.5} /> 다시 시도
        </Button>
        {onBackToLive && (
          <Button size="sm" className="bg-emerald-700 text-white hover:bg-emerald-800" onClick={onBackToLive}>
            Live Dashboard로 돌아가기
          </Button>
        )}
      </div>
    </div>
  );
}

function snapshotDetailErrorCopy(error: Error): { title: string; body: string } {
  if (error instanceof AuthRequiredError) {
    return {
      title: "인증 필요",
      body: error.message,
    };
  }
  if (error instanceof ApiRequestError && error.status === 404) {
    return {
      title: "스냅샷 상세 없음",
      body: "보관 기간이 지났거나 저장된 snapshot을 찾을 수 없습니다. live dashboard/current accepted bucket으로 복원하지 않습니다.",
    };
  }
  if (error instanceof ApiRequestError && error.status === 400) {
    return {
      title: "스냅샷 ID 확인 필요",
      body: "상태 변화 기록이나 저장된 스냅샷에서 선택한 항목만 상세로 열 수 있습니다.",
    };
  }
  return {
    title: "스냅샷 상세 로드 실패",
    body: "저장된 상세 기록을 불러오지 못했습니다.",
  };
}

function InfoCell({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 border border-neutral-200 bg-white p-4" title={`${label}: ${value}`}>
      <div className="text-[16px] text-neutral-500">{label}</div>
      <div className="mt-3 break-words text-[19px] font-medium leading-7 text-neutral-950">{value}</div>
    </div>
  );
}
