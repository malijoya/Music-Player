package com.musp.musicplayer.data

import com.musp.musicplayer.data.db.AppDatabase
import com.musp.musicplayer.data.db.PlaylistEntity
import com.musp.musicplayer.model.Playlist
import com.musp.musicplayer.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * User data stored in Room: favorites, playlists and play history.
 * Everything is joined with the scanned library so deleted files never show up.
 */
class UserLibraryRepository(
    private val database: AppDatabase,
    private val musicRepository: MusicRepository
) {
    private val favoriteDao = database.favoriteDao()
    private val playlistDao = database.playlistDao()
    private val recentDao = database.recentlyPlayedDao()

    val favoriteIds: Flow<Set<Long>> = favoriteDao.observeFavoriteIds().map { it.toSet() }

    val favoriteSongs: Flow<List<Song>> =
        combine(favoriteDao.observeFavoriteIds(), musicRepository.songs) { ids, songs ->
            resolve(ids, songs)
        }

    val recentSongs: Flow<List<Song>> =
        combine(recentDao.observeRecentIds(RECENT_DISPLAY_LIMIT), musicRepository.songs) { ids, songs ->
            resolve(ids, songs)
        }

    val playlists: Flow<List<Playlist>> = combine(
        playlistDao.observePlaylists(),
        playlistDao.observeAllPlaylistSongs(),
        musicRepository.songs
    ) { playlists, entries, songs ->
        val available = songs.mapTo(HashSet()) { it.id }
        val counts = entries.filter { it.songId in available }.groupingBy { it.playlistId }.eachCount()
        playlists.map { Playlist(it.id, it.name, counts[it.id] ?: 0) }
    }

    fun playlistName(playlistId: Long): Flow<String?> =
        playlistDao.observePlaylist(playlistId).map { it?.name }

    fun playlistSongs(playlistId: Long): Flow<List<Song>> =
        combine(playlistDao.observePlaylistSongIds(playlistId), musicRepository.songs) { ids, songs ->
            resolve(ids, songs)
        }

    suspend fun toggleFavorite(songId: Long): Boolean = favoriteDao.toggle(songId)

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun renamePlaylist(playlistId: Long, name: String) =
        playlistDao.renamePlaylist(playlistId, name.trim())

    suspend fun deletePlaylist(playlistId: Long) = playlistDao.deletePlaylist(playlistId)

    /** @return number of songs that were not already in the playlist */
    suspend fun addToPlaylist(playlistId: Long, songIds: List<Long>): Int =
        playlistDao.addSongs(playlistId, songIds)

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) =
        playlistDao.removeSong(playlistId, songId)

    suspend fun removeFromPlaylist(playlistId: Long, songIds: List<Long>) =
        playlistDao.removeSongs(playlistId, songIds)

    suspend fun recordPlayed(songId: Long) = recentDao.record(songId, RECENT_KEEP)

    suspend fun clearRecent() = recentDao.clear()

    private fun resolve(ids: List<Long>, songs: List<Song>): List<Song> {
        if (ids.isEmpty() || songs.isEmpty()) return emptyList()
        val byId = songs.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    companion object {
        private const val RECENT_DISPLAY_LIMIT = 50
        private const val RECENT_KEEP = 100
    }
}
