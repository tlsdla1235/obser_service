/**
 * Java record 기반 Project/Application/Dashboard read model DTO 타입이다.
 * UI adapter는 이 타입을 source of truth로 삼고 server-computed 의미를 재계산하지 않는다.
 */

export type IsoDateTimeString = string;
export type UuidString = string;

export type LifecycleStateCode =
  | "waiting_first_data"
  | "unknown"
  | "idle"
  | "active"
  | "stale"
  | "down"
  | "degraded";

export type ConcernSummary = {
  code: string;
  label: string;
  source: string;
};

export type LifecycleBadge = {
  source: string;
  code: string;
  label: string;
};

export type ProjectNavigationReadModel = {
  generatedAt: IsoDateTimeString;
  projects: ProjectNavigationProjectItem[];
};

export type ProjectNavigationProjectItem = {
  projectId: UuidString;
  name: string;
  applicationCount: number;
  setupConnectionIssueCount: number;
  recentConcern: ConcernSummary | null;
  links: {
    applications: string;
  };
};

export type ProjectApplicationNavigationReadModel = {
  generatedAt: IsoDateTimeString;
  project: {
    projectId: UuidString;
    name: string;
  };
  applications: ProjectApplicationNavigationApplicationItem[];
};

export type ProjectApplicationNavigationApplicationItem = {
  applicationId: UuidString;
  name: string;
  environment: string;
  metricData: MetricDataSummary;
  starterConnection: StarterConnectionSummary;
  lifecycleBadge: LifecycleBadge;
  topConcern: ConcernSummary | null;
  links: {
    dashboard: string;
  };
};

export type MetricDataSummary = {
  statusSource: string;
  lastAcceptedBucketAt: IsoDateTimeString | null;
  freshnessLabel: string;
};

export type StarterConnectionSummary = {
  statusSource: string;
  lastHeartbeatAt: IsoDateTimeString | null;
  heartbeatStatus: string;
  freshnessLabel: string;
  connectionMeaning: string;
  stateImpact: string;
};

export type ApplicationDashboardReadModel = {
  generatedAt: IsoDateTimeString;
  application: DashboardApplication;
  state: DashboardState;
  starterConnection: DashboardStarterConnection;
  zeroInsight: ZeroInsight | null;
  recovery: Recovery;
  metrics: Metrics;
  sourceScopedPercentiles: SourceScopedPercentiles;
  histogramDistribution: HistogramDistribution;
  triageCards: TriageCard[];
  endpointPriority: EndpointPriorityItem[];
  instances: InstanceEntry[];
  snapshot: unknown | null;
};

export type DashboardApplication = {
  projectId: UuidString;
  applicationId: UuidString;
  name: string;
  environment: string;
  lastAcceptedBucketAt: IsoDateTimeString | null;
  lastHealthyAt: IsoDateTimeString | null;
  sourceWindow: {
    current: DashboardWindow;
    baseline: DashboardWindow | null;
  };
  freshness: Freshness;
};

export type DashboardWindow = {
  startUtc: IsoDateTimeString;
  endUtc: IsoDateTimeString;
};

export type Freshness = {
  lastObservedAt: IsoDateTimeString | null;
  staleAt: IsoDateTimeString | null;
  downAt: IsoDateTimeString | null;
};

export type DashboardState = {
  code: LifecycleStateCode | (string & {});
  label: string;
  rationale: string;
  recommendedAction: string;
  scope: string;
};

export type DashboardStarterConnection = {
  statusSource: string;
  lastHeartbeatAt: IsoDateTimeString | null;
  lastHeartbeatStatus: string;
  connectionMeaning: string;
  stateImpact: string;
};

export type ZeroInsight = {
  reasonCode:
    | "no_action_needed"
    | "insufficient_sample"
    | "waiting_first_data"
    | "metric_data_idle"
    | "telemetry_unreachable"
    | "observing_recovery"
    | (string & {});
  message: string;
  recommendedAction: string;
};

export type Recovery = {
  isRecovering: boolean;
  lastHealthyAt: IsoDateTimeString | null;
  retryAfterSeconds: number | null;
  recommendedAction: string | null;
};

export type Metrics = {
  requestCount: number;
  errorCount: number;
  errorRate: number | null;
};

export type SourceScopedPercentiles = {
  source: string;
  scope: string;
  displayPolicy: string;
  aggregatePolicy: string;
  status: string;
  reason: string | null;
  items: SourceScopedPercentileItem[];
};

