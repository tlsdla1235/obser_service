# Spring Boot 운영 첫 화면 토이 프로젝트 명세서

> Version: 0.8
> 상태: Micrometer direct ingest 기반으로 재정의
> 전제: 2인 1팀 / 1개월 MVP / 학습·포트폴리오 목적
> 참고: `observability_toy_spec_v0.7.md`는 historical reference로 남긴다
> 최신 p95/p99 계약: `summary.localPercentiles.p95Ms` / `p99Ms`는 starter가 해당 instance의 해당 30초 bucket에서 직접 산출해 보낸 canonical 값이다. Histogram bucket은 percentile 계산 입력이 아니라 distribution visualization, endpoint bucket display, diagnostic raw bucket source다.

---

## 0. 변경 요약

### v0.7 → v0.8 (이번 변경)

- **메트릭 수집 구조를 `Micrometer + Prometheus query`에서 `Micrometer + starter direct ingest`로 전환**
  - starter는 앱 내부 메트릭을 low-cardinality 기준으로 수집하고
  - background worker가 summary metric + histogram bucket을 포털 ingest API로 직접 전송한다
  - 사용자는 Prometheus, scrape config, selector 등록, `/actuator/prometheus` 공개를 알 필요가 없다
- **metric ingestion contract를 `Prometheus query profile`에서 accepted bucket ingest로 되돌린다**
  - 단, v0.6을 그대로 복원하지 않고, 더 좁고 더 엄격한 bounded payload로 다시 정의한다
- **p95/p99 source를 starter-reported canonical percentile로 고정한다**
  - starter는 `summary.localPercentiles.p95Ms` / `p99Ms`를 해당 instance의 해당 30초 bucket canonical 값으로 보낸다
  - field name은 호환성 때문에 `localPercentiles`로 유지하지만, 의미는 `starter-reported percentile`이다
  - portal의 service layer는 app/project/window p95/p99를 histogram bucket 병합 결과에서 새로 만들지 않는다
  - histogram bucket은 distribution visualization, endpoint bucket display, diagnostic raw bucket source로만 쓴다
- **온보딩 계약을 `Prometheus reachable endpoint`에서 `starter 추가 + 최소 설정 + outbound HTTPS`로 단순화한다**
- **starter heartbeat를 metric ingest와 분리된 주기적 control-plane/liveness 신호로 둔다**
  - heartbeat는 project key, portal reachability, metadata shape, starter liveness 확인에만 사용한다
  - heartbeat 성공은 accepted bucket, application state, dashboard snapshot, operational event를 만들지 않는다
- **제품 정체성을 `Prometheus 온보딩 포털`이 아니라 `starter-first 운영 첫 화면`으로 다시 고정한다**
- **사용자 계정 생성과 로그인은 GitHub OAuth only로 고정한다**
  - email/password, magic link, GitHub 외 social OAuth, anonymous flow는 MVP 범위에서 제외한다
  - API 인증은 cookie 기반 server session이 아니라 Bearer access token 기준으로 둔다

### 이 버전의 핵심 의도

이 버전은 "표준 stack 재사용"보다 "starter를 붙였더니 바로 보인다"를 우선한다.

다시 말해, 이번 피벗은 다음 질문에 대한 대답이다.

- 사용자가 Prometheus를 이해해야만 첫 성공을 할 수 있다면, 우리 제품 약속과 맞는가
- 계측 경로의 표준성보다 time-to-first-insight가 더 중요한가

이 문서는 그 질문에 대해 **MVP에서는 starter-first direct ingest가 더 맞다**는 입장을 전제로 쓴다.

---

## 1. 이 문서의 역할

이 문서는 기존 `v0.7`의 Prometheus 중심 구조를 historical reference로 남기면서,
이번 프로젝트를 **Micrometer direct ingest 기반 운영 첫 화면**으로 다시 정의하는 기준 문서다.

이 문서의 목적은 세 가지다.

1. 제품 약속을 다시 고정한다
2. PRD / architecture / contracts / epics가 같은 방향으로 움직이게 만든다
3. 2인 MVP 팀이 구현 가능한 범위를 벗어나지 않게 막는다

이 문서에서 고정하는 것은 "예쁘게 설명되는 구조"보다 "실제로 1개월 안에 닫히는 구조"다.

### 1.1 학습 목표

이 프로젝트는 단순히 메트릭을 모아보는 연습이 아니라, 아래 기술 토픽을 의도적으로 다루기 위해 설계한다.

1. **Spring Boot Starter 자체 제작 경험**
 - auto-configuration, conditional activation, opinionated defaults, starter packaging
2. **Micrometer 기반 서버 계측 표준화 경험**
 - HTTP/JVM/health/datasource 계측, route normalization, low-cardinality guard
3. **비동기 direct ingest 경로 설계 경험**
 - background flush, bounded queue, retry/backoff, idempotent ingest
