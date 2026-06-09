import { ApiRequestError } from "./api.js";
import type {
  ApplicationDashboardReadModel,
  DashboardSnapshotDetailReadModel,
  DashboardSnapshotMarkerReadModel,
  HistoryPreset,
  InstanceEvidenceReadModel,
  InstanceSnapshotTrendReadModel,
  OperationalEventHistoryReadModel,
  TrendPreset,
} from "./read-model-types.js";

type ApplicationContext = {
  applicationId: string;
  projectId: string;
};

type InstanceContext = ApplicationContext & {
  instanceId: string;
};

type SnapshotHistoryContext = {
  applicationId: string;
  eventLimit: number;
  markerLimit: number;
  preset: HistoryPreset;
};

type SnapshotDetailContext = {
  snapshotId: string;
};

type InstanceTrendContext = InstanceContext & {
  limit: number;
  preset: TrendPreset;
};

const DASHBOARD_CONTRACT_ERROR = "dashboard_contract_mismatch";
const SNAPSHOT_HISTORY_CONTRACT_ERROR = "snapshot_history_context_mismatch";
const SNAPSHOT_DETAIL_CONTRACT_ERROR = "snapshot_detail_contract_mismatch";
const INSTANCE_EVIDENCE_CONTRACT_ERROR = "instance_evidence_contract_mismatch";
const INSTANCE_TREND_CONTRACT_ERROR = "instance_snapshot_trend_contract_mismatch";
const SOURCE_SCOPED_PERCENTILE_SOURCE = "starter_canonical_percentile";
const SOURCE_SCOPED_PERCENTILE_POLICY = "no_average_no_max_no_merge_no_histogram_recalculation";
const HISTOGRAM_SOURCE = "accepted_bucket";
const HISTOGRAM_DISPLAY_POLICY = "cumulative_bucket_distribution";
const HISTOGRAM_AGGREGATE_POLICY = "display_bucket_only_no_percentile_recalculation";
const SNAPSHOT_SOURCE = "dashboard_snapshots";
const SNAPSHOT_DETAIL_ENDPOINT_SOURCE = "dashboard_snapshots.read_model_json.endpointPriority";
const SNAPSHOT_INSTANCE_SUMMARY_SOURCE = "dashboard_snapshots.read_model_json.instanceSummary.items";
const INSTANCE_TREND_SOURCE = "dashboard_snapshots.read_model_json.instanceSummary.items";
const STORED_READ_MODEL_SELECTION = "stored_read_model";
const MARKER_TIMELINE_INDEX = "timeline_index";
const FORBIDDEN_INSTANCE_DECISION_FIELDS = [
  "state",
  "lifecycleState",
  "instanceState",
  "currentState",
  "healthScore",
  "recoveryProof",
  "rootCause",
] as const;

/**
 * Application Dashboard read model의 서버 계산 값과 배열 순서를 그대로 통과시키는 runtime guard다.
 * 이 함수는 order/rank/state를 새로 만들지 않고, 필수 표시 field가 깨졌을 때 화면이 safe error로 수렴하게 한다.
 */
