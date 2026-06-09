import { useCallback, useEffect, useState } from "react";
import type { LucideIcon } from "lucide-react";
import { Activity, ArrowLeft, Gauge, History, RefreshCw, Server } from "lucide-react";
import { ApiRequestError, AuthRequiredError, NO_STORE_REQUEST_OPTIONS, readJsonResource } from "../lib/api";
import { type AuthFetch } from "../lib/auth";
import {
  guardInstanceEvidenceReadModel,
  guardInstanceSnapshotTrendReadModel,
} from "../lib/read-model-contract-guard";
import { useApiResource } from "../lib/use-api-resource";
import {
  buildInstanceSnapshotTrendPath,
  formatCount,
  formatDateRange,
  formatNullableRatio,
  formatOptionalDateTime,
  formatRatio,
  histogramRangeBarWidth,
  humanizeCaptureReason,
  humanizeOrderCode,
  humanizeSourceCode,
  humanizeStatusCode,
  statusBadgeClassName,
  toDisplayLatencyBuckets,
  TREND_PRESET_QUERY,
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
      return guardInstanceEvidenceReadModel(await readJsonResource<InstanceEvidenceReadModel>(response), target);
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
        description="이 인스턴스가 현재 상태 판단에 어떤 근거를 보탰는지 확인합니다."
        actionLabel="변화 기록"
        actionIcon={History}
        actionDisabled={!evidence?.links.snapshotTrend}
        onAction={() => {
          if (evidence?.links.snapshotTrend) {
            onSwitch(evidence.links.snapshotTrend);
          }
        }}
      />
      <div className="p-5 space-y-4 text-[13px]">
        {loading && <PanelMessage title="근거 로딩 중" body="선택한 인스턴스의 상태 판단 근거를 불러오는 중입니다." />}
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
            <div className="text-[11px] uppercase text-neutral-500">인스턴스</div>
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
            <History className="h-3.5 w-3.5" strokeWidth={1.5} /> 변화 기록
          </Button>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-2 text-[11px] md:grid-cols-4">
          <InfoCell label="생성 시각" value={formatOptionalDateTime(evidence.generatedAt)} />
          <InfoCell label="처음 관측" value={formatOptionalDateTime(evidence.instance.firstSeenAt)} />
          <InfoCell label="마지막 관측" value={formatOptionalDateTime(evidence.instance.lastSeenAt)} />
          <InfoCell label="변화 기록" value={evidence.links.snapshotTrend ? "확인 가능" : "아직 없음"} />
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
        <SectionLabel icon={Activity}>수집 데이터 상태</SectionLabel>
        <div className="mt-2 grid grid-cols-2 gap-2 text-[11px]">
          <InfoCell label="근거" value={humanizeSourceCode(evidence.metricData.statusSource)} />
          <InfoCell label="데이터 최신성" value={humanizeStatusCode(evidence.metricData.freshnessLabel)} />
          <InfoCell label="표본 상태" value={humanizeStatusCode(evidence.metricData.sampleReadiness)} />
          <InfoCell label="설명" value={evidence.metricData.reason ? humanizeStatusCode(evidence.metricData.reason) : "추가 설명 없음"} />
          <InfoCell label="요청 수" value={formatCount(evidence.metricData.requestCount)} />
          <InfoCell label="오류율" value={formatRatio(evidence.metricData.errorRate)} />
        </div>
      </div>
      <div className="border border-neutral-200 bg-white p-3">
        <SectionLabel icon={Gauge}>앱 연결 상태</SectionLabel>
        <div className="mt-2 grid grid-cols-2 gap-2 text-[11px]">
          <InfoCell label="근거" value={humanizeSourceCode(evidence.starterConnection.statusSource)} />
          <InfoCell label="연결 신호" value={humanizeStatusCode(evidence.starterConnection.lastHeartbeatStatus)} />
          <InfoCell label="신호 최신성" value={humanizeStatusCode(evidence.starterConnection.freshnessLabel)} />
          <InfoCell label="연결 의미" value={humanizeStatusCode(evidence.starterConnection.connectionMeaning)} />
          <InfoCell label="상태 영향" value={humanizeStatusCode(evidence.starterConnection.stateImpact)} />
          <InfoCell label="마지막 연결 확인" value={formatOptionalDateTime(evidence.starterConnection.lastHeartbeatAt)} />
        </div>
      </div>
    </div>
  );
}

