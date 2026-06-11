import { ApiRequestError } from "./api.js";
import type {
  ApplicationDashboardReadModel,
  DashboardSnapshotDetailReadModel,
  DashboardSnapshotMarkerReadModel,
  HistoryPreset,
  InstanceDashboardReadModel,
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

type InstanceDashboardContext = InstanceContext & {
  mode: "live" | "snapshot";
  snapshotId?: string;
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
const INSTANCE_DASHBOARD_CONTRACT_ERROR = "instance_dashboard_contract_mismatch";
const INSTANCE_EVIDENCE_CONTRACT_ERROR = "instance_evidence_contract_mismatch";
const INSTANCE_TREND_CONTRACT_ERROR = "instance_snapshot_trend_contract_mismatch";
const DASHBOARD_SCHEMA_VERSION = "dashboard_read_model.v1";
const DASHBOARD_LIVE_MODE = "live";
const DASHBOARD_WINDOW_TYPE = "recent_30_minutes";
const DASHBOARD_READ_SOURCE = "accepted_metric_buckets";
const INSTANCE_DASHBOARD_SCHEMA_VERSION = "instance_dashboard_read_model.v1";
const INSTANCE_DASHBOARD_WINDOW_SOURCE_LIVE = "live_recent_30_minutes";
const INSTANCE_DASHBOARD_WINDOW_SOURCE_SNAPSHOT = "selected_application_snapshot";
const SOURCE_SCOPED_PERCENTILE_SOURCE = "starter_canonical_percentile";
const SOURCE_SCOPED_PERCENTILE_SCOPE = "instance_bucket";
const SOURCE_SCOPED_PERCENTILE_DISPLAY_POLICY = "source_scoped_points";
const SOURCE_SCOPED_PERCENTILE_POLICY = "no_average_no_max_no_merge_no_histogram_recalculation";
const HISTOGRAM_SOURCE = "accepted_bucket";
const HISTOGRAM_DISPLAY_POLICY = "cumulative_bucket_distribution";
const HISTOGRAM_AGGREGATE_POLICY = "display_bucket_only_no_percentile_recalculation";
const SNAPSHOT_SOURCE = "dashboard_snapshots";
const SNAPSHOT_READ_MODEL_SOURCE = "dashboard_snapshots.read_model_json";
const SNAPSHOT_DETAIL_ENDPOINT_SOURCE = "dashboard_snapshots.read_model_json.endpointPriority";
const SNAPSHOT_INSTANCE_SUMMARY_SOURCE = "dashboard_snapshots.read_model_json.instanceSummary.items";
const INSTANCE_TREND_SOURCE = "dashboard_snapshots.read_model_json.instanceSummary.items";
const STORED_READ_MODEL_SELECTION = "stored_read_model";
const MARKER_TIMELINE_INDEX = "timeline_index";
const MARKER_STORED_POINT = "stored_read_model_point";
const FORBIDDEN_INSTANCE_DECISION_FIELDS = [
  "state",
  "stateCode",
  "health",
  "lifecycleState",
  "instanceState",
  "currentState",
  "healthScore",
  "cause",
  "rootCauseCandidate",
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
  const window = asRecord(root.window, DASHBOARD_CONTRACT_ERROR);
  const thresholds = asRecord(root.thresholds, DASHBOARD_CONTRACT_ERROR);
  const operatorSummary = asRecord(root.operatorSummary, DASHBOARD_CONTRACT_ERROR);
  const dataQuality = asRecord(root.dataQuality, DASHBOARD_CONTRACT_ERROR);
  const signals = asRecord(root.signals, DASHBOARD_CONTRACT_ERROR);
  const readSemantics = asRecord(root.readSemantics, DASHBOARD_CONTRACT_ERROR);
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
  assertDashboardCanonicalHeader(root, window, readSemantics);
  assertDashboardThresholds(thresholds);
  assertNonEmptyString(operatorSummary.headline, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(operatorSummary.firstLookText, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(dataQuality.state, DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(dataQuality.requestCount, DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(dataQuality.minimumRequestCount, DASHBOARD_CONTRACT_ERROR);
  for (const limitation of assertArray(dataQuality.limitations, DASHBOARD_CONTRACT_ERROR)) {
    assertNonEmptyString(limitation, DASHBOARD_CONTRACT_ERROR);
  }
  assertOptionalString(dataQuality.lastObservedAt, DASHBOARD_CONTRACT_ERROR);
  assertOptionalString(operatorSummary.primaryProblemCode, DASHBOARD_CONTRACT_ERROR);
  assertDashboardSignals(signals);
  assertDashboardWindow(sourceWindow.current, DASHBOARD_CONTRACT_ERROR);
  assertOptionalDashboardWindow(sourceWindow.baseline, DASHBOARD_CONTRACT_ERROR);
  if (sourceWindow.baseline !== null) {
    throw new ApiRequestError(DASHBOARD_CONTRACT_ERROR);
  }

  if (
    sourceScopedPercentiles.source !== SOURCE_SCOPED_PERCENTILE_SOURCE ||
    sourceScopedPercentiles.scope !== SOURCE_SCOPED_PERCENTILE_SCOPE ||
    sourceScopedPercentiles.displayPolicy !== SOURCE_SCOPED_PERCENTILE_DISPLAY_POLICY ||
    sourceScopedPercentiles.aggregatePolicy !== SOURCE_SCOPED_PERCENTILE_POLICY ||
    histogramDistribution.source !== HISTOGRAM_SOURCE ||
    histogramDistribution.displayPolicy !== HISTOGRAM_DISPLAY_POLICY ||
    histogramDistribution.aggregatePolicy !== HISTOGRAM_AGGREGATE_POLICY
  ) {
    throw new ApiRequestError(DASHBOARD_CONTRACT_ERROR);
  }

  for (const item of assertArray(sourceScopedPercentiles.items, DASHBOARD_CONTRACT_ERROR)) {
    const percentile = asRecord(item, DASHBOARD_CONTRACT_ERROR);
    if (percentile.source !== SOURCE_SCOPED_PERCENTILE_SOURCE) {
      throw new ApiRequestError(DASHBOARD_CONTRACT_ERROR);
    }
    assertFiniteNumber(percentile.requestCount, DASHBOARD_CONTRACT_ERROR);
    assertFiniteNumber(percentile.p95Ms, DASHBOARD_CONTRACT_ERROR);
    assertFiniteNumber(percentile.p99Ms, DASHBOARD_CONTRACT_ERROR);
  }
  assertHistogramWindow(histogramDistribution.current, DASHBOARD_CONTRACT_ERROR);
  assertHistogramWindow(histogramDistribution.baseline, DASHBOARD_CONTRACT_ERROR);

  for (const item of assertArray(root.stateReasons, DASHBOARD_CONTRACT_ERROR)) {
    assertDashboardStateReason(item);
  }
  for (const item of assertArray(root.attentionEvidence, DASHBOARD_CONTRACT_ERROR)) {
    const evidence = assertDashboardStateReason(item);
    if (evidence.affectsLifecycleState !== false) {
      throw new ApiRequestError(DASHBOARD_CONTRACT_ERROR);
    }
  }
  for (const item of assertArray(root.firstLookCandidates, DASHBOARD_CONTRACT_ERROR)) {
    const candidate = asRecord(item, DASHBOARD_CONTRACT_ERROR);
    assertFiniteNumber(candidate.rank, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(candidate.type, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(candidate.source, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(candidate.reasonCode, DASHBOARD_CONTRACT_ERROR);
    assertOptionalString(candidate.target, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(candidate.operatorText, DASHBOARD_CONTRACT_ERROR);
  }

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
    if (evidence.bucketDistributionSource !== HISTOGRAM_SOURCE) {
      throw new ApiRequestError(DASHBOARD_CONTRACT_ERROR);
    }
    assertNonEmptyString(evidence.errorEvidenceStatus, DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(evidence.latencyEvidenceStatus, DASHBOARD_CONTRACT_ERROR);
  }

  assertArray(root.triageCards, DASHBOARD_CONTRACT_ERROR);
  assertArray(root.instances, DASHBOARD_CONTRACT_ERROR);
  return model;
}

function assertDashboardCanonicalHeader(
  root: Record<string, unknown>,
  window: Record<string, unknown>,
  readSemantics: Record<string, unknown>,
) {
  if (
    root.schemaVersion !== DASHBOARD_SCHEMA_VERSION ||
    root.mode !== DASHBOARD_LIVE_MODE ||
    window.type !== DASHBOARD_WINDOW_TYPE ||
    readSemantics.source !== DASHBOARD_READ_SOURCE ||
    readSemantics.snapshotDetailRecalculates !== false ||
    readSemantics.markerIsStateSource !== false ||
    readSemantics.baselineComparisonUsedForMvpDecision !== false ||
    readSemantics.helperColumnsAreStateSource !== false ||
    readSemantics.histogramBucketsUsedForPercentiles !== false ||
    readSemantics.bucketDistributionSource !== HISTOGRAM_SOURCE
  ) {
    throw new ApiRequestError(DASHBOARD_CONTRACT_ERROR);
  }
  assertDashboardWindow(window, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(readSemantics.bucketDistributionMeaning, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(readSemantics.bucketEndBoundary, DASHBOARD_CONTRACT_ERROR);
}

function assertDashboardThresholds(thresholds: Record<string, unknown>) {
  assertFiniteNumber(thresholds.minimumRequestCount, DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(thresholds.errorRate, DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(thresholds.slowShareOver500ms, DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(thresholds.datasourcePoolUsage, DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(thresholds.cpuUsage, DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(thresholds.heapUsage, DASHBOARD_CONTRACT_ERROR);
}

function assertDashboardSignals(signals: Record<string, unknown>) {
  const red = asRecord(signals.red, DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(red.requestCount, DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(red.errorCount, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(red.errorSemantic, DASHBOARD_CONTRACT_ERROR);
  assertNullableFiniteNumber(red.errorRate, DASHBOARD_CONTRACT_ERROR);
  assertNullableFiniteNumber(red.slowCountOver500ms, DASHBOARD_CONTRACT_ERROR);
  assertNullableFiniteNumber(red.slowShareOver500ms, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(red.latencyEvidenceStatus, DASHBOARD_CONTRACT_ERROR);

  const use = asRecord(signals.use, DASHBOARD_CONTRACT_ERROR);
  assertDashboardResourceSignal(use.datasourcePoolUsage);
  assertDashboardResourceSignal(use.cpuUsage);
  assertDashboardResourceSignal(use.heapUsage);
}

function assertDashboardResourceSignal(value: unknown) {
  const signal = asRecord(value, DASHBOARD_CONTRACT_ERROR);
  assertNullableFiniteNumber(signal.max, DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(signal.threshold, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(signal.status, DASHBOARD_CONTRACT_ERROR);
  assertOptionalString(signal.observedAt, DASHBOARD_CONTRACT_ERROR);
}

function assertDashboardStateReason(value: unknown): Record<string, unknown> {
  const reason = asRecord(value, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(reason.type, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(reason.severity, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(reason.scope, DASHBOARD_CONTRACT_ERROR);
  assertOptionalString(reason.target, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(reason.reasonCode, DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(reason.operatorText, DASHBOARD_CONTRACT_ERROR);
  return reason;
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
      maxLimit: 672,
      order: "currentWindowEndUtc_asc",
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

  const markerHorizon = asRecord(markerRoot.horizon, SNAPSHOT_HISTORY_CONTRACT_ERROR);
  let previousMarkerWindowEndMs: number | null = null;
  for (const marker of assertArray(markerRoot.markers, SNAPSHOT_HISTORY_CONTRACT_ERROR)) {
    const item = asRecord(marker, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    assertNonEmptyString(item.markerId, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    assertNonEmptyString(item.snapshotId, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    if (!markerReadMeaningIsSafe(item.readMeaning)) {
      throw new ApiRequestError(SNAPSHOT_HISTORY_CONTRACT_ERROR);
    }
    assertNonEmptyString(item.storedApplicationStateCode, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    const currentWindowEndMs = assertSnapshotMarkerSlot(item.currentWindowEndUtc, markerHorizon, SNAPSHOT_HISTORY_CONTRACT_ERROR);
    if (previousMarkerWindowEndMs !== null && currentWindowEndMs < previousMarkerWindowEndMs) {
      throw new ApiRequestError(SNAPSHOT_HISTORY_CONTRACT_ERROR);
    }
    previousMarkerWindowEndMs = currentWindowEndMs;
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
  const snapshotCurrentWindow = asRecord(snapshot.currentWindow, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const liveSourcesJoined = assertArray(readSemantics.liveSourcesJoined, SNAPSHOT_DETAIL_CONTRACT_ERROR);

  if (
    root.source !== SNAPSHOT_SOURCE ||
    readSemantics.mode !== "stored_snapshot_detail" ||
    readSemantics.source !== SNAPSHOT_READ_MODEL_SOURCE ||
    readSemantics.snapshotDetailRecalculates !== false ||
    readSemantics.currentStateRecalculated !== false ||
    liveSourcesJoined.length !== 0 ||
    readSemantics.rawReadModelJsonExposed !== false ||
    readSemantics.markerIsStateSource !== false ||
    readSemantics.baselineComparisonUsedForMvpDecision !== false ||
    snapshot.snapshotId !== context.snapshotId ||
    marker.snapshotId !== snapshot.snapshotId ||
    marker.currentWindowEndUtc !== snapshotCurrentWindow.endUtc ||
    marker.storedApplicationStateCode !== snapshot.storedApplicationStateCode ||
    !markerReadMeaningIsSafe(marker.readMeaning)
  ) {
    throw new ApiRequestError(SNAPSHOT_DETAIL_CONTRACT_ERROR);
  }

  const storedReadModel = asRecord(root.readModel, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  if (
    storedReadModel.schemaVersion !== DASHBOARD_SCHEMA_VERSION ||
    storedReadModel.mode !== "snapshot"
  ) {
    throw new ApiRequestError(SNAPSHOT_DETAIL_CONTRACT_ERROR);
  }
  const storedWindow = asRecord(storedReadModel.window, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  if (
    storedWindow.type !== DASHBOARD_WINDOW_TYPE ||
    storedWindow.endUtc !== snapshotCurrentWindow.endUtc ||
    !halfHourBoundaryIsValid(snapshotCurrentWindow.endUtc)
  ) {
    throw new ApiRequestError(SNAPSHOT_DETAIL_CONTRACT_ERROR);
  }
  asRecord(storedReadModel.operatorSummary, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  asRecord(storedReadModel.dataQuality, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  asRecord(storedReadModel.signals, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const storedState = asRecord(storedReadModel.state, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  assertNonEmptyString(storedState.code, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  assertNonEmptyString(storedState.label, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  assertNonEmptyString(storedState.rationale, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  assertNonEmptyString(storedState.recommendedAction, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  assertArray(storedReadModel.stateReasons, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  assertArray(storedReadModel.attentionEvidence, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  assertArray(storedReadModel.firstLookCandidates, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  const storedReadSemantics = asRecord(storedReadModel.readSemantics, SNAPSHOT_DETAIL_CONTRACT_ERROR);
  if (
    storedReadSemantics.source !== SNAPSHOT_READ_MODEL_SOURCE ||
    storedReadSemantics.snapshotDetailRecalculates !== false ||
    storedReadSemantics.markerIsStateSource !== false ||
    storedReadSemantics.baselineComparisonUsedForMvpDecision !== false ||
    storedReadSemantics.histogramBucketsUsedForPercentiles !== false ||
    storedReadSemantics.bucketDistributionSource !== HISTOGRAM_SOURCE
  ) {
    throw new ApiRequestError(SNAPSHOT_DETAIL_CONTRACT_ERROR);
  }
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
 * Instance Dashboard live/snapshot read model을 mode별 source semantics로 검증한다.
 * Application-owned state reference는 허용하지만 selected instance lifecycle/health/root cause field는 거부한다.
 */
export function guardInstanceDashboardReadModel(
  model: InstanceDashboardReadModel,
  context: InstanceDashboardContext,
): InstanceDashboardReadModel {
  const root = asRecord(model, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNoForbiddenFields(root, ["state", "stateCode"], INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNoInstanceDashboardStateFieldsDeep(root, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNoForbiddenFieldsDeep(
    root,
    [
      "health",
      "lifecycleState",
      "instanceState",
      "currentState",
      "healthScore",
      "cause",
      "rootCauseCandidate",
      "recoveryProof",
      "rootCause",
      "endpointPriority",
      "instanceSummary",
    ],
    INSTANCE_DASHBOARD_CONTRACT_ERROR,
  );

  const application = asRecord(root.application, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const instance = asRecord(root.instance, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const window = asRecord(root.window, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const applicationStateRef = asRecord(root.applicationStateRef, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const observationStatus = asRecord(root.observationStatus, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const applicationContribution = asRecord(root.applicationContribution, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const dataQuality = asRecord(root.dataQuality, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const starterConnection = asRecord(root.starterConnection, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const signals = asRecord(root.signals, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const endpointEvidence = asRecord(root.endpointEvidence, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const resourceEvidence = asRecord(root.resourceEvidence, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const readSemantics = asRecord(root.readSemantics, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  const links = asRecord(root.links, INSTANCE_DASHBOARD_CONTRACT_ERROR);

  if (
    root.schemaVersion !== INSTANCE_DASHBOARD_SCHEMA_VERSION ||
    root.mode !== context.mode ||
    application.projectId !== context.projectId ||
    application.applicationId !== context.applicationId ||
    instance.instanceId !== context.instanceId ||
    window.name !== DASHBOARD_WINDOW_TYPE ||
    window.bucketDurationSeconds !== 30 ||
    readSemantics.source !== DASHBOARD_READ_SOURCE ||
    readSemantics.windowSource !== window.windowSource ||
    readSemantics.acceptedAtCutoffApplied !== false ||
    readSemantics.applicationSnapshotRecalculated !== false ||
    readSemantics.markerIsStateSource !== false ||
    applicationStateRef.lifecycleOwner !== "application" ||
    dataQuality.source !== DASHBOARD_READ_SOURCE ||
    starterConnection.statusSource !== "starter_heartbeat" ||
    endpointEvidence.source !== "accepted_metric_buckets.endpoints_json" ||
    endpointEvidence.scope !== "instance_recent_30_minutes" ||
    endpointEvidence.displayOrderingPolicy !== "server_order" ||
    resourceEvidence.source !== DASHBOARD_READ_SOURCE
  ) {
    throw new ApiRequestError(INSTANCE_DASHBOARD_CONTRACT_ERROR);
  }

  assertDashboardWindow(window, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(root.generatedAt, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(application.name, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(application.environment, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(instance.instanceName, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(instance.firstSeenAt, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(instance.lastSeenAt, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(observationStatus.code, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertOptionalString(observationStatus.reason, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertOptionalString(observationStatus.lastObservedBucketEndUtc, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(applicationContribution.level, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertOptionalString(applicationContribution.reason, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertArray(applicationContribution.evidenceRefs, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(dataQuality.state, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertArray(dataQuality.limitations, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(starterConnection.lastHeartbeatStatus, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(starterConnection.freshnessLabel, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(starterConnection.connectionMeaning, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  if (
    starterConnection.stateImpact !== "does_not_change_metric_state" &&
    starterConnection.stateImpact !== "control_plane_only"
  ) {
    throw new ApiRequestError(INSTANCE_DASHBOARD_CONTRACT_ERROR);
  }
  assertInstanceDashboardSignals(signals);
  assertInstanceDashboardEndpointEvidence(endpointEvidence);
  assertInstanceDashboardResourceEvidence(resourceEvidence);
  assertArray(root.patterns, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertArray(root.excludedCapabilities, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(links.self, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(links.applicationDashboard, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(links.instanceEvidence, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(links.snapshotTrend, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertOptionalString(links.applicationSnapshotDetail, INSTANCE_DASHBOARD_CONTRACT_ERROR);

  if (context.mode === "live") {
    if (
      window.windowSource !== INSTANCE_DASHBOARD_WINDOW_SOURCE_LIVE ||
      root.snapshot !== null ||
      applicationStateRef.source !== "application_dashboard_live" ||
      applicationStateRef.snapshotId !== null ||
      readSemantics.snapshotRowSource !== null ||
      readSemantics.includesLateAcceptedMetrics !== false ||
      readSemantics.mayDifferFromStoredApplicationSnapshot !== false ||
      readSemantics.instanceEvidenceReconstructedFromMetrics !== false
    ) {
      throw new ApiRequestError(INSTANCE_DASHBOARD_CONTRACT_ERROR);
    }
  } else {
    const snapshot = asRecord(root.snapshot, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    if (
      !context.snapshotId ||
      window.windowSource !== INSTANCE_DASHBOARD_WINDOW_SOURCE_SNAPSHOT ||
      snapshot.snapshotId !== context.snapshotId ||
      snapshot.snapshotRowSource !== SNAPSHOT_SOURCE ||
      snapshot.currentWindowStartUtc !== window.startUtc ||
      snapshot.currentWindowEndUtc !== window.endUtc ||
      applicationStateRef.source !== "selected_application_snapshot" ||
      applicationStateRef.snapshotId !== context.snapshotId ||
      readSemantics.snapshotRowSource !== SNAPSHOT_SOURCE ||
      readSemantics.includesLateAcceptedMetrics !== true ||
      readSemantics.mayDifferFromStoredApplicationSnapshot !== true ||
      readSemantics.instanceEvidenceReconstructedFromMetrics !== true
    ) {
      throw new ApiRequestError(INSTANCE_DASHBOARD_CONTRACT_ERROR);
    }
    assertNonEmptyString(snapshot.generatedAt, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertOptionalString(snapshot.captureReason, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertOptionalString(snapshot.storedApplicationStateCode, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  }

  return model;
}

function assertNoInstanceDashboardStateFieldsDeep(
  value: unknown,
  errorCode: string,
  path: readonly string[] = [],
) {
  if (value === null || typeof value !== "object") {
    return;
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      assertNoInstanceDashboardStateFieldsDeep(item, errorCode, path);
    }
    return;
  }

  const record = value as Record<string, unknown>;
  for (const [key, child] of Object.entries(record)) {
    const childPath = [...path, key];
    const pathKey = childPath.join(".");
    if (key === "state" && pathKey !== "dataQuality.state") {
      throw new ApiRequestError(errorCode);
    }
    if (key === "stateCode") {
      throw new ApiRequestError(errorCode);
    }
    assertNoInstanceDashboardStateFieldsDeep(child, errorCode, childPath);
  }
}

function assertInstanceDashboardSignals(signals: Record<string, unknown>) {
  const red = asRecord(signals.red, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(red.requestCount, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertFiniteNumber(red.errorCount, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNullableFiniteNumber(red.errorRate, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNullableFiniteNumber(red.slowCountOver500ms, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNullableFiniteNumber(red.slowShareOver500ms, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  if (typeof red.requestSymptomPresent !== "boolean") {
    throw new ApiRequestError(INSTANCE_DASHBOARD_CONTRACT_ERROR);
  }
}

function assertInstanceDashboardEndpointEvidence(endpointEvidence: Record<string, unknown>) {
  assertNonEmptyString(endpointEvidence.selectionPolicy, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertNonEmptyString(endpointEvidence.status, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  assertOptionalString(endpointEvidence.reason, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  for (const item of assertArray(endpointEvidence.items, INSTANCE_DASHBOARD_CONTRACT_ERROR)) {
    const endpoint = asRecord(item, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(endpoint.method, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(endpoint.route, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(endpoint.endpointKey, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(endpoint.presenceOnSelectedInstance, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    if (
      endpoint.presenceOnSelectedInstance !== "observed" &&
      endpoint.presenceOnSelectedInstance !== "not_observed" &&
      endpoint.presenceOnSelectedInstance !== "insufficient"
    ) {
      throw new ApiRequestError(INSTANCE_DASHBOARD_CONTRACT_ERROR);
    }
    assertFiniteNumber(endpoint.requestCount, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertFiniteNumber(endpoint.errorCount, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertNullableFiniteNumber(endpoint.errorRate, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertFiniteNumber(endpoint.localDisplayOrder, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(endpoint.status, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertOptionalString(endpoint.reason, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertOptionalString(endpoint.relatedApplicationEndpointEvidenceRef, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  }
}

function assertInstanceDashboardResourceEvidence(resourceEvidence: Record<string, unknown>) {
  assertNonEmptyString(resourceEvidence.status, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  for (const item of assertArray(resourceEvidence.items, INSTANCE_DASHBOARD_CONTRACT_ERROR)) {
    const resource = asRecord(item, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(resource.resourceKey, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    if (resource.scope !== "instance") {
      throw new ApiRequestError(INSTANCE_DASHBOARD_CONTRACT_ERROR);
    }
    assertNullableFiniteNumber(resource.usage, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertNullableFiniteNumber(resource.threshold, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(resource.status, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertOptionalString(resource.observedAt, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    if (typeof resource.requestSymptomPresent !== "boolean") {
      throw new ApiRequestError(INSTANCE_DASHBOARD_CONTRACT_ERROR);
    }
    assertNonEmptyString(resource.patternContribution, INSTANCE_DASHBOARD_CONTRACT_ERROR);
    assertNonEmptyString(resource.operatorText, INSTANCE_DASHBOARD_CONTRACT_ERROR);
  }
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
    horizon.order !== "currentWindowEndUtc_asc" ||
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

function markerReadMeaningIsSafe(value: unknown): boolean {
  return value === MARKER_TIMELINE_INDEX || value === MARKER_STORED_POINT;
}

function horizonWindowIsValid(horizon: Record<string, unknown>): boolean {
  const since = typeof horizon.since === "string" ? Date.parse(horizon.since) : Number.NaN;
  const until = typeof horizon.until === "string" ? Date.parse(horizon.until) : Number.NaN;
  return Number.isFinite(since) && Number.isFinite(until) && until > since;
}

function assertSnapshotMarkerSlot(value: unknown, horizon: Record<string, unknown>, errorCode: string): number {
  if (typeof value !== "string" || !value.trim()) {
    throw new ApiRequestError(errorCode);
  }
  const timestamp = Date.parse(value);
  const since = typeof horizon.since === "string" ? Date.parse(horizon.since) : Number.NaN;
  const until = typeof horizon.until === "string" ? Date.parse(horizon.until) : Number.NaN;
  if (
    !Number.isFinite(timestamp) ||
    !Number.isFinite(since) ||
    !Number.isFinite(until) ||
    timestamp < since ||
    timestamp > until ||
    !halfHourBoundaryIsValid(value)
  ) {
    throw new ApiRequestError(errorCode);
  }
  return timestamp;
}

function halfHourBoundaryIsValid(value: unknown): boolean {
  if (typeof value !== "string") {
    return false;
  }
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) {
    return false;
  }
  const date = new Date(timestamp);
  return (date.getUTCMinutes() === 0 || date.getUTCMinutes() === 30) && date.getUTCSeconds() === 0 && date.getUTCMilliseconds() === 0;
}

function assertHistogramWindow(value: unknown, errorCode: string) {
  const window = asRecord(value, errorCode);
  assertNonEmptyString(window.status, errorCode);
  assertOptionalString(window.reason, errorCode);
  assertFiniteNumber(window.totalCount, errorCode);
  for (const bucket of assertArray(window.buckets, errorCode)) {
    const item = asRecord(bucket, errorCode);
    assertFiniteNumber(item.leMs, errorCode);
    assertFiniteNumber(item.count, errorCode);
  }
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

function assertNullableFiniteNumber(value: unknown, errorCode: string) {
  if (value === null) {
    return;
  }
  assertFiniteNumber(value, errorCode);
}

function assertNonEmptyString(value: unknown, errorCode: string) {
  if (typeof value !== "string" || value.trim() === "") {
    throw new ApiRequestError(errorCode);
  }
}

function assertOptionalString(value: unknown, errorCode: string) {
  if (value === null) {
    return;
  }
  assertNonEmptyString(value, errorCode);
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
