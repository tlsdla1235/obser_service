#!/usr/bin/env bash
# SSM Run Command 또는 운영자가 실행하는 단일 EC2 stop/start portal 배포 스크립트다.
# 새 jar 검증, 기존 jar 백업, systemd 재기동, server-local ready 확인, 실패 시 자동 롤백을 수행한다.
set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-observation.service}"
APP_DIR="${APP_DIR:-/opt/observation/current}"
RELEASE_DIR="${RELEASE_DIR:-/opt/observation/releases}"
APP_JAR="${APP_JAR:-$APP_DIR/app.jar}"
READY_URL="${READY_URL:-http://127.0.0.1:8080/internal/health/ready}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-2}"
ARTIFACT_SHA256="${ARTIFACT_SHA256:?ARTIFACT_SHA256 is required}"
DEPLOY_COMMIT_SHA="${DEPLOY_COMMIT_SHA:-unknown}"
DEPLOY_SOURCE="${DEPLOY_SOURCE:-manual}"
ARTIFACT_S3_URI="${ARTIFACT_S3_URI:-}"
ARTIFACT_LOCAL_PATH="${ARTIFACT_LOCAL_PATH:-}"

if [[ "$EUID" -ne 0 ]]; then
  echo "observation-deploy-portal must run as root" >&2
  exit 1
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
safe_sha="${DEPLOY_COMMIT_SHA//[^A-Za-z0-9._-]/_}"
safe_sha="${safe_sha:0:40}"
tmp_dir="$(mktemp -d /tmp/observation-deploy.XXXXXX)"
candidate_jar="$tmp_dir/app.jar"
backup_jar="$RELEASE_DIR/app-${timestamp}-${safe_sha}.jar"
rollback_enabled=0

cleanup() {
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

wait_ready() {
  for _ in $(seq 1 "$HEALTH_RETRIES"); do
    if curl -fsS -o /dev/null "$READY_URL"; then
      return 0
    fi
    sleep "$HEALTH_INTERVAL_SECONDS"
  done
  return 1
}

rollback_to_backup() {
  echo "deploy failed; rolling back to $backup_jar" >&2
  systemctl stop "$SERVICE_NAME" || true
  install -o appuser -g appuser -m 0644 "$backup_jar" "$APP_JAR"
  systemctl start "$SERVICE_NAME"
  wait_ready
}

on_error() {
  exit_code=$?
  if [[ "$rollback_enabled" -eq 1 && -f "$backup_jar" ]]; then
    rollback_to_backup || echo "rollback failed; inspect journalctl -u $SERVICE_NAME" >&2
  fi
  exit "$exit_code"
}
trap on_error ERR

install -d -o appuser -g appuser -m 0755 "$APP_DIR" "$RELEASE_DIR"

if [[ -n "$ARTIFACT_LOCAL_PATH" ]]; then
  cp "$ARTIFACT_LOCAL_PATH" "$candidate_jar"
elif [[ -n "$ARTIFACT_S3_URI" ]]; then
  aws s3 cp "$ARTIFACT_S3_URI" "$candidate_jar" --only-show-errors
else
  echo "ARTIFACT_S3_URI or ARTIFACT_LOCAL_PATH is required" >&2
  exit 1
fi

actual_sha256="$(sha256sum "$candidate_jar" | awk '{print $1}')"
if [[ "$actual_sha256" != "$ARTIFACT_SHA256" ]]; then
  echo "artifact sha256 mismatch" >&2
  exit 1
fi

if [[ ! -f "$APP_JAR" ]]; then
  echo "current app jar not found: $APP_JAR" >&2
  exit 1
fi

cp -a "$APP_JAR" "$backup_jar"
chown appuser:appuser "$backup_jar"
chmod 0644 "$backup_jar"

rollback_enabled=1
systemctl stop "$SERVICE_NAME"
install -o appuser -g appuser -m 0644 "$candidate_jar" "$APP_JAR"
rm -f "$APP_DIR/app.pid"
systemctl start "$SERVICE_NAME"
wait_ready
rollback_enabled=0

cat > "$APP_DIR/deploy-metadata.env" <<METADATA
DEPLOYED_AT_UTC=${timestamp}
DEPLOY_COMMIT_SHA=${DEPLOY_COMMIT_SHA}
DEPLOY_ARTIFACT_SHA256=${actual_sha256}
DEPLOY_SOURCE=${DEPLOY_SOURCE}
BACKUP_JAR=${backup_jar}
METADATA
chown appuser:appuser "$APP_DIR/deploy-metadata.env"
chmod 0644 "$APP_DIR/deploy-metadata.env"

echo "deploy succeeded"
echo "commit_sha=$DEPLOY_COMMIT_SHA"
echo "artifact_sha256=$actual_sha256"
echo "backup_jar=$backup_jar"
