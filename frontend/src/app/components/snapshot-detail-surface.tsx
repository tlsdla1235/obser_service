import { useCallback, useMemo } from "react";
import { AlertCircle, FileSearch, RefreshCw } from "lucide-react";
import { ApiRequestError, AuthRequiredError, NO_STORE_REQUEST_OPTIONS, readJsonResource } from "../lib/api";
import { type AuthFetch } from "../lib/auth";
import { useApiResource } from "../lib/use-api-resource";
import {
  buildSnapshotDetailPath,
  formatCount,
  formatNullableRatio,
  formatOptionalDateTime,
  snapshotIdFromDetailPath,
  statusBadgeClassName,
  validateSnapshotDetailPath,
} from "../lib/read-model-adapters";
import type { DashboardSnapshotDetailReadModel } from "../lib/read-model-types";
import { Alert, AlertDescription, AlertTitle } from "./ui/alert";
import { Button } from "./ui/button";

export type SnapshotDetailTarget = {
  activeAnchor?: string | null;
  snapshotId?: string;
  snapshotLink?: string;
};

function StatusBadge({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <span className={`inline-flex items-center border px-1.5 py-0.5 text-[11px] uppercase ${className || "border-neutral-400 text-neutral-800"}`}>
      {children}
    </span>
  );
}

/**
 * stored snapshot detail API를 읽고 bounded projection만 렌더링하는 공용 surface다.
 * current dashboard fallback이나 raw read_model_json dump 없이 readSemantics/self link 계약을 검증한다.
 */
