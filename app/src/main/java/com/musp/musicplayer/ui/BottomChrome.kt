package com.musp.musicplayer.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.musp.musicplayer.activity.MainActivity
import kotlinx.coroutines.launch

// Screens draw edge to edge under the floating mini player + navigation bar.
// These keep their content reachable as that chrome grows, shrinks or hides.

/** Adds the floating chrome's height to the bottom padding of scrolling lists and empty states. */
fun Fragment.padForBottomChrome(vararg views: View) {
    val chromeHeight = (activity as? MainActivity)?.bottomChromeHeight ?: return
    val basePadding = views.map { it.paddingBottom }
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            chromeHeight.collect { height ->
                views.forEachIndexed { index, view -> view.updatePadding(bottom = basePadding[index] + height) }
            }
        }
    }
}

/** Keeps a floating action button above the chrome. */
fun Fragment.liftAboveBottomChrome(view: View) {
    val chromeHeight = (activity as? MainActivity)?.bottomChromeHeight ?: return
    val baseMargin = (view.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            chromeHeight.collect { height ->
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = baseMargin + height }
            }
        }
    }
}
