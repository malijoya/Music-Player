package com.musp.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FavoriteDao {
    @Query("SELECT songId FROM favorites ORDER BY addedAt DESC")
    abstract fun observeFavoriteIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    abstract suspend fun isFavorite(songId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    abstract suspend fun delete(songId: Long)

    @Transaction
    open suspend fun toggle(songId: Long): Boolean {
        return if (isFavorite(songId)) {
            delete(songId)
            false
        } else {
            insert(FavoriteEntity(songId, System.currentTimeMillis()))
            true
        }
    }
}

@Dao
abstract class PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    abstract fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position ASC")
    abstract fun observeAllPlaylistSongs(): Flow<List<PlaylistSongEntity>>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    abstract fun observePlaylistSongIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    abstract fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Insert
    abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    abstract suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract suspend fun deletePlaylistRow(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    abstract suspend fun clearPlaylist(playlistId: Long)

    @Transaction
    open suspend fun deletePlaylist(playlistId: Long) {
        clearPlaylist(playlistId)
        deletePlaylistRow(playlistId)
    }

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    abstract suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSongs(songs: List<PlaylistSongEntity>): List<Long>

    @Transaction
    open suspend fun addSongs(playlistId: Long, songIds: List<Long>): Int {
        var position = maxPosition(playlistId)
        val now = System.currentTimeMillis()
        val rows = songIds.distinct().map { PlaylistSongEntity(playlistId, it, ++position, now) }
        // IGNORE skips songs already in the playlist; count the rows actually inserted
        return insertSongs(rows).count { it != -1L }
    }

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    abstract suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId IN (:songIds)")
    abstract suspend fun removeSongs(playlistId: Long, songIds: List<Long>)
}

@Dao
abstract class RecentlyPlayedDao {
    @Query("SELECT songId FROM recently_played ORDER BY playedAt DESC LIMIT :limit")
    abstract fun observeRecentIds(limit: Int): Flow<List<Long>>

    @Upsert
    abstract suspend fun upsert(entry: RecentlyPlayedEntity)

    @Query("DELETE FROM recently_played WHERE songId NOT IN (SELECT songId FROM recently_played ORDER BY playedAt DESC LIMIT :keep)")
    abstract suspend fun trim(keep: Int)

    @Query("DELETE FROM recently_played")
    abstract suspend fun clear()

    @Transaction
    open suspend fun record(songId: Long, keep: Int) {
        upsert(RecentlyPlayedEntity(songId, System.currentTimeMillis()))
        trim(keep)
    }
}
