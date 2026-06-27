# Epic 3 AWS 인프라 Runbook

이 문서는 Epic 3에서 운영 portal을 띄우기 위한 AWS 인프라 결정값, 프로비저닝 순서, 생성 후 handoff 값을 기록한다.
시크릿 원문과 private key 원문은 이 문서, 로그, 채팅, Git 추적 파일에 남기지 않는다.

## 현재 상태

- 작업 브랜치: `codex/cicd-deployment-plan`
- AWS account: `491013322019`
- AWS caller: `arn:aws:iam::491013322019:user/obser-service-infra-operator`
- Region: `ap-northeast-2`
- 조회 시점의 current public IP: `110.34.75.186/32`
- AL2023 ARM64 AMI: `ami-0cf2079469a00abef`
- AWS RDS PostgreSQL 기본 engine version: `18.3`
- Epic 3 RDS engine version: `16.14` (테스트 기준과 맞춘 PostgreSQL 16 계열 최신 orderable version)
- `db.t4g.micro` + `gp3` 최소 스토리지: 20 GiB
- 로컬 SSH key pair는 생성 완료. private key 원문은 출력하지 않았고 AWS에는 public key만 import했다.
- VPC, subnet, route table, Internet Gateway, Security Group, SQS, IAM role/profile, RDS subnet group, RDS, EC2, Elastic IP는 생성 완료.
- RDS master password와 app password는 SSM SecureString으로 저장했고 원문은 출력하지 않았다.
- EC2 SSM Agent는 `Online`이며, instance role로 SSM/SQS 접근을 검증했다.
- 가비아 DNS A record는 `portal.observstarter.cloud -> 3.34.77.80`로 등록됐고 `dig` 확인을 완료했다.
- 운영 GitHub OAuth App 값과 signing key는 SSM에 입력 완료됐다. secret 원문은 문서화하지 않는다.

## 승인 게이트

아래 작업은 비용 발생, 외부 노출, IAM 권한 변경이 있으므로 실행 전에 운영자 확인을 받는다.

- VPC, subnet, route table, Internet Gateway 생성
- Security Group 생성과 inbound 80/443/22, 5432 rule 추가
- EC2 key pair import, EC2 `t4g.small` instance, Elastic IP 생성/연결
- RDS PostgreSQL `db.t4g.micro` instance, DB subnet group 생성
- IAM role, instance profile, inline policy, managed policy attach
- SQS source queue/DLQ 생성과 redrive policy 설정
- SSM Parameter Store 값 생성/수정

비용 영향은 단일 EC2 `t4g.small`, RDS `db.t4g.micro` + gp3 20 GiB, Elastic IP, SQS request/storage가 중심이다.
보안 영향은 EC2 80/443 public open, SSH 22 current public IP `/32` open, EC2 instance role의 `/observation/prod/` SSM read와 지정 SQS 접근 권한 부여다.

## 결정값

| 항목 | 값 |
| --- | --- |
| VPC name | `observation-prod-vpc` |
| VPC CIDR | `10.30.0.0/16` |
| public subnet A | `10.30.0.0/24` (`ap-northeast-2a`) |
| public subnet C | `10.30.1.0/24` (`ap-northeast-2c`) |
| private subnet A | `10.30.10.0/24` (`ap-northeast-2a`) |
| private subnet C | `10.30.11.0/24` (`ap-northeast-2c`) |
| NAT Gateway | 생성하지 않음 |
| Internet Gateway | 생성 |
| EC2 | `t4g.small`, Amazon Linux 2023 ARM64 |
| EC2 name | `observation-prod-portal` |
| AWS key pair | `observation-prod-portal` |
| EC2 SSH key | `~/.ssh/observation-prod-portal` local private key, `~/.ssh/observation-prod-portal.pub` public key |
| EC2 inbound | `80/tcp`, `443/tcp` from `0.0.0.0/0`; `22/tcp` from current public IP `/32` |
| RDS | PostgreSQL `16.14`, `db.t4g.micro`, single AZ, gp3 20 GiB, `publicly-accessible=false` |
| RDS identifier | `observation-prod-postgres` |
| RDS DB name | `observation` |
| RDS master username | `observation_admin` |
| RDS app username | `observation_app` |
| SQS source queue | `observation-prod-ingest-source` |
| SQS DLQ | `observation-prod-ingest-dlq` |
| SQS long polling | `20` seconds |
| SQS visibility timeout | `60` seconds |
| SQS redrive max receive count | `5` |
| SSM prefix | `/observation/prod/` |
| 운영 URL 기준 | `https://portal.observstarter.cloud` |
| DNS provider | 가비아 네임서버 |

## 생성된 리소스

| 항목 | 값 |
| --- | --- |
| AWS key pair ID | `key-0a5643f7b18bebe50` |
| VPC ID | `vpc-0821ede465eb5dcfa` |
| public subnet A | `subnet-0c8e4a95cd777be78` |
| public subnet C | `subnet-0059a6a3e23bb091c` |
| private subnet A | `subnet-09922b0cc52176351` |
| private subnet C | `subnet-066e5290c7e8762a5` |
| Internet Gateway | `igw-03022d819802b996f` |
| public route table | `rtb-04f9ee056a497f0a5` |
| private route table | `rtb-0755643dcad522bb2` |
| EC2 security group | `sg-035d3bbbadb239dba` |
| RDS security group | `sg-03de3fa644e0f10ee` |
| SQS source queue URL | `https://sqs.ap-northeast-2.amazonaws.com/491013322019/observation-prod-ingest-source` |
| SQS source queue ARN | `arn:aws:sqs:ap-northeast-2:491013322019:observation-prod-ingest-source` |
| SQS DLQ URL | `https://sqs.ap-northeast-2.amazonaws.com/491013322019/observation-prod-ingest-dlq` |
| SQS DLQ ARN | `arn:aws:sqs:ap-northeast-2:491013322019:observation-prod-ingest-dlq` |
| EC2 role ARN | `arn:aws:iam::491013322019:role/observation-prod-portal-role` |
| EC2 instance profile ARN | `arn:aws:iam::491013322019:instance-profile/observation-prod-portal-profile` |
| RDS subnet group | `observation-prod-rds-subnet-group` |
| RDS instance | `observation-prod-postgres` |
| RDS endpoint | `observation-prod-postgres.c9e6es0mquhz.ap-northeast-2.rds.amazonaws.com` |
| EC2 instance ID | `i-06defdb40c40905d9` |
| EC2 private IP | `10.30.0.50` |
| Elastic IP allocation ID | `eipalloc-011a817ed51b43e9e` |
| Elastic IP public IP | `3.34.77.80` |
| 가비아 DNS A record | `portal.observstarter.cloud -> 3.34.77.80` |

## SQS 설정 근거

운영 SQS 설정은 repo의 local/LocalStack SQS 기본값과 의미상 동일하게 맞춘다.

- `portal.ingest.buffer.worker.long-poll-seconds=20`
- `portal.ingest.buffer.worker.visibility-timeout=60s`
- `portal.ingest.buffer.worker.max-receive-count=5`
- `portal.ingest.buffer.worker.max-messages-per-poll=10`
- `portal.ingest.buffer.worker.max-batch-size=10`
- `portal.ingest.buffer.worker.max-batch-age=2s`

Source queue에는 SQS redrive policy도 `maxReceiveCount=5`로 건다. 애플리케이션이 malformed/conflict message를 sanitized application DLQ로 직접 보내고 source message를 삭제하는 경로와, worker 장애로 source message가 반복 수신될 때 SQS redrive가 DLQ로 넘기는 경로를 모두 보존한다.

## 프로비저닝 명령

명령은 `ap-northeast-2`와 account `491013322019` 기준이다. `set -euo pipefail`을 켠 shell에서 실행한다.

```bash
export AWS_REGION=ap-northeast-2
export AWS_DEFAULT_REGION=ap-northeast-2
export ACCOUNT_ID=491013322019
export SSH_CIDR=110.34.75.186/32
export AMI_ID=ami-0cf2079469a00abef
export PG_ENGINE_VERSION=16.14
```

재실행 시에는 먼저 current public IP와 AL2023 ARM64 AMI를 다시 확인한다.

