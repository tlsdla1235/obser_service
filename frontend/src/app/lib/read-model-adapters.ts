import { ApiRequestError } from "./api.js";
import type {
  ApplicationDashboardReadModel,
  ConcernSummary,
  DashboardWindow,
  HistoryPreset,
  HistogramBucket,
  HistogramWindow,
  LifecycleBadge,
  LifecycleStateCode,
  ProjectApplicationNavigationApplicationItem,
  ProjectApplicationNavigationReadModel,
  ProjectNavigationProjectItem,
  ProjectNavigationReadModel,
  TrendPreset,
} from "./read-model-types.js";

const PROJECT_APPLICATIONS_PATH = /^\/api\/projects\/([^/]+)\/applications$/;
const APPLICATION_DASHBOARD_PATH = /^\/api\/projects\/([^/]+)\/applications\/([^/]+)\/dashboard$/;
const LIVE_INSTANCE_DASHBOARD_PATH = /^\/api\/projects\/([^/]+)\/applications\/([^/]+)\/instances\/([^/]+)\/dashboard$/;
const SNAPSHOT_INSTANCE_DASHBOARD_PATH = /^\/api\/projects\/([^/]+)\/applications\/([^/]+)\/snapshots\/([^/]+)\/instances\/([^/]+)\/dashboard$/;
const INSTANCE_EVIDENCE_PATH = /^\/api\/projects\/([^/]+)\/applications\/([^/]+)\/instances\/([^/]+)\/evidence$/;
const INSTANCE_SNAPSHOT_TREND_PATH = /^\/api\/projects\/([^/]+)\/applications\/([^/]+)\/instances\/([^/]+)\/snapshot-trend$/;
const SNAPSHOT_DETAIL_PATH = /^\/api\/projects\/([^/]+)\/applications\/([^/]+)\/dashboard\/snapshots\/([^/]+)$/;
const UNKNOWN_TEXT = "확인할 수 없음";
const EMPTY_DISPLAY_TEXT = "해당 없음";
const BASELINE_NOT_USED_TEXT = "MVP 판단에 사용하지 않음";
const DISPLAY_TIME_ZONE = "Asia/Seoul";
const DISPLAY_TIME_ZONE_LABEL = "KST";

export const TREND_PRESET_QUERY = {
  "7d": { limit: 336, since: "7d" },
  "14d": { limit: 672, since: "14d" },
} as const satisfies Record<TrendPreset, { limit: number; since: TrendPreset }>;

export const HISTORY_PRESET_QUERY = {
  "24h": {
    eventLimit: 50,
    markerLimit: 48,
    since: "24h",
  },
  "7d": {
    eventLimit: 100,
    markerLimit: 336,
    since: "7d",
  },
  "14d": {
    eventLimit: 100,
    markerLimit: 672,
    since: "14d",
  },
} as const satisfies Record<HistoryPreset, { eventLimit: number; markerLimit: number; since: HistoryPreset }>;

/**
 * backend DTO를 화면 표시 모델로 옮기는 adapter 모음이다.
 * server order와 server-computed 의미를 보존하고, null 방어와 표시 문자열만 만든다.
 */

export type ProjectPresentationItem = ProjectNavigationProjectItem & {
  applicationsLink: string;
  recentConcernDisplay: string;
  recentConcernMeta: string;
};

export type ApplicationPresentationItem = ProjectApplicationNavigationApplicationItem & {
  dashboardLink: string;
  lifecycleBadgeDisplay: string;
  lifecycleBadgeMeta: string;
  lifecycleBadgeClassName: string;
  metricLastAcceptedBucketDisplay: string;
  starterLastHeartbeatDisplay: string;
  topConcernDisplay: string;
  topConcernMeta: string;
};

export type DashboardPresentation = ApplicationDashboardReadModel & {
  baselineDecisionDisplay: string;
  baselineWindowDisplay: string;
  bucketEndBoundaryDisplay: string;
  canonicalWindowDisplay: string;
  dataQualityLastObservedDisplay: string;
  currentWindowDisplay: string;
  generatedAtDisplay: string;
  lastAcceptedBucketDisplay: string;
  lastHealthyDisplay: string;
  metricStateClassName: string;
  readSemanticsBucketSourceDisplay: string;
  readSemanticsSourceDisplay: string;
  sourceScopedReasonDisplay: string;
  starterLastHeartbeatDisplay: string;
};

export type DisplayLatencyBucket = {
  count: number;
  cumulativeCount: number;
  key: string;
  label: string;
  leMs: number;
};

