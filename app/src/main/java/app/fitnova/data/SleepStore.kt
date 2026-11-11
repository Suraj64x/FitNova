package app.fitnova.data

import android.content.ContentValues
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import app.fitnova.domain.Alarm
import app.fitnova.domain.Exercise
import app.fitnova.domain.MAX_SLEEP_MINUTES
import app.fitnova.domain.MIN_SLEEP_MINUTES
import app.fitnova.domain.SleepEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Db v3 tables, kept next to the stores that read them. Times are epoch ms; alarm days are the [Alarm.days] bitmask;
 * next_at is when the alarm is armed with the system (NULL = not armed).
 */
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
            label TEXT NOT NULL,
            next_at INTEGER
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
     * Ends the night in progress and logs it as [source] ("tap", "alarm" or "manual"). Null when no night was open,
     * or when it lasted under [MIN_SLEEP_MINUTES] (a stray tap, a test ring) or over [MAX_SLEEP_MINUTES] (a forgotten
     * "I'm awake"); the night is closed either way. Reads prefs rather than [bedtime], so it also works in a process
     * the alarm just started.
     */
    suspend fun wakeUp(source: String, at: Long = System.currentTimeMillis()): SleepEntry? {
        val bed = withContext(Dispatchers.IO) { prefs.getLong(BED, 0L) }.takeIf { it > 0 } ?: return null
        cancelBed()
        val entry = SleepEntry(UUID.randomUUID().toString(), bed, at, source)
        if (entry.minutes !in MIN_SLEEP_MINUTES..MAX_SLEEP_MINUTES) return null
        save(entry)
        return entry
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

    // ponytail: development builds of 1.5 created the v3 alarms table before next_at existed; drop this once 1.5 ships.
    private val sql by lazy {
        db.writableDatabase.also { w ->
            val columns = w.rawQuery("PRAGMA table_info(alarms)", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(1)) } }
            if ("next_at" !in columns) w.execSQL("ALTER TABLE alarms ADD COLUMN next_at INTEGER")
        }
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        _items.value = sql.rawQuery(
            "SELECT id, hour, minute, days, enabled, exercise, reps, label, next_at FROM alarms ORDER BY hour, minute",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val exercise = Exercise.of(c.getString(5)) ?: Exercise.SQUAT
                    val nextAt = if (c.isNull(8)) null else c.getLong(8)
                    add(Alarm(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4) == 1, exercise, c.getInt(6), c.getString(7), nextAt))
                }
            }
        }
    }

    /** Reads it from the database, so it also works in a process an alarm just started. */
    suspend fun get(id: String): Alarm? {
        load() // ponytail: a handful of rows; query by id if people ever keep hundreds of alarms
        return _items.value.firstOrNull { it.id == id }
    }

    /** Adds or replaces (same id) an alarm. */
    suspend fun save(alarm: Alarm) {
        withContext(Dispatchers.IO) {
            sql.insertWithOnConflict(
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
                    put("next_at", alarm.nextAt)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        load()
    }

    /** Records when [id] is armed (null = not armed). Touches only that column, so it can't undo an edit made meanwhile. */
    suspend fun armed(id: String, nextAt: Long?) = update(id, ContentValues().apply { put("next_at", nextAt) })

    /** A one-time alarm that has rung (or was missed) turns itself off. */
    suspend fun switchOff(id: String) = update(id, ContentValues().apply { put("enabled", 0); putNull("next_at") })

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) { sql.delete("alarms", "id = ?", arrayOf(id)) }
        load()
    }

    private suspend fun update(id: String, values: ContentValues) {
        withContext(Dispatchers.IO) { sql.update("alarms", values, "id = ?", arrayOf(id)) }
        load()
    }
}
