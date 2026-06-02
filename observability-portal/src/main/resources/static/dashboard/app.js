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
const registrationForm = document.querySelector('#project-registration-form');
const projectNameInput = document.querySelector('#project-name-input');
const registrationSubmitButton = document.querySelector('#project-registration-submit');
const registrationStatus = document.querySelector('#project-registration-status');
const starterCredentialOneTime = document.querySelector('#starter-credential-onetime');
const credentialLifecyclePanel = document.querySelector('#credential-lifecycle-panel');

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

const INSTANCE_SNAPSHOT_TREND_VIEW_STATE = Object.freeze({
  IDLE: 'idle',
  LOADING: 'loading',
  AUTH_REQUIRED: 'auth-required',
  INVALID_LINK: 'invalid-link',
  BAD_QUERY: 'bad-query',
  NOT_FOUND: 'not-found',
  ERROR: 'error',
  READY: 'ready',
  PENDING_PRESET: 'pending-preset'
});

const INSTANCE_SNAPSHOT_TREND_PRESETS = Object.freeze({
  PENDING_24H: '24h',
  SEVEN_DAYS: '7d',
  FOURTEEN_DAYS: '14d'
});

const SNAPSHOT_HISTORY_VIEW_STATE = Object.freeze({
  IDLE: 'idle',
  LOADING: 'loading',
  AUTH_REQUIRED: 'auth-required',
  INVALID_LINK: 'invalid-link',
  BAD_QUERY: 'bad-query',
  NOT_FOUND: 'not-found',
  ERROR: 'error',
  READY: 'ready',
  EMPTY: 'empty'
});

const SNAPSHOT_DETAIL_VIEW_STATE = Object.freeze({
  IDLE: 'idle',
  LOADING: 'loading',
  AUTH_REQUIRED: 'auth-required',
  INVALID_LINK: 'invalid-link',
  NOT_FOUND_OR_EXPIRED: 'not-found-or-expired',
  ERROR: 'error',
  READY: 'ready'
});

const SNAPSHOT_HISTORY_PRESETS = Object.freeze({
  DAY: '24h',
  SEVEN_DAYS: '7d',
  FOURTEEN_DAYS: '14d'
});

const SNAPSHOT_HISTORY_PRESET_QUERY = Object.freeze({
  [SNAPSHOT_HISTORY_PRESETS.DAY]: Object.freeze({ eventLimit: 50, markerLimit: 50 }),
  [SNAPSHOT_HISTORY_PRESETS.SEVEN_DAYS]: Object.freeze({ eventLimit: 100, markerLimit: 168 }),
  [SNAPSHOT_HISTORY_PRESETS.FOURTEEN_DAYS]: Object.freeze({ eventLimit: 100, markerLimit: 336 })
});

// zeroInsight reason별 보조 문구는 서버가 준 reasonCode를 설명만 하며 상태를 새로 판정하지 않는다.
const ZERO_INSIGHT_SOURCE_GUIDANCE = Object.freeze({
  waiting_first_data: 'starter heartbeat는 수신됐지만 metric 판단 source인 accepted bucket은 아직 없습니다.',
  insufficient_sample: 'accepted bucket은 들어왔지만 minimum sample guard를 통과할 표본이 아직 부족합니다.',
  no_action_needed: '현재 우선 노출할 triage는 없습니다. accepted bucket freshness와 starter heartbeat는 별도 source로 봅니다.',
  metric_data_idle: 'metric data는 요청 없음, bucket sample 부족, 다음 accepted bucket 확인이 필요한 상태일 수 있습니다. starter heartbeat와 별도 source이며 host application 상태는 이 신호만으로 확정하지 않습니다.',
  telemetry_unreachable: 'starter/portal/network 연결 후보를 확인하세요. host application 상태는 이 신호만으로 확정하지 않습니다.',
  observing_recovery: '새 metric bucket이 다시 관찰됐고 sample이 충분해지는지 다음 bucket에서 확인합니다.'
});

// Snapshot marker type은 backend read model enum만 표시하기 위한 allow-list다.
const SNAPSHOT_MARKER_TYPES = Object.freeze([
  'scheduled_snapshot',
  'query_fallback_snapshot',
  'state_change',
  'state_observation',
  'high_confidence_concern',
  'short_strong_spike',
  'recovery_observed',
  'stored_snapshot'
]);

const GITHUB_OAUTH_CALLBACK_MESSAGE = 'observation-portal.github-oauth-complete';
const GITHUB_OAUTH_CALLBACK_RELAY_META = 'observation-github-callback-relay-id';
const GITHUB_OAUTH_RELAY_ENDPOINT = '/api/auth/github/callback/tokens';
const GITHUB_OAUTH_POPUP_NAME = 'observationPortalGithubOAuth';
const GITHUB_OAUTH_POPUP_FEATURES = 'width=520,height=720';
const GITHUB_OAUTH_WATCH_INTERVAL_MS = 500;
const GITHUB_OAUTH_WATCH_TIMEOUT_MS = 120000;

let serviceAccessToken = null;
let githubAuthWindow = null;
let githubAuthWatchTimer = null;
let githubAuthWatchStartedAt = 0;
let githubAuthRelayInFlight = false;
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
let selectedInstanceSnapshotTrendContext = null;
let loadedInstanceSnapshotTrend = null;
let currentInstanceSnapshotTrendViewState = INSTANCE_SNAPSHOT_TREND_VIEW_STATE.AUTH_REQUIRED;
let instanceSnapshotTrendRequestSequence = 0;
let selectedSnapshotHistoryContext = null;
let loadedOperationalEvents = null;
let loadedSnapshotMarkers = null;
let currentSnapshotHistoryViewState = SNAPSHOT_HISTORY_VIEW_STATE.AUTH_REQUIRED;
let snapshotHistoryRequestSequence = 0;
let selectedSnapshotDetailContext = null;
let loadedSnapshotDetail = null;
let currentSnapshotDetailViewState = SNAPSHOT_DETAIL_VIEW_STATE.AUTH_REQUIRED;
let snapshotDetailRequestSequence = 0;
let registrationRequestSequence = 0;
let credentialLifecycleRequestSequence = 0;

window.observationPortalAuth = Object.freeze({
  setAccessToken(accessToken) {
    serviceAccessToken = normalizeAccessToken(accessToken);
    projectRequestSequence += 1;
    applicationRequestSequence += 1;
    dashboardRequestSequence += 1;
    instanceEvidenceRequestSequence += 1;
    instanceSnapshotTrendRequestSequence += 1;
    snapshotHistoryRequestSequence += 1;
    snapshotDetailRequestSequence += 1;
    registrationRequestSequence += 1;
    credentialLifecycleRequestSequence += 1;
    clearProjectSnapshot({ resetFilter: true });
    clearApplicationSnapshot({ resetFilter: true, resetSelection: true });
    clearDashboardSnapshot({ resetSelection: true });
    clearInstanceEvidenceSnapshot({ resetSelection: true });
    clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
    clearSnapshotHistorySnapshot({ resetSelection: true });
    clearSnapshotDetailSnapshot({ resetSelection: true });
    clearCredentialUi();
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
  discardInstanceSnapshotTrendForParentReload();
  discardSnapshotHistoryForParentReload();
  discardSnapshotDetailForParentReload();
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
        <p><strong>Project가 아직 없습니다.</strong>아래 등록 폼으로 새 project를 만든 뒤 서버가 준 membership project 목록을 다시 불러옵니다.</p>
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
        <button class="link-button" type="button" data-credential-action="metadata" data-project-id="${escapeAttribute(project.projectId)}" data-project-name="${escapeAttribute(project.name)}">Credential</button>
        <button class="link-button" type="button" data-credential-action="rotate" data-project-id="${escapeAttribute(project.projectId)}" data-project-name="${escapeAttribute(project.name)}">Rotate</button>
        <button class="link-button danger" type="button" data-credential-action="revoke" data-project-id="${escapeAttribute(project.projectId)}" data-project-name="${escapeAttribute(project.name)}">Revoke</button>
      </div>
    </article>
  `;
}

function safeApplicationsLink(project) {
  const applicationsLink = project.links && project.links.applications ? String(project.links.applications) : '';
  return isProjectApplicationsLink(applicationsLink, project.projectId) ? applicationsLink : null;
}

function handleProjectListClick(event) {
  const credentialAction = event.target.closest('[data-credential-action]');
  if (credentialAction) {
    handleCredentialLifecycleAction({
      action: credentialAction.dataset.credentialAction,
      projectId: credentialAction.dataset.projectId,
      projectName: credentialAction.dataset.projectName
    });
    return;
  }
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
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  selectedProjectContext = {
    projectId: String(projectContext.projectId ?? ''),
    projectName: String(projectContext.projectName ?? ''),
    applicationsLink: String(projectContext.applicationsLink ?? '')
  };
  clearApplicationSnapshot({ resetFilter: true });
  clearDashboardSnapshot({ resetSelection: true });
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  clearSnapshotHistorySnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
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
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  clearDashboardSnapshot({ resetSelection: true });
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  clearSnapshotHistorySnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
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
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
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
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  clearSnapshotHistorySnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
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
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  clearSnapshotHistorySnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
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
  setInstanceSnapshotTrendViewState(INSTANCE_SNAPSHOT_TREND_VIEW_STATE.IDLE);
  setSnapshotHistoryViewState(SNAPSHOT_HISTORY_VIEW_STATE.IDLE);
  setSnapshotDetailViewState(SNAPSHOT_DETAIL_VIEW_STATE.IDLE);
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
        ${keyValueMarkup('source', 'accepted_bucket')}
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
        <p class="dashboard-empty-copy">${escapeText(zeroInsightSourceGuidance(zeroInsight.reasonCode))}</p>
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

function zeroInsightSourceGuidance(reasonCode) {
  const key = String(reasonCode ?? '').trim();
  return ZERO_INSIGHT_SOURCE_GUIDANCE[key] || 'server-provided zeroInsight reason/action을 accepted bucket metric source와 starter heartbeat source 경계 안에서 표시합니다.';
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
  const snapshotHistoryBackAction = event.target.closest('[data-snapshot-history-back]');
  if (snapshotHistoryBackAction) {
    renderSnapshotHistoryFromDetail();
    return;
  }
  const backAction = event.target.closest('[data-dashboard-back]');
  if (backAction) {
    renderApplicationDashboardFromDetail();
    return;
  }
  const historyPresetAction = event.target.closest('[data-history-preset]');
  if (historyPresetAction) {
    if (historyPresetAction.disabled || historyPresetAction.getAttribute && historyPresetAction.getAttribute('aria-disabled') === 'true') {
      return;
    }
    loadSnapshotHistoryForSelectedApplication(historyPresetAction.dataset.historyPreset);
    return;
  }
  const snapshotHistoryAction = event.target.closest('[data-snapshot-history-action]');
  if (snapshotHistoryAction) {
    if (snapshotHistoryAction.disabled || snapshotHistoryAction.getAttribute && snapshotHistoryAction.getAttribute('aria-disabled') === 'true') {
      return;
    }
    selectSnapshotHistory();
    return;
  }
  const snapshotDetailAction = event.target.closest('[data-snapshot-detail-link]');
  if (snapshotDetailAction) {
    if (snapshotDetailAction.disabled || snapshotDetailAction.getAttribute && snapshotDetailAction.getAttribute('aria-disabled') === 'true') {
      return;
    }
    selectSnapshotDetailFromLink({
      detailLink: snapshotDetailAction.dataset.snapshotDetailLink,
      anchor: snapshotDetailAction.dataset.snapshotDetailAnchor,
      origin: snapshotDetailAction.dataset.snapshotDetailOrigin
    });
    return;
  }
  const trendDetailAction = event.target.closest('[data-trend-snapshot-id]');
  if (trendDetailAction) {
    if (trendDetailAction.disabled || trendDetailAction.getAttribute && trendDetailAction.getAttribute('aria-disabled') === 'true') {
      return;
    }
    selectSnapshotDetailFromTrendPoint({
      snapshotId: trendDetailAction.dataset.trendSnapshotId,
      anchor: trendDetailAction.dataset.snapshotDetailAnchor
    });
    return;
  }
  const instanceEvidenceBackAction = event.target.closest('[data-instance-evidence-back]');
  if (instanceEvidenceBackAction) {
    renderInstanceEvidenceFromSnapshotTrend();
    return;
  }
  const trendHorizonAction = event.target.closest('[data-trend-horizon]');
  if (trendHorizonAction) {
    if (trendHorizonAction.disabled || trendHorizonAction.getAttribute && trendHorizonAction.getAttribute('aria-disabled') === 'true') {
      return;
    }
    loadInstanceSnapshotTrendForSelectedInstance(trendHorizonAction.dataset.trendHorizon);
    return;
  }
  const snapshotTrendAction = event.target.closest('button[data-snapshot-trend-link]');
  if (snapshotTrendAction) {
    if (snapshotTrendAction.disabled || snapshotTrendAction.getAttribute && snapshotTrendAction.getAttribute('aria-disabled') === 'true') {
      return;
    }
    selectInstanceSnapshotTrend({
      snapshotTrendLink: snapshotTrendAction.dataset.snapshotTrendLink
    });
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
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  clearSnapshotHistorySnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
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
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  clearSnapshotHistorySnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
  if (!selectedInstanceEvidenceContext) {
    renderApplicationDashboardFromDetail();
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

function renderApplicationDashboardFromDetail() {
  instanceEvidenceRequestSequence += 1;
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  clearSnapshotHistorySnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
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
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  loadedInstanceEvidence = null;
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  clearSnapshotHistorySnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
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

function selectInstanceSnapshotTrend(trendContext) {
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  clearSnapshotHistorySnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
  selectedInstanceSnapshotTrendContext = {
    projectId: selectedInstanceEvidenceContext ? selectedInstanceEvidenceContext.projectId : '',
    projectName: selectedInstanceEvidenceContext ? selectedInstanceEvidenceContext.projectName : '',
    applicationId: selectedInstanceEvidenceContext ? selectedInstanceEvidenceContext.applicationId : '',
    applicationName: selectedInstanceEvidenceContext ? selectedInstanceEvidenceContext.applicationName : '',
    applicationEnvironment: selectedInstanceEvidenceContext ? selectedInstanceEvidenceContext.applicationEnvironment : '',
    instanceId: selectedInstanceEvidenceContext ? selectedInstanceEvidenceContext.instanceId : '',
    instanceName: selectedInstanceEvidenceContext ? selectedInstanceEvidenceContext.instanceName : '',
    snapshotTrendLink: String(trendContext.snapshotTrendLink ?? ''),
    horizonPreset: INSTANCE_SNAPSHOT_TREND_PRESETS.SEVEN_DAYS
  };
  loadedInstanceSnapshotTrend = null;
  if (!serviceAccessToken) {
    renderInstanceSnapshotTrendAuthorizationRequired();
    return;
  }
  if (!isInstanceSnapshotTrendLink(
    selectedInstanceSnapshotTrendContext.snapshotTrendLink,
    selectedInstanceSnapshotTrendContext.projectId,
    selectedInstanceSnapshotTrendContext.applicationId,
    selectedInstanceSnapshotTrendContext.instanceId
  )) {
    renderInstanceSnapshotTrendInvalidLink();
    return;
  }
  loadInstanceSnapshotTrendForSelectedInstance(INSTANCE_SNAPSHOT_TREND_PRESETS.SEVEN_DAYS);
}

async function loadInstanceSnapshotTrendForSelectedInstance(horizonPreset) {
  const normalizedPreset = normalizeTrendPreset(horizonPreset);
  const requestId = ++instanceSnapshotTrendRequestSequence;
  if (!selectedInstanceSnapshotTrendContext) {
    renderInstanceEvidenceFromSnapshotTrend();
    return;
  }
  selectedInstanceSnapshotTrendContext.horizonPreset = normalizedPreset;
  if (normalizedPreset === INSTANCE_SNAPSHOT_TREND_PRESETS.PENDING_24H) {
    loadedInstanceSnapshotTrend = null;
    renderInstanceSnapshotTrendPendingPreset();
    return;
  }
  if (!serviceAccessToken) {
    loadedInstanceSnapshotTrend = null;
    renderInstanceSnapshotTrendAuthorizationRequired();
    return;
  }
  if (!isInstanceSnapshotTrendLink(
    selectedInstanceSnapshotTrendContext.snapshotTrendLink,
    selectedInstanceSnapshotTrendContext.projectId,
    selectedInstanceSnapshotTrendContext.applicationId,
    selectedInstanceSnapshotTrendContext.instanceId
  )) {
    renderInstanceSnapshotTrendInvalidLink();
    return;
  }
  const requestLink = snapshotTrendRequestLink(selectedInstanceSnapshotTrendContext.snapshotTrendLink, normalizedPreset);
  if (!requestLink) {
    loadedInstanceSnapshotTrend = null;
    renderInstanceSnapshotTrendPendingPreset();
    return;
  }
  renderInstanceSnapshotTrendLoadingState();
  try {
    const response = await fetch(snapshotTrendRequestLink(
      selectedInstanceSnapshotTrendContext.snapshotTrendLink,
      normalizedPreset
    ), { headers: projectRequestHeaders() });
    if (!isLatestInstanceSnapshotTrendRequest(requestId)) {
      return;
    }
    if (response.status === 401) {
      handleInstanceSnapshotTrendAuthorizationLoss();
      return;
    }
    if (response.status === 400) {
      loadedInstanceSnapshotTrend = null;
      renderInstanceSnapshotTrendBadQuery();
      return;
    }
    if (response.status === 404) {
      loadedInstanceSnapshotTrend = null;
      renderInstanceSnapshotTrendNotFound();
      return;
    }
    if (!response.ok) {
      throw new Error('instance_snapshot_trend_load_failed');
    }
    const trend = await response.json();
    if (!isLatestInstanceSnapshotTrendRequest(requestId)) {
      return;
    }
    if (!isSelectedInstanceSnapshotTrendResponse(trend)) {
      throw new Error('instance_snapshot_trend_context_mismatch');
    }
    loadedInstanceSnapshotTrend = trend;
    renderInstanceSnapshotTrendReady();
  } catch (error) {
    if (!isLatestInstanceSnapshotTrendRequest(requestId)) {
      return;
    }
    loadedInstanceSnapshotTrend = null;
    renderInstanceSnapshotTrendLoadError();
  }
}

function renderInstanceEvidenceFromSnapshotTrend() {
  instanceSnapshotTrendRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
  if (!serviceAccessToken) {
    renderInstanceEvidenceAuthorizationRequired();
    return;
  }
  if (loadedInstanceEvidence && selectedInstanceEvidenceContext) {
    renderInstanceEvidenceReady();
    return;
  }
  renderApplicationDashboardFromDetail();
}

function renderInstanceSnapshotTrendLoadingState() {
  setInstanceSnapshotTrendViewState(INSTANCE_SNAPSHOT_TREND_VIEW_STATE.LOADING);
  setDashboardGeneratedAtLabel('Instance Snapshot Trend를 불러오는 중');
  setSelectedApplicationLabel(instanceSnapshotTrendContextText());
  dashboardDetail.innerHTML = instanceSnapshotTrendShellMarkup({
    state: 'loading',
    generatedAt: null,
    bodyMarkup: `
      <div class="loading-state" aria-label="Instance Snapshot Trend 로딩 중">
        <p class="dashboard-empty-copy">Instance Snapshot Trend를 불러오는 중</p>
        <div class="skeleton-line"></div>
        <div class="skeleton-line short"></div>
      </div>
    `
  });
}

function renderInstanceSnapshotTrendAuthorizationRequired() {
  setInstanceSnapshotTrendViewState(INSTANCE_SNAPSHOT_TREND_VIEW_STATE.AUTH_REQUIRED);
  setDashboardGeneratedAtLabel('로그인이 필요합니다');
  setSelectedApplicationLabel(instanceSnapshotTrendContextText());
  dashboardDetail.innerHTML = instanceSnapshotTrendShellMarkup({
    state: 'auth-required',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>GitHub 로그인 후 Instance Snapshot Trend를 볼 수 있습니다.</strong>로그인을 완료한 뒤 같은 Instance Evidence에서 Snapshot Trend action을 다시 선택해 주세요.</p>
      </div>
    `
  });
}

