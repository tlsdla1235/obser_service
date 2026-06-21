# Epic 5 — Starter 라이브러리 배포 (GitHub Packages)

> **목표:** `observability-spring-boot-starter`를 **GitHub** 패키지 레지스트리로 게시해,
> 외부 사용자가 우리 의존성을 받아 자기 Spring Boot 앱에 붙일 수 있게 한다.

**선행:** E2(CI) · **후행:** —

## 배경

- `observability-spring-boot-starter`는 `java-library`(현재 `com.sst:observation:0.1.0-SNAPSHOT`)로,
  외부에 공개하려면 별도 좌표(groupId/artifactId/version) + publish 설정이 필요하다.
- 배포 채널은 **GitHub Packages (Maven)**. 소비자는 GitHub PAT(`read:packages`)로 인증해 의존성을 받는다.

## Stories

### S5.1 — 게시용 좌표 & maven-publish 설정
- **As a** 라이브러리 메인테이너, **I want** starter에 공개 좌표와 publish 설정을 부여하고 싶다, **so that** 레지스트리에 올릴 수 있다.
- **AC**
  - starter 모듈에 `maven-publish` 적용, 명확한 `groupId`/`artifactId`(예: `com.sst.observation:observability-spring-boot-starter`) 확정.
  - sources/javadoc jar 포함(소비자 IDE 경험), POM 메타데이터(name/description/license/scm) 채움.
  - 버전 전략 확정: SNAPSHOT(내부) vs 정식 SemVer 태그 릴리스.
- **검증:** `./gradlew :observability-spring-boot-starter:publishToMavenLocal`로 로컬 게시 확인.

### S5.2 — GitHub Packages 게시 워크플로
- **As a** 메인테이너, **I want** 태그/릴리스 시 GitHub Packages에 자동 게시되길 원한다, **so that** 수동 배포가 없어진다.
- **AC**
  - `.github/workflows/publish-starter.yml`: 트리거는 릴리스 태그(`v*`) 또는 release publish.
  - `publishing.repositories`에 GitHub Packages URL(`https://maven.pkg.github.com/<owner>/<repo>`), 인증은 `GITHUB_TOKEN`(`packages: write`).
  - workflow `permissions`에 `contents: read`, `packages: write`를 명시하고, publish job은 E2 CI 통과 후 실행되게 한다.
  - 게시 성공 후 패키지가 repo Packages 탭에 노출됨.
- **의존:** S5.1, E2

### S5.3 — 소비자(사용자) 의존성 수신 가이드
- **As a** 외부 사용자, **I want** 의존성을 어떻게 받는지 알고 싶다, **so that** 내 프로젝트에 붙인다.
- **AC**
  - `starter-consumer-guide.md`: Gradle/Maven에서 GitHub Packages repo 추가 + PAT(`read:packages`) 인증 설정 예시.
  - 의존성 좌표/버전 + 최소 사용 예제(autoconfigure 활성화, 필수 프로퍼티) 포함.
  - 호환 Spring Boot/Java 버전 명시.
  - GitHub Packages는 공개 package라도 소비자 환경에 따라 인증이 필요할 수 있음을 가이드에 명시.
- **의존:** S5.1

### S5.4 — 버전·릴리스 정책 & 호환성
- **As a** 메인테이너, **I want** 릴리스/버저닝 규칙을 정하고 싶다, **so that** 사용자가 안정적으로 업그레이드한다.
- **AC**
  - SemVer 채택, CHANGELOG 또는 GitHub Release 노트 작성 규칙.
  - starter guard/ArchUnit가 게시 전 CI에서 통과해야 함(공개 API 안정성).
  - 공개 API 표면(패키지/클래스) 문서화 및 breaking change 정책.
- **의존:** S5.2

### S5.5 — 엔드투엔드 소비 검증
- **As a** 팀, **I want** 외부 입장에서 의존성 수신이 실제로 되는지 확인하고 싶다, **so that** 가이드가 정확하다.
- **AC**
  - 별도 샘플/스모크 앱에서 게시된 좌표로 의존성 추가 → 빌드/기동 성공.
  - portal heartbeat/metric 전송 등 starter 핵심 동작 1개 이상 검증.
  - 검증 샘플은 로컬 Maven cache에 기대지 않도록 clean Gradle user home 또는 별도 임시 프로젝트에서 실행.
- **의존:** S5.2, S5.3

## Epic 완료 조건 (DoD)
- starter가 GitHub Packages에 게시되고, 태그/릴리스로 자동화된다.
- 외부 사용자가 가이드만 보고 PAT 인증 → 의존성 수신 → 사용까지 가능.
- 공개 API/버전 정책이 문서화되고 CI 가드로 보호된다.
