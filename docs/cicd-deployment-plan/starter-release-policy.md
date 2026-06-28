# Starter Release And Version Policy

이 문서는 `observability-spring-boot-starter`를 GitHub Packages와 Maven Central로 게시할 때의 버전, 태그, 검증, 호환성 정책을 정리한다.
portal production deploy와 충돌하지 않도록 starter 릴리스 태그는 항상 `starter-v*` 형식을 사용한다.

## 현재 릴리스

| 항목 | 값 |
| --- | --- |
| groupId | `io.github.tlsdla1235.observation` |
| artifactId | `observability-spring-boot-starter` |
| version | `0.1.0` |
| release tag | `starter-v0.1.0` |
| primary registry | GitHub Packages Maven registry |
| public registry | Maven Central |
| GitHub Packages workflow | `.github/workflows/publish-starter.yml` |
| Maven Central workflow | `.github/workflows/publish-starter-central.yml` |

Portal production CD는 `main` push와 `v*` tag를 사용한다. starter publish workflow는 `starter-v*` tag와 release published 이벤트 중 `starter-v*` tag만 처리하므로 portal jar 배포와 분리된다.

## SemVer 규칙

버전은 `MAJOR.MINOR.PATCH` SemVer를 따른다.

| 변경 유형 | 버전 증가 | 예시 |
| --- | --- | --- |
| Patch | `0.1.0 -> 0.1.1` | 버그 수정, 문서 수정, 내부 구현 수정, 호환되는 retry/logging 보정 |
| Minor | `0.1.0 -> 0.2.0` | 새 optional property, 새 metric field, 기존 사용자를 깨지 않는 자동구성 추가 |
| Major | `0.x -> 1.0` 이후 `1.x -> 2.0` | 공개 API 제거/변경, property rename, 최소 Java/Spring Boot 상향, payload contract breaking change |

`0.x` 구간에서도 외부 consumer가 붙는 순간부터 breaking change는 release note에서 명확히 표시하고, 가능하면 deprecation 기간을 둔다.

## Breaking Change 기준

아래 변경은 breaking change로 취급한다.

- `com.observation.starter.*` 공개 class, record, enum, public method signature 삭제 또는 의미 변경.
- `observation.metric-flush.*`, `observation.heartbeat.*`, `observation.route-attribution.*` property 이름, 기본값, fail-open/fail-closed 동작 변경.
- portal ingest/heartbeat payload schema 또는 header contract 변경.
- Java 17 이상, Spring Boot 4.0.x 같은 최소 runtime 요구사항 상향.
- consumer가 제공하던 custom bean override 지점이 더 이상 동작하지 않는 자동구성 조건 변경.
- 기존 no-op 또는 non-blocking 경로가 host request path를 block하거나 startup fail로 바뀌는 변경.

아래 변경은 일반적으로 non-breaking으로 본다.

- optional property 추가.
- 새 metric sample 추가. 단, portal이 모르는 field를 거부하지 않는지 먼저 검증한다.
- log message 보정. 단, secret masking 또는 category 의미는 유지한다.
- 내부 service 구현, retry/backoff, queue 처리 성능 개선.

## 공개 API 표면

현재 공개 표면은 Maven artifact에 포함되는 `com.observation.starter.*` package 전체다. 특히 consumer가 직접 참조할 가능성이 높은 영역은 아래와 같다.

| Package | 용도 |
| --- | --- |
| `com.observation.starter.config` | auto-configuration과 `@ConfigurationProperties` 바인딩 |
| `com.observation.starter.client` | heartbeat/metric portal client override 지점 |
| `com.observation.starter.model.*` | portal ingest/heartbeat payload와 local metric model |
| `com.observation.starter.service` | collector, sampler, rollup, sender service |
| `com.observation.starter.spring.*` | Micrometer/Spring adapter |
| `com.observation.starter.queue` | bounded metric queue와 overflow policy |

내부용으로만 쓰려는 class라도 public으로 게시되면 consumer가 참조할 수 있다. public surface를 줄이려는 변경은 별도 deprecation 또는 major/minor release 계획과 함께 진행한다.

## 릴리스 절차

1. starter version을 결정하고 `observability-spring-boot-starter/build.gradle`, heartbeat metadata 기본값, consumer guide, release note를 함께 맞춘다.
2. 로컬에서 starter guard와 local publish를 실행한다.

```bash
./gradlew :observability-spring-boot-starter:check
./gradlew :observability-spring-boot-starter:publishToMavenLocal
```

3. 별도 임시 consumer 프로젝트에서 `mavenLocal()` 기준 dependency 수신, compile/test, Spring Boot context 기동을 확인한다.
4. 변경 diff에 secret 원문, PAT, AWS credential, SSH private key가 없는지 확인한다.
5. GitHub Packages 실제 publish가 필요하면 운영자 승인 후 `starter-vX.Y.Z` tag를 push하거나 같은 tag로 GitHub Release를 publish한다.
6. `.github/workflows/publish-starter.yml`이 `starter-guard`를 통과한 뒤 `GITHUB_TOKEN`과 `packages: write` 권한으로 GitHub Packages에 게시한다.
7. Maven Central publish는 `.github/workflows/publish-starter-central.yml`을 수동 실행한다. 이미 사용한 `starter-v0.1.0` tag를 다시 push할 수 없으므로 첫 Central 게시도 workflow input `version=0.1.0`으로 실행한다.
8. Maven Central workflow의 기본 `publishing_type`은 `USER_MANAGED`다. 이 모드는 Central Portal validation이 끝난 뒤 Portal UI에서 최종 Publish를 사람이 누르게 하며, `AUTOMATIC`은 검증 성공 시 바로 Maven Central에 게시한다.
9. publish 완료 후 GitHub Packages consumer 검증은 별도 consumer 프로젝트에서 `read:packages` PAT로 수행하고, Maven Central publish 완료 후에는 `mavenCentral()`만 둔 consumer 프로젝트에서 같은 좌표를 받아 검증한다.

## Release Note 작성 기준

GitHub Release note 또는 CHANGELOG에는 아래 항목을 남긴다.

- 버전과 태그: 예) `0.1.0`, `starter-v0.1.0`.
- 호환 범위: Java, Spring Boot, portal ingest/heartbeat contract.
- 추가/변경/수정/제거 항목.
- breaking change 여부와 migration 방법.
- 검증 명령: starter check, `publishToMavenLocal`, local consumer smoke, GitHub Packages consumer smoke, Maven Central consumer smoke 여부.
- 보안 메모: secret masking, PAT 원문 미기록, raw project key 미기록 확인.

## Publish 권한 정책

- GitHub Actions publish는 repository `GITHUB_TOKEN`만 사용한다.
- Workflow 권한은 `contents: read`, `packages: write`로 제한한다.
- 별도 PAT를 GitHub Actions Secret에 넣지 않는다.
- consumer 검증용 PAT는 로컬 shell 또는 consumer CI secret store에서 `read:packages` 용도로만 사용한다.
- Maven Central publish는 `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_PASSWORD` GitHub Secret을 사용한다.
- `SIGNING_KEY`는 GPG private key export 값이므로 채팅, 문서, workflow log에 출력하지 않는다. public key는 keyserver에 업로드해 서명 검증에 사용한다.