4. **histogram bucket distribution 기반 read model 설계 경험**
 - bucket을 canonical p95/p99 계산 입력이 아니라 app/project distribution 표시, endpoint bucket display, diagnostic raw bucket source로 다루는 방식
5. **규칙 기반 triage 요약 설계 경험**
 - 차트를 많이 보여주기보다 "어디부터 볼까"를 먼저 말하는 summary 계층
6. **contract-first 협업 경험**
 - starter, ingest API, state semantics, triage summary, endpoint evidence를 문서 계약으로 먼저 닫는 방식

---

## 2. 프로젝트 한 줄 정의

**Spring Boot 앱에 starter를 붙이면 5분 안에 설치되고 30~60초 안에 운영 첫 화면이 보이는, Micrometer direct ingest 기반 미니 SaaS형 관측성 도구**

---

## 3. 프로젝트 철학

### 3.1 외부 face

외부에 보여줄 때 이 프로젝트는 다음처럼 설명한다.

- **Spring Boot 초보 운영자를 위한 starter-first 운영 첫 화면**
- Prometheus 설치나 scrape 설정 없이도 바로 insight를 제공하는 미니 SaaS형 관측성 도구
- Micrometer 계측, histogram bucket distribution, 규칙 기반 triage를 직접 설계·구현한 작품

README, 발표, 면접에서는 "토이", "best-effort", "자선사업" 같은 표현을 쓰지 않는다.
외부 face는 제품의 가치와 설계 선택을 또렷하게 설명하는 용도다.

### 3.2 내부 face

내부 의사결정에서는 다음을 항상 기억한다.

- 이 프로젝트는 1개월 MVP다
- 범용 observability platform을 만들지 않는다
- 운영 부담이 커지는 기능은 먼저 자른다
- 사용자에게 숨길 수 있는 복잡도는 문서가 아니라 starter 내부로 넣는다

### 3.3 이 프로젝트는 무엇인가

- Spring Boot 운영 초보자를 위한 첫 관측성 화면
- starter 설치 경험을 제품의 핵심 가치로 삼는 미니 SaaS
- 메트릭 저장소보다 **운영 판단 경험**을 우선하는 해석형 도구
- "지금 무슨 일이 벌어지는지"를 먼저 말하는 triage product

### 3.4 이 프로젝트는 무엇이 아닌가

- Prometheus / Grafana / Datadog / New Relic 대체재
- 범용 metrics platform
- 자유 질의형 TSDB 제품
- 로그, 메트릭, 트레이스를 모두 흡수하는 플랫폼
- 엔터프라이즈 운영 제품

### 3.5 왜 이 방향이 맞는가

`Micrometer + Prometheus`는 기술적으로 건강한 조합이지만,
MVP에서 사용자가 Prometheus까지 이해해야만 첫 성공을 할 수 있다면
우리의 제품 약속과 정면으로 충돌한다.

이번 버전은 다음을 우선한다.

- 설치 난이도보다 첫 성공 속도
- 표준 stack 설명 가능성보다 starter 경험
- query 유연성보다 운영 첫 화면의 명확성
- platform purity보다 MVP 완성 가능성

---

## 4. 목표 사용자

### 4.1 핵심 사용자

다음 조건을 만족하는 사용자를 핵심 타깃으로 한다.

- Spring Boot 서비스를 운영 중이다
- 팀 규모가 작다
- 운영 전담 인력이 없다
- Grafana/Prometheus를 깊게 다뤄본 적이 없거나, 지금 당장은 그 스택까지 들이고 싶지 않다
- 장애나 느려짐을 겪은 적은 있지만, "어디부터 봐야 하지?"를 빠르게 답받고 싶다

### 4.2 사용자 문제

- 메트릭을 잘 보고 싶은데 설정과 인프라가 먼저 나온다
- 장애가 나면 로그부터 뒤지게 된다
- "죽었나 / 느려졌나 / 어디가 문제인가"를 한 화면에서 빨리 판단하기 어렵다
- 설치가 복잡하면 아예 시도하지 않게 된다

### 4.3 사용자에게 주려는 가치

- dependency 추가와 최소 설정만으로 시작할 수 있다
- 30~60초 안에 첫 insight가 뜬다
- 차트가 많지 않아도 어디부터 봐야 할지 안다
- 인프라 구성을 몰라도 운영 첫 판단이 가능하다

---

## 5. 제품 원칙

1. `starter를 붙이면 된다`가 가장 중요한 제품 약속이다
2. `time-to-first-insight`가 표준 stack 순정성보다 우선한다
3. 사용자는 비동기 전송, 큐, 재시도, histogram bucket distribution 처리를 몰라도 된다
4. metric은 많이 모으는 것보다 **낮은 cardinality로 안정적으로 모으는 것**이 중요하다
5. p95/p99는 starter가 보낸 source-scoped canonical 값을 그대로 쓰며, 여러 instance 값을 평균/병합/최댓값 선택으로 app/project scalar로 만들지 않는다
6. portal 장애가 host app request path를 막아서는 안 된다
7. 화면은 "무슨 일이 벌어졌는가"를 먼저 말하고, 상세 분석은 나중에 연다
8. starter heartbeat와 accepted bucket freshness/application state를 서로 다른 축으로 보여준다

