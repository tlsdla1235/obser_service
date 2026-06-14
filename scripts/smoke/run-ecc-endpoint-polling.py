#!/usr/bin/env python3
"""ECC 유사 smoke endpoint를 호출하고 JSONL 실행 로그를 남기는 스크립트다.

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
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin, urlsplit, urlunsplit
from urllib.request import Request, urlopen


DEFAULT_BASE_URL = "http://localhost:8082"
DEFAULT_ROUTES_PATH = "/api/ecc-smoke/routes"
DEFAULT_ERROR_PATH = "/api/ecc-smoke/error-500"
DEFAULT_SLOW_PATH = "/api/ecc-smoke/slow-p99"
DEFAULT_DURATION_SECONDS = 3600.0
DEFAULT_SCENARIO_DURATION_SECONDS = 7200.0
DEFAULT_SCENARIO_SLOT_SECONDS = 1800.0
DEFAULT_INTERVAL_SECONDS = 15.0
DEFAULT_ERROR_EVERY = 10
DEFAULT_SLOW_DELAY_MILLIS = 850
DEFAULT_ERROR_DELAY_MILLIS = 850
DEFAULT_TIMEOUT_SECONDS = 5.0
SCENARIO_PLAN_RANDOM = "random"
SCENARIO_PLAN_SNAPSHOT_2H = "snapshot-2h"
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
class ScenarioDefinition:
    """30분 snapshot slot 안에서 반복할 요청 패턴을 정의한다."""

    name: str
    description: str
    error_every: int = 0
    slow_every: int = 0
    delay_error: bool = False


SNAPSHOT_2H_SCENARIOS = (
    ScenarioDefinition(
        name="healthy",
        description="정상 route만 호출해 active/normal 기준 slot을 만든다.",
    ),
    ScenarioDefinition(
        name="error-spike",
        description="의도적 500 응답을 섞어 error endpoint priority를 만든다.",
        error_every=3,
    ),
    ScenarioDefinition(
        name="latency-spike",
        description="850ms slow 응답을 섞어 duration bucket과 local p99를 늦춘다.",
        slow_every=2,
    ),
    ScenarioDefinition(
        name="error-and-latency",
        description="지연된 500과 slow 응답을 섞어 오류+지연 endpoint priority를 만든다.",
        error_every=3,
        slow_every=2,
        delay_error=True,
    ),
)


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
    scenarios: Counter[str] | None = None

    def __post_init__(self) -> None:
        self.status_codes = Counter()
        self.methods = Counter()
        self.groups = Counter()
        self.scenarios = Counter()

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
        if result.get("scenario") is not None:
            self.scenarios[str(result.get("scenario"))] += 1


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


def optional_positive_float_from_env(name: str) -> float | None:
    """환경변수가 있을 때만 양수 float로 파싱하고, 없으면 None을 반환한다."""

    raw_value = os.environ.get(name)
    if raw_value is None:
        return None
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
    """CLI 인자를 파싱하고 랜덤 polling 또는 snapshot QA 시나리오 설정을 제공한다."""

    parser = argparse.ArgumentParser(
        description="Randomly call ECC-shaped smoke endpoints and write JSONL polling logs."
    )
    parser.add_argument(
        "--scenario-plan",
        choices=(SCENARIO_PLAN_RANDOM, SCENARIO_PLAN_SNAPSHOT_2H),
        default=os.environ.get("ECC_ENDPOINT_POLL_SCENARIO_PLAN", SCENARIO_PLAN_RANDOM),
        help="Traffic scenario plan. Use snapshot-2h to rotate four 30-minute snapshot slots. Default: random.",
    )
    parser.add_argument(
        "--align-to-half-hour",
        action="store_true",
        default=os.environ.get("ECC_ENDPOINT_POLL_ALIGN_TO_HALF_HOUR", "").lower() in {"1", "true", "yes"},
        help="Wait until the next wall-clock :00 or :30 boundary before starting scenario calls.",
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
        "--slow-path",
        default=os.environ.get("ECC_ENDPOINT_SMOKE_SLOW_PATH", DEFAULT_SLOW_PATH),
        help=f"Intentional slow endpoint path. Default: {DEFAULT_SLOW_PATH}",
    )
    parser.add_argument(
        "--duration-seconds",
        type=float,
        default=optional_positive_float_from_env("ECC_ENDPOINT_POLL_DURATION_SECONDS"),
        help="Polling duration in seconds. Default: 3600 for random, 7200 for snapshot-2h.",
    )
    parser.add_argument(
        "--slot-seconds",
        type=float,
        default=positive_float_from_env("ECC_ENDPOINT_POLL_SLOT_SECONDS", DEFAULT_SCENARIO_SLOT_SECONDS),
        help="Scenario slot duration in seconds for snapshot-2h. Default: 1800.",
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
        "--slow-delay-millis",
        type=int,
        default=non_negative_int_from_env("ECC_ENDPOINT_POLL_SLOW_DELAY_MILLIS", DEFAULT_SLOW_DELAY_MILLIS),
        help="Delay used by the intentional slow endpoint. Default: 850.",
    )
    parser.add_argument(
        "--error-delay-millis",
        type=int,
        default=non_negative_int_from_env("ECC_ENDPOINT_POLL_ERROR_DELAY_MILLIS", DEFAULT_ERROR_DELAY_MILLIS),
        help="Delay used by delayed intentional 500 requests in scenario mode. Default: 850.",
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


def seconds_until_next_half_hour(now: datetime | None = None) -> float:
    """scheduled snapshot slot과 맞출 수 있게 다음 UTC 00/30분 경계까지 남은 초를 계산한다."""

    current = now or datetime.now(timezone.utc)
    if current.tzinfo is None:
        current = current.replace(tzinfo=timezone.utc)
    current = current.astimezone(timezone.utc)
    next_minute = 30 if current.minute < 30 else 60
    if next_minute == 60:
        boundary = current.replace(minute=0, second=0, microsecond=0) + timedelta(hours=1)
    else:
        boundary = current.replace(minute=30, second=0, microsecond=0)
    return max(0.0, (boundary - current).total_seconds())


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


def call_slow_response(
    base_url: str,
    slow_path: str,
    delay_millis: int,
    sequence: int,
    timeout_seconds: float,
) -> dict[str, Any]:
    """의도적 slow endpoint를 호출하고 duration bucket 검증용 결과를 기록한다."""

    started_at = time.monotonic()
    status, _, network_error = request_json(
        "GET",
        build_url(base_url, slow_path, {"delayMillis": str(delay_millis)}),
        timeout_seconds,
    )
    latency_ms = round((time.monotonic() - started_at) * 1000, 2)
    return {
        "timestamp": now_iso(),
        "sequence": sequence,
        "kind": "intentional_slow",
        "method": "GET",
        "path": slow_path,
        "route": slow_path,
        "group": "smoke-latency",
        "status": status,
        "expectedStatus": 200,
        "delayMillis": delay_millis,
        "latencyMs": latency_ms,
        "ok": status == 200 and network_error is None,
        "networkError": network_error,
    }


def call_intentional_error(
    base_url: str,
    error_path: str,
    delay_millis: int,
    sequence: int,
    timeout_seconds: float,
) -> dict[str, Any]:
    """의도적 500 endpoint를 호출하고 기대한 오류인지 기록한다."""

    started_at = time.monotonic()
    query = {"delayMillis": str(delay_millis)} if delay_millis > 0 else None
    status, _, network_error = request_json("GET", build_url(base_url, error_path, query), timeout_seconds)
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
        "delayMillis": delay_millis,
        "latencyMs": latency_ms,
        "ok": status == 500 and network_error is None,
        "networkError": network_error,
    }


def attach_scenario_context(
    result: dict[str, Any],
    scenario_plan: str,
    scenario: ScenarioDefinition,
    slot_index: int,
    slot_elapsed_seconds: float,
) -> dict[str, Any]:
    """시나리오 요청 결과에 snapshot slot 대조용 메타데이터를 덧붙인다."""

    result["scenarioPlan"] = scenario_plan
    result["scenario"] = scenario.name
    result["scenarioDescription"] = scenario.description
    result["slotIndex"] = slot_index
    result["slotElapsedSeconds"] = round(slot_elapsed_seconds, 3)
    return result


def scenario_for_elapsed(elapsed_seconds: float, slot_seconds: float) -> tuple[int, ScenarioDefinition, float]:
    """경과 시간으로 snapshot QA slot과 해당 scenario를 결정한다."""

    slot_index = int(elapsed_seconds // slot_seconds)
    scenario = SNAPSHOT_2H_SCENARIOS[slot_index % len(SNAPSHOT_2H_SCENARIOS)]
    slot_elapsed_seconds = elapsed_seconds - (slot_index * slot_seconds)
    return slot_index, scenario, slot_elapsed_seconds


def scenario_request_kind(scenario: ScenarioDefinition, slot_sequence: int) -> str:
    """slot 내부 순번을 기반으로 route/error/slow 중 이번 요청 종류를 고른다."""

    if scenario.error_every and slot_sequence % scenario.error_every == 0:
        return "error"
    if scenario.slow_every and slot_sequence % scenario.slow_every == 0:
        return "slow"
    return "route"


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
    """polling이 끝났을 때 판단에 필요한 요약을 출력한다."""

    print("ECC endpoint polling finished.")
    print(f"Log file: {log_file}")
    print(f"Total requests: {summary.total}")
    print(f"Expected responses: {summary.ok}")
    print(f"Unexpected responses: {summary.failures}")
    print(f"Status codes: {dict(summary.status_codes or {})}")
    print(f"Methods: {dict(summary.methods or {})}")
    print(f"Groups: {dict(summary.groups or {})}")
    if summary.scenarios:
        print(f"Scenarios: {dict(summary.scenarios)}")


def validate_args(args: argparse.Namespace) -> None:
    """polling 실행 전 주요 숫자 인자가 안전한 범위인지 확인한다."""

    if args.duration_seconds is None:
        args.duration_seconds = (
            DEFAULT_SCENARIO_DURATION_SECONDS
            if args.scenario_plan == SCENARIO_PLAN_SNAPSHOT_2H
            else DEFAULT_DURATION_SECONDS
        )
    if args.duration_seconds <= 0:
        fail("--duration-seconds must be positive.")
    if args.slot_seconds <= 0:
        fail("--slot-seconds must be positive.")
    if args.interval_seconds <= 0:
        fail("--interval-seconds must be positive.")
    if args.timeout_seconds <= 0:
        fail("--timeout-seconds must be positive.")
    if args.error_every < 0:
        fail("--error-every must be zero or positive.")
    if args.slow_delay_millis < 0:
        fail("--slow-delay-millis must be zero or positive.")
    if args.error_delay_millis < 0:
        fail("--error-delay-millis must be zero or positive.")
    if args.summary_every < 0:
        fail("--summary-every must be zero or positive.")


def run() -> None:
    """route catalog를 읽고 지정 시간 동안 랜덤 endpoint polling을 수행한다."""

    args = parse_args()
    validate_args(args)
    random_generator = random.Random(args.seed)
    args.log_file.parent.mkdir(parents=True, exist_ok=True)

    routes = fetch_routes(args.base_url, args.routes_path, args.timeout_seconds)
    if args.align_to_half_hour:
        wait_seconds = seconds_until_next_half_hour()
        if wait_seconds > 0:
            print(
                f"Waiting {wait_seconds:.1f}s until the next :00/:30 boundary before polling.",
                flush=True,
            )
            time.sleep(wait_seconds)

    started_monotonic = time.monotonic()
    deadline = time.monotonic() + args.duration_seconds
    summary = RunSummary()
    sequence = 0
    slot_sequences: Counter[int] = Counter()

    print(
        f"Loaded {len(routes)} ECC-shaped routes from {args.base_url.rstrip('/')}{args.routes_path}. "
        f"Polling plan={args.scenario_plan} for {args.duration_seconds:g}s every {args.interval_seconds:g}s.",
        flush=True,
    )
    if args.scenario_plan == SCENARIO_PLAN_SNAPSHOT_2H:
        print(
            "Scenario slots: "
            + ", ".join(
                f"{index}:{scenario.name}" for index, scenario in enumerate(SNAPSHOT_2H_SCENARIOS)
            )
            + f" · slotSeconds={args.slot_seconds:g}",
            flush=True,
        )
    print(f"Writing JSONL results to {args.log_file}", flush=True)

    try:
        while time.monotonic() < deadline:
            sequence += 1
            if args.scenario_plan == SCENARIO_PLAN_SNAPSHOT_2H:
                elapsed_seconds = time.monotonic() - started_monotonic
                slot_index, scenario, slot_elapsed_seconds = scenario_for_elapsed(
                    elapsed_seconds,
                    args.slot_seconds,
                )
                slot_sequences[slot_index] += 1
                slot_sequence = slot_sequences[slot_index]
                request_kind = scenario_request_kind(scenario, slot_sequence)
                if request_kind == "error":
                    error_delay_millis = args.error_delay_millis if scenario.delay_error else 0
                    result = call_intentional_error(
                        args.base_url,
                        args.error_path,
                        error_delay_millis,
                        sequence,
                        args.timeout_seconds,
                    )
                elif request_kind == "slow":
                    result = call_slow_response(
                        args.base_url,
                        args.slow_path,
                        args.slow_delay_millis,
                        sequence,
                        args.timeout_seconds,
                    )
                else:
                    route = random_generator.choice(routes)
                    result = call_route(args.base_url, route, sequence, args.timeout_seconds)
                result = attach_scenario_context(
                    result,
                    args.scenario_plan,
                    scenario,
                    slot_index,
                    slot_elapsed_seconds,
                )
            else:
                route = random_generator.choice(routes)
                result = call_route(args.base_url, route, sequence, args.timeout_seconds)
            append_log(args.log_file, result)
            summary.record(result)

            if args.scenario_plan == SCENARIO_PLAN_RANDOM and args.error_every and sequence % args.error_every == 0:
                error_result = call_intentional_error(
                    args.base_url,
                    args.error_path,
                    0,
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
