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

아직 검증하지 않은 항목:

- GitHub OAuth 로그인 end-to-end 동작.
- Nginx/TLS 적용 후 `https://portal.observstarter.cloud` 외부 접속.

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
