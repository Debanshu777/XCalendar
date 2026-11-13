#!/usr/bin/env python3
"""Opt-in dense UI fixture for matched Android/iOS builds.

Examples:
  python3 scripts/performance/fixture.py --output captures/performance/dense/fixture --manifest-only
  python3 scripts/performance/fixture.py --output captures/performance/dense/fixture -- ./kotlin build -m iosApp -p iosSimulatorArm64 -v release

The context manager temporarily replaces CalendarApp's UI-state binding and
adds one generated common Kotlin file. It restores the exact original bytes in
finally. Do not run another build or edit CalendarApp while this context is active.
Repositories still initialize normally; fixture data bypasses their UI output.
This measures populated UI rendering, not database/network throughput or writes.
"""

import argparse
import calendar as calendar_dates
from contextlib import contextmanager
from datetime import datetime, timedelta
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = Path("app/shared/src/com/debanshu/xcalendar")
FIXTURE_NAME = "dense-november-2026-v1"
OBJECT_NAME = "PerformanceUiFixture"


def canonical_json(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def sha256(content):
    return hashlib.sha256(content).hexdigest()


def fixture_data(paging=False, year=False, load_delay_ms=0, batch_delay_ms=0, refresh_delay_ms=0, gate_loading=False):
    """Return local wall-clock data: same displayed schedule in each device zone."""
    colors = [0xFF1E88E5, 0xFF00897B, 0xFFFB8C00, 0xFF8E24AA, 0xFFE53935]
    calendars = [
        {"id": f"fixture-calendar-{i}", "name": name, "color": color - 2**32,
         "userId": "fixture-user", "isVisible": True, "isPrimary": i == 0}
        for i, (name, color) in enumerate(zip(
            ["Work", "Personal", "Family", "Travel", "Community"], colors))
    ]
    events = []
    for day in range(1, 31):
        midnight = datetime(2026, 11, day)
        slots = [(510, 120), (540, 90), (555, 180), (660, 90), (780, 180), (840, 150),
                 (1350, 180), (1395, 180), (0, 1440), (0, 2880)]
        for slot, (minute, duration) in enumerate(slots):
            calendar = calendars[(day * 10 + slot) % len(calendars)]
            start = midnight + timedelta(minutes=minute)
            end = start + timedelta(minutes=duration)
            kind = "daytime_overlap" if slot < 6 else "overnight" if slot < 8 else "all_day"
            events.append({
                "id": f"fixture-event-{day:02}-{slot:02}",
                "calendarId": calendar["id"], "calendarName": calendar["name"],
                "title": (f"November {day:02} / {slot + 1:02} — {calendar['name']} planning: "
                          "a deliberately long calendar title covering milestones, review notes, and follow-up actions"),
                "description": "Deterministic UI benchmark fixture; repository data is not used for this event.",
                "location": f"Fixture room {slot + 1}",
                "startLocal": start.isoformat(timespec="seconds"),
                "endLocal": end.isoformat(timespec="seconds"),
                "isAllDay": slot >= 8, "color": calendar["color"],
                "reminderMinutes": [10], "kind": kind,
            })
    holidays = [
        {"id": f"fixture-holiday-{i:02}", "name": f"Fixture holiday {i + 1}",
         "dateLocal": datetime(2026, 11, day).isoformat(timespec="seconds"), "countryCode": "US"}
        for i, day in enumerate([1, 7, 8, 8, 12, 15, 20, 25, 30])
    ]
    if paging and year:
        raise ValueError("Choose paging or full-year data")
    if min(load_delay_ms, batch_delay_ms, refresh_delay_ms) < 0:
        raise ValueError("Mock delays must be nonnegative")
    if year:
        originals = events
        events = []
        for month in range(1, 13):
            for day in range(1, calendar_dates.monthrange(2026, month)[1] + 1):
                for event in originals[((day - 1) % 30) * 10:((day - 1) % 30) * 10 + 10]:
                    start = datetime.fromisoformat(event["startLocal"])
                    end = datetime.fromisoformat(event["endLocal"])
                    shifted = start.replace(month=month, day=day)
                    slot = event["id"].split("-")[-1]
                    events.append(dict(event, id=f"year-2026-{month:02}-{day:02}-{slot}",
                                       title=f"{calendar_dates.month_name[month]} {day:02}" + event["title"][11:],
                                       startLocal=shifted.isoformat(timespec="seconds"),
                                       endLocal=(shifted + (end - start)).isoformat(timespec="seconds")))
        holidays = [dict(h, id=f"{month:02}-" + h["id"],
                         dateLocal=datetime(2026, month, min(datetime.fromisoformat(h["dateLocal"]).day, calendar_dates.monthrange(2026, month)[1])).isoformat(timespec="seconds"))
                    for month in range(1, 13) for h in holidays]
    elif paging:
        original_events = events
        events = []
        for month in (10, 11, 12):
            for event in original_events:
                start = datetime.fromisoformat(event["startLocal"])
                end = datetime.fromisoformat(event["endLocal"])
                shifted = start.replace(month=month)
                events.append(dict(event, id=f"{month:02}-" + event["id"],
                                   startLocal=shifted.isoformat(timespec="seconds"),
                                   endLocal=(shifted + (end - start)).isoformat(timespec="seconds")))
    data = {
        "name": "dense-year-2026-v1" if year else "dense-paging-2026-v1" if paging else FIXTURE_NAME, "schema_version": 1, "selected_date": "2026-01-08" if year else "2026-11-08",
        "time_zone_policy": "Convert local date-times using TimeZone.currentSystemDefault() once at fixture initialization; keep the device zone fixed within a comparison.",
        "accounts": [{"id": "fixture-user", "name": "Benchmark User",
                      "email": "benchmark@example.invalid", "photoUrl": ""}],
        "calendars": calendars, "events": events, "holidays": holidays,
    }
    if load_delay_ms or batch_delay_ms or refresh_delay_ms:
        data["load_profile"] = dict(initial_delay_ms=load_delay_ms, batch_delay_ms=batch_delay_ms,
                                    refresh_delay_ms=refresh_delay_ms, batch_size=100,
                                    gate_loading=gate_loading,
                                    refresh_policy="Update every tenth event title, preserving IDs; one refresh after initial load")
    return data


def kotlin_string(value):
    return json.dumps(value, ensure_ascii=False).replace("$", "\\$")


def kotlin_source(data):
    """Generate ordinary common Kotlin compatible with both reviewed releases."""
    q = kotlin_string
    compact = len(data["events"]) > 1000 or "load_profile" in data
    lines = [
        "// Generated by scripts/performance/fixture.py. Temporary UI benchmark input only.",
        f"// Fixture data SHA-256: {sha256(canonical_json(data))}",
        "package com.debanshu.xcalendar", "",
        "import com.debanshu.xcalendar.domain.model.Calendar",
        "import com.debanshu.xcalendar.domain.model.Event",
        "import com.debanshu.xcalendar.domain.model.Holiday",
        "import com.debanshu.xcalendar.domain.model.User",
        "import com.debanshu.xcalendar.domain.states.CalendarUiState",
        "import kotlinx.collections.immutable.toImmutableList",
        "import kotlinx.datetime.LocalDate",
        "import kotlinx.datetime.LocalDateTime",
        "import kotlinx.datetime.TimeZone",
        "import kotlinx.datetime.toInstant", "",
        "import kotlinx.serialization.json.jsonArray",
        "import kotlinx.serialization.json.jsonObject",
        "import kotlinx.serialization.json.jsonPrimitive",
        "import kotlinx.coroutines.flow.first",
        "@OptIn(kotlin.time.ExperimentalTime::class)",
        f"internal object {OBJECT_NAME} {{",
        f"    val selectedDate = LocalDate.parse({q(data['selected_date'])})",
        "    private val timeZone = TimeZone.currentSystemDefault()",
        "    private fun epoch(local: String): Long =",
        "        LocalDateTime.parse(local).toInstant(timeZone).toEpochMilliseconds()", "",
        "    private fun event(id: String, calendar: Int, title: String, start: String, end: String, allDay: Boolean, location: String): Event =",
        "        Event(id = id, calendarId = calendars[calendar].id, calendarName = calendars[calendar].name,",
        "            title = title, description = \"Deterministic UI benchmark fixture; repository data is not used for this event.\",",
        "            location = location, startTime = epoch(start), endTime = epoch(end), isAllDay = allDay,",
        "            reminderMinutes = listOf(10), color = calendars[calendar].color)", "",
        "    private val calendars = listOf(",
    ]
    for calendar in data["calendars"]:
        lines.append("        Calendar(" + ", ".join([
            f"id = {q(calendar['id'])}", f"name = {q(calendar['name'])}",
            f"color = {calendar['color']}", f"userId = {q(calendar['userId'])}",
            f"isVisible = {str(calendar['isVisible']).lower()}",
            f"isPrimary = {str(calendar['isPrimary']).lower()}"]) + "),")
    lines += ["    ).toImmutableList()", "", "    val uiState by lazy { CalendarUiState(" if compact else "    val uiState = CalendarUiState(", "        accounts = listOf("]
    for user in data["accounts"]:
        lines.append("            User(" + ", ".join(f"{key} = {q(value)}" for key, value in user.items()) + "),")
    lines += ["        ).toImmutableList(),", "        calendars = calendars,", "        events = loadEvents().toImmutableList()," if compact else "        events = listOf("]
    calendar_indices = {item["id"]: i for i, item in enumerate(data["calendars"])}
    event_lines = []
    for event in data["events"]:
        event_lines.append("            event(" + ", ".join([
            q(event["id"]), str(calendar_indices[event["calendarId"]]), q(event["title"]),
            q(event["startLocal"]), q(event["endLocal"]), str(event["isAllDay"]).lower(),
            q(event["location"])]) + "),")
    if compact:
        lines += ["        holidays = listOf("]
    else:
        lines += event_lines
        lines += ["        ).toImmutableList(),", "        holidays = listOf("]
    for holiday in data["holidays"]:
        lines.append("            Holiday(" + ", ".join([
            f"id = {q(holiday['id'])}", f"name = {q(holiday['name'])}",
            f"date = epoch({q(holiday['dateLocal'])})", f"countryCode = {q(holiday['countryCode'])}"]) + "),")
    lines += ["        ).toImmutableList(),", "    ) }" if compact else "    )"]
    if compact:
        # Keep large fixture data out of native compiler IR and JVM method-size
        # limits. Each string constant stays below the JVM UTF-8 constant limit.
        payload = canonical_json(data["events"]).decode("utf-8")
        lines += ["    private fun loadEvents(): List<Event> {", "        val payload = listOf("]
        lines += ["            " + q(payload[start:start + 12000]) + "," for start in range(0, len(payload), 12000)]
        lines += ['        ).joinToString("")',
                  "        return kotlinx.serialization.json.Json.parseToJsonElement(payload).jsonArray.map { element ->",
                  "            val row = element.jsonObject",
                  "            fun field(name: String) = row.getValue(name).jsonPrimitive.content",
                  '            event(field("id"), calendars.indexOfFirst { it.id == field("calendarId") }, field("title"),',
                  '                  field("startLocal"), field("endLocal"), field("isAllDay").toBooleanStrict(), field("location"))',
                  "        }", "    }"]
    if "load_profile" in data:
        profile = data["load_profile"]
        lines += [
            f"    private val loadStarted = kotlinx.coroutines.flow.MutableStateFlow({str(not profile['gate_loading']).lower()})",
            "    fun startLoading() { loadStarted.value = true }",
            "    @androidx.compose.runtime.Composable",
            "    fun rememberState(): androidx.compose.runtime.State<CalendarUiState> =",
            "        androidx.compose.runtime.produceState(CalendarUiState()) {",
            "            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { uiState }",
            "            value = loaded.copy(events = kotlinx.collections.immutable.persistentListOf())",
            "            loadStarted.first { it }",
            '            println("PERF_FIXTURE waiting_for_initial_load")',
            f"            kotlinx.coroutines.delay({profile['initial_delay_ms']}L)",
            f"            val batchSize = {profile['batch_size']}" if profile['batch_delay_ms'] else "            val batchSize = loaded.events.size",
            "            var count = 0",
            "            while (count < loaded.events.size) {",
            "                count = minOf(count + batchSize, loaded.events.size)",
            "                value = loaded.copy(events = loaded.events.take(count).toImmutableList())",
            '                println("PERF_FIXTURE loaded=$count/${loaded.events.size}")',
            f"                if (count < loaded.events.size) kotlinx.coroutines.delay({profile['batch_delay_ms']}L)",
            "            }",
        ]
        if profile['refresh_delay_ms']:
            lines += [f"            kotlinx.coroutines.delay({profile['refresh_delay_ms']}L)",
                      "            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {",
                      '                loaded.copy(events = loaded.events.mapIndexed { i, event -> if (i % 10 == 0) event.copy(title = event.title + " [updated]") else event }.toImmutableList())',
                      "            }", '            println("PERF_FIXTURE refreshed")']
        lines += ["        }"]
    lines += ["}", ""]
    return "\n".join(lines).encode("utf-8")


def fixture_manifest(data, source):
    return {
        "fixture": data["name"], "schema_version": 1, "scope": "UI-only; not database/network/write throughput",
        "selected_date": data["selected_date"], "time_zone_policy": data["time_zone_policy"],
        "current_date_policy": "The selected date/month are fixed; the application's live today/clock behavior is unchanged. Do not use Today during this workload.",
        "repository_policy": "ViewModels/repositories still initialize; their calendar UI data is replaced by a fixed CalendarUiState.",
        "load_profile": data.get("load_profile"),
        "event_count": len(data["events"]), "calendar_count": len(data["calendars"]),
        "account_count": len(data["accounts"]), "holiday_count": len(data["holidays"]),
        "events_by_kind": {kind: sum(e["kind"] == kind for e in data["events"])
                           for kind in ["daytime_overlap", "overnight", "all_day"]},
        "event_start_date_range": [min(e["startLocal"][:10] for e in data["events"]),
                                   max(e["startLocal"][:10] for e in data["events"])],
        "latest_exclusive_end_local": max(e["endLocal"] for e in data["events"]),
        "data_sha256": sha256(canonical_json(data)), "source_sha256": sha256(source),
    }


def write_artifacts(output, data=None, source=None):
    data = fixture_data() if data is None else data
    source = kotlin_source(data) if source is None else source
    manifest = fixture_manifest(data, source)
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    (output / "fixture-data.json").write_bytes(canonical_json(data))
    (output / "fixture-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output / f"{OBJECT_NAME}.kt").write_bytes(source)
    return manifest


def patched_calendar_app(content, loading=False):
    text = content.decode("utf-8")
    if OBJECT_NAME in text:
        raise RuntimeError("CalendarApp already contains fixture input; refusing nested/unknown patch")
    pattern = r"^([ \t]*)val calendarUiState by calendarViewModel\.uiState\.collectAsState(?:WithLifecycle)?\(\)[ \t]*$"
    if len(re.findall(pattern, text, flags=re.MULTILINE)) != 1:
        raise RuntimeError("Expected exactly one known CalendarApp UI-state binding; no source was changed")
    binding = f"{OBJECT_NAME}.rememberState().value" if loading else f"{OBJECT_NAME}.uiState"
    text = re.sub(pattern, lambda m: m[1] + f"val calendarUiState = {binding}", text, flags=re.MULTILINE)
    # A saved Google session must never silently replace the mock UI dataset.
    google_anchor = "val connected = googleSnapshots.isNotEmpty()"
    if text.count(google_anchor) != 1:
        raise RuntimeError("Expected exactly one known Google routing anchor; no source was changed")
    text = text.replace(google_anchor, "val connected = false // Benchmark UI always uses the fixture")
    anchor = "    val backStack = rememberNavBackStack(config, NavigableScreen.Month)"
    if text.count(anchor) != 1:
        raise RuntimeError("Expected exactly one known CalendarApp navigation anchor; no source was changed")
    effect = ("    // Temporary deterministic benchmark selection; removed after the build.\n"
              "    androidx.compose.runtime.LaunchedEffect(dateStateHolder) {\n"
              f"        dateStateHolder.updateSelectedDateState({OBJECT_NAME}.selectedDate)\n"
              "    }\n")
    return text.replace(anchor, effect + anchor).encode("utf-8")


@contextmanager
def temporary_fixture(root, output, paging=False, year=False, load_delay_ms=0, batch_delay_ms=0, refresh_delay_ms=0, gate_loading=False):
    """Apply the same UI fixture to baseline/current checkouts and always restore.

    SIGTERM becomes SystemExit so finally runs. SIGINT already raises
    KeyboardInterrupt. SIGKILL/power loss cannot be caught; the output retains
    CalendarApp.original.kt and build metadata for manual recovery in that case.
    """
    root, output = Path(root).resolve(), Path(output).resolve()
    source_dir = root / SOURCE_DIR
    common_source_root = root / "app/shared/src"
    if output == common_source_root or common_source_root in output.parents:
        raise ValueError("Fixture artifacts must be outside the application source tree")
    calendar_app = source_dir / "CalendarApp.kt"
    generated = source_dir / f"{OBJECT_NAME}.kt"
    if generated.exists():
        raise RuntimeError(f"Refusing to overwrite existing generated fixture: {generated}")
    original = calendar_app.read_bytes()
    data = fixture_data(paging=paging, year=year, load_delay_ms=load_delay_ms, batch_delay_ms=batch_delay_ms, refresh_delay_ms=refresh_delay_ms, gate_loading=gate_loading)
    patched = patched_calendar_app(original, loading="load_profile" in data)
    source = kotlin_source(data)
    lock = root / "build/performance-ui-fixture.lock"
    lock.parent.mkdir(parents=True, exist_ok=True)
    # An interrupted process can leave this lock. Recover from the saved source
    # before removing it; silently ignoring the lock could restore wrong inputs.
    with lock.open("x", encoding="utf-8") as stream:
        stream.write(f"pid={os.getpid()}\nbackup={output / 'CalendarApp.original.kt'}\n")
    previous_sigterm = signal.getsignal(signal.SIGTERM)
    generated_created = False
    source_touched = False

    def interrupted(signum, frame):
        raise SystemExit(128 + signum)

    try:
        signal.signal(signal.SIGTERM, interrupted)
        manifest = write_artifacts(output, data, source)
        (output / "CalendarApp.original.kt").write_bytes(original)
        (output / "fixture-build-inputs.json").write_text(json.dumps({
            "fixture_data_sha256": manifest["data_sha256"],
            "original_calendar_app_sha256": sha256(original),
            "patched_calendar_app_sha256": sha256(patched),
            "temporary_source_path": str(SOURCE_DIR / generated.name),
        }, indent=2) + "\n", encoding="utf-8")
        with generated.open("xb") as stream:
            generated_created = True
            stream.write(source)
        source_touched = True
        calendar_app.write_bytes(patched)
        yield manifest
    finally:
        try:
            if source_touched:
                calendar_app.write_bytes(original)
            if generated_created:
                generated.unlink()
            lock.unlink()
        finally:
            signal.signal(signal.SIGTERM, previous_sigterm)


def run_command(command, cwd):
    """Wait for the complete build command; stop its process group on interruption."""
    process = subprocess.Popen(command, cwd=cwd, start_new_session=os.name == "posix")
    try:
        status = process.wait()
        if status:
            raise subprocess.CalledProcessError(status, command)
    except BaseException:
        if process.poll() is None:
            if os.name == "posix":
                os.killpg(process.pid, signal.SIGTERM)
            else:
                process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                if os.name == "posix":
                    os.killpg(process.pid, signal.SIGKILL)
                else:
                    process.kill()
                process.wait()
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help="Checkout to build; supports original baseline and current source")
    parser.add_argument("--output", type=Path, required=True, help="Artifact/manifest directory, normally under ignored captures/")
    parser.add_argument("--manifest-only", action="store_true", help="Generate data/source/manifest without touching application inputs")
    parser.add_argument("--paging", action="store_true", help="900 events across October–December; titles retain November labels for existing viewport checks")
    parser.add_argument("--year", action="store_true", help="3,650 events across all of 2026")
    parser.add_argument("--load-delay-ms", type=int, default=0)
    parser.add_argument("--batch-delay-ms", type=int, default=0, help="Delay between 100-event batches")
    parser.add_argument("--refresh-delay-ms", type=int, default=0, help="Delay after initial completion before one mocked refresh")
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    options = dict(paging=args.paging, year=args.year, load_delay_ms=args.load_delay_ms,
                   batch_delay_ms=args.batch_delay_ms, refresh_delay_ms=args.refresh_delay_ms)
    if (args.year and args.paging) or min(args.load_delay_ms, args.batch_delay_ms, args.refresh_delay_ms) < 0:
        parser.error("Choose --year or --paging; delays must be nonnegative")
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if args.manifest_only:
        if command:
            parser.error("--manifest-only cannot be combined with a build command")
        output, source_dir = args.output.resolve(), (args.root / "app/shared/src").resolve()
        if output == source_dir or source_dir in output.parents:
            parser.error("Fixture artifacts must be outside the application source tree")
        manifest = write_artifacts(output, fixture_data(**options))
        print(json.dumps(manifest, indent=2))
    else:
        if not command:
            parser.error("Supply a command after --, or use --manifest-only")
        with temporary_fixture(args.root, args.output, **options) as manifest:
            print(f"UI fixture {manifest['fixture']}: {manifest['event_count']} events, SHA-256 {manifest['data_sha256']}", flush=True)
            run_command(command, args.root)


if __name__ == "__main__":
    main()
