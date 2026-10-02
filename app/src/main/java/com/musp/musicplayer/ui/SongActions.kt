package com.musp.musicplayer.ui

import android.app.Dialog
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.format.Formatter
import android.text.style.StyleSpan
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.musp.musicplayer.R
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.data.SleepTimer
import com.musp.musicplayer.databinding.DialogTextInputBinding
import com.musp.musicplayer.fragment.SongCollectionFragment
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.utils.MusicUtils
import com.musp.musicplayer.viewmodel.LibraryViewModel
import com.musp.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// Song menus and dialogs shared by every screen that lists songs.

val Fragment.playerViewModel: PlayerViewModel
    get() = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]

val Fragment.libraryViewModel: LibraryViewModel
    get() = ViewModelProvider(requireActivity())[LibraryViewModel::class.java]

fun Fragment.toast(@StringRes message: Int, vararg args: Any) {
    val context = context ?: return
    Toast.makeText(context, context.getString(message, *args), Toast.LENGTH_SHORT).show()
}

/** Dismiss dialogs together with the fragment's view (e.g. on rotation) to avoid window leaks. */
private fun Fragment.dismissWithView(dialog: Dialog) {
    val owner = viewLifecycleOwnerLiveData.value ?: this
    owner.lifecycle.addObserver(object : LifecycleEventObserver {
        override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_DESTROY) {
                source.lifecycle.removeObserver(this)
                dialog.dismiss()
            }
        }
    })
}

/**
 * Options menu for a song: queue, playlists, favourites, navigation and details.
 * @param playlistId set when the song is shown inside a playlist (enables "Remove from playlist")
 */
fun Fragment.showSongMenu(song: Song, anchor: View, playlistId: Long? = null) {
    val activity = activity as? MainActivity ?: return
    val player = playerViewModel
    val library = libraryViewModel
    val appContext = requireContext().applicationContext

    val popup = PopupMenu(requireContext(), anchor)
    popup.inflate(R.menu.menu_song)
    popup.menu.findItem(R.id.action_toggle_favorite).setTitle(
        if (library.isFavorite(song.id)) R.string.remove_from_favorites else R.string.add_to_favorites
    )
    popup.menu.findItem(R.id.action_remove_from_playlist).isVisible = playlistId != null
    popup.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            R.id.action_play_next -> {
                player.playNext(listOf(song))
                toast(R.string.will_play_next)
            }
            R.id.action_add_to_queue -> {
                player.addToQueue(listOf(song))
                toast(R.string.added_to_queue)
            }
            R.id.action_add_to_playlist -> showAddToPlaylistDialog(listOf(song))
            R.id.action_toggle_favorite -> library.toggleFavorite(song) { isFavorite ->
                Toast.makeText(
                    appContext,
                    if (isFavorite) R.string.added_to_favorites else R.string.removed_from_favorites,
                    Toast.LENGTH_SHORT
                ).show()
            }
            R.id.action_remove_from_playlist -> playlistId?.let { library.removeFromPlaylist(it, song) }
            R.id.action_go_to_album ->
                activity.navigateTo(SongCollectionFragment.forAlbum(song.albumId, song.album))
            R.id.action_go_to_artist ->
                activity.navigateTo(SongCollectionFragment.forArtist(song.artist))
            R.id.action_details -> showSongDetails(song)
        }
        true
    }
    popup.show()
}

