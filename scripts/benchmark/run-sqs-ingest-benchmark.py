#!/usr/bin/env python3
"""Opt-in SQS ingest benchmark evidence runner.

이 runner는 일반 local/dev/test/smoke/CI 실행을 건드리지 않고, 명시 opt-in일 때만
Testcontainers PostgreSQL 기반 Story 12.6 scenario runner를 실행해 sanitized evidence artifact를 만든다.
"""

from __future__ import annotations

import argparse
import os
import pathlib
import re
import subprocess
import sys


FORBIDDEN_MARKERS = (
    "https://sqs.",
    "queueurl=",
    "queue-url=",
    "projectkey=",
    "project_key=",
    "raw project key",
    "observation_smoke_project_key",
    "ecc_endpoint_smoke_project_key",
    "startercredential=",
    "starter_credential=",
    "starter credential",
    "authorization: bearer",
    "authorization=bearer",
    "access_token",
    "refresh_token",
    "session_token",
    "token=",
    '"token":',
    "discord.com/api/webhooks",
    "aws_access_key_id",
    "aws_secret_access_key",
    "aws_session_token",
    "rawpayload",
    "raw_payload",
    "raw payload",
    '"payload":',
    '"schemaversion":',
)
AWS_ACCESS_KEY_RE = re.compile(r"(AKIA|ASIA)[0-9A-Z]{16}")
QUEUE_URL_RE = re.compile(
    r"(https?://(?:sqs\.|localhost:4566|[^\s\"']*amazonaws\.com)|\"?queue[-_]?url\"?\s*[:=])",
    re.IGNORECASE,
)
TOKEN_JSON_KEY_RE = re.compile(r"\"(?:access_token|refresh_token|session_token|token)\"\s*:", re.IGNORECASE)
RAW_PAYLOAD_JSON_KEY_RE = re.compile(r"\"(?:payload|schemaversion)\"\s*:", re.IGNORECASE)
REQUIRED_ARTIFACTS = (
    "manifest.json",
    "phase-1-request-latency.json",
    "phase-2-db-throughput.json",
    "report.md",
)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run sanitized SQS ingest benchmark evidence scenarios")
    parser.add_argument(
        "--output-dir",
        default="observability-portal/build/reports/ingest-benchmark",
        help="Directory where sanitized benchmark artifacts will be written.",
    )
    parser.add_argument(
        "--fallback-reason",
        required=True,
        help="Isolated PostgreSQL/RDS-reference fallback reason to record in the manifest.",
    )
    parser.add_argument("--measurement-count", type=int, default=90)
    parser.add_argument("--warmup-count", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=30)
    parser.add_argument("--opt-in", action="store_true")
    args = parser.parse_args()

    if not args.opt_in and os.environ.get("PORTAL_INGEST_BENCHMARK_OPT_IN") != "true":
        print("benchmark requires --opt-in or PORTAL_INGEST_BENCHMARK_OPT_IN=true", file=sys.stderr)
        return 2

    if args.measurement_count < 1 or args.warmup_count < 1 or args.batch_size < 1:
        print("measurement-count, warmup-count, and batch-size must be positive", file=sys.stderr)
        return 2

    repo_root = pathlib.Path(__file__).resolve().parents[2]
    output_dir = (repo_root / args.output_dir).resolve() if not pathlib.Path(args.output_dir).is_absolute() else pathlib.Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    for name in REQUIRED_ARTIFACTS:
        artifact = output_dir / name
        if artifact.exists():
            artifact.unlink()

    env = os.environ.copy()
    env["PORTAL_INGEST_BENCHMARK_OPT_IN"] = "true"
    env["PORTAL_INGEST_BENCHMARK_OUTPUT_DIR"] = str(output_dir)
    env["PORTAL_INGEST_BENCHMARK_FALLBACK_REASON"] = args.fallback_reason
    env["PORTAL_INGEST_BENCHMARK_MEASUREMENT_COUNT"] = str(args.measurement_count)
    env["PORTAL_INGEST_BENCHMARK_WARMUP_COUNT"] = str(args.warmup_count)
    env["PORTAL_INGEST_BENCHMARK_BATCH_SIZE"] = str(args.batch_size)

    command = [
        "./gradlew",
        ":observability-portal:cleanTest",
        ":observability-portal:test",
        "--rerun-tasks",
        "--tests",
        "*IngestBenchmarkScenarioRunTest",
    ]
    result = subprocess.run(command, cwd=repo_root, env=env, text=True)
    if result.returncode != 0:
        return result.returncode

    missing = [name for name in REQUIRED_ARTIFACTS if not (output_dir / name).is_file()]
    if missing:
        print(f"benchmark did not create required artifacts: {missing}", file=sys.stderr)
        return 4

    violations = scan_output(output_dir)
    if violations:
        print(f"redaction scan failed: {violations}", file=sys.stderr)
        return 3

    print(f"wrote sanitized benchmark evidence to {output_dir}")
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
    if QUEUE_URL_RE.search(combined):
        violations.append("queue_url")
    if TOKEN_JSON_KEY_RE.search(combined):
        violations.append("token")
    if RAW_PAYLOAD_JSON_KEY_RE.search(combined):
        violations.append("raw_payload")
    if AWS_ACCESS_KEY_RE.search(combined):
        violations.append("aws_access_key")
    return violations


if __name__ == "__main__":
    raise SystemExit(main())