---

## 6. 제품 정체성 재정의

| 구분 | 표현 |
|---|---|
| 외부 face | **Spring Boot 앱에 starter를 붙이면 운영 첫 화면을 바로 제공하는 Micrometer direct ingest 기반 미니 SaaS** |
| 내부 face | **starter-first 운영 첫 화면 MVP** |

이 버전부터 제품의 중심 문장은 다음으로 바뀐다.

> "우리는 Prometheus를 쉽게 붙여주는 포털"이 아니라  
> "starter를 붙였더니 바로 보이는 운영 첫 화면"을 만든다.

---

## 7. MVP 핵심 가설

이 프로젝트의 핵심 가설은 다음 세 가지다.

1. `사용자는 Prometheus를 배우기 전에, starter만 붙여도 바로 보이는 경험을 원한다`
2. `운영 첫 화면은 request/error/source-scoped starter p95/top endpoint 정도만 보여줘도 충분히 가치가 있다`
3. `설치가 쉬우면 작은 팀도 관측을 시작하고, 한 번 보이면 더 깊은 기능을 요구한다`

이 MVP의 성공 기준은 기능 수가 아니라,
이 세 가설이 데모와 사용자 관점에서 성립하는가다.

---

## 8. MVP 범위

### 8.1 반드시 구현하는 것

#### Host App / Starter

- Spring Boot starter 1개
- Micrometer 기반 HTTP / JVM / health / datasource pool 계측
- low-cardinality common tags 자동 부여
 - `application`
 - `environment`
 - `instance`
- route normalization
- noise exclusion 정책
- 전역 제외 패턴 설정 지원
 - `observability.exclude-path-patterns`
- 코드 레벨 제외 어노테이션 지원
 - `@ObservabilityExclude`
- app-level summary metric 집계
- endpoint-level histogram bucket 집계
- 30초 cadence background flush
- starter heartbeat background schedule
- HTTPS direct ingest
- bounded retry / backoff
- project key 기반 인증
- 최소 설치 가이드

#### Central Portal

- 사용자 계정 생성/로그인: GitHub OAuth only
- application 등록
- project key 발급
- ingest API
- bucket validation / idempotency
- summary snapshot read model
- 메인 대시보드 1개
- endpoint 상세 화면 1개
- app-level triage summary
- endpoint priority view
- starter heartbeat / connection status 표시 후보

#### 메인 화면에서 반드시 답해야 하는 질문

- `지금 살아있나?`
- `최근 데이터가 들어오고 있나?`
- `느려졌나?`
- `에러가 늘었나?`
- `어디부터 봐야 하나?`

### 8.2 MVP에 꼭 포함할 정보

- app availability / lifecycle state
- request count
- error count
- error rate
- starter-reported p95 latency
- bucket distribution 기준 느린 endpoint 후보
- top error endpoints
- JVM heap usage
- CPU usage
- datasource pool usage

### 8.3 Stretch Goal A

대표 느린 endpoint / 대표 에러 endpoint의 bounded evidence drill-down

이 기능은 "더 깊은 root cause"가 아니라 "어디를 먼저 볼지 한 단계 더 좁혀보자" 수준으로만 연다. MVP read model에는 raw path, query string, high-cardinality tag, trace id, per-request sample을 넣지 않는다.

### 8.4 Stretch Goal B

알림 1채널

단, 이번 버전에서는 메트릭 수집 경로를 닫는 것이 우선이므로,
알림은 direct ingest core가 안정화된 뒤에만 고려한다.

### 8.5 MVP에서 의도적으로 제외하는 것

- Prometheus 설치 / scrape / selector 등록
- `/actuator/prometheus` 공개
- arbitrary query UI
- user-defined custom metric
- high-cardinality tag 검색
- logs / spans / distributed trace graph
- cross-service correlation
- Kubernetes cluster-level monitoring
- multi-region / large-scale tenancy
- 장기 보관형 시계열 플랫폼
- email/password signup, local account registration, local password
- password reset, email verification required for signup
- magic link signup/login
- multiple OAuth providers, Google/Kakao/Naver OAuth
- anonymous user flow

---

## 9. 고정 기술 결정

### 9.1 계측 계층

- `Micrometer`를 사용한다
- Spring Boot 3.x 계열을 기준으로 한다
- observation / timer / histogram 기반 계측을 사용한다
- 필터링은 `기본 자동 계측 + 예외만 제외` 원칙으로 둔다
- MVP에서 공식 지원하는 제외 수단은 아래 둘로 제한한다
 - `@ObservabilityExclude`
 - `observability.exclude-path-patterns`