function handleInstanceSnapshotTrendAuthorizationLoss() {
  serviceAccessToken = null;
  projectRequestSequence += 1;
  applicationRequestSequence += 1;
  dashboardRequestSequence += 1;
  instanceEvidenceRequestSequence += 1;
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  loadedInstanceSnapshotTrend = null;
  clearSnapshotHistorySnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
  renderAuthorizationRequired();
  renderApplicationAuthorizationRequired();
  renderInstanceSnapshotTrendAuthorizationRequired();
}

function renderInstanceSnapshotTrendInvalidLink() {
  setInstanceSnapshotTrendViewState(INSTANCE_SNAPSHOT_TREND_VIEW_STATE.INVALID_LINK);
  setDashboardGeneratedAtLabel('Instance Snapshot Trend 기준 시각 확인 불가');
  setSelectedApplicationLabel(instanceSnapshotTrendContextText());
  dashboardDetail.innerHTML = instanceSnapshotTrendShellMarkup({
    state: 'invalid-link',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>Snapshot Trend link를 확인할 수 없습니다.</strong>현재 Project/Application/Instance와 일치하는 내부 Snapshot Trend link만 사용할 수 있습니다.</p>
      </div>
    `
  });
}

function renderInstanceSnapshotTrendBadQuery() {
  setInstanceSnapshotTrendViewState(INSTANCE_SNAPSHOT_TREND_VIEW_STATE.BAD_QUERY);
  setDashboardGeneratedAtLabel('Instance Snapshot Trend 기준 시각 확인 불가');
  setSelectedApplicationLabel(instanceSnapshotTrendContextText());
  dashboardDetail.innerHTML = instanceSnapshotTrendShellMarkup({
    state: 'bad-query',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>Snapshot Trend query contract를 확인할 수 없습니다.</strong>UI는 fixed 7d/14d preset만 사용하며 잠시 후 다시 시도해 주세요.</p>
      </div>
    `
  });
}

function renderInstanceSnapshotTrendNotFound() {
  setInstanceSnapshotTrendViewState(INSTANCE_SNAPSHOT_TREND_VIEW_STATE.NOT_FOUND);
  setDashboardGeneratedAtLabel('Instance Snapshot Trend 기준 시각 확인 불가');
  setSelectedApplicationLabel(instanceSnapshotTrendContextText());
  dashboardDetail.innerHTML = instanceSnapshotTrendShellMarkup({
    state: 'not-found',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>Project/Application/Instance scope를 찾을 수 없습니다.</strong>scope mismatch 또는 접근 가능한 instance 범위를 다시 확인해 주세요.</p>
      </div>
    `
  });
}

function renderInstanceSnapshotTrendLoadError() {
  setInstanceSnapshotTrendViewState(INSTANCE_SNAPSHOT_TREND_VIEW_STATE.ERROR);
  setDashboardGeneratedAtLabel('Instance Snapshot Trend 기준 시각 확인 불가');
  setSelectedApplicationLabel(instanceSnapshotTrendContextText());
  dashboardDetail.innerHTML = instanceSnapshotTrendShellMarkup({
    state: 'error',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>Instance Snapshot Trend를 불러오지 못했습니다.</strong>잠시 후 같은 instance에서 다시 시도해 주세요.</p>
      </div>
    `
  });
}

function renderInstanceSnapshotTrendPendingPreset() {
  setInstanceSnapshotTrendViewState(INSTANCE_SNAPSHOT_TREND_VIEW_STATE.PENDING_PRESET);
  setDashboardGeneratedAtLabel('Instance Snapshot Trend 24h preset 대기 중');
  setSelectedApplicationLabel(instanceSnapshotTrendContextText());
  dashboardDetail.innerHTML = instanceSnapshotTrendShellMarkup({
    state: 'pending-preset',
    generatedAt: loadedInstanceSnapshotTrend ? loadedInstanceSnapshotTrend.generatedAt : null,
    bodyMarkup: `
      ${instanceSnapshotTrendHorizonControlsMarkup()}
      <div class="empty-state">
        <p><strong>24h preset pending.</strong>현재 backend contract는 7d/14d만 지원하므로 24h backend 호출은 만들지 않습니다.</p>
      </div>
    `
  });
}

function renderInstanceSnapshotTrendReady() {
  const trend = loadedInstanceSnapshotTrend;
  setInstanceSnapshotTrendViewState(INSTANCE_SNAPSHOT_TREND_VIEW_STATE.READY);
  setDashboardGeneratedAtLabel(formatTrendGeneratedAt(trend.generatedAt));
  setSelectedApplicationLabel(instanceSnapshotTrendReadyText(trend));
  dashboardDetail.innerHTML = instanceSnapshotTrendShellMarkup({
    state: 'ready',
    generatedAt: trend.generatedAt,
    bodyMarkup: `
      ${instanceSnapshotTrendHorizonControlsMarkup()}
      ${instanceSnapshotTrendMetadataMarkup(trend)}
      ${instanceSnapshotTrendLanesMarkup(trend.points)}
      ${instanceSnapshotTrendPointListMarkup(trend.points)}
    `
  });
}

function instanceSnapshotTrendShellMarkup({ state, generatedAt, bodyMarkup }) {
  return `
    <article class="instance-snapshot-trend-detail" data-instance-snapshot-trend-state="${escapeAttribute(state)}">
      ${instanceSnapshotTrendHeaderMarkup(generatedAt)}
      ${bodyMarkup}
    </article>
  `;
}

function instanceSnapshotTrendHeaderMarkup(generatedAt) {
  const context = selectedInstanceSnapshotTrendContext || {};
  return `
    <section class="dashboard-section instance-detail-header" aria-label="Instance snapshot trend context">
      <div class="instance-detail-title">
        <div>
          <p class="eyebrow">Instance Snapshot Trend</p>
          <h3>${escapeText(valueOrAbsence(context.instanceName, 'instance name source absence'))}</h3>
        </div>
        <div class="detail-back-actions">
          <button class="link-button" type="button" data-dashboard-back="true">Application Dashboard</button>
          <button class="link-button" type="button" data-instance-evidence-back="true">Instance Evidence</button>
        </div>
      </div>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('project id', context.projectId)}
        ${keyValueMarkup('application id', context.applicationId)}
        ${keyValueMarkup('application', valueOrAbsence(context.applicationName, 'application name source absence'))}
        ${keyValueMarkup('environment', context.applicationEnvironment)}
        ${keyValueMarkup('instanceId', context.instanceId)}
        ${keyValueMarkup('instanceName', context.instanceName)}
        ${keyValueMarkup('generatedAt', valueOrAbsence(generatedAt, 'trend generatedAt source absence'))}
      </dl>
    </section>
  `;
}

function instanceSnapshotTrendHorizonControlsMarkup() {
  const selectedPreset = selectedInstanceSnapshotTrendContext
    ? selectedInstanceSnapshotTrendContext.horizonPreset
    : INSTANCE_SNAPSHOT_TREND_PRESETS.SEVEN_DAYS;
  return `
    <section class="dashboard-section trend-horizon-controls" aria-label="Snapshot trend horizon controls">
      <p class="eyebrow">Horizon preset</p>
      <div class="trend-preset-row">
        ${trendPresetButtonMarkup(INSTANCE_SNAPSHOT_TREND_PRESETS.PENDING_24H, selectedPreset, true)}
        ${trendPresetButtonMarkup(INSTANCE_SNAPSHOT_TREND_PRESETS.SEVEN_DAYS, selectedPreset, false)}
        ${trendPresetButtonMarkup(INSTANCE_SNAPSHOT_TREND_PRESETS.FOURTEEN_DAYS, selectedPreset, false)}
      </div>
      <p class="dashboard-empty-copy">24h preset pending · backend 지원 전까지 24h backend call을 만들지 않습니다.</p>
    </section>
  `;
}

function trendPresetButtonMarkup(preset, selectedPreset, disabled) {
  const selected = preset === selectedPreset ? 'true' : 'false';
  const disabledAttributes = disabled ? 'disabled aria-disabled="true"' : 'aria-disabled="false"';
  return `<button class="link-button trend-preset-button" type="button" data-trend-horizon="${escapeAttribute(preset)}" data-selected="${selected}" ${disabledAttributes}>${escapeText(preset)}</button>`;
}

function instanceSnapshotTrendMetadataMarkup(trend) {
  const application = trend.application || {};
  const instance = trend.instance || {};
  const horizon = trend.horizon || {};
  return `
    <section class="dashboard-section" aria-label="Snapshot trend metadata">
      <p class="eyebrow">Trend source and horizon</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('source', trend.source)}
        ${keyValueMarkup('projectId', application.projectId)}
        ${keyValueMarkup('applicationId', application.applicationId)}
        ${keyValueMarkup('instanceId', instance.instanceId)}
        ${keyValueMarkup('since', horizon.since)}
        ${keyValueMarkup('until', horizon.until)}
        ${keyValueMarkup('requestedSince', horizon.requestedSince)}
        ${keyValueMarkup('defaultSince', horizon.defaultSince)}
        ${keyValueMarkup('maxSince', horizon.maxSince)}
        ${keyValueMarkup('limit', horizon.limit)}
        ${keyValueMarkup('maxLimit', horizon.maxLimit)}
        ${keyValueMarkup('order', horizon.order)}
        ${keyValueMarkup('point count', Array.isArray(trend.points) ? trend.points.length : 0)}
      </dl>
    </section>
  `;
}

function instanceSnapshotTrendLanesMarkup(points) {
  const storedPoints = Array.isArray(points) ? points : [];
  const emptyCopy = storedPoints.length === 0
    ? '<p class="dashboard-empty-copy">points source absence · display range empty, snapshot source absence, retention gap, target instance absence 중 하나일 수 있습니다.</p>'
    : '';
  return `
    <section class="dashboard-section snapshot-trend-lanes" aria-label="Snapshot trend lanes">
      <p class="eyebrow">Stored trend lanes</p>
      ${emptyCopy}
      ${storedPoints.length === 0 ? '' : noConcernObservedCopy(storedPoints)}
      <div class="trend-lane-grid">
        ${trendLaneMarkup('Stored application state copy lane', storedPoints.map(point => [
          point.capturedAt,
          `storedApplicationStateCode ${valueOrAbsence(point.storedApplicationStateCode, 'source absence')}`
        ]))}
        ${trendLaneMarkup('Metric data axis lane', storedPoints.map(point => [
          point.capturedAt,
          `source ${valueOrAbsence(point.metricData && point.metricData.statusSource, 'metric data source absence')}`,
          `freshness ${valueOrAbsence(point.metricData && point.metricData.freshnessLabel, 'metric freshness source absence')}`
        ]))}
        ${trendLaneMarkup('Starter connection axis lane', storedPoints.map(point => [
          point.capturedAt,
          `source ${valueOrAbsence(point.starterConnection && point.starterConnection.statusSource, 'starter heartbeat source absence')}`,
          `stateImpact ${valueOrAbsence(point.starterConnection && point.starterConnection.stateImpact, 'state impact source absence')}`,
          `meaning ${valueOrAbsence(point.starterConnection && point.starterConnection.connectionMeaning, 'connection meaning source absence')}`
        ]))}
        ${trendLaneMarkup('Capture reason/source marker lane', storedPoints.map(point => [
          point.capturedAt,
          `captureReason opaque metadata ${valueOrAbsence(point.captureReason, 'capture reason source absence')}`
        ]))}
      </div>
    </section>
  `;
}

function trendLaneMarkup(title, rows) {
  return `
    <article class="dashboard-mini-card trend-lane-card">
      <h3>${escapeText(title)}</h3>
      <div class="dashboard-list">
        ${rows.length === 0 ? '<p class="dashboard-empty-copy">lane source absence</p>' : rows.map(row => `<p class="dashboard-empty-copy">${escapeText(row.filter(Boolean).join(' · '))}</p>`).join('')}
      </div>
    </article>
  `;
}

function noConcernObservedCopy(points) {
  return points.some(hasStoredConcernCandidate)
    ? ''
    : '<p class="dashboard-empty-copy">no concern observed · selected stored points에서 triage contribution 또는 endpoint reference가 관찰되지 않았다는 뜻이며 health proof가 아닙니다.</p>';
}

function hasStoredConcernCandidate(point) {
  const contribution = point.applicationTriageContribution || {};
  const refs = Array.isArray(point.endpointEvidenceRefs) ? point.endpointEvidenceRefs : [];
  return contribution.contributed === true || refs.length > 0;
}

function instanceSnapshotTrendPointListMarkup(points) {
  const storedPoints = Array.isArray(points) ? points : [];
  return `
    <section class="dashboard-section" aria-label="Snapshot trend point list">
      <p class="eyebrow">Point list and stored detail</p>
      <div class="dashboard-list">
        ${storedPoints.length === 0 ? '<p class="dashboard-empty-copy">stored point source absence · display range empty 또는 missing hourly snapshot은 보간하지 않습니다.</p>' : storedPoints.map(instanceSnapshotTrendPointMarkup).join('')}
      </div>
    </section>
  `;
}

function instanceSnapshotTrendPointMarkup(point) {
  return `
    <article class="dashboard-mini-card">
      <h3>${escapeText(valueOrAbsence(point.capturedAt, 'capturedAt source absence'))}</h3>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('snapshotId', point.snapshotId)}
        ${keyValueMarkup('capturedAt', point.capturedAt)}
        ${keyValueMarkup('currentWindowEndUtc', point.currentWindowEndUtc)}
        ${keyValueMarkup('storedApplicationStateCode', point.storedApplicationStateCode)}
        ${keyValueMarkup('captureReason', valueOrAbsence(point.captureReason, 'nullable opaque metadata source absence'))}
        ${keyValueMarkup('instanceName', point.instanceName)}
        ${keyValueMarkup('observationStatus', point.observationStatus)}
        ${keyValueMarkup('metricData', snapshotTrendMetricDataText(point.metricData))}
        ${keyValueMarkup('starterConnection', snapshotTrendStarterConnectionText(point.starterConnection))}
        ${keyValueMarkup('starterPercentilePoint', snapshotTrendStarterPercentilePointText(point.starterPercentilePoint))}
        ${keyValueMarkup('resourceHints', snapshotTrendResourceHintsText(point.resourceHints))}
        ${keyValueMarkup('applicationTriageContribution', snapshotTrendTriageContributionText(point.applicationTriageContribution))}
        ${keyValueMarkup('endpointEvidenceRefs', snapshotTrendEndpointRefsText(point.endpointEvidenceRefs))}
      </dl>
      ${snapshotTrendDetailActionsMarkup(point)}
    </article>
  `;
}

function snapshotTrendDetailActionsMarkup(point) {
  const snapshotId = String(point && point.snapshotId ? point.snapshotId : '');
  const detailLink = canonicalSnapshotDetailLink(snapshotId);
  if (!detailLink) {
    return '<p class="dashboard-empty-copy">snapshot detail link source absence</p>';
  }
  const refs = Array.isArray(point.endpointEvidenceRefs) ? point.endpointEvidenceRefs : [];
  const anchorButtons = refs
    .filter(ref => hasRequiredText(ref.snapshotDetailAnchor))
    .map(ref => `
      <button class="link-button" type="button" data-trend-snapshot-id="${escapeAttribute(snapshotId)}" data-snapshot-detail-anchor="${escapeAttribute(ref.snapshotDetailAnchor)}">
        ${escapeText(valueOrAbsence(ref.endpointKey, 'endpoint anchor'))}
      </button>
    `)
    .join('');
  return `
    <div class="snapshot-detail-actions">
      <button class="link-button" type="button" data-trend-snapshot-id="${escapeAttribute(snapshotId)}" data-snapshot-detail-anchor="">Snapshot detail</button>
      ${anchorButtons}
    </div>
  `;
}

