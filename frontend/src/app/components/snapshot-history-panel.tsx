import { useCallback, useEffect, useMemo, useState } from "react";
import type { LucideIcon } from "lucide-react";
import { CalendarDays, Clock3, FileSearch, History, RefreshCw } from "lucide-react";
import { ApiRequestError, AuthRequiredError, NO_STORE_REQUEST_OPTIONS, readJsonResource } from "../lib/api";
import { type AuthFetch } from "../lib/auth";
import { guardSnapshotHistoryReadModels, guardSnapshotMarkerReadModel } from "../lib/read-model-contract-guard";
import { useApiResource } from "../lib/use-api-resource";
import {
  buildSnapshotHistoryPaths,
  formatNullableRatio,
  formatOptionalDateTime,
  HISTORY_PRESET_QUERY,
  humanizeAnchorStatus,
  humanizeCaptureReason,
  humanizeOrderCode,
  humanizeStatusCode,
  snapshotRetentionDayKeys,
  snapshotSlotDayKey,
  snapshotSlotIndexFromWindowEndUtc,
  snapshotSlotTimeLabel,
  severityBadgeClassName,
  severityDisplayText,
  type ApplicationPresentationItem,
  type DashboardPresentation,
  type ProjectPresentationItem,
} from "../lib/read-model-adapters";
import type {
  DashboardSnapshotMarkerItem,
  DashboardSnapshotMarkerReadModel,
  HistoryPreset,
  OperationalEventHistoryReadModel,
} from "../lib/read-model-types";
import { SnapshotDetailSurface, type SnapshotDetailTarget } from "./snapshot-detail-surface";
import type { SnapshotInstanceDashboardTarget } from "./instance-dashboard-surface";
import { Button } from "./ui/button";

const SNAPSHOT_RETENTION_DAYS = 14;
const HALF_HOUR_SLOT_COUNT = 48;

type SnapshotHistoryModel = {
  events: OperationalEventHistoryReadModel;
  markers: DashboardSnapshotMarkerReadModel;
  retentionMarkers: DashboardSnapshotMarkerReadModel;
};

