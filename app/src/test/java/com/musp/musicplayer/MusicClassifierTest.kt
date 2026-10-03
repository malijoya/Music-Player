package com.musp.musicplayer

import com.musp.musicplayer.utils.MusicClassifier
import com.musp.musicplayer.utils.MusicClassifier.AudioInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicClassifierTest {

    private fun info(
        path: String,
        title: String = path.substringAfterLast('/').substringBeforeLast('.'),
        artist: String? = null,
        album: String? = null,
        durationMs: Long = 240_000,
        mimeType: String? = "audio/mpeg",
        year: Int = 0,
        isMusicFlag: Boolean = true,
        isRecording: Boolean = false
    ) = AudioInfo(
        path = path,
        displayName = path.substringAfterLast('/'),
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        mimeType = mimeType,
        year = year,
        trackNumber = 0,
        isMusicFlag = isMusicFlag,
        isRecording = isRecording
    )

    @Test
    fun taggedSongIsMusic() {
        assertTrue(
            MusicClassifier.isMusic(
                info("/storage/emulated/0/Music/Tum Hi Ho.mp3", artist = "Arijit Singh", album = "Aashiqui 2", year = 2013)
            )
        )
    }

    @Test
    fun untaggedSongInMusicOrDownloadIsMusic() {
        assertTrue(MusicClassifier.isMusic(info("/storage/emulated/0/Music/Pasoori.mp3", album = "Music")))
        assertTrue(MusicClassifier.isMusic(info("/storage/emulated/0/Download/Kahani Suno 2.0.mp3", album = "Download")))
    }

    @Test
    fun songTitlesThatLookLikeRecorderWordsAreMusic() {
        assertTrue(MusicClassifier.isMusic(info("/storage/emulated/0/Music/Call Me Maybe.mp3", artist = "Carly Rae Jepsen")))
        assertTrue(MusicClassifier.isMusic(info("/storage/emulated/0/Download/Lost Stars.mp3")))
    }

    @Test
    fun whatsAppVoiceNoteIsNotMusic() {
        assertFalse(
            MusicClassifier.isMusic(
                info(
                    "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202410/PTT-20241001-WA0003.opus",
                    mimeType = "audio/ogg", durationMs = 95_000
                )
            )
        )
    }

    @Test
    fun untaggedWhatsAppAudioIsNotMusic() {
        assertFalse(
            MusicClassifier.isMusic(
                info(
                    "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/AUD-20240512-WA0012.mp3",
                    album = "WhatsApp Audio"
                )
            )
        )
    }

    @Test
    fun recordingsAreNotMusic() {
        assertFalse(MusicClassifier.isMusic(info("/storage/emulated/0/Recordings/Voice 012.m4a", mimeType = "audio/mp4")))
        assertFalse(MusicClassifier.isMusic(info("/storage/emulated/0/Music/Recording_045.m4a", mimeType = "audio/mp4")))
        assertFalse(MusicClassifier.isMusic(info("/storage/emulated/0/Music/20240101_101500.amr", mimeType = "audio/amr")))
        assertFalse(MusicClassifier.isMusic(info("/storage/emulated/0/Music/anything.m4a", isRecording = true)))
    }

    @Test
    fun shortClipsAreNotMusic() {
        assertFalse(MusicClassifier.isMusic(info("/storage/emulated/0/Music/beep.mp3", durationMs = 4_000)))
    }
}