function snapshotTrendMetricDataText(metricData) {
  const metric = metricData || {};
  return [
    metric.statusSource,
    `lastAcceptedBucketAt ${valueOrAbsence(metric.lastAcceptedBucketAt, 'accepted bucket source absence')}`,
    `freshness ${valueOrAbsence(metric.freshnessLabel, 'metric freshness source absence')}`
  ].filter(value => String(value ?? '').trim().length > 0).join(' · ');
}

function snapshotTrendStarterConnectionText(starterConnection) {
  const starter = starterConnection || {};
  return [
    starter.statusSource,
    `lastHeartbeatAt ${valueOrAbsence(starter.lastHeartbeatAt, 'starter heartbeat source absence')}`,
    `lastHeartbeatStatus ${valueOrAbsence(starter.lastHeartbeatStatus, 'heartbeat status source absence')}`,
    `connectionMeaning ${valueOrAbsence(starter.connectionMeaning, 'connection meaning source absence')}`,
    `stateImpact ${valueOrAbsence(starter.stateImpact, 'state impact source absence')}`
  ].filter(value => String(value ?? '').trim().length > 0).join(' · ');
}

function snapshotTrendStarterPercentilePointText(point) {
  if (!point) {
    return 'stored starter percentile point source absence';
  }
  return [
    point.source,
    point.scope,
    `bucketStartUtc ${point.bucketStartUtc}`,
    `bucketEndUtc ${point.bucketEndUtc}`,
    `requestCount ${point.requestCount}`,
    `p95Ms ${point.p95Ms}`,
    `p99Ms ${point.p99Ms}`
  ].join(' · ');
}

function snapshotTrendResourceHintsText(resourceHints) {
  if (!resourceHints) {
    return 'stored resource hint source absence';
  }
  return [
    resourceHints.source,
    `status ${resourceHints.status}`,
    `bucketEndUtc ${valueOrAbsence(resourceHints.bucketEndUtc, 'latest sample source absence')}`,
    `cpuUsageRatio ${valueOrAbsence(resourceHints.cpuUsageRatio, 'source absence')}`,
    `heapUsedRatio ${valueOrAbsence(resourceHints.heapUsedRatio, 'source absence')}`,
    `datasourcePoolUsageRatio ${valueOrAbsence(resourceHints.datasourcePoolUsageRatio, 'source absence')}`
  ].join(' · ');
}

function snapshotTrendTriageContributionText(applicationTriageContribution) {
  const contribution = applicationTriageContribution || {};
  const ruleIds = Array.isArray(contribution.relatedRuleIds) ? contribution.relatedRuleIds.join(', ') : '';
  const absence = contribution.contributed ? '' : '저장된 application triage contribution 없음';
  return [
    `status ${valueOrAbsence(contribution.status, 'triage contribution status source absence')}`,
    `contributed ${valueOrAbsence(contribution.contributed, 'source absence')}`,
    `relatedRuleIds ${valueOrAbsence(ruleIds, 'triage rule source absence')}`,
    `reason ${valueOrAbsence(contribution.reason, absence || 'triage contribution reason source absence')}`,
    absence
  ].filter(value => String(value ?? '').trim().length > 0).join(' · ');
}

function snapshotTrendEndpointRefsText(endpointEvidenceRefs) {
  const refs = Array.isArray(endpointEvidenceRefs) ? endpointEvidenceRefs : [];
  if (refs.length === 0) {
    return 'bounded endpoint reference source absence';
  }
  return refs.map(ref => [
    ref.endpointKey,
    ref.method,
    ref.route,
    `relatedApplicationPriorityRank ${valueOrAbsence(ref.relatedApplicationPriorityRank, 'rank source absence')}`,
    `relatedRuleIds ${Array.isArray(ref.relatedRuleIds) ? ref.relatedRuleIds.join(', ') : ''}`,
    `snapshotDetailAnchor ${valueOrAbsence(ref.snapshotDetailAnchor, 'anchor source absence')}`
  ].filter(value => String(value ?? '').trim().length > 0).join(' · ')).join(' | ');
}

function selectSnapshotHistory() {
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  instanceEvidenceRequestSequence += 1;
  instanceSnapshotTrendRequestSequence += 1;
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
  selectedSnapshotHistoryContext = snapshotHistoryContextFromSelectedDashboard();
  loadedOperationalEvents = null;
  loadedSnapshotMarkers = null;
  if (!serviceAccessToken) {
    renderSnapshotHistoryAuthorizationRequired();
    return;
  }
  if (!isValidSnapshotHistoryContext(selectedSnapshotHistoryContext)) {
    renderSnapshotHistoryInvalidLink();
    return;
  }
  loadSnapshotHistoryForSelectedApplication(SNAPSHOT_HISTORY_PRESETS.DAY);
}

async function loadSnapshotHistoryForSelectedApplication(preset) {
  const normalizedPreset = normalizeSnapshotHistoryPreset(preset);
  const requestId = ++snapshotHistoryRequestSequence;
  snapshotDetailRequestSequence += 1;
  clearSnapshotDetailSnapshot({ resetSelection: true });
  if (!selectedSnapshotHistoryContext) {
    renderApplicationDashboardFromDetail();
    return;
  }
  selectedSnapshotHistoryContext.preset = normalizedPreset;
  if (!serviceAccessToken) {
    loadedOperationalEvents = null;
    loadedSnapshotMarkers = null;
    renderSnapshotHistoryAuthorizationRequired();
    return;
  }
  if (!isValidSnapshotHistoryContext(selectedSnapshotHistoryContext)) {
    renderSnapshotHistoryInvalidLink();
    return;
  }
  const eventsLink = snapshotHistoryOperationalEventsRequestLink(normalizedPreset);
  const markersLink = snapshotHistoryMarkersRequestLink(normalizedPreset);
  if (!eventsLink || !markersLink) {
    loadedOperationalEvents = null;
    loadedSnapshotMarkers = null;
    renderSnapshotHistoryBadQuery();
    return;
  }
  renderSnapshotHistoryLoadingState();
  try {
    const eventResponsePromise = fetch(eventsLink, { headers: projectRequestHeaders() });
    const markerResponsePromise = fetch(markersLink, { headers: projectRequestHeaders() });
    const [eventResponse, markerResponse] = await Promise.all([eventResponsePromise, markerResponsePromise]);
    if (!isLatestSnapshotHistoryRequest(requestId)) {
      return;
    }
    if (eventResponse.status === 401 || markerResponse.status === 401) {
      handleSnapshotHistoryAuthorizationLoss();
      return;
    }
    if (eventResponse.status === 400 || markerResponse.status === 400) {
      loadedOperationalEvents = null;
      loadedSnapshotMarkers = null;
      renderSnapshotHistoryBadQuery();
      return;
    }
    if (eventResponse.status === 404 || markerResponse.status === 404) {
      loadedOperationalEvents = null;
      loadedSnapshotMarkers = null;
      renderSnapshotHistoryNotFound();
      return;
    }
    if (!eventResponse.ok || !markerResponse.ok) {
      throw new Error('snapshot_history_load_failed');
    }
    const [events, markers] = await Promise.all([eventResponse.json(), markerResponse.json()]);
    if (!isLatestSnapshotHistoryRequest(requestId)) {
      return;
    }
    if (!isSelectedOperationalEventsHistoryResponse(events, normalizedPreset)
      || !isSelectedSnapshotMarkerResponse(markers, normalizedPreset)) {
      throw new Error('snapshot_history_context_mismatch');
    }
    loadedOperationalEvents = events;
    loadedSnapshotMarkers = markers;
    renderSnapshotHistoryReady();
  } catch (error) {
    if (!isLatestSnapshotHistoryRequest(requestId)) {
      return;
    }
    loadedOperationalEvents = null;
    loadedSnapshotMarkers = null;
    renderSnapshotHistoryLoadError();
  }
}

function renderSnapshotHistoryLoadingState() {
  setSnapshotHistoryViewState(SNAPSHOT_HISTORY_VIEW_STATE.LOADING);
  setDashboardGeneratedAtLabel('Snapshot/History를 불러오는 중');
  setSelectedApplicationLabel(snapshotHistoryContextText());
  dashboardDetail.innerHTML = snapshotHistoryShellMarkup({
    state: 'loading',
    generatedAt: null,
    bodyMarkup: `
      <div class="loading-state" aria-label="Snapshot/History 로딩 중">
        <p class="dashboard-empty-copy">Snapshot/History를 불러오는 중</p>
        <div class="skeleton-line"></div>
        <div class="skeleton-line short"></div>
      </div>
    `
  });
}

function renderSnapshotHistoryAuthorizationRequired() {
  setSnapshotHistoryViewState(SNAPSHOT_HISTORY_VIEW_STATE.AUTH_REQUIRED);
  setDashboardGeneratedAtLabel('로그인이 필요합니다');
  setSelectedApplicationLabel(snapshotHistoryContextText());
  dashboardDetail.innerHTML = snapshotHistoryShellMarkup({
    state: 'auth-required',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>GitHub 로그인 후 Snapshot/History를 볼 수 있습니다.</strong>로그인을 완료한 뒤 같은 Application Dashboard에서 Snapshot/History action을 다시 선택해 주세요.</p>
      </div>
    `
  });
}

function handleSnapshotHistoryAuthorizationLoss() {
  serviceAccessToken = null;
  projectRequestSequence += 1;
  applicationRequestSequence += 1;
  dashboardRequestSequence += 1;
  instanceEvidenceRequestSequence += 1;
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  loadedOperationalEvents = null;
  loadedSnapshotMarkers = null;
  clearSnapshotDetailSnapshot({ resetSelection: true });
  renderAuthorizationRequired();
  renderApplicationAuthorizationRequired();
  renderSnapshotHistoryAuthorizationRequired();
}

function renderSnapshotHistoryInvalidLink() {
  setSnapshotHistoryViewState(SNAPSHOT_HISTORY_VIEW_STATE.INVALID_LINK);
  setDashboardGeneratedAtLabel('Snapshot/History 기준 시각 확인 불가');
  setSelectedApplicationLabel(snapshotHistoryContextText());
  dashboardDetail.innerHTML = snapshotHistoryShellMarkup({
    state: 'invalid-link',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>Snapshot/History context를 확인할 수 없습니다.</strong>현재 Project/Application과 일치하는 Dashboard context에서만 history를 조회합니다.</p>
      </div>
    `
  });
}

function renderSnapshotHistoryBadQuery() {
  setSnapshotHistoryViewState(SNAPSHOT_HISTORY_VIEW_STATE.BAD_QUERY);
  setDashboardGeneratedAtLabel('Snapshot/History 기준 시각 확인 불가');
  setSelectedApplicationLabel(snapshotHistoryContextText());
  dashboardDetail.innerHTML = snapshotHistoryShellMarkup({
    state: 'bad-query',
    generatedAt: null,
    bodyMarkup: `
      ${snapshotHistoryPresetControlsMarkup()}
      <div class="empty-state">
        <p><strong>History query contract를 확인할 수 없습니다.</strong>UI는 fixed 24h/7d/14d preset만 사용하며 잠시 후 같은 Application에서 다시 시도해 주세요.</p>
      </div>
    `
  });
}

function renderSnapshotHistoryNotFound() {
  setSnapshotHistoryViewState(SNAPSHOT_HISTORY_VIEW_STATE.NOT_FOUND);
  setDashboardGeneratedAtLabel('Snapshot/History 기준 시각 확인 불가');
  setSelectedApplicationLabel(snapshotHistoryContextText());
  dashboardDetail.innerHTML = snapshotHistoryShellMarkup({
    state: 'not-found',
    generatedAt: null,
    bodyMarkup: `
      ${snapshotHistoryPresetControlsMarkup()}
      <div class="empty-state">
        <p><strong>Project/Application scope를 찾을 수 없습니다.</strong>scope mismatch 또는 접근 가능한 Application 범위를 다시 확인해 주세요.</p>
      </div>
    `
  });
}

function renderSnapshotHistoryLoadError() {
  setSnapshotHistoryViewState(SNAPSHOT_HISTORY_VIEW_STATE.ERROR);
  setDashboardGeneratedAtLabel('Snapshot/History 기준 시각 확인 불가');
  setSelectedApplicationLabel(snapshotHistoryContextText());
  dashboardDetail.innerHTML = snapshotHistoryShellMarkup({
    state: 'error',
    generatedAt: null,
    bodyMarkup: `
      ${snapshotHistoryPresetControlsMarkup()}
      <div class="empty-state">
        <p><strong>Snapshot/History를 불러오지 못했습니다.</strong>잠시 후 같은 Application에서 다시 시도해 주세요.</p>
      </div>
    `
  });
}

function renderSnapshotHistoryReady() {
  const events = loadedOperationalEvents;
  const markers = loadedSnapshotMarkers;
  const eventItems = Array.isArray(events && events.events) ? events.events : [];
  const markerItems = Array.isArray(markers && markers.markers) ? markers.markers : [];
  const state = eventItems.length === 0 && markerItems.length === 0 ? SNAPSHOT_HISTORY_VIEW_STATE.EMPTY : SNAPSHOT_HISTORY_VIEW_STATE.READY;
  setSnapshotHistoryViewState(state);
  setDashboardGeneratedAtLabel(formatSnapshotHistoryGeneratedAt(historyDisplayGeneratedAt(events, markers)));
  setSelectedApplicationLabel(snapshotHistoryReadyText(events, markers));
  dashboardDetail.innerHTML = snapshotHistoryShellMarkup({
    state,
    generatedAt: historyDisplayGeneratedAt(events, markers),
    bodyMarkup: `
      ${snapshotHistoryPresetControlsMarkup()}
      ${snapshotHistoryMetadataMarkup(events, markers)}
      ${operationalEventFeedMarkup(eventItems)}
      ${snapshotMarkerTimelineMarkup(markerItems, markers && markers.emptyState)}
    `
  });
}

function renderSnapshotHistoryFromDetail() {
  snapshotDetailRequestSequence += 1;
  clearSnapshotDetailSnapshot({ resetSelection: true });
  if (!serviceAccessToken) {
    renderSnapshotHistoryAuthorizationRequired();
    return;
  }
  if (loadedOperationalEvents && loadedSnapshotMarkers && selectedSnapshotHistoryContext) {
    renderSnapshotHistoryReady();
    return;
  }
  renderApplicationDashboardFromDetail();
}

function snapshotHistoryShellMarkup({ state, generatedAt, bodyMarkup }) {
  return `
    <article class="snapshot-history-detail" data-snapshot-history-state="${escapeAttribute(state)}">
      ${snapshotHistoryHeaderMarkup(generatedAt)}
      ${bodyMarkup}
    </article>
  `;
}

function snapshotHistoryHeaderMarkup(generatedAt) {
  const context = selectedSnapshotHistoryContext || snapshotHistoryContextFromSelectedDashboard() || {};
  return `
    <section class="dashboard-section instance-detail-header snapshot-history-header" aria-label="Snapshot history context">
      <div class="instance-detail-title">
        <div>
          <p class="eyebrow">Snapshot/History</p>
          <h3>${escapeText(valueOrAbsence(context.applicationName, 'Application name source absence'))}</h3>
        </div>
        <div class="detail-back-actions">
          <button class="link-button" type="button" data-dashboard-back="true">Application Dashboard</button>
        </div>
      </div>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('project id', context.projectId)}
        ${keyValueMarkup('project', valueOrAbsence(context.projectName, 'Project name source absence'))}
        ${keyValueMarkup('application id', context.applicationId)}
        ${keyValueMarkup('application', valueOrAbsence(context.applicationName, 'Application name source absence'))}
        ${keyValueMarkup('environment', context.applicationEnvironment)}
        ${keyValueMarkup('generatedAt', valueOrAbsence(generatedAt, 'history query 기준 시각 source absence'))}
      </dl>
    </section>
  `;
}

function snapshotHistoryPresetControlsMarkup() {
  const selectedPreset = selectedSnapshotHistoryContext
    ? selectedSnapshotHistoryContext.preset
    : SNAPSHOT_HISTORY_PRESETS.DAY;
  return `
    <section class="dashboard-section snapshot-history-presets" aria-label="Snapshot history preset controls">
      <p class="eyebrow">History preset</p>
      <div class="trend-preset-row">
        ${historyPresetButtonMarkup(SNAPSHOT_HISTORY_PRESETS.DAY, selectedPreset)}
        ${historyPresetButtonMarkup(SNAPSHOT_HISTORY_PRESETS.SEVEN_DAYS, selectedPreset)}
        ${historyPresetButtonMarkup(SNAPSHOT_HISTORY_PRESETS.FOURTEEN_DAYS, selectedPreset)}
      </div>
      <p class="dashboard-empty-copy">fixed 24h/7d/14d preset만 사용하며 custom query는 만들지 않습니다.</p>
    </section>
  `;
}

function historyPresetButtonMarkup(preset, selectedPreset) {
  const selected = preset === selectedPreset ? 'true' : 'false';
  return `<button class="link-button trend-preset-button" type="button" data-history-preset="${escapeAttribute(preset)}" data-selected="${selected}" aria-disabled="false">${escapeText(preset)}</button>`;
}

function snapshotHistoryMetadataMarkup(events, markers) {
  const eventHorizon = events && events.horizon ? events.horizon : {};
  const markerHorizon = markers && markers.horizon ? markers.horizon : {};
  return `
    <section class="dashboard-section" aria-label="Snapshot history source metadata">
      <p class="eyebrow">Stored source and horizon</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('event source', events && events.source)}
        ${keyValueMarkup('event applicationId', events && events.applicationId)}
        ${keyValueMarkup('event defaultSince', eventHorizon.defaultSince)}
        ${keyValueMarkup('event maxSince', eventHorizon.maxSince)}
        ${keyValueMarkup('event maxLimit', eventHorizon.maxLimit)}
        ${keyValueMarkup('event order', eventHorizon.order)}
        ${keyValueMarkup('marker source', markers && markers.source)}
        ${keyValueMarkup('marker applicationId', markers && markers.applicationId)}
        ${keyValueMarkup('marker defaultSince', markerHorizon.defaultSince)}
        ${keyValueMarkup('marker maxSince', markerHorizon.maxSince)}
        ${keyValueMarkup('marker maxLimit', markerHorizon.maxLimit)}
        ${keyValueMarkup('marker order', markerHorizon.order)}
      </dl>
    </section>
  `;
}

