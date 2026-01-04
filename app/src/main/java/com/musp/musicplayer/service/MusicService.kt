package com.musp.musicplayer.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.notification.MusicNotification

class MusicService : Service(), MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener,
    MediaPlayer.OnErrorListener {

    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    private var currentSong: Song? = null
    private var playlist: List<Song> = emptyList()
    private var currentPosition = 0
    private var isShuffleEnabled = false
    private var repeatMode = RepeatMode.OFF // OFF, ONE, ALL
    
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private lateinit var sharedPreferences: SharedPreferences
    
    // Callbacks for UI updates
    private var onSongChangeListener: ((Song) -> Unit)? = null
    private var onPlaybackStateChangeListener: ((Boolean) -> Unit)? = null

    enum class RepeatMode {
        OFF, ONE, ALL
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sharedPreferences = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
        
        // Create notification channel
        MusicNotification.createNotificationChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    // ========== Playback Control Functions ==========

    fun setPlaylist(songs: List<Song>, startPosition: Int = 0) {
        this.playlist = songs
        this.currentPosition = startPosition
        if (songs.isNotEmpty()) {
            playSong(songs[startPosition])
        }
    }

    fun playSong(song: Song) {
        if (currentSong?.id == song.id && mediaPlayer?.isPlaying == true) {
            return
        }
        
        currentSong = song
        
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(song.uri)
                setOnCompletionListener(this@MusicService)
                setOnPreparedListener(this@MusicService)
                setOnErrorListener(this@MusicService)
                prepareAsync()
            }
            
            requestAudioFocus()
            onSongChangeListener?.invoke(song)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mp?.start()
        onPlaybackStateChangeListener?.invoke(true)
        showNotification()
    }

    fun play() {
        if (mediaPlayer == null && currentSong != null) {
            playSong(currentSong!!)
        } else {
            requestAudioFocus()
            mediaPlayer?.start()
            onPlaybackStateChangeListener?.invoke(true)
            showNotification()
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        onPlaybackStateChangeListener?.invoke(false)
        showNotification()
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        onPlaybackStateChangeListener?.invoke(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun playPause() {
        if (isPlaying()) {
            pause()
        } else {
            play()
        }
    }

    fun next() {
        if (playlist.isEmpty()) return
        
        if (isShuffleEnabled) {
            currentPosition = (0 until playlist.size).random()
        } else {
            currentPosition = (currentPosition + 1) % playlist.size
        }
        
        playSong(playlist[currentPosition])
    }

    fun previous() {
        if (playlist.isEmpty()) return
        
        // If more than 3 seconds into song, restart it
        if (getCurrentPosition() > 3000) {
            seekTo(0)
            return
        }
        
        if (isShuffleEnabled) {
            currentPosition = (0 until playlist.size).random()
        } else {
            currentPosition = if (currentPosition - 1 < 0) {
                playlist.size - 1
            } else {
                currentPosition - 1
            }
        }
        
        playSong(playlist[currentPosition])
    }

    override fun onCompletion(mp: MediaPlayer?) {
        when (repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0)
                play()
            }
            RepeatMode.ALL -> {
                next()
            }
            RepeatMode.OFF -> {
                if (currentPosition < playlist.size - 1) {
                    next()
                } else {
                    stop()
                }
            }
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        mediaPlayer?.release()
        mediaPlayer = null
        return false
    }

    // ========== Playback State Functions ==========

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun getCurrentSong(): Song? = currentSong

    // ========== Shuffle and Repeat ==========

    fun toggleShuffle(): Boolean {
        isShuffleEnabled = !isShuffleEnabled
        savePlaybackState()
        return isShuffleEnabled
    }

    fun isShuffleEnabled(): Boolean = isShuffleEnabled

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        savePlaybackState()
    }

    fun cycleRepeatMode(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        savePlaybackState()
        return repeatMode
    }

    fun getRepeatMode(): RepeatMode = repeatMode

    // ========== Listeners ==========

    fun setOnSongChangeListener(listener: (Song) -> Unit) {
        onSongChangeListener = listener
    }

    fun setOnPlaybackStateChangeListener(listener: (Boolean) -> Unit) {
        onPlaybackStateChangeListener = listener
    }

    // ========== Audio Focus ==========

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            mediaPlayer?.setVolume(0.3f, 0.3f)
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            mediaPlayer?.setVolume(1.0f, 1.0f)
                        }
                    }
                }
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            mediaPlayer?.setVolume(0.3f, 0.3f)
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            mediaPlayer?.setVolume(1.0f, 1.0f)
                        }
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    // ========== Notification ==========

    private fun showNotification() {
        currentSong?.let { song ->
            val notification = MusicNotification.createNotification(this, song, isPlaying())
            startForeground(MusicNotification.NOTIFICATION_ID, notification)
        }
    }

    fun updateNotification() {
        showNotification()
    }

    // ========== Persistence ==========

    private fun savePlaybackState() {
        sharedPreferences.edit().apply {
            putBoolean("shuffle", isShuffleEnabled)
            putString("repeat", repeatMode.name)
            putLong("lastSongId", currentSong?.id ?: -1)
            apply()
        }
    }

    fun restorePlaybackState() {
        isShuffleEnabled = sharedPreferences.getBoolean("shuffle", false)
        val repeatName = sharedPreferences.getString("repeat", RepeatMode.OFF.name) ?: RepeatMode.OFF.name
        repeatMode = RepeatMode.valueOf(repeatName)
    }

    // ========== Cleanup ==========

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}