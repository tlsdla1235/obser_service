# Epic 4 — CD 배포 + Nginx 리버스 프록시

> **목표:** CI가 만든 portal jar를 EC2에 자동 배포하고, **Nginx 리버스 프록시 + TLS**로 외부에 서비스한다.

**선행:** E2(아티팩트), E3(인프라) · **후행:** —

## 설계 요약

```
Internet ─► :443 Nginx (TLS 종료, reverse proxy)
                 └─► proxy_pass http://127.0.0.1:8080  (portal, systemd 서비스)
                                     └─► RDS PostgreSQL / SQS
```

- 앱은 `127.0.0.1:8080`만 바인딩(외부 직접 노출 금지), 외부는 Nginx 80/443.
- 프로세스 관리: `systemd` 유닛(`observation.service`), `SPRING_PROFILES_ACTIVE=prod`.
- 배포 트리거: main 머지 또는 릴리스 태그(`v*`). 운영 보호를 위해 GitHub Environment 승인 게이트 권장.
- 배포 가용성: small 단일 EC2에서 `systemctl stop/start`로 jar를 교체하는 **짧은 다운타임 배포**를 목표로 한다. 무중단, rolling, blue/green, ALB 이중화는 이번 Epic의 범위가 아니다.

## 실행 순서 결정

Epic 4는 운영 프로세스 소유권을 먼저 안정화하기 위해 아래 순서로 진행한다.

1. `systemd` unit + SSM env loader 전환.
2. 배포 health endpoint 확정.
3. Nginx reverse proxy + TLS.
4. 배포/롤백 스크립트.
5. GitHub Actions CD + OIDC deploy role + production approval gate.
6. `portal.observstarter.cloud` 기준 GitHub OAuth E2E 검증.

IAM/OIDC 생성과 비용 또는 권한 변경이 있는 작업은 실행 전에 운영자 확인을 받는다.

## Stories

### S4.0 — 배포 헬스 엔드포인트 확정
- **As a** 운영자, **I want** CD가 확인할 안정적인 health endpoint를 갖고 싶다, **so that** 배포 성공/실패를 자동 판정한다.
- **AC**
  - 직접 구현한 `/internal/health/live`, `/internal/health/ready`를 코드에 반영한다.
  - 응답은 `status`와 component별 `checks`만 포함하고 secret, SSM key 원문, datasource URL/password, queue URL query를 포함하지 않는다.
  - `/internal/health/live`는 프로세스와 HTTP server 응답 가능 여부만 확인한다.
  - `/internal/health/ready`는 DB `select 1`과 Flyway pending migration 여부로 운영 요청 준비 상태를 확인한다.
  - Nginx, systemd, GitHub Actions deploy job이 문서화된 health URL을 사용한다.
- **의존:** E2

#### Health endpoint 사용 정책

| Consumer | Endpoint | 기준 |
| --- | --- | --- |
| systemd | `http://127.0.0.1:8080/internal/health/live` | `ExecStartPost`에서 HTTP server liveness만 확인한다. |
| Nginx | `http://127.0.0.1:8080/internal/health/ready` | reverse proxy 적용/재시작 후 upstream이 운영 요청을 받을 수 있는지 확인한다. |
| GitHub Actions CD | `https://portal.observstarter.cloud/internal/health/ready` | 배포 후 외부 HTTPS 경로에서 DB/Flyway까지 ready인지 확인한다. |
| 배포/롤백 스크립트 | `http://127.0.0.1:8080/internal/health/ready` | jar 교체 후 server-local ready 판정으로 성공/롤백을 결정한다. |

### S4.1 — systemd 서비스 정의
- **As a** 운영자, **I want** portal을 systemd로 관리하고 싶다, **so that** 자동 재시작/로그 수집이 된다.
- **AC**
  - `/etc/systemd/system/observation.service` 작성: `ExecStart=java -jar /opt/observation/app.jar`, `Restart=on-failure`.
  - `EnvironmentFile` 또는 SSM에서 받은 환경변수로 `SPRING_PROFILES_ACTIVE=prod` 및 시크릿 주입.
  - 앱은 `server.address=127.0.0.1` 또는 동등한 설정으로 외부 직접 노출을 막는다.
  - `journalctl`로 로그 확인 가능.
- **의존:** E3

