// static dashboard runtime은 서버가 제공한 read model link와 field를 표시 계층에서만 소비한다.
const projectList = document.querySelector('#project-list');
const reloadButton = document.querySelector('#reload-projects');
const filterInput = document.querySelector('#project-filter');
const githubButton = document.querySelector('#github-login');
const authStatus = document.querySelector('#auth-status');
const generatedAtLabel = document.querySelector('#projects-generated-at');
const applicationList = document.querySelector('#application-list');
const applicationFilterInput = document.querySelector('#application-filter');
const applicationReloadButton = document.querySelector('#reload-applications');
const applicationsGeneratedAtLabel = document.querySelector('#applications-generated-at');
const selectedProjectLabel = document.querySelector('#selected-project-label');
const dashboardDetail = document.querySelector('#dashboard-detail');
const dashboardGeneratedAtLabel = document.querySelector('#dashboard-generated-at');
const selectedApplicationLabel = document.querySelector('#selected-application-label');

const VIEW_STATE = Object.freeze({
  LOADING: 'loading',
  AUTH_REQUIRED: 'auth-required',
  ERROR: 'error',
  EMPTY: 'empty',
  READY: 'ready',
  FILTERED_EMPTY: 'filtered-empty'
});

const APPLICATION_VIEW_STATE = Object.freeze({
  IDLE: 'idle',
  LOADING: 'loading',
  AUTH_REQUIRED: 'auth-required',
  INVALID_LINK: 'invalid-link',
  ERROR: 'error',
  PROJECT_NOT_FOUND: 'project-not-found',
  EMPTY: 'empty',
  READY: 'ready',
  FILTERED_EMPTY: 'filtered-empty'
});

const DASHBOARD_VIEW_STATE = Object.freeze({
  IDLE: 'idle',
  LOADING: 'loading',
  AUTH_REQUIRED: 'auth-required',
  INVALID_LINK: 'invalid-link',
  NOT_FOUND: 'not-found',
  ERROR: 'error',
  READY: 'ready'
});

const INSTANCE_EVIDENCE_VIEW_STATE = Object.freeze({
  IDLE: 'idle',
  LOADING: 'loading',
  AUTH_REQUIRED: 'auth-required',
  INVALID_LINK: 'invalid-link',
  NOT_FOUND: 'not-found',
  ERROR: 'error',
  READY: 'ready'
});

let serviceAccessToken = null;
let loadedProjects = [];
let loadedGeneratedAt = null;
let currentViewState = VIEW_STATE.LOADING;
let projectRequestSequence = 0;
let selectedProjectContext = null;
let loadedApplications = [];
let loadedApplicationsGeneratedAt = null;
let loadedApplicationsProject = null;
let currentApplicationViewState = APPLICATION_VIEW_STATE.AUTH_REQUIRED;
let applicationRequestSequence = 0;
let selectedDashboardContext = null;
let loadedDashboard = null;
let currentDashboardViewState = DASHBOARD_VIEW_STATE.AUTH_REQUIRED;
let dashboardRequestSequence = 0;
let selectedInstanceEvidenceContext = null;
let loadedInstanceEvidence = null;
let currentInstanceEvidenceViewState = INSTANCE_EVIDENCE_VIEW_STATE.AUTH_REQUIRED;
let instanceEvidenceRequestSequence = 0;

window.observationPortalAuth = Object.freeze({
  setAccessToken(accessToken) {
    serviceAccessToken = normalizeAccessToken(accessToken);
    projectRequestSequence += 1;
    applicationRequestSequence += 1;
    dashboardRequestSequence += 1;
    instanceEvidenceRequestSequence += 1;
    clearProjectSnapshot({ resetFilter: true });
    clearApplicationSnapshot({ resetFilter: true, resetSelection: true });
    clearDashboardSnapshot({ resetSelection: true });
    clearInstanceEvidenceSnapshot({ resetSelection: true });
    if (!serviceAccessToken) {
      renderAuthorizationRequired();
      renderApplicationAuthorizationRequired();
      renderDashboardAuthorizationRequired();
      return;
    }
    renderApplicationIdle();
    renderDashboardIdle();
    loadProjects();
  },
  clearAccessToken() {
    handleAuthorizationLoss();
  }
});

async function loadProjects() {
  const requestId = ++projectRequestSequence;
  discardInstanceEvidenceForParentReload();
  if (!serviceAccessToken) {
    clearProjectSnapshot({ resetFilter: true });
    renderAuthorizationRequired();
    return;
  }
  renderLoadingState();
  try {
    const response = await fetch('/api/projects', { headers: projectRequestHeaders() });
    if (!isLatestProjectRequest(requestId)) {
      return;
    }
    if (response.status === 401) {
      handleAuthorizationLoss();
      return;
    }
    if (!response.ok) {
      throw new Error('project_load_failed');
    }
    const data = await response.json();
    if (!isLatestProjectRequest(requestId)) {
      return;
    }
    loadedProjects = Array.isArray(data.projects) ? data.projects : [];
    loadedGeneratedAt = data.generatedAt;
    reconcileSelectedProjectAfterProjectLoad();
    renderProjects();
  } catch (error) {
    if (!isLatestProjectRequest(requestId)) {
      return;
    }
    clearProjectSnapshot();
    renderProjectLoadError();
  }
}

function renderLoadingState() {
  setProjectViewState(VIEW_STATE.LOADING);
  setGeneratedAtLabel('목록을 불러오는 중');
  projectList.innerHTML = `
    <div class="loading-state" aria-label="Project 목록 로딩 중">
      <div class="skeleton-line"></div>
      <div class="skeleton-line short"></div>
    </div>
  `;
}

function renderAuthorizationRequired() {
  setProjectViewState(VIEW_STATE.AUTH_REQUIRED);
  setGeneratedAtLabel('로그인이 필요합니다');
  projectList.innerHTML = `
    <div class="empty-state">
      <p><strong>GitHub 로그인 후 Project 목록을 볼 수 있습니다.</strong>로그인을 완료한 뒤 다시 시도해 주세요.</p>
    </div>
  `;
}

function renderProjectLoadError() {
  setProjectViewState(VIEW_STATE.ERROR);
  setGeneratedAtLabel('목록 기준 시각 확인 불가');
  projectList.innerHTML = `
    <div class="empty-state">
      <p><strong>Project 목록을 불러오지 못했습니다.</strong>잠시 후 다시 시도해 주세요.</p>
    </div>
  `;
}

function renderProjects() {
  setGeneratedAtLabel(formatGeneratedAt(loadedGeneratedAt));
  if (loadedProjects.length === 0) {
    setProjectViewState(VIEW_STATE.EMPTY);
    projectList.innerHTML = `
      <div class="empty-state">
        <p><strong>Project가 아직 없습니다.</strong>local/internal seed 또는 admin bootstrap decision이 필요합니다.</p>
      </div>
    `;
    return;
  }

  const visibleProjects = filterProjects(loadedProjects, filterInput.value);
  if (visibleProjects.length === 0) {
    setProjectViewState(VIEW_STATE.FILTERED_EMPTY);
    projectList.innerHTML = `
      <div class="empty-state compact">
        <p><strong>표시할 Project가 없습니다.</strong>필터 값을 조정하거나 Project 목록을 다시 불러와 주세요.</p>
      </div>
    `;
    return;
  }

  setProjectViewState(VIEW_STATE.READY);
  projectList.innerHTML = visibleProjects.map(project => projectMarkup(project)).join('');
}

function filterProjects(projects, filterText) {
  const query = String(filterText ?? '').trim().toLocaleLowerCase('ko-KR');
  if (query.length === 0) {
    return projects;
  }
  return projects.filter(project => String(project.name ?? '').toLocaleLowerCase('ko-KR').includes(query));
}

function projectMarkup(project) {
  const applicationsLink = project.links && project.links.applications ? String(project.links.applications) : '';
  const applicationsLinkValid = Boolean(safeApplicationsLink(project));
  const exposedApplicationsLink = applicationsLinkValid ? applicationsLink : '';
  const recentConcern = project.recentConcern ? escapeText(project.recentConcern.label) : '최근 concern 없음';
  const issueCount = Number.isFinite(project.setupConnectionIssueCount) ? project.setupConnectionIssueCount : 0;
  const issueClass = issueCount > 0 ? 'badge attention' : 'badge';
  const actionState = applicationsLinkValid ? 'ready-application-list' : 'missing-applications-link';
  const actionLabel = applicationsLinkValid
    ? 'Application 목록 불러오기'
    : 'Application List link를 확인할 수 없습니다.';

  return `
    <article class="project-item">
      <div class="project-title">
        <div>
          <p class="project-name">${escapeText(project.name)}</p>
          <p class="project-id">${escapeText(project.projectId)}</p>
        </div>
      </div>
      <div class="project-meta">
        <span class="badge">Applications ${escapeText(project.applicationCount)}</span>
        <span class="${issueClass}">Connection/setup candidates ${escapeText(issueCount)}</span>
        <span class="badge">${recentConcern}</span>
      </div>
      <div class="project-actions">
        <button class="link-button" type="button" data-action-state="${actionState}" data-project-id="${escapeAttribute(project.projectId)}" data-project-name="${escapeAttribute(project.name)}" data-applications-link="${escapeAttribute(exposedApplicationsLink)}" aria-label="${escapeAttribute(actionLabel)}" title="${escapeAttribute(actionLabel)}">Applications</button>
      </div>
    </article>
  `;
}

function safeApplicationsLink(project) {
  const applicationsLink = project.links && project.links.applications ? String(project.links.applications) : '';
  return isProjectApplicationsLink(applicationsLink, project.projectId) ? applicationsLink : null;
}

function handleProjectListClick(event) {
  const action = event.target.closest('[data-applications-link]');
  if (!action) {
    return;
  }
  selectProjectApplications({
    projectId: action.dataset.projectId,
    projectName: action.dataset.projectName,
    applicationsLink: action.dataset.applicationsLink
  });
}

function selectProjectApplications(projectContext) {
  applicationRequestSequence += 1;
  dashboardRequestSequence += 1;
  instanceEvidenceRequestSequence += 1;
  selectedProjectContext = {
    projectId: String(projectContext.projectId ?? ''),
    projectName: String(projectContext.projectName ?? ''),
    applicationsLink: String(projectContext.applicationsLink ?? '')
  };
  clearApplicationSnapshot({ resetFilter: true });
  clearDashboardSnapshot({ resetSelection: true });
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  renderDashboardIdle();
  if (!isProjectApplicationsLink(selectedProjectContext.applicationsLink, selectedProjectContext.projectId)) {
    renderApplicationInvalidLink();
    return;
  }
  loadApplicationsForSelectedProject();
}

async function loadApplicationsForSelectedProject() {
  const requestId = ++applicationRequestSequence;
  dashboardRequestSequence += 1;
  instanceEvidenceRequestSequence += 1;
  clearDashboardSnapshot({ resetSelection: true });
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  renderDashboardIdle();
  if (!selectedProjectContext) {
    renderApplicationIdle();
    return;
  }
  if (!serviceAccessToken) {
    clearApplicationSnapshot({ resetFilter: true });
    renderApplicationAuthorizationRequired();
    return;
  }
  if (!isProjectApplicationsLink(selectedProjectContext.applicationsLink, selectedProjectContext.projectId)) {
    renderApplicationInvalidLink();
    return;
  }
  renderApplicationLoadingState();
  try {
    const response = await fetch(selectedProjectContext.applicationsLink, { headers: projectRequestHeaders() });
    if (!isLatestApplicationRequest(requestId)) {
      return;
    }
    if (response.status === 401) {
      handleAuthorizationLoss();
      return;
    }
    if (response.status === 404) {
      clearApplicationSnapshot();
      renderApplicationProjectNotFound();
      return;
    }
    if (!response.ok) {
      throw new Error('application_load_failed');
    }
    const data = await response.json();
    if (!isLatestApplicationRequest(requestId)) {
      return;
    }
    if (!isSelectedProjectApplicationResponse(data)) {
      throw new Error('application_project_mismatch');
    }
    if (!Array.isArray(data.applications) || !hasValidApplicationItems(data.applications, data.project.projectId)) {
      throw new Error('application_payload_malformed');
    }
    loadedApplications = data.applications;
    loadedApplicationsGeneratedAt = data.generatedAt;
    loadedApplicationsProject = {
      projectId: data.project.projectId,
      name: data.project.name
    };
    renderApplications();
  } catch (error) {
    if (!isLatestApplicationRequest(requestId)) {
      return;
    }
    clearApplicationSnapshot();
    renderApplicationLoadError();
  }
}

function renderApplicationIdle() {
  setApplicationViewState(APPLICATION_VIEW_STATE.IDLE);
  setApplicationsGeneratedAtLabel('Application 목록 기준 시각 대기 중');
  setSelectedProjectLabel('Project를 선택하면 Application 목록을 볼 수 있습니다.');
  applicationList.innerHTML = `
    <div class="empty-state">
      <p><strong>Project를 먼저 선택해 주세요.</strong>선택한 Project의 Application 목록만 인증 요청으로 불러옵니다.</p>
    </div>
  `;
}

