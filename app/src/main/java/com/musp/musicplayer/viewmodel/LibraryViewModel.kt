package com.musp.musicplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musp.musicplayer.appContainer
import com.musp.musicplayer.data.LibraryState
import com.musp.musicplayer.model.Album
import com.musp.musicplayer.model.Artist
import com.musp.musicplayer.model.Playlist
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.utils.MusicUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SongSort { TITLE, ARTIST, DURATION, DATE_ADDED }

/**
 * Activity-scoped access to the music library and user data (favorites, playlists, history).
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val musicRepository = container.musicRepository
    private val userRepository = container.userLibraryRepository
    private val prefs = container.playbackStateStore

    val libraryState: StateFlow<LibraryState> = musicRepository.state

    private val _sortOrder = MutableStateFlow(
        SongSort.entries.getOrElse(prefs.songSortOrder) { SongSort.TITLE }
    )
    val sortOrder: StateFlow<SongSort> = _sortOrder.asStateFlow()

    // List flows are cold: each screen collects them while visible, so the first value
    // it receives is real data rather than a placeholder empty list.
    val sortedSongs: Flow<List<Song>> =
        combine(musicRepository.songs, _sortOrder) { songs, sort ->
            when (sort) {
                SongSort.TITLE -> MusicUtils.sortByTitle(songs)
                SongSort.ARTIST -> MusicUtils.sortByArtist(songs)
                SongSort.DURATION -> MusicUtils.sortByDuration(songs)
                SongSort.DATE_ADDED -> MusicUtils.sortByDateAdded(songs)
            }
        }

    val albums: Flow<List<Album>> = musicRepository.albums

    val artists: Flow<List<Artist>> = musicRepository.artists

    /** Hot so the song menu can synchronously show "Add to"/"Remove from" favourites. */
    val favoriteIds: StateFlow<Set<Long>> =
        userRepository.favoriteIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val favoriteSongs: Flow<List<Song>> = userRepository.favoriteSongs

    val recentSongs: Flow<List<Song>> = userRepository.recentSongs

    val playlists: Flow<List<Playlist>> = userRepository.playlists

    suspend fun getPlaylists(): List<Playlist> = userRepository.playlists.first()

    /** All scanned songs (title order), or empty if the library isn't loaded. */
    fun allSongs(): List<Song> = (libraryState.value as? LibraryState.Ready)?.songs.orEmpty()

    fun setSortOrder(sort: SongSort) {
        _sortOrder.value = sort
        prefs.songSortOrder = sort.ordinal
    }

    fun refreshLibrary() = musicRepository.requestRefresh()

    /** true when voice notes, recordings and other non-music audio are hidden from the library. */
    val musicOnly: StateFlow<Boolean> = musicRepository.musicOnly

    fun setMusicOnly(musicOnly: Boolean) = musicRepository.setMusicOnly(musicOnly)

    /** Number of scanned audio files that aren't music (shown or not). */
    fun nonMusicCount(): Int = musicRepository.nonMusicCount()

    fun albumSongs(albumId: Long): Flow<List<Song>> =
        musicRepository.songs.map { songs -> MusicUtils.sortAlbumTracks(songs.filter { it.albumId == albumId }) }

    fun artistSongs(artistName: String): Flow<List<Song>> =
        musicRepository.songs.map { songs ->
            songs.filter { it.artist == artistName }
                .sortedWith(compareBy<Song> { it.album.lowercase() }.thenBy { it.trackNumber })
        }

    fun playlistSongs(playlistId: Long): Flow<List<Song>> = userRepository.playlistSongs(playlistId)

    fun playlistName(playlistId: Long): Flow<String?> = userRepository.playlistName(playlistId)

    data class SearchResults(
        val songs: List<Song> = emptyList(),
        val albums: List<Album> = emptyList(),
        val artists: List<Artist> = emptyList()
    ) {
        val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
    }

    fun search(query: Flow<String>): Flow<SearchResults> =
        combine(query, musicRepository.songs) { q, songs ->
            if (q.isBlank()) {
                SearchResults()
            } else {
                val needle = q.trim().lowercase()
                SearchResults(
                    songs = MusicUtils.searchSongs(songs, q),
                    albums = MusicUtils.groupAlbums(songs)
                        .filter { it.title.lowercase().contains(needle) },
                    artists = MusicUtils.groupArtists(songs)
                        .filter { it.name.lowercase().contains(needle) }
                )
            }
        }

    // ========== Favorites ==========

    fun isFavorite(songId: Long): Boolean = songId in favoriteIds.value

    fun toggleFavorite(song: Song, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val nowFavorite = userRepository.toggleFavorite(song.id)
            onResult?.invoke(nowFavorite)
        }
    }

    // ========== Playlists ==========

    fun createPlaylist(name: String, songsToAdd: List<Song> = emptyList(), onCreated: ((Long) -> Unit)? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = userRepository.createPlaylist(name)
            if (songsToAdd.isNotEmpty()) userRepository.addToPlaylist(id, songsToAdd.map { it.id })
            onCreated?.invoke(id)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { userRepository.renamePlaylist(playlistId, name) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { userRepository.deletePlaylist(playlistId) }
    }

    fun addToPlaylist(playlistId: Long, songs: List<Song>, onResult: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val added = userRepository.addToPlaylist(playlistId, songs.map { it.id })
            onResult?.invoke(added)
        }
    }

    fun removeFromPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch { userRepository.removeFromPlaylist(playlistId, song.id) }
    }

    fun clearRecentlyPlayed() {
        viewModelScope.launch { userRepository.clearRecent() }
    }
}
