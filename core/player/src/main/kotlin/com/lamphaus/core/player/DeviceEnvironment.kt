package com.lamphaus.core.player

import android.os.Build

/** Device traits that affect decoder and rendering choices. */
object DeviceEnvironment {
    fun isAndroidEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true)
}
