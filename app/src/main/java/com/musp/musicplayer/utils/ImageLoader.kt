package com.musp.musicplayer.utils

import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
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
        .transition(DrawableTransitionOptions.withCrossFade(ARTWORK_FADE_MS))
        .into(this)
}

/**
 * Loads artwork as a soft, heavily blurred ambient backdrop. Shows nothing when there is no art.
 * Android 12+ blurs on the GPU; older versions get the same look from a tiny upscaled bitmap.
 */
fun ImageView.loadBlurredArtwork(uri: String?) {
    val blurOnGpu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if (blurOnGpu && getTag(R.id.tag_blur_applied) == null) {
        setRenderEffect(RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP))
        setTag(R.id.tag_blur_applied, true)
    }
    Glide.with(this)
        .load(uri?.let(Uri::parse))
        .override(if (blurOnGpu) BACKDROP_SIZE_GPU else BACKDROP_SIZE_SOFT)
        .centerCrop()
        .transition(DrawableTransitionOptions.withCrossFade(BACKDROP_FADE_MS))
        .into(this)
}

private const val ARTWORK_FADE_MS = 180
private const val BACKDROP_FADE_MS = 450
private const val BLUR_RADIUS = 80f
private const val BACKDROP_SIZE_GPU = 160
private const val BACKDROP_SIZE_SOFT = 24
