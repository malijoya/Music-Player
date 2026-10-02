package com.musp.musicplayer.utils

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.musp.musicplayer.model.Song

@OptIn(UnstableApi::class)
fun Song.toMediaItem(): MediaItem {
    val mediaUri = Uri.parse(uri)
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(mediaUri)
        // The URI is also kept in request metadata because local configuration is not
        // always forwarded from a MediaController to the session
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder().setMediaUri(mediaUri).build()
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArt?.let(Uri::parse))
                .setDurationMs(duration.takeIf { it > 0 })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()
}

/** Fallback used when a queued item is no longer in the scanned library. */
@OptIn(UnstableApi::class)
fun MediaItem.toFallbackSong(): Song {
    val metadata = mediaMetadata
    return Song(
        id = mediaId.toLongOrNull() ?: -1L,
        title = metadata.title?.toString() ?: "Unknown Title",
        artist = metadata.artist?.toString() ?: "Unknown Artist",
        uri = (localConfiguration?.uri ?: requestMetadata.mediaUri)?.toString() ?: "",
        duration = metadata.durationMs ?: 0L,
        albumArt = metadata.artworkUri?.toString(),
        album = metadata.albumTitle?.toString() ?: ""
    )
}
