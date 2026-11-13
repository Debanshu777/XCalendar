#!/usr/bin/env python3
"""Gate a merge on reviewed, reproducible, presented-frame evidence.

The version 1 report contains five or more complete before/after runs for each
scenario on Android, iOS, and macOS. It compares the median of each metric
within each platform/scenario group. A merge is rejected when p95, p99, startup,
or maximum frame time regresses by more than 5%; memory regresses by more than
2%; late-frame percentage rises by more than 0.5 percentage points; or a
>=40 ms stall appears when the before group had none. Fixture and device/config
metadata must match the policy exactly. Device/config are declared once per
platform and are claims that reviewers must verify against the linked evidence.
Artifact URLs are evidence pointers for human review; this checker validates
their HTTPS form and metadata, not their contents or cryptographic authenticity.
"Presented frames" must come from a
presented-frame metric source; callback intervals are not accepted. The gate
makes no 120 Hz / 120 FPS claim.

Example report shape is documented by the constants and validators below. Run
with --help for invocation details. A missing default report is a failure.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import statistics
import sys
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


DEFAULT_REPORT = Path("performance-evidence/merge.json")
PLATFORMS = ("android", "ios", "macos")
SCENARIOS = ("alternating", "forward", "loading_refresh")
RUNS_PER_SIDE = 5
METRICS = (
    "p95_ms",
    "p99_ms",
    "late_frame_pct",
    "max_ms",
    "startup_ms",
    "memory_bytes",
    "stalls_ge_40ms",
)
FLOAT_METRICS = set(METRICS) - {"stalls_ge_40ms"}
SHA256_RE = re.compile(r"^[0-9a-fA-F]{64}$")
ALLOWED_REGRESSION = {
    "p95_ms": 0.05,
    "p99_ms": 0.05,
    "startup_ms": 0.05,
    "memory_bytes": 0.02,
    "max_ms": 0.05,
}


class EvidenceError(ValueError):
    """Raised when report evidence is incomplete, malformed, or regressive."""


def _exact_keys(value: dict[str, Any], keys: set[str], where: str) -> None:
    actual = set(value)
    missing = keys - actual
    extra = actual - keys
    if missing or extra:
        details = []
        if missing:
            details.append("missing " + ", ".join(sorted(missing)))
        if extra:
            details.append("unknown " + ", ".join(sorted(extra)))
        raise EvidenceError(f"{where}: " + "; ".join(details))


def _object(value: Any, where: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise EvidenceError(f"{where} must be an object")
    return value


def _string(value: Any, where: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise EvidenceError(f"{where} must be a non-empty string")
    return value.strip()


def _digest(value: Any, where: str) -> str:
    if not isinstance(value, str) or not SHA256_RE.fullmatch(value):
        raise EvidenceError(f"{where} must be a full 64-character SHA-256 digest")
    return value.lower()


def _number(value: Any, where: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise EvidenceError(f"{where} must be a finite non-negative number")
    try:
        number = float(value)
    except (OverflowError, ValueError):
        raise EvidenceError(f"{where} must be a finite non-negative number") from None
    if not math.isfinite(number) or number < 0:
        raise EvidenceError(f"{where} must be a finite non-negative number")
    return number


def _https_url(value: Any, where: str) -> str:
    url = _string(value, where)
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.netloc:
        raise EvidenceError(f"{where} must be an absolute HTTPS URL")
    return url


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise EvidenceError(f"duplicate JSON object key: {key}")
        result[key] = value
    return result


def _validate_run(run: Any, where: str) -> tuple[str, dict[str, float]]:
    obj = _object(run, where)
    _exact_keys(obj, {"run_id", "valid_complete", *METRICS}, where)
    _string(obj["run_id"], f"{where}.run_id")
    if obj["valid_complete"] is not True:
        raise EvidenceError(f"{where}.valid_complete must be true")
    metrics: dict[str, float] = {}
    for name in METRICS:
        value = _number(obj[name], f"{where}.{name}")
        if name == "stalls_ge_40ms" and not value.is_integer():
            raise EvidenceError(f"{where}.{name} must be an integer count")
        metrics[name] = value
    for name in ("p95_ms", "p99_ms", "max_ms", "startup_ms", "memory_bytes"):
        if metrics[name] <= 0:
            raise EvidenceError(f"{where}.{name} must be positive")
    if metrics["late_frame_pct"] > 100:
        raise EvidenceError(f"{where}.late_frame_pct must be between 0 and 100")
    if not metrics["p95_ms"] <= metrics["p99_ms"] <= metrics["max_ms"]:
        raise EvidenceError(f"{where} must satisfy p95_ms <= p99_ms <= max_ms")
    has_stall = metrics["stalls_ge_40ms"] > 0
    if (metrics["max_ms"] >= 40) != has_stall:
        raise EvidenceError(f"{where}.max_ms must agree with stalls_ge_40ms at 40 ms")
    return obj["run_id"].strip(), metrics


def _median_metrics(runs: list[dict[str, float]]) -> dict[str, float]:
    return {name: statistics.median(run[name] for run in runs) for name in METRICS}


def _compare_group(before: list[dict[str, float]], after: list[dict[str, float]], where: str) -> list[str]:
    baseline = _median_metrics(before)
    candidate = _median_metrics(after)
    failures: list[str] = []
    for metric, limit in ALLOWED_REGRESSION.items():
        old, new = baseline[metric], candidate[metric]
        # A zero baseline cannot support a relative percentage calculation;
        # any positive value is a regression beyond the allowed tolerance.
        if (old == 0 and new > 0) or (old > 0 and new > old * (1 + limit)):
            failures.append(
                f"{where}: {metric} regressed from {old:g} to {new:g} "
                f"(allowed {limit * 100:g}%)"
            )
    old_late, new_late = baseline["late_frame_pct"], candidate["late_frame_pct"]
    if new_late > old_late + 0.5:
        failures.append(
            f"{where}: late_frame_pct rose from {old_late:g} to {new_late:g} "
            "(allowed increase 0.5 percentage points)"
        )
    if (all(run["stalls_ge_40ms"] == 0 for run in before)
            and any(run["stalls_ge_40ms"] > 0 for run in after)):
        failures.append(f"{where}: newly introduced >=40 ms stalls")
    return failures


def validate_report(report: Any, base_source_sha256: str, head_source_sha256: str) -> list[str]:
    """Validate report schema and policy; return an empty list when it passes."""
    expected_base = _digest(base_source_sha256, "--base-source-sha256")
    expected_head = _digest(head_source_sha256, "--head-source-sha256")
    root = _object(report, "report")
    _exact_keys(root, {
        "version", "base_source_sha256", "head_source_sha256", "metric_kind",
        "fixture", "platforms",
    }, "report")
    if type(root["version"]) is not int or root["version"] != 1:
        raise EvidenceError("report.version must be integer 1")
    if _digest(root["base_source_sha256"], "report.base_source_sha256") != expected_base:
        raise EvidenceError("report base source digest does not match --base-source-sha256")
    if _digest(root["head_source_sha256"], "report.head_source_sha256") != expected_head:
        raise EvidenceError("report head source digest does not match --head-source-sha256")
    if root["metric_kind"] != "presented_frames":
        raise EvidenceError("report.metric_kind must be presented_frames; callback metrics are not accepted")

    fixture = _object(root["fixture"], "report.fixture")
    _exact_keys(fixture, {"year", "events"}, "report.fixture")
    if type(fixture["year"]) is not int or fixture["year"] != 2026:
        raise EvidenceError("report.fixture.year must be integer 2026")
    if type(fixture["events"]) is not int or fixture["events"] != 3650:
        raise EvidenceError("report.fixture.events must be integer 3650")

    platforms = _object(root["platforms"], "report.platforms")
    if set(platforms) != set(PLATFORMS):
        raise EvidenceError("report.platforms must contain exactly android, ios, and macos")

    failures: list[str] = []
    for platform in PLATFORMS:
        entry = _object(platforms[platform], f"platforms.{platform}")
        _exact_keys(entry, {"device", "config", "artifacts", "scenarios"}, f"platforms.{platform}")
        _string(entry["device"], f"platforms.{platform}.device")
        _string(entry["config"], f"platforms.{platform}.config")
        artifacts = _object(entry["artifacts"], f"platforms.{platform}.artifacts")
        _exact_keys(artifacts, {"before_url", "after_url"}, f"platforms.{platform}.artifacts")
        _https_url(artifacts["before_url"], f"platforms.{platform}.artifacts.before_url")
        _https_url(artifacts["after_url"], f"platforms.{platform}.artifacts.after_url")
        scenarios = _object(entry["scenarios"], f"platforms.{platform}.scenarios")
        if set(scenarios) != set(SCENARIOS):
            raise EvidenceError(
                f"platforms.{platform}.scenarios must contain exactly " + ", ".join(SCENARIOS)
            )
        for scenario in SCENARIOS:
            group = _object(scenarios[scenario], f"platforms.{platform}.scenarios.{scenario}")
            _exact_keys(group, {"before", "after"}, f"platforms.{platform}.scenarios.{scenario}")
            validated: dict[str, list[dict[str, float]]] = {}
            run_ids: set[str] = set()
            for side in ("before", "after"):
                runs = group[side]
                where = f"platforms.{platform}.scenarios.{scenario}.{side}"
                if not isinstance(runs, list) or len(runs) < RUNS_PER_SIDE:
                    raise EvidenceError(f"{where} must contain at least {RUNS_PER_SIDE} runs")
                validated[side] = []
                for index, run in enumerate(runs):
                    run_where = f"{where}[{index}]"
                    run_id, metrics = _validate_run(run, run_where)
                    if run_id in run_ids:
                        raise EvidenceError(f"{where} run_id values must be unique across before and after")
                    run_ids.add(run_id)
                    validated[side].append(metrics)
            if len(validated["before"]) != len(validated["after"]):
                raise EvidenceError(
                    f"platforms.{platform}.scenarios.{scenario} must have equal before/after run counts"
                )
            failures.extend(_compare_group(
                validated["before"], validated["after"], f"{platform}/{scenario}"
            ))
    return failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate merge performance evidence")
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT,
                        help=f"JSON report (default: {DEFAULT_REPORT})")
    parser.add_argument("--base-source-sha256", required=True, help="full SHA-256 digest of base source scope")
    parser.add_argument("--head-source-sha256", required=True, help="full SHA-256 digest of head source scope")
    args = parser.parse_args(argv)
    try:
        with args.report.open(encoding="utf-8") as source:
            report = json.load(
                source,
                object_pairs_hook=_unique_object,
                parse_constant=lambda token: (_ for _ in ()).throw(
                    EvidenceError(f"invalid JSON numeric constant {token}")),
            )
        failures = validate_report(report, args.base_source_sha256, args.head_source_sha256)
    except (OSError, json.JSONDecodeError, EvidenceError) as error:
        print(f"performance evidence: FAIL: {error}", file=sys.stderr)
        return 1
    if failures:
        print("performance evidence: FAIL", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print("performance evidence: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