export function toProjectPresentationItems(model: ProjectNavigationReadModel): ProjectPresentationItem[] {
  return model.projects.map((project) => ({
    ...project,
    applicationsLink: project.links.applications,
    recentConcernDisplay: concernDisplay(project.recentConcern),
    recentConcernMeta: concernMeta(project.recentConcern),
  }));
}

export function toApplicationPresentationItems(
  model: ProjectApplicationNavigationReadModel,
): ApplicationPresentationItem[] {
  return model.applications.map((application) => ({
    ...application,
    dashboardLink: application.links.dashboard,
    lifecycleBadgeDisplay: lifecycleBadgeDisplay(application.lifecycleBadge),
    lifecycleBadgeMeta: lifecycleBadgeMeta(application.lifecycleBadge),
    lifecycleBadgeClassName: lifecycleBadgeClassName(application.lifecycleBadge.code),
    metricLastAcceptedBucketDisplay: formatOptionalDateTime(application.metricData.lastAcceptedBucketAt),
    starterLastHeartbeatDisplay: formatOptionalDateTime(application.starterConnection.lastHeartbeatAt),
    topConcernDisplay: concernDisplay(application.topConcern),
    topConcernMeta: concernMeta(application.topConcern),
  }));
}

export function toDashboardPresentation(model: ApplicationDashboardReadModel): DashboardPresentation {
  const { application, dataQuality, generatedAt, readSemantics, sourceScopedPercentiles, starterConnection, window } = model;
  const { code } = model.state;
  return {
    ...model,
    baselineDecisionDisplay: readSemantics.baselineComparisonUsedForMvpDecision ? formatOptionalWindow(application.sourceWindow.baseline) : BASELINE_NOT_USED_TEXT,
    baselineWindowDisplay: readSemantics.baselineComparisonUsedForMvpDecision ? formatOptionalWindow(application.sourceWindow.baseline) : BASELINE_NOT_USED_TEXT,
    bucketEndBoundaryDisplay: readSemantics.bucketEndBoundary,
    canonicalWindowDisplay: formatWindow(window),
    dataQualityLastObservedDisplay: formatOptionalDateTime(dataQuality.lastObservedAt),
    currentWindowDisplay: formatWindow(application.sourceWindow.current),
    generatedAtDisplay: formatOptionalDateTime(generatedAt),
    lastAcceptedBucketDisplay: formatOptionalDateTime(application.lastAcceptedBucketAt),
    lastHealthyDisplay: formatOptionalDateTime(application.lastHealthyAt),
    metricStateClassName: metricStateClassName(code),
    readSemanticsBucketSourceDisplay: humanizeSourceCode(readSemantics.bucketDistributionSource),
    readSemanticsSourceDisplay: humanizeSourceCode(readSemantics.source),
    sourceScopedReasonDisplay: humanizeCode(sourceScopedPercentiles.reason ?? UNKNOWN_TEXT),
    starterLastHeartbeatDisplay: formatOptionalDateTime(starterConnection.lastHeartbeatAt),
  };
}

export function validateProjectApplicationsPath(link: string, expectedProjectId: string): string {
  const path = internalApiPath(link, "project_applications_link_invalid");
  const match = PROJECT_APPLICATIONS_PATH.exec(path);
  if (!match || match[1] !== expectedProjectId) {
    throw new ApiRequestError("project_applications_link_invalid");
  }
  return path;
}

export function validateDashboardPath(link: string, expectedProjectId: string, expectedApplicationId: string): string {
  const path = internalApiPath(link, "dashboard_link_invalid");
  const match = APPLICATION_DASHBOARD_PATH.exec(path);
  if (!match || match[1] !== expectedProjectId || match[2] !== expectedApplicationId) {
    throw new ApiRequestError("dashboard_link_invalid");
  }
  return path;
}

export function validateInstanceEvidencePath(
  link: string,
  expectedProjectId: string,
  expectedApplicationId: string,
  expectedInstanceId: string,
): string {
  const path = internalApiPath(link, "instance_evidence_link_invalid");
  const match = INSTANCE_EVIDENCE_PATH.exec(path);
  if (
    !match ||
    match[1] !== expectedProjectId ||
    match[2] !== expectedApplicationId ||
    match[3] !== expectedInstanceId
  ) {
    throw new ApiRequestError("instance_evidence_link_invalid");
  }
  return path;
}

