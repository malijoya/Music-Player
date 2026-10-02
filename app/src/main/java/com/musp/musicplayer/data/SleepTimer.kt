package com.musp.musicplayer.data

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared sleep-timer state. The UI sets it; [com.musp.musicplayer.service.MusicService]
 * observes it and pauses playback when it fires, so it keeps working in the background.
 */
class SleepTimer {

    sealed interface State {
        data object Off : State
        /** @param endElapsedRealtime [SystemClock.elapsedRealtime] at which playback pauses */
        data class Countdown(val endElapsedRealtime: Long) : State {
            val remainingMs: Long get() = (endElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        }
        data object EndOfTrack : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(minutes: Int) {
        _state.value = State.Countdown(SystemClock.elapsedRealtime() + minutes * 60_000L)
    }

    fun stopAtEndOfTrack() {
        _state.value = State.EndOfTrack
    }

    fun cancel() {
        _state.value = State.Off
    }
}
