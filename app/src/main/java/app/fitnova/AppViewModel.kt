package app.fitnova

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fitnova.data.ApiException
import app.fitnova.data.Rank
import app.fitnova.data.SyncJob
import app.fitnova.data.SyncState
import app.fitnova.data.loadRecipes
import app.fitnova.domain.Activity
import app.fitnova.domain.Body
import app.fitnova.domain.Exercise
import app.fitnova.domain.Goal
import app.fitnova.domain.Meal
import app.fitnova.domain.Recipe
import app.fitnova.domain.Sex
import app.fitnova.domain.Workout
import app.fitnova.domain.stats
import app.fitnova.domain.targets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class Tab { HOME, TRAIN, FOOD, RANKS, PROFILE }

enum class Period(val id: String, val label: String) { WEEK("week", "Week"), MONTH("month", "Month"), ALL("all", "All time") }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Full-screen steps of a workout, shown above the tabs. */
sealed interface WorkoutFlow {
    data class Live(val exercise: Exercise, val target: Int, val camera: Boolean) : WorkoutFlow
    data class Done(val workout: Workout) : WorkoutFlow
}

data class BoardState(
    val period: Period = Period.WEEK,
    val rows: List<Rank> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long = 0,
)

data class AuthState(val busy: Boolean = false, val error: String? = null, val recoveryCode: String? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var tab by mutableStateOf(Tab.HOME)
    var flow by mutableStateOf<WorkoutFlow?>(null)
        private set

    // Pop-ups (bottom sheets) that live above every tab.
    var setup by mutableStateOf<Exercise?>(null)
    var recipe by mutableStateOf<Recipe?>(null)
    var editingBody by mutableStateOf(false)

    val workouts = Graph.store.all
    val stats = workouts.map { stats(it) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, stats(emptyList()))
    val sync = Graph.sync.state
    val meals = Graph.meals.today

    var recipes by mutableStateOf<List<Recipe>>(emptyList())
        private set
    var body by mutableStateOf(readBody())
        private set
    val targets get() = body?.let(::targets)
    var dailyGoal by mutableIntStateOf(Graph.prefs.getInt("daily_goal", 50))
        private set

    var username by mutableStateOf(Graph.session.username)
        private set
    var board by mutableStateOf(BoardState())
        private set
    var auth by mutableStateOf(AuthState())
        private set
    var theme by mutableStateOf(
        runCatching { ThemeMode.valueOf(Graph.prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
    )
        private set

    /** Short confirmations shown in a snackbar ("Saved · +12 pts"). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val boardCache = mutableMapOf<Period, List<Rank>>()

    init {
        viewModelScope.launch {
            Graph.store.load()
            Graph.meals.load()
            recipes = loadRecipes(getApplication())
        }
    }

    /** Called on every app resume: refresh today's food log and sync only when needed. */
    fun onResume() {
        viewModelScope.launch { Graph.meals.load() }
        val last = (sync.value as? SyncState.Done)?.at ?: 0L
        if (Graph.store.pending().isNotEmpty() || System.currentTimeMillis() - last > 2 * 60_000) syncNow()
    }

    fun start(exercise: Exercise, target: Int, camera: Boolean) {
        setup = null
        flow = WorkoutFlow.Live(exercise, target, camera)
    }

    /** End a live session: with a result go to the summary, without one (nothing done) just close. */
    fun finish(result: Workout?) {
        flow = result?.let { WorkoutFlow.Done(it) }
    }

    fun save(workout: Workout) {
        flow = null
        tab = Tab.HOME
        viewModelScope.launch {
            Graph.store.save(listOf(workout))
            _messages.emit("Workout saved · +${workout.points} pts")
            syncNow()
        }
    }

    fun discard() {
        flow = null
    }

    fun syncNow() {
        viewModelScope.launch {
            if (!Graph.sync.run()) SyncJob.schedule(getApplication())
        }
    }

    fun changeDailyGoal(points: Int) {
        dailyGoal = points
        Graph.prefs.edit().putInt("daily_goal", points).apply()
    }

    // --- Nutrition ---

    fun logRecipe(r: Recipe) {
        recipe = null
        logMeal(r.name, r.kcal, r.protein, r.carbs, r.fat, r.id)
    }

    fun logMeal(name: String, kcal: Int, protein: Int, carbs: Int, fat: Int, recipeId: String? = null) {
        viewModelScope.launch {
            Graph.meals.add(Meal(UUID.randomUUID().toString(), LocalDate.now().toString(), name, kcal, protein, carbs, fat, recipeId, System.currentTimeMillis()))
            _messages.emit("Logged $name · $kcal kcal")
        }
    }

    fun removeMeal(id: String) {
        viewModelScope.launch { Graph.meals.remove(id) }
    }

    fun saveBody(b: Body) {
        body = b
        editingBody = false
        Graph.prefs.edit().putString("body", listOf(b.sex, b.age, b.heightCm, b.weightKg, b.activity, b.goal).joinToString("|")).apply()
        viewModelScope.launch { _messages.emit("Daily targets updated") }
    }

    private fun readBody(): Body? = runCatching {
        val p = Graph.prefs.getString("body", null)!!.split("|")
        Body(Sex.valueOf(p[0]), p[1].toInt(), p[2].toInt(), p[3].toInt(), Activity.valueOf(p[4]), Goal.valueOf(p[5]))
    }.getOrNull()

    // --- Leaderboard & account ---

    fun loadBoard(period: Period = board.period) {
        if (username == null) return
        board = board.copy(period = period, rows = boardCache[period] ?: emptyList(), loading = true, error = null)
        viewModelScope.launch {
            val next = try {
                val rows = Graph.api.leaderboard(period.id, ZoneId.systemDefault().id)
                boardCache[period] = rows
                board.copy(rows = rows, loading = false, updatedAt = System.currentTimeMillis())
            } catch (e: ApiException) {
                board.copy(loading = false, error = e.message)
            }
            if (board.period == period) board = next // ignore late answers after switching tabs
        }
    }

    fun signIn(username: String, password: String, create: Boolean) {
        val name = username.trim()
        auth = AuthState(busy = true)
        viewModelScope.launch {
            auth = try {
                val code = if (create) Graph.api.register(name, password) else Graph.api.login(name, password).let { null }
                this@AppViewModel.username = Graph.session.username
                runCatching { Graph.api.saveProfile(name) } // leaderboard display name; non-fatal
                syncNow()
                loadBoard()
                _messages.emit("Signed in as $name")
                AuthState(recoveryCode = code)
            } catch (e: ApiException) {
                AuthState(error = e.message)
            }
        }
    }

    fun dismissRecoveryCode() {
        auth = auth.copy(recoveryCode = null)
    }

    fun signOut() {
        viewModelScope.launch {
            Graph.session.clear()
            Graph.store.clearSynced()
            username = null
            board = BoardState()
            boardCache.clear()
            auth = AuthState()
        }
    }

    fun changeTheme(mode: ThemeMode) {
        theme = mode
        Graph.prefs.edit().putString("theme", mode.name).apply()
    }
}
