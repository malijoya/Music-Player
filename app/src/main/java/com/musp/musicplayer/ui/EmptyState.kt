package com.musp.musicplayer.ui

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.musp.musicplayer.R
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.data.LibraryState
import com.musp.musicplayer.databinding.ViewEmptyStateBinding

fun ViewEmptyStateBinding.show(
    @DrawableRes icon: Int,
    title: CharSequence,
    message: CharSequence? = null,
    actionText: CharSequence? = null,
    onAction: (() -> Unit)? = null
) {
    root.isVisible = true
    emptyIcon.setImageResource(icon)
    emptyTitle.text = title
    emptyMessage.text = message
    emptyMessage.isVisible = !message.isNullOrEmpty()
    emptyAction.isVisible = actionText != null && onAction != null
    emptyAction.text = actionText
    emptyAction.setOnClickListener { onAction?.invoke() }
}

fun ViewEmptyStateBinding.hide() {
    root.isVisible = false
}

fun Fragment.showEmpty(
    binding: ViewEmptyStateBinding,
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes message: Int? = null,
    @StringRes action: Int? = null,
    onAction: (() -> Unit)? = null
) {
    binding.show(
        icon,
        getString(title),
        message?.let { getString(it) },
        action?.let { getString(it) },
        onAction
    )
}

/**
 * Handles the loading and permission states shared by every library screen.
 * @return true when the library is ready and the caller should render its content
 */
fun Fragment.renderLibraryGate(
    state: LibraryState,
    empty: ViewEmptyStateBinding,
    progress: View? = null
): Boolean {
    progress?.isVisible = state is LibraryState.Loading
    return when (state) {
        LibraryState.Loading -> {
            empty.hide()
            false
        }
        LibraryState.PermissionRequired -> {
            showEmpty(
                empty,
                R.drawable.ic_lock,
                R.string.permission_title,
                R.string.permission_message,
                R.string.grant_permission
            ) { (activity as? MainActivity)?.requestAudioPermission() }
            false
        }
        is LibraryState.Ready -> true
    }
}
