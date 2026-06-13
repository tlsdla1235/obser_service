import { useCallback } from "react";
import type { LucideIcon } from "lucide-react";
import { Activity, Gauge, ListChecks, Radio, RefreshCw, Server } from "lucide-react";
import { ApiRequestError, AuthRequiredError, NO_STORE_REQUEST_OPTIONS, readJsonResource } from "../lib/api";
import { type AuthFetch } from "../lib/auth";
import { guardInstanceDashboardReadModel } from "../lib/read-model-contract-guard";
import { useApiResource } from "../lib/use-api-resource";
import {
  buildLiveInstanceDashboardPath,
  buildSnapshotInstanceDashboardPath,
  formatCount,
  formatDateRange,
  formatNullableRatio,
  formatOptionalDateTime,
  humanizeCaptureReason,
  humanizeSourceCode,
  humanizeStatusCode,
  statusBadgeClassName,
  validateLiveInstanceDashboardPath,
  validateSnapshotDetailPath,
  validateSnapshotInstanceDashboardPath,
} from "../lib/read-model-adapters";
import type {
  InstanceDashboardEndpointEvidenceItem,
  InstanceDashboardReadModel,
  InstanceDashboardResourceEvidenceItem,
} from "../lib/read-model-types";
import { Button } from "./ui/button";

export type InstanceDashboardTarget = {
  applicationId: string;
  evidenceLink?: string | null;
  instanceId: string;
  instanceName: string;
  projectId: string;
  snapshotTrendLink?: string | null;
};

export type SnapshotInstanceDashboardTarget = InstanceDashboardTarget & {
  snapshotDetailLink?: string | null;
  snapshotId: string;
};

type InstanceDashboardMode = "live" | "snapshot";

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
 * Instance Dashboard live/snapshot endpoint를 소비하는 wide detail surface다.
 * selected instance evidence만 렌더링하고 application-owned state reference를 instance 판단으로 승격하지 않는다.
 */
export function InstanceDashboardSurface({
  mode,
  target,
}: {
  mode: InstanceDashboardMode;
  target: InstanceDashboardTarget | SnapshotInstanceDashboardTarget;
}) {
  const snapshotId = mode === "snapshot" && "snapshotId" in target ? target.snapshotId : null;
  const resourceKey = [
    "instance-dashboard",
    mode,
    target.projectId,
    target.applicationId,
    snapshotId ?? "live",
    target.instanceId,
  ].join("|");

  const requestDashboard = useCallback(
    async ({ authFetch, signal }: { authFetch: AuthFetch; signal: AbortSignal }) => {
      const dashboardPath = mode === "snapshot"
        ? validateSnapshotInstanceDashboardPath(
            buildSnapshotInstanceDashboardPath(
              target.projectId,
              target.applicationId,
              snapshotId ?? "",
              target.instanceId,
            ),
            target.projectId,
            target.applicationId,
            snapshotId ?? "",
            target.instanceId,
          )
        : validateLiveInstanceDashboardPath(
            buildLiveInstanceDashboardPath(target.projectId, target.applicationId, target.instanceId),
            target.projectId,
            target.applicationId,
            target.instanceId,
          );
      const response = await authFetch(dashboardPath, {
        ...NO_STORE_REQUEST_OPTIONS,
        signal,
      });
      const dashboard = guardInstanceDashboardReadModel(
        await readJsonResource<InstanceDashboardReadModel>(response),
        {
          applicationId: target.applicationId,
          instanceId: target.instanceId,
          mode,
          projectId: target.projectId,
          snapshotId: snapshotId ?? undefined,
        },
      );
      if (mode === "snapshot") {
        validateSnapshotInstanceDashboardPath(
          dashboard.links.self,
          target.projectId,
          target.applicationId,
          snapshotId ?? "",
          target.instanceId,
        );
        if (dashboard.links.applicationSnapshotDetail) {
          validateSnapshotDetailPath(dashboard.links.applicationSnapshotDetail, target.projectId, target.applicationId, snapshotId ?? "");
        }
      } else {
        validateLiveInstanceDashboardPath(dashboard.links.self, target.projectId, target.applicationId, target.instanceId);
      }
      return dashboard;
    },
    [mode, snapshotId, target.applicationId, target.instanceId, target.projectId],
  );

  const resource = useApiResource<InstanceDashboardReadModel>({
    dependencies: [resourceKey],
    request: requestDashboard,
    resourceKey,
  });

  const current = resource.resourceKey === resourceKey;
  const loading = !current || resource.loading;
  const error = current ? resource.error : null;
  const dashboard = current ? resource.data : null;

  if (loading) {
    return <InstanceDashboardMessage title="Instance Dashboard 로딩 중" body="선택한 인스턴스의 dashboard evidence를 불러오는 중입니다." />;
  }
  if (error) {
    return <InstanceDashboardError error={error} mode={mode} onReload={resource.reload} />;
  }
  if (!dashboard) {
    return <InstanceDashboardMessage title="Instance Dashboard 선택 대기" body="선택한 target의 detail을 아직 불러오지 않았습니다." />;
  }

  return (
    <div className="space-y-4 text-[13px] text-neutral-900">
      <InstanceContextNote dashboard={dashboard} mode={mode} />
      <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
        <ApplicationStateReferencePanel dashboard={dashboard} />
        <ReadSemanticsPanel dashboard={dashboard} />
      </div>
      <MetricGrid dashboard={dashboard} />
      <EndpointEvidencePanel dashboard={dashboard} />
      <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
        <ResourceEvidencePanel dashboard={dashboard} />
        <StarterConnectionPanel dashboard={dashboard} />
      </div>
    </div>
  );
}