### S4.2 — 배포 워크플로 (CD)
- **As a** 릴리스 담당, **I want** 머지/태그 시 EC2로 자동 배포되길 원한다, **so that** 수동 배포를 없앤다.
- **AC**
  - `.github/workflows/deploy.yml`: main push 또는 `v*` tag에서 새로 빌드한 immutable jar를 S3에 업로드한 뒤 SSM Run Command로 EC2에 배포한다.
  - PR artifact는 운영 배포 입력으로 사용하지 않고, deploy workflow가 checkout한 main/tag ref에서 `:observability-portal:bootJar`를 다시 생성한다.
  - 배포 대상 jar의 commit SHA와 artifact SHA-256을 서버에 남긴다.
  - 배포 절차: 전송 → `systemctl stop` → jar 교체(이전 버전 백업) → `systemctl start` → 헬스체크.
  - 단일 인스턴스 stop/start 동안 짧은 502/503 또는 연결 실패가 발생할 수 있음을 운영 런북에 명시한다.
  - 무중단 배포를 흉내 내기 위한 background dual-port, local proxy switch, blue/green 스크립트는 이번 범위에서 만들지 않는다.
  - AWS 자격증명은 SSH private key GitHub Secret이 아니라 GitHub OIDC deploy role로 주입한다.
  - GitHub Environment(`production`) 보호 규칙으로 수동 승인 게이트를 둔다.
- **의존:** S4.1, E2(S2.5)

#### CD/OIDC 구성 결정

- Workflow: `.github/workflows/deploy.yml`.
- Trigger: `main` push, `v*` tag, `workflow_dispatch`.
- Guard: `workflow_dispatch`도 `refs/heads/main` 또는 `refs/tags/v*`가 아니면 실패한다.
- Artifact: deploy workflow가 생성한 `app.jar`를 `s3://observation-prod-deploy-artifacts-491013322019-ap-northeast-2/portal/<sha>/<run-id>-<attempt>/` 아래에 저장한다.
- EC2 deploy: SSM Run Command가 `/usr/local/bin/observation-deploy-portal`을 실행한다.
- Rollback: 배포 스크립트가 실패 시 직전 jar로 자동 복구하고, 운영자는 `/usr/local/bin/observation-rollback-portal`로 최신 backup jar를 수동 복구할 수 있다.
- Health: 배포 성공 판정은 server-local `http://127.0.0.1:8080/internal/health/ready`, workflow 최종 검증은 `https://portal.observstarter.cloud/internal/health/ready`를 사용한다.

#### Approval gate 범위

- Code로 제공: workflow job의 `environment: production`, OIDC role assume, main/tag guard.
- GitHub UI 수동 설정: `production` environment required reviewers, deployment branches/tags 제한(`main`, `v*`).
- 필요한 environment variables:
  - `OBSERVATION_PROD_DEPLOY_ROLE_ARN`
  - `OBSERVATION_DEPLOY_ARTIFACT_BUCKET`
  - `OBSERVATION_PROD_INSTANCE_ID`
- Environment variables 3개는 등록 완료됐고, required reviewers와 branch/tag restriction은 운영 merge 전 UI에서 설정해야 한다.
- 개인 repository에서 운영자가 1명뿐이면 self-review 방지를 켤 때 배포가 막힐 수 있다. 별도 reviewer가 가능하면 self-review 방지를 켜고, 1인 운영이면 reviewer 추가 전까지는 위험을 인지한 상태로 운영한다.

#### AWS 권한 변경 승인 필요

아래 작업은 보안 범위 변경이므로 실행 전에 운영자 확인을 받는다.

- GitHub OIDC provider가 없으면 `token.actions.githubusercontent.com` provider 생성.
- GitHub deploy role 생성: `repo:tlsdla1235/obser_service:environment:production` subject만 assume 가능.
- Deploy role policy 부여: 지정 S3 artifact prefix write/read, 지정 EC2 instance에 `AWS-RunShellScript` SSM command 전송, command result 조회.
- Artifact S3 bucket 생성/보안 설정: public access block, versioning 권장. 비용은 S3 storage/request 중심이다.
- EC2 instance role에 artifact bucket `portal/*` read 권한 추가.

#### AWS/GitHub CD 리소스 생성 결과

- Artifact bucket: `observation-prod-deploy-artifacts-491013322019-ap-northeast-2`.
- Bucket 보안: public access block, versioning, SSE-S3 기본 암호화.
- GitHub OIDC provider: `arn:aws:iam::491013322019:oidc-provider/token.actions.githubusercontent.com`.
- Deploy role: `arn:aws:iam::491013322019:role/observation-prod-github-deploy-role`.
- Deploy role trust: `repo:tlsdla1235/obser_service:environment:production` subject와 `sts.amazonaws.com` audience로 제한.
- EC2 instance role 추가 권한: artifact bucket `portal/*` read와 bucket location 조회만 허용.
- GitHub `production` environment variables 등록 완료: deploy role ARN, artifact bucket, EC2 instance ID.
- GitHub Secrets에는 AWS access key, SSH private key, prod secret을 넣지 않는다.

