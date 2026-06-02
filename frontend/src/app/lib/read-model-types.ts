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
    baseline: DashboardWindow;
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
