package com.musp.musicplayer.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.musp.musicplayer.R
import com.musp.musicplayer.activity.MainActivity
import com.musp.musicplayer.model.Song

// Wrapper class for handling Music Notifications
class MusicNotification {

    companion object {
        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 1

        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_PLAY = "action_play"
        const val ACTION_NEXT = "action_next"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Music Player Channel"
                val descriptionText = "Channel for music player controls"
                val importance = NotificationManager.IMPORTANCE_LOW 
                // Low importance to prevent sound/vibration on every update
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager: NotificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun createNotification(context: Context, song: Song, isPlaying: Boolean): Notification {
            // Create Intents for actions
            val prevIntent = Intent(context, MusicNotificationReceiver::class.java).setAction(ACTION_PREVIOUS)
            val prevPendingIntent = PendingIntent.getBroadcast(context, 0, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val playIntent = Intent(context, MusicNotificationReceiver::class.java).setAction(ACTION_PLAY)
            val playPendingIntent = PendingIntent.getBroadcast(context, 0, playIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val nextIntent = Intent(context, MusicNotificationReceiver::class.java).setAction(ACTION_NEXT)
            val nextPendingIntent = PendingIntent.getBroadcast(context, 0, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            
            // Content Intent (Open App)
            val contentIntent = Intent(context, MainActivity::class.java)
            val contentPendingIntent = PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE)

            // Play/Pause Icon
            val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Placeholder logic, usually explicit icon
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setLargeIcon(null) // TODO: Convert album art URI to Bitmap
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(contentPendingIntent)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
                .addAction(playPauseIcon, "Play", playPendingIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
                .setStyle(
                    Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(null)) // TODO: Link MediaSession
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
}

// Dummy Receiver class to allow compilation of Intents
class MusicNotificationReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Handle actions
    }
}