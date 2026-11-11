package app.fitnova

import app.fitnova.data.Rank
import app.fitnova.domain.Exercise
import app.fitnova.domain.Workout
import app.fitnova.domain.badges
import app.fitnova.domain.stats
import app.fitnova.ui.BOARD_LIMIT
import app.fitnova.ui.boardStanding
import app.fitnova.ui.inviteCode
import app.fitnova.ui.inviteText
import app.fitnova.ui.isInviteCode
import app.fitnova.ui.licenceParagraphs
import app.fitnova.ui.podium
import app.fitnova.ui.reminderTime
import app.fitnova.ui.sleepLength
import app.fitnova.ui.updatedAgo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class EarlyBirdTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 2)

    private fun at(hour: Int, minute: Int = 0) =
        Workout("w-$hour-$minute", Exercise.SQUAT, 10, 60, 80, true, day.atTime(hour, minute).toInstant(zone).toEpochMilli())

    private fun early(vararg list: Workout) = badges(list.toList(), zone).first { it.id == "early" }.earned

    @Test fun earlyBirdIsFourToSeven() {
        assertFalse(early(at(3, 59)))
        assertTrue(early(at(4, 0)))
        assertTrue(early(at(6, 59)))
        assertFalse(early(at(7, 0)))
    }

    @Test fun aSmallHoursWorkoutIsNotEarly() {
        assertFalse(early(at(0, 30), at(1, 0), at(2, 45)))
    }

    @Test fun precomputedStatsGiveTheSameBadges() {
        val list = listOf(at(5), at(23), at(12).copy(id = "x", reps = 120))
        assertEquals(badges(list, zone), badges(list, zone, stats(list, zone = zone)))
    }

    @Test fun badgeTextUsesAndroid8SafeEmoji() {
        // 🧱 (Emoji 11) showed as a box on Android 8 without Play services.
        assertTrue(badges(emptyList(), zone).none { it.emoji == "🧱" })
        assertTrue(badges(emptyList(), zone).all { it.hint.isNotBlank() })
    }
}

class RanksLogicTest {
    @Test fun findsTheCodeInAnInviteMessage() {
        assertEquals("K7P2QX", inviteCode(inviteText("Squads", "K7P2QX"))) // a 6-letter name and "LATEST" in the link
        assertEquals("K7P2QX", inviteCode(inviteText("Code Crew", "K7P2QX")))
        assertEquals("ABC234", inviteCode("Join my Fit Nova group \"Morning\" with code ABC234")) // 1.4 read this as JOINMY
        assertEquals("K7P2QX", inviteCode("K7P2QX is the code"))
        assertEquals("HJKMNP", inviteCode("code for group Sweaty: HJKMNP"))
    }

    @Test fun acceptsATypedCode() {
        assertEquals("K7P2QX", inviteCode(" k7p-2qx "))
        assertEquals("K7P2QX", inviteCode("K7P 2QX"))
    }

    @Test fun rejectsWhatCannotBeACode() {
        assertNull(inviteCode("hello there"))
        assertNull(inviteCode("K7P2Q"))
        assertNull(inviteCode("K7P2Q0")) // codes never use 0, 1, I or O
        assertFalse(isInviteCode("ABCDEI"))
        assertTrue(isInviteCode("ABC234"))
    }

    private fun r(id: String, points: Int, rank: Int) = Rank(id, id, points, 1, rank)

    @Test fun standingShowsTheGapToTheNextPlace() {
        val rows = listOf(r("a", 300, 1), r("b", 200, 2), r("c", 200, 2), r("d", 150, 4))
        boardStanding(rows, "d").let {
            assertEquals("d", it.mine?.userId)
            assertEquals(50, it.ahead!!.points - it.mine!!.points)
            assertEquals(2, it.ahead!!.rank)
        }
        assertEquals("a", boardStanding(rows, "c").ahead?.userId) // a tie isn't a place to climb to
        assertNull(boardStanding(rows, "a").ahead)
        boardStanding(rows, "z").let {
            assertNull(it.mine)
            assertFalse(it.full)
        }
        assertTrue(boardStanding(List(BOARD_LIMIT) { r("u$it", 1000 - it, it + 1) }, "z").full)
    }

    @Test fun podiumGoesByRankSoTiesShareAMedal() {
        val spots = podium(listOf(r("a", 90, 1), r("b", 90, 1), r("c", 50, 3), r("d", 10, 4)))
        assertEquals(listOf("b", "a", "c"), spots.map { it.first.userId }) // 2-1-3 layout
        assertEquals(listOf(0, 0, 2), spots.map { it.second }) // gold, gold, bronze
        assertEquals(listOf(1, 0, 1), podium(listOf(r("a", 90, 1), r("b", 80, 2), r("c", 80, 2))).map { it.second })
        assertTrue(podium(listOf(r("a", 90, 1), r("b", 80, 2))).isEmpty())
    }

    @Test fun updatedLabels() {
        val now = 10_000_000_000L
        assertEquals("just now", updatedAgo(now - 30_000, now))
        assertEquals("5m ago", updatedAgo(now - 5 * 60_000, now))
        assertEquals("3h ago", updatedAgo(now - 3 * 3_600_000, now))
        assertEquals("2d ago", updatedAgo(now - 49 * 3_600_000, now))
        assertEquals("just now", updatedAgo(now + 60_000, now)) // clock moved back
    }
}

class ProfileLogicTest {
    @Test fun reminderTimeFollowsTheClockSetting() {
        assertEquals("19:00", reminderTime(19 * 60, is24 = true))
        assertEquals("07:05", reminderTime(7 * 60 + 5, is24 = true))
        assertEquals("7:00 PM", reminderTime(19 * 60, is24 = false))
        assertEquals("12:30 AM", reminderTime(30, is24 = false))
        assertEquals("12:00 PM", reminderTime(12 * 60, is24 = false))
    }

    @Test fun sleepLengths() {
        assertEquals("8h", sleepLength(480))
        assertEquals("7h 45m", sleepLength(465))
    }

    @Test fun licencesReflowProseButKeepListsAndHeadings() {
        val text = """
            Intro line.

            - First item (MIT) - icons
            - Second item, a longer one that is still a list line - ok

            ==============================================================
            Phosphor Icons
            https://github.com/phosphor-icons/core
            ==============================================================
            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files (the "Software"), to deal
            in the Software.
        """.trimIndent()
        val p = licenceParagraphs(text)
        assertEquals(4, p.size)
        assertEquals("- First item (MIT) - icons\n- Second item, a longer one that is still a list line - ok", p[1].first)
        assertEquals("Phosphor Icons\nhttps://github.com/phosphor-icons/core" to true, p[2])
        assertFalse(p[3].second)
        assertFalse('\n' in p[3].first)
        assertTrue(p[3].first.startsWith("Permission is hereby granted, free of charge, to any person obtaining a copy of this"))
    }
}