```bash
export SSH_CIDR="$(curl -fsS https://checkip.amazonaws.com | tr -d '\n')/32"
export AMI_ID="$(aws ssm get-parameter \
  --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 \
  --query 'Parameter.Value' \
  --output text)"
export PG_ENGINE_VERSION="$(aws rds describe-orderable-db-instance-options \
  --engine postgres \
  --db-instance-class db.t4g.micro \
  --query 'OrderableDBInstanceOptions[?starts_with(EngineVersion, `16.`) && StorageType==`gp3`].EngineVersion' \
  --output json \
  | jq -r 'sort_by(split(".") | map(tonumber)) | reverse | .[0]')"
```

### 1. 로컬 SSH key 준비

로컬 private key 원문은 절대 출력하거나 커밋하지 않는다. 파일이 없을 때만 생성한다.

```bash
test -f ~/.ssh/observation-prod-portal || ssh-keygen -t ed25519 -a 100 \
  -f ~/.ssh/observation-prod-portal \
  -C observation-prod-portal \
  -N ''
chmod 600 ~/.ssh/observation-prod-portal
chmod 644 ~/.ssh/observation-prod-portal.pub
```

AWS에는 public key만 import한다.

```bash
aws ec2 import-key-pair \
  --key-name observation-prod-portal \
  --public-key-material fileb://$HOME/.ssh/observation-prod-portal.pub \
  --tag-specifications 'ResourceType=key-pair,Tags=[{Key=Name,Value=observation-prod-portal},{Key=Project,Value=observation},{Key=Environment,Value=prod}]'
```

### 2. VPC, subnet, route 구성

```bash
VPC_ID="$(aws ec2 create-vpc \
  --cidr-block 10.30.0.0/16 \
  --tag-specifications 'ResourceType=vpc,Tags=[{Key=Name,Value=observation-prod-vpc},{Key=Project,Value=observation},{Key=Environment,Value=prod}]' \
  --query 'Vpc.VpcId' \
  --output text)"

aws ec2 modify-vpc-attribute --vpc-id "$VPC_ID" --enable-dns-support '{"Value":true}'
aws ec2 modify-vpc-attribute --vpc-id "$VPC_ID" --enable-dns-hostnames '{"Value":true}'

PUBLIC_SUBNET_A_ID="$(aws ec2 create-subnet \
  --vpc-id "$VPC_ID" \
  --cidr-block 10.30.0.0/24 \
  --availability-zone ap-northeast-2a \
  --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=observation-prod-public-a},{Key=Project,Value=observation},{Key=Environment,Value=prod},{Key=Tier,Value=public}]' \
  --query 'Subnet.SubnetId' \
  --output text)"
PUBLIC_SUBNET_C_ID="$(aws ec2 create-subnet \
  --vpc-id "$VPC_ID" \
  --cidr-block 10.30.1.0/24 \
  --availability-zone ap-northeast-2c \
  --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=observation-prod-public-c},{Key=Project,Value=observation},{Key=Environment,Value=prod},{Key=Tier,Value=public}]' \
  --query 'Subnet.SubnetId' \
  --output text)"
PRIVATE_SUBNET_A_ID="$(aws ec2 create-subnet \
  --vpc-id "$VPC_ID" \
  --cidr-block 10.30.10.0/24 \
  --availability-zone ap-northeast-2a \
  --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=observation-prod-private-a},{Key=Project,Value=observation},{Key=Environment,Value=prod},{Key=Tier,Value=private}]' \
  --query 'Subnet.SubnetId' \
  --output text)"
PRIVATE_SUBNET_C_ID="$(aws ec2 create-subnet \
  --vpc-id "$VPC_ID" \
  --cidr-block 10.30.11.0/24 \
  --availability-zone ap-northeast-2c \
  --tag-specifications 'ResourceType=subnet,Tags=[{Key=Name,Value=observation-prod-private-c},{Key=Project,Value=observation},{Key=Environment,Value=prod},{Key=Tier,Value=private}]' \
  --query 'Subnet.SubnetId' \
  --output text)"

aws ec2 modify-subnet-attribute --subnet-id "$PUBLIC_SUBNET_A_ID" --map-public-ip-on-launch
aws ec2 modify-subnet-attribute --subnet-id "$PUBLIC_SUBNET_C_ID" --map-public-ip-on-launch

IGW_ID="$(aws ec2 create-internet-gateway \
  --tag-specifications 'ResourceType=internet-gateway,Tags=[{Key=Name,Value=observation-prod-igw},{Key=Project,Value=observation},{Key=Environment,Value=prod}]' \
  --query 'InternetGateway.InternetGatewayId' \
  --output text)"
aws ec2 attach-internet-gateway --internet-gateway-id "$IGW_ID" --vpc-id "$VPC_ID"

PUBLIC_RT_ID="$(aws ec2 create-route-table \
  --vpc-id "$VPC_ID" \
  --tag-specifications 'ResourceType=route-table,Tags=[{Key=Name,Value=observation-prod-public-rt},{Key=Project,Value=observation},{Key=Environment,Value=prod}]' \
  --query 'RouteTable.RouteTableId' \
  --output text)"
aws ec2 create-route --route-table-id "$PUBLIC_RT_ID" --destination-cidr-block 0.0.0.0/0 --gateway-id "$IGW_ID"
aws ec2 associate-route-table --route-table-id "$PUBLIC_RT_ID" --subnet-id "$PUBLIC_SUBNET_A_ID"
aws ec2 associate-route-table --route-table-id "$PUBLIC_RT_ID" --subnet-id "$PUBLIC_SUBNET_C_ID"

PRIVATE_RT_ID="$(aws ec2 create-route-table \
  --vpc-id "$VPC_ID" \
  --tag-specifications 'ResourceType=route-table,Tags=[{Key=Name,Value=observation-prod-private-rt},{Key=Project,Value=observation},{Key=Environment,Value=prod}]' \
  --query 'RouteTable.RouteTableId' \
  --output text)"
aws ec2 associate-route-table --route-table-id "$PRIVATE_RT_ID" --subnet-id "$PRIVATE_SUBNET_A_ID"
aws ec2 associate-route-table --route-table-id "$PRIVATE_RT_ID" --subnet-id "$PRIVATE_SUBNET_C_ID"
```

### 3. Security Group 구성

```bash
EC2_SG_ID="$(aws ec2 create-security-group \
  --group-name observation-prod-portal-sg \
  --description 'Observation prod portal EC2 ingress' \
  --vpc-id "$VPC_ID" \
  --tag-specifications 'ResourceType=security-group,Tags=[{Key=Name,Value=observation-prod-portal-sg},{Key=Project,Value=observation},{Key=Environment,Value=prod}]' \
  --query 'GroupId' \
  --output text)"

RDS_SG_ID="$(aws ec2 create-security-group \
  --group-name observation-prod-rds-sg \
  --description 'Observation prod RDS ingress from portal EC2 only' \
  --vpc-id "$VPC_ID" \
  --tag-specifications 'ResourceType=security-group,Tags=[{Key=Name,Value=observation-prod-rds-sg},{Key=Project,Value=observation},{Key=Environment,Value=prod}]' \
  --query 'GroupId' \
  --output text)"

aws ec2 authorize-security-group-ingress \
  --group-id "$EC2_SG_ID" \
  --ip-permissions \
    "IpProtocol=tcp,FromPort=80,ToPort=80,IpRanges=[{CidrIp=0.0.0.0/0,Description=HTTP for Epic 4 Nginx}]" \
    "IpProtocol=tcp,FromPort=443,ToPort=443,IpRanges=[{CidrIp=0.0.0.0/0,Description=HTTPS for Epic 4 Nginx}]" \
    "IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges=[{CidrIp=$SSH_CIDR,Description=SSH operator current public IP}]"

aws ec2 authorize-security-group-ingress \
  --group-id "$RDS_SG_ID" \
  --ip-permissions \
    "IpProtocol=tcp,FromPort=5432,ToPort=5432,UserIdGroupPairs=[{GroupId=$EC2_SG_ID,Description=PostgreSQL from portal EC2 SG only}]"
```

### 4. SQS source queue와 DLQ

