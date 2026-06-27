---
title: 'Epic 1 Config Secrets Separation'
type: 'feature'
created: '2026-06-21'
status: 'done'
baseline_commit: '4cffb56616605b3645af49e1b7e16553ff7a1dbd'
context:
  - '../docs/cicd-deployment-plan/epic-01-config-secrets-separation.md'
---

<frozen-after-approval reason="human-owned intent - do not modify unless human renegotiates">

## Intent

**Problem:** 현재 portal runtime은 local `.private/github-oauth.properties`를 모든 profile에서 optional import해 OAuth, datasource, server, signing key가 local/ci/prod 구분 없이 섞일 수 있다.

**Approach:** 현재 로컬 값은 local 전용 fallback으로 유지하고, ci/prod profile은 환경변수로만 값을 받는 표준 interface를 만든다. Prod에서는 OAuth secret, datasource password, service token signing key, OAuth state signing key가 비면 기동을 막는다.

## Boundaries & Constraints

**Always:** 로컬 실행은 기존 `.private/github-oauth.properties`와 `.env` 흐름이 깨지지 않아야 한다. 주석/문서는 한국어로 쓴다. Secret 원문은 tracked file, 로그, 테스트 출력에 남기지 않는다.

**Ask First:** 실제 AWS 리소스 생성, GitHub OAuth App 생성/수정, GitHub Secrets 또는 SSM Parameter Store 쓰기, 기존 local secret rotation.

**Never:** Prod 실값 override 파일을 커밋하지 않는다. `prod`를 default profile로 만들지 않는다. `.private` import를 ci/prod에서 사용하지 않는다.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Local profile | `SPRING_PROFILES_ACTIVE=local` and existing `.private/github-oauth.properties` | 기존 local datasource/OAuth/signing key가 계속 import된다. | 누락된 local secret은 기존 Spring/code error path를 따른다. |
| CI profile | `SPRING_PROFILES_ACTIVE=ci` plus CI env vars | `.private` 없이 환경변수 interface만 사용한다. | 누락 필수값은 CI setup error로 드러난다. |
| Prod missing secret | `SPRING_PROFILES_ACTIVE=prod` and blank required secret | context startup fails before normal runtime work. | 실패 메시지는 key 이름과 주입 경로만 포함하고 값을 포함하지 않는다. |
| Prod configured | `SPRING_PROFILES_ACTIVE=prod` plus required env vars | profile-specific config binds without local secret files. | 잘못된 DB/AWS endpoint는 해당 client boundary에서 실패한다. |

</frozen-after-approval>

## Code Map

- `observability-portal/src/main/resources/application.properties` -- portal common non-secret runtime defaults.
- `observability-portal/src/main/resources/application-local.properties` -- local-only `.private` fallback import.
- `observability-portal/src/main/resources/application-ci.properties` -- CI environment variable interface.
- `observability-portal/src/main/resources/application-prod.properties` -- prod environment variable interface and proxy settings.
- `observability-portal/src/main/java/com/observation/portal/config/ProdRequiredPropertiesGuard.java` -- prod fail-closed required property guard.
- `observability-portal/src/test/java/com/observation/portal/config/ProdRequiredPropertiesGuardTest.java` -- guard behavior tests.
- `.env.example` -- local/CI/prod env var template without secret values.
- `docs/cicd-deployment-plan/config-matrix.md` -- Epic 1 configuration matrix.
- `README.md` -- local/CI/prod setup note.

## Tasks & Acceptance

**Execution:**
- [x] `observability-portal/src/main/resources/application*.properties` -- split common/local/ci/prod configuration.
- [x] `observability-portal/src/main/java/com/observation/portal/config/ProdRequiredPropertiesGuard.java` -- add prod missing-secret fail-closed guard.
- [x] `observability-portal/src/test/java/com/observation/portal/config/ProdRequiredPropertiesGuardTest.java` -- cover prod missing/configured and local bypass behavior.
- [x] `.env.example` and `README.md` -- document local values and env injection.
- [x] `docs/cicd-deployment-plan/config-matrix.md` -- record key matrix and operator checklist.

**Acceptance Criteria:**
- Given local profile and existing `.private`, when portal starts, then local import remains available only for local.
- Given ci/prod profile, when profile config loads, then values are sourced from environment variable placeholders rather than `.private`.
- Given prod profile with required secret missing or blank, when context starts, then startup fails with key names only.
- Given tracked files, when searched, then no local/prod secret values are committed.

## Spec Change Log

## Handoff Notes

- Epic 1 구현은 완료됐고, local profile은 기존 `.private` 기반 값으로 Spring Boot 기동까지 확인했다.
- Prod 실값은 저장소에 커밋하지 않았고, 아직 GitHub Secrets나 AWS SSM Parameter Store에도 이 작업에서 쓰지 않았다.
- AWS RDS, EC2, SQS 운영 queue/DLQ, IAM role/policy, SSM parameter 생성은 아직 수행하지 않았다. 해당 리소스 확정은 E3 범위다.
- Epic 2는 이 config interface를 사용해 GitHub Actions CI에서 `ci` profile과 더미 secret/env를 주입하면 된다.
- Epic 4/CD에서는 main 또는 tag에서 생성된 immutable portal jar artifact만 운영 배포 입력으로 사용한다.

## Verification

**Commands:**
- `./gradlew :observability-portal:test --tests '*ProdRequiredPropertiesGuardTest'` -- expected: guard tests pass.
- `./gradlew :observability-portal:test` -- expected: portal tests pass.
- `./gradlew :observability-portal:bootJar` -- expected: boot artifact builds.
- `./gradlew :observability-portal:bootRun --args='--spring.profiles.active=local'` -- expected: local profile starts with existing private config.