function renderApplicationAuthorizationRequired() {
  setApplicationViewState(APPLICATION_VIEW_STATE.AUTH_REQUIRED);
  setApplicationsGeneratedAtLabel('로그인이 필요합니다');
  setSelectedProjectLabel('로그인이 필요합니다');
  applicationList.innerHTML = `
    <div class="empty-state">
      <p><strong>GitHub 로그인 후 Application 목록을 볼 수 있습니다.</strong>로그인을 완료한 뒤 Project를 선택해 주세요.</p>
    </div>
  `;
}

function renderApplicationInvalidLink() {
  setApplicationViewState(APPLICATION_VIEW_STATE.INVALID_LINK);
  setApplicationsGeneratedAtLabel('Application 목록 기준 시각 확인 불가');
  setSelectedProjectLabel(selectedProjectText());
  applicationList.innerHTML = `
    <div class="empty-state">
      <p><strong>Application List link를 확인할 수 없습니다.</strong>서버가 준 내부 Application List link만 사용할 수 있습니다.</p>
    </div>
  `;
}

function renderApplicationLoadingState() {
  setApplicationViewState(APPLICATION_VIEW_STATE.LOADING);
  setApplicationsGeneratedAtLabel('Application 목록을 불러오는 중');
  setSelectedProjectLabel(selectedProjectText());
  applicationList.innerHTML = `
    <div class="loading-state" aria-label="Application 목록 로딩 중">
      <div class="skeleton-line"></div>
      <div class="skeleton-line short"></div>
    </div>
  `;
}

function renderApplicationProjectNotFound() {
  setApplicationViewState(APPLICATION_VIEW_STATE.PROJECT_NOT_FOUND);
  setApplicationsGeneratedAtLabel('Application 목록 기준 시각 확인 불가');
  setSelectedProjectLabel(selectedProjectText());
  applicationList.innerHTML = `
    <div class="empty-state">
      <p><strong>Project를 찾을 수 없습니다.</strong>Project catalog와 Application source를 다시 확인해 주세요.</p>
    </div>
  `;
}

function renderApplicationLoadError() {
  setApplicationViewState(APPLICATION_VIEW_STATE.ERROR);
  setApplicationsGeneratedAtLabel('Application 목록 기준 시각 확인 불가');
  setSelectedProjectLabel(selectedProjectText());
  applicationList.innerHTML = `
    <div class="empty-state">
      <p><strong>Application 목록을 불러오지 못했습니다.</strong>잠시 후 다시 시도해 주세요.</p>
    </div>
  `;
}

function renderApplications() {
  setApplicationsGeneratedAtLabel(formatGeneratedAt(loadedApplicationsGeneratedAt));
  setSelectedProjectLabel(applicationProjectText());
  if (loadedApplications.length === 0) {
    setApplicationViewState(APPLICATION_VIEW_STATE.EMPTY);
    applicationList.innerHTML = `
      <div class="empty-state">
        <p><strong>표시할 Application이 없습니다.</strong>catalog 또는 accepted bucket source가 아직 비어 있습니다.</p>
      </div>
    `;
    return;
  }

  const visibleApplications = filterApplications(loadedApplications, applicationFilterInput.value);
  if (visibleApplications.length === 0) {
    setApplicationViewState(APPLICATION_VIEW_STATE.FILTERED_EMPTY);
    applicationList.innerHTML = `
      <div class="empty-state compact">
        <p><strong>표시할 Application이 없습니다.</strong>필터 값은 이미 받은 Application 목록의 표시 범위에만 적용됩니다.</p>
      </div>
    `;
    return;
  }

  setApplicationViewState(APPLICATION_VIEW_STATE.READY);
  applicationList.innerHTML = visibleApplications.map(application => applicationMarkup(application)).join('');
}

function filterApplications(applications, filterText) {
  const query = String(filterText ?? '').trim().toLocaleLowerCase('ko-KR');
  if (query.length === 0) {
    return applications;
  }
  return applications.filter(application => {
    const name = String(application.name ?? '').toLocaleLowerCase('ko-KR');
    const environment = String(application.environment ?? '').toLocaleLowerCase('ko-KR');
    return name.includes(query) || environment.includes(query);
  });
}

function applicationMarkup(application) {
  const metricData = application.metricData || {};
  const starterConnection = application.starterConnection || {};
  const lifecycleBadge = application.lifecycleBadge || {};
  const rawDashboardLink = application.links && application.links.dashboard ? application.links.dashboard : '';
  const dashboardLink = safeDashboardLink(application, currentApplicationProjectId(), rawDashboardLink);
  const dashboardActionAttributes = dashboardLink
    ? `data-action-state="ready-dashboard" data-application-id="${escapeAttribute(application.applicationId)}" data-application-name="${escapeAttribute(application.name)}" data-application-environment="${escapeAttribute(application.environment)}" data-dashboard-link="${escapeAttribute(dashboardLink)}" aria-label="Application Dashboard 열기" title="Application Dashboard 열기"`
    : `disabled aria-disabled="true" data-action-state="missing-dashboard-link" data-application-id="${escapeAttribute(application.applicationId)}" data-application-name="${escapeAttribute(application.name)}" data-application-environment="${escapeAttribute(application.environment)}" data-dashboard-link="" aria-label="Dashboard link를 확인할 수 없습니다." title="Dashboard link를 확인할 수 없습니다."`;

  return `
    <article class="application-item">
      <div class="application-title">
        <div>
          <p class="application-name">${escapeText(application.name)}</p>
          <p class="application-id">${escapeText(application.applicationId)}</p>
        </div>
        <span class="badge">${escapeText(application.environment)}</span>
      </div>
      <div class="application-meta">
        <span class="badge lifecycle-badge">server-computed light badge · ${escapeText(lifecycleBadge.source)} · ${escapeText(lifecycleBadge.code)} · ${escapeText(lifecycleBadge.label)}</span>
      </div>
      <div class="application-axes">
        ${axisMarkup('Accepted bucket', [
          ['source', metricData.statusSource, 'accepted bucket source absence'],
          ['last accepted bucket', metricData.lastAcceptedBucketAt, 'last accepted bucket absence'],
          ['freshness', metricData.freshnessLabel, 'accepted bucket freshness absence']
        ])}
        ${axisMarkup('Starter connection', [
          ['source', starterConnection.statusSource, 'starter heartbeat source absence'],
          ['last heartbeat', starterConnection.lastHeartbeatAt, 'last heartbeat absence'],
          ['heartbeat', starterConnection.heartbeatStatus, 'heartbeat status absence'],
          ['freshness', starterConnection.freshnessLabel, 'starter heartbeat freshness absence'],
          ['meaning', starterConnection.connectionMeaning, 'connection meaning absence'],
          ['state impact', starterConnection.stateImpact, 'state impact absence']
        ])}
      </div>
      ${topConcernMarkup(application.topConcern)}
      <div class="application-actions">
        <button class="link-button dashboard-handoff" type="button" ${dashboardActionAttributes}>Dashboard</button>
      </div>
    </article>
  `;
}

function handleApplicationListClick(event) {
  const action = event.target.closest('[data-dashboard-link]');
  if (!action) {
    return;
  }
  if (action.disabled || action.getAttribute && action.getAttribute('aria-disabled') === 'true') {
    return;
  }
  selectApplicationDashboard({
    applicationId: action.dataset.applicationId,
    applicationName: action.dataset.applicationName,
    applicationEnvironment: action.dataset.applicationEnvironment,
    dashboardLink: action.dataset.dashboardLink
  });
}

function selectApplicationDashboard(dashboardContext) {
  dashboardRequestSequence += 1;
  instanceEvidenceRequestSequence += 1;
  selectedDashboardContext = {
    projectId: currentApplicationProjectId(),
    projectName: currentApplicationProjectName(),
    applicationId: String(dashboardContext.applicationId ?? ''),
    applicationName: String(dashboardContext.applicationName ?? ''),
    applicationEnvironment: String(dashboardContext.applicationEnvironment ?? ''),
    dashboardLink: String(dashboardContext.dashboardLink ?? '')
  };
  loadedDashboard = null;
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  if (!serviceAccessToken) {
    renderDashboardAuthorizationRequired();
    return;
  }
  if (!isApplicationDashboardLink(
    selectedDashboardContext.dashboardLink,
    selectedDashboardContext.projectId,
    selectedDashboardContext.applicationId
  )) {
    renderDashboardInvalidLink();
    return;
  }
  loadDashboardForSelectedApplication();
}

async function loadDashboardForSelectedApplication() {
  const requestId = ++dashboardRequestSequence;
  instanceEvidenceRequestSequence += 1;
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  if (!selectedDashboardContext) {
    renderDashboardIdle();
    return;
  }
  if (!serviceAccessToken) {
    clearDashboardSnapshot();
    renderDashboardAuthorizationRequired();
    return;
  }
  if (!isApplicationDashboardLink(
    selectedDashboardContext.dashboardLink,
    selectedDashboardContext.projectId,
    selectedDashboardContext.applicationId
  )) {
    renderDashboardInvalidLink();
    return;
  }
  renderDashboardLoadingState();
  try {
    const response = await fetch(selectedDashboardContext.dashboardLink, { headers: projectRequestHeaders() });
    if (!isLatestDashboardRequest(requestId)) {
      return;
    }
    if (response.status === 401) {
      handleAuthorizationLoss();
      return;
    }
    if (response.status === 404) {
      clearDashboardSnapshot();
      renderDashboardNotFound();
      return;
    }
    if (!response.ok) {
      throw new Error('dashboard_load_failed');
    }
    const dashboard = await response.json();
    if (!isLatestDashboardRequest(requestId)) {
      return;
    }
    if (!isSelectedApplicationDashboardResponse(dashboard)) {
      throw new Error('dashboard_context_mismatch');
    }
    loadedDashboard = dashboard;
    renderDashboardReady();
  } catch (error) {
    if (!isLatestDashboardRequest(requestId)) {
      return;
    }
    clearDashboardSnapshot();
    renderDashboardLoadError();
  }
}

function renderDashboardIdle() {
  setDashboardViewState(DASHBOARD_VIEW_STATE.IDLE);
  setDashboardGeneratedAtLabel('Dashboard 기준 시각 대기 중');
  setSelectedApplicationLabel('Project를 선택하면 Application Dashboard를 볼 수 있습니다.');
  dashboardDetail.innerHTML = `
    <div class="empty-state">
      <p><strong>Application을 선택해 주세요.</strong>Project를 선택하면 Application Dashboard를 볼 수 있습니다. Application List의 Dashboard action만 인증 요청으로 연결합니다.</p>
    </div>
  `;
}

function renderDashboardAuthorizationRequired() {
  setDashboardViewState(DASHBOARD_VIEW_STATE.AUTH_REQUIRED);
  setDashboardGeneratedAtLabel('로그인이 필요합니다');
  setSelectedApplicationLabel('로그인이 필요합니다');
  dashboardDetail.innerHTML = `
    <div class="empty-state">
      <p><strong>GitHub 로그인 후 Dashboard를 볼 수 있습니다.</strong>로그인을 완료한 뒤 Project와 Application을 선택해 주세요.</p>
    </div>
  `;
}

function renderDashboardInvalidLink() {
  setDashboardViewState(DASHBOARD_VIEW_STATE.INVALID_LINK);
  setDashboardGeneratedAtLabel('Dashboard 기준 시각 확인 불가');
  setSelectedApplicationLabel(selectedApplicationText());
  dashboardDetail.innerHTML = `
    <div class="empty-state">
      <p><strong>Dashboard link를 확인할 수 없습니다.</strong>현재 Project/Application과 일치하는 내부 Dashboard link만 사용할 수 있습니다.</p>
    </div>
  `;
}

function renderDashboardLoadingState() {
  setDashboardViewState(DASHBOARD_VIEW_STATE.LOADING);
  setDashboardGeneratedAtLabel('Dashboard를 불러오는 중');
  setSelectedApplicationLabel(selectedApplicationText());
  dashboardDetail.innerHTML = `
    <div class="loading-state" aria-label="Application Dashboard 로딩 중">
      <p class="dashboard-empty-copy">Dashboard를 불러오는 중</p>
      <div class="skeleton-line"></div>
      <div class="skeleton-line short"></div>
    </div>
  `;
}

function renderDashboardNotFound() {
  setDashboardViewState(DASHBOARD_VIEW_STATE.NOT_FOUND);
  setDashboardGeneratedAtLabel('Dashboard 기준 시각 확인 불가');
  setSelectedApplicationLabel(selectedApplicationText());
  dashboardDetail.innerHTML = `
    <div class="empty-state">
      <p><strong>Project/Application scope를 찾을 수 없습니다.</strong>catalog scope 또는 접근 가능한 Application 범위를 다시 확인해 주세요.</p>
    </div>
  `;
}