중요한 점은 `Micrometer`와 `Prometheus`가 같은 층위가 아니라는 것이다.
이번 버전은 **Micrometer는 유지하고, Prometheus는 MVP 필수 경로에서 제외**한다.

`@ObservabilityExclude`는 클래스 또는 메서드 수준에서 특정 endpoint를
starter 집계 대상에서 제외하는 용도로 사용한다.

`observability.exclude-path-patterns`는 `/actuator/**`, `/health`, `/internal/**` 같은
전역 noise route를 path pattern 기준으로 제외하는 용도로 사용한다.

MVP에서는 include annotation, metric rename annotation, custom tag annotation 같은
추가 사용자 확장 포인트는 열지 않는다.

#### Post-MVP annotation 확장 후보

아래 항목은 core MVP에 포함하지 않는다. MVP의 기본 정책은 query string, raw path,
사용자 식별자, 임의 tag를 endpoint key나 ingest payload 후보로 남기지 않는 것이다.

- query string 기반 endpoint 분류 opt-in
 - 사용자가 명시한 annotation이나 설정으로 허용한 query parameter만 dimension 후보가 될 수 있다.
 - query string 전체를 route로 쓰지 않는다.
 - 허용된 parameter도 low-cardinality allowlist, value bucket, `other` fallback 같은 bounded 정책을 통과해야 한다.
- endpoint route/display masking annotation
 - 특정 API를 원본 route 대신 고정된 normalized route나 display name으로 집계할 수 있다.
 - 예: 민감한 결제/인증/내부 운영 endpoint를 세부 path가 아닌 `/sensitive/**` 같은 bounded 이름으로 묶는다.
 - masking은 request body, header, token, user id 같은 민감 데이터를 수집하기 위한 기능이 아니다.
- annotation 기반 metric rename/custom tag 확장
 - starter와 portal 양쪽 validation contract가 닫힌 뒤에만 연다.
 - arbitrary label map이나 high-cardinality custom tag로 확장하지 않는다.

이 확장들은 backlog 후보로만 남긴다. 구현하려면 별도 story에서 starter annotation metadata,
route normalization, ingest validation, dashboard 표시 정책을 함께 설계해야 한다.

### 9.2 전송 방식

- metric은 `HTTPS POST`로 포털 ingest API에 직접 전송한다
- 전송은 background worker가 비동기로 수행한다
- host app request thread는 전송 성공 여부를 기다리지 않는다
- portal 장애가 나도 host app의 본래 HTTP 처리에는 영향이 없어야 한다
- starter heartbeat 실패도 fail-open이며 host app startup/request path를 막지 않는다

### 9.3 Starter Heartbeat 경계

- heartbeat는 metric ingest가 아니라 주기적 control-plane/liveness signal이다
- heartbeat는 project key 유효성, portal reachability, schema version, `application/environment/instance` metadata shape, starter liveness를 확인한다
- heartbeat 성공은 accepted bucket, host application health, dashboard snapshot, operational event, state/read-model calculation을 생성하거나 암시하지 않는다
- heartbeat 미수신은 host application down 판정이 아니다
- heartbeat telemetry를 저장하더라도 `lastHeartbeatAt`, `lastHeartbeatStatus`, failure category 같은 connection field로 제한한다
- UI는 starter heartbeat/connection status와 accepted bucket freshness/application state를 분리해서 보여준다

### 9.4 p95/p99 계산 책임

- `summary.localPercentiles.p95Ms` / `p99Ms`는 starter가 해당 instance의 해당 30초 bucket에서 직접 산출해 보낸 canonical 값이다
- 필드명은 호환성 때문에 `localPercentiles`로 유지하지만, 의미는 `starter-reported percentile` 또는 `starter canonical percentile`이다
- starter는 p95/p99와 함께 HTTP server duration histogram bucket도 전송한다
- portal의 service layer는 app/project/window p95/p99를 histogram bucket 병합 결과에서 새로 만들지 않는다
- histogram bucket은 distribution visualization, endpoint bucket display, diagnostic raw bucket source로만 쓴다
- 같은 scope에서 starter-reported p95/p99와 histogram-derived p95/p99를 동시에 보여주지 않는다
- 여러 starter instance의 p95/p99가 같은 app/project/window에 섞이면 평균/최댓값/병합/히스토그램 재계산으로 단일 p95/p99를 만들지 않는다
- 이 경우 source/instance 단위로 보여주거나 상위 scope에는 bucket distribution을 표시한다
- endpoint별 p95/p99는 계산하지 않고, endpoint 상세는 histogram bucket을 그대로 보여준다

이 결정을 택한 이유는 다음과 같다.

- starter가 실제 instance bucket에서 산출한 percentile 값을 canonical source로 삼는 편이 계약이 명확하다
- percentile 숫자끼리 합치면 상위 scope p95/p99처럼 보이지만 통계적으로 안전하지 않다
- bucket distribution은 병합해 표시할 수 있지만, 그 결과에서 percentile scalar를 만들지는 않는다
- 개별 instance detail에서는 starter-reported tail latency point를 정확한 라벨로 강조할 수 있다

