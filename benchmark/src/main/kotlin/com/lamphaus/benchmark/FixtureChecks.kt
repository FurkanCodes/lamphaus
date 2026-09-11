package com.lamphaus.benchmark

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** QA-05/QA-08: never accept a sign-in screen as a successful Home journey. */
internal fun UiDevice.requireFixtureHome() {
    check(wait(Until.hasObject(By.text("The Last Aurora")), 15_000)) {
        "Fixture Home did not load. Verify the isolated benchmark APK is installed."
    }
    waitForIdle()
}

/** QA-07: activity hosts redirect on the wrong form factor. */
internal fun requireBenchmarkFormFactor(leanback: Boolean) {
    val pm = InstrumentationRegistry.getInstrumentation().context.packageManager
    check(pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) == leanback) {
        "Wrong device for this journey: expected leanback=$leanback"
    }
}

internal fun UiDevice.openFixtureDetails() {
    val card = wait(Until.findObject(By.descContains("The Last Aurora").clickable(true)), 10_000)
    checkNotNull(card) { "Fixture card is missing" }.click()
    check(wait(Until.hasObject(By.text("Play")), 10_000)) { "Details did not open" }
}

internal fun UiDevice.playFixtureAndReturn() {
    checkNotNull(findObject(By.text("Play"))).click()
    check(wait(Until.hasObject(By.desc("Pause")), 15_000)) { "Local clip did not start playing" }
    pressBack()
    waitForIdle()
    // Playback Back can first dismiss its controls.
    if (!hasObject(By.text("Play"))) pressBack()
    check(wait(Until.hasObject(By.text("Play")), 10_000)) { "Playback did not return to details" }
}