function renderDashboardLoadError() {
  setDashboardViewState(DASHBOARD_VIEW_STATE.ERROR);
  setDashboardGeneratedAtLabel('Dashboard 기준 시각 확인 불가');
  setSelectedApplicationLabel(selectedApplicationText());
  dashboardDetail.innerHTML = `
    <div class="empty-state">
      <p><strong>Dashboard를 불러오지 못했습니다.</strong>잠시 후 같은 Application에서 다시 시도해 주세요.</p>
    </div>
  `;
}

function renderDashboardReady() {
  const dashboard = loadedDashboard;
  setDashboardViewState(DASHBOARD_VIEW_STATE.READY);
  setInstanceEvidenceViewState(INSTANCE_EVIDENCE_VIEW_STATE.IDLE);
  setDashboardGeneratedAtLabel(formatDashboardGeneratedAt(dashboard.generatedAt));
  setSelectedApplicationLabel(dashboardApplicationText(dashboard.application));
  dashboardDetail.innerHTML = `
    <article class="dashboard-read-model" data-dashboard-state="ready">
      ${dashboardHeaderMarkup(dashboard)}
      <div class="dashboard-strip-grid">
        ${metricStateStripMarkup(dashboard)}
        ${starterConnectionStripMarkup(dashboard)}
      </div>
      ${zeroInsightRecoveryMarkup(dashboard)}
      ${sourceScopedPercentilesMarkup(dashboard.sourceScopedPercentiles)}
      ${histogramDistributionMarkup(dashboard.histogramDistribution)}
      ${triageCardsMarkup(dashboard.triageCards)}
      ${endpointPriorityMarkup(dashboard.endpointPriority)}
      ${instanceHandoffMarkup(dashboard.instances)}
      ${snapshotHandoffMarkup(dashboard.snapshot)}
    </article>
  `;
}

function dashboardHeaderMarkup(dashboard) {
  const application = dashboard.application || {};
  return `
    <section class="dashboard-section dashboard-identity" aria-label="Application dashboard context">
      <div>
        <p class="eyebrow">Current read model</p>
        <h3>${escapeText(valueOrAbsence(application.name, 'Application name source absence'))}</h3>
      </div>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('project id', application.projectId)}
        ${keyValueMarkup('application id', application.applicationId)}
        ${keyValueMarkup('environment', application.environment)}
        ${keyValueMarkup('generatedAt', dashboard.generatedAt)}
        ${keyValueMarkup('last accepted bucket', formatTimestamp(application.lastAcceptedBucketAt, 'accepted bucket source absence'))}
        ${keyValueMarkup('last healthy at', formatTimestamp(application.lastHealthyAt, '이전 정상 시점 없음'))}
        ${keyValueMarkup('current window', windowRangeText(application.sourceWindow && application.sourceWindow.current))}
        ${keyValueMarkup('baseline window', windowRangeText(application.sourceWindow && application.sourceWindow.baseline))}
      </dl>
    </section>
  `;
}

function metricStateStripMarkup(dashboard) {
  const application = dashboard.application || {};
  const state = dashboard.state || {};
  const freshness = application.freshness || {};
  const metrics = dashboard.metrics || {};
  return `
    <section class="dashboard-strip metric-state-strip" aria-label="Metric data state">
      <div class="strip-heading">
        <p class="eyebrow">Metric data state</p>
        <h3>${escapeText(valueOrAbsence(state.label, 'state label source absence'))}</h3>
      </div>
      <dl>
        ${keyValueMarkup('state code', state.code)}
        ${keyValueMarkup('scope', state.scope)}
        ${keyValueMarkup('rationale', state.rationale)}
        ${keyValueMarkup('recommended action', state.recommendedAction)}
        ${keyValueMarkup('freshness last observed', formatTimestamp(freshness.lastObservedAt, 'freshness source absence'))}
        ${keyValueMarkup('stale at', formatTimestamp(freshness.staleAt, 'stale threshold source absence'))}
        ${keyValueMarkup('down at', formatTimestamp(freshness.downAt, 'down threshold source absence'))}
        ${keyValueMarkup('last accepted bucket', formatTimestamp(application.lastAcceptedBucketAt, 'accepted bucket source absence'))}
        ${keyValueMarkup('current window', windowRangeText(application.sourceWindow && application.sourceWindow.current))}
        ${keyValueMarkup('request count', metrics.requestCount)}
        ${keyValueMarkup('error count', metrics.errorCount)}
        ${keyValueMarkup('error rate', metrics.errorRate)}
      </dl>
    </section>
  `;
}

function starterConnectionStripMarkup(dashboard) {
  const starterConnection = dashboard.starterConnection || {};
  return `
    <section class="dashboard-strip starter-connection-strip" aria-label="Starter connection">
      <div class="strip-heading">
        <p class="eyebrow">Starter connection</p>
        <h3>${escapeText(valueOrAbsence(starterConnection.connectionMeaning, 'connection meaning source absence'))}</h3>
      </div>
      <dl>
        ${keyValueMarkup('source', starterConnection.statusSource)}
        ${keyValueMarkup('last heartbeat', formatTimestamp(starterConnection.lastHeartbeatAt, 'starter heartbeat source absence'))}
        ${keyValueMarkup('last heartbeat status', starterConnection.lastHeartbeatStatus)}
        ${keyValueMarkup('connection meaning', starterConnection.connectionMeaning)}
        ${keyValueMarkup('state impact', starterConnection.stateImpact)}
      </dl>
    </section>
  `;
}

function zeroInsightRecoveryMarkup(dashboard) {
  const triageCards = Array.isArray(dashboard.triageCards) ? dashboard.triageCards : [];
  const zeroInsight = dashboard.zeroInsight || {};
  const recovery = dashboard.recovery || {};
  const zeroInsightMarkup = triageCards.length === 0
    ? `
      <section class="dashboard-section" aria-label="Zero insight">
        <p class="eyebrow">Zero insight</p>
        <dl class="dashboard-kv-grid">
          ${keyValueMarkup('reasonCode', zeroInsight.reasonCode)}
          ${keyValueMarkup('message', zeroInsight.message)}
          ${keyValueMarkup('recommendedAction', zeroInsight.recommendedAction)}
        </dl>
      </section>
    `
    : '';
  const recoveryState = recovery.isRecovering ? '회복 관찰 중' : '회복 관찰 안내 없음';
  const retryText = recovery.retryAfterSeconds == null
    ? '다음 판단 대기 source absence'
    : `${recovery.retryAfterSeconds}초 · 자동 예약이 아니라 다음 판단 대기`;

  return `
    ${zeroInsightMarkup}
    <section class="dashboard-section" aria-label="Recovery guidance">
      <p class="eyebrow">Recovery</p>
      <h3>${escapeText(recoveryState)}</h3>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('isRecovering', recovery.isRecovering)}
        ${keyValueMarkup('lastHealthyAt', formatTimestamp(recovery.lastHealthyAt, '이전 정상 시점 없음'))}
        ${keyValueMarkup('retryAfterSeconds', retryText)}
        ${keyValueMarkup('recommendedAction', recovery.recommendedAction)}
      </dl>
    </section>
  `;
}

function sourceScopedPercentilesMarkup(sourceScopedPercentiles) {
  const percentileBlock = sourceScopedPercentiles || {};
  const items = Array.isArray(percentileBlock.items) ? percentileBlock.items : [];
  const absenceCopy = percentileBlock.status === 'missing' || percentileBlock.status === 'insufficient'
    ? 'source absence/evidence 부족'
    : 'source-scoped starter point';
  return `
    <section class="dashboard-section" aria-label="Source scoped percentiles">
      <p class="eyebrow">Source scoped percentiles</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('source', percentileBlock.source)}
        ${keyValueMarkup('scope', percentileBlock.scope)}
        ${keyValueMarkup('displayPolicy', percentileBlock.displayPolicy)}
        ${keyValueMarkup('aggregatePolicy', percentileBlock.aggregatePolicy)}
        ${keyValueMarkup('status', percentileBlock.status)}
        ${keyValueMarkup('reason', valueOrAbsence(percentileBlock.reason, absenceCopy))}
      </dl>
      <div class="dashboard-list">
        ${items.length === 0 ? `<p class="dashboard-empty-copy">${escapeText(absenceCopy)}</p>` : items.map(percentileItemMarkup).join('')}
      </div>
    </section>
  `;
}

function percentileItemMarkup(item) {
  return `
    <article class="dashboard-mini-card">
      <h3>${escapeText(valueOrAbsence(item.instance, 'instance source absence'))}</h3>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('source', item.source)}
        ${keyValueMarkup('application', item.application)}
        ${keyValueMarkup('environment', item.environment)}
        ${keyValueMarkup('bucketStartUtc', item.bucketStartUtc)}
        ${keyValueMarkup('bucketEndUtc', item.bucketEndUtc)}
        ${keyValueMarkup('requestCount', item.requestCount)}
        ${keyValueMarkup('p95Ms', item.p95Ms)}
        ${keyValueMarkup('p99Ms', item.p99Ms)}
      </dl>
    </article>
  `;
}

function histogramDistributionMarkup(histogramDistribution) {
  const histogram = histogramDistribution || {};
  return `
    <section class="dashboard-section" aria-label="Histogram distribution evidence">
      <p class="eyebrow">Histogram distribution evidence</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('source', histogram.source)}
        ${keyValueMarkup('scope', histogram.scope)}
        ${keyValueMarkup('displayPolicy', histogram.displayPolicy)}
        ${keyValueMarkup('aggregatePolicy', histogram.aggregatePolicy)}
      </dl>
      <div class="histogram-grid">
        ${histogramWindowMarkup('current', histogram.current)}
        ${histogramWindowMarkup('baseline', histogram.baseline)}
      </div>
    </section>
  `;
}

function histogramWindowMarkup(label, window) {
  const histogramWindow = window || {};
  const buckets = Array.isArray(histogramWindow.buckets) ? histogramWindow.buckets : [];
  return `
    <article class="dashboard-mini-card">
      <h3>${escapeText(label)}</h3>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('status', histogramWindow.status)}
        ${keyValueMarkup('reason', valueOrAbsence(histogramWindow.reason, 'bucket evidence reason source absence'))}
        ${keyValueMarkup('totalCount', histogramWindow.totalCount)}
      </dl>
      <div class="bucket-list">
        ${buckets.length === 0 ? '<p class="dashboard-empty-copy">bucket distribution evidence 부족</p>' : buckets.map(bucketMarkup).join('')}
      </div>
    </article>
  `;
}

function bucketMarkup(bucket) {
  return `
    <span class="bucket-pill">
      <span>leMs ${escapeText(bucket.leMs)}</span>
      <strong>${escapeText(bucket.count)}</strong>
    </span>
  `;
}

function triageCardsMarkup(triageCards) {
  const cards = Array.isArray(triageCards) ? triageCards : [];
  return `
    <section class="dashboard-section" aria-label="Triage cards">
      <p class="eyebrow">Triage cards</p>
      <div class="dashboard-list">
        ${cards.length === 0 ? '<p class="dashboard-empty-copy">server-computed triage card source absence · zeroInsight 표시 중</p>' : cards.map(triageCardMarkup).join('')}
      </div>
    </section>
  `;
}

function triageCardMarkup(card) {
  return `
    <article class="dashboard-mini-card">
      <h3>${escapeText(valueOrAbsence(card.title, 'triage title source absence'))}</h3>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('ruleId', card.ruleId)}
        ${keyValueMarkup('severity', card.severity)}
        ${keyValueMarkup('summary', card.summary)}
        ${keyValueMarkup('recommendation', card.recommendation)}
        ${keyValueMarkup('confidence', card.confidence)}
        ${keyValueMarkup('score', card.score)}
        ${keyValueMarkup('affectedEndpoint', card.affectedEndpoint)}
      </dl>
      ${triageEvidenceMarkup(card.evidence)}
    </article>
  `;
}

function triageEvidenceMarkup(evidence) {
  const boundedEvidence = evidence || {};
  return `
    <dl class="dashboard-kv-grid compact evidence-grid">
      ${keyValueMarkup('requestCount', boundedEvidence.requestCount)}
      ${keyValueMarkup('currentErrorCount', boundedEvidence.currentErrorCount)}
      ${keyValueMarkup('currentErrorRate', boundedEvidence.currentErrorRate)}
      ${keyValueMarkup('baselineRequestCount', boundedEvidence.baselineRequestCount)}
      ${keyValueMarkup('baselineErrorCount', boundedEvidence.baselineErrorCount)}
      ${keyValueMarkup('baselineErrorRate', boundedEvidence.baselineErrorRate)}
      ${keyValueMarkup('errorRateDelta', boundedEvidence.errorRateDelta)}
      ${keyValueMarkup('currentSlowShare', boundedEvidence.currentSlowShare)}
      ${keyValueMarkup('baselineSlowShare', boundedEvidence.baselineSlowShare)}
      ${keyValueMarkup('freshnessStatusReason', boundedEvidence.freshnessStatusReason)}
      ${keyValueMarkup('sourcePercentilePoint', sourcePercentileSummaryText(boundedEvidence.sourcePercentilePoint))}
      ${keyValueMarkup('currentHistogram', histogramSummaryText(boundedEvidence.currentHistogram))}
      ${keyValueMarkup('baselineHistogram', histogramSummaryText(boundedEvidence.baselineHistogram))}
      ${keyValueMarkup('runtimeRatio', runtimeRatioText(boundedEvidence.runtimeRatio))}
    </dl>
  `;
}

