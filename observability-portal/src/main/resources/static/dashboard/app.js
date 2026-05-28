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

window.observationPortalAuth = Object.freeze({
  setAccessToken(accessToken) {
    serviceAccessToken = normalizeAccessToken(accessToken);
    projectRequestSequence += 1;
    applicationRequestSequence += 1;
    clearProjectSnapshot({ resetFilter: true });
    clearApplicationSnapshot({ resetFilter: true, resetSelection: true });
    if (!serviceAccessToken) {
      renderAuthorizationRequired();
      renderApplicationAuthorizationRequired();
      return;
    }
    renderApplicationIdle();
    loadProjects();
  },
  clearAccessToken() {
    handleAuthorizationLoss();
  }
});

async function loadProjects() {
  const requestId = ++projectRequestSequence;
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
  selectedProjectContext = {
    projectId: String(projectContext.projectId ?? ''),
    projectName: String(projectContext.projectName ?? ''),
    applicationsLink: String(projectContext.applicationsLink ?? '')
  };
  clearApplicationSnapshot({ resetFilter: true });
  if (!isProjectApplicationsLink(selectedProjectContext.applicationsLink, selectedProjectContext.projectId)) {
    renderApplicationInvalidLink();
    return;
  }
  loadApplicationsForSelectedProject();
}

async function loadApplicationsForSelectedProject() {
  const requestId = ++applicationRequestSequence;
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
        <button class="link-button dashboard-handoff" type="button" disabled aria-disabled="true" data-application-id="${escapeAttribute(application.applicationId)}" data-dashboard-link="${escapeAttribute(dashboardLink)}" aria-label="Dashboard handoff link">Dashboard</button>
      </div>
    </article>
  `;
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

function isSelectedProjectApplicationResponse(data) {
  if (!selectedProjectContext || !data || !data.project) {
    return false;
  }
  return String(data.project.projectId ?? '') === selectedProjectContext.projectId;
}

function hasValidApplicationItems(applications, projectId) {
  return applications.every(application => application
    && typeof application === 'object'
    && String(application.applicationId ?? '').trim().length > 0
    && isApplicationDashboardLink(
      application.links && application.links.dashboard,
      projectId,
      application.applicationId
    ));
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
  clearProjectSnapshot({ resetFilter: true });
  clearApplicationSnapshot({ resetFilter: true, resetSelection: true });
  renderAuthorizationRequired();
  renderApplicationAuthorizationRequired();
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

function setProjectViewState(nextState) {
  currentViewState = nextState;
  filterInput.disabled = !canRenderFilteredProjects();
}

function setApplicationViewState(nextState) {
  currentApplicationViewState = nextState;
  applicationFilterInput.disabled = !canRenderFilteredApplications();
  applicationReloadButton.disabled = !canReloadApplications();
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

function isLatestProjectRequest(requestId) {
  return requestId === projectRequestSequence;
}

function isLatestApplicationRequest(requestId) {
  return requestId === applicationRequestSequence;
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
applicationFilterInput.addEventListener('input', handleApplicationFilterInput);
applicationReloadButton.addEventListener('click', loadApplicationsForSelectedProject);
githubButton.addEventListener('click', startGithubEntry);
renderAuthorizationRequired();
renderApplicationAuthorizationRequired();
