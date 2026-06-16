#!/usr/bin/env python3
"""LocalStack SQS ingest evidence를 생성하는 실행 스크립트.

LocalStack SQS 기반 ingest benchmark를 위한 opt-in runner다. 일반 test/CI에서는 실행되지 않고,
명시 opt-in일 때만 Testcontainers PostgreSQL + LocalStack SQS 기반 evidence artifact를 생성한다.
"""

from __future__ import annotations

import argparse
import os
import pathlib
import re
import subprocess
import sys


REQUIRED_ARTIFACTS = (
    "manifest.json",
    "direct-db.json",
    "localstack-sqs.json",
    "summary.md",
)
FORBIDDEN_MARKERS = (
    "https://sqs.",
    "localhost:4566",
    "queueurl=",
    "queue-url=",
    '"queueUrl"',
    "projectkey=",
    "project_key=",
    "raw project key",
    "starter credential",
    "authorization: bearer",
    "access_token",
    "refresh_token",
    "session_token",
    "aws_access_key_id",
    "aws_secret_access_key",
    "aws_session_token",
    "discord.com/api/webhooks",
    '"payload":',
)
AWS_ACCESS_KEY_RE = re.compile(r"(AKIA|ASIA)[0-9A-Z]{16}")
TOKEN_JSON_KEY_RE = re.compile(r"\"(?:access_token|refresh_token|session_token|token)\"\s*:", re.IGNORECASE)


def main() -> int:
    parser = argparse.ArgumentParser(description="LocalStack SQS ingest evidence benchmark를 실행한다.")
    parser.add_argument(
        "--output-dir",
        default="implementation-artifacts/benchmark-evidence/localstack-sqs-30-instance",
        help="Directory where sanitized benchmark artifacts will be written.",
    )
    parser.add_argument("--instance-count", type=int, default=30)
    parser.add_argument("--measurement-count", type=int, default=3000)
    parser.add_argument("--warmup-count", type=int, default=300)
    parser.add_argument("--concurrency", type=int, default=30)
    parser.add_argument("--worker-batch-size", type=int, default=10)
    parser.add_argument("--drain-timeout-seconds", type=int, default=30)
    parser.add_argument("--opt-in", action="store_true")
    args = parser.parse_args()

    if not args.opt_in and os.environ.get("PORTAL_LOCALSTACK_SQS_EVIDENCE_OPT_IN") != "true":
        print("localstack evidence run requires --opt-in or PORTAL_LOCALSTACK_SQS_EVIDENCE_OPT_IN=true", file=sys.stderr)
        return 2

    positive_args = {
        "instance-count": args.instance_count,
        "measurement-count": args.measurement_count,
        "warmup-count": args.warmup_count,
        "concurrency": args.concurrency,
        "worker-batch-size": args.worker_batch_size,
        "drain-timeout-seconds": args.drain_timeout_seconds,
    }
    invalid = [name for name, value in positive_args.items() if value < 1]
    if invalid:
        print(f"positive integer options required: {invalid}", file=sys.stderr)
        return 2

    repo_root = pathlib.Path(__file__).resolve().parents[2]
    output_dir = pathlib.Path(args.output_dir)
    if not output_dir.is_absolute():
        output_dir = repo_root / output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    for name in REQUIRED_ARTIFACTS:
        artifact = output_dir / name
        if artifact.exists():
            artifact.unlink()

    env = os.environ.copy()
    env["PORTAL_LOCALSTACK_SQS_EVIDENCE_OPT_IN"] = "true"
    env["PORTAL_LOCALSTACK_SQS_EVIDENCE_OUTPUT_DIR"] = str(output_dir)
    env["PORTAL_LOCALSTACK_SQS_EVIDENCE_INSTANCE_COUNT"] = str(args.instance_count)
    env["PORTAL_LOCALSTACK_SQS_EVIDENCE_MEASUREMENT_COUNT"] = str(args.measurement_count)
    env["PORTAL_LOCALSTACK_SQS_EVIDENCE_WARMUP_COUNT"] = str(args.warmup_count)
    env["PORTAL_LOCALSTACK_SQS_EVIDENCE_CONCURRENCY"] = str(args.concurrency)
    env["PORTAL_LOCALSTACK_SQS_EVIDENCE_WORKER_BATCH_SIZE"] = str(args.worker_batch_size)
    env["PORTAL_LOCALSTACK_SQS_EVIDENCE_DRAIN_TIMEOUT_SECONDS"] = str(args.drain_timeout_seconds)

    command = [
        "./gradlew",
        ":observability-portal:cleanTest",
        ":observability-portal:test",
        "--rerun-tasks",
        "--tests",
        "*LocalStackSqsIngestEvidenceRunTest",
    ]
    result = subprocess.run(command, cwd=repo_root, env=env, text=True)
    if result.returncode != 0:
        return result.returncode

    missing = [name for name in REQUIRED_ARTIFACTS if not (output_dir / name).is_file()]
    if missing:
        print(f"localstack evidence run did not create required artifacts: {missing}", file=sys.stderr)
        return 4

    violations = scan_output(output_dir)
    if violations:
        print(f"redaction scan failed: {violations}", file=sys.stderr)
        return 3

    print(f"wrote LocalStack SQS evidence to {output_dir}")
    for name in REQUIRED_ARTIFACTS:
        print(f"- {output_dir / name}")
    return 0


def scan_output(output_dir: pathlib.Path) -> list[str]:
    combined_parts: list[str] = []
    for path in output_dir.rglob("*"):
        if path.is_file():
            combined_parts.append(path.read_text(encoding="utf-8"))
    combined = "\n".join(combined_parts)
    lower_combined = combined.lower()
    violations = [marker for marker in FORBIDDEN_MARKERS if marker in lower_combined]
    if AWS_ACCESS_KEY_RE.search(combined):
        violations.append("aws_access_key")
    if TOKEN_JSON_KEY_RE.search(combined):
        violations.append("token")
    return violations


if __name__ == "__main__":
    raise SystemExit(main())
