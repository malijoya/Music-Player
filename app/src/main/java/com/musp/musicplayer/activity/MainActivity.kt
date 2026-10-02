package com.musp.musicplayer.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.musp.musicplayer.R
import com.musp.musicplayer.appContainer
import com.musp.musicplayer.databinding.ActivityMainBinding
import com.musp.musicplayer.fragment.FavouritesFragment
import com.musp.musicplayer.fragment.HomeFragment
import com.musp.musicplayer.fragment.MusicFragment
import com.musp.musicplayer.fragment.PlayerFragment
import com.musp.musicplayer.fragment.PlaylistFragment
import com.musp.musicplayer.utils.MusicUtils
import com.musp.musicplayer.utils.loadArtwork
import com.musp.musicplayer.viewmodel.PlaybackUiState
import com.musp.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


//
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Activity-scoped: survives rotation and keeps the MediaController connection alive
    private val playerViewModel: PlayerViewModel by viewModels()

    private val musicRepository by lazy { appContainer.musicRepository }
    private var permissionPermanentlyDenied = false
    private var miniPlayerSongId: Long? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        musicRepository.onAppForeground()
        if (!granted) {
            // No rationale after a denial means "Don't ask again" (or the system auto-denied)
            permissionPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                this, MusicUtils.getAudioPermission()
            )
            Snackbar.make(binding.root, R.string.permission_denied_message, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.bottomChrome)
                .apply {
                    if (permissionPermanentlyDenied) setAction(R.string.open_settings) { openAppSettings() }
                }
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root) // Set content view via ViewBinding

        setupWindowInsets()

        permissionPermanentlyDenied = savedInstanceState?.getBoolean(KEY_PERMISSION_DENIED) ?: false

        // Set default fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()

            if (!MusicUtils.hasAudioPermission(this)) {
                permissionLauncher.launch(MusicUtils.getAudioPermission())
            }
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_playlist -> PlaylistFragment()
                R.id.nav_music -> MusicFragment()
                R.id.nav_favourites -> FavouritesFragment()
                else -> null
            }

            if (fragment != null && !supportFragmentManager.isStateSaved) {
                // Switching tabs discards detail screens opened from the previous tab
                supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.tab_enter, R.anim.tab_exit)
                    .replace(R.id.fragment_container, fragment)
                    .commit()
                true
            } else {
                false
            }
        }
        binding.bottomNavigation.setOnItemReselectedListener {
            // Re-selecting the current tab returns to its root screen
            if (!supportFragmentManager.isStateSaved) {
                supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
        }

        supportFragmentManager.addOnBackStackChangedListener { updateMiniPlayerVisibility() }
        setupMiniPlayer()

        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Pick up permission changes made in Settings and files added while we were away
        musicRepository.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        musicRepository.onAppBackground()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PERMISSION_DENIED, permissionPermanentlyDenied)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_PLAYER) openPlayer()
    }

    // ========== Permissions ==========

    fun requestAudioPermission() {
        if (MusicUtils.hasAudioPermission(this)) {
            musicRepository.onAppForeground()
        } else if (permissionPermanentlyDenied) {
            openAppSettings()
        } else {
            permissionLauncher.launch(MusicUtils.getAudioPermission())
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
        runCatching { startActivity(intent) }
    }

    // ========== Navigation ==========

    /** Opens a detail screen (album, artist, playlist, search) on top of the current tab. */
    fun navigateTo(fragment: Fragment) {
        if (supportFragmentManager.isStateSaved) return
        closePlayer()
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.screen_enter, R.anim.screen_exit,
                R.anim.screen_pop_enter, R.anim.screen_pop_exit
            )
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    val isPlayerOpen: Boolean
        get() = supportFragmentManager.findFragmentByTag(PlayerFragment.TAG) != null

    fun openPlayer() {
        if (isPlayerOpen || supportFragmentManager.isStateSaved) return
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_up, 0, 0, R.anim.slide_out_down)
            .add(R.id.player_container, PlayerFragment(), PlayerFragment.TAG)
            .addToBackStack(PlayerFragment.TAG)
            .commit()
        binding.miniPlayer.root.isVisible = false
    }

    fun closePlayer() {
        if (isPlayerOpen && !supportFragmentManager.isStateSaved) {
            supportFragmentManager.popBackStack(PlayerFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    // ========== Window insets / floating chrome ==========

    /** Height of the floating mini player + navigation bar; screens pad their content by it. */
    val bottomChromeHeight = MutableStateFlow(0)

    private fun setupWindowInsets() {
        val chromeGap = resources.getDimensionPixelSize(R.dimen.space_md)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.fragmentContainer.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.bottomChrome.updatePadding(
                left = bars.left + chromeGap,
                right = bars.right + chromeGap,
                bottom = bars.bottom + chromeGap
            )
            insets
        }
        // The navigation bar floats inside a card, so it must not pad itself for the system bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { _, insets -> insets }

        binding.bottomChrome.addOnLayoutChangeListener { view, _, top, _, bottom, _, _, _, _ ->
            bottomChromeHeight.value = if (view.isVisible) bottom - top else 0
        }
    }

    // ========== Mini Player ==========

    private fun setupMiniPlayer() {
        val mini = binding.miniPlayer
        mini.root.setOnClickListener { openPlayer() }
        mini.miniPlayerPlayPause.setOnClickListener { playerViewModel.playPause() }
        mini.miniPlayerNext.setOnClickListener { playerViewModel.next() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { playerViewModel.uiState.collect { renderMiniPlayer(it) } }
                launch {
                    playerViewModel.positionMs.collect { position ->
                        val duration = playerViewModel.uiState.value.durationMs
                        val progress = if (duration > 0) (position * 1000 / duration).toInt() else 0
                        mini.miniPlayerProgress.setProgressCompat(progress.coerceIn(0, 1000), false)
                    }
                }
            }
        }
    }

    private fun renderMiniPlayer(state: PlaybackUiState) {
        val mini = binding.miniPlayer
        val song = state.song
        updateMiniPlayerVisibility()
        if (song == null) {
            miniPlayerSongId = null
            return
        }
        mini.miniPlayerTitle.text = song.title
        mini.miniPlayerArtist.text = song.artist
        if (miniPlayerSongId != song.id) {
            miniPlayerSongId = song.id
            mini.miniPlayerAlbumArt.loadArtwork(song.albumArt)
        }
        mini.miniPlayerPlayPause.setImageResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun updateMiniPlayerVisibility() {
        binding.miniPlayer.root.isVisible = playerViewModel.uiState.value.song != null && !isPlayerOpen
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.musp.musicplayer.action.OPEN_PLAYER"
        private const val KEY_PERMISSION_DENIED = "permission_denied"
    }
}