function endpointPriorityMarkup(endpointPriority) {
  const items = Array.isArray(endpointPriority) ? endpointPriority : [];
  return `
    <section class="dashboard-section" aria-label="Endpoint next check">
      <p class="eyebrow">Next check</p>
      <h3>먼저 확인할 endpoint</h3>
      <div class="dashboard-list">
        ${items.length === 0 ? '<p class="dashboard-empty-copy">현재 source/freshness/evidence 조건에서 표시할 next-check surface가 없습니다.</p>' : items.map(endpointPriorityItemMarkup).join('')}
      </div>
    </section>
  `;
}

function endpointPriorityItemMarkup(item) {
  const freshness = item.freshness || {};
  const ruleIds = Array.isArray(item.ruleIds) ? item.ruleIds.join(', ') : '';
  return `
    <article class="dashboard-mini-card">
      <h3>${escapeText(valueOrAbsence(item.endpointKey, 'endpoint key source absence'))}</h3>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('rank', item.rank)}
        ${keyValueMarkup('method', item.method)}
        ${keyValueMarkup('route', item.route)}
        ${keyValueMarkup('reason', item.reason)}
        ${keyValueMarkup('ruleIds', ruleIds)}
        ${keyValueMarkup('confidence', item.confidence)}
        ${keyValueMarkup('score', item.score)}
        ${keyValueMarkup('freshness status', freshness.status)}
        ${keyValueMarkup('freshness lastObservedAt', freshness.lastObservedAt)}
        ${keyValueMarkup('freshness sourceWindow', freshness.sourceWindow)}
        ${keyValueMarkup('freshness reason', freshness.reason)}
        ${keyValueMarkup('recommendedAction', item.recommendedAction)}
      </dl>
      ${endpointEvidenceMarkup(item.evidence)}
    </article>
  `;
}

function endpointEvidenceMarkup(evidence) {
  const boundedEvidence = evidence || {};
  return `
    <dl class="dashboard-kv-grid compact evidence-grid">
      ${keyValueMarkup('requestCount', boundedEvidence.requestCount)}
      ${keyValueMarkup('errorCount', boundedEvidence.errorCount)}
      ${keyValueMarkup('errorRate', boundedEvidence.errorRate)}
      ${keyValueMarkup('baselineRequestCount', boundedEvidence.baselineRequestCount)}
      ${keyValueMarkup('baselineErrorCount', boundedEvidence.baselineErrorCount)}
      ${keyValueMarkup('baselineErrorRate', boundedEvidence.baselineErrorRate)}
      ${keyValueMarkup('errorRateDelta', boundedEvidence.errorRateDelta)}
      ${keyValueMarkup('slowShare', boundedEvidence.slowShare)}
      ${keyValueMarkup('baselineSlowShare', boundedEvidence.baselineSlowShare)}
      ${keyValueMarkup('slowShareDelta', boundedEvidence.slowShareDelta)}
      ${keyValueMarkup('bucketDistributionSource', boundedEvidence.bucketDistributionSource)}
      ${keyValueMarkup('errorEvidenceStatus', boundedEvidence.errorEvidenceStatus)}
      ${keyValueMarkup('latencyEvidenceStatus', boundedEvidence.latencyEvidenceStatus)}
      ${keyValueMarkup('durationBuckets', bucketSummaryText(boundedEvidence.durationBuckets))}
      ${keyValueMarkup('baselineDurationBuckets', bucketSummaryText(boundedEvidence.baselineDurationBuckets))}
    </dl>
  `;
}

function instanceHandoffMarkup(instances) {
  const entries = Array.isArray(instances) ? instances : [];
  return `
    <section class="dashboard-section" aria-label="Instance handoff">
      <p class="eyebrow">Instance handoff</p>
      <div class="dashboard-list">
        ${entries.length === 0 ? '<p class="dashboard-empty-copy">instance handoff source absence</p>' : entries.map(instanceEntryMarkup).join('')}
      </div>
    </section>
  `;
}

function instanceEntryMarkup(instance) {
  const links = instance.links || {};
  const evidenceLink = String(links.evidence || '');
  return `
    <article class="dashboard-mini-card">
      <h3>${escapeText(valueOrAbsence(instance.instanceName, 'instance name source absence'))}</h3>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('instanceId', instance.instanceId)}
        ${keyValueMarkup('lastSeenAt', formatTimestamp(instance.lastSeenAt, 'last seen source absence'))}
      </dl>
      <button class="link-button evidence-handoff" type="button" data-instance-id="${escapeAttribute(instance.instanceId)}" data-instance-name="${escapeAttribute(instance.instanceName)}" data-evidence-link="${escapeAttribute(evidenceLink)}">Evidence</button>
    </article>
  `;
}

function handleDashboardDetailClick(event) {
  const backAction = event.target.closest('[data-dashboard-back]');
  if (backAction) {
    renderApplicationDashboardFromInstanceDetail();
    return;
  }
  const evidenceAction = event.target.closest('[data-evidence-link]');
  if (!evidenceAction) {
    return;
  }
  if (evidenceAction.disabled || evidenceAction.getAttribute && evidenceAction.getAttribute('aria-disabled') === 'true') {
    return;
  }
  selectInstanceEvidence({
    instanceId: evidenceAction.dataset.instanceId,
    instanceName: evidenceAction.dataset.instanceName,
    evidenceLink: evidenceAction.dataset.evidenceLink
  });
}

function selectInstanceEvidence(instanceContext) {
  instanceEvidenceRequestSequence += 1;
  selectedInstanceEvidenceContext = {
    projectId: selectedDashboardContext ? selectedDashboardContext.projectId : '',
    projectName: selectedDashboardContext ? selectedDashboardContext.projectName : '',
    applicationId: selectedDashboardContext ? selectedDashboardContext.applicationId : '',
    applicationName: selectedDashboardContext ? selectedDashboardContext.applicationName : '',
    applicationEnvironment: selectedDashboardContext ? selectedDashboardContext.applicationEnvironment : '',
    instanceId: String(instanceContext.instanceId ?? ''),
    instanceName: String(instanceContext.instanceName ?? ''),
    evidenceLink: String(instanceContext.evidenceLink ?? '')
  };
  loadedInstanceEvidence = null;
  if (!serviceAccessToken) {
    renderInstanceEvidenceAuthorizationRequired();
    return;
  }
  if (!isInstanceEvidenceLink(
    selectedInstanceEvidenceContext.evidenceLink,
    selectedInstanceEvidenceContext.projectId,
    selectedInstanceEvidenceContext.applicationId,
    selectedInstanceEvidenceContext.instanceId
  )) {
    renderInstanceEvidenceInvalidLink();
    return;
  }
  loadInstanceEvidenceForSelectedInstance();
}

async function loadInstanceEvidenceForSelectedInstance() {
  const requestId = ++instanceEvidenceRequestSequence;
  if (!selectedInstanceEvidenceContext) {
    renderApplicationDashboardFromInstanceDetail();
    return;
  }
  if (!serviceAccessToken) {
    loadedInstanceEvidence = null;
    renderInstanceEvidenceAuthorizationRequired();
    return;
  }
  if (!isInstanceEvidenceLink(
    selectedInstanceEvidenceContext.evidenceLink,
    selectedInstanceEvidenceContext.projectId,
    selectedInstanceEvidenceContext.applicationId,
    selectedInstanceEvidenceContext.instanceId
  )) {
    renderInstanceEvidenceInvalidLink();
    return;
  }
  renderInstanceEvidenceLoadingState();
  try {
    const response = await fetch(selectedInstanceEvidenceContext.evidenceLink, { headers: projectRequestHeaders() });
    if (!isLatestInstanceEvidenceRequest(requestId)) {
      return;
    }
    if (response.status === 401) {
      handleInstanceEvidenceAuthorizationLoss();
      return;
    }
    if (response.status === 404) {
      loadedInstanceEvidence = null;
      renderInstanceEvidenceNotFound();
      return;
    }
    if (!response.ok) {
      throw new Error('instance_evidence_load_failed');
    }
    const evidence = await response.json();
    if (!isLatestInstanceEvidenceRequest(requestId)) {
      return;
    }
    if (!isSelectedInstanceEvidenceResponse(evidence)) {
      throw new Error('instance_evidence_context_mismatch');
    }
    loadedInstanceEvidence = evidence;
    renderInstanceEvidenceReady();
  } catch (error) {
    if (!isLatestInstanceEvidenceRequest(requestId)) {
      return;
    }
    loadedInstanceEvidence = null;
    renderInstanceEvidenceLoadError();
  }
}

function renderApplicationDashboardFromInstanceDetail() {
  instanceEvidenceRequestSequence += 1;
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  if (!serviceAccessToken) {
    clearDashboardSnapshot({ resetSelection: true });
    renderDashboardAuthorizationRequired();
    return;
  }
  if (loadedDashboard && selectedDashboardContext) {
    renderDashboardReady();
    return;
  }
  renderDashboardIdle();
}

function renderInstanceEvidenceLoadingState() {
  setInstanceEvidenceViewState(INSTANCE_EVIDENCE_VIEW_STATE.LOADING);
  setDashboardGeneratedAtLabel('Instance Evidence를 불러오는 중');
  setSelectedApplicationLabel(instanceEvidenceContextText());
  dashboardDetail.innerHTML = instanceEvidenceShellMarkup({
    state: 'loading',
    generatedAt: null,
    bodyMarkup: `
      <div class="loading-state" aria-label="Instance Evidence 로딩 중">
        <p class="dashboard-empty-copy">Instance Evidence를 불러오는 중</p>
        <div class="skeleton-line"></div>
        <div class="skeleton-line short"></div>
      </div>
    `
  });
}

function renderInstanceEvidenceAuthorizationRequired() {
  setInstanceEvidenceViewState(INSTANCE_EVIDENCE_VIEW_STATE.AUTH_REQUIRED);
  setDashboardGeneratedAtLabel('로그인이 필요합니다');
  setSelectedApplicationLabel(instanceEvidenceContextText());
  dashboardDetail.innerHTML = instanceEvidenceShellMarkup({
    state: 'auth-required',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>GitHub 로그인 후 Instance Evidence를 볼 수 있습니다.</strong>로그인을 완료한 뒤 같은 Dashboard에서 Evidence action을 다시 선택해 주세요.</p>
      </div>
    `
  });
}

function handleInstanceEvidenceAuthorizationLoss() {
  serviceAccessToken = null;
  projectRequestSequence += 1;
  applicationRequestSequence += 1;
  dashboardRequestSequence += 1;
  instanceEvidenceRequestSequence += 1;
  loadedInstanceEvidence = null;
  clearDashboardSnapshot({ resetSelection: true });
  renderAuthorizationRequired();
  renderApplicationAuthorizationRequired();
  renderInstanceEvidenceAuthorizationRequired();
}

function renderInstanceEvidenceInvalidLink() {
  setInstanceEvidenceViewState(INSTANCE_EVIDENCE_VIEW_STATE.INVALID_LINK);
  setDashboardGeneratedAtLabel('Instance Evidence 기준 시각 확인 불가');
  setSelectedApplicationLabel(instanceEvidenceContextText());
  dashboardDetail.innerHTML = instanceEvidenceShellMarkup({
    state: 'invalid-link',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>Evidence link를 확인할 수 없습니다.</strong>현재 Project/Application/Instance와 일치하는 내부 Evidence link만 사용할 수 있습니다.</p>
      </div>
    `
  });
}

function renderInstanceEvidenceNotFound() {
  setInstanceEvidenceViewState(INSTANCE_EVIDENCE_VIEW_STATE.NOT_FOUND);
  setDashboardGeneratedAtLabel('Instance Evidence 기준 시각 확인 불가');
  setSelectedApplicationLabel(instanceEvidenceContextText());
  dashboardDetail.innerHTML = instanceEvidenceShellMarkup({
    state: 'not-found',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>Project/Application/Instance scope를 찾을 수 없습니다.</strong>catalog scope 또는 접근 가능한 instance 범위를 다시 확인해 주세요.</p>
      </div>
    `
  });
}

function renderInstanceEvidenceLoadError() {
  setInstanceEvidenceViewState(INSTANCE_EVIDENCE_VIEW_STATE.ERROR);
  setDashboardGeneratedAtLabel('Instance Evidence 기준 시각 확인 불가');
  setSelectedApplicationLabel(instanceEvidenceContextText());
  dashboardDetail.innerHTML = instanceEvidenceShellMarkup({
    state: 'error',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>Instance Evidence를 불러오지 못했습니다.</strong>잠시 후 같은 instance에서 다시 시도해 주세요.</p>
      </div>
    `
  });
}

