package com.musp.musicplayer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.musp.musicplayer.adapter.SongAdapter
import com.musp.musicplayer.data.LibraryState
import com.musp.musicplayer.databinding.FragmentSongCollectionBinding
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.ui.hide
import com.musp.musicplayer.ui.padForBottomChrome
import com.musp.musicplayer.ui.renderLibraryGate
import com.musp.musicplayer.ui.showConfirmDialog
import com.musp.musicplayer.ui.showEmpty
import com.musp.musicplayer.ui.showSongMenu
import com.musp.musicplayer.ui.showTextInputDialog
import com.musp.musicplayer.utils.MusicUtils
import com.musp.musicplayer.utils.loadArtwork
import com.musp.musicplayer.utils.loadBlurredArtwork
import com.musp.musicplayer.viewmodel.LibraryViewModel
import com.musp.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.Flow
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
            onSongClick = { _, position -> playerViewModel.playSongs(songAdapter.currentList, position) },
            onMoreClick = { song, anchor -> showSongMenu(song, anchor, playlistId) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }
        padForBottomChrome(binding.recyclerView, binding.emptyState.root)

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

    /** Opens the picker of library songs that are not in the playlist yet. */
    private fun showAddSongsDialog() {
        if (childFragmentManager.isStateSaved || childFragmentManager.findFragmentByTag(AddSongsBottomSheet.TAG) != null) return
        AddSongsBottomSheet.newInstance(collectionId, name).show(childFragmentManager, AddSongsBottomSheet.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TYPE = "type"
        private const val ARG_ID = "id"
        private const val ARG_NAME = "name"

        private fun create(type: Type, id: Long, name: String) = SongCollectionFragment().apply {
            arguments = bundleOf(ARG_TYPE to type.name, ARG_ID to id, ARG_NAME to name)
        }

        fun forAlbum(albumId: Long, title: String) = create(Type.ALBUM, albumId, title)
        fun forArtist(artistName: String) = create(Type.ARTIST, 0L, artistName)
        fun forPlaylist(playlistId: Long, name: String) = create(Type.PLAYLIST, playlistId, name)
    }
}
