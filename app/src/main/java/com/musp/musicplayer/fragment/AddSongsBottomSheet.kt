package com.musp.musicplayer.fragment

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.musp.musicplayer.R
import com.musp.musicplayer.adapter.PickSongAdapter
import com.musp.musicplayer.data.LibraryState
import com.musp.musicplayer.databinding.SheetAddSongsBinding
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.ui.SongPreviewPlayer
import com.musp.musicplayer.viewmodel.LibraryViewModel
import com.musp.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Full-height picker for adding library songs to a playlist: search, select (or select all)
 * and preview songs before adding them. Previews pause the main player and resume it when
 * the picker closes.
 */
class AddSongsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetAddSongsBinding? = null
    private val binding get() = _binding!!

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val libraryViewModel: LibraryViewModel by activityViewModels()

    private val playlistId: Long by lazy { requireArguments().getLong(ARG_PLAYLIST_ID) }
    private val playlistName: String by lazy { requireArguments().getString(ARG_PLAYLIST_NAME).orEmpty() }

    /** Selected song ids, in the order they were picked. */
    private val selectedIds = LinkedHashSet<Long>()
    private var candidates: List<Song> = emptyList()
    private val query = MutableStateFlow("")

    private lateinit var songAdapter: PickSongAdapter
    private var preview: SongPreviewPlayer? = null
    /** Whether the main player was playing when a preview paused it. */
    private var resumeMainOnClose = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            state.getLongArray(KEY_SELECTED)?.let { selectedIds.addAll(it.asList()) }
            resumeMainOnClose = state.getBoolean(KEY_RESUME_MAIN)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetAddSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvPlaylistName.text = getString(R.string.add_songs_to, playlistName)

        preview = SongPreviewPlayer(requireContext()) { renderPreview() }

        songAdapter = PickSongAdapter(
            isSelected = { it.id in selectedIds },
            onToggle = { song ->
                if (!selectedIds.remove(song.id)) selectedIds.add(song.id)
                songAdapter.notifySelectionChanged(song)
                renderSelection()
            },
            onPreview = ::togglePreview
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
            itemAnimator?.changeDuration = 0 // payload rebinds must not cross-fade the rows
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
                }
            })
        }

        binding.etSearch.doAfterTextChanged { text ->
            binding.btnClear.isVisible = !text.isNullOrEmpty()
            query.value = text?.toString().orEmpty().trim()
        }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) hideKeyboard()
            false
        }
        binding.btnClear.setOnClickListener { binding.etSearch.text = null }

        binding.btnSelectAll.setOnClickListener {
            val visibleIds = songAdapter.currentList.map { it.id }
            if (visibleIds.all { it in selectedIds }) selectedIds.removeAll(visibleIds.toSet())
            else selectedIds.addAll(visibleIds)
            songAdapter.notifySelectionChanged()
            renderSelection()
        }
        binding.btnAdd.setOnClickListener { addSelected() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    libraryViewModel.libraryState
                        .combine(libraryViewModel.playlistSongs(playlistId)) { state, inPlaylist ->
                            val existing = inPlaylist.mapTo(HashSet()) { it.id }
                            (state as? LibraryState.Ready)?.songs.orEmpty().filter { it.id !in existing }
                        }
                        .combine(query) { songs, q -> songs to q }
                        .collect { (songs, q) -> render(songs, q) }
                }
                // Tick the preview progress bar
                launch {
                    while (true) {
                        preview?.takeIf { it.isPlaying }?.let { songAdapter.setPreviewProgress(it.progress) }
                        delay(PROGRESS_TICK_MS)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Open at full height; a long list deserves the whole screen
        val dialog = dialog as? BottomSheetDialog ?: return
        @Suppress("DEPRECATION") // keeps the add button above the keyboard on this non edge-to-edge dialog
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.updateLayoutParams {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        dialog.behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onStop() {
        super.onStop()
        // Never keep previewing in the background
        preview?.pause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLongArray(KEY_SELECTED, selectedIds.toLongArray())
        outState.putBoolean(KEY_RESUME_MAIN, resumeMainOnClose)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        preview?.stop()
        if (resumeMainOnClose) {
            resumeMainOnClose = false
            playerViewModel.resume()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        preview?.release()
        preview = null
        _binding = null
    }

    // ========== Rendering ==========

    private fun render(songs: List<Song>, query: String) {
        candidates = songs
        // Songs that left the library (or were added elsewhere) can't stay selected
        val available = songs.mapTo(HashSet()) { it.id }
        selectedIds.retainAll(available)

        val visible = if (query.isEmpty()) songs else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
        songAdapter.submitList(visible)

        binding.tvEmpty.isVisible = visible.isEmpty()
        binding.tvEmpty.text = if (songs.isEmpty()) {
            getString(R.string.all_songs_in_playlist)
        } else {
            getString(R.string.no_songs_match, query)
        }
        renderSelection(visible)
    }

    private fun renderSelection(visible: List<Song> = songAdapter.currentList) {
        val count = selectedIds.size
        val available = resources.getQuantityString(R.plurals.songs_available, candidates.size, candidates.size)
        binding.tvSelectionInfo.text = if (count > 0) {
            getString(R.string.dot_separated, available, resources.getQuantityString(R.plurals.songs_selected, count, count))
        } else {
            available
        }
        binding.tvSelectionInfo.isVisible = candidates.isNotEmpty()

        binding.btnSelectAll.isVisible = visible.isNotEmpty()
        binding.btnSelectAll.setText(
            if (visible.isNotEmpty() && visible.all { it.id in selectedIds }) R.string.deselect_all else R.string.select_all
        )

        binding.btnAdd.isEnabled = count > 0
        binding.btnAdd.text = if (count > 0) {
            resources.getQuantityString(R.plurals.add_song_count, count, count)
        } else {
            getString(R.string.select_songs_to_add)
        }
    }

    private fun renderPreview() {
        val player = preview ?: return
        if (_binding == null) return
        songAdapter.setPreview(player.songId, player.isPlaying)
    }

    // ========== Actions ==========

    private fun togglePreview(song: Song) {
        val player = preview ?: return
        val startsPlaying = player.songId != song.id || !player.isPlaying
        if (startsPlaying && playerViewModel.uiState.value.isPlaying) {
            // Previews take over the speaker; the main player picks up again when we close
            playerViewModel.pause()
            resumeMainOnClose = true
        }
        player.toggle(song)
    }

    private fun addSelected() {
        val byId = candidates.associateBy { it.id }
        val songs = selectedIds.mapNotNull { byId[it] }
        if (songs.isEmpty()) return
        val appContext = requireContext().applicationContext
        val name = playlistName
        libraryViewModel.addToPlaylist(playlistId, songs) { added ->
            val message = if (added == 0) {
                appContext.getString(R.string.already_in_playlist, name)
            } else {
                appContext.resources.getQuantityString(R.plurals.songs_added_to_playlist, added, added, name)
            }
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
        dismiss()
    }

    private fun hideKeyboard() {
        val view = _binding?.etSearch ?: return
        val window = dialog?.window ?: return
        WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
        view.clearFocus()
    }

    companion object {
        const val TAG = "add_songs"
        private const val ARG_PLAYLIST_ID = "playlist_id"
        private const val ARG_PLAYLIST_NAME = "playlist_name"
        private const val KEY_SELECTED = "selected"
        private const val KEY_RESUME_MAIN = "resume_main"
        private const val PROGRESS_TICK_MS = 250L

        fun newInstance(playlistId: Long, playlistName: String) = AddSongsBottomSheet().apply {
            arguments = bundleOf(ARG_PLAYLIST_ID to playlistId, ARG_PLAYLIST_NAME to playlistName)
        }
    }
}
