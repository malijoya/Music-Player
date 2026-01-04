package com.musp.musicplayer.fragment

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.musp.musicplayer.R
import com.musp.musicplayer.databinding.FragmentHomeBinding
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.service.MusicService

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private var musicService: MusicService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            
            // Set up listeners
            musicService?.setOnSongChangeListener { song ->
                updateMiniPlayer(song)
            }
            
            musicService?.setOnPlaybackStateChangeListener { isPlaying ->
                updatePlayPauseButton(isPlaying)
            }
            
            // Update UI with current song if playing
            musicService?.getCurrentSong()?.let { 
                updateMiniPlayer(it)
                binding.miniPlayer.visibility = View.VISIBLE
            }
            updatePlayPauseButton(musicService?.isPlaying() ?: false)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupMiniPlayer()
        bindToService()
    }

    private fun setupMiniPlayer() {
        binding.miniPlayerPlayPause.setOnClickListener {
            musicService?.playPause()
        }
        
        binding.miniPlayerNext.setOnClickListener {
            musicService?.next()
        }
        
        binding.miniPlayer.setOnClickListener {
            // Navigate to PlayerFragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PlayerFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun updateMiniPlayer(song: Song) {
        binding.miniPlayer.visibility = View.VISIBLE
        binding.miniPlayerTitle.text = song.title
        binding.miniPlayerArtist.text = song.artist
        
        // Load album art
        if (!song.albumArt.isNullOrEmpty()) {
            try {
                Glide.with(this)
                    .load(song.albumArt)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(binding.miniPlayerAlbumArt)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            binding.miniPlayerAlbumArt.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val iconRes = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        binding.miniPlayerPlayPause.setImageResource(iconRes)
    }

    private fun bindToService() {
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }
        
        _binding = null
    }
}
