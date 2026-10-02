package com.musp.musicplayer.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.musp.musicplayer.AppContainer
import com.musp.musicplayer.R
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.appContainer
import com.musp.musicplayer.data.PlaybackStateStore
import com.musp.musicplayer.data.SleepTimer
import com.musp.musicplayer.utils.MusicUtils
import com.musp.musicplayer.utils.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Background playback service built on Media3.
 *
 * Media3 provides: the media-style notification with controls, lock-screen / Bluetooth /
 * headset controls (via MediaSession), audio focus (pauses for calls and resumes afterwards,
 * ducks for navigation prompts), pausing when headphones/Bluetooth disconnect, and
 * wake-lock management while playing.
 */
@OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var container: AppContainer
    private lateinit var stateStore: PlaybackStateStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var restoreJob: Job? = null
    private var isRestoring = false
    private var lastRecordedMediaId: String? = null

    private val sleepRunnable = object : Runnable {
        override fun run() {
            val state = container.sleepTimer.state.value
            if (state is SleepTimer.State.Countdown && state.remainingMs > 1000) {
                // Handler time does not advance in deep sleep; re-check against the real clock
                mainHandler.postDelayed(this, state.remainingMs)
            } else {
                player.pause()
                container.sleepTimer.cancel()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = appContainer
        stateStore = container.playbackStateStore

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(PlayerEventListener())

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildSessionActivity())
            .setCallback(SessionCallback())
            .build()

        observeSleepTimer()
        isRestoring = true
        restoreJob = serviceScope.launch {
            try {
                restorePlaybackState()
            } finally {
                isRestoring = false
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveProgress()
        val player = mediaSession?.player
        if (player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            // Nothing is playing: don't keep the service alive after the app is swiped away
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveProgress()
        mainHandler.removeCallbacks(sleepRunnable)
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun buildSessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ========== Persistence ==========

    private fun saveQueue() {
        if (isRestoring) return
        val ids = (0 until player.mediaItemCount).mapNotNull {
            player.getMediaItemAt(it).mediaId.toLongOrNull()
        }
        stateStore.saveQueue(ids)
    }

    private fun saveProgress() {
        if (!::player.isInitialized || isRestoring) return
        stateStore.saveProgress(
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )
    }

    private suspend fun restorePlaybackState() {
        val snapshot = stateStore.load()
        player.shuffleModeEnabled = snapshot.shuffle
        player.repeatMode = snapshot.repeatMode.takeIf {
            it == Player.REPEAT_MODE_OFF || it == Player.REPEAT_MODE_ONE || it == Player.REPEAT_MODE_ALL
        } ?: Player.REPEAT_MODE_OFF

        if (snapshot.queueIds.isEmpty()) return
        container.musicRepository.ensureLoaded()
        // A controller may have started new playback while the library was loading
        if (player.mediaItemCount > 0) return

        val restored = resolveSnapshot(snapshot) ?: return
        // Not prepared on purpose: nothing is decoded until the user presses play
        player.setMediaItems(restored.mediaItems, restored.startIndex, restored.startPositionMs)
    }

    /** Maps the saved queue onto songs that still exist, skipping deleted files. */
    private fun resolveSnapshot(snapshot: PlaybackStateStore.Snapshot): MediaSession.MediaItemsWithStartPosition? {
        val repository = container.musicRepository
        val items = ArrayList<MediaItem>(snapshot.queueIds.size)
        var startIndex = 0
        var startPosition = 0L
        var foundStart = false
        snapshot.queueIds.forEachIndexed { index, id ->
            val song = repository.getSong(id) ?: return@forEachIndexed
            if (!foundStart && index >= snapshot.index) {
                startIndex = items.size
                // Only resume mid-song if it is the exact song that was playing
                startPosition = if (index == snapshot.index) snapshot.positionMs else 0L
                foundStart = true
            }
            items.add(song.toMediaItem())
        }
        if (items.isEmpty()) return null
        return MediaSession.MediaItemsWithStartPosition(items, startIndex, startPosition)
    }

    // ========== Sleep timer ==========

    private fun observeSleepTimer() {
        serviceScope.launch {
            container.sleepTimer.state.collect { state ->
                mainHandler.removeCallbacks(sleepRunnable)
                player.pauseAtEndOfMediaItems = state is SleepTimer.State.EndOfTrack
                if (state is SleepTimer.State.Countdown) {
                    mainHandler.postDelayed(sleepRunnable, state.remainingMs)
                }
            }
        }
    }

    // ========== Shuffle / queue ==========

    /** Indices in the order the player will play them (respecting shuffle). */
    private fun playOrder(): MutableList<Int> {
        val timeline = player.currentTimeline
        val order = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(player.shuffleModeEnabled)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order.add(index)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        return order
    }

    private fun applyShuffleOrder(order: List<Int>) {
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
    }

    /**
     * ExoPlayer's random shuffle order may place the current song anywhere, which would skip
     * everything before it. Keep the current song first so the whole queue gets played.
     */
    private fun keepCurrentFirstInShuffle() {
        if (!player.shuffleModeEnabled || player.mediaItemCount < 2) return
        val current = player.currentMediaItemIndex
        val order = playOrder()
        if (order.firstOrNull() == current || current !in order) return
        order.remove(current)
        order.add(0, current)
        applyShuffleOrder(order)
    }

    /** Inserts songs right after the current one, also when shuffle is on. */
    private fun playNext(items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (player.mediaItemCount == 0) {
            player.setMediaItems(items)
            player.prepare()
            player.play()
            return
        }
        val insertAt = player.currentMediaItemIndex + 1
        player.addMediaItems(insertAt, items)
        if (player.shuffleModeEnabled) {
            val inserted = (insertAt until insertAt + items.size).toList()
            val order = playOrder().filterNot { it in inserted }.toMutableList()
            val currentPosition = order.indexOf(player.currentMediaItemIndex)
            order.addAll(currentPosition + 1, inserted)
            applyShuffleOrder(order)
        }
    }

    // ========== History ==========

    private fun recordRecentlyPlayed() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (mediaId == lastRecordedMediaId) return
        lastRecordedMediaId = mediaId
        val songId = mediaId.toLongOrNull() ?: return
        // App scope so the write completes even if the service is being torn down
        container.appScope.launch {
            runCatching { container.userLibraryRepository.recordPlayed(songId) }
        }
    }

    // ========== Errors / missing files ==========

    private fun handlePlaybackError(error: PlaybackException) {
        val failedIndex = player.currentMediaItemIndex
        val failedItem = player.currentMediaItem

        if (!MusicUtils.hasAudioPermission(this)) {
            toast(getString(R.string.error_permission_revoked))
            container.musicRepository.requestRefresh()
            return
        }

        if (failedItem == null || !isUnplayableFileError(error.errorCode)) {
            // Transient problem (e.g. audio output); the user can press play to retry
            toast(getString(R.string.error_playback_generic))
            return
        }

        val title = failedItem.mediaMetadata.title ?: getString(R.string.unknown_title)
        toast(getString(R.string.error_skipped_missing, title))
        container.musicRepository.requestRefresh()

        // Remove the broken file from the queue and move on to the next song
        val nextIndex = player.nextMediaItemIndex
        player.removeMediaItem(failedIndex)
        if (player.mediaItemCount == 0) {
            player.stop()
            return
        }
        if (nextIndex == C.INDEX_UNSET) {
            // It was the last song: stop at the start of the queue
            player.seekToDefaultPosition(0)
            player.prepare()
            player.pause()
            return
        }
        val adjusted = if (nextIndex > failedIndex) nextIndex - 1 else nextIndex
        player.seekToDefaultPosition(adjusted.coerceIn(0, player.mediaItemCount - 1))
        player.prepare()
        player.play()
    }

    private fun isUnplayableFileError(code: Int): Boolean = when (code) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> true
        else -> false
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    // ========== Listeners ==========

    private inner class PlayerEventListener : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                keepCurrentFirstInShuffle()
                saveQueue()
                saveProgress()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveProgress()
            if (player.isPlaying) recordRecentlyPlayed()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) recordRecentlyPlayed() else saveProgress()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            keepCurrentFirstInShuffle()
            saveProgress()
        }

        override fun onRepeatModeChanged(repeatMode: Int) = saveProgress()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                container.sleepTimer.state.value is SleepTimer.State.EndOfTrack
            ) {
                container.sleepTimer.cancel()
            }
        }

        override fun onPlayerError(error: PlaybackException) = handlePlaybackError(error)
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Custom commands are only offered to this app's own UI
            if (controller.packageName != packageName) return super.onConnect(session, controller)
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_PLAY_NEXT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_PLAY_NEXT) {
                val songs = container.musicRepository.getSongs(
                    args.getLongArray(EXTRA_SONG_IDS)?.toList().orEmpty()
                )
                playNext(songs.map { it.toMediaItem() })
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            return Futures.immediateFuture(mediaItems.map { resolveMediaItem(it) }.toMutableList())
        }

        /** Called when the system (e.g. Android 13+ media controls, headset button) asks to resume. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return serviceScope.future {
                restoreJob?.join()
                container.musicRepository.ensureLoaded()
                resolveSnapshot(stateStore.load())
                    ?: throw IllegalStateException("No saved queue to resume")
            }
        }
    }

    /** Controllers may strip the URI from media items; rebuild it from our library. */
    private fun resolveMediaItem(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        item.mediaId.toLongOrNull()
            ?.let { container.musicRepository.getSong(it) }
            ?.let { return it.toMediaItem() }
        val uri = item.requestMetadata.mediaUri ?: Uri.EMPTY // empty URI fails and gets skipped
        return item.buildUpon().setUri(uri).build()
    }

    companion object {
        /** Custom session command: insert songs ([EXTRA_SONG_IDS]) right after the current one. */
        const val COMMAND_PLAY_NEXT = "com.musp.musicplayer.PLAY_NEXT"
        const val EXTRA_SONG_IDS = "song_ids"
    }
}
