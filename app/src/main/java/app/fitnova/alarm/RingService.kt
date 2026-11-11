package app.fitnova.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.VolumeShaper
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import app.fitnova.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rings one alarm: a foreground service of type systemExempted (allowed because the app holds the exact-alarm
 * permission; no timeout, unlike shortService). It plays the alarm sound with a 30 s volume ramp, vibrates, holds a
 * wake lock and posts a CATEGORY_ALARM notification whose full-screen intent opens [RingActivity]. Only the
 * challenge, a snooze or the 15-minute cap stop it.
 */
class RingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
    }
    private var player: MediaPlayer? = null
    private var shaper: VolumeShaper? = null
    private var focus: AudioFocusRequest? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val current = _ringing.value
        when {
            intent?.action == CALM -> if (current != null) calm() else stopSelf()
            intent?.action == SNOOZE -> if (current != null) snooze(this, current) else stopSelf()
            // Swiped away, or a second alarm while one rings (ponytail: it is dropped; one ring at a time).
            current != null -> foreground(current)
            else -> begin(Ring.from(intent) ?: return stop())
        }
        return START_NOT_STICKY
    }

    private fun stop(): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    private fun begin(r: Ring) {
        _ringing.value = r // before the notification can open the ring screen
        ensureChannels(this)
        try {
            foreground(r) // first: startForegroundService gives only a few seconds
        } catch (e: Exception) {
            Log.w("FitNova", "can't ring in the foreground", e) // exact-alarm access revoked a moment ago
            _ringing.value = null
            stopSelf()
            return
        }
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fitnova:ring")
            .apply { acquire(RING_MAX_MS + 10_000) }
        play()
        vibrate()
        handler.postDelayed(::silence, RING_MAX_MS)
        RingRedirect.openIfVisible(r)
    }

    private fun foreground(r: Ring) {
        val n = notification(this, r)
        if (Build.VERSION.SDK_INT >= 34) startForeground(RING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else startForeground(RING_ID, n)
    }

    private fun play() {
        // ponytail: during a call only vibrate, no in-call beep.
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION) return
        // The settings:// defaults come from the system ringtone cache (no storage permission); the chain avoids silent devices.
        val afd = listOf(Settings.System.DEFAULT_ALARM_ALERT_URI, Settings.System.DEFAULT_RINGTONE_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
            .firstNotNullOfOrNull { runCatching { contentResolver.openAssetFileDescriptor(it, "r") }.getOrNull() } ?: return // vibration still runs
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(ATTRS).build().also { audio.requestAudioFocus(it) }
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(ATTRS)
                setWakeMode(this@RingService, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                afd.use { setDataSource(it) }
                setOnPreparedListener { mp ->
                    shaper = mp.createVolumeShaper(RAMP).apply { apply(VolumeShaper.Operation.PLAY) }
                    mp.start()
                }
                prepareAsync()
            }
        }.onFailure { Log.w("FitNova", "alarm sound failed", it) }.getOrNull()
    }

    private fun vibrate() {
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 600, 900), 0)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION") // the API 26 overload; USAGE_ALARM keeps it going in Do Not Disturb
            vibrator.vibrate(pattern, ATTRS)
        }
    }

    /** The challenge has started: stop shaking the propped-up phone and turn the sound down so the user can focus. */
    private fun calm() {
        vibrator.cancel()
        shaper?.close()
        shaper = null
        player?.setVolume(0.35f, 0.35f)
    }

    /** 15 minutes with nobody stopping it (battery, neighbours, nobody home): stop, and say so. Not counted as done. */
    private fun silence() {
        getSystemService(NotificationManager::class.java).notify(
            RING_ID + 1,
            Notification.Builder(this, CH_REMIND)
                .setSmallIcon(R.drawable.ic_alarm)
                .setColor(BRAND)
                .setContentTitle("Alarm silenced after 15 min")
                .setAutoCancel(true)
                .build(),
        )
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        shaper?.close()
        player?.release()
        focus?.let(audio::abandonAudioFocusRequest)
        vibrator.cancel()
        wake?.takeIf { it.isHeld }?.release()
        _ringing.value = null
        super.onDestroy() // the foreground notification goes with the service
    }

    companion object {
        const val CALM = "app.fitnova.alarm.CALM"
        const val SNOOZE = "app.fitnova.alarm.SNOOZE_NOW"
        private const val RING_ID = 42
        private const val RING_MAX_MS = 15 * 60_000L

        private val _ringing = MutableStateFlow<Ring?>(null)

        /** The alarm ringing right now in this process, or null. */
        val ringing: StateFlow<Ring?> = _ringing.asStateFlow()

        private val ATTRS: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /** Gentle wake: silence to full alarm volume on a cubic curve over 30 s. */
        private val RAMP = VolumeShaper.Configuration.Builder(VolumeShaper.Configuration.CUBIC_RAMP).setDuration(30_000).build()

        /** From an exact alarm, which may start a foreground service from the background. */
        fun start(c: Context, r: Ring) {
            try {
                c.startForegroundService(r.into(Intent(c, RingService::class.java)))
            } catch (e: Exception) {
                // Not expected for setAlarmClock. At least post the alarm, so its full-screen intent opens the ring
                // screen, which may then start the service from the foreground.
                Log.w("FitNova", "ring service blocked", e)
                ensureChannels(c)
                c.getSystemService(NotificationManager::class.java).notify(RING_ID, notification(c, r))
            }
        }

        /** Rings again in 5 minutes with one snooze less, and stops this ring. */
        fun snooze(c: Context, r: Ring) {
            if (AlarmScheduler.snooze(c, r.copy(snoozes = r.snoozes - 1))) end(c)
        }

        /**
         * Stops the ring. [ringing] clears at once rather than when the service gets round to onDestroy, so the app
         * coming back to the front in between doesn't open the ring screen again.
         */
        fun end(c: Context) {
            _ringing.value = null
            c.stopService(Intent(c, RingService::class.java))
        }

        private fun notification(c: Context, r: Ring): Notification {
            val open = PendingIntent.getActivity(
                c,
                r.id.hashCode(),
                r.into(Intent(c, RingActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val b = Notification.Builder(c, CH_ALARM)
                .setSmallIcon(R.drawable.ic_alarm)
                .setColor(BRAND)
                .setContentTitle(listOf(r.time, r.label).filter { it.isNotBlank() }.joinToString(" · "))
                .setContentText("Do ${r.reps} ${r.exercise.title.lowercase()} to stop")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC) // nothing personal in it
                .setOngoing(true)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                // Android 14+ lets people swipe ongoing notifications away while unlocked: put it straight back.
                .setDeleteIntent(PendingIntent.getService(c, 0, Intent(c, RingService::class.java), PendingIntent.FLAG_IMMUTABLE))
                .addAction(Notification.Action.Builder(null, "Start", open).build())
            if (r.snoozes > 0) {
                val snooze = PendingIntent.getService(c, 1, Intent(c, RingService::class.java).setAction(SNOOZE), PendingIntent.FLAG_IMMUTABLE)
                b.addAction(Notification.Action.Builder(null, "Snooze 5 min", snooze).build())
            }
            return b.build() // no Dismiss: only the challenge turns it off
        }
    }
}
