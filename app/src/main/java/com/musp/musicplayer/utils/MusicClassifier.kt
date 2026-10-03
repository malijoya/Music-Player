package com.musp.musicplayer.utils

import java.util.Locale

/**
 * Decides whether an audio file is a song or something else (voice note, call/voice recording,
 * WhatsApp audio, ringtone, podcast...) using only local information: MediaStore flags,
 * the folder it lives in, its file name, format, duration and tags. No audio analysis.
 *
 * Hard signals reject a file outright; everything else adds to or subtracts from a score.
 */
object MusicClassifier {

    /** What the scanner knows about a file. Tags are null when missing. */
    data class AudioInfo(
        val path: String?,
        val displayName: String?,
        val title: String,
        val artist: String?,
        val album: String?,
        val durationMs: Long,
        val mimeType: String?,
        val year: Int,
        val trackNumber: Int,
        val isMusicFlag: Boolean,
        val isRingtone: Boolean = false,
        val isNotification: Boolean = false,
        val isAlarm: Boolean = false,
        val isPodcast: Boolean = false,
        val isAudiobook: Boolean = false,
        val isRecording: Boolean = false
    )

    private const val MUSIC_THRESHOLD = 1

    private const val MIN_SONG_MS = 20_000L
    private const val SHORT_CLIP_MS = 60_000L
    private const val VERY_LONG_MS = 30 * 60_000L

    // Folders that only ever hold recordings
    private val VOICE_NOTE_FOLDERS = listOf("whatsapp voice notes", "whatsapp business voice notes")

    private val RECORDING_FOLDERS = listOf(
        "recordings", "recording", "recorder", "voice recorder", "voicerecorder", "soundrecorder",
        "sound_recorder", "sound recorder", "call", "calls", "call recordings", "callrecordings",
        "call_recordings", "callrecord", "call_rec", "callrec", "voice", "voices", "audiorecorder",
        "voice memos", "ringtones", "notifications", "alarms", "ui sounds"
    )

    private val WHATSAPP_FOLDERS = listOf("whatsapp audio", "whatsapp business audio")

    // PTT-20240101-WA0001.opus is a WhatsApp voice note
    private val VOICE_NOTE_NAME = Regex("""^ptt-\d{8}-wa\d+""")

    // AUD-20240512-WA0012.mp3 is an audio file sent over WhatsApp
    private val WHATSAPP_NAME = Regex("""^aud-\d{8}-wa\d+""")

    private val RECORDER_NAMES = listOf(
        Regex("""^(voice|recording|record|rec|memo|audio|new recording|call|callrecord|sound)[ _\-]*\d+"""),
        Regex("""^call[ _@\-]?(recording|record|rec|\+|\d)"""), // "Call recording ...", "Call@+92..."
        Regex("""^(rec|vid|aud)[_\-]?\d{8}"""),          // REC_20240101..., AUD20240101...
        Regex("""^\d{8}[_\- ]?\d{6}"""),                 // 20240101_101500 timestamp names
        Regex("""^\d{4}-\d{2}-\d{2}[ _]\d{2}[-.:]\d{2}""") // 2024-01-01 10-15...
    )

    private val SPEECH_MIME_TYPES = setOf("audio/amr", "audio/amr-wb", "audio/3gpp", "audio/3gpp2")

    // Long files with these words are mixes or albums, not lectures
    private val LONG_MUSIC_WORDS = Regex(
        """\b(mashup|mash-up|medley|jukebox|non-?stop|non stop|mix|album|lofi|songs)\b"""
    )

    private val SONG_WORDS = Regex(
        """\b(song|remix|mashup|official|lyrics|lyrical|feat|ft|cover|unplugged|ost|slowed|reverb|acoustic|instrumental)\b"""
    )

    fun isMusic(info: AudioInfo): Boolean = score(info) >= MUSIC_THRESHOLD

    /** Positive means "probably a song". [Int.MIN_VALUE] means definitely not a song. */
    fun score(info: AudioInfo): Int {
        val folders = folderNames(info.path)
        val fullName = (info.displayName ?: info.path?.substringAfterLast('/') ?: "").lowercase(Locale.ROOT)
        val fileName = fullName.substringBeforeLast('.').trim()
        val extension = fullName.substringAfterLast('.', "")
        val title = info.title.lowercase(Locale.ROOT)

        // Hard rules: Android itself or the file location says it's not a song
        if (info.isRecording || info.isRingtone || info.isNotification || info.isAlarm ||
            info.isPodcast || info.isAudiobook
        ) return Int.MIN_VALUE
        if (folders.any { it in VOICE_NOTE_FOLDERS } || VOICE_NOTE_NAME.containsMatchIn(fileName)) {
            return Int.MIN_VALUE
        }
        // Duration 0 means unknown, so don't judge it
        if (info.durationMs in 1 until MIN_SONG_MS) return Int.MIN_VALUE

        var score = 0

        // Tags: recordings almost never have real ones
        if (info.artist != null) score += 2
        if (info.album != null && !albumIsFolderName(info.album, folders)) score += 1
        if (info.year > 0) score += 1
        if (info.trackNumber > 0) score += 1
        if (info.isMusicFlag) score += 1
        if ("music" in folders) score += 1
        if (SONG_WORDS.containsMatchIn(title) || SONG_WORDS.containsMatchIn(fileName)) score += 1

        // Location and naming
        if (folders.any { it in RECORDING_FOLDERS }) score -= 3
        if (RECORDER_NAMES.any { it.containsMatchIn(fileName) }) score -= 3
        if (folders.any { it in WHATSAPP_FOLDERS }) score -= 2
        if (WHATSAPP_NAME.containsMatchIn(fileName)) score -= 2

        // Format: AMR/3GPP are speech codecs, Opus is what messaging apps record in
        val mime = info.mimeType?.lowercase(Locale.ROOT)
        if (mime in SPEECH_MIME_TYPES) score -= 2
        if (mime == "audio/opus" || extension == "opus") score -= 1

        // Duration
        if (info.durationMs in 1 until SHORT_CLIP_MS) score -= 2
        if (info.durationMs > VERY_LONG_MS &&
            !LONG_MUSIC_WORDS.containsMatchIn(title) && !LONG_MUSIC_WORDS.containsMatchIn(fileName)
        ) {
            score -= 1
        }

        return score
    }

    private fun folderNames(path: String?): List<String> {
        if (path.isNullOrEmpty()) return emptyList()
        return path.substringBeforeLast('/', "")
            .split('/')
            .filter { it.isNotEmpty() }
            .map { it.lowercase(Locale.ROOT) }
    }

    // Untagged files get their folder name as album (e.g. "WhatsApp Audio", "Download")
    private fun albumIsFolderName(album: String, folders: List<String>): Boolean {
        val lower = album.lowercase(Locale.ROOT)
        return folders.lastOrNull() == lower || lower in setOf("music", "download", "downloads", "audio")
    }
}