```bash
DLQ_URL="$(aws sqs create-queue \
  --queue-name observation-prod-ingest-dlq \
  --attributes ReceiveMessageWaitTimeSeconds=20,VisibilityTimeout=60 \
  --tags Project=observation,Environment=prod,Name=observation-prod-ingest-dlq \
  --query 'QueueUrl' \
  --output text)"

DLQ_ARN="$(aws sqs get-queue-attributes \
  --queue-url "$DLQ_URL" \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' \
  --output text)"

cat > /tmp/observation-prod-source-queue-attributes.json <<JSON
{
  "ReceiveMessageWaitTimeSeconds": "20",
  "VisibilityTimeout": "60",
  "RedrivePolicy": "{\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":\"5\"}"
}
JSON

SOURCE_QUEUE_URL="$(aws sqs create-queue \
  --queue-name observation-prod-ingest-source \
  --attributes file:///tmp/observation-prod-source-queue-attributes.json \
  --tags Project=observation,Environment=prod,Name=observation-prod-ingest-source \
  --query 'QueueUrl' \
  --output text)"

SOURCE_QUEUE_ARN="$(aws sqs get-queue-attributes \
  --queue-url "$SOURCE_QUEUE_URL" \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' \
  --output text)"
```

### 5. IAM role과 instance profile

```bash
cat > /tmp/observation-prod-portal-trust-policy.json <<'JSON'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ec2.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
JSON

aws iam create-role \
  --role-name observation-prod-portal-role \
  --assume-role-policy-document file:///tmp/observation-prod-portal-trust-policy.json \
  --tags Key=Name,Value=observation-prod-portal-role Key=Project,Value=observation Key=Environment,Value=prod

aws iam attach-role-policy \
  --role-name observation-prod-portal-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore

cat > /tmp/observation-prod-portal-policy.json <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadObservationProdParameters",
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters",
        "ssm:GetParametersByPath"
      ],
      "Resource": "arn:aws:ssm:${AWS_REGION}:${ACCOUNT_ID}:parameter/observation/prod/*"
    },
    {
      "Sid": "UseSourceQueue",
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:ChangeMessageVisibility",
        "sqs:GetQueueAttributes",
        "sqs:GetQueueUrl"
      ],
      "Resource": "$SOURCE_QUEUE_ARN"
    },
    {
      "Sid": "UseApplicationDlq",
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:GetQueueAttributes",
        "sqs:GetQueueUrl"
      ],
      "Resource": "$DLQ_ARN"
    }
  ]
}
JSON

aws iam put-role-policy \
  --role-name observation-prod-portal-role \
  --policy-name observation-prod-portal-runtime \
  --policy-document file:///tmp/observation-prod-portal-policy.json

aws iam create-instance-profile \
  --instance-profile-name observation-prod-portal-profile \
  --tags Key=Name,Value=observation-prod-portal-profile Key=Project,Value=observation Key=Environment,Value=prod

aws iam add-role-to-instance-profile \
  --instance-profile-name observation-prod-portal-profile \
  --role-name observation-prod-portal-role

sleep 10
```

### 6. RDS subnet group과 DB instance

DB master password는 출력하지 않는다. 운영자가 직접 안전하게 입력하거나, 로컬 shell 변수에만 생성해 사용한다.
아래 예시는 값을 echo하지 않고 `read -s`로 입력한다.

```bash
read -r -s -p 'RDS master password: ' DB_MASTER_PASSWORD
printf '\n'

DB_SUBNET_GROUP_NAME=observation-prod-rds-subnet-group
aws rds create-db-subnet-group \
  --db-subnet-group-name "$DB_SUBNET_GROUP_NAME" \
  --db-subnet-group-description 'Observation prod private RDS subnet group' \
  --subnet-ids "$PRIVATE_SUBNET_A_ID" "$PRIVATE_SUBNET_C_ID" \
  --tags Key=Name,Value=observation-prod-rds-subnet-group Key=Project,Value=observation Key=Environment,Value=prod

aws rds create-db-instance \
  --db-instance-identifier observation-prod-postgres \
  --db-instance-class db.t4g.micro \
  --engine postgres \
  --engine-version "$PG_ENGINE_VERSION" \
  --allocated-storage 20 \
  --storage-type gp3 \
  --db-name observation \
  --master-username observation_admin \
  --master-user-password "$DB_MASTER_PASSWORD" \
  --vpc-security-group-ids "$RDS_SG_ID" \
  --db-subnet-group-name "$DB_SUBNET_GROUP_NAME" \
  --no-publicly-accessible \
  --backup-retention-period 7 \
  --no-multi-az \
  --deletion-protection \
  --tags Key=Name,Value=observation-prod-postgres Key=Project,Value=observation Key=Environment,Value=prod

aws rds wait db-instance-available --db-instance-identifier observation-prod-postgres
RDS_ENDPOINT="$(aws rds describe-db-instances \
  --db-instance-identifier observation-prod-postgres \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text)"
```

### 7. EC2 instance와 Elastic IP

```bash
cat > /tmp/observation-prod-portal-user-data.sh <<'EOF'
#!/bin/bash
set -euxo pipefail
dnf update -y
dnf install -y java-17-amazon-corretto-headless postgresql15 awscli jq
useradd --system --create-home --home-dir /opt/observation --shell /sbin/nologin appuser || true
install -d -o appuser -g appuser -m 0755 /opt/observation/releases
install -d -o appuser -g appuser -m 0755 /opt/observation/current
install -d -o root -g root -m 0750 /etc/observation
EOF

INSTANCE_ID="$(aws ec2 run-instances \
  --image-id "$AMI_ID" \
  --instance-type t4g.small \
  --key-name observation-prod-portal \
  --subnet-id "$PUBLIC_SUBNET_A_ID" \
  --security-group-ids "$EC2_SG_ID" \
  --iam-instance-profile Name=observation-prod-portal-profile \
  --associate-public-ip-address \
  --user-data file:///tmp/observation-prod-portal-user-data.sh \
  --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=16,VolumeType=gp3,DeleteOnTermination=true,Encrypted=true}' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=observation-prod-portal},{Key=Project,Value=observation},{Key=Environment,Value=prod}]' 'ResourceType=volume,Tags=[{Key=Name,Value=observation-prod-portal-root},{Key=Project,Value=observation},{Key=Environment,Value=prod}]' \
  --query 'Instances[0].InstanceId' \
  --output text)"

aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"

ALLOC_ID="$(aws ec2 allocate-address \
  --domain vpc \
  --tag-specifications 'ResourceType=elastic-ip,Tags=[{Key=Name,Value=observation-prod-portal-eip},{Key=Project,Value=observation},{Key=Environment,Value=prod}]' \
  --query 'AllocationId' \
  --output text)"
aws ec2 associate-address --instance-id "$INSTANCE_ID" --allocation-id "$ALLOC_ID"
ELASTIC_IP="$(aws ec2 describe-addresses --allocation-ids "$ALLOC_ID" --query 'Addresses[0].PublicIp' --output text)"
```

가비아 DNS에는 아래 A record를 추가한다.

| Host | Type | Value | TTL |
| --- | --- | --- | --- |
| `portal` | `A` | `3.34.77.80` | `300` 권장 |

Root `observstarter.cloud`는 나중에 소개/문서 페이지로 남길 수 있으므로 Epic 3에서는 `portal.observstarter.cloud`만 운영 앱 URL로 문서화한다.

### 8. SSM Parameter Store

Parameter 이름은 E1의 환경변수 interface와 동일한 leaf name을 사용한다. non-secret은 `String`, secret은 `SecureString`으로 저장한다.
아래 non-secret 값은 생성 완료했다.

