package com.musp.musicplayer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.musp.musicplayer.R
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.adapter.SongAdapter
import com.musp.musicplayer.appContainer
import com.musp.musicplayer.data.LibraryState
import com.musp.musicplayer.databinding.FragmentHomeBinding
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
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val libraryViewModel: LibraryViewModel by activityViewModels()

    private lateinit var recentAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recentAdapter = SongAdapter(
            onSongClick = { _, position -> playerViewModel.playSongs(recentAdapter.currentList, position) },
            onMoreClick = { song, anchor -> showSongMenu(song, anchor) }
        )
        binding.recyclerRecent.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
        }
        padForBottomChrome(binding.recyclerRecent, binding.emptyState.root)
        binding.tvGreeting.setText(greetingForNow())

        binding.searchBar.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(SearchFragment())
        }
        binding.btnShuffleAll.setOnClickListener {
            playerViewModel.shuffleAll(libraryViewModel.allSongs())
        }
        binding.btnClearRecent.setOnClickListener { libraryViewModel.clearRecentlyPlayed() }
        binding.btnTheme.setOnClickListener { showThemeDialog() }
        binding.btnLibraryFilter.setOnClickListener { showLibraryFilterDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    libraryViewModel.libraryState
                        .combine(libraryViewModel.recentSongs) { state, recent -> state to recent }
                        .collect { (state, recent) -> render(state, recent) }
                }
                launch {
                    playerViewModel.uiState.collect { recentAdapter.setCurrentSongId(it.song?.id) }
                }
            }
        }
    }

    private fun render(state: LibraryState, recent: List<Song>) {
        val ready = renderLibraryGate(state, binding.emptyState)
        val hasSongs = ready && (state as LibraryState.Ready).songs.isNotEmpty()
        binding.btnShuffleAll.isEnabled = hasSongs
        binding.tvRecentHeader.isVisible = hasSongs
        binding.btnClearRecent.isVisible = hasSongs && recent.isNotEmpty()
        recentAdapter.submitList(if (hasSongs) recent else emptyList())

        if (!ready) return
        when {
            !hasSongs -> showEmpty(
                binding.emptyState, R.drawable.ic_music_note, R.string.no_songs_title, R.string.no_songs_message
            )
            recent.isEmpty() -> showEmpty(
                binding.emptyState, R.drawable.ic_history, R.string.no_recent_title, R.string.no_recent_message
            )
            else -> binding.emptyState.hide()
        }
    }

    private fun greetingForNow(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..16 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }

    private fun showThemeDialog() {
        val store = requireContext().appContainer.playbackStateStore
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val labels = arrayOf(
            getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)
        )
        val checked = modes.indexOf(store.themeMode).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.theme)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                store.themeMode = modes[which]
                AppCompatDelegate.setDefaultNightMode(modes[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLibraryFilterDialog() {
        val nonMusic = libraryViewModel.nonMusicCount()
        val labels = arrayOf(
            if (nonMusic > 0) getString(R.string.library_music_only_hiding, nonMusic)
            else getString(R.string.library_music_only),
            getString(R.string.library_all_audio)
        )
        val checked = if (libraryViewModel.musicOnly.value) 0 else 1
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.library_filter)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                libraryViewModel.setMusicOnly(which == 0)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
