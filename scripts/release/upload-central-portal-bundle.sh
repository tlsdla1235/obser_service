#!/usr/bin/env bash
set -euo pipefail

# Central Portal Publisher API에 deployment bundle을 업로드하고 검증 상태를 폴링한다.
# 인증 토큰은 header로만 전달하며, secret 원문이나 base64 토큰을 로그에 출력하지 않는다.

usage() {
  cat <<'USAGE'
Usage: upload-central-portal-bundle.sh [options] <bundle-zip-path>

Options:
  --name <deployment-name>                 Central Portal deployment name
  --publishing-type <USER_MANAGED|AUTOMATIC>
                                           USER_MANAGED는 검증 후 Portal UI에서 수동 publish,
                                           AUTOMATIC은 검증 성공 시 바로 Maven Central에 publish
  --poll-attempts <count>                  상태 확인 횟수, 기본 60
  --poll-interval-seconds <seconds>        상태 확인 간격, 기본 10

Environment:
  MAVEN_CENTRAL_USERNAME                   Central Portal user token username
  MAVEN_CENTRAL_PASSWORD                   Central Portal user token password
USAGE
}

deployment_name="observation-starter"
publishing_type="USER_MANAGED"
poll_attempts=60
poll_interval_seconds=10

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name)
      deployment_name="$2"
      shift 2
      ;;
    --publishing-type)
      publishing_type="$2"
      shift 2
      ;;
    --poll-attempts)
      poll_attempts="$2"
      shift 2
      ;;
    --poll-interval-seconds)
      poll_interval_seconds="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 64
      ;;
    *)
      break
      ;;
  esac
done

if [[ $# -ne 1 ]]; then
  usage >&2
  exit 64
fi

bundle_path="$1"

if [[ ! -f "${bundle_path}" ]]; then
  echo "Bundle file does not exist: ${bundle_path}" >&2
  exit 66
fi

case "${publishing_type}" in
  USER_MANAGED|AUTOMATIC) ;;
  *)
    echo "Invalid publishing type: ${publishing_type}" >&2
    exit 64
    ;;
esac

if [[ -z "${MAVEN_CENTRAL_USERNAME:-}" || -z "${MAVEN_CENTRAL_PASSWORD:-}" ]]; then
  echo "MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD are required." >&2
  exit 64
fi

urlencode() {
  python3 - "$1" <<'PY'
import sys
from urllib.parse import quote

print(quote(sys.argv[1], safe=""))
PY
}

json_field() {
  local field="$1"
  python3 -c '
import json
import sys

field = sys.argv[1]
payload = json.load(sys.stdin)
value = payload.get(field, "")
if isinstance(value, (dict, list)):
    print(json.dumps(value, ensure_ascii=False))
else:
    print(value)
' "$field"
}

auth_token="$(printf '%s:%s' "${MAVEN_CENTRAL_USERNAME}" "${MAVEN_CENTRAL_PASSWORD}" | base64 | tr -d '\n')"
encoded_name="$(urlencode "${deployment_name}")"
upload_url="https://central.sonatype.com/api/v1/publisher/upload?name=${encoded_name}&publishingType=${publishing_type}"

deployment_id="$(
  curl --fail-with-body --silent --show-error \
    --request POST \
    --header "Authorization: Bearer ${auth_token}" \
    --form "bundle=@${bundle_path};type=application/octet-stream" \
    "${upload_url}"
)"

if [[ -z "${deployment_id}" ]]; then
  echo "Central Portal did not return a deployment id." >&2
  exit 70
fi

echo "Central deployment id: ${deployment_id}" >&2

for ((attempt = 1; attempt <= poll_attempts; attempt++)); do
  status_json="$(
    curl --fail-with-body --silent --show-error \
      --request POST \
      --header "Authorization: Bearer ${auth_token}" \
      "https://central.sonatype.com/api/v1/publisher/status?id=${deployment_id}"
  )"
  deployment_state="$(printf '%s' "${status_json}" | json_field deploymentState)"
  echo "Central deployment state (${attempt}/${poll_attempts}): ${deployment_state}" >&2

  case "${deployment_state}" in
    VALIDATED)
      if [[ "${publishing_type}" == "USER_MANAGED" ]]; then
        printf '%s\n' "${deployment_id}"
        exit 0
      fi
      ;;
    PUBLISHED)
      printf '%s\n' "${deployment_id}"
      exit 0
      ;;
    FAILED)
      printf '%s\n' "${status_json}" >&2
      exit 70
      ;;
  esac

  sleep "${poll_interval_seconds}"
done

echo "Timed out waiting for Central deployment validation: ${deployment_id}" >&2
exit 70
