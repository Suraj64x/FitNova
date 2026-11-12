package app.fitnova.alarm

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import app.fitnova.Graph
import app.fitnova.MainActivity
import app.fitnova.domain.Alarm
import app.fitnova.domain.Exercise
import app.fitnova.domain.missed
import app.fitnova.domain.next
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.time.ZonedDateTime
import java.util.Date

internal const val CH_ALARM = "alarm"
internal const val CH_REMIND = "remind"

/** The same ringing alarm while its ring screen is in front: no heads-up covering the challenge. */
internal const val CH_RINGING = "ringing"
internal const val BRAND = 0xFF534EF5.toInt()

/** Snoozes allowed per ring, and how long each lasts. */
const val SNOOZES = 2
const val SNOOZE_MS = 5 * 60_000L

/** For receivers and the ring screen's last writes: outlives any one screen, never the main thread. */
internal val alarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Runs [work] off the main thread and keeps the receiver alive until it ends. Failures are logged, never thrown:
 * a crash here would also kill an alarm that is ringing in the same process.
 */
internal fun BroadcastReceiver.background(work: suspend () -> Unit) {
    val pending = goAsync()
    alarmScope.launch {
        try {
            work()
        } catch (e: Exception) {
            Log.w("FitNova", "alarm work failed", e)
        } finally {
            pending.finish()
        }
    }
}

