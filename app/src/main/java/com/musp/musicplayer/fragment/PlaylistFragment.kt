package com.musp.musicplayer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.musp.musicplayer.databinding.FragmentPlaylistBinding

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // TODO: Implement playlist functionality with Room database
        // For now, show placeholder message
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = "No playlists yet.\n\nTap + to create a playlist."
        
        binding.fabCreatePlaylist.setOnClickListener {
            // TODO: Show dialog to create new playlist
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
