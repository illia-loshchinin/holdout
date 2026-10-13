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
 * | a rep counts | one short blip — the member's only proof, face-down, that it did |
 * | getting into position | the one correction on screen, in two words — "Closer", "Phone to 45°" |
 * | ten reps banked | a two-tone chime — those ten cannot be lost now |
 * | day complete | a success chime, then the screen |
 *
 * The per-rep blip is deliberately the shortest, quietest-sounding tone here,
 * so the chime every ten still stands out as the bigger moment.
 *
 * The words are the recorded calm voice ([VoiceClips]); a line with no clip —
 * the deadline, a total past 300 — is said by the device's own speech.
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

    private val voice = VoiceClips(context)

    private var speechReady = false

    /**
     * The fallback for a line with no recorded clip. Best effort. A device with no engine, or no English voice, does a sitting
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
            // The countdown is said on its tick, whatever is still playing: a
            // word that waits for the last one's end falls behind the count.
            is SittingViewModel.Cue.CountdownTick -> say(
                when (cue.remaining) {
                    3 -> "three"
                    2 -> "two"
                    1 -> "one"
                    else -> return
                },
                flush = true,
            )

            SittingViewModel.Cue.Counting -> {
                say("go", flush = true)
                tone(ToneGenerator.TONE_PROP_BEEP, 180)
            }

            SittingViewModel.Cue.Rep -> tone(ToneGenerator.TONE_PROP_BEEP, 70)

            // A correction replaces whatever correction was still being said:
            // only the latest one is worth hearing.
            is SittingViewModel.Cue.Say -> say(cue.words, flush = true)

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

    private fun say(text: String, flush: Boolean = false) {
        if (voice.say(text, flush)) {
            // A flushed clip also cuts off a fallback line still being spoken.
            if (flush) runCatching { speech?.stop() }
            return
        }
        if (!speechReady) return
        // QUEUE_ADD by default: "10 banked" after its chime, or a correction
        // after another, waits its turn rather than cutting the last one off.
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        runCatching { speech?.speak(text, mode, null, text) }
    }

    fun close() {
        voice.close()
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