```bash
aws ssm put-parameter --name /observation/prod/SPRING_PROFILES_ACTIVE --type String --value prod --overwrite
aws ssm put-parameter --name /observation/prod/SERVER_PORT --type String --value 8080 --overwrite
aws ssm put-parameter --name /observation/prod/SERVER_FORWARD_HEADERS_STRATEGY --type String --value framework --overwrite
aws ssm put-parameter --name /observation/prod/AWS_REGION --type String --value ap-northeast-2 --overwrite
aws ssm put-parameter --name /observation/prod/AWS_DEFAULT_REGION --type String --value ap-northeast-2 --overwrite

aws ssm put-parameter --name /observation/prod/SPRING_DATASOURCE_URL --type String \
  --value "jdbc:postgresql://${RDS_ENDPOINT}:5432/observation" --overwrite
aws ssm put-parameter --name /observation/prod/SPRING_DATASOURCE_USERNAME --type String \
  --value observation_app --overwrite

aws ssm put-parameter --name /observation/prod/PORTAL_AUTH_GITHUB_REDIRECT_URI --type String \
  --value https://portal.observstarter.cloud/api/auth/github/callback --overwrite
aws ssm put-parameter --name /observation/prod/PORTAL_AUTH_GITHUB_HOMEPAGE_URL --type String \
  --value https://portal.observstarter.cloud/dashboard/ --overwrite

aws ssm put-parameter --name /observation/prod/PORTAL_INGEST_BUFFER_MODE --type String --value sqs --overwrite
aws ssm put-parameter --name /observation/prod/PORTAL_INGEST_BUFFER_SQS_QUEUE_URL --type String \
  --value "$SOURCE_QUEUE_URL" --overwrite
aws ssm put-parameter --name /observation/prod/PORTAL_INGEST_BUFFER_WORKER_ENABLED --type String --value true --overwrite
aws ssm put-parameter --name /observation/prod/PORTAL_INGEST_BUFFER_WORKER_DLQ_URL --type String \
  --value "$DLQ_URL" --overwrite
aws ssm put-parameter --name /observation/prod/PORTAL_INGEST_BUFFER_WORKER_LONG_POLL_SECONDS --type String --value 20 --overwrite
aws ssm put-parameter --name /observation/prod/PORTAL_INGEST_BUFFER_WORKER_VISIBILITY_TIMEOUT --type String --value 60s --overwrite
aws ssm put-parameter --name /observation/prod/PORTAL_INGEST_BUFFER_WORKER_MAX_RECEIVE_COUNT --type String --value 5 --overwrite
```

아래 secret 값은 운영자가 직접 입력한다. 명령은 값을 출력하지 않는다.

```bash
read -r -s -p 'RDS app password: ' DB_APP_PASSWORD
printf '\n'
aws ssm put-parameter --name /observation/prod/SPRING_DATASOURCE_PASSWORD --type SecureString \
  --value "$DB_APP_PASSWORD" --overwrite
aws ssm put-parameter --name /observation/prod/RDS_MASTER_PASSWORD --type SecureString \
  --value "$DB_MASTER_PASSWORD" --overwrite
unset DB_MASTER_PASSWORD

read -r -s -p 'GitHub OAuth client id: ' PORTAL_AUTH_GITHUB_CLIENT_ID
printf '\n'
aws ssm put-parameter --name /observation/prod/PORTAL_AUTH_GITHUB_CLIENT_ID --type String \
  --value "$PORTAL_AUTH_GITHUB_CLIENT_ID" --overwrite
unset PORTAL_AUTH_GITHUB_CLIENT_ID

read -r -s -p 'GitHub OAuth client secret: ' PORTAL_AUTH_GITHUB_CLIENT_SECRET
printf '\n'
aws ssm put-parameter --name /observation/prod/PORTAL_AUTH_GITHUB_CLIENT_SECRET --type SecureString \
  --value "$PORTAL_AUTH_GITHUB_CLIENT_SECRET" --overwrite
unset PORTAL_AUTH_GITHUB_CLIENT_SECRET

read -r -s -p 'Service token signing key: ' PORTAL_AUTH_SERVICE_TOKEN_SIGNING_KEY
printf '\n'
aws ssm put-parameter --name /observation/prod/PORTAL_AUTH_SERVICE_TOKEN_SIGNING_KEY --type SecureString \
  --value "$PORTAL_AUTH_SERVICE_TOKEN_SIGNING_KEY" --overwrite
unset PORTAL_AUTH_SERVICE_TOKEN_SIGNING_KEY

read -r -s -p 'OAuth state signing key: ' PORTAL_AUTH_OAUTH_STATE_SIGNING_KEY
printf '\n'
aws ssm put-parameter --name /observation/prod/PORTAL_AUTH_OAUTH_STATE_SIGNING_KEY --type SecureString \
  --value "$PORTAL_AUTH_OAUTH_STATE_SIGNING_KEY" --overwrite
unset PORTAL_AUTH_OAUTH_STATE_SIGNING_KEY
unset DB_APP_PASSWORD
```

생성 완료한 DB/secret parameter:

- `/observation/prod/SPRING_DATASOURCE_URL` - `String`.
- `/observation/prod/SPRING_DATASOURCE_PASSWORD` - `SecureString`.
- `/observation/prod/RDS_MASTER_PASSWORD` - 운영자 전용 `SecureString`.

생성 완료한 필수 application secret:

- `/observation/prod/PORTAL_AUTH_GITHUB_CLIENT_ID` - `String`.
- `/observation/prod/PORTAL_AUTH_GITHUB_CLIENT_SECRET` - `SecureString`.
- `/observation/prod/PORTAL_AUTH_SERVICE_TOKEN_SIGNING_KEY` - `SecureString`.
- `/observation/prod/PORTAL_AUTH_OAUTH_STATE_SIGNING_KEY` - `SecureString`.

### 9. App DB user 생성

RDS는 private subnet에 있으므로 EC2에서 실행한다. password는 SSM에서 읽되 출력하지 않는다.

```bash
ssh -i ~/.ssh/observation-prod-portal ec2-user@"$ELASTIC_IP"
```

EC2 shell:

```bash
set -euo pipefail
export RDS_ENDPOINT='<RDS endpoint>'
export DB_MASTER_PASSWORD="$(aws ssm get-parameter --name /observation/prod/RDS_MASTER_PASSWORD --with-decryption --query 'Parameter.Value' --output text)"
export DB_APP_PASSWORD="$(aws ssm get-parameter --name /observation/prod/SPRING_DATASOURCE_PASSWORD --with-decryption --query 'Parameter.Value' --output text)"
PGPASSWORD="$DB_MASTER_PASSWORD" psql "host=$RDS_ENDPOINT port=5432 dbname=observation user=observation_admin sslmode=require" <<'SQL'
\set app_password `printf "%s" "$DB_APP_PASSWORD"`
SELECT format('CREATE ROLE observation_app LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'observation_app') \gexec
ALTER ROLE observation_app PASSWORD :'app_password';
GRANT CONNECT ON DATABASE observation TO observation_app;
GRANT USAGE, CREATE ON SCHEMA public TO observation_app;
SQL
unset DB_MASTER_PASSWORD DB_APP_PASSWORD
```

### 10. EC2 환경파일 생성과 수동 jar 기동 준비

E4에서 systemd/Nginx/TLS/CD를 자동화하기 전, Epic 3에서는 수동 jar 기동 준비까지만 확인한다.

EC2 shell:

```bash
sudo install -d -o root -g root -m 0750 /etc/observation
aws ssm get-parameters-by-path \
  --path /observation/prod/ \
  --recursive \
  --with-decryption \
  --query 'Parameters[].{Name:Name,Value:Value}' \
  --output json \
  | jq -r '.[] | "\(.Name | split("/")[-1])=\(.Value | @sh)"' \
  | sudo tee /etc/observation/observation.env >/dev/null
sudo chmod 600 /etc/observation/observation.env
sudo chown root:root /etc/observation/observation.env
```

로컬에서 jar를 빌드하고 EC2로 전송한다.

```bash
./gradlew :observability-portal:bootJar
scp -i ~/.ssh/observation-prod-portal \
  observability-portal/build/libs/observability-portal-0.1.0-SNAPSHOT.jar \
  ec2-user@"$ELASTIC_IP":/tmp/observation-portal.jar
```

EC2 shell:

```bash
sudo install -o appuser -g appuser -m 0644 /tmp/observation-portal.jar /opt/observation/current/app.jar
sudo bash -lc '
set -euo pipefail
set -a
source /etc/observation/observation.env
set +a
cd /opt/observation/current
runuser -u appuser --preserve-environment -- bash -c "nohup java -jar /opt/observation/current/app.jar > /opt/observation/current/prod-manual-start.log 2>&1 & echo \$! > /opt/observation/current/app.pid"
'
```

