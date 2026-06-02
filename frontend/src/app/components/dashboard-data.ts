export type ProjectItem = {
  projectId: string;
  name: string;
  applicationCount: number;
  setupConnectionIssueCount: number;
  recentConcern: string | null;
};

export type ApplicationItem = {
  applicationId: string;
  name: string;
  environment: string;
  lifecycleBadge: string;
  metricData: {
    statusSource: "accepted_bucket";
    lastAcceptedBucketAt: string;
    freshnessLabel: string;
  };
  starterConnection: {
    statusSource: "starter_heartbeat";
    lastHeartbeatAt: string;
    heartbeatStatus: string;
    freshnessLabel: string;
    connectionMeaning: string;
    stateImpact: string;
  };
  topConcern: string | null;
};

export type DashboardModel = {
  generatedAt: string;
  application: {
    name: string;
    environment: string;
    currentWindowEndUtc: string;
    baselineWindowEndUtc: string;
    freshness: string;
  };
  state: {
    code: "STEADY" | "DEGRADED" | "RECOVERING" | "INSUFFICIENT_SAMPLE";
    label: string;
    rationale: string;
    recommendedAction: string;
  };
  starterConnection: {
    statusSource: "starter_heartbeat";
    lastHeartbeatAt: string;
    lastHeartbeatStatus: string;
    connectionMeaning: string;
    stateImpact: string;
  };
  zeroInsight: string | null;
  recovery: { isRecovering: boolean; note: string } | null;
  metrics: { requestCount: number; errorCount: number; errorRate: string };
  sourceScopedPercentiles: {
    items: {
      source: string;
      scope: string;
      p95Ms: number;
      p99Ms: number;
      instance: string;
      bucketBoundary: string;
    }[];
  };
  triageCards: { title: string; detail: string; evidence: string }[];
  endpointPriority: { route: string; reason: string; lastSeen: string }[];
  instances: {
    instanceId: string;
    name: string;
    firstSeen: string;
    lastSeen: string;
    contribution: string;
  }[];
  snapshot: { lastCapturedAt: string; markerCount: number; eventCount: number };
};

export const projects: ProjectItem[] = [
  {
    projectId: "p-orders",
    name: "Orders Platform",
    applicationCount: 4,
    setupConnectionIssueCount: 1,
    recentConcern: "orders-api: p99 above baseline",
  },
  {
    projectId: "p-inventory",
    name: "Inventory Service",
    applicationCount: 2,
    setupConnectionIssueCount: 0,
    recentConcern: null,
  },
  {
    projectId: "p-billing",
    name: "Billing",
    applicationCount: 3,
    setupConnectionIssueCount: 2,
    recentConcern: "billing-worker: heartbeat missing",
  },
];