export type SourceScopedPercentileItem = {
  source: string;
  application: string;
  environment: string;
  instance: string;
  bucketStartUtc: IsoDateTimeString;
  bucketEndUtc: IsoDateTimeString;
  requestCount: number;
  p95Ms: number;
  p99Ms: number;
};

export type HistogramDistribution = {
  source: string;
  scope: string;
  displayPolicy: string;
  aggregatePolicy: string;
  current: HistogramWindow;
  baseline: HistogramWindow;
};

export type HistogramWindow = {
  status: string;
  reason: string | null;
  totalCount: number;
  buckets: HistogramBucket[];
};

export type HistogramBucket = {
  leMs: number;
  count: number;
};

export type TriageCard = {
  ruleId: string;
  severity: "info" | "warning" | "critical" | (string & {});
  title: string;
  summary: string;
  recommendation: string;
  confidence: number;
  score: number;
  affectedEndpoint: string | null;
  evidence: TriageEvidence;
};

export type TriageEvidence = {
  requestCount: number | null;
  currentErrorCount: number | null;
  currentErrorRate: number | null;
  baselineRequestCount: number | null;
  baselineErrorCount: number | null;
  baselineErrorRate: number | null;
  errorRateDelta: number | null;
  currentSlowShare: number | null;
  baselineSlowShare: number | null;
  currentHistogram: HistogramEvidenceSummary | null;
  baselineHistogram: HistogramEvidenceSummary | null;
  runtimeRatio: RuntimeRatioEvidence | null;
  freshnessStatusReason: string | null;
  sourcePercentilePoint: SourcePercentilePointSummary | null;
};

export type HistogramEvidenceSummary = {
  status: string;
  totalCount: number;
  buckets: HistogramBucket[];
};

export type RuntimeRatioEvidence = {
  cpuUsageRatio: number | null;
  heapUsedRatio: number | null;
  datasourcePoolUsageRatio: number | null;
};

export type SourcePercentilePointSummary = {
  source: string;
  scope: string;
  instance: string;
  bucketEndUtc: IsoDateTimeString;
  requestCount: number;
  p95Ms: number | null;
  p99Ms: number | null;
};

export type EndpointPriorityItem = {
  rank: number;
  method: string;
  route: string;
  endpointKey: string;
  reason:
    | "error_spike"
    | "latency_spike"
    | "error_and_latency"
    | "comparative_regression"
    | (string & {});
  ruleIds: string[];
  confidence: number;
  score: number;
  freshness: EndpointPriorityFreshness;
  evidence: EndpointPriorityEvidence;
  recommendedAction: string;
};

export type EndpointPriorityFreshness = {
  status: string;
  lastObservedAt: IsoDateTimeString;
  sourceWindow: string;
  reason: string | null;
};

export type EndpointEvidenceStatus =
  | "available"
  | "missing"
  | "insufficient"
  | "insufficient_baseline"
  | "unavailable"
  | (string & {});

export type EndpointPriorityEvidence = {
  requestCount: number;
  errorCount: number;
  errorRate: number;
  baselineRequestCount: number | null;
  baselineErrorCount: number | null;
  baselineErrorRate: number | null;
  errorRateDelta: number | null;
  durationBuckets: HistogramBucket[] | null;
  baselineDurationBuckets: HistogramBucket[] | null;
  slowShare: number | null;
  baselineSlowShare: number | null;
  slowShareDelta: number | null;
  bucketDistributionSource: string;
  errorEvidenceStatus: EndpointEvidenceStatus;
  latencyEvidenceStatus: EndpointEvidenceStatus;
};

export type InstanceEntry = {
  instanceId: UuidString;
  instanceName: string;
  lastSeenAt: IsoDateTimeString | null;
  links: {
    evidence: string;
  };
};

export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };

export type InstanceEvidenceReadModel = {
  generatedAt: IsoDateTimeString;
  application: EvidenceApplication;
  instance: EvidenceInstance;
  metricData: EvidenceMetricData;
  starterConnection: EvidenceStarterConnection;
  starterPercentiles: EvidenceStarterPercentiles;
  histogramDistribution: EvidenceHistogramDistribution;
  resourceHints: EvidenceResourceHints;
  applicationTriageContribution: EvidenceApplicationTriageContribution;
  endpointEvidence: EvidenceEndpointEvidence;
  links: EvidenceLinks;
};

