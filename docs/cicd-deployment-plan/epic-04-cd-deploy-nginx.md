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

## Stories

### S4.0 — 배포 헬스 엔드포인트 확정
- **As a** 운영자, **I want** CD가 확인할 안정적인 health endpoint를 갖고 싶다, **so that** 배포 성공/실패를 자동 판정한다.
- **AC**
  - Actuator `/actuator/health` 또는 직접 구현한 `/health` 중 하나를 선택하고 코드에 반영.
  - 외부에서 확인 가능한 응답은 비밀·내부 설정을 포함하지 않는다.
  - DB 연결까지 포함할지, 프로세스 기동만 볼지 운영 정책을 문서화.
  - Nginx, systemd, GitHub Actions deploy job이 같은 health URL을 사용한다.
- **의존:** E2

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
  - `.github/workflows/deploy.yml`: CI 통과한 jar 아티팩트를 EC2로 전송(SSM Run Command 또는 SSH).
  - E2 S2.5에서 정한 artifact 전달 방식을 사용하고, 배포 대상 jar의 commit SHA를 서버에 남긴다.
  - 배포 절차: 전송 → `systemctl stop` → jar 교체(이전 버전 백업) → `systemctl start` → 헬스체크.
  - 단일 인스턴스 stop/start 동안 짧은 502/503 또는 연결 실패가 발생할 수 있음을 운영 런북에 명시한다.
  - 무중단 배포를 흉내 내기 위한 background dual-port, local proxy switch, blue/green 스크립트는 이번 범위에서 만들지 않는다.
  - AWS 자격증명은 GitHub OIDC(권장) 또는 Secrets로 주입, 최소권한.
  - GitHub Environment(`production`) 보호 규칙으로 수동 승인 게이트(선택).
- **의존:** S4.1, E2(S2.5)

### S4.3 — 헬스체크 & 배포 검증(롤백 안전망)
- **As a** 운영자, **I want** 배포 후 헬스체크가 실패하면 알게 되길 원한다, **so that** 장애 배포를 막는다.
- **AC**
  - portal 헬스 엔드포인트(예: actuator/health 또는 기존 상태 API)로 배포 후 200 확인.
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

### S4.5 — TLS 인증서 (HTTPS)
- **As a** 사용자, **I want** HTTPS로 접속하고 싶다, **so that** 통신이 암호화된다.
- **AC**
  - 도메인 연결(Route 53 또는 외부 DNS) 후 Let's Encrypt(certbot) 또는 ACM+(ALB 사용 시) 인증서 적용.
  - 자동 갱신 설정(certbot timer) 및 갱신 검증.
  - OAuth redirect-uri(prod)가 HTTPS 도메인과 일치(E1 S1.2 연계).
- **의존:** S4.4, E1(S1.2)

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
