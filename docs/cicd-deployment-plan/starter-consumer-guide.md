# Starter Consumer Guide

이 문서는 외부 Spring Boot 프로젝트가 GitHub Packages에서 `observability-spring-boot-starter`를 받아 Observation Portal로 heartbeat와 metric bucket을 전송하는 최소 절차를 정리한다.
PAT 원문, starter project key 원문, 운영 portal secret은 문서, 로그, 저장소에 남기지 않는다.

## 배포 좌표

| 항목 | 값 |
| --- | --- |
| Maven repository | `https://maven.pkg.github.com/tlsdla1235/obser_service` |
| groupId | `io.github.tlsdla1235.observation` |
| artifactId | `observability-spring-boot-starter` |
| version | `0.1.0` |
| release tag | `starter-v0.1.0` |

GitHub Packages Maven registry는 공개 package라도 consumer 환경에 따라 인증을 요구할 수 있다. 외부 consumer는 classic PAT에 `read:packages` 권한을 부여해 사용한다.

## 호환 범위

- Java: 17 이상.
- Spring Boot: 4.0.x. 현재 starter는 repository BOM 기준 `4.0.6`에서 빌드와 테스트를 검증한다.
- Host application: Spring MVC와 Micrometer Observation이 켜진 Spring Boot 애플리케이션을 우선 지원한다.
- Datasource pool metric: HikariCP가 포함된 `DataSource`가 있으면 자동 샘플링하고, 없으면 JVM/HTTP 지표만 수집한다.

Spring Boot 3.x, WebFlux-only 애플리케이션, Java 21 전용 기능은 이번 `0.1.0` 지원 범위로 보증하지 않는다.

## 인증 환경변수

로컬 shell이나 CI secret store에서 아래 값을 주입한다. 토큰 원문은 출력하지 않는다.

```bash
export GITHUB_PACKAGES_USERNAME=tlsdla1235
export GITHUB_PACKAGES_TOKEN='<classic PAT with read:packages>'
```

검증이 끝나면 shell에서 값을 제거한다.

```bash
unset GITHUB_PACKAGES_USERNAME GITHUB_PACKAGES_TOKEN
```

## Gradle 사용 예시

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/tlsdla1235/obser_service")
        credentials {
            username = System.getenv("GITHUB_PACKAGES_USERNAME")
            password = System.getenv("GITHUB_PACKAGES_TOKEN")
        }
    }
}

dependencies {
    implementation "io.github.tlsdla1235.observation:observability-spring-boot-starter:0.1.0"
}
```

CI에서는 `GITHUB_PACKAGES_USERNAME`, `GITHUB_PACKAGES_TOKEN`을 secret/variable로 주입한다. repository에 `gradle.properties`로 PAT 원문을 커밋하지 않는다.

## Maven 사용 예시

`pom.xml`에는 repository와 dependency만 둔다.

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/tlsdla1235/obser_service</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.github.tlsdla1235.observation</groupId>
        <artifactId>observability-spring-boot-starter</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

인증은 `~/.m2/settings.xml`이나 CI secret 기반 settings 파일에 둔다.

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>${env.GITHUB_PACKAGES_USERNAME}</username>
            <password>${env.GITHUB_PACKAGES_TOKEN}</password>
        </server>
    </servers>
</settings>
```

## 최소 Spring Boot 설정

portal에서 발급한 starter project key는 secret으로 다룬다. 아래 예시는 환경변수 `OBSERVATION_PROJECT_KEY`에서만 읽는다.

```yaml
spring:
  application:
    name: orders-api

observation:
  metric-flush:
    portal-base-url: https://portal.observstarter.cloud
    project-key: ${OBSERVATION_PROJECT_KEY}
    project-id: orders-prod
    application-name: ${spring.application.name}
    environment: prod
    instance: ${HOSTNAME:local}
    queue-capacity: 1024
    drop-policy: DROP_NEWEST
    timeout-millis: 1000
  heartbeat:
    portal-base-url: https://portal.observstarter.cloud
    project-key: ${OBSERVATION_PROJECT_KEY}
    interval-seconds: 30
    timeout-millis: 1000
  route-attribution:
    allowlist:
      - /orders/{orderId}
```

필수 설정은 전송 목적에 따라 나뉜다.

| 목적 | 필수 property | 비고 |
| --- | --- | --- |
| metric bucket 전송 | `observation.metric-flush.portal-base-url`, `observation.metric-flush.project-key` | 둘 중 하나만 설정하면 startup에서 실패한다. |
| metric identity | `observation.metric-flush.project-id`, `application-name`, `environment`, `instance` | 실제 flush worker가 generic 기본값으로 시작하지 않도록 막는다. |
| heartbeat 전송 | `observation.heartbeat.portal-base-url`, `observation.heartbeat.project-key` | 둘 다 없으면 no-op client로 시작한다. |
| route fallback | `observation.route-attribution.allowlist` | 선택값이다. raw path가 아니라 `/orders/{orderId}` 같은 route template만 허용한다. |

`project-key`는 portal 인증 header에 들어가는 raw key이므로 로그, exception, 문서, 테스트 fixture에 원문을 남기지 않는다. `project-id`는 idempotency와 payload identity에 쓰이는 non-secret 식별자이며 raw key와 다르다.

## 로컬 Maven Cache 검증

starter repository에서 로컬 publish를 먼저 수행한다.

```bash
./gradlew :observability-spring-boot-starter:publishToMavenLocal
```

별도 임시 consumer 프로젝트에서는 GitHub Packages 대신 `mavenLocal()`을 앞에 두고 같은 좌표를 받는다.

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation "io.github.tlsdla1235.observation:observability-spring-boot-starter:0.1.0"
}
```

이 검증은 dependency 수신과 Spring Boot context 기동을 확인하기 위한 로컬 smoke다. 실제 GitHub Packages에서 받아오는 검증은 `starter-v0.1.0` tag publish가 끝난 뒤 `GITHUB_PACKAGES_USERNAME`과 `GITHUB_PACKAGES_TOKEN`을 입력한 별도 consumer 프로젝트에서 수행한다.
