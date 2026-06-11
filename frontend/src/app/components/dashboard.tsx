import { type FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { LucideIcon } from "lucide-react";
import {
  Activity,
  AlertCircle,
  Check,
  ChevronRight,
  Copy,
  Gauge,
  KeyRound,
  ListChecks,
  Radio,
  RefreshCw,
  Search,
  Server,
  X,
} from "lucide-react";
import {
  ApiRequestError,
  AuthRequiredError,
  CREDENTIAL_LIFECYCLE_REQUEST_OPTIONS,
  JSON_BODY_HEADERS,
  NO_STORE_REQUEST_OPTIONS,
  READ_MODEL_ENDPOINTS,
  SECRET_BEARING_REQUEST_OPTIONS,
  readJsonResource,
} from "../lib/api";
import { type AuthFetch, useAuth } from "../lib/auth";
import { guardApplicationDashboardReadModel } from "../lib/read-model-contract-guard";
import { useApiResource } from "../lib/use-api-resource";
import {
  buildStarterCredentialMetadataPath,
  buildStarterCredentialRevocationPath,
  buildStarterCredentialRotationPath,
  formatCount,
  formatDateRange,
  formatNullableRatio,
  formatOptionalDateTime,
  formatRatio,
  histogramRangeBarWidth,
  humanizeSourceCode,
  humanizeStatusCode,
  severityBadgeClassName,
  severityDisplayText,
  statusBadgeClassName,
  toApplicationPresentationItems,
  toDashboardPresentation,
  toDisplayLatencyBuckets,
  toProjectPresentationItems,
  validateDashboardPath,
  validateProjectApplicationsPath,
  type ApplicationPresentationItem,
  type DashboardPresentation,
  type ProjectPresentationItem,
} from "../lib/read-model-adapters";
import type {
  ApplicationDashboardReadModel,
  DashboardAttentionEvidence,
  DashboardFirstLookCandidate,
  DashboardResourceSignal,
  DashboardStateReason,
  EndpointPriorityItem,
  HistogramWindow,
  OneTimeStarterCredential,
  ProjectRegistrationResponse,
  ProjectApplicationNavigationReadModel,
  ProjectNavigationReadModel,
  StarterCredentialMetadataResponse,
  StarterCredentialRotationResponse,
  TriageCard,
} from "../lib/read-model-types";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "./ui/tooltip";
import { InstancePanels, useInstanceView } from "./instance-panels";
import { SnapshotHistoryPanel } from "./snapshot-history-panel";

type ResourceScope = "applications" | "dashboard" | "projects";

function StatusBadge({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <span className={`inline-flex shrink-0 items-center whitespace-nowrap border px-1.5 py-0.5 text-[11px] uppercase ${className || "border-neutral-400 text-neutral-800"}`}>
      {children}
    </span>
  );
}

function SectionLabel({ icon: Icon, children }: { icon: LucideIcon; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-1.5 text-neutral-500 text-[11px] uppercase">
      <Icon className="h-3.5 w-3.5" strokeWidth={1.5} />
      {children}
    </div>
  );
}

function InlineHelp({ children, label }: { children: React.ReactNode; label: string }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          aria-label={label}
          className="inline-flex h-4 w-4 items-center justify-center rounded-full border border-neutral-300 text-[10px] leading-none text-neutral-500 hover:border-neutral-500 hover:text-neutral-800"
        >
          ?
        </button>
      </TooltipTrigger>
      <TooltipContent className="max-w-xs text-[11px] leading-relaxed">{children}</TooltipContent>
    </Tooltip>
  );
}

/**
 * Project/Application/Dashboard read model을 실제 backend link chain으로 로드하는 운영 첫 화면이다.
 * 각 resource의 인증, 404, empty, filter-empty 상태를 서로 다른 의미로 유지한다.
 */
