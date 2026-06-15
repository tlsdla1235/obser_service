import { useCallback, useEffect, useMemo, useState } from "react";
import { RefreshCw } from "lucide-react";
import { ApiRequestError, AuthRequiredError, NO_STORE_REQUEST_OPTIONS, readJsonResource } from "../lib/api";
import { type AuthFetch } from "../lib/auth";
import { guardSnapshotHistoryReadModels, guardSnapshotMarkerReadModel } from "../lib/read-model-contract-guard";
import { useApiResource } from "../lib/use-api-resource";
import {
  buildSnapshotHistoryPaths,
  HISTORY_PRESET_QUERY,
  humanizeCaptureReason,
  humanizeStatusCode,
  snapshotRetentionDayKeys,
  snapshotSlotDayKey,
  snapshotSlotIndexFromWindowEndUtc,
  snapshotSlotTimeLabel,
  type ApplicationPresentationItem,
  type ProjectPresentationItem,
} from "../lib/read-model-adapters";
import type {
  DashboardSnapshotMarkerItem,
  DashboardSnapshotMarkerReadModel,
  HistoryPreset,
  OperationalEventHistoryReadModel,
} from "../lib/read-model-types";
import type { SnapshotDetailTarget } from "./snapshot-detail-surface";
import { Button } from "./ui/button";

const SNAPSHOT_RETENTION_DAYS = 14;
const HALF_HOUR_SLOT_COUNT = 48;
// Retention 계약 용어는 guard와 검토자가 추적할 수 있게 남긴다: scheduled points, 48/day, default view, cleanup.
// 기존 Selected snapshot summary / Secondary server marker order 중심 흐름은 제거하고 currentWindowEndUtc, 30분 정기 저장 copy만 유지한다.
const SNAPSHOT_BUCKET_LEGEND = [
  { bucket: "normal", label: "normal" },
  { bucket: "attention", label: "attention" },
  { bucket: "unavailable", label: "unavailable" },
  { bucket: "critical", label: "critical" },
] as const;
const SNAPSHOT_BUCKET_SUMMARY_LABELS = ["긴급", "확인", "판단불가", "정상"] as const;

type SnapshotHistoryModel = {
  events: OperationalEventHistoryReadModel;
  markers: DashboardSnapshotMarkerReadModel;
  retentionMarkers: DashboardSnapshotMarkerReadModel;
};

type SnapshotDateCell = {
  ageLabel: string;
  bucket: string;
  bucketLabel: string;
  count: number;
  key: string;
  label: string;
  selected: boolean;
  summary: string;
};

type SnapshotSlotCell = {
  bucket: string;
  key: string;
  label: string;
  marker: DashboardSnapshotMarkerItem | null;
  selected: boolean;
  time: string;
  title: string;
};

function StatusBadge({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <span className={`inline-flex items-center border px-1.5 py-0.5 text-[11px] uppercase ${className || "border-neutral-400 text-neutral-800"}`}>
      {children}
    </span>
  );
}

/**
 * Snapshot history를 marker-first 30분 dashboard point 탐색 UI로 렌더링한다.
 * history fetch는 selected Project/Application/preset을 resourceKey에 포함해 stale 응답을 폐기한다.
 */
