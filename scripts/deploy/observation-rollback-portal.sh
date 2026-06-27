#!/usr/bin/env bash
# /opt/observation/releases 아래의 백업 jar를 /opt/observation/current/app.jar로 복구한다.
# 기본값은 가장 최신 backup이며, 복구 후 server-local ready health check까지 확인한다.
set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-observation.service}"
APP_DIR="${APP_DIR:-/opt/observation/current}"
RELEASE_DIR="${RELEASE_DIR:-/opt/observation/releases}"
APP_JAR="${APP_JAR:-$APP_DIR/app.jar}"
READY_URL="${READY_URL:-http://127.0.0.1:8080/internal/health/ready}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-2}"
REQUESTED_BACKUP="${1:-}"

if [[ "$EUID" -ne 0 ]]; then
  echo "observation-rollback-portal must run as root" >&2
  exit 1
fi

wait_ready() {
  for _ in $(seq 1 "$HEALTH_RETRIES"); do
    if curl -fsS -o /dev/null "$READY_URL"; then
      return 0
    fi
    sleep "$HEALTH_INTERVAL_SECONDS"
  done
  return 1
}

if [[ -n "$REQUESTED_BACKUP" ]]; then
  backup_jar="$REQUESTED_BACKUP"
else
  backup_jar="$(find "$RELEASE_DIR" -maxdepth 1 -type f -name 'app-*.jar' -print | sort -r | head -n 1)"
fi

if [[ -z "$backup_jar" || ! -f "$backup_jar" ]]; then
  echo "backup jar not found" >&2
  exit 1
fi

case "$backup_jar" in
  "$RELEASE_DIR"/app-*.jar) ;;
  *)
    echo "backup jar must be under $RELEASE_DIR" >&2
    exit 1
    ;;
esac

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
if [[ -f "$APP_JAR" ]]; then
  cp -a "$APP_JAR" "$RELEASE_DIR/rollback-replaced-${timestamp}.jar"
fi

systemctl stop "$SERVICE_NAME"
install -o appuser -g appuser -m 0644 "$backup_jar" "$APP_JAR"
rm -f "$APP_DIR/app.pid"
systemctl start "$SERVICE_NAME"
wait_ready

cat > "$APP_DIR/deploy-metadata.env" <<METADATA
DEPLOYED_AT_UTC=${timestamp}
DEPLOY_SOURCE=manual-rollback
ROLLBACK_JAR=${backup_jar}
METADATA
chown appuser:appuser "$APP_DIR/deploy-metadata.env"
chmod 0644 "$APP_DIR/deploy-metadata.env"

echo "rollback succeeded"
echo "rollback_jar=$backup_jar"
