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
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.adapter.MediaRowAdapter
import com.musp.musicplayer.databinding.FragmentPlaylistBinding
import com.musp.musicplayer.model.Playlist
import com.musp.musicplayer.ui.hide
import com.musp.musicplayer.ui.showConfirmDialog
import com.musp.musicplayer.ui.showEmpty
import com.musp.musicplayer.ui.showTextInputDialog
import com.musp.musicplayer.viewmodel.LibraryViewModel
import com.musp.musicplayer.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    private val playerViewModel: PlayerViewModel by activityViewModels()
    private val libraryViewModel: LibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val playlistAdapter = MediaRowAdapter.forPlaylists(
            onClick = { openPlaylist(it) },
            onMoreClick = { playlist, anchor -> showPlaylistMenu(playlist, anchor) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = playlistAdapter
        }

        binding.fabCreatePlaylist.setOnClickListener {
            showTextInputDialog(R.string.create_playlist, positive = R.string.create) { name ->
                libraryViewModel.createPlaylist(name)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                libraryViewModel.playlists.collect { playlists ->
                    playlistAdapter.submitList(playlists)
                    binding.recyclerView.isVisible = playlists.isNotEmpty()
                    if (playlists.isEmpty()) {
                        showEmpty(
                            binding.emptyState, R.drawable.ic_queue_music,
                            R.string.no_playlists_title, R.string.no_playlists_message
                        )
                    } else {
                        binding.emptyState.hide()
                    }
                }
            }
        }
    }

    private fun openPlaylist(playlist: Playlist) {
        (activity as? MainActivity)?.navigateTo(SongCollectionFragment.forPlaylist(playlist.id, playlist.name))
    }

    private fun showPlaylistMenu(playlist: Playlist, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.inflate(R.menu.menu_playlist_item)
        popup.menu.findItem(R.id.action_play).isEnabled = playlist.songCount > 0
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_play -> viewLifecycleOwner.lifecycleScope.launch {
                    val songs = libraryViewModel.playlistSongs(playlist.id).first()
                    playerViewModel.playSongs(songs, 0, shuffle = false)
                }
                R.id.action_rename -> showTextInputDialog(
                    R.string.rename_playlist, initial = playlist.name, positive = R.string.rename
                ) { name -> libraryViewModel.renamePlaylist(playlist.id, name) }
                R.id.action_delete -> showConfirmDialog(
                    getString(R.string.delete_playlist),
                    getString(R.string.delete_playlist_message, playlist.name),
                    R.string.delete
                ) { libraryViewModel.deletePlaylist(playlist.id) }
            }
            true
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