성공 기준:

- prod 필수 설정 guard가 통과한다.
- RDS 접속이 성공한다.
- Flyway가 `V001`부터 현재 migration까지 적용한다.
- SQS client가 EC2 instance role credential chain으로 source queue/DLQ에 접근한다.
- AWS static credential env var를 EC2에 두지 않는다.

## 검증 명령

생성 후 로컬에서 실행한다.

```bash
aws ec2 describe-instances \
  --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].{InstanceId:InstanceId,State:State.Name,PrivateIp:PrivateIpAddress,PublicIp:PublicIpAddress,IamProfile:IamInstanceProfile.Arn,SecurityGroups:SecurityGroups[].GroupId}' \
  --output json

aws ec2 describe-security-groups \
  --group-ids "$EC2_SG_ID" "$RDS_SG_ID" \
  --query 'SecurityGroups[].{GroupName:GroupName,GroupId:GroupId,Ingress:IpPermissions}' \
  --output json

aws rds describe-db-instances \
  --db-instance-identifier observation-prod-postgres \
  --query 'DBInstances[0].{Status:DBInstanceStatus,Endpoint:Endpoint.Address,Class:DBInstanceClass,Engine:Engine,EngineVersion:EngineVersion,Public:PubliclyAccessible,Subnets:DBSubnetGroup.Subnets[].SubnetIdentifier}' \
  --output json

aws sqs get-queue-attributes \
  --queue-url "$SOURCE_QUEUE_URL" \
  --attribute-names QueueArn VisibilityTimeout ReceiveMessageWaitTimeSeconds RedrivePolicy \
  --output json

aws iam get-role --role-name observation-prod-portal-role --output json
aws iam get-role-policy --role-name observation-prod-portal-role --policy-name observation-prod-portal-runtime --output json

aws ssm get-parameters-by-path \
  --path /observation/prod/ \
  --recursive \
  --query 'Parameters[].Name' \
  --output table
```

EC2에서 실행한다.

```bash
aws sts get-caller-identity
aws ssm get-parameter --name /observation/prod/SPRING_PROFILES_ACTIVE --query 'Parameter.Value' --output text
aws sqs get-queue-attributes --queue-url '<source queue url>' --attribute-names QueueArn
psql "host=<RDS endpoint> port=5432 dbname=observation user=observation_app sslmode=require" -c 'select current_database(), current_user;'
```

## Epic 4 Handoff

| 항목 | 값 |
| --- | --- |
| VPC ID | `vpc-0821ede465eb5dcfa` |
| public subnet A/C | `subnet-0c8e4a95cd777be78`, `subnet-0059a6a3e23bb091c` |
| private subnet A/C | `subnet-09922b0cc52176351`, `subnet-066e5290c7e8762a5` |
| EC2 security group | `sg-035d3bbbadb239dba` |
| RDS security group | `sg-03de3fa644e0f10ee` |
| EC2 instance ID | `i-06defdb40c40905d9` |
| EC2 private IP | `10.30.0.50` |
| EC2 instance role ARN | `arn:aws:iam::491013322019:role/observation-prod-portal-role` |
| EC2 instance profile ARN | `arn:aws:iam::491013322019:instance-profile/observation-prod-portal-profile` |
| Elastic IP allocation ID | `eipalloc-011a817ed51b43e9e` |
| Elastic IP public IP | `3.34.77.80` |
| 가비아 DNS A record | `portal.observstarter.cloud -> 3.34.77.80` |
| RDS DB instance identifier | `observation-prod-postgres` |
| RDS endpoint | `observation-prod-postgres.c9e6es0mquhz.ap-northeast-2.rds.amazonaws.com` |
| DB name | `observation` |
| DB app username | `observation_app` |
| SQS source queue URL | `https://sqs.ap-northeast-2.amazonaws.com/491013322019/observation-prod-ingest-source` |
| SQS source queue ARN | `arn:aws:sqs:ap-northeast-2:491013322019:observation-prod-ingest-source` |
| SQS DLQ URL | `https://sqs.ap-northeast-2.amazonaws.com/491013322019/observation-prod-ingest-dlq` |
| SQS DLQ ARN | `arn:aws:sqs:ap-northeast-2:491013322019:observation-prod-ingest-dlq` |
| SSM parameter prefix | `/observation/prod/` |
| 운영 앱 URL | `https://portal.observstarter.cloud` |

### E4.1 systemd + SSM env loader 전환

Repository에는 systemd 전환을 위한 운영 파일을 둔다.

| Repo 파일 | 서버 배치 위치 | 역할 |
| --- | --- | --- |
| `scripts/deploy/observation-load-env.sh` | `/usr/local/bin/observation-load-env` | `/observation/prod/` SSM parameters를 복호화해 `/etc/observation/observation.env`로 갱신한다. |
| `scripts/deploy/observation-stop-manual-java.sh` | `/usr/local/bin/observation-stop-manual-java` | systemd 전환 전에 남은 수동 `java -jar /opt/observation/current/app.jar` 프로세스를 종료한다. |
| `deploy/systemd/observation.service` | `/etc/systemd/system/observation.service` | SSM env loader 실행 후 `appuser` 권한으로 portal jar를 관리하고 live endpoint로 HTTP 기동을 확인한다. |

서버 적용 순서:

```bash
sudo install -o root -g root -m 0755 scripts/deploy/observation-load-env.sh /usr/local/bin/observation-load-env
sudo install -o root -g root -m 0755 scripts/deploy/observation-stop-manual-java.sh /usr/local/bin/observation-stop-manual-java
sudo install -o root -g root -m 0644 deploy/systemd/observation.service /etc/systemd/system/observation.service
sudo /usr/local/bin/observation-load-env
sudo /usr/local/bin/observation-stop-manual-java
sudo systemctl daemon-reload
sudo systemctl enable --now observation.service
sudo systemctl status observation.service --no-pager
```

전환 시 주의:

- Epic 3 수동 기동은 pid file이 실제 PID가 아닐 수 있으므로 `observation-stop-manual-java`는 command line 기준으로 수동 프로세스를 찾는다.
- systemd가 이미 관리 중인 `observation.service`의 `MainPID`는 종료 대상에서 제외한다.
- prod profile은 `SERVER_ADDRESS` 기본값 `127.0.0.1`로 앱을 loopback에만 바인딩한다.
- systemd는 `ExecStartPost`에서 `http://127.0.0.1:8080/internal/health/live`를 사용한다.
- 이 단계는 기존 EC2 instance role의 SSM 읽기 권한을 사용하며 IAM/OIDC 리소스를 만들지 않는다.

### E4.2 health endpoint 확정

운영 health endpoint는 Spring Actuator 대신 secret-free lightweight controller로 직접 구현한다.

| Consumer | Endpoint | 사용 방식 |
| --- | --- | --- |
| systemd | `http://127.0.0.1:8080/internal/health/live` | app process와 HTTP server가 응답 가능한지만 확인한다. |
| Nginx | `http://127.0.0.1:8080/internal/health/ready` | reverse proxy 적용/재시작 후 upstream ready smoke에 사용한다. |
| GitHub Actions CD | `https://portal.observstarter.cloud/internal/health/ready` | 배포 후 외부 HTTPS 경로가 DB/Flyway 준비 상태까지 통과하는지 확인한다. |
| 배포/롤백 스크립트 | `http://127.0.0.1:8080/internal/health/ready` | jar 교체 후 server-local success/rollback 판단에 사용한다. |

응답 정책:

- `/internal/health/live`: HTTP 응답 가능 여부만 나타내며 DB나 외부 dependency를 확인하지 않는다.
- `/internal/health/ready`: DB `select 1`과 Flyway pending migration `0`을 확인한다.
- 응답 body에는 `status`와 `checks`만 포함하며 secret, 내부 endpoint 값, SSM parameter 값, datasource URL/password, queue URL query를 포함하지 않는다.

## 검증 결과

2026-06-21에 로컬 AWS CLI와 EC2 SSM Run Command로 아래를 확인했다.