export const applicationsByProject: Record<string, ApplicationItem[]> = {
  "p-orders": [
    {
      applicationId: "a-orders-api",
      name: "orders-api",
      environment: "prod",
      lifecycleBadge: "DEGRADED",
      metricData: {
        statusSource: "accepted_bucket",
        lastAcceptedBucketAt: "2026-06-02T08:59:30Z",
        freshnessLabel: "fresh · 12s ago",
      },
      starterConnection: {
        statusSource: "starter_heartbeat",
        lastHeartbeatAt: "2026-06-02T08:59:18Z",
        heartbeatStatus: "OK",
        freshnessLabel: "22s ago",
        connectionMeaning: "starter alive",
        stateImpact: "none",
      },
      topConcern: "p99 increased on /orders/{orderId}",
    },
    {
      applicationId: "a-orders-worker",
      name: "orders-worker",
      environment: "prod",
      lifecycleBadge: "STEADY",
      metricData: {
        statusSource: "accepted_bucket",
        lastAcceptedBucketAt: "2026-06-02T08:59:00Z",
        freshnessLabel: "fresh · 42s ago",
      },
      starterConnection: {
        statusSource: "starter_heartbeat",
        lastHeartbeatAt: "2026-06-02T08:59:05Z",
        heartbeatStatus: "OK",
        freshnessLabel: "35s ago",
        connectionMeaning: "starter alive",
        stateImpact: "none",
      },
      topConcern: null,
    },
    {
      applicationId: "a-orders-edge",
      name: "orders-edge",
      environment: "staging",
      lifecycleBadge: "INSUFFICIENT_SAMPLE",
      metricData: {
        statusSource: "accepted_bucket",
        lastAcceptedBucketAt: "2026-06-02T08:55:30Z",
        freshnessLabel: "stale · 4m ago",
      },
      starterConnection: {
        statusSource: "starter_heartbeat",
        lastHeartbeatAt: "2026-06-02T08:58:50Z",
        heartbeatStatus: "OK",
        freshnessLabel: "50s ago",
        connectionMeaning: "starter alive · data missing",
        stateImpact: "observed",
      },
      topConcern: "no accepted bucket for 4m",
    },
  ],
  "p-inventory": [
    {
      applicationId: "a-inv-api",
      name: "inventory-api",
      environment: "prod",
      lifecycleBadge: "STEADY",
      metricData: {
        statusSource: "accepted_bucket",
        lastAcceptedBucketAt: "2026-06-02T08:59:30Z",
        freshnessLabel: "fresh · 10s ago",
      },
      starterConnection: {
        statusSource: "starter_heartbeat",
        lastHeartbeatAt: "2026-06-02T08:59:25Z",
        heartbeatStatus: "OK",
        freshnessLabel: "15s ago",
        connectionMeaning: "starter alive",
        stateImpact: "none",
      },
      topConcern: null,
    },
  ],
  "p-billing": [
    {
      applicationId: "a-billing-worker",
      name: "billing-worker",
      environment: "prod",
      lifecycleBadge: "DEGRADED",
      metricData: {
        statusSource: "accepted_bucket",
        lastAcceptedBucketAt: "2026-06-02T08:42:00Z",
        freshnessLabel: "stale · 18m ago",
      },
      starterConnection: {
        statusSource: "starter_heartbeat",
        lastHeartbeatAt: "2026-06-02T08:30:00Z",
        heartbeatStatus: "MISSING",
        freshnessLabel: "30m ago",
        connectionMeaning: "starter unreachable",
        stateImpact: "observed",
      },
      topConcern: "heartbeat missing for 30m",
    },
  ],
};

export type InstanceEvidence = {
  instance: { instanceId: string; name: string; firstSeen: string; lastSeen: string };
  metricData: {
    statusSource: "accepted_bucket";
    freshnessLabel: string;
    sampleReadiness: string;
    requestCount: number;
    errorCount: number;
    errorRate: string;
  };
  starterConnection: {
    statusSource: "starter_heartbeat";
    lastHeartbeatAt: string;
    stateImpact: "none" | "observed";
  };
  starterPercentilePoints: {
    bucketBoundary: string;
    source: string;
    scope: string;
    p95Ms: number;
    p99Ms: number;
  }[];
  histogramDistribution: { upperMs: number; count: number }[];
  resourceHints: { label: string; value: string }[];
  triageContribution: string[];
  endpointEvidenceSubset: { route: string; p99Ms: number; lastSeen: string }[];
};

export type SnapshotTrendItem = {
  capturedAt: string;
  currentWindowEndUtc: string;
  storedStateCode: "STEADY" | "DEGRADED" | "RECOVERING" | "INSUFFICIENT_SAMPLE";
  metricDataFreshness: string;
  starterConnection: string;
  starterPercentilePoint: { p95Ms: number; p99Ms: number } | null;
  resourceHints: string[];
  triageContribution: string;
  endpointEvidenceRefs: string[];
};

