# CI/CD & 배포 계획 (Epic / Story)

이 문서는 `observation` 프로젝트를 **로컬 개발 상태에서 AWS 운영 배포까지** 끌고 가기 위한
CI/CD 로드맵이다. 작업 단위는 Epic → Story로 분해했고, Story마다 목적·완료 조건(AC)·세부 작업·선행 의존성을 명시한다.

> 작성일: 2026-06-20 · 대상 브랜치: `main`

---

## 1. 현재 상태 (As-Is)

| 영역 | 현황 |
| --- | --- |
| 빌드 | Gradle 멀티모듈, Java 17 toolchain, Spring Boot 4.0.6 |
| 모듈 | `observability-portal`(런타임), `observability-spring-boot-starter`(배포용 라이브러리), `observability-smoke-service`, `ecc-endpoint-smoke-service` |
| 프론트엔드 | Vite + React/MUI, `node-gradle`로 portal jar에 번들링 (`frontendBuild` → `processResources`) |
| DB | PostgreSQL + Flyway 마이그레이션 (`observability-portal/src/main/resources/db/migration`) |
| 인입 버퍼 | AWS SQS (`PORTAL_INGEST_BUFFER_*`) |
| 인증 | GitHub OAuth App — `.private/github-oauth.properties` 한 파일에 **local/ci/prod 구분 없이** client-id/secret/redirect-uri + datasource + signing-key가 섞여 있음 |
| CI/CD | **없음** (`.github/workflows` 부재) |
| 배포 | **없음** (수동 로컬 실행) |

## 2. 목표 상태 (To-Be)

```
                         ┌──────────────────────────────────────────┐
   GitHub (main/PR) ──►  │  GitHub Actions                           │
                         │  - build + test + guards (CI)             │
                         │  - publish starter → GitHub Packages      │
                         │  - build portal jar → 아티팩트            │
                         │  - SSH/SSM 배포 (CD)                       │
                         └───────────────┬──────────────────────────┘
                                         │  deploy
                                         ▼
        ┌────────────────────────────────────────────────────────┐
        │  AWS (ap-northeast-2)                                    │
        │                                                          │
        │   Internet ──► Nginx (reverse proxy, TLS)               │
        │                   │  EC2 small (t3.small / t4g.small)    │
        │                   ▼                                      │
        │                portal jar (systemd, 127.0.0.1:8080)     │
        │                   │                                      │
        │                   ▼                                      │
        │                RDS PostgreSQL (db.t4g.micro, 최소 사양) │
        │                                                          │
        │                SQS (기존 인입 버퍼)                      │
        └────────────────────────────────────────────────────────┘

   사용자 서비스 ──► GitHub Packages 에서 observability-spring-boot-starter 의존성 수신
```

## 3. Epic 목록 & 실행 순서

| # | Epic | 핵심 산출물 | 선행 |
| --- | --- | --- | --- |
| [E1](epic-01-config-secrets-separation.md) | 설정·시크릿 환경 분리 | local/ci/prod OAuth·DB 시크릿 분리, profile 구조 | — |
| [E2](epic-02-ci-pipeline.md) | CI 파이프라인 (GitHub Actions) | build/test/guard 워크플로, PR 게이트 | E1 |
| [E3](epic-03-aws-infrastructure.md) | AWS 인프라 프로비저닝 | RDS(최소), EC2 small, 네트워크/보안그룹 | E1 |
| [E4](epic-04-cd-deploy-nginx.md) | CD 배포 + Nginx 리버스 프록시 | 자동 배포 워크플로, systemd, Nginx+TLS | E2, E3 |
| [E5](epic-05-starter-distribution.md) | Starter 라이브러리 배포 | GitHub Packages 게시, 소비자 가이드 | E2 |

권장 진행: **E1 → (E2 ∥ E3) → E4 → E5**. E2와 E3는 병렬 가능.

### 실행 보강 결정

- **Profile 경계:** 실행 profile은 `local / ci / prod`로 분리한다. 운영과 CI는 `SPRING_PROFILES_ACTIVE`를 명시하고, `prod`가 기본값으로 켜지지 않게 한다.
- **Prod 설정 파일:** `application-prod.properties`는 placeholder와 운영 기본값만 담는 커밋 대상이다. 실값은 GitHub Secrets, SSM Parameter Store, EC2 environment file로만 주입한다.
- **Health endpoint:** CD 헬스체크 전에 portal에 비밀을 노출하지 않는 health endpoint를 확정한다. Actuator를 쓰면 `health`만 외부 확인 대상으로 열고, 직접 구현하면 DB 연결 여부까지 포함할지 별도 AC로 정한다.
- **Artifact 전달:** E2가 만든 jar를 E4가 어떻게 받는지 먼저 고정한다. 단순 운영은 같은 workflow 안에서 `build -> deploy` job으로 넘기고, workflow를 분리하면 S3 또는 GitHub artifact 조회 방식을 명시한다.
- **SQS 운영 자원:** 기존 SQS라는 표현만으로는 부족하다. 운영 source queue, DLQ, redrive policy, `AWS_REGION`, EC2 IAM 권한을 E3에서 확정한다.
- **배포 가용성:** 현재 목표는 small 단일 EC2의 짧은 다운타임 stop/start 배포다. 무중단, rolling, blue/green, ALB 기반 배포는 이번 범위에서 제외하고 트래픽/비용이 커질 때 후속으로 검토한다.
- **컨텍스트 운영:** 한 Epic을 한 작업 컨텍스트에서 진행해도 된다. 단, Epic 종료 시 완료/미완료/검증 로그/다음 Epic 입력값을 짧은 handoff note로 남긴다.

## 4. 마일스톤

- **M1 — 설정 분리 완료**: E1 종료. local/ci/prod에서 시크릿 외부 주입으로 빌드·테스트 통과.
- **M2 — CI 그린**: E2 종료. main/PR에서 자동 빌드·테스트·가드 통과.
- **M3 — 인프라 가동**: E3 종료. RDS/EC2/보안그룹 준비, 수동 배포로 1회 기동 검증.
- **M4 — 운영 배포 자동화**: E4 종료. 태그/머지 시 짧은 다운타임 stop/start 자동 배포 + Nginx TLS.
- **M5 — Starter 공개**: E5 종료. 외부 사용자가 GitHub Packages로 의존성 수신.

## 5. 공통 정의 (Definition of Done)

- 시크릿 값은 저장소·아티팩트·로그에 평문으로 남지 않는다(GitHub Secrets / SSM Parameter Store / 환경변수 주입).
- 모든 Story는 재현 가능한 명령 또는 워크플로로 검증되고, 근거(로그/스크린샷/링크)를 남긴다.
- 문서(README·운영 가이드)가 변경분과 함께 갱신된다.
- 기존 가드(`read-model-contract`, ArchUnit, starter guard)를 깨지 않는다.
- 각 Epic 종료 시 다음 Epic이 바로 이어받을 수 있도록 handoff note를 남긴다.