export function validateLiveInstanceDashboardPath(
  link: string,
  expectedProjectId: string,
  expectedApplicationId: string,
  expectedInstanceId: string,
): string {
  const path = internalApiPath(link, "live_instance_dashboard_link_invalid");
  const match = LIVE_INSTANCE_DASHBOARD_PATH.exec(path);
  if (
    !match ||
    match[1] !== expectedProjectId ||
    match[2] !== expectedApplicationId ||
    match[3] !== expectedInstanceId
  ) {
    throw new ApiRequestError("live_instance_dashboard_link_invalid");
  }
  return path;
}

export function validateSnapshotInstanceDashboardPath(
  link: string,
  expectedProjectId: string,
  expectedApplicationId: string,
  expectedSnapshotId: string,
  expectedInstanceId: string,
): string {
  const path = internalApiPath(link, "snapshot_instance_dashboard_link_invalid");
  const match = SNAPSHOT_INSTANCE_DASHBOARD_PATH.exec(path);
  if (
    !match ||
    match[1] !== expectedProjectId ||
    match[2] !== expectedApplicationId ||
    match[3] !== expectedSnapshotId ||
    match[4] !== expectedInstanceId
  ) {
    throw new ApiRequestError("snapshot_instance_dashboard_link_invalid");
  }
  return path;
}

export function validateInstanceSnapshotTrendPath(
  link: string,
  expectedProjectId: string,
  expectedApplicationId: string,
  expectedInstanceId: string,
): string {
  const path = internalApiPath(link, "instance_snapshot_trend_link_invalid");
  const match = INSTANCE_SNAPSHOT_TREND_PATH.exec(path);
  if (
    !match ||
    match[1] !== expectedProjectId ||
    match[2] !== expectedApplicationId ||
    match[3] !== expectedInstanceId
  ) {
    throw new ApiRequestError("instance_snapshot_trend_link_invalid");
  }
  return path;
}

export function validateSnapshotDetailPath(
  link: string,
  expectedProjectId: string,
  expectedApplicationId: string,
  expectedSnapshotId?: string,
): string {
  const path = internalApiPath(link, "snapshot_detail_link_invalid");
  const match = SNAPSHOT_DETAIL_PATH.exec(path);
  if (
    !match ||
    match[1] !== expectedProjectId ||
    match[2] !== expectedApplicationId ||
    (expectedSnapshotId && match[3] !== expectedSnapshotId)
  ) {
    throw new ApiRequestError("snapshot_detail_link_invalid");
  }
  return path;
}

export function snapshotIdFromDetailPath(path: string): string {
  const match = SNAPSHOT_DETAIL_PATH.exec(path);
  if (!match) {
    throw new ApiRequestError("snapshot_detail_link_invalid");
  }
  return match[3];
}

/**
 * Live Instance Dashboard는 dashboard read model에서 받은 legacy evidence link에 의존하지 않고
 * selected Project/Application/Instance context로 새 13.8 endpoint를 구성한다.
 */
export function buildLiveInstanceDashboardPath(
  projectId: string,
  applicationId: string,
  instanceId: string,
): string {
  return `/api/projects/${safePathComponent(projectId)}/applications/${safePathComponent(applicationId)}/instances/${safePathComponent(instanceId)}/dashboard`;
}

/**
 * Snapshot Instance Dashboard는 selected Application Snapshot id를 path에 고정해
 * live/current evidence fallback으로 흐르지 않게 한다.
 */
export function buildSnapshotInstanceDashboardPath(
  projectId: string,
  applicationId: string,
  snapshotId: string,
  instanceId: string,
): string {
  return `/api/projects/${safePathComponent(projectId)}/applications/${safePathComponent(applicationId)}/snapshots/${safePathComponent(snapshotId)}/instances/${safePathComponent(instanceId)}/dashboard`;
}

/**
 * Trend 직접 진입은 evidence response link가 없을 때만 쓰는 문서화된 fallback이다.
 * caller가 넘긴 selected context를 path component로만 인코딩하고 임의 URL input은 받지 않는다.
 */
export function buildInstanceSnapshotTrendPath(
  projectId: string,
  applicationId: string,
  instanceId: string,
): string {
  return `/api/projects/${safePathComponent(projectId)}/applications/${safePathComponent(applicationId)}/instances/${safePathComponent(instanceId)}/snapshot-trend`;
}

export function trendPathWithPreset(path: string, preset: TrendPreset): string {
  const query = TREND_PRESET_QUERY[preset];
  return `${path}?since=${query.since}&limit=${query.limit}`;
}

