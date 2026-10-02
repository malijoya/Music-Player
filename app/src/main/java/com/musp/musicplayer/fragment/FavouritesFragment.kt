package com.musp.musicplayer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.musp.musicplayer.databinding.FragmentFavouritesBinding
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.ui.hide
import com.musp.musicplayer.ui.padForBottomChrome
import com.musp.musicplayer.ui.renderLibraryGate
import com.musp.musicplayer.ui.showEmpty
import com.musp.musicplayer.ui.showSongMenu
import com.musp.musicplayer.viewmodel.LibraryViewModel
import com.musp.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FavouritesFragment : Fragment() {

    private var _binding: FragmentFavouritesBinding? = null
    private val binding get() = _binding!!

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val libraryViewModel: LibraryViewModel by activityViewModels()

    private lateinit var songAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavouritesBinding.inflate(inflater, container, false)
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
                        .combine(libraryViewModel.favoriteSongs) { state, songs -> state to songs }
                        .collect { (state, songs) -> render(state, songs) }
                }
                launch {
                    playerViewModel.uiState.collect { songAdapter.setCurrentSongId(it.song?.id) }
                }
            }
        }
    }

    private fun render(state: LibraryState, favorites: List<Song>) {
        val ready = renderLibraryGate(state, binding.emptyState)
        val hasFavorites = ready && favorites.isNotEmpty()
        binding.recyclerView.isVisible = hasFavorites
        binding.actionsRow.isVisible = hasFavorites
        songAdapter.submitList(if (hasFavorites) favorites else emptyList())
        if (!ready) return
        if (hasFavorites) {
            binding.emptyState.hide()
            binding.tvCount.text = resources.getQuantityString(R.plurals.song_count, favorites.size, favorites.size)
        } else {
            showEmpty(
                binding.emptyState, R.drawable.ic_favorite_border,
                R.string.no_favorites_title, R.string.no_favorites_message
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