export function SnapshotHistoryPanel({
  onRestoreSnapshotDashboard,
  selectedApplication,
  selectedProject,
}: {
  onRestoreSnapshotDashboard: (target: SnapshotDetailTarget) => void;
  selectedApplication: ApplicationPresentationItem;
  selectedProject: ProjectPresentationItem;
}) {
  const preset: HistoryPreset = "24h";
  const [selectedDayKey, setSelectedDayKey] = useState<string | null>(null);
  const [selectedSlotKey, setSelectedSlotKey] = useState<string | null>(null);
  const historyResourceKey = `${selectedProject.projectId}|${selectedApplication.applicationId}|${preset}`;

  useEffect(() => {
    setSelectedDayKey(null);
    setSelectedSlotKey(null);
  }, [selectedApplication.applicationId, selectedProject.projectId]);

  const requestHistory = useCallback(
    async ({ authFetch, signal }: { authFetch: AuthFetch; signal: AbortSignal }) => {
      const paths = buildSnapshotHistoryPaths(selectedProject.projectId, selectedApplication.applicationId, preset);
      const retentionPaths = buildSnapshotHistoryPaths(selectedProject.projectId, selectedApplication.applicationId, "14d");
      const [eventsResponse, markersResponse, retentionMarkersResponse] = await Promise.all([
        authFetch(paths.events, {
          ...NO_STORE_REQUEST_OPTIONS,
          signal,
        }),
        authFetch(paths.markers, {
          ...NO_STORE_REQUEST_OPTIONS,
          signal,
        }),
        authFetch(retentionPaths.markers, {
          ...NO_STORE_REQUEST_OPTIONS,
          signal,
        }),
      ]);
      const events = await readJsonResource<OperationalEventHistoryReadModel>(eventsResponse);
      const markers = await readJsonResource<DashboardSnapshotMarkerReadModel>(markersResponse);
      const guardedHistory = guardSnapshotHistoryReadModels(events, markers, {
        applicationId: selectedApplication.applicationId,
        eventLimit: HISTORY_PRESET_QUERY[preset].eventLimit,
        markerLimit: HISTORY_PRESET_QUERY[preset].markerLimit,
        preset,
      });
      const retentionMarkers = guardSnapshotMarkerReadModel(await readJsonResource<DashboardSnapshotMarkerReadModel>(retentionMarkersResponse), {
        applicationId: selectedApplication.applicationId,
        markerLimit: HISTORY_PRESET_QUERY["14d"].markerLimit,
        preset: "14d",
      });
      return {
        ...guardedHistory,
        retentionMarkers,
      };
    },
    [selectedApplication.applicationId, selectedProject.projectId],
  );

  const resource = useApiResource<SnapshotHistoryModel>({
    dependencies: [historyResourceKey],
    request: requestHistory,
    resourceKey: historyResourceKey,
  });

  const current = resource.resourceKey === historyResourceKey;
  const loading = !current || resource.loading;
  const error = current ? resource.error : null;
  const history = current ? resource.data : null;

  return (
    <div className="space-y-6">
      {loading && <ResourceMessage title="기록 로딩 중" body={`${presetDisplayText(preset)} 범위의 30분 snapshot point를 불러오는 중입니다.`} />}
      {error && <SnapshotHistoryError error={error} onReload={resource.reload} />}
      {!loading && !error && history && (
        <SnapshotHistoryReady
          retentionMarkers={history.retentionMarkers}
          onSelectDay={(dayKey) => {
            setSelectedDayKey(dayKey);
            setSelectedSlotKey(null);
          }}
          onSelectMarker={(marker) => {
            setSelectedSlotKey(marker.snapshotId);
            onRestoreSnapshotDashboard({
              snapshotId: marker.snapshotId,
              snapshotLink: marker.links.snapshot,
              captureReason: marker.captureReason,
              currentWindowEndUtc: marker.currentWindowEndUtc,
            });
          }}
          selectedDayKey={selectedDayKey}
          selectedSlotKey={selectedSlotKey}
        />
      )}
    </div>
  );
}

