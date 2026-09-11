package com.lamphaus.benchmark

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.runner.RunWith

/** QA-05/QA-06/QA-07: usable account, catalog, details and local playback on either device. */
@RunWith(AndroidJUnit4::class)
class FixtureSmokeTest {
    @Test
    fun fixtureHomeDetailsPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val tv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val host = if (tv) "tv.TvActivity" else "mobile.MobileActivity"
        val device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand("am force-stop com.lamphaus.app.benchmark")
        context.startActivity(Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName("com.lamphaus.app.benchmark", "com.lamphaus.app.$host"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.requireFixtureHome()
        device.openFixtureDetails()
        device.playFixtureAndReturn()
        device.pressBack()
        device.requireFixtureHome()
    }
}