export function Dashboard() {
  const auth = useAuth();
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [selectedApplicationId, setSelectedApplicationId] = useState<string | null>(null);
  const [projectFilter, setProjectFilter] = useState("");
  const [applicationFilter, setApplicationFilter] = useState("");
  const [pendingProjectSelectionId, setPendingProjectSelectionId] = useState<string | null>(null);
  const instanceView = useInstanceView();

  const requestProjects = useCallback(async ({ authFetch, signal }: { authFetch: AuthFetch; signal: AbortSignal }) => {
    const response = await authFetch(READ_MODEL_ENDPOINTS.projects, {
      ...NO_STORE_REQUEST_OPTIONS,
      signal,
    });
    return readJsonResource<ProjectNavigationReadModel>(response);
  }, []);

  const projectsResource = useApiResource<ProjectNavigationReadModel>({
    request: requestProjects,
  });

  const projects = useMemo(
    () => (projectsResource.data ? toProjectPresentationItems(projectsResource.data) : []),
    [projectsResource.data],
  );

  const selectedProject = useMemo(
    () => projects.find((project) => project.projectId === selectedProjectId) ?? null,
    [projects, selectedProjectId],
  );

  useEffect(() => {
    const nextProjectId = projects.find((project) => project.projectId === selectedProjectId)?.projectId ?? projects[0]?.projectId ?? null;
    if (nextProjectId !== selectedProjectId) {
      setSelectedProjectId(nextProjectId);
    }
  }, [projects, selectedProjectId]);

  useEffect(() => {
    if (!pendingProjectSelectionId) {
      return;
    }
    const registeredProject = projects.find((project) => project.projectId === pendingProjectSelectionId);
    if (registeredProject) {
      setSelectedProjectId(registeredProject.projectId);
      setPendingProjectSelectionId(null);
    }
  }, [pendingProjectSelectionId, projects]);

  useEffect(() => {
    setSelectedApplicationId(null);
  }, [selectedProjectId]);

  useEffect(() => {
    instanceView.close();
  }, [selectedApplicationId, selectedProjectId]);

  const applicationsResourceKey = selectedProject
    ? `${selectedProject.projectId}|${selectedProject.applicationsLink}`
    : "applications:none";

  const requestApplications = useCallback(
    async ({ authFetch, signal }: { authFetch: AuthFetch; signal: AbortSignal }) => {
      if (!selectedProject) {
        throw new ApiRequestError("project_not_selected");
      }
      const applicationsPath = validateProjectApplicationsPath(selectedProject.applicationsLink, selectedProject.projectId);
      const response = await authFetch(applicationsPath, {
        ...NO_STORE_REQUEST_OPTIONS,
        signal,
      });
      const model = await readJsonResource<ProjectApplicationNavigationReadModel>(response);
      if (model.project.projectId !== selectedProject.projectId) {
        throw new ApiRequestError("application_context_mismatch");
      }
      return model;
    },
    [selectedProject],
  );

  const applicationsResource = useApiResource<ProjectApplicationNavigationReadModel>({
    dependencies: [applicationsResourceKey],
    enabled: Boolean(selectedProject),
    request: requestApplications,
    resourceKey: applicationsResourceKey,
  });

  const applicationsResourceCurrent = applicationsResource.resourceKey === applicationsResourceKey;
  const applicationsLoading = Boolean(selectedProject) && (!applicationsResourceCurrent || applicationsResource.loading);
  const applicationsError = applicationsResourceCurrent ? applicationsResource.error : null;

  const applications = useMemo(() => {
    if (!applicationsResourceCurrent || !applicationsResource.data || !selectedProject) {
      return [];
    }
    if (applicationsResource.data.project.projectId !== selectedProject.projectId) {
      return [];
    }
    return toApplicationPresentationItems(applicationsResource.data);
  }, [applicationsResource.data, applicationsResourceCurrent, selectedProject]);

  const selectedApplication = useMemo(
    () => applications.find((application) => application.applicationId === selectedApplicationId) ?? null,
    [applications, selectedApplicationId],
  );

  useEffect(() => {
    const nextApplicationId =
      applications.find((application) => application.applicationId === selectedApplicationId)?.applicationId ??
      applications[0]?.applicationId ??
      null;
    if (nextApplicationId !== selectedApplicationId) {
      setSelectedApplicationId(nextApplicationId);
    }
  }, [applications, selectedApplicationId]);

  const dashboardResourceKey = selectedProject && selectedApplication
    ? `${selectedProject.projectId}|${selectedApplication.applicationId}|${selectedApplication.dashboardLink}`
    : "dashboard:none";

  const requestDashboard = useCallback(
    async ({ authFetch, signal }: { authFetch: AuthFetch; signal: AbortSignal }) => {
      if (!selectedProject || !selectedApplication) {
        throw new ApiRequestError("application_not_selected");
      }
      const dashboardPath = validateDashboardPath(
        selectedApplication.dashboardLink,
        selectedProject.projectId,
        selectedApplication.applicationId,
      );
      const response = await authFetch(dashboardPath, {
        ...NO_STORE_REQUEST_OPTIONS,
        signal,
      });
      const model = guardApplicationDashboardReadModel(await readJsonResource<ApplicationDashboardReadModel>(response), {
        projectId: selectedProject.projectId,
        applicationId: selectedApplication.applicationId,
      });
      if (
        model.application.projectId !== selectedProject.projectId ||
        model.application.applicationId !== selectedApplication.applicationId
      ) {
        throw new ApiRequestError("dashboard_context_mismatch");
      }
      return model;
    },
    [selectedApplication, selectedProject],
  );

  const dashboardResource = useApiResource<ApplicationDashboardReadModel>({
    dependencies: [dashboardResourceKey],
    enabled: Boolean(selectedProject && selectedApplication),
    request: requestDashboard,
    resourceKey: dashboardResourceKey,
  });

  const dashboardResourceCurrent = dashboardResource.resourceKey === dashboardResourceKey;
  const dashboardLoading = Boolean(selectedProject && selectedApplication) && (!dashboardResourceCurrent || dashboardResource.loading);
  const dashboardError = dashboardResourceCurrent ? dashboardResource.error : null;

  const dashboard = useMemo(() => {
    if (!dashboardResourceCurrent || !dashboardResource.data || !selectedProject || !selectedApplication) {
      return null;
    }
    if (
      dashboardResource.data.application.projectId !== selectedProject.projectId ||
      dashboardResource.data.application.applicationId !== selectedApplication.applicationId
    ) {
      return null;
    }
    return toDashboardPresentation(dashboardResource.data);
  }, [dashboardResource.data, dashboardResourceCurrent, selectedApplication, selectedProject]);

  const filteredProjects = useMemo(() => {
    const query = projectFilter.trim().toLowerCase();
    return query ? projects.filter((project) => project.name.toLowerCase().includes(query)) : projects;
  }, [projectFilter, projects]);

  const filteredApplications = useMemo(() => {
    const query = applicationFilter.trim().toLowerCase();
    return query
      ? applications.filter((application) =>
          `${application.name} ${application.environment}`.toLowerCase().includes(query),
        )
      : applications;
  }, [applicationFilter, applications]);

  const authStatusText =
    auth.errorMessage || auth.statusMessage || (auth.authenticated ? "GitHub 인증됨" : "GitHub 로그인 필요");

  return (
    <TooltipProvider delayDuration={150}>
      <div className="min-h-[calc(100vh-56px)] overflow-x-hidden bg-neutral-50">
        <div className="border-b border-neutral-200 bg-white">
          <div className="mx-auto grid min-h-12 max-w-[1400px] gap-2 px-3 py-2 text-[13px] md:flex md:items-center md:justify-between md:px-6 md:py-0">
            <div className="flex min-w-0 items-center gap-2 text-neutral-600">
              <span>Projects</span>
              <ChevronRight className="h-3.5 w-3.5 shrink-0" strokeWidth={1.5} />
              <span className="truncate text-neutral-900">{selectedProject?.name ?? "선택 대기"}</span>
              {selectedApplication && (
                <>
                  <ChevronRight className="h-3.5 w-3.5 shrink-0" strokeWidth={1.5} />
                  <span className="truncate text-neutral-900">{selectedApplication.name}</span>
                  <span className="shrink-0 text-neutral-500">· {selectedApplication.environment}</span>
                </>
              )}
            </div>
            <div className="flex min-w-0 flex-wrap items-center justify-between gap-2 text-neutral-600 md:justify-end md:gap-3">
              <span className="hidden max-w-[360px] truncate text-[12px] md:inline">{authStatusText}</span>
              <Button variant="outline" size="sm" className="gap-2 border-neutral-300" onClick={() => {
                projectsResource.reload();
                applicationsResource.reload();
                dashboardResource.reload();
              }}>
                <RefreshCw className="h-3.5 w-3.5" strokeWidth={1.5} /> Reload
              </Button>
            </div>
          </div>
        </div>

        <div className="mx-auto grid max-w-[1400px] grid-cols-1 gap-0 bg-white md:grid-cols-[minmax(180px,0.8fr)_minmax(240px,1fr)] xl:min-h-[calc(100vh-104px)] xl:grid-cols-[minmax(170px,2fr)_minmax(260px,3fr)_minmax(0,7fr)]">
          <aside className="min-w-0 border-b border-neutral-200 bg-white md:border-r xl:min-h-[calc(100vh-104px)] xl:border-b-0">
            <div className="p-3 border-b border-neutral-200">
              <SectionLabel icon={ListChecks}>Projects</SectionLabel>
              <div className="relative mt-2">
                <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-neutral-400" strokeWidth={1.5} />
                <Input
                  value={projectFilter}
                  onChange={(event) => setProjectFilter(event.target.value)}
                  placeholder="검색"
                  className="h-8 pl-7 border-neutral-300 bg-white"
                />
              </div>
            </div>
            <ProjectRail
              error={projectsResource.error}
              filteredProjects={filteredProjects}
              loading={projectsResource.loading}
              onReload={projectsResource.reload}
              onSelectProject={(projectId) => setSelectedProjectId(projectId)}
              projectFilter={projectFilter}
              projects={projects}
              selectedProjectId={selectedProjectId}
            />
            <div className="p-3 border-t border-neutral-200">
              <ProjectRegistrationPanel
                onRegistered={(projectId) => {
                  setPendingProjectSelectionId(projectId);
                  projectsResource.reload();
                }}
              />
            </div>
          </aside>

          <aside className="min-w-0 border-b border-neutral-200 bg-white xl:border-r xl:border-b-0">
            <div className="p-3 border-b border-neutral-200">
              <SectionLabel icon={Server}>Applications</SectionLabel>
              <div className="relative mt-2">
                <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-neutral-400" strokeWidth={1.5} />
                <Input
                  value={applicationFilter}
                  onChange={(event) => setApplicationFilter(event.target.value)}
                  placeholder="검색"
                  className="h-8 pl-7 border-neutral-300"
                />
              </div>
            </div>
            <ApplicationRail
              applications={applications}
              error={applicationsError}
              filteredApplications={filteredApplications}
              loading={applicationsLoading}
              onReload={applicationsResource.reload}
              onSelectApplication={(applicationId) => setSelectedApplicationId(applicationId)}
              selectedApplicationId={selectedApplicationId}
              selectedProject={selectedProject}
              applicationFilter={applicationFilter}
            />
          </aside>

          <main className="min-w-0 bg-neutral-50 md:col-span-2 xl:col-span-1">
            <DashboardMain
              dashboard={dashboard}
              error={dashboardError}
              loading={dashboardLoading}
              onOpenEvidence={instanceView.openEvidence}
              onOpenSnapshotDashboard={instanceView.openSnapshotDashboard}
              onReload={dashboardResource.reload}
              selectedApplication={selectedApplication}
              selectedProject={selectedProject}
            />
          </main>
        </div>

        <InstancePanels
          view={instanceView.view}
          onClose={instanceView.close}
        />
      </div>
    </TooltipProvider>
  );
}

function ProjectRail({
  error,
  filteredProjects,
  loading,
  onReload,
  onSelectProject,
  projectFilter,
  projects,
  selectedProjectId,
}: {
  error: Error | null;
  filteredProjects: ProjectPresentationItem[];
  loading: boolean;
  onReload: () => void;
  onSelectProject: (projectId: string) => void;
  projectFilter: string;
  projects: ProjectPresentationItem[];
  selectedProjectId: string | null;
}) {
  if (loading) {
    return <ResourceMessage title="Project 로딩 중" body="계정에 연결된 프로젝트 목록을 불러오는 중입니다." />;
  }
  if (error) {
    return <ResourceErrorMessage scope="projects" error={error} onReload={onReload} />;
  }
  if (projects.length === 0) {
    return (
      <ResourceMessage
        title="표시할 Project 없음"
        body="현재 GitHub 계정의 active membership project가 없습니다. 인증 상태나 application 상태로 해석하지 않습니다."
      />
    );
  }
  if (filteredProjects.length === 0) {
    return <ResourceMessage title="검색 결과 없음" body={`"${projectFilter}" 필터와 일치하는 loaded project가 없습니다.`} />;
  }
  return (
    <ul className="m-0 list-none p-0">
      {filteredProjects.map((project) => {
        const active = project.projectId === selectedProjectId;
        const projectMeta = project.setupConnectionIssueCount > 0
          ? `${project.applicationCount} apps · ${project.setupConnectionIssueCount} setup signal`
          : `${project.applicationCount} apps · setup signal 없음`;
        return (
          <li key={project.projectId}>
            <button
              onClick={() => onSelectProject(project.projectId)}
              className={`w-full border-b border-neutral-100 border-l-2 px-3 py-2.5 text-left ${active ? "border-l-neutral-900 bg-neutral-50" : "border-l-transparent hover:bg-neutral-50"}`}
            >
              <div className="text-[13px] text-neutral-900">{project.name}</div>
              <div className="mt-0.5 text-[11px] text-neutral-500">{projectMeta}</div>
              <div className="mt-1.5 border-l-2 border-neutral-300 pl-2 text-[11px] text-neutral-700">
                <span className="line-clamp-2">{project.recentConcernDisplay}</span>
              </div>
            </button>
          </li>
        );
      })}
    </ul>
  );
}

