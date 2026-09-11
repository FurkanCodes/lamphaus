package com.lamphaus.app.update

import android.content.Context
import android.os.SystemClock
import com.lamphaus.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Application-scoped update coordinator (plan §4, SHR-ARC-02/07/08/10).
 * Independent of account sync and the large application ViewModel; wired
 * through the manual dependency container.
 *
 * Presentation policy:
 * - Async cold-launch + foreground-after-30m checks; never delays first display.
 * - 15-minute automatic cooldown with bounded failure backoff; manual checks
 *   bypass reminders/cooldown but honor server retry.
 * - Later/Back/dismissal defers that version 24h; newer versions independently
 *   eligible; once-per-session automatic presentation.
 * - Automatic prompts deferred during playback, PiP, Cast, auth handoffs, and
 *   other active modals.
 */
class UpdateCoordinator(
    private val context: Context,
    private val repository: UpdateRepository,
    private val prefs: UpdatePreferences,
    private val downloader: UpdateDownloader,
    private val installer: UpdateInstaller,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checkJob: Job? = null
    private var lastForegroundCheckUptime: Long = 0
    private var autoPresentedSession: Int = -1

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** Playback/modal guard set by hosts before presenting automatically. */
    @Volatile
    var presentationBlocked: Boolean = false
    fun channel(): UpdateChannel = prefs.channel

    /** Stable-only never downgrades: switching channels only affects future offers. */
    fun setChannel(channel: UpdateChannel) {
        prefs.channel = channel
        checkManual()
    }

    fun onColdLaunch() {
        scope.launch { checkAutomatic() }
        scope.launch { reconcile() }
    }

    fun onForegroundReturn() {
        val now = SystemClock.uptimeMillis()
        if (now - lastForegroundCheckUptime < FOREGROUND_GAP_MILLIS) return
        lastForegroundCheckUptime = now
        val lastCheck = prefs.lastAutoCheckMillis
        if (System.currentTimeMillis() - lastCheck < AUTO_COOLDOWN_MILLIS) return
        scope.launch { checkAutomatic() }
        scope.launch { reconcile() }
    }

    fun checkManual() {
        checkJob?.cancel()
        checkJob = scope.launch {
            _state.value = _state.value.copy(phase = UpdatePhase.Checking, manual = true)
            val result = repository.check(manual = true)
            prefs.lastAutoCheckMillis = System.currentTimeMillis()
            applyResult(result, manual = true)
        }
    }

    private suspend fun checkAutomatic() {
        if (checkJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (now - prefs.lastAutoCheckMillis < AUTO_COOLDOWN_MILLIS) return
        val backoff = BACKOFF_BASE_MILLIS * (1 shl prefs.consecutiveFailures.coerceAtMost(4))
        if (now - prefs.lastAutoCheckMillis < backoff && prefs.consecutiveFailures > 0) return
        checkJob = scope.launch {
            _state.value = _state.value.copy(phase = UpdatePhase.Checking)
            val result = repository.check()
            prefs.lastAutoCheckMillis = now
            applyResult(result, manual = false)
        }
        checkJob?.join()
    }

    private fun applyResult(result: UpdateRepository.CheckResult, manual: Boolean) {
        when (result.status) {
            UpdateRepository.Status.AVAILABLE -> {
                val rel = requireNotNull(result.candidate)
                if (!manual && isDeferred(rel.versionCode)) {
                    _state.value = UpdateUiState(phase = UpdatePhase.Idle)
                    return
                }
                if (!manual && presentationBlocked) {
                    _state.value = UpdateUiState(
                        phase = UpdatePhase.Available,
                        release = rel,
                        autoDeferredByContext = true,
                    )
                    return
                }
                if (!manual && autoPresentedSession == rel.versionCode) {
                    _state.value = UpdateUiState(phase = UpdatePhase.Available, release = rel, silent = true)
                    return
                }
                if (!manual) autoPresentedSession = rel.versionCode
                _state.value = UpdateUiState(phase = UpdatePhase.Available, release = rel, manual = manual)
            }
            UpdateRepository.Status.WAITING_FOR_STABLE ->
                _state.value = UpdateUiState(phase = UpdatePhase.WaitingForStable)
            UpdateRepository.Status.NO_COMPATIBLE ->
                _state.value = UpdateUiState(phase = UpdatePhase.NoCompatible)
            UpdateRepository.Status.UP_TO_DATE ->
                _state.value = UpdateUiState(phase = UpdatePhase.UpToDate)
            UpdateRepository.Status.CHECK_FAILED -> {
                prefs.consecutiveFailures += 1
                // A network failure is never an up-to-date result (plan §4).
                _state.value = UpdateUiState(phase = UpdatePhase.CheckFailed)
            }
        }
    }

    private fun isDeferred(versionCode: Int): Boolean {
        val until = prefs.reminderDeadline(versionCode)
        if (until <= 0) return false
        // Clock changes must not suppress indefinitely: a deadline more than
        // 25h out is treated as stale.
        if (until - System.currentTimeMillis() > REMINDER_MAX_SKEW_MILLIS) return false
        return System.currentTimeMillis() < until
    }

    fun deferSelected() {
        val code = _state.value.release?.versionCode ?: return
        prefs.defer(code, System.currentTimeMillis() + REMINDER_MILLIS)
        _state.value = UpdateUiState(phase = UpdatePhase.Idle)
    }

    fun startDownload(allowMetered: Boolean = false) {
        val rel = _state.value.release ?: return
        scope.launch {
            _state.value = _state.value.copy(phase = UpdatePhase.Downloading, progress = 0f)
            val id = downloader.enqueue(rel.apk.url, rel.versionCode)
            prefs.selectedSha256 = rel.apk.sha256
            if (allowMetered) downloader.setMeteredAllowed(id, true)
            pollDownload(rel)
        }
    }

    private suspend fun pollDownload(rel: UpdateFeed.Release) {
        val id = prefs.downloadId
        while (true) {
            delay(500)
            // Reconcile against authoritative DownloadManager state (plan §4).
            val p = downloader.query(id)
            if (p == null) {
                _state.value = _state.value.copy(phase = UpdatePhase.Error, errorKind = UpdateError.DOWNLOAD_LOST)
                return
            }
            when (p.status) {
                android.app.DownloadManager.STATUS_RUNNING,
                android.app.DownloadManager.STATUS_PENDING,
                -> {
                    val frac = if (p.totalBytes > 0) p.downloadedBytes.toFloat() / p.totalBytes else 0f
                    _state.value = _state.value.copy(phase = UpdatePhase.Downloading, progress = frac)
                }
                android.app.DownloadManager.STATUS_PAUSED -> {
                    _state.value = _state.value.copy(phase = UpdatePhase.Waiting, progress = 0f)
                }
                android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                    _state.value = _state.value.copy(phase = UpdatePhase.Verifying)
                    val file = downloader.copyAndVerify(rel.versionCode, rel.apk.byteLength, rel.apk.sha256)
                    if (file == null) {
                        _state.value = _state.value.copy(phase = UpdatePhase.Error, errorKind = UpdateError.VERIFY_FAILED)
                        return
                    }
                    if (!repository.revalidate(rel)) {
                        // Preserve the verified download; offer Retry (plan §4).
                        _state.value = _state.value.copy(phase = UpdatePhase.Error, errorKind = UpdateError.WITHDRAWN_OR_STALE)
                        return
                    }
                    _state.value = _state.value.copy(phase = UpdatePhase.Ready)
                    return
                }
                else -> {
                    _state.value = _state.value.copy(phase = UpdatePhase.Error, errorKind = UpdateError.DOWNLOAD_FAILED)
                    return
                }
            }
        }
    }

    fun cancelDownload() {
        downloader.cancel()
        _state.value = UpdateUiState(phase = UpdatePhase.Idle)
    }

    fun retryAfterError() {
        _state.value = UpdateUiState(phase = UpdatePhase.Idle)
        checkManual()
    }

    /** Launch installation UI only from a resumed host in the active flow. */
    fun beginInstall(hostResumed: Boolean, playbackActive: Boolean): InstallGate {
        if (playbackActive) return InstallGate.DEFERRED_PLAYBACK
        if (!hostResumed) return InstallGate.DEFERRED_NO_HOST
        val rel = _state.value.release ?: return InstallGate.NO_RELEASE
        if (!installer.canRequestInstalls()) {
            _state.value = _state.value.copy(phase = UpdatePhase.PermissionRequired)
            return InstallGate.NEEDS_PERMISSION
        }
        scope.launch {
            _state.value = _state.value.copy(phase = UpdatePhase.Installing)
            val file = downloader.privateFile(rel.versionCode)
            if (!file.exists()) {
                _state.value = _state.value.copy(phase = UpdatePhase.Error, errorKind = UpdateError.MISSING_FILE)
                return@launch
            }
            val session = installer.createSession(file.length())
            prefs.installerSessionId = session.sessionId
            if (!installer.writeSession(session.sessionId, file, rel.apk.sha256)) {
                _state.value = _state.value.copy(phase = UpdatePhase.Error, errorKind = UpdateError.VERIFY_FAILED)
                return@launch
            }
            installer.commit(session.sessionId, rel.versionCode)
        }
        return InstallGate.STARTED
    }

    /** Confirm self-update success from the installed package on next launch. */
    fun confirmInstalledVersion(): Boolean {
        val selected = prefs.selectedVersionCode
        if (selected <= 0) return false
        if (installer.installedVersionCode() >= selected) {
            downloader.cleanupStale(emptyList())
            prefs.selectedVersionCode = -1
            prefs.selectedSha256 = ""
            prefs.downloadId = -1
            prefs.installerSessionId = -1
            _state.value = UpdateUiState(phase = UpdatePhase.Complete)
            return true
        }
        return false
    }

    /** Reconcile persisted hints against authoritative system state. */
    suspend fun reconcile() {
        val active = downloader.existingActive()
        if (active == null && _state.value.phase == UpdatePhase.Downloading) {
            _state.value = _state.value.copy(phase = UpdatePhase.Error, errorKind = UpdateError.DOWNLOAD_LOST)
        }
        confirmInstalledVersion()
        // Retain retryable downloads up to 7 days; drop superseded ones.
        downloader.cleanupStale(listOfNotNull(_state.value.release?.versionCode?.takeIf { it > 0 }))
    }

    enum class InstallGate { STARTED, NEEDS_PERMISSION, DEFERRED_PLAYBACK, DEFERRED_NO_HOST, NO_RELEASE }

    companion object {
        const val AUTO_COOLDOWN_MILLIS = 15 * 60_000L
        const val FOREGROUND_GAP_MILLIS = 30 * 60_000L
        const val REMINDER_MILLIS = 24 * 60 * 60_000L
        const val REMINDER_MAX_SKEW_MILLIS = 25 * 60 * 60_000L
        const val BACKOFF_BASE_MILLIS = 60_000L
        fun enabled(): Boolean = BuildConfig.UPDATES_ENABLED
    }
}

/** Checking → Available → Downloading/Waiting → Verifying → Ready → Permission → Installing → Complete/Error */
enum class UpdatePhase {
    Idle, Checking, Available, Downloading, Waiting, Verifying, Ready,
    PermissionRequired, Installing, Complete, Error, UpToDate, CheckFailed,
    NoCompatible, WaitingForStable,
}

enum class UpdateError { DOWNLOAD_FAILED, DOWNLOAD_LOST, VERIFY_FAILED, WITHDRAWN_OR_STALE, MISSING_FILE, INSTALL_FAILED }

/** Immutable UI state for the dedicated UpdateViewModel (SHR-ARC-08/09). */
data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val release: UpdateFeed.Release? = null,
    val progress: Float = 0f,
    val errorKind: UpdateError? = null,
    val manual: Boolean = false,
    val silent: Boolean = false,
    val autoDeferredByContext: Boolean = false,
)