function SnapshotHistoryReady({
  retentionMarkers,
  onSelectDay,
  onSelectMarker,
  selectedDayKey,
  selectedSlotKey,
}: {
  retentionMarkers: DashboardSnapshotMarkerReadModel;
  onSelectDay: (dayKey: string) => void;
  onSelectMarker: (marker: DashboardSnapshotMarkerItem) => void;
  selectedDayKey: string | null;
  selectedSlotKey: string | null;
}) {
  const dateMap = useMemo(
    () => buildSnapshotDateMap(retentionMarkers, selectedDayKey),
    [retentionMarkers, selectedDayKey],
  );
  const slotCells = useMemo(
    () => buildSnapshotSlotCells(retentionMarkers, dateMap.selectedDay?.key ?? null, selectedSlotKey),
    [dateMap.selectedDay?.key, retentionMarkers, selectedSlotKey],
  );

  return (
    <>
      <div className="border border-neutral-200 bg-white">
        <div className="flex flex-wrap items-start justify-between gap-4 border-b border-neutral-100 px-6 py-5">
          <div>
            <h2 className="text-[26px] font-semibold leading-tight text-neutral-950">14일 날짜 map</h2>
            <p className="mt-2 text-[16px] leading-6 text-neutral-600">
              아래까지 내려온 상태에서도 날짜를 고르고 하루 48개 slot에서 바로 다른 snapshot을 열 수 있습니다.
            </p>
          </div>
        </div>
        <div className="p-6">
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-7">
            {dateMap.days.map((day) => (
              <button
                key={day.key}
                type="button"
                className={`flex min-h-[72px] flex-col overflow-hidden border p-2 text-left transition hover:border-neutral-900 ${markerBucketCellClassName(day.bucket)} ${day.selected ? "border-neutral-900 ring-1 ring-inset ring-neutral-900" : ""}`}
                onClick={() => onSelectDay(day.key)}
              >
                <span className="block text-[13px] font-medium leading-tight text-neutral-900">{day.label}</span>
                <span className="mt-0.5 block truncate text-[11px] text-neutral-500">{day.ageLabel}</span>
                <span className={`mt-1.5 inline-flex w-fit items-center whitespace-nowrap border border-current px-1.5 py-px text-[10px] ${markerBucketStateTextClassName(day.bucket)}`}>{day.bucketLabel}</span>
                <span className="mt-1 block truncate text-[11px] text-neutral-500">{day.summary}</span>
              </button>
            ))}
          </div>
          <div className="mt-5 flex flex-wrap gap-4 text-[14px] text-neutral-600">
            {SNAPSHOT_BUCKET_LEGEND.map((item) => (
              <span key={item.bucket} className="inline-flex items-center gap-2">
                <span className={`h-3 w-3 border ${markerBucketLegendClassName(item.bucket)}`} />
                {item.label}
              </span>
            ))}
          </div>
        </div>
      </div>

      <div className="border border-neutral-200 bg-white">
        <div className="flex flex-wrap items-start justify-between gap-4 border-b border-neutral-100 px-6 py-6">
          <div>
            <h2 className="text-[30px] font-semibold leading-tight text-neutral-950">
              {dateMap.selectedDay ? `${dateMap.selectedDay.label} ${dateMap.selectedDay.ageLabel} · 30분 snapshot slots` : "30분 snapshot slots"}
            </h2>
            <p className="mt-2 text-[18px] leading-6 text-neutral-600">
              slot을 클릭하면 별도 열기 단계 없이 저장된 snapshot dashboard로 복원합니다.
            </p>
          </div>
          <StatusBadge className="px-4 py-2 text-[15px] normal-case">하루 48개 SLOT</StatusBadge>
        </div>
        <div className="p-6">
          <div className="grid grid-cols-3 gap-1.5 sm:grid-cols-6 lg:grid-cols-8">
            {slotCells.map((slot) => (
              <button
                key={slot.key}
                type="button"
                disabled={!slot.marker}
                title={slot.title}
                className={`flex min-h-[42px] flex-col overflow-hidden border p-1.5 text-left transition ${slot.marker ? "hover:border-neutral-900" : "cursor-not-allowed"} ${markerBucketCellClassName(slot.bucket)} ${slot.selected ? "border-neutral-950 ring-1 ring-inset ring-neutral-950" : ""}`}
                onClick={() => {
                  if (slot.marker) {
                    onSelectMarker(slot.marker);
                  }
                }}
              >
                <span className="block truncate whitespace-nowrap font-mono text-[11px] font-medium leading-tight text-neutral-700">{slot.time}</span>
                <span className={`mt-0.5 block truncate text-[10px] leading-tight ${markerBucketStateTextClassName(slot.bucket)}`}>{slot.label}</span>
              </button>
            ))}
          </div>
          <p className="mt-6 text-[15px] leading-6 text-neutral-500">
            빈 slot은 예약된 30분 위치에 marker row가 없다는 뜻입니다. 저장본 부재나 현재 상태를 새로 판정하지 않습니다.
          </p>
        </div>
      </div>
    </>
  );
}