function ApplicationRail({
  applicationFilter,
  applications,
  error,
  filteredApplications,
  loading,
  onReload,
  onSelectApplication,
  selectedApplicationId,
  selectedProject,
}: {
  applicationFilter: string;
  applications: ApplicationPresentationItem[];
  error: Error | null;
  filteredApplications: ApplicationPresentationItem[];
  loading: boolean;
  onReload: () => void;
  onSelectApplication: (applicationId: string) => void;
  selectedApplicationId: string | null;
  selectedProject: ProjectPresentationItem | null;
}) {
  if (!selectedProject) {
    return <ResourceMessage title="Project 선택 대기" body="Project 목록이 로드되면 application link를 통해 다음 목록을 불러옵니다." />;
  }
  if (loading) {
    return <ResourceMessage title="Application 로딩 중" body="선택한 project의 앱 목록을 불러오는 중입니다." />;
  }
  if (error) {
    return <ResourceErrorMessage scope="applications" error={error} onReload={onReload} />;
  }
  if (applications.length === 0) {
    return (
      <ResourceMessage
        title="Application 목록 없음"
        body="이 project에 등록된 앱이나 첫 수집 데이터가 아직 없습니다. 정상/장애 상태를 단정하지 않습니다."
      />
    );
  }
  if (filteredApplications.length === 0) {
    return <ResourceMessage title="검색 결과 없음" body={`"${applicationFilter}" 필터와 일치하는 loaded application이 없습니다.`} />;
  }
  return (
    <ul className="m-0 list-none p-0">
      {filteredApplications.map((application) => {
        const active = application.applicationId === selectedApplicationId;
        return (
          <li key={application.applicationId}>
            <button
              onClick={() => onSelectApplication(application.applicationId)}
              className={`w-full border-b border-neutral-100 border-l-2 px-3 py-2.5 text-left ${active ? "border-l-neutral-900 bg-neutral-50" : "border-l-transparent hover:bg-neutral-50"}`}
            >
              <div className="flex flex-wrap items-center gap-1.5">
                <StatusBadge className={application.lifecycleBadgeClassName}>{application.lifecycleBadgeDisplay}</StatusBadge>
                <StatusBadge className={statusBadgeClassName(application.starterConnection.heartbeatStatus)}>
                  starter {humanizeStatusCode(application.starterConnection.heartbeatStatus)}
                </StatusBadge>
              </div>
              <div className="mt-2 min-w-0 text-[13px] text-neutral-900">
                <span>{application.name}</span>
                <span className="text-neutral-500"> · {application.environment}</span>
              </div>
              <div className="mt-0.5 text-[11px] text-neutral-500">
                accepted bucket {application.metricLastAcceptedBucketDisplay} · {humanizeStatusCode(application.metricData.freshnessLabel)}
              </div>
              <div className="mt-0.5 text-[11px] text-neutral-500">
                heartbeat {application.starterLastHeartbeatDisplay} · {humanizeStatusCode(application.starterConnection.connectionMeaning)}
              </div>
              <div className="mt-1.5 border-l-2 border-neutral-300 pl-2 text-[11px] text-neutral-700">
                <span className="line-clamp-2">{application.topConcernDisplay}</span>
              </div>
            </button>
          </li>
        );
      })}
    </ul>
  );
}

function DashboardMain({
  dashboard,
  error,
  loading,
  onOpenEvidence,
  onOpenSnapshotDashboard,
  onReload,
  selectedApplication,
  selectedProject,
}: {
  dashboard: DashboardPresentation | null;
  error: Error | null;
  loading: boolean;
  onOpenEvidence: ReturnType<typeof useInstanceView>["openEvidence"];
  onOpenSnapshotDashboard: ReturnType<typeof useInstanceView>["openSnapshotDashboard"];
  onReload: () => void;
  selectedApplication: ApplicationPresentationItem | null;
  selectedProject: ProjectPresentationItem | null;
}) {
  if (!selectedProject) {
    return <MainMessage title="Project를 선택하세요" body="Project 목록이 로드된 뒤 application 목록을 불러옵니다." />;
  }
  if (!selectedApplication) {
    return <MainMessage title="Application 선택 대기" body="선택한 project의 application 목록이 비어 있거나 아직 로드 중입니다." />;
  }
  if (loading) {
    return <MainMessage title="Dashboard 로딩 중" body="선택한 application의 상태 화면을 불러오는 중입니다." />;
  }
  if (error) {
    return <ResourceErrorMessage scope="dashboard" error={error} onReload={onReload} roomy />;
  }
  if (!dashboard) {
    return <MainMessage title="Dashboard 선택 대기" body="Application을 선택하면 상태 화면을 불러옵니다." />;
  }
  return (
    <div className="grid gap-4 p-3 md:p-5">
      <DashboardContext selectedProject={selectedProject} dashboard={dashboard} />
      <DataQualityFreshnessStrip dashboard={dashboard} />
      <LifecycleStateHero dashboard={dashboard} />
      <DirectStateReasonsPanel reasons={dashboard.stateReasons} />
      <AttentionAndFirstLookPanel
        evidence={dashboard.attentionEvidence}
        firstLookCandidates={dashboard.firstLookCandidates}
      />
      <EndpointResourceEvidencePanel dashboard={dashboard} />
      <MetricDetailSection dashboard={dashboard} />
      <StarterConnectionStrip dashboard={dashboard} />
      <InstancesPanel dashboard={dashboard} onOpenEvidence={onOpenEvidence} />
      <SnapshotHistoryPanel
        dashboard={dashboard}
        onOpenSnapshotInstanceDashboard={onOpenSnapshotDashboard}
        selectedApplication={selectedApplication}
        selectedProject={selectedProject}
      />
      <CredentialLifecyclePanel selectedProject={selectedProject} />
    </div>
  );
}

function DashboardContext({ dashboard, selectedProject }: { dashboard: DashboardPresentation; selectedProject: ProjectPresentationItem }) {
  const baselineSignal = dashboard.readSemantics.baselineComparisonUsedForMvpDecision ? "baseline used" : "baseline not used";
  return (
    <section className="border border-neutral-900 bg-white">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-neutral-200 p-3">
        <div className="min-w-0">
          <SectionLabel icon={Activity}>Application Dashboard / Live</SectionLabel>
          <h1 className="mt-1 text-[22px] font-medium leading-tight text-neutral-950">{dashboard.application.name}</h1>
          <p className="mt-1 text-[12px] text-neutral-600">
            Server read model을 표시합니다. UI는 lifecycle state, endpoint priority, resource pattern을 재계산하지 않습니다.
          </p>
        </div>
        <div className="flex max-w-full flex-wrap gap-1.5">
          <StatusBadge className={statusBadgeClassName("live")}>mode={dashboard.mode}</StatusBadge>
          <StatusBadge className={statusBadgeClassName("info")}>{dashboard.window.type}</StatusBadge>
          <StatusBadge>{dashboard.readSemantics.source}</StatusBadge>
          <StatusBadge>{baselineSignal}</StatusBadge>
        </div>
      </div>
      <div className="p-3">
        <div className="grid grid-cols-1 gap-2 text-[11px] text-neutral-600 md:grid-cols-2 lg:grid-cols-4">
          <InfoCell label="mode" value={dashboard.mode} />
          <InfoCell label="window" value={`${dashboard.window.type} · ${dashboard.canonicalWindowDisplay}`} />
          <InfoCell label="source" value={dashboard.readSemantics.source} />
          <InfoCell label="baseline" value={baselineSignal} />
          <InfoCell label="project / application" value={`${selectedProject.name} / ${dashboard.application.name}`} />
          <InfoCell label="environment" value={dashboard.application.environment} />
          <InfoCell label="generated" value={dashboard.generatedAtDisplay} />
          <InfoCell label="bucket boundary" value={dashboard.bucketEndBoundaryDisplay} />
        </div>
      </div>
    </section>
  );
}

function DataQualityFreshnessStrip({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="border border-neutral-200 bg-white p-3">
      <div className="flex items-center justify-between gap-3">
        <SectionLabel icon={Activity}>Data quality / freshness</SectionLabel>
        <StatusBadge className={statusBadgeClassName(dashboard.dataQuality.state)}>
          {humanizeStatusCode(dashboard.dataQuality.state)}
        </StatusBadge>
      </div>
      <div className="mt-3 grid grid-cols-1 gap-2 text-[11px] text-neutral-600 sm:grid-cols-2 lg:grid-cols-4">
        <InfoCell label="판단 요청 수" value={`${formatCount(dashboard.dataQuality.requestCount)} / 최소 ${formatCount(dashboard.dataQuality.minimumRequestCount)}`} />
        <InfoCell label="마지막 관측" value={dashboard.dataQualityLastObservedDisplay} />
        <InfoCell label="마지막 수집 구간" value={dashboard.lastAcceptedBucketDisplay} />
        <InfoCell label="baseline" value="baseline not used" />
        <InfoCell label="stale 기준" value={formatOptionalDateTime(dashboard.application.freshness.staleAt)} />
        <InfoCell label="down 기준" value={formatOptionalDateTime(dashboard.application.freshness.downAt)} />
        <InfoCell label="histogram percentile" value={dashboard.readSemantics.histogramBucketsUsedForPercentiles ? "사용" : "사용 안 함"} />
        <InfoCell label="분포 source" value={dashboard.readSemanticsBucketSourceDisplay} />
      </div>
      <LimitationList limitations={dashboard.dataQuality.limitations} />
    </div>
  );
}

function LimitationList({ limitations }: { limitations: string[] }) {
  if (limitations.length === 0) {
    return <div className="mt-3 text-[12px] text-neutral-500">서버가 별도 data quality limitation을 제공하지 않았습니다.</div>;
  }
  return (
    <ul className="mt-3 grid grid-cols-1 gap-2 text-[12px] text-neutral-600 md:grid-cols-2">
      {limitations.map((limitation) => (
        <li key={limitation} className="border-l-2 border-neutral-300 pl-2">
          {humanizeStatusCode(limitation)}
        </li>
      ))}
    </ul>
  );
}

