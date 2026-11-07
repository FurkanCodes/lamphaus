package com.lamphaus.app

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * Remote focus needs a device out of touch mode, and phone layouts need a
 * portrait window, so a test of one form factor is skipped on the other
 * instead of failing there (QA-02, QA-07).
 */
private val television: Boolean
    get() = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        .hasSystemFeature(PackageManager.FEATURE_LEANBACK)

internal fun assumeTelevision() = assumeTrue("needs a television", television)

internal fun assumePhone() = assumeTrue("needs a phone", !television)