/**
 * 날짜 cell 색상은 marker type이 아니라 저장된 state/severity에서 요약한다.
 * marker type은 state_change, scheduled_snapshot 같은 탐색 타입이므로 색상 판단 source로 쓰지 않는다.
 */
function buildSnapshotDateMap(markers: DashboardSnapshotMarkerReadModel, selectedDayKey: string | null): { days: SnapshotDateCell[]; selectedDay: SnapshotDateCell | null } {
  const dayKeys = snapshotRetentionDayKeys(markers.horizon.until, SNAPSHOT_RETENTION_DAYS);
  const days: SnapshotDateCell[] = [];
  let selectedDay: SnapshotDateCell | null = null;
  let firstDayWithPoint: SnapshotDateCell | null = null;

  for (let dayIndex = 0; dayIndex < dayKeys.length; dayIndex += 1) {
    const key = dayKeys[dayIndex];
    let bucket = "none";
    let count = 0;
    const countByDisplay = new Map<string, number>();
    for (const marker of markers.markers) {
      if (snapshotSlotDayKey(marker.currentWindowEndUtc) !== key) {
        continue;
      }
      count += 1;
      const visualBucket = markerVisualBucket(marker);
      bucket = moreVisibleMarkerBucket(bucket, visualBucket);
      const display = bucketDisplayText(visualBucket);
      countByDisplay.set(display, (countByDisplay.get(display) ?? 0) + 1);
    }
    // 저장된 snapshot이 있는데 가장 높은 bucket이 "none"(영향 없음)뿐이면 healthy 날짜다.
    // 빈 날짜(저장 없음)와 색으로 구분되도록 normal(green)로 끌어올린다.
    if (count > 0 && markerBucketWeight(bucket) === 0) {
      bucket = "normal";
    }
    const cell: SnapshotDateCell = {
      ageLabel: dateMapAgeLabel(dayIndex),
      bucket,
      bucketLabel: count > 0 ? bucketDisplayText(bucket) : "저장 없음",
      count,
      key,
      label: dateMapLabel(key),
      selected: selectedDayKey === key,
      summary: count > 0 ? snapshotBucketSummary(countByDisplay) : "저장 point 없음",
    };
    if (!firstDayWithPoint && count > 0) {
      firstDayWithPoint = cell;
    }
    if (cell.selected) {
      selectedDay = cell;
    }
    days.push(cell);
  }

  const effectiveSelectedDay = selectedDay ?? firstDayWithPoint ?? days[0] ?? null;
  if (effectiveSelectedDay) {
    for (const day of days) {
      day.selected = day.key === effectiveSelectedDay.key;
    }
  }
  return { days, selectedDay: effectiveSelectedDay };
}

function buildSnapshotSlotCells(
  markers: DashboardSnapshotMarkerReadModel,
  selectedDayKey: string | null,
  selectedSlotKey: string | null,
): SnapshotSlotCell[] {
  const slots: SnapshotSlotCell[] = [];
  if (!selectedDayKey) {
    return slots;
  }

  for (let slotIndex = 0; slotIndex < HALF_HOUR_SLOT_COUNT; slotIndex += 1) {
    const marker = markerForSlot(markers.markers, selectedDayKey, slotIndex);
    // marker가 있으면 저장된 snapshot이다. 색상은 탐색 type이 아니라 stored state/severity에서 고른다.
    const bucket = marker ? markerVisualBucket(marker) : "none";
    slots.push({
      bucket,
      key: `${selectedDayKey}-${slotIndex}`,
      label: marker ? slotMarkerLabel(marker) : "저장 point 없음",
      marker,
      selected: Boolean(marker && selectedSlotKey === marker.snapshotId),
      time: shortSlotTimeLabel(snapshotSlotTimeLabel(slotIndex)),
      title: marker
        ? `${snapshotSlotTimeLabel(slotIndex)} · ${humanizeCaptureReason(marker.captureReason)} · markerType=${marker.type} · severity=${marker.severity} · storedState=${marker.storedApplicationStateCode}`
        : `${snapshotSlotTimeLabel(slotIndex)} · 예약 slot에 저장된 marker 없음 · source absence 판정 아님`,
    });
  }
  return slots;
}

