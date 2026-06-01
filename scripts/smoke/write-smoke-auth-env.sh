#!/usr/bin/env bash
set -euo pipefail

# callback JSON에서 복사한 portal service accessToken만 .private/smoke-auth.env에 저장한다.
# token 값은 stdout/stderr에 출력하지 않고, 파일은 가능한 범위에서 owner-only 권한으로 만든다.

target_file=".private/smoke-auth.env"
target_dir=".private"

reject_input() {
  printf '%s\n' "$1" >&2
  exit 1
}

# .private/smoke-auth.env는 shell에서 source하지 않고도 읽을 수 있는 단일 key/value memo만 허용한다.
validate_access_token() {
  local candidate="$1"

  if [[ -z "${candidate//[[:space:]]/}" ]]; then
    reject_input 'accessToken input is required.'
  fi
  if [[ "${candidate}" == *$'\n'* || "${candidate}" == *$'\r'* ]]; then
    reject_input 'Paste only the service accessToken value, not multiple lines.'
  fi
  if [[ "${candidate}" == *'{'* || "${candidate}" == *'}'* || "${candidate}" == *accessToken* ]]; then
    reject_input 'Paste only the service accessToken value, not callback JSON.'
  fi
  if [[ "${candidate}" =~ (refreshToken|OBSERVATION_SMOKE_REFRESH_TOKEN|providerAccessToken|provider_access_token|providerRawPayload|provider_raw_payload|GITHUB_PROVIDER_TOKEN|GITHUB_TOKEN|GH_TOKEN|github_pat_|gho_|client_id|client-id|clientId|client_secret|client-secret|clientSecret|portal\.auth\.github\.client-id|portal\.auth\.github\.client-secret) ]]; then
    reject_input 'Input contains forbidden token or OAuth credential material.'
  fi
  if [[ ! "${candidate}" =~ ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$ ]]; then
    reject_input 'accessToken must be a service access token with JWT-like three-segment shape.'
  fi
}

if [[ "$#" -ne 0 ]]; then
  reject_input 'write-smoke-auth-env.sh always writes .private/smoke-auth.env and accepts no target path argument.'
fi

printf 'Paste portal service accessToken, then press Enter: ' >&2
if [[ -t 0 ]]; then
  IFS= read -r access_token
else
  access_token="$(cat)"
fi
validate_access_token "${access_token}"

mkdir -p "${target_dir}"
umask 077
tmp_file="$(mktemp "${target_dir}/smoke-auth.env.XXXXXX")"
trap 'rm -f "${tmp_file}"' EXIT
chmod 600 "${tmp_file}"
printf 'OBSERVATION_SMOKE_ACCESS_TOKEN=%s\n' "${access_token}" > "${tmp_file}"
mv "${tmp_file}" "${target_file}"
trap - EXIT
chmod 600 "${target_file}"

printf 'Wrote .private/smoke-auth.env with OBSERVATION_SMOKE_ACCESS_TOKEN only.\n' >&2