function LifecycleStateHero({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className={`grid gap-3 border border-neutral-900 border-l-4 bg-white p-3 md:grid-cols-[minmax(180px,0.55fr)_minmax(0,1.4fr)_minmax(200px,0.8fr)] md:items-center ${stateStripAccentClassName(dashboard.state.code)}`}>
      <div>
        <div className="flex items-center gap-2">
          <SectionLabel icon={Gauge}>Lifecycle state</SectionLabel>
          <InlineHelp label="데이터 지연 기준 설명">
            <div className="space-y-1">
              <div>Lifecycle state는 server read model의 `state` field를 표시합니다.</div>
              <div>Heartbeat, histogram bucket, baseline compatibility field로 상태를 다시 계산하지 않습니다.</div>
            </div>
          </InlineHelp>
        </div>
        <StatusBadge className={`mt-2 ${dashboard.metricStateClassName}`}>{applicationStateDisplayText(dashboard.state.code)}</StatusBadge>
        <h2 className="mt-2 text-[16px] font-medium leading-tight text-neutral-950">{dashboard.operatorSummary.headline}</h2>
      </div>
      <p className="text-[13px] text-neutral-900">{applicationStateSummary(dashboard)}</p>
      <div className="text-[12px] text-neutral-600">
        <p>{dashboard.state.recommendedAction}</p>
        {dashboard.recovery.isRecovering && (
          <p className="mt-2 border border-neutral-300 bg-neutral-50 p-2">
            {dashboard.recovery.recommendedAction ?? "회복 여부를 확정하지 말고 다음 수집 데이터까지 관찰하세요."}
            {dashboard.recovery.retryAfterSeconds !== null && (
              <span className="ml-1 text-neutral-500">다음 판단 대기 {dashboard.recovery.retryAfterSeconds}s</span>
            )}
          </p>
        )}
      </div>
    </div>
  );
}

function stateStripAccentClassName(code: string): string {
  switch (code) {
    case "active":
      return "border-l-emerald-600";
    case "down":
      return "border-l-red-700";
    case "degraded":
    case "stale":
    case "waiting_first_data":
      return "border-l-amber-600";
    default:
      return "border-l-neutral-500";
  }
}

function DirectStateReasonsPanel({ reasons }: { reasons: DashboardStateReason[] }) {
  return (
    <EvidenceListPanel
      emptyText="서버가 별도 state-changing reason을 제공하지 않았습니다."
      icon={AlertCircle}
      items={reasons}
      title="Direct state reasons"
    />
  );
}

function AttentionAndFirstLookPanel({
  evidence,
  firstLookCandidates,
}: {
  evidence: DashboardAttentionEvidence[];
  firstLookCandidates: DashboardFirstLookCandidate[];
}) {
  return (
    <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
      <EvidenceListPanel
        emptyText="서버가 별도 attention evidence를 제공하지 않았습니다."
        icon={Search}
        items={evidence}
        title="Attention evidence"
      />
      <FirstLookCandidatesPanel candidates={firstLookCandidates} />
    </div>
  );
}

