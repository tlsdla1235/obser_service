#!/usr/bin/env bash
set -euo pipefail

# .private/smoke-auth.env의 service access token으로 GET /api/projects를 검증한다.
# Bearer 값은 명령 출력에 남기지 않고, 응답 body는 project visibility 확인에만 사용한다.

auth_file=".private/smoke-auth.env"
portal_base_url="${OBSERVATION_PORTAL_BASE_URL:-http://localhost:8080}"
expected_project_name="${OBSERVATION_SMOKE_PROJECT_NAME:-local-smoke}"

fail() {
  printf '%s\n' "$1" >&2
  exit 1
}

if [[ ! -f "${auth_file}" ]]; then
  fail 'Missing auth env file: .private/smoke-auth.env'
fi

if ! command -v jq >/dev/null 2>&1; then
  fail 'jq is required to verify GET /api/projects response shape.'
fi

auth_line_count="$(awk 'END { print NR }' "${auth_file}")"
if [[ "${auth_line_count}" != "1" ]]; then
  fail '.private/smoke-auth.env must contain exactly one OBSERVATION_SMOKE_ACCESS_TOKEN line.'
fi

auth_line="$(sed -n '1p' "${auth_file}")"
if [[ "${auth_line}" =~ (OBSERVATION_SMOKE_REFRESH_TOKEN|refreshToken|providerAccessToken|provider_access_token|providerRawPayload|provider_raw_payload|GITHUB_PROVIDER_TOKEN|GITHUB_TOKEN|GH_TOKEN|github_pat_|gho_|client_id|client-id|clientId|client_secret|client-secret|clientSecret|portal\.auth\.github\.client-id|portal\.auth\.github\.client-secret) ]]; then
  fail '.private/smoke-auth.env contains forbidden token or OAuth credential material.'
fi
if [[ "${auth_line}" != OBSERVATION_SMOKE_ACCESS_TOKEN=* ]]; then
  fail '.private/smoke-auth.env must contain only OBSERVATION_SMOKE_ACCESS_TOKEN.'
fi

access_token="${auth_line#OBSERVATION_SMOKE_ACCESS_TOKEN=}"
if [[ ! "${access_token}" =~ ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$ ]]; then
  fail 'OBSERVATION_SMOKE_ACCESS_TOKEN must be a service access token with JWT-like three-segment shape.'
fi

response_file="$(mktemp)"
trap 'rm -f "${response_file}"' EXIT
http_status="$(curl -sS -o "${response_file}" -w '%{http_code}' \
  -H "Authorization: Bearer ${access_token}" \
  "${portal_base_url%/}/api/projects")"

if [[ "${http_status}" != "200" ]]; then
  fail "GET /api/projects failed with HTTP ${http_status}."
fi

if ! jq -e --arg name "${expected_project_name}" '
    (.projects | type == "array")
    and any(.projects[]; .name == $name
      and (.projectId | type == "string" and length > 0)
      and (.links.applications == ("/api/projects/" + .projectId + "/applications")))
  ' "${response_file}" >/dev/null; then
  fail 'Smoke project or matching applications link was not found in GET /api/projects response.'
fi

printf 'GET /api/projects returned the smoke project and applications link.\n'