- EC2 `i-06defdb40c40905d9`: `running`, system/instance status `ok`.
- Elastic IP `3.34.77.80`: EC2 instance에 연결됨.
- EC2 SG `sg-035d3bbbadb239dba`: inbound 80/443 `0.0.0.0/0`, 22 `110.34.75.186/32`.
- RDS SG `sg-03de3fa644e0f10ee`: inbound 5432는 EC2 SG에서만 허용.
- RDS `observation-prod-postgres`: `available`, PostgreSQL `16.14`, `db.t4g.micro`, `publicly-accessible=false`, private subnet group.
- SQS source queue: long polling `20`, visibility timeout `60`, DLQ redrive `maxReceiveCount=5`.
- SQS DLQ: long polling `20`, visibility timeout `60`.
- IAM role inline policy Sid: `ReadObservationProdParameters`, `UseSourceQueue`, `UseApplicationDlq`.
- SSM Agent: `Online`.
- EC2 runtime SSM command: Java `17.0.19`, instance role assume, SQS source queue attribute lookup, `/opt/observation/current`와 `/etc/observation` 생성 확인.
- DB app user SSM command: `observation_app`으로 `observation` DB 접속 확인.
- SSH 접속: `ec2-user@3.34.77.80`에 `~/.ssh/observation-prod-portal` key로 접속 확인.
- Portal jar: `./gradlew :observability-portal:bootJar` 성공, `/opt/observation/current/app.jar`에 `appuser:appuser` 소유로 배치 완료.
- Prod manual start: PID `27994`로 `java -jar /opt/observation/current/app.jar` 실행 확인.
- Local HTTP: EC2 내부 `http://127.0.0.1:8080/` 응답 `200`.
- Flyway: `flyway_schema_history` 성공 migration `13`개, 최신 version `013`.
- DB schema: `public` table `13`개 확인.
- E4.1 systemd 전환: 기존 수동 Java process를 종료하고 `observation.service`를 `enabled/active` 상태로 기동했다.
- E4.1 SSM env loader: `/etc/observation/observation.env`를 root 전용 `600` 권한으로 갱신했다.
- E4.1 service process: `appuser` 권한의 `/usr/bin/java -jar /opt/observation/current/app.jar`, `MainPID=29001`, restart count `0`.
- E4.1 bind 확인: `8080`은 `[::ffff:127.0.0.1]:8080` loopback에만 listen하며 EC2 내부 `http://127.0.0.1:8080/` 응답 `200`.
- E4.2 health endpoint: 새 jar 배포 후 EC2 내부 `http://127.0.0.1:8080/internal/health/live` 응답 `200`, body `{"status":"UP","checks":{"http":"UP"}}`.
- E4.2 readiness endpoint: EC2 내부 `http://127.0.0.1:8080/internal/health/ready` 응답 `200`, body `{"status":"UP","checks":{"database":"UP","flyway":"UP"}}`.
- E4.2 service process: health endpoint 포함 jar로 교체 후 `observation.service`는 `enabled/active`, `MainPID=29580`, restart count `0`.

### E4.3 Nginx reverse proxy + TLS

Repository에는 Nginx/TLS 구성을 위한 운영 파일을 둔다.

| Repo 파일 | 서버 배치 위치 | 역할 |
| --- | --- | --- |
| `deploy/nginx/observation.conf` | `/etc/nginx/conf.d/observation.conf` | 80 -> 443 redirect와 443 reverse proxy를 정의한다. |
| `scripts/deploy/install-nginx-tls.sh` | 필요 시 서버 작업 디렉터리에서 실행 | Nginx/certbot 설치, webroot 인증서 발급, 최종 Nginx 설정 적용, certbot timer 활성화를 수행한다. |

Nginx 정책:

- 외부는 Nginx `80/443`만 받는다.
- portal jar는 계속 `127.0.0.1:8080` loopback에만 listen한다.
- `80`은 `/.well-known/acme-challenge/`를 제외하고 `443`으로 `301` redirect한다.
- `443`은 Let's Encrypt certificate를 사용한다.
- `proxy_pass`는 `http://127.0.0.1:8080`이다.
- 전달 header는 `Host`, `X-Forwarded-Proto`, `X-Forwarded-For`, `X-Forwarded-Host`, `X-Real-IP`다.
- Nginx access log는 OAuth callback `code`/`state` query가 남지 않도록 `$request_uri` 대신 `$uri`만 기록하는 `observation_no_query` format을 사용한다.

검증 명령:

```bash
curl -I http://portal.observstarter.cloud/internal/health/ready
curl -fsS https://portal.observstarter.cloud/internal/health/ready
systemctl is-active nginx
systemctl is-enabled nginx
systemctl is-active certbot-renew.timer
systemctl is-enabled certbot-renew.timer
sudo certbot renew --dry-run
```

GitHub OAuth prod redirect URI와 실제 HTTPS callback URL은 모두 아래 값으로 맞춘다.

```text
https://portal.observstarter.cloud/api/auth/github/callback
```

검증 결과:

- Nginx package `1.30.2`와 certbot package `2.6.0`을 설치했다.
- `/etc/nginx/conf.d/observation.conf`를 적용했고 `nginx -t`가 성공했다.
- `nginx.service`는 `enabled/active` 상태다.
- 외부 HTTP `http://portal.observstarter.cloud/internal/health/ready`는 `301`로 `https://portal.observstarter.cloud/internal/health/ready`에 redirect한다.
- 외부 HTTPS `https://portal.observstarter.cloud/internal/health/ready`는 `200`, body `{"status":"UP","checks":{"database":"UP","flyway":"UP"}}`.
- EC2 listen 상태는 Nginx `0.0.0.0:80`, `0.0.0.0:443`, portal Java `[::ffff:127.0.0.1]:8080`이다.
- TLS certificate subject는 `CN=portal.observstarter.cloud`, issuer는 Let's Encrypt `YE2`, 만료 시각은 `2026-09-19T14:06:19Z`다.
- `certbot-renew.timer`는 `enabled/active`이고 다음 실행 예정이 등록되어 있다.
- `sudo certbot renew --dry-run`은 simulated renewal success로 완료됐다.
- SSM `/observation/prod/PORTAL_AUTH_GITHUB_REDIRECT_URI` 값과 실제 HTTPS callback URL은 `https://portal.observstarter.cloud/api/auth/github/callback`로 일치한다.
- 실제 callback route는 query 없이 접근 시 secret-free OAuth 실패 응답 `400`을 반환해 route가 HTTPS로 도달 가능함을 확인했다.
- OAuth callback query가 기존 Nginx 기본 access log에 1건 남은 것을 확인했고 원문 출력 없이 log를 비운 뒤, `/etc/nginx/conf.d/observation.conf`를 query 없는 access log format으로 재적용했다.
- fake callback query 요청 후 `/var/log/nginx/observation_access.log`에는 callback path만 기록되고 query 기록은 `0`건임을 확인했다.

### E4.4 deploy/rollback script + GitHub Actions CD/OIDC

Repository에는 단일 EC2 stop/start 배포를 위한 스크립트와 workflow를 둔다.

| Repo 파일 | 서버/서비스 위치 | 역할 |
| --- | --- | --- |
| `scripts/deploy/observation-deploy-portal.sh` | `/usr/local/bin/observation-deploy-portal` | 새 jar 다운로드/검증, 기존 jar 백업, `systemctl stop/start`, server-local ready check, 실패 시 자동 롤백을 수행한다. |
| `scripts/deploy/observation-rollback-portal.sh` | `/usr/local/bin/observation-rollback-portal` | `/opt/observation/releases/app-*.jar` 최신 백업 또는 지정 백업으로 수동 롤백한다. |
| `.github/workflows/deploy.yml` | GitHub Actions | main/tag ref에서 immutable jar를 빌드하고 OIDC + SSM Run Command로 배포한다. |
| `deploy/aws/github-oidc-trust-policy.json` | AWS IAM 검토용 | GitHub production environment OIDC subject만 deploy role을 assume하도록 제한한다. |
| `deploy/aws/github-oidc-deploy-policy.json` | AWS IAM 검토용 | deploy role의 S3 artifact write/read, SSM Run Command, command result 조회 권한 범위다. |
| `deploy/aws/ec2-deploy-artifact-read-policy.json` | AWS IAM 검토용 | EC2 instance role이 artifact bucket `portal/*`를 읽는 최소 권한이다. |

