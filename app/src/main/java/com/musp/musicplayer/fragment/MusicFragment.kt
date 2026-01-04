package com.musp.musicplayer.fragment

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.musp.musicplayer.adapter.SongAdapter
import com.musp.musicplayer.databinding.FragmentMusicBinding
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.service.MusicService
import com.musp.musicplayer.utils.MusicUtils

class MusicFragment : Fragment() {

    private var _binding: FragmentMusicBinding? = null
    private val binding get() = _binding!!
    
    private var musicService: MusicService? = null
    private var isBound = false
    
    private lateinit var songAdapter: SongAdapter
    private var allSongs: List<Song> = emptyList()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadSongs()
        } else {
            showPermissionDenied()
        }
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
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
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        checkPermissionAndLoadSongs()
        bindToService()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(emptyList()) { song ->
            onSongClick(song)
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }
    }

    private fun checkPermissionAndLoadSongs() {
        if (MusicUtils.hasAudioPermission(requireContext())) {
            loadSongs()
        } else {
            requestPermission()
        }
    }

    private fun requestPermission() {
        val permission = MusicUtils.getAudioPermission()
        requestPermissionLauncher.launch(permission)
    }

    private fun loadSongs() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        
        // Load songs in background
        Thread {
            allSongs = MusicUtils.getAllSongsFromDevice(requireContext())
            
            requireActivity().runOnUiThread {
                binding.progressBar.visibility = View.GONE
                
                if (allSongs.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "No music files found on device"
                } else {
                    updateAdapter(allSongs)
                }
            }
        }.start()
    }

    private fun updateAdapter(songs: List<Song>) {
        songAdapter = SongAdapter(songs) { song ->
            onSongClick(song)
        }
        binding.recyclerView.adapter = songAdapter
    }

    private fun onSongClick(song: Song) {
        // Start service if not already running
        val intent = Intent(requireContext(), MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
        
        // Set playlist and play selected song
        val songIndex = allSongs.indexOf(song)
        musicService?.setPlaylist(allSongs, songIndex)
        
        // Navigate to player fragment (optional)
        // You can add navigation here if desired
    }

    private fun showPermissionDenied() {
        binding.progressBar.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = "Permission required to access music files.\nPlease grant permission in Settings."
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
