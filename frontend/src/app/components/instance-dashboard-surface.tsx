import { useCallback, useMemo, useState } from "react";
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
type EndpointEvidenceSort = "requestCountDesc" | "errorRateDesc" | "slowShareOver500msDesc";

const ENDPOINT_TABLE_LIMIT = 10;

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
  const [sort, setSort] = useState<EndpointEvidenceSort>("requestCountDesc");
  const visibleItems = useMemo(
    () => sortedEndpointEvidenceItems(evidence.items, sort).slice(0, ENDPOINT_TABLE_LIMIT),
    [evidence.items, sort],
  );
  const sortState = useMemo(
    () => endpointSortState(evidence.items, visibleItems, sort),
    [evidence.items, sort, visibleItems],
  );
  return (
    <section className="border border-neutral-200 bg-white">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-neutral-100 px-3 py-2.5">
        <div>
          <SectionLabel icon={Server}>NORMALIZED ENDPOINT EVIDENCE TABLE</SectionLabel>
          <p className="mt-1 text-[12px] text-neutral-500">
            selected instance의 normalized route evidence를 탐색합니다. application state나 endpoint priority를 새로 판정하지 않습니다.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <select
            aria-label="endpoint table sort"
            className="h-8 border border-neutral-300 bg-white px-2 text-[11px] uppercase text-neutral-700"
            onChange={(event) => setSort(event.target.value as EndpointEvidenceSort)}
            value={sort}
          >
            <option value="requestCountDesc">requestCount desc</option>
            <option value="errorRateDesc">errorRate desc</option>
            <option value="slowShareOver500msDesc">slowShareOver500ms desc</option>
          </select>
          <Button className="h-8 border-neutral-300 px-2 text-[11px] uppercase" disabled size="sm" type="button" variant="outline">
            max {ENDPOINT_TABLE_LIMIT}
          </Button>
          <StatusBadge className={sortState.badgeClassName}>{sortState.badgeText}</StatusBadge>
          <StatusBadge className={statusBadgeClassName(evidence.status)}>{humanizeStatusCode(evidence.status)}</StatusBadge>
        </div>
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
        <div className="overflow-x-auto p-3">
          <div className="mb-2 flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-neutral-500">
            <span>source: accepted_metric_buckets.endpoints_json · raw path/query/per-request sample 없음 · endpoint p95/p99 rollup 없음</span>
            <span>{sortState.description}</span>
          </div>
          <table className="w-full min-w-[980px] border-collapse text-left text-[11px]">
            <thead>
              <tr className="border-y border-neutral-200 bg-neutral-50 text-neutral-500">
                <th className="px-2 py-2 font-normal uppercase">ENDPOINTKEY / NORMALIZED ROUTE</th>
                <th className="px-2 py-2 text-right font-normal uppercase">REQUESTCOUNT</th>
                <th className="px-2 py-2 text-right font-normal uppercase">ERRORCOUNT</th>
                <th className="px-2 py-2 text-right font-normal uppercase">ERRORRATE</th>
                <th className="px-2 py-2 text-right font-normal uppercase">SLOWCOUNT &gt;500MS</th>
                <th className="px-2 py-2 text-right font-normal uppercase">SLOWSHARE &gt;500MS</th>
                <th className="px-2 py-2 font-normal uppercase">ENDPOINT DURATION BUCKET DISTRIBUTION</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {visibleItems.map((item) => (
                <EndpointEvidenceTableRow item={item} key={`${item.localDisplayOrder}-${item.endpointKey}`} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function EndpointEvidenceTableRow({ item }: { item: InstanceDashboardEndpointEvidenceItem }) {
  return (
    <tr className="align-top text-neutral-700">
      <td className="px-2 py-2">
        <div className="font-medium text-neutral-950">{item.endpointKey}</div>
        <div className="mt-0.5 text-neutral-500">
          {item.method} · {item.route} · {endpointPresenceText(item.presenceOnSelectedInstance)}
        </div>
        <div className="mt-1 flex flex-wrap gap-1.5">
          <StatusBadge className={endpointRowStatusClassName(item)}>{humanizeStatusCode(item.status)}</StatusBadge>
          <StatusBadge>#{item.localDisplayOrder}</StatusBadge>
        </div>
      </td>
      <td className="px-2 py-2 text-right tabular-nums text-neutral-900">{formatCount(item.requestCount)}</td>
      <td className={`px-2 py-2 text-right tabular-nums ${item.errorCount > 0 ? "text-red-700" : "text-neutral-900"}`}>
        {formatCount(item.errorCount)}
      </td>
      <td className={`px-2 py-2 text-right tabular-nums ${hasPositiveRatio(item.errorRate) ? "text-red-700" : "text-neutral-900"}`}>
        {formatNullableRatio(item.errorRate)}
      </td>
      <td className={`px-2 py-2 text-right tabular-nums ${hasPositiveCount(item.slowCountOver500ms) ? "text-amber-800" : "text-neutral-900"}`}>
        {formatNullableCount(item.slowCountOver500ms)}
      </td>
      <td className={`px-2 py-2 text-right tabular-nums ${hasPositiveRatio(item.slowShareOver500ms) ? "text-amber-800" : "text-neutral-900"}`}>
        {formatNullableRatio(item.slowShareOver500ms)}
      </td>
      <td className="px-2 py-2">
        <EndpointDurationDistribution item={item} />
      </td>
    </tr>
  );
}

function EndpointDurationDistribution({ item }: { item: InstanceDashboardEndpointEvidenceItem }) {
  const segments = endpointDurationSegments(item.durationBuckets);
  if (!segments) {
    return (
      <div className="text-neutral-500">
        확인할 수 없음 · 100ms/500ms boundary 미제공
      </div>
    );
  }
  return (
    <div className="grid gap-1.5">
      {segments.map((segment) => (
        <div className="grid grid-cols-[64px_minmax(96px,1fr)_48px] items-center gap-2" key={segment.key}>
          <span className="truncate text-neutral-500">{segment.label}</span>
          <span className="h-1.5 bg-neutral-100">
            <span className={`block h-full ${segment.className}`} style={{ width: `${segment.width}%` }} />
          </span>
          <span className="text-right tabular-nums text-neutral-700">{formatCount(segment.count)}</span>
        </div>
      ))}
    </div>
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

function sortedEndpointEvidenceItems(
  items: InstanceDashboardEndpointEvidenceItem[],
  sort: EndpointEvidenceSort,
): InstanceDashboardEndpointEvidenceItem[] {
  return [...items].sort((left, right) => {
    const primary = sort === "errorRateDesc"
      ? compareNullableNumberDesc(left.errorRate, right.errorRate)
      : sort === "slowShareOver500msDesc"
        ? compareNullableNumberDesc(left.slowShareOver500ms, right.slowShareOver500ms)
        : compareNumberDesc(left.requestCount, right.requestCount);
    if (primary !== 0) {
      return primary;
    }
    const secondary = compareEndpointSortTieBreaker(left, right, sort);
    return secondary !== 0 ? secondary : left.localDisplayOrder - right.localDisplayOrder;
  });
}

function compareEndpointSortTieBreaker(
  left: InstanceDashboardEndpointEvidenceItem,
  right: InstanceDashboardEndpointEvidenceItem,
  sort: EndpointEvidenceSort,
): number {
  if (sort === "errorRateDesc") {
    return (
      compareNumberDesc(left.errorCount, right.errorCount) ||
      compareNumberDesc(left.requestCount, right.requestCount) ||
      compareNullableNumberDesc(left.slowShareOver500ms, right.slowShareOver500ms) ||
      left.endpointKey.localeCompare(right.endpointKey)
    );
  }
  if (sort === "slowShareOver500msDesc") {
    return (
      compareNullableNumberDesc(left.slowCountOver500ms, right.slowCountOver500ms) ||
      compareNumberDesc(left.requestCount, right.requestCount) ||
      compareNullableNumberDesc(left.errorRate, right.errorRate) ||
      left.endpointKey.localeCompare(right.endpointKey)
    );
  }
  return (
    compareNullableNumberDesc(left.errorRate, right.errorRate) ||
    compareNullableNumberDesc(left.slowShareOver500ms, right.slowShareOver500ms) ||
    compareNumberDesc(left.errorCount, right.errorCount) ||
    left.endpointKey.localeCompare(right.endpointKey)
  );
}

type EndpointSortState = {
  badgeClassName: string;
  badgeText: string;
  description: string;
};

function endpointSortState(
  items: InstanceDashboardEndpointEvidenceItem[],
  visibleItems: InstanceDashboardEndpointEvidenceItem[],
  sort: EndpointEvidenceSort,
): EndpointSortState {
  if (items.length <= 1) {
    return {
      badgeClassName: "border-neutral-300 bg-neutral-50 text-neutral-600",
      badgeText: "비교 없음",
      description: `정렬: ${endpointSortLabel(sort)} · 비교할 endpoint가 ${items.length}개입니다.`,
    };
  }

  const providedItems = [...items]
    .sort((left, right) => left.localDisplayOrder - right.localDisplayOrder)
    .slice(0, ENDPOINT_TABLE_LIMIT);
  const sameOrder = sameEndpointOrder(providedItems, visibleItems);
  if (!sameOrder) {
    return {
      badgeClassName: "border-emerald-300 bg-emerald-50 text-emerald-700",
      badgeText: "정렬 적용",
      description: `정렬: ${endpointSortLabel(sort)} · ${formatCount(visibleItems.length)}개 행에 적용됨`,
    };
  }

  const distinctPrimaryValues = distinctEndpointPrimarySortValues(items, sort);
  const reason = distinctPrimaryValues <= 1
    ? `${endpointSortMetricLabel(sort)} 값이 모두 같아 행 순서가 바뀌지 않습니다.`
    : `server 제공 순서가 이미 ${endpointSortLabel(sort)} 결과와 같습니다.`;
  return {
    badgeClassName: "border-neutral-300 bg-neutral-50 text-neutral-600",
    badgeText: distinctPrimaryValues <= 1 ? "동일값" : "동일 순서",
    description: `정렬: ${endpointSortLabel(sort)} · ${reason}`,
  };
}

function endpointSortLabel(sort: EndpointEvidenceSort): string {
  if (sort === "errorRateDesc") {
    return "errorRate desc";
  }
  if (sort === "slowShareOver500msDesc") {
    return "slowShare >500ms desc";
  }
  return "requestCount desc";
}

function endpointSortMetricLabel(sort: EndpointEvidenceSort): string {
  if (sort === "errorRateDesc") {
    return "errorRate";
  }
  if (sort === "slowShareOver500msDesc") {
    return "slowShare >500ms";
  }
  return "requestCount";
}

function distinctEndpointPrimarySortValues(
  items: InstanceDashboardEndpointEvidenceItem[],
  sort: EndpointEvidenceSort,
): number {
  const values = items
    .map((item) => endpointPrimarySortValue(item, sort))
    .filter((value): value is number => value !== null && Number.isFinite(value));
  return new Set(values.map((value) => value.toString())).size;
}

function endpointPrimarySortValue(
  item: InstanceDashboardEndpointEvidenceItem,
  sort: EndpointEvidenceSort,
): number | null {
  if (sort === "errorRateDesc") {
    return item.errorRate;
  }
  if (sort === "slowShareOver500msDesc") {
    return item.slowShareOver500ms;
  }
  return item.requestCount;
}

function sameEndpointOrder(
  providedItems: InstanceDashboardEndpointEvidenceItem[],
  visibleItems: InstanceDashboardEndpointEvidenceItem[],
): boolean {
  if (providedItems.length !== visibleItems.length) {
    return false;
  }
  return providedItems.every((item, index) => {
    const visibleItem = visibleItems[index];
    return visibleItem?.endpointKey === item.endpointKey
      && visibleItem.localDisplayOrder === item.localDisplayOrder;
  });
}

function compareNumberDesc(left: number, right: number): number {
  return right - left;
}

function compareNullableNumberDesc(left: number | null, right: number | null): number {
  const leftMissing = left === null || !Number.isFinite(left);
  const rightMissing = right === null || !Number.isFinite(right);
  if (leftMissing && rightMissing) {
    return 0;
  }
  if (leftMissing) {
    return 1;
  }
  if (rightMissing) {
    return -1;
  }
  return right - left;
}

function endpointRowStatusClassName(item: InstanceDashboardEndpointEvidenceItem): string {
  if (item.errorCount > 0 || (item.errorRate ?? 0) > 0) {
    return "border-red-200 bg-red-50 text-red-700";
  }
  if ((item.slowCountOver500ms ?? 0) > 0 || (item.slowShareOver500ms ?? 0) > 0) {
    return "border-amber-200 bg-amber-50 text-amber-800";
  }
  return statusBadgeClassName(item.status);
}

type EndpointDurationSegment = {
  className: string;
  count: number;
  key: string;
  label: string;
  width: number;
};

function endpointDurationSegments(
  buckets: InstanceDashboardEndpointEvidenceItem["durationBuckets"],
): EndpointDurationSegment[] | null {
  if (!buckets || buckets.length === 0) {
    return null;
  }
  const sortedBuckets = [...buckets].sort((left, right) => left.leMs - right.leMs);
  const total = sortedBuckets[sortedBuckets.length - 1]?.count ?? 0;
  const atOrBelow100 = sortedBuckets.find((bucket) => bucket.leMs === 100)?.count;
  const atOrBelow500 = sortedBuckets.find((bucket) => bucket.leMs === 500)?.count;
  if (total <= 0 || atOrBelow100 === undefined || atOrBelow500 === undefined || atOrBelow500 < atOrBelow100) {
    return null;
  }
  return [
    {
      className: "bg-neutral-500",
      count: atOrBelow100,
      key: "lte-100",
      label: "<=100ms",
      width: segmentWidth(atOrBelow100, total),
    },
    {
      className: "bg-neutral-400",
      count: Math.max(0, atOrBelow500 - atOrBelow100),
      key: "100-500",
      label: "100-500ms",
      width: segmentWidth(Math.max(0, atOrBelow500 - atOrBelow100), total),
    },
    {
      className: "bg-amber-700",
      count: Math.max(0, total - atOrBelow500),
      key: "gt-500",
      label: ">500ms",
      width: segmentWidth(Math.max(0, total - atOrBelow500), total),
    },
  ];
}

function segmentWidth(count: number, total: number): number {
  if (count <= 0 || total <= 0) {
    return 0;
  }
  return Math.min(100, Math.max(4, (count / total) * 100));
}

function hasPositiveCount(value: number | null): boolean {
  return value !== null && value > 0;
}

function hasPositiveRatio(value: number | null): boolean {
  return value !== null && value > 0;
}

function formatNullableCount(value: number | null): string {
  return value === null ? "미제공" : formatCount(value);
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
