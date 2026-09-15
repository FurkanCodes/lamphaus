package com.lamphaus.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Separately labeled controlled integration run (PERF-02): startup of the
 * timeline APK built with production-like construction enabled —
 * `-Plamphaus.benchmarkCloud=true -Plamphaus.benchmarkUpdates=true` plus the
 * Supabase Gradle properties. The catalog/session stays the deterministic
 * fixture set, the app starts signed out, and no personal account is used, so
 * only initialization and signed-out readiness are measured. Never run this
 * against a default fixture APK or with a personal account signed in.
 *
 * The run is skipped unless it is explicitly requested:
 * `-Pandroid.testInstrumentationRunnerArguments.lamphaus.integration=true`.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class ProductionStartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun requireIntegrationRun() {
        Assume.assumeTrue(
            "Skipped: run with " +
                "-Pandroid.testInstrumentationRunnerArguments.lamphaus.integration=true " +
                "against the cloud/updates-enabled timing APK.",
            InstrumentationRegistry.getArguments().getString("lamphaus.integration") == "true",
        )
    }

    @Test
    fun coldMobileStartupCloudAndUpdates() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = startupMetrics(),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 20,
        setupBlock = {
            requireBenchmarkFormFactor(leanback = false)
            pressHome()
        },
    ) {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(PACKAGE_NAME, "com.lamphaus.app.mobile.MobileActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        check(device.wait(Until.hasObject(By.text("Continue with Google")), 15_000)) {
            "Signed-out mobile surface never became actionable"
        }
    }

    @Test
    fun coldTvStartupCloudAndUpdates() = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = startupMetrics(),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 20,
        setupBlock = {
            requireBenchmarkFormFactor(leanback = true)
            pressHome()
        },
    ) {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(PACKAGE_NAME, "com.lamphaus.app.tv.TvActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        check(device.wait(Until.hasObject(By.text("Finish setup on your phone")), 15_000)) {
            "Signed-out TV pairing surface never became actionable"
        }
    }

    private fun startupMetrics() = listOf(
        StartupTimingMetric(),
        FrameTimingMetric(),
        TraceSectionMetric(
            "lamphaus.startup.readiness",
            TraceSectionMetric.Mode.First,
            label = "startupReadinessFirstMs",
        ),
    )

    private companion object {
        const val PACKAGE_NAME = "com.lamphaus.app.benchmark"
    }
}