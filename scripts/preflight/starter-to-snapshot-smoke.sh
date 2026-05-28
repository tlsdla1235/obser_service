#!/usr/bin/env bash

# Story 6.0 starter-to-snapshot preflight를 사람이 명시적으로 실행할 때 쓰는 opt-in runner다.
# raw project key는 E2E 테스트 내부에서 런타임 생성되며, 이 스크립트는 secret 값을 받거나 출력하지 않는다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

echo "[starter-to-snapshot] focused starter auto-configuration smoke"
./gradlew -p smoke-tests/starter-to-snapshot --rerun-tasks test \
  --tests com.observation.smoke.startertosnapshot.StarterAutoConfigurationRequiredBeansSmokeTest

echo "[starter-to-snapshot] portal dashboard axis separation E2E"
./gradlew -p smoke-tests/starter-to-snapshot --rerun-tasks portalE2eTest \
  --tests com.observation.smoke.startertosnapshot.StarterToPortalDashboardAxisSeparationE2ETest
