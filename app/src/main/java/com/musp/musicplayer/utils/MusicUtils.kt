package com.musp.musicplayer.utils

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.musp.musicplayer.model.Song
import java.util.concurrent.TimeUnit

// # Helper functions (fetch songs, formatting duration)
object MusicUtils {

    /**
     * Check if the app has permission to read audio files
     */
    fun hasAudioPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get the appropriate permission string based on Android version
     */
    fun getAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    /**
     * Scan and retrieve all audio files from device storage
     */
    fun getAllSongsFromDevice(context: Context): List<Song> {
        val songs = mutableListOf<Song>()

        if (!hasAudioPermission(context)) {
            return songs
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Title"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val data = cursor.getString(dataColumn)
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)

                val albumArtUri = getAlbumArtUri(albumId)

                songs.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        uri = data,
                        duration = duration,
                        albumId = albumId,
                        albumArt = albumArtUri
                    )
                )
            }
        }

        return songs
    }

    /**
     * Get album art URI for a given album ID
     */
    private fun getAlbumArtUri(albumId: Long): String {
        val artworkUri = Uri.parse("content://media/external/audio/albumart")
        return ContentUris.withAppendedId(artworkUri, albumId).toString()
    }

    /**
     * Format duration from milliseconds to MM:SS format
     */
    fun formatDuration(durationMillis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Sort songs by title (A-Z)
     */
    fun sortByTitle(songs: List<Song>): List<Song> {
        return songs.sortedBy { it.title.lowercase() }
    }

    /**
     * Sort songs by artist (A-Z)
     */
    fun sortByArtist(songs: List<Song>): List<Song> {
        return songs.sortedBy { it.artist.lowercase() }
    }

    /**
     * Sort songs by duration (shortest to longest)
     */
    fun sortByDuration(songs: List<Song>): List<Song> {
        return songs.sortedBy { it.duration }
    }

    /**
     * Search songs by title or artist
     */
    fun searchSongs(songs: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return songs
        
        val lowerQuery = query.lowercase()
        return songs.filter {
            it.title.lowercase().contains(lowerQuery) ||
            it.artist.lowercase().contains(lowerQuery)
        }
    }
}