/** All channels; idempotent, so every path that may post calls it first. */
internal fun ensureChannels(c: Context) = c.getSystemService(NotificationManager::class.java).createNotificationChannels(
    listOf(
        NotificationChannel(CH_ALARM, "Wake-up alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null) // the ring service plays a ramped alarm sound itself
            enableVibration(false) // ...and vibrates itself
            setShowBadge(false)
        },
        NotificationChannel(CH_REMIND, "Reminders", NotificationManager.IMPORTANCE_DEFAULT),
        NotificationChannel(CH_RINGING, "Alarm in progress", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
    ),
)

/** What a ringing alarm needs. It travels in Intent extras, so the ring path never reads the database. */
data class Ring(val id: String, val time: String, val label: String, val exercise: Exercise, val reps: Int, val snoozes: Int) {
    fun into(i: Intent): Intent = i.putExtra(ID, id).putExtra(TIME, time).putExtra(LABEL, label)
        .putExtra(EXERCISE, exercise.id).putExtra(REPS, reps).putExtra(SNOOZES_LEFT, snoozes)

    companion object {
        private const val ID = "ring_id"
        private const val TIME = "ring_time"
        private const val LABEL = "ring_label"
        private const val EXERCISE = "ring_exercise"
        private const val REPS = "ring_reps"
        private const val SNOOZES_LEFT = "ring_snoozes"

        fun from(i: Intent?): Ring? {
            val id = i?.getStringExtra(ID) ?: return null
            return Ring(
                id,
                i.getStringExtra(TIME).orEmpty(),
                i.getStringExtra(LABEL).orEmpty(),
                Exercise.of(i.getStringExtra(EXERCISE).orEmpty()) ?: Exercise.SQUAT,
                i.getIntExtra(REPS, 15),
                i.getIntExtra(SNOOZES_LEFT, 0),
            )
        }

        /** [at] is shown in the user's 12/24-hour setting. */
        fun of(c: Context, a: Alarm, at: Long) = Ring(a.id, DateFormat.getTimeFormat(c).format(Date(at)), a.label, a.exercise, a.reps, SNOOZES)
    }
}

object AlarmScheduler {
    const val RING = "app.fitnova.alarm.RING"
    const val SNOOZE = "app.fitnova.alarm.SNOOZE"

    /** Id of the editor's "Test ring": rings like the real thing but logs no night and touches no alarm row. */
    const val TEST_ID = "test"

    private fun am(c: Context) = c.getSystemService(AlarmManager::class.java)

    /** Exact alarms are user-granted from Android 12 and off by default for new installs from 14. */
    fun canExact(c: Context) = Build.VERSION.SDK_INT < 31 || am(c).canScheduleExactAlarms()

    // The data URI makes each alarm's intents distinct (extras don't count), and RING vs SNOOZE keeps a snooze
    // from replacing next week's ring.
    private fun op(c: Context, action: String, id: String, ring: Ring? = null, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? {
        val i = Intent(c, AlarmReceiver::class.java).setAction(action).setData(Uri.parse("fitnova://alarm/${Uri.encode(id)}"))
        return PendingIntent.getBroadcast(c, id.hashCode(), ring?.into(i) ?: i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setClock(c: Context, at: Long, op: PendingIntent?): Boolean {
        op ?: return false
        // Shown when someone taps the alarm in the status bar or clock.
        val show = PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java).putExtra("open", "sleep"), PendingIntent.FLAG_IMMUTABLE)
        // setAlarmClock: exact, leaves Doze, may start the ring service from the background, and shows in the status bar.
        return runCatching { am(c).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op) }.isSuccess // revoked a moment ago
    }

    /** Arms [a] for its next ring after [now], or cancels it when off or not allowed; returns that moment (epoch ms). */
    fun arm(c: Context, a: Alarm, now: ZonedDateTime = ZonedDateTime.now()): Long? {
        val at = a.next(now)?.toInstant()?.toEpochMilli()
        if (at == null || !canExact(c)) {
            op(c, RING, a.id, flags = PendingIntent.FLAG_NO_CREATE)?.let(am(c)::cancel)
            return null
        }
        ensureChannels(c) // so "Wake-up alarms" is in the app's notification settings before the first ring
        return at.takeIf { setClock(c, at, op(c, RING, a.id, Ring.of(c, a, at))) }
    }

    fun cancel(c: Context, id: String) {
        for (action in listOf(RING, SNOOZE)) op(c, action, id, flags = PendingIntent.FLAG_NO_CREATE)?.let(am(c)::cancel)
    }

    /** Rings [r] again in [delayMs]; false when exact alarms aren't allowed. */
    fun snooze(c: Context, r: Ring, delayMs: Long = SNOOZE_MS): Boolean =
        canExact(c) && setClock(c, System.currentTimeMillis() + delayMs, op(c, SNOOZE, r.id, r))

    /** The editor's "Test ring": [a]'s challenge in [delayMs], nothing saved. */
    fun test(c: Context, a: Alarm, delayMs: Long = 5_000): Boolean {
        val at = System.currentTimeMillis() + delayMs
        return snooze(c, Ring.of(c, a.copy(id = TEST_ID), at).copy(snoozes = 0), delayMs)
    }

    /** After a RING (not a snooze): arm the next repeat, or switch a one-time alarm off. */
    suspend fun afterRing(c: Context, id: String) {
        if (id == TEST_ID) return
        val a = Graph.alarms.get(id) ?: return
        if (a.days == 0) Graph.alarms.switchOff(id) else Graph.alarms.armed(id, arm(c, a, ZonedDateTime.now().plusMinutes(1)))
    }

    /** Boot, clock or zone change, app update, exact-alarm access granted, every app start. */
    suspend fun rearmAll(c: Context) {
        Graph.alarms.load()
        val now = ZonedDateTime.now()
        for (a in Graph.alarms.items.value) {
            if (!a.enabled) continue
            if (a.missed(now.toInstant().toEpochMilli())) Graph.alarms.switchOff(a.id) else Graph.alarms.armed(a.id, arm(c, a, now))
        }
    }
}

/** What the phone allows; each missing piece has its own fix on the Sleep tab. */
data class Readiness(
    /** Runtime permission (Android 13+), app notifications on, and the alarm channel still high. */
    val notifications: Boolean,
    val exact: Boolean,
    /** Android 14+: without it the ring screen only opens from the heads-up. */
    val fullScreen: Boolean,
    val volume: Boolean,
    /** Without it the challenge is maths. */
    val camera: Boolean,
) {
    val all get() = notifications && exact && fullScreen && volume && camera
}

fun readiness(c: Context): Readiness {
    val nm = c.getSystemService(NotificationManager::class.java)
    val channelHigh = (nm.getNotificationChannel(CH_ALARM)?.importance ?: NotificationManager.IMPORTANCE_HIGH) >= NotificationManager.IMPORTANCE_HIGH
    return Readiness(
        notifications = nm.areNotificationsEnabled() && channelHigh,
        exact = AlarmScheduler.canExact(c),
        fullScreen = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent(),
        volume = c.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) > 0,
        camera = c.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
    )
}

/**
 * Opens the ring screen whenever the app is on screen during a ring: at once if the app is in use when it starts
 * (a heads-up alone is easy to miss), and on every later open of the app, which is the only way back when
 * notifications are off. Lives here because MainActivity is shared; installed once per process.
 */
internal object RingRedirect : Application.ActivityLifecycleCallbacks {
    @Volatile private var installed = false
    @Volatile private var top: WeakReference<Activity>? = null

    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(this)
    }

    /** Called by the ring service on the main thread when ringing starts. */
    fun openIfVisible(ring: Ring) {
        top?.get()?.let { open(it, ring) }
    }

    private fun open(from: Activity, ring: Ring) {
        runCatching { from.startActivity(ring.into(Intent(from, RingActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivity) return
        top = WeakReference(activity)
        RingService.ringing.value?.let { open(activity, it) }
    }

    override fun onActivityPaused(activity: Activity) {
        if (top?.get() === activity) top = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
