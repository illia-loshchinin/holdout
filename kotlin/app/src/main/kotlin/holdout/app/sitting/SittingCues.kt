package holdout.app.sitting

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * What a sitting sounds like.
 *
 * The member is face-down with the phone propped beside them. They cannot read
 * the screen and cannot tap it, so every transition that used to be a button or
 * a caption is a sound instead.
 *
 * | Moment | |
 * |---|---|
 * | countdown | "three", "two", "one" |
 * | counting starts | "go", and a tone under it |
 * | ten reps banked | a two-tone chime — those ten cannot be lost now |
 * | day complete | a success chime, then the screen |
 *
 * Nothing sounds per rep. The chime every ten is the progress signal; a click
 * twenty-five times is noise, not information.
 *
 * **This is audio out, not audio in.** FR-022a and OD-3 forbid the microphone
 * and forbid audio as a *verification* signal; `RECORD_AUDIO` is not declared
 * anywhere and the manifest check in quickstart §4 still passes. Playback needs
 * no permission.
 */
class SittingCues(context: Context) {

    private val tones: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
    }.getOrNull()

    private var speechReady = false

    /**
     * Best effort. A device with no engine, or no English voice, does a sitting
     * with tones alone — speech is the nicer half of this, never the necessary
     * half.
     */
    private val speech: TextToSpeech? = runCatching {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            speechReady = status == TextToSpeech.SUCCESS &&
                engine?.setLanguage(Locale.UK) !in INVALID_LANGUAGE
        }
        engine
    }.getOrNull()

    fun play(cue: SittingViewModel.Cue) {
        when (cue) {
            is SittingViewModel.Cue.CountdownTick -> say(
                when (cue.remaining) {
                    3 -> "three"
                    2 -> "two"
                    1 -> "one"
                    else -> return
                },
            )

            SittingViewModel.Cue.Counting -> {
                say("go")
                tone(ToneGenerator.TONE_PROP_BEEP, 180)
            }

            is SittingViewModel.Cue.BlockBanked -> {
                tone(ToneGenerator.TONE_PROP_BEEP2, 300)
                say("${cue.banked} banked")
            }

            SittingViewModel.Cue.DayComplete -> {
                tone(ToneGenerator.TONE_PROP_ACK, 500)
                say("Done")
            }
        }
    }

    private fun tone(type: Int, durationMillis: Int) {
        runCatching { tones?.startTone(type, durationMillis) }
    }

    private fun say(text: String) {
        if (!speechReady) return
        // QUEUE_ADD, not QUEUE_FLUSH: "three, two, one, go" arrives as four
        // separate cues a second apart and must not cut each other off.
        runCatching { speech?.speak(text, TextToSpeech.QUEUE_ADD, null, text) }
    }

    fun close() {
        runCatching { tones?.release() }
        runCatching {
            speech?.stop()
            speech?.shutdown()
        }
    }

    private companion object {
        /** Out of 100. Loud enough to hear from the floor, not loud enough to startle. */
        const val VOLUME = 80

        val INVALID_LANGUAGE = setOf(
            TextToSpeech.LANG_MISSING_DATA,
            TextToSpeech.LANG_NOT_SUPPORTED,
        )
    }
}
