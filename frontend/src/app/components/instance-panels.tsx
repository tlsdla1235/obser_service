import { useCallback, useEffect, useMemo, useState } from "react";
import type { LucideIcon } from "lucide-react";
import { Activity, ArrowLeft, Gauge, History, RefreshCw, Server } from "lucide-react";
import { ApiRequestError, AuthRequiredError, NO_STORE_REQUEST_OPTIONS, readJsonResource } from "../lib/api";
import { type AuthFetch } from "../lib/auth";
import { useApiResource } from "../lib/use-api-resource";
import {
  buildInstanceSnapshotTrendPath,
  formatCount,
  formatNullableRatio,
  formatOptionalDateTime,
  formatRatio,
  histogramBarWidth,
  statusBadgeClassName,
  trendPathWithPreset,
  validateInstanceEvidencePath,
  validateInstanceSnapshotTrendPath,
} from "../lib/read-model-adapters";
import type {
  EvidenceEndpointEvidenceItem,
  EvidenceHistogramDistribution,
  EvidencePercentilePoint,
  EvidenceResourceHints,
  InstanceEvidenceReadModel,
  InstanceSnapshotTrendReadModel,
  TrendEndpointEvidenceRef,
  TrendPoint,
  TrendPreset,
} from "../lib/read-model-types";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "./ui/sheet";
import { Button } from "./ui/button";
import { SnapshotDetailSurface, type SnapshotDetailTarget } from "./snapshot-detail-surface";

export type InstancePanelTarget = {
  applicationId: string;
  evidenceLink: string;
  instanceId: string;
  instanceName: string;
  projectId: string;
  snapshotTrendLink?: string | null;
};

type View =
  | { kind: "closed" }
  | { kind: "evidence"; target: InstancePanelTarget }
  | { kind: "trend"; target: InstancePanelTarget };

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
 * Instance drawer의 evidence/trend 선택 state를 관리한다.
 * target에는 dashboard read model에서 받은 evidence link와 selected context id만 보관한다.
 */
export function useInstanceView() {
  const [view, setView] = useState<View>({ kind: "closed" });
  return {
    view,
    openEvidence: (target: InstancePanelTarget) => setView({ kind: "evidence", target }),
    openTrend: (target: InstancePanelTarget) => setView({ kind: "trend", target }),
    close: () => setView({ kind: "closed" }),
  };
}

export function InstancePanels({
  onClose,
  onOpenEvidence,
  onOpenTrend,
  view,
}: {
  onClose: () => void;
  onOpenEvidence: (target: InstancePanelTarget) => void;
  onOpenTrend: (target: InstancePanelTarget) => void;
  view: View;
}) {
  const target = view.kind === "closed" ? null : view.target;

  return (
    <Sheet open={view.kind !== "closed"} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-[660px] p-0 bg-white text-neutral-900">
        {view.kind === "evidence" && target && (
          <InstanceEvidenceView
            target={target}
            onSwitch={(snapshotTrendLink) => onOpenTrend({ ...target, snapshotTrendLink })}
          />
        )}
        {view.kind === "trend" && target && (
          <InstanceTrendView
            target={target}
            onSwitch={() => onOpenEvidence(target)}
            onOpenTrend={onOpenTrend}
          />
        )}
      </SheetContent>
    </Sheet>
  );
}