export function buildSnapshotHistoryPaths(projectId: string, applicationId: string, preset: HistoryPreset) {
  const query = HISTORY_PRESET_QUERY[preset];
  const projectPath = safePathComponent(projectId);
  const applicationPath = safePathComponent(applicationId);
  return {
    events: `/api/projects/${projectPath}/applications/${applicationPath}/operational-events?since=${query.since}&limit=${query.eventLimit}`,
    markers: `/api/projects/${projectPath}/applications/${applicationPath}/dashboard/snapshot-markers?since=${query.since}&limit=${query.markerLimit}`,
  };
}

/**
 * Snapshot date map은 currentWindowEndUtc end-boundary를 기준으로 날짜를 나눈다.
 * 정확한 00:00Z boundary는 새 날짜 00:00이 아니라 전날 24:00 slot으로 표시한다.
 */
export function snapshotSlotDayKey(value: string): string {
  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) {
    return "invalid-date";
  }
  const date = new Date(parsed);
  if (date.getUTCHours() === 0 && date.getUTCMinutes() === 0 && date.getUTCSeconds() === 0 && date.getUTCMilliseconds() === 0) {
    return snapshotUtcDateKey(new Date(date.getTime() - 24 * 60 * 60 * 1000));
  }
  return snapshotUtcDayKey(value);
}

/**
 * 14일 retention date map의 day key를 최신 slot day부터 과거 방향으로 만든다.
 * horizon.until이 00:00Z이면 slot day 기준 전날을 최신 날짜로 삼아 marker bucketing과 맞춘다.
 */
export function snapshotRetentionDayKeys(until: string, days: number): string[] {
  const endKey = snapshotSlotDayKey(until);
  if (endKey === "invalid-date") {
    return [];
  }
  const end = snapshotUtcDayStart(`${endKey}T00:00:00Z`);
  if (!end) {
    return [];
  }
  const keys: string[] = [];
  for (let offset = 0; offset < days; offset += 1) {
    const cursor = new Date(end.getTime() - offset * 24 * 60 * 60 * 1000);
    keys.push(snapshotUtcDateKey(cursor));
  }
  return keys;
}

/**
 * currentWindowEndUtc end-boundary를 0~47 slot index로 변환한다.
 * API guard가 30분 boundary만 통과시키므로 이 함수는 표시용 index 계산만 담당한다.
 */
export function snapshotSlotIndexFromWindowEndUtc(value: string): number {
  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) {
    return -1;
  }
  const date = new Date(parsed);
  const endMinutes = date.getUTCHours() * 60 + date.getUTCMinutes();
  const normalizedEndMinutes = endMinutes === 0 ? 24 * 60 : endMinutes;
  return Math.floor(normalizedEndMinutes / 30) - 1;
}

/**
 * slot index를 end-boundary label로 표시한다.
 * slot 계산은 UTC boundary 계약을 따르지만, operator 화면에는 같은 instant를 KST로 보여준다.
 */
export function snapshotSlotTimeLabel(slotIndex: number): string {
  if (!Number.isInteger(slotIndex) || slotIndex < 0 || slotIndex > 47) {
    return `--:-- ${DISPLAY_TIME_ZONE_LABEL}`;
  }
  const utcEndMinutes = (slotIndex + 1) * 30;
  const rawKstEndMinutes = utcEndMinutes + 9 * 60;
  const kstEndMinutes = rawKstEndMinutes % (24 * 60);
  const dayPrefix = rawKstEndMinutes >= 24 * 60 ? "D+1 " : "";
  const hour = String(Math.floor(kstEndMinutes / 60)).padStart(2, "0");
  const minute = String(kstEndMinutes % 60).padStart(2, "0");
  return `${dayPrefix}${hour}:${minute} ${DISPLAY_TIME_ZONE_LABEL}`;
}

function snapshotUtcDayStart(value: string): Date | null {
  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) {
    return null;
  }
  const date = new Date(parsed);
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
}

function snapshotUtcDayKey(value: string): string {
  const start = snapshotUtcDayStart(value);
  return start ? snapshotUtcDateKey(start) : "invalid-date";
}

