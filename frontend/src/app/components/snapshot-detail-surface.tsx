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
import type { DashboardSnapshotDetailReadModel, JsonValue } from "../lib/read-model-types";
import { Alert, AlertDescription, AlertTitle } from "./ui/alert";
import { Button } from "./ui/button";
import type { SnapshotInstanceDashboardTarget } from "./instance-dashboard-surface";

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
  onOpenSnapshotInstanceDashboard,
  projectId,
  target,
}: {
  applicationId: string;
  compact?: boolean;
  onOpenSnapshotInstanceDashboard?: (target: SnapshotInstanceDashboardTarget) => void;
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
  const storedDashboard = toStoredDashboardView(detail);

  return (
    <div className={`border border-neutral-200 bg-white ${compact ? "text-[12px]" : "text-[13px]"}`}>
      <div className="border-b border-neutral-200 p-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <SectionLabel icon={FileSearch}>Application Dashboard / Snapshot</SectionLabel>
            <h2 className="mt-1 text-base font-semibold text-neutral-950">Stored Application Snapshot Dashboard</h2>
            <p className="mt-1 text-[12px] text-neutral-500">
              dashboard_snapshots.read_model_json에 저장된 과거 dashboard surface를 복원합니다. 현재 metric으로 state/evidence를 보정하지 않습니다.
            </p>
          </div>
          <div className="flex flex-wrap gap-1.5">
            <StatusBadge className={statusBadgeClassName(storedDashboard.mode)}>{`mode=${storedDashboard.mode}`}</StatusBadge>
            <StatusBadge>{`source=${detail.readSemantics.source}`}</StatusBadge>
          </div>
        </div>
      </div>
      <div className="border-b border-neutral-100 bg-neutral-50 px-3 py-2 text-[12px] text-neutral-600">
        capturedAt={formatOptionalDateTime(detail.snapshot.capturedAt)} · window={formatDateRange(detail.snapshot.currentWindow.startUtc, detail.snapshot.currentWindow.endUtc)} · markerBucket={humanizeStatusCode(detail.marker.type)}은 탐색 색인입니다.
      </div>
      <div className="grid grid-cols-1 gap-2 border-b border-neutral-100 p-3 text-[11px] sm:grid-cols-2 lg:grid-cols-4">
        <InfoCell label="mode" value={storedDashboard.mode} />
        <InfoCell label="source" value={detail.readSemantics.source} />
        <InfoCell label="snapshotId" value={detail.snapshot.snapshotId} />
        <InfoCell label="capturedAt" value={formatOptionalDateTime(detail.snapshot.capturedAt)} />
        <InfoCell label="generatedAt" value={formatOptionalDateTime(detail.snapshot.generatedAt)} />
        <InfoCell label="currentWindowStartUtc" value={formatOptionalDateTime(detail.snapshot.currentWindow.startUtc)} />
        <InfoCell label="currentWindowEndUtc" value={formatOptionalDateTime(detail.snapshot.currentWindow.endUtc)} />
        <InfoCell label="window" value={formatDateRange(detail.snapshot.currentWindow.startUtc, detail.snapshot.currentWindow.endUtc)} />
        <InfoCell label="captureReason" value={humanizeCaptureReason(detail.snapshot.captureReason)} />
        <InfoCell label="snapshotDetailRecalculates" value={String(detail.readSemantics.snapshotDetailRecalculates)} />
        <InfoCell label="currentStateRecalculated" value={String(detail.readSemantics.currentStateRecalculated)} />
        <InfoCell label="markerIsStateSource" value={String(detail.readSemantics.markerIsStateSource)} />
      </div>
      <div className="grid gap-3 border-b border-neutral-100 p-3 lg:grid-cols-3">
        <section className="border border-neutral-200 bg-white p-3">
          <div className="flex items-center justify-between gap-2">
            <SectionLabel icon={Activity}>Stored state</SectionLabel>
            <StatusBadge className={statusBadgeClassName(storedDashboard.stateCode)}>
              {storedDashboard.stateLabel}
            </StatusBadge>
          </div>
          <p className="mt-2 text-neutral-900">{storedDashboard.stateRationale}</p>
          <p className="mt-1 text-[12px] text-neutral-500">{storedDashboard.recommendedAction}</p>
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
    </div>
  );
}

type StoredDashboardItem = {
  body: string;
  key: string;
  meta: string;
  title: string;
};

type StoredDashboardView = {
  attentionEvidence: StoredDashboardItem[];
  dataQualityState: string;
  firstLookCandidates: StoredDashboardItem[];
  firstLookText: string;
  headline: string;
  lastObservedAt: string;
  minimumRequestCount: string;
  mode: string;
  recommendedAction: string;
  requestCount: string;
  source: string;
  stateCode: string;
  stateLabel: string;
  stateRationale: string;
  stateReasons: StoredDashboardItem[];
};

/**
 * dashboard_snapshots.read_model_json 저장본에서 화면에 필요한 bounded field만 꺼낸다.
 * 값이 없으면 current dashboard를 조회하거나 조합하지 않고 저장본 부재 copy로 표시한다.
 */
function toStoredDashboardView(detail: DashboardSnapshotDetailReadModel): StoredDashboardView {
  const readModel = detail.readModel;
  const state = jsonRecord(readModel.state);
  const operatorSummary = jsonRecord(readModel.operatorSummary);
  const dataQuality = jsonRecord(readModel.dataQuality);
  const readSemantics = jsonRecord(readModel.readSemantics);

  const stateCode = stringField(state, "code", "저장본에 state code 없음");
  const stateLabel = stringField(state, "label", humanizeStatusCode(stateCode));
  return {
    attentionEvidence: storedReasonItems(readModel.attentionEvidence, "attention"),
    dataQualityState: stringField(dataQuality, "state", "저장본에 dataQuality state 없음"),
    firstLookCandidates: firstLookItems(readModel.firstLookCandidates),
    firstLookText: stringField(operatorSummary, "firstLookText", "저장된 first look 안내가 없습니다."),
    headline: stringField(operatorSummary, "headline", "저장된 operator summary가 없습니다."),
    lastObservedAt: formatOptionalDateTime(nullableStringField(dataQuality, "lastObservedAt")),
    minimumRequestCount: numberField(dataQuality, "minimumRequestCount"),
    mode: stringJsonValue(readModel.mode, "snapshot"),
    recommendedAction: stringField(state, "recommendedAction", "저장된 권장 조치가 없습니다."),
    requestCount: numberField(dataQuality, "requestCount"),
    source: stringField(readSemantics, "source", detail.readSemantics.source),
    stateCode,
    stateLabel,
    stateRationale: stringField(state, "rationale", "저장된 state rationale이 없습니다."),
    stateReasons: storedReasonItems(readModel.stateReasons, "state-reason"),
  };
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
      {detail.instanceSummary.items.map((item) => (
        <li key={item.instanceId} className="flex flex-wrap items-center justify-between gap-2 border border-neutral-200 bg-white p-2">
          <div className="min-w-0">
            <div className="truncate text-[12px] text-neutral-900">{item.instanceName}</div>
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
        </li>
      ))}
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
    <div className="min-w-0">
      <div className="text-neutral-500">{label}</div>
      <div className="break-words text-neutral-900">{value}</div>
    </div>
  );
}