function InstanceEvidenceView({
  onSwitch,
  target,
}: {
  onSwitch: (snapshotTrendLink: string) => void;
  target: InstancePanelTarget;
}) {
  const evidenceResourceKey = `${target.projectId}|${target.applicationId}|${target.instanceId}|${target.evidenceLink}`;
  const requestEvidence = useCallback(
    async ({ authFetch, signal }: { authFetch: AuthFetch; signal: AbortSignal }) => {
      const evidencePath = validateInstanceEvidencePath(
        target.evidenceLink,
        target.projectId,
        target.applicationId,
        target.instanceId,
      );
      const response = await authFetch(evidencePath, {
        ...NO_STORE_REQUEST_OPTIONS,
        signal,
      });
      const model = await readJsonResource<InstanceEvidenceReadModel>(response);
      validateEvidenceContext(model, target);
      return model;
    },
    [target],
  );

  const resource = useApiResource<InstanceEvidenceReadModel>({
    dependencies: [evidenceResourceKey],
    request: requestEvidence,
    resourceKey: evidenceResourceKey,
  });

  const current = resource.resourceKey === evidenceResourceKey;
  const loading = !current || resource.loading;
  const error = current ? resource.error : null;
  const evidence = current ? resource.data : null;

  return (
    <div>
      <InstanceHeader
        icon={Server}
        title={target.instanceName}
        description="Dashboard instance handoff의 evidence link만 사용해 bounded evidence bundle을 불러옵니다."
        actionLabel="Trend"
        actionIcon={History}
        actionDisabled={!evidence?.links.snapshotTrend}
        onAction={() => {
          if (evidence?.links.snapshotTrend) {
            onSwitch(evidence.links.snapshotTrend);
          }
        }}
      />
      <div className="p-5 space-y-4 text-[13px]">
        {loading && <PanelMessage title="Evidence 로딩 중" body="server-provided evidence link를 검증한 뒤 호출하는 중입니다." />}
        {error && <InstanceResourceError error={error} onReload={resource.reload} />}
        {!loading && !error && evidence && (
          <EvidenceReadyView
            evidence={evidence}
            onOpenTrend={() => {
              if (evidence.links.snapshotTrend) {
                onSwitch(evidence.links.snapshotTrend);
              }
            }}
          />
        )}
      </div>
    </div>
  );
}

function EvidenceReadyView({
  evidence,
  onOpenTrend,
}: {
  evidence: InstanceEvidenceReadModel;
  onOpenTrend: () => void;
}) {
  return (
    <>
      <div className="border border-neutral-900 bg-white p-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="text-[11px] uppercase text-neutral-500">identity</div>
            <div className="mt-1 text-neutral-900">{evidence.instance.instanceName}</div>
            <div className="mt-0.5 text-[12px] text-neutral-500">
              {evidence.application.name} · {evidence.application.environment}
            </div>
          </div>
          <Button
            variant="outline"
            size="sm"
            className="gap-2 border-neutral-300"
            disabled={!evidence.links.snapshotTrend}
            onClick={onOpenTrend}
          >
            <History className="h-3.5 w-3.5" strokeWidth={1.5} /> Trend
          </Button>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-2 text-[11px] md:grid-cols-4">
          <InfoCell label="generated" value={formatOptionalDateTime(evidence.generatedAt)} />
          <InfoCell label="first seen" value={formatOptionalDateTime(evidence.instance.firstSeenAt)} />
          <InfoCell label="last seen" value={formatOptionalDateTime(evidence.instance.lastSeenAt)} />
          <InfoCell label="trend link" value={evidence.links.snapshotTrend ? "server link 있음" : "server link 없음"} />
        </div>
      </div>
      <AxisGrid evidence={evidence} />
      <TriageContributionPanel evidence={evidence} />
      <PercentileSeriesPanel points={evidence.starterPercentiles.points} evidence={evidence} />
      <EvidenceHistogramPanel histogram={evidence.histogramDistribution} />
      <ResourceHintsPanel hints={evidence.resourceHints} />
      <EndpointEvidencePanel items={evidence.endpointEvidence.items} evidence={evidence} />
    </>
  );
}

function AxisGrid({ evidence }: { evidence: InstanceEvidenceReadModel }) {
  return (
    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
      <div className="border border-neutral-200 bg-white p-3">
        <SectionLabel icon={Activity}>Metric data axis</SectionLabel>
        <div className="mt-2 grid grid-cols-2 gap-2 text-[11px]">
          <InfoCell label="source" value={evidence.metricData.statusSource} />
          <InfoCell label="freshness" value={evidence.metricData.freshnessLabel} />
          <InfoCell label="sample" value={evidence.metricData.sampleReadiness} />
          <InfoCell label="reason" value={evidence.metricData.reason ?? "reason 없음"} />
          <InfoCell label="requests" value={formatCount(evidence.metricData.requestCount)} />
          <InfoCell label="error rate" value={formatRatio(evidence.metricData.errorRate)} />
        </div>
      </div>
      <div className="border border-neutral-200 bg-white p-3">
        <SectionLabel icon={Gauge}>Starter connection axis</SectionLabel>
        <div className="mt-2 grid grid-cols-2 gap-2 text-[11px]">
          <InfoCell label="source" value={evidence.starterConnection.statusSource} />
          <InfoCell label="heartbeat" value={evidence.starterConnection.lastHeartbeatStatus} />
          <InfoCell label="freshness" value={evidence.starterConnection.freshnessLabel} />
          <InfoCell label="meaning" value={evidence.starterConnection.connectionMeaning} />
          <InfoCell label="state impact" value={evidence.starterConnection.stateImpact} />
          <InfoCell label="last heartbeat" value={formatOptionalDateTime(evidence.starterConnection.lastHeartbeatAt)} />
        </div>
      </div>
    </div>
  );
}

