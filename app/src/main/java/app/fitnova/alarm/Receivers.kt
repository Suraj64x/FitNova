package app.fitnova.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/** An alarm (RING) or a snooze (SNOOZE) is due: start ringing, then bring the alarm row up to date. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ring = Ring.from(intent) ?: return
        // Bridges the gap until the ring service holds its own wake lock (AOSP DeskClock does the same).
        context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fitnova:alarm").acquire(10_000)
        RingService.start(context, ring)
        val app = context.applicationContext
        if (intent.action == AlarmScheduler.RING) background { AlarmScheduler.afterRing(app, ring.id) }
    }
}

/**
 * Boot, clock or time zone change, app update and exact-alarm access granted: the system has dropped (or would
 * mistime) our alarms, so arm everything again. Not exported: these broadcasts come from the system.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        background {
            AlarmScheduler.rearmAll(app)
            Reminders.rearm(app)
        }
    }
}
