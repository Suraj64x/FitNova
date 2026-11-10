package app.fitnova

import app.fitnova.ui.CueGate
import app.fitnova.ui.holdLine
import app.fitnova.ui.repLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTest {
    @Test
    fun repsAreCountedOneByOneUpTo20ThenEvery5() {
        val said = (1..40).associateWith { repLine(it, 30) }.filterValues { it != null }
        assertEquals((1..20).toList() + listOf(25, 30, 35, 40), said.keys.toList())
        assertEquals("15. Halfway", said[15])
        assertEquals("30. Target reached", said[30])
        assertEquals("12", repLine(12, 30))
        assertNull(repLine(0, 30))
        // Small targets get no halfway call.
        assertEquals("4", repLine(4, 8))
        assertEquals("8. Target reached", repLine(8, 8))
        // A +5 tap that jumps over the target or halfway still calls it; Undo says nothing.
        assertEquals("23. Target reached", repLine(23, 20, from = 18))
        assertEquals("13. Halfway", repLine(13, 20, from = 8))
        assertEquals("25", repLine(25, 30, from = 20))
        assertNull(repLine(12, 30, from = 13))
    }

    @Test
    fun holdsCallTensHalfwayLastThreeAndTarget() {
        val said = (1..50).mapNotNull { s -> holdLine(s, 45)?.let { s to it } }.toMap()
        assertEquals(
            mapOf(10 to "10 seconds", 20 to "20 seconds", 22 to "Halfway", 30 to "30 seconds", 40 to "40 seconds", 42 to "3", 43 to "2", 44 to "1", 45 to "Target reached", 50 to "50 seconds"),
            said,
        )
    }

    @Test
    fun cuesWaitThenThrottle() {
        val gate = CueGate(after = 1500, gap = 4000)
        assertNull(gate.update("Hips up", 0))
        assertNull(gate.update("Hips up", 1400)) // not long enough yet
        assertEquals("Hips up", gate.update("Hips up", 1500))
        assertNull(gate.update("Hips up", 3000)) // throttled
        assertNull(gate.update(null, 3100)) // form fixed: the timer resets
        assertNull(gate.update("Chest up", 3200))
        assertNull(gate.update("Chest up", 4800)) // lasted 1.6 s but only 3.3 s since the last line
        assertEquals("Chest up", gate.update("Chest up", 5600))
    }
}