function buildTrend(instanceId: string, base: { p95: number; p99: number }): SnapshotTrendItem[] {
  const out: SnapshotTrendItem[] = [];
  const startMs = Date.parse("2026-05-26T09:00:00Z");
  for (let i = 0; i < 56; i += 1) {
    const t = new Date(startMs + i * 3 * 60 * 60 * 1000);
    const drift = Math.round(Math.sin(i / 3) * 40 + (i > 40 ? 120 : 0));
    const code: SnapshotTrendItem["storedStateCode"] =
      i > 50 ? "DEGRADED" : i > 45 ? "RECOVERING" : "STEADY";
    out.push({
      capturedAt: t.toISOString(),
      currentWindowEndUtc: t.toISOString(),
      storedStateCode: code,
      metricDataFreshness: "fresh",
      starterConnection: "heartbeat ok",
      starterPercentilePoint: { p95Ms: base.p95 + drift, p99Ms: base.p99 + drift * 2 },
      resourceHints: i % 6 === 0 ? ["cpu 78%"] : [],
      triageContribution: code === "DEGRADED" ? "p99 above baseline" : "—",
      endpointEvidenceRefs: code === "DEGRADED" ? [`/orders/{orderId}@${instanceId}`] : [],
    });
  }
  return out;
}

export const instanceEvidenceById: Record<string, InstanceEvidence> = {
  "i-7c4a": {
    instance: {
      instanceId: "i-7c4a",
      name: "orders-api-7c4a",
      firstSeen: "2026-05-30T11:00:00Z",
      lastSeen: "2026-06-02T08:59:30Z",
    },
    metricData: {
      statusSource: "accepted_bucket",
      freshnessLabel: "fresh · 12s ago",
      sampleReadiness: "ready",
      requestCount: 9210,
      errorCount: 98,
      errorRate: "1.06%",
    },
    starterConnection: {
      statusSource: "starter_heartbeat",
      lastHeartbeatAt: "2026-06-02T08:59:18Z",
      stateImpact: "none",
    },
    starterPercentilePoints: [
      { bucketBoundary: "2026-06-02T08:59:30Z", source: "starter_histogram", scope: "instance", p95Ms: 430, p99Ms: 1220 },
      { bucketBoundary: "2026-06-02T08:59:00Z", source: "starter_histogram", scope: "instance", p95Ms: 410, p99Ms: 1180 },
      { bucketBoundary: "2026-06-02T08:58:30Z", source: "starter_histogram", scope: "instance", p95Ms: 402, p99Ms: 1140 },
    ],
    histogramDistribution: [
      { upperMs: 50, count: 4210 },
      { upperMs: 100, count: 2380 },
      { upperMs: 250, count: 1640 },
      { upperMs: 500, count: 540 },
      { upperMs: 1000, count: 280 },
      { upperMs: 2500, count: 160 },
    ],
    resourceHints: [
      { label: "cpu", value: "78%" },
      { label: "heap used", value: "612 MB" },
    ],
    triageContribution: [
      "p99 above baseline on /orders/{orderId}",
      "error rate uplift on POST /orders",
    ],
    endpointEvidenceSubset: [
      { route: "/orders/{orderId}", p99Ms: 1220, lastSeen: "12s ago" },
      { route: "POST /orders", p99Ms: 880, lastSeen: "20s ago" },
    ],
  },
  "i-9b21": {
    instance: {
      instanceId: "i-9b21",
      name: "orders-api-9b21",
      firstSeen: "2026-05-30T11:00:00Z",
      lastSeen: "2026-06-02T08:59:30Z",
    },
    metricData: {
      statusSource: "accepted_bucket",
      freshnessLabel: "fresh · 14s ago",
      sampleReadiness: "ready",
      requestCount: 9210,
      errorCount: 44,
      errorRate: "0.48%",
    },
    starterConnection: {
      statusSource: "starter_heartbeat",
      lastHeartbeatAt: "2026-06-02T08:59:20Z",
      stateImpact: "none",
    },
    starterPercentilePoints: [
      { bucketBoundary: "2026-06-02T08:59:30Z", source: "starter_histogram", scope: "instance", p95Ms: 388, p99Ms: 990 },
      { bucketBoundary: "2026-06-02T08:59:00Z", source: "starter_histogram", scope: "instance", p95Ms: 372, p99Ms: 940 },
    ],
    histogramDistribution: [
      { upperMs: 50, count: 4520 },
      { upperMs: 100, count: 2510 },
      { upperMs: 250, count: 1380 },
      { upperMs: 500, count: 460 },
      { upperMs: 1000, count: 220 },
      { upperMs: 2500, count: 110 },
    ],
    resourceHints: [
      { label: "cpu", value: "52%" },
      { label: "heap used", value: "488 MB" },
    ],
    triageContribution: ["baseline-matching"],
    endpointEvidenceSubset: [
      { route: "/orders/{orderId}", p99Ms: 990, lastSeen: "14s ago" },
    ],
  },
  "i-w1": {
    instance: {
      instanceId: "i-w1",
      name: "orders-worker-1",
      firstSeen: "2026-05-28T10:00:00Z",
      lastSeen: "2026-06-02T08:59:30Z",
    },
    metricData: {
      statusSource: "accepted_bucket",
      freshnessLabel: "fresh · 42s ago",
      sampleReadiness: "ready",
      requestCount: 5210,
      errorCount: 4,
      errorRate: "0.08%",
    },
    starterConnection: {
      statusSource: "starter_heartbeat",
      lastHeartbeatAt: "2026-06-02T08:59:05Z",
      stateImpact: "none",
    },
    starterPercentilePoints: [
      { bucketBoundary: "2026-06-02T08:59:30Z", source: "starter_histogram", scope: "instance", p95Ms: 88, p99Ms: 142 },
    ],
    histogramDistribution: [
      { upperMs: 50, count: 3210 },
      { upperMs: 100, count: 1480 },
      { upperMs: 250, count: 420 },
      { upperMs: 500, count: 80 },
    ],
    resourceHints: [{ label: "cpu", value: "31%" }],
    triageContribution: ["single contributor"],
    endpointEvidenceSubset: [],
  },
  "i-inv-a": {
    instance: {
      instanceId: "i-inv-a",
      name: "inventory-api-a",
      firstSeen: "2026-05-29T10:00:00Z",
      lastSeen: "2026-06-02T08:59:30Z",
    },
    metricData: {
      statusSource: "accepted_bucket",
      freshnessLabel: "fresh · 10s ago",
      sampleReadiness: "ready",
      requestCount: 9120,
      errorCount: 12,
      errorRate: "0.13%",
    },
    starterConnection: {
      statusSource: "starter_heartbeat",
      lastHeartbeatAt: "2026-06-02T08:59:25Z",
      stateImpact: "none",
    },
    starterPercentilePoints: [
      { bucketBoundary: "2026-06-02T08:59:30Z", source: "starter_histogram", scope: "instance", p95Ms: 120, p99Ms: 240 },
    ],
    histogramDistribution: [
      { upperMs: 50, count: 5800 },
      { upperMs: 100, count: 2200 },
      { upperMs: 250, count: 880 },
      { upperMs: 500, count: 180 },
    ],
    resourceHints: [{ label: "cpu", value: "44%" }],
    triageContribution: ["baseline-matching · recovering"],
    endpointEvidenceSubset: [],
  },
};

