package app.fitnova.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import app.fitnova.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** Uploads pending workouts, then pulls the account's history. Returns false when it should be retried later. */
    suspend fun run(): Boolean = lock.withLock {
        if (!session.signedIn) {
            _state.value = SyncState.Idle
            return true
        }
        _state.value = SyncState.Running
        try {
            store.load() // the process may have been started just for this job
            val pending = store.pending()
            if (pending.isNotEmpty()) {
                api.pushWorkouts(pending)
                store.markSynced(pending.map { it.id })
            }
            store.save(api.pullWorkouts())
            _state.value = SyncState.Done(System.currentTimeMillis())
            true
        } catch (e: ApiException) {
            _state.value = SyncState.Failed(e.message ?: "Sync failed", offline = e.code == 0)
            // Retry only what time can fix: no network or a server error. 4xx needs the user.
            !(e.code == 0 || e.code >= 500)
        }
    }
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