function renderInstanceEvidenceReady() {
  const evidence = loadedInstanceEvidence;
  setInstanceEvidenceViewState(INSTANCE_EVIDENCE_VIEW_STATE.READY);
  setDashboardGeneratedAtLabel(formatEvidenceGeneratedAt(evidence.generatedAt));
  setSelectedApplicationLabel(instanceEvidenceReadyText(evidence));
  dashboardDetail.innerHTML = instanceEvidenceShellMarkup({
    state: 'ready',
    generatedAt: evidence.generatedAt,
    bodyMarkup: `
      <div class="dashboard-strip-grid">
        ${instanceMetricDataAxisMarkup(evidence.metricData)}
        ${instanceStarterConnectionAxisMarkup(evidence.starterConnection)}
      </div>
      ${instanceApplicationTriageContributionMarkup(evidence.applicationTriageContribution)}
      ${instanceStarterPercentilesMarkup(evidence.starterPercentiles)}
      ${instanceHistogramDistributionMarkup(evidence.histogramDistribution)}
      ${instanceResourceHintsMarkup(evidence.resourceHints)}
      ${instanceEndpointEvidenceMarkup(evidence.endpointEvidence)}
      ${snapshotTrendPendingHandoffMarkup(evidence.links)}
    `
  });
}

function instanceEvidenceShellMarkup({ state, generatedAt, bodyMarkup }) {
  return `
    <article class="instance-evidence-detail" data-instance-evidence-state="${escapeAttribute(state)}">
      ${instanceEvidenceHeaderMarkup(generatedAt)}
      ${bodyMarkup}
    </article>
  `;
}

function instanceEvidenceHeaderMarkup(generatedAt) {
  const context = selectedInstanceEvidenceContext || {};
  return `
    <section class="dashboard-section instance-detail-header" aria-label="Instance evidence context">
      <div class="instance-detail-title">
        <div>
          <p class="eyebrow">Instance Evidence</p>
          <h3>${escapeText(valueOrAbsence(context.instanceName, 'instance name source absence'))}</h3>
        </div>
        <button class="link-button" type="button" data-dashboard-back="true">Application Dashboard</button>
      </div>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('project id', context.projectId)}
        ${keyValueMarkup('application id', context.applicationId)}
        ${keyValueMarkup('application', valueOrAbsence(context.applicationName, 'application name source absence'))}
        ${keyValueMarkup('environment', context.applicationEnvironment)}
        ${keyValueMarkup('instanceId', context.instanceId)}
        ${keyValueMarkup('instanceName', context.instanceName)}
        ${keyValueMarkup('generatedAt', valueOrAbsence(generatedAt, 'evidence generatedAt source absence'))}
      </dl>
    </section>
  `;
}

function instanceMetricDataAxisMarkup(metricData) {
  const metric = metricData || {};
  const window = metric.window || {};
  return `
    <section class="dashboard-strip instance-evidence-axis metric-data-axis" aria-label="Metric data axis">
      <div class="strip-heading">
        <p class="eyebrow">Metric data axis</p>
        <h3>${escapeText(valueOrAbsence(metric.freshnessLabel, 'metric freshness source absence'))}</h3>
      </div>
      <dl>
        ${keyValueMarkup('source', metric.statusSource)}
        ${keyValueMarkup('window', window.name)}
        ${keyValueMarkup('current window', windowRangeText(window))}
        ${keyValueMarkup('bucketDurationSeconds', window.bucketDurationSeconds)}
        ${keyValueMarkup('last accepted bucket', formatTimestamp(metric.lastAcceptedBucketAt, 'accepted bucket source absence'))}
        ${keyValueMarkup('sampleReadiness', metric.sampleReadiness)}
        ${keyValueMarkup('reason', valueOrAbsence(metric.reason, 'metric data source absence'))}
        ${keyValueMarkup('requestCount', metric.requestCount)}
        ${keyValueMarkup('errorCount', metric.errorCount)}
        ${keyValueMarkup('errorRate', metric.errorRate)}
      </dl>
    </section>
  `;
}

function instanceStarterConnectionAxisMarkup(starterConnection) {
  const starter = starterConnection || {};
  return `
    <section class="dashboard-strip instance-evidence-axis starter-connection-axis" aria-label="Starter connection axis">
      <div class="strip-heading">
        <p class="eyebrow">Starter connection axis</p>
        <h3>${escapeText(valueOrAbsence(starter.connectionMeaning, 'connection meaning source absence'))}</h3>
      </div>
      <dl>
        ${keyValueMarkup('source', starter.statusSource)}
        ${keyValueMarkup('last heartbeat', formatTimestamp(starter.lastHeartbeatAt, 'starter heartbeat source absence'))}
        ${keyValueMarkup('last heartbeat status', starter.lastHeartbeatStatus)}
        ${keyValueMarkup('freshnessLabel', starter.freshnessLabel)}
        ${keyValueMarkup('connectionMeaning', starter.connectionMeaning)}
        ${keyValueMarkup('state impact', starter.stateImpact)}
      </dl>
    </section>
  `;
}

function instanceApplicationTriageContributionMarkup(applicationTriageContribution) {
  const contribution = applicationTriageContribution || {};
  const relatedRuleIds = Array.isArray(contribution.relatedRuleIds) ? contribution.relatedRuleIds.join(', ') : '';
  const absenceCopy = contribution.contributed ? '' : '<p class="dashboard-empty-copy">기여 evidence 없음 또는 source absence</p>';
  return `
    <section class="dashboard-section" aria-label="Application triage contribution">
      <p class="eyebrow">Application triage contribution</p>
      ${absenceCopy}
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('status', contribution.status)}
        ${keyValueMarkup('contributed', contribution.contributed)}
        ${keyValueMarkup('relatedRuleIds', valueOrAbsence(relatedRuleIds, 'triage rule source absence'))}
        ${keyValueMarkup('reason', valueOrAbsence(contribution.reason, 'triage contribution reason source absence'))}
      </dl>
    </section>
  `;
}

function instanceStarterPercentilesMarkup(starterPercentiles) {
  const percentile = starterPercentiles || {};
  const points = Array.isArray(percentile.points) ? percentile.points : [];
  const absenceCopy = percentile.status === 'available'
    ? 'source-scoped percentile point source absence'
    : 'percentile source absence/evidence 부족';
  return `
    <section class="dashboard-section" aria-label="Starter percentile series">
      <p class="eyebrow">Starter percentile series</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('source', percentile.source)}
        ${keyValueMarkup('scope', percentile.scope)}
        ${keyValueMarkup('window', percentile.window)}
        ${keyValueMarkup('bucketDurationSeconds', percentile.bucketDurationSeconds)}
        ${keyValueMarkup('maxPointCount', percentile.maxPointCount)}
        ${keyValueMarkup('displayPolicy', percentile.displayPolicy)}
        ${keyValueMarkup('aggregatePolicy', percentile.aggregatePolicy)}
        ${keyValueMarkup('status', percentile.status)}
        ${keyValueMarkup('reason', valueOrAbsence(percentile.reason, absenceCopy))}
      </dl>
      <div class="dashboard-list">
        ${points.length === 0 ? `<p class="dashboard-empty-copy">${escapeText(absenceCopy)}</p>` : points.map(instancePercentilePointMarkup).join('')}
      </div>
    </section>
  `;
}

function instancePercentilePointMarkup(point) {
  return `
    <article class="dashboard-mini-card">
      <h3>${escapeText(valueOrAbsence(point.bucketEndUtc, 'bucketEndUtc source absence'))}</h3>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('bucketStartUtc', point.bucketStartUtc)}
        ${keyValueMarkup('bucketEndUtc', point.bucketEndUtc)}
        ${keyValueMarkup('requestCount', point.requestCount)}
        ${keyValueMarkup('p95Ms', point.p95Ms)}
        ${keyValueMarkup('p99Ms', point.p99Ms)}
      </dl>
    </article>
  `;
}

function instanceHistogramDistributionMarkup(histogramDistribution) {
  const histogram = histogramDistribution || {};
  const buckets = Array.isArray(histogram.buckets) ? histogram.buckets : [];
  return `
    <section class="dashboard-section" aria-label="Instance histogram distribution">
      <p class="eyebrow">Histogram distribution evidence</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('source', histogram.source)}
        ${keyValueMarkup('scope', histogram.scope)}
        ${keyValueMarkup('status', histogram.status)}
        ${keyValueMarkup('reason', valueOrAbsence(histogram.reason, 'histogram evidence reason source absence'))}
        ${keyValueMarkup('totalCount', histogram.totalCount)}
      </dl>
      <div class="bucket-list">
        ${buckets.length === 0 ? '<p class="dashboard-empty-copy">bucket distribution evidence 부족</p>' : buckets.map(bucketMarkup).join('')}
      </div>
    </section>
  `;
}

function instanceResourceHintsMarkup(resourceHints) {
  const hints = resourceHints || {};
  return `
    <section class="dashboard-section" aria-label="Resource hints">
      <p class="eyebrow">Resource hints</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('source', hints.source)}
        ${keyValueMarkup('status', hints.status)}
        ${keyValueMarkup('reason', valueOrAbsence(hints.reason, 'resource hint source absence'))}
        ${keyValueMarkup('bucketEndUtc', formatTimestamp(hints.bucketEndUtc, 'latest sample source absence'))}
        ${keyValueMarkup('cpuUsageRatio', hints.cpuUsageRatio)}
        ${keyValueMarkup('heapUsedRatio', hints.heapUsedRatio)}
        ${keyValueMarkup('datasourcePoolUsageRatio', hints.datasourcePoolUsageRatio)}
      </dl>
    </section>
  `;
}

function instanceEndpointEvidenceMarkup(endpointEvidence) {
  const evidence = endpointEvidence || {};
  const suppressed = evidence.status === 'suppressed' && evidence.reason === 'application_freshness_not_current';
  const items = suppressed ? [] : (Array.isArray(evidence.items) ? evidence.items : []);
  const suppressedCopy = evidence.status === 'suppressed' && evidence.reason === 'application_freshness_not_current'
    ? '<p class="dashboard-empty-copy">application freshness가 current가 아니라 stale/down 직전 endpoint evidence를 current concern처럼 표시하지 않습니다.</p>'
    : '';
  return `
    <section class="dashboard-section" aria-label="Instance endpoint evidence">
      <p class="eyebrow">Instance endpoint evidence</p>
      ${suppressedCopy}
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('source', evidence.source)}
        ${keyValueMarkup('scope', evidence.scope)}
        ${keyValueMarkup('selectionPolicy', evidence.selectionPolicy)}
        ${keyValueMarkup('displayOrderingPolicy', evidence.displayOrderingPolicy)}
        ${keyValueMarkup('status', evidence.status)}
        ${keyValueMarkup('reason', valueOrAbsence(evidence.reason, 'endpoint evidence reason source absence'))}
      </dl>
      <div class="dashboard-list">
        ${items.length === 0 ? '<p class="dashboard-empty-copy">endpoint evidence source absence 또는 evidence 부족</p>' : items.map(instanceEndpointEvidenceItemMarkup).join('')}
      </div>
    </section>
  `;
}

function instanceEndpointEvidenceItemMarkup(item) {
  const relatedRuleIds = Array.isArray(item.relatedRuleIds) ? item.relatedRuleIds.join(', ') : '';
  return `
    <article class="dashboard-mini-card">
      <h3>${escapeText(valueOrAbsence(item.endpointKey, 'endpoint key source absence'))}</h3>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('method', item.method)}
        ${keyValueMarkup('route', item.route)}
        ${keyValueMarkup('presenceOnSelectedInstance', item.presenceOnSelectedInstance)}
        ${keyValueMarkup('presence meaning', endpointPresenceMeaning(item.presenceOnSelectedInstance))}
        ${keyValueMarkup('instanceRequestCount', item.instanceRequestCount)}
        ${keyValueMarkup('instanceErrorCount', item.instanceErrorCount)}
        ${keyValueMarkup('instanceErrorRate', item.instanceErrorRate)}
        ${keyValueMarkup('applicationEndpointRequestCount', item.applicationEndpointRequestCount)}
        ${keyValueMarkup('applicationEndpointErrorCount', item.applicationEndpointErrorCount)}
        ${keyValueMarkup('applicationEndpointErrorRate', item.applicationEndpointErrorRate)}
        ${keyValueMarkup('instanceRequestShare', item.instanceRequestShare)}
        ${keyValueMarkup('instanceErrorShare', item.instanceErrorShare)}
        ${keyValueMarkup('durationBuckets', bucketSummaryText(item.durationBuckets))}
        ${keyValueMarkup('bucketDistributionSource', item.bucketDistributionSource)}
        ${keyValueMarkup('relatedApplicationPriorityRank', item.relatedApplicationPriorityRank)}
        ${keyValueMarkup('localDisplayOrder', item.localDisplayOrder)}
        ${keyValueMarkup('relatedRuleIds', valueOrAbsence(relatedRuleIds, 'related rule source absence'))}
        ${keyValueMarkup('status', item.status)}
        ${keyValueMarkup('reason', valueOrAbsence(item.reason, 'endpoint item reason source absence'))}
      </dl>
    </article>
  `;
}

