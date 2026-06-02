import { useMemo, useState } from "react";
import { Activity, ArrowLeft, History, Radio, Server } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "./ui/sheet";
import { Tabs, TabsList, TabsTrigger } from "./ui/tabs";
import { Button } from "./ui/button";
import {
  instanceEvidenceById,
  snapshotTrendByInstance,
  type SnapshotTrendItem,
} from "./dashboard-data";

type View =
  | { kind: "closed" }
  | { kind: "evidence"; instanceId: string }
  | { kind: "trend"; instanceId: string };

function SourceBadge({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center border border-neutral-300 px-1.5 py-0.5 text-[10px] uppercase tracking-wider text-neutral-700 bg-neutral-50">
      {children}
    </span>
  );
}

function StateBadge({ code }: { code: SnapshotTrendItem["storedStateCode"] }) {
  return (
    <span className="inline-flex items-center border border-neutral-400 px-1.5 py-0.5 text-[10px] uppercase tracking-wider text-neutral-800">
      {code}
    </span>
  );
}

export function useInstanceView() {
  const [view, setView] = useState<View>({ kind: "closed" });
  return {
    view,
    openEvidence: (instanceId: string) => setView({ kind: "evidence", instanceId }),
    openTrend: (instanceId: string) => setView({ kind: "trend", instanceId }),
    close: () => setView({ kind: "closed" }),
  };
}

export function InstancePanels({
  view,
  onClose,
  onOpenTrend,
  onOpenEvidence,
}: {
  view: View;
  onClose: () => void;
  onOpenTrend: (id: string) => void;
  onOpenEvidence: (id: string) => void;
}) {
  return (
    <Sheet open={view.kind !== "closed"} onOpenChange={(o) => !o && onClose()}>
      <SheetContent
        side="right"
        className="w-full sm:max-w-[720px] p-0 bg-white text-neutral-900 overflow-y-auto"
      >
        {view.kind === "evidence" && (
          <EvidenceView
            instanceId={view.instanceId}
            onOpenTrend={() => onOpenTrend(view.instanceId)}
          />
        )}
        {view.kind === "trend" && (
          <TrendView
            instanceId={view.instanceId}
            onOpenEvidence={() => onOpenEvidence(view.instanceId)}
          />
        )}
      </SheetContent>
    </Sheet>
  );
}