function snapshotUtcDateKey(date: Date): string {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const day = String(date.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function buildSnapshotDetailPath(projectId: string, applicationId: string, snapshotId: string): string {
  return `/api/projects/${safePathComponent(projectId)}/applications/${safePathComponent(applicationId)}/dashboard/snapshots/${safePathComponent(snapshotId)}`;
}

export function buildStarterCredentialMetadataPath(projectId: string): string {
  return `/api/projects/${safePathComponent(projectId)}/starter-credential`;
}

export function buildStarterCredentialRotationPath(projectId: string): string {
  return `${buildStarterCredentialMetadataPath(projectId)}/rotations`;
}

export function buildStarterCredentialRevocationPath(projectId: string): string {
  return `${buildStarterCredentialMetadataPath(projectId)}/revocations`;
}

export function formatOptionalDateTime(value: string | null | undefined): string {
  if (!value) {
    return EMPTY_DISPLAY_TEXT;
  }
  return formatLocalDateTime(value);
}

/**
 * backend UTC timestamp를 한국 사용자가 읽는 KST 시각으로 변환한다.
 * 화면에서는 ISO 원문을 노출하지 않고 모든 주요 시각 표시가 이 helper를 거친다.
 */
export function formatLocalDateTime(value: string): string {
  return formatDateTime(value);
}

export function formatCount(value: number): string {
  return Number.isFinite(value) ? value.toLocaleString() : "0";
}

export function formatRatio(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return "sample 없음";
  }
  return `${(value * 100).toLocaleString(undefined, { maximumFractionDigits: 3 })}%`;
}

export function formatNullableRatio(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return UNKNOWN_TEXT;
  }
  return `${(value * 100).toLocaleString(undefined, { maximumFractionDigits: 3 })}%`;
}

export function formatWindow(window: DashboardWindow): string {
  return formatDateRange(window.startUtc, window.endUtc);
}

export function formatOptionalWindow(window: DashboardWindow | null | undefined): string {
  return window ? formatWindow(window) : EMPTY_DISPLAY_TEXT;
}

/**
 * UTC start/end instant를 같은 KST 기준의 날짜/시간 범위로 줄여 보여준다.
 * read model의 UTC 계약은 유지하고, 사용자가 보는 문자열만 로컬 시간대로 바꾼다.
 */
export function formatDateRange(startUtc: string, endUtc: string): string {
  const start = parseDate(startUtc);
  const end = parseDate(endUtc);
  if (!start || !end) {
    return `${formatDateTime(startUtc)} - ${formatDateTime(endUtc)}`;
  }
  const includeSeconds = hasVisibleSeconds(start) || hasVisibleSeconds(end);
  const startText = formatDateTime(startUtc, { includeSeconds });
  const endText = sameZonedDate(start, end)
    ? formatTimeOnly(end, includeSeconds)
    : sameZonedYear(start, end)
      ? formatMonthDayTime(end, includeSeconds)
      : formatFullDateTime(end, includeSeconds);
  return `${startText} - ${endText}`;
}

export const formatLocalDateTimeRange = formatDateRange;

export function histogramBarWidth(bucketCount: number, window: HistogramWindow): string {
  const maxCount = Math.max(1, ...window.buckets.map((bucket) => bucket.count));
  return `${Math.max(3, (bucketCount / maxCount) * 100)}%`;
}

/**
 * 누적 histogram bucket을 화면에서 읽기 쉬운 구간 bucket으로 바꾼다.
 * count도 누적값 차이로 계산해 막대와 숫자가 같은 구간 기준을 사용하게 한다.
 */
export function toDisplayLatencyBuckets(buckets: HistogramBucket[]): DisplayLatencyBucket[] {
  let previousUpperBound: number | null = null;
  let previousCumulativeCount = 0;
  return buckets.map((bucket) => {
    const count = Math.max(0, bucket.count - previousCumulativeCount);
    const displayBucket = {
      count,
      cumulativeCount: bucket.count,
      key: `${previousUpperBound ?? "start"}-${bucket.leMs}`,
      label: formatLatencyBucketRange(previousUpperBound, bucket.leMs),
      leMs: bucket.leMs,
    };
    previousUpperBound = bucket.leMs;
    previousCumulativeCount = bucket.count;
    return displayBucket;
  });
}

export function histogramRangeBarWidth(bucketCount: number, buckets: DisplayLatencyBucket[]): string {
  const maxCount = Math.max(1, ...buckets.map((bucket) => bucket.count));
  return `${Math.max(3, (bucketCount / maxCount) * 100)}%`;
}

export function metricStateClassName(code: LifecycleStateCode | (string & {})): string {
  switch (code) {
    case "active":
      return "border-emerald-500 bg-emerald-50 text-emerald-900";
    case "degraded":
      return "border-rose-500 bg-rose-50 text-rose-900";
    case "down":
      return "border-neutral-900 bg-neutral-900 text-white";
    case "idle":
      return "border-sky-500 bg-sky-50 text-sky-900";
    case "stale":
      return "border-amber-500 bg-amber-50 text-amber-900";
    case "waiting_first_data":
      return "border-violet-500 bg-violet-50 text-violet-900";
    case "unknown":
      return "border-neutral-400 bg-neutral-50 text-neutral-800";
    default:
      return "border-neutral-400 bg-white text-neutral-800";
  }
}

