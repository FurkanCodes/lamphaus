package com.lamphaus.benchmark

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release-gate benchmarks (plan §7/PERF-01/PERF-02). The timing target
 * (`benchmarkRelease`) mirrors production R8/resource shrinking, so these
 * measurements describe the optimized artifact. Same device, OS, refresh
 * rate, data, network, and thermal conditions for comparisons; at least 20
 * startup iterations. Compare uncompiled execution against Baseline Profile
 * compilation on both platforms; isolate Startup Profile build-time effects
 * with separate controlled APK builds.
 *
 * Trace sections use the fixed `lamphaus.*` names from PerfTrace so a
 * captured Perfetto trace and these metrics describe the same spans.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldMobileStartupCompiled() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = startupMetrics(),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 20,
        setupBlock = { requireBenchmarkFormFactor(leanback = false); pressHome() },
    ) {
        startMobile()
        device.requireFixtureHome()
    }

    @Test
    fun coldMobileStartupUncompiled() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = startupMetrics(),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 20,
        setupBlock = { requireBenchmarkFormFactor(leanback = false); pressHome() },
    ) {
        startMobile()
        device.requireFixtureHome()
    }

    @Test
    fun warmMobileStartup() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = startupMetrics(),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 20,
        setupBlock = { requireBenchmarkFormFactor(leanback = false); pressHome() },
    ) {
        startMobile()
        device.requireFixtureHome()
    }

    @Test
    fun coldTvStartupCompiled() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = startupMetrics(),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 20,
        setupBlock = { requireBenchmarkFormFactor(leanback = true); pressHome() },
    ) {
        startTv()
        device.requireFixtureHome()
    }

    /** TV startup without Baseline Profile compilation (PERF-02 parity). */
    @Test
    fun coldTvStartupUncompiled() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = startupMetrics(),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 20,
        setupBlock = { requireBenchmarkFormFactor(leanback = true); pressHome() },
    ) {
        startTv()
        device.requireFixtureHome()
    }

    @Test
    fun tvDpadFrameTiming() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = { requireBenchmarkFormFactor(leanback = true); pressHome() },
    ) {
        startTv()
        device.requireFixtureHome()
        repeat(6) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        repeat(6) {
            device.pressDPadUp()
            device.waitForIdle()
        }
    }

    /**
     * Browsing a row at reading pace. Each pause outlasts the 240 ms hero
     * delay (TV-MOT-01), so every press settles and triggers the hero
     * crossfade, ambient artwork load, and accent change that the plain
     * D-pad journey above skips.
     */
    @Test
    fun tvRowBrowseFrameTiming() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = { requireBenchmarkFormFactor(leanback = true); pressHome() },
    ) {
        startTv()
        device.requireFixtureHome()
        device.pressDPadDown()
        device.pressDPadDown()
        // Alternate between neighbouring cards so every settle lands on
        // artwork even in the short default fixture row.
        repeat(6) {
            device.pressDPadRight()
            SystemClock.sleep(TV_SETTLE_MILLIS)
            device.pressDPadLeft()
            SystemClock.sleep(TV_SETTLE_MILLIS)
        }
    }

    /**
     * PERF-21: moving through the side rail (the default navigation,
     * TV-NAV-01) into Discover and Library, where each page's first
     * composition happens inside the Select press, then a short walk through
     * each poster grid. Real-TV numbers come from `scripts/perf/tv_page_jank.py`
     * (this metric records nothing on the SEI Box).
     */
    @Test
    fun tvDiscoverLibraryFrameTiming() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = { requireBenchmarkFormFactor(leanback = true); pressHome() },
    ) {
        startTv()
        device.requireFixtureHome()
        // Left at the start of the row opens the rail on Home; moving through
        // it never switches the page, so Home → Discover is three moves and a Select.
        device.pressDPadLeft()
        SystemClock.sleep(TV_SETTLE_MILLIS)
        repeat(3) {
            device.pressDPadDown()
            SystemClock.sleep(TV_SETTLE_MILLIS)
        }
        device.pressDPadCenter() // Discover
        SystemClock.sleep(TV_SETTLE_MILLIS)
        repeat(3) {
            device.pressDPadDown()
            SystemClock.sleep(TV_SETTLE_MILLIS)
        }
        // Left walks to the grid's first column, then opens the rail; extra
        // presses stay on the rail.
        repeat(6) { device.pressDPadLeft() }
        device.waitForIdle()
        device.pressDPadDown()
        device.pressDPadCenter() // Library
        SystemClock.sleep(TV_SETTLE_MILLIS)
        repeat(2) {
            device.pressDPadDown()
            SystemClock.sleep(TV_SETTLE_MILLIS)
        }
    }

    @Test
    fun mobileHomeScrollFrameTiming() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = { requireBenchmarkFormFactor(leanback = false); pressHome() },
    ) {
        startMobile()
        device.requireFixtureHome()
        repeat(4) {
            device.swipe(
                device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 4, 12,
            )
            device.waitForIdle()
        }
    }

    /** Query to first publishable result; the fixture catalog is searchable offline. */
    @Test
    fun mobileSearchFrameTiming() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { requireBenchmarkFormFactor(leanback = false); pressHome() },
    ) {
        startMobile()
        device.requireFixtureHome()
        val search = checkNotNull(device.findObject(By.descContains("Search"))) { "Search destination missing" }
        search.click()
        val field = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)) {
            "Search field did not appear"
        }
        field.text = "aurora"
        check(device.wait(Until.hasObject(By.text("The Last Aurora")), 10_000)) {
            "Fixture search never published a result"
        }
        field.text = ""
        device.waitForIdle()
    }

    /** Selection to first video output through the real playback handoff. */
    @Test
    fun mobilePlaybackEntryFrameTiming() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { requireBenchmarkFormFactor(leanback = false); pressHome() },
    ) {
        startMobile()
        device.requireFixtureHome()
        device.openFixtureDetails()
        checkNotNull(device.findObject(By.text("Play"))).click()
        check(device.wait(Until.hasObject(By.desc("Pause")), 15_000)) { "Local clip did not start playing" }
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
        if (!device.hasObject(By.text("Play"))) device.pressBack()
    }

    private fun homeWindowLoadMetric() =
        TraceSectionMetric("lamphaus.home.window.load", label = "homeWindowLoadSumMs")

    private fun startupMetrics() = listOf(
        StartupTimingMetric(),
        FrameTimingMetric(),
        homeWindowLoadMetric(),
        usableContentMetric(),
    )

    /** Time to usable content or an actionable state, next to TTID/TTFD. */
    private fun usableContentMetric() =
        TraceSectionMetric(
            "lamphaus.startup.readiness",
            TraceSectionMetric.Mode.First,
            label = "startupReadinessFirstMs",
        )

    private fun androidx.benchmark.macro.MacrobenchmarkScope.startMobile() {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(PACKAGE_NAME, "com.lamphaus.app.mobile.MobileActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.startTv() {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(PACKAGE_NAME, "com.lamphaus.app.tv.TvActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        const val PACKAGE_NAME = "com.lamphaus.app.benchmark"
        const val TV_SETTLE_MILLIS = 600L
    }
}