### S4.3 — 헬스체크 & 배포 검증(롤백 안전망)
- **As a** 운영자, **I want** 배포 후 헬스체크가 실패하면 알게 되길 원한다, **so that** 장애 배포를 막는다.
- **AC**
  - portal ready endpoint(`/internal/health/ready`)로 배포 후 200 확인.
  - 실패 시 워크플로 실패 + 직전 jar로 복구하는 절차(스크립트 또는 수동 런북) 명시.
- **의존:** S4.0, S4.2

### S4.4 — Nginx 리버스 프록시 구성
- **As a** 운영자, **I want** Nginx가 외부 요청을 portal로 프록시하길 원한다, **so that** 앱을 직접 노출하지 않는다.
- **AC**
  - Nginx 설치 및 server block: `proxy_pass http://127.0.0.1:8080`, `X-Forwarded-*`/`Host` 헤더 전달.
  - Spring이 `X-Forwarded-*`를 신뢰하도록 `server.forward-headers-strategy` 또는 동등한 설정을 prod profile에 반영.
  - 정적/SPA 라우팅 및 업로드 사이즈(`client_max_body_size`)가 인입 페이로드 한도와 충돌 없는지 확인.
  - 80→443 리다이렉트.
- **의존:** S4.1

#### Nginx 설정 결정

- Repo template: `deploy/nginx/observation.conf`.
- Server target: `/etc/nginx/conf.d/observation.conf`.
- HTTP `80`: `/.well-known/acme-challenge/`만 ACME webroot로 제공하고 나머지는 `https://$host$request_uri`로 `301` redirect한다.
- HTTPS `443`: Let's Encrypt 인증서를 사용하고 `proxy_pass http://127.0.0.1:8080`으로 portal에 전달한다.
- Forwarded headers: `Host`, `X-Forwarded-Proto`, `X-Forwarded-For`, `X-Forwarded-Host`, `X-Real-IP`.
- Access log: OAuth callback `code`/`state` query가 남지 않도록 `$request_uri` 대신 `$uri`를 기록하는 `observation_no_query` format을 사용한다.
- 검증 기준: 외부 HTTPS `https://portal.observstarter.cloud/internal/health/ready`가 `200`이고 body가 secret-free `status/checks` shape다.

### S4.5 — TLS 인증서 (HTTPS)
- **As a** 사용자, **I want** HTTPS로 접속하고 싶다, **so that** 통신이 암호화된다.
- **AC**
  - 도메인 연결(Route 53 또는 외부 DNS) 후 Let's Encrypt(certbot) 또는 ACM+(ALB 사용 시) 인증서 적용.
  - 자동 갱신 설정(certbot timer) 및 갱신 검증.
  - OAuth redirect-uri(prod)가 HTTPS 도메인과 일치(E1 S1.2 연계).
- **의존:** S4.4, E1(S1.2)

#### TLS/OAuth 결정

- TLS: Let's Encrypt certbot webroot 방식으로 `portal.observstarter.cloud` 인증서를 발급한다.
- 자동 갱신: `certbot-renew.timer`를 enabled/active 상태로 유지하고 `certbot renew --dry-run`으로 검증한다.
- GitHub OAuth prod redirect URI: `https://portal.observstarter.cloud/api/auth/github/callback`.
- 실제 HTTPS callback URL: `https://portal.observstarter.cloud/api/auth/github/callback`.
- OAuth E2E: GitHub 로그인/승인 후 `/dashboard`로 돌아오며, 상단 Dashboard link를 한 번 클릭하면 `Projects`/`Applications` empty state가 렌더링된다. 현재 계정에 active membership project가 없는 empty state는 인증 실패로 보지 않는다.
- OAuth 노출 검증: 최종 dashboard URL에는 query/hash가 없고, browser storage/cookie/console, application journal, Nginx log에서 secret/provider token/raw payload 패턴이 0건이다.

### S4.6 — 운영 배포 런북 & 1차 운영 배포
- **As a** 팀, **I want** 배포/롤백 런북을 갖고 실제 한 번 배포하고 싶다, **so that** 절차가 검증된다.
- **AC**
  - `deploy-runbook.md`: 배포/롤백/긴급 정지/로그 확인 절차.
  - 실제 prod 배포 1회 수행 → HTTPS로 GitHub OAuth 로그인 + 대시보드 동작 + DB 영속 확인.
- **의존:** S4.0~S4.5

## Epic 완료 조건 (DoD)
- main 머지/태그로 EC2에 자동 배포되고 헬스체크로 검증된다.
- 배포 방식은 단일 EC2 stop/start이며, 예상 짧은 다운타임과 롤백 절차가 런북에 명시되어 있다.
- 외부는 Nginx HTTPS만 노출, 앱은 내부 포트로만 동작.
- prod OAuth/DB로 실제 로그인·데이터 영속이 동작한다.
- 롤백 경로가 문서화·검증되어 있다.
