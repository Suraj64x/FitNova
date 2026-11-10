package app.fitnova

import app.fitnova.ui.showPaced
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MessagesTest {
    @Test fun newerMessageWaitsForTheMinimumThenReplaces() = runBlocking {
        val queue = Channel<String>(Channel.UNLIMITED)
        val log = mutableListOf<String>()
        val job = launch {
            showPaced(queue, minMs = 200) { m ->
                log += "show $m"
                try {
                    delay(1_000)
                } finally {
                    log += "hide $m"
                }
            }
        }
        queue.send("saved")
        queue.send("badge")
        delay(100)
        assertEquals(listOf("show saved"), log) // the badge waits until "saved" has been read
        delay(300)
        assertEquals(listOf("show saved", "hide saved", "show badge"), log) // then replaces it at once
        job.cancel()
    }

    @Test fun aLoneMessageRunsItsFullTime() = runBlocking {
        val queue = Channel<String>(Channel.UNLIMITED)
        val log = mutableListOf<String>()
        val job = launch {
            showPaced(queue, minMs = 50) { m ->
                log += "show $m"
                delay(200)
                log += "done $m"
            }
        }
        queue.send("synced")
        delay(150)
        assertEquals(listOf("show synced"), log) // past the minimum, but nothing is waiting
        delay(150)
        assertEquals(listOf("show synced", "done synced"), log)
        queue.send("next")
        delay(50)
        assertEquals("show next", log.last())
        job.cancel()
    }
}
