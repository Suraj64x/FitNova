package app.fitnova

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fitnova.alarm.Reminders
import app.fitnova.data.ApiException
import app.fitnova.data.Group
import app.fitnova.data.Rank
import app.fitnova.data.SyncJob
import app.fitnova.data.SyncState
import app.fitnova.data.loadExercisePhotos
import app.fitnova.data.loadRecipes
import app.fitnova.domain.Activity
import app.fitnova.domain.Alarm
import app.fitnova.domain.Badge
import app.fitnova.domain.badges
import app.fitnova.domain.Body
import app.fitnova.domain.Exercise
import app.fitnova.domain.Goal
import app.fitnova.domain.Meal
import app.fitnova.domain.Photo
import app.fitnova.domain.Recipe
import app.fitnova.domain.Routine
import app.fitnova.domain.Sex
import app.fitnova.domain.SleepEntry
import app.fitnova.domain.SleepStats
import app.fitnova.domain.Workout
import app.fitnova.domain.sleepStats
import app.fitnova.domain.stats
import app.fitnova.domain.targets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

enum class Tab { HOME, TRAIN, FOOD, SLEEP, RANKS }

/** Full-screen pages that cover the tabs. */
enum class Screen { PROFILE, HISTORY }

enum class Period(val id: String, val label: String) { WEEK("week", "Week"), MONTH("month", "Month"), ALL("all", "All time") }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Scope(val label: String) { GLOBAL("Global"), GROUPS("Groups"), COMMUNITIES("Clubs") }

