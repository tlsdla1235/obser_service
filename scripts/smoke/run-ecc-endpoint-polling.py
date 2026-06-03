#!/usr/bin/env python3
"""ECC 유사 smoke endpoint를 1시간 동안 랜덤 호출하고 JSONL 실행 로그를 남기는 스크립트다.

project key나 Bearer token은 이 스크립트가 직접 읽지 않는다. smoke 서버가 starter 설정으로
portal에 전송하는 동안, 이 스크립트는 HTTP traffic 생성과 결과 요약만 담당한다.
"""

from __future__ import annotations

import argparse
import json
import os
import random
import re
import sys
import time
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin, urlsplit, urlunsplit
from urllib.request import Request, urlopen


DEFAULT_BASE_URL = "http://localhost:8082"
DEFAULT_ROUTES_PATH = "/api/ecc-smoke/routes"
DEFAULT_ERROR_PATH = "/api/ecc-smoke/error-500"
DEFAULT_DURATION_SECONDS = 3600.0
DEFAULT_INTERVAL_SECONDS = 15.0
DEFAULT_ERROR_EVERY = 10
DEFAULT_TIMEOUT_SECONDS = 5.0
PATH_VARIABLE_VALUES = {
    "uuid": "1",
    "requestId": "1",
    "level": "3",
    "status": "OPEN",
    "teamId": "1",
    "week": "1",
    "reportId": "report-1",
    "memberUuid": "2",
    "categoryId": "1",
    "topicId": "1",
    "studyId": "study-1",
    "reviewId": "review-1",
}
QUERY_PARAMS_BY_PATH = {
    "/api/auth/signup/check-id": {"studentId": "20201234"},
    "/api/admin/users/filter": {"status": "ACTIVE", "level": "3"},
    "/api/admin/teams": {"regular": "true", "semesterId": "1"},
    "/api/admin/teams/reports/status": {"semesterId": "1"},
    "/api/admin/teams/{teamId}/{week}/report/grade": {"grade": "95"},
    "/api/admin/teams/{teamId}/score": {"score": "10"},
    "/api/admin/teams/{teamId}/members": {"memberUuid": "2"},
    "/api/admin/setting/study-recruitment": {"status": "true"},
    "/api/users/me/level": {"level": "3"},
    "/chat": {"source": "ecc-smoke"},
}
BODY_METHODS = {"POST", "PUT", "PATCH"}


@dataclass(frozen=True)
class RouteSpec:
    """smoke 서버의 route catalog에서 받은 호출 후보를 표현한다."""

    method: str
    path: str
    group: str


@dataclass
class RunSummary:
    """polling 실행 중 누적한 상태 코드와 실패 건수를 보관한다."""

    total: int = 0
    ok: int = 0
    failures: int = 0
    status_codes: Counter[str] | None = None
    methods: Counter[str] | None = None
    groups: Counter[str] | None = None

    def __post_init__(self) -> None:
        self.status_codes = Counter()
        self.methods = Counter()
        self.groups = Counter()

    def record(self, result: dict[str, Any]) -> None:
        """단일 request 결과를 누적 summary에 반영한다."""

        self.total += 1
        if result["ok"]:
            self.ok += 1
        else:
            self.failures += 1
        self.status_codes[str(result.get("status", "network_error"))] += 1
        self.methods[str(result.get("method", "UNKNOWN"))] += 1
        self.groups[str(result.get("group", "unknown"))] += 1


def positive_float_from_env(name: str, default: float) -> float:
    """환경변수 기반 duration/timeout 값을 양수 float로 파싱한다."""

    raw_value = os.environ.get(name)
    if raw_value is None:
        return default
    try:
        value = float(raw_value)
    except ValueError:
        fail(f"{name} must be a positive number.")
    if value <= 0:
        fail(f"{name} must be a positive number.")
    return value


def non_negative_int_from_env(name: str, default: int) -> int:
    """환경변수 기반 반복 횟수 값을 0 이상의 정수로 파싱한다."""

    raw_value = os.environ.get(name)
    if raw_value is None:
        return default
    try:
        value = int(raw_value)
    except ValueError:
        fail(f"{name} must be a non-negative integer.")
    if value < 0:
        fail(f"{name} must be a non-negative integer.")
    return value


