package app.fitnova.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.fitnova.domain.Exercise
import app.fitnova.domain.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

// ponytail: plain SQLite instead of Room (no annotation processing, smaller build). Move to Room once
// the schema needs real migrations (M2 groups / M3 alarms, sleep).
class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, "fitnova.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE workouts (
                id TEXT PRIMARY KEY,
                exercise TEXT NOT NULL,
                reps INTEGER NOT NULL,
                seconds INTEGER NOT NULL,
                form INTEGER NOT NULL,
                tracked INTEGER NOT NULL,
                started_at INTEGER NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0
            )""",
        )
        db.execSQL("CREATE INDEX workouts_started ON workouts(started_at)")
        createMeals(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createMeals(db)
    }

    private fun createMeals(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE meals (
                id TEXT PRIMARY KEY,
                day TEXT NOT NULL,
                name TEXT NOT NULL,
                kcal INTEGER NOT NULL,
                protein INTEGER NOT NULL,
                carbs INTEGER NOT NULL,
                fat INTEGER NOT NULL,
                recipe_id TEXT,
                logged_at INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX meals_day ON meals(day)")
    }
}

/** Offline-first workout log. Everything is written here first; sync uploads rows with synced = 0. */
class WorkoutStore(private val db: Db) {
    private val _all = MutableStateFlow<List<Workout>>(emptyList())

    /** All workouts, newest first. Small enough to keep in memory (a few KB per hundred workouts). */
    val all: StateFlow<List<Workout>> = _all.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _all.value = db.readableDatabase.rawQuery(
            "SELECT id, exercise, reps, seconds, form, tracked, started_at, synced FROM workouts ORDER BY started_at DESC",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val exercise = Exercise.of(c.getString(1)) ?: continue
                    add(Workout(c.getString(0), exercise, c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5) == 1, c.getLong(6), c.getInt(7) == 1))
                }
            }
        }
    }

    suspend fun save(workouts: List<Workout>) {
        if (workouts.isEmpty()) return
        withContext(Dispatchers.IO) {
            val w = db.writableDatabase
            w.beginTransaction()
            try {
                for (it in workouts) w.insertWithOnConflict("workouts", null, it.values(), SQLiteDatabase.CONFLICT_REPLACE)
                w.setTransactionSuccessful()
            } finally {
                w.endTransaction()
            }
        }
        load()
    }

    fun pending(): List<Workout> = _all.value.filter { !it.synced }

    suspend fun markSynced(ids: Collection<String>) {
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            val w = db.writableDatabase
            w.beginTransaction()
            try {
                for (id in ids) w.execSQL("UPDATE workouts SET synced = 1 WHERE id = ?", arrayOf(id))
                w.setTransactionSuccessful()
            } finally {
                w.endTransaction()
            }
        }
        load()
    }

    /** On sign-out: synced rows belong to the account (they live in the cloud); unsynced ones stay on the phone. */
    suspend fun clearSynced() {
        withContext(Dispatchers.IO) { db.writableDatabase.execSQL("DELETE FROM workouts WHERE synced = 1") }
        load()
    }

    private fun Workout.values() = ContentValues().apply {
        put("id", id)
        put("exercise", exercise.id)
        put("reps", reps)
        put("seconds", seconds)
        put("form", form)
        put("tracked", if (tracked) 1 else 0)
        put("started_at", startedAt)
        put("synced", if (synced) 1 else 0)
    }
}