export function statusBadgeClassName(status: string): string {
  switch (status) {
    case "snapshot":
      return "border-amber-300 bg-amber-50 text-amber-800";
    case "critical":
      return "border-red-300 bg-red-50 text-red-700";
    case "warning":
      return "border-amber-300 bg-amber-50 text-amber-700";
    case "info":
      return "border-sky-300 bg-sky-50 text-sky-700";
    case "active":
    case "available":
    case "current":
    case "live":
    case "received":
    case "recent":
    case "within_threshold":
      return "border-emerald-500 bg-emerald-50 text-emerald-900";
    case "down_candidate":
    case "failed":
    case "revoked":
    case "stale":
    case "unavailable":
      return "border-rose-500 bg-rose-50 text-rose-900";
    case "insufficient":
    case "insufficient_baseline":
    case "insufficient_evidence":
    case "malformed_evidence":
    case "metric_missing":
    case "missing":
    case "not_observed":
    case "not_observed_in_window":
    case "sample_limited":
    case "stale_candidate":
    case "threshold_hit":
    case "threshold_exceeded":
    case "waiting_first_data":
      return "border-amber-500 bg-amber-50 text-amber-900";
    default:
      return "border-neutral-400 bg-white text-neutral-800";
  }
}

export function severityBadgeClassName(severity: string | null | undefined): string {
  switch ((severity ?? "").toLowerCase()) {
    case "critical":
      return "border-red-300 bg-red-50 text-red-700";
    case "attention":
    case "warning":
      return "border-amber-300 bg-amber-50 text-amber-700";
    case "info":
      return "border-sky-300 bg-sky-50 text-sky-700";
    default:
      return "border-neutral-400 bg-white text-neutral-800";
  }
}

export function severityDisplayText(severity: string | null | undefined): string {
  switch ((severity ?? "").toLowerCase()) {
    case "critical":
      return "긴급";
    case "warning":
      return "주의";
    case "info":
      return "참고";
    case "":
      return EMPTY_DISPLAY_TEXT;
    default:
      return humanizeCode(severity ?? "");
  }
}

export function humanizeSourceCode(value: string | null | undefined): string {
  const normalized = (value ?? "").trim();
  switch (normalized) {
    case "accepted_metric_buckets":
      return "최근 30분 accepted metric bucket";
    case "accepted_bucket":
      return "accepted bucket 분포";
    case "accepted_metric_buckets.endpoints_json":
      return "accepted bucket endpoint evidence";
    case "application_dashboard_live":
      return "live Application Dashboard";
    case "live_recent_30_minutes":
      return "live recent 30 minutes";
    case "selected_application_snapshot":
      return "selected Application Snapshot";
    case "starter_heartbeat":
      return "앱 연결 신호";
    case "dashboard_snapshots":
      return "저장된 상태 기록";
    case "dashboard_snapshots.read_model_json":
      return "저장된 dashboard read model";
    case "dashboard_snapshots.read_model_json.endpointPriority":
      return "저장된 dashboard endpoint priority";
    case "dashboard_snapshots.read_model_json.instanceSummary.items":
      return "저장된 dashboard instance summary";
    case "starter_canonical_percentile":
      return "Starter canonical p95/p99";
    case "endpointPriority":
      return "server endpoint priority";
    case "attentionEvidence":
      return "server attention evidence";
    case "stateReasons":
      return "server state reasons";
    case "stored read model":
    case "stored_read_model":
    case "stored_snapshot_detail":
      return "저장된 화면 기록";
    case "":
      return UNKNOWN_TEXT;
    default:
      if (normalized.startsWith("dashboard_snapshots.")) {
        return "저장된 상태 기록";
      }
      return humanizeCode(normalized);
  }
}

