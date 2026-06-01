#!/usr/bin/env bash
set -euo pipefail

# Story 7.3 read API flow를 service Bearer token으로 검증한다.
# .private/smoke-auth.env는 shell source하지 않고 단일 key/value 데이터로만 파싱한다.

auth_file=".private/smoke-auth.env"
project_key_file=".private/smoke-project.env"
portal_base_url="${OBSERVATION_PORTAL_BASE_URL:-http://localhost:8080}"
project_name="${OBSERVATION_SMOKE_PROJECT_NAME:-local-smoke}"
application_name="${OBSERVATION_SMOKE_APPLICATION_NAME:-observation-smoke-service}"
application_environment="${OBSERVATION_SMOKE_APPLICATION_ENVIRONMENT:-local-smoke}"
wait_seconds="${OBSERVATION_SMOKE_WAIT_SECONDS:-45}"
curl_connect_timeout="${OBSERVATION_SMOKE_CURL_CONNECT_TIMEOUT:-2}"
curl_max_time="${OBSERVATION_SMOKE_CURL_MAX_TIME:-10}"

fail() {
  printf '%s\n' "$1" >&2
  exit 1
}

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "$1 is required to verify the smoke portal flow."
  fi
}

validate_positive_duration() {
  local value="$1"
  local name="$2"

  if [[ ! "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]] \
      || ! awk -v value="${value}" 'BEGIN { exit !(value > 0) }'; then
    fail "${name} must be a positive curl timeout value."
  fi
}

is_service_access_token_shape() {
  local candidate="$1"

  [[ "${candidate}" =~ ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$ ]]
}

validate_project_key_value() {
  local value="$1"

  if [[ -z "${value}" || ! "${value}" =~ ^[A-Za-z0-9._~-]+\.[A-Za-z0-9._~-]+$ ]]; then
    fail 'OBSERVATION_SMOKE_PROJECT_KEY is missing or does not look like a raw project key.'
  fi
}

# starter project key는 smoke service의 X-OBS-Project-Key 경계에만 필요하다.
# 이 검사는 누락 troubleshooting만 제공하며 값을 source하거나 Bearer header에 섞지 않는다.
require_project_key_material() {
  if [[ -n "${OBSERVATION_SMOKE_PROJECT_KEY:-}" ]]; then
    validate_project_key_value "${OBSERVATION_SMOKE_PROJECT_KEY}"
    return
  fi

  if [[ ! -f "${project_key_file}" ]]; then
    fail 'Missing starter project key material: export OBSERVATION_SMOKE_PROJECT_KEY or create .private/smoke-project.env with OBSERVATION_SMOKE_PROJECT_KEY only.'
  fi

  local line_count
  line_count="$(awk 'END { print NR }' "${project_key_file}")"
  if [[ "${line_count}" != "1" ]]; then
    fail '.private/smoke-project.env must contain exactly one OBSERVATION_SMOKE_PROJECT_KEY line.'
  fi

  local project_key_line
  project_key_line="$(sed -n '1p' "${project_key_file}")"
  if [[ "${project_key_line}" != OBSERVATION_SMOKE_PROJECT_KEY=* ]]; then
    fail '.private/smoke-project.env must contain only OBSERVATION_SMOKE_PROJECT_KEY.'
  fi
  validate_project_key_value "${project_key_line#OBSERVATION_SMOKE_PROJECT_KEY=}"
}

parse_auth_token() {
  if [[ ! -f "${auth_file}" ]]; then
    fail 'Missing auth env file: .private/smoke-auth.env'
  fi

  local line_count
  line_count="$(awk 'END { print NR }' "${auth_file}")"
  if [[ "${line_count}" != "1" ]]; then
    fail '.private/smoke-auth.env must contain exactly one OBSERVATION_SMOKE_ACCESS_TOKEN line.'
  fi

  local auth_line
  auth_line="$(sed -n '1p' "${auth_file}")"
  if [[ "${auth_line}" =~ (OBSERVATION_SMOKE_REFRESH_TOKEN|refreshToken|providerAccessToken|provider_access_token|providerRawPayload|provider_raw_payload|GITHUB_PROVIDER_TOKEN|GITHUB_TOKEN|GH_TOKEN|github_pat_|gho_|client_id|client-id|clientId|client_secret|client-secret|clientSecret|portal\.auth\.github\.client-id|portal\.auth\.github\.client-secret) ]]; then
    fail '.private/smoke-auth.env contains forbidden token or OAuth credential material.'
  fi
  if [[ "${auth_line}" != OBSERVATION_SMOKE_ACCESS_TOKEN=* ]]; then
    fail '.private/smoke-auth.env must contain only OBSERVATION_SMOKE_ACCESS_TOKEN.'
  fi

  local token
  token="${auth_line#OBSERVATION_SMOKE_ACCESS_TOKEN=}"
  if ! is_service_access_token_shape "${token}"; then
    fail 'OBSERVATION_SMOKE_ACCESS_TOKEN must be a service access token with JWT-like three-segment shape.'
  fi
  printf '%s' "${token}"
}

request_json() {
  local path="$1"
  local output_file="$2"
  local label="$3"

  if [[ "${path}" != /* ]]; then
    fail "${label}: expected an API path link, not an absolute or malformed URL."
  fi

  local http_status
  if ! http_status="$(curl -sS --connect-timeout "${curl_connect_timeout}" --max-time "${curl_max_time}" \
      -o "${output_file}" -w '%{http_code}' \
      -H "Authorization: Bearer ${access_token}" \
      "${portal_base_url%/}${path}")"; then
    fail "${label}: portal request failed. Check OBSERVATION_PORTAL_BASE_URL and local portal status."
  fi

  case "${http_status}" in
    200)
      ;;
    401)
      fail "${label}: Bearer access token was rejected or expired. Refresh by repeating the GitHub OAuth login step."
      ;;
    404)
      fail "${label}: resource was not visible. Check active membership, project/application/instance ids, and accepted bucket timing."
      ;;
    *)
      fail "${label}: unexpected HTTP ${http_status}."
      ;;
  esac

  if ! jq -e 'type == "object"' "${output_file}" >/dev/null; then
    fail "${label}: response was not a JSON object."
  fi
}

require_tool jq
require_tool curl

if [[ ! "${wait_seconds}" =~ ^[0-9]+$ ]]; then
  fail 'OBSERVATION_SMOKE_WAIT_SECONDS must be a non-negative integer.'
fi
validate_positive_duration "${curl_connect_timeout}" "OBSERVATION_SMOKE_CURL_CONNECT_TIMEOUT"
validate_positive_duration "${curl_max_time}" "OBSERVATION_SMOKE_CURL_MAX_TIME"

access_token="$(parse_auth_token)"
require_project_key_material
work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

if [[ "${wait_seconds}" != "0" ]]; then
  printf 'Waiting %s seconds for bucket closure and starter scheduler cadence.\n' "${wait_seconds}"
  sleep "${wait_seconds}"
fi

projects_json="${work_dir}/projects.json"
request_json "/api/projects" "${projects_json}" "Project list"

applications_link="$(jq -r --arg name "${project_name}" '
    first(.projects[]? | select(.name == $name and (.links.applications | type == "string")) | .links.applications) // empty
  ' "${projects_json}")"
project_id="$(jq -r --arg name "${project_name}" '
    first(.projects[]? | select(.name == $name and (.projectId | type == "string")) | .projectId) // empty
  ' "${projects_json}")"

if [[ -z "${project_id}" || -z "${applications_link}" ]]; then
  fail 'Smoke project or applications link was not found in GET /api/projects response.'
fi

applications_json="${work_dir}/applications.json"
request_json "${applications_link}" "${applications_json}" "Application list"

dashboard_link="$(jq -r --arg name "${application_name}" --arg env "${application_environment}" '
    first(.applications[]? | select(.name == $name and .environment == $env and (.links.dashboard | type == "string")) | .links.dashboard) // empty
  ' "${applications_json}")"
application_id="$(jq -r --arg name "${application_name}" --arg env "${application_environment}" '
    first(.applications[]? | select(.name == $name and .environment == $env and (.applicationId | type == "string")) | .applicationId) // empty
  ' "${applications_json}")"

if [[ -z "${application_id}" || -z "${dashboard_link}" ]]; then
  fail 'Smoke application/environment or dashboard link was not found in Application List response.'
fi

dashboard_json="${work_dir}/dashboard.json"
request_json "${dashboard_link}" "${dashboard_json}" "Application dashboard"

if ! jq -e '
    (.application.lastAcceptedBucketAt | type == "string" and length > 0)
    and (.starterConnection.statusSource == "starter_heartbeat")
    and (.starterConnection.lastHeartbeatStatus == "received")
    and (.starterConnection.lastHeartbeatAt | type == "string" and length > 0)
    and (.starterConnection.stateImpact == "none")
    and ((.state.code as $state | ["waiting_first_data", "unknown", "idle", "active"] | index($state) != null))
    and (
      (.zeroInsight != null
        and (.zeroInsight.reasonCode as $reason
          | ["no_action_needed", "insufficient_sample", "waiting_first_data", "metric_data_idle", "telemetry_unreachable", "observing_recovery"]
          | index($reason) != null))
      or (.zeroInsight == null and ((.triageCards | type == "array") and (.triageCards | length <= 3)))
    )
  ' "${dashboard_json}" >/dev/null; then
  fail 'Dashboard response did not match accepted bucket plus starter heartbeat two-axis contract.'
fi

evidence_link="$(jq -r '
    first(.instances[]? | select(.links.evidence | type == "string") | .links.evidence) // empty
  ' "${dashboard_json}")"

if [[ -z "${evidence_link}" ]]; then
  fail 'Dashboard response did not include an instance evidence link. Check accepted bucket catalog/instance creation.'
fi

evidence_json="${work_dir}/evidence.json"
request_json "${evidence_link}" "${evidence_json}" "Instance evidence"

if ! jq -e '
    (.metricData.statusSource == "accepted_bucket")
    and (.starterConnection.statusSource == "starter_heartbeat")
    and (.starterConnection.lastHeartbeatStatus == "received")
    and (.starterConnection.lastHeartbeatAt | type == "string" and length > 0)
    and (.starterConnection.stateImpact == "none")
    and (.starterPercentiles.status as $status | ["missing", "insufficient", "available"] | index($status) != null)
  ' "${evidence_json}" >/dev/null; then
  fail 'Instance Evidence response did not match accepted bucket plus starter heartbeat two-axis contract.'
fi

printf 'Verified Project -> Application -> Dashboard -> Instance Evidence smoke portal flow.\n'
