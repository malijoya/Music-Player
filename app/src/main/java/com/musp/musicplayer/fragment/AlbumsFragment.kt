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
import androidx.recyclerview.widget.GridLayoutManager
import com.musp.musicplayer.R
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.adapter.AlbumAdapter
import com.musp.musicplayer.data.LibraryState
import com.musp.musicplayer.databinding.FragmentListBinding
import com.musp.musicplayer.model.Album
import com.musp.musicplayer.ui.hide
import com.musp.musicplayer.ui.padForBottomChrome
import com.musp.musicplayer.ui.renderLibraryGate
import com.musp.musicplayer.ui.showEmpty
import com.musp.musicplayer.viewmodel.LibraryViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AlbumsFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private val libraryViewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val albumAdapter = AlbumAdapter { album ->
            (activity as? MainActivity)?.navigateTo(SongCollectionFragment.forAlbum(album.id, album.title))
        }
        // Responsive grid: more columns on wide screens / landscape
        val minTile = resources.getDimension(R.dimen.album_grid_min_width)
        val spans = (resources.displayMetrics.widthPixels / minTile).toInt().coerceAtLeast(2)
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), spans)
            adapter = albumAdapter
            setPadding(12.dp, paddingTop, 12.dp, paddingBottom)
        }
        padForBottomChrome(binding.recyclerView, binding.emptyState.root)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                libraryViewModel.libraryState
                    .combine(libraryViewModel.albums) { state, albums -> state to albums }
                    .collect { (state, albums) -> render(state, albums, albumAdapter) }
            }
        }
    }

    private fun render(state: LibraryState, albums: List<Album>, adapter: AlbumAdapter) {
        val ready = renderLibraryGate(state, binding.emptyState, binding.progressBar)
        val hasAlbums = ready && albums.isNotEmpty()
        binding.recyclerView.isVisible = hasAlbums
        adapter.submitList(if (hasAlbums) albums else emptyList())
        if (!ready) return
        if (hasAlbums) {
            binding.emptyState.hide()
        } else {
            showEmpty(binding.emptyState, R.drawable.ic_album, R.string.no_albums, R.string.no_songs_message)
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
