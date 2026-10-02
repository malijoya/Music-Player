package com.musp.musicplayer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.musp.musicplayer.R
import com.musp.musicplayer.adapter.SongAdapter
import com.musp.musicplayer.data.LibraryState
import com.musp.musicplayer.databinding.FragmentListBinding
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.ui.hide
import com.musp.musicplayer.ui.padForBottomChrome
import com.musp.musicplayer.ui.renderLibraryGate
import com.musp.musicplayer.ui.showEmpty
import com.musp.musicplayer.ui.showSongMenu
import com.musp.musicplayer.viewmodel.LibraryViewModel
import com.musp.musicplayer.viewmodel.PlayerViewModel
import com.musp.musicplayer.viewmodel.SongSort
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** All songs on the device, sortable. */
class SongsFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val libraryViewModel: LibraryViewModel by activityViewModels()

    private lateinit var songAdapter: SongAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        songAdapter = SongAdapter(
            onSongClick = { _, position -> playerViewModel.playSongs(songAdapter.currentList, position) },
            onMoreClick = { song, anchor -> showSongMenu(song, anchor) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
            setHasFixedSize(true)
        }
        padForBottomChrome(binding.recyclerView, binding.emptyState.root)

        binding.btnShuffle.setOnClickListener { playerViewModel.shuffleAll(songAdapter.currentList) }
        binding.btnSort.setOnClickListener { showSortMenu(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    libraryViewModel.libraryState
                        .combine(libraryViewModel.sortedSongs) { state, songs -> state to songs }
                        .collect { (state, songs) -> render(state, songs) }
                }
                launch {
                    playerViewModel.uiState.collect { songAdapter.setCurrentSongId(it.song?.id) }
                }
            }
        }
    }

    private fun render(state: LibraryState, songs: List<Song>) {
        val ready = renderLibraryGate(state, binding.emptyState, binding.progressBar)
        val hasSongs = ready && songs.isNotEmpty()
        binding.listHeader.isVisible = hasSongs
        binding.recyclerView.isVisible = hasSongs
        songAdapter.submitList(if (hasSongs) songs else emptyList())
        if (!ready) return
        if (hasSongs) {
            binding.emptyState.hide()
            binding.tvCount.text = resources.getQuantityString(R.plurals.song_count, songs.size, songs.size)
        } else {
            showEmpty(binding.emptyState, R.drawable.ic_music_note, R.string.no_songs_title, R.string.no_songs_message)
        }
    }

    private fun showSortMenu(anchor: View) {
        val options = listOf(
            SongSort.TITLE to R.string.sort_title,
            SongSort.ARTIST to R.string.sort_artist,
            SongSort.DURATION to R.string.sort_duration,
            SongSort.DATE_ADDED to R.string.sort_date_added
        )
        val popup = PopupMenu(requireContext(), anchor)
        options.forEachIndexed { index, (sort, label) ->
            popup.menu.add(0, index, index, label).apply {
                isCheckable = true
                isChecked = libraryViewModel.sortOrder.value == sort
            }
        }
        popup.menu.setGroupCheckable(0, true, true)
        popup.setOnMenuItemClickListener { item ->
            libraryViewModel.setSortOrder(options[item.itemId].first)
            binding.recyclerView.scrollToPosition(0)
            true
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
