#!/usr/bin/env python3
"""Opt-in SQS ingest benchmark evidence harness.

이 script는 실제 부하 실행 대신 안전한 manifest/report skeleton을 먼저 만들고,
긴 benchmark runner를 붙일 때도 같은 opt-in/redaction 경계를 재사용하게 한다.
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import subprocess
import sys
import time


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


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate sanitized SQS ingest benchmark evidence skeleton")
    parser.add_argument("--output-dir", default="observability-portal/build/reports/ingest-benchmark")
    parser.add_argument("--fallback-reason", required=True)
    parser.add_argument("--opt-in", action="store_true")
    args = parser.parse_args()

    if not args.opt_in and os.environ.get("PORTAL_INGEST_BENCHMARK_OPT_IN") != "true":
        print("benchmark requires --opt-in or PORTAL_INGEST_BENCHMARK_OPT_IN=true", file=sys.stderr)
        return 2

    output_dir = pathlib.Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    git_revision = subprocess.run(
        ["git", "rev-parse", "--short", "HEAD"],
        check=False,
        capture_output=True,
        text=True,
    ).stdout.strip() or "unknown"
    dirty = subprocess.run(["git", "diff", "--quiet"], check=False).returncode != 0
    manifest = {
        "runId": f"{time.strftime('%Y%m%dT%H%M%S')}-local",
        "gitRevision": f"{git_revision}{'-dirty' if dirty else ''}",
        "fixture": {
            "applicationCount": 1,
            "instanceCount": 30,
            "distribution": "same fixture/idempotency distribution for direct and SQS buffered paths",
        },
        "database": {
            "engine": "Amazon RDS for PostgreSQL reference or isolated PostgreSQL fallback",
            "referenceInstanceClass": "db.t4g.micro",
            "referenceCompute": "2 vCPU / 1 GiB memory",
            "referenceStorage": "gp3 20 GiB, 3,000 IOPS / 125 MiB/s baseline",
            "fallbackReason": args.fallback_reason,
        },
        "queue": {
            "description": "fake/SQS/LocalStack type and region only; queue URL redacted",
        },
    }
    report = (
        "# SQS Buffered Ingest Benchmark Evidence\n\n"
        "portfolio evidence and relative trend context only.\n\n"
        "## Phase 1 Request Latency Evidence\n\n"
        "Direct insert request latency and SQS enqueue request latency are recorded separately.\n\n"
        "## Worker MVP Correctness/Lag Baseline\n\n"
        "Worker MVP lag/correctness baseline is not a DB throughput improvement claim.\n\n"
        "## Phase 2 DB Batch Throughput Evidence\n\n"
        "Batch writer throughput, bucket statement count, and persisted buckets/sec are recorded here.\n"
    )
    manifest_path = output_dir / "manifest.json"
    report_path = output_dir / "report.md"
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    report_path.write_text(report, encoding="utf-8")

    combined = manifest_path.read_text(encoding="utf-8") + report_path.read_text(encoding="utf-8")
    lower_combined = combined.lower()
    violations = [marker for marker in FORBIDDEN_MARKERS if marker in lower_combined]
    if AWS_ACCESS_KEY_RE.search(combined):
        violations.append("aws_access_key")
    if violations:
        print(f"redaction scan failed: {violations}", file=sys.stderr)
        return 3
    print(f"wrote sanitized benchmark skeleton to {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
