package app.fitnova.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.fitnova.Graph
import app.fitnova.MainActivity
import app.fitnova.R
import app.fitnova.domain.nextTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Daily workout reminder. The setting itself is Graph.prefs "reminder_min" (minute of day, -1 = off). */
object Reminders {
    private const val ID = 7

    private fun op(c: Context) = PendingIntent.getBroadcast(
        c, ID, Intent(c, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Arms the reminder for [minuteOfDay] (0..1439), or cancels it when null. Inexact on purpose: it needs no special
     * permission, a 10-minute window is fine for a nudge, and in Doze it waits for the next maintenance window.
     * Each one arms the next, recomputed from the clock time, so DST never shifts it (a 24 h repeat would).
     */
    fun schedule(context: Context, minuteOfDay: Int?) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (minuteOfDay == null || minuteOfDay !in 0 until 24 * 60) return am.cancel(op(context))
        ensureChannels(context)
        val at = nextTime(minuteOfDay / 60, minuteOfDay % 60, 0, ZonedDateTime.now()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, op(context))
    }

    /** Arms it again from the saved setting (boot, app update, every launch). */
    fun rearm(context: Context) = schedule(context, Graph.prefs.getInt("reminder_min", -1).takeIf { it >= 0 })

    /** Posts the nudge unless a workout is already logged today or notifications are off. */
    internal suspend fun fire(c: Context) {
        rearm(c) // tomorrow
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        Graph.store.load() // the process may have started just for this
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Graph.store.all.value.any { it.startedAt >= dayStart }) return
        // CLEAR_TOP|SINGLE_TOP: an open app switches tabs in onNewIntent instead of stacking a second copy.
        val tap = Intent(c, MainActivity::class.java).putExtra("open", "train").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(c, ID, tap, PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            ID,
            Notification.Builder(c, CH_REMIND)
                .setSmallIcon(R.drawable.ic_lightning)
                .setColor(BRAND)
                .setContentTitle("Keep your streak going 🔥")
                .setContentText("A few minutes counts. Tap to train.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        background { Reminders.fire(app) }
    }
}