export function humanizeStatusCode(value: string | null | undefined): string {
  const normalized = (value ?? "").trim();
  switch (normalized) {
    case "active":
      return "정상";
    case "application":
      return "앱 전체";
    case "available":
      return "측정됨";
    case "capturedAt_asc":
    case "currentWindowEndUtc_asc":
      return "오래된 기록 먼저";
    case "capturedAt_desc":
    case "occurredAt_desc":
      return "최신 기록 먼저";
    case "current":
      return "최신";
    case "current_window":
      return "현재 구간";
    case "does_not_change_metric_state":
      return "metric state에 영향 없음";
    case "degraded":
      return "주의 필요";
    case "down":
      return "중단";
    case "down_candidate":
      return "수집 끊김 후보";
    case "failed":
      return "실패";
    case "info":
      return "참고";
    case "insufficient":
      return "표본 부족";
    case "insufficient_evidence":
      return "evidence 부족";
    case "insufficient_baseline":
      return "비교 기준 부족";
    case "recent_30_minutes":
      return "최근 30분";
    case "instance_bucket":
      return "instance bucket";
    case "application_latency":
      return "application latency";
    case "application_error":
      return "application error";
    case "resource_pressure":
      return "resource pressure";
    case "endpoint":
      return "endpoint";
    case "data_quality":
      return "data quality";
    case "baseline_comparison_not_used_for_mvp":
      return "baseline 비교는 MVP 판단에 사용하지 않음";
    case "sourceWindow.baseline_null_public_read_model":
      return "sourceWindow.baseline=null public read model";
    case "sample_limited":
      return "표본 제한";
    case "source_scoped_points":
      return "source-scoped point 표시";
    case "no_average_no_max_no_merge_no_histogram_recalculation":
      return "평균·최댓값·병합·histogram 재계산 없음";
    case "cumulative_bucket_distribution":
      return "누적 bucket 분포";
    case "display_bucket_only_no_percentile_recalculation":
      return "분포 표시 전용";
    case "threshold_hit":
    case "threshold_exceeded":
      return "기준 이상";
    case "within_threshold":
      return "기준 이하";
    case "normal":
      return "정상 범위";
    case "control_plane_only":
      return "control-plane 참고";
    case "missing":
      return "아직 없음";
    case "metric_missing":
      return "metric evidence 없음";
    case "malformed_evidence":
      return "해석할 수 없는 evidence";
    case "none":
      return "영향 없음";
    case "not_observed":
      return "selected instance에서 관찰되지 않음";
    case "not_observed_in_window":
      return "window 안에서 관찰되지 않음";
    case "received":
      return "수신됨";
    case "recent":
      return "최근 수신";
    case "resolved":
      return "상세 근거 연결됨";
    case "revoked":
      return "사용 중지";
    case "stale":
      return "데이터 지연";
    case "stale_candidate":
      return "데이터 지연 후보";
    case "starter_connected":
      return "앱과 포털이 연결됨";
    case "unavailable":
      return "사용 불가";
    case "unknown":
      return "확인 필요";
    case "waiting_first_data":
      return "첫 데이터 대기";
    case "warning":
      return "주의";
    case "server_order":
      return "server 제공 순서";
    case "":
      return EMPTY_DISPLAY_TEXT;
    default:
      return humanizeCode(normalized);
  }
}

export function humanizeCaptureReason(value: string | null | undefined): string {
  const normalized = (value ?? "").trim();
  switch (normalized) {
    case "hourly_scheduled":
      return "30분 정기 저장";
    case "query_fallback":
      return "정기 확인 기록";
    case "state_change":
      return "상태 변화";
    case "":
      return "저장 이유 없음";
    default:
      return humanizeStatusCode(normalized);
  }
}

export function humanizeOrderCode(value: string | null | undefined): string {
  const normalized = (value ?? "").trim();
  switch (normalized) {
    case "capturedAt_asc":
      return "오래된 기록 먼저";
    case "currentWindowEndUtc_asc":
      return "30분 slot 오래된 순";
    case "capturedAt_desc":
    case "occurredAt_desc":
      return "최신 기록 먼저";
    case "":
      return EMPTY_DISPLAY_TEXT;
    default:
      return humanizeStatusCode(normalized);
  }
}

export function humanizeAnchorStatus(value: string | null | undefined): string {
  const normalized = (value ?? "").trim();
  switch (normalized) {
    case "missing":
      return "연결된 상세 근거 없음";
    case "resolved":
      return "상세 근거 연결됨";
    case "":
      return UNKNOWN_TEXT;
    default:
      return humanizeStatusCode(normalized);
  }
}

function formatDateTime(value: string, options: { includeSeconds?: boolean } = {}): string {
  const date = parseDate(value);
  if (!date) {
    return value;
  }
  return formatFullDateTime(date, Boolean(options.includeSeconds));
}