export type EvidenceApplication = {
  projectId: UuidString;
  applicationId: UuidString;
  name: string;
  environment: string;
  links: {
    dashboard: string;
  };
};

export type EvidenceInstance = {
  instanceId: UuidString;
  instanceName: string;
  firstSeenAt: IsoDateTimeString;
  lastSeenAt: IsoDateTimeString;
};

export type EvidenceMetricData = {
  statusSource: "accepted_bucket" | (string & {});
  window: {
    name: string;
    startUtc: IsoDateTimeString;
    endUtc: IsoDateTimeString;
    bucketDurationSeconds: number;
  };
  lastAcceptedBucketAt: IsoDateTimeString | null;
  freshnessLabel: string;
  sampleReadiness: string;
  requestCount: number;
  errorCount: number;
  errorRate: number | null;
  reason: string | null;
};

export type EvidenceStarterConnection = {
  statusSource: "starter_heartbeat" | (string & {});
  lastHeartbeatAt: IsoDateTimeString | null;
  lastHeartbeatStatus: string;
  freshnessLabel: string;
  connectionMeaning: string;
  stateImpact: "none" | (string & {});
};

export type EvidenceStarterPercentiles = {
  source: string;
  scope: string;
  window: string;
  bucketDurationSeconds: number;
  maxPointCount: number;
  displayPolicy: string;
  aggregatePolicy: string;
  status: string;
  reason: string | null;
  points: EvidencePercentilePoint[];
};

export type EvidencePercentilePoint = {
  bucketStartUtc: IsoDateTimeString;
  bucketEndUtc: IsoDateTimeString;
  requestCount: number;
  p95Ms: number;
  p99Ms: number;
};

export type EvidenceHistogramDistribution = {
  source: string;
  scope: string;
  status: string;
  reason: string | null;
  totalCount: number;
  buckets: HistogramBucket[];
};

export type EvidenceResourceHints = {
  source: string;
  status: string;
  reason: string | null;
  bucketEndUtc: IsoDateTimeString | null;
  cpuUsageRatio: number | null;
  heapUsedRatio: number | null;
  datasourcePoolUsageRatio: number | null;
};

export type EvidenceApplicationTriageContribution = {
  status: string;
  contributed: boolean;
  relatedRuleIds: string[];
  reason: string | null;
};

export type EvidenceEndpointEvidence = {
  source: string;
  scope: string;
  selectionPolicy: string;
  displayOrderingPolicy: string;
  status: string;
  reason: string | null;
  items: EvidenceEndpointEvidenceItem[];
};

export type EvidenceEndpointEvidenceItem = {
  method: string;
  route: string;
  endpointKey: string;
  presenceOnSelectedInstance: string;
  instanceRequestCount: number;
  instanceErrorCount: number;
  instanceErrorRate: number | null;
  applicationEndpointRequestCount: number | null;
  applicationEndpointErrorCount: number | null;
  applicationEndpointErrorRate: number | null;
  instanceRequestShare: number | null;
  instanceErrorShare: number | null;
  durationBuckets: HistogramBucket[];
  bucketDistributionSource: string;
  relatedApplicationPriorityRank: number | null;
  localDisplayOrder: number;
  relatedRuleIds: string[];
  status: string;
  reason: string | null;
};

export type EvidenceLinks = {
  self: string;
  dashboard: string;
  snapshotTrend: string | null;
};

export type InstanceSnapshotTrendReadModel = {
  generatedAt: IsoDateTimeString;
  application: EvidenceApplication;
  instance: TrendInstance;
  source: string;
  horizon: TrendHorizon;
  points: TrendPoint[];
};

export type TrendInstance = EvidenceInstance & {
  links: {
    evidence: string;
  };
};

export type TrendHorizon = {
  since: IsoDateTimeString;
  until: IsoDateTimeString;
  requestedSince: "7d" | "14d" | (string & {});
  defaultSince: string;
  maxSince: string;
  limit: number;
  maxLimit: number;
  order: string;
};

export type TrendPoint = {
  snapshotId: UuidString;
  capturedAt: IsoDateTimeString;
  currentWindowEndUtc: IsoDateTimeString;
  storedApplicationStateCode: string;
  captureReason: string | null;
  instanceName: string;
  observationStatus: string;
  metricData: TrendMetricData;
  starterConnection: TrendStarterConnection;
  starterPercentilePoint: TrendStarterPercentilePoint | null;
  resourceHints: TrendResourceHints;
  applicationTriageContribution: EvidenceApplicationTriageContribution;
  endpointEvidenceRefs: TrendEndpointEvidenceRef[];
};