function TriageContributionPanel({ evidence }: { evidence: InstanceEvidenceReadModel }) {
  const contribution = evidence.applicationTriageContribution;
  return (
    <div className="border border-neutral-200 bg-white p-3">
      <SectionLabel icon={Activity}>Application triage contribution</SectionLabel>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] md:grid-cols-4">
        <InfoCell label="status" value={contribution.status} />
        <InfoCell label="contributed" value={contribution.contributed ? "true" : "false"} />
        <InfoCell label="rules" value={contribution.relatedRuleIds.join(", ") || "related rule 없음"} />
        <InfoCell label="reason" value={contribution.reason ?? "기여 evidence 없음"} />
      </div>
    </div>
  );
}

function PercentileSeriesPanel({
  evidence,
  points,
}: {
  evidence: InstanceEvidenceReadModel;
  points: EvidencePercentilePoint[];
}) {
  return (
    <div className="border border-neutral-200 bg-white">
      <div className="border-b border-neutral-200 px-3 py-2.5 flex items-center justify-between gap-2">
        <SectionLabel icon={Gauge}>Starter percentile series</SectionLabel>
        <StatusBadge className={statusBadgeClassName(evidence.starterPercentiles.status)}>
          {evidence.starterPercentiles.status}
        </StatusBadge>
      </div>
      <div className="grid grid-cols-2 gap-2 border-b border-neutral-100 p-3 text-[11px] md:grid-cols-4">
        <InfoCell label="window" value={evidence.starterPercentiles.window} />
        <InfoCell label="bucket seconds" value={String(evidence.starterPercentiles.bucketDurationSeconds)} />
        <InfoCell label="max points" value={String(evidence.starterPercentiles.maxPointCount)} />
        <InfoCell label="aggregate policy" value={evidence.starterPercentiles.aggregatePolicy} />
      </div>
      {points.length === 0 ? (
        <div className="p-3 text-[12px] text-neutral-500">
          {evidence.starterPercentiles.reason ?? "starter percentile series source가 없습니다."}
        </div>
      ) : (
        <table className="w-full text-[12px]">
          <thead>
            <tr className="text-left text-neutral-500">
              <th className="px-3 py-2">bucket</th>
              <th className="px-3 py-2">requests</th>
              <th className="px-3 py-2">p95</th>
              <th className="px-3 py-2">p99</th>
            </tr>
          </thead>
          <tbody>
            {points.map((point) => (
              <tr key={`${point.bucketStartUtc}-${point.bucketEndUtc}`} className="border-t border-neutral-100">
                <td className="px-3 py-2 text-neutral-500">{`${point.bucketStartUtc} -> ${point.bucketEndUtc}`}</td>
                <td className="px-3 py-2">{formatCount(point.requestCount)}</td>
                <td className="px-3 py-2">{point.p95Ms} ms</td>
                <td className="px-3 py-2">{point.p99Ms} ms</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function EvidenceHistogramPanel({ histogram }: { histogram: EvidenceHistogramDistribution }) {
  return (
    <div className="border border-neutral-200 bg-white p-3">
      <div className="flex items-center justify-between gap-2">
        <SectionLabel icon={Activity}>Histogram distribution evidence</SectionLabel>
        <StatusBadge className={statusBadgeClassName(histogram.status)}>{histogram.status}</StatusBadge>
      </div>
      <div className="mt-1 text-[11px] text-neutral-500">
        {histogram.source} · {histogram.scope} · total {formatCount(histogram.totalCount)} · {histogram.reason ?? "reason 없음"}
      </div>
      {histogram.buckets.length === 0 ? (
        <div className="mt-3 text-[12px] text-neutral-500">bucket distribution source가 없습니다.</div>
      ) : (
        <div className="mt-3 space-y-1.5">
          {histogram.buckets.map((bucket) => (
            <div key={bucket.leMs} className="flex items-center gap-2 text-[11px]">
              <span className="w-20 text-neutral-500 tabular-nums">≤ {bucket.leMs} ms</span>
              <div className="h-3 flex-1 border border-neutral-200 bg-neutral-100">
                <div className="h-full bg-neutral-800" style={{ width: histogramBarWidth(bucket.count, { status: histogram.status, reason: histogram.reason, totalCount: histogram.totalCount, buckets: histogram.buckets }) }} />
              </div>
              <span className="w-16 text-right text-neutral-700 tabular-nums">{formatCount(bucket.count)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function ResourceHintsPanel({ hints }: { hints: EvidenceResourceHints }) {
  return (
    <div className="border border-neutral-200 bg-white p-3">
      <SectionLabel icon={Activity}>Resource hints</SectionLabel>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] md:grid-cols-4">
        <InfoCell label="source" value={hints.source} />
        <InfoCell label="status" value={hints.status} />
        <InfoCell label="reason" value={hints.reason ?? "reason 없음"} />
        <InfoCell label="bucket end" value={formatOptionalDateTime(hints.bucketEndUtc)} />
        <InfoCell label="cpu" value={formatNullableRatio(hints.cpuUsageRatio)} />
        <InfoCell label="heap" value={formatNullableRatio(hints.heapUsedRatio)} />
        <InfoCell label="datasource" value={formatNullableRatio(hints.datasourcePoolUsageRatio)} />
      </div>
    </div>
  );
}

function EndpointEvidencePanel({
  evidence,
  items,
}: {
  evidence: InstanceEvidenceReadModel;
  items: EvidenceEndpointEvidenceItem[];
}) {
  return (
    <div className="border border-neutral-200 bg-white">
      <div className="border-b border-neutral-200 px-3 py-2.5 flex items-center justify-between gap-2">
        <SectionLabel icon={Server}>Endpoint evidence</SectionLabel>
        <StatusBadge className={statusBadgeClassName(evidence.endpointEvidence.status)}>
          {evidence.endpointEvidence.status}
        </StatusBadge>
      </div>
      <div className="border-b border-neutral-100 p-3 text-[11px] text-neutral-500">
        {evidence.endpointEvidence.selectionPolicy} · {evidence.endpointEvidence.displayOrderingPolicy}
      </div>
      {items.length === 0 ? (
        <div className="p-3 text-[12px] text-neutral-500">
          {evidence.endpointEvidence.reason ?? "selected instance endpoint evidence source가 없습니다."}
        </div>
      ) : (
        <ul>
          {items.map((item) => (
            <EndpointEvidenceRow item={item} key={`${item.localDisplayOrder}-${item.endpointKey}`} />
          ))}
        </ul>
      )}
    </div>
  );
}

function EndpointEvidenceRow({ item }: { item: EvidenceEndpointEvidenceItem }) {
  return (
    <li className="border-b border-neutral-100 p-3 last:border-b-0">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="truncate text-neutral-900">{item.endpointKey}</div>
          <div className="mt-0.5 text-[11px] text-neutral-500">
            local order {item.localDisplayOrder} · app rank {item.relatedApplicationPriorityRank ?? "참조 없음"}
          </div>
        </div>
        <StatusBadge className={statusBadgeClassName(item.status)}>{item.presenceOnSelectedInstance}</StatusBadge>
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500 md:grid-cols-4">
        <InfoCell label="instance requests" value={formatCount(item.instanceRequestCount)} />
        <InfoCell label="instance errors" value={formatCount(item.instanceErrorCount)} />
        <InfoCell label="instance error rate" value={formatRatio(item.instanceErrorRate)} />
        <InfoCell label="bucket source" value={item.bucketDistributionSource} />
        <InfoCell label="app requests" value={formatOptionalCount(item.applicationEndpointRequestCount)} />
        <InfoCell label="app error rate" value={formatNullableRatio(item.applicationEndpointErrorRate)} />
        <InfoCell label="request share" value={formatNullableRatio(item.instanceRequestShare)} />
        <InfoCell label="reason" value={item.reason ?? "reason 없음"} />
      </div>
    </li>
  );
}

function InstanceTrendView({
  onOpenTrend,
  onSwitch,
  target,
}: {
  onOpenTrend: (target: InstancePanelTarget) => void;
  onSwitch: () => void;
  target: InstancePanelTarget;
}) {
  const [preset, setPreset] = useState<TrendPreset>("7d");
  const [detailTarget, setDetailTarget] = useState<SnapshotDetailTarget | null>(null);
  const trendLinkSource = target.snapshotTrendLink ?? null;
  const trendResourceKey = `${target.projectId}|${target.applicationId}|${target.instanceId}|${trendLinkSource ?? "fallback"}|${preset}`;

  useEffect(() => {
    setDetailTarget(null);
  }, [target.applicationId, target.evidenceLink, target.instanceId, target.projectId, trendLinkSource]);

  const requestTrend = useCallback(
    async ({ authFetch, signal }: { authFetch: AuthFetch; signal: AbortSignal }) => {
      const basePath = trendLinkSource
        ? validateInstanceSnapshotTrendPath(
            trendLinkSource,
            target.projectId,
            target.applicationId,
            target.instanceId,
          )
        : buildInstanceSnapshotTrendPath(target.projectId, target.applicationId, target.instanceId);
      const response = await authFetch(trendPathWithPreset(basePath, preset), {
        ...NO_STORE_REQUEST_OPTIONS,
        signal,
      });
      const model = await readJsonResource<InstanceSnapshotTrendReadModel>(response);
      validateTrendContext(model, target);
      return model;
    },
    [preset, target, trendLinkSource],
  );

  const resource = useApiResource<InstanceSnapshotTrendReadModel>({
    dependencies: [trendResourceKey],
    request: requestTrend,
    resourceKey: trendResourceKey,
  });

  const current = resource.resourceKey === trendResourceKey;
  const loading = !current || resource.loading;
  const error = current ? resource.error : null;
  const trend = current ? resource.data : null;

  return (
    <div>
      <InstanceHeader
        icon={History}
        title={target.instanceName}
        description="Stored dashboard snapshot projection입니다. 현재 instance health나 recovery proof로 재해석하지 않습니다."
        actionLabel="Evidence"
        actionIcon={ArrowLeft}
        onAction={onSwitch}
      />
      <div className="p-5 space-y-4 text-[13px]">
        <div className="flex items-center gap-2">
          {(["7d", "14d"] as const).map((candidate) => (
            <Button
              key={candidate}
              variant={preset === candidate ? "default" : "outline"}
              size="sm"
              className="h-8 border-neutral-300"
              onClick={() => {
                setPreset(candidate);
                setDetailTarget(null);
                onOpenTrend({ ...target, snapshotTrendLink: trendLinkSource });
              }}
            >
              {candidate}
            </Button>
          ))}
        </div>
        {loading && <PanelMessage title="Trend 로딩 중" body={`${preset} fixed query를 호출하는 중입니다.`} />}
        {error && <InstanceResourceError error={error} onReload={resource.reload} />}
        {!loading && !error && trend && (
          <TrendReadyView
            detailTarget={detailTarget}
            onSelectDetail={setDetailTarget}
            preset={preset}
            target={target}
            trend={trend}
          />
        )}
      </div>
    </div>
  );
}

function TrendReadyView({
  detailTarget,
  onSelectDetail,
  preset,
  target,
  trend,
}: {
  detailTarget: SnapshotDetailTarget | null;
  onSelectDetail: (target: SnapshotDetailTarget | null) => void;
  preset: TrendPreset;
  target: InstancePanelTarget;
  trend: InstanceSnapshotTrendReadModel;
}) {
  return (
    <>
      <div className="border border-neutral-200 bg-white p-3">
        <div className="flex items-center justify-between gap-3">
          <SectionLabel icon={History}>Snapshot trend</SectionLabel>
          <StatusBadge>{preset}</StatusBadge>
        </div>
        <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] md:grid-cols-4">
          <InfoCell label="source" value={trend.source} />
          <InfoCell label="order" value={trend.horizon.order} />
          <InfoCell label="default since" value={trend.horizon.defaultSince} />
          <InfoCell label="max limit" value={String(trend.horizon.maxLimit)} />
          <InfoCell label="since" value={formatOptionalDateTime(trend.horizon.since)} />
          <InfoCell label="until" value={formatOptionalDateTime(trend.horizon.until)} />
          <InfoCell label="requested" value={trend.horizon.requestedSince} />
          <InfoCell label="points" value={formatCount(trend.points.length)} />
        </div>
      </div>
      {trend.points.length === 0 ? (
        <PanelMessage
          title="Trend point 없음"
          body="snapshot source absence, retention gap, target instance absence일 수 있습니다. 정상/복구 완료로 표현하지 않습니다."
        />
      ) : (
        <ul className="space-y-3">
          {trend.points.map((point) => (
            <TrendPointCard
              key={`${point.snapshotId}-${point.capturedAt}`}
              onSelectDetail={onSelectDetail}
              point={point}
            />
          ))}
        </ul>
      )}
      <SnapshotDetailSurface
        applicationId={target.applicationId}
        compact
        projectId={target.projectId}
        target={detailTarget}
      />
    </>
  );
}

function TrendPointCard({
  onSelectDetail,
  point,
}: {
  onSelectDetail: (target: SnapshotDetailTarget) => void;
  point: TrendPoint;
}) {
  return (
    <li className="border border-neutral-200 bg-white p-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-neutral-900">{point.capturedAt}</div>
          <div className="mt-0.5 text-[11px] text-neutral-500">
            stored application state {point.storedApplicationStateCode} · capture reason {point.captureReason ?? "opaque reason 없음"}
          </div>
        </div>
        <Button
          variant="outline"
          size="sm"
          className="gap-2 border-neutral-300"
          onClick={() => onSelectDetail({ snapshotId: point.snapshotId })}
        >
          <History className="h-3.5 w-3.5" strokeWidth={1.5} /> Detail
        </Button>
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] md:grid-cols-4">
        <InfoCell label="metric source" value={point.metricData.statusSource} />
        <InfoCell label="metric freshness" value={point.metricData.freshnessLabel} />
        <InfoCell label="last bucket" value={formatOptionalDateTime(point.metricData.lastAcceptedBucketAt)} />
        <InfoCell label="starter meaning" value={point.starterConnection.connectionMeaning} />
        <InfoCell label="starter impact" value={point.starterConnection.stateImpact} />
        <InfoCell label="triage contributed" value={point.applicationTriageContribution.contributed ? "true" : "false"} />
        <InfoCell label="resource" value={point.resourceHints.status} />
        <InfoCell label="endpoint refs" value={formatCount(point.endpointEvidenceRefs.length)} />
      </div>
      {point.starterPercentilePoint && (
        <div className="mt-2 border border-neutral-100 bg-neutral-50 p-2 text-[11px] text-neutral-600">
          stored starter percentile point · {point.starterPercentilePoint.source} · {point.starterPercentilePoint.scope} ·
          {" "}p95 {point.starterPercentilePoint.p95Ms}ms · p99 {point.starterPercentilePoint.p99Ms}ms
        </div>
      )}
      {point.endpointEvidenceRefs.length > 0 && (
        <div className="mt-2 space-y-1">
          {point.endpointEvidenceRefs.map((ref) => (
            <EndpointRefButton key={`${point.snapshotId}-${ref.endpointKey}-${ref.snapshotDetailAnchor ?? "no-anchor"}`} refItem={ref} snapshotId={point.snapshotId} onSelectDetail={onSelectDetail} />
          ))}
        </div>
      )}
    </li>
  );
}

function EndpointRefButton({
  onSelectDetail,
  refItem,
  snapshotId,
}: {
  onSelectDetail: (target: SnapshotDetailTarget) => void;
  refItem: TrendEndpointEvidenceRef;
  snapshotId: string;
}) {
  return (
    <button
      className="block w-full border border-neutral-200 px-2 py-1 text-left text-[11px] text-neutral-600 hover:bg-neutral-50"
      onClick={() => onSelectDetail({ activeAnchor: refItem.snapshotDetailAnchor, snapshotId })}
    >
      {refItem.endpointKey} · anchor {refItem.snapshotDetailAnchor ?? "없음"} · app rank {refItem.relatedApplicationPriorityRank ?? "참조 없음"}
    </button>
  );
}

function InstanceHeader({
  actionDisabled = false,
  actionIcon: ActionIcon,
  actionLabel,
  description,
  icon: Icon,
  onAction,
  title,
}: {
  actionDisabled?: boolean;
  actionIcon: LucideIcon;
  actionLabel: string;
  description: string;
  icon: LucideIcon;
  onAction: () => void;
  title: string;
}) {
  return (
    <SheetHeader className="px-5 py-4 border-b border-neutral-200">
      <div className="flex items-center justify-between gap-3">
        <SheetTitle className="flex items-center gap-2">
          <Icon className="h-4 w-4" strokeWidth={1.5} />
          {title}
        </SheetTitle>
        <Button
          variant="outline"
          size="sm"
          className="gap-2 border-neutral-300"
          disabled={actionDisabled}
          onClick={onAction}
        >
          <ActionIcon className="h-3.5 w-3.5" strokeWidth={1.5} /> {actionLabel}
        </Button>
      </div>
      <SheetDescription className="text-[12px] text-neutral-500">{description}</SheetDescription>
    </SheetHeader>
  );
}

function InstanceResourceError({ error, onReload }: { error: Error; onReload: () => void }) {
  const copy = instanceResourceErrorCopy(error);
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

function PanelMessage({ body, title }: { body: string; title: string }) {
  return (
    <div className="border border-neutral-200 bg-white p-4 text-[12px]">
      <div className="text-neutral-900">{title}</div>
      <div className="mt-1 text-neutral-500">{body}</div>
    </div>
  );
}

function InfoCell({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <div className="text-neutral-500">{label}</div>
      <div className="truncate text-neutral-900">{value}</div>
    </div>
  );
}

function validateEvidenceContext(model: InstanceEvidenceReadModel, target: InstancePanelTarget) {
  if (
    model.application.projectId !== target.projectId ||
    model.application.applicationId !== target.applicationId ||
    model.instance.instanceId !== target.instanceId
  ) {
    throw new ApiRequestError("instance_evidence_context_mismatch");
  }
}

function validateTrendContext(model: InstanceSnapshotTrendReadModel, target: InstancePanelTarget) {
  if (
    model.application.projectId !== target.projectId ||
    model.application.applicationId !== target.applicationId ||
    model.instance.instanceId !== target.instanceId ||
    model.source !== "dashboard_snapshots.read_model_json.instanceSummary.items" ||
    model.horizon.defaultSince !== "7d" ||
    model.horizon.maxSince !== "14d" ||
    model.horizon.maxLimit !== 336 ||
    model.horizon.order !== "capturedAt_asc"
  ) {
    throw new ApiRequestError("instance_snapshot_trend_context_mismatch");
  }
}

function instanceResourceErrorCopy(error: Error): { title: string; body: string } {
  if (error instanceof AuthRequiredError) {
    return {
      title: "인증 필요",
      body: error.message,
    };
  }
  if (error instanceof ApiRequestError && error.status === 404) {
    return {
      title: "Instance scope 확인 필요",
      body: "membership mismatch, scope mismatch, missing resource일 수 있습니다. instance down/deleted 또는 host down으로 단정하지 않습니다.",
    };
  }
  if (error instanceof ApiRequestError && error.status === 400) {
    return {
      title: "조회 조건 확인 필요",
      body: "Story 10.4는 trend 7d/14d fixed query만 호출합니다.",
    };
  }
  if (error instanceof ApiRequestError && !error.status) {
    return {
      title: "Link 검증 실패",
      body: "현재 선택한 Project/Application/Instance context와 server-provided link가 일치하지 않아 API call을 만들지 않았습니다.",
    };
  }
  return {
    title: "Instance resource 로드 실패",
    body: "resource를 불러오지 못했습니다. backend detail, token, provider payload, raw secret은 표시하지 않습니다.",
  };
}

function formatOptionalCount(value: number | null): string {
  return value === null || !Number.isFinite(value) ? "source 없음" : formatCount(value);
}