배포 방식:

- 운영 배포는 PR artifact를 사용하지 않는다.
- `.github/workflows/deploy.yml`이 `main` push 또는 `v*` tag에서 checkout한 ref로 `:observability-portal:bootJar`를 새로 생성한다.
- 생성 jar는 SHA-256과 함께 S3 immutable prefix에 저장한다.
- SSM Run Command가 EC2에서 배포 스크립트를 실행한다.
- EC2 배포 스크립트는 `/opt/observation/current/app.jar` 기준을 유지한다.
- 기존 jar는 `/opt/observation/releases/app-<UTC timestamp>-<commit sha>.jar`로 백업한다.
- 배포 성공 기준은 `http://127.0.0.1:8080/internal/health/ready` 성공이다.
- workflow 최종 검증 URL은 `https://portal.observstarter.cloud/internal/health/ready`다.
- 단일 EC2 stop/start이므로 `systemctl stop`부터 ready 통과 전까지 짧은 502/503 또는 연결 실패가 발생할 수 있다.

수동 롤백:

```bash
sudo /usr/local/bin/observation-rollback-portal
```

특정 백업으로 롤백:

```bash
sudo /usr/local/bin/observation-rollback-portal /opt/observation/releases/app-<timestamp>-<sha>.jar
```

GitHub Environment `production` 설정:

- Code로 제공됨: workflow의 `environment: production`, OIDC role assume, `main`/`v*` ref guard.
- GitHub UI에서 수동 설정 필요: required reviewers, deployment branches/tags 제한(`main`, `v*`).
- 등록할 environment variables:
  - `OBSERVATION_PROD_DEPLOY_ROLE_ARN`: GitHub OIDC deploy role ARN.
  - `OBSERVATION_DEPLOY_ARTIFACT_BUCKET`: `observation-prod-deploy-artifacts-491013322019-ap-northeast-2`.
  - `OBSERVATION_PROD_INSTANCE_ID`: `i-06defdb40c40905d9`.

GitHub UI 수동 설정 절차:

1. Repository `Settings` -> `Environments` -> `production`으로 이동한다.
2. `Required reviewers`를 켜고 운영 배포를 승인할 사용자 또는 team을 등록한다.
3. 개인 repository에서 운영자가 1명뿐이면 self-review 방지를 켤 경우 배포가 막힐 수 있다. 별도 reviewer를 둘 수 있으면 self-review 방지를 켜고, 1인 운영이면 위험을 인지한 상태로 끄거나 reviewer를 추가한 뒤 켠다.
4. `Deployment branches and tags`는 selected branch/tag policy로 제한하고 `main`, `v*` tag만 허용한다.
5. Environment variables 3개가 위 값으로 등록되어 있는지 확인한다. AWS access key, SSH private key, prod secret은 GitHub Secrets/Variables에 넣지 않는다.

생성/변경한 AWS/GitHub CD 리소스:

| 항목 | 값 |
| --- | --- |
| Artifact S3 bucket | `observation-prod-deploy-artifacts-491013322019-ap-northeast-2` |
| GitHub OIDC provider | `arn:aws:iam::491013322019:oidc-provider/token.actions.githubusercontent.com` |
| GitHub deploy role | `arn:aws:iam::491013322019:role/observation-prod-github-deploy-role` |
| Deploy role inline policy | `observation-prod-github-deploy` |
| EC2 artifact read inline policy | `observation-prod-deploy-artifact-read` on `observation-prod-portal-role` |
| GitHub environment | `production` |

적용한 보안/권한:

- S3 bucket은 portal deploy artifact 용도이며 public access block을 모두 켰다.
- S3 bucket versioning을 enabled로 두고 SSE-S3 기본 암호화를 적용했다.
- GitHub OIDC deploy role trust는 `repo:tlsdla1235/obser_service:environment:production` subject와 `sts.amazonaws.com` audience로 제한했다.
- Deploy role은 artifact bucket `portal/*` write/read, 지정 EC2 `i-06defdb40c40905d9` 대상 `AWS-RunShellScript` SSM Run Command, command 조회, EC2 describe만 허용한다.
- EC2 instance role은 artifact bucket `portal/*` read와 bucket location 조회만 허용한다.
- GitHub Secrets에는 AWS access key, SSH private key, prod secret을 넣지 않았다.

GitHub Environment `production` 상태:

- Environment variables 등록 완료:
  - `OBSERVATION_PROD_DEPLOY_ROLE_ARN`
  - `OBSERVATION_DEPLOY_ARTIFACT_BUCKET`
  - `OBSERVATION_PROD_INSTANCE_ID`
- Required reviewers와 deployment branch/tag restriction은 GitHub UI에서 수동 설정해야 한다.
- 현재 `protection_rules`는 비어 있고 `deployment_branch_policy`는 설정되지 않았으므로 운영 merge 전 GitHub UI에서 승인 게이트와 branch/tag 제한을 반드시 설정한다.

승인받아 실행한 권한/영향:

- S3 bucket `observation-prod-deploy-artifacts-491013322019-ap-northeast-2` 생성: 비용은 artifact storage와 request 중심, public access block과 versioning 권장.
- GitHub OIDC provider 생성 또는 기존 provider 확인: `token.actions.githubusercontent.com`.
- IAM role `observation-prod-github-deploy-role` 생성: trust policy는 `repo:tlsdla1235/obser_service:environment:production` subject로 제한.
- Deploy role inline policy: S3 `portal/*` write/read, SSM `AWS-RunShellScript`를 EC2 `i-06defdb40c40905d9`에 전송, command invocation 조회, EC2 describe.
- EC2 role `observation-prod-portal-role` inline policy 추가: artifact bucket `portal/*` read만 허용.

검증 결과:

- `/usr/local/bin/observation-deploy-portal`, `/usr/local/bin/observation-rollback-portal`을 root 소유 `755`로 서버에 설치했다.
- local artifact 입력 방식으로 deploy script를 실행했고 기존 jar를 `/opt/observation/releases/app-20260621T151435Z-local-script-validation.jar`로 백업했다.
- deploy script 실행 후 `observation.service`는 `active`, server-local ready `200`, external HTTPS ready `200`을 확인했다.
- 수동 rollback script를 최신 backup jar 대상으로 실행했고 `observation.service`는 `active`, server-local ready `200`, external HTTPS ready `200`을 확인했다.
- rollback 후 `observation.service`는 `MainPID=31648`, restart count `0`이며 `/opt/observation/current/app.jar`는 `appuser:appuser 644` 상태다.
- IAM policy simulation: deploy role의 artifact S3 object write/read, S3 bucket prefix list/location/multipart lookup, 단일 EC2 + `AWS-RunShellScript` SSM SendCommand, command 조회, EC2 describe가 allowed임을 확인했다.
- IAM policy simulation: EC2 role은 artifact S3 object read와 bucket location만 allowed이며 S3 put은 implicit deny임을 확인했다.
- S3 artifact bucket -> SSM Run Command -> EC2 deploy script 경로를 `portal/local-validation/20260621T152453Z/` artifact로 실제 검증했다.
- S3/SSM 경로 deploy 후 `observation.service`는 `active`, `MainPID=32280`, restart count `0`, server-local ready `200`, external HTTPS ready `200`이다.
- S3/SSM 경로 deploy metadata: `DEPLOY_COMMIT_SHA=s3-ssm-validation`, `DEPLOY_SOURCE=manual-s3-ssm-validation`.

Deploy workflow 실행 가능 상태:

- `.github/workflows/deploy.yml`은 아직 working tree에만 있고 현재 브랜치가 `codex/cicd-deployment-plan`이므로 GitHub Actions 원격 workflow 목록에는 아직 나타나지 않는다.
- workflow는 `main` 또는 `v*` tag에서만 운영 배포가 진행되도록 trigger와 ref guard를 둔다.
- 이 변경을 main에 merge하거나 `v*` tag로 배포 가능한 commit을 push한 뒤, GitHub UI에서 `production` required reviewers/branch restriction을 설정하면 OIDC + SSM CD 실행 준비가 완료된다.
- 현재 브랜치에서 `workflow_dispatch`를 시도하더라도 guard가 `refs/heads/main` 또는 `refs/tags/v*`가 아니면 실패하도록 되어 있다.

