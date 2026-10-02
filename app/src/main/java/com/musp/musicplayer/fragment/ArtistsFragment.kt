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
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.adapter.MediaRowAdapter
import com.musp.musicplayer.data.LibraryState
import com.musp.musicplayer.databinding.FragmentListBinding
import com.musp.musicplayer.model.Artist
import com.musp.musicplayer.ui.hide
import com.musp.musicplayer.ui.renderLibraryGate
import com.musp.musicplayer.ui.showEmpty
import com.musp.musicplayer.viewmodel.LibraryViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ArtistsFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private val libraryViewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val artistAdapter = MediaRowAdapter.forArtists { artist ->
            (activity as? MainActivity)?.navigateTo(SongCollectionFragment.forArtist(artist.name))
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = artistAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                libraryViewModel.libraryState
                    .combine(libraryViewModel.artists) { state, artists -> state to artists }
                    .collect { (state, artists) -> render(state, artists, artistAdapter) }
            }
        }
    }

    private fun render(state: LibraryState, artists: List<Artist>, adapter: MediaRowAdapter<Artist>) {
        val ready = renderLibraryGate(state, binding.emptyState, binding.progressBar)
        val hasArtists = ready && artists.isNotEmpty()
        binding.recyclerView.isVisible = hasArtists
        adapter.submitList(if (hasArtists) artists else emptyList())
        if (!ready) return
        if (hasArtists) {
            binding.emptyState.hide()
        } else {
            showEmpty(binding.emptyState, R.drawable.ic_person, R.string.no_artists, R.string.no_songs_message)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