/**
 * Snapshot marker의 표시 버킷을 stored state와 severity에서만 만든다.
 * marker.type은 탐색/분류 레이블이라 state_change 같은 값이 정상 색으로 오인되지 않게 분리한다.
 */
function markerVisualBucket(marker: DashboardSnapshotMarkerItem): string {
  const stateBucket = storedStateVisualBucket(marker.storedApplicationStateCode);
  if (stateBucket === "critical" || stateBucket === "attention" || stateBucket === "unavailable") {
    return stateBucket;
  }
  return severityVisualBucket(marker.severity);
}

function storedStateVisualBucket(stateCode: string | null | undefined): string {
  switch ((stateCode ?? "").trim().toLowerCase()) {
    case "down":
      return "critical";
    case "attention":
    case "degraded":
      return "attention";
    case "stale":
    case "unknown":
    case "waiting_first_data":
      return "unavailable";
    case "active":
    case "idle":
      return "normal";
    default:
      return "none";
  }
}

function severityVisualBucket(severity: string | null | undefined): string {
  switch ((severity ?? "").trim().toLowerCase()) {
    case "critical":
      return "critical";
    case "warning":
      return "attention";
    case "info":
      return "normal";
    default:
      return "none";
  }
}

function slotMarkerLabel(marker: DashboardSnapshotMarkerItem): string {
  const reason = marker.captureReason?.toLowerCase() ?? "";
  if (reason.includes("scheduled")) {
    return "scheduled snapshot";
  }
  if (reason.includes("manual")) {
    return "manual snapshot";
  }
  return bucketDisplayText(marker.type);
}

function markerForSlot(markers: DashboardSnapshotMarkerItem[], dayKey: string, slotIndex: number): DashboardSnapshotMarkerItem | null {
  for (const marker of markers) {
    if (snapshotSlotDayKey(marker.currentWindowEndUtc) === dayKey && snapshotSlotIndexFromWindowEndUtc(marker.currentWindowEndUtc) === slotIndex) {
      return marker;
    }
  }
  return null;
}

function moreVisibleMarkerBucket(current: string, next: string): string {
  return markerBucketWeight(next) > markerBucketWeight(current) ? next : current;
}

function markerBucketWeight(bucket: string): number {
  switch (bucket) {
    case "critical":
    case "down":
      return 4;
    case "attention":
    case "warning":
    case "degraded":
      return 3;
    case "unavailable":
    case "stale":
    case "missing":
      return 2;
    case "normal":
    case "active":
    case "info":
      return 1;
    default:
      return 0;
  }
}

// SoT mockup의 markerBucket 색을 그대로 따른다: border + 옅은 tint background.
function markerBucketCellClassName(bucket: string): string {
  switch (bucket) {
    case "critical":
    case "down":
      return "border-[#fecaca] bg-[#fef2f2]";
    case "attention":
    case "warning":
    case "degraded":
      return "border-[#fcd34d] bg-[#fffbeb]";
    case "unavailable":
    case "stale":
    case "missing":
      return "border-[#d4d4d4] bg-[#f5f5f5]";
    case "normal":
    case "active":
    case "info":
      return "border-[#86efac] bg-[#f0fdf4]";
    default:
      return "border-neutral-200 bg-white";
  }
}

// date-state badge / slot label 텍스트 색. border-current와 함께 쓰면 mockup의 currentColor 테두리를 재현한다.
function markerBucketStateTextClassName(bucket: string): string {
  switch (bucket) {
    case "critical":
    case "down":
      return "text-[#b91c1c]";
    case "attention":
    case "warning":
    case "degraded":
      return "text-[#b45309]";
    case "unavailable":
    case "stale":
    case "missing":
      return "text-[#525252]";
    case "normal":
    case "active":
    case "info":
      return "text-[#15803d]";
    default:
      return "text-neutral-500";
  }
}