### E4.5 외부 HTTPS OAuth E2E 검증

검증 기준:

- 기준 도메인은 `https://portal.observstarter.cloud`다.
- GitHub OAuth prod redirect URI와 실제 callback URL은 `https://portal.observstarter.cloud/api/auth/github/callback`로 일치해야 한다.
- 로그인 후 dashboard 또는 정상 진입 화면까지 도달해야 한다.
- URL, browser storage, console log, application journal, Nginx log에 secret/provider token/raw payload를 남기지 않는다.

검증 결과:

- `GET https://portal.observstarter.cloud/api/auth/github/authorize` 응답의 provider는 `github`이고, authorization host는 `github.com`, path는 `/login/oauth/authorize`다.
- authorization URL query는 `client_id`, `redirect_uri`, `scope`, `state`만 포함하며 `redirect_uri`는 `https://portal.observstarter.cloud/api/auth/github/callback`다.
- SSM `/observation/prod/PORTAL_AUTH_GITHUB_REDIRECT_URI` 값도 같은 callback URL이다.
- GitHub 로그인/승인 이후 `https://portal.observstarter.cloud/dashboard`로 돌아왔고, 상단 `Dashboard` link를 한 번 클릭하면 dashboard 본문이 렌더링된다.
- 확인된 정상 진입 화면은 로그인 상태 `GitHub 로그인됨`, `Projects`, `Applications`, `Project를 선택하세요`이며 현재 계정에 active membership project가 없다는 empty state다. 이는 인증 실패나 application 장애로 해석하지 않는다.
- 최종 dashboard URL은 query string과 hash가 없었다.
- browser `localStorage`, `sessionStorage`는 비어 있었고 cookie는 없었다.
- dashboard DOM, browser console log에는 `client_secret`, provider token/raw payload, access/refresh/id token, bearer header 패턴이 없었다.
- `observation.service` journal 최근 30분, Nginx access/error log에는 secret/provider token/raw payload 계열 패턴이 없었다.
- Nginx access log는 query 없는 `observation_no_query` format으로 재적용했고, fake callback query 요청 후 callback path는 기록되지만 query 기록은 `0`건임을 확인했다.
- 외부 HTTPS ready endpoint `https://portal.observstarter.cloud/internal/health/ready`는 OAuth 검증 후에도 `200`이다.

아직 검증하지 않은 항목:

- GitHub Actions CD workflow end-to-end 운영 배포.
- GitHub Environment `production` UI approval gate와 deployment branch/tag restriction 실제 설정.

### E4 최종 handoff

main/tag 배포 흐름:

1. 이 변경을 `main`에 merge하거나 `v*` tag를 push한다.
2. GitHub Actions deploy workflow가 해당 ref를 checkout하고 `:observability-portal:bootJar`로 jar를 새로 만든다.
3. jar와 SHA-256 file을 S3 `s3://observation-prod-deploy-artifacts-491013322019-ap-northeast-2/portal/<sha>/<run-id>-<attempt>/` prefix에 업로드한다.
4. GitHub OIDC token으로 `observation-prod-github-deploy-role`을 assume한다.
5. SSM Run Command가 단일 EC2 `i-06defdb40c40905d9`에서 `/usr/local/bin/observation-deploy-portal`을 실행한다.
6. EC2는 S3 artifact를 instance role로 읽고 SHA-256을 검증한 뒤, 기존 jar 백업, `systemctl stop`, jar 교체, `systemctl start`, server-local ready check를 수행한다.
7. workflow는 마지막으로 `https://portal.observstarter.cloud/internal/health/ready`를 확인한다.

S3 artifact handoff:

- GitHub deploy role은 artifact bucket `portal/*`에 write/read/multipart 권한만 가진다.
- EC2 instance role은 같은 bucket `portal/*` read와 bucket location 조회만 가진다.
- GitHub Secrets에는 AWS access key, SSH private key, prod secret을 넣지 않는다.
- artifact는 immutable prefix에 commit SHA, run id, run attempt를 포함해 남긴다.

Rollback 절차:

- 배포 스크립트는 start 또는 server-local ready check 실패 시 직전 jar 백업으로 자동 rollback을 시도한다.
- 수동 rollback은 `sudo /usr/local/bin/observation-rollback-portal`을 실행한다.
- 특정 backup jar로 되돌릴 때는 `sudo /usr/local/bin/observation-rollback-portal /opt/observation/releases/app-<timestamp>-<sha>.jar`를 실행한다.
- rollback 후 `systemctl is-active observation`과 `curl -fsS http://127.0.0.1:8080/internal/health/ready`를 확인하고, 외부 `https://portal.observstarter.cloud/internal/health/ready`까지 확인한다.

Emergency stop/start:

```bash
sudo systemctl stop observation
sudo systemctl start observation
sudo systemctl restart observation
sudo systemctl status observation --no-pager
journalctl -u observation.service -n 100 --no-pager
```

비용/리소스 유지 주의사항:

- EC2 `t4g.small`, RDS `db.t4g.micro` + gp3 20 GiB, Elastic IP, S3 artifact bucket, SQS queue는 유지 중이면 비용이 발생한다.
- RDS와 EC2를 중지하면 서비스는 내려간다. Elastic IP를 unattached 상태로 두면 비용이 발생할 수 있으므로 연결 상태를 확인한다.
- S3 artifact bucket은 versioning이 켜져 있어 오래된 jar가 누적된다. 보존 정책 또는 lifecycle rule은 별도 운영 결정으로 둔다.
- certbot 갱신 timer는 EC2가 켜져 있어야 자동 갱신된다. 장기간 EC2를 중지했다가 재기동하면 `sudo certbot renew --dry-run` 또는 실제 갱신 상태를 확인한다.

E5로 넘어가기 전 남은 항목:

- GitHub UI에서 `production` required reviewers와 `main`/`v*` deployment branch/tag restriction을 설정한다.
- main merge 또는 `v*` tag 이후 GitHub Actions CD를 실제로 1회 실행하고 approval gate, OIDC assume, SSM deploy, external ready check를 확인한다.
- 로그인 후 active membership project가 없는 계정의 empty state는 정상 동작으로 확인했다. E5나 후속 운영 smoke에서 실제 project 등록/credential 발급/스타터 연결 흐름을 별도로 검증한다.
- E5 starter distribution 작업에서는 release tag 기준 starter publish, package registry 권한, 사용 문서, 버전 pinning을 확정한다.

### E4 CD 접속 방식 입력

E4는 SSH와 SSM 중 하나로 배포를 자동화할 수 있다.

| 방식 | 준비된 값 | E4에서 필요한 추가 작업 |
| --- | --- | --- |
| SSH deploy | host `3.34.77.80`, user `ec2-user`, private key `~/.ssh/observation-prod-portal` | GitHub Secrets 또는 GitHub Environment secret에 deploy 전용 private key를 등록할지, 운영자 로컬 배포만 허용할지 결정한다. GitHub에 private key를 넣는 경우 키 교체/접근 권한 정책을 별도 승인한다. |
| SSM Run Command deploy | instance `i-06defdb40c40905d9`, role `observation-prod-portal-role`, SSM Agent `Online` 확인 | GitHub Actions OIDC deploy role을 만들고 `ssm:SendCommand`, `ssm:GetCommandInvocation`, `ec2:DescribeInstances`, artifact fetch 권한을 최소 범위로 부여한다. |
| GitHub OIDC | 아직 생성하지 않음 | AWS IAM identity provider와 deploy role trust policy에 repo/branch/environment 조건을 둔다. 이 작업은 GitHub UI/API 권한과 운영 승인 정책이 필요하므로 E4에서 수행한다. |

## Epic 4로 남기는 범위

- Nginx reverse proxy 설치와 80 -> 443 redirect
- TLS 인증서 발급/갱신 자동화
- systemd unit 파일과 배포/롤백 스크립트
- GitHub Actions CD workflow, GitHub OIDC deploy role, GitHub Environment approval
- 배포 health endpoint 확정
- GitHub OAuth App 운영 redirect URI 등록
