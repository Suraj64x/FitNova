package app.fitnova.data

import android.content.ContentValues
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import app.fitnova.domain.Alarm
import app.fitnova.domain.Exercise
import app.fitnova.domain.SleepEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/** Db v3 tables, kept next to the stores that read them. Times are epoch ms; alarm days are the [Alarm.days] bitmask. */
internal fun createSleepTables(db: SQLiteDatabase) {
    db.execSQL(
        """CREATE TABLE sleep (
            id TEXT PRIMARY KEY,
            bed_at INTEGER NOT NULL,
            wake_at INTEGER NOT NULL,
            source TEXT NOT NULL
        )""",
    )
    db.execSQL(
        """CREATE TABLE alarms (
            id TEXT PRIMARY KEY,
            hour INTEGER NOT NULL,
            minute INTEGER NOT NULL,
            days INTEGER NOT NULL,
            enabled INTEGER NOT NULL,
            exercise TEXT NOT NULL,
            reps INTEGER NOT NULL,
            label TEXT NOT NULL
        )""",
    )
}

/** Sleep log, kept on the phone. The night in progress lives in prefs, so it survives the app being closed overnight. */
class SleepStore(private val db: Db, private val prefs: SharedPreferences) {
    private val _entries = MutableStateFlow<List<SleepEntry>>(emptyList())

    /** Every logged sleep, newest first. */
    val entries: StateFlow<List<SleepEntry>> = _entries.asStateFlow()

    private val _bedtime = MutableStateFlow<Long?>(null)

    /** When "Going to bed" was tapped (epoch ms); null while awake. */
    val bedtime: StateFlow<Long?> = _bedtime.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _bedtime.value = prefs.getLong(BED, 0L).takeIf { it > 0 }
        _entries.value = db.readableDatabase.rawQuery("SELECT id, bed_at, wake_at, source FROM sleep ORDER BY wake_at DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(SleepEntry(c.getString(0), c.getLong(1), c.getLong(2), c.getString(3))) }
        }
    }

    /** Adds or replaces (same id) an entry. */
    suspend fun save(entry: SleepEntry) {
        withContext(Dispatchers.IO) {
            db.writableDatabase.insertWithOnConflict(
                "sleep",
                null,
                ContentValues().apply {
                    put("id", entry.id)
                    put("bed_at", entry.bedAt)
                    put("wake_at", entry.wakeAt)
                    put("source", entry.source)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        load()
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) { db.writableDatabase.delete("sleep", "id = ?", arrayOf(id)) }
        load()
    }

    fun goToBed(at: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(BED, at).apply()
        _bedtime.value = at
    }

    /**
     * Ends the night in progress and logs it as [source] ("tap", "alarm" or "manual"); null when no night was open.
     * Reads prefs rather than [bedtime], so it also works in a process the alarm just started.
     */
    suspend fun wakeUp(source: String, at: Long = System.currentTimeMillis()): SleepEntry? {
        val bed = withContext(Dispatchers.IO) { prefs.getLong(BED, 0L) }.takeIf { it > 0 } ?: return null
        cancelBed()
        if (at <= bed) return null // the clock went backwards: nothing sensible to log
        return SleepEntry(UUID.randomUUID().toString(), bed, at, source).also { save(it) }
    }

    /** Forgets the night in progress without logging it. */
    fun cancelBed() {
        prefs.edit().remove(BED).apply()
        _bedtime.value = null
    }

    private companion object {
        const val BED = "bedtime"
    }
}

/** Wake-up alarms, sorted by time of day. Arming them with the system is the alarm package's job. */
class AlarmStore(private val db: Db) {
    private val _items = MutableStateFlow<List<Alarm>>(emptyList())
    val items: StateFlow<List<Alarm>> = _items.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _items.value = db.readableDatabase.rawQuery(
            "SELECT id, hour, minute, days, enabled, exercise, reps, label FROM alarms ORDER BY hour, minute",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val exercise = Exercise.of(c.getString(5)) ?: Exercise.SQUAT
                    add(Alarm(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4) == 1, exercise, c.getInt(6), c.getString(7)))
                }
            }
        }
    }

    /** Adds or replaces (same id) an alarm. */
    suspend fun save(alarm: Alarm) {
        withContext(Dispatchers.IO) {
            db.writableDatabase.insertWithOnConflict(
                "alarms",
                null,
                ContentValues().apply {
                    put("id", alarm.id)
                    put("hour", alarm.hour)
                    put("minute", alarm.minute)
                    put("days", alarm.days)
                    put("enabled", if (alarm.enabled) 1 else 0)
                    put("exercise", alarm.exercise.id)
                    put("reps", alarm.reps)
                    put("label", alarm.label)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        load()
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) { db.writableDatabase.delete("alarms", "id = ?", arrayOf(id)) }
        load()
    }
}
