package com.musp.musicplayer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.musp.musicplayer.service.MusicService

/**
 * Receiver to handle headphone disconnect events
 * Pauses playback when headphones are unplugged
 */
class HeadphoneReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            // Headphones were unplugged, pause playback
            context?.let {
                val serviceIntent = Intent(it, MusicService::class.java)
                it.startService(serviceIntent)
                
                // Note: In a real app, you'd need to bind to the service to call pause()
                // For now, this sets up the receiver structure
            }
        }
    }
}