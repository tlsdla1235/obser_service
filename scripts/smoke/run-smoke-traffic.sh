#!/usr/bin/env bash
set -euo pipefail

# local smoke service의 /smoke/ok endpoint로 green-path traffic을 만든다.
# response body 전체나 secret 값을 출력하지 않고, JSON shape와 HTTP status만 확인한다.

smoke_base_url="${OBSERVATION_SMOKE_SERVICE_BASE_URL:-http://localhost:8081}"
traffic_count="${OBSERVATION_SMOKE_TRAFFIC_COUNT:-12}"
project_key_file=".private/smoke-project.env"
curl_connect_timeout="${OBSERVATION_SMOKE_CURL_CONNECT_TIMEOUT:-2}"
curl_max_time="${OBSERVATION_SMOKE_CURL_MAX_TIME:-10}"

fail() {
  printf '%s\n' "$1" >&2
  exit 1
}

validate_positive_duration() {
  local value="$1"
  local name="$2"

  if [[ ! "${value}" =~ ^[0-9]+([.][0-9]+)?$ ]] \
      || ! awk -v value="${value}" 'BEGIN { exit !(value > 0) }'; then
    fail "${name} must be a positive curl timeout value."
  fi
}

validate_project_key_value() {
  local value="$1"

  if [[ -z "${value}" || ! "${value}" =~ ^[A-Za-z0-9._~-]+\.[A-Za-z0-9._~-]+$ ]]; then
    fail 'OBSERVATION_SMOKE_PROJECT_KEY is missing or does not look like a raw project key.'
  fi
}

# smoke service가 starter X-OBS-Project-Key 경계로 쓸 raw project key 준비 여부만 확인한다.
# 이 스크립트는 값을 source하거나 출력하지 않고, Bearer token으로도 사용하지 않는다.
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

if ! command -v jq >/dev/null 2>&1; then
  fail 'jq is required to verify smoke traffic response shape.'
fi

if ! command -v curl >/dev/null 2>&1; then
  fail 'curl is required to generate smoke traffic.'
fi

if [[ ! "${traffic_count}" =~ ^[1-9][0-9]*$ ]]; then
  fail 'OBSERVATION_SMOKE_TRAFFIC_COUNT must be a positive integer.'
fi
validate_positive_duration "${curl_connect_timeout}" "OBSERVATION_SMOKE_CURL_CONNECT_TIMEOUT"
validate_positive_duration "${curl_max_time}" "OBSERVATION_SMOKE_CURL_MAX_TIME"
require_project_key_material

response_file="$(mktemp)"
trap 'rm -f "${response_file}"' EXIT
endpoint="${smoke_base_url%/}/smoke/ok"

for _ in $(seq 1 "${traffic_count}"); do
  if ! http_status="$(curl -sS --connect-timeout "${curl_connect_timeout}" --max-time "${curl_max_time}" \
      -o "${response_file}" -w '%{http_code}' "${endpoint}")"; then
    fail 'Smoke service /smoke/ok request failed. Check OBSERVATION_SMOKE_SERVICE_BASE_URL and service status.'
  fi

  if [[ "${http_status}" != "200" ]]; then
    fail "Smoke service /smoke/ok returned HTTP ${http_status}; expected 200."
  fi

  if ! jq -e '.status == "ok" and .path == "/smoke/ok"' "${response_file}" >/dev/null; then
    fail 'Smoke service /smoke/ok response shape did not match the expected bounded smoke response.'
  fi
done

printf 'Generated %s /smoke/ok requests against the smoke service.\n' "${traffic_count}"