function TriageContributionPanel({ evidence }: { evidence: InstanceEvidenceReadModel }) {
  const contribution = evidence.applicationTriageContribution;
  return (
    <div className="border border-neutral-200 bg-white p-3">
      <SectionLabel icon={Activity}>전체 상태 판단에 반영된 내용</SectionLabel>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] md:grid-cols-4">
        <InfoCell label="반영 상태" value={humanizeStatusCode(contribution.status)} />
        <InfoCell label="반영 여부" value={contribution.contributed ? "반영됨" : "반영 안 됨"} />
        <InfoCell label="판단 기준" value={contribution.relatedRuleIds.join(", ") || "적용된 판단 기준 없음"} />
        <InfoCell label="설명" value={contribution.reason ? humanizeStatusCode(contribution.reason) : "추가 근거 없음"} />
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
        <SectionLabel icon={Gauge}>최근 응답시간 p95/p99</SectionLabel>
        <StatusBadge className={statusBadgeClassName(evidence.starterPercentiles.status)}>
          {humanizeStatusCode(evidence.starterPercentiles.status)}
        </StatusBadge>
      </div>
      <details className="border-b border-neutral-100 p-3 text-[11px] text-neutral-500">
        <summary className="cursor-pointer text-neutral-700">기술 세부 정보</summary>
        <div className="mt-2 grid grid-cols-2 gap-2 md:grid-cols-4">
          <InfoCell label="조회 범위" value={evidence.starterPercentiles.window} />
          <InfoCell label="수집 간격" value={`${evidence.starterPercentiles.bucketDurationSeconds}초`} />
          <InfoCell label="최대 표시 수" value={String(evidence.starterPercentiles.maxPointCount)} />
          <InfoCell label="집계 방식" value={humanizeStatusCode(evidence.starterPercentiles.aggregatePolicy)} />
        </div>
      </details>
      {points.length === 0 ? (
        <div className="p-3 text-[12px] text-neutral-500">
          {evidence.starterPercentiles.reason ? humanizeStatusCode(evidence.starterPercentiles.reason) : "최근 응답시간 데이터가 없습니다."}
        </div>
      ) : (
        <table className="w-full text-[12px]">
          <thead>
            <tr className="text-left text-neutral-500">
              <th className="px-3 py-2">측정 구간</th>
              <th className="px-3 py-2">요청 수</th>
              <th className="px-3 py-2">p95</th>
              <th className="px-3 py-2">p99</th>
            </tr>
          </thead>
          <tbody>
            {points.map((point) => (
              <tr key={`${point.bucketStartUtc}-${point.bucketEndUtc}`} className="border-t border-neutral-100">
                <td className="px-3 py-2 text-neutral-500">{formatDateRange(point.bucketStartUtc, point.bucketEndUtc)}</td>
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
  const buckets = toDisplayLatencyBuckets(histogram.buckets);
  return (
    <div className="border border-neutral-200 bg-white p-3">
      <div className="flex items-center justify-between gap-2">
        <SectionLabel icon={Activity}>응답시간 분포</SectionLabel>
        <StatusBadge className={statusBadgeClassName(histogram.status)}>{humanizeStatusCode(histogram.status)}</StatusBadge>
      </div>
      <div className="mt-1 text-[11px] text-neutral-500">
        총 요청 수 {formatCount(histogram.totalCount)}
        {histogram.reason ? ` · ${humanizeStatusCode(histogram.reason)}` : ""}
      </div>
      {buckets.length === 0 ? (
        <div className="mt-3 text-[12px] text-neutral-500">응답시간 구간 데이터가 아직 없습니다.</div>
      ) : (
        <div className="mt-3 space-y-1.5">
          {buckets.map((bucket) => (
            <div key={bucket.key} className="flex items-center gap-2 text-[11px]">
              <span className="w-44 text-neutral-500 tabular-nums">{bucket.label}</span>
              <div className="h-3 flex-1 border border-neutral-200 bg-neutral-100">
                <div className="h-full bg-neutral-800" style={{ width: histogramRangeBarWidth(bucket.count, buckets) }} />
              </div>
              <span className="w-16 text-right text-neutral-700 tabular-nums">{formatCount(bucket.count)}</span>
            </div>
          ))}
        </div>
      )}
      <details className="mt-3 text-[11px] text-neutral-500">
        <summary className="cursor-pointer text-neutral-700">기술 세부 정보</summary>
        <div className="mt-2 grid grid-cols-2 gap-2">
          <InfoCell label="근거" value={humanizeSourceCode(histogram.source)} />
          <InfoCell label="범위" value={humanizeStatusCode(histogram.scope)} />
        </div>
      </details>
    </div>
  );
}

function ResourceHintsPanel({ hints }: { hints: EvidenceResourceHints }) {
  return (
    <div className="border border-neutral-200 bg-white p-3">
      <SectionLabel icon={Activity}>리소스 참고 신호</SectionLabel>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] md:grid-cols-4">
        <InfoCell label="근거" value={humanizeSourceCode(hints.source)} />
        <InfoCell label="상태" value={humanizeStatusCode(hints.status)} />
        <InfoCell label="설명" value={hints.reason ? humanizeStatusCode(hints.reason) : "추가 설명 없음"} />
        <InfoCell label="마지막 측정" value={formatOptionalDateTime(hints.bucketEndUtc)} />
        <InfoCell label="CPU" value={formatNullableRatio(hints.cpuUsageRatio)} />
        <InfoCell label="Heap" value={formatNullableRatio(hints.heapUsedRatio)} />
        <InfoCell label="DB 연결 풀" value={formatNullableRatio(hints.datasourcePoolUsageRatio)} />
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
        <SectionLabel icon={Server}>엔드포인트별 근거</SectionLabel>
        <StatusBadge className={statusBadgeClassName(evidence.endpointEvidence.status)}>
          {humanizeStatusCode(evidence.endpointEvidence.status)}
        </StatusBadge>
      </div>
      <details className="border-b border-neutral-100 p-3 text-[11px] text-neutral-500">
        <summary className="cursor-pointer text-neutral-700">기술 세부 정보</summary>
        <div className="mt-2 grid grid-cols-2 gap-2">
          <InfoCell label="선택 방식" value={humanizeStatusCode(evidence.endpointEvidence.selectionPolicy)} />
          <InfoCell label="정렬 방식" value={humanizeStatusCode(evidence.endpointEvidence.displayOrderingPolicy)} />
        </div>
      </details>
      {items.length === 0 ? (
        <div className="p-3 text-[12px] text-neutral-500">
          {evidence.endpointEvidence.reason ? humanizeStatusCode(evidence.endpointEvidence.reason) : "이 인스턴스에서 확인할 엔드포인트 근거가 없습니다."}
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
            표시 순서 {item.localDisplayOrder} · 앱 우선순위 {item.relatedApplicationPriorityRank ?? "참조 없음"}
          </div>
        </div>
        <StatusBadge className={statusBadgeClassName(item.status)}>{humanizeStatusCode(item.presenceOnSelectedInstance)}</StatusBadge>
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500 md:grid-cols-4">
        <InfoCell label="인스턴스 요청" value={formatCount(item.instanceRequestCount)} />
        <InfoCell label="인스턴스 오류" value={formatCount(item.instanceErrorCount)} />
        <InfoCell label="인스턴스 오류율" value={formatRatio(item.instanceErrorRate)} />
        <InfoCell label="분포 기준" value={humanizeSourceCode(item.bucketDistributionSource)} />
        <InfoCell label="앱 전체 요청" value={formatOptionalCount(item.applicationEndpointRequestCount)} />
        <InfoCell label="앱 전체 오류율" value={formatNullableRatio(item.applicationEndpointErrorRate)} />
        <InfoCell label="요청 비중" value={formatNullableRatio(item.instanceRequestShare)} />
        <InfoCell label="설명" value={item.reason ? humanizeStatusCode(item.reason) : "추가 설명 없음"} />
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
      return guardInstanceSnapshotTrendReadModel(await readJsonResource<InstanceSnapshotTrendReadModel>(response), {
        ...target,
        limit: TREND_PRESET_QUERY[preset].limit,
        preset,
      });
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
        description="과거에 저장된 상태 기록입니다. 현재 상태와 다를 수 있습니다."
        actionLabel="현재 근거"
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
              {trendPresetDisplayText(candidate)}
            </Button>
          ))}
        </div>
        {loading && <PanelMessage title="변화 기록 로딩 중" body={`${trendPresetDisplayText(preset)} 범위의 저장된 상태 기록을 불러오는 중입니다.`} />}
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
          <SectionLabel icon={History}>저장된 상태 기록</SectionLabel>
          <StatusBadge>{trendPresetDisplayText(preset)}</StatusBadge>
        </div>
        <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] md:grid-cols-4">
          <InfoCell label="조회 시작" value={formatOptionalDateTime(trend.horizon.since)} />
          <InfoCell label="조회 끝" value={formatOptionalDateTime(trend.horizon.until)} />
          <InfoCell label="표시 순서" value={humanizeOrderCode(trend.horizon.order)} />
          <InfoCell label="기록 수" value={formatCount(trend.points.length)} />
        </div>
        <details className="mt-2 text-[11px] text-neutral-500">
          <summary className="cursor-pointer text-neutral-700">기술 세부 정보</summary>
          <div className="mt-2 grid grid-cols-2 gap-2 md:grid-cols-4">
            <InfoCell label="근거" value={humanizeSourceCode(trend.source)} />
            <InfoCell label="기본 범위" value={trend.horizon.defaultSince} />
            <InfoCell label="최대 범위" value={trend.horizon.maxSince} />
            <InfoCell label="최대 표시 수" value={String(trend.horizon.maxLimit)} />
          </div>
        </details>
      </div>
      {trend.points.length === 0 ? (
        <PanelMessage
          title="저장된 기록 없음"
          body="이 범위에 표시할 인스턴스 상태 기록이 없습니다."
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
          <div className="text-neutral-900">{formatOptionalDateTime(point.capturedAt)}</div>
          <div className="mt-0.5 text-[11px] text-neutral-500">
            저장 당시 상태: {humanizeStatusCode(point.storedApplicationStateCode)} · 저장 이유: {humanizeCaptureReason(point.captureReason)}
          </div>
        </div>
        <Button
          variant="outline"
          size="sm"
          className="gap-2 border-neutral-300"
          onClick={() => onSelectDetail({ snapshotId: point.snapshotId })}
        >
          <History className="h-3.5 w-3.5" strokeWidth={1.5} /> 상세 보기
        </Button>
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] md:grid-cols-4">
        <InfoCell label="수집 근거" value={humanizeSourceCode(point.metricData.statusSource)} />
        <InfoCell label="데이터 상태" value={humanizeStatusCode(point.metricData.freshnessLabel)} />
        <InfoCell label="마지막 수집" value={formatOptionalDateTime(point.metricData.lastAcceptedBucketAt)} />
        <InfoCell label="앱 연결" value={humanizeStatusCode(point.starterConnection.connectionMeaning)} />
        <InfoCell label="연결 영향" value={humanizeStatusCode(point.starterConnection.stateImpact)} />
        <InfoCell label="상태 판단 반영" value={point.applicationTriageContribution.contributed ? "반영됨" : "반영 안 됨"} />
        <InfoCell label="리소스" value={humanizeStatusCode(point.resourceHints.status)} />
        <InfoCell label="상세 근거" value={formatCount(point.endpointEvidenceRefs.length)} />
      </div>
      {point.starterPercentilePoint && (
        <div className="mt-2 border border-neutral-100 bg-neutral-50 p-2 text-[11px] text-neutral-600">
          저장된 응답시간 · {formatDateRange(point.starterPercentilePoint.bucketStartUtc, point.starterPercentilePoint.bucketEndUtc)} ·
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
      {refItem.endpointKey} · 상세 근거 {refItem.snapshotDetailAnchor ?? "없음"} · 앱 우선순위 {refItem.relatedApplicationPriorityRank ?? "참조 없음"}
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

function trendPresetDisplayText(preset: TrendPreset): string {
  switch (preset) {
    case "7d":
      return "7일";
    case "14d":
      return "14일";
    default:
      return preset;
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
      body: "선택한 인스턴스의 근거를 찾지 못했습니다. 권한이나 보관 기간을 확인해 주세요.",
    };
  }
  if (error instanceof ApiRequestError && error.status === 400) {
    return {
      title: "조회 조건 확인 필요",
      body: "지원하는 조회 범위는 7일과 14일입니다.",
    };
  }
  if (error instanceof ApiRequestError && !error.status) {
    return {
      title: "Link 검증 실패",
      body: "현재 선택한 프로젝트, 앱, 인스턴스와 연결 정보가 맞지 않습니다.",
    };
  }
  return {
    title: "인스턴스 정보 로드 실패",
    body: "선택한 인스턴스 정보를 불러오지 못했습니다.",
  };
}

function formatOptionalCount(value: number | null): string {
  return value === null || !Number.isFinite(value) ? "확인할 수 없음" : formatCount(value);
}
