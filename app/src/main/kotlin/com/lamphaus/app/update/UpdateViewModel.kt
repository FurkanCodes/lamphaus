package com.lamphaus.app.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Dedicated update ViewModel (plan §4, SHR-ARC-07/08/09). Exposes immutable
 * StateFlow collected with collectAsStateWithLifecycle; outcomes are durable
 * state, never fire-and-forget events.
 */
class UpdateViewModel(private val coordinator: UpdateCoordinator) : ViewModel() {
    val state: StateFlow<UpdateUiState> = coordinator.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UpdateUiState(),
    )

    /** Hosts report playback/modal ownership so auto prompts defer (plan §4). */
    fun setPresentationBlocked(blocked: Boolean) {
        coordinator.presentationBlocked = blocked
    }

    fun channel(): UpdateChannel = coordinator.channel()
    fun setChannel(channel: UpdateChannel) = coordinator.setChannel(channel)
    fun checkManual() = coordinator.checkManual()
    fun defer() = coordinator.deferSelected()
    fun download(allowMetered: Boolean = false) = coordinator.startDownload(allowMetered)
    fun cancel() = coordinator.cancelDownload()
    fun retry() = coordinator.retryAfterError()
    fun install(hostResumed: Boolean, playbackActive: Boolean): UpdateCoordinator.InstallGate =
        coordinator.beginInstall(hostResumed, playbackActive)

    companion object {
        fun factory(coordinator: UpdateCoordinator): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    UpdateViewModel(coordinator) as T
            }
    }
}