def default_log_file() -> Path:
    """실행 시각을 포함한 기본 JSONL 로그 파일 경로를 만든다."""

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return Path("build/ecc-endpoint-polling") / f"polling-{timestamp}.jsonl"


def parse_args() -> argparse.Namespace:
    """CLI 인자를 파싱하고 기본 1시간 polling 설정을 제공한다."""

    parser = argparse.ArgumentParser(
        description="Randomly call ECC-shaped smoke endpoints and write JSONL polling logs."
    )
    parser.add_argument(
        "--base-url",
        default=os.environ.get("ECC_ENDPOINT_SMOKE_BASE_URL", DEFAULT_BASE_URL),
        help=f"ECC endpoint smoke server base URL. Default: {DEFAULT_BASE_URL}",
    )
    parser.add_argument(
        "--routes-path",
        default=os.environ.get("ECC_ENDPOINT_SMOKE_ROUTES_PATH", DEFAULT_ROUTES_PATH),
        help=f"Route catalog path. Default: {DEFAULT_ROUTES_PATH}",
    )
    parser.add_argument(
        "--error-path",
        default=os.environ.get("ECC_ENDPOINT_SMOKE_ERROR_PATH", DEFAULT_ERROR_PATH),
        help=f"Intentional 500 endpoint path. Default: {DEFAULT_ERROR_PATH}",
    )
    parser.add_argument(
        "--duration-seconds",
        type=float,
        default=positive_float_from_env("ECC_ENDPOINT_POLL_DURATION_SECONDS", DEFAULT_DURATION_SECONDS),
        help="Polling duration in seconds. Default: 3600.",
    )
    parser.add_argument(
        "--interval-seconds",
        type=float,
        default=positive_float_from_env("ECC_ENDPOINT_POLL_INTERVAL_SECONDS", DEFAULT_INTERVAL_SECONDS),
        help="Seconds to wait between random route calls. Default: 15.",
    )
    parser.add_argument(
        "--error-every",
        type=int,
        default=non_negative_int_from_env("ECC_ENDPOINT_POLL_ERROR_EVERY", DEFAULT_ERROR_EVERY),
        help="Call the intentional 500 endpoint every N random calls. Use 0 to disable. Default: 10.",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=positive_float_from_env("ECC_ENDPOINT_POLL_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS),
        help="Per-request timeout in seconds. Default: 5.",
    )
    parser.add_argument(
        "--log-file",
        type=Path,
        default=Path(os.environ.get("ECC_ENDPOINT_POLL_LOG_FILE", default_log_file())),
        help="JSONL log file path. Default: build/ecc-endpoint-polling/polling-<timestamp>.jsonl",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=os.environ.get("ECC_ENDPOINT_POLL_RANDOM_SEED"),
        help="Optional random seed for reproducible endpoint selection.",
    )
    parser.add_argument(
        "--summary-every",
        type=int,
        default=non_negative_int_from_env("ECC_ENDPOINT_POLL_SUMMARY_EVERY", 20),
        help="Print a compact progress line every N random calls. Use 0 to disable. Default: 20.",
    )
    return parser.parse_args()


def fail(message: str) -> None:
    """사람이 바로 조치할 수 있는 실패 메시지를 출력하고 종료한다."""

    print(message, file=sys.stderr)
    raise SystemExit(1)


def now_iso() -> str:
    """JSONL 로그용 UTC timestamp를 반환한다."""

    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def build_url(base_url: str, path: str, query: dict[str, str] | None = None) -> str:
    """base URL과 API path를 합치고 필요한 query string을 추가한다."""

    url = urljoin(base_url.rstrip("/") + "/", path.lstrip("/"))
    if not query:
        return url
    parts = urlsplit(url)
    encoded_query = urlencode(query)
    merged_query = "&".join(part for part in (parts.query, encoded_query) if part)
    return urlunsplit((parts.scheme, parts.netloc, parts.path, merged_query, parts.fragment))


def resolve_path_template(path: str) -> str:
    """`{teamId}` 같은 route template 변수를 smoke fixture 값으로 치환한다."""

    def replace(match: re.Match[str]) -> str:
        variable_name = match.group(1)
        return PATH_VARIABLE_VALUES.get(variable_name, "1")

    return re.sub(r"\{([^}]+)}", replace, path)


def request_json(
    method: str,
    url: str,
    timeout_seconds: float,
    body: dict[str, Any] | None = None,
) -> tuple[int, dict[str, Any] | None, str | None]:
    """단일 HTTP request를 보내고 상태 코드, JSON body, 네트워크 오류를 반환한다."""

    request_body = None
    headers = {"Accept": "application/json"}
    if body is not None:
        request_body = json.dumps(body, separators=(",", ":")).encode("utf-8")
        headers["Content-Type"] = "application/json"

    request = Request(url, data=request_body, headers=headers, method=method)
    try:
        with urlopen(request, timeout=timeout_seconds) as response:
            payload = response.read()
            return response.status, parse_json_payload(payload), None
    except HTTPError as error:
        payload = error.read()
        return error.code, parse_json_payload(payload), None
    except URLError as error:
        return 0, None, str(error.reason)
    except TimeoutError as error:
        return 0, None, str(error)


def parse_json_payload(payload: bytes) -> dict[str, Any] | None:
    """응답 body가 JSON 객체일 때만 파싱하고, 원문 body는 로그에 남기지 않는다."""

    if not payload:
        return None
    try:
        parsed = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None
    return parsed if isinstance(parsed, dict) else None


def fetch_routes(base_url: str, routes_path: str, timeout_seconds: float) -> list[RouteSpec]:
    """smoke 서버 route catalog를 읽어 랜덤 호출 후보로 변환한다."""

    status, payload, network_error = request_json(
        "GET",
        build_url(base_url, routes_path),
        timeout_seconds,
    )
    if network_error:
        fail(f"Route catalog request failed: {network_error}")
    if status != 200:
        fail(f"Route catalog returned HTTP {status}; expected 200.")
    routes = (((payload or {}).get("data") or {}).get("routes") or [])
    if not isinstance(routes, list) or not routes:
        fail("Route catalog response did not include a non-empty data.routes array.")

    route_specs: list[RouteSpec] = []
    for route in routes:
        if not isinstance(route, dict):
            continue
        method = str(route.get("method", "")).upper()
        path = str(route.get("path", ""))
        group = str(route.get("group", "unknown"))
        if method and path.startswith("/"):
            route_specs.append(RouteSpec(method=method, path=path, group=group))

    if not route_specs:
        fail("Route catalog did not contain usable route specs.")
    return route_specs


def call_route(
    base_url: str,
    route: RouteSpec,
    sequence: int,
    timeout_seconds: float,
) -> dict[str, Any]:
    """선택된 ECC 유사 endpoint 하나를 호출하고 JSONL 기록용 결과를 만든다."""

    resolved_path = resolve_path_template(route.path)
    query = QUERY_PARAMS_BY_PATH.get(route.path)
    url = build_url(base_url, resolved_path, query)
    body = None
    if route.method in BODY_METHODS:
        body = {
            "smoke": True,
            "sequence": sequence,
            "route": route.path,
        }
    started_at = time.monotonic()
    status, _, network_error = request_json(route.method, url, timeout_seconds, body)
    latency_ms = round((time.monotonic() - started_at) * 1000, 2)
    return {
        "timestamp": now_iso(),
        "sequence": sequence,
        "kind": "route",
        "method": route.method,
        "path": resolved_path,
        "route": route.path,
        "group": route.group,
        "status": status,
        "expectedStatus": 200,
        "latencyMs": latency_ms,
        "ok": status == 200 and network_error is None,
        "networkError": network_error,
    }


def call_intentional_error(
    base_url: str,
    error_path: str,
    sequence: int,
    timeout_seconds: float,
) -> dict[str, Any]:
    """의도적 500 endpoint를 호출하고 기대한 오류인지 기록한다."""

    started_at = time.monotonic()
    status, _, network_error = request_json("GET", build_url(base_url, error_path), timeout_seconds)
    latency_ms = round((time.monotonic() - started_at) * 1000, 2)
    return {
        "timestamp": now_iso(),
        "sequence": sequence,
        "kind": "intentional_error",
        "method": "GET",
        "path": error_path,
        "route": error_path,
        "group": "smoke-error",
        "status": status,
        "expectedStatus": 500,
        "latencyMs": latency_ms,
        "ok": status == 500 and network_error is None,
        "networkError": network_error,
    }


def append_log(log_file: Path, result: dict[str, Any]) -> None:
    """단일 request 결과를 JSONL 파일에 append한다."""

    with log_file.open("a", encoding="utf-8") as output:
        output.write(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
        output.write("\n")


def print_progress(summary: RunSummary, sequence: int, log_file: Path) -> None:
    """긴 polling 중 사람이 현재 상태를 볼 수 있도록 짧은 진행 상황을 출력한다."""

    print(
        f"[{now_iso()}] randomCalls={sequence} totalRequests={summary.total} "
        f"ok={summary.ok} failures={summary.failures} log={log_file}",
        flush=True,
    )


def print_final_summary(summary: RunSummary, log_file: Path) -> None:
    """1시간 polling이 끝났을 때 판단에 필요한 요약을 출력한다."""

    print("ECC endpoint polling finished.")
    print(f"Log file: {log_file}")
    print(f"Total requests: {summary.total}")
    print(f"Expected responses: {summary.ok}")
    print(f"Unexpected responses: {summary.failures}")
    print(f"Status codes: {dict(summary.status_codes or {})}")
    print(f"Methods: {dict(summary.methods or {})}")
    print(f"Groups: {dict(summary.groups or {})}")


def validate_args(args: argparse.Namespace) -> None:
    """polling 실행 전 주요 숫자 인자가 안전한 범위인지 확인한다."""

    if args.duration_seconds <= 0:
        fail("--duration-seconds must be positive.")
    if args.interval_seconds <= 0:
        fail("--interval-seconds must be positive.")
    if args.timeout_seconds <= 0:
        fail("--timeout-seconds must be positive.")
    if args.error_every < 0:
        fail("--error-every must be zero or positive.")
    if args.summary_every < 0:
        fail("--summary-every must be zero or positive.")


def run() -> None:
    """route catalog를 읽고 지정 시간 동안 랜덤 endpoint polling을 수행한다."""

    args = parse_args()
    validate_args(args)
    random_generator = random.Random(args.seed)
    args.log_file.parent.mkdir(parents=True, exist_ok=True)

    routes = fetch_routes(args.base_url, args.routes_path, args.timeout_seconds)
    deadline = time.monotonic() + args.duration_seconds
    summary = RunSummary()
    sequence = 0

    print(
        f"Loaded {len(routes)} ECC-shaped routes from {args.base_url.rstrip('/')}{args.routes_path}. "
        f"Polling for {args.duration_seconds:g}s every {args.interval_seconds:g}s.",
        flush=True,
    )
    print(f"Writing JSONL results to {args.log_file}", flush=True)

    try:
        while time.monotonic() < deadline:
            sequence += 1
            route = random_generator.choice(routes)
            result = call_route(args.base_url, route, sequence, args.timeout_seconds)
            append_log(args.log_file, result)
            summary.record(result)

            if args.error_every and sequence % args.error_every == 0:
                error_result = call_intentional_error(
                    args.base_url,
                    args.error_path,
                    sequence,
                    args.timeout_seconds,
                )
                append_log(args.log_file, error_result)
                summary.record(error_result)

            if args.summary_every and sequence % args.summary_every == 0:
                print_progress(summary, sequence, args.log_file)

            remaining_seconds = deadline - time.monotonic()
            if remaining_seconds <= 0:
                break
            time.sleep(min(args.interval_seconds, remaining_seconds))
    except KeyboardInterrupt:
        print("Polling interrupted by user; printing partial summary.", file=sys.stderr)
    finally:
        print_final_summary(summary, args.log_file)


if __name__ == "__main__":
    run()
