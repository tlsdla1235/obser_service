# Epic 2 CI 파이프라인 Handoff

이 문서는 Epic 2 구현 결과와 Epic 4/CD가 이어받을 입력을 정리한다.
실제 운영 secret, local `.private` 값, AWS static credential은 CI workflow에 넣지 않는다.

## 구현 요약

- `.github/workflows/ci.yml`은 `pull_request`, `main` push, 수동 실행에서 동작한다.
- `backend` job은 `SPRING_PROFILES_ACTIVE=ci`와 dummy CI env를 주입한 뒤 `./gradlew build`를 실행한다.
- `frontend` job은 `npm ci`, `npm run typecheck`, `npm run guard:read-model-contract`를 실행한다.
- `secret-scan` job은 `ghcr.io/gitleaks/gitleaks:v8.30.1` CLI로 현재 checkout working tree를 스캔한다.
- `portal-artifact` job은 backend/frontend/secret scan이 모두 통과한 뒤 `:observability-portal:bootJar`를 실행하고 `observability-portal-${{ github.sha }}` 아티팩트를 업로드한다.

## CI Dummy Env 기준

CI의 context load에는 실제 secret 대신 아래 성격의 값만 쓴다.

- datasource는 GitHub Actions PostgreSQL service container의 `ci` 계정과 `observation_ci` DB를 사용한다.
- OAuth client id/secret, service token signing key, OAuth state signing key는 모두 non-prod dummy 문자열이다.
- `AWS_REGION`, `AWS_DEFAULT_REGION`은 SDK region resolution용 non-secret 값만 둔다.
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, prod DB/OAuth/signing key 값은 workflow에 넣지 않는다.

## Secret Scan False Positive 처리 기준

- `.gitleaks.toml` allowlist는 path와 line regex를 함께 만족하는 false positive만 허용한다.
- allowlist description에는 왜 secret이 아닌지와 언제 제거를 재검토할지 남긴다.
- 실제 token, OAuth secret, datasource password, signing key, AWS credential처럼 회전이 필요한 값은 allowlist하지 않는다.
- 발견값이 실제일 가능성이 있으면 먼저 값을 폐기/회전하고, 필요하면 git history 제거 여부를 별도 판단한다.
- 전체 git history 스캔은 과거 문서/테스트 fixture false positive가 있어 별도 baseline 정책을 정한 뒤 운영한다. Epic 2 CI gate는 현재 checkout 기준 working tree 스캔으로 PR 유입을 막는다.

## Branch Protection 수동 설정

GitHub branch protection은 저장소 관리자 권한이 필요하므로 이 커밋에서는 workflow 코드만 제공한다.
`main` branch에는 GitHub UI 또는 API로 아래 status check를 required로 설정한다.

- `Backend build and test`
- `Frontend typecheck and contract guard`
- `Secret scan`
- `Build portal bootJar artifact`

권장 설정은 PR 필수, stale approval dismiss 여부는 팀 정책에 맞춤, required status checks는 최신 commit 기준이다.

## E4/CD 입력

E4는 `portal-artifact` job의 `observability-portal-${{ github.sha }}` 아티팩트를 배포 입력으로 사용한다.
운영 배포는 PR artifact가 아니라 `main` push 또는 release/tag에서 생성된 immutable artifact만 대상으로 삼는다.
