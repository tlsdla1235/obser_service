# Epic 2 — CI 파이프라인 (GitHub Actions)

> **목표:** PR/머지 시 Gradle 멀티모듈 + 프론트엔드 빌드/테스트/가드를 자동 실행하는 CI를 GitHub Actions로 구성한다.

**선행:** E1 (시크릿 주입 인터페이스 확정) · **후행:** E4, E5

## 배경

- `.github/workflows` 없음. 검증은 로컬 수동.
- 검증 대상: Gradle 테스트(JUnit5 + Testcontainers PostgreSQL), ArchUnit/starter guard, 프론트 `typecheck` + `guard:read-model-contract`.
- Testcontainers는 CI 러너에 Docker 필요(`ubuntu-latest`는 Docker 제공).

## Stories

### S2.1 — 기본 CI 워크플로 스캐폴딩
- **As a** 개발자, **I want** PR과 main push에서 도는 `ci.yml`을 갖고 싶다, **so that** 깨진 코드가 머지되지 않는다.
- **AC**
  - `.github/workflows/ci.yml` 생성. 트리거: `pull_request`, `push`(main).
  - `actions/checkout`, `actions/setup-java@v4`(temurin 17), `gradle/actions/setup-gradle`(캐시) 사용.
  - 동일 PR 재푸시 시 이전 run 취소(`concurrency`).
  - workflow `permissions`는 기본 read-only로 시작하고, artifact/package 작성이 필요한 job에서만 명시적으로 올린다.
- **검증:** PR 생성 시 워크플로가 트리거되고 green/red가 PR에 표시.

### S2.2 — 백엔드 build & test 잡
- **As a** 개발자, **I want** `./gradlew build`(테스트 포함)가 CI에서 돌기를 원한다, **so that** 회귀를 잡는다.
- **AC**
  - Testcontainers가 도는 Docker 환경에서 portal 테스트 통과.
  - 테스트 리포트/실패 로그를 아티팩트로 업로드(`actions/upload-artifact`).
  - `SPRING_PROFILES_ACTIVE=ci`로 실행하고, context load에 필요한 값은 더미 CI 환경변수로 주입한다.
  - 실제 GitHub OAuth secret이나 prod DB secret은 CI 테스트에 주입하지 않는다.
- **의존:** S2.1, E1

### S2.3 — 프론트엔드 검증 잡
- **As a** 프론트 개발자, **I want** `typecheck`와 `guard:read-model-contract`가 CI에서 돌기를 원한다, **so that** 대시보드 계약이 깨지지 않는다.
- **AC**
  - `actions/setup-node@v4`(+npm 캐시), `npm ci`, `npm run typecheck`, `npm run guard:read-model-contract` 실행.
  - 가드 실패 시 잡 실패. (메모리 노트: read-model-contract-guard가 UI 카피를 강제함 — 관련 변경 시 주의)
- **의존:** S2.1

### S2.4 — 가드/품질 게이트 통합 & 브랜치 보호
- **As a** 메인테이너, **I want** CI 통과를 머지 필수 조건으로 걸고 싶다, **so that** main이 항상 그린이다.
- **AC**
  - ArchUnit / starter guard / read-model-contract가 모두 CI 잡으로 실행됨.
  - GitHub branch protection: main에 대해 위 status check들을 required로 설정.
  - PR 머지 전 최소 1개 잡 그린 필요.
- **의존:** S2.2, S2.3

### S2.5 — 배포용 아티팩트 빌드 잡 (CD 연계 준비)
- **As a** 릴리스 담당, **I want** portal 실행 jar(프론트 번들 포함)를 CI가 산출하기를 원한다, **so that** CD가 그대로 배포한다.
- **AC**
  - `./gradlew :observability-portal:bootJar`로 fat jar 생성(`frontendBuild`→`processResources` 경유 프론트 포함 확인).
  - jar을 워크플로 아티팩트로 업로드(또는 후속 CD 잡으로 전달).
  - 버전/커밋 SHA를 아티팩트 이름 또는 메타데이터에 포함.
  - E4에서 사용할 artifact 전달 방식을 결정: 같은 workflow의 `needs` artifact, S3 업로드, 또는 `workflow_run` artifact download 중 하나.
  - deploy job은 PR artifact를 운영 배포에 사용하지 않고 main/tag에서 생성된 immutable artifact만 사용.
- **의존:** S2.2

### S2.6 — CI 보안 스캔 및 로그 redaction 점검
- **As a** 메인테이너, **I want** CI가 기본적인 secret leak을 잡아주길 원한다, **so that** 운영 배포 전에 노출 사고를 줄인다.
- **AC**
  - gitleaks 또는 동등한 secret scan을 PR/main push에서 실행.
  - 테스트/빌드 로그에 OAuth secret, signing key, datasource password, SQS queue URL query credential이 출력되지 않는지 실패 사례 기준을 문서화.
  - scan은 false positive 처리 절차를 문서화하되, allowlist에는 사유와 만료 기준을 남긴다.
- **의존:** S1.5, S2.1

## Epic 완료 조건 (DoD)
- PR/Push에서 백엔드+프론트 검증이 자동 실행되고 결과가 PR에 노출.
- main이 required check로 보호됨.
- 배포 가능한 portal jar 아티팩트가 CI에서 재현 가능하게 산출됨(E4 입력).
