import { useCallback, useMemo } from "react";
import { AlertCircle, FileSearch, RefreshCw } from "lucide-react";
import { ApiRequestError, AuthRequiredError, NO_STORE_REQUEST_OPTIONS, readJsonResource } from "../lib/api";
import { type AuthFetch } from "../lib/auth";
import { guardSnapshotDetailReadModel } from "../lib/read-model-contract-guard";
import { useApiResource } from "../lib/use-api-resource";
import {
  buildSnapshotDetailPath,
  formatCount,
  formatNullableRatio,
  formatOptionalDateTime,
  humanizeAnchorStatus,
  humanizeCaptureReason,
  humanizeSourceCode,
  humanizeStatusCode,
  severityBadgeClassName,
  severityDisplayText,
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
    return <SnapshotDetailError error={error} onReload={resource.reload} compact={compact} />;
  }
  if (!detail) {
    return <SnapshotDetailMessage title="스냅샷 상세 선택 대기" body="선택한 스냅샷 상세를 아직 불러오지 않았습니다." compact={compact} />;
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
            스냅샷 상세
          </div>
          <StatusBadge className={statusBadgeClassName(detail.source)}>{humanizeSourceCode(detail.source)}</StatusBadge>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2 border-b border-neutral-100 p-3 text-[11px] md:grid-cols-4">
        <InfoCell label="스냅샷 ID" value={detail.snapshot.snapshotId} />
        <InfoCell label="저장 시각" value={formatOptionalDateTime(detail.snapshot.capturedAt)} />
        <InfoCell label="저장 당시 상태" value={humanizeStatusCode(detail.snapshot.storedApplicationStateCode)} />
        <InfoCell label="저장 이유" value={humanizeCaptureReason(detail.snapshot.captureReason)} />
        <InfoCell label="이전 상태" value={humanizeStatusCode(detail.previousState.stateCode)} />
        <InfoCell label="마지막 정상 시각" value={formatOptionalDateTime(detail.lastHealthyAt.value)} />
        <InfoCell label="주요 판단 기준" value={detail.snapshot.primaryRuleId ?? "적용된 판단 기준 없음"} />
        <InfoCell label="주요 엔드포인트" value={detail.snapshot.primaryEndpointKey ?? "해당 엔드포인트 없음"} />
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
      <div className="p-3">
        <div className="text-[11px] uppercase text-neutral-500">엔드포인트 근거</div>
        {detail.snapshotEndpointEvidence.items.length === 0 ? (
          <div className="mt-2 text-[12px] text-neutral-500">
            이 스냅샷에 연결된 엔드포인트 근거가 없습니다.
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
                        상세 근거 {item.anchorId} · 우선순위 {item.rank ?? "참조 없음"} · {item.reason ? humanizeStatusCode(item.reason) : "추가 설명 없음"}
                      </div>
                    </div>
                    <StatusBadge>{active ? "선택됨" : "저장됨"}</StatusBadge>
                  </div>
                  <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500">
                    <InfoCell label="요청 수" value={item.requestCount === null ? "확인할 수 없음" : formatCount(item.requestCount)} />
                    <InfoCell label="오류율" value={formatNullableRatio(item.errorRate)} />
                    <InfoCell label="신뢰도" value={formatNullableRatio(item.confidence)} />
                    <InfoCell label="분포 기준" value={humanizeSourceCode(item.bucketDistributionSource)} />
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
      <div className="border-t border-neutral-100 p-3">
        <div className="text-[11px] uppercase text-neutral-500">인스턴스 요약</div>
        <div className="mt-1 text-[12px] text-neutral-600">
          저장 당시 인스턴스 {formatCount(detail.instanceSummary.items.length)}개
        </div>
      </div>
      <details className="border-t border-neutral-100 p-3 text-[11px] text-neutral-500">
        <summary className="cursor-pointer text-neutral-700">기술 세부 정보</summary>
        <div className="mt-2 grid grid-cols-2 gap-2 md:grid-cols-4">
          <InfoCell label="상세 링크" value={detail.links.self} />
          <InfoCell label="읽기 방식" value={humanizeStatusCode(detail.readSemantics.mode)} />
          <InfoCell label="현재 상태 재계산" value={detail.readSemantics.currentStateRecalculated ? "예" : "아니오"} />
          <InfoCell label="실시간 데이터 결합" value={formatCount(detail.readSemantics.liveSourcesJoined.length)} />
          <InfoCell label="원본 데이터 노출" value={detail.readSemantics.rawReadModelJsonExposed ? "예" : "아니오"} />
          <InfoCell label="이전 상태 근거" value={humanizeSourceCode(detail.previousState.source)} />
          <InfoCell label="마지막 정상 근거" value={humanizeSourceCode(detail.lastHealthyAt.source)} />
          <InfoCell label="인스턴스 요약 근거" value={humanizeSourceCode(detail.instanceSummary.source)} />
        </div>
      </details>
    </div>
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
      title: "스냅샷 상세 없음",
      body: "저장된 상세 기록이 없거나 보관 기간이 지났을 수 있습니다.",
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
    <div className="min-w-0">
      <div className="text-neutral-500">{label}</div>
      <div className="truncate text-neutral-900">{value}</div>
    </div>
  );
}
