package com.musp.musicplayer.ui

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musp.musicplayer.model.Song

/**
 * A throwaway player for previewing songs while picking them, kept apart from
 * [com.musp.musicplayer.service.MusicService] so the real queue, position and
 * notification are never touched.
 */
class SongPreviewPlayer(context: Context, private val onChanged: () -> Unit) {

    private val player = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) stop() else onChanged()
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = onChanged()

                override fun onPlayerError(error: PlaybackException) = stop()
            })
        }

    /** The song loaded for preview, or null when nothing is. */
    var songId: Long? = null
        private set

    /** True while the preview is playing or about to (buffering). */
    val isPlaying: Boolean get() = songId != null && player.playWhenReady

    /** Playback progress of the preview in 0..1. */
    val progress: Float
        get() {
            val duration = player.duration
            if (duration == C.TIME_UNSET || duration <= 0) return 0f
            return (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
        }

    /** Starts [song], or pauses / resumes it when it is already the one loaded. */
    fun toggle(song: Song) {
        if (song.id == songId) {
            player.playWhenReady = !player.playWhenReady
            return
        }
        songId = song.id
        player.setMediaItem(MediaItem.fromUri(song.uri))
        player.prepare()
        player.play()
        onChanged()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        if (songId == null) return
        songId = null
        player.stop()
        player.clearMediaItems()
        onChanged()
    }

    fun release() {
        songId = null
        player.release()
    }
}
