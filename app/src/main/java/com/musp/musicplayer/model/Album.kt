package com.musp.musicplayer.model

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artUri: String?,
    val songCount: Int,
    val year: Int
)

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val artUri: String?
)

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int
)
