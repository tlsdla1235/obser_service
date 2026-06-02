import { ApiRequestError } from "./api";
import type {
  ApplicationDashboardReadModel,
  ConcernSummary,
  DashboardWindow,
  HistoryPreset,
  HistogramWindow,
  LifecycleBadge,
  LifecycleStateCode,
  ProjectApplicationNavigationApplicationItem,
  ProjectApplicationNavigationReadModel,
  ProjectNavigationProjectItem,
  ProjectNavigationReadModel,
  TrendPreset,
} from "./read-model-types";

const PROJECT_APPLICATIONS_PATH = /^\/api\/projects\/([^/]+)\/applications$/;
const APPLICATION_DASHBOARD_PATH = /^\/api\/projects\/([^/]+)\/applications\/([^/]+)\/dashboard$/;
const INSTANCE_EVIDENCE_PATH = /^\/api\/projects\/([^/]+)\/applications\/([^/]+)\/instances\/([^/]+)\/evidence$/;
const INSTANCE_SNAPSHOT_TREND_PATH = /^\/api\/projects\/([^/]+)\/applications\/([^/]+)\/instances\/([^/]+)\/snapshot-trend$/;
const SNAPSHOT_DETAIL_PATH = /^\/api\/projects\/([^/]+)\/applications\/([^/]+)\/dashboard\/snapshots\/([^/]+)$/;
const UNKNOWN_TEXT = "source 없음";

export const TREND_PRESET_QUERY = {
  "7d": { limit: 168, since: "7d" },
  "14d": { limit: 336, since: "14d" },
} as const satisfies Record<TrendPreset, { limit: number; since: TrendPreset }>;

export const HISTORY_PRESET_QUERY = {
  "24h": {
    eventLimit: 50,
    markerLimit: 50,
    since: "24h",
  },
  "7d": {
    eventLimit: 100,
    markerLimit: 168,
    since: "7d",
  },
  "14d": {
    eventLimit: 100,
    markerLimit: 336,
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
  baselineWindowDisplay: string;
  currentWindowDisplay: string;
  generatedAtDisplay: string;
  lastAcceptedBucketDisplay: string;
  lastHealthyDisplay: string;
  metricStateClassName: string;
  sourceScopedReasonDisplay: string;
  starterLastHeartbeatDisplay: string;
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
  const { application, generatedAt, sourceScopedPercentiles, starterConnection } = model;
  const { code } = model.state;
  return {
    ...model,
    baselineWindowDisplay: formatWindow(application.sourceWindow.baseline),
    currentWindowDisplay: formatWindow(application.sourceWindow.current),
    generatedAtDisplay: formatOptionalDateTime(generatedAt),
    lastAcceptedBucketDisplay: formatOptionalDateTime(application.lastAcceptedBucketAt),
    lastHealthyDisplay: formatOptionalDateTime(application.lastHealthyAt),
    metricStateClassName: metricStateClassName(code),
    sourceScopedReasonDisplay: sourceScopedPercentiles.reason ?? UNKNOWN_TEXT,
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
    return UNKNOWN_TEXT;
  }
  return value;
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
  return `${window.startUtc} -> ${window.endUtc}`;
}

export function histogramBarWidth(bucketCount: number, window: HistogramWindow): string {
  const maxCount = Math.max(1, ...window.buckets.map((bucket) => bucket.count));
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
    case "available":
    case "current":
    case "received":
    case "recent":
      return "border-emerald-500 bg-emerald-50 text-emerald-900";
    case "down_candidate":
    case "failed":
    case "stale":
    case "unavailable":
      return "border-rose-500 bg-rose-50 text-rose-900";
    case "insufficient":
    case "insufficient_baseline":
    case "missing":
    case "stale_candidate":
      return "border-amber-500 bg-amber-50 text-amber-900";
    default:
      return "border-neutral-400 bg-white text-neutral-800";
  }
}

function lifecycleBadgeClassName(code: string): string {
  return statusBadgeClassName(code);
}

function concernDisplay(concern: ConcernSummary | null): string {
  return concern?.label ?? "concern source 없음";
}

function concernMeta(concern: ConcernSummary | null): string {
  return concern ? `${concern.code} · ${concern.source}` : "server concern 없음";
}

function lifecycleBadgeDisplay(badge: LifecycleBadge): string {
  return badge.label;
}

function lifecycleBadgeMeta(badge: LifecycleBadge): string {
  return `${badge.code} · ${badge.source}`;
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
