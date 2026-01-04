package com.musp.musicplayer.model


// # Data class representing a song (title, artist, URI, duration)
data class Song(
    val id: Long,
    val title: String, 
    val artist: String, 
    val uri: String, 
    val duration: Long,
    val albumId: Long = 0,
    val albumArt: String? = null
)
