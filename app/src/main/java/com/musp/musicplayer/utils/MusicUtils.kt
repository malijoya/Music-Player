package com.musp.musicplayer.utils

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.musp.musicplayer.model.Album
import com.musp.musicplayer.model.Artist
import com.musp.musicplayer.model.Song
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

// # Helper functions (fetch songs, formatting duration)
object MusicUtils {

    private const val UNKNOWN_ARTIST = "Unknown Artist"
    private const val UNKNOWN_ALBUM = "Unknown Album"

    /**
     * Check if the app has permission to read audio files
     */
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            getAudioPermission()
        ) == PackageManager.PERMISSION_GRANTED
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
     * The MediaStore collection holding audio files on all external volumes (including SD cards).
     */
    fun audioCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    /**
     * Scan and retrieve all audio files from device storage, each marked with whether it is music.
     * Must be called off the main thread.
     */
    @Suppress("DEPRECATION") // DATA is only used for display and the stale-entry check
    fun getAllSongsFromDevice(context: Context): List<Song> {
        val songs = mutableListOf<Song>()

        if (!hasAudioPermission(context)) {
            return songs
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_ALARM,
            MediaStore.Audio.Media.IS_PODCAST
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.Audio.Media.IS_AUDIOBOOK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            projection += MediaStore.Audio.Media.IS_RECORDING
        }

        // No selection: every audio file is scanned and classified, the library filters later
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        // Checking file existence is only reliable where direct path access works for media files
        val canCheckFiles = Build.VERSION.SDK_INT != Build.VERSION_CODES.Q

        try {
            context.contentResolver.query(
                audioCollectionUri(),
                projection.toTypedArray(),
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val isMusicColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)
                val isRingtoneColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE)
                val isNotificationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION)
                val isAlarmColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_ALARM)
                val isPodcastColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_PODCAST)
                // -1 on Android versions that don't have these columns
                val isAudiobookColumn = cursor.getColumnIndex(MediaStore.Audio.Media.IS_AUDIOBOOK)
                val isRecordingColumn = cursor.getColumnIndex(MediaStore.Audio.Media.IS_RECORDING)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val path = cursor.getString(dataColumn)

                    // Skip stale MediaStore rows whose file was deleted
                    if (canCheckFiles && path != null && !File(path).exists()) continue

                    val displayName = cursor.getString(nameColumn)
                    val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() }
                        ?: displayName?.substringBeforeLast('.')
                        ?: "Unknown Title"
                    val rawArtist = cursor.getString(artistColumn)
                    val rawAlbum = cursor.getString(albumColumn)
                    val duration = cursor.getLong(durationColumn).coerceAtLeast(0)
                    val mimeType = cursor.getString(mimeColumn)
                    val year = cursor.getInt(yearColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    // MediaStore encodes track as disc * 1000 + track
                    val track = cursor.getInt(trackColumn) % 1000

                    val isMusic = MusicClassifier.isMusic(
                        MusicClassifier.AudioInfo(
                            path = path,
                            displayName = displayName,
                            title = title,
                            artist = tagOrNull(rawArtist),
                            album = tagOrNull(rawAlbum),
                            durationMs = duration,
                            mimeType = mimeType,
                            year = year,
                            trackNumber = track,
                            isMusicFlag = cursor.getInt(isMusicColumn) != 0,
                            isRingtone = cursor.getInt(isRingtoneColumn) != 0,
                            isNotification = cursor.getInt(isNotificationColumn) != 0,
                            isAlarm = cursor.getInt(isAlarmColumn) != 0,
                            isPodcast = cursor.getInt(isPodcastColumn) != 0,
                            isAudiobook = isAudiobookColumn >= 0 && cursor.getInt(isAudiobookColumn) != 0,
                            isRecording = isRecordingColumn >= 0 && cursor.getInt(isRecordingColumn) != 0
                        )
                    )

                    songs.add(
                        Song(
                            id = id,
                            title = title,
                            artist = cleanTag(rawArtist, UNKNOWN_ARTIST),
                            uri = ContentUris.withAppendedId(audioCollectionUri(), id).toString(),
                            duration = duration,
                            albumId = albumId,
                            albumArt = getAlbumArtUri(albumId),
                            album = cleanTag(rawAlbum, UNKNOWN_ALBUM),
                            path = path,
                            size = cursor.getLong(sizeColumn),
                            mimeType = mimeType,
                            year = year,
                            trackNumber = track,
                            dateAdded = cursor.getLong(dateAddedColumn),
                            isMusic = isMusic
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission revoked while scanning
            return emptyList()
        }

        return songs
    }

    private fun cleanTag(value: String?, fallback: String): String = tagOrNull(value) ?: fallback

    private fun tagOrNull(value: String?): String? {
        return if (value.isNullOrBlank() || value == MediaStore.UNKNOWN_STRING) null else value
    }

    /**
     * Get album art URI for a given album ID
     */
    private fun getAlbumArtUri(albumId: Long): String {
        val artworkUri = Uri.parse("content://media/external/audio/albumart")
        return ContentUris.withAppendedId(artworkUri, albumId).toString()
    }

    /**
     * Group songs into albums
     */
    fun groupAlbums(songs: List<Song>): List<Album> {
        return songs.groupBy { it.albumId }.map { (albumId, albumSongs) ->
            val first = albumSongs.first()
            val artists = albumSongs.map { it.artist }.distinct()
            Album(
                id = albumId,
                title = first.album,
                artist = if (artists.size == 1) artists.first() else "Various Artists",
                artUri = first.albumArt,
                songCount = albumSongs.size,
                year = albumSongs.maxOf { it.year }
            )
        }.sortedBy { it.title.lowercase(Locale.getDefault()) }
    }

    /**
     * Group songs into artists
     */
    fun groupArtists(songs: List<Song>): List<Artist> {
        return songs.groupBy { it.artist }.map { (name, artistSongs) ->
            Artist(
                name = name,
                songCount = artistSongs.size,
                albumCount = artistSongs.map { it.albumId }.distinct().size,
                artUri = artistSongs.first().albumArt
            )
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    /**
     * Sort tracks of an album by track number, then title
     */
    fun sortAlbumTracks(songs: List<Song>): List<Song> {
        return songs.sortedWith(compareBy<Song> { it.trackNumber }.thenBy { it.title.lowercase() })
    }

    /**
     * Format duration from milliseconds to MM:SS format (H:MM:SS for long tracks)
     */
    fun formatDuration(durationMillis: Long): String {
        val safe = durationMillis.coerceAtLeast(0)
        val hours = TimeUnit.MILLISECONDS.toHours(safe)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
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
     * Sort songs by date added (newest first)
     */
    fun sortByDateAdded(songs: List<Song>): List<Song> {
        return songs.sortedByDescending { it.dateAdded }
    }

    /**
     * Search songs by title, artist or album
     */
    fun searchSongs(songs: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return songs

        val lowerQuery = query.trim().lowercase()
        return songs.filter {
            it.title.lowercase().contains(lowerQuery) ||
            it.artist.lowercase().contains(lowerQuery) ||
            it.album.lowercase().contains(lowerQuery)
        }
    }
}