function operationalEventFeedMarkup(events) {
  const items = Array.isArray(events) ? events : [];
  return `
    <section class="dashboard-section operational-event-feed" aria-label="Operational event feed">
      <p class="eyebrow">Operational events</p>
      <div class="dashboard-list">
        ${items.length === 0 ? '<p class="dashboard-empty-copy">operational event source absence 또는 event 후보 없음 · current health proof가 아닙니다.</p>' : items.map(operationalEventItemMarkup).join('')}
      </div>
    </section>
  `;
}

function operationalEventItemMarkup(event) {
  const evidence = event.evidence || {};
  const snapshotLink = safeSnapshotDetailLink(event.links && event.links.snapshot);
  return `
    <article class="dashboard-mini-card">
      <div class="snapshot-item-title">
        <h3>${escapeText(valueOrAbsence(event.title, 'event title source absence'))}</h3>
        <span class="badge">${escapeText(valueOrAbsence(event.severity, 'severity source absence'))}</span>
      </div>
      <p class="dashboard-empty-copy">${escapeText(valueOrAbsence(event.summary, 'event summary source absence'))}</p>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('eventId', event.eventId)}
        ${keyValueMarkup('type', event.type)}
        ${keyValueMarkup('severity', event.severity)}
        ${keyValueMarkup('occurredAt', event.occurredAt)}
        ${keyValueMarkup('resolvedAt', valueOrAbsence(event.resolvedAt, 'nullable stored period field source absence'))}
        ${keyValueMarkup('stateCode', event.stateCode)}
        ${keyValueMarkup('confidence', valueOrAbsence(event.confidence, 'nullable confidence source absence'))}
        ${keyValueMarkup('snapshotId', event.snapshotId)}
        ${keyValueMarkup('evidence', operationalEventEvidenceText(evidence))}
      </dl>
      ${snapshotLink ? snapshotDetailButtonMarkup(snapshotLink, evidence.snapshotDetailAnchor, 'event') : '<p class="dashboard-empty-copy">snapshot detail link source absence</p>'}
    </article>
  `;
}

function operationalEventEvidenceText(evidence) {
  return [
    `ruleId ${valueOrAbsence(evidence.ruleId, 'rule source absence')}`,
    `endpointKey ${valueOrAbsence(evidence.endpointKey, 'endpoint source absence')}`,
    evidence.method ? `method ${evidence.method}` : '',
    evidence.route ? `route ${evidence.route}` : '',
    evidence.snapshotDetailAnchor ? `snapshotDetailAnchor ${evidence.snapshotDetailAnchor}` : 'snapshotDetailAnchor source absence',
    evidence.anchorStatus ? `anchorStatus ${evidence.anchorStatus}` : 'anchorStatus source absence'
  ].filter(value => String(value ?? '').trim().length > 0).join(' · ');
}

function snapshotMarkerTimelineMarkup(markers, emptyState) {
  const items = Array.isArray(markers) ? markers : [];
  return `
    <section class="dashboard-section snapshot-marker-timeline" aria-label="Snapshot marker timeline">
      <p class="eyebrow">Snapshot markers</p>
      <div class="dashboard-list">
        ${items.length === 0 ? snapshotMarkerEmptyCopy(emptyState) : items.map(snapshotMarkerItemMarkup).join('')}
      </div>
    </section>
  `;
}

function snapshotMarkerEmptyCopy(emptyState) {
  const state = emptyState || {};
  return `
    <p class="dashboard-empty-copy">
      marker source absence 또는 retention horizon 안에 표시할 marker 없음 · ${escapeText(valueOrAbsence(state.reasonCode, 'empty reason source absence'))}
    </p>
  `;
}

function snapshotMarkerItemMarkup(marker) {
  const previous = marker.previousState || {};
  const snapshotLink = safeSnapshotDetailLink(marker.links && marker.links.snapshot);
  return `
    <article class="dashboard-mini-card">
      <div class="snapshot-item-title">
        <h3>${escapeText(valueOrAbsence(marker.title, 'marker title source absence'))}</h3>
        <span class="badge">${escapeText(valueOrAbsence(marker.severity, 'severity source absence'))}</span>
      </div>
      <p class="dashboard-empty-copy">${escapeText(valueOrAbsence(marker.summary, 'marker summary source absence'))}</p>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('markerId', marker.markerId)}
        ${keyValueMarkup('snapshotId', marker.snapshotId)}
        ${keyValueMarkup('capturedAt', marker.capturedAt)}
        ${keyValueMarkup('currentWindowEndUtc', marker.currentWindowEndUtc)}
        ${keyValueMarkup('type', marker.type)}
        ${keyValueMarkup('severity', marker.severity)}
        ${keyValueMarkup('readMeaning', marker.readMeaning)}
        ${keyValueMarkup('captureReason', `opaque metadata ${valueOrAbsence(marker.captureReason, 'capture reason source absence')}`)}
        ${keyValueMarkup('storedApplicationStateCode', marker.storedApplicationStateCode)}
        ${keyValueMarkup('previousState', previousStateText(previous))}
        ${keyValueMarkup('recommendedAction', valueOrAbsence(marker.recommendedAction, 'recommended action source absence'))}
        ${keyValueMarkup('confidence', valueOrAbsence(marker.confidence, 'nullable confidence source absence'))}
        ${keyValueMarkup('primaryRuleId', valueOrAbsence(marker.primaryRuleId, 'rule source absence'))}
        ${keyValueMarkup('primaryEndpointKey', valueOrAbsence(marker.primaryEndpointKey, 'endpoint source absence'))}
      </dl>
      ${snapshotLink ? snapshotDetailButtonMarkup(snapshotLink, '', 'marker') : '<p class="dashboard-empty-copy">snapshot detail link source absence</p>'}
    </article>
  `;
}

function snapshotDetailButtonMarkup(snapshotLink, anchor, origin) {
  return `
    <button class="link-button" type="button" data-snapshot-detail-link="${escapeAttribute(snapshotLink)}" data-snapshot-detail-anchor="${escapeAttribute(anchor || '')}" data-snapshot-detail-origin="${escapeAttribute(origin || '')}">
      Snapshot detail
    </button>
  `;
}

function selectSnapshotDetailFromLink(detailContext) {
  const detailLink = String(detailContext.detailLink ?? '');
  const anchor = String(detailContext.anchor ?? '');
  snapshotDetailRequestSequence += 1;
  selectedSnapshotDetailContext = snapshotDetailContextFromLink(detailLink, anchor, detailContext.origin || 'snapshot-history');
  loadedSnapshotDetail = null;
  if (!serviceAccessToken) {
    renderSnapshotDetailAuthorizationRequired();
    return;
  }
  if (!selectedSnapshotDetailContext) {
    renderSnapshotDetailInvalidLink();
    return;
  }
  loadSelectedSnapshotDetail();
}

function selectSnapshotDetailFromTrendPoint(detailContext) {
  const snapshotId = String(detailContext.snapshotId ?? '');
  const detailLink = canonicalSnapshotDetailLink(snapshotId);
  snapshotDetailRequestSequence += 1;
  selectedSnapshotDetailContext = snapshotDetailContextFromLink(detailLink, String(detailContext.anchor ?? ''), 'instance-snapshot-trend');
  loadedSnapshotDetail = null;
  if (!serviceAccessToken) {
    renderSnapshotDetailAuthorizationRequired();
    return;
  }
  if (!selectedSnapshotDetailContext) {
    renderSnapshotDetailInvalidLink();
    return;
  }
  loadSelectedSnapshotDetail();
}

async function loadSelectedSnapshotDetail() {
  const requestId = ++snapshotDetailRequestSequence;
  if (!selectedSnapshotDetailContext) {
    renderSnapshotDetailInvalidLink();
    return;
  }
  if (!serviceAccessToken) {
    loadedSnapshotDetail = null;
    renderSnapshotDetailAuthorizationRequired();
    return;
  }
  if (!isSnapshotDetailLink(
    selectedSnapshotDetailContext.detailLink,
    selectedSnapshotDetailContext.projectId,
    selectedSnapshotDetailContext.applicationId
  )) {
    renderSnapshotDetailInvalidLink();
    return;
  }
  renderSnapshotDetailLoadingState();
  try {
    const response = await fetch(selectedSnapshotDetailContext.detailLink, { headers: projectRequestHeaders() });
    if (!isLatestSnapshotDetailRequest(requestId)) {
      return;
    }
    if (response.status === 401) {
      handleSnapshotDetailAuthorizationLoss();
      return;
    }
    if (response.status === 400) {
      loadedSnapshotDetail = null;
      renderSnapshotDetailInvalidLink();
      return;
    }
    if (response.status === 404) {
      loadedSnapshotDetail = null;
      renderSnapshotDetailNotFoundOrExpired();
      return;
    }
    if (!response.ok) {
      throw new Error('snapshot_detail_load_failed');
    }
    const detail = await response.json();
    if (!isLatestSnapshotDetailRequest(requestId)) {
      return;
    }
    if (!isSelectedSnapshotDetailResponse(detail)) {
      throw new Error('snapshot_detail_context_mismatch');
    }
    loadedSnapshotDetail = detail;
    renderSnapshotDetailReady();
  } catch (error) {
    if (!isLatestSnapshotDetailRequest(requestId)) {
      return;
    }
    loadedSnapshotDetail = null;
    renderSnapshotDetailLoadError();
  }
}

function renderSnapshotDetailLoadingState() {
  setSnapshotDetailViewState(SNAPSHOT_DETAIL_VIEW_STATE.LOADING);
  setDashboardGeneratedAtLabel('Snapshot Detail을 불러오는 중');
  setSelectedApplicationLabel(snapshotDetailContextText());
  dashboardDetail.innerHTML = snapshotDetailShellMarkup({
    state: 'loading',
    generatedAt: null,
    bodyMarkup: `
      <div class="loading-state" aria-label="Snapshot Detail 로딩 중">
        <p class="dashboard-empty-copy">Snapshot Detail을 불러오는 중</p>
        <div class="skeleton-line"></div>
        <div class="skeleton-line short"></div>
      </div>
    `
  });
}

function renderSnapshotDetailAuthorizationRequired() {
  setSnapshotDetailViewState(SNAPSHOT_DETAIL_VIEW_STATE.AUTH_REQUIRED);
  setDashboardGeneratedAtLabel('로그인이 필요합니다');
  setSelectedApplicationLabel(snapshotDetailContextText());
  dashboardDetail.innerHTML = snapshotDetailShellMarkup({
    state: 'auth-required',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>GitHub 로그인 후 Snapshot Detail을 볼 수 있습니다.</strong>로그인을 완료한 뒤 같은 Application에서 detail action을 다시 선택해 주세요.</p>
      </div>
    `
  });
}

function handleSnapshotDetailAuthorizationLoss() {
  serviceAccessToken = null;
  projectRequestSequence += 1;
  applicationRequestSequence += 1;
  dashboardRequestSequence += 1;
  instanceEvidenceRequestSequence += 1;
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  loadedSnapshotDetail = null;
  renderAuthorizationRequired();
  renderApplicationAuthorizationRequired();
  renderSnapshotDetailAuthorizationRequired();
}

function renderSnapshotDetailInvalidLink() {
  setSnapshotDetailViewState(SNAPSHOT_DETAIL_VIEW_STATE.INVALID_LINK);
  setDashboardGeneratedAtLabel('Snapshot Detail 기준 시각 확인 불가');
  setSelectedApplicationLabel(snapshotDetailContextText());
  dashboardDetail.innerHTML = snapshotDetailShellMarkup({
    state: 'invalid-link',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>Snapshot detail link를 확인할 수 없습니다.</strong>현재 Project/Application과 일치하는 내부 snapshot detail link만 사용할 수 있습니다.</p>
      </div>
    `
  });
}

