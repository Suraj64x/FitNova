package app.fitnova

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import app.fitnova.data.AlarmStore
import app.fitnova.data.Api
import app.fitnova.data.Db
import app.fitnova.data.MealStore
import app.fitnova.data.SessionStore
import app.fitnova.data.SleepStore
import app.fitnova.data.Sync
import app.fitnova.data.WorkoutStore

class FitNovaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** App-wide singletons, created once per process. */
object Graph {
    lateinit var store: WorkoutStore
        private set
    lateinit var meals: MealStore
        private set
    lateinit var sleep: SleepStore
        private set
    lateinit var alarms: AlarmStore
        private set
    lateinit var session: SessionStore
        private set
    lateinit var api: Api
        private set
    lateinit var sync: Sync
        private set
    lateinit var prefs: SharedPreferences
        private set

    fun init(context: Context) {
        val db = Db(context)
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        store = WorkoutStore(db)
        meals = MealStore(db)
        sleep = SleepStore(db, prefs)
        alarms = AlarmStore(db)
        session = SessionStore(context)
        api = Api(session)
        sync = Sync(store, api, session)
    }
}
