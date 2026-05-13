---
artifactType: contract
name: starter-failure-semantics
architectureStyle: Traditional MVC
status: proposed
date: 2026-05-11
---

# Contract - Starter Failure Semantics MVC Version

## 1. 역할

`starter-failure-semantics`는 observability starter가 portal 또는 수집 서버와 연결하지 못할 때 host Spring Boot application에 어떤 영향을 주는지 고정한다.

이 계약의 기본 원칙은 starter가 host application의 보조 관측 기능이며, portal 장애가 host application의 build, startup, request path 실패로 전파되지 않아야 한다는 것이다.

## 2. 적용 범위

이 계약은 starter에서 portal ingest endpoint로 bucket/envelope를 전송하는 경로에 적용한다.

포함:

- DNS 실패
- TCP connection refused
- connect/read timeout
- TLS handshake failure
- portal `5xx` 응답
- 일시적인 네트워크 단절
- portal 배포/재시작 중 연결 실패

제외:

- starter artifact 자체를 Maven/Gradle repository에서 resolve하지 못하는 dependency resolution 실패
- host application code compile failure
- 사용자가 명시적으로 켠 integration test의 portal availability assertion
- 사용자가 `fail-fast` mode를 켠 운영 정책

## 3. 기본 동작 계약

| 상황 | 기본 동작 | 비고 |
|---|---|---|
| host app build | portal availability를 확인하지 않는다 | build는 starter artifact와 compile/test classpath 문제만 검증한다 |
| Spring bean 생성 | portal로 동기 네트워크 호출을 하지 않는다 | unreachable portal 때문에 `ApplicationContext` refresh가 실패하면 안 된다 |
| application startup | 연결 확인이 필요하면 비동기 또는 lazy로 수행한다 | 기본값에서는 startup을 막지 않는다 |
| 최초 전송 실패 | `WARN` log를 최초 1회 남기고 host flow는 계속 진행한다 | endpoint, failure category, retry/backoff 상태를 식별 가능하게 남긴다 |
| 반복 전송 실패 | rate-limited logging과 backoff를 적용한다 | 동일 장애로 log spam을 만들지 않는다 |
| queue full | bounded drop policy를 적용하고 host request를 실패시키지 않는다 | durable delivery보다 host app safety를 우선한다 |
| worker retry | background worker 안에서만 수행한다 | request thread에서 retry/backoff를 실행하지 않는다 |
| shutdown flush | bounded timeout 안에서만 대기한다 | portal 장애 때문에 host shutdown이 무기한 지연되면 안 된다 |

## 4. Fail-Fast 옵션

기본값은 fail-open이다.

```yaml
observability:
  fail-fast: false
```

`fail-fast=true`를 사용자가 명시적으로 설정한 경우에만 starter는 portal 연결 실패를 startup 또는 flush failure로 전파할 수 있다.

Fail-fast mode에서도 구현은 다음을 지켜야 한다.

- 실패 원인은 starter 전용 exception 또는 명확한 error message로 감싼다.
- 어느 endpoint와 설정값 때문에 실패했는지 로그와 exception message로 식별 가능해야 한다.
- fail-fast default를 `true`로 바꾸려면 이 계약과 sprint acceptance를 함께 갱신해야 한다.

## 5. Logging Contract

최초 연결 실패 로그는 `WARN` level이다.

로그에는 최소한 다음 정보를 포함한다.

- portal ingest endpoint 또는 endpoint alias
- failure category: `dns`, `connect_timeout`, `read_timeout`, `connection_refused`, `tls`, `server_5xx`, `unknown`
- host app이 계속 실행된다는 사실
- 다음 retry/backoff가 적용된다는 사실

동일 failure window 안에서 같은 원인의 반복 실패는 `WARN`을 반복하지 않는다. 필요하면 `DEBUG` 또는 rate-limited `WARN`으로 남긴다.

## 6. Portal Health와 UI 의미

starter가 portal로 보내지 못한 bucket은 portal accepted bucket이 아니다.

따라서 dashboard freshness와 state는 `time-buckets`와 `state-semantics` 계약을 따른다. portal은 마지막 accepted bucket 기준으로 `stale` 또는 `down`을 판단할 수 있지만, 그 판단은 host application을 실패시키는 근거가 아니다.

HealthIndicator를 제공하는 경우 기본값에서는 host application 전체 health를 `DOWN`으로 만들지 않는다. 별도의 component health 또는 detail field로 관측 전송 상태를 표현한다.

## 7. MVC Boundary

- Spring integration layer는 host request signal을 starter service input으로 변환한다.
- request path는 local record 또는 bounded queue enqueue까지만 수행한다.
- portal HTTP client 호출, retry, backoff는 worker 또는 client boundary 내부에서만 수행한다.
- starter에는 MVC web controller를 만들지 않는다.
- portal controller/repository는 starter fail-open 결정을 재판정하지 않는다.

## 8. Test Requirements

- portal down 상태에서도 host application context가 로드되는 test
- bean 생성 중 portal network call이 발생하지 않는 guard test
- 최초 전송 실패가 `WARN` once로 기록되는 test
- 반복 실패가 log spam을 만들지 않는 test
- request path가 portal timeout을 기다리지 않는 non-blocking test
- `fail-fast=true`일 때만 실패가 전파되는 test

## 9. Non-Goals

- durable delivery guarantee
- local disk outbox
- Kafka/Redis 기반 재전송 queue
- portal 장애를 host app 장애로 간주하는 기본 정책
- build phase에서 portal availability를 검증하는 정책
