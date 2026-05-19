---
artifactType: architecture-input
projectName: Spring Boot 운영 첫 화면 포털
architectureStyle: Traditional MVC
status: mvc-version-input
date: 2026-05-09
---

# Infrastructure Input Notes - MVC Version

이 문서는 아직 최종 인프라 구현 결정이 아니다.

세부 아키텍처, 배포 구조, 스키마, 비동기 처리 방식을 정의할 때 반드시 다시 확인해야 하는 사용자 요구사항을 보존하기 위한 입력 노트다. MVC 버전에서도 인프라 전제는 기존과 동일하게 유지한다.

## 1. Persistence

Portal의 기본 영속 저장소는 PostgreSQL을 전제로 둔다.

- 로컬 개발 환경에서는 PostgreSQL을 Docker로 실행해서 portal이 붙는다.
- 배포 환경에서는 PostgreSQL을 AWS에서 별도 호스팅되는 관리형 또는 외부 DB로 연결한다.
- PostgreSQL은 project/application metadata, accepted bucket data, idempotency record, derived dashboard snapshot 저장을 담당하는 후보 저장소다.
- MVC 버전에서는 repository layer가 PostgreSQL 접근을 담당한다.

## 2. Redis

Redis는 필수 결정으로 잠그지 않고, 필요에 따라 도입하는 선택지로 둔다.

도입 후보 용도:

- 비동기 처리를 위한 message queue 또는 lightweight work queue
- ingest 후 snapshot refresh 작업 분리
- dashboard read model refresh scheduling 보조
- 짧은 TTL 기반 캐시 또는 coordination
- account auth의 고성능 revoke list, distributed token state, refresh token reuse detection 최적화

환경별 전제:

- 로컬 개발 환경에서는 Redis가 필요해질 경우 Docker로 실행한다.
- 배포 환경에서도 Redis를 붙인다면 우선 Docker로 실행하는 방향을 고려한다.
- Redis를 도입하지 않아도 MVP ingest/read path가 닫힐 수 있는지 먼저 검토한다.

Account/auth token store는 Redis 도입을 전제로 잠그지 않는다. 초기 구현 후보는 PostgreSQL/RDBMS에 hashed refresh token 또는 token family metadata를 저장하는 방식이다. Redis는 logout/revoke/reuse detection 요구가 고성능 distributed state를 필요로 할 때 후속 선택지로 검토한다.

## 3. Reverse Proxy And Web Server

Nginx를 web server / reverse proxy로 두고 portal HTTP traffic을 프록시하는 배포 구성을 고려한다.

MVP에서는 WebSocket을 필수 구성으로 보지 않는다. dashboard first-screen은 polling 또는 일반 HTTP refresh만으로도 충분한지 먼저 검토한다.

설계 시 확인할 것:

- dashboard HTTP API와 static asset routing
- TLS termination 위치
- portal application의 public base URL과 internal service URL 구분
- local docker compose와 배포 환경의 proxy 설정 차이

## 4. Local Development Runtime

로컬 개발 환경은 Docker 기반 주변 인프라를 전제로 둔다.

필수 후보:

- PostgreSQL

선택 후보:

- Redis
- Nginx

세부 아키텍처 정의 시 `docker-compose.yml` 또는 동등한 local runtime spec을 함께 정의한다.

## 5. Deployment Runtime

배포 환경의 초기 전제는 아래와 같다.

- Portal application은 별도 runtime으로 배포된다.
- PostgreSQL은 AWS 호스팅 DB에 연결한다.
- Redis가 필요하면 Docker로 붙이는 방향을 우선 검토한다.
- Nginx는 web server / reverse proxy 역할을 맡는다.

## 6. Open Architecture Questions

세부 설계 단계에서 아래 질문을 닫아야 한다.

- Redis 없이 PostgreSQL + in-process service 작업만으로 MVP 비동기 처리가 충분한가?
- Redis를 쓴다면 queue source of truth를 Redis로 둘지, PostgreSQL outbox와 조합할지?
- Account auth refresh token store를 RDBMS hashed token/token family metadata로 시작해도 rotation, revoke, reuse detection을 충분히 닫을 수 있는가?
- Redis가 필요해지는 account auth 조건은 revoke list 규모, multi-node token state, reuse detection latency 중 무엇인가?
- ingest accept와 snapshot refresh를 동기 transaction 안에서 처리할지, 비동기 job으로 분리할지?
- dashboard 업데이트는 polling 또는 Server-Sent Events 중 무엇으로 갈지? MVP에서는 polling을 우선 검토한다.
- Nginx 뒤에서 portal HTTP API와 static asset path를 어떻게 나눌지?
- 로컬 Docker compose와 배포 환경 설정을 얼마나 동일하게 유지할지?