export type TrendMetricData = {
  statusSource: "accepted_bucket" | (string & {});
  lastAcceptedBucketAt: IsoDateTimeString | null;
  freshnessLabel: string;
};

export type TrendStarterConnection = {
  statusSource: "starter_heartbeat" | (string & {});
  lastHeartbeatAt: IsoDateTimeString | null;
  lastHeartbeatStatus: string;
  connectionMeaning: string;
  stateImpact: "none" | (string & {});
};

export type TrendStarterPercentilePoint = {
  source: string;
  scope: string;
  bucketStartUtc: IsoDateTimeString;
  bucketEndUtc: IsoDateTimeString;
  requestCount: number;
  p95Ms: number;
  p99Ms: number;
};

export type TrendResourceHints = {
  source: string;
  status: string;
  bucketEndUtc: IsoDateTimeString | null;
  cpuUsageRatio: number | null;
  heapUsedRatio: number | null;
  datasourcePoolUsageRatio: number | null;
};

export type TrendEndpointEvidenceRef = {
  endpointKey: string;
  method: string | null;
  route: string | null;
  relatedApplicationPriorityRank: number | null;
  relatedRuleIds: string[];
  snapshotDetailAnchor: string | null;
};

export type HistoryPreset = "24h" | "7d" | "14d";
export type TrendPreset = "7d" | "14d";

export type OperationalEventHistoryReadModel = {
  generatedAt: IsoDateTimeString;
  applicationId: UuidString;
  source: "dashboard_snapshots" | (string & {});
  horizon: HistoryHorizon;
  events: OperationalEventItem[];
};

export type HistoryHorizon = {
  since: IsoDateTimeString;
  until: IsoDateTimeString;
  requestedSince: HistoryPreset | (string & {});
  defaultSince: string;
  maxSince: string;
  limit: number;
  maxLimit: number;
  order: string;
};

export type OperationalEventItem = {
  eventId: string;
  type: string;
  severity: "info" | "warning" | "critical" | (string & {});
  title: string;
  summary: string;
  occurredAt: IsoDateTimeString;
  resolvedAt: IsoDateTimeString | null;
  stateCode: string;
  confidence: number | null;
  snapshotId: UuidString;
  evidence: OperationalEventEvidence;
  links: {
    snapshot: string;
  };
};

export type OperationalEventEvidence = {
  ruleId: string | null;
  endpointKey: string | null;
  method: string | null;
  route: string | null;
  snapshotDetailAnchor: string | null;
  anchorStatus: "resolved" | "missing" | (string & {}) | null;
};

export type DashboardSnapshotMarkerReadModel = {
  generatedAt: IsoDateTimeString;
  applicationId: UuidString;
  source: "dashboard_snapshots" | (string & {});
  horizon: HistoryHorizon;
  emptyState: SnapshotEmptyState | null;
  markers: DashboardSnapshotMarkerItem[];
};

export type SnapshotEmptyState = {
  reasonCode: string;
  message: string;
  recommendedAction: string;
};

export type DashboardSnapshotMarkerItem = {
  markerId: string;
  snapshotId: UuidString;
  capturedAt: IsoDateTimeString;
  currentWindowEndUtc: IsoDateTimeString;
  type: string;
  severity: "info" | "warning" | "critical" | (string & {});
  readMeaning: string;
  captureReason: string | null;
  storedApplicationStateCode: string;
  previousState: SnapshotPreviousState;
  title: string;
  summary: string;
  recommendedAction: string | null;
  confidence: number | null;
  primaryRuleId: string | null;
  primaryEndpointKey: string | null;
  links: {
    snapshot: string;
  };
};

export type DashboardSnapshotDetailReadModel = {
  generatedAt: IsoDateTimeString;
  source: "dashboard_snapshots" | (string & {});
  readSemantics: SnapshotReadSemantics;
  snapshot: SnapshotMetadata;
  marker: DashboardSnapshotMarkerItem;
  previousState: SnapshotPreviousState;
  lastHealthyAt: SnapshotLastHealthyAt;
  recoveryMarker: SnapshotRecoveryMarker | null;
  readModel: SnapshotStoredReadModel;
  snapshotEndpointEvidence: SnapshotEndpointEvidence;
  instanceSummary: SnapshotInstanceSummary;
  links: SnapshotLinks;
};

