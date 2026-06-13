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
  buildSnapshotDetailPath,
  buildStarterCredentialMetadataPath,
  buildStarterCredentialRevocationPath,
  buildStarterCredentialRotationPath,
  formatCount,
  formatNullableRatio,
  formatOptionalDateTime,
  formatRatio,
  humanizeCaptureReason,
  humanizeSourceCode,
  humanizeStatusCode,
  severityBadgeClassName,
  severityDisplayText,
  snapshotIdFromDetailPath,
  statusBadgeClassName,
  toApplicationPresentationItems,
  toDashboardPresentation,
  toProjectPresentationItems,
  validateDashboardPath,
  validateProjectApplicationsPath,
  validateSnapshotDetailPath,
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
  HistogramBucket,
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
import type { InstanceDashboardTarget, SnapshotInstanceDashboardTarget } from "./instance-dashboard-surface";
import { SnapshotHistoryPanel } from "./snapshot-history-panel";
import { type SnapshotDetailTarget } from "./snapshot-detail-surface";

type ResourceScope = "applications" | "dashboard" | "projects";

type InstanceSummaryOpenAction =
  | {
      mode: "live";
      open: (target: InstanceDashboardTarget) => void;
    }
  | {
      mode: "snapshot";
      open: (target: SnapshotInstanceDashboardTarget) => void;
      snapshotDetailLink: string;
      snapshotId: string;
    };

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

/**
 * live와 snapshot이 동일하게 쓰는 dashboard panel tree다.
 * snapshot mode는 같은 컴포넌트에 mode=snapshot presentation만 주입한다.
 */
function DashboardPanels({
  dashboard,
  instanceOpenAction,
  mode = "live",
  selectedProject,
}: {
  dashboard: DashboardPresentation;
  instanceOpenAction: InstanceSummaryOpenAction;
  mode?: "live" | "snapshot";
  selectedProject: ProjectPresentationItem;
}) {
  return (
    <>
      <DashboardContext dashboard={dashboard} mode={mode} selectedProject={selectedProject} />
      <LifecycleStateHero dashboard={dashboard} />
      <StarterConnectionStrip dashboard={dashboard} />
      <DataQualityFreshnessStrip dashboard={dashboard} />
      <GoldenSignalsGrid dashboard={dashboard} />
      <FirstLookCandidatesPanel candidates={dashboard.firstLookCandidates} />
      <EndpointPriorityPanel dashboard={dashboard} />
      <ResourceSignalsPanel dashboard={dashboard} />
      <InstancesPanel dashboard={dashboard} openAction={instanceOpenAction} />
    </>
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
  const [snapshotTarget, setSnapshotTarget] = useState<SnapshotDetailTarget | null>(null);

  useEffect(() => {
    setSnapshotTarget(null);
  }, [selectedApplication?.applicationId, selectedProject?.projectId]);

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
      {snapshotTarget ? (
        <SnapshotModeSurface
          onBackToLive={() => setSnapshotTarget(null)}
          onOpenSnapshotDashboard={onOpenSnapshotDashboard}
          selectedApplication={selectedApplication}
          selectedProject={selectedProject}
          target={snapshotTarget}
        />
      ) : (
        <DashboardPanels
          dashboard={dashboard}
          instanceOpenAction={{ mode: "live", open: onOpenEvidence }}
          selectedProject={selectedProject}
        />
      )}
      <SnapshotHistoryPanel
        onRestoreSnapshotDashboard={setSnapshotTarget}
        selectedApplication={selectedApplication}
        selectedProject={selectedProject}
      />
      {!snapshotTarget && (
        <details className="border border-neutral-200 bg-white">
          <summary className="cursor-pointer px-3 py-2.5 text-[11px] uppercase text-neutral-500">
            Starter credential lifecycle
          </summary>
          <div className="border-t border-neutral-100 p-3">
            <CredentialLifecyclePanel selectedProject={selectedProject} />
          </div>
        </details>
      )}
    </div>
  );
}

/**
 * snapshot slot 클릭 시 저장된 read model을 live와 동일한 컴포넌트로 복원하는 surface다.
 * 별도 detail 화면으로 redirect하지 않고 같은 자리에서 mode=snapshot으로 전환한 뒤 상단으로 scroll한다.
 */
