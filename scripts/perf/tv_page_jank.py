#!/usr/bin/env python3
"""Real-TV frame measurement for the TV browse pages (QA-08, PERF-21).

Macrobenchmark's FrameTimingMetric records nothing on the SEI Box R firmware,
so, like PERF-15..20, this drives the installed `benchmarkRelease` fixture APK
over ADB and reads `dumpsys gfxinfo` framestats for one D-pad journey.

Usage (TV connected, ANDROID_SERIAL set, stress fixture installed with
`./gradlew :app:installBenchmarkRelease -Plamphaus.benchmarkStress=true`):

    python3 scripts/perf/tv_page_jank.py discover-enter discover library-enter library --runs 3

Each run: cold start, wait out the boot sequence and Home load, move along
the top navigation to the page, reset gfxinfo, run the journey, and report
janky %, frame P50/P90, and frames over 100 ms.
"""
import argparse
import statistics
import subprocess
import time

PACKAGE = "com.lamphaus.app.benchmark"
ACTIVITY = f"{PACKAGE}/com.lamphaus.app.tv.TvActivity"

# Journeys start with focus on the Home tab of the top navigation
# (Search · Home · Movies · Series · Discover · Library, TV-NAV-01); tabs
# follow focus, so moving along the navigation composes each page.
DOWN_BROWSE = ["DPAD_DOWN"] * 12 + ["DPAD_RIGHT", "DPAD_RIGHT", "DPAD_LEFT"] + ["DPAD_UP"] * 6
JOURNEYS = {
    # Entering the tab: the page's first composition happens inside the key press.
    "discover-enter": ["DPAD_RIGHT"] * 3 + ["DPAD_DOWN"],
    "library-enter": ["DPAD_RIGHT"] * 4 + ["DPAD_DOWN"],
    "series-enter": ["DPAD_RIGHT"] * 2 + ["DPAD_DOWN"],
    # Brisk browsing inside the page (0.35 s between presses, as in PERF-15/20).
    "discover": ["DPAD_RIGHT"] * 3 + ["DPAD_DOWN"] + DOWN_BROWSE,
    "library": ["DPAD_RIGHT"] * 4 + ["DPAD_DOWN"] + DOWN_BROWSE,
    "home": ["DPAD_DOWN"] + DOWN_BROWSE,
    # Picking other catalogs re-filters the grid in place.
    "discover-catalogs": ["DPAD_RIGHT"] * 3 + ["DPAD_DOWN"]
        + ["DPAD_RIGHT", "DPAD_RIGHT", "DPAD_RIGHT", "DPAD_CENTER", "DPAD_RIGHT", "DPAD_CENTER", "DPAD_RIGHT", "DPAD_CENTER"],
}
KEY_GAP_SECONDS = 0.35


def adb(*args: str) -> str:
    return subprocess.run(["adb", *args], check=True, capture_output=True, text=True).stdout


def keys(names: list[str], gap: float) -> None:
    # One shell session: host round-trips would stretch and jitter the gaps.
    script = "; ".join(f"input keyevent {name}; sleep {gap}" for name in names)
    adb("shell", script)


def summary_stats(summary: str) -> dict:
    """Totals and percentiles over every frame since the reset (framestats keeps only 120)."""
    stats = {}
    for raw in summary.splitlines():
        line = raw.strip()
        if line.startswith("Total frames rendered:"):
            stats["frames"] = int(line.split(":")[1])
        elif line.startswith("Janky frames:") and "legacy" not in line:
            stats["janky"] = float(line.split("(")[1].split("%")[0])
        for pct in ("50", "90", "95", "99"):
            if line.startswith(f"{pct}th percentile:") and f"p{pct}" not in stats:
                stats[f"p{pct}"] = float(line.split(":")[1].strip().rstrip("ms"))
        if line.startswith("Number Slow UI thread:"):
            stats["slow_ui"] = int(line.split(":")[1])
        if line.startswith("Number Frame deadline missed:") and "legacy" not in line:
            stats["missed"] = int(line.split(":")[1])
    return stats


def run_once(journey: str, boot_wait: float) -> dict:
    adb("shell", "am", "force-stop", PACKAGE)
    time.sleep(1)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    time.sleep(boot_wait)
    keys(["DPAD_UP"], 0.8)  # content → the Home tab
    time.sleep(1)
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    keys(JOURNEYS[journey], KEY_GAP_SECONDS if "enter" not in journey else 0.8)
    time.sleep(2)
    return summary_stats(adb("shell", "dumpsys", "gfxinfo", PACKAGE))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("pages", nargs="+", choices=sorted(JOURNEYS), metavar="journey")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--boot-wait", type=float, default=12.0, help="seconds for boot sequence + Home")
    args = parser.parse_args()
    for journey in args.pages:
        results = [run_once(journey, args.boot_wait) for _ in range(args.runs)]
        for index, r in enumerate(results, 1):
            print(f"{journey} run {index}: {r.get('frames')} frames, janky {r.get('janky')}%, "
                  f"P50 {r.get('p50')} P90 {r.get('p90')} P99 {r.get('p99')} ms, "
                  f"slow UI {r.get('slow_ui')}, missed {r.get('missed')}")
        def med(key):
            return statistics.median(r.get(key, float("nan")) for r in results)
        print(f"{journey} median: janky {med('janky'):.1f}%, P50 {med('p50'):.0f} P90 {med('p90'):.0f} "
              f"P99 {med('p99'):.0f} ms, slow UI {med('slow_ui'):.0f}, missed {med('missed'):.0f}")


if __name__ == "__main__":
    main()
