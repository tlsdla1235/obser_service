# Starter-to-snapshot preflight smoke

이 fixture는 Story 6.0 starter-to-snapshot preflight를 위한 독립 Spring Boot 사용자 앱이다.

루트 `settings.gradle`의 product module로 포함하지 않는 독립 Gradle build로 두어 기본 `./gradlew test`가 heavy preflight를 항상 실행하지 않게 한다. 앱 코드에는 `PortalMetricBucketClient`, `MicrometerHttpServerObservationBinder`, `ObservationHandler`, `ObservationRegistry` 사용자 bean을 추가하지 않는다.

## Opt-in 실행

전체 preflight runner:

```bash
./scripts/preflight/starter-to-snapshot-smoke.sh
```

runner는 아래 두 명령을 순서대로 실행한다.

```bash
./gradlew -p smoke-tests/starter-to-snapshot --rerun-tasks test --tests com.observation.smoke.startertosnapshot.StarterAutoConfigurationRequiredBeansSmokeTest
./gradlew -p smoke-tests/starter-to-snapshot --rerun-tasks portalE2eTest --tests com.observation.smoke.startertosnapshot.StarterToPortalDashboardAxisSeparationE2ETest
```

Checkpoint 1에서는 starter auto-configuration만으로 아래 runtime wiring이 생성되지 않는 RED를 확인했다. Checkpoint 2에서는 smoke app을 바꾸지 않고 production starter auto-configuration 보강만으로 이 focused test가 GREEN이어야 한다.

- `PortalMetricBucketClient` bean
- `MicrometerHttpServerObservationBinder` runtime `ObservationHandler` registration

## 전제 조건

- Java 17 toolchain과 Gradle wrapper를 사용한다.
- `portalE2eTest`는 Testcontainers PostgreSQL을 사용하므로 Docker가 실행 중이어야 한다.
- 이 fixture는 composite build로 root project의 starter/portal module을 참조하지만, root `settings.gradle`에는 include하지 않는다.

## 실패 지점 해석

- focused starter smoke가 실패하면 사용자 앱 custom bean 없이 starter auto-configuration이 필요한 runtime bean을 만들지 못한 것이다.
- portal E2E의 heartbeat persistence 실패는 starter heartbeat 송신 또는 portal heartbeat ingest 경로를 먼저 본다.
- accepted bucket persistence 실패는 HTTP observation capture, bucket close/drain, portal bucket ingest 경로를 먼저 본다.
- dashboard current 또는 snapshot assertion 실패는 accepted bucket metric axis와 starter heartbeat connection axis 분리 계약을 먼저 본다.

## Secret 취급

- raw project key는 테스트 런타임에만 생성하고 tracked config, log, exception, response body, snapshot JSON에 남기지 않는다.
- runner는 secret 값을 인자로 받거나 echo하지 않는다.
- local 수동 실험이 필요하면 `.private/` 또는 환경 변수에만 secret을 두고 Git 추적 파일에는 placeholder만 둔다.
