# Epic 1 — 설정·시크릿 환경 분리

> **목표:** GitHub OAuth 키를 포함한 모든 시크릿/환경설정을 **local / ci / prod** 로 분리하고,
> 코드 밖에서 주입 가능한 구조로 만든다. 이후 모든 Epic의 전제 조건.

**선행:** 없음 · **후행:** E2, E3, E4

## 배경 / 문제

현재 `observability-portal/src/main/resources/application.properties`는
`.private/github-oauth.properties` 한 파일을 `spring.config.import optional`로 끌어온다.
이 파일에 **local/ci/prod 구분 없이** 다음이 한꺼번에 들어 있다.

```
portal.auth.github.client-id / client-secret / redirect-uri / homepage-url
spring.datasource.url / username / password
server.port
portal.auth.service-token.signing-key
portal.auth.oauth-state.signing-key
```

→ 운영 OAuth App과 개발 OAuth App이 분리되지 않아, 운영 redirect-uri/secret을 로컬과 공유하게 되는 위험.
CI에서 시크릿을 안전하게 주입할 표준 경로도 없다.

## Stories

### S1.1 — 설정 키 인벤토리 & 환경 매트릭스 작성
- **As a** 운영자, **I want** 모든 외부 주입 설정 키와 환경별(local/ci/prod) 값 소유 위치를 한 표로 보고 싶다, **so that** 분리 대상이 누락되지 않는다.
- **AC**
  - `application.properties` + `.private/*` + `.env.example`의 모든 시크릿/환경 키를 나열한 표 작성.
  - 각 키마다 `local / ci / prod` 3열로 "값 출처(개발자 .env, GitHub Secrets, SSM 등)"를 명시.
  - 비밀(secret) vs 비밀 아님(config) 구분.
  - `portal.ingest.buffer.*`, `portal.smoke.seed.*`, OAuth, datasource, token signing key, server/proxy 관련 키를 모두 포함.
- **작업**: 문서 `config-matrix.md` 생성 → README §5 DoD 반영.

### S1.2 — GitHub OAuth App을 local/운영 2개로 분리
- **As a** 개발자, **I want** 개발용·운영용 GitHub OAuth App을 별도로 갖고 싶다, **so that** 운영 credential이 로컬에 노출되지 않는다.
- **AC**
  - GitHub에 OAuth App 2개 등록: `observation-dev`, `observation-prod`.
  - 각 App의 `Authorization callback URL`이 환경별 redirect-uri와 일치 (예: dev=`http://localhost:8080/...`, prod=`https://<도메인>/...`).
  - 두 App의 client-id/secret을 안전한 저장소에 기록(로컬 .private, GitHub Secrets, SSM). 저장소 평문 커밋 금지.
- **의존:** S1.1

### S1.3 — Spring profile 기반 설정 구조 도입
- **As a** 개발자, **I want** `application-{local,ci,prod}.properties` + 환경변수 placeholder 구조를 갖고 싶다, **so that** 코드 변경 없이 환경만 바꿔 기동된다.
- **AC**
  - 공통값은 `application.properties`, 환경별 값은 `application-local.properties` / `application-ci.properties` / `application-prod.properties`로 분리.
  - 시크릿은 파일에 평문 대신 `${PORTAL_AUTH_GITHUB_CLIENT_ID}` 형태 placeholder + 환경변수 주입.
  - `SPRING_PROFILES_ACTIVE=local|ci|prod`로 전환 가능. CI와 prod는 profile을 반드시 명시한다.
  - `prod`는 기본 profile이 되지 않는다. profile 미지정 기동은 안전한 기본값으로 실패하거나 local runbook에서 명시적으로 `local`을 사용한다.
  - 기존 `.private/*` import는 **local 전용 fallback**으로만 유지(`optional`).
  - `application-prod.properties`는 placeholder와 비밀이 아닌 운영 기본값만 담아 커밋한다. 실값이 들어간 prod override 파일은 만들지 않는다.
- **의존:** S1.1
- **검증:** `SPRING_PROFILES_ACTIVE=ci` 테스트, `SPRING_PROFILES_ACTIVE=prod` + 더미 환경변수 context 기동, `bootJar` 산출 확인.

### S1.4 — `.env.example` / `.private` 템플릿 정리 및 문서화
- **As a** 신규 합류자, **I want** 환경변수 예시 템플릿을 보고 싶다, **so that** 로컬 셋업을 빠르게 한다.
- **AC**
  - `.env.example`에 OAuth/DB/server/signing-key 키를 환경별로 주석과 함께 추가(값은 빈칸).
  - `.private/github-oauth.properties`는 local 전용임을 명시하는 헤더 주석 갱신.
  - README/운영 가이드에 "환경변수 주입 방법(local/CI/prod)" 절 추가.
- **의존:** S1.3

### S1.5 — 시크릿 누출 방지 가드
- **As a** 보안 담당, **I want** 실수로 시크릿이 커밋되지 않게 막고 싶다, **so that** 사고를 예방한다.
- **AC**
  - `.gitignore`에 `.env`, `.env.*`, `.private/` 포함 확인/보강.
  - `application-prod.properties`는 실값 금지 placeholder 파일로 커밋하고, 실값 override 파일이 필요하면 별도 ignore 패턴을 정의한다.
  - 간단한 시크릿 스캔(예: gitleaks) 실행 절차 또는 pre-commit 안내 문서화.
  - 과거 커밋에 평문 시크릿이 없는지 1회 점검(있으면 회수/로테이션 계획 기재).
- **의존:** S1.3

### S1.6 — prod 필수 시크릿 fail-closed 가드
- **As a** 운영자, **I want** 운영에서 필수 시크릿이 빠지면 앱이 뜨지 않기를 원한다, **so that** 임시 키로 인증·토큰이 동작하는 사고를 막는다.
- **AC**
  - `prod` profile에서 OAuth client secret, datasource password, service-token signing key, oauth-state signing key가 비어 있으면 startup 실패.
  - local profile의 개발용 fallback과 prod fail-closed 동작을 테스트로 구분.
  - 실패 메시지는 키 이름과 조치 경로를 알려주되 실제 secret 값을 로그에 남기지 않는다.
- **의존:** S1.3

## Epic 완료 조건 (DoD)
- `SPRING_PROFILES_ACTIVE` 전환만으로 local/ci/prod 설정이 갈린다.
- OAuth/DB/signing-key 시크릿이 소스 트리에 평문으로 존재하지 않는다.
- CI(E2)와 배포(E4)가 참조할 "시크릿 주입 인터페이스(환경변수 키 이름)"가 확정·문서화된다.