data class GroupsState(
    val mine: List<Group> = emptyList(),
    val communities: List<Group> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Full-screen steps of a workout, shown above the tabs (always dark).
 * ponytail: lives in memory, so process death mid-set loses that set (finished steps are already saved);
 * keep it in a SavedStateHandle if that shows up.
 */
sealed interface WorkoutFlow {
    /** A live exercise. In a [routine] it is step [step], and [rest] seconds of rest (previewing it) come first. */
    data class Live(
        val exercise: Exercise,
        val target: Int,
        val camera: Boolean,
        val routine: Routine? = null,
        val step: Int = 0,
        val rest: Int = 0,
    ) : WorkoutFlow

    /** The summary. Every workout in [steps] is already saved; [workout] is the last one. [target] is 0 for routines. */
    data class Done(
        val workout: Workout,
        val steps: List<Workout> = listOf(workout),
        val routine: Routine? = null,
        val target: Int = 0,
        val badge: Badge? = null,
    ) : WorkoutFlow
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
    var screen by mutableStateOf<Screen?>(null)
        private set

    /** The day History opens on; null = today / the latest. */
    var historyDay by mutableStateOf<LocalDate?>(null)
        private set
    var flow by mutableStateOf<WorkoutFlow?>(null)
        private set

    // Pop-ups (bottom sheets) that live above every tab.
    var setup by mutableStateOf<Exercise?>(null)
    var recipe by mutableStateOf<Recipe?>(null)
    var editingBody by mutableStateOf(false)

    private val _today = MutableStateFlow(LocalDate.now())

    /** The current date. Moves on at midnight and on every resume, so day-based numbers never go stale. */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    val workouts = Graph.store.all

    // Worked out off the main thread, once per change of the history or the date.
    val stats = combine(workouts, today) { w, d -> stats(w, d) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, stats(emptyList()))
    // Badges reuse the stats pass above instead of running their own (the expensive part of both).
    val badges = combine(workouts, stats) { w, s -> badges(w, s = s) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, badges(emptyList()))
    val sync = Graph.sync.state
    val meals = Graph.meals.today

    /** False until the workout history has loaded once, so screens can show a placeholder instead of an empty state. */
    var loaded by mutableStateOf(false)
        private set

    var scope by mutableStateOf(Scope.GLOBAL)
    var groups by mutableStateOf(GroupsState())
        private set
    var openGroup by mutableStateOf<Group?>(null)
        private set
    var groupBoard by mutableStateOf(BoardState())
        private set

    var recipes by mutableStateOf<List<Recipe>>(emptyList())
        private set
    var body by mutableStateOf(readBody())
        private set
    val targets get() = body?.let(::targets)
    var dailyGoal by mutableIntStateOf(Graph.prefs.getInt("daily_goal", 50))
        private set
    var exercisePhotos by mutableStateOf<Map<String, Photo>>(emptyMap())
        private set

    /** Glasses of water today (goal 8), kept per day in prefs. */
    var water by mutableIntStateOf(Graph.prefs.getInt(waterKey(LocalDate.now()), 0))
        private set

    var username by mutableStateOf(Graph.session.username.value)
        private set
    var board by mutableStateOf(BoardState())
        private set
    var auth by mutableStateOf(AuthState())
        private set
    var theme by mutableStateOf(
        runCatching { ThemeMode.valueOf(Graph.prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
    )
        private set

    /** First-run flag. People upgrading from 1.4 have no flag yet, so any saved setting or an account counts as done. */
    var onboarded by mutableStateOf(Graph.prefs.getBoolean("onboarded", Graph.prefs.all.isNotEmpty() || Graph.session.signedIn))
        private set

    /** Voice coach during workouts. */
    var voice by mutableStateOf(Graph.prefs.getBoolean("voice", true))
        private set

    /** Daily workout reminder as minute of day; null = off. */
    var reminder by mutableStateOf(Graph.prefs.getInt("reminder_min", -1).takeIf { it >= 0 })
        private set

    /** Sleep goal in minutes. */
    var sleepGoal by mutableIntStateOf(Graph.prefs.getInt("sleep_goal_min", 480))
        private set

    /** Logged sleeps, newest first. */
    val sleep: StateFlow<List<SleepEntry>> = Graph.sleep.entries

    /** When "Going to bed" was tapped (epoch ms); null while awake. */
    val bedtime: StateFlow<Long?> = Graph.sleep.bedtime
    val alarms: StateFlow<List<Alarm>> = Graph.alarms.items
    val sleepStats: StateFlow<SleepStats> = combine(sleep, today, snapshotFlow { sleepGoal }) { e, d, goal -> sleepStats(e, goal, d) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, sleepStats(emptyList(), sleepGoal, today.value))

    /** Short confirmations shown in a snackbar ("Saved · +12 pts"). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val boardCache = mutableMapOf<Period, List<Rank>>()

    init {
        // History first: it decides between the loading state and the real screens. Other loads run alongside.
        viewModelScope.launch {
            Graph.store.load()
            loaded = true
            launch { Graph.sleep.load() }
            launch { Graph.alarms.load() }
            // Day-based state follows the date: water and today's meals reload when it changes.
            today.collect { day ->
                water = Graph.prefs.getInt(waterKey(day), 0)
                Graph.meals.load(day)
            }
        }
        viewModelScope.launch { exercisePhotos = loadExercisePhotos(getApplication()) }
        viewModelScope.launch { recipes = loadRecipes(getApplication()) }
        viewModelScope.launch {
            // Sign-out and an expired session (refresh rejected) both end up here.
            Graph.session.username.collect { name ->
                username = name
                if (name == null) {
                    board = BoardState()
                    boardCache.clear()
                    groups = GroupsState()
                    openGroup = null
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                val now = ZonedDateTime.now()
                delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis())
                _today.value = LocalDate.now()
            }
        }
    }

    /** Called on every app resume: catch up with the date, and sync only when there is something to do. */
    fun onResume() {
        _today.value = LocalDate.now()
        val last = (sync.value as? SyncState.Done)?.at ?: 0L
        // A full pull is heavy on slow networks: only for waiting uploads or when the last one is 15+ minutes old.
        if (Graph.store.pending().isNotEmpty() || System.currentTimeMillis() - last > 15 * 60_000) syncNow()
    }

    // --- Navigation ---

    fun openProfile() {
        screen = Screen.PROFILE
    }

    fun openHistory(day: LocalDate? = null) {
        historyDay = day
        screen = Screen.HISTORY
    }

    fun closeScreen() {
        screen = null
    }

    fun finishOnboarding(goal: Int?) {
        goal?.let(::changeDailyGoal)
        onboarded = true
        Graph.prefs.edit().putBoolean("onboarded", true).apply()
    }

    // --- Workouts ---

    // This session's saved steps (a routine has several) and the save still running, so a delete never races it.
    private val sessionSteps = mutableListOf<Workout>()
    private var stepSave: Job? = null

    /** The target used last time for [exercise], else its default. */
    fun lastTarget(exercise: Exercise) = Graph.prefs.getInt("target_${exercise.id}", 0).takeIf { it > 0 } ?: exercise.defaultTarget

    fun start(exercise: Exercise, target: Int, camera: Boolean) {
        setup = null
        sessionSteps.clear()
        Graph.prefs.edit().putInt("target_${exercise.id}", target).apply()
        flow = WorkoutFlow.Live(exercise, target, camera)
    }

    fun startRoutine(routine: Routine, camera: Boolean) {
        setup = null
        sessionSteps.clear()
        val first = routine.steps.first()
        flow = WorkoutFlow.Live(first.exercise, first.target, camera, routine)
    }

    /**
     * A live step ended. Its result is saved at once, so nothing is lost. A routine moves on to its next step
     * (rest first), anything else to the summary; with nothing saved it just closes.
     */
    fun finish(result: Workout?) {
        val live = flow as? WorkoutFlow.Live ?: return
        result?.let(::keepStep)
        val routine = live.routine
        val next = live.step + 1
        flow = if (routine != null && next < routine.steps.size) {
            val s = routine.steps[next]
            WorkoutFlow.Live(s.exercise, s.target, live.camera, routine, next, routine.restSeconds)
        } else {
            summaryOf(live)
        }
    }

    /** Stops a routine early: the steps already done (and [result]) stay saved and show in the summary. */
    fun endRoutine(result: Workout?) {
        val live = flow as? WorkoutFlow.Live ?: return
        result?.let(::keepStep)
        flow = summaryOf(live)
    }

    private fun keepStep(w: Workout) {
        sessionSteps += w
        val previous = stepSave
        stepSave = viewModelScope.launch {
            previous?.join()
            Graph.store.save(listOf(w))
        }
    }

    private fun summaryOf(live: WorkoutFlow.Live): WorkoutFlow.Done? {
        if (sessionSteps.isEmpty()) return null
        val steps = sessionSteps.toList()
        val saved = stepSave
        viewModelScope.launch {
            saved?.join()
            val badge = newBadgeFor(steps) ?: return@launch
            (flow as? WorkoutFlow.Done)?.takeIf { it.steps == steps }?.let { flow = it.copy(badge = badge) }
        }
        return WorkoutFlow.Done(steps.last(), steps, live.routine, if (live.routine == null) live.target else 0)
    }

    /** The first badge the history earns only thanks to [added]. Worked out off the main thread. */
    private suspend fun newBadgeFor(added: List<Workout>): Badge? {
        val ids = added.mapTo(HashSet()) { it.id }
        val all = workouts.value
        return withContext(Dispatchers.Default) {
            val before = badges(all.filter { it.id !in ids }).filter { it.earned }.mapTo(HashSet()) { it.id }
            badges(all).firstOrNull { it.earned && it.id !in before }
        }
    }

    /**
     * The summary's Done: its workouts are already saved, so close, report the points and any new badge, and sync.
     * A workout from anywhere else is stored first.
     */
    fun save(workout: Workout) {
        val done = (flow as? WorkoutFlow.Done)?.takeIf { d -> d.steps.any { it.id == workout.id } }
        val steps = done?.steps ?: listOf(workout)
        flow = null
        tab = Tab.HOME
        sessionSteps.clear()
        viewModelScope.launch {
            if (done != null) stepSave?.join() else Graph.store.save(steps)
            _messages.emit("+${steps.sumOf { it.points }} pts ✓")
            (done?.badge ?: newBadgeFor(steps))?.let { _messages.emit("${it.emoji} New badge: ${it.title}") }
            syncNow()
        }
    }

    /** Deletes a workout. One already in the cloud is deleted there first, or the next pull would bring it back. */
    fun deleteWorkout(w: Workout) {
        viewModelScope.launch {
            // Re-read: it may have been uploaded since the list was drawn.
            // ponytail: a delete racing an in-flight upload can return on the next pull; keep tombstones if that shows up.
            val synced = Graph.store.all.value.firstOrNull { it.id == w.id }?.synced ?: return@launch
            if (synced) {
                try {
                    Graph.api.deleteWorkout(w.id)
                } catch (e: Exception) {
                    _messages.emit("Can't delete while offline")
                    return@launch
                }
            }
            Graph.store.delete(w.id)
        }
    }

    // --- Groups & communities ---

    fun loadGroups() {
        if (username == null) return
        groups = groups.copy(loading = true, error = null)
        viewModelScope.launch {
            groups = try {
                GroupsState(mine = Graph.api.myGroups(), communities = Graph.api.communities())
            } catch (e: Exception) {
                groups.copy(loading = false, error = e.userMessage())
            }
        }
    }

    fun openGroup(g: Group) {
        openGroup = g
        groupBoard = BoardState()
        loadGroupBoard(Period.WEEK)
    }

    fun closeGroup() {
        openGroup = null
    }

    fun loadGroupBoard(period: Period) {
        val g = openGroup ?: return
        groupBoard = groupBoard.copy(period = period, loading = true, error = null)
        viewModelScope.launch {
            val next = try {
                groupBoard.copy(rows = Graph.api.groupLeaderboard(g.id, period.id, ZoneId.systemDefault().id), loading = false, updatedAt = System.currentTimeMillis())
            } catch (e: Exception) {
                groupBoard.copy(loading = false, error = e.userMessage())
            }
            if (openGroup?.id == g.id && groupBoard.period == period) groupBoard = next
        }
    }

    /** The summary's Delete: removes what this session saved (once the save is done) and closes. */
    fun discard() {
        val done = flow as? WorkoutFlow.Done
        flow = null
        sessionSteps.clear()
        if (done != null) {
            viewModelScope.launch {
                stepSave?.join()
                done.steps.forEach(::deleteWorkout)
            }
        }
    }

    /** [manual] = the user tapped sync, so report the outcome. */
    fun syncNow(manual: Boolean = false) {
        viewModelScope.launch {
            if (!Graph.sync.run()) SyncJob.schedule(getApplication())
            if (manual) {
                when (val s = sync.value) {
                    is SyncState.Done -> _messages.emit("☁️ All synced")
                    is SyncState.Failed -> _messages.emit("⚠️ ${s.message}")
                    else -> Unit
                }
            }
        }
    }

    fun addWater(delta: Int) {
        // Always the real current day, even if the screen still shows yesterday's count.
        val key = waterKey(LocalDate.now())
        water = (Graph.prefs.getInt(key, 0) + delta).coerceIn(0, 20)
        Graph.prefs.edit().putInt(key, water).apply()
    }

    private fun waterKey(day: LocalDate) = "water_$day"

    // --- Settings ---

    fun changeDailyGoal(points: Int) {
        dailyGoal = points
        Graph.prefs.edit().putInt("daily_goal", points).apply()
    }

    fun changeVoice(on: Boolean) {
        voice = on
        Graph.prefs.edit().putBoolean("voice", on).apply()
    }

    fun changeReminder(minuteOfDay: Int?) {
        reminder = minuteOfDay
        Graph.prefs.edit().putInt("reminder_min", minuteOfDay ?: -1).apply()
        Reminders.schedule(getApplication(), minuteOfDay)
    }

    fun changeSleepGoal(minutes: Int) {
        sleepGoal = minutes
        Graph.prefs.edit().putInt("sleep_goal_min", minutes).apply()
    }

    fun changeTheme(mode: ThemeMode) {
        theme = mode
        Graph.prefs.edit().putString("theme", mode.name).apply()
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
            } catch (e: Exception) {
                board.copy(loading = false, error = e.userMessage())
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
                this@AppViewModel.username = Graph.session.username.value
                runCatching { Graph.api.saveProfile(name) } // leaderboard display name; non-fatal
                syncNow()
                loadBoard()
                _messages.emit("Signed in as $name")
                AuthState(recoveryCode = code)
            } catch (e: Exception) {
                AuthState(error = e.userMessage())
            }
        }
    }

    fun dismissRecoveryCode() {
        auth = auth.copy(recoveryCode = null)
    }

    fun signOut() {
        // Reset first: the cleanup below can wait for a slow sync, and a sign-in started meanwhile owns this state.
        auth = AuthState()
        viewModelScope.launch { Graph.sync.signOut() } // the session collector in init resets the account state
    }

    /** Text for a failed request; anything but an [ApiException] is a malformed response or a bug, not something to show raw. */
    private fun Exception.userMessage() = (this as? ApiException)?.message ?: "Something went wrong"

    // region food
    /** Puts back a meal removed by mistake (Undo on the Food tab), with its own id, day and time. */
    fun restoreMeal(m: Meal) {
        viewModelScope.launch { Graph.meals.add(m) }
    }
    // endregion

    // region social

    private var ranksFor: String? = null
    private var ranksAt = 0L

    /** Loads the board and group lists unless this account got them under 2 minutes ago; [force] is the refresh button. */
    fun refreshRanks(force: Boolean = false) {
        val user = username ?: return
        val now = System.currentTimeMillis()
        val fresh = user == ranksFor && now - ranksAt < 2 * 60_000 && board.error == null && groups.error == null
        if (fresh && !force) return
        ranksFor = user
        ranksAt = now
        loadBoard()
        loadGroups()
    }

    /** The group request in flight ("create", "join" or a group id): its button shows busy and no other can start. */
    var groupBusy by mutableStateOf<String?>(null)
        private set

    /** Status line for the open group sheet, since snackbars sit behind sheets. */
    var groupNote by mutableStateOf<String?>(null)
        private set

    fun clearGroupNote() {
        groupNote = null
    }

    /** Creates a group or club, then opens its sheet. */
    fun createGroupAndOpen(name: String, club: Boolean, emoji: String) = groupRequest("create") {
        openGroup(Graph.api.createGroup(name.trim(), if (club) "community" else "group", emoji))
    }

    /** Joins a group by invite code, then opens its sheet. */
    fun joinGroupByCode(code: String) = groupRequest("join") { openGroup(Graph.api.joinGroup(code)) }

    fun joinClub(g: Group) = groupRequest(g.id) {
        Graph.api.joinCommunity(g.id)
        if (openGroup?.id == g.id) {
            groupNote = "${g.emoji} Joined"
            loadGroupBoard(groupBoard.period)
        } else {
            _messages.emit("${g.emoji} Joined ${g.name}")
        }
    }

    /** Runs after the confirm dialog; the sheet closes once the server agrees. */
    fun leaveGroupAndClose(g: Group) = groupRequest(g.id) {
        Graph.api.leaveGroup(g.id)
        closeGroup()
        _messages.emit("Left ${g.name}")
    }

    private fun groupRequest(key: String, block: suspend () -> Unit) {
        if (groupBusy != null || username == null) return
        groupBusy = key
        groupNote = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                val text = if ((e as? ApiException)?.code == 0) "You're offline" else e.userMessage()
                // A sheet answers inside itself; a tap in the list answers in the snackbar.
                if (key == "create" || key == "join" || openGroup?.id == key) groupNote = text else _messages.emit(text)
            }
            // Fresh lists before the button frees up, so a joined club never flashes "Join" again.
            try {
                groups = GroupsState(mine = Graph.api.myGroups(), communities = Graph.api.communities())
            } catch (e: Exception) {
                // Keep the lists as they are; the next refresh tries again.
            }
            groupBusy = null
        }
    }

    /** Hides the sign-in error as soon as the form is edited. */
    fun clearAuthError() {
        if (auth.error != null) auth = auth.copy(error = null)
    }

    /** Daily reminder on (at the last time used, 19:00 the first time) or off. */
    fun setReminderOn(on: Boolean) {
        if (on) {
            changeReminder(Graph.prefs.getInt("reminder_last_min", 19 * 60))
        } else {
            reminder?.let { Graph.prefs.edit().putInt("reminder_last_min", it).apply() }
            changeReminder(null)
        }
    }
    // endregion

    // region sleep

    init {
        armOnLaunch()
    }

    // (Inside functions `app` is the package, not the constructor parameter, so these names resolve.)
    private fun armOnLaunch() {
        val context = getApplication<Application>()
        // Force-stop and OEM "cleaners" delete alarms and the reminder; every launch arms them again.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { app.fitnova.alarm.AlarmScheduler.rearmAll(context) }
            runCatching { Reminders.rearm(context) }
        }
        // While an alarm rings, opening the app goes straight to its challenge.
        app.fitnova.alarm.RingRedirect.install(context)
    }

    fun goToBed() = Graph.sleep.goToBed()

    fun cancelBed() = Graph.sleep.cancelBed()

    fun wakeUp() {
        viewModelScope.launch {
            val night = Graph.sleep.wakeUp("tap")
            _messages.emit(if (night != null) "☀️ Slept ${app.fitnova.domain.hm(night.minutes)}" else "Too short to log")
        }
    }

    /** Adds or edits a night (manual entry); the sheet has already checked it. */
    fun saveSleep(entry: SleepEntry) {
        viewModelScope.launch { Graph.sleep.save(entry) }
    }

    fun deleteSleep(id: String) {
        viewModelScope.launch { Graph.sleep.delete(id) }
    }

    /** Saves [alarm] and arms it with the system (or disarms it when off), then says when it rings. */
    fun saveAlarm(alarm: Alarm) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val at = withContext(Dispatchers.IO) {
                app.fitnova.alarm.AlarmScheduler.cancel(context, alarm.id) // a pending snooze belongs to the old settings
                app.fitnova.alarm.AlarmScheduler.arm(context, alarm).also { Graph.alarms.save(alarm.copy(nextAt = it)) }
            }
            if (!alarm.enabled) return@launch
            val minutes = at?.let { ((it - System.currentTimeMillis() + 59_999) / 60_000).toInt() }
            _messages.emit(if (minutes != null) "⏰ Rings in ${app.fitnova.domain.untilText(minutes)}" else "⚠️ Allow Alarms & reminders so it can ring")
        }
    }

    fun toggleAlarm(alarm: Alarm, on: Boolean) = saveAlarm(alarm.copy(enabled = on))

    fun deleteAlarm(alarm: Alarm) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.fitnova.alarm.AlarmScheduler.cancel(context, alarm.id) }
            Graph.alarms.delete(alarm.id)
        }
    }

    /** Rings [alarm]'s challenge in 5 seconds without saving anything; false while exact alarms aren't allowed. */
    fun testRing(alarm: Alarm): Boolean = app.fitnova.alarm.AlarmScheduler.test(getApplication(), alarm)

    /** Arms what couldn't be armed, e.g. right after exact-alarm access was granted (the system's own broadcast can lag). */
    fun rearmAlarms() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { runCatching { app.fitnova.alarm.AlarmScheduler.rearmAll(context) } }
    }

    // endregion
}
