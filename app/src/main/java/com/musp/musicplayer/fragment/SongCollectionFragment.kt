package com.musp.musicplayer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.PluralsRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.shape.ShapeAppearanceModel
import com.musp.musicplayer.R
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.adapter.SongAdapter
import com.musp.musicplayer.data.LibraryState
import com.musp.musicplayer.databinding.FragmentSongCollectionBinding
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.ui.hide
import com.musp.musicplayer.ui.liftAboveBottomChrome
import com.musp.musicplayer.ui.padForBottomChrome
import com.musp.musicplayer.ui.renderLibraryGate
import com.musp.musicplayer.ui.showConfirmDialog
import com.musp.musicplayer.ui.showEmpty
import com.musp.musicplayer.ui.showAddToPlaylistDialog
import com.musp.musicplayer.ui.showSongMenu
import com.musp.musicplayer.ui.showTextInputDialog
import com.musp.musicplayer.utils.MusicUtils
import com.musp.musicplayer.utils.loadArtwork
import com.musp.musicplayer.utils.loadBlurredArtwork
import com.musp.musicplayer.viewmodel.LibraryViewModel
import com.musp.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Lists the songs of an album, an artist or a playlist.
 */
class SongCollectionFragment : Fragment() {

    enum class Type { ALBUM, ARTIST, PLAYLIST }

    private var _binding: FragmentSongCollectionBinding? = null
    private val binding get() = _binding!!

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val libraryViewModel: LibraryViewModel by activityViewModels()

    private lateinit var songAdapter: SongAdapter

    private val type: Type by lazy { Type.valueOf(requireArguments().getString(ARG_TYPE, Type.ALBUM.name)) }
    private val collectionId: Long by lazy { requireArguments().getLong(ARG_ID) }
    private var name: String = ""

