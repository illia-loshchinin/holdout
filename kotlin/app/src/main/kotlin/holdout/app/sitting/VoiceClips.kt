package holdout.app.sitting

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * The sitting's recorded voice: one clip per line the app can say, in
 * `assets/voice/`, recorded by `scripts/gen-sitting-voice.py` in a calm,
 * steady voice (owner, 13 Oct). The same voice on every phone, offline.
 *
 * A line with no clip is not played here — [say] returns false and the caller
 * falls back to the device's own speech.
 *
 * Clips play one after another, as the speech queue did: "three, two, one, go"
 * must not cut each other off. A flush stops whatever is playing and drops
 * what was waiting. Main thread only, like the cues it plays.
 */
class VoiceClips(context: Context) {

    private val assets = context.assets

    private val clips: Set<String> = runCatching {
        assets.list(DIR).orEmpty().toSet()
    }.getOrDefault(emptySet())

    private val speech = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val waiting = ArrayDeque<String>()
    private var player: MediaPlayer? = null
    private var closed = false

    /** Plays [words], or returns false when there is no clip for them. */
    fun say(words: String, flush: Boolean = false): Boolean {
        val file = "${slug(words)}.mp3"
        if (closed || file !in clips) return false
        if (flush) {
            waiting.clear()
            stop()
        }
        waiting.addLast(file)
        if (player == null) next()
        return true
    }

    private fun next() {
        val file = waiting.removeFirstOrNull() ?: return
        val mp = MediaPlayer()
        val started = runCatching {
            assets.openFd("$DIR/$file").use { fd ->
                mp.setAudioAttributes(speech)
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            mp.setOnCompletionListener { done(it) }
            mp.setOnErrorListener { failed, _, _ -> done(failed); true }
            mp.prepare()
            mp.start()
        }.isSuccess
        if (started) {
            player = mp
        } else {
            mp.release()
            next()
        }
    }

    private fun done(finished: MediaPlayer) {
        finished.release()
        if (player === finished) {
            player = null
            next()
        }
    }

    private fun stop() {
        val playing = player ?: return
        player = null
        runCatching { playing.stop() }
        playing.release()
    }

    fun close() {
        closed = true
        waiting.clear()
        stop()
    }

    companion object {
        const val DIR = "voice"

        /** The clip's name for [words]; the same rule as `slug` in the recording script. */
        fun slug(words: String): String =
            words.lowercase().replace("°", "deg").replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