function endpointPresenceMeaning(presence) {
  if (presence === 'observed') {
    return 'selected instance current window에서 관찰됨';
  }
  if (presence === 'not_observed') {
    return 'selected instance current window에 해당 endpoint evidence가 없음';
  }
  if (presence === 'insufficient') {
    return 'endpoint evidence 신뢰 부족';
  }
  return 'presence source absence';
}

function snapshotTrendPendingHandoffMarkup(links) {
  const linkBlock = links || {};
  const snapshotTrendLink = safeSnapshotTrendLink(linkBlock.snapshotTrend);
  const handoffAttributes = snapshotTrendLink
    ? `data-snapshot-trend-link="${escapeAttribute(snapshotTrendLink)}"`
    : 'data-snapshot-trend-link=""';
  return `
    <section class="dashboard-section snapshot-trend-handoff" aria-label="Snapshot trend handoff" ${handoffAttributes}>
      <p class="eyebrow">Snapshot trend handoff</p>
      <p class="dashboard-empty-copy">${snapshotTrendLink ? '후속 trend 화면에서 사용할 handoff link가 보존되었습니다.' : 'snapshot trend handoff source absence'}</p>
      ${snapshotTrendLink ? `<button class="link-button pending-handoff" type="button" disabled aria-disabled="true" data-snapshot-trend-link="${escapeAttribute(snapshotTrendLink)}">Snapshot trend pending</button>` : ''}
    </section>
  `;
}

function snapshotHandoffMarkup(snapshot) {
  const available = isObjectValue(snapshot);
  const snapshotData = available ? snapshot : {};
  const snapshotId = String(snapshotData.id ?? snapshotData.snapshotId ?? '');
  const snapshotLink = snapshotHandoffLink(snapshotData);
  const handoffAttributes = available
    ? `data-snapshot-handoff="available" data-snapshot-id="${escapeAttribute(snapshotId)}" data-snapshot-link="${escapeAttribute(snapshotLink)}"`
    : 'data-snapshot-handoff="source-absence" data-snapshot-id="" data-snapshot-link=""';
  return `
    <section class="dashboard-section snapshot-handoff" aria-label="Snapshot handoff" ${handoffAttributes}>
      <p class="eyebrow">Snapshot handoff</p>
      <p class="dashboard-empty-copy">${available ? 'Snapshot/History handoff data 보존됨' : 'snapshot handoff source absence'}</p>
      ${available ? `<button class="link-button pending-handoff" type="button" disabled aria-disabled="true" data-snapshot-link="${escapeAttribute(snapshotLink)}">Snapshot pending</button>` : ''}
    </section>
  `;
}

function snapshotHandoffLink(snapshotData) {
  const links = isObjectValue(snapshotData.links) ? snapshotData.links : {};
  return handoffLinkCandidate(
    snapshotData.link,
    snapshotData.snapshotLink,
    snapshotData.detailLink,
    links.snapshot,
    links.detail,
    links.self,
    links.selfSnapshot,
    links.history
  );
}

function handoffLinkCandidate(...candidates) {
  for (const candidate of candidates) {
    const normalized = normalizeHandoffLinkCandidate(candidate);
    if (normalized) {
      return normalized;
    }
  }
  return '';
}

function normalizeHandoffLinkCandidate(candidate) {
  if (candidate == null) {
    return '';
  }
  if (typeof candidate === 'string') {
    return candidate;
  }
  if (isObjectValue(candidate)) {
    return normalizeHandoffLinkCandidate(candidate.href ?? candidate.url);
  }
  return '';
}

function keyValueMarkup(label, value) {
  return `
    <div>
      <dt>${escapeText(label)}</dt>
      <dd>${escapeText(valueOrAbsence(value, 'source absence'))}</dd>
    </div>
  `;
}

function sourcePercentileSummaryText(sourcePercentilePoint) {
  if (!sourcePercentilePoint) {
    return 'source percentile point absence';
  }
  return [
    sourcePercentilePoint.source,
    sourcePercentilePoint.scope,
    sourcePercentilePoint.instance,
    sourcePercentilePoint.bucketEndUtc,
    `requestCount ${sourcePercentilePoint.requestCount}`,
    `p95Ms ${valueOrAbsence(sourcePercentilePoint.p95Ms, 'source absence')}`,
    `p99Ms ${valueOrAbsence(sourcePercentilePoint.p99Ms, 'source absence')}`
  ].filter(value => String(value ?? '').trim().length > 0).join(' · ');
}

function histogramSummaryText(histogramSummary) {
  if (!histogramSummary) {
    return 'histogram summary absence';
  }
  return [
    histogramSummary.status,
    `totalCount ${histogramSummary.totalCount}`,
    bucketSummaryText(histogramSummary.buckets)
  ].join(' · ');
}

function runtimeRatioText(runtimeRatio) {
  if (!runtimeRatio) {
    return 'runtime ratio absence';
  }
  return [
    `cpu ${valueOrAbsence(runtimeRatio.cpuUsageRatio, 'source absence')}`,
    `heap ${valueOrAbsence(runtimeRatio.heapUsedRatio, 'source absence')}`,
    `datasource ${valueOrAbsence(runtimeRatio.datasourcePoolUsageRatio, 'source absence')}`
  ].join(' · ');
}

function bucketSummaryText(buckets) {
  if (!Array.isArray(buckets) || buckets.length === 0) {
    return 'bucket evidence absence';
  }
  return buckets
    .filter(isValidHistogramBucket)
    .map(bucket => `leMs ${bucket.leMs}: ${bucket.count}`)
    .join(' · ') || 'bucket evidence absence';
}


function axisMarkup(title, rows) {
  return `
    <section class="application-axis" aria-label="${escapeAttribute(title)}">
      <h3>${escapeText(title)}</h3>
      <dl>
        ${rows.map(([label, value, fallback]) => `
          <div>
            <dt>${escapeText(label)}</dt>
            <dd>${escapeText(valueOrAbsence(value, fallback))}</dd>
          </div>
        `).join('')}
      </dl>
    </section>
  `;
}

function topConcernMarkup(topConcern) {
  if (!topConcern) {
    return `
      <div class="application-concern">
        <span class="badge">Concern source absence</span>
      </div>
    `;
  }
  return `
    <div class="application-concern">
      <span class="badge attention">${escapeText(topConcern.source)} · ${escapeText(topConcern.code)} · ${escapeText(topConcern.label)}</span>
    </div>
  `;
}

function safeDashboardLink(application, projectId, rawDashboardLink = '') {
  const dashboardLink = String(rawDashboardLink);
  return isApplicationDashboardLink(dashboardLink, projectId, application.applicationId) ? dashboardLink : '';
}

