# Observation Portal

Spring Boot 애플리케이션에 관측용 starter를 붙이면, 애플리케이션 지표와 연결 상태를 수집해 운영자가 첫 화면에서 “지금 데이터가 들어오는지, 어떤 API부터 봐야 하는지” 판단할 수 있게 돕는 observability dashboard.

## 문제 정의

작은 서비스나 팀에서는 Prometheus, Grafana, APM 전체 구성을 한 번에 갖추기보다, Spring Boot 애플리케이션에 최소 설정을 붙여 운영 첫 화면을 빠르게 확보하는 일이 먼저 필요할 때가 많습니다.

Observation Portal은 이 문제를 starter-first 방식으로 풀었습니다. 호스트 애플리케이션은 관측용 Spring Boot starter를 통해 30초 단위 애플리케이션 지표와 연결 상태 신호를 포털로 보내고, 포털은 이를 검증, 저장, 집계해 운영자가 바로 판단할 수 있는 대시보드 데이터를 제공합니다.

## 주요 기능

- Spring Boot starter 기반 애플리케이션 지표 수집
- request path와 DB persistence path를 분리한 SQS buffered ingest
- 애플리케이션, 인스턴스, API 엔드포인트 단위 상태 확인
- 지표 수집 상태와 애플리케이션 연결 상태를 분리해서 표시
- p95/p99 latency, error rate, endpoint priority 등 서버 계산 결과 표시
- GitHub OAuth 기반 프로젝트 접근
- starter credential 생성, 회전, 폐기 흐름
- 생성/회전 직후 starter credential 원문 1회 표시
- snapshot/history 기반 최근 상태 변화 확인
- smoke service와 ECC endpoint-shaped traffic을 통한 실제 HTTP route 관측 경로 검증

## 아키텍처

```mermaid
flowchart LR
  Host["Spring Boot 애플리케이션"] --> Starter["observability-spring-boot-starter"]
  Starter --> Metrics["30초 단위 애플리케이션 지표<br/>(accepted metric bucket)"]
  Starter --> Heartbeat["애플리케이션 연결 상태 신호<br/>(starter heartbeat)"]
  Metrics --> Portal["Portal backend<br/>검증 / enqueue 또는 저장 / 집계"]
  Heartbeat --> Portal
  Portal -->|direct mode| Db["PostgreSQL<br/>Flyway schema"]
  Portal -->|buffered mode| Queue["SQS buffered queue<br/>(metric ingest buffer)"]
  Queue --> Worker["Portal 내부 worker<br/>bounded batch persistence"]
  Worker --> Db
  Portal --> Api["Dashboard API<br/>서버 계산 대시보드 데이터<br/>(read model)"]
  Api --> Frontend["React dashboard<br/>계산 결과 표시"]
  Smoke["smoke services"] --> Starter
```

- Spring Boot starter가 Micrometer observation을 기반으로 애플리케이션 지표를 모읍니다.
- Portal backend가 수집 데이터를 검증하고 PostgreSQL에 저장한 뒤, 운영 판단에 필요한 형태로 집계합니다.
- SQS buffered ingest mode에서는 request thread가 검증과 enqueue까지만 수행하고 `202 queued`를 반환하며, PostgreSQL 저장은 Spring Boot portal 내부 worker가 처리합니다.
- Dashboard API가 상태, 신선도, p95/p99, triage, endpoint priority, snapshot/history 데이터를 계산해서 제공합니다.
- Snapshot/history는 저장 당시 dashboard read model을 읽습니다. 새 scheduled/fallback snapshot 저장은 accepted bucket 이력과 최근 starter heartbeat가 함께 있을 때만 허용하며, heartbeat가 없거나 오래된 query fallback은 저장만 건너뛰고 current dashboard response는 계속 성공합니다.
- Starter heartbeat는 연결 상태 신호와 snapshot 저장 gate로만 사용하며, metric freshness/state/read model source로 합성하지 않습니다.
- Frontend는 서버가 계산한 결과를 재계산하지 않고 표시합니다.
- Smoke service가 Spring MVC 요청 경로를 실제로 태워 starter의 HTTP route 관측 경로를 검증합니다.

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| Backend | Java 17, Spring Boot, Spring MVC, JPA, Flyway, PostgreSQL, Micrometer |
| Frontend | React, TypeScript, Vite, Tailwind/shadcn 스타일 UI |
| Test/Verification | JUnit, MockMvc, Testcontainers, LocalStack SQS, Gradle, smoke scripts |

## 실행 방법

프론트엔드는 Vite SPA로 구성되어 있으며, 타입 검사와 빌드로 정적 자산을 확인할 수 있습니다.