export type SnapshotReadSemantics = {
  mode: string;
  currentStateRecalculated: boolean;
  liveSourcesJoined: string[];
  markerIsStateSource: boolean;
  rawReadModelJsonExposed: boolean;
};

export type SnapshotMetadata = {
  snapshotId: UuidString;
  capturedAt: IsoDateTimeString;
  generatedAt: IsoDateTimeString;
  currentWindow: DashboardWindow;
  baselineWindow: DashboardWindow;
  captureReason: string | null;
  storedApplicationStateCode: string;
  primaryRuleId: string | null;
  primaryEndpointKey: string | null;
  maxConfidence: number | null;
};

export type SnapshotPreviousState = {
  stateCode: string | null;
  source: string;
  snapshotId: UuidString | null;
  capturedAt: IsoDateTimeString | null;
};

export type SnapshotLastHealthyAt = {
  value: IsoDateTimeString | null;
  source: string;
  snapshotId: UuidString | null;
};

export type SnapshotRecoveryMarker = {
  markerId: string;
  type: string;
  severity: string;
  title: string;
  summary: string;
  recommendedAction: string;
  previousState: SnapshotPreviousState;
  lastHealthyAt: SnapshotLastHealthyAt;
};

export type SnapshotStoredReadModel = {
  application: JsonValue | null;
  state: JsonValue | null;
  starterConnection: JsonValue | null;
  zeroInsight: JsonValue | null;
  recovery: JsonValue | null;
  metrics: JsonValue | null;
  sourceScopedPercentiles: JsonValue | null;
  triageCards: JsonValue | null;
  endpointPriority: JsonValue | null;
};

export type SnapshotEndpointEvidence = {
  source: string;
  maxItems: number;
  selectionPolicy: string | null;
  unavailableReason: string | null;
  items: SnapshotEndpointEvidenceItem[];
};

export type SnapshotEndpointEvidenceItem = {
  anchorId: string;
  method: string | null;
  route: string | null;
  endpointKey: string;
  rank: number | null;
  reason: string | null;
  ruleIds: string[];
  confidence: number | null;
  score: number | null;
  requestCount: number | null;
  errorRate: number | null;
  durationBuckets: JsonValue | null;
  baselineDurationBuckets: JsonValue | null;
  bucketDistributionSource: string | null;
  freshness: JsonValue | null;
  recommendedAction: string | null;
};

export type SnapshotInstanceSummary = {
  schemaVersion: string;
  source: string;
  maxItems: number;
  selectionPolicy: string | null;
  unavailableReason: string | null;
  items: SnapshotInstanceSummaryItem[];
};

export type SnapshotInstanceSummaryItem = {
  instanceId: string;
  instanceName: string;
  observationStatus: string;
  metricData: JsonValue | null;
  starterConnection: JsonValue | null;
  starterPercentilePoint: JsonValue | null;
  resourceHints: JsonValue | null;
  applicationTriageContribution: JsonValue | null;
  endpointEvidenceRefs: SnapshotEndpointEvidenceRef[];
};

export type SnapshotEndpointEvidenceRef = TrendEndpointEvidenceRef & {
  anchorStatus: "resolved" | "missing" | (string & {});
};

export type SnapshotLinks = {
  self: string;
  markers: string;
};

export type ProjectRegistrationRequest = {
  name: string;
};

export type ProjectRegistrationResponse = {
  project: {
    projectId: UuidString;
    name: string;
    links: {
      applications: string;
    };
  };
  starterCredential: OneTimeStarterCredential;
};

export type StarterCredentialMetadataResponse = {
  projectId: UuidString;
  starterCredential: StarterCredentialMetadata;
};

export type StarterCredentialRotationResponse = {
  projectId: UuidString;
  starterCredential: OneTimeStarterCredential & StarterCredentialMetadata;
};

export type StarterCredentialMetadata = {
  keyPrefix: string;
  status: string;
  issuedAt: IsoDateTimeString;
  rotatedAt: IsoDateTimeString | null;
  revokedAt: IsoDateTimeString | null;
};

export type OneTimeStarterCredential = {
  displayValue: string;
  keyPrefix: string;
  visibleOnce: boolean;
  issuedAt: IsoDateTimeString;
};