function formatFullDateTime(date: Date, includeSeconds: boolean): string {
  return `${new Intl.DateTimeFormat("ko-KR", {
    day: "numeric",
    hour: "2-digit",
    hourCycle: "h23",
    minute: "2-digit",
    month: "long",
    second: includeSeconds ? "2-digit" : undefined,
    timeZone: DISPLAY_TIME_ZONE,
    year: "numeric",
  }).format(date)} ${DISPLAY_TIME_ZONE_LABEL}`;
}

function formatMonthDayTime(date: Date, includeSeconds: boolean): string {
  return `${new Intl.DateTimeFormat("ko-KR", {
    day: "numeric",
    hour: "2-digit",
    hourCycle: "h23",
    minute: "2-digit",
    month: "long",
    second: includeSeconds ? "2-digit" : undefined,
    timeZone: DISPLAY_TIME_ZONE,
  }).format(date)} ${DISPLAY_TIME_ZONE_LABEL}`;
}

function formatTimeOnly(date: Date, includeSeconds: boolean): string {
  return `${new Intl.DateTimeFormat("ko-KR", {
    hour: "2-digit",
    hourCycle: "h23",
    minute: "2-digit",
    second: includeSeconds ? "2-digit" : undefined,
    timeZone: DISPLAY_TIME_ZONE,
  }).format(date)} ${DISPLAY_TIME_ZONE_LABEL}`;
}

function parseDate(value: string): Date | null {
  const timestamp = Date.parse(value);
  return Number.isFinite(timestamp) ? new Date(timestamp) : null;
}

function hasVisibleSeconds(date: Date): boolean {
  return date.getUTCSeconds() !== 0 || date.getUTCMilliseconds() !== 0;
}

function sameZonedDate(a: Date, b: Date): boolean {
  return zonedDateKey(a) === zonedDateKey(b);
}

function sameZonedYear(a: Date, b: Date): boolean {
  return zonedYear(a) === zonedYear(b);
}

function zonedDateKey(date: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    day: "2-digit",
    month: "2-digit",
    timeZone: DISPLAY_TIME_ZONE,
    year: "numeric",
  }).format(date);
}

function zonedYear(date: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: DISPLAY_TIME_ZONE,
    year: "numeric",
  }).format(date);
}

function formatLatencyBucketRange(previousUpperBound: number | null, upperBound: number): string {
  if (!Number.isFinite(upperBound)) {
    return previousUpperBound === null ? "응답시간 기준 없음" : `${formatMilliseconds(previousUpperBound)} 이상`;
  }
  if (previousUpperBound === null) {
    return `응답시간 <= ${formatMilliseconds(upperBound)}`;
  }
  return `${formatMilliseconds(previousUpperBound)} <= 응답시간 <= ${formatMilliseconds(upperBound)}`;
}

function formatMilliseconds(value: number): string {
  return `${value}ms`;
}

function humanizeCode(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    return UNKNOWN_TEXT;
  }
  return trimmed.replace(/[_.-]+/g, " ");
}

function lifecycleBadgeClassName(code: string): string {
  return statusBadgeClassName(code);
}

function concernDisplay(concern: ConcernSummary | null): string {
  return concern?.label ?? "최근 확인할 항목 없음";
}

function concernMeta(concern: ConcernSummary | null): string {
  return concern ? `근거: ${humanizeStatusCode(concern.code)} · ${humanizeSourceCode(concern.source)}` : "표시할 추가 근거 없음";
}

function lifecycleBadgeDisplay(badge: LifecycleBadge): string {
  return badge.label;
}

function lifecycleBadgeMeta(badge: LifecycleBadge): string {
  return `상태 근거: ${humanizeStatusCode(badge.code)} · ${humanizeSourceCode(badge.source)}`;
}

function internalApiPath(link: string, errorCode: string): string {
  try {
    const baseOrigin = browserOrigin();
    const url = new URL(link, baseOrigin);
    if (url.origin !== baseOrigin || url.search || url.hash) {
      throw new ApiRequestError(errorCode);
    }
    return url.pathname;
  } catch (error) {
    if (error instanceof ApiRequestError) {
      throw error;
    }
    throw new ApiRequestError(errorCode);
  }
}

function browserOrigin(): string {
  return typeof window === "undefined" ? "http://localhost" : window.location.origin;
}

function safePathComponent(value: string): string {
  const trimmed = value.trim();
  if (!trimmed || /[/?#]/.test(trimmed)) {
    throw new ApiRequestError("api_path_component_invalid");
  }
  return encodeURIComponent(trimmed);
}