export function guardApplicationDashboardReadModel(
  model: ApplicationDashboardReadModel,
  context?: ApplicationContext,
): ApplicationDashboardReadModel {
  const root = asRecord(model, DASHBOARD_CONTRACT_ERROR);
  const application = asRecord(root.application, DASHBOARD_CONTRACT_ERROR);
  const sourceWindow = asRecord(application.sourceWindow, DASHBOARD_CONTRACT_ERROR);
  const state = asRecord(root.state, DASHBOARD_CONTRACT_ERROR);
  const sourceScopedPercentiles = asRecord(root.sourceScopedPercentiles, DASHBOARD_CONTRACT_ERROR);
  const histogramDistribution = asRecord(root.histogramDistribution, DASHBOARD_CONTRACT_ERROR);

  if (
    context &&
    (application.projectId !== context.projectId || application.applicationId !== context.applicationId)
  ) {
    throw new ApiRequestError(DASHBOARD_CONTRACT_ERROR);
  }

  assertNonEmptyString(state.code, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(state.label, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(state.scope, DASHBOARD_CONTRACT_ERROR);
  assertDashboardWindow(sourceWindow.current, DASHBOARD_CONTRACT_ERROR);
  assertOptionalDashboardWindow(sourceWindow.baseline, DASHBOARD_CONTRACT_ERROR);

  if (
    sourceScopedPercentiles.source !== SOURCE_SCOPED_PERCENTILE_SOURCE ||
    sourceScopedPercentiles.aggregatePolicy !== SOURCE_SCOPED_PERCENTILE_POLICY ||
    histogramDistribution.source !== HISTOGRAM_SOURCE ||
    histogramDistribution.displayPolicy !== HISTOGRAM_DISPLAY_POLICY ||
    histogramDistribution.aggregatePolicy !== HISTOGRAM_AGGREGATE_POLICY
  ) {
    throw new ApiRequestError(DASHBOARD_CONTRACT_ERROR);
  }

  assertArray(sourceScopedPercentiles.items, DASHBOARD_CONTRACT_ERROR);
  assertHistogramWindow(histogramDistribution.current, DASHBOARD_CONTRACT_ERROR);
  assertHistogramWindow(histogramDistribution.baseline, DASHBOARD_CONTRACT_ERROR);

  for (const item of assertArray(root.endpointPriority, DASHBOARD_CONTRACT_ERROR)) {
    const endpoint = asRecord(item, DASHBOARD_CONTRACT_ERROR);
    assertFiniteNumber(endpoint.rank, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(endpoint.endpointKey, DASHBOARD_CONTRACT_ERROR);
    assertArray(endpoint.ruleIds, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(endpoint.recommendedAction, DASHBOARD_CONTRACT_ERROR);
    const freshness = asRecord(endpoint.freshness, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(freshness.status, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(freshness.sourceWindow, DASHBOARD_CONTRACT_ERROR);
    const evidence = asRecord(endpoint.evidence, DASHBOARD_CONTRACT_ERROR);
    assertFiniteNumber(evidence.requestCount, DASHBOARD_CONTRACT_ERROR);
    assertFiniteNumber(evidence.errorCount, DASHBOARD_CONTRACT_ERROR);
    assertFiniteNumber(evidence.errorRate, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(evidence.bucketDistributionSource, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(evidence.errorEvidenceStatus, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(evidence.latencyEvidenceStatus, DASHBOARD_CONTRACT_ERROR);
  }

  assertArray(root.triageCards, DASHBOARD_CONTRACT_ERROR);
  assertArray(root.instances, DASHBOARD_CONTRACT_ERROR);
  return model;
}

/**
 * Snapshot history/marker 응답의 source, horizon, server order metadata를 검증한다.
 * 배열은 복사하거나 정렬하지 않고 같은 reference를 반환해 UI가 서버 순서를 그대로 렌더링하게 한다.
 */
export function guardSnapshotHistoryReadModels(
  events: OperationalEventHistoryReadModel,
  markers: DashboardSnapshotMarkerReadModel,
  context: SnapshotHistoryContext,
): { events: OperationalEventHistoryReadModel; markers: DashboardSnapshotMarkerReadModel } {
  const eventRoot = asRecord(events, SNAPSHOT_HISTORY_CONTRACT_ERROR);
  const markerRoot = asRecord(markers, SNAPSHOT_HISTORY_CONTRACT_ERROR);

  if (
    eventRoot.source !== SNAPSHOT_SOURCE ||
    markerRoot.source !== SNAPSHOT_SOURCE ||
    eventRoot.applicationId !== context.applicationId ||
    markerRoot.applicationId !== context.applicationId ||
    !historyHorizonMatches(eventRoot.horizon, {
      limit: context.eventLimit,
      maxLimit: 100,
      order: "occurredAt_desc",
      preset: context.preset,
    }) ||
    !historyHorizonMatches(markerRoot.horizon, {
      limit: context.markerLimit,
      maxLimit: 336,
      order: "capturedAt_asc",
      preset: context.preset,
    })
  ) {
    throw new ApiRequestError(SNAPSHOT_HISTORY_CONTRACT_ERROR);
  }

  for (const event of assertArray(eventRoot.events, SNAPSHOT_HISTORY_CONTRACT_ERROR)) {
    const item = asRecord(event, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    assertNonEmptyString(item.eventId, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    assertNonEmptyString(item.snapshotId, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    assertNonEmptyString(item.stateCode, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    asRecord(item.evidence, SNAPSHOT_HISTORY_CONTRACT_ERROR);
  }

  for (const marker of assertArray(markerRoot.markers, SNAPSHOT_HISTORY_CONTRACT_ERROR)) {
    const item = asRecord(marker, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    assertNonEmptyString(item.markerId, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    assertNonEmptyString(item.snapshotId, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    if (item.readMeaning !== MARKER_TIMELINE_INDEX) {
      throw new ApiRequestError(SNAPSHOT_HISTORY_CONTRACT_ERROR);
    }
    assertNonEmptyString(item.storedApplicationStateCode, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    asRecord(item.links, SNAPSHOT_HISTORY_CONTRACT_ERROR);
  }

  return { events, markers };
}

/**
 * Snapshot detail이 stored read model 복원 surface인지 검증한다.
 * marker helper 값이 state source로 승격되거나 live/current source가 결합되면 fail-closed로 막는다.
 */
export function guardSnapshotDetailReadModel(
  model: DashboardSnapshotDetailReadModel,
  context: SnapshotDetailContext,
): DashboardSnapshotDetailReadModel {
  const root = asRecord(model, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const readSemantics = asRecord(root.readSemantics, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const snapshot = asRecord(root.snapshot, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const marker = asRecord(root.marker, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const liveSourcesJoined = assertArray(readSemantics.liveSourcesJoined, SNAPSHOT_DETAIL_CONTRACT_ERROR);

  if (
    root.source !== SNAPSHOT_SOURCE ||
    readSemantics.mode !== "stored_snapshot_detail" ||
    readSemantics.currentStateRecalculated !== false ||
    liveSourcesJoined.length !== 0 ||
    readSemantics.rawReadModelJsonExposed !== false ||
    readSemantics.markerIsStateSource !== false ||
    snapshot.snapshotId !== context.snapshotId ||
    marker.snapshotId !== snapshot.snapshotId ||
    marker.storedApplicationStateCode !== snapshot.storedApplicationStateCode ||
    marker.readMeaning !== MARKER_TIMELINE_INDEX
  ) {
    throw new ApiRequestError(SNAPSHOT_DETAIL_CONTRACT_ERROR);
  }

  asRecord(root.readModel, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const snapshotEndpointEvidence = asRecord(root.snapshotEndpointEvidence, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const instanceSummary = asRecord(root.instanceSummary, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const previousState = asRecord(root.previousState, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const lastHealthyAt = asRecord(root.lastHealthyAt, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const links = asRecord(root.links, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  if (
    snapshotEndpointEvidence.source !== SNAPSHOT_DETAIL_ENDPOINT_SOURCE ||
    snapshotEndpointEvidence.selectionPolicy !== STORED_READ_MODEL_SELECTION ||
    instanceSummary.source !== SNAPSHOT_INSTANCE_SUMMARY_SOURCE ||
    instanceSummary.selectionPolicy !== STORED_READ_MODEL_SELECTION ||
    previousState.source !== SNAPSHOT_SOURCE ||
    lastHealthyAt.source !== SNAPSHOT_SOURCE
  ) {
    throw new ApiRequestError(SNAPSHOT_DETAIL_CONTRACT_ERROR);
  }
  assertNonEmptyString(links.self, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  assertArray(snapshotEndpointEvidence.items, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  assertArray(instanceSummary.items, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  return model;
}

/**
 * Instance Evidence가 Application Dashboard보다 강한 state/health/root cause 판단을 만들지 않는지 검증한다.
 * endpoint evidence는 서버 projection 순서를 보존하고 localDisplayOrder를 새 rank로 바꾸지 않는다.
 */
export function guardInstanceEvidenceReadModel(
  model: InstanceEvidenceReadModel,
  context: InstanceContext,
): InstanceEvidenceReadModel {
  const root = asRecord(model, INSTANCE_EVIDENCE_CONTRACT_ERROR);
  assertNoForbiddenFieldsDeep(
    root,
    [...FORBIDDEN_INSTANCE_DECISION_FIELDS, "endpointPriority"],
    INSTANCE_EVIDENCE_CONTRACT_ERROR,
  );

  const application = asRecord(root.application, INSTANCE_EVIDENCE_CONTRACT_ERROR);
  const instance = asRecord(root.instance, INSTANCE_EVIDENCE_CONTRACT_ERROR);
  if (
    application.projectId !== context.projectId ||
    application.applicationId !== context.applicationId ||
    instance.instanceId !== context.instanceId
  ) {
    throw new ApiRequestError(INSTANCE_EVIDENCE_CONTRACT_ERROR);
  }

  const starterPercentiles = asRecord(root.starterPercentiles, INSTANCE_EVIDENCE_CONTRACT_ERROR);
  const histogramDistribution = asRecord(root.histogramDistribution, INSTANCE_EVIDENCE_CONTRACT_ERROR);
  const endpointEvidence = asRecord(root.endpointEvidence, INSTANCE_EVIDENCE_CONTRACT_ERROR);
  if (endpointEvidence.displayOrderingPolicy !== "server_order") {
    throw new ApiRequestError(INSTANCE_EVIDENCE_CONTRACT_ERROR);
  }
  assertArray(starterPercentiles.points, INSTANCE_EVIDENCE_CONTRACT_ERROR);
  assertArray(histogramDistribution.buckets, INSTANCE_EVIDENCE_CONTRACT_ERROR);

  for (const item of assertArray(endpointEvidence.items, INSTANCE_EVIDENCE_CONTRACT_ERROR)) {
    const endpoint = asRecord(item, INSTANCE_EVIDENCE_CONTRACT_ERROR);
    assertFiniteNumber(endpoint.localDisplayOrder, INSTANCE_EVIDENCE_CONTRACT_ERROR);
    assertNonEmptyString(endpoint.endpointKey, INSTANCE_EVIDENCE_CONTRACT_ERROR);
  }

  return model;
}

/**
 * Instance Snapshot Trend가 stored instanceSummary projection으로만 소비되는지 검증한다.
 * point field를 조합해 current state, instance state, recovery proof, health score를 만들 수 있는 field는 거부한다.
 */
export function guardInstanceSnapshotTrendReadModel(
  model: InstanceSnapshotTrendReadModel,
  context: InstanceTrendContext,
): InstanceSnapshotTrendReadModel {
  const root = asRecord(model, INSTANCE_TREND_CONTRACT_ERROR);
  assertNoForbiddenFieldsDeep(root, [...FORBIDDEN_INSTANCE_DECISION_FIELDS, "endpointPriority"], INSTANCE_TREND_CONTRACT_ERROR);

  const application = asRecord(root.application, INSTANCE_TREND_CONTRACT_ERROR);
  const instance = asRecord(root.instance, INSTANCE_TREND_CONTRACT_ERROR);
  const horizon = asRecord(root.horizon, INSTANCE_TREND_CONTRACT_ERROR);

  if (
    application.projectId !== context.projectId ||
    application.applicationId !== context.applicationId ||
    instance.instanceId !== context.instanceId ||
    root.source !== INSTANCE_TREND_SOURCE ||
    horizon.requestedSince !== context.preset ||
    horizon.defaultSince !== "7d" ||
    horizon.maxSince !== "14d" ||
    horizon.limit !== context.limit ||
    horizon.maxLimit !== 672 ||
    horizon.order !== "capturedAt_asc" ||
    !horizonWindowIsValid(horizon)
  ) {
    throw new ApiRequestError(INSTANCE_TREND_CONTRACT_ERROR);
  }

  for (const point of assertArray(root.points, INSTANCE_TREND_CONTRACT_ERROR)) {
    const item = asRecord(point, INSTANCE_TREND_CONTRACT_ERROR);
    assertNonEmptyString(item.snapshotId, INSTANCE_TREND_CONTRACT_ERROR);
    assertNonEmptyString(item.storedApplicationStateCode, INSTANCE_TREND_CONTRACT_ERROR);
    asRecord(item.metricData, INSTANCE_TREND_CONTRACT_ERROR);
    asRecord(item.starterConnection, INSTANCE_TREND_CONTRACT_ERROR);
    asRecord(item.resourceHints, INSTANCE_TREND_CONTRACT_ERROR);
    asRecord(item.applicationTriageContribution, INSTANCE_TREND_CONTRACT_ERROR);
    assertArray(item.endpointEvidenceRefs, INSTANCE_TREND_CONTRACT_ERROR);
  }

  return model;
}

function historyHorizonMatches(
  value: unknown,
  expected: { limit: number; maxLimit: number; order: string; preset: HistoryPreset },
): boolean {
  const horizon = asRecord(value, SNAPSHOT_HISTORY_CONTRACT_ERROR);
  return (
    horizon.requestedSince === expected.preset &&
    horizon.defaultSince === "24h" &&
    horizon.maxSince === "14d" &&
    horizon.limit === expected.limit &&
    horizon.maxLimit === expected.maxLimit &&
    horizon.order === expected.order &&
    horizonWindowIsValid(horizon)
  );
}

function horizonWindowIsValid(horizon: Record<string, unknown>): boolean {
  const since = typeof horizon.since === "string" ? Date.parse(horizon.since) : Number.NaN;
  const until = typeof horizon.until === "string" ? Date.parse(horizon.until) : Number.NaN;
  return Number.isFinite(since) && Number.isFinite(until) && until > since;
}

function assertHistogramWindow(value: unknown, errorCode: string) {
  const window = asRecord(value, errorCode);
  assertArray(window.buckets, errorCode);
}

function assertDashboardWindow(value: unknown, errorCode: string) {
  const window = asRecord(value, errorCode);
  assertNonEmptyString(window.startUtc, errorCode);
  assertNonEmptyString(window.endUtc, errorCode);
}

function assertOptionalDashboardWindow(value: unknown, errorCode: string) {
  if (value === null || value === undefined) {
    return;
  }
  assertDashboardWindow(value, errorCode);
}

function assertArray(value: unknown, errorCode: string): unknown[] {
  if (!Array.isArray(value)) {
    throw new ApiRequestError(errorCode);
  }
  return value;
}

function assertFiniteNumber(value: unknown, errorCode: string) {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new ApiRequestError(errorCode);
  }
}

function assertNonEmptyString(value: unknown, errorCode: string) {
  if (typeof value !== "string" || value.trim() === "") {
    throw new ApiRequestError(errorCode);
  }
}

function assertNoForbiddenFields(
  value: Record<string, unknown>,
  fields: readonly string[],
  errorCode: string,
) {
  for (const field of fields) {
    if (Object.prototype.hasOwnProperty.call(value, field)) {
      throw new ApiRequestError(errorCode);
    }
  }
}

function assertNoForbiddenFieldsDeep(value: unknown, fields: readonly string[], errorCode: string) {
  if (value === null || typeof value !== "object") {
    return;
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      assertNoForbiddenFieldsDeep(item, fields, errorCode);
    }
    return;
  }

  const record = value as Record<string, unknown>;
  assertNoForbiddenFields(record, fields, errorCode);
  for (const item of Object.values(record)) {
    assertNoForbiddenFieldsDeep(item, fields, errorCode);
  }
}

function asRecord(value: unknown, errorCode: string): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new ApiRequestError(errorCode);
  }
  return value as Record<string, unknown>;
}