```bash
npm --prefix frontend ci
npm --prefix frontend run typecheck
npm --prefix frontend run build
```

포털 백엔드는 PostgreSQL, GitHub OAuth, 토큰 서명 키 등 필요한 local secret을 준비한 뒤 Gradle로 실행하거나 bootJar를 만들 수 있습니다. 현재 로컬 값은 `local` profile 전용으로 유지하고, CI와 운영은 환경변수/SSM에서 같은 key 이름으로 주입합니다.

- `.env`: shell에서 export할 AWS/SQS와 환경변수 예시 값
- `.private/github-oauth.properties`: `local` profile에서만 Spring Boot가 import하는 GitHub OAuth, datasource, service token, OAuth state signing key
- `.private/smoke-seed.properties`: `local` profile에서만 Spring Boot가 import하는 smoke seed 설정
- `.private/smoke-auth.env`, `.private/smoke-project.env`: smoke script가 단일 key/value로 파싱하는 access token과 starter project key

이 분리는 AWS credential, OAuth secret, smoke token, raw project key가 서로 다른 실행 경로에 섞이지 않게 하기 위한 local-only guard입니다. 설정 key 전체 표는 [config matrix](docs/cicd-deployment-plan/config-matrix.md)를 기준으로 관리합니다.

환경 profile은 명시적으로 선택합니다.

| 환경 | 실행 profile | 값 주입 위치 |
|---|---|---|
| local | `SPRING_PROFILES_ACTIVE=local` | `.private/*`, `.env` |
| ci | `SPRING_PROFILES_ACTIVE=ci` | GitHub Secrets / workflow env |
| prod | `SPRING_PROFILES_ACTIVE=prod` | AWS SSM Parameter Store / systemd env |

운영 profile에서는 OAuth client secret, datasource password, service token signing key, OAuth state signing key 등 필수값이 비어 있으면 application startup이 실패합니다. 실패 메시지는 key 이름만 출력하고 secret 원문은 출력하지 않습니다.

`.env`를 사용하는 SQS buffered ingest 실행은 shell에서 값을 export한 뒤 실행합니다.

```bash
set -a
source .env
set +a
```

```bash
./gradlew test
./gradlew :observability-portal:bootJar
./gradlew :observability-portal:bootRun --args='--spring.profiles.active=local'
```

starter가 붙은 smoke service는 starter credential 환경 변수를 설정한 뒤 실행합니다.

```bash
OBSERVATION_SMOKE_PROJECT_KEY='<starter credential>' \
./gradlew :observability-smoke-service:bootRun --args='--spring.profiles.active=local-smoke'
```

ECC endpoint-shaped traffic 검증용 서비스도 별도 프로필로 실행할 수 있습니다.

```bash
ECC_ENDPOINT_SMOKE_PROJECT_KEY='<starter credential>' \
./gradlew :ecc-endpoint-smoke-service:bootRun --args='--spring.profiles.active=local-ecc'
```

서비스가 떠 있으면 polling script로 다양한 HTTP route 호출을 생성합니다.

```bash
scripts/smoke/run-ecc-endpoint-polling.py
```

snapshot QA처럼 30분마다 다른 상태 신호를 만들고 싶을 때는 2시간 시나리오 모드를 사용합니다.
이 모드는 healthy, error spike, latency spike, error+latency slot을 순서대로 만들며, 지연 응답은
ECC smoke 서버가 의도적으로 늦게 응답해 starter duration bucket과 local p95/p99에 반영되게 합니다.

```bash
scripts/smoke/run-ecc-endpoint-polling.py \
  --scenario-plan snapshot-2h \
  --align-to-half-hour \
  --duration-seconds 7200 \
  --slot-seconds 1800 \
  --interval-seconds 5
```

## 검증 방법

대표 검증 명령은 아래와 같습니다.

```bash
npm --prefix frontend run typecheck
npm --prefix frontend run build
./gradlew test
./gradlew :ecc-endpoint-smoke-service:test
scripts/smoke/run-ecc-endpoint-polling.py
```

검증 범위는 프론트엔드 타입/빌드, Spring MVC controller, service/repository, Flyway 기반 PostgreSQL integration, dashboard read model, starter boundary, smoke traffic까지 이어집니다.

## SQS Buffered Ingest 전환과 benchmark evidence

기존 ingest는 HTTP request thread가 metric bucket 검증뿐 아니라 `accepted_metric_buckets` DB insert까지 수행했습니다. SQS buffered ingest는 이 경로를 request path와 persistence path로 분리합니다.