### 9.5 저장소

- Portal DB는 PostgreSQL을 기본 선택으로 둔다
- 목적은 metadata + bounded bucket data + derived snapshot 저장이다
- 이 DB를 범용 TSDB처럼 쓰지 않는다

### 9.6 시간 버킷

- starter flush cadence: `30초`
- bucket duration: `30초`
- current window: 최근 `15분`
- baseline window: 그 직전 `15분`
- 시간 기준: `UTC`

30초를 고른 이유는 첫 insight를 30~60초 안에 보여주기 위해서다.

### 9.7 freshness 기준

- freshness는 `마지막으로 수용된 ingest bucket timestamp` 기준으로 판단한다
- `stale` 후보: 최근 수용 bucket이 `90초` 이상 없음
- `down` 후보: 최근 수용 bucket이 `180초` 이상 없음
- starter heartbeat 성공/미수신은 freshness 기준에 들어가지 않는다

단, `waiting first data / unknown / idle / stale / down`의 상세 semantics와 truth table은
후속 contract 문서에서 단일 원천으로 정의한다.

### 9.8 최소 사용자 설정

사용자에게 요구하는 최소 설정은 다음 정도로 제한한다.

- `portal base url`
- `project key`
- `environment` 또는 그에 준하는 식별값

가능하면 다음은 자동으로 채운다.

- `application`: `spring.application.name` 활용
- `instance`: hostname / pod name / generated id 활용

Heartbeat는 이 설정으로 portal 도달성, key 유효성, metadata shape, starter liveness를 주기적으로 알려줄 수 있다. 그러나 첫 accepted bucket이 들어오기 전까지 application freshness/state는 계속 accepted bucket 기준 대기 상태다.

### 9.9 네트워크 요구사항

- inbound 노출을 요구하지 않는다
- outbound HTTPS만 가능하면 된다
- 사용자가 방화벽, scrape target, discovery 설정을 구성하게 하지 않는다

### 9.10 사용자 계정 인증과 session

MVP의 account signup과 login은 **GitHub OAuth only**다.

- GitHub OAuth 인증 성공 후 내부 `user/account` row를 생성하거나 기존 GitHub identity와 연결한다.
- GitHub user id 또는 provider subject를 외부 identity의 stable key로 사용한다.
- GitHub OAuth token은 GitHub API 호출이 필요한 경우에만 저장한다. MVP에서 GitHub API 호출이 필요 없다면 저장하지 않는다.
- GitHub OAuth token을 저장해야 한다면 암호화, 최소 scope, 만료/회전, 폐기 기준을 함께 명세한다.
- MVP 인증은 cookie 기반 server session을 사용하지 않는다.
- API 요청 인증은 `Authorization: Bearer <access_token>` header를 사용한다.
- Access Token은 stateless하게 검증 가능한 짧은 만료 JWT로 둔다.
- Refresh Token은 Bearer token으로 사용하되 rotation, 만료, revoke, reuse detection 기준을 둔다.
- Refresh Token 저장소는 Redis 같은 특정 인프라에 고정하지 않고 `token store` 추상 기준으로 둔다.
- 초기 구현 후보는 RDBMS에 hashed refresh token 또는 token family metadata를 저장하는 방식이다.
- Redis는 고성능 revoke list, distributed token state, reuse detection 최적화가 필요할 때 후속 선택지로 둔다.

GitHub OAuth token과 우리 서비스의 access token/refresh token은 서로 다른 token이다. Controller/API response, log, error에는 GitHub OAuth token, provider raw payload, secret을 노출하지 않는다. 일반 resource API response, log, error에도 우리 서비스 access token/refresh token을 노출하지 않는다. Token issuance/refresh response의 전달 방식은 별도 story에서 승인 기준으로 닫는다.

가입 실패나 거부 사유는 provider 내부 오류, raw payload, token 상태를 사용자에게 과도하게 드러내지 않는 일반화된 메시지로 처리한다.

---

## 10. 데이터 흐름

### 10.1 수집 흐름

1. starter가 Micrometer 계측을 활성화한다
2. 앱 요청과 런타임 지표를 30초 버킷으로 집계한다
3. route normalization과 low-cardinality guard를 적용한다
4. background worker가 summary + histogram bucket payload를 만든다
5. payload를 ingest API에 비동기로 전송한다
6. starter heartbeat scheduler는 별도 endpoint로 connection/liveness 신호를 보낸다
7. portal은 bucket payload를 검증하고 idempotent 저장한다
8. read model은 accepted bucket을 기준으로 app summary와 endpoint priority view를 계산한다

### 10.2 Portal이 하는 일

