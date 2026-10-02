package com.musp.musicplayer.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.musp.musicplayer.R
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.databinding.FragmentPlayerBinding
import com.musp.musicplayer.ui.showSleepTimerDialog
import com.musp.musicplayer.ui.showSongMenu
import com.musp.musicplayer.utils.MusicUtils
import com.musp.musicplayer.utils.loadArtwork
import com.musp.musicplayer.viewmodel.LibraryViewModel
import com.musp.musicplayer.viewmodel.PlaybackUiState
import com.musp.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Full-screen Now Playing screen. */
class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val libraryViewModel: LibraryViewModel by activityViewModels()

    private var isUserSeeking = false
    private var displayedArtSongId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // This screen covers the bottom navigation, so keep controls clear of the system nav bar
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }

        setupClickListeners()
        setupSeekBar()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    playerViewModel.uiState
                        .combine(libraryViewModel.favoriteIds) { state, favorites -> state to favorites }
                        .collect { (state, favorites) -> updateUI(state, favorites) }
                }
                launch {
                    playerViewModel.positionMs.collect { updateSeekBar(it) }
                }
                launch {
                    playerViewModel.sleepTimerRemaining.collect { remaining ->
                        binding.tvSleepTimer.isVisible = remaining != null
                        binding.tvSleepTimer.text = when {
                            remaining == null -> null
                            remaining < 0 -> getString(R.string.sleep_timer_after_song)
                            else -> getString(R.string.sleep_timer_remaining, MusicUtils.formatDuration(remaining))
                        }
                        tintToggle(binding.btnSleepTimer, remaining != null)
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnCollapse.setOnClickListener { (activity as? MainActivity)?.closePlayer() }
        binding.btnPlayPause.setOnClickListener { playerViewModel.playPause() }
        binding.btnNext.setOnClickListener { playerViewModel.next() }
        binding.btnPrev.setOnClickListener { playerViewModel.previous() }
        binding.btnShuffle.setOnClickListener { playerViewModel.toggleShuffle() }
        binding.btnRepeat.setOnClickListener { playerViewModel.cycleRepeatMode() }
        binding.btnFavorite.setOnClickListener {
            playerViewModel.uiState.value.song?.takeIf { it.id >= 0 }?.let { libraryViewModel.toggleFavorite(it) }
        }
        binding.btnQueue.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(QueueBottomSheet.TAG) == null) {
                QueueBottomSheet().show(childFragmentManager, QueueBottomSheet.TAG)
            }
        }
        binding.btnSleepTimer.setOnClickListener { showSleepTimerDialog() }
        binding.btnMore.setOnClickListener { anchor ->
            playerViewModel.uiState.value.song?.let { showSongMenu(it, anchor) }
        }
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = MusicUtils.formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                seekBar?.progress?.let { playerViewModel.seekTo(it.toLong()) }
            }
        })
    }

    private fun updateUI(state: PlaybackUiState, favorites: Set<Long>) {
        val song = state.song
        val hasSong = song != null
        listOf(
            binding.btnPlayPause, binding.btnNext, binding.btnPrev, binding.btnShuffle, binding.btnRepeat,
            binding.btnFavorite, binding.btnMore, binding.seekBar
        ).forEach { it.isEnabled = hasSong }

        if (song == null) {
            binding.tvSongTitle.setText(R.string.nothing_playing)
            binding.tvArtist.text = null
            binding.ivAlbumArt.setImageResource(R.drawable.art_placeholder)
            displayedArtSongId = null
        } else {
            binding.tvSongTitle.text = song.title
            binding.tvSongTitle.isSelected = true // start marquee for long titles
            binding.tvArtist.text = if (song.album.isNotBlank()) {
                getString(R.string.dot_separated, song.artist, song.album)
            } else {
                song.artist
            }
            // Load album art only when the song changes
            if (displayedArtSongId != song.id) {
                displayedArtSongId = song.id
                binding.ivAlbumArt.loadArtwork(song.albumArt)
            }
        }

        // Update duration
        binding.tvTotalTime.text = MusicUtils.formatDuration(state.durationMs)
        binding.seekBar.max = state.durationMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

        binding.btnPlayPause.setImageResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        tintToggle(binding.btnShuffle, state.shuffleEnabled)
        binding.btnRepeat.setImageResource(
            if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        )
        tintToggle(binding.btnRepeat, state.repeatMode != Player.REPEAT_MODE_OFF)

        val isFavorite = song != null && song.id in favorites
        binding.btnFavorite.setImageResource(if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        tintToggle(binding.btnFavorite, isFavorite)
    }

    private fun updateSeekBar(positionMs: Long) {
        if (isUserSeeking) return
        binding.seekBar.progress = positionMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        binding.tvCurrentTime.text = MusicUtils.formatDuration(positionMs)
    }

    private fun tintToggle(button: android.widget.ImageButton, active: Boolean) {
        val color = ContextCompat.getColor(
            requireContext(), if (active) R.color.accent_color else R.color.text_secondary
        )
        button.imageTintList = ColorStateList.valueOf(color)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "player"
    }
}