fun Fragment.showAddToPlaylistDialog(songs: List<Song>) {
    if (songs.isEmpty()) return
    val library = libraryViewModel
    val appContext = requireContext().applicationContext
    viewLifecycleOwner.lifecycleScope.launch {
        val playlists = library.getPlaylists()
        val names = arrayOf(getString(R.string.new_playlist)) + playlists.map { it.name }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_to_playlist)
            .setItems(names) { _, which ->
                if (which == 0) {
                    showTextInputDialog(R.string.create_playlist, positive = R.string.create) { name ->
                        library.createPlaylist(name, songs) {
                            Toast.makeText(
                                appContext, appContext.getString(R.string.added_to_playlist, name), Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    val playlist = playlists[which - 1]
                    library.addToPlaylist(playlist.id, songs) { added ->
                        val message = if (added == 0) R.string.already_in_playlist else R.string.added_to_playlist
                        Toast.makeText(
                            appContext, appContext.getString(message, playlist.name), Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dismissWithView(dialog)
        dialog.show()
    }
}

fun Fragment.showTextInputDialog(
    @StringRes title: Int,
    initial: String = "",
    @StringRes positive: Int,
    onConfirm: (String) -> Unit
) {
    val binding = DialogTextInputBinding.inflate(layoutInflater)
    binding.etInput.setText(initial)
    binding.etInput.setSelection(initial.length)
    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setView(binding.root)
        .setPositiveButton(positive, null)
        .setNegativeButton(R.string.cancel, null)
        .create()
    dialog.setOnShowListener {
        binding.etInput.requestFocus()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = binding.etInput.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                binding.inputLayout.error = getString(R.string.playlist_name_required)
            } else {
                onConfirm(text)
                dialog.dismiss()
            }
        }
    }
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    dismissWithView(dialog)
    dialog.show()
}

fun Fragment.showConfirmDialog(title: String, message: String, @StringRes positive: Int, onConfirm: () -> Unit) {
    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positive) { _, _ -> onConfirm() }
        .setNegativeButton(R.string.cancel, null)
        .create()
    dismissWithView(dialog)
    dialog.show()
}

fun Fragment.showSongDetails(song: Song) {
    val context = requireContext()
    val details = SpannableStringBuilder()
    fun line(@StringRes label: Int, value: String?) {
        if (value.isNullOrBlank()) return
        if (details.isNotEmpty()) details.append("\n\n")
        val start = details.length
        details.append(getString(label))
        details.setSpan(StyleSpan(Typeface.BOLD), start, details.length, 0)
        details.append("\n").append(value)
    }
    line(R.string.detail_title, song.title)
    line(R.string.detail_artist, song.artist)
    line(R.string.detail_album, song.album)
    line(R.string.detail_duration, MusicUtils.formatDuration(song.duration))
    if (song.trackNumber > 0) line(R.string.detail_track, song.trackNumber.toString())
    if (song.year > 0) line(R.string.detail_year, song.year.toString())
    line(R.string.detail_format, song.mimeType)
    if (song.size > 0) line(R.string.detail_size, Formatter.formatShortFileSize(context, song.size))
    if (song.dateAdded > 0) {
        line(R.string.detail_date_added, DateFormat.getDateInstance().format(Date(song.dateAdded * 1000)))
    }
    line(R.string.detail_path, song.path)

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.song_details)
        .setMessage(details)
        .setPositiveButton(R.string.ok, null)
        .create()
    dismissWithView(dialog)
    dialog.show()
}

fun Fragment.showSleepTimerDialog() {
    val player = playerViewModel
    val minutes = intArrayOf(5, 10, 15, 30, 45, 60, 90)
    val labels = buildList {
        add(getString(R.string.sleep_timer_off))
        minutes.forEach { add(getString(R.string.sleep_timer_minutes, it)) }
        add(getString(R.string.sleep_timer_end_of_track))
    }.toTypedArray()

    val title = when (val state = player.sleepTimer.value) {
        is SleepTimer.State.Countdown -> getString(
            R.string.dot_separated,
            getString(R.string.sleep_timer),
            getString(R.string.sleep_timer_remaining, MusicUtils.formatDuration(state.remainingMs))
        )
        SleepTimer.State.EndOfTrack -> getString(
            R.string.dot_separated, getString(R.string.sleep_timer), getString(R.string.sleep_timer_after_song)
        )
        SleepTimer.State.Off -> getString(R.string.sleep_timer)
    }

    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setItems(labels) { _, which ->
            when (which) {
                0 -> {
                    player.cancelSleepTimer()
                    toast(R.string.sleep_timer_cancelled)
                }
                labels.lastIndex -> {
                    player.setSleepTimerEndOfTrack()
                    toast(R.string.sleep_timer_set_end)
                }
                else -> {
                    val value = minutes[which - 1]
                    player.setSleepTimer(value)
                    toast(R.string.sleep_timer_set, value)
                }
            }
        }
        .setNegativeButton(R.string.cancel, null)
        .create()
    dismissWithView(dialog)
    dialog.show()
}