- 데이터를 모아두는 것
- app-level triage summary를 계산하는 것
- endpoint priority와 comparative evidence를 계산하는 것
- 최근 데이터 기준으로 상태와 다음 행동을 보여주는 것
- starter heartbeat/connection status를 accepted bucket 기반 application state와 분리해 보여주는 것

### 10.3 Portal이 하지 않는 일

- host app 메트릭 source를 동적으로 탐색하는 일
- 외부 모니터링 인프라를 대신 설치하는 일
- arbitrary PromQL이나 자유 질의를 제공하는 일
- 사용자 app 내부 동작을 실시간 제어하는 일

### 10.4 Insight / Rule Engine 모델

이 제품의 rule engine은 "원인을 진단하는 엔진"이 아니라
"운영자가 먼저 확인할 지점을 제안하는 triage 엔진"으로 정의한다.

핵심 원칙은 아래 한 줄로 요약한다.

> 룰 후보는 내부에서 많이 평가할 수 있지만, 화면에는 상위 `0~3개`만 보여준다.

이 원칙을 깨면 제품은 observability 초보자를 위한 첫 화면이 아니라
전문가용 rule catalog처럼 보이게 된다.

#### 평가 흐름

1. starter가 `30초` bucket을 보낸다
2. portal은 recent `15분 current`와 직전 `15분 baseline`을 구성한다
3. 모든 rule은 freshness, 최소 요청 수, baseline 충분성 guard를 먼저 통과해야 한다
4. rule은 candidate를 여러 개 만들 수 있지만, engine이 정렬 후 상위 `0~3개`만 카드로 노출한다
5. 카드 아래의 endpoint priority와 evidence는 같은 판단 흐름을 뒷받침해야 한다

#### Guard 원칙

모든 비교형 rule은 최소한 아래 세 축을 같이 본다.

- 최소 요청 수
- 절대 임계값
- baseline 대비 변화율

추가로 아래 guard를 공통 적용한다.

- freshness가 부족하면 stale/down 계열 평가를 우선하고, 일반 비교형 rule은 억제한다
- heartbeat telemetry는 freshness/rule guard 입력으로 쓰지 않는다
- baseline이 부족하면 변화율 rule을 끄고 절대값 기반 rule만 허용한다
- low-traffic endpoint는 card 후보에서 제외하거나 confidence를 낮춘다

#### Candidate 모델

각 rule은 내부적으로 아래 의미를 가진 candidate를 반환한다고 가정한다.

- `ruleId`
- `severity`
- `title`
- `summary`
- `recommendation`
- `affectedEndpoint`
- `score`
- `confidence`
- `evidence`

이 구조의 목적은 UI 장식이 아니라,
`무슨 일이 보였는가`, `어디를 먼저 볼까`, `왜 그렇게 말하는가`를
같은 단위로 묶기 위함이다.

#### 정렬 원칙

후보 정렬은 아래 순서를 기본값으로 둔다.

- `severity`
- `score`
- `confidence`
- `actionability`

대표 우선순위는 아래 정도를 기준으로 둔다.

- `down / stale`
- `error spike`
- `latency spike`
- `saturation hint`
- `traffic change`
- `info`

#### 문구 원칙

rule 문구는 진단이 아니라 확인 제안이어야 한다.

- 원인 확정 금지
- 가능성 표현 허용
- 다음 행동을 남기는 문장 우선

좋은 문구는 아래 성격을 가진다.

- "DB pool 사용률이 높고 응답 지연도 함께 증가했습니다. DB 연결 대기 가능성을 먼저 확인해보세요."
- "POST /orders에서 오류율과 응답 시간이 함께 증가했습니다. 이 endpoint를 먼저 확인해보세요."

나쁜 문구는 아래 성격을 가진다.

- "DB pool 고갈로 장애가 발생했습니다."
- "POST /orders가 장애 원인입니다."

#### MVP에서 실제로 닫는 rule 묶음

MVP는 rule catalog 전체를 구현하지 않는다.
아래 묶음만 실제 구현 대상으로 고정한다.

- availability
 - `service_down`
 - `service_stale`
 - `service_idle`
- error
 - global error spike
 - endpoint error spike
- latency
 - global latency spike
 - endpoint latency spike
- saturation hint
 - DB pool high + latency spike
 - CPU high + latency spike
 - heap high hint
- endpoint priority
 - slow/error/comparative evidence 기반 우선순위

여기서 중요한 점은 availability semantics를 rule engine이 새로 정의하면 안 된다는 것이다.
`waiting first data / unknown / idle / stale / down`의 단일 의미 원천은
후속 contract 문서와 Epic 3 경계에서 고정한다.

#### Post-MVP 또는 내부 catalog 후보

아래는 유효한 확장 후보지만, core MVP에 섞지 않는다.

- traffic spike / drop
- new endpoint detected
- intermittent reporting / missing bucket ratio
- baseline insufficient
- possible restart
- alert reuse rule