Buffered mode에서 request thread는 project key와 payload를 검증하고 LocalStack SQS enqueue가 성공하면 `202 queued`를 반환합니다. 이 응답은 DB 저장 완료, dashboard freshness current, snapshot 반영 완료를 뜻하지 않습니다. DB 저장은 Spring Boot portal 내부 worker가 queue를 poll한 뒤 bounded batch로 수행합니다.

로컬 검증은 같은 코드 경로에서 `DIRECT_DB`와 `LOCALSTACK_SQS`를 비교했습니다. 환경은 macOS arm64, Docker, Testcontainers PostgreSQL `postgres:16-alpine`, LocalStack SQS `localstack/localstack:4.8.1`입니다. 아래 수치는 request-path acceptance latency와 worker drain 이후 최종 저장 정합성을 보기 위한 로컬 evidence이며, AWS 운영 환경 latency, autoscaling, 비용, dashboard UI 성능을 보장하지 않습니다.

| Scenario | Mode | Requests | Error | p50 ms | p95 ms | p99 ms | Request-thread DB writes | Persisted | Drain sec |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 30 synthetic instances, concurrency 30 | Direct DB | 3,000 | 0% | 27.648 | 42.776 | 52.882 | 3,000 | 100% | 0.000 |
| 30 synthetic instances, concurrency 30 | LocalStack SQS | 3,000 | 0% | 23.668 | 41.839 | 63.271 | 0 | 100% | 11.712 |
| 100 synthetic instances, concurrency 100 | Direct DB | 10,000 | 0% | 86.873 | 132.491 | 278.518 | 10,000 | 100% | 0.000 |
| 100 synthetic instances, concurrency 100 | LocalStack SQS | 10,000 | 0% | 69.209 | 119.177 | 136.400 | 0 | 100% | 34.735 |

30-instance 실행에서는 request thread DB write가 제거되고 최종 저장률 100%를 확인했지만, p99는 개선되지 않았습니다. 따라서 30-instance 결과는 p99 개선 claim으로 사용하지 않습니다. 100-instance burst 실행에서는 request p95가 `132.491ms -> 119.177ms`, p99가 `278.518ms -> 136.400ms`로 낮아졌고, worker drain 이후 10,000개 bucket이 모두 저장됐습니다.

Worker는 SQS Standard queue의 at-least-once/out-of-order 가능성을 전제로 idempotency를 처리합니다. Duplicate smoke에서는 같은 bucket 2건 중 1건이 중복으로 억제됐고, same key/different hash 및 same instance bucket/different key는 application DLQ 대상으로 분리됩니다. Snapshot에는 capture delay와 `accepted_at` cutoff를 적용해 queue lag가 stale/down 또는 host application down 의미를 오염시키지 않게 했습니다.

Benchmark artifact:

- [30-instance summary](implementation-artifacts/benchmark-evidence/localstack-sqs-30-instance/summary.md)
- [100-instance summary](implementation-artifacts/benchmark-evidence/localstack-sqs-100-instance/summary.md)
- [evidence note](docs/sqs-buffered-ingest-evidence.md)
- [runner plan](docs/sqs-buffered-ingest-local-evidence-plan.md)

## 핵심 구현 포인트

Observation Portal은 단순 CRUD가 아니라 수집, 보안, 집계, 상태 판단, UI 표현까지 연결된 end-to-end 제품입니다.

- 수집 경계: starter가 Spring Boot 애플리케이션의 HTTP observation을 30초 단위 지표로 모으고, 요청 처리 경로와 전송 경로를 분리합니다.
- 보안 경계: project key, token, OAuth payload, credential 원문이 응답, 로그, 저장소에 남지 않도록 다루며, starter credential은 생성/회전 직후 1회 표시로 제한합니다.
- 데이터 경계: 지표 수집 상태와 연결 상태 신호를 섞지 않고, 운영자가 각각의 의미를 따로 판단할 수 있게 보여줍니다.
- 계산 경계: p95/p99, lifecycle state, triage, endpoint priority는 서버가 계산하고 프론트엔드는 서버 응답을 표시합니다.
- 검증 경계: 단위 테스트, MockMvc 기반 controller 테스트, Testcontainers 기반 PostgreSQL 통합 테스트, smoke traffic으로 구현 경로를 확인합니다.

## 다음 확장 계획

1. Discord 알림
   - 중요한 상태 변화나 장애 후보를 Discord로 알림

2. 운영 smoke와 전환 runbook 고도화
   - 실제 SQS queue, worker receive/delete, malformed/conflict DLQ, direct rollback config를 배포 환경 smoke로 분리 검증
   - local/isolated benchmark evidence를 운영 성능 보증으로 확장하지 않도록 전환 기준과 runbook을 정리

3. Cache hit ratio 추가
   - API/서비스 관점에서 cache 효율을 대시보드 지표로 확장
