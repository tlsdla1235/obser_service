#!/usr/bin/env bash
# systemd 전환 전에 남아 있을 수 있는 수동 portal java 프로세스를 종료한다.
# observation.service의 MainPID는 건드리지 않아 재실행해도 systemd 관리 프로세스를 보존한다.
set -euo pipefail

MATCH_PATTERN="${OBSERVATION_MANUAL_PATTERN:-java -jar /opt/observation/current/app.jar}"
STOP_TIMEOUT_SECONDS="${STOP_TIMEOUT_SECONDS:-30}"
PID_FILE="${PID_FILE:-/opt/observation/current/app.pid}"

systemd_main_pid="$(systemctl show -p MainPID --value observation.service 2>/dev/null || true)"

mapfile -t candidate_pids < <(pgrep -f "$MATCH_PATTERN" || true)
if [[ "${#candidate_pids[@]}" -eq 0 ]]; then
  rm -f "$PID_FILE" 2>/dev/null || true
  exit 0
fi

for pid in "${candidate_pids[@]}"; do
  if [[ -n "$systemd_main_pid" && "$systemd_main_pid" != "0" && "$pid" == "$systemd_main_pid" ]]; then
    continue
  fi

  if ! kill -0 "$pid" 2>/dev/null; then
    continue
  fi

  kill -TERM "$pid"
done

deadline=$((SECONDS + STOP_TIMEOUT_SECONDS))
while [[ "$SECONDS" -lt "$deadline" ]]; do
  remaining=0
  for pid in "${candidate_pids[@]}"; do
    if [[ -n "$systemd_main_pid" && "$systemd_main_pid" != "0" && "$pid" == "$systemd_main_pid" ]]; then
      continue
    fi
    if kill -0 "$pid" 2>/dev/null; then
      remaining=1
      break
    fi
  done

  if [[ "$remaining" -eq 0 ]]; then
    rm -f "$PID_FILE" 2>/dev/null || true
    exit 0
  fi

  sleep 1
done

for pid in "${candidate_pids[@]}"; do
  if [[ -n "$systemd_main_pid" && "$systemd_main_pid" != "0" && "$pid" == "$systemd_main_pid" ]]; then
    continue
  fi
  kill -KILL "$pid" 2>/dev/null || true
done

rm -f "$PID_FILE" 2>/dev/null || true