이 후보들은 backlog와 rule catalog로 남길 수는 있지만,
첫 화면의 카드 수와 사용자의 해석 부담을 늘려서는 안 된다.

---

## 11. 제품이 숨겨야 하는 복잡도

사용자는 아래를 몰라도 된다.

- background flush가 몇 초마다 도는지
- 실패한 payload를 몇 번 재시도하는지
- histogram bucket을 어떻게 직렬화하는지
- portal이 bucket distribution을 어떻게 구성하는지
- state summary가 어떤 내부 룰 순서로 계산되는지

이 복잡도는 사용자 문서가 아니라 starter와 portal 구현이 책임진다.

사용자에게 보여줄 약속은 아래 정도면 충분하다.

- starter를 추가한다
- project key를 넣는다
- 앱을 실행한다
- starter heartbeat/connection status와 first accepted bucket 수신 여부를 구분한다
- 30~60초 안에 첫 화면이 뜬다

---

## 12. 화면 원칙

### 12.1 메인 화면 순서

1. app 상태 요약
2. 핵심 숫자
 - request
 - error rate
 - source-scoped starter p95
3. triage summary
4. 어디부터 볼까 카드 0~3개
5. endpoint priority 목록

### 12.2 메인 화면에서 금지할 것

- 복잡한 query builder
- 태그 탐색 UI
- 차트만 많은 대시보드
- 사용자가 state semantics를 다시 해석하게 만드는 문장
- 앱 summary 화면에서 endpoint ranking을 다시 계산하는 행위
- heartbeat 상태를 application health 또는 accepted bucket freshness로 표현하는 행위

### 12.3 제품이 성공한 화면의 기준

- 10초 안에 구조가 이해된다
- 30초 안에 운영 질문이 무엇인지 보인다
- 60초 안에 어디부터 볼지 결정할 수 있다

### 12.4 Visual Reference Baseline

아래 legacy archive HTML은 `v0.8`에서도 여전히 유효한 시각 참조물로 유지한다.
이 파일들은 Prometheus 연동 구조가 아니라,
첫 화면의 정보 위계와 시각적 톤을 다루므로 direct ingest 전환 이후에도 계속 쓸 수 있다.

- `/Users/tlsdla1235/Desktop/study/관프/{output_folder}/planning-artifacts/legacy-archive/2026-05-06-prometheus-pivot/planning-artifacts/ux-design-directions.html`
- `/Users/tlsdla1235/Desktop/study/관프/{output_folder}/planning-artifacts/legacy-archive/2026-05-06-prometheus-pivot/planning-artifacts/ux-color-themes.html`

`ux-design-directions.html`에서 유지할 핵심은 아래다.

- `Signal Strip + Split Desk + Guided Recovery` 하이브리드
- 상태 semantics를 먼저 읽게 하는 구조
- insight `0~3개` 제한
- endpoint priority split
- failure-first recovery panel

`ux-color-themes.html`에서 유지할 핵심은 아래다.

- 기본 추천 테마는 `Calm Ops Desk`
- light-base, warm neutral, teal-accent 방향
- stale와 down을 분리된 hue로 다룬다
- 상태는 색만이 아니라 텍스트와 아이콘을 함께 쓴다

향후 `bmad-create-prd`, `bmad-create-ux-design`, `bmad-create-architecture` 프롬프트에는
위 두 HTML을 supporting visual reference로 함께 넣는다.

---

## 13. 2인 구현 전략

### 13.1 역할 분담 원칙

- 한 사람은 starter / ingest producer 경로를 책임진다
- 한 사람은 portal / read model / summary 경로를 책임진다
- 상태 의미와 comparative evidence는 contract 문서로 먼저 고정한다

### 13.2 추천 역할 분담

#### Engineer A — Starter Lane Owner

- starter auto-configuration
- Micrometer 계측 기본값
- route normalization
- 30초 bucket 집계
- background flush worker
- retry / backoff / bounded queue
- ingest envelope producer
- local demo app green path

#### Engineer B — Portal Lane Owner

- project/application 등록
- project key 검증
- ingest API
- bucket validation / idempotency
- bucket persistence
- histogram bucket distribution
- app summary read model
- endpoint priority read model
- dashboard / endpoint view

#### 공동 책임

- ingest envelope contract
- lifecycle state semantics
- AppTriageSummary contract
- EndpointPriority contract
- demo narrative

### 13.3 Vertical Slice 순서

1. `starter 추가 -> heartbeat 수신 여부와 first bucket 수신 여부 분리 표시 -> accepted bucket 기반 app state 표시`
2. `request/error/source-scoped starter p95 숫자 표시`
3. `top endpoint와 triage summary 표시`
4. `stale/down/recovery 흐름 표시`
5. `stretch drill-down 또는 알림`

---

## 14. 먼저 고정해야 할 계약

이번 버전에서 초기에 반드시 고정해야 하는 계약은 아래다.

