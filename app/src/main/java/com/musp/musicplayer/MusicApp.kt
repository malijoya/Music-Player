package com.musp.musicplayer

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.musp.musicplayer.data.MusicRepository
import com.musp.musicplayer.data.PlaybackStateStore
import com.musp.musicplayer.data.SleepTimer
import com.musp.musicplayer.data.UserLibraryRepository
import com.musp.musicplayer.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MusicApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppCompatDelegate.setDefaultNightMode(container.playbackStateStore.themeMode)
    }
}

/** Simple manual dependency container shared by the UI and the playback service. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database: AppDatabase by lazy { AppDatabase.create(context) }
    val musicRepository = MusicRepository(context, appScope)
    val userLibraryRepository by lazy { UserLibraryRepository(database, musicRepository) }
    val playbackStateStore = PlaybackStateStore(context)
    val sleepTimer = SleepTimer()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MusicApp).container