function markerBucketLegendClassName(bucket: string): string {
  switch (bucket) {
    case "critical":
      return "border-red-700 bg-red-700";
    case "attention":
      return "border-amber-700 bg-amber-700";
    case "unavailable":
      return "border-neutral-500 bg-neutral-500";
    case "normal":
      return "border-emerald-700 bg-emerald-700";
    default:
      return "border-neutral-300 bg-neutral-300";
  }
}

function bucketDisplayText(bucket: string): string {
  switch (bucket) {
    case "critical":
    case "down":
      return "긴급";
    case "attention":
    case "warning":
    case "degraded":
      return "확인";
    case "unavailable":
    case "stale":
    case "missing":
      return "판단불가";
    case "normal":
    case "active":
    case "info":
      return "정상";
    default:
      return humanizeStatusCode(bucket);
  }
}

function snapshotBucketSummary(countByDisplay: Map<string, number>): string {
  const summary: string[] = [];
  for (const label of SNAPSHOT_BUCKET_SUMMARY_LABELS) {
    const count = countByDisplay.get(label);
    if (count) {
      summary.push(`${label} ${count}`);
    }
  }
  for (const [label, count] of countByDisplay) {
    if (!SNAPSHOT_BUCKET_SUMMARY_LABELS.includes(label as (typeof SNAPSHOT_BUCKET_SUMMARY_LABELS)[number])) {
      summary.push(`${label} ${count}`);
    }
  }
  return summary.join(" · ");
}

function shortSlotTimeLabel(value: string): string {
  return value.replace(/\s*KST$/, "");
}

function dateMapAgeLabel(dayIndex: number): string {
  if (dayIndex === 0) {
    return "오늘";
  }
  if (dayIndex === 1) {
    return "어제";
  }
  return `${dayIndex}일 전`;
}

function dateMapLabel(dayKey: string): string {
  const [, month, day] = dayKey.split("-");
  return `${month}/${day}`;
}

function presetDisplayText(preset: HistoryPreset): string {
  switch (preset) {
    case "24h":
      return "최근 24시간";
    case "7d":
      return "최근 7일";
    case "14d":
      return "최근 14일";
    default:
      return preset;
  }
}

function SnapshotHistoryError({ error, onReload }: { error: Error; onReload: () => void }) {
  const copy = snapshotHistoryErrorCopy(error);
  return (
    <div className="border border-neutral-200 bg-white p-3 text-[12px]">
      <div className="text-neutral-900">{copy.title}</div>
      <div className="mt-1 text-neutral-500">{copy.body}</div>
      <Button variant="outline" size="sm" className="mt-3 gap-2 border-neutral-300" onClick={onReload}>
        <RefreshCw className="h-3.5 w-3.5" strokeWidth={1.5} /> 다시 시도
      </Button>
    </div>
  );
}

function snapshotHistoryErrorCopy(error: Error): { title: string; body: string } {
  if (error instanceof AuthRequiredError) {
    return {
      title: "인증 필요",
      body: error.message,
    };
  }
  if (error instanceof ApiRequestError && error.status === 404) {
    return {
      title: "스냅샷 기록 없음",
      body: "보관 기간 안에서 선택한 Project/Application의 저장된 snapshot point를 찾을 수 없습니다.",
    };
  }
  if (error instanceof ApiRequestError && error.status === 400) {
    return {
      title: "History 조회 조건 확인 필요",
      body: "지원하는 fixed preset은 24h, 7d, 14d뿐입니다.",
    };
  }
  return {
    title: "스냅샷 기록 로드 실패",
    body: "저장된 snapshot marker를 불러오지 못했습니다.",
  };
}

function ResourceMessage({ body, title }: { body: string; title: string }) {
  return (
    <div className="border border-neutral-200 bg-white p-3 text-[12px]">
      <div className="text-neutral-900">{title}</div>
      <div className="mt-1 text-neutral-500">{body}</div>
    </div>
  );
}