function EvidenceView({
  instanceId,
  onOpenTrend,
}: {
  instanceId: string;
  onOpenTrend: () => void;
}) {
  const e = instanceEvidenceById[instanceId];
  if (!e) {
    return (
      <div className="p-6 text-[13px] text-neutral-500">instance evidence가 없습니다.</div>
    );
  }
  const maxBin = Math.max(...e.histogramDistribution.map((b) => b.count));

  return (
    <div>
      <SheetHeader className="px-5 py-4 border-b border-neutral-200">
        <div className="flex items-center justify-between">
          <SheetTitle className="flex items-center gap-2">
            <Server className="h-4 w-4" strokeWidth={1.5} />
            {e.instance.name}
          </SheetTitle>
          <Button variant="outline" size="sm" className="gap-2 border-neutral-300" onClick={onOpenTrend}>
            <History className="h-3.5 w-3.5" strokeWidth={1.5} /> Snapshot trend
          </Button>
        </div>
        <SheetDescription className="text-[11px] text-neutral-500">
          source: <code>/api/.../instances/{instanceId}/evidence</code> · bounded evidence, not raw
          explorer
        </SheetDescription>
        <div className="text-[11px] text-neutral-500">
          first seen {e.instance.firstSeen} · last seen {e.instance.lastSeen}
        </div>
      </SheetHeader>

      <div className="p-5 space-y-4 text-[13px]">
        <div className="grid grid-cols-2 gap-3">
          <div className="border border-neutral-200 p-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5 text-neutral-500 text-[11px] uppercase tracking-wider">
                <Activity className="h-3.5 w-3.5" strokeWidth={1.5} /> metric data
              </div>
              <SourceBadge>{e.metricData.statusSource}</SourceBadge>
            </div>
            <div className="mt-2 grid grid-cols-2 gap-y-1 text-[12px]">
              <span className="text-neutral-500">freshness</span>
              <span className="text-neutral-900">{e.metricData.freshnessLabel}</span>
              <span className="text-neutral-500">sample readiness</span>
              <span className="text-neutral-900">{e.metricData.sampleReadiness}</span>
              <span className="text-neutral-500">requests</span>
              <span className="text-neutral-900">{e.metricData.requestCount.toLocaleString()}</span>
              <span className="text-neutral-500">errors</span>
              <span className="text-neutral-900">
                {e.metricData.errorCount.toLocaleString()} ({e.metricData.errorRate})
              </span>
            </div>
          </div>

          <div className="border border-neutral-200 p-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5 text-neutral-500 text-[11px] uppercase tracking-wider">
                <Radio className="h-3.5 w-3.5" strokeWidth={1.5} /> starter connection
              </div>
              <SourceBadge>{e.starterConnection.statusSource}</SourceBadge>
            </div>
            <div className="mt-2 grid grid-cols-2 gap-y-1 text-[12px]">
              <span className="text-neutral-500">last heartbeat</span>
              <span className="text-neutral-900">{e.starterConnection.lastHeartbeatAt}</span>
              <span className="text-neutral-500">state impact</span>
              <span className="text-neutral-900">{e.starterConnection.stateImpact}</span>
            </div>
          </div>
        </div>

        {/* Starter percentile series */}
        <div className="border border-neutral-200">
          <div className="px-3 py-2 border-b border-neutral-200 flex items-center justify-between">
            <div className="text-[11px] uppercase tracking-wider text-neutral-500">
              Starter percentile series · 30s bucket
            </div>
            <SourceBadge>source-scoped</SourceBadge>
          </div>
          <table className="w-full text-[12px]">
            <thead>
              <tr className="text-left text-neutral-500">
                <th className="px-3 py-2">bucket boundary</th>
                <th className="px-3 py-2">scope</th>
                <th className="px-3 py-2">p95</th>
                <th className="px-3 py-2">p99</th>
              </tr>
            </thead>
            <tbody>
              {e.starterPercentilePoints.map((p, i) => (
                <tr key={i} className="border-t border-neutral-100">
                  <td className="px-3 py-1.5 text-neutral-700">{p.bucketBoundary}</td>
                  <td className="px-3 py-1.5 text-neutral-700">{p.scope}</td>
                  <td className="px-3 py-1.5 text-neutral-900">{p.p95Ms} ms</td>
                  <td className="px-3 py-1.5 text-neutral-900">{p.p99Ms} ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Histogram as evidence only */}
        <div className="border border-neutral-200">
          <div className="px-3 py-2 border-b border-neutral-200 flex items-center justify-between">
            <div className="text-[11px] uppercase tracking-wider text-neutral-500">
              Histogram distribution (evidence)
            </div>
            <span className="text-[10px] text-neutral-500">not a percentile source</span>
          </div>
          <div className="p-3 space-y-1.5">
            {e.histogramDistribution.map((b) => (
              <div key={b.upperMs} className="flex items-center gap-2 text-[11px]">
                <span className="w-20 text-neutral-500 tabular-nums">≤ {b.upperMs} ms</span>
                <div className="flex-1 h-3 bg-neutral-100 border border-neutral-200">
                  <div
                    className="h-full bg-neutral-800"
                    style={{ width: `${(b.count / maxBin) * 100}%` }}
                  />
                </div>
                <span className="w-16 text-right text-neutral-700 tabular-nums">
                  {b.count.toLocaleString()}
                </span>
              </div>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="border border-neutral-200 p-3">
            <div className="text-[11px] uppercase tracking-wider text-neutral-500">
              Resource hints
            </div>
            <div className="mt-2 grid grid-cols-2 gap-y-1 text-[12px]">
              {e.resourceHints.map((r) => (
                <span key={r.label} className="contents">
                  <span className="text-neutral-500">{r.label}</span>
                  <span className="text-neutral-900">{r.value}</span>
                </span>
              ))}
            </div>
          </div>
          <div className="border border-neutral-200 p-3">
            <div className="text-[11px] uppercase tracking-wider text-neutral-500">
              Triage contribution
            </div>
            <ul className="mt-2 space-y-1 text-[12px] text-neutral-800">
              {e.triageContribution.map((t) => (
                <li key={t}>· {t}</li>
              ))}
            </ul>
          </div>
        </div>

        <div className="border border-neutral-200">
          <div className="px-3 py-2 border-b border-neutral-200 text-[11px] uppercase tracking-wider text-neutral-500">
            Endpoint evidence subset
          </div>
          {e.endpointEvidenceSubset.length === 0 ? (
            <div className="p-3 text-[12px] text-neutral-500">관련 엔드포인트 evidence가 없습니다.</div>
          ) : (
            <table className="w-full text-[12px]">
              <thead>
                <tr className="text-left text-neutral-500">
                  <th className="px-3 py-2">route</th>
                  <th className="px-3 py-2">p99</th>
                  <th className="px-3 py-2">last seen</th>
                </tr>
              </thead>
              <tbody>
                {e.endpointEvidenceSubset.map((r) => (
                  <tr key={r.route} className="border-t border-neutral-100">
                    <td className="px-3 py-1.5 text-neutral-900">{r.route}</td>
                    <td className="px-3 py-1.5 text-neutral-900">{r.p99Ms} ms</td>
                    <td className="px-3 py-1.5 text-neutral-500">{r.lastSeen}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <p className="text-[11px] text-neutral-500">
          Instance detail은 application state나 instance health score를 계산하지 않습니다.
        </p>
      </div>
    </div>
  );
}

function TrendView({
  instanceId,
  onOpenEvidence,
}: {
  instanceId: string;
  onOpenEvidence: () => void;
}) {
  const all = snapshotTrendByInstance[instanceId] ?? [];
  const [horizon, setHorizon] = useState<"7d" | "14d">("7d");
  const limit = 168;

  const items = useMemo(() => {
    const horizonMs = (horizon === "7d" ? 7 : 14) * 24 * 60 * 60 * 1000;
    const cutoff = Date.parse("2026-06-02T09:00:00Z") - horizonMs;
    return all
      .filter((it) => Date.parse(it.capturedAt) >= cutoff)
      .slice(-limit);
  }, [all, horizon]);

  const maxP99 = Math.max(1, ...items.map((i) => i.starterPercentilePoint?.p99Ms ?? 0));

  return (
    <div>
      <SheetHeader className="px-5 py-4 border-b border-neutral-200">
        <div className="flex items-center justify-between">
          <SheetTitle className="flex items-center gap-2">
            <History className="h-4 w-4" strokeWidth={1.5} />
            Snapshot trend
          </SheetTitle>
          <Button
            variant="outline"
            size="sm"
            className="gap-2 border-neutral-300"
            onClick={onOpenEvidence}
          >
            <ArrowLeft className="h-3.5 w-3.5" strokeWidth={1.5} /> Evidence
          </Button>
        </div>
        <SheetDescription className="text-[11px] text-neutral-500">
          source: <code>dashboard_snapshots.read_model_json.instanceSummary.items</code> · stored
          snapshot projection, current state not recalculated
        </SheetDescription>

        <div className="mt-3 flex items-center justify-between">
          <Tabs value={horizon} onValueChange={(v) => setHorizon(v as "7d" | "14d")}>
            <TabsList className="h-8">
              <TabsTrigger value="7d" className="text-[11px] px-3">7d</TabsTrigger>
              <TabsTrigger value="14d" className="text-[11px] px-3">14d</TabsTrigger>
            </TabsList>
          </Tabs>
          <div className="text-[11px] text-neutral-500">
            limit {limit} · ordered capturedAt_asc · {items.length} points
          </div>
        </div>
      </SheetHeader>

      {/* Sparkline */}
      <div className="px-5 pt-4">
        <div className="border border-neutral-200 p-3">
          <div className="flex items-center justify-between text-[11px] text-neutral-500">
            <span>p99 (stored)</span>
            <span>max {maxP99} ms</span>
          </div>
          <div className="mt-2 flex items-end gap-[2px] h-24">
            {items.map((it, i) => {
              const v = it.starterPercentilePoint?.p99Ms ?? 0;
              const h = (v / maxP99) * 100;
              const tone =
                it.storedStateCode === "DEGRADED"
                  ? "bg-neutral-900"
                  : it.storedStateCode === "RECOVERING"
                  ? "bg-neutral-600"
                  : "bg-neutral-400";
              return (
                <div
                  key={i}
                  title={`${it.capturedAt} · ${it.storedStateCode} · p99 ${v}ms`}
                  className={`flex-1 ${tone}`}
                  style={{ height: `${Math.max(h, 4)}%` }}
                />
              );
            })}
          </div>
        </div>
      </div>

      {/* Timeline list */}
      <div className="p-5">
        <div className="border border-neutral-200">
          <table className="w-full text-[12px]">
            <thead>
              <tr className="text-left text-neutral-500">
                <th className="px-3 py-2">capturedAt</th>
                <th className="px-3 py-2">state</th>
                <th className="px-3 py-2">starter</th>
                <th className="px-3 py-2">p95</th>
                <th className="px-3 py-2">p99</th>
                <th className="px-3 py-2">triage</th>
                <th className="px-3 py-2">refs</th>
              </tr>
            </thead>
            <tbody>
              {items.slice(-40).map((it) => (
                <tr key={it.capturedAt} className="border-t border-neutral-100">
                  <td className="px-3 py-1.5 text-neutral-700">{it.capturedAt}</td>
                  <td className="px-3 py-1.5">
                    <StateBadge code={it.storedStateCode} />
                  </td>
                  <td className="px-3 py-1.5 text-neutral-700">{it.starterConnection}</td>
                  <td className="px-3 py-1.5 text-neutral-900">
                    {it.starterPercentilePoint ? `${it.starterPercentilePoint.p95Ms} ms` : "—"}
                  </td>
                  <td className="px-3 py-1.5 text-neutral-900">
                    {it.starterPercentilePoint ? `${it.starterPercentilePoint.p99Ms} ms` : "—"}
                  </td>
                  <td className="px-3 py-1.5 text-neutral-700">{it.triageContribution}</td>
                  <td className="px-3 py-1.5 text-neutral-500">
                    {it.endpointEvidenceRefs.length > 0 ? it.endpointEvidenceRefs[0] : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {items.length > 40 && (
            <div className="px-3 py-2 text-[11px] text-neutral-500 border-t border-neutral-100">
              최근 40개를 표시 중 · 전체 {items.length} 개 stored snapshot
            </div>
          )}
        </div>
        <p className="mt-3 text-[11px] text-neutral-500">
          Trend는 stored snapshot projection입니다. current state 재계산이나 instance health
          score는 만들지 않습니다.
        </p>
      </div>
    </div>
  );
}
