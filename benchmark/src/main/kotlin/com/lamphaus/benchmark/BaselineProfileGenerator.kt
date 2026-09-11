package com.lamphaus.benchmark

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile journeys (plan §7). Mobile and TV coverage is generated
 * on separate targets: TvActivity redirects to mobile on phones and vice
 * versa, so each journey asserts the expected form factor and fails loudly
 * on the wrong device instead of capturing meaningless behavior.
 *
 * Only startup journeys use includeInStartupProfile=true; everything else
 * belongs in the main Baseline Profile to keep startup-specific code compact.
 * Journeys use the isolated local fixture account and fail if Home is absent.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    // ── Mobile ──────────────────────────────────────────────────────────

    @Test
    fun mobileStartup() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        requireFormFactor(leanback = false)
        pressHome()
        startActivityAndWait(mobileIntent())
        device.requireFixtureHome()
        device.waitForIdle()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), STARTUP_TIMEOUT)
    }

    @Test
    fun mobileHomeScroll() = rule.collect(PACKAGE_NAME) {
        requireFormFactor(leanback = false)
        pressHome()
        startActivityAndWait(mobileIntent())
        device.requireFixtureHome()
        device.waitForIdle()
        repeat(4) {
            device.swipe(
                device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 4, 12,
            )
            device.waitForIdle()
        }
    }

    @Test
    fun mobileDetailsAndSearch() = rule.collect(PACKAGE_NAME) {
        requireFormFactor(leanback = false)
        pressHome()
        startActivityAndWait(mobileIntent())
        device.requireFixtureHome()
        device.waitForIdle()
        device.openFixtureDetails()
        device.pressBack()
        device.requireFixtureHome()
        val search = checkNotNull(device.findObject(By.descContains("Search")))
        search.click()
        device.waitForIdle()
    }

    @Test
    fun mobilePlaybackEntryAndReturn() = rule.collect(PACKAGE_NAME) {
        requireFormFactor(leanback = false)
        pressHome()
        startActivityAndWait(mobileIntent())
        device.requireFixtureHome()
        device.waitForIdle()
        device.openFixtureDetails()
        device.playFixtureAndReturn()

    }

    // ── TV ──────────────────────────────────────────────────────────────

    @Test
    fun televisionStartup() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        requireFormFactor(leanback = true)
        pressHome()
        startActivityAndWait(tvIntent())
        device.requireFixtureHome()
        device.waitForIdle()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), STARTUP_TIMEOUT)
    }

    @Test
    fun televisionDpadRails() = rule.collect(PACKAGE_NAME) {
        requireFormFactor(leanback = true)
        pressHome()
        startActivityAndWait(tvIntent())
        device.requireFixtureHome()
        device.waitForIdle()
        // D-pad traversal across rails with focus restoration.
        repeat(3) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        repeat(4) {
            device.pressDPadRight()
            device.waitForIdle()
        }
        repeat(4) {
            device.pressDPadLeft()
            device.waitForIdle()
        }
        repeat(3) {
            device.pressDPadUp()
            device.waitForIdle()
        }
    }

    @Test
    fun televisionDetailsAndPlayback() = rule.collect(PACKAGE_NAME) {
        requireFormFactor(leanback = true)
        pressHome()
        startActivityAndWait(tvIntent())
        device.requireFixtureHome()
        device.waitForIdle()
        device.pressDPadDown()
        device.waitForIdle()
        device.pressDPadCenter()
        device.waitForIdle()
        // Return restores the originating item and row (TV-NAV-07).
        device.pressBack()
        device.waitForIdle()
    }

    private fun requireFormFactor(leanback: Boolean) {
        val pm = InstrumentationRegistry.getInstrumentation().context.packageManager
        val isTv = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        check(isTv == leanback) {
            "Wrong target: leanback=$isTv but journey needs leanback=$leanback. " +
                "Generate mobile and TV profiles on separate targets."
        }
    }

    private fun mobileIntent() = Intent(Intent.ACTION_MAIN)
        .setComponent(ComponentName(PACKAGE_NAME, "com.lamphaus.app.mobile.MobileActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun tvIntent() = Intent(Intent.ACTION_MAIN)
        .setComponent(ComponentName(PACKAGE_NAME, "com.lamphaus.app.tv.TvActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val PACKAGE_NAME = "com.lamphaus.app.benchmark"
        const val STARTUP_TIMEOUT = 10_000L
    }
}