function SnapshotModeSurface({
  onBackToLive,
  onOpenSnapshotDashboard,
  selectedApplication,
  selectedProject,
  target,
}: {
  onBackToLive: () => void;
  onOpenSnapshotDashboard: ReturnType<typeof useInstanceView>["openSnapshotDashboard"];
  selectedApplication: ApplicationPresentationItem;
  selectedProject: ProjectPresentationItem;
  target: SnapshotDetailTarget;
}) {
  const topRef = useRef<HTMLDivElement | null>(null);
  const readModelPath = snapshotReadModelPath(target, selectedProject, selectedApplication);
  const resourceKey = `snapshot-read-model:${readModelPath}`;
  const instanceOpenAction = useMemo(
    () => snapshotInstanceOpenAction(target, selectedProject, selectedApplication, onOpenSnapshotDashboard),
    [onOpenSnapshotDashboard, selectedApplication, selectedProject, target],
  );

  const requestSnapshotReadModel = useCallback(
    async ({ authFetch, signal }: { authFetch: AuthFetch; signal: AbortSignal }) => {
      const response = await authFetch(readModelPath, { ...NO_STORE_REQUEST_OPTIONS, signal });
      return guardApplicationDashboardReadModel(await readJsonResource<ApplicationDashboardReadModel>(response), {
        applicationId: selectedApplication.applicationId,
        expectedMode: "snapshot",
        projectId: selectedProject.projectId,
      });
    },
    [readModelPath, selectedApplication.applicationId, selectedProject.projectId],
  );

  const resource = useApiResource<ApplicationDashboardReadModel>({
    dependencies: [resourceKey],
    request: requestSnapshotReadModel,
    resourceKey,
  });

  const current = resource.resourceKey === resourceKey;
  const loading = !current || resource.loading;
  const error = current ? resource.error : null;
  const model = current ? resource.data : null;
  const presentation = useMemo(() => (model ? toDashboardPresentation(model) : null), [model]);

  useEffect(() => {
    topRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [target]);

  return (
    <div ref={topRef} className="grid gap-4 scroll-mt-4">
      <SnapshotModeBanner onBackToLive={onBackToLive} presentation={presentation} target={target} />
      {loading && <MainMessage title="Snapshot 복원 중" body="저장된 read model을 live surface로 불러오는 중입니다." />}
      {error && <ResourceErrorMessage scope="dashboard" error={error} onReload={resource.reload} roomy />}
      {!loading && !error && presentation && (
        <DashboardPanels
          dashboard={presentation}
          instanceOpenAction={instanceOpenAction}
          mode="snapshot"
          selectedProject={selectedProject}
        />
      )}
    </div>
  );
}

/**
 * snapshot mode의 Instance summary action은 selected snapshot id를 target에 고정한다.
 * snapshotId가 없는 legacy 진입은 snapshot detail link에서 id를 파싱해 live/current evidence로 흐르지 않게 한다.
 */
function snapshotInstanceOpenAction(
  target: SnapshotDetailTarget,
  selectedProject: ProjectPresentationItem,
  selectedApplication: ApplicationPresentationItem,
  open: ReturnType<typeof useInstanceView>["openSnapshotDashboard"],
): InstanceSummaryOpenAction {
  const snapshotDetailLink = target.snapshotLink
    ? validateSnapshotDetailPath(target.snapshotLink, selectedProject.projectId, selectedApplication.applicationId, target.snapshotId)
    : buildSnapshotDetailPath(selectedProject.projectId, selectedApplication.applicationId, target.snapshotId ?? "");
  const snapshotId = target.snapshotId ?? snapshotIdFromDetailPath(snapshotDetailLink);
  validateSnapshotDetailPath(snapshotDetailLink, selectedProject.projectId, selectedApplication.applicationId, snapshotId);
  return {
    mode: "snapshot",
    open,
    snapshotDetailLink,
    snapshotId,
  };
}

/**
 * snapshot mode 진입 안내와 live 복귀 버튼, 저장 시점 provenance를 보여주는 상단 banner다.
 */
function SnapshotModeBanner({
  onBackToLive,
  presentation,
  target,
}: {
  onBackToLive: () => void;
  presentation: DashboardPresentation | null;
  target: SnapshotDetailTarget;
}) {
  const captureReason = target.captureReason ? humanizeCaptureReason(target.captureReason) : null;
  return (
    <section className="flex flex-wrap items-start justify-between gap-3 border border-amber-300 bg-amber-50 p-3">
      <div className="min-w-0">
        <SectionLabel icon={Radio}>Snapshot mode</SectionLabel>
        <div className="mt-1 text-[13px] font-medium text-neutral-900">
          저장된 시점을 live와 동일한 dashboard로 복원했습니다.
        </div>
        <div className="mt-1 text-[11px] text-neutral-600">
          {presentation
            ? `생성 ${presentation.generatedAtDisplay} · window ${presentation.canonicalWindowDisplay}${captureReason ? ` · ${captureReason}` : ""}`
            : captureReason ?? "저장된 read model을 불러오는 중입니다."}
        </div>
      </div>
      <Button
        variant="outline"
        className="h-9 gap-1.5 border-neutral-300 bg-white px-3 text-[12px] font-medium text-neutral-900 hover:border-neutral-500 hover:bg-neutral-50"
        onClick={onBackToLive}
      >
        <Radio className="h-3.5 w-3.5" strokeWidth={1.5} /> 라이브로 돌아가기
      </Button>
    </section>
  );
}

/**
 * snapshot read model API path를 만든다. marker가 준 self link 뒤에 `/read-model`을 붙이고, 없으면 id로 직접 만든다.
 */
function snapshotReadModelPath(
  target: SnapshotDetailTarget,
  selectedProject: ProjectPresentationItem,
  selectedApplication: ApplicationPresentationItem,
): string {
  const base =
    target.snapshotLink ??
    `/api/projects/${selectedProject.projectId}/applications/${selectedApplication.applicationId}/dashboard/snapshots/${target.snapshotId ?? ""}`;
  return `${base.replace(/\/+$/, "")}/read-model`;
}

function DashboardContext({
  dashboard,
  mode = "live",
  selectedProject,
}: {
  dashboard: DashboardPresentation;
  mode?: "live" | "snapshot";
  selectedProject: ProjectPresentationItem;
}) {
  const snapshotMode = mode === "snapshot";
  const baselineSignal = dashboard.readSemantics.baselineComparisonUsedForMvpDecision ? "baseline used" : "baseline not used";
  return (
    <section className="border border-neutral-900 bg-white">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-neutral-200 p-3">
        <div className="min-w-0">
          <SectionLabel icon={Activity}>Application Dashboard / {snapshotMode ? "Snapshot" : "Live"}</SectionLabel>
          <h1 className="mt-1 text-[22px] font-medium leading-tight text-neutral-950">{dashboard.application.name}</h1>
          <p className="mt-1 text-[12px] text-neutral-600">
            {snapshotMode
              ? "저장된 read model을 live와 동일한 surface로 복원합니다. 현재 metric으로 재계산하지 않습니다."
              : "Server read model을 표시합니다. UI는 lifecycle state, endpoint priority, resource pattern을 재계산하지 않습니다."}
          </p>
        </div>
        <div className="flex max-w-full flex-wrap gap-1.5">
          <StatusBadge className={statusBadgeClassName(snapshotMode ? "attention" : "live")}>mode={dashboard.mode}</StatusBadge>
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
    <details className="border border-neutral-200 bg-white">
      <summary className="grid cursor-pointer gap-2 px-3 py-2.5 text-[11px] uppercase text-neutral-500 md:grid-cols-[minmax(180px,0.55fr)_minmax(0,1.4fr)_minmax(200px,0.8fr)]">
        <SectionLabel icon={Activity}>Data quality / freshness</SectionLabel>
        <span className="text-neutral-700">
          {formatCount(dashboard.dataQuality.requestCount)} / 최소 {formatCount(dashboard.dataQuality.minimumRequestCount)}
        </span>
        <span className="text-neutral-700">{humanizeStatusCode(dashboard.dataQuality.state)} · {dashboard.dataQualityLastObservedDisplay}</span>
      </summary>
      <div className={`grid gap-3 border-t border-neutral-100 border-l-4 p-3 md:grid-cols-[minmax(180px,0.55fr)_minmax(0,1.4fr)_minmax(200px,0.8fr)] md:items-center ${stateStripAccentClassName(dashboard.dataQuality.state)}`}>
        <div>
          <StatusBadge className={statusBadgeClassName(dashboard.dataQuality.state)}>
            {humanizeStatusCode(dashboard.dataQuality.state)}
          </StatusBadge>
          <h2 className="mt-2 text-[16px] font-medium leading-tight text-neutral-950">
            {formatCount(dashboard.dataQuality.requestCount)} / 최소 {formatCount(dashboard.dataQuality.minimumRequestCount)}
          </h2>
        </div>
        <div className="text-[13px] leading-5 text-neutral-900">
          마지막 관측 {dashboard.dataQualityLastObservedDisplay}
          <p className="text-[12px] text-neutral-600">마지막 수집 구간 {dashboard.lastAcceptedBucketDisplay}</p>
        </div>
        <div className="text-[12px] text-neutral-600">
          <p>stale {formatOptionalDateTime(dashboard.application.freshness.staleAt)}</p>
          <p>down {formatOptionalDateTime(dashboard.application.freshness.downAt)}</p>
          <p className="mt-1 text-[11px] text-neutral-500">
            baseline not used · histogram percentile {dashboard.readSemantics.histogramBucketsUsedForPercentiles ? "사용" : "사용 안 함"}
          </p>
          <p className="mt-1 truncate text-[11px] text-neutral-500" title={dashboard.dataQuality.limitations.map(humanizeStatusCode).join(" · ")}>
            {dashboard.dataQuality.limitations.length > 0
              ? dashboard.dataQuality.limitations.map(humanizeStatusCode).join(" · ")
              : "서버가 별도 data quality limitation을 제공하지 않았습니다."}
          </p>
        </div>
      </div>
    </details>
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
        <h2 className="mt-2 text-[16px] font-medium leading-tight text-neutral-950">{applicationStateDisplayText(dashboard.state.code)}</h2>
      </div>
      <div className="text-[13px] text-neutral-900">
        <p className="line-clamp-1">{dashboard.operatorSummary.headline}</p>
        <p className="line-clamp-2 text-[12px] text-neutral-600">{applicationStateSummary(dashboard)}</p>
      </div>
      <div className="text-[12px] text-neutral-600">
        <p className="line-clamp-2">{dashboard.state.recommendedAction}</p>
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
      <div className="flex items-start justify-between gap-3 border-b border-neutral-200 px-3 py-2.5">
        <div>
          <SectionLabel icon={ListChecks}>First look candidates</SectionLabel>
          <p className="mt-1 text-[12px] text-neutral-500">최대 3개만 보여주는 bounded evidence queue입니다.</p>
        </div>
        <span className="text-[11px] text-neutral-500">server order</span>
      </div>
      {candidates.length === 0 ? (
        <div className="p-3 text-[12px] text-neutral-500">서버가 먼저 볼 후보를 제공하지 않았습니다.</div>
      ) : (
        <ol className="grid gap-3 p-3 lg:grid-cols-3">
          {candidates.map((candidate) => (
            <li key={`${candidate.rank}-${candidate.type}-${candidate.target ?? "none"}`} className="border border-neutral-200 p-3">
              <StatusBadge>{humanizeStatusCode(candidate.type)}</StatusBadge>
              <h3 className="mt-2 text-[14px] font-medium leading-snug text-neutral-950">{candidate.target ?? candidate.reasonCode}</h3>
              <div className="mt-1 text-[11px] text-neutral-500">
                rank {candidate.rank} · {humanizeSourceCode(candidate.source)}
              </div>
              <p className="mt-2 text-[12px] leading-5 text-neutral-700">{candidate.operatorText}</p>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

function StarterConnectionStrip({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="grid gap-3 border border-neutral-900 border-l-4 border-l-emerald-600 bg-white p-3 md:grid-cols-[minmax(180px,0.55fr)_minmax(0,1.4fr)_minmax(200px,0.8fr)] md:items-center">
      <div>
        <div className="flex items-center gap-2">
          <SectionLabel icon={Radio}>StarterConnection</SectionLabel>
          <InlineHelp label="상태 판단 영향 설명">
            <div className="space-y-1">
              <div>마지막 연결 신호는 앱이 마지막으로 살아 있다고 알려온 시각입니다.</div>
              <div>{starterStateImpactText(dashboard.starterConnection.stateImpact)}</div>
            </div>
          </InlineHelp>
        </div>
        <StatusBadge className={`mt-2 ${statusBadgeClassName(dashboard.starterConnection.lastHeartbeatStatus)}`}>
          {statusDisplayText(dashboard.starterConnection.lastHeartbeatStatus)}
        </StatusBadge>
        <h2 className="mt-2 text-[16px] font-medium leading-tight text-neutral-950">Control-plane only</h2>
      </div>
      <div className="text-[13px] text-neutral-900">
        heartbeat {dashboard.starterLastHeartbeatDisplay}, metric state 변경 없음
      </div>
      <div className="text-[12px] text-neutral-600">
        heartbeat는 accepted bucket freshness나 application lifecycle state를 직접 만들지 않습니다.
        <p className="mt-1 text-[11px] text-neutral-500">
          {humanizeStatusCode(dashboard.starterConnection.connectionMeaning)} · {humanizeSourceCode(dashboard.starterConnection.statusSource)}
        </p>
      </div>
    </div>
  );
}

function EndpointResourceEvidencePanel({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <div className="space-y-4">
      <EndpointPriorityPanel dashboard={dashboard} />
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
      <div className="border-b border-neutral-200 px-3 py-2.5">
        <SectionLabel icon={Activity}>Resource evidence</SectionLabel>
        <p className="mt-1 text-[12px] text-neutral-500">root cause 확정이 아니라 server read model의 USE hint를 표시합니다.</p>
      </div>
      <div className="grid grid-cols-1 gap-3 p-3 md:grid-cols-3">
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

function GoldenSignalsGrid({ dashboard }: { dashboard: DashboardPresentation }) {
  return (
    <section className="grid grid-cols-1 gap-0 border border-neutral-200 bg-white md:grid-cols-4" aria-label="golden signals">
      <MetricCell label="RED Rate" note="최근 30분 요청량" value={formatCount(dashboard.signals.red.requestCount)} />
      <MetricCell label="RED Errors" note={dashboard.signals.red.errorSemantic} value={formatRatio(dashboard.signals.red.errorRate)} />
      <MetricCell label="RED Duration" note="500ms 초과 요청 비율" value={formatNullableRatio(dashboard.signals.red.slowShareOver500ms)} />
      <MetricCell
        label="USE Hint"
        note="DB pool window max"
        value={formatNullableRatio(dashboard.signals.use.datasourcePoolUsage.max)}
        last
      />
    </section>
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

type EndpointPrioritySortKey = "server" | "request" | "error" | "slow";

function EndpointPriorityPanel({ dashboard }: { dashboard: DashboardPresentation }) {
  const items = dashboard.endpointPriority;
  const [sortKey, setSortKey] = useState<EndpointPrioritySortKey>("server");
  const [limit, setLimit] = useState(5);
  const slowSortAvailable = items.some((item) => item.evidence.slowShare !== null);
  const effectiveSortKey = sortKey === "slow" && !slowSortAvailable ? "server" : sortKey;
  const sortedItems = useMemo(
    () => sortEndpointPriorityItems(items, effectiveSortKey),
    [effectiveSortKey, items],
  );
  const limitOptions = endpointLimitOptions(items.length);
  const effectiveLimit = Math.min(limit, items.length || limit);
  const visibleItems = sortedItems.slice(0, effectiveLimit);
  const maxRequestCount = Math.max(0, ...visibleItems.map((item) => item.evidence.requestCount));
  const maxErrorRate = Math.max(0, ...visibleItems.map((item) => item.evidence.errorRate));
  const maxSlowShare = Math.max(0, ...visibleItems.map((item) => item.evidence.slowShare ?? 0));

  return (
    <div className="border border-neutral-200 bg-white">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-neutral-200 px-3 py-2.5">
        <div>
          <SectionLabel icon={ListChecks}>Endpoint evidence</SectionLabel>
          <p className="mt-1 text-[12px] text-neutral-500">
            server가 보낸 endpointPriority 안에서만 정렬하고, p95/p99나 raw path를 만들지 않습니다.
          </p>
        </div>
        {items.length > 0 && (
          <div className="flex flex-wrap items-center gap-2 text-[11px] text-neutral-500">
            <select
              aria-label="endpoint evidence sort"
              className="h-8 border border-neutral-300 bg-white px-2 text-[11px] text-neutral-800"
              onChange={(event) => setSortKey(event.target.value as EndpointPrioritySortKey)}
              value={sortKey}
            >
              <option value="server">server rank</option>
              <option value="request">requestCount desc</option>
              <option value="error">errorRate desc</option>
              <option disabled={!slowSortAvailable} value="slow">
                {slowSortAvailable ? "slowShare desc" : "slowShare 미제공"}
              </option>
            </select>
            {limitOptions.length > 1 && (
              <select
                aria-label="endpoint evidence limit"
                className="h-8 border border-neutral-300 bg-white px-2 text-[11px] text-neutral-800"
                onChange={(event) => setLimit(Number(event.target.value))}
                value={String(effectiveLimit)}
              >
                {limitOptions.map((option) => (
                  <option key={option} value={option}>
                    max {option}
                  </option>
                ))}
              </select>
            )}
            <span>received {items.length}</span>
          </div>
        )}
      </div>
      {items.length === 0 ? (
        <div className="p-3 text-[12px] leading-5 text-neutral-500">{endpointPriorityEmptyCopy(dashboard)}</div>
      ) : (
        <ol className="divide-y divide-neutral-100">
          {visibleItems.map((item) => (
            <EndpointPriorityRow
              item={item}
              key={`${item.rank}-${item.endpointKey}`}
              maxErrorRate={maxErrorRate}
              maxRequestCount={maxRequestCount}
              maxSlowShare={maxSlowShare}
            />
          ))}
        </ol>
      )}
    </div>
  );
}

function EndpointPriorityRow({
  item,
  maxErrorRate,
  maxRequestCount,
  maxSlowShare,
}: {
  item: EndpointPriorityItem;
  maxErrorRate: number;
  maxRequestCount: number;
  maxSlowShare: number;
}) {
  return (
    <li className="p-3">
      <div className="grid gap-3 md:grid-cols-[32px_minmax(0,1fr)_minmax(220px,0.9fr)] md:items-start">
        <span className="grid h-8 w-8 place-items-center border border-neutral-900 bg-neutral-900 text-[12px] text-white tabular-nums">
          {item.rank}
        </span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="truncate text-[13px] font-medium text-neutral-950">{item.endpointKey}</span>
            <StatusBadge className={endpointPriorityBadgeClassName(item)}>{endpointPriorityBadgeText(item)}</StatusBadge>
          </div>
          <div className="mt-1 text-[12px] text-neutral-700">{item.recommendedAction}</div>
          <div className="mt-1 text-[11px] text-neutral-500">
            {humanizeStatusCode(item.reason)} · {item.ruleIds.length > 0 ? item.ruleIds.join(", ") : "rule 없음"} · server rank {item.rank}
          </div>
        </div>
        <div className="grid gap-1.5 text-[11px] text-neutral-500">
          <EndpointMetricBar
            label="request"
            tone="neutral"
            value={formatCount(item.evidence.requestCount)}
            width={ratioWidth(item.evidence.requestCount, maxRequestCount)}
          />
          <EndpointMetricBar
            label="error"
            tone="danger"
            value={`${formatCount(item.evidence.errorCount)} · ${formatRatio(item.evidence.errorRate)}`}
            width={ratioWidth(item.evidence.errorRate, maxErrorRate)}
          />
          <EndpointMetricBar
            label="slow >500ms"
            tone="hot"
            unavailable={item.evidence.slowShare === null}
            value={formatNullableRatio(item.evidence.slowShare)}
            width={ratioWidth(item.evidence.slowShare ?? 0, maxSlowShare)}
          />
        </div>
      </div>
      <div className="mt-3 grid gap-2 text-[11px] text-neutral-500 md:grid-cols-[minmax(0,1fr)_minmax(220px,0.55fr)]">
        <EndpointBucketStrip buckets={item.evidence.durationBuckets} source={item.evidence.bucketDistributionSource} />
        <div className="grid grid-cols-2 gap-2">
          <InfoCell label="마지막 관측" value={formatOptionalDateTime(item.freshness.lastObservedAt)} />
          <InfoCell label="근거 상태" value={`${humanizeStatusCode(item.evidence.errorEvidenceStatus)} / ${humanizeStatusCode(item.evidence.latencyEvidenceStatus)}`} />
        </div>
      </div>
    </li>
  );
}

function EndpointMetricBar({
  label,
  tone,
  unavailable = false,
  value,
  width,
}: {
  label: string;
  tone: "danger" | "hot" | "neutral";
  unavailable?: boolean;
  value: string;
  width: number;
}) {
  const fillClassName = tone === "danger" ? "bg-red-500" : tone === "hot" ? "bg-amber-700" : "bg-neutral-500";
  return (
    <div className="grid grid-cols-[68px_minmax(72px,1fr)_72px] items-center gap-2">
      <span className="truncate uppercase">{label}</span>
      <span className={`h-1.5 bg-neutral-100 ${unavailable ? "border border-dashed border-neutral-300 bg-white" : ""}`}>
        <span className={`block h-full ${fillClassName}`} style={{ width: `${unavailable ? 0 : width}%` }} />
      </span>
      <span className="truncate text-right text-neutral-700">{unavailable ? "미제공" : value}</span>
    </div>
  );
}

function EndpointBucketStrip({ buckets, source }: { buckets: HistogramBucket[] | null; source: string }) {
  if (!buckets || buckets.length === 0) {
    return (
      <div className="border border-neutral-200 bg-neutral-50 p-2">
        duration buckets 미제공 · endpoint p95/p99로 해석하지 않습니다.
      </div>
    );
  }
  const maxCount = Math.max(0, ...buckets.map((bucket) => bucket.count));
  return (
    <div className="border border-neutral-200 bg-neutral-50 p-2">
      <div className="mb-1 text-neutral-500">duration buckets · {humanizeSourceCode(source)}</div>
      <div className="flex h-8 items-end gap-1">
        {buckets.slice(0, 8).map((bucket) => (
          <span
            aria-label={`<= ${bucket.leMs}ms: ${bucket.count}`}
            className="min-w-3 flex-1 bg-neutral-300"
            key={`${bucket.leMs}-${bucket.count}`}
            style={{ height: `${Math.max(8, ratioWidth(bucket.count, maxCount))}%` }}
            title={`<= ${bucket.leMs}ms · ${formatCount(bucket.count)}`}
          />
        ))}
      </div>
    </div>
  );
}

function endpointPriorityBadgeText(item: EndpointPriorityItem): string {
  return item.rank === 1 ? "FIRST LOOK" : "ATTENTION";
}

function endpointPriorityBadgeClassName(item: EndpointPriorityItem): string {
  if (item.rank === 1) {
    return "border-neutral-900 bg-neutral-900 text-white";
  }
  if (item.evidence.errorCount > 0 || item.evidence.errorRate > 0) {
    return "border-red-200 bg-red-50 text-red-700";
  }
  if ((item.evidence.slowShare ?? 0) > 0) {
    return "border-amber-200 bg-amber-50 text-amber-700";
  }
  return "border-neutral-300 bg-neutral-50 text-neutral-700";
}

function sortEndpointPriorityItems(items: EndpointPriorityItem[], sortKey: EndpointPrioritySortKey): EndpointPriorityItem[] {
  const sorted = [...items];
  sorted.sort((left, right) => {
    if (sortKey === "request") {
      return right.evidence.requestCount - left.evidence.requestCount || left.rank - right.rank;
    }
    if (sortKey === "error") {
      return right.evidence.errorRate - left.evidence.errorRate || right.evidence.errorCount - left.evidence.errorCount || left.rank - right.rank;
    }
    if (sortKey === "slow") {
      return (right.evidence.slowShare ?? -1) - (left.evidence.slowShare ?? -1) || left.rank - right.rank;
    }
    return left.rank - right.rank;
  });
  return sorted;
}

function endpointLimitOptions(itemCount: number): number[] {
  if (itemCount <= 0) {
    return [];
  }
  return Array.from(new Set([3, 5, itemCount].filter((option) => option > 0 && option <= itemCount))).sort((left, right) => left - right);
}

function endpointPriorityEmptyCopy(dashboard: DashboardPresentation): string {
  const red = dashboard.signals.red;
  const hasRedSignal = red.errorCount > 0 || (red.errorRate ?? 0) > 0 || (red.slowShareOver500ms ?? 0) > 0;
  if (hasRedSignal) {
    return "RED signal은 관찰됐지만 server endpointPriority[]가 비어 있습니다. endpoint breakdown 미수집, read model 생성 불가, 또는 현재 window의 evidence 제한으로 봐야 하며 '문제 endpoint 없음'으로 해석하지 않습니다.";
  }
  return "server가 제공한 endpoint evidence 후보가 없습니다. endpoint breakdown을 새로 계산하거나 raw path/query를 표시하지 않습니다.";
}

function ratioWidth(value: number, max: number): number {
  if (!Number.isFinite(value) || !Number.isFinite(max) || value <= 0 || max <= 0) {
    return 0;
  }
  return Math.min(100, Math.max(6, (value / max) * 100));
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
  openAction,
}: {
  dashboard: DashboardPresentation;
  openAction: InstanceSummaryOpenAction;
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
            // 과거 snapshot은 InstanceEntry.summary(D5에서 추가) 이전 스키마일 수 있어 summary가 없을 수 있다.
            const summary = instance.summary as typeof instance.summary | undefined;
            const target = {
              applicationId: dashboard.application.applicationId,
              evidenceLink: instance.links.evidence,
              instanceId: instance.instanceId,
              instanceName: instance.instanceName,
              projectId: dashboard.application.projectId,
            };
            const openInstanceDashboard = () => {
              if (openAction.mode === "snapshot") {
                openAction.open({
                  ...target,
                  snapshotDetailLink: openAction.snapshotDetailLink,
                  snapshotId: openAction.snapshotId,
                });
                return;
              }
              openAction.open(target);
            };
            return (
              <li key={instance.instanceId} className="grid gap-3 border border-neutral-200 p-3 md:grid-cols-[minmax(180px,1fr)_minmax(220px,0.85fr)_auto] md:items-center">
                <div>
                  <div className="text-[13px] text-neutral-900">{instance.instanceName}</div>
                  <div className="mt-0.5 text-[11px] text-neutral-500">
                    {summary
                      ? `${instanceObservationText(summary.observationStatus)} · heartbeat ${instanceHeartbeatText(summary.starterConnection)}`
                      : `마지막 관찰 ${formatOptionalDateTime(instance.lastSeenAt)}`}
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-1.5 text-[11px] text-neutral-500">
                  {summary ? (
                    <>
                      <InfoCell label="requests" value={formatCount(summary.red.requestCount)} />
                      <InfoCell label="slow >500ms" value={formatNullableRatio(summary.red.slowShareOver500ms)} />
                    </>
                  ) : (
                    <div className="col-span-2 text-[11px] text-neutral-400">이 시점 snapshot에는 instance summary가 저장되지 않았습니다.</div>
                  )}
                </div>
                <div className="flex flex-wrap items-center justify-start gap-2 text-[11px] text-neutral-700 md:justify-end">
                  {summary && (
                    <StatusBadge className={instanceContributionBadgeClassName(summary.applicationContribution.level)}>
                      {humanizeStatusCode(summary.applicationContribution.level)}
                    </StatusBadge>
                  )}
                  <Button variant="outline" size="sm" className="h-8 border-neutral-300" onClick={openInstanceDashboard}>
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

function instanceObservationText(
  observationStatus: DashboardPresentation["instances"][number]["summary"]["observationStatus"],
): string {
  const observedAt = formatOptionalDateTime(observationStatus.lastObservedBucketEndUtc);
  if (observationStatus.code === "observed") {
    return `observed ${observedAt}`;
  }
  return `${humanizeStatusCode(observationStatus.code)} ${observedAt}`;
}

function instanceHeartbeatText(
  starterConnection: DashboardPresentation["instances"][number]["summary"]["starterConnection"],
): string {
  if (!starterConnection.lastHeartbeatAt) {
    return humanizeStatusCode(starterConnection.lastHeartbeatStatus);
  }
  return `${humanizeStatusCode(starterConnection.lastHeartbeatStatus)} ${formatOptionalDateTime(starterConnection.lastHeartbeatAt)}`;
}

function instanceContributionBadgeClassName(level: string): string {
  switch (level) {
    case "contributing":
    case "attention":
      return "border-amber-500 bg-amber-50 text-amber-900";
    case "supporting":
      return "border-sky-400 bg-sky-50 text-sky-800";
    case "none":
      return "border-neutral-300 bg-white text-neutral-700";
    case "insufficient":
      return "border-neutral-400 bg-neutral-50 text-neutral-700";
    default:
      return statusBadgeClassName(level);
  }
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
    <div className="min-w-0 border border-neutral-200 bg-white p-2" title={`${label}: ${value}`}>
      <div className="text-neutral-500">{label}</div>
      <div className="mt-0.5 truncate text-neutral-900">{value}</div>
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
