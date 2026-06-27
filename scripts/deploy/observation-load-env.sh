#!/usr/bin/env bash
# AWS SSM Parameter Store의 운영 값을 systemd EnvironmentFile로 동기화한다.
# 비밀값은 표준 출력에 남기지 않고 root 전용 파일에만 shell-safe 형식으로 기록한다.
set -euo pipefail

SSM_PATH="${SSM_PATH:-/observation/prod/}"
ENV_FILE="${ENV_FILE:-/etc/observation/observation.env}"
AWS_REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-ap-northeast-2}}"
AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-$AWS_REGION}"
export AWS_REGION AWS_DEFAULT_REGION

tmp_file="$(mktemp)"
cleanup() {
  rm -f "$tmp_file"
}
trap cleanup EXIT

install -d -o root -g root -m 0750 "$(dirname "$ENV_FILE")"

aws ssm get-parameters-by-path \
  --path "$SSM_PATH" \
  --recursive \
  --with-decryption \
  --output json \
  | jq -r '
      .Parameters
      | sort_by(.Name)
      | .[]
      | (.Name | split("/")[-1]) as $key
      | select($key | test("^[A-Z_][A-Z0-9_]*$"))
      | "\($key)=\(.Value | @sh)"
    ' > "$tmp_file"

if [[ ! -s "$tmp_file" ]]; then
  echo "SSM path has no exportable parameters: $SSM_PATH" >&2
  exit 1
fi

chmod 0600 "$tmp_file"
chown root:root "$tmp_file"
install -o root -g root -m 0600 "$tmp_file" "$ENV_FILE"
