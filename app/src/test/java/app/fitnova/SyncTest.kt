package app.fitnova

import app.fitnova.data.deletedRemotely
import app.fitnova.domain.Exercise
import app.fitnova.domain.Workout
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncTest {
    private fun w(id: String, at: Long, synced: Boolean = true) = Workout(id, Exercise.SQUAT, 10, 0, 80, true, at, synced)

    @Test fun theWholeHistoryDropsSyncedRowsTheServerNoLongerHas() {
        val local = listOf(w("a", 3), w("b", 2), w("c", 1))
        assertEquals(listOf("b"), deletedRemotely(local, listOf(w("a", 3), w("c", 1)), complete = true))
    }

    @Test fun unsyncedRowsAreNeverDeleted() {
        val local = listOf(w("new", 5, synced = false), w("old", 1, synced = false))
        assertEquals(emptyList<String>(), deletedRemotely(local, listOf(w("x", 3)), complete = true))
        assertEquals(emptyList<String>(), deletedRemotely(local, listOf(w("x", 3)), complete = false))
    }

    @Test fun aFullPageOnlyCoversRowsNewerThanItsOldest() {
        // The page ends at t = 10: older rows, and ties at 10 that may sit past the cut, are not in it.
        val local = listOf(w("kept", 30), w("gone", 20), w("edge", 10), w("tie", 10), w("older", 5))
        assertEquals(listOf("gone"), deletedRemotely(local, listOf(w("kept", 30), w("edge", 10)), complete = false))
    }

    @Test fun aFullPageWithNothingReadableDeletesNothing() {
        // 1,000 rows came back, all with exercises this version doesn't know: no window to compare against.
        assertEquals(emptyList<String>(), deletedRemotely(listOf(w("a", 1)), emptyList(), complete = false))
    }

    @Test fun anEmptyAccountDropsEverySyncedRowButKeepsUnsynced() {
        val local = listOf(w("a", 2), w("b", 1, synced = false))
        assertEquals(listOf("a"), deletedRemotely(local, emptyList(), complete = true))
    }
}
