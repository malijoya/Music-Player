package com.musp.musicplayer.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import com.musp.musicplayer.model.Album
import com.musp.musicplayer.model.Artist
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.utils.MusicUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface LibraryState {
    /** Nothing scanned yet */
    data object Loading : LibraryState

    /** Storage/media permission is missing */
    data object PermissionRequired : LibraryState

    data class Ready(val songs: List<Song>) : LibraryState
}

/**
 * Single source of truth for songs on the device (MediaStore).
 * Every audio file is scanned; when "music only" is on, [state] leaves out files that
 * [com.musp.musicplayer.utils.MusicClassifier] judged not to be music.
 * Albums and artists are derived from the visible songs.
 */
class MusicRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: PlaybackStateStore
) {
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _musicOnly = MutableStateFlow(prefs.musicOnly)
    val musicOnly: StateFlow<Boolean> = _musicOnly.asStateFlow()

    private val _allScannedSongs = MutableStateFlow<List<Song>>(emptyList())
    /** Everything found by the last scan, including files hidden by the music-only filter. */
    val allScannedSongs: StateFlow<List<Song>> = _allScannedSongs.asStateFlow()

    private var scannedSongs: List<Song>
        get() = _allScannedSongs.value
        set(value) { _allScannedSongs.value = value }

    val songs: Flow<List<Song>> = state.map { (it as? LibraryState.Ready)?.songs ?: emptyList() }
        .distinctUntilChanged()

    val albums: Flow<List<Album>> = songs.map { MusicUtils.groupAlbums(it) }
    val artists: Flow<List<Artist>> = songs.map { MusicUtils.groupArtists(it) }

    @Volatile
    private var songMap: Map<Long, Song> = emptyMap()
    private val scanMutex = Mutex()
    private var pendingRefresh: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // MediaStore fires many change events while scanning; debounce them
            requestRefresh(debounceMs = 1500)
        }
    }
    private var observerRegistered = false

    // Lookups by id include hidden files so a restored queue can still play them
    fun getSong(id: Long): Song? = songMap[id]

    fun getSongs(ids: List<Long>): List<Song> = ids.mapNotNull { songMap[it] }

    /** Total number of non-music files found by the last scan. */
    fun nonMusicCount(): Int = scannedSongs.count { !it.isMusic }

    fun setMusicOnly(musicOnly: Boolean) {
        if (_musicOnly.value == musicOnly) return
        prefs.musicOnly = musicOnly
        _musicOnly.value = musicOnly
        if (_state.value is LibraryState.Ready) {
            _state.value = LibraryState.Ready(visible(scannedSongs))
        }
    }

    private fun visible(songs: List<Song>): List<Song> =
        if (_musicOnly.value) songs.filter { it.isMusic } else songs

    /** Re-scan the device. Safe to call often; concurrent scans are serialized. */
    suspend fun refresh(): LibraryState = scanMutex.withLock {
        val scanned = withContext(Dispatchers.IO) {
            if (MusicUtils.hasAudioPermission(context)) MusicUtils.getAllSongsFromDevice(context) else null
        }
        scannedSongs = scanned.orEmpty()
        songMap = scannedSongs.associateBy { it.id }
        val newState = if (scanned == null) LibraryState.PermissionRequired else LibraryState.Ready(visible(scanned))
        _state.value = newState
        newState
    }

    /** Returns the scanned songs, scanning first if nothing has been loaded yet. */
    suspend fun ensureLoaded(): List<Song> {
        val current = _state.value
        val result = if (current is LibraryState.Ready) current else refresh()
        return (result as? LibraryState.Ready)?.songs ?: emptyList()
    }

    fun requestRefresh(debounceMs: Long = 0) {
        pendingRefresh?.cancel()
        pendingRefresh = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            refresh()
        }
    }

    /** Called when the UI becomes visible: picks up permission changes and new files. */
    fun onAppForeground() {
        val hasPermission = MusicUtils.hasAudioPermission(context)
        val current = _state.value
        if (!hasPermission && current !is LibraryState.PermissionRequired) {
            songMap = emptyMap()
            scannedSongs = emptyList()
            _state.value = LibraryState.PermissionRequired
        } else if (hasPermission) {
            requestRefresh()
        }
        startObserving()
    }

    /** Stop listening for MediaStore changes while the UI is hidden to save battery. */
    fun onAppBackground() {
        if (observerRegistered) {
            context.contentResolver.unregisterContentObserver(observer)
            observerRegistered = false
        }
    }

    private fun startObserving() {
        if (observerRegistered || !MusicUtils.hasAudioPermission(context)) return
        try {
            context.contentResolver.registerContentObserver(
                MusicUtils.audioCollectionUri(), true, observer
            )
            observerRegistered = true
        } catch (e: SecurityException) {
            observerRegistered = false
        }
    }
}
