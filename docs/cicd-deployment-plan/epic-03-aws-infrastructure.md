# Epic 3 — AWS 인프라 프로비저닝

> **목표:** 운영 구동에 필요한 AWS 자원을 최소 사양으로 준비한다 —
> **RDS PostgreSQL(최소 인스턴스)**, **EC2 small 인스턴스**, 네트워크/보안그룹, 기존 SQS 연계.

**선행:** E1 · **후행:** E4 · **리전:** `ap-northeast-2` (Seoul, 기존 SQS와 동일)

## 설계 요약

- **DB:** Amazon RDS for PostgreSQL, `db.t4g.micro`(최소 사양, 가용 시 free-tier 후보). 단일 AZ, gp3 최소 스토리지.
- **App 서버:** EC2 `t3.small` 또는 `t4g.small`(ARM, 비용↓). Amazon Linux 2023 또는 Ubuntu LTS. JDK 17 설치.
- **네트워크:** EC2는 public subnet(Nginx 노출), RDS는 private subnet(외부 비공개). 보안그룹으로 5432는 EC2 SG에서만 허용.
- **시크릿:** SSM Parameter Store(SecureString)에 prod 환경변수 보관 → EC2 인스턴스 프로파일로 조회. (E1 인터페이스 사용)
- **Queue:** 운영 source queue와 DLQ를 명시하고, EC2 인스턴스 프로파일에 필요한 SQS 권한만 부여.

## Stories

### S3.1 — RDS PostgreSQL(최소 사양) 프로비저닝
- **As a** 운영자, **I want** 가장 작은 RDS PostgreSQL 인스턴스를 띄우고 싶다, **so that** 비용 최소로 운영 DB를 확보한다.
- **AC**
  - `db.t4g.micro`, PostgreSQL(현재 portal 호환 버전), 단일 AZ, gp3 최소 스토리지, 자동 백업 활성.
  - DB 파라미터/타임존 점검, 마스터 자격증명은 SSM SecureString에 저장.
  - `publicly accessible = false`.
- **검증:** EC2(또는 임시 bastion)에서 `psql`/JDBC 접속 성공.

### S3.2 — 네트워크 & 보안그룹
- **As a** 운영자, **I want** EC2↔RDS만 통신하고 외부엔 80/443만 열고 싶다, **so that** 공격면을 줄인다.
- **AC**
  - EC2 SG: inbound 80/443(0.0.0.0/0), 22(관리 IP 한정 또는 SSM Session Manager 사용 시 닫기).
  - RDS SG: inbound 5432는 EC2 SG에서만 허용.
  - VPC/subnet 구성 문서화(public: EC2, private: RDS).
- **의존:** S3.1

### S3.3 — EC2 small 인스턴스 + 런타임 베이스 이미지
- **As a** 운영자, **I want** JDK17이 깔린 EC2 small을 갖고 싶다, **so that** portal jar를 바로 구동한다.
- **AC**
  - `t3.small`/`t4g.small` 기동, OS·JDK17 설치, 스왑/디스크 기본 점검.
  - 배포용 시스템 사용자(`appuser`) 및 디렉터리(`/opt/observation`) 생성.
  - IAM 인스턴스 프로파일: SSM Parameter 읽기 + SQS 접근(기존 인입 버퍼) 권한.
- **의존:** S3.2

### S3.4 — DB 마이그레이션 적용 경로 확정
- **As a** 개발자, **I want** Flyway 마이그레이션이 RDS에 안전하게 적용되길 원한다, **so that** 스키마가 일관된다.
- **AC**
  - 앱 기동 시 Flyway가 RDS에 `V001~` 적용되는지 검증(또는 배포 전 별도 마이그레이션 스텝 결정).
  - 운영 DB 대상 마이그레이션 실행 정책(앱 기동 시 자동 vs CD 단계 분리) 문서화.
- **의존:** S3.1, S3.3

### S3.5 — 운영 SQS source queue / DLQ 확정
- **As a** 운영자, **I want** 운영 인입 버퍼 queue와 DLQ를 명확히 갖고 싶다, **so that** ingest 장애와 poison message를 분리해 볼 수 있다.
- **AC**
  - `ap-northeast-2`의 source queue URL, DLQ URL, redrive policy, visibility timeout을 문서화.
  - `PORTAL_INGEST_BUFFER_MODE=sqs`, `PORTAL_INGEST_BUFFER_SQS_QUEUE_URL`, `PORTAL_INGEST_BUFFER_WORKER_DLQ_URL`, `AWS_REGION` 주입 경로 확정.
  - EC2 인스턴스 프로파일은 source queue `SendMessage/ReceiveMessage/DeleteMessage/GetQueueAttributes`, DLQ `SendMessage` 등 필요한 권한만 부여.
  - 운영 smoke에서 enqueue, worker receive/delete, malformed/conflict DLQ 이동을 1회 검증.
- **의존:** S3.3

### S3.6 — 시크릿/파라미터 저장소 구성 (SSM)
- **As a** 운영자, **I want** prod 시크릿을 SSM SecureString에 두고 싶다, **so that** 배포 시 안전하게 주입된다.
- **AC**
  - E1에서 정의한 prod 환경변수 키들을 `/observation/prod/*` 경로로 SSM에 저장.
  - EC2가 기동 시(또는 배포 스크립트가) SSM에서 읽어 환경변수로 export.
  - 접근 권한은 인스턴스 프로파일로 최소권한 부여.
- **의존:** S3.3, E1

### S3.7 — 인프라 구성 문서화(또는 IaC)
- **As a** 팀, **I want** 인프라 구성이 재현 가능하길 원한다, **so that** 재구축/인수인계가 쉽다.
- **AC**
  - 최소: 콘솔 구성 단계와 자원 식별자를 `infra-runbook.md`로 기록.
  - 권장: Terraform/CloudFormation으로 RDS·EC2·SG·SSM 정의(선택, 후속 가능).
- **의존:** S3.1~S3.6

## Epic 완료 조건 (DoD)
- RDS(최소)와 EC2 small이 기동되어 상호 접속 가능.
- 80/443만 외부 노출, 5432는 내부 한정.
- 운영 SQS source queue/DLQ와 EC2 최소권한 IAM이 검증되어 있다.
- prod 시크릿이 SSM에 안전 보관되고 EC2에서 조회 가능.
- portal jar를 수동으로 1회 기동해 RDS 연결 + Flyway 적용을 검증(E4 자동화의 발판).
