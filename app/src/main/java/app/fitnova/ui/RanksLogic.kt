package app.fitnova.ui

import app.fitnova.data.Rank

// Pure helpers behind Ranks and Profile, free of Android so unit tests can run them.

/** Server boards stop at this many rows (fn_leaderboard, fn_group_leaderboard). */
internal const val BOARD_LIMIT = 100

/** Where the app is downloaded; part of the invite text. */
internal const val APP_LINK = "https://github.com/Suraj64x/FitNova/releases/latest"

// fn_new_invite_code draws from this alphabet: no I, O, 0 or 1.
private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private val NOT_CODE = Regex("[^A-Z0-9]+")

internal fun isInviteCode(s: String) = s.length == 6 && s.all { it in CODE_CHARS }

/**
 * Finds the invite code in whatever was pasted: the code alone ("k7p-2qx") or a whole invite message.
 * In a message the token right after the word "code" wins, else the last code-shaped token.
 */
internal fun inviteCode(text: String): String? {
    val upper = text.uppercase()
    val compact = upper.filter { it.isLetterOrDigit() }
    if (isInviteCode(compact)) return compact
    val tokens = upper.split(NOT_CODE).filter { it.isNotEmpty() }
    val at = tokens.lastIndexOf("CODE")
    if (at >= 0) tokens.getOrNull(at + 1)?.let { if (isInviteCode(it)) return it }
    return tokens.lastOrNull(::isInviteCode)
}

/** What Share sends. [inviteCode] reads the code back out of it. */
internal fun inviteText(name: String, code: String) = "Join my Fit Nova group \"$name\"\nCode: $code\nGet the app: $APP_LINK"

/** The signed-in user's row ([mine]), the closest row above it ([ahead], null at the top) and whether the board is cut off. */
internal class Standing(val mine: Rank?, val ahead: Rank?, val full: Boolean)

internal fun boardStanding(rows: List<Rank>, me: String?): Standing {
    val mine = rows.firstOrNull { it.userId == me }
    // Rows come sorted by points, so the last one with more points is the next place up.
    val ahead = mine?.let { m -> rows.lastOrNull { it.points > m.points } }
    return Standing(mine, ahead, rows.size >= BOARD_LIMIT)
}

/**
 * The top three in podium order (2nd, 1st, 3rd), each with its medal: 0 gold, 1 silver, 2 bronze.
 * Medals follow the rank, not the position, so tied places share a colour. Empty below three rows.
 */
internal fun podium(rows: List<Rank>): List<Pair<Rank, Int>> =
    if (rows.size < 3) emptyList() else listOf(rows[1], rows[0], rows[2]).map { it to (it.rank - 1).coerceIn(0, 2) }

/** "just now", "5m ago", "3h ago", "2d ago". */
internal fun updatedAgo(at: Long, now: Long): String {
    val minutes = ((now - at) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

internal fun workoutCount(n: Int) = if (n == 1) "1 workout" else "$n workouts"

internal fun memberCount(n: Int) = if (n == 1) "1 member" else "$n members"
