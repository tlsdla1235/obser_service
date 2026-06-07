import { type FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { LucideIcon } from "lucide-react";
import {
  Activity,
  AlertCircle,
  Check,
  ChevronRight,
  Copy,
  Gauge,
  History,
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
import { useApiResource } from "../lib/use-api-resource";
import {
  buildSnapshotHistoryPaths,
  buildStarterCredentialMetadataPath,
  buildStarterCredentialRevocationPath,
  buildStarterCredentialRotationPath,
  formatCount,
  formatDateRange,
  formatNullableRatio,
  formatOptionalDateTime,
  formatRatio,
  histogramRangeBarWidth,
  HISTORY_PRESET_QUERY,
  humanizeAnchorStatus,
  humanizeCaptureReason,
  humanizeOrderCode,
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
  DashboardSnapshotMarkerReadModel,
  EndpointPriorityItem,
  HistoryHorizon,
  HistoryPreset,
  HistogramWindow,
  OneTimeStarterCredential,
  OperationalEventHistoryReadModel,
  ProjectRegistrationResponse,
  ProjectApplicationNavigationReadModel,
  ProjectNavigationReadModel,
  StarterCredentialMetadataResponse,
  StarterCredentialRotationResponse,
  TrendPreset,
  TriageCard,
} from "../lib/read-model-types";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Alert, AlertDescription, AlertTitle } from "./ui/alert";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "./ui/tooltip";
import { InstancePanels, useInstanceView } from "./instance-panels";
import { SnapshotDetailSurface, type SnapshotDetailTarget } from "./snapshot-detail-surface";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "./ui/tabs";

type ResourceScope = "applications" | "dashboard" | "projects";

function StatusBadge({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <span className={`inline-flex items-center border px-1.5 py-0.5 text-[11px] uppercase ${className || "border-neutral-400 text-neutral-800"}`}>
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
      const model = await readJsonResource<ApplicationDashboardReadModel>(response);
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
      <div className="bg-neutral-50 min-h-[calc(100vh-56px)]">
        <div className="border-b border-neutral-200 bg-white">
          <div className="mx-auto max-w-[1400px] px-6 h-12 flex items-center justify-between text-[13px]">
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
            <div className="flex items-center gap-3 text-neutral-600">
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

        <div className="mx-auto max-w-[1400px] grid grid-cols-12 gap-0">
          <aside className="col-span-12 lg:col-span-2 border-r border-neutral-200 bg-white min-h-[calc(100vh-104px)]">
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

          <aside className="col-span-12 lg:col-span-3 border-r border-neutral-200 bg-white">
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

          <main className="col-span-12 lg:col-span-7">
            <DashboardMain
              dashboard={dashboard}
              error={dashboardError}
              loading={dashboardLoading}
              onOpenEvidence={instanceView.openEvidence}
              onOpenTrend={instanceView.openTrend}
              onReload={dashboardResource.reload}
              selectedApplication={selectedApplication}
              selectedProject={selectedProject}
            />
          </main>
        </div>

        <InstancePanels
          view={instanceView.view}
          onClose={instanceView.close}
          onOpenTrend={instanceView.openTrend}
          onOpenEvidence={instanceView.openEvidence}
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
    <ul>
      {filteredProjects.map((project) => {
        const active = project.projectId === selectedProjectId;
        return (
          <li key={project.projectId}>
            <button
              onClick={() => onSelectProject(project.projectId)}
              className={`w-full text-left px-3 py-2.5 border-l-2 ${active ? "border-neutral-900 bg-neutral-50" : "border-transparent hover:bg-neutral-50"}`}
            >
              <div className="text-[13px] text-neutral-900">{project.name}</div>
              <div className="mt-1 flex items-center gap-2 text-[11px] text-neutral-500">
                <span>{project.applicationCount} apps</span>
                {project.setupConnectionIssueCount > 0 && (
                  <span className="text-neutral-800">· {project.setupConnectionIssueCount} setup signal</span>
                )}
              </div>
              <div className="mt-1 truncate text-[11px] text-neutral-600">{project.recentConcernDisplay}</div>
              <div className="mt-0.5 truncate text-[10px] text-neutral-400">{project.recentConcernMeta}</div>
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
    <ul>
      {filteredApplications.map((application) => {
        const active = application.applicationId === selectedApplicationId;
        return (
          <li key={application.applicationId}>
            <button
              onClick={() => onSelectApplication(application.applicationId)}
              className={`w-full text-left p-3 border-b border-neutral-100 ${active ? "bg-neutral-50" : "hover:bg-neutral-50"}`}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0 text-[13px] text-neutral-900">
                  <span className="truncate">{application.name}</span>
                  <span className="text-neutral-500"> · {application.environment}</span>
                </div>
                <StatusBadge className={application.lifecycleBadgeClassName}>{application.lifecycleBadgeDisplay}</StatusBadge>
              </div>
              <div className="mt-1 text-[10px] text-neutral-400">{application.lifecycleBadgeMeta}</div>
              <div className="mt-2 grid grid-cols-2 gap-2 text-[11px]">
                <div className="border border-neutral-200 p-1.5">
                  <SectionLabel icon={Activity}>수집 데이터</SectionLabel>
                  <div className="mt-0.5 text-neutral-800">{humanizeStatusCode(application.metricData.freshnessLabel)}</div>
                  <div className="text-neutral-500">{humanizeSourceCode(application.metricData.statusSource)}</div>
                  <div className="truncate text-neutral-400">{application.metricLastAcceptedBucketDisplay}</div>
                </div>
                <div className="border border-neutral-200 p-1.5">
                  <SectionLabel icon={Radio}>앱 연결</SectionLabel>
                  <div className="mt-0.5 text-neutral-800">
                    {humanizeStatusCode(application.starterConnection.heartbeatStatus)} · {humanizeStatusCode(application.starterConnection.freshnessLabel)}
                  </div>
                  <div className="truncate text-neutral-500">{humanizeStatusCode(application.starterConnection.connectionMeaning)}</div>
                  <div className="truncate text-neutral-400">{application.starterLastHeartbeatDisplay}</div>
                </div>
              </div>
              <div className="mt-2 border-l-2 border-neutral-300 pl-2 text-[11px] text-neutral-700">
                {application.topConcernDisplay}
              </div>
              <div className="mt-0.5 pl-2 text-[10px] text-neutral-400">{application.topConcernMeta}</div>
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
  onOpenTrend,
  onReload,
  selectedApplication,
  selectedProject,
}: {
  dashboard: DashboardPresentation | null;
  error: Error | null;
  loading: boolean;
  onOpenEvidence: ReturnType<typeof useInstanceView>["openEvidence"];
  onOpenTrend: ReturnType<typeof useInstanceView>["openTrend"];
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
    <div className="p-5">
      <DashboardContext selectedProject={selectedProject} dashboard={dashboard} />
      <Tabs defaultValue="current" className="mt-4 gap-4">
        <TabsList className="h-10 w-full justify-start rounded-none border border-neutral-200 bg-white p-1 md:w-fit">
          <TabsTrigger value="current" className="rounded-none px-4 text-[13px] data-[state=active]:bg-neutral-900 data-[state=active]:text-white">
            현재 상태
          </TabsTrigger>
          <TabsTrigger value="snapshots" className="rounded-none px-4 text-[13px] data-[state=active]:bg-neutral-900 data-[state=active]:text-white">
            스냅샷 기록
          </TabsTrigger>
        </TabsList>
        <TabsContent value="current" className="mt-0">
          <div className="grid grid-cols-12 gap-4">
            <div className="col-span-12 xl:col-span-8 space-y-4">
              <MetricStateStrip dashboard={dashboard} />
              <StarterConnectionStrip dashboard={dashboard} />
              <RecoveryNotice dashboard={dashboard} />
              <MetricScalars dashboard={dashboard} />
              <SourceScopedPercentilesPanel dashboard={dashboard} />
              <HistogramPanel dashboard={dashboard} />
              <TriagePanel dashboard={dashboard} />
              <EndpointPriorityPanel items={dashboard.endpointPriority} />
            </div>
            <aside className="col-span-12 space-y-4 xl:col-span-4">
              <CredentialLifecyclePanel selectedProject={selectedProject} />
              <InstancesPanel dashboard={dashboard} onOpenEvidence={onOpenEvidence} onOpenTrend={onOpenTrend} />
            </aside>
          </div>
        </TabsContent>
        <TabsContent value="snapshots" className="mt-0">
          <SnapshotHistoryPanel dashboard={dashboard} selectedApplication={selectedApplication} selectedProject={selectedProject} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function DashboardContext({ dashboard, selectedProject }: { dashboard: DashboardPresentation; selectedProject: ProjectPresentationItem }) {
  return (
    <div className="border border-neutral-200 bg-white p-4">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <div className="text-[12px] text-neutral-500">{selectedProject.name} / {dashboard.application.name}</div>
          <div className="mt-0.5 text-neutral-900">
            {dashboard.application.name}
            <span className="text-neutral-500"> · {dashboard.application.environment}</span>
          </div>
        </div>
        <div className="text-[12px] text-neutral-600 text-right">
          <div>생성 시각 <span className="text-neutral-900">{dashboard.generatedAtDisplay}</span></div>
          <div>현재 구간 <span className="text-neutral-900">{dashboard.currentWindowDisplay}</span></div>
          <div>비교 기준 <span className="text-neutral-700">{dashboard.baselineWindowDisplay}</span></div>
        </div>
      </div>
    </div>
  );
}

function MetricStateStrip({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="border border-neutral-900 bg-white p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <SectionLabel icon={Gauge}>상태 판단</SectionLabel>
          <InlineHelp label="데이터 지연 기준 설명">
            <div className="space-y-1">
              <div>마지막 수집 구간은 앱 상태 판단에 사용한 최신 데이터입니다.</div>
              <div>데이터 지연 기준: {formatOptionalDateTime(dashboard.application.freshness.staleAt)}까지 새 데이터가 들어오지 않으면 지연으로 볼 수 있습니다.</div>
              <div>수집 끊김 기준: {formatOptionalDateTime(dashboard.application.freshness.downAt)}까지도 새 데이터가 없으면 앱 연결, 트래픽, 수집 경로를 함께 확인합니다.</div>
            </div>
          </InlineHelp>
        </div>
        <StatusBadge className={dashboard.metricStateClassName}>{applicationStateDisplayText(dashboard.state.code)}</StatusBadge>
      </div>
      <div className="mt-2 text-neutral-900">{applicationStateSummary(dashboard)}</div>
      <div className="mt-1 text-[12px] text-neutral-600">{dashboard.state.recommendedAction}</div>
      <div className="mt-3 grid grid-cols-2 gap-2 text-[11px] text-neutral-600">
        <InfoCell label="판단 범위" value={humanizeStatusCode(dashboard.state.scope)} />
        <InfoCell label="마지막 수집 구간" value={dashboard.lastAcceptedBucketDisplay} />
      </div>
    </div>
  );
}

function StarterConnectionStrip({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="border border-neutral-300 bg-white p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <SectionLabel icon={Radio}>Starter 연결 상태</SectionLabel>
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
      <div className="mt-2 grid grid-cols-2 gap-3 text-[12px]">
        <InfoCell label="마지막 연결 확인" value={dashboard.starterLastHeartbeatDisplay} />
        <InfoCell label="연결 의미" value={humanizeStatusCode(dashboard.starterConnection.connectionMeaning)} />
      </div>
    </div>
  );
}

function RecoveryNotice({ dashboard }: { dashboard: DashboardPresentation }) {
  if (!dashboard.recovery.isRecovering) {
    return null;
  }
  return (
    <Alert className="border-neutral-400">
      <History className="h-4 w-4" strokeWidth={1.5} />
      <AlertTitle>회복 관찰 중</AlertTitle>
      <AlertDescription>
        {dashboard.recovery.recommendedAction ?? "회복 여부를 확정하지 말고 다음 수집 데이터까지 관찰하세요."}
        {dashboard.recovery.retryAfterSeconds !== null && (
          <span className="ml-1 text-neutral-500">다음 판단 대기 {dashboard.recovery.retryAfterSeconds}s</span>
        )}
      </AlertDescription>
    </Alert>
  );
}

function MetricScalars({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="grid grid-cols-3 gap-0 border border-neutral-200 bg-white">
      <MetricCell label="요청 수" value={formatCount(dashboard.metrics.requestCount)} />
      <MetricCell label="오류 수" value={formatCount(dashboard.metrics.errorCount)} />
      <MetricCell label="오류율" value={formatRatio(dashboard.metrics.errorRate)} last />
    </div>
  );
}

function SourceScopedPercentilesPanel({ dashboard }: { dashboard: DashboardPresentation }) {
  const source = dashboard.sourceScopedPercentiles;
  return (
    <div className="border border-neutral-200 bg-white">
      <div className="px-4 py-3 border-b border-neutral-200 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <SectionLabel icon={Gauge}>인스턴스별 응답 시간</SectionLabel>
          <InlineHelp label="응답 시간 지표 설명">
            <div className="space-y-1">
              <div>Starter가 보낸 인스턴스별 최신 측정값을 보여줍니다.</div>
              <div>p95 응답시간: 요청 100개 중 95개가 이 시간 안에 끝났다는 뜻입니다.</div>
              <div>p99 응답시간: 매우 느린 일부 요청까지 포함해 tail latency를 보는 기준입니다.</div>
            </div>
          </InlineHelp>
        </div>
        <StatusBadge className={statusBadgeClassName(source.status)}>{statusDisplayText(source.status)}</StatusBadge>
      </div>
      {source.items.length === 0 ? (
        <div className="p-4 text-[12px] text-neutral-600">
          {sourceScopedEmptyText(source.status, source.reason ? humanizeStatusCode(source.reason) : dashboard.sourceScopedReasonDisplay)}
        </div>
      ) : (
        <table className="w-full text-[12px]">
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
      )}
    </div>
  );
}

function HistogramPanel({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="border border-neutral-200 bg-white">
      <div className="px-4 py-3 border-b border-neutral-200 flex items-center gap-2">
        <SectionLabel icon={Activity}>응답 시간 분포</SectionLabel>
        <InlineHelp label="응답 시간 구간 설명">
          응답시간 구간마다 해당 범위에 들어온 요청 수를 보여줍니다.
        </InlineHelp>
      </div>
      <div className="grid grid-cols-1 gap-0 md:grid-cols-2">
        <HistogramWindowCard label="현재 구간" window={dashboard.histogramDistribution.current} />
        <HistogramWindowCard label="비교 기준 구간" window={dashboard.histogramDistribution.baseline} />
      </div>
    </div>
  );
}

function HistogramWindowCard({ label, window }: { label: string; window: HistogramWindow }) {
  const buckets = toDisplayLatencyBuckets(window.buckets);
  return (
    <div className="border-b border-neutral-100 p-4 md:border-b-0 md:border-r md:last:border-r-0">
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
        <SectionLabel icon={ListChecks}>먼저 확인할 엔드포인트</SectionLabel>
        <span className="text-[11px] text-neutral-500">우선순위 높은 순서</span>
      </div>
      {items.length === 0 ? (
        <div className="p-4 text-[12px] text-neutral-500">먼저 확인할 엔드포인트 후보가 없습니다.</div>
      ) : (
        <ol>
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
    <li className="px-4 py-3 border-b border-neutral-100 last:border-b-0">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-3">
            <span className="text-neutral-400 text-[12px] tabular-nums">{String(item.rank).padStart(2, "0")}</span>
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
  onOpenTrend,
}: {
  dashboard: DashboardPresentation;
  onOpenEvidence: ReturnType<typeof useInstanceView>["openEvidence"];
  onOpenTrend: ReturnType<typeof useInstanceView>["openTrend"];
}) {
  return (
    <div className="border border-neutral-200">
      <div className="px-3 py-2.5 border-b border-neutral-200">
        <SectionLabel icon={Server}>실행 인스턴스</SectionLabel>
      </div>
      {dashboard.instances.length === 0 ? (
        <div className="p-3 text-[12px] text-neutral-500">아직 확인할 실행 인스턴스가 없습니다.</div>
      ) : (
        <ul>
          {dashboard.instances.map((instance) => {
            const target = {
              applicationId: dashboard.application.applicationId,
              evidenceLink: instance.links.evidence,
              instanceId: instance.instanceId,
              instanceName: instance.instanceName,
              projectId: dashboard.application.projectId,
            };
            return (
              <li key={instance.instanceId} className="px-3 py-2.5 border-b border-neutral-100 last:border-b-0">
                <div className="text-[13px] text-neutral-900">{instance.instanceName}</div>
                <div className="mt-0.5 text-[11px] text-neutral-500">마지막 관측 {formatOptionalDateTime(instance.lastSeenAt)}</div>
                <div className="mt-2 flex gap-3 text-[11px] text-neutral-700">
                  <button onClick={() => onOpenEvidence(target)} className="underline underline-offset-2 hover:text-neutral-900">근거 보기</button>
                  <button onClick={() => onOpenTrend(target)} className="underline underline-offset-2 hover:text-neutral-900">변화 보기</button>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function SnapshotHistoryPanel({
  dashboard,
  selectedApplication,
  selectedProject,
}: {
  dashboard: DashboardPresentation;
  selectedApplication: ApplicationPresentationItem;
  selectedProject: ProjectPresentationItem;
}) {
  const [preset, setPreset] = useState<HistoryPreset>("24h");
  const [detailTarget, setDetailTarget] = useState<SnapshotDetailTarget | null>(null);
  const historyResourceKey = `${selectedProject.projectId}|${selectedApplication.applicationId}|${preset}`;

  useEffect(() => {
    setDetailTarget(null);
  }, [selectedApplication.applicationId, selectedProject.projectId]);

  const requestHistory = useCallback(
    async ({ authFetch, signal }: { authFetch: AuthFetch; signal: AbortSignal }) => {
      const paths = buildSnapshotHistoryPaths(selectedProject.projectId, selectedApplication.applicationId, preset);
      const [eventsResponse, markersResponse] = await Promise.all([
        authFetch(paths.events, {
          ...NO_STORE_REQUEST_OPTIONS,
          signal,
        }),
        authFetch(paths.markers, {
          ...NO_STORE_REQUEST_OPTIONS,
          signal,
        }),
      ]);
      const events = await readJsonResource<OperationalEventHistoryReadModel>(eventsResponse);
      const markers = await readJsonResource<DashboardSnapshotMarkerReadModel>(markersResponse);
      validateSnapshotHistoryResponse(events, markers, selectedApplication.applicationId, preset);
      return { events, markers };
    },
    [preset, selectedApplication.applicationId, selectedProject.projectId],
  );

  const resource = useApiResource<{
    events: OperationalEventHistoryReadModel;
    markers: DashboardSnapshotMarkerReadModel;
  }>({
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
      <div className="px-3 py-2.5 border-b border-neutral-200 flex items-center justify-between gap-2">
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
              onClick={() => {
                setPreset(candidate);
                setDetailTarget(null);
              }}
            >
              {presetDisplayText(candidate)}
            </Button>
          ))}
        </div>
      </div>
      <div className="p-4 text-[12px] text-neutral-600 space-y-4">
        {dashboard.snapshot === null && (
          <div className="border border-neutral-200 bg-neutral-50 p-2">
            현재 상태에서 바로 연결된 스냅샷은 없습니다. 저장된 기록이 있으면 아래에서 따로 불러옵니다.
          </div>
        )}
        {loading && <ResourceMessage title="기록 로딩 중" body={`${presetDisplayText(preset)} 범위의 상태 변화와 스냅샷을 불러오는 중입니다.`} />}
        {error && <SnapshotHistoryError error={error} onReload={resource.reload} />}
        {!loading && !error && history && (
          <SnapshotHistoryReady
            applicationId={selectedApplication.applicationId}
            events={history.events}
            markers={history.markers}
            onSelectDetail={setDetailTarget}
            projectId={selectedProject.projectId}
          />
        )}
        <SnapshotDetailSurface
          applicationId={selectedApplication.applicationId}
          projectId={selectedProject.projectId}
          target={detailTarget}
        />
      </div>
    </div>
  );
}

function SnapshotHistoryReady({
  applicationId,
  events,
  markers,
  onSelectDetail,
  projectId,
}: {
  applicationId: string;
  events: OperationalEventHistoryReadModel;
  markers: DashboardSnapshotMarkerReadModel;
  onSelectDetail: (target: SnapshotDetailTarget) => void;
  projectId: string;
}) {
  const sortedEvents = useMemo(
    () => [...events.events].sort((a, b) => compareTimestampDesc(a.occurredAt, b.occurredAt)),
    [events.events],
  );
  const sortedMarkers = useMemo(
    () => [...markers.markers].sort((a, b) => compareTimestampDesc(a.capturedAt, b.capturedAt)),
    [markers.markers],
  );

  return (
    <>
      <div className="border border-neutral-200 bg-white">
        <div className="flex items-center justify-between gap-2 border-b border-neutral-100 px-3 py-2 text-[11px] text-neutral-500">
          <span className="uppercase">상태 변화 기록</span>
          <span>{humanizeOrderCode(events.horizon.order)}</span>
        </div>
        {sortedEvents.length === 0 ? (
          <div className="p-3 text-[12px] text-neutral-500">
            이 범위에 표시할 상태 변화 기록이 없습니다.
          </div>
        ) : (
          <ul>
            {sortedEvents.map((event) => (
              <li key={event.eventId} className="border-b border-neutral-100 p-3 last:border-b-0">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="text-neutral-900">{event.title}</div>
                    <div className="mt-0.5 text-neutral-600">{event.summary}</div>
                    <div className="mt-1 text-[11px] text-neutral-500">
                      {humanizeStatusCode(event.type)} · 저장 당시 상태 {humanizeStatusCode(event.stateCode)} · 발생 {formatOptionalDateTime(event.occurredAt)} · 해결 {formatOptionalDateTime(event.resolvedAt)}
                    </div>
                  </div>
                  <StatusBadge className={severityBadgeClassName(event.severity)}>{severityDisplayText(event.severity)}</StatusBadge>
                </div>
                <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500">
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
                    onSelectDetail({
                      activeAnchor: event.evidence.snapshotDetailAnchor,
                      snapshotId: event.snapshotId,
                      snapshotLink: event.links.snapshot,
                    });
                  }}
                >
                  <History className="h-3.5 w-3.5" strokeWidth={1.5} /> 상세 보기
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="border border-neutral-200 bg-white">
        <div className="flex items-center justify-between gap-2 border-b border-neutral-100 px-3 py-2 text-[11px] text-neutral-500">
          <span className="uppercase">저장된 스냅샷</span>
          <span>{humanizeOrderCode(markers.horizon.order)}</span>
        </div>
        {sortedMarkers.length === 0 ? (
          <div className="p-3 text-[12px] text-neutral-500">
            {markers.emptyState?.message ?? "저장된 스냅샷이 없습니다."} {markers.emptyState?.recommendedAction ?? ""}
          </div>
        ) : (
          <ul>
            {sortedMarkers.map((marker) => (
              <li key={marker.markerId} className="border-b border-neutral-100 p-3 last:border-b-0">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="text-neutral-900">{marker.title}</div>
                    <div className="mt-0.5 text-neutral-600">{marker.summary}</div>
                    <div className="mt-1 text-[11px] text-neutral-500">
                      {humanizeStatusCode(marker.type)} · {humanizeStatusCode(marker.readMeaning)} · 저장 이유 {humanizeCaptureReason(marker.captureReason)}
                    </div>
                  </div>
                  <StatusBadge className={severityBadgeClassName(marker.severity)}>{severityDisplayText(marker.severity)}</StatusBadge>
                </div>
                <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-neutral-500">
                  <InfoCell label="스냅샷 ID" value={marker.markerId} />
                  <InfoCell label="저장 시각" value={formatOptionalDateTime(marker.capturedAt)} />
                  <InfoCell label="저장 당시 상태" value={humanizeStatusCode(marker.storedApplicationStateCode)} />
                  <InfoCell label="신뢰도" value={formatNullableRatio(marker.confidence)} />
                  <InfoCell label="판단 기준" value={marker.primaryRuleId ?? "적용된 판단 기준 없음"} />
                  <InfoCell label="엔드포인트" value={marker.primaryEndpointKey ?? "해당 엔드포인트 없음"} />
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  className="mt-2 gap-2 border-neutral-300"
                  onClick={() => {
                    onSelectDetail({
                      snapshotId: marker.snapshotId,
                      snapshotLink: marker.links.snapshot,
                    });
                  }}
                >
                  <History className="h-3.5 w-3.5" strokeWidth={1.5} /> 상세 보기
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function validateSnapshotHistoryResponse(
  events: OperationalEventHistoryReadModel,
  markers: DashboardSnapshotMarkerReadModel,
  expectedApplicationId: string,
  expectedPreset: HistoryPreset,
) {
  if (
    events.source !== "dashboard_snapshots" ||
    markers.source !== "dashboard_snapshots" ||
    events.applicationId !== expectedApplicationId ||
    markers.applicationId !== expectedApplicationId ||
    !historyHorizonMatches(events.horizon, {
      limit: HISTORY_PRESET_QUERY[expectedPreset].eventLimit,
      maxLimit: 100,
      order: "occurredAt_desc",
      requestedSince: expectedPreset,
    }) ||
    !historyHorizonMatches(markers.horizon, {
      limit: HISTORY_PRESET_QUERY[expectedPreset].markerLimit,
      maxLimit: 336,
      order: "capturedAt_asc",
      requestedSince: expectedPreset,
    })
  ) {
    throw new ApiRequestError("snapshot_history_context_mismatch");
  }
}

/**
 * History/marker 응답은 fixed preset query와 server order metadata가 일치할 때만 화면에 반영한다.
 * contract drift가 있으면 fail-closed로 처리해 오래된 horizon을 current context처럼 보여주지 않는다.
 */
function historyHorizonMatches(
  horizon: HistoryHorizon,
  expected: { limit: number; maxLimit: number; order: string; requestedSince: HistoryPreset },
): boolean {
  return (
    horizon.requestedSince === expected.requestedSince &&
    horizon.defaultSince === "24h" &&
    horizon.maxSince === "14d" &&
    horizon.limit === expected.limit &&
    horizon.maxLimit === expected.maxLimit &&
    horizon.order === expected.order &&
    horizonWindowIsValid(horizon)
  );
}

function horizonWindowIsValid(horizon: HistoryHorizon): boolean {
  const since = Date.parse(horizon.since);
  const until = Date.parse(horizon.until);
  return Number.isFinite(since) && Number.isFinite(until) && until > since;
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
      title: "History scope 확인 필요",
      body: "선택한 앱의 저장 기록을 찾지 못했습니다. 권한이나 보관 기간을 확인해 주세요.",
    };
  }
  if (error instanceof ApiRequestError && error.status === 400) {
    return {
      title: "History 조회 조건 확인 필요",
      body: "지원하는 fixed preset은 24h, 7d, 14d뿐입니다.",
    };
  }
  return {
    title: "기록 로드 실패",
    body: "저장된 스냅샷 기록을 불러오지 못했습니다.",
  };
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
  switch ((dashboard.state.code ?? "").toLowerCase()) {
    case "active":
      return "최근 수집 데이터 기준으로 앱이 정상 동작 중입니다.";
    case "degraded":
      return "일부 지표가 평소보다 나빠져 먼저 확인이 필요합니다.";
    case "down":
      return "수집 데이터가 끊긴 것 같아요. 앱 연결과 트래픽을 함께 확인해 주세요.";
    case "idle":
      return "최근 트래픽이 적어 상태를 단정하기 어렵습니다.";
    case "stale":
      return "최근 수집 데이터가 늦게 들어오고 있습니다.";
    case "waiting_first_data":
      return "앱의 첫 수집 데이터를 기다리는 중입니다.";
    default:
      return dashboard.state.rationale || "상태 판단에 필요한 데이터를 확인하고 있습니다.";
  }
}

function presetDisplayText(preset: HistoryPreset | TrendPreset): string {
  switch (preset) {
    case "24h":
      return "24시간";
    case "7d":
      return "7일";
    case "14d":
      return "14일";
    default:
      return preset;
  }
}

function compareTimestampDesc(a: string | null | undefined, b: string | null | undefined): number {
  const left = a ? Date.parse(a) : Number.NEGATIVE_INFINITY;
  const right = b ? Date.parse(b) : Number.NEGATIVE_INFINITY;
  return (Number.isFinite(right) ? right : Number.NEGATIVE_INFINITY) - (Number.isFinite(left) ? left : Number.NEGATIVE_INFINITY);
}

function starterStateImpactText(stateImpact: string): string {
  if (stateImpact === "none") {
    return "상태 판단 영향 없음: 앱 연결 신호가 현재 정상/주의/지연/끊김 판단을 직접 바꾸지 않습니다.";
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

function MetricCell({ label, last = false, value }: { label: string; last?: boolean; value: string }) {
  return (
    <div className={`p-4 ${last ? "" : "border-r border-neutral-200"}`}>
      <div className="text-[11px] uppercase text-neutral-500">{label}</div>
      <div className="mt-1 text-neutral-900">{value}</div>
    </div>
  );
}

function InfoCell({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <div className="text-neutral-500">{label}</div>
      <div className="truncate text-neutral-900">{value}</div>
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