export const snapshotTrendByInstance: Record<string, SnapshotTrendItem[]> = {
  "i-7c4a": buildTrend("i-7c4a", { p95: 400, p99: 900 }),
  "i-9b21": buildTrend("i-9b21", { p95: 380, p99: 880 }),
  "i-w1": buildTrend("i-w1", { p95: 85, p99: 140 }),
  "i-inv-a": buildTrend("i-inv-a", { p95: 120, p99: 240 }),
};

export const dashboardByApplication: Record<string, DashboardModel> = {
  "a-orders-api": {
    generatedAt: "2026-06-02T09:00:00Z",
    application: {
      name: "orders-api",
      environment: "prod",
      currentWindowEndUtc: "2026-06-02T09:00:00Z",
      baselineWindowEndUtc: "2026-06-01T09:00:00Z",
      freshness: "fresh · 12s ago",
    },
    state: {
      code: "DEGRADED",
      label: "DEGRADED",
      rationale: "p99 on /orders/{orderId} exceeds baseline by 2.4x in current window",
      recommendedAction: "먼저 확인할 endpoint 3건을 확인하세요",
    },
    starterConnection: {
      statusSource: "starter_heartbeat",
      lastHeartbeatAt: "2026-06-02T08:59:18Z",
      lastHeartbeatStatus: "OK",
      connectionMeaning: "starter alive",
      stateImpact: "none",
    },
    zeroInsight: null,
    recovery: null,
    metrics: { requestCount: 18420, errorCount: 142, errorRate: "0.77%" },
    sourceScopedPercentiles: {
      items: [
        {
          source: "starter_histogram",
          scope: "application",
          p95Ms: 412,
          p99Ms: 1180,
          instance: "orders-api-7c4a",
          bucketBoundary: "2026-06-02T09:00:00Z",
        },
        {
          source: "starter_histogram",
          scope: "application",
          p95Ms: 388,
          p99Ms: 990,
          instance: "orders-api-9b21",
          bucketBoundary: "2026-06-02T09:00:00Z",
        },
      ],
    },
    triageCards: [
      {
        title: "Slow endpoint detected",
        detail: "/orders/{orderId} p99 1180ms vs baseline 480ms",
        evidence: "source-scoped percentile · last 30m",
      },
      {
        title: "Error rate uplift",
        detail: "POST /orders error rate 1.4% vs baseline 0.3%",
        evidence: "accepted bucket error counters",
      },
    ],
    endpointPriority: [
      { route: "/orders/{orderId}", reason: "p99 above baseline", lastSeen: "12s ago" },
      { route: "POST /orders", reason: "error rate uplift", lastSeen: "20s ago" },
      { route: "/orders/{orderId}/items", reason: "request rate drop", lastSeen: "1m ago" },
    ],
    instances: [
      {
        instanceId: "i-7c4a",
        name: "orders-api-7c4a",
        firstSeen: "2026-05-30T11:00:00Z",
        lastSeen: "2026-06-02T08:59:30Z",
        contribution: "carries the slow endpoint evidence",
      },
      {
        instanceId: "i-9b21",
        name: "orders-api-9b21",
        firstSeen: "2026-05-30T11:00:00Z",
        lastSeen: "2026-06-02T08:59:30Z",
        contribution: "baseline-matching",
      },
    ],
    snapshot: {
      lastCapturedAt: "2026-06-02T08:55:00Z",
      markerCount: 4,
      eventCount: 6,
    },
  },
  "a-orders-worker": {
    generatedAt: "2026-06-02T09:00:00Z",
    application: {
      name: "orders-worker",
      environment: "prod",
      currentWindowEndUtc: "2026-06-02T09:00:00Z",
      baselineWindowEndUtc: "2026-06-01T09:00:00Z",
      freshness: "fresh · 42s ago",
    },
    state: { code: "STEADY", label: "STEADY", rationale: "within baseline", recommendedAction: "관찰 유지" },
    starterConnection: {
      statusSource: "starter_heartbeat",
      lastHeartbeatAt: "2026-06-02T08:59:05Z",
      lastHeartbeatStatus: "OK",
      connectionMeaning: "starter alive",
      stateImpact: "none",
    },
    zeroInsight: null,
    recovery: null,
    metrics: { requestCount: 5210, errorCount: 4, errorRate: "0.08%" },
    sourceScopedPercentiles: {
      items: [
        {
          source: "starter_histogram",
          scope: "application",
          p95Ms: 88,
          p99Ms: 142,
          instance: "orders-worker-1",
          bucketBoundary: "2026-06-02T09:00:00Z",
        },
      ],
    },
    triageCards: [],
    endpointPriority: [],
    instances: [
      {
        instanceId: "i-w1",
        name: "orders-worker-1",
        firstSeen: "2026-05-28T10:00:00Z",
        lastSeen: "2026-06-02T08:59:30Z",
        contribution: "single contributor",
      },
    ],
    snapshot: { lastCapturedAt: "2026-06-02T08:55:00Z", markerCount: 1, eventCount: 1 },
  },
  "a-orders-edge": {
    generatedAt: "2026-06-02T09:00:00Z",
    application: {
      name: "orders-edge",
      environment: "staging",
      currentWindowEndUtc: "2026-06-02T09:00:00Z",
      baselineWindowEndUtc: "2026-06-01T09:00:00Z",
      freshness: "stale · 4m ago",
    },
    state: {
      code: "INSUFFICIENT_SAMPLE",
      label: "INSUFFICIENT SAMPLE",
      rationale: "no accepted bucket in current window",
      recommendedAction: "starter metric flush 설정을 확인하세요",
    },
    starterConnection: {
      statusSource: "starter_heartbeat",
      lastHeartbeatAt: "2026-06-02T08:58:50Z",
      lastHeartbeatStatus: "OK",
      connectionMeaning: "starter alive · metric not arriving",
      stateImpact: "observed",
    },
    zeroInsight: "현재 윈도우에 sample이 부족합니다. starter metric flush 설정 확인이 필요합니다.",
    recovery: null,
    metrics: { requestCount: 0, errorCount: 0, errorRate: "—" },
    sourceScopedPercentiles: { items: [] },
    triageCards: [],
    endpointPriority: [],
    instances: [],
    snapshot: { lastCapturedAt: "2026-06-02T08:30:00Z", markerCount: 0, eventCount: 2 },
  },
  "a-inv-api": {
    generatedAt: "2026-06-02T09:00:00Z",
    application: {
      name: "inventory-api",
      environment: "prod",
      currentWindowEndUtc: "2026-06-02T09:00:00Z",
      baselineWindowEndUtc: "2026-06-01T09:00:00Z",
      freshness: "fresh · 10s ago",
    },
    state: { code: "STEADY", label: "STEADY", rationale: "within baseline", recommendedAction: "관찰 유지" },
    starterConnection: {
      statusSource: "starter_heartbeat",
      lastHeartbeatAt: "2026-06-02T08:59:25Z",
      lastHeartbeatStatus: "OK",
      connectionMeaning: "starter alive",
      stateImpact: "none",
    },
    zeroInsight: null,
    recovery: { isRecovering: true, note: "회복 관찰 중 · 두 윈도우 연속 baseline 복귀 확인 필요" },
    metrics: { requestCount: 9120, errorCount: 12, errorRate: "0.13%" },
    sourceScopedPercentiles: {
      items: [
        {
          source: "starter_histogram",
          scope: "application",
          p95Ms: 120,
          p99Ms: 240,
          instance: "inventory-api-a",
          bucketBoundary: "2026-06-02T09:00:00Z",
        },
      ],
    },
    triageCards: [],
    endpointPriority: [],
    instances: [
      {
        instanceId: "i-inv-a",
        name: "inventory-api-a",
        firstSeen: "2026-05-29T10:00:00Z",
        lastSeen: "2026-06-02T08:59:30Z",
        contribution: "single contributor",
      },
    ],
    snapshot: { lastCapturedAt: "2026-06-02T08:55:00Z", markerCount: 0, eventCount: 0 },
  },
  "a-billing-worker": {
    generatedAt: "2026-06-02T09:00:00Z",
    application: {
      name: "billing-worker",
      environment: "prod",
      currentWindowEndUtc: "2026-06-02T09:00:00Z",
      baselineWindowEndUtc: "2026-06-01T09:00:00Z",
      freshness: "stale · 18m ago",
    },
    state: {
      code: "DEGRADED",
      label: "DEGRADED",
      rationale: "starter heartbeat missing for 30m; metric data stale",
      recommendedAction: "starter heartbeat 설정과 process 상태를 확인하세요",
    },
    starterConnection: {
      statusSource: "starter_heartbeat",
      lastHeartbeatAt: "2026-06-02T08:30:00Z",
      lastHeartbeatStatus: "MISSING",
      connectionMeaning: "starter unreachable",
      stateImpact: "observed",
    },
    zeroInsight: null,
    recovery: null,
    metrics: { requestCount: 30, errorCount: 0, errorRate: "0.00%" },
    sourceScopedPercentiles: { items: [] },
    triageCards: [
      {
        title: "Starter heartbeat missing",
        detail: "지난 30분간 heartbeat 수신 없음",
        evidence: "starter_heartbeat source",
      },
    ],
    endpointPriority: [],
    instances: [],
    snapshot: { lastCapturedAt: "2026-06-02T08:30:00Z", markerCount: 2, eventCount: 3 },
  },
};