function isProjectApplicationsLink(applicationsLink, projectId) {
  const normalizedProjectId = String(projectId ?? '').trim();
  if (normalizedProjectId.length === 0) {
    return false;
  }
  const match = String(applicationsLink ?? '').match(/^\/api\/projects\/([^/?#]+)\/applications$/);
  if (!match) {
    return false;
  }
  try {
    return decodeURIComponent(match[1]) === normalizedProjectId;
  } catch (error) {
    return false;
  }
}

function isApplicationDashboardLink(dashboardLink, projectId, applicationId) {
  const normalizedProjectId = String(projectId ?? '').trim();
  const normalizedApplicationId = String(applicationId ?? '').trim();
  if (normalizedProjectId.length === 0 || normalizedApplicationId.length === 0) {
    return false;
  }
  const match = String(dashboardLink ?? '').match(/^\/api\/projects\/([^/?#]+)\/applications\/([^/?#]+)\/dashboard$/);
  if (!match) {
    return false;
  }
  try {
    return decodeURIComponent(match[1]) === normalizedProjectId
      && decodeURIComponent(match[2]) === normalizedApplicationId;
  } catch (error) {
    return false;
  }
}

function isInstanceEvidenceLink(evidenceLink, projectId, applicationId, instanceId) {
  const normalizedProjectId = String(projectId ?? '').trim();
  const normalizedApplicationId = String(applicationId ?? '').trim();
  const normalizedInstanceId = String(instanceId ?? '').trim();
  if (normalizedProjectId.length === 0 || normalizedApplicationId.length === 0 || normalizedInstanceId.length === 0) {
    return false;
  }
  const match = String(evidenceLink ?? '').match(
    /^\/api\/projects\/([^/?#]+)\/applications\/([^/?#]+)\/instances\/([^/?#]+)\/evidence$/
  );
  if (!match) {
    return false;
  }
  try {
    return decodeURIComponent(match[1]) === normalizedProjectId
      && decodeURIComponent(match[2]) === normalizedApplicationId
      && decodeURIComponent(match[3]) === normalizedInstanceId;
  } catch (error) {
    return false;
  }
}

function isInstanceSnapshotTrendLink(snapshotTrendLink, projectId, applicationId, instanceId) {
  const normalizedProjectId = String(projectId ?? '').trim();
  const normalizedApplicationId = String(applicationId ?? '').trim();
  const normalizedInstanceId = String(instanceId ?? '').trim();
  if (normalizedProjectId.length === 0 || normalizedApplicationId.length === 0 || normalizedInstanceId.length === 0) {
    return false;
  }
  const match = String(snapshotTrendLink ?? '').match(
    /^\/api\/projects\/([^/?#]+)\/applications\/([^/?#]+)\/instances\/([^/?#]+)\/snapshot-trend$/
  );
  if (!match) {
    return false;
  }
  try {
    return decodeURIComponent(match[1]) === normalizedProjectId
      && decodeURIComponent(match[2]) === normalizedApplicationId
      && decodeURIComponent(match[3]) === normalizedInstanceId;
  } catch (error) {
    return false;
  }
}

function safeSnapshotTrendLink(candidate) {
  const normalized = normalizeHandoffLinkCandidate(candidate);
  if (!selectedInstanceEvidenceContext || normalized.length === 0) {
    return '';
  }
  return isInstanceSnapshotTrendLink(
    normalized,
    selectedInstanceEvidenceContext.projectId,
    selectedInstanceEvidenceContext.applicationId,
    selectedInstanceEvidenceContext.instanceId
  ) ? normalized : '';
}

function isSelectedProjectApplicationResponse(data) {
  if (!selectedProjectContext || !data || !data.project) {
    return false;
  }
  return String(data.project.projectId ?? '') === selectedProjectContext.projectId;
}

function isSelectedApplicationDashboardResponse(dashboard) {
  if (!selectedDashboardContext || !dashboard || !dashboard.application) {
    return false;
  }
  if (!hasDashboardTopLevelFields(dashboard)) {
    return false;
  }
  return String(dashboard.application.projectId ?? '') === selectedDashboardContext.projectId
    && String(dashboard.application.applicationId ?? '') === selectedDashboardContext.applicationId;
}

function isSelectedInstanceEvidenceResponse(evidence) {
  if (!selectedInstanceEvidenceContext || !evidence || !evidence.application || !evidence.instance) {
    return false;
  }
  if (!hasInstanceEvidenceTopLevelFields(evidence)) {
    return false;
  }
  return String(evidence.application.projectId ?? '') === selectedInstanceEvidenceContext.projectId
    && String(evidence.application.applicationId ?? '') === selectedInstanceEvidenceContext.applicationId
    && String(evidence.instance.instanceId ?? '') === selectedInstanceEvidenceContext.instanceId;
}

function hasInstanceEvidenceTopLevelFields(evidence) {
  return Boolean(isObjectValue(evidence)
    && evidence.generatedAt
    && isObjectValue(evidence.application)
    && isObjectValue(evidence.instance)
    && isValidInstanceMetricData(evidence.metricData)
    && isValidInstanceStarterConnection(evidence.starterConnection)
    && isValidInstanceStarterPercentiles(evidence.starterPercentiles, evidence.metricData.window)
    && isValidInstanceHistogramDistribution(evidence.histogramDistribution)
    && isValidInstanceResourceHints(evidence.resourceHints)
    && isValidInstanceTriageContribution(evidence.applicationTriageContribution)
    && isValidInstanceEndpointEvidence(evidence.endpointEvidence)
    && isValidInstanceEvidenceLinks(evidence.links));
}

function isValidInstanceMetricData(metricData) {
  return Boolean(isObjectValue(metricData)
    && metricData.statusSource === 'accepted_bucket'
    && isValidInstanceMetricWindow(metricData.window)
    && hasRequiredText(metricData.freshnessLabel)
    && hasRequiredText(metricData.sampleReadiness)
    && isNonNegativeNumber(metricData.requestCount)
    && isNonNegativeNumber(metricData.errorCount)
    && metricData.errorCount <= metricData.requestCount
    && isNullableFraction(metricData.errorRate));
}

function isValidInstanceMetricWindow(window) {
  return Boolean(isObjectValue(window)
    && window.name === 'current_15m'
    && hasRequiredText(window.startUtc)
    && hasRequiredText(window.endUtc)
    && isBeforeTimestamp(window.startUtc, window.endUtc)
    && window.bucketDurationSeconds === 30);
}

function isValidInstanceStarterConnection(starterConnection) {
  return Boolean(isObjectValue(starterConnection)
    && starterConnection.statusSource === 'starter_heartbeat'
    && hasRequiredText(starterConnection.lastHeartbeatStatus)
    && hasRequiredText(starterConnection.freshnessLabel)
    && hasRequiredText(starterConnection.connectionMeaning)
    && starterConnection.stateImpact === 'none');
}

function isValidInstanceStarterPercentiles(starterPercentiles, metricWindow) {
  return Boolean(isObjectValue(starterPercentiles)
    && starterPercentiles.source === 'starter_canonical_percentile'
    && starterPercentiles.scope === 'instance'
    && starterPercentiles.window === 'current_15m'
    && starterPercentiles.bucketDurationSeconds === 30
    && starterPercentiles.maxPointCount === 30
    && starterPercentiles.displayPolicy === 'source_scoped_series'
    && starterPercentiles.aggregatePolicy === 'no_average_no_max_no_merge_no_histogram_recalculation'
    && isAllowedValue(starterPercentiles.status, ['available', 'missing', 'insufficient'])
    && Array.isArray(starterPercentiles.points)
    && starterPercentiles.points.length <= 30
    && hasAscendingPercentilePoints(starterPercentiles.points)
    && starterPercentiles.points.every(point => isValidInstancePercentilePoint(point, metricWindow)));
}

function isValidInstancePercentilePoint(point, metricWindow) {
  return Boolean(isObjectValue(point)
    && hasRequiredText(point.bucketStartUtc)
    && hasRequiredText(point.bucketEndUtc)
    && isBeforeTimestamp(point.bucketStartUtc, point.bucketEndUtc)
    && isThirtySecondBucket(point.bucketStartUtc, point.bucketEndUtc)
    && isBucketEndWithinMetricWindow(point.bucketEndUtc, metricWindow)
    && isPositiveNumber(point.requestCount)
    && isNonNegativeNumber(point.p95Ms)
    && isNonNegativeNumber(point.p99Ms)
    && point.p99Ms >= point.p95Ms);
}

function isValidInstanceHistogramDistribution(histogramDistribution) {
  return Boolean(isObjectValue(histogramDistribution)
    && histogramDistribution.source === 'histogram_bucket_distribution'
    && histogramDistribution.scope === 'selected_instance_current_15m'
    && isAllowedValue(histogramDistribution.status, ['available', 'missing', 'insufficient', 'unavailable'])
    && isNonNegativeNumber(histogramDistribution.totalCount)
    && Array.isArray(histogramDistribution.buckets)
    && histogramDistribution.buckets.every(isValidHistogramBucket));
}

function isValidInstanceResourceHints(resourceHints) {
  return Boolean(isObjectValue(resourceHints)
    && resourceHints.source === 'accepted_bucket_latest_sample'
    && hasRequiredText(resourceHints.status)
    && isNullableFraction(resourceHints.cpuUsageRatio)
    && isNullableFraction(resourceHints.heapUsedRatio)
    && isNullableFraction(resourceHints.datasourcePoolUsageRatio));
}

function isValidInstanceTriageContribution(applicationTriageContribution) {
  return Boolean(isObjectValue(applicationTriageContribution)
    && hasRequiredText(applicationTriageContribution.status)
    && typeof applicationTriageContribution.contributed === 'boolean'
    && Array.isArray(applicationTriageContribution.relatedRuleIds)
    && applicationTriageContribution.relatedRuleIds.every(hasRequiredText)
    && (applicationTriageContribution.contributed || applicationTriageContribution.relatedRuleIds.length === 0));
}

function isValidInstanceEndpointEvidence(endpointEvidence) {
  const items = Array.isArray(endpointEvidence && endpointEvidence.items) ? endpointEvidence.items : [];
  const hasSuppressionReason = endpointEvidence && endpointEvidence.reason === 'application_freshness_not_current';
  const suppressed = endpointEvidence
    && endpointEvidence.status === 'suppressed'
    && hasSuppressionReason;
  return Boolean(isObjectValue(endpointEvidence)
    && endpointEvidence.source === 'accepted_metric_buckets.endpoints_json'
    && endpointEvidence.scope === 'instance_current_15m'
    && endpointEvidence.selectionPolicy === 'application_priority_presence_then_triage_then_instance_request_count'
    && endpointEvidence.displayOrderingPolicy === 'selected_instance_signal_then_application_priority_reference'
    && isAllowedValue(endpointEvidence.status, ['available', 'missing', 'insufficient', 'suppressed', 'unavailable'])
    && isNullableAllowedValue(endpointEvidence.reason, [
      'application_priority_endpoint_observed_on_selected_instance',
      'application_priority_endpoint_not_seen_on_selected_instance',
      'selected_instance_endpoint_observed',
      'endpoint_evidence_insufficient',
      'histogram_boundary_mismatch',
      'application_freshness_not_current'
    ])
    && (endpointEvidence.status !== 'suppressed' || hasSuppressionReason)
    && (endpointEvidence.status === 'suppressed' || !hasSuppressionReason)
    && Array.isArray(endpointEvidence.items)
    && endpointEvidence.items.length <= 5
    && (!suppressed || items.length === 0)
    && endpointEvidence.items.every(isValidInstanceEndpointEvidenceItem));
}

function isValidInstanceEndpointEvidenceItem(item) {
  return Boolean(isObjectValue(item)
    && hasRequiredText(item.method)
    && hasRequiredText(item.route)
    && !hasRawEndpointDetail(item.route)
    && item.endpointKey === `${item.method} ${item.route}`
    && !hasRawEndpointDetail(item.endpointKey)
    && isAllowedValue(item.presenceOnSelectedInstance, ['observed', 'not_observed', 'insufficient'])
    && isNonNegativeNumber(item.instanceRequestCount)
    && isNonNegativeNumber(item.instanceErrorCount)
    && item.instanceErrorCount <= item.instanceRequestCount
    && isNullableFraction(item.instanceErrorRate)
    && isNullableNonNegativeNumber(item.applicationEndpointRequestCount)
    && isNullableNonNegativeNumber(item.applicationEndpointErrorCount)
    && isNullableEndpointErrorCountValid(item)
    && isNullableFraction(item.applicationEndpointErrorRate)
    && isNullableFraction(item.instanceRequestShare)
    && isNullableFraction(item.instanceErrorShare)
    && Array.isArray(item.durationBuckets)
    && item.durationBuckets.every(isValidHistogramBucket)
    && item.bucketDistributionSource === 'histogram_bucket_distribution'
    && isNullablePositiveInteger(item.relatedApplicationPriorityRank)
    && isPositiveInteger(item.localDisplayOrder)
    && Array.isArray(item.relatedRuleIds)
    && item.relatedRuleIds.every(hasRequiredText)
    && isAllowedValue(item.status, ['available', 'missing', 'insufficient', 'unavailable'])
    && isNullableAllowedValue(item.reason, [
      'application_priority_endpoint_observed_on_selected_instance',
      'application_priority_endpoint_not_seen_on_selected_instance',
      'selected_instance_endpoint_observed',
      'endpoint_evidence_insufficient',
      'histogram_boundary_mismatch',
      'application_freshness_not_current'
    ]));
}

function isValidInstanceEvidenceLinks(links) {
  if (!selectedInstanceEvidenceContext) {
    return false;
  }
  const snapshotTrend = normalizeHandoffLinkCandidate(links && links.snapshotTrend);
  return Boolean(isObjectValue(links)
    && isInstanceEvidenceLink(
      links.self,
      selectedInstanceEvidenceContext.projectId,
      selectedInstanceEvidenceContext.applicationId,
      selectedInstanceEvidenceContext.instanceId
    )
    && isApplicationDashboardLink(
      links.dashboard,
      selectedInstanceEvidenceContext.projectId,
      selectedInstanceEvidenceContext.applicationId
    )
    && (snapshotTrend.length === 0 || isInstanceSnapshotTrendLink(
      snapshotTrend,
      selectedInstanceEvidenceContext.projectId,
      selectedInstanceEvidenceContext.applicationId,
      selectedInstanceEvidenceContext.instanceId
    )));
}

function hasDashboardTopLevelFields(dashboard) {
  return Boolean(isObjectValue(dashboard)
    && dashboard.generatedAt
    && isObjectValue(dashboard.application)
    && isObjectValue(dashboard.state)
    && isObjectValue(dashboard.starterConnection)
    && Object.prototype.hasOwnProperty.call(dashboard, 'zeroInsight')
    && hasValidDashboardZeroInsight(dashboard)
    && isObjectValue(dashboard.recovery)
    && isObjectValue(dashboard.metrics)
    && isValidSourceScopedPercentiles(dashboard.sourceScopedPercentiles)
    && isValidHistogramDistribution(dashboard.histogramDistribution)
    && isArrayOfObjects(dashboard.triageCards)
    && isArrayOfObjects(dashboard.endpointPriority)
    && isArrayOfObjects(dashboard.instances)
    && Object.prototype.hasOwnProperty.call(dashboard, 'snapshot')
    && isValidSnapshotHandoff(dashboard.snapshot));
}

function hasValidDashboardZeroInsight(dashboard) {
  if (!Array.isArray(dashboard.triageCards)) {
    return false;
  }
  return dashboard.triageCards.length > 0 || isValidZeroInsight(dashboard.zeroInsight);
}

function isValidZeroInsight(zeroInsight) {
  return Boolean(isObjectValue(zeroInsight)
    && hasRequiredText(zeroInsight.reasonCode)
    && hasRequiredText(zeroInsight.message)
    && hasRequiredText(zeroInsight.recommendedAction));
}

function isValidSourceScopedPercentiles(sourceScopedPercentiles) {
  return Boolean(isObjectValue(sourceScopedPercentiles)
    && Array.isArray(sourceScopedPercentiles.items)
    && sourceScopedPercentiles.items.every(isObjectValue));
}

function isValidHistogramDistribution(histogramDistribution) {
  return Boolean(isObjectValue(histogramDistribution)
    && isValidHistogramWindow(histogramDistribution.current)
    && isValidHistogramWindow(histogramDistribution.baseline));
}

function isValidHistogramWindow(histogramWindow) {
  return Boolean(isObjectValue(histogramWindow)
    && Array.isArray(histogramWindow.buckets)
    && histogramWindow.buckets.every(isValidHistogramBucket));
}

function isArrayOfObjects(value) {
  return Array.isArray(value) && value.every(isObjectValue);
}

function isValidHistogramBucket(bucket) {
  return Boolean(isObjectValue(bucket)
    && isNonNegativeNumber(bucket.leMs)
    && isNonNegativeNumber(bucket.count));
}

function isNonNegativeNumber(value) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0;
}

function isPositiveNumber(value) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0;
}

function isNullableNonNegativeNumber(value) {
  return value == null || isNonNegativeNumber(value);
}

function isPositiveInteger(value) {
  return Number.isInteger(value) && value >= 1;
}

function isNullablePositiveInteger(value) {
  return value == null || isPositiveInteger(value);
}

function isNullableFraction(value) {
  return value == null || (typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 1);
}

function isNullableEndpointErrorCountValid(item) {
  return item.applicationEndpointRequestCount == null
    || item.applicationEndpointErrorCount == null
    || item.applicationEndpointErrorCount <= item.applicationEndpointRequestCount;
}

function hasAscendingPercentilePoints(points) {
  let previousBucketEnd = null;
  for (const point of points) {
    if (!isObjectValue(point)) {
      return false;
    }
    const currentBucketEnd = parseTimestampMillis(point.bucketEndUtc);
    if (currentBucketEnd == null || (previousBucketEnd != null && currentBucketEnd <= previousBucketEnd)) {
      return false;
    }
    previousBucketEnd = currentBucketEnd;
  }
  return true;
}

function isBeforeTimestamp(start, end) {
  const startMillis = parseTimestampMillis(start);
  const endMillis = parseTimestampMillis(end);
  return startMillis != null && endMillis != null && startMillis < endMillis;
}

// Starter percentile point는 selected instance current metric window의 30초 bucket일 때만 ready 렌더링을 허용한다.
function isThirtySecondBucket(start, end) {
  const startMillis = parseTimestampMillis(start);
  const endMillis = parseTimestampMillis(end);
  return startMillis != null && endMillis != null && endMillis - startMillis === 30000;
}

function isBucketEndWithinMetricWindow(bucketEnd, metricWindow) {
  if (!isObjectValue(metricWindow)) {
    return false;
  }
  const startMillis = parseTimestampMillis(metricWindow.startUtc);
  const endMillis = parseTimestampMillis(metricWindow.endUtc);
  const bucketEndMillis = parseTimestampMillis(bucketEnd);
  return startMillis != null
    && endMillis != null
    && bucketEndMillis != null
    && bucketEndMillis > startMillis
    && bucketEndMillis <= endMillis;
}

function parseTimestampMillis(value) {
  const millis = Date.parse(String(value ?? ''));
  return Number.isNaN(millis) ? null : millis;
}

function isAllowedValue(value, allowedValues) {
  return allowedValues.includes(value);
}

function isNullableAllowedValue(value, allowedValues) {
  return value == null || allowedValues.includes(value);
}

function hasRawEndpointDetail(value) {
  return /[?#]/.test(String(value ?? ''));
}

function isValidSnapshotHandoff(snapshot) {
  return snapshot === null || isObjectValue(snapshot);
}

function isObjectValue(value) {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value));
}

function hasValidApplicationItems(applications, projectId) {
  return applications.every(application => application
    && typeof application === 'object'
    && String(application.applicationId ?? '').trim().length > 0
    && hasRequiredText(application.name)
    && hasRequiredText(application.environment)
    && isValidMetricData(application.metricData)
    && isValidStarterConnection(application.starterConnection)
    && isValidLifecycleBadge(application.lifecycleBadge)
    && isValidTopConcern(application.topConcern)
    && isApplicationDashboardLink(
      application.links && application.links.dashboard,
      projectId,
      application.applicationId
    ));
}

function isValidMetricData(metricData) {
  return Boolean(metricData
    && typeof metricData === 'object'
    && hasRequiredText(metricData.statusSource)
    && hasRequiredText(metricData.freshnessLabel));
}

function isValidStarterConnection(starterConnection) {
  return Boolean(starterConnection
    && typeof starterConnection === 'object'
    && hasRequiredText(starterConnection.statusSource)
    && hasRequiredText(starterConnection.heartbeatStatus)
    && hasRequiredText(starterConnection.freshnessLabel)
    && hasRequiredText(starterConnection.connectionMeaning)
    && hasRequiredText(starterConnection.stateImpact));
}

function isValidLifecycleBadge(lifecycleBadge) {
  return Boolean(lifecycleBadge
    && typeof lifecycleBadge === 'object'
    && hasRequiredText(lifecycleBadge.source)
    && hasRequiredText(lifecycleBadge.code)
    && hasRequiredText(lifecycleBadge.label));
}

function isValidTopConcern(topConcern) {
  return topConcern === null || Boolean(topConcern
    && typeof topConcern === 'object'
    && hasRequiredText(topConcern.source)
    && hasRequiredText(topConcern.code)
    && hasRequiredText(topConcern.label));
}

function hasRequiredText(value) {
  return String(value ?? '').trim().length > 0;
}

function projectRequestHeaders() {
  const headers = { Accept: 'application/json' };
  if (serviceAccessToken) {
    headers.Authorization = `Bearer ${serviceAccessToken}`;
  }
  return headers;
}

async function startGithubEntry() {
  setAuthStatus('');
  githubButton.disabled = true;
  try {
    const response = await fetch(githubButton.dataset.authUrl, {
      cache: 'no-store',
      headers: { Accept: 'application/json' }
    });
    if (!response.ok) {
      throw new Error('auth_unavailable');
    }
    const data = await response.json();
    if (!data.authorizationUrl) {
      throw new Error('auth_unavailable');
    }
    window.location.assign(data.authorizationUrl);
  } catch (error) {
    setAuthStatus('GitHub 로그인을 시작할 수 없습니다. 잠시 후 다시 시도해 주세요.');
  } finally {
    githubButton.disabled = false;
  }
}

function handleAuthorizationLoss() {
  serviceAccessToken = null;
  projectRequestSequence += 1;
  applicationRequestSequence += 1;
  dashboardRequestSequence += 1;
  instanceEvidenceRequestSequence += 1;
  clearProjectSnapshot({ resetFilter: true });
  clearApplicationSnapshot({ resetFilter: true, resetSelection: true });
  clearDashboardSnapshot({ resetSelection: true });
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  renderAuthorizationRequired();
  renderApplicationAuthorizationRequired();
  renderDashboardAuthorizationRequired();
}

function setAuthStatus(message) {
  authStatus.textContent = message;
}

function setGeneratedAtLabel(message) {
  generatedAtLabel.textContent = message;
}

function setApplicationsGeneratedAtLabel(message) {
  applicationsGeneratedAtLabel.textContent = message;
}

function setSelectedProjectLabel(message) {
  selectedProjectLabel.textContent = message;
}

function setDashboardGeneratedAtLabel(message) {
  dashboardGeneratedAtLabel.textContent = message;
}

function setSelectedApplicationLabel(message) {
  selectedApplicationLabel.textContent = message;
}

function setProjectViewState(nextState) {
  currentViewState = nextState;
  filterInput.disabled = !canRenderFilteredProjects();
}

function setApplicationViewState(nextState) {
  currentApplicationViewState = nextState;
  applicationFilterInput.disabled = !canRenderFilteredApplications();
  applicationReloadButton.disabled = !canReloadApplications();
}

function setDashboardViewState(nextState) {
  currentDashboardViewState = nextState;
}

function setInstanceEvidenceViewState(nextState) {
  currentInstanceEvidenceViewState = nextState;
}

function canRenderFilteredProjects() {
  return currentViewState === VIEW_STATE.READY || currentViewState === VIEW_STATE.FILTERED_EMPTY;
}

function canRenderFilteredApplications() {
  return currentApplicationViewState === APPLICATION_VIEW_STATE.READY
    || currentApplicationViewState === APPLICATION_VIEW_STATE.FILTERED_EMPTY;
}

function canReloadApplications() {
  return Boolean(serviceAccessToken && selectedProjectContext
    && isProjectApplicationsLink(selectedProjectContext.applicationsLink, selectedProjectContext.projectId));
}

function handleFilterInput() {
  if (!canRenderFilteredProjects()) {
    return;
  }
  renderProjects();
}

function handleApplicationFilterInput() {
  if (!canRenderFilteredApplications()) {
    return;
  }
  renderApplications();
}

function reconcileSelectedProjectAfterProjectLoad() {
  if (!selectedProjectContext) {
    return;
  }
  const matchingProject = loadedProjects.find(project =>
    String(project.projectId ?? '') === selectedProjectContext.projectId);
  const freshApplicationsLink = matchingProject ? safeApplicationsLink(matchingProject) : null;
  if (!matchingProject || freshApplicationsLink !== selectedProjectContext.applicationsLink) {
    applicationRequestSequence += 1;
    dashboardRequestSequence += 1;
    instanceEvidenceRequestSequence += 1;
    clearApplicationSnapshot({ resetFilter: true, resetSelection: true });
    clearDashboardSnapshot({ resetSelection: true });
    clearInstanceEvidenceSnapshot({ resetSelection: true });
    renderApplicationIdle();
    renderDashboardIdle();
  }
}

function clearProjectSnapshot({ resetFilter = false } = {}) {
  loadedProjects = [];
  loadedGeneratedAt = null;
  if (resetFilter) {
    filterInput.value = '';
  }
}

function clearApplicationSnapshot({ resetFilter = false, resetSelection = false } = {}) {
  loadedApplications = [];
  loadedApplicationsGeneratedAt = null;
  loadedApplicationsProject = null;
  if (resetFilter) {
    applicationFilterInput.value = '';
  }
  if (resetSelection) {
    selectedProjectContext = null;
  }
}

function clearDashboardSnapshot({ resetSelection = false } = {}) {
  loadedDashboard = null;
  if (resetSelection) {
    selectedDashboardContext = null;
  }
}

function clearInstanceEvidenceSnapshot({ resetSelection = false } = {}) {
  loadedInstanceEvidence = null;
  if (resetSelection) {
    selectedInstanceEvidenceContext = null;
  }
}

function discardInstanceEvidenceForParentReload() {
  instanceEvidenceRequestSequence += 1;
  const hadVisibleInstanceEvidence = Boolean(selectedInstanceEvidenceContext || loadedInstanceEvidence);
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  if (hadVisibleInstanceEvidence) {
    renderDashboardIdle();
  }
}

function isLatestProjectRequest(requestId) {
  return requestId === projectRequestSequence;
}

function isLatestApplicationRequest(requestId) {
  return requestId === applicationRequestSequence;
}

function isLatestDashboardRequest(requestId) {
  return requestId === dashboardRequestSequence;
}

function isLatestInstanceEvidenceRequest(requestId) {
  return requestId === instanceEvidenceRequestSequence;
}

function selectedProjectText() {
  if (!selectedProjectContext) {
    return 'Project를 선택하면 Application 목록을 볼 수 있습니다.';
  }
  const label = selectedProjectContext.projectName || selectedProjectContext.projectId;
  return `${label} · ${selectedProjectContext.projectId}`;
}

function applicationProjectText() {
  if (loadedApplicationsProject) {
    return `${valueOrAbsence(loadedApplicationsProject.name, 'Project name absence')} · ${valueOrAbsence(loadedApplicationsProject.projectId, 'Project id absence')}`;
  }
  return selectedProjectText();
}

function currentApplicationProjectId() {
  if (loadedApplicationsProject && loadedApplicationsProject.projectId) {
    return loadedApplicationsProject.projectId;
  }
  return selectedProjectContext ? selectedProjectContext.projectId : '';
}

function currentApplicationProjectName() {
  if (loadedApplicationsProject && loadedApplicationsProject.name) {
    return loadedApplicationsProject.name;
  }
  return selectedProjectContext ? selectedProjectContext.projectName : '';
}

function selectedApplicationText() {
  if (!selectedDashboardContext) {
    return 'Project를 선택하면 Application Dashboard를 볼 수 있습니다.';
  }
  const projectLabel = selectedDashboardContext.projectName || selectedDashboardContext.projectId;
  const applicationLabel = selectedDashboardContext.applicationName || selectedDashboardContext.applicationId;
  return `${projectLabel} · ${applicationLabel} · ${selectedDashboardContext.applicationId}`;
}

function dashboardApplicationText(application) {
  if (!application) {
    return selectedApplicationText();
  }
  return `${valueOrAbsence(application.name, 'Application name source absence')} · ${valueOrAbsence(application.environment, 'environment source absence')} · ${valueOrAbsence(application.applicationId, 'Application id source absence')}`;
}

function instanceEvidenceContextText() {
  if (!selectedInstanceEvidenceContext) {
    return 'Application Dashboard에서 instance를 선택하면 Evidence를 볼 수 있습니다.';
  }
  const applicationLabel = selectedInstanceEvidenceContext.applicationName || selectedInstanceEvidenceContext.applicationId;
  const instanceLabel = selectedInstanceEvidenceContext.instanceName || selectedInstanceEvidenceContext.instanceId;
  return `${valueOrAbsence(applicationLabel, 'Application source absence')} · ${valueOrAbsence(instanceLabel, 'Instance source absence')} · ${valueOrAbsence(selectedInstanceEvidenceContext.instanceId, 'Instance id source absence')}`;
}

function instanceEvidenceReadyText(evidence) {
  const application = evidence.application || {};
  const instance = evidence.instance || {};
  return `${valueOrAbsence(application.name, 'Application name source absence')} · ${valueOrAbsence(instance.instanceName, 'Instance name source absence')} · ${valueOrAbsence(instance.instanceId, 'Instance id source absence')}`;
}

function formatGeneratedAt(generatedAt) {
  if (!generatedAt) {
    return '목록 기준 시각 대기 중';
  }
  const date = new Date(generatedAt);
  if (Number.isNaN(date.getTime())) {
    return '목록 기준 시각 확인 불가';
  }
  return `목록 기준 ${new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date)}`;
}

function formatDashboardGeneratedAt(generatedAt) {
  if (!generatedAt) {
    return 'Dashboard 기준 시각 대기 중';
  }
  const date = new Date(generatedAt);
  if (Number.isNaN(date.getTime())) {
    return 'Dashboard 기준 시각 확인 불가';
  }
  return `Dashboard 기준 ${new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date)}`;
}

function formatEvidenceGeneratedAt(generatedAt) {
  if (!generatedAt) {
    return 'Instance Evidence 기준 시각 대기 중';
  }
  const date = new Date(generatedAt);
  if (Number.isNaN(date.getTime())) {
    return 'Instance Evidence 기준 시각 확인 불가';
  }
  return `Instance Evidence 기준 ${new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date)}`;
}

function formatTimestamp(value, fallback) {
  const normalized = String(value ?? '').trim();
  return normalized.length === 0 ? fallback : normalized;
}

function windowRangeText(window) {
  if (!window) {
    return 'source window absence';
  }
  return `${valueOrAbsence(window.startUtc, 'start source absence')} -> ${valueOrAbsence(window.endUtc, 'end source absence')}`;
}

function valueOrAbsence(value, fallback) {
  const normalized = String(value ?? '').trim();
  return normalized.length === 0 ? fallback : normalized;
}

function escapeText(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[character]));
}

function normalizeAccessToken(value) {
  const token = String(value ?? '').trim();
  return token.length === 0 ? null : token;
}

function escapeAttribute(value) {
  return escapeText(value);
}

reloadButton.addEventListener('click', loadProjects);
filterInput.addEventListener('input', handleFilterInput);
projectList.addEventListener('click', handleProjectListClick);
applicationList.addEventListener('click', handleApplicationListClick);
dashboardDetail.addEventListener('click', handleDashboardDetailClick);
applicationFilterInput.addEventListener('input', handleApplicationFilterInput);
applicationReloadButton.addEventListener('click', loadApplicationsForSelectedProject);
githubButton.addEventListener('click', startGithubEntry);
renderAuthorizationRequired();
renderApplicationAuthorizationRequired();
renderDashboardAuthorizationRequired();