export function SnapshotDetailSurface({
  applicationId,
  compact = false,
  projectId,
  target,
}: {
  applicationId: string;
  compact?: boolean;
  projectId: string;
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
      const model = await readJsonResource<DashboardSnapshotDetailReadModel>(response);
      validateSnapshotDetailResponse(model, projectId, applicationId, requestedSnapshotId);
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
        event, marker, trend point에서 snapshot detail을 선택하면 저장된 read model projection을 불러옵니다.
      </div>
    );
  }
  if (loading) {
    return <SnapshotDetailMessage title="Snapshot detail 로딩 중" body="저장된 dashboard snapshot projection을 불러오는 중입니다." compact={compact} />;
  }
  if (error) {
    return <SnapshotDetailError error={error} onReload={resource.reload} compact={compact} />;
  }
  if (!detail) {
    return <SnapshotDetailMessage title="Snapshot detail 선택 대기" body="선택한 snapshot detail을 아직 불러오지 않았습니다." compact={compact} />;
  }

  const activeAnchor = target.activeAnchor ?? null;
  const anchorExists = activeAnchor
    ? detail.snapshotEndpointEvidence.items.some((item) => item.anchorId === activeAnchor)
    : false;

  return (
    <div className={`border border-neutral-200 bg-white ${compact ? "text-[12px]" : "text-[13px]"}`}>
      <div className="border-b border-neutral-200 px-3 py-2.5">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-1.5 text-[11px] uppercase text-neutral-500">
            <FileSearch className="h-3.5 w-3.5" strokeWidth={1.5} />
            Snapshot detail
          </div>
          <StatusBadge className={statusBadgeClassName(detail.source)}>{detail.source}</StatusBadge>
        </div>
        <div className="mt-1 break-all text-[11px] text-neutral-500">{detail.links.self}</div>
      </div>
      <div className="grid grid-cols-2 gap-2 border-b border-neutral-100 p-3 text-[11px] md:grid-cols-4">
        <InfoCell label="snapshot" value={detail.snapshot.snapshotId} />
        <InfoCell label="captured" value={formatOptionalDateTime(detail.snapshot.capturedAt)} />
        <InfoCell label="stored state" value={detail.snapshot.storedApplicationStateCode} />
        <InfoCell label="capture reason" value={detail.snapshot.captureReason ?? "opaque reason 없음"} />
        <InfoCell label="semantics" value={detail.readSemantics.mode} />
        <InfoCell label="recalculated" value={String(detail.readSemantics.currentStateRecalculated)} />
        <InfoCell label="live joins" value={formatCount(detail.readSemantics.liveSourcesJoined.length)} />
        <InfoCell label="raw JSON exposed" value={String(detail.readSemantics.rawReadModelJsonExposed)} />
      </div>
      {detail.recoveryMarker && (
        <Alert className="m-3 border-amber-300">
          <AlertCircle className="h-4 w-4" strokeWidth={1.5} />
          <AlertTitle>{detail.recoveryMarker.title}</AlertTitle>
          <AlertDescription>{detail.recoveryMarker.summary} {detail.recoveryMarker.recommendedAction}</AlertDescription>
        </Alert>
      )}
      {activeAnchor && (
        <div className="mx-3 mt-3 border border-neutral-200 bg-neutral-50 p-2 text-[11px] text-neutral-600">
          anchor {activeAnchor}: {anchorExists ? "resolved" : "missing"}
        </div>
      )}
      <div className="p-3">
        <div className="text-[11px] uppercase text-neutral-500">Endpoint evidence</div>
        {detail.snapshotEndpointEvidence.items.length === 0 ? (
          <div className="mt-2 text-[12px] text-neutral-500">
            bounded endpoint evidence가 없습니다. source absence로만 표시합니다.
          </div>
        ) : (
          <ul className="mt-2 space-y-2">
            {detail.snapshotEndpointEvidence.items.map((item) => {
              const active = activeAnchor === item.anchorId;
              return (
                <li key={item.anchorId} id={item.anchorId} className={`border p-2 ${active ? "border-neutral-900 bg-neutral-50" : "border-neutral-200"}`}>
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="truncate text-neutral-900">{item.endpointKey}</div>
                      <div className="mt-0.5 text-[11px] text-neutral-500">
                        {item.anchorId} · rank {item.rank ?? "source 없음"} · {item.reason ?? "reason 없음"}
                      </div>
                    </div>
                    <StatusBadge>{active ? "active" : "stored"}</StatusBadge>
                  </div>
                  <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500">
                    <InfoCell label="requests" value={item.requestCount === null ? "source 없음" : formatCount(item.requestCount)} />
                    <InfoCell label="error rate" value={formatNullableRatio(item.errorRate)} />
                    <InfoCell label="confidence" value={formatNullableRatio(item.confidence)} />
                    <InfoCell label="bucket source" value={item.bucketDistributionSource ?? "source 없음"} />
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
      <div className="border-t border-neutral-100 p-3">
        <div className="text-[11px] uppercase text-neutral-500">Instance summary refs</div>
        <div className="mt-1 text-[12px] text-neutral-600">
          {formatCount(detail.instanceSummary.items.length)} stored instance summary item · {detail.instanceSummary.source}
        </div>
      </div>
    </div>
  );
}

function validateSnapshotDetailResponse(
  model: DashboardSnapshotDetailReadModel,
  projectId: string,
  applicationId: string,
  snapshotId: string,
) {
  if (
    model.source !== "dashboard_snapshots" ||
    model.readSemantics.mode !== "stored_snapshot_detail" ||
    model.readSemantics.currentStateRecalculated ||
    model.readSemantics.liveSourcesJoined.length !== 0 ||
    model.readSemantics.rawReadModelJsonExposed ||
    model.snapshot.snapshotId !== snapshotId
  ) {
    throw new ApiRequestError("snapshot_detail_contract_mismatch");
  }
  validateSnapshotDetailPath(model.links.self, projectId, applicationId, snapshotId);
}

function SnapshotDetailMessage({ body, compact, title }: { body: string; compact: boolean; title: string }) {
  return (
    <div className={`border border-neutral-200 bg-white p-3 ${compact ? "text-[12px]" : "text-[13px]"}`}>
      <div className="text-neutral-900">{title}</div>
      <div className="mt-1 text-neutral-500">{body}</div>
    </div>
  );
}

function SnapshotDetailError({ compact, error, onReload }: { compact: boolean; error: Error; onReload: () => void }) {
  const copy = snapshotDetailErrorCopy(error);
  return (
    <div className={`border border-neutral-200 bg-white p-3 ${compact ? "text-[12px]" : "text-[13px]"}`}>
      <div className="text-neutral-900">{copy.title}</div>
      <div className="mt-1 text-neutral-500">{copy.body}</div>
      <Button variant="outline" size="sm" className="mt-3 gap-2 border-neutral-300" onClick={onReload}>
        <RefreshCw className="h-3.5 w-3.5" strokeWidth={1.5} /> 다시 시도
      </Button>
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
      title: "Snapshot detail 없음",
      body: "저장된 detail이 없거나 retention에서 사라졌을 수 있습니다. current dashboard fallback은 만들지 않습니다.",
    };
  }
  if (error instanceof ApiRequestError && error.status === 400) {
    return {
      title: "Snapshot id 확인 필요",
      body: "snapshot marker나 trend point에서 받은 bounded id/link만 사용할 수 있습니다.",
    };
  }
  return {
    title: "Snapshot detail 로드 실패",
    body: "저장된 detail을 불러오지 못했습니다. backend detail, token, raw payload는 표시하지 않습니다.",
  };
}

function InfoCell({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <div className="text-neutral-500">{label}</div>
      <div className="truncate text-neutral-900">{value}</div>
    </div>
  );
}