function EvidenceListPanel({
  emptyText,
  icon,
  items,
  title,
}: {
  emptyText: string;
  icon: LucideIcon;
  items: Array<DashboardStateReason | DashboardAttentionEvidence>;
  title: string;
}) {
  return (
    <div className="border border-neutral-200 bg-white">
      <div className="border-b border-neutral-200 px-3 py-2.5">
        <SectionLabel icon={icon}>{title}</SectionLabel>
      </div>
      {items.length === 0 ? (
        <div className="p-3 text-[12px] text-neutral-500">{emptyText}</div>
      ) : (
        <ul>
          {items.map((item) => (
            <li key={`${item.reasonCode}-${item.scope}-${item.target ?? "application"}`} className="border-b border-neutral-100 p-3 last:border-b-0">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-[13px] text-neutral-900">{item.operatorText}</div>
                  <div className="mt-1 text-[11px] text-neutral-500">
                    {item.reasonCode} · {humanizeStatusCode(item.scope)} · {item.target ?? "application"}
                  </div>
                </div>
                <StatusBadge className={severityBadgeClassName(item.severity)}>{severityDisplayText(item.severity)}</StatusBadge>
              </div>
              {"affectsLifecycleState" in item && (
                <div className="mt-2 text-[11px] text-neutral-500">
                  lifecycle state 반영: {item.affectsLifecycleState ? "예" : "아니오"}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function FirstLookCandidatesPanel({ candidates }: { candidates: DashboardFirstLookCandidate[] }) {
  return (
    <div className="border border-neutral-200 bg-white">
      <div className="flex items-center justify-between border-b border-neutral-200 px-3 py-2.5">
        <SectionLabel icon={ListChecks}>First look candidates</SectionLabel>
        <span className="text-[11px] text-neutral-500">server order</span>
      </div>
      {candidates.length === 0 ? (
        <div className="p-3 text-[12px] text-neutral-500">서버가 먼저 볼 후보를 제공하지 않았습니다.</div>
      ) : (
        <ol>
          {candidates.map((candidate) => (
            <li key={`${candidate.rank}-${candidate.type}-${candidate.target ?? "none"}`} className="border-b border-neutral-100 p-3 last:border-b-0">
              <div className="flex items-start gap-3">
                <span className="text-[12px] tabular-nums text-neutral-400">{String(candidate.rank).padStart(2, "0")}</span>
                <div>
                  <div className="text-[13px] text-neutral-900">{candidate.operatorText}</div>
                  <div className="mt-1 text-[11px] text-neutral-500">
                    {humanizeStatusCode(candidate.type)} · {candidate.target ?? "target 없음"} · {humanizeSourceCode(candidate.source)}
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

function StarterConnectionStrip({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="border border-neutral-300 bg-white p-3">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <SectionLabel icon={Radio}>Starter control-plane</SectionLabel>
          <InlineHelp label="상태 판단 영향 설명">
            <div className="space-y-1">
              <div>마지막 연결 신호는 앱이 마지막으로 살아 있다고 알려온 시각입니다.</div>
              <div>{starterStateImpactText(dashboard.starterConnection.stateImpact)}</div>
            </div>
          </InlineHelp>
        </div>
        <StatusBadge className={statusBadgeClassName(dashboard.starterConnection.lastHeartbeatStatus)}>
          {statusDisplayText(dashboard.starterConnection.lastHeartbeatStatus)}
        </StatusBadge>
      </div>
      <div className="mt-2 text-[13px] text-neutral-900">Control-plane only</div>
      <div className="mt-1 text-[12px] text-neutral-600">
        heartbeat는 accepted bucket freshness나 application lifecycle state를 직접 만들지 않습니다.
      </div>
      <div className="mt-2 grid grid-cols-1 gap-2 text-[12px] sm:grid-cols-2">
        <InfoCell label="마지막 연결 확인" value={dashboard.starterLastHeartbeatDisplay} />
        <InfoCell label="연결 의미" value={humanizeStatusCode(dashboard.starterConnection.connectionMeaning)} />
        <InfoCell label="state impact" value={starterStateImpactText(dashboard.starterConnection.stateImpact)} />
        <InfoCell label="source" value={humanizeSourceCode(dashboard.starterConnection.statusSource)} />
      </div>
    </div>
  );
}

function EndpointResourceEvidencePanel({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="space-y-4">
      <EndpointPriorityPanel items={dashboard.endpointPriority} />
      <ResourceSignalsPanel dashboard={dashboard} />
    </div>
  );
}

function ResourceSignalsPanel({ dashboard }: { dashboard: DashboardPresentation }) {
  const resources = [
    ["datasource_pool", "DB pool", dashboard.signals.use.datasourcePoolUsage],
    ["cpu", "CPU", dashboard.signals.use.cpuUsage],
    ["heap", "Heap", dashboard.signals.use.heapUsage],
  ] as const;

  return (
    <div className="border border-neutral-200 bg-white">
      <div className="px-4 py-3 border-b border-neutral-200">
        <SectionLabel icon={Activity}>Resource evidence</SectionLabel>
        <p className="mt-1 text-[12px] text-neutral-500">root cause 확정이 아니라 server read model의 USE hint를 표시합니다.</p>
      </div>
      <div className="grid grid-cols-1 gap-3 p-4 md:grid-cols-3">
        {resources.map(([key, label, signal]) => (
          <ResourceSignalCard key={key} label={label} signal={signal} />
        ))}
      </div>
    </div>
  );
}

function ResourceSignalCard({ label, signal }: { label: string; signal: DashboardResourceSignal }) {
  const value = formatNullableRatio(signal.max);
  return (
    <div className="border border-neutral-200 p-3">
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="text-[13px] text-neutral-900">{label}</div>
          <div className="mt-1 text-[11px] text-neutral-500">threshold {formatRatio(signal.threshold)}</div>
        </div>
        <StatusBadge className={statusBadgeClassName(signal.status)}>{humanizeStatusCode(signal.status)}</StatusBadge>
      </div>
      <div className="mt-3 text-[20px] leading-none text-neutral-900">{value}</div>
      <div className="mt-2 text-[11px] text-neutral-500">observed {formatOptionalDateTime(signal.observedAt)}</div>
    </div>
  );
}

function MetricDetailSection({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="space-y-4">
      <div className="border border-neutral-200 bg-white">
        <div className="px-4 py-3 border-b border-neutral-200">
          <SectionLabel icon={Gauge}>Metric detail</SectionLabel>
          <p className="mt-1 text-[12px] text-neutral-500">
            request/error/slow share와 source-scoped starter percentile, bucket distribution을 표시합니다.
          </p>
        </div>
        <GoldenSignalsGrid dashboard={dashboard} />
      </div>
      <SourceScopedPercentilesPanel dashboard={dashboard} />
      <HistogramPanel dashboard={dashboard} />
    </div>
  );
}

function GoldenSignalsGrid({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="grid grid-cols-1 gap-0 bg-white md:grid-cols-4">
      <MetricCell label="RED Rate" note="최근 30분 요청량" value={formatCount(dashboard.signals.red.requestCount)} />
      <MetricCell label="RED Errors" note={dashboard.signals.red.errorSemantic} value={formatRatio(dashboard.signals.red.errorRate)} />
      <MetricCell label="RED Duration" note="500ms 초과 요청 비율" value={formatNullableRatio(dashboard.signals.red.slowShareOver500ms)} />
      <MetricCell
        label="USE Hint"
        note="DB pool window max"
        value={formatNullableRatio(dashboard.signals.use.datasourcePoolUsage.max)}
        last
      />
    </div>
  );
}

function SourceScopedPercentilesPanel({ dashboard }: { dashboard: DashboardPresentation }) {
  const source = dashboard.sourceScopedPercentiles;
  return (
    <div className="border border-neutral-200 bg-white">
      <div className="px-4 py-3 border-b border-neutral-200 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <SectionLabel icon={Gauge}>Source-scoped p95 / p99</SectionLabel>
          <InlineHelp label="응답 시간 지표 설명">
            <div className="space-y-1">
              <div>Starter canonical percentile source와 instance bucket scope만 표시합니다.</div>
              <div>여러 instance 값을 평균, 최댓값, 병합하거나 histogram bucket에서 다시 계산하지 않습니다.</div>
            </div>
          </InlineHelp>
        </div>
        <StatusBadge className={statusBadgeClassName(source.status)}>{statusDisplayText(source.status)}</StatusBadge>
      </div>
      <div className="grid grid-cols-1 gap-2 border-b border-neutral-100 px-4 py-3 text-[11px] text-neutral-500 md:grid-cols-3">
        <InfoCell label="source" value={humanizeSourceCode(source.source)} />
        <InfoCell label="scope" value={humanizeStatusCode(source.scope)} />
        <InfoCell label="policy" value={humanizeStatusCode(source.aggregatePolicy)} />
      </div>
      {source.items.length === 0 ? (
        <div className="p-4 text-[12px] text-neutral-600">
          {sourceScopedEmptyText(source.status, source.reason ? humanizeStatusCode(source.reason) : dashboard.sourceScopedReasonDisplay)}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-[720px] w-full text-[12px]">
            <thead>
              <tr className="text-left text-neutral-500">
                <th className="px-4 py-2">인스턴스</th>
                <th className="px-4 py-2">요청 수</th>
                <th className="px-4 py-2">
                  <span className="inline-flex items-center gap-1">
                    p95 응답시간
                    <InlineHelp label="p95 응답시간 설명">요청 100개 중 95개가 이 시간 안에 끝났다는 뜻입니다.</InlineHelp>
                  </span>
                </th>
                <th className="px-4 py-2">
                  <span className="inline-flex items-center gap-1">
                    p99 응답시간
                    <InlineHelp label="p99 응답시간 설명">매우 느린 일부 요청까지 포함해 tail latency를 보는 기준입니다.</InlineHelp>
                  </span>
                </th>
                <th className="px-4 py-2">측정 구간</th>
              </tr>
            </thead>
            <tbody>
              {source.items.map((item) => (
                <tr key={`${item.instance}-${item.bucketEndUtc}`} className="border-t border-neutral-100">
                  <td className="px-4 py-2 text-neutral-700">{item.instance}</td>
                  <td className="px-4 py-2 text-neutral-700">{formatCount(item.requestCount)}</td>
                  <td className="px-4 py-2 text-neutral-900">{item.p95Ms} ms</td>
                  <td className="px-4 py-2 text-neutral-900">{item.p99Ms} ms</td>
                  <td className="px-4 py-2 text-neutral-500">{formatDateRange(item.bucketStartUtc, item.bucketEndUtc)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function HistogramPanel({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="border border-neutral-200 bg-white">
      <div className="px-4 py-3 border-b border-neutral-200 flex items-center gap-2">
        <SectionLabel icon={Activity}>Bucket distribution</SectionLabel>
        <InlineHelp label="응답 시간 구간 설명">
          accepted bucket 분포를 표시 전용으로 변환합니다. p95/p99나 평균/최댓값 latency를 만들지 않습니다.
        </InlineHelp>
      </div>
      <div className="grid grid-cols-1 gap-2 border-b border-neutral-100 px-4 py-3 text-[11px] text-neutral-500 md:grid-cols-3">
        <InfoCell label="source" value={humanizeSourceCode(dashboard.histogramDistribution.source)} />
        <InfoCell label="display policy" value={humanizeStatusCode(dashboard.histogramDistribution.displayPolicy)} />
        <InfoCell label="percentile source" value={dashboard.readSemantics.histogramBucketsUsedForPercentiles ? "사용" : "사용 안 함"} />
      </div>
      <HistogramWindowCard label="최근 30분 분포" window={dashboard.histogramDistribution.current} />
    </div>
  );
}

function HistogramWindowCard({ label, window }: { label: string; window: HistogramWindow }) {
  const buckets = toDisplayLatencyBuckets(window.buckets);
  return (
    <div className="p-4">
      <div className="flex items-center justify-between gap-2">
        <div className="text-[11px] uppercase text-neutral-500">{label}</div>
        <StatusBadge className={statusBadgeClassName(window.status)}>{statusDisplayText(window.status)}</StatusBadge>
      </div>
      <div className="mt-1 text-[11px] text-neutral-500">총 요청 수 {formatCount(window.totalCount)}</div>
      {buckets.length === 0 ? (
        <div className="mt-3 text-[12px] text-neutral-500">응답 시간 구간 데이터가 아직 없습니다.</div>
      ) : (
        <div className="mt-3 space-y-1.5">
          {buckets.map((bucket) => (
            <div key={bucket.key} className="flex items-center gap-2 text-[11px]">
              <span className="w-44 text-neutral-500 tabular-nums">{bucket.label}</span>
              <div className="h-3 flex-1 border border-neutral-200 bg-neutral-100">
                <div className="h-full bg-neutral-800" style={{ width: histogramRangeBarWidth(bucket.count, buckets) }} />
              </div>
              <span className="w-16 text-right text-neutral-700 tabular-nums">{formatCount(bucket.count)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function TriagePanel({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="border border-neutral-200 bg-white">
      <div className="px-4 py-3 border-b border-neutral-200">
        <SectionLabel icon={AlertCircle}>먼저 볼 문제</SectionLabel>
      </div>
      {dashboard.triageCards.length === 0 ? (
        <ZeroInsightBlock dashboard={dashboard} />
      ) : (
        <ul>
          {dashboard.triageCards.map((card) => (
            <TriageCardItem card={card} key={card.ruleId} />
          ))}
        </ul>
      )}
    </div>
  );
}

function ZeroInsightBlock({ dashboard }: { dashboard: DashboardPresentation }) {
  if (!dashboard.zeroInsight) {
    return <div className="p-4 text-[12px] text-neutral-600">현재 화면에서 보여줄 추가 판단 근거가 없습니다.</div>;
  }
  return (
    <div className="p-4 text-[12px] text-neutral-700">
      <div className="text-neutral-900">{dashboard.zeroInsight.message}</div>
      <div className="mt-1">{dashboard.zeroInsight.recommendedAction}</div>
      <div className="mt-2 text-[11px] text-neutral-500">{humanizeStatusCode(dashboard.zeroInsight.reasonCode)}</div>
    </div>
  );
}

function TriageCardItem({ card }: { card: TriageCard }) {
  return (
    <li className="px-4 py-3 border-b border-neutral-100 last:border-b-0">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-[13px] text-neutral-900">{card.title}</div>
          <div className="mt-0.5 text-[12px] text-neutral-700">{card.summary}</div>
          <div className="mt-1 text-[12px] text-neutral-600">{card.recommendation}</div>
        </div>
        <StatusBadge className={severityBadgeClassName(card.severity)}>{severityDisplayText(card.severity)}</StatusBadge>
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500 md:grid-cols-4">
        <InfoCell label="판단 기준" value={card.ruleId || "적용된 판단 기준 없음"} />
        <InfoCell label="우선순위 점수" value={String(card.score)} />
        <InfoCell label="신뢰도" value={formatNullableRatio(card.confidence)} />
        <InfoCell label="엔드포인트" value={card.affectedEndpoint ?? "해당 엔드포인트 없음"} />
        <InfoCell label="요청 수" value={formatOptionalNumber(card.evidence.requestCount)} />
        <InfoCell label="오류 수" value={formatOptionalNumber(card.evidence.currentErrorCount)} />
        <InfoCell label="오류율" value={formatNullableRatio(card.evidence.currentErrorRate)} />
        <InfoCell label="데이터 상태" value={card.evidence.freshnessStatusReason ? humanizeStatusCode(card.evidence.freshnessStatusReason) : "확인할 수 없음"} />
      </div>
    </li>
  );
}

function EndpointPriorityPanel({ items }: { items: EndpointPriorityItem[] }) {
  return (
    <div className="border border-neutral-200 bg-white">
      <div className="px-4 py-3 border-b border-neutral-200 flex items-center justify-between">
        <div>
          <SectionLabel icon={ListChecks}>Endpoint evidence</SectionLabel>
          <p className="mt-1 text-[12px] text-neutral-500">server-provided order와 rank를 그대로 표시합니다.</p>
        </div>
        <span className="text-[11px] text-neutral-500">server order</span>
      </div>
      {items.length === 0 ? (
        <div className="p-4 text-[12px] text-neutral-500">서버가 제공한 endpoint evidence 후보가 없습니다.</div>
      ) : (
        <ol className="grid gap-2 p-4">
          {items.map((item) => (
            <EndpointPriorityRow item={item} key={`${item.rank}-${item.endpointKey}`} />
          ))}
        </ol>
      )}
    </div>
  );
}

function EndpointPriorityRow({ item }: { item: EndpointPriorityItem }) {
  return (
    <li className="border border-neutral-200 p-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-3">
            <span className="grid h-6 w-6 place-items-center bg-neutral-900 text-[11px] text-white tabular-nums">{item.rank}</span>
            <span className="truncate text-[13px] text-neutral-900">{item.endpointKey}</span>
          </div>
          <div className="mt-1 text-[12px] text-neutral-700">{item.recommendedAction}</div>
          <div className="mt-1 text-[11px] text-neutral-500">{item.reason} · {item.ruleIds.join(", ")}</div>
        </div>
        <StatusBadge className={statusBadgeClassName(item.freshness.status)}>{humanizeStatusCode(item.freshness.status)}</StatusBadge>
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500 md:grid-cols-4">
        <InfoCell label="요청 수" value={formatCount(item.evidence.requestCount)} />
        <InfoCell label="오류 수" value={formatCount(item.evidence.errorCount)} />
        <InfoCell label="오류율" value={formatRatio(item.evidence.errorRate)} />
        <InfoCell label="분포 기준" value={humanizeSourceCode(item.evidence.bucketDistributionSource)} />
        <InfoCell label="마지막 관측" value={formatOptionalDateTime(item.freshness.lastObservedAt)} />
        <InfoCell label="관측 구간" value={humanizeStatusCode(item.freshness.sourceWindow)} />
        <InfoCell label="오류 근거" value={humanizeStatusCode(item.evidence.errorEvidenceStatus)} />
        <InfoCell label="응답시간 근거" value={humanizeStatusCode(item.evidence.latencyEvidenceStatus)} />
      </div>
    </li>
  );
}

function ProjectRegistrationPanel({ onRegistered }: { onRegistered: (projectId: string) => void }) {
  const { authFetch } = useAuth();
  const [open, setOpen] = useState(false);
  const [projectName, setProjectName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [oneTimeCredential, setOneTimeCredential] = useState<OneTimeStarterCredential | null>(null);
  const [copyStatus, setCopyStatus] = useState<string | null>(null);
  const mountedRef = useRef(true);
  const openRef = useRef(false);
  const registrationSequenceRef = useRef(0);

  useEffect(() => {
    openRef.current = open;
  }, [open]);

  useEffect(() => {
    return () => {
      mountedRef.current = false;
      registrationSequenceRef.current += 1;
    };
  }, []);

  const clearRegistrationCredential = useCallback((message: string | null = null) => {
    setOneTimeCredential(null);
    setCopyStatus(message);
  }, []);

  const toggleRegistrationPanel = useCallback(() => {
    if (open) {
      registrationSequenceRef.current += 1;
      setLoading(false);
      clearRegistrationCredential(null);
    }
    setOpen((current) => !current);
  }, [clearRegistrationCredential, open]);

  const submitRegistration = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      clearRegistrationCredential(null);
      const name = projectName.trim();
      if (!name) {
        setError(new ApiRequestError("project_name_required", 400));
        return;
      }

      const requestSequence = registrationSequenceRef.current + 1;
      registrationSequenceRef.current = requestSequence;
      setLoading(true);
      setError(null);
      try {
        const response = await authFetch(READ_MODEL_ENDPOINTS.projects, {
          ...SECRET_BEARING_REQUEST_OPTIONS,
          method: "POST",
          headers: JSON_BODY_HEADERS,
          body: JSON.stringify({ name }),
        });
        const model = await readJsonResource<ProjectRegistrationResponse>(response);
        if (!isRegistrationRequestCurrent(requestSequence, registrationSequenceRef, mountedRef, openRef)) {
          return;
        }
        setOneTimeCredential(model.starterCredential);
        setProjectName("");
        onRegistered(model.project.projectId);
      } catch (caught) {
        if (!isRegistrationRequestCurrent(requestSequence, registrationSequenceRef, mountedRef, openRef)) {
          return;
        }
        setError(caught instanceof Error ? caught : new ApiRequestError("project_registration_failed"));
      } finally {
        if (isRegistrationRequestCurrent(requestSequence, registrationSequenceRef, mountedRef, openRef)) {
          setLoading(false);
        }
      }
    },
    [authFetch, clearRegistrationCredential, onRegistered, projectName],
  );

  return (
    <div className="border border-neutral-200">
      <button
        className="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left text-[12px] text-neutral-800 hover:bg-neutral-50"
        onClick={toggleRegistrationPanel}
      >
        <span className="flex items-center gap-1.5">
          <KeyRound className="h-3.5 w-3.5" strokeWidth={1.5} /> Project 등록
        </span>
        <span className="text-[10px] uppercase text-neutral-500">{open ? "close" : "open"}</span>
      </button>
      {open && (
        <div className="border-t border-neutral-200 p-3">
          <form className="space-y-2" onSubmit={submitRegistration}>
            <Input
              value={projectName}
              onChange={(event) => setProjectName(event.target.value)}
              placeholder="Project name"
              className="h-8 border-neutral-300"
            />
            {error && <div className="text-[11px] text-rose-700">{registrationErrorCopy(error)}</div>}
            {copyStatus && <div className="text-[11px] text-neutral-600">{copyStatus}</div>}
            <Button type="submit" size="sm" className="w-full gap-2" disabled={loading}>
              {loading ? (
                <RefreshCw className="h-3.5 w-3.5 animate-spin" strokeWidth={1.5} />
              ) : (
                <Check className="h-3.5 w-3.5" strokeWidth={1.5} />
              )}
              등록
            </Button>
          </form>
          {oneTimeCredential && (
            <OneTimeCredentialPanel
              credential={oneTimeCredential}
              onCleared={(message) => {
                clearRegistrationCredential(message);
              }}
            />
          )}
        </div>
      )}
    </div>
  );
}

function CredentialLifecyclePanel({ selectedProject }: { selectedProject: ProjectPresentationItem | null }) {
  const { authFetch, authGeneration } = useAuth();
  const [operationError, setOperationError] = useState<Error | null>(null);
  const [operationLoading, setOperationLoading] = useState<"revoke" | "rotate" | null>(null);
  const [oneTimeCredential, setOneTimeCredential] = useState<OneTimeStarterCredential | null>(null);
  const [copyStatus, setCopyStatus] = useState<string | null>(null);
  const [revokeConfirm, setRevokeConfirm] = useState(false);
  const projectId = selectedProject?.projectId ?? null;
  const credentialResourceKey = projectId ? `credential:${projectId}` : "credential:none";
  const mountedRef = useRef(true);
  const credentialMutationSequenceRef = useRef(0);
  const currentCredentialScopeRef = useRef({ authGeneration, projectId });
  currentCredentialScopeRef.current = { authGeneration, projectId };

  useEffect(() => {
    currentCredentialScopeRef.current = { authGeneration, projectId };
  }, [authGeneration, projectId]);

  useEffect(() => {
    return () => {
      mountedRef.current = false;
      credentialMutationSequenceRef.current += 1;
    };
  }, []);

  const requestCredential = useCallback(
    async ({ authFetch, signal }: { authFetch: AuthFetch; signal: AbortSignal }) => {
      if (!projectId) {
        throw new ApiRequestError("project_not_selected");
      }
      const response = await authFetch(buildStarterCredentialMetadataPath(projectId), {
        ...CREDENTIAL_LIFECYCLE_REQUEST_OPTIONS,
        signal,
      });
      const model = await readJsonResource<StarterCredentialMetadataResponse>(response);
      if (model.projectId !== projectId) {
        throw new ApiRequestError("credential_context_mismatch");
      }
      return model;
    },
    [projectId],
  );

  const credentialResource = useApiResource<StarterCredentialMetadataResponse>({
    dependencies: [credentialResourceKey],
    enabled: Boolean(projectId),
    request: requestCredential,
    resourceKey: credentialResourceKey,
  });

  const credentialCurrent = credentialResource.resourceKey === credentialResourceKey;
  const credential = credentialCurrent ? credentialResource.data?.starterCredential ?? null : null;
  const credentialError = credentialCurrent ? credentialResource.error : null;
  const credentialLoading = Boolean(projectId) && (!credentialCurrent || credentialResource.loading);

  useEffect(() => {
    credentialMutationSequenceRef.current += 1;
    setOperationError(null);
    setOperationLoading(null);
    setOneTimeCredential(null);
    setCopyStatus(null);
    setRevokeConfirm(false);
  }, [authGeneration, projectId]);

  const rotateCredential = useCallback(async () => {
    if (!projectId) {
      return;
    }
    const requestProjectId = projectId;
    const requestAuthGeneration = authGeneration;
    const requestSequence = credentialMutationSequenceRef.current + 1;
    credentialMutationSequenceRef.current = requestSequence;
    setOperationLoading("rotate");
    setOperationError(null);
    setCopyStatus(null);
    setOneTimeCredential(null);
    try {
      const response = await authFetch(buildStarterCredentialRotationPath(requestProjectId), {
        ...CREDENTIAL_LIFECYCLE_REQUEST_OPTIONS,
        method: "POST",
      });
      const model = await readJsonResource<StarterCredentialRotationResponse>(response);
      if (model.projectId !== requestProjectId) {
        throw new ApiRequestError("credential_rotation_context_mismatch");
      }
      if (!isCredentialMutationCurrent(requestSequence, requestProjectId, requestAuthGeneration, credentialMutationSequenceRef, currentCredentialScopeRef, mountedRef)) {
        return;
      }
      setOneTimeCredential(model.starterCredential);
      credentialResource.reload();
    } catch (caught) {
      if (!isCredentialMutationCurrent(requestSequence, requestProjectId, requestAuthGeneration, credentialMutationSequenceRef, currentCredentialScopeRef, mountedRef)) {
        return;
      }
      setOperationError(caught instanceof Error ? caught : new ApiRequestError("credential_rotation_failed"));
    } finally {
      if (isCredentialMutationCurrent(requestSequence, requestProjectId, requestAuthGeneration, credentialMutationSequenceRef, currentCredentialScopeRef, mountedRef)) {
        setOperationLoading(null);
      }
    }
  }, [authFetch, authGeneration, credentialResource, projectId]);

  const revokeCredential = useCallback(async () => {
    if (!projectId) {
      return;
    }
    if (!revokeConfirm) {
      setRevokeConfirm(true);
      return;
    }
    const requestProjectId = projectId;
    const requestAuthGeneration = authGeneration;
    const requestSequence = credentialMutationSequenceRef.current + 1;
    credentialMutationSequenceRef.current = requestSequence;
    setOperationLoading("revoke");
    setOperationError(null);
    setCopyStatus(null);
    setOneTimeCredential(null);
    try {
      const response = await authFetch(buildStarterCredentialRevocationPath(requestProjectId), {
        ...CREDENTIAL_LIFECYCLE_REQUEST_OPTIONS,
        method: "POST",
      });
      const model = await readJsonResource<StarterCredentialMetadataResponse>(response);
      if (model.projectId !== requestProjectId) {
        throw new ApiRequestError("credential_revocation_context_mismatch");
      }
      if (!isCredentialMutationCurrent(requestSequence, requestProjectId, requestAuthGeneration, credentialMutationSequenceRef, currentCredentialScopeRef, mountedRef)) {
        return;
      }
      setRevokeConfirm(false);
      credentialResource.reload();
    } catch (caught) {
      if (!isCredentialMutationCurrent(requestSequence, requestProjectId, requestAuthGeneration, credentialMutationSequenceRef, currentCredentialScopeRef, mountedRef)) {
        return;
      }
      setOperationError(caught instanceof Error ? caught : new ApiRequestError("credential_revocation_failed"));
    } finally {
      if (isCredentialMutationCurrent(requestSequence, requestProjectId, requestAuthGeneration, credentialMutationSequenceRef, currentCredentialScopeRef, mountedRef)) {
        setOperationLoading(null);
      }
    }
  }, [authFetch, authGeneration, credentialResource, projectId, revokeConfirm]);

  if (!selectedProject) {
    return (
      <div className="border border-neutral-200">
        <div className="px-3 py-2.5 border-b border-neutral-200">
          <SectionLabel icon={KeyRound}>Starter 연결 키</SectionLabel>
        </div>
        <div className="p-3 text-[12px] text-neutral-500">Project를 선택하면 데이터 수집 키 상태를 확인합니다.</div>
      </div>
    );
  }

  return (
    <div className="border border-neutral-200">
      <div className="px-3 py-2.5 border-b border-neutral-200 flex items-center justify-between">
        <SectionLabel icon={KeyRound}>Starter 연결 키</SectionLabel>
        <StatusBadge className={credential ? statusBadgeClassName(credential.status) : ""}>
          {credential ? statusDisplayText(credential.status) : "불러오는 중"}
        </StatusBadge>
      </div>
      <div className="p-3 text-[12px] text-neutral-600">
        {credentialLoading && "연결 키 상태를 불러오는 중입니다."}
        {credentialError && credentialErrorCopy(credentialError)}
        {!credentialLoading && !credentialError && credential && (
          <div className="space-y-2">
            <div className="grid grid-cols-2 gap-2 text-[11px]">
              <InfoCell label="키 앞부분" value={credential.keyPrefix} />
              <InfoCell label="상태" value={statusDisplayText(credential.status)} />
              <InfoCell label="발급일" value={formatOptionalDateTime(credential.issuedAt)} />
              <InfoCell label="마지막 교체" value={formatOptionalDateTime(credential.rotatedAt)} />
              <InfoCell label="사용 중지일" value={formatOptionalDateTime(credential.revokedAt)} />
            </div>
            <div className="text-[11px] text-neutral-500">보안을 위해 전체 키 값은 생성 직후 한 번만 표시됩니다.</div>
          </div>
        )}
        {operationError && <div className="mt-2 text-[11px] text-rose-700">{credentialErrorCopy(operationError)}</div>}
        {copyStatus && <div className="mt-2 text-[11px] text-neutral-600">{copyStatus}</div>}
        {oneTimeCredential && (
          <OneTimeCredentialPanel
            credential={oneTimeCredential}
            onCleared={(message) => {
              setOneTimeCredential(null);
              setCopyStatus(message);
            }}
          />
        )}
      </div>
      <div className="px-3 py-2 border-t border-neutral-200 flex gap-2">
        <Button
          variant="outline"
          size="sm"
          className="flex-1 border-sky-200 bg-sky-50 text-sky-800 hover:bg-sky-100 hover:text-sky-900"
          disabled={operationLoading !== null}
          onClick={rotateCredential}
        >
          {operationLoading === "rotate" ? "새 키 발급 중" : "새 키 발급"}
        </Button>
        <Button
          variant="outline"
          size="sm"
          className="flex-1 border-rose-200 bg-rose-50 text-rose-800 hover:bg-rose-100 hover:text-rose-900"
          disabled={operationLoading !== null}
          onClick={revokeCredential}
        >
          {operationLoading === "revoke" ? "사용 중지 중" : revokeConfirm ? "사용 중지 확인" : "키 사용 중지"}
        </Button>
      </div>
    </div>
  );
}

function OneTimeCredentialPanel({
  credential,
  onCleared,
}: {
  credential: OneTimeStarterCredential;
  onCleared: (message: string) => void;
}) {
  const copyAndClear = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(credential.displayValue);
      onCleared("연결 키를 클립보드에 복사했고 화면에서 숨겼습니다.");
    } catch {
      onCleared("클립보드 복사에는 실패했지만 전체 키 값은 화면에서 숨겼습니다.");
    }
  }, [credential.displayValue, onCleared]);

  return (
    <div className="mt-3 border border-neutral-900 bg-neutral-50 p-3 text-[12px]">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-neutral-900">Starter 연결 키 1회 표시</div>
          <div className="mt-1 text-[11px] text-neutral-600">
            보안을 위해 전체 키 값은 생성 직후 한 번만 표시됩니다. 필요하면 새 키를 발급받아야 합니다.
          </div>
        </div>
        <StatusBadge>{credential.visibleOnce ? "한 번만 표시" : "일회 표시"}</StatusBadge>
      </div>
      <code className="mt-2 block break-all border border-neutral-300 bg-white p-2 text-[11px] text-neutral-900">
        {credential.displayValue}
      </code>
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px]">
        <InfoCell label="키 앞부분" value={credential.keyPrefix} />
        <InfoCell label="발급일" value={formatOptionalDateTime(credential.issuedAt)} />
      </div>
      <div className="mt-3 flex gap-2">
        <Button type="button" variant="outline" size="sm" className="gap-2 border-neutral-300" onClick={copyAndClear}>
          <Copy className="h-3.5 w-3.5" strokeWidth={1.5} /> 복사 후 숨기기
        </Button>
        <Button type="button" variant="outline" size="sm" className="gap-2 border-neutral-300" onClick={() => onCleared("연결 키 표시를 닫고 화면에서 숨겼습니다.")}>
          <X className="h-3.5 w-3.5" strokeWidth={1.5} /> 닫기
        </Button>
      </div>
    </div>
  );
}

function registrationErrorCopy(error: Error): string {
  if (error instanceof AuthRequiredError) {
    return error.message;
  }
  if (error instanceof ApiRequestError && error.status === 400) {
    return "Project name이 유효하지 않습니다. non-secret 이름만 다시 입력해 주세요.";
  }
  if (error instanceof ApiRequestError && error.status === 409) {
    return "동일하거나 정규화 후 충돌하는 project name이 있을 수 있습니다.";
  }
  if (error instanceof ApiRequestError && error.status === 401) {
    return "인증이 만료되었습니다. 다시 GitHub 로그인 후 시도해 주세요.";
  }
  return "Project registration에 실패했습니다. 민감한 인증 정보는 화면에 표시하지 않습니다.";
}

function credentialErrorCopy(error: Error): string {
  if (error instanceof AuthRequiredError) {
    return error.message;
  }
  if (error instanceof ApiRequestError && error.status === 404) {
    return "Project 권한이나 범위가 맞지 않을 수 있습니다. 키 폐기나 project 없음으로 단정하지 않습니다.";
  }
  if (error instanceof ApiRequestError && error.status === 401) {
    return "인증이 만료되었습니다. 다시 GitHub 로그인 후 시도해 주세요.";
  }
  return "연결 키 상태 요청에 실패했습니다. 전체 키 값, 해시, 토큰은 표시하지 않습니다.";
}

function InstancesPanel({
  dashboard,
  onOpenEvidence,
}: {
  dashboard: DashboardPresentation;
  onOpenEvidence: ReturnType<typeof useInstanceView>["openEvidence"];
}) {
  return (
    <div className="border border-neutral-200 bg-white">
      <div className="border-b border-neutral-200 px-3 py-2.5">
        <SectionLabel icon={Server}>Instance summary</SectionLabel>
        <p className="mt-1 text-[12px] text-neutral-500">
          Application 판단을 대체하지 않고 selected instance evidence를 wide modal로 확인합니다.
        </p>
      </div>
      {dashboard.instances.length === 0 ? (
        <div className="p-3 text-[12px] text-neutral-500">아직 확인할 실행 인스턴스가 없습니다.</div>
      ) : (
        <ul className="grid gap-2 p-3">
          {dashboard.instances.map((instance) => {
            const target = {
              applicationId: dashboard.application.applicationId,
              evidenceLink: instance.links.evidence,
              instanceId: instance.instanceId,
              instanceName: instance.instanceName,
              projectId: dashboard.application.projectId,
            };
            return (
              <li key={instance.instanceId} className="grid gap-3 border border-neutral-200 p-3 md:grid-cols-[minmax(180px,1fr)_minmax(180px,1fr)_auto] md:items-center">
                <div>
                  <div className="text-[13px] text-neutral-900">{instance.instanceName}</div>
                  <div className="mt-0.5 text-[11px] text-neutral-500">마지막 관측 {formatOptionalDateTime(instance.lastSeenAt)}</div>
                </div>
                <div className="text-[12px] text-neutral-600">
                  같은 live window의 selected instance evidence만 확인합니다.
                </div>
                <div className="flex flex-wrap gap-2 text-[11px] text-neutral-700">
                  <Button variant="outline" size="sm" className="h-8 border-neutral-300" onClick={() => onOpenEvidence(target)}>
                    Open modal
                  </Button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function isRegistrationRequestCurrent(
  requestSequence: number,
  sequenceRef: React.MutableRefObject<number>,
  mountedRef: React.MutableRefObject<boolean>,
  openRef: React.MutableRefObject<boolean>,
): boolean {
  return mountedRef.current && openRef.current && sequenceRef.current === requestSequence;
}

function isCredentialMutationCurrent(
  requestSequence: number,
  requestProjectId: string,
  requestAuthGeneration: number,
  sequenceRef: React.MutableRefObject<number>,
  scopeRef: React.MutableRefObject<{ authGeneration: number; projectId: string | null }>,
  mountedRef: React.MutableRefObject<boolean>,
): boolean {
  return (
    mountedRef.current &&
    sequenceRef.current === requestSequence &&
    scopeRef.current.projectId === requestProjectId &&
    scopeRef.current.authGeneration === requestAuthGeneration
  );
}

function statusDisplayText(status: string | null | undefined): string {
  switch ((status ?? "").toLowerCase()) {
    case "active":
      return "사용 중";
    case "available":
      return "측정됨";
    case "current":
      return "현재";
    case "down_candidate":
      return "수집 끊김 후보";
    case "failed":
      return "실패";
    case "insufficient":
      return "표본 부족";
    case "insufficient_baseline":
      return "비교 기준 부족";
    case "loading":
      return "불러오는 중";
    case "missing":
      return "아직 없음";
    case "received":
      return "수신됨";
    case "recent":
      return "최근 수신";
    case "revoked":
      return "사용 중지";
    case "stale":
      return "데이터 지연";
    case "stale_candidate":
      return "데이터 지연 후보";
    case "unavailable":
      return "사용 불가";
    case "":
      return "해당 없음";
    default:
      return humanizeStatusCode(status);
  }
}

function applicationStateDisplayText(status: string | null | undefined): string {
  switch ((status ?? "").toLowerCase()) {
    case "active":
      return "정상";
    case "degraded":
      return "주의 필요";
    case "attention":
      return "확인 필요";
    case "down":
      return "수집 중단";
    case "idle":
      return "대기 중";
    case "stale":
      return "데이터 지연";
    case "waiting_first_data":
      return "첫 데이터 대기";
    case "unknown":
      return "확인 필요";
    default:
      return humanizeStatusCode(status);
  }
}

function applicationStateSummary(dashboard: DashboardPresentation): string {
  return dashboard.state.rationale || "서버가 제공한 lifecycle state rationale이 없습니다.";
}

function starterStateImpactText(stateImpact: string): string {
  if (stateImpact === "none" || stateImpact === "does_not_change_metric_state") {
    return "상태 판단 영향 없음: 앱 연결 신호가 현재 정상/주의/지연/끊김 판단을 직접 바꾸지 않습니다.";
  }
  if (stateImpact === "control_plane_only") {
    return "control-plane 참고 정보: metric state와 분리해 표시합니다.";
  }
  return `상태 판단 영향: ${humanizeStatusCode(stateImpact)}`;
}

function sourceScopedEmptyText(status: string, reason: string | null): string {
  if (status === "missing") {
    return "인스턴스별 응답 시간 데이터가 아직 없습니다.";
  }
  if (status === "insufficient") {
    return "인스턴스별 응답 시간 표본이 아직 충분하지 않습니다.";
  }
  return reason && reason !== "확인할 수 없음" ? reason : "인스턴스별 응답 시간 데이터를 확인할 수 없습니다.";
}

function formatLatencyThreshold(leMs: number): string {
  if (!Number.isFinite(leMs)) {
    return "응답 시간 기준 없음";
  }
  if (leMs >= 1000 && leMs % 1000 === 0) {
    return `${leMs / 1000}초 이하`;
  }
  return `${leMs}ms 이하`;
}

function MetricCell({
  label,
  last = false,
  note,
  value,
}: {
  label: string;
  last?: boolean;
  note?: string;
  value: string;
}) {
  return (
    <div className={`p-4 ${last ? "" : "border-b border-neutral-200 md:border-b-0 md:border-r"}`}>
      <div className="text-[11px] uppercase text-neutral-500">{label}</div>
      <div className="mt-1 text-[22px] leading-tight text-neutral-900">{value}</div>
      {note && <div className="mt-1 text-[12px] text-neutral-500">{note}</div>}
    </div>
  );
}

function InfoCell({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 border border-neutral-200 bg-white p-2">
      <div className="text-neutral-500">{label}</div>
      <div className="mt-0.5 break-words text-neutral-900">{value}</div>
    </div>
  );
}

function MainMessage({ body, title }: { body: string; title: string }) {
  return (
    <div className="p-10">
      <div className="border border-neutral-200 bg-white p-5 text-[13px]">
        <div className="text-neutral-900">{title}</div>
        <div className="mt-1 text-neutral-500">{body}</div>
      </div>
    </div>
  );
}

function ResourceMessage({ body, title }: { body: string; title: string }) {
  return (
    <div className="p-4 text-[12px]">
      <div className="text-neutral-900">{title}</div>
      <div className="mt-1 text-neutral-500">{body}</div>
    </div>
  );
}

function ResourceErrorMessage({
  error,
  onReload,
  roomy = false,
  scope,
}: {
  error: Error;
  onReload: () => void;
  roomy?: boolean;
  scope: ResourceScope;
}) {
  const copy = resourceErrorCopy(scope, error);
  const content = (
    <div className="border border-neutral-200 bg-white p-4 text-[12px]">
      <div className="text-neutral-900">{copy.title}</div>
      <div className="mt-1 text-neutral-500">{copy.body}</div>
      <Button variant="outline" size="sm" className="mt-3 gap-2 border-neutral-300" onClick={onReload}>
        <RefreshCw className="h-3.5 w-3.5" strokeWidth={1.5} /> 다시 시도
      </Button>
    </div>
  );
  return roomy ? <div className="p-10">{content}</div> : <div className="p-4">{content}</div>;
}

function resourceErrorCopy(scope: ResourceScope, error: Error): { title: string; body: string } {
  if (error instanceof AuthRequiredError) {
    return {
      title: "인증 필요",
      body: error.message,
    };
  }
  if (error instanceof ApiRequestError && error.status === 404) {
    if (scope === "applications") {
      return {
        title: "Application scope 확인 필요",
        body: "선택한 project scope가 없거나 membership이 맞지 않아 fail-closed로 처리됐습니다. application 상태나 host down으로 해석하지 않습니다.",
      };
    }
    if (scope === "dashboard") {
      return {
        title: "Dashboard scope 확인 필요",
        body: "선택한 application scope가 없거나 membership이 맞지 않을 수 있습니다. application down/deleted/healthy로 단정하지 않습니다.",
      };
    }
  }
  if (error instanceof ApiRequestError && error.status === 401) {
    return {
      title: "인증 필요",
      body: "인증이 만료되었습니다. 다시 GitHub 로그인 후 시도해 주세요.",
    };
  }
  return {
    title: "Resource 로드 실패",
    body: "화면에 필요한 데이터를 불러오지 못했습니다. 인증 정보나 내부 응답 내용은 표시하지 않습니다.",
  };
}

function formatOptionalNumber(value: number | null | undefined): string {
  return value === null || value === undefined || !Number.isFinite(value) ? "확인할 수 없음" : formatCount(value);
}