function renderSnapshotDetailNotFoundOrExpired() {
  setSnapshotDetailViewState(SNAPSHOT_DETAIL_VIEW_STATE.NOT_FOUND_OR_EXPIRED);
  setDashboardGeneratedAtLabel('Snapshot Detail 기준 시각 확인 불가');
  setSelectedApplicationLabel(snapshotDetailContextText());
  dashboardDetail.innerHTML = snapshotDetailShellMarkup({
    state: 'not-found-or-expired',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>저장된 snapshot detail이 없거나 보관 기간이 지나 더 이상 없습니다.</strong>현재 dashboard로 대체하지 않고 저장된 detail absence로 표시합니다.</p>
      </div>
    `
  });
}

function renderSnapshotDetailLoadError() {
  setSnapshotDetailViewState(SNAPSHOT_DETAIL_VIEW_STATE.ERROR);
  setDashboardGeneratedAtLabel('Snapshot Detail 기준 시각 확인 불가');
  setSelectedApplicationLabel(snapshotDetailContextText());
  dashboardDetail.innerHTML = snapshotDetailShellMarkup({
    state: 'error',
    generatedAt: null,
    bodyMarkup: `
      <div class="empty-state">
        <p><strong>Snapshot detail을 불러오지 못했습니다.</strong>잠시 후 같은 snapshot detail action으로 다시 시도해 주세요.</p>
      </div>
    `
  });
}

function renderSnapshotDetailReady() {
  const detail = loadedSnapshotDetail;
  setSnapshotDetailViewState(SNAPSHOT_DETAIL_VIEW_STATE.READY);
  setDashboardGeneratedAtLabel(formatSnapshotDetailGeneratedAt(detail.generatedAt));
  setSelectedApplicationLabel(snapshotDetailReadyText(detail));
  dashboardDetail.innerHTML = snapshotDetailShellMarkup({
    state: 'ready',
    generatedAt: detail.generatedAt,
    bodyMarkup: `
      ${snapshotDetailAnchorStatusMarkup(detail)}
      ${snapshotDetailReadSemanticsMarkup(detail.readSemantics)}
      ${snapshotDetailMetadataMarkup(detail)}
      ${snapshotDetailStateSourceMarkup(detail)}
      ${snapshotDetailReadModelMarkup(detail.readModel)}
      ${snapshotEndpointEvidenceMarkup(detail.snapshotEndpointEvidence)}
      ${snapshotInstanceSummaryMarkup(detail.instanceSummary)}
    `
  });
}

function snapshotDetailShellMarkup({ state, generatedAt, bodyMarkup }) {
  return `
    <article class="snapshot-detail-read-model" data-snapshot-detail-state="${escapeAttribute(state)}">
      ${snapshotDetailHeaderMarkup(generatedAt)}
      ${bodyMarkup}
    </article>
  `;
}

function snapshotDetailHeaderMarkup(generatedAt) {
  const context = selectedSnapshotDetailContext || {};
  const historyBack = loadedOperationalEvents && loadedSnapshotMarkers
    ? '<button class="link-button" type="button" data-snapshot-history-back="true">Snapshot/History</button>'
    : '';
  return `
    <section class="dashboard-section instance-detail-header snapshot-detail-header" aria-label="Snapshot detail context">
      <div class="instance-detail-title">
        <div>
          <p class="eyebrow">Snapshot Detail</p>
          <h3>${escapeText(valueOrAbsence(context.snapshotId, 'snapshot id source absence'))}</h3>
        </div>
        <div class="detail-back-actions">
          <button class="link-button" type="button" data-dashboard-back="true">Application Dashboard</button>
          ${historyBack}
        </div>
      </div>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('project id', context.projectId)}
        ${keyValueMarkup('application id', context.applicationId)}
        ${keyValueMarkup('snapshot id', context.snapshotId)}
        ${keyValueMarkup('active anchor', valueOrAbsence(context.anchor, 'anchor source absence'))}
        ${keyValueMarkup('origin', valueOrAbsence(context.origin, 'origin source absence'))}
        ${keyValueMarkup('generatedAt', valueOrAbsence(generatedAt, 'detail generatedAt source absence'))}
      </dl>
    </section>
  `;
}

function snapshotDetailAnchorStatusMarkup(detail) {
  const anchor = selectedSnapshotDetailContext ? selectedSnapshotDetailContext.anchor : '';
  if (!anchor) {
    return '';
  }
  return hasSnapshotEndpointAnchor(detail, anchor)
    ? `<p class="dashboard-empty-copy snapshot-anchor-status">anchorStatus resolved · ${escapeText(anchor)}</p>`
    : `<p class="dashboard-empty-copy snapshot-anchor-status">anchorStatus missing · ${escapeText(anchor)}</p>`;
}

function snapshotDetailReadSemanticsMarkup(readSemantics) {
  const semantics = readSemantics || {};
  return `
    <section class="dashboard-section" aria-label="Snapshot detail read semantics">
      <p class="eyebrow">Read semantics</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('mode', semantics.mode)}
        ${keyValueMarkup('currentStateRecalculated', semantics.currentStateRecalculated)}
        ${keyValueMarkup('liveSourcesJoined', Array.isArray(semantics.liveSourcesJoined) ? semantics.liveSourcesJoined.join(', ') : 'source absence')}
        ${keyValueMarkup('rawReadModelJsonExposed', semantics.rawReadModelJsonExposed)}
      </dl>
    </section>
  `;
}

function snapshotDetailMetadataMarkup(detail) {
  const snapshot = detail.snapshot || {};
  const marker = detail.marker || {};
  return `
    <section class="dashboard-section" aria-label="Snapshot metadata">
      <p class="eyebrow">snapshot metadata</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('snapshotId', snapshot.snapshotId)}
        ${keyValueMarkup('capturedAt', snapshot.capturedAt)}
        ${keyValueMarkup('generatedAt', snapshot.generatedAt)}
        ${keyValueMarkup('currentWindow', windowRangeText(snapshot.currentWindow))}
        ${keyValueMarkup('baselineWindow', windowRangeText(snapshot.baselineWindow))}
        ${keyValueMarkup('captureReason', valueOrAbsence(snapshot.captureReason, 'capture reason source absence'))}
        ${keyValueMarkup('storedApplicationStateCode', snapshot.storedApplicationStateCode)}
        ${keyValueMarkup('primaryRuleId', valueOrAbsence(snapshot.primaryRuleId, 'rule source absence'))}
        ${keyValueMarkup('primaryEndpointKey', valueOrAbsence(snapshot.primaryEndpointKey, 'endpoint source absence'))}
        ${keyValueMarkup('maxConfidence', valueOrAbsence(snapshot.maxConfidence, 'confidence source absence'))}
        ${keyValueMarkup('marker', markerSummaryText(marker))}
      </dl>
    </section>
  `;
}

function markerSummaryText(marker) {
  return [
    marker.markerId,
    marker.type,
    marker.severity,
    marker.readMeaning,
    marker.title,
    marker.summary,
    marker.recommendedAction
  ].filter(value => String(value ?? '').trim().length > 0).join(' · ');
}

function snapshotDetailStateSourceMarkup(detail) {
  return `
    <section class="dashboard-section" aria-label="Snapshot state source">
      <p class="eyebrow">Stored state source</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('previousState', previousStateText(detail.previousState || {}))}
        ${keyValueMarkup('lastHealthyAt', lastHealthyAtText(detail.lastHealthyAt || {}))}
        ${keyValueMarkup('recoveryMarker', recoveryMarkerText(detail.recoveryMarker))}
      </dl>
    </section>
  `;
}

function snapshotDetailReadModelMarkup(readModel) {
  const model = readModel || {};
  return `
    <section class="dashboard-section" aria-label="Snapshot read model summary">
      <p class="eyebrow">readModel summary</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('application', readModelApplicationText(model.application))}
        ${keyValueMarkup('state', readModelStateText(model.state))}
        ${keyValueMarkup('starterConnection', readModelStarterConnectionText(model.starterConnection))}
        ${keyValueMarkup('zeroInsight', readModelZeroInsightText(model.zeroInsight))}
        ${keyValueMarkup('recovery', readModelRecoveryText(model.recovery))}
        ${keyValueMarkup('metrics', readModelMetricsText(model.metrics))}
        ${keyValueMarkup('sourceScopedPercentiles', readModelSourceScopedPercentilesText(model.sourceScopedPercentiles))}
        ${keyValueMarkup('triageCards', readModelTriageCardsText(model.triageCards))}
        ${keyValueMarkup('endpointPriority', readModelEndpointPriorityText(model.endpointPriority))}
      </dl>
    </section>
  `;
}

function snapshotEndpointEvidenceMarkup(snapshotEndpointEvidence) {
  const evidence = snapshotEndpointEvidence || {};
  const items = Array.isArray(evidence.items) ? evidence.items : [];
  return `
    <section class="dashboard-section" aria-label="Snapshot endpoint evidence">
      <p class="eyebrow">snapshotEndpointEvidence</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('source', evidence.source)}
        ${keyValueMarkup('maxItems', evidence.maxItems)}
        ${keyValueMarkup('selectionPolicy', valueOrAbsence(evidence.selectionPolicy, 'selection policy source absence'))}
        ${keyValueMarkup('unavailableReason', valueOrAbsence(evidence.unavailableReason, 'unavailable reason source absence'))}
      </dl>
      <div class="dashboard-list">
        ${items.length === 0 ? '<p class="dashboard-empty-copy">bounded endpoint evidence source absence</p>' : items.map(snapshotEndpointEvidenceItemMarkup).join('')}
      </div>
    </section>
  `;
}

function snapshotEndpointEvidenceItemMarkup(item) {
  const active = selectedSnapshotDetailContext && item.anchorId === selectedSnapshotDetailContext.anchor ? 'true' : 'false';
  const ruleIds = Array.isArray(item.ruleIds) ? item.ruleIds.join(', ') : '';
  return `
    <article class="dashboard-mini-card snapshot-endpoint-evidence-item" id="${escapeAttribute(item.anchorId)}" data-active-anchor="${active}">
      <h3>${escapeText(valueOrAbsence(item.endpointKey, 'endpoint evidence source absence'))}</h3>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('anchorId', item.anchorId)}
        ${keyValueMarkup('method', valueOrAbsence(item.method, 'method source absence'))}
        ${keyValueMarkup('route', valueOrAbsence(item.route, 'route source absence'))}
        ${keyValueMarkup('rank', valueOrAbsence(item.rank, 'rank source absence'))}
        ${keyValueMarkup('reason', valueOrAbsence(item.reason, 'reason source absence'))}
        ${keyValueMarkup('ruleIds', valueOrAbsence(ruleIds, 'rule source absence'))}
        ${keyValueMarkup('confidence', valueOrAbsence(item.confidence, 'confidence source absence'))}
        ${keyValueMarkup('score', valueOrAbsence(item.score, 'score source absence'))}
        ${keyValueMarkup('requestCount', valueOrAbsence(item.requestCount, 'request count source absence'))}
        ${keyValueMarkup('errorRate', valueOrAbsence(item.errorRate, 'error rate source absence'))}
        ${keyValueMarkup('durationBuckets', bucketSummaryText(item.durationBuckets))}
        ${keyValueMarkup('baselineDurationBuckets', bucketSummaryText(item.baselineDurationBuckets))}
        ${keyValueMarkup('bucketDistributionSource', valueOrAbsence(item.bucketDistributionSource, 'bucket distribution source absence'))}
        ${keyValueMarkup('freshness', readModelFreshnessText(item.freshness))}
        ${keyValueMarkup('recommendedAction', valueOrAbsence(item.recommendedAction, 'recommended action source absence'))}
      </dl>
    </article>
  `;
}

function snapshotInstanceSummaryMarkup(instanceSummary) {
  const summary = instanceSummary || {};
  const items = Array.isArray(summary.items) ? summary.items : [];
  return `
    <section class="dashboard-section" aria-label="Snapshot instance summary">
      <p class="eyebrow">instanceSummary</p>
      <dl class="dashboard-kv-grid">
        ${keyValueMarkup('schemaVersion', summary.schemaVersion)}
        ${keyValueMarkup('source', summary.source)}
        ${keyValueMarkup('maxItems', summary.maxItems)}
        ${keyValueMarkup('selectionPolicy', valueOrAbsence(summary.selectionPolicy, 'selection policy source absence'))}
        ${keyValueMarkup('unavailableReason', valueOrAbsence(summary.unavailableReason, 'unavailable reason source absence'))}
      </dl>
      <div class="dashboard-list">
        ${items.length === 0 ? '<p class="dashboard-empty-copy">bounded instance summary source absence</p>' : items.map(snapshotInstanceSummaryItemMarkup).join('')}
      </div>
    </section>
  `;
}

function snapshotInstanceSummaryItemMarkup(item) {
  const refs = Array.isArray(item.endpointEvidenceRefs) ? item.endpointEvidenceRefs : [];
  return `
    <article class="dashboard-mini-card">
      <h3>${escapeText(valueOrAbsence(item.instanceName, 'instance name source absence'))}</h3>
      <dl class="dashboard-kv-grid compact">
        ${keyValueMarkup('instanceId', item.instanceId)}
        ${keyValueMarkup('observationStatus', item.observationStatus)}
        ${keyValueMarkup('metricData', snapshotTrendMetricDataText(item.metricData))}
        ${keyValueMarkup('starterConnection', snapshotTrendStarterConnectionText(item.starterConnection))}
        ${keyValueMarkup('starterPercentilePoint', snapshotTrendStarterPercentilePointText(item.starterPercentilePoint))}
        ${keyValueMarkup('resourceHints', snapshotTrendResourceHintsText(item.resourceHints))}
        ${keyValueMarkup('applicationTriageContribution', snapshotTrendTriageContributionText(item.applicationTriageContribution))}
        ${keyValueMarkup('endpointEvidenceRefs', snapshotEndpointEvidenceRefsText(refs))}
      </dl>
    </article>
  `;
}

function snapshotEndpointEvidenceRefsText(refs) {
  const values = Array.isArray(refs) ? refs : [];
  if (values.length === 0) {
    return 'bounded endpoint reference source absence';
  }
  return values.map(ref => [
    ref.endpointKey,
    ref.method,
    ref.route,
    `relatedApplicationPriorityRank ${valueOrAbsence(ref.relatedApplicationPriorityRank, 'rank source absence')}`,
    `relatedRuleIds ${Array.isArray(ref.relatedRuleIds) ? ref.relatedRuleIds.join(', ') : ''}`,
    `snapshotDetailAnchor ${valueOrAbsence(ref.snapshotDetailAnchor, 'anchor source absence')}`,
    `anchorStatus ${valueOrAbsence(ref.anchorStatus, 'anchor status source absence')}`
  ].filter(value => String(value ?? '').trim().length > 0).join(' · ')).join(' | ');
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
      <p class="dashboard-empty-copy">${snapshotTrendLink ? 'links.snapshotTrend handoff link로 stored snapshot trend를 조회합니다.' : 'snapshot trend handoff source absence'}</p>
      ${snapshotTrendLink ? `<button class="link-button snapshot-trend-handoff-action" type="button" aria-disabled="false" data-snapshot-trend-link="${escapeAttribute(snapshotTrendLink)}">Snapshot trend</button>` : ''}
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
      <p class="eyebrow">Snapshot/History</p>
      <p class="dashboard-empty-copy">${available ? 'Snapshot handoff metadata 보존됨 · history는 selected Project/Application 기준 stored snapshots에서 조회합니다.' : 'current dashboard snapshot field source absence · history는 selected Project/Application 기준 stored snapshots에서 조회합니다.'}</p>
      <button class="link-button snapshot-history-action" type="button" aria-disabled="false" data-snapshot-history-action="true">Snapshot/History</button>
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

function previousStateText(previousState) {
  const previous = previousState || {};
  return [
    `stateCode ${valueOrAbsence(previous.stateCode, 'previous state source absence')}`,
    `source ${valueOrAbsence(previous.source, 'previous source absence')}`,
    `snapshotId ${valueOrAbsence(previous.snapshotId, 'previous snapshot source absence')}`,
    `capturedAt ${valueOrAbsence(previous.capturedAt, 'previous capturedAt source absence')}`
  ].join(' · ');
}

function lastHealthyAtText(lastHealthyAt) {
  const value = lastHealthyAt || {};
  return [
    `value ${valueOrAbsence(value.value, 'lastHealthyAt source absence')}`,
    `source ${valueOrAbsence(value.source, 'lastHealthyAt source absence')}`,
    `snapshotId ${valueOrAbsence(value.snapshotId, 'lastHealthyAt snapshot source absence')}`
  ].join(' · ');
}

function recoveryMarkerText(recoveryMarker) {
  if (!recoveryMarker) {
    return 'recovery marker source absence';
  }
  return [
    '회복 관찰 중',
    recoveryMarker.markerId,
    recoveryMarker.type,
    recoveryMarker.severity,
    recoveryMarker.title,
    recoveryMarker.summary,
    recoveryMarker.recommendedAction
  ].filter(value => String(value ?? '').trim().length > 0).join(' · ');
}

function readModelApplicationText(application) {
  const value = application || {};
  return [
    value.name,
    value.environment,
    value.projectId,
    value.applicationId
  ].filter(item => String(item ?? '').trim().length > 0).join(' · ') || 'stored application summary source absence';
}

function readModelStateText(state) {
  const value = state || {};
  return [
    value.code,
    value.label,
    value.scope,
    value.rationale,
    value.recommendedAction
  ].filter(item => String(item ?? '').trim().length > 0).join(' · ') || 'stored state summary source absence';
}

function readModelStarterConnectionText(starterConnection) {
  const value = starterConnection || {};
  return [
    value.statusSource,
    value.lastHeartbeatAt,
    value.lastHeartbeatStatus,
    value.connectionMeaning,
    value.stateImpact
  ].filter(item => String(item ?? '').trim().length > 0).join(' · ') || 'stored starter connection source absence';
}

function readModelZeroInsightText(zeroInsight) {
  if (!zeroInsight) {
    return 'zeroInsight source absence';
  }
  return [
    zeroInsight.reasonCode,
    zeroInsight.message,
    zeroInsight.recommendedAction
  ].filter(item => String(item ?? '').trim().length > 0).join(' · ') || 'zeroInsight source absence';
}

function readModelRecoveryText(recovery) {
  const value = recovery || {};
  return [
    `isRecovering ${valueOrAbsence(value.isRecovering, 'source absence')}`,
    `lastHealthyAt ${valueOrAbsence(value.lastHealthyAt, 'source absence')}`,
    `retryAfterSeconds ${valueOrAbsence(value.retryAfterSeconds, 'source absence')}`,
    value.recommendedAction
  ].filter(item => String(item ?? '').trim().length > 0).join(' · ') || 'stored recovery summary source absence';
}

function readModelMetricsText(metrics) {
  const value = metrics || {};
  return [
    `requestCount ${valueOrAbsence(value.requestCount, 'source absence')}`,
    `errorCount ${valueOrAbsence(value.errorCount, 'source absence')}`,
    `errorRate ${valueOrAbsence(value.errorRate, 'source absence')}`
  ].join(' · ');
}

function readModelSourceScopedPercentilesText(sourceScopedPercentiles) {
  const value = sourceScopedPercentiles || {};
  const items = Array.isArray(value.items) ? value.items : [];
  return [
    value.source,
    value.scope,
    value.displayPolicy,
    value.aggregatePolicy,
    `status ${valueOrAbsence(value.status, 'source absence')}`,
    `itemCount ${items.length}`
  ].filter(item => String(item ?? '').trim().length > 0).join(' · ') || 'stored source scoped percentile summary source absence';
}

function readModelTriageCardsText(triageCards) {
  const cards = Array.isArray(triageCards) ? triageCards : [];
  if (cards.length === 0) {
    return 'stored triage card source absence';
  }
  return cards.map(card => [
    card.title,
    card.ruleId,
    card.severity,
    card.summary
  ].filter(value => String(value ?? '').trim().length > 0).join(' · ')).join(' | ');
}

function readModelEndpointPriorityText(endpointPriority) {
  const items = Array.isArray(endpointPriority) ? endpointPriority : [];
  if (items.length === 0) {
    return 'stored endpoint priority source absence';
  }
  return items.map(item => [
    `rank ${valueOrAbsence(item.rank, 'rank source absence')}`,
    item.endpointKey,
    item.reason,
    item.recommendedAction
  ].filter(value => String(value ?? '').trim().length > 0).join(' · ')).join(' | ');
}

function readModelFreshnessText(freshness) {
  const value = freshness || {};
  return [
    value.status,
    value.lastObservedAt,
    value.sourceWindow,
    value.reason
  ].filter(item => String(item ?? '').trim().length > 0).join(' · ') || 'freshness source absence';
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

function normalizeTrendPreset(preset) {
  if (preset === INSTANCE_SNAPSHOT_TREND_PRESETS.FOURTEEN_DAYS) {
    return INSTANCE_SNAPSHOT_TREND_PRESETS.FOURTEEN_DAYS;
  }
  if (preset === INSTANCE_SNAPSHOT_TREND_PRESETS.PENDING_24H) {
    return INSTANCE_SNAPSHOT_TREND_PRESETS.PENDING_24H;
  }
  return INSTANCE_SNAPSHOT_TREND_PRESETS.SEVEN_DAYS;
}

function snapshotTrendRequestLink(snapshotTrendLink, preset) {
  if (!selectedInstanceSnapshotTrendContext || !isInstanceSnapshotTrendLink(
    snapshotTrendLink,
    selectedInstanceSnapshotTrendContext.projectId,
    selectedInstanceSnapshotTrendContext.applicationId,
    selectedInstanceSnapshotTrendContext.instanceId
  )) {
    return null;
  }
  if (preset === INSTANCE_SNAPSHOT_TREND_PRESETS.SEVEN_DAYS) {
    return `${snapshotTrendLink}?since=7d&limit=168`;
  }
  if (preset === INSTANCE_SNAPSHOT_TREND_PRESETS.FOURTEEN_DAYS) {
    return `${snapshotTrendLink}?since=14d&limit=336`;
  }
  return null;
}

function snapshotHistoryContextFromSelectedDashboard() {
  if (!selectedDashboardContext) {
    return null;
  }
  return {
    projectId: selectedDashboardContext.projectId,
    projectName: selectedDashboardContext.projectName,
    applicationId: selectedDashboardContext.applicationId,
    applicationName: selectedDashboardContext.applicationName,
    applicationEnvironment: selectedDashboardContext.applicationEnvironment,
    dashboardLink: selectedDashboardContext.dashboardLink,
    preset: SNAPSHOT_HISTORY_PRESETS.DAY
  };
}

function isValidSnapshotHistoryContext(context) {
  return Boolean(isObjectValue(context)
    && hasRequiredText(context.projectId)
    && hasRequiredText(context.applicationId)
    && isApplicationDashboardLink(
      context.dashboardLink,
      context.projectId,
      context.applicationId
    ));
}

function normalizeSnapshotHistoryPreset(preset) {
  if (preset === SNAPSHOT_HISTORY_PRESETS.SEVEN_DAYS) {
    return SNAPSHOT_HISTORY_PRESETS.SEVEN_DAYS;
  }
  if (preset === SNAPSHOT_HISTORY_PRESETS.FOURTEEN_DAYS) {
    return SNAPSHOT_HISTORY_PRESETS.FOURTEEN_DAYS;
  }
  return SNAPSHOT_HISTORY_PRESETS.DAY;
}

function snapshotHistoryOperationalEventsRequestLink(preset) {
  const normalizedPreset = normalizeSnapshotHistoryPreset(preset);
  const query = SNAPSHOT_HISTORY_PRESET_QUERY[normalizedPreset];
  if (!isValidSnapshotHistoryContext(selectedSnapshotHistoryContext) || !query) {
    return null;
  }
  return `/api/projects/${selectedSnapshotHistoryContext.projectId}/applications/${selectedSnapshotHistoryContext.applicationId}/operational-events?since=${normalizedPreset}&limit=${query.eventLimit}`;
}

function snapshotHistoryMarkersRequestLink(preset) {
  const normalizedPreset = normalizeSnapshotHistoryPreset(preset);
  const query = SNAPSHOT_HISTORY_PRESET_QUERY[normalizedPreset];
  if (!isValidSnapshotHistoryContext(selectedSnapshotHistoryContext) || !query) {
    return null;
  }
  return `/api/projects/${selectedSnapshotHistoryContext.projectId}/applications/${selectedSnapshotHistoryContext.applicationId}/dashboard/snapshot-markers?since=${normalizedPreset}&limit=${query.markerLimit}`;
}

function safeSnapshotDetailLink(candidate) {
  const normalized = normalizeHandoffLinkCandidate(candidate);
  const context = selectedSnapshotHistoryContext || snapshotHistoryContextFromSelectedDashboard();
  if (!context || normalized.length === 0) {
    return '';
  }
  return isSnapshotDetailLink(normalized, context.projectId, context.applicationId) ? normalized : '';
}

function canonicalSnapshotDetailLink(snapshotId) {
  const normalizedSnapshotId = String(snapshotId ?? '').trim();
  if (!selectedDashboardContext || !isValidSnapshotId(normalizedSnapshotId)) {
    return '';
  }
  if (!isApplicationDashboardLink(
    selectedDashboardContext.dashboardLink,
    selectedDashboardContext.projectId,
    selectedDashboardContext.applicationId
  )) {
    return '';
  }
  return `/api/projects/${selectedDashboardContext.projectId}/applications/${selectedDashboardContext.applicationId}/dashboard/snapshots/${normalizedSnapshotId}`;
}

function snapshotDetailContextFromLink(detailLink, anchor, origin) {
  const context = selectedSnapshotHistoryContext || snapshotHistoryContextFromSelectedDashboard();
  if (!context || !isSnapshotDetailLink(detailLink, context.projectId, context.applicationId)) {
    return null;
  }
  const snapshotId = snapshotIdFromDetailLink(detailLink);
  if (!snapshotId) {
    return null;
  }
  return {
    projectId: context.projectId,
    projectName: context.projectName,
    applicationId: context.applicationId,
    applicationName: context.applicationName,
    applicationEnvironment: context.applicationEnvironment,
    snapshotId,
    detailLink,
    anchor: String(anchor ?? '').trim(),
    origin: String(origin ?? '').trim()
  };
}

function isSnapshotDetailLink(detailLink, projectId, applicationId) {
  const selectedProjectId = String(projectId ?? '').trim();
  const selectedApplicationId = String(applicationId ?? '').trim();
  if (selectedProjectId.length === 0 || selectedApplicationId.length === 0) {
    return false;
  }
  const match = String(detailLink ?? '').match(
    /^\/api\/projects\/([^/?#]+)\/applications\/([^/?#]+)\/dashboard\/snapshots\/([^/?#]+)$/
  );
  if (!match || !isValidSnapshotId(match[3])) {
    return false;
  }
  try {
    return decodeURIComponent(match[1]) === selectedProjectId
      && decodeURIComponent(match[2]) === selectedApplicationId;
  } catch (error) {
    return false;
  }
}

function snapshotIdFromDetailLink(detailLink) {
  const match = String(detailLink ?? '').match(
    /^\/api\/projects\/[^/?#]+\/applications\/[^/?#]+\/dashboard\/snapshots\/([^/?#]+)$/
  );
  if (!match || !isValidSnapshotId(match[1])) {
    return '';
  }
  try {
    return decodeURIComponent(match[1]);
  } catch (error) {
    return '';
  }
}

function isSnapshotMarkerListLink(markersLink, projectId, applicationId) {
  const normalizedProjectId = String(projectId ?? '').trim();
  const normalizedApplicationId = String(applicationId ?? '').trim();
  if (normalizedProjectId.length === 0 || normalizedApplicationId.length === 0) {
    return false;
  }
  const match = String(markersLink ?? '').match(
    /^\/api\/projects\/([^/?#]+)\/applications\/([^/?#]+)\/dashboard\/snapshot-markers(?:\?[^#]*)?$/
  );
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

function isSelectedInstanceSnapshotTrendResponse(trend) {
  if (!selectedInstanceSnapshotTrendContext || !trend || !trend.application || !trend.instance) {
    return false;
  }
  if (!hasInstanceSnapshotTrendTopLevelFields(trend)) {
    return false;
  }
  return String(trend.application.projectId ?? '') === selectedInstanceSnapshotTrendContext.projectId
    && String(trend.application.applicationId ?? '') === selectedInstanceSnapshotTrendContext.applicationId
    && String(trend.instance.instanceId ?? '') === selectedInstanceSnapshotTrendContext.instanceId;
}

function isSelectedOperationalEventsHistoryResponse(history, preset) {
  if (!isValidSnapshotHistoryContext(selectedSnapshotHistoryContext) || !isObjectValue(history)) {
    return false;
  }
  return history.source === 'dashboard_snapshots'
    && String(history.applicationId ?? '') === selectedSnapshotHistoryContext.applicationId
    && isValidOperationalEventHorizon(history.horizon, preset)
    && Array.isArray(history.events)
    && history.events.length <= history.horizon.limit
    && hasOperationalEventOrder(history.events)
    && history.events.every(isValidOperationalEventItem);
}

function isValidOperationalEventHorizon(horizon, preset) {
  const normalizedPreset = normalizeSnapshotHistoryPreset(preset);
  const query = SNAPSHOT_HISTORY_PRESET_QUERY[normalizedPreset];
  return Boolean(isObjectValue(horizon)
    && isBeforeTimestamp(horizon.since, horizon.until)
    && horizon.requestedSince === normalizedPreset
    && horizon.defaultSince === SNAPSHOT_HISTORY_PRESETS.DAY
    && horizon.maxSince === SNAPSHOT_HISTORY_PRESETS.FOURTEEN_DAYS
    && horizon.limit === query.eventLimit
    && horizon.maxLimit === 100
    && horizon.order === 'occurredAt_desc');
}

function hasOperationalEventOrder(events) {
  let previousOccurredAt = null;
  let previousEventId = null;
  for (const event of events) {
    if (!isObjectValue(event)) {
      return false;
    }
    const occurredAtMillis = parseTimestampMillis(event.occurredAt);
    const eventId = String(event.eventId ?? '');
    if (occurredAtMillis == null) {
      return false;
    }
    if (previousOccurredAt != null && occurredAtMillis > previousOccurredAt) {
      return false;
    }
    if (previousOccurredAt != null
      && occurredAtMillis === previousOccurredAt
      && eventId <= previousEventId) {
      return false;
    }
    previousOccurredAt = occurredAtMillis;
    previousEventId = eventId;
  }
  return true;
}

function isValidOperationalEventItem(event) {
  const snapshotId = String(event && event.snapshotId ? event.snapshotId : '');
  return Boolean(isObjectValue(event)
    && hasRequiredText(event.eventId)
    && isAllowedValue(event.type, [
      'degraded_entered',
      'degraded_resolved',
      'stale_entered',
      'down_entered',
      'recovery_observed',
      'high_confidence_concern',
      'state_changed'
    ])
    && isAllowedValue(event.severity, ['info', 'warning', 'critical'])
    && hasRequiredText(event.title)
    && hasRequiredText(event.summary)
    && hasRequiredText(event.occurredAt)
    && (event.resolvedAt == null || hasRequiredText(event.resolvedAt))
    && hasRequiredText(event.stateCode)
    && isNullableFraction(event.confidence)
    && isValidSnapshotId(snapshotId)
    && isValidOperationalEventEvidence(event.evidence)
    && isObjectValue(event.links)
    && isSnapshotDetailLink(
      event.links.snapshot,
      selectedSnapshotHistoryContext.projectId,
      selectedSnapshotHistoryContext.applicationId
    )
    && snapshotIdFromDetailLink(event.links.snapshot) === snapshotId);
}

function isValidOperationalEventEvidence(evidence) {
  return Boolean(isObjectValue(evidence)
    && (evidence.ruleId == null || hasRequiredText(evidence.ruleId))
    && (evidence.endpointKey == null || (hasRequiredText(evidence.endpointKey) && !hasRawEndpointDetail(evidence.endpointKey)))
    && (evidence.method == null || hasRequiredText(evidence.method))
    && (evidence.route == null || (hasRequiredText(evidence.route) && !hasRawEndpointDetail(evidence.route)))
    && (evidence.snapshotDetailAnchor == null || hasRequiredText(evidence.snapshotDetailAnchor))
    && (evidence.anchorStatus == null || isAllowedValue(evidence.anchorStatus, ['resolved', 'missing'])));
}

function isSelectedSnapshotMarkerResponse(markers, preset) {
  if (!isValidSnapshotHistoryContext(selectedSnapshotHistoryContext) || !isObjectValue(markers)) {
    return false;
  }
  return markers.source === 'dashboard_snapshots'
    && String(markers.applicationId ?? '') === selectedSnapshotHistoryContext.applicationId
    && isValidSnapshotMarkerHorizon(markers.horizon, preset)
    && Array.isArray(markers.markers)
    && markers.markers.length <= markers.horizon.limit
    && hasAscendingSnapshotMarkers(markers.markers)
    && markers.markers.every(isValidSnapshotMarkerItem);
}

function isValidSnapshotMarkerHorizon(horizon, preset) {
  const normalizedPreset = normalizeSnapshotHistoryPreset(preset);
  const query = SNAPSHOT_HISTORY_PRESET_QUERY[normalizedPreset];
  return Boolean(isObjectValue(horizon)
    && isBeforeTimestamp(horizon.since, horizon.until)
    && horizon.requestedSince === normalizedPreset
    && horizon.defaultSince === SNAPSHOT_HISTORY_PRESETS.DAY
    && horizon.maxSince === SNAPSHOT_HISTORY_PRESETS.FOURTEEN_DAYS
    && horizon.limit === query.markerLimit
    && horizon.maxLimit === 336
    && horizon.order === 'capturedAt_asc');
}

function hasAscendingSnapshotMarkers(markers) {
  let previousCapturedAt = null;
  let previousSnapshotId = null;
  for (const marker of markers) {
    if (!isObjectValue(marker)) {
      return false;
    }
    const capturedAtMillis = parseTimestampMillis(marker.capturedAt);
    const snapshotId = String(marker.snapshotId ?? '');
    if (capturedAtMillis == null) {
      return false;
    }
    if (previousCapturedAt != null && capturedAtMillis < previousCapturedAt) {
      return false;
    }
    if (previousCapturedAt != null
      && capturedAtMillis === previousCapturedAt
      && snapshotId <= previousSnapshotId) {
      return false;
    }
    previousCapturedAt = capturedAtMillis;
    previousSnapshotId = snapshotId;
  }
  return true;
}

function isValidSnapshotMarkerItem(marker) {
  const snapshotId = String(marker && marker.snapshotId ? marker.snapshotId : '');
  return Boolean(isObjectValue(marker)
    && hasRequiredText(marker.markerId)
    && isValidSnapshotId(snapshotId)
    && hasRequiredText(marker.capturedAt)
    && hasRequiredText(marker.currentWindowEndUtc)
    && isAllowedSnapshotMarkerType(marker.type)
    && isAllowedValue(marker.severity, ['info', 'warning', 'critical'])
    && marker.readMeaning === 'stored_read_model_point'
    && (marker.captureReason == null || typeof marker.captureReason === 'string')
    && hasRequiredText(marker.storedApplicationStateCode)
    && isObjectValue(marker.previousState)
    && hasRequiredText(marker.title)
    && hasRequiredText(marker.summary)
    && (marker.recommendedAction == null || typeof marker.recommendedAction === 'string')
    && isNullableFraction(marker.confidence)
    && (marker.primaryRuleId == null || hasRequiredText(marker.primaryRuleId))
    && (marker.primaryEndpointKey == null || (hasRequiredText(marker.primaryEndpointKey) && !hasRawEndpointDetail(marker.primaryEndpointKey)))
    && isObjectValue(marker.links)
    && isSnapshotDetailLink(
      marker.links.snapshot,
      selectedSnapshotHistoryContext.projectId,
      selectedSnapshotHistoryContext.applicationId
    )
    && snapshotIdFromDetailLink(marker.links.snapshot) === snapshotId);
}

function isSelectedSnapshotDetailResponse(detail) {
  if (!selectedSnapshotDetailContext || !isObjectValue(detail)) {
    return false;
  }
  return detail.source === 'dashboard_snapshots'
    && isValidSnapshotReadSemantics(detail.readSemantics)
    && isValidSnapshotMetadata(detail.snapshot)
    && String(detail.snapshot.snapshotId ?? '') === selectedSnapshotDetailContext.snapshotId
    && isValidSnapshotMarkerItemForDetail(detail.marker)
    && isObjectValue(detail.previousState)
    && isObjectValue(detail.lastHealthyAt)
    && isValidRecoveryMarker(detail.recoveryMarker)
    && isValidSnapshotDetailReadModel(detail.readModel)
    && isValidSnapshotEndpointEvidence(detail.snapshotEndpointEvidence)
    && isValidSnapshotInstanceSummary(detail.instanceSummary)
    && isObjectValue(detail.links)
    && isSnapshotDetailLink(
      detail.links.self,
      selectedSnapshotDetailContext.projectId,
      selectedSnapshotDetailContext.applicationId
    )
    && snapshotIdFromDetailLink(detail.links.self) === selectedSnapshotDetailContext.snapshotId
    && isSnapshotMarkerListLink(
      detail.links.markers,
      selectedSnapshotDetailContext.projectId,
      selectedSnapshotDetailContext.applicationId
    );
}

function isValidSnapshotReadSemantics(readSemantics) {
  return Boolean(isObjectValue(readSemantics)
    && readSemantics.mode === 'stored_snapshot_detail'
    && readSemantics.currentStateRecalculated === false
    && Array.isArray(readSemantics.liveSourcesJoined)
    && readSemantics.liveSourcesJoined.length === 0
    && readSemantics.rawReadModelJsonExposed === false);
}

function isValidSnapshotMetadata(snapshot) {
  return Boolean(isObjectValue(snapshot)
    && isValidSnapshotId(snapshot.snapshotId)
    && hasRequiredText(snapshot.capturedAt)
    && hasRequiredText(snapshot.generatedAt)
    && isValidSnapshotWindow(snapshot.currentWindow)
    && isValidSnapshotWindow(snapshot.baselineWindow)
    && (snapshot.captureReason == null || typeof snapshot.captureReason === 'string')
    && hasRequiredText(snapshot.storedApplicationStateCode)
    && (snapshot.primaryRuleId == null || hasRequiredText(snapshot.primaryRuleId))
    && (snapshot.primaryEndpointKey == null || (hasRequiredText(snapshot.primaryEndpointKey) && !hasRawEndpointDetail(snapshot.primaryEndpointKey)))
    && isNullableFraction(snapshot.maxConfidence));
}

function isValidSnapshotWindow(window) {
  return Boolean(isObjectValue(window)
    && hasRequiredText(window.startUtc)
    && hasRequiredText(window.endUtc)
    && isBeforeTimestamp(window.startUtc, window.endUtc));
}

function isValidSnapshotMarkerItemForDetail(marker) {
  const context = selectedSnapshotDetailContext;
  const snapshotId = String(marker && marker.snapshotId ? marker.snapshotId : '');
  return Boolean(isObjectValue(marker)
    && hasRequiredText(marker.markerId)
    && isValidSnapshotId(snapshotId)
    && snapshotId === context.snapshotId
    && hasRequiredText(marker.capturedAt)
    && hasRequiredText(marker.currentWindowEndUtc)
    && isAllowedSnapshotMarkerType(marker.type)
    && isAllowedValue(marker.severity, ['info', 'warning', 'critical'])
    && marker.readMeaning === 'stored_read_model_point'
    && hasRequiredText(marker.storedApplicationStateCode)
    && isObjectValue(marker.previousState)
    && hasRequiredText(marker.title)
    && hasRequiredText(marker.summary)
    && isObjectValue(marker.links)
    && isSnapshotDetailLink(marker.links.snapshot, context.projectId, context.applicationId)
    && snapshotIdFromDetailLink(marker.links.snapshot) === snapshotId);
}

function isAllowedSnapshotMarkerType(value) {
  return isAllowedValue(value, SNAPSHOT_MARKER_TYPES);
}

function isValidRecoveryMarker(recoveryMarker) {
  return recoveryMarker == null || Boolean(isObjectValue(recoveryMarker)
    && hasRequiredText(recoveryMarker.markerId)
    && recoveryMarker.type === 'recovery_observed'
    && recoveryMarker.severity === 'warning'
    && hasRequiredText(recoveryMarker.title)
    && hasRequiredText(recoveryMarker.summary)
    && hasRequiredText(recoveryMarker.recommendedAction)
    && isObjectValue(recoveryMarker.previousState)
    && isObjectValue(recoveryMarker.lastHealthyAt));
}

function isValidSnapshotDetailReadModel(readModel) {
  return Boolean(isObjectValue(readModel)
    && Object.prototype.hasOwnProperty.call(readModel, 'application')
    && Object.prototype.hasOwnProperty.call(readModel, 'state')
    && Object.prototype.hasOwnProperty.call(readModel, 'starterConnection')
    && Object.prototype.hasOwnProperty.call(readModel, 'zeroInsight')
    && Object.prototype.hasOwnProperty.call(readModel, 'recovery')
    && Object.prototype.hasOwnProperty.call(readModel, 'metrics')
    && Object.prototype.hasOwnProperty.call(readModel, 'sourceScopedPercentiles')
    && Object.prototype.hasOwnProperty.call(readModel, 'triageCards')
    && Object.prototype.hasOwnProperty.call(readModel, 'endpointPriority'));
}

function isValidSnapshotEndpointEvidence(snapshotEndpointEvidence) {
  const items = Array.isArray(snapshotEndpointEvidence && snapshotEndpointEvidence.items)
    ? snapshotEndpointEvidence.items
    : [];
  return Boolean(isObjectValue(snapshotEndpointEvidence)
    && snapshotEndpointEvidence.source === 'bounded_endpoint_evidence'
    && snapshotEndpointEvidence.maxItems === 10
    && (snapshotEndpointEvidence.selectionPolicy == null || typeof snapshotEndpointEvidence.selectionPolicy === 'string')
    && (snapshotEndpointEvidence.unavailableReason == null || typeof snapshotEndpointEvidence.unavailableReason === 'string')
    && Array.isArray(snapshotEndpointEvidence.items)
    && items.length <= 10
    && items.every((item, index) => isValidSnapshotEndpointEvidenceItem(item, index)));
}

function isValidSnapshotEndpointEvidenceItem(item, index) {
  return Boolean(isObjectValue(item)
    && item.anchorId === `endpoint-evidence-${index + 1}`
    && hasRequiredText(item.endpointKey)
    && !hasRawEndpointDetail(item.endpointKey)
    && (item.method == null || hasRequiredText(item.method))
    && (item.route == null || (hasRequiredText(item.route) && !hasRawEndpointDetail(item.route)))
    && isNullablePositiveInteger(item.rank)
    && (item.reason == null || hasRequiredText(item.reason))
    && Array.isArray(item.ruleIds)
    && item.ruleIds.every(hasRequiredText)
    && isNullableFraction(item.confidence)
    && isNullableNonNegativeNumber(item.score)
    && isNullableNonNegativeNumber(item.requestCount)
    && isNullableFraction(item.errorRate)
    && isNullableBucketArray(item.durationBuckets)
    && isNullableBucketArray(item.baselineDurationBuckets)
    && (item.bucketDistributionSource == null || hasRequiredText(item.bucketDistributionSource))
    && (item.freshness == null || isObjectValue(item.freshness))
    && (item.recommendedAction == null || typeof item.recommendedAction === 'string'));
}

function isNullableBucketArray(value) {
  return value == null || (Array.isArray(value) && value.every(isValidHistogramBucket));
}

function isValidSnapshotInstanceSummary(instanceSummary) {
  const items = Array.isArray(instanceSummary && instanceSummary.items) ? instanceSummary.items : [];
  return Boolean(isObjectValue(instanceSummary)
    && instanceSummary.schemaVersion === '1.0'
    && instanceSummary.source === 'bounded_instance_summary'
    && instanceSummary.maxItems === 50
    && (instanceSummary.selectionPolicy == null || typeof instanceSummary.selectionPolicy === 'string')
    && (instanceSummary.unavailableReason == null || typeof instanceSummary.unavailableReason === 'string')
    && Array.isArray(instanceSummary.items)
    && items.length <= 50
    && items.every(isValidSnapshotInstanceSummaryItem));
}

function isValidSnapshotInstanceSummaryItem(item) {
  return Boolean(isObjectValue(item)
    && hasRequiredText(item.instanceId)
    && hasRequiredText(item.instanceName)
    && hasRequiredText(item.observationStatus)
    && Array.isArray(item.endpointEvidenceRefs)
    && item.endpointEvidenceRefs.every(isValidSnapshotEndpointEvidenceRef));
}

function isValidSnapshotEndpointEvidenceRef(ref) {
  return Boolean(isObjectValue(ref)
    && hasRequiredText(ref.endpointKey)
    && !hasRawEndpointDetail(ref.endpointKey)
    && (ref.method == null || hasRequiredText(ref.method))
    && (ref.route == null || (hasRequiredText(ref.route) && !hasRawEndpointDetail(ref.route)))
    && isNullablePositiveInteger(ref.relatedApplicationPriorityRank)
    && Array.isArray(ref.relatedRuleIds)
    && ref.relatedRuleIds.every(hasRequiredText)
    && (ref.snapshotDetailAnchor == null || hasRequiredText(ref.snapshotDetailAnchor))
    && isAllowedValue(ref.anchorStatus, ['resolved', 'missing']));
}

function hasSnapshotEndpointAnchor(detail, anchor) {
  const items = detail && detail.snapshotEndpointEvidence && Array.isArray(detail.snapshotEndpointEvidence.items)
    ? detail.snapshotEndpointEvidence.items
    : [];
  return items.some(item => item.anchorId === anchor);
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

function hasInstanceSnapshotTrendTopLevelFields(trend) {
  return Boolean(isObjectValue(trend)
    && trend.generatedAt
    && isObjectValue(trend.application)
    && isObjectValue(trend.instance)
    && trend.source === 'dashboard_snapshots.read_model_json.instanceSummary.items'
    && isValidInstanceSnapshotTrendApplication(trend.application)
    && isValidInstanceSnapshotTrendInstance(trend.instance)
    && isValidInstanceSnapshotTrendHorizon(trend.horizon)
    && Array.isArray(trend.points)
    && trend.points.length <= trend.horizon.limit
    && hasAscendingSnapshotTrendPoints(trend.points)
    && trend.points.every(isValidInstanceSnapshotTrendPoint));
}

function isValidInstanceSnapshotTrendApplication(application) {
  return Boolean(isObjectValue(application)
    && hasRequiredText(application.projectId)
    && hasRequiredText(application.applicationId)
    && hasRequiredText(application.name)
    && hasRequiredText(application.environment)
    && isObjectValue(application.links)
    && isApplicationDashboardLink(
      application.links.dashboard,
      selectedInstanceSnapshotTrendContext.projectId,
      selectedInstanceSnapshotTrendContext.applicationId
    ));
}

function isValidInstanceSnapshotTrendInstance(instance) {
  return Boolean(isObjectValue(instance)
    && hasRequiredText(instance.instanceId)
    && hasRequiredText(instance.instanceName)
    && isObjectValue(instance.links)
    && isInstanceEvidenceLink(
      instance.links.evidence,
      selectedInstanceSnapshotTrendContext.projectId,
      selectedInstanceSnapshotTrendContext.applicationId,
      selectedInstanceSnapshotTrendContext.instanceId
    ));
}

function isValidInstanceSnapshotTrendHorizon(horizon) {
  return Boolean(isObjectValue(horizon)
    && hasRequiredText(horizon.since)
    && hasRequiredText(horizon.until)
    && isBeforeTimestamp(horizon.since, horizon.until)
    && isAllowedValue(horizon.requestedSince, [
      INSTANCE_SNAPSHOT_TREND_PRESETS.SEVEN_DAYS,
      INSTANCE_SNAPSHOT_TREND_PRESETS.FOURTEEN_DAYS
    ])
    && horizon.defaultSince === INSTANCE_SNAPSHOT_TREND_PRESETS.SEVEN_DAYS
    && horizon.maxSince === INSTANCE_SNAPSHOT_TREND_PRESETS.FOURTEEN_DAYS
    && isPositiveInteger(horizon.limit)
    && horizon.limit <= 336
    && horizon.maxLimit === 336
    && horizon.order === 'capturedAt_asc'
    && (horizon.requestedSince !== INSTANCE_SNAPSHOT_TREND_PRESETS.SEVEN_DAYS || horizon.limit === 168)
    && (horizon.requestedSince !== INSTANCE_SNAPSHOT_TREND_PRESETS.FOURTEEN_DAYS || horizon.limit === 336));
}

function hasAscendingSnapshotTrendPoints(points) {
  let previousCapturedAt = null;
  let previousSnapshotId = null;
  for (const point of points) {
    if (!isObjectValue(point)) {
      return false;
    }
    const capturedAtMillis = parseTimestampMillis(point.capturedAt);
    const snapshotId = String(point.snapshotId ?? '');
    if (capturedAtMillis == null) {
      return false;
    }
    if (previousCapturedAt != null && capturedAtMillis < previousCapturedAt) {
      return false;
    }
    if (previousCapturedAt != null
      && capturedAtMillis === previousCapturedAt
      && snapshotId <= previousSnapshotId) {
      return false;
    }
    previousCapturedAt = capturedAtMillis;
    previousSnapshotId = snapshotId;
  }
  return true;
}

function isValidInstanceSnapshotTrendPoint(point) {
  return Boolean(isObjectValue(point)
    && hasRequiredText(point.snapshotId)
    && hasRequiredText(point.capturedAt)
    && hasRequiredText(point.currentWindowEndUtc)
    && hasRequiredText(point.storedApplicationStateCode)
    && (point.captureReason == null || typeof point.captureReason === 'string')
    && hasRequiredText(point.instanceName)
    && hasRequiredText(point.observationStatus)
    && isValidSnapshotTrendMetricData(point.metricData)
    && isValidSnapshotTrendStarterConnection(point.starterConnection)
    && isValidSnapshotTrendStarterPercentilePoint(point.starterPercentilePoint)
    && isValidSnapshotTrendResourceHints(point.resourceHints)
    && isValidSnapshotTrendTriageContribution(point.applicationTriageContribution)
    && Array.isArray(point.endpointEvidenceRefs)
    && point.endpointEvidenceRefs.length <= 10
    && point.endpointEvidenceRefs.every(isValidSnapshotTrendEndpointRef));
}

function isValidSnapshotTrendMetricData(metricData) {
  return Boolean(isObjectValue(metricData)
    && metricData.statusSource === 'accepted_bucket'
    && hasRequiredText(metricData.freshnessLabel));
}

function isValidSnapshotTrendStarterConnection(starterConnection) {
  return Boolean(isObjectValue(starterConnection)
    && starterConnection.statusSource === 'starter_heartbeat'
    && hasRequiredText(starterConnection.lastHeartbeatStatus)
    && hasRequiredText(starterConnection.connectionMeaning)
    && starterConnection.stateImpact === 'none');
}

function isValidSnapshotTrendStarterPercentilePoint(point) {
  return point == null || Boolean(isObjectValue(point)
    && point.source === 'starter_canonical_percentile'
    && point.scope === 'instance_bucket'
    && hasRequiredText(point.bucketStartUtc)
    && hasRequiredText(point.bucketEndUtc)
    && isBeforeTimestamp(point.bucketStartUtc, point.bucketEndUtc)
    && isNonNegativeNumber(point.requestCount)
    && isNonNegativeNumber(point.p95Ms)
    && isNonNegativeNumber(point.p99Ms)
    && point.p99Ms >= point.p95Ms);
}

function isValidSnapshotTrendResourceHints(resourceHints) {
  return resourceHints == null || Boolean(isObjectValue(resourceHints)
    && resourceHints.source === 'accepted_bucket_latest_sample'
    && hasRequiredText(resourceHints.status)
    && isNullableFraction(resourceHints.cpuUsageRatio)
    && isNullableFraction(resourceHints.heapUsedRatio)
    && isNullableFraction(resourceHints.datasourcePoolUsageRatio));
}

function isValidSnapshotTrendTriageContribution(applicationTriageContribution) {
  return Boolean(isObjectValue(applicationTriageContribution)
    && hasRequiredText(applicationTriageContribution.status)
    && typeof applicationTriageContribution.contributed === 'boolean'
    && Array.isArray(applicationTriageContribution.relatedRuleIds)
    && applicationTriageContribution.relatedRuleIds.every(hasRequiredText)
    && (applicationTriageContribution.contributed || applicationTriageContribution.relatedRuleIds.length === 0));
}

function isValidSnapshotTrendEndpointRef(ref) {
  return Boolean(isObjectValue(ref)
    && hasRequiredText(ref.endpointKey)
    && !hasRawEndpointDetail(ref.endpointKey)
    && (ref.method == null || hasRequiredText(ref.method))
    && (ref.route == null || (hasRequiredText(ref.route) && !hasRawEndpointDetail(ref.route)))
    && isNullablePositiveInteger(ref.relatedApplicationPriorityRank)
    && Array.isArray(ref.relatedRuleIds)
    && ref.relatedRuleIds.every(hasRequiredText)
    && (ref.snapshotDetailAnchor == null || hasRequiredText(ref.snapshotDetailAnchor)));
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

function isValidSnapshotId(value) {
  return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(String(value ?? '').trim());
}

function projectRequestHeaders() {
  const headers = { Accept: 'application/json' };
  if (serviceAccessToken) {
    headers.Authorization = `Bearer ${serviceAccessToken}`;
  }
  return headers;
}

function projectMutationHeaders() {
  return {
    ...projectRequestHeaders(),
    'Content-Type': 'application/json'
  };
}

async function handleProjectRegistrationSubmit(event) {
  event.preventDefault();
  const requestId = ++registrationRequestSequence;
  if (!serviceAccessToken) {
    setRegistrationStatus('GitHub 로그인 후 Project를 등록할 수 있습니다.');
    return;
  }
  const projectName = String(projectNameInput.value ?? '').trim();
  if (projectName.length === 0) {
    setRegistrationStatus('Project name을 입력해 주세요.');
    return;
  }
  renderRegistrationLoading();
  try {
    const response = await fetch('/api/projects', {
      method: 'POST',
      cache: 'no-store',
      headers: projectMutationHeaders(),
      body: JSON.stringify({ name: projectName })
    });
    if (!isLatestRegistrationRequest(requestId)) {
      return;
    }
    if (response.status === 401) {
      handleAuthorizationLoss();
      return;
    }
    if (!response.ok) {
      renderRegistrationFailure(response.status);
      return;
    }
    const data = await response.json();
    if (!isLatestRegistrationRequest(requestId)) {
      return;
    }
    if (!isValidOneTimeCredentialResponse(data)) {
      throw new Error('registration_payload_malformed');
    }
    const displayValue = data.starterCredential.displayValue;
    projectNameInput.value = '';
    setRegistrationStatus('Project가 등록되었습니다. 서버 기준 목록을 다시 불러옵니다.');
    renderOneTimeCredentialSuccess('registration', data.project && data.project.name, displayValue, data.starterCredential);
    loadProjects();
  } catch (error) {
    if (!isLatestRegistrationRequest(requestId)) {
      return;
    }
    renderRegistrationFailure();
  }
}

async function handleCredentialLifecycleAction(context) {
  const requestId = ++credentialLifecycleRequestSequence;
  if (!serviceAccessToken) {
    renderCredentialLifecycleMessage('GitHub 로그인 후 credential lifecycle을 사용할 수 있습니다.');
    return;
  }
  if (!isValidProjectActionContext(context)) {
    renderCredentialLifecycleMessage('Project credential endpoint를 확인할 수 없습니다.');
    return;
  }
  renderCredentialLifecycleLoading(context);
  try {
    const response = await fetch(starterCredentialActionPath(context.projectId, context.action), {
      method: context.action === 'metadata' ? 'GET' : 'POST',
      cache: 'no-store',
      headers: context.action === 'metadata' ? projectRequestHeaders() : projectMutationHeaders()
    });
    if (!isLatestCredentialLifecycleRequest(requestId)) {
      return;
    }
    if (response.status === 401) {
      handleAuthorizationLoss();
      return;
    }
    if (response.status === 404) {
      renderCredentialLifecycleMessage('Project credential 정보를 확인할 수 없습니다.');
      return;
    }
    if (!response.ok) {
      renderCredentialLifecycleMessage('Credential lifecycle 요청을 완료하지 못했습니다.');
      return;
    }
    const data = await response.json();
    if (!isLatestCredentialLifecycleRequest(requestId)) {
      return;
    }
    if (context.action === 'rotate') {
      if (!isValidOneTimeCredentialResponse(data)) {
        throw new Error('rotation_payload_malformed');
      }
      const displayValue = data.starterCredential.displayValue;
      renderOneTimeCredentialSuccess('rotation', context.projectName, displayValue, data.starterCredential);
      renderCredentialMetadata(data, context.projectName, 'Rotation 완료. 이전 starter credential은 즉시 폐기되었습니다.');
      return;
    }
    if (!isValidCredentialMetadataResponse(data)) {
      throw new Error('credential_metadata_payload_malformed');
    }
    const message = context.action === 'revoke'
      ? 'Revocation 완료. Project는 목록에 남지만 starter ingest credential 검증은 실패합니다.'
      : 'Credential metadata는 raw value나 hash 없이 표시됩니다.';
    renderCredentialMetadata(data, context.projectName, message);
  } catch (error) {
    if (!isLatestCredentialLifecycleRequest(requestId)) {
      return;
    }
    renderCredentialLifecycleMessage('Credential lifecycle 요청을 완료하지 못했습니다.');
  }
}

function renderRegistrationLoading() {
  registrationSubmitButton.disabled = true;
  setRegistrationStatus('Project를 등록하는 중');
}

function renderRegistrationFailure(status) {
  registrationSubmitButton.disabled = false;
  if (status === 409) {
    setRegistrationStatus('이미 사용 중인 Project name입니다.');
    return;
  }
  if (status === 400) {
    setRegistrationStatus('Project name은 소문자, 숫자, 하이픈 조합으로 입력해 주세요.');
    return;
  }
  setRegistrationStatus('Project 등록을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.');
}

function renderOneTimeCredentialSuccess(source, projectName, displayValue, starterCredential) {
  registrationSubmitButton.disabled = false;
  const label = source === 'rotation' ? '새 starter credential' : 'starter credential';
  const issuedAt = formatTimestamp(starterCredential && starterCredential.issuedAt, 'issuedAt source absence');
  starterCredentialOneTime.innerHTML = `
    <div class="one-time-card">
      <p><strong>${escapeText(label)}은 이 화면에서 한 번만 표시됩니다.</strong>복사 후 확인하면 이 화면에서도 지워지며, 나중에 다시 볼 수 없고 필요하면 rotation으로 새 credential을 발급받아야 합니다.</p>
      <p class="credential-context">${escapeText(projectName || 'Project')} · issuedAt ${escapeText(issuedAt)}</p>
      <code class="credential-display" id="starter-credential-display">${escapeText(displayValue)}</code>
      <div class="credential-actions">
        <button class="icon-button" type="button" id="copy-starter-credential">복사</button>
        <button class="icon-button primary" type="button" id="confirm-starter-credential">복사했음</button>
      </div>
      <p id="starter-credential-copy-status" class="lifecycle-status" aria-live="polite"></p>
    </div>
  `;
  const copyButton = document.querySelector('#copy-starter-credential');
  const confirmButton = document.querySelector('#confirm-starter-credential');
  copyButton.addEventListener('click', () => copyStarterCredential(displayValue));
  confirmButton.addEventListener('click', () => clearOneTimeCredentialDisplay('복사 확인됨. 이 화면에서 credential 표시를 지웠습니다.'));
}

async function copyStarterCredential(displayValue) {
  const copyStatus = document.querySelector('#starter-credential-copy-status');
  try {
    if (!navigator.clipboard || typeof navigator.clipboard.writeText !== 'function') {
      throw new Error('clipboard_unavailable');
    }
    await navigator.clipboard.writeText(displayValue);
    copyStatus.textContent = '복사 완료. starter 설정에 붙여 넣은 뒤 확인해 주세요.';
  } catch (error) {
    copyStatus.textContent = '클립보드 복사를 완료하지 못했습니다. 표시된 값을 직접 선택해 복사해 주세요.';
  }
}

function clearOneTimeCredentialDisplay(message) {
  starterCredentialOneTime.innerHTML = `
    <p>${escapeText(message)}</p>
    <p>credential은 다시 표시되지 않습니다. 필요하면 선택한 Project에서 rotation을 실행해 새 값을 발급받으세요.</p>
  `;
}

function renderCredentialLifecycleLoading(context) {
  const actionLabel = context.action === 'rotate'
    ? 'rotation'
    : context.action === 'revoke' ? 'revocation' : 'metadata';
  renderCredentialLifecycleMessage(`${valueOrAbsence(context.projectName, context.projectId)} credential ${actionLabel} 요청 중`);
}

function renderCredentialMetadata(data, projectName, message) {
  const credential = data.starterCredential || {};
  credentialLifecyclePanel.innerHTML = `
    <div class="metadata-grid">
      <p><strong>${escapeText(message)}</strong></p>
      ${keyValueMarkup('Project', projectName || data.projectId)}
      ${keyValueMarkup('Prefix', credential.keyPrefix)}
      ${keyValueMarkup('Status', credential.status)}
      ${keyValueMarkup('Issued at', credential.issuedAt)}
      ${keyValueMarkup('Rotated at', credential.rotatedAt || 'rotation 없음')}
      ${keyValueMarkup('Revoked at', credential.revokedAt || 'revocation 없음')}
    </div>
  `;
}

function renderCredentialLifecycleMessage(message) {
  credentialLifecyclePanel.innerHTML = `<p>${escapeText(message)}</p>`;
}

function setRegistrationStatus(message) {
  registrationStatus.textContent = message;
}

function clearCredentialUi() {
  registrationSubmitButton.disabled = false;
  setRegistrationStatus('');
  starterCredentialOneTime.innerHTML = '<p>credential은 생성/회전 성공 직후에만 표시됩니다. 화면을 떠나면 다시 볼 수 없고 필요하면 새로 회전해야 합니다.</p>';
  credentialLifecyclePanel.innerHTML = '<p>Project card에서 credential metadata, rotation, revocation을 선택할 수 있습니다.</p>';
}

function starterCredentialActionPath(projectId, action) {
  const basePath = `/api/projects/${encodeURIComponent(projectId)}/starter-credential`;
  if (action === 'rotate') {
    return `${basePath}/rotations`;
  }
  if (action === 'revoke') {
    return `${basePath}/revocations`;
  }
  return basePath;
}

function isValidProjectActionContext(context) {
  return Boolean(context
    && ['metadata', 'rotate', 'revoke'].includes(context.action)
    && hasRequiredText(context.projectId));
}

function isValidOneTimeCredentialResponse(data) {
  const credential = data && data.starterCredential;
  return Boolean(isObjectValue(data)
    && isObjectValue(credential)
    && hasRequiredText(data.projectId || data.project && data.project.projectId)
    && hasRequiredText(credential.displayValue)
    && hasRequiredText(credential.keyPrefix)
    && credential.visibleOnce === true
    && hasRequiredText(credential.issuedAt)
    && !Object.prototype.hasOwnProperty.call(credential, 'projectKeyHash'));
}

function isValidCredentialMetadataResponse(data) {
  const credential = data && data.starterCredential;
  return Boolean(isObjectValue(data)
    && hasRequiredText(data.projectId)
    && isObjectValue(credential)
    && hasRequiredText(credential.keyPrefix)
    && ['active', 'revoked'].includes(String(credential.status ?? ''))
    && hasRequiredText(credential.issuedAt)
    && !Object.prototype.hasOwnProperty.call(credential, 'displayValue')
    && !Object.prototype.hasOwnProperty.call(credential, 'projectKeyHash'));
}

async function startGithubEntry() {
  setAuthStatus('');
  githubButton.disabled = true;
  const authWindow = openGithubAuthWindow();
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
    if (authWindow && shouldUsePopupAuthFlow(data.authorizationUrl)) {
      authWindow.location.assign(data.authorizationUrl);
      watchGithubAuthWindow(authWindow);
      setAuthStatus('GitHub 인증 창에서 로그인을 완료해 주세요.');
      return;
    }
    closeGithubAuthWindow(authWindow);
    setAuthStatus('GitHub 인증 화면으로 이동합니다.');
    window.location.assign(data.authorizationUrl);
  } catch (error) {
    closeGithubAuthWindow(authWindow);
    setAuthStatus('GitHub 로그인을 시작할 수 없습니다. 잠시 후 다시 시도해 주세요.');
  } finally {
    githubButton.disabled = false;
  }
}

function openGithubAuthWindow() {
  if (typeof window.open !== 'function') {
    return null;
  }
  const authWindow = window.open('', GITHUB_OAUTH_POPUP_NAME, GITHUB_OAUTH_POPUP_FEATURES);
  if (authWindow) {
    githubAuthWindow = authWindow;
  }
  return authWindow;
}

function shouldUsePopupAuthFlow(authorizationUrl) {
  const redirectOrigin = authorizationRedirectOrigin(authorizationUrl);
  return redirectOrigin.length === 0 || redirectOrigin === dashboardOrigin();
}

function authorizationRedirectOrigin(authorizationUrl) {
  try {
    const redirectUri = new URL(authorizationUrl).searchParams.get('redirect_uri');
    return redirectUri ? new URL(redirectUri).origin : '';
  } catch (error) {
    return '';
  }
}

function closeGithubAuthWindow(authWindow) {
  try {
    if (authWindow && !authWindow.closed) {
      authWindow.close();
    }
  } catch (error) {
    // 브라우저가 popup 제어를 막아도 auth state 정리는 계속 진행한다.
  }
  if (githubAuthWindow === authWindow) {
    githubAuthWindow = null;
  }
  clearGithubAuthWatcher();
}

async function handleGithubOAuthMessage(event) {
  if (!isTrustedGithubOAuthMessage(event)) {
    return;
  }
  await consumeGithubCallbackRelay(event.data.relayId);
}

function isTrustedGithubOAuthMessage(event) {
  return Boolean(event
    && event.origin === dashboardOrigin()
    && isObjectValue(event.data)
    && event.data.type === GITHUB_OAUTH_CALLBACK_MESSAGE
    && hasRequiredText(event.data.relayId));
}

function watchGithubAuthWindow(authWindow) {
  clearGithubAuthWatcher();
  githubAuthWatchStartedAt = Date.now();
  githubAuthWatchTimer = window.setInterval(() => {
    if (githubAuthRelayInFlight) {
      return;
    }
    if (!authWindow || authWindow.closed) {
      clearGithubAuthWatcher();
      if (!serviceAccessToken) {
        setAuthStatus('GitHub 인증 창이 닫혔습니다. 로그인을 다시 시도해 주세요.');
      }
      return;
    }
    const relayId = githubCallbackRelayIdFromWindow(authWindow);
    if (relayId) {
      consumeGithubCallbackRelay(relayId);
      return;
    }
    if (Date.now() - githubAuthWatchStartedAt > GITHUB_OAUTH_WATCH_TIMEOUT_MS) {
      clearGithubAuthWatcher();
      setAuthStatus('GitHub 로그인 완료를 확인하지 못했습니다. 다시 시도해 주세요.');
    }
  }, GITHUB_OAUTH_WATCH_INTERVAL_MS);
}

function clearGithubAuthWatcher() {
  if (githubAuthWatchTimer !== null) {
    window.clearInterval(githubAuthWatchTimer);
    githubAuthWatchTimer = null;
  }
  githubAuthWatchStartedAt = 0;
}

function githubCallbackRelayIdFromWindow(authWindow) {
  try {
    const marker = authWindow.document
      && authWindow.document.querySelector(`meta[name="${GITHUB_OAUTH_CALLBACK_RELAY_META}"]`);
    return marker && hasRequiredText(marker.content) ? marker.content.trim() : null;
  } catch (error) {
    return null;
  }
}

async function consumeGithubCallbackRelay(relayId) {
  const normalizedRelayId = normalizeRelayId(relayId);
  if (!normalizedRelayId || githubAuthRelayInFlight) {
    return;
  }
  githubAuthRelayInFlight = true;
  try {
    const response = await fetch(GITHUB_OAUTH_RELAY_ENDPOINT, {
      method: 'POST',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ relayId: normalizedRelayId })
    });
    if (!response.ok) {
      throw new Error('callback_relay_failed');
    }
    const data = await response.json();
    const token = normalizeAccessToken(data && data.accessToken);
    if (!token) {
      throw new Error('callback_relay_malformed');
    }
    window.observationPortalAuth.setAccessToken(token);
    setAuthStatus('GitHub 로그인이 완료되었습니다.');
    closeGithubAuthWindow(githubAuthWindow);
  } catch (error) {
    setAuthStatus('GitHub 로그인을 완료할 수 없습니다. 다시 시도해 주세요.');
  } finally {
    githubAuthRelayInFlight = false;
  }
}

function dashboardOrigin() {
  if (!window.location || typeof window.location.origin !== 'string') {
    return '';
  }
  return window.location.origin;
}

function handleAuthorizationLoss() {
  serviceAccessToken = null;
  projectRequestSequence += 1;
  applicationRequestSequence += 1;
  dashboardRequestSequence += 1;
  instanceEvidenceRequestSequence += 1;
  instanceSnapshotTrendRequestSequence += 1;
  snapshotHistoryRequestSequence += 1;
  snapshotDetailRequestSequence += 1;
  registrationRequestSequence += 1;
  credentialLifecycleRequestSequence += 1;
  clearProjectSnapshot({ resetFilter: true });
  clearApplicationSnapshot({ resetFilter: true, resetSelection: true });
  clearDashboardSnapshot({ resetSelection: true });
  clearInstanceEvidenceSnapshot({ resetSelection: true });
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  clearSnapshotHistorySnapshot({ resetSelection: true });
  clearSnapshotDetailSnapshot({ resetSelection: true });
  clearCredentialUi();
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

function setInstanceSnapshotTrendViewState(nextState) {
  currentInstanceSnapshotTrendViewState = nextState;
}

function setSnapshotHistoryViewState(nextState) {
  currentSnapshotHistoryViewState = nextState;
}

function setSnapshotDetailViewState(nextState) {
  currentSnapshotDetailViewState = nextState;
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
    instanceSnapshotTrendRequestSequence += 1;
    snapshotHistoryRequestSequence += 1;
    snapshotDetailRequestSequence += 1;
    clearApplicationSnapshot({ resetFilter: true, resetSelection: true });
    clearDashboardSnapshot({ resetSelection: true });
    clearInstanceEvidenceSnapshot({ resetSelection: true });
    clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
    clearSnapshotHistorySnapshot({ resetSelection: true });
    clearSnapshotDetailSnapshot({ resetSelection: true });
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

function clearInstanceSnapshotTrendSnapshot({ resetSelection = false } = {}) {
  loadedInstanceSnapshotTrend = null;
  if (resetSelection) {
    selectedInstanceSnapshotTrendContext = null;
  }
}

function clearSnapshotHistorySnapshot({ resetSelection = false } = {}) {
  loadedOperationalEvents = null;
  loadedSnapshotMarkers = null;
  if (resetSelection) {
    selectedSnapshotHistoryContext = null;
  }
}

function clearSnapshotDetailSnapshot({ resetSelection = false } = {}) {
  loadedSnapshotDetail = null;
  if (resetSelection) {
    selectedSnapshotDetailContext = null;
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

function discardInstanceSnapshotTrendForParentReload() {
  instanceSnapshotTrendRequestSequence += 1;
  const hadVisibleTrend = Boolean(selectedInstanceSnapshotTrendContext || loadedInstanceSnapshotTrend);
  clearInstanceSnapshotTrendSnapshot({ resetSelection: true });
  if (hadVisibleTrend) {
    renderDashboardIdle();
  }
}

function discardSnapshotHistoryForParentReload() {
  snapshotHistoryRequestSequence += 1;
  const hadVisibleHistory = Boolean(selectedSnapshotHistoryContext || loadedOperationalEvents || loadedSnapshotMarkers);
  clearSnapshotHistorySnapshot({ resetSelection: true });
  if (hadVisibleHistory) {
    renderDashboardIdle();
  }
}

function discardSnapshotDetailForParentReload() {
  snapshotDetailRequestSequence += 1;
  const hadVisibleDetail = Boolean(selectedSnapshotDetailContext || loadedSnapshotDetail);
  clearSnapshotDetailSnapshot({ resetSelection: true });
  if (hadVisibleDetail) {
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

function isLatestInstanceSnapshotTrendRequest(requestId) {
  return requestId === instanceSnapshotTrendRequestSequence;
}

function isLatestSnapshotHistoryRequest(requestId) {
  return requestId === snapshotHistoryRequestSequence;
}

function isLatestSnapshotDetailRequest(requestId) {
  return requestId === snapshotDetailRequestSequence;
}

function isLatestRegistrationRequest(requestId) {
  return requestId === registrationRequestSequence;
}

function isLatestCredentialLifecycleRequest(requestId) {
  return requestId === credentialLifecycleRequestSequence;
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

function instanceSnapshotTrendContextText() {
  if (!selectedInstanceSnapshotTrendContext) {
    return 'Instance Evidence에서 Snapshot Trend를 선택하면 stored trend를 볼 수 있습니다.';
  }
  const applicationLabel = selectedInstanceSnapshotTrendContext.applicationName || selectedInstanceSnapshotTrendContext.applicationId;
  const instanceLabel = selectedInstanceSnapshotTrendContext.instanceName || selectedInstanceSnapshotTrendContext.instanceId;
  return `${valueOrAbsence(applicationLabel, 'Application source absence')} · ${valueOrAbsence(instanceLabel, 'Instance source absence')} · ${valueOrAbsence(selectedInstanceSnapshotTrendContext.instanceId, 'Instance id source absence')}`;
}

function instanceSnapshotTrendReadyText(trend) {
  const application = trend.application || {};
  const instance = trend.instance || {};
  return `${valueOrAbsence(application.name, 'Application name source absence')} · ${valueOrAbsence(instance.instanceName, 'Instance name source absence')} · ${valueOrAbsence(instance.instanceId, 'Instance id source absence')}`;
}

function snapshotHistoryContextText() {
  if (!selectedSnapshotHistoryContext) {
    return selectedApplicationText();
  }
  const applicationLabel = selectedSnapshotHistoryContext.applicationName || selectedSnapshotHistoryContext.applicationId;
  return `${valueOrAbsence(selectedSnapshotHistoryContext.projectName || selectedSnapshotHistoryContext.projectId, 'Project source absence')} · ${valueOrAbsence(applicationLabel, 'Application source absence')} · ${valueOrAbsence(selectedSnapshotHistoryContext.applicationId, 'Application id source absence')}`;
}

function snapshotHistoryReadyText(events, markers) {
  const applicationId = events && events.applicationId ? events.applicationId : markers && markers.applicationId;
  const label = selectedSnapshotHistoryContext
    ? selectedSnapshotHistoryContext.applicationName || selectedSnapshotHistoryContext.applicationId
    : applicationId;
  return `${valueOrAbsence(label, 'Application source absence')} · ${valueOrAbsence(applicationId, 'Application id source absence')}`;
}

function snapshotDetailContextText() {
  if (!selectedSnapshotDetailContext) {
    return selectedApplicationText();
  }
  return `${valueOrAbsence(selectedSnapshotDetailContext.applicationName || selectedSnapshotDetailContext.applicationId, 'Application source absence')} · ${valueOrAbsence(selectedSnapshotDetailContext.snapshotId, 'Snapshot id source absence')}`;
}

function snapshotDetailReadyText(detail) {
  const snapshot = detail.snapshot || {};
  return `${snapshotDetailContextText()} · ${valueOrAbsence(snapshot.capturedAt, 'capturedAt source absence')}`;
}

function historyDisplayGeneratedAt(events, markers) {
  return events && events.generatedAt ? events.generatedAt : markers && markers.generatedAt;
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

function formatTrendGeneratedAt(generatedAt) {
  if (!generatedAt) {
    return 'Instance Snapshot Trend 기준 시각 대기 중';
  }
  const date = new Date(generatedAt);
  if (Number.isNaN(date.getTime())) {
    return 'Instance Snapshot Trend 기준 시각 확인 불가';
  }
  return `Instance Snapshot Trend 기준 ${new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date)}`;
}

function formatSnapshotHistoryGeneratedAt(generatedAt) {
  if (!generatedAt) {
    return 'Snapshot/History 기준 시각 대기 중';
  }
  const date = new Date(generatedAt);
  if (Number.isNaN(date.getTime())) {
    return 'Snapshot/History 기준 시각 확인 불가';
  }
  return `Snapshot/History 기준 ${new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date)}`;
}

function formatSnapshotDetailGeneratedAt(generatedAt) {
  if (!generatedAt) {
    return 'Snapshot Detail 기준 시각 대기 중';
  }
  const date = new Date(generatedAt);
  if (Number.isNaN(date.getTime())) {
    return 'Snapshot Detail 기준 시각 확인 불가';
  }
  return `Snapshot Detail 기준 ${new Intl.DateTimeFormat('ko-KR', {
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

function normalizeRelayId(value) {
  const relayId = String(value ?? '').trim();
  return relayId.length === 0 ? null : relayId;
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
registrationForm.addEventListener('submit', handleProjectRegistrationSubmit);
if (typeof window.addEventListener === 'function') {
  window.addEventListener('message', handleGithubOAuthMessage);
}
renderAuthorizationRequired();
renderApplicationAuthorizationRequired();
renderDashboardAuthorizationRequired();
