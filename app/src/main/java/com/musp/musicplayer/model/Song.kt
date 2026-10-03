package com.musp.musicplayer.model


// # Data class representing a song (title, artist, URI, duration)
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: String, // content:// URI of the audio file
    val duration: Long,
    val albumId: Long = 0,
    val albumArt: String? = null,
    val album: String = "",
    val path: String? = null,
    val size: Long = 0,
    val mimeType: String? = null,
    val year: Int = 0,
    val trackNumber: Int = 0,
    val dateAdded: Long = 0, // seconds since epoch
    val isMusic: Boolean = true // false for voice notes, recordings, ringtones... (see MusicClassifier)
)
