package com.musp.musicplayer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persists the queue and playback position so playback can be restored
 * after the app/service is killed or the device restarts.
 */
class PlaybackStateStore(context: Context) {

    // Same preferences file the original MusicService used
    private val prefs: SharedPreferences =
        context.getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)

    data class Snapshot(
        val queueIds: List<Long>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int
    )

    fun saveQueue(ids: List<Long>) {
        prefs.edit { putString(KEY_QUEUE, ids.joinToString(",")) }
    }

    fun saveProgress(index: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        prefs.edit {
            putInt(KEY_INDEX, index)
            putLong(KEY_POSITION, positionMs)
            putBoolean(KEY_SHUFFLE, shuffle)
            putInt(KEY_REPEAT, repeatMode)
        }
    }

    fun load(): Snapshot {
        val ids = prefs.getString(KEY_QUEUE, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()
        return Snapshot(
            queueIds = ids,
            index = prefs.getInt(KEY_INDEX, 0),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
            repeatMode = prefs.getInt(KEY_REPEAT, 0)
        )
    }

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, -1) // AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        set(value) = prefs.edit { putInt(KEY_THEME, value) }

    var songSortOrder: Int
        get() = prefs.getInt(KEY_SORT, 0)
        set(value) = prefs.edit { putInt(KEY_SORT, value) }

    /** true: the library shows only songs; false: every audio file (voice notes, recordings...) */
    var musicOnly: Boolean
        get() = prefs.getBoolean(KEY_MUSIC_ONLY, true)
        set(value) = prefs.edit { putBoolean(KEY_MUSIC_ONLY, value) }

    companion object {
        private const val KEY_QUEUE = "queue_ids"
        private const val KEY_INDEX = "queue_index"
        private const val KEY_POSITION = "queue_position"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_REPEAT = "repeat_mode"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SORT = "song_sort"
        private const val KEY_MUSIC_ONLY = "music_only"
    }
}
