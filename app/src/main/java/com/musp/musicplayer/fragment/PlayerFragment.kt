package com.musp.musicplayer.fragment

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.musp.musicplayer.R
import com.musp.musicplayer.databinding.FragmentPlayerBinding
import com.musp.musicplayer.model.Song
import com.musp.musicplayer.service.MusicService
import com.musp.musicplayer.utils.MusicUtils

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    
    private var musicService: MusicService? = null
    private var isBound = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            updateSeekBar()
            handler.postDelayed(this, 1000)
        }
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            
            // Set up listeners
            musicService?.setOnSongChangeListener { song ->
                updateUI(song)
            }
            
            musicService?.setOnPlaybackStateChangeListener { isPlaying ->
                updatePlayPauseButton(isPlaying)
            }
            
            // Update UI with current song if playing
            musicService?.getCurrentSong()?.let { updateUI(it) }
            updatePlayPauseButton(musicService?.isPlaying() ?: false)
            
            // Start updating seek bar
            handler.post(updateSeekBarRunnable)
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
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupClickListeners()
        setupSeekBar()
        bindToService()
    }

    private fun setupClickListeners() {
        binding.btnPlayPause.setOnClickListener {
            musicService?.playPause()
        }
        
        binding.btnNext.setOnClickListener {
            musicService?.next()
        }
        
        binding.btnPrev.setOnClickListener {
            musicService?.previous()
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
                handler.removeCallbacks(updateSeekBarRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.progress?.let { musicService?.seekTo(it) }
                handler.post(updateSeekBarRunnable)
            }
        })
    }

    private fun bindToService() {
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updateUI(song: Song) {
        binding.tvSongTitle.text = song.title
        binding.tvArtist.text = song.artist
        
        // Load album art
        if (!song.albumArt.isNullOrEmpty()) {
            try {
                Glide.with(this)
                    .load(song.albumArt)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(binding.ivAlbumArt)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            binding.ivAlbumArt.setImageResource(R.drawable.ic_launcher_foreground)
        }
        
        // Update duration
        val duration = song.duration
        binding.tvTotalTime.text = MusicUtils.formatDuration(duration)
        binding.seekBar.max = duration.toInt()
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val iconRes = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        binding.btnPlayPause.setImageResource(iconRes)
    }

    private fun updateSeekBar() {
        musicService?.let { service ->
            if (service.isPlaying()) {
                val currentPosition = service.getCurrentPosition()
                binding.seekBar.progress = currentPosition
                binding.tvCurrentTime.text = MusicUtils.formatDuration(currentPosition.toLong())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateSeekBarRunnable)
        
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }
        
        _binding = null
    }
}