1. `ingest-envelope`
 - accepted bucket source of truth for application freshness/state/read-model
 - summary metric + histogram bucket shape
 - `localPercentiles` instance-local 30초 bucket evidence
 - idempotency key
2. `time-buckets`
 - 30초 cadence
 - 15분 current / 15분 baseline
3. `state semantics`
 - waiting first data / unknown / idle / stale / down
 - freshness / recovery 기준
4. `AppTriageSummary`
 - app-level summary와 rationale
 - endpoint ranking 재계산 금지
5. `EndpointPriority`
 - comparative evidence
 - fallback rules
 - app-level semantics 재판정 금지
6. `starter heartbeat`
 - metric ingest와 분리된 endpoint
 - accepted bucket/state/read-model side effect 금지
 - starter connection status와 application state 분리

---

## 15. 성공 기준

### 15.1 사용자 경험 기준

- starter 설치에 5분 이상 걸리지 않는다
- Prometheus나 scrape 개념을 몰라도 첫 설정이 가능하다
- 앱 기동 후 30~60초 안에 첫 insight가 뜬다
- 메인 화면에서 "살아있나 / 느려졌나 / 어디부터 볼까"를 바로 이해할 수 있다

### 15.2 제품 가치 기준

- 설치 마찰이 낮다
- 작은 팀이 바로 써볼 수 있다
- 단순한 숫자 나열이 아니라 판단 문장이 나온다
- endpoint 우선순위가 실제 디버깅 시작점 역할을 한다

### 15.3 기술 성공 기준

- host app request path가 portal 장애에 의해 막히지 않는다
- 같은 bucket 중복 수신이 안전하게 처리된다
- starter-reported p95/p99가 source/instance scope와 함께 오해 없이 표시된다
- app/project 상위 scope는 percentile scalar가 모호할 때 bucket distribution으로 대체된다
- low-cardinality guard가 payload 폭발을 막는다

---

## 16. 리스크와 대응

### 리스크 1. direct ingest가 작은 Prometheus 재구현으로 번질 수 있다

대응:

- arbitrary query를 열지 않는다
- raw unrestricted timeseries를 목표로 삼지 않는다
- 지원 metric taxonomy를 명시적으로 제한한다

### 리스크 2. payload cardinality가 커질 수 있다

대응:

- route normalization을 강하게 건다
- 허용 tag를 제한한다
- 문서에 없는 자유 metric map을 금지한다

### 리스크 3. portal 장애가 앱에 영향을 줄 수 있다

대응:

- request thread 동기 전송 금지
- bounded queue + drop/backoff 허용
- ingest 실패는 앱 business flow와 분리한다

### 리스크 4. 멀티 인스턴스 정확도를 챙기다 구현량이 커질 수 있다

대응:

- `localPercentiles`를 starter-reported canonical percentile로 쓰되, source/instance scope를 잃지 않는다
- `localPercentiles` 숫자끼리 app/project/window p95/p99 rollup을 만들지 않는다
- histogram bucket distribution 범위를 HTTP 서버 지표에 한정한다
- 범용 분포 엔진으로 확대하지 않는다

### 리스크 5. 너무 많은 기능을 다시 넣고 싶어질 수 있다

대응:

- first insight를 돕지 않는 기능은 후순위로 민다
- direct ingest core가 닫히기 전에는 alerting / deep drill-down을 뒤로 미룬다

---

## 17. 이 명세서 기준으로 바뀌는 의사결정

1. Prometheus는 MVP 필수 의존성이 아니다
2. application freshness/state/read-model source-of-truth는 accepted bucket이다
3. p95/p99는 starter-reported canonical percentile이며, app/project/window 단일 scalar를 histogram merge로 새로 만들지 않는다
4. onboarding은 outbound HTTPS 기준으로 단순화한다
5. 사용자에게 `/actuator/prometheus`나 scrape 구조를 설명하지 않는다
6. portal은 query portal이 아니라 summary portal이다

---

## 18. 이전 버전 대비 달라진 점

- `v0.7`의 핵심 문장은 `Micrometer + Prometheus query`였다
- `v0.8`의 핵심 문장은 `Micrometer + direct ingest`다
- `v0.7`은 Prometheus를 통해 source of truth를 확보했다
- `v0.8`은 starter-first 설치 경험을 위해 accepted bucket ingest contract를 다시 채택한다
- `v0.7`은 selector / scrape / query가 중요했다
- `v0.8`은 project key / bucket ingest / summary read model이 중요하다

이 차이는 구현 디테일 변화가 아니라,
**제품 약속의 중심을 어디에 둘 것인가**에 대한 재선언이다.

---

## 19. 최종 한 줄 요약

**이 버전의 제품은 "Prometheus를 쉽게 붙이는 포털"이 아니라, "Spring Boot 앱에 starter를 붙이면 30~60초 안에 운영 첫 화면이 뜨는 Micrometer direct ingest 기반 triage 도구"다.**
