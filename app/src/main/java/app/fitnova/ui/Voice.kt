package app.fitnova.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import kotlin.concurrent.thread

/**
 * The voice coach: counts, holds, form cues and rest calls over the phone's own TextToSpeech, so it works
 * offline. The engine starts on [start]; a line asked for before it is ready waits (the latest one wins).
 * Other audio is ducked while it talks. Without an English voice it falls back to beeps for counts.
 */
class Voice(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)

    // Guidance plays at media volume and ducks music. Sonification would ride the system stream,
    // which silent and vibrate mode mute, and a workout is exactly when the phone is on vibrate.
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var pending: String? = null
    private var tones: ToneGenerator? = null

    /** Binds the engine. Called from an effect, not during composition. */
    fun start() {
        if (tts == null && !failed) tts = TextToSpeech(app, ::onInit)
    }

    // Runs on the main thread. An error can arrive inside the TextToSpeech constructor, before [tts] is set.
    private fun onInit(status: Int) {
        val t = tts
        if (status != TextToSpeech.SUCCESS || t == null) return fail()
        // setLanguage is a chain of blocking calls into the engine (one returns its whole voice list), so it runs
        // off the main thread; the rest comes back to main, where ready and pending live.
        thread(name = "voice-init") {
            val english = listOf(Locale.US, Locale.ENGLISH).any { runCatching { t.setLanguage(it) }.getOrDefault(-2) >= TextToSpeech.LANG_AVAILABLE }
            main.post {
                if (tts !== t) return@post // shut down meanwhile
                if (!english) return@post fail()
                t.setAudioAttributes(attributes)
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = finished(utteranceId)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)

                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) = finished(utteranceId)
                })
                ready = true
                pending?.let { speak(it, TextToSpeech.QUEUE_FLUSH, LINE) }
                pending = null
            }
        }
    }

    private fun fail() {
        failed = true
        ready = false
        pending = null
        tts?.shutdown()
        tts = null
    }

    // Runs on the engine's thread. Focus is let go once nothing more is queued, so the music comes back up.
    private fun finished(utteranceId: String?) {
        if (utteranceId == CUE) cueSpeaking = false
        if (tts?.isSpeaking != true) audio.abandonAudioFocusRequest(focus)
    }

    private fun speak(text: String, mode: Int, id: String) {
        val t = tts ?: return
        audio.requestAudioFocus(focus)
        t.speak(text, mode, null, id)
    }

    // Queue rules: a count or call replaces the line before it, so it is always current, except that it waits
    // for a form cue to finish; a form cue cuts in at once (they are rare and throttled).
    @Volatile private var cueSpeaking = false

    fun say(text: String) {
        when {
            ready -> speak(text, if (cueSpeaking) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, LINE)
            !failed -> pending = text
        }
    }

    fun cue(text: String) {
        if (!ready) return
        cueSpeaking = true
        speak(text, TextToSpeech.QUEUE_FLUSH, CUE)
    }

    /** 3, 2, 1, then go at 0. */
    fun countdown(n: Int) {
        if (failed) beep(long = n <= 0) else say(if (n > 0) "$n" else "Go")
    }

    /** The count went from [from] to [n] (more than one at a time when +5 is tapped). */
    fun rep(n: Int, target: Int, from: Int = n - 1) {
        if (failed) beep(long = from < target && n >= target) else repLine(n, target, from)?.let(::say)
    }

    fun held(seconds: Int, target: Int) {
        if (failed) {
            if (seconds == target) beep(long = true)
        } else {
            holdLine(seconds, target)?.let(::say)
        }
    }

    private fun beep(long: Boolean) {
        val t = tones ?: runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()?.also { tones = it } ?: return
        t.startTone(if (long) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, if (long) 400 else 120)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        tones?.release()
        tones = null
        audio.abandonAudioFocusRequest(focus)
    }

    private companion object {
        const val LINE = "line"
        const val CUE = "cue"
    }
}

/** A [Voice] while [enabled], shut down when it leaves the screen or is switched off. */
@Composable
fun rememberVoice(enabled: Boolean): Voice? {
    val context = LocalContext.current
    val voice = remember(enabled) { if (enabled) Voice(context) else null }
    DisposableEffect(voice) {
        voice?.start()
        onDispose { voice?.shutdown() }
    }
    return voice
}

/**
 * What to say when the count goes from [from] to [n]: every rep up to 20, then every 5th, plus halfway and the
 * target when they are passed (a +5 tap can jump over them).
 */
internal fun repLine(n: Int, target: Int, from: Int = n - 1): String? = when {
    n <= from || n <= 0 -> null
    from < target && n >= target -> "$n. Target reached"
    target >= 10 && from < target / 2 && n >= target / 2 -> "$n. Halfway"
    n <= 20 || n % 5 == 0 -> "$n"
    else -> null
}

/** What to say at second [seconds] of a hold: every 10 s, halfway, the last three seconds and the target. */
internal fun holdLine(seconds: Int, target: Int): String? = when {
    seconds <= 0 -> null
    seconds == target -> "Target reached"
    target >= 20 && seconds == target / 2 -> "Halfway"
    target - seconds in 1..3 -> "${target - seconds}"
    seconds % 10 == 0 -> "$seconds seconds"
    else -> null
}

/** Lets a form cue through once it has lasted [after] ms, and then at most every [gap] ms. */
internal class CueGate(private val after: Long = 1500, private val gap: Long = 4000) {
    private var cue: String? = null
    private var since = 0L
    private var spokenAt = Long.MIN_VALUE / 2

    /** [cue] is the current fix cue, or null while form is fine. Returns the line to speak, if any. */
    fun update(cue: String?, now: Long): String? {
        if (cue != this.cue) {
            this.cue = cue
            since = now
        }
        if (cue == null || now - since < after || now - spokenAt < gap) return null
        spokenAt = now
        return cue
    }
}
