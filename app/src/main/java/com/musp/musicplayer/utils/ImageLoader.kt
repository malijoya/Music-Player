package com.musp.musicplayer.utils

import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.musp.musicplayer.R

/**
 * Loads album artwork, falling back to a placeholder when the song has no embedded art
 * or the file no longer exists.
 */
fun ImageView.loadArtwork(uri: String?) {
    Glide.with(this)
        .load(uri?.let(Uri::parse))
        .placeholder(R.drawable.art_placeholder)
        .error(R.drawable.art_placeholder)
        .fallback(R.drawable.art_placeholder)
        .into(this)
}
