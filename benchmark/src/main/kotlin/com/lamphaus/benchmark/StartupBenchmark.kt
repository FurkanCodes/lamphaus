package com.lamphaus.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release-gate benchmarks (plan §7). Same device, OS, refresh rate, data,
 * network, and thermal conditions for comparisons; at least 20 startup
 * iterations. Compare uncompiled execution against Baseline Profile
 * compilation; isolate Startup Profile build-time effects with separate
 * controlled APK builds.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldMobileStartupCompiled() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 20,
        setupBlock = { requireBenchmarkFormFactor(leanback = false); pressHome() },
    ) {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(PACKAGE_NAME, "com.lamphaus.app.mobile.MobileActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        device.requireFixtureHome()
    }

    @Test
    fun coldMobileStartupUncompiled() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 20,
        setupBlock = { requireBenchmarkFormFactor(leanback = false); pressHome() },
    ) {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(PACKAGE_NAME, "com.lamphaus.app.mobile.MobileActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        device.requireFixtureHome()
    }

    @Test
    fun warmMobileStartup() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 20,
        setupBlock = { requireBenchmarkFormFactor(leanback = false); pressHome() },
    ) {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(PACKAGE_NAME, "com.lamphaus.app.mobile.MobileActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        device.requireFixtureHome()
    }

    @Test
    fun coldTvStartupCompiled() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 20,
        setupBlock = { requireBenchmarkFormFactor(leanback = true); pressHome() },
    ) {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(PACKAGE_NAME, "com.lamphaus.app.tv.TvActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(PACKAGE_NAME, "com.lamphaus.app.tv.TvActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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

    private companion object {
        const val PACKAGE_NAME = "com.lamphaus.app.benchmark"
    }
}
