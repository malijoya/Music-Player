package com.musp.musicplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.musp.musicplayer.appContainer
import com.musp.musicplayer.data.SleepTimer
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.service.MusicService
import com.musp.musicplayer.utils.toFallbackSong
import com.musp.musicplayer.utils.toMediaItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

/** @param index the real index in the player's playlist (used for seek/remove/move) */
data class QueueItem(val index: Int, val song: Song, val isCurrent: Boolean)

/**
 * Connects to [MusicService] through a [MediaController] and exposes playback state to the UI.
 * Scoped to the activity so it survives configuration changes; released in [onCleared].
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val musicRepository = container.musicRepository
    val sleepTimer: StateFlow<SleepTimer.State> = container.sleepTimer.state

    private val controllerFuture: ListenableFuture<MediaController>
    private val controllerDeferred = CompletableDeferred<MediaController>()
    private var controller: MediaController? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    /** Current position, ticking only while someone is collecting (i.e. a screen is visible). */
    val positionMs: StateFlow<Long> = flow {
        while (true) {
            emit(controller?.currentPosition?.coerceAtLeast(0L) ?: 0L)
            delay(if (_uiState.value.isPlaying) 500L else 1000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), 0L)

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState()
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
                )
            ) {
                updateQueue()
            }
        }
    }

    init {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, MusicService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        viewModelScope.launch {
            val connected = try {
                controllerFuture.await()
            } catch (e: Exception) {
                return@launch
            }
            controller = connected
            connected.addListener(playerListener)
            controllerDeferred.complete(connected)
            updateState()
            updateQueue()
        }
        // The queue may be restored before the library finishes scanning: refresh song details
        viewModelScope.launch {
            musicRepository.state.collect {
                updateState()
                updateQueue()
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
        super.onCleared()
    }

    private fun withController(action: (MediaController) -> Unit) {
        val ready = controller
        if (ready != null) {
            action(ready)
        } else {
            viewModelScope.launch { action(controllerDeferred.await()) }
        }
    }

    private fun songAt(player: Player, index: Int): Song {
        val item = player.getMediaItemAt(index)
        return item.mediaId.toLongOrNull()?.let(musicRepository::getSong) ?: item.toFallbackSong()
    }

    private fun updateState() {
        val player = controller ?: return
        val item = player.currentMediaItem
        val song = item?.let {
            it.mediaId.toLongOrNull()?.let(musicRepository::getSong) ?: it.toFallbackSong()
        }
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (song?.duration ?: 0L)
        _uiState.value = PlaybackUiState(
            song = song,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = duration,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem()
        )
    }

    /** Builds the queue in actual play order (respecting shuffle). */
    private fun updateQueue() {
        val player = controller ?: return
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            _queue.value = emptyList()
            return
        }
        val shuffle = player.shuffleModeEnabled
        val current = player.currentMediaItemIndex
        val items = ArrayList<QueueItem>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(shuffle)
        while (index != C.INDEX_UNSET && items.size < timeline.windowCount) {
            items.add(QueueItem(index, songAt(player, index), index == current))
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        _queue.value = items
    }

    // ========== Playback Control Functions ==========

    /** Replace the queue with [songs] and start playing at [startIndex]. */
    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean? = null) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        val start = startIndex.coerceIn(0, songs.lastIndex)
        withController { player ->
            shuffle?.let { player.shuffleModeEnabled = it }
            player.setMediaItems(items, start, 0L)
            player.prepare()
            player.play()
        }
    }

    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        playSongs(songs, songs.indices.random(), shuffle = true)
    }

    fun playPause() = withController { player ->
        when {
            player.isPlaying -> player.pause()
            player.mediaItemCount == 0 -> Unit
            else -> {
                // Restored queues are not prepared until the user presses play
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekToDefaultPosition(player.currentMediaItemIndex)
                }
                player.play()
            }
        }
    }

    fun next() = withController { it.seekToNext() }

    /** Restarts the song if more than ~3 seconds in, otherwise goes to the previous song. */
    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeatMode() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ========== Queue ==========

    /** Handled by the service so the songs really play next, even with shuffle on. */
    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { player ->
            player.sendCustomCommand(
                SessionCommand(MusicService.COMMAND_PLAY_NEXT, Bundle.EMPTY),
                bundleOf(MusicService.EXTRA_SONG_IDS to songs.map { it.id }.toLongArray())
            )
        }
    }

    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { player ->
            if (player.mediaItemCount == 0) {
                playSongs(songs)
            } else {
                player.addMediaItems(songs.map { it.toMediaItem() })
            }
        }
    }

    fun playQueueItem(index: Int) = withController { player ->
        if (index !in 0 until player.mediaItemCount) return@withController
        player.seekToDefaultPosition(index)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun removeQueueItem(index: Int) = withController { player ->
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) = withController { player ->
        val count = player.mediaItemCount
        if (from in 0 until count && to in 0 until count && from != to) {
            player.moveMediaItem(from, to)
        }
    }

    fun clearQueue() = withController { player ->
        player.stop()
        player.clearMediaItems()
    }

    // ========== Sleep timer ==========

    fun setSleepTimer(minutes: Int) = container.sleepTimer.start(minutes)

    fun setSleepTimerEndOfTrack() = container.sleepTimer.stopAtEndOfTrack()

    fun cancelSleepTimer() = container.sleepTimer.cancel()

    val sleepTimerRemaining: Flow<Long?> = flow {
        while (true) {
            when (val state = sleepTimer.value) {
                is SleepTimer.State.Countdown -> emit(state.remainingMs)
                SleepTimer.State.EndOfTrack -> emit(-1L)
                SleepTimer.State.Off -> emit(null)
            }
            delay(1000)
        }
    }
}
