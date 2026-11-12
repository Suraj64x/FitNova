package app.fitnova.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import app.fitnova.Graph
import app.fitnova.domain.Workout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Done(val at: Long) : SyncState
    data class Failed(val message: String, val offline: Boolean) : SyncState
}

class Sync(private val store: WorkoutStore, private val api: Api, private val session: SessionStore) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state = _state.asStateFlow()
    private val lock = Mutex()

    /**
     * Uploads pending workouts, then pulls the account's history and drops rows deleted on another device.
     * Returns false when it should be retried later.
     * Runs on IO (JSON, Keystore and disk work) and never throws, except to cancel.
     */
    suspend fun run(): Boolean = withContext(Dispatchers.IO) { lock.withLock { runLocked() } }

    /**
     * Ends the session at once (the UI and any new sync see a guest), then drops the account's rows only after a running
     * sync is done, so a pull already on the wire can't put them back. Not cancellable: leaving the app mid-wait must not skip it.
     */
    suspend fun signOut() = withContext(NonCancellable) {
        session.clear()
        lock.withLock { store.clearSynced() }
    }

    private suspend fun runLocked(): Boolean {
        if (!session.signedIn) {
            _state.value = SyncState.Idle
            return true
        }
        _state.value = SyncState.Running
        return try {
            if (store.all.value.isEmpty()) store.load() // the process may have been started just for this job
            val pending = store.pending()
            if (pending.isNotEmpty()) {
                api.pushWorkouts(pending)
                store.markSynced(pending.map { it.id })
            }
            // Write only new or changed rows: rewriting the whole history every time is slow on cheap storage.
            val known = store.all.value.associateBy { it.id }
            val (pulled, complete) = api.pullWorkouts()
            store.save(pulled.filter { known[it.id] != it })
            store.deleteSynced(deletedRemotely(known.values, pulled, complete))
            _state.value = SyncState.Done(System.currentTimeMillis())
            true
        } catch (e: CancellationException) {
            _state.value = SyncState.Idle
            throw e
        } catch (e: ApiException) {
            _state.value = SyncState.Failed(e.message ?: "Sync failed", offline = e.code == 0)
            // Retry only what time can fix: no network or a server error. 4xx needs the user.
            !(e.code == 0 || e.code >= 500)
        } catch (e: Exception) {
            // A malformed response or a disk error: retrying in the background won't fix it; the next resume tries again.
            _state.value = SyncState.Failed("Sync failed", offline = false)
            true
        }
    }
}

/**
 * Ids of [local] synced workouts the server no longer returns, i.e. deleted on another device. Unsynced rows are never
 * picked. Unless [pulled] is the [complete] history, it only covers rows newer than its oldest one (a tie at that edge
 * may sit just past the page cut), so older history stays.
 */
fun deletedRemotely(local: Collection<Workout>, pulled: List<Workout>, complete: Boolean): List<String> {
    // A full page with nothing this version can read gives no window to compare against.
    val floor = if (complete) Long.MIN_VALUE else pulled.minOfOrNull { it.startedAt } ?: return emptyList()
    val ids = pulled.mapTo(HashSet()) { it.id }
    return local.filter { it.synced && it.id !in ids && it.startedAt > floor }.map { it.id }
}

/** Background retry: Android runs this once there is a network, even if the app is closed. */
class SyncJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch { jobFinished(params, !Graph.sync.run()) }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 1001

        fun schedule(context: Context) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, SyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
        }
    }
}
