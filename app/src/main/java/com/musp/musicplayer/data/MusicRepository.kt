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
 * Albums and artists are derived from the scanned songs.
 */
class MusicRepository(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

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

    fun getSong(id: Long): Song? = songMap[id]

    fun getSongs(ids: List<Long>): List<Song> = ids.mapNotNull { songMap[it] }

    /** Re-scan the device. Safe to call often; concurrent scans are serialized. */
    suspend fun refresh(): LibraryState = scanMutex.withLock {
        val newState = withContext(Dispatchers.IO) {
            if (!MusicUtils.hasAudioPermission(context)) {
                LibraryState.PermissionRequired
            } else {
                LibraryState.Ready(MusicUtils.getAllSongsFromDevice(context))
            }
        }
        songMap = (newState as? LibraryState.Ready)?.songs?.associateBy { it.id } ?: emptyMap()
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