function InstanceContextNote({
  dashboard,
  mode,
}: {
  dashboard: InstanceDashboardReadModel;
  mode: InstanceDashboardMode;
}) {
  const snapshotCopy = "selected Application Snapshot row window 기준 evidence입니다. Application Snapshot 자체는 dashboard_snapshots.read_model_json 저장본이고, selected instance evidence는 selected snapshot row metadata와 accepted_metric_buckets로 재구성합니다. late accepted metric이 포함될 수 있습니다. stored Application Snapshot state/evidence를 override, 검증, 대체하지 않습니다.";
  const liveCopy = "live context evidence입니다. 현재 query 시각 기준 recent_30_minutes accepted_metric_buckets에서 selected instance evidence를 봅니다. Application 판단을 새로 만들지 않고 application-owned state reference만 표시합니다.";
  return (
    <section className={`border p-3 ${mode === "snapshot" ? "border-amber-300 bg-amber-50 text-amber-950" : "border-neutral-200 bg-neutral-50 text-neutral-700"}`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <SectionLabel icon={Server}>Context note</SectionLabel>
          <p className="mt-2 max-w-3xl text-[12px] leading-5">
            {mode === "snapshot" ? snapshotCopy : liveCopy}
          </p>
          <div className="mt-2 text-[11px] text-neutral-500">
            {dashboard.application.name} · {dashboard.application.environment} · {dashboard.instance.instanceId}
          </div>
          <div className="mt-2 flex flex-wrap gap-2">
            <StatusBadge>{dashboard.schemaVersion}</StatusBadge>
            <StatusBadge className={statusBadgeClassName(mode)}>{`mode=${dashboard.mode}`}</StatusBadge>
            <StatusBadge>{humanizeStatusCode(dashboard.window.name)}</StatusBadge>
            <StatusBadge>{humanizeSourceCode(dashboard.readSemantics.source)}</StatusBadge>
          </div>
        </div>
        <div className="flex flex-col items-start gap-2 text-[12px] text-neutral-600 sm:items-end">
          <div>생성 시각 <span className="text-neutral-900">{formatOptionalDateTime(dashboard.generatedAt)}</span></div>
          <div>window <span className="text-neutral-900">{formatDateRange(dashboard.window.startUtc, dashboard.window.endUtc)}</span></div>
          {dashboard.snapshot && (
            <div>
              snapshot <span className="text-neutral-900">{dashboard.snapshot.snapshotId}</span> · {humanizeCaptureReason(dashboard.snapshot.captureReason)}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

function ApplicationStateReferencePanel({ dashboard }: { dashboard: InstanceDashboardReadModel }) {
  const ref = dashboard.applicationStateRef;
  return (
    <section className="border border-neutral-200 bg-white">
      <div className="border-b border-neutral-100 px-3 py-2.5">
        <SectionLabel icon={Activity}>Application state reference</SectionLabel>
      </div>
      <div className="grid grid-cols-2 gap-2 p-3 text-[11px] md:grid-cols-4 lg:grid-cols-2">
        <InfoCell label="lifecycleOwner" value={ref.lifecycleOwner} />
        <InfoCell label="source" value={humanizeSourceCode(ref.source)} />
        <InfoCell label="applicationStateCode" value={ref.applicationStateCode ? humanizeStatusCode(ref.applicationStateCode) : "참조 없음"} />
        <InfoCell label="contribution" value={dashboard.applicationContribution.level} />
        <InfoCell label="snapshotId" value={ref.snapshotId ?? "live reference"} />
        <InfoCell label="instance top-level state" value="없음" />
      </div>
      <p className="border-t border-neutral-100 p-3 text-[12px] text-neutral-500">
        Application Dashboard/Application Snapshot이 소유한 state 참조만 표시합니다. selected instance evidence는 이 판단을 대체하지 않습니다.
      </p>
    </section>
  );
}

function ReadSemanticsPanel({ dashboard }: { dashboard: InstanceDashboardReadModel }) {
  const semantics = dashboard.readSemantics;
  return (
    <section className="border border-neutral-200 bg-white">
      <div className="border-b border-neutral-100 px-3 py-2.5">
        <SectionLabel icon={ListChecks}>Read semantics</SectionLabel>
      </div>
      <div className="grid grid-cols-2 gap-2 p-3 text-[11px] md:grid-cols-4 lg:grid-cols-2">
        <InfoCell label="mode" value={dashboard.mode} />
        <InfoCell label="source" value={humanizeSourceCode(semantics.source)} />
        <InfoCell label="window" value={humanizeStatusCode(dashboard.window.name)} />
        <InfoCell label="windowSource" value={humanizeSourceCode(semantics.windowSource)} />
        <InfoCell label="snapshotRowSource" value={semantics.snapshotRowSource ?? "n/a"} />
        <InfoCell label="acceptedAtCutoffApplied" value={String(semantics.acceptedAtCutoffApplied)} />
        <InfoCell label="includesLateAcceptedMetrics" value={String(semantics.includesLateAcceptedMetrics)} />
        <InfoCell label="mayDifferFromStoredApplicationSnapshot" value={String(semantics.mayDifferFromStoredApplicationSnapshot)} />
        <InfoCell label="applicationSnapshotRecalculated" value={String(semantics.applicationSnapshotRecalculated)} />
        <InfoCell label="instanceEvidenceReconstructedFromMetrics" value={String(semantics.instanceEvidenceReconstructedFromMetrics)} />
        <InfoCell label="markerIsStateSource" value={String(semantics.markerIsStateSource)} />
      </div>
    </section>
  );
}

function MetricGrid({ dashboard }: { dashboard: InstanceDashboardReadModel }) {
  const red = dashboard.signals.red;
  return (
    <section className="grid grid-cols-1 gap-0 border border-neutral-200 bg-white md:grid-cols-4">
      <MetricCell label="Instance requests" note="selected instance scope" value={formatCount(red.requestCount)} />
      <MetricCell label="Server errors" note="5xx server error semantic" value={formatCount(red.errorCount)} />
      <MetricCell label="Slow >500ms" note="server-provided RED evidence" value={formatNullableRatio(red.slowShareOver500ms)} />
      <MetricCell label="Observation" note={dashboard.observationStatus.reason ? humanizeStatusCode(dashboard.observationStatus.reason) : "evidence availability"} value={humanizeStatusCode(dashboard.observationStatus.code)} last />
    </section>
  );
}

function EndpointEvidencePanel({ dashboard }: { dashboard: InstanceDashboardReadModel }) {
  const evidence = dashboard.endpointEvidence;
  const maxRequestCount = Math.max(0, ...evidence.items.map((item) => item.requestCount));
  const maxErrorRate = Math.max(0, ...evidence.items.map((item) => item.errorRate ?? 0));
  return (
    <section className="border border-neutral-200 bg-white">
      <div className="flex items-center justify-between gap-3 border-b border-neutral-100 px-3 py-2.5">
        <div>
          <SectionLabel icon={Server}>Endpoint evidence on selected instance</SectionLabel>
          <p className="mt-1 text-[12px] text-neutral-500">
            server order를 보존하고 selected instance 관측 여부만 표시합니다. endpoint별 slow/bucket 값은 read model이 제공하지 않으면 만들지 않습니다.
          </p>
        </div>
        <StatusBadge className={statusBadgeClassName(evidence.status)}>{humanizeStatusCode(evidence.status)}</StatusBadge>
      </div>
      <div className="grid grid-cols-2 gap-2 border-b border-neutral-100 p-3 text-[11px] md:grid-cols-4">
        <InfoCell label="source" value={humanizeSourceCode(evidence.source)} />
        <InfoCell label="scope" value={humanizeStatusCode(evidence.scope)} />
        <InfoCell label="selection" value={humanizeStatusCode(evidence.selectionPolicy)} />
        <InfoCell label="display order" value={humanizeStatusCode(evidence.displayOrderingPolicy)} />
      </div>
      {evidence.items.length === 0 ? (
        <div className="p-3 text-[12px] leading-5 text-neutral-500">
          {instanceEndpointEvidenceEmptyCopy(dashboard)}
        </div>
      ) : (
        <ul className="divide-y divide-neutral-100">
          {evidence.items.map((item) => (
            <EndpointEvidenceRow
              item={item}
              key={`${item.localDisplayOrder}-${item.endpointKey}`}
              maxErrorRate={maxErrorRate}
              maxRequestCount={maxRequestCount}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function EndpointEvidenceRow({
  item,
  maxErrorRate,
  maxRequestCount,
}: {
  item: InstanceDashboardEndpointEvidenceItem;
  maxErrorRate: number;
  maxRequestCount: number;
}) {
  return (
    <li className="p-3">
      <div className="grid gap-3 md:grid-cols-[32px_minmax(0,1fr)_minmax(220px,0.9fr)] md:items-start">
        <span className="grid h-8 w-8 place-items-center border border-neutral-900 bg-neutral-900 text-[12px] text-white tabular-nums">
          {item.localDisplayOrder}
        </span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <div className="truncate font-medium text-neutral-950">{item.endpointKey}</div>
            <StatusBadge className={instanceEndpointBadgeClassName(item)}>{instanceEndpointBadgeText(item)}</StatusBadge>
          </div>
          <div className="mt-0.5 text-[11px] text-neutral-500">
            {endpointPresenceText(item.presenceOnSelectedInstance)} · 앱 근거 {item.relatedApplicationEndpointEvidenceRef ?? "연결 없음"}
            {item.reason ? ` · ${humanizeStatusCode(item.reason)}` : ""}
          </div>
        </div>
        <div className="grid gap-1.5 text-[11px] text-neutral-500">
          <EndpointMetricBar
            label="request"
            tone="neutral"
            value={formatCount(item.requestCount)}
            width={ratioWidth(item.requestCount, maxRequestCount)}
          />
          <EndpointMetricBar
            label="error"
            tone="danger"
            value={`${formatCount(item.errorCount)} · ${formatNullableRatio(item.errorRate)}`}
            width={ratioWidth(item.errorRate ?? 0, maxErrorRate)}
          />
          <EndpointMetricBar label="slow >500ms" tone="hot" unavailable value="미제공" width={0} />
        </div>
      </div>
      <div className="mt-3 grid grid-cols-2 gap-2 text-[11px] text-neutral-500 md:grid-cols-4">
        <InfoCell label="duration buckets" value="미제공" />
        <InfoCell label="slowShare" value="미제공" />
        <InfoCell label="display order" value="server order 유지" />
        <InfoCell label="status" value={humanizeStatusCode(item.status)} />
      </div>
    </li>
  );
}

function ResourceEvidencePanel({ dashboard }: { dashboard: InstanceDashboardReadModel }) {
  const evidence = dashboard.resourceEvidence;
  return (
    <section className="border border-neutral-200 bg-white">
      <div className="flex items-center justify-between gap-3 border-b border-neutral-100 px-3 py-2.5">
        <SectionLabel icon={Gauge}>Resource evidence</SectionLabel>
        <StatusBadge className={statusBadgeClassName(evidence.status)}>{humanizeStatusCode(evidence.status)}</StatusBadge>
      </div>
      {evidence.items.length === 0 ? (
        <div className="p-3 text-[12px] text-neutral-500">resource evidence가 없거나 selected window에서 해석할 수 없습니다.</div>
      ) : (
        <ul>
          {evidence.items.map((item) => (
            <ResourceEvidenceRow item={item} key={item.resourceKey} />
          ))}
        </ul>
      )}
    </section>
  );
}

function ResourceEvidenceRow({ item }: { item: InstanceDashboardResourceEvidenceItem }) {
  return (
    <li className="border-b border-neutral-100 p-3 last:border-b-0">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-neutral-900">{humanizeStatusCode(item.resourceKey)}</div>
          <div className="mt-0.5 text-[11px] text-neutral-500">{resourcePatternText(item)}</div>
        </div>
        <StatusBadge className={statusBadgeClassName(item.status)}>{humanizeStatusCode(item.status)}</StatusBadge>
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500">
        <InfoCell label="usage" value={formatNullableRatio(item.usage)} />
        <InfoCell label="threshold" value={formatNullableRatio(item.threshold)} />
        <InfoCell label="observedAt" value={formatOptionalDateTime(item.observedAt)} />
        <InfoCell label="request symptom" value={item.requestSymptomPresent ? "함께 관찰됨" : "함께 관찰되지 않음"} />
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

function instanceEndpointBadgeText(item: InstanceDashboardEndpointEvidenceItem): string {
  if (item.localDisplayOrder === 1) {
    return "FIRST LOOK";
  }
  return "ATTENTION";
}

function instanceEndpointBadgeClassName(item: InstanceDashboardEndpointEvidenceItem): string {
  if (item.localDisplayOrder === 1) {
    return "border-neutral-900 bg-neutral-900 text-white";
  }
  if (item.errorCount > 0 || (item.errorRate ?? 0) > 0) {
    return "border-red-200 bg-red-50 text-red-700";
  }
  return "border-neutral-300 bg-neutral-50 text-neutral-700";
}

function instanceEndpointEvidenceEmptyCopy(dashboard: InstanceDashboardReadModel): string {
  const evidence = dashboard.endpointEvidence;
  const red = dashboard.signals.red;
  const hasRedSignal = red.errorCount > 0 || (red.errorRate ?? 0) > 0 || (red.slowShareOver500ms ?? 0) > 0;
  if (evidence.reason) {
    return `${humanizeStatusCode(evidence.reason)} · endpoint evidence 부재를 selected instance 정상으로 해석하지 않습니다.`;
  }
  if (hasRedSignal) {
    return "selected instance RED signal은 관찰됐지만 endpointEvidence.items가 비어 있습니다. endpoint breakdown 미수집 또는 read model evidence 제한으로 표시합니다.";
  }
  return "selected instance에서 표시할 endpoint evidence가 없습니다. raw path/query나 endpoint priority를 client에서 만들지 않습니다.";
}

function ratioWidth(value: number, max: number): number {
  if (!Number.isFinite(value) || !Number.isFinite(max) || value <= 0 || max <= 0) {
    return 0;
  }
  return Math.min(100, Math.max(6, (value / max) * 100));
}

function StarterConnectionPanel({ dashboard }: { dashboard: InstanceDashboardReadModel }) {
  const starter = dashboard.starterConnection;
  return (
    <section className="border border-neutral-200 bg-white">
      <div className="flex items-center justify-between gap-3 border-b border-neutral-100 px-3 py-2.5">
        <SectionLabel icon={Radio}>Starter connection</SectionLabel>
        <StatusBadge className={statusBadgeClassName(starter.lastHeartbeatStatus)}>{humanizeStatusCode(starter.lastHeartbeatStatus)}</StatusBadge>
      </div>
      <div className="grid grid-cols-2 gap-2 p-3 text-[11px] md:grid-cols-4 lg:grid-cols-2">
        <InfoCell label="source" value={humanizeSourceCode(starter.statusSource)} />
        <InfoCell label="lastHeartbeatAt" value={formatOptionalDateTime(starter.lastHeartbeatAt)} />
        <InfoCell label="freshness" value={humanizeStatusCode(starter.freshnessLabel)} />
        <InfoCell label="stateImpact" value={humanizeStatusCode(starter.stateImpact)} />
      </div>
      <p className="border-t border-neutral-100 p-3 text-[12px] text-neutral-500">
        heartbeat는 metric state, observationStatus, applicationState를 직접 바꾸지 않는 control-plane 정보입니다.
      </p>
    </section>
  );
}

function MetricCell({
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
    <div className={`min-h-28 p-4 ${last ? "" : "border-b border-neutral-100 md:border-b-0 md:border-r"}`}>
      <div className="text-[11px] uppercase text-neutral-500">{label}</div>
      <div className="mt-2 text-[22px] leading-none text-neutral-950">{value}</div>
      <div className="mt-2 text-[11px] text-neutral-500">{note}</div>
    </div>
  );
}

function InfoCell({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 border border-neutral-200 bg-white p-2">
      <div className="text-neutral-500">{label}</div>
      <div className="truncate text-neutral-900">{value}</div>
    </div>
  );
}

function InstanceDashboardError({
  error,
  mode,
  onReload,
}: {
  error: Error;
  mode: InstanceDashboardMode;
  onReload: () => void;
}) {
  const copy = instanceDashboardErrorCopy(error, mode);
  return (
    <div className="border border-neutral-200 bg-white p-4 text-[12px]">
      <div className="text-neutral-900">{copy.title}</div>
      <div className="mt-1 text-neutral-500">{copy.body}</div>
      <Button variant="outline" size="sm" className="mt-3 gap-2 border-neutral-300" onClick={onReload}>
        <RefreshCw className="h-3.5 w-3.5" strokeWidth={1.5} /> 다시 시도
      </Button>
    </div>
  );
}

function InstanceDashboardMessage({ body, title }: { body: string; title: string }) {
  return (
    <div className="border border-neutral-200 bg-white p-4 text-[12px]">
      <div className="text-neutral-900">{title}</div>
      <div className="mt-1 text-neutral-500">{body}</div>
    </div>
  );
}

function instanceDashboardErrorCopy(error: Error, mode: InstanceDashboardMode): { title: string; body: string } {
  if (error instanceof AuthRequiredError) {
    return {
      title: "인증 필요",
      body: error.message,
    };
  }
  if (error instanceof ApiRequestError && error.status === 404) {
    return mode === "snapshot"
      ? {
          title: "Snapshot instance evidence 없음",
          body: "보관 기간이 지났거나 selected snapshot/instance evidence를 찾을 수 없습니다. live dashboard/current accepted bucket으로 복원하지 않습니다.",
        }
      : {
          title: "Live instance evidence 없음",
          body: "선택한 Project/Application/Instance의 live dashboard evidence를 찾을 수 없습니다. 다른 source로 상태를 단정하지 않습니다.",
        };
  }
  if (error instanceof ApiRequestError && !error.status) {
    return {
      title: "Link 검증 실패",
      body: "현재 선택한 Project/Application/Instance/Snapshot context와 endpoint path가 맞지 않습니다.",
    };
  }
  return {
    title: "Instance Dashboard 로드 실패",
    body: "선택한 인스턴스의 dashboard evidence를 불러오지 못했습니다.",
  };
}

function endpointPresenceText(value: string): string {
  switch (value) {
    case "observed":
      return "selected instance에서 관찰됨";
    case "not_observed":
      return "selected instance에서 관찰되지 않음";
    case "insufficient":
      return "selected instance evidence 제한";
    default:
      return humanizeStatusCode(value);
  }
}

function resourcePatternText(item: InstanceDashboardResourceEvidenceItem): string {
  if (item.patternContribution === "shared_resource_pressure_pattern" && item.requestSymptomPresent) {
    return "요청 증상과 resource 압박이 같은 window에서 관찰됩니다.";
  }
  if (item.patternContribution === "attention_only") {
    return "resource threshold hit는 있으나 요청 오류/지연 증상이 함께 관찰되지는 않았습니다.";
  }
  return "resource hint는 request symptom과 함께 확인합니다.";
}
