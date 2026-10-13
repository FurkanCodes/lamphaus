package com.lamphaus.app.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.lamphaus.app.BuildConfig
import com.lamphaus.app.LamphausApplication
import com.lamphaus.core.data.diagnostics.PlaybackReportStore
import com.lamphaus.core.model.AppFacts
import com.lamphaus.core.model.CrashFacts
import com.lamphaus.core.model.DeviceFacts
import com.lamphaus.core.model.DiagnosticText
import com.lamphaus.core.model.PlaybackReport
import com.lamphaus.core.model.ProcessExitFacts
import com.lamphaus.core.model.ReportKind
import com.lamphaus.core.model.ReportOutcome
import com.lamphaus.core.player.PlaybackDiagnostics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Playback diagnostics for the app process (SHR-PROD-18): connects the
 * recorder to the journal, writes crashes before the process dies, and on
 * the next launch reads why Android ended the previous process.
 */
object AppDiagnostics {
    private const val DIRECTORY = "diagnostics"

    /** Exits and pending sends wait until startup has settled (QA-08). */
    private const val STARTUP_DELAY_MILLIS = 15_000L
    private const val MAX_TRACE_BYTES = 512 * 1024

    @Volatile private lateinit var appContext: Context

    /**
     * The journal lives in the app's external files folder, so a developer
     * can `adb pull` it from a release build; it falls back to internal
     * storage when that folder is unavailable.
     */
    val store: PlaybackReportStore by lazy {
        PlaybackReportStore { appContext.getExternalFilesDir(DIRECTORY) ?: File(appContext.filesDir, DIRECTORY) }
    }

    private val app = AppFacts(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TYPE)
    private val device: DeviceFacts by lazy { deviceFacts(appContext) }

    fun install(application: LamphausApplication) {
        appContext = application
        PlaybackDiagnostics.install(application, app, { device }) { report, closed ->
            SentryDiagnostics.onPlaybackReport(report, closed)
            application.container.playbackReports.record(report, closed)
        }
        installCrashHandler()
        // Sentry follows the Privacy switches from the first settings read on,
        // started off the main thread (QA-08). Posted, so the app's
        // dependencies are built where they always are, not inside onCreate.
        Handler(Looper.getMainLooper()).post {
            val container = application.container
            container.applicationScope.launch {
                container.preferences.settings
                    .map { it.diagnostics }
                    .distinctUntilChanged()
                    .collect { consent -> SentryDiagnostics.apply(application, consent) }
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val container = application.container
            container.applicationScope.launch {
                container.playbackReports.reconcileExits(
                    readExits = { since -> processExits(application, since) },
                    currentPid = Process.myPid(),
                    template = PlaybackReport(id = "", startedAtEpochMillis = 0, app = app, device = device),
                    newId = { UUID.randomUUID().toString() },
                )
                container.playbackReports.sendPending()
            }
        }, STARTUP_DELAY_MILLIS)
    }

    /**
     * Writes the crash, and the playback it interrupted, before handing the
     * exception to the platform. Only classes and frames are kept.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val onMainThread = thread === Looper.getMainLooper().thread
                PlaybackDiagnostics.crashSnapshot(error, onMainThread)?.let(store::write)
                val now = System.currentTimeMillis()
                store.write(
                    PlaybackReport(
                        id = UUID.randomUUID().toString(),
                        kind = ReportKind.CRASH,
                        startedAtEpochMillis = now,
                        pid = Process.myPid(),
                        app = app,
                        device = device,
                        outcome = ReportOutcome.PROCESS_DIED,
                        crash = CrashFacts(now, onMainThread, DiagnosticText.exceptions(error)),
                    ),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Android's records of how this app's processes ended, newer than [sinceEpochMillis]. */
    private fun processExits(context: Context, sinceEpochMillis: Long): List<ProcessExitFacts> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        return manager.getHistoricalProcessExitReasons(context.packageName, 0, 16)
            .filter { it.timestamp > sinceEpochMillis }
            .map { info ->
                ProcessExitFacts(
                    atEpochMillis = info.timestamp,
                    pid = info.pid,
                    reason = exitReasonName(info.reason),
                    status = info.status,
                    importance = info.importance,
                    description = DiagnosticText.clean(info.description, 200),
                    pssKb = info.pss,
                    rssKb = info.rss,
                    mainThread = if (info.reason == ApplicationExitInfo.REASON_ANR) anrMainThread(info) else emptyList(),
                )
            }
    }

    private fun anrMainThread(info: ApplicationExitInfo): List<String> = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        info.traceInputStream?.use { stream ->
            DiagnosticText.anrMainThread(stream.readNBytesCompat(MAX_TRACE_BYTES).decodeToString())
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (output.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "UNKNOWN"
    }

    private fun deviceFacts(context: Context): DeviceFacts {
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { info -> runCatching { manager?.getMemoryInfo(info) } }
        return DeviceFacts(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            television = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            ramMb = memory.totalMem / (1024 * 1024),
            lowRam = manager?.isLowRamDevice == true,
        )
    }
}