    /** Selected song ids while multi-selecting (playlists only), null otherwise. */
    private var selectedIds: LinkedHashSet<Long>? = null
    /** Height the selection card adds above the bottom chrome. */
    private val selectionCardHeight = MutableStateFlow(0)
    private val exitSelectionOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = setSelecting(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getLongArray(KEY_SELECTED)?.let { selectedIds = LinkedHashSet(it.asList()) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSongCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        name = requireArguments().getString(ARG_NAME).orEmpty()
        binding.tvHeaderTitle.text = name

        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        if (type == Type.PLAYLIST) setupPlaylistMenu()
        if (type == Type.ARTIST) {
            binding.ivCover.shapeAppearanceModel =
                ShapeAppearanceModel.builder().setAllCornerSizes(ShapeAppearanceModel.PILL).build()
        }

        val playlistId = collectionId.takeIf { type == Type.PLAYLIST }
        songAdapter = SongAdapter(
            onSongClick = { song, position ->
                if (selectedIds != null) toggleSelected(song)
                else playerViewModel.playSongs(songAdapter.currentList, position)
            },
            onMoreClick = { song, anchor -> showSongMenu(song, anchor, playlistId) },
            // Playlists: long press starts multi-select with that song
            onLongClick = if (type == Type.PLAYLIST) ::startSelection else null
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
            itemAnimator?.changeDuration = 0 // selection rebinds must not cross-fade the rows
        }
        padForBottomChrome(binding.recyclerView, binding.emptyState.root, extra = selectionCardHeight)
        if (type == Type.PLAYLIST) setupSelection()

        binding.btnPlay.setOnClickListener {
            playerViewModel.playSongs(songAdapter.currentList, 0, shuffle = false)
        }
        binding.btnShuffle.setOnClickListener { playerViewModel.shuffleAll(songAdapter.currentList) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    libraryViewModel.libraryState
                        .combine(songsFlow()) { state, songs -> state to songs }
                        .collect { (state, songs) -> render(state, songs) }
                }
                launch {
                    playerViewModel.uiState.collect { songAdapter.setCurrentSongId(it.song?.id) }
                }
                if (type == Type.PLAYLIST) {
                    launch {
                        libraryViewModel.playlistName(collectionId).collect { newName ->
                            if (newName == null) {
                                // Playlist was deleted
                                parentFragmentManager.popBackStack()
                            } else {
                                name = newName
                                binding.tvHeaderTitle.text = newName
                            }
                        }
                    }
                }
            }
        }
    }

    private fun songsFlow(): Flow<List<Song>> = when (type) {
        Type.ALBUM -> libraryViewModel.albumSongs(collectionId)
        Type.ARTIST -> libraryViewModel.artistSongs(name)
        Type.PLAYLIST -> libraryViewModel.playlistSongs(collectionId)
    }

    private fun render(state: LibraryState, songs: List<Song>) {
        val ready = renderLibraryGate(state, binding.emptyState)
        val hasSongs = ready && songs.isNotEmpty()
        songAdapter.submitList(if (hasSongs) songs else emptyList())
        binding.recyclerView.isVisible = hasSongs
        binding.btnPlay.isEnabled = hasSongs
        binding.btnShuffle.isEnabled = hasSongs
        val coverArt = songs.firstOrNull()?.albumArt
        binding.ivCover.loadArtwork(coverArt)
        binding.ivBackdrop.loadBlurredArtwork(coverArt)

        val countText = resources.getQuantityString(R.plurals.song_count, songs.size, songs.size)
        val totalDuration = MusicUtils.formatDuration(songs.sumOf { it.duration })
        binding.tvHeaderSubtitle.text = when (type) {
            Type.ALBUM -> {
                val artist = songs.map { it.artist }.distinct().singleOrNull()
                listOfNotNull(artist, countText, totalDuration).joinToString(" · ")
            }
            Type.ARTIST -> {
                val albums = songs.map { it.albumId }.distinct().size
                listOf(resources.getQuantityString(R.plurals.album_count, albums, albums), countText)
                    .joinToString(" · ")
            }
            Type.PLAYLIST -> getString(R.string.dot_separated, countText, totalDuration)
        }

        // Drop selected songs that left the playlist; nothing left to select ends selection
        selectedIds?.let { selected ->
            if (!hasSongs) {
                setSelecting(false)
            } else {
                val present = songs.mapTo(HashSet()) { it.id }
                selected.retainAll(present)
                renderSelection()
            }
        }

        if (!ready) return
        when {
            hasSongs -> binding.emptyState.hide()
            type == Type.PLAYLIST -> showEmpty(
                binding.emptyState, R.drawable.ic_queue_music,
                R.string.empty_playlist_title, R.string.empty_playlist_message,
                R.string.add_songs
            ) { showAddSongsDialog() }
            else -> showEmpty(binding.emptyState, R.drawable.ic_music_note, R.string.no_songs_title)
        }
    }

    // ========== Playlist management ==========

    private fun setupPlaylistMenu() {
        binding.toolbar.inflateMenu(R.menu.menu_playlist_detail)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add_songs -> showAddSongsDialog()
                R.id.action_select -> startSelection(null)
                R.id.action_rename -> showTextInputDialog(
                    R.string.rename_playlist, initial = name, positive = R.string.rename
                ) { newName -> libraryViewModel.renamePlaylist(collectionId, newName) }
                R.id.action_delete -> showConfirmDialog(
                    getString(R.string.delete_playlist),
                    getString(R.string.delete_playlist_message, name),
                    R.string.delete
                ) { libraryViewModel.deletePlaylist(collectionId) }
            }
            true
        }
    }

    // ========== Multi-select (playlists) ==========

    private fun setupSelection() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, exitSelectionOnBack)
        liftAboveBottomChrome(binding.selectionCard)
        binding.selectionCard.addOnLayoutChangeListener { card, _, top, _, bottom, _, _, _, _ ->
            // The card's margin already includes the chrome height that padForBottomChrome adds
            val chrome = (activity as? MainActivity)?.bottomChromeHeight?.value ?: 0
            val margin = (card.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
            if (card.isVisible) selectionCardHeight.value = bottom - top + margin - chrome
        }

        binding.btnCloseSelection.setOnClickListener { setSelecting(false) }
        binding.btnSelectionAll.setOnClickListener {
            val selected = selectedIds ?: return@setOnClickListener
            val all = songAdapter.currentList.map { it.id }
            if (selected.containsAll(all)) selected.clear() else selected.addAll(all)
            renderSelection()
        }
        binding.btnSelectionPlay.setOnClickListener {
            playerViewModel.playSongs(selectedSongs(), 0, shuffle = false)
            setSelecting(false)
        }
        binding.btnSelectionQueue.setOnClickListener {
            val songs = selectedSongs()
            playerViewModel.addToQueue(songs)
            toast(R.plurals.songs_added_to_queue, songs.size)
            setSelecting(false)
        }
        binding.btnSelectionAddTo.setOnClickListener {
            showAddToPlaylistDialog(selectedSongs())
            setSelecting(false)
        }
        binding.btnSelectionRemove.setOnClickListener { confirmRemoveSelected() }

        // Restore a selection that survived rotation
        if (selectedIds != null) setSelecting(true)
    }

    /** Starts multi-select, optionally with [song] already selected. */
    private fun startSelection(song: Song?) {
        val selected = selectedIds ?: LinkedHashSet<Long>().also { selectedIds = it }
        song?.let { selected.add(it.id) }
        setSelecting(true)
    }

    private fun setSelecting(selecting: Boolean) {
        if (!selecting) selectedIds = null
        exitSelectionOnBack.isEnabled = selecting
        binding.selectionCard.isVisible = selecting
        if (!selecting) selectionCardHeight.value = 0
        renderSelection()
    }

    private fun toggleSelected(song: Song) {
        val selected = selectedIds ?: return
        if (!selected.remove(song.id)) selected.add(song.id)
        renderSelection()
    }

    private fun renderSelection() {
        val selected = selectedIds
        songAdapter.setSelection(selected)
        if (selected == null) return
        val count = selected.size
        binding.tvSelectionCount.text = if (count == 0) {
            getString(R.string.tap_songs_to_select)
        } else {
            resources.getQuantityString(R.plurals.songs_selected, count, count)
        }
        val allIds = songAdapter.currentList.map { it.id }
        val allSelected = allIds.isNotEmpty() && selected.containsAll(allIds)
        binding.btnSelectionAll.setText(if (allSelected) R.string.deselect_all else R.string.select_all)
        listOf(
            binding.btnSelectionPlay, binding.btnSelectionQueue,
            binding.btnSelectionAddTo, binding.btnSelectionRemove
        ).forEach { it.isEnabled = count > 0 }
    }

    /** Selected songs in playlist order. */
    private fun selectedSongs(): List<Song> {
        val selected = selectedIds ?: return emptyList()
        return songAdapter.currentList.filter { it.id in selected }
    }

    private fun confirmRemoveSelected() {
        val songs = selectedSongs()
        if (songs.isEmpty()) return
        val appContext = requireContext().applicationContext
        val playlistName = name
        showConfirmDialog(
            resources.getQuantityString(R.plurals.remove_songs_title, songs.size, songs.size),
            getString(R.string.remove_songs_message, playlistName),
            R.string.action_remove
        ) {
            libraryViewModel.removeFromPlaylist(collectionId, songs)
            val message = appContext.resources.getQuantityString(
                R.plurals.songs_removed_from_playlist, songs.size, songs.size, playlistName
            )
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            if (_binding != null) setSelecting(false)
        }
    }

    private fun toast(@PluralsRes message: Int, count: Int) {
        val context = context ?: return
        Toast.makeText(context, resources.getQuantityString(message, count, count), Toast.LENGTH_SHORT).show()
    }

    /** Opens the picker of library songs that are not in the playlist yet. */
    private fun showAddSongsDialog() {
        if (childFragmentManager.isStateSaved || childFragmentManager.findFragmentByTag(AddSongsBottomSheet.TAG) != null) return
        AddSongsBottomSheet.newInstance(collectionId, name).show(childFragmentManager, AddSongsBottomSheet.TAG)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedIds?.let { outState.putLongArray(KEY_SELECTED, it.toLongArray()) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TYPE = "type"
        private const val ARG_ID = "id"
        private const val ARG_NAME = "name"
        private const val KEY_SELECTED = "selected"

        private fun create(type: Type, id: Long, name: String) = SongCollectionFragment().apply {
            arguments = bundleOf(ARG_TYPE to type.name, ARG_ID to id, ARG_NAME to name)
        }

        fun forAlbum(albumId: Long, title: String) = create(Type.ALBUM, albumId, title)
        fun forArtist(artistName: String) = create(Type.ARTIST, 0L, artistName)
        fun forPlaylist(playlistId: Long, name: String) = create(Type.PLAYLIST, playlistId, name)
    }
}
