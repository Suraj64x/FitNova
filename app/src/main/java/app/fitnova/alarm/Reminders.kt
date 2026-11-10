package app.fitnova.alarm

import android.content.Context

/** Daily workout reminder. The setting itself is Graph.prefs "reminder_min" (minute of day, -1 = off). */
object Reminders {
    /**
     * Arms the reminder for [minuteOfDay] (0..1439) every day, or cancels it when null.
     * TODO(sleep, phase 2): inexact daily notification per alarm.md "reminders"; this stub does nothing yet.
     */
    fun schedule(context: Context, minuteOfDay: Int?) = Unit
}
