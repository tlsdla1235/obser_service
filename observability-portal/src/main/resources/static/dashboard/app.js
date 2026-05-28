const projectList = document.querySelector('#project-list');
const reloadButton = document.querySelector('#reload-projects');
const githubButton = document.querySelector('#github-login');
const authStatus = document.querySelector('#auth-status');

let serviceAccessToken = null;

window.observationPortalAuth = Object.freeze({
  setAccessToken(accessToken) {
    serviceAccessToken = normalizeAccessToken(accessToken);
    loadProjects();
  },
  clearAccessToken() {
    serviceAccessToken = null;
  }
});

async function loadProjects() {
  projectList.innerHTML = '<div class="skeleton-line"></div><div class="skeleton-line short"></div>';
  try {
    const response = await fetch('/api/projects', { headers: projectRequestHeaders() });
    if (response.status === 401) {
      renderAuthorizationRequired();
      return;
    }
    if (!response.ok) {
      throw new Error('project_load_failed');
    }
    const data = await response.json();
    renderProjects(Array.isArray(data.projects) ? data.projects : []);
  } catch (error) {
    projectList.innerHTML = `
      <div class="empty-state">
        <p><strong>Project 목록을 불러오지 못했습니다.</strong>잠시 후 다시 시도해 주세요.</p>
      </div>
    `;
  }
}

function renderAuthorizationRequired() {
  projectList.innerHTML = `
    <div class="empty-state">
      <p><strong>GitHub 로그인 후 Project 목록을 볼 수 있습니다.</strong>로그인을 완료한 뒤 다시 시도해 주세요.</p>
    </div>
  `;
}

function renderProjects(projects) {
  if (projects.length === 0) {
    projectList.innerHTML = `
      <div class="empty-state">
        <p><strong>Project가 아직 없습니다.</strong>local/internal seed 또는 admin bootstrap decision이 필요합니다.</p>
      </div>
    `;
    return;
  }

  projectList.innerHTML = projects.map(project => projectMarkup(project)).join('');
}

function projectMarkup(project) {
  const applicationsLink = project.links && project.links.applications ? project.links.applications : '#';
  const recentConcern = project.recentConcern ? escapeText(project.recentConcern.label) : '최근 concern 없음';
  const issueCount = Number.isFinite(project.setupConnectionIssueCount) ? project.setupConnectionIssueCount : 0;
  const issueClass = issueCount > 0 ? 'badge warn' : 'badge';

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
        <span class="${issueClass}">Setup candidates ${escapeText(issueCount)}</span>
        <span class="badge">${recentConcern}</span>
      </div>
      <div class="project-actions">
        <a class="link-button" href="${escapeAttribute(applicationsLink)}">Applications</a>
      </div>
    </article>
  `;
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
  const text = String(value ?? '');
  if (!text.startsWith('/api/projects/')) {
    return '#';
  }
  return escapeText(text);
}

reloadButton.addEventListener('click', loadProjects);
githubButton.addEventListener('click', startGithubEntry);
loadProjects();