type SnapshotDateCell = {
  bucket: string;
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

function SectionLabel({ icon: Icon, children }: { icon: LucideIcon; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-1.5 text-[11px] uppercase text-neutral-500">
      <Icon className="h-3.5 w-3.5" strokeWidth={1.5} />
      {children}
    </div>
  );
}

/**
 * Snapshot history를 marker-first 30분 dashboard point 탐색 UI로 렌더링한다.
 * history/detail fetch는 selected Project/Application/preset/snapshot target을 resourceKey에 포함해 stale 응답을 폐기한다.
 */
export function SnapshotHistoryPanel({
  dashboard,
  onOpenSnapshotInstanceDashboard,
  selectedApplication,
  selectedProject,
}: {
  dashboard: DashboardPresentation;
  onOpenSnapshotInstanceDashboard: (target: SnapshotInstanceDashboardTarget) => void;
  selectedApplication: ApplicationPresentationItem;
  selectedProject: ProjectPresentationItem;
}) {
  const [preset, setPreset] = useState<HistoryPreset>("24h");
  const [selectedDayKey, setSelectedDayKey] = useState<string | null>(null);
  const [selectedSlotKey, setSelectedSlotKey] = useState<string | null>(null);
  const [detailTarget, setDetailTarget] = useState<SnapshotDetailTarget | null>(null);
  const historyResourceKey = `${selectedProject.projectId}|${selectedApplication.applicationId}|${preset}`;

  useEffect(() => {
    setSelectedDayKey(null);
    setSelectedSlotKey(null);
    setDetailTarget(null);
  }, [preset, selectedApplication.applicationId, selectedProject.projectId]);

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
        preset === "14d"
          ? Promise.resolve(null)
          : authFetch(retentionPaths.markers, {
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
      const retentionMarkers = retentionMarkersResponse
        ? guardSnapshotMarkerReadModel(await readJsonResource<DashboardSnapshotMarkerReadModel>(retentionMarkersResponse), {
            applicationId: selectedApplication.applicationId,
            markerLimit: HISTORY_PRESET_QUERY["14d"].markerLimit,
            preset: "14d",
          })
        : guardedHistory.markers;
      return {
        ...guardedHistory,
        retentionMarkers,
      };
    },
    [preset, selectedApplication.applicationId, selectedProject.projectId],
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
    <div className="border border-neutral-200 bg-white">
      <div className="flex items-center justify-between gap-2 border-b border-neutral-200 px-3 py-2.5">
        <SectionLabel icon={History}>스냅샷 기록</SectionLabel>
        <StatusBadge>{presetDisplayText(preset)}</StatusBadge>
      </div>
      <div className="border-b border-neutral-100 p-3">
        <div className="flex flex-wrap gap-2">
          {(["24h", "7d", "14d"] as const).map((candidate) => (
            <Button
              key={candidate}
              variant={preset === candidate ? "default" : "outline"}
              size="sm"
              className="h-8 min-w-24 border-neutral-300"
              onClick={() => setPreset(candidate)}
            >
              {presetDisplayText(candidate)}
            </Button>
          ))}
        </div>
      </div>
      <div className="space-y-4 p-4 text-[12px] text-neutral-600">
        {dashboard.snapshot === null && (
          <div className="border border-neutral-200 bg-neutral-50 p-2">
            현재 live dashboard에 직접 연결된 스냅샷은 없습니다. 저장된 30분 point가 있으면 아래 marker 탐색에서 불러옵니다.
          </div>
        )}
        {loading && <ResourceMessage title="기록 로딩 중" body={`${presetDisplayText(preset)} 범위의 30분 snapshot point를 불러오는 중입니다.`} />}
        {error && <SnapshotHistoryError error={error} onReload={resource.reload} />}
        {!loading && !error && history && (
          <SnapshotHistoryReady
            events={history.events}
            markers={history.markers}
            retentionMarkers={history.retentionMarkers}
            onSelectDay={(dayKey) => {
              setSelectedDayKey(dayKey);
              setSelectedSlotKey(null);
              setDetailTarget(null);
            }}
            onSelectMarker={(marker) => {
              setSelectedSlotKey(marker.snapshotId);
              setDetailTarget({
                snapshotId: marker.snapshotId,
                snapshotLink: marker.links.snapshot,
              });
            }}
            onSelectEvent={(eventTarget) => {
              setSelectedSlotKey(eventTarget.snapshotId ?? null);
              setDetailTarget(eventTarget);
            }}
            preset={preset}
            selectedDayKey={selectedDayKey}
            selectedSlotKey={selectedSlotKey}
          />
        )}
        <SnapshotDetailSurface
          applicationId={selectedApplication.applicationId}
          onOpenSnapshotInstanceDashboard={onOpenSnapshotInstanceDashboard}
          projectId={selectedProject.projectId}
          target={detailTarget}
        />
      </div>
    </div>
  );
}

function SnapshotHistoryReady({
  events,
  markers,
  retentionMarkers,
  onSelectDay,
  onSelectEvent,
  onSelectMarker,
  preset,
  selectedDayKey,
  selectedSlotKey,
}: {
  events: OperationalEventHistoryReadModel;
  markers: DashboardSnapshotMarkerReadModel;
  retentionMarkers: DashboardSnapshotMarkerReadModel;
  onSelectDay: (dayKey: string) => void;
  onSelectEvent: (target: SnapshotDetailTarget) => void;
  onSelectMarker: (marker: DashboardSnapshotMarkerItem) => void;
  preset: HistoryPreset;
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
  const selectedMarker = useMemo(
    () =>
      retentionMarkers.markers.find((marker) => marker.snapshotId === selectedSlotKey) ??
      markers.markers.find((marker) => marker.snapshotId === selectedSlotKey) ??
      null,
    [markers.markers, retentionMarkers.markers, selectedSlotKey],
  );

  return (
    <>
      <div className="space-y-2 border border-neutral-200 bg-white p-3">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <SectionLabel icon={History}>Snapshot / History</SectionLabel>
            <p className="mt-1 text-[12px] text-neutral-500">
              markerBucket은 state가 아니라 30분 dashboard point 탐색 색인입니다. detail은 stored read model에서만 복원합니다.
            </p>
          </div>
          <StatusBadge>{`default ${HISTORY_PRESET_QUERY["24h"].since}`}</StatusBadge>
        </div>
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-6">
          <InfoCell label="retention" value={`${SNAPSHOT_RETENTION_DAYS}일`} />
          <InfoCell label="scheduled points" value={`${SNAPSHOT_RETENTION_DAYS * HALF_HOUR_SLOT_COUNT} point`} />
          <InfoCell label="cadence" value="30분 정기 저장" />
          <InfoCell label="per day" value="48/day" />
          <InfoCell label="default view" value="24h" />
          <InfoCell label="cleanup" value="14일 이후 만료" />
        </div>
        <p className="text-[11px] text-neutral-500">
          날짜/slot map은 14일 retention marker query 기준입니다. 현재 preset은 보조 event/server marker list를 API query limit {HISTORY_PRESET_QUERY[preset].markerLimit}개로 좁히며, 보관 기간 밖 snapshot은 현재 dashboard로 대체하지 않습니다.
        </p>
      </div>

      <div className="border border-neutral-200 bg-white">
        <div className="flex items-center justify-between gap-2 border-b border-neutral-100 px-3 py-2">
          <SectionLabel icon={CalendarDays}>Snapshot date map</SectionLabel>
          <span className="text-[11px] text-neutral-500">{humanizeOrderCode(markers.horizon.order)}</span>
        </div>
        <div className="p-3">
          <p className="text-[12px] text-neutral-500">
            날짜 색은 해당 날짜의 가장 높은 markerBucket 요약입니다. state/evidence source가 아니며, slot을 열면 저장된 read model에서 복원합니다.
          </p>
          <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-7">
            {dateMap.days.map((day) => (
              <button
                key={day.key}
                type="button"
                className={`min-h-20 border p-2 text-left transition hover:border-neutral-800 ${markerBucketCellClassName(day.bucket)} ${day.selected ? "ring-2 ring-neutral-900" : ""}`}
                onClick={() => onSelectDay(day.key)}
              >
                <span className="block text-[12px] text-neutral-900">{day.label}</span>
                <span className="mt-1 block text-[11px] text-neutral-600">{day.summary}</span>
                <span className="mt-2 block text-[11px] text-neutral-500">{day.count} point</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="border border-neutral-200 bg-white">
        <div className="flex items-center justify-between gap-2 border-b border-neutral-100 px-3 py-2">
          <SectionLabel icon={Clock3}>{dateMap.selectedDay?.label ?? "선택된 날짜 없음"} · 30분 slots</SectionLabel>
          <span className="text-[11px] text-neutral-500">하루 48개 slot</span>
        </div>
        <div className="p-3">
          <div className="grid grid-cols-4 gap-1.5 sm:grid-cols-6 lg:grid-cols-8">
            {slotCells.map((slot) => (
              <button
                key={slot.key}
                type="button"
                disabled={!slot.marker}
                title={slot.title}
                className={`min-h-14 border p-1.5 text-left transition ${slot.marker ? "hover:border-neutral-900" : "cursor-not-allowed opacity-60"} ${markerBucketCellClassName(slot.bucket)} ${slot.selected ? "ring-2 ring-neutral-900" : ""}`}
                onClick={() => {
                  if (slot.marker) {
                    onSelectMarker(slot.marker);
                  }
                }}
              >
                <span className="block text-[11px] text-neutral-900">{slot.time}</span>
                <span className="mt-0.5 block break-words text-[10px] leading-tight text-neutral-600">{slot.label}</span>
              </button>
            ))}
          </div>
          <p className="mt-2 text-[11px] text-neutral-500">
            빈 slot은 예약된 30분 위치에 marker row가 없다는 뜻입니다. 저장본 부재나 현재 상태를 새로 판정하지 않습니다.
          </p>
        </div>
      </div>

      <SelectedSnapshotSummary marker={selectedMarker} />

      <div className="border border-neutral-200 bg-white">
        <div className="flex items-center justify-between gap-2 border-b border-neutral-100 px-3 py-2">
          <SectionLabel icon={FileSearch}>Secondary server marker order</SectionLabel>
          <span className="text-[11px] text-neutral-500">server order · 재정렬 없음</span>
        </div>
        {markers.markers.length === 0 ? (
          <div className="p-3 text-[12px] text-neutral-500">
            {markers.emptyState?.message ?? "보관 기간 안에 표시할 snapshot marker가 없습니다."} 저장된 snapshot point가 없으면 live/current 값으로 대체하지 않습니다.
          </div>
        ) : (
          <ul className="max-h-96 overflow-auto">
            {markers.markers.map((marker) => (
              <li key={marker.markerId} className="border-b border-neutral-100 p-3 last:border-b-0">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-neutral-900">{marker.title}</div>
                    <div className="mt-0.5 text-neutral-600">{marker.summary}</div>
                    <div className="mt-1 text-[11px] text-neutral-500">
                      markerBucket {humanizeStatusCode(marker.type)} · 저장 상태 {humanizeStatusCode(marker.storedApplicationStateCode)} · {humanizeCaptureReason(marker.captureReason)}
                    </div>
                  </div>
                  <StatusBadge className={markerBucketBadgeClassName(marker.type)}>{humanizeStatusCode(marker.type)}</StatusBadge>
                </div>
                <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500 md:grid-cols-3">
                  <InfoCell label="slot end" value={formatOptionalDateTime(marker.currentWindowEndUtc)} />
                  <InfoCell label="capturedAt" value={formatOptionalDateTime(marker.capturedAt)} />
                  <InfoCell label="stored state" value={humanizeStatusCode(marker.storedApplicationStateCode)} />
                  <InfoCell label="read meaning" value={humanizeStatusCode(marker.readMeaning)} />
                  <InfoCell label="신뢰도" value={formatNullableRatio(marker.confidence)} />
                  <InfoCell label="주요 엔드포인트" value={marker.primaryEndpointKey ?? "해당 엔드포인트 없음"} />
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  className="mt-2 gap-2 border-neutral-300"
                  onClick={() => onSelectMarker(marker)}
                >
                  <History className="h-3.5 w-3.5" strokeWidth={1.5} /> Snapshot 열기
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <details className="border border-neutral-200 bg-white">
        <summary className="cursor-pointer px-3 py-2 text-[11px] uppercase text-neutral-500">
          상태 변화 기록 보조 context
        </summary>
        <div className="border-t border-neutral-100">
          <div className="flex items-center justify-between gap-2 px-3 py-2 text-[11px] text-neutral-500">
            <span>{humanizeOrderCode(events.horizon.order)}</span>
            <span>marker/date/slot 탐색 아래에만 표시</span>
          </div>
          {events.events.length === 0 ? (
            <div className="p-3 text-[12px] text-neutral-500">
              이 범위에 표시할 상태 변화 기록이 없습니다.
            </div>
          ) : (
            <ul>
              {events.events.map((event) => (
                <li key={event.eventId} className="border-t border-neutral-100 p-3 first:border-t-0">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="text-neutral-900">{event.title}</div>
                      <div className="mt-0.5 text-neutral-600">{event.summary}</div>
                      <div className="mt-1 text-[11px] text-neutral-500">
                        {humanizeStatusCode(event.type)} · 저장 당시 상태 {humanizeStatusCode(event.stateCode)} · 발생 {formatOptionalDateTime(event.occurredAt)} · 해결 {formatOptionalDateTime(event.resolvedAt)}
                      </div>
                    </div>
                    <StatusBadge className={severityBadgeClassName(event.severity)}>{severityDisplayText(event.severity)}</StatusBadge>
                  </div>
                  <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500 md:grid-cols-3">
                    <InfoCell label="기록 ID" value={event.eventId} />
                    <InfoCell label="신뢰도" value={formatNullableRatio(event.confidence)} />
                    <InfoCell label="판단 기준" value={event.evidence.ruleId ?? "적용된 판단 기준 없음"} />
                    <InfoCell label="엔드포인트" value={event.evidence.endpointKey ?? "해당 엔드포인트 없음"} />
                    <InfoCell label="상세 근거" value={event.evidence.snapshotDetailAnchor ?? "연결된 상세 근거 없음"} />
                    <InfoCell label="근거 연결" value={humanizeAnchorStatus(event.evidence.anchorStatus)} />
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    className="mt-2 gap-2 border-neutral-300"
                    onClick={() => {
                      onSelectEvent({
                        activeAnchor: event.evidence.snapshotDetailAnchor,
                        snapshotId: event.snapshotId,
                        snapshotLink: event.links.snapshot,
                      });
                    }}
                  >
                    <History className="h-3.5 w-3.5" strokeWidth={1.5} /> 연결 snapshot 열기
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </details>
    </>
  );
}

function SelectedSnapshotSummary({ marker }: { marker: DashboardSnapshotMarkerItem | null }) {
  if (!marker) {
    return (
      <div className="border border-neutral-200 bg-white p-3 text-[12px] text-neutral-500">
        날짜와 30분 slot을 선택하면 selected snapshot summary가 snapshotId, capturedAt, currentWindowEndUtc, markerBucket, stored state, capture reason으로 분리되어 표시됩니다.
      </div>
    );
  }

  return (
    <div className="border border-neutral-200 bg-white">
      <div className="flex items-center justify-between gap-2 border-b border-neutral-100 px-3 py-2">
        <SectionLabel icon={FileSearch}>Selected snapshot summary</SectionLabel>
        <StatusBadge className={markerBucketBadgeClassName(marker.type)}>{`markerBucket=${humanizeStatusCode(marker.type)}`}</StatusBadge>
      </div>
      <div className="grid gap-2 p-3 text-[11px] sm:grid-cols-2 lg:grid-cols-3">
        <InfoCell label="snapshotId" value={marker.snapshotId} />
        <InfoCell label="capturedAt" value={formatOptionalDateTime(marker.capturedAt)} />
        <InfoCell label="currentWindowEndUtc" value={formatOptionalDateTime(marker.currentWindowEndUtc)} />
        <InfoCell label="markerBucket" value={humanizeStatusCode(marker.type)} />
        <InfoCell label="stored state" value={humanizeStatusCode(marker.storedApplicationStateCode)} />
        <InfoCell label="capture reason" value={humanizeCaptureReason(marker.captureReason)} />
      </div>
      <div className="border-t border-neutral-100 px-3 py-2 text-[11px] text-neutral-500">
        source=dashboard_snapshots.read_model_json · snapshotDetailRecalculates=false · markerIsStateSource=false
      </div>
    </div>
  );
}

/**
 * 날짜 cell의 markerBucket은 색상/탐색 색인일 뿐 state나 evidence source가 아니다.
 * 같은 날짜에 여러 marker가 있으면 더 눈에 띄는 bucket만 date map summary 색으로 사용한다.
 */
function buildSnapshotDateMap(markers: DashboardSnapshotMarkerReadModel, selectedDayKey: string | null): { days: SnapshotDateCell[]; selectedDay: SnapshotDateCell | null } {
  const dayKeys = snapshotRetentionDayKeys(markers.horizon.until, SNAPSHOT_RETENTION_DAYS);
  const days: SnapshotDateCell[] = [];
  let selectedDay: SnapshotDateCell | null = null;
  let firstDayWithPoint: SnapshotDateCell | null = null;

  for (const key of dayKeys) {
    let bucket = "none";
    let count = 0;
    for (const marker of markers.markers) {
      if (snapshotSlotDayKey(marker.currentWindowEndUtc) !== key) {
        continue;
      }
      count += 1;
      bucket = moreVisibleMarkerBucket(bucket, marker.type);
    }
    const cell: SnapshotDateCell = {
      bucket,
      count,
      key,
      label: dateMapLabel(key),
      selected: selectedDayKey === key,
      summary: count > 0 ? `${humanizeStatusCode(bucket)} marker` : "저장 point 없음",
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
    const bucket = marker?.type ?? "none";
    slots.push({
      bucket,
      key: `${selectedDayKey}-${slotIndex}`,
      label: marker ? humanizeStatusCode(bucket) : "저장 point 없음",
      marker,
      selected: Boolean(marker && selectedSlotKey === marker.snapshotId),
      time: snapshotSlotTimeLabel(slotIndex),
      title: marker
        ? `${snapshotSlotTimeLabel(slotIndex)} · markerBucket=${marker.type} · storedState=${marker.storedApplicationStateCode}`
        : `${snapshotSlotTimeLabel(slotIndex)} · 예약 slot에 저장된 marker 없음 · source absence 판정 아님`,
    });
  }
  return slots;
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

function markerBucketCellClassName(bucket: string): string {
  switch (bucket) {
    case "critical":
    case "down":
      return "border-red-300 bg-red-50 text-red-800";
    case "attention":
    case "warning":
    case "degraded":
      return "border-amber-300 bg-amber-50 text-amber-800";
    case "unavailable":
    case "stale":
    case "missing":
      return "border-neutral-300 bg-neutral-100 text-neutral-700";
    case "normal":
    case "active":
    case "info":
      return "border-emerald-300 bg-emerald-50 text-emerald-800";
    default:
      return "border-neutral-200 bg-white text-neutral-700";
  }
}

function markerBucketBadgeClassName(bucket: string): string {
  switch (bucket) {
    case "critical":
    case "down":
      return "border-red-300 bg-red-50 text-red-700";
    case "attention":
    case "warning":
    case "degraded":
      return "border-amber-300 bg-amber-50 text-amber-700";
    case "normal":
    case "active":
    case "info":
      return "border-emerald-300 bg-emerald-50 text-emerald-800";
    default:
      return "border-neutral-400 bg-white text-neutral-800";
  }
}

function dateMapLabel(dayKey: string): string {
  const [, month, day] = dayKey.split("-");
  return `${month}/${day} UTC`;
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

function InfoCell({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 border border-neutral-200 bg-white p-2">
      <div className="text-[11px] text-neutral-500">{label}</div>
      <div className="break-words text-[12px] text-neutral-900">{value}</div>
    </div>
  );
}

function ResourceMessage({ body, title }: { body: string; title: string }) {
  return (
    <div className="border border-neutral-200 bg-white p-3 text-[12px]">
      <div className="text-neutral-900">{title}</div>
      <div className="mt-1 text-neutral-500">{body}</div>
    </div>
  );
}
