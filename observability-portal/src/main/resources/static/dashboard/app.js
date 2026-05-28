const projectList = document.querySelector('#project-list');
const reloadButton = document.querySelector('#reload-projects');
const filterInput = document.querySelector('#project-filter');
const githubButton = document.querySelector('#github-login');
const authStatus = document.querySelector('#auth-status');
const generatedAtLabel = document.querySelector('#projects-generated-at');

const VIEW_STATE = Object.freeze({
  LOADING: 'loading',
  AUTH_REQUIRED: 'auth-required',
  ERROR: 'error',
  EMPTY: 'empty',
  READY: 'ready',
  FILTERED_EMPTY: 'filtered-empty'
});

let serviceAccessToken = null;
let loadedProjects = [];
let loadedGeneratedAt = null;
let currentViewState = VIEW_STATE.LOADING;
let projectRequestSequence = 0;

window.observationPortalAuth = Object.freeze({
  setAccessToken(accessToken) {
    serviceAccessToken = normalizeAccessToken(accessToken);
    clearProjectSnapshot({ resetFilter: true });
    projectRequestSequence += 1;
    if (!serviceAccessToken) {
      renderAuthorizationRequired();
      return;
    }
    loadProjects();
  },
  clearAccessToken() {
    serviceAccessToken = null;
    clearProjectSnapshot({ resetFilter: true });
    projectRequestSequence += 1;
    renderAuthorizationRequired();
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
      serviceAccessToken = null;
      clearProjectSnapshot({ resetFilter: true });
      renderAuthorizationRequired();
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
  const applicationsLink = safeApplicationsLink(project);
  const recentConcern = project.recentConcern ? escapeText(project.recentConcern.label) : '최근 concern 없음';
  const issueCount = Number.isFinite(project.setupConnectionIssueCount) ? project.setupConnectionIssueCount : 0;
  const issueClass = issueCount > 0 ? 'badge attention' : 'badge';
  const actionState = applicationsLink ? 'pending-application-list' : 'missing-applications-link';
  const actionLabel = applicationsLink
    ? 'Application List는 후속 화면에서 인증 fetch로 연결됩니다.'
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
        <button class="link-button pending-action" type="button" disabled aria-disabled="true" data-action-state="${actionState}" data-applications-link="${escapeAttribute(applicationsLink ?? '')}" aria-label="${escapeAttribute(actionLabel)}" title="${escapeAttribute(actionLabel)}">Applications</button>
      </div>
    </article>
  `;
}

function safeApplicationsLink(project) {
  const applicationsLink = project.links && project.links.applications ? String(project.links.applications) : '';
  return isProjectApplicationsLink(applicationsLink, project.projectId) ? applicationsLink : null;
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

function setAuthStatus(message) {
  authStatus.textContent = message;
}

function setGeneratedAtLabel(message) {
  generatedAtLabel.textContent = message;
}

function setProjectViewState(nextState) {
  currentViewState = nextState;
  filterInput.disabled = !canRenderFilteredProjects();
}

function canRenderFilteredProjects() {
  return currentViewState === VIEW_STATE.READY || currentViewState === VIEW_STATE.FILTERED_EMPTY;
}

function handleFilterInput() {
  if (!canRenderFilteredProjects()) {
    return;
  }
  renderProjects();
}

function clearProjectSnapshot({ resetFilter = false } = {}) {
  loadedProjects = [];
  loadedGeneratedAt = null;
  if (resetFilter) {
    filterInput.value = '';
  }
}

function isLatestProjectRequest(requestId) {
  return requestId === projectRequestSequence;
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
githubButton.addEventListener('click', startGithubEntry);
renderAuthorizationRequired();
