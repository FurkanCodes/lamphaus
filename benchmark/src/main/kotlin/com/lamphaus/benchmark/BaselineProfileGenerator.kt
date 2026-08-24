package com.lamphaus.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun mobileStartup() = rule.collect(PACKAGE_NAME) {
        pressHome()
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.mobile.MobileActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        device.waitForIdle()
    }

    @Test
    fun televisionStartup() = rule.collect(PACKAGE_NAME) {
        pressHome()
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.tv.TvActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "com.lamphaus.app"
    }
}
