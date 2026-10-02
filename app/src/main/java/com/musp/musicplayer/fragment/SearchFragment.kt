package com.musp.musicplayer.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musp.musicplayer.R
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.adapter.MediaRowAdapter
import com.musp.musicplayer.adapter.SectionHeaderAdapter
import com.musp.musicplayer.adapter.SongAdapter
import com.musp.musicplayer.data.LibraryState
import com.musp.musicplayer.databinding.FragmentSearchBinding
import com.musp.musicplayer.ui.hide
import com.musp.musicplayer.ui.padForBottomChrome
import com.musp.musicplayer.ui.renderLibraryGate
import com.musp.musicplayer.ui.show
import com.musp.musicplayer.ui.showEmpty
import com.musp.musicplayer.ui.showSongMenu
import com.musp.musicplayer.viewmodel.LibraryViewModel
import com.musp.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** Searches songs, albums and artists as you type. */
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val libraryViewModel: LibraryViewModel by activityViewModels()

    private val query = MutableStateFlow("")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    @OptIn(FlowPreview::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val artistHeader = SectionHeaderAdapter(getString(R.string.tab_artists))
        val albumHeader = SectionHeaderAdapter(getString(R.string.tab_albums))
        val songHeader = SectionHeaderAdapter(getString(R.string.tab_songs))
        val artistAdapter = MediaRowAdapter.forArtists { artist ->
            (activity as? MainActivity)?.navigateTo(SongCollectionFragment.forArtist(artist.name))
        }
        val albumAdapter = MediaRowAdapter.forAlbums { album ->
            (activity as? MainActivity)?.navigateTo(SongCollectionFragment.forAlbum(album.id, album.title))
        }
        lateinit var songAdapter: SongAdapter
        songAdapter = SongAdapter(
            onSongClick = { _, position -> playerViewModel.playSongs(songAdapter.currentList, position) },
            onMoreClick = { song, anchor -> showSongMenu(song, anchor) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ConcatAdapter(artistHeader, artistAdapter, albumHeader, albumAdapter, songHeader, songAdapter)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
                }
            })
        }
        padForBottomChrome(binding.recyclerView, binding.emptyState.root)

        binding.btnBack.setOnClickListener {
            hideKeyboard()
            parentFragmentManager.popBackStack()
        }
        binding.btnClear.setOnClickListener { binding.etSearch.text?.clear() }
        binding.etSearch.doAfterTextChanged { text ->
            query.value = text?.toString().orEmpty()
            binding.btnClear.isVisible = !text.isNullOrEmpty()
        }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) hideKeyboard()
            actionId == EditorInfo.IME_ACTION_SEARCH
        }

        if (savedInstanceState == null) {
            binding.etSearch.requestFocus()
            binding.etSearch.post { showKeyboard() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    libraryViewModel.libraryState
                        .combine(libraryViewModel.search(query.debounce(150))) { state, results -> state to results }
                        .collect { (state, results) ->
                            val ready = renderLibraryGate(state, binding.emptyState)
                            val shown = if (ready) results else LibraryViewModel.SearchResults()

                            artistHeader.visible = shown.artists.isNotEmpty()
                            albumHeader.visible = shown.albums.isNotEmpty()
                            songHeader.visible = shown.songs.isNotEmpty()
                            artistAdapter.submitList(shown.artists.take(MAX_GROUP_RESULTS))
                            albumAdapter.submitList(shown.albums.take(MAX_GROUP_RESULTS))
                            songAdapter.submitList(shown.songs)

                            if (!ready) return@collect
                            val currentQuery = query.value
                            when {
                                currentQuery.isBlank() -> showEmpty(
                                    binding.emptyState, R.drawable.ic_search, R.string.search_prompt
                                )
                                results.isEmpty -> binding.emptyState.show(
                                    R.drawable.ic_search,
                                    getString(R.string.no_search_results, currentQuery.trim())
                                )
                                else -> binding.emptyState.hide()
                            }
                        }
                }
                launch {
                    playerViewModel.uiState.collect { songAdapter.setCurrentSongId(it.song?.id) }
                }
            }
        }
    }

    private fun showKeyboard() {
        val editText = _binding?.etSearch ?: return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val editText = _binding?.etSearch ?: return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
        editText.clearFocus()
    }

    override fun onDestroyView() {
        hideKeyboard()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MAX_GROUP_RESULTS = 5
    }
}
