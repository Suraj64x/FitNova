package app.fitnova.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fitnova.R
import app.fitnova.domain.Exercise
import app.fitnova.domain.Stats
import java.time.LocalDate
import kotlin.math.roundToInt

private val Ranges = listOf("Week", "30 days")

/** Points this week (bars) or over 30 days (line), against the daily goal. */
@Composable
internal fun HomeActivityCard(stats: Stats, today: LocalDate, goal: Int, range: Int, onRange: (Int) -> Unit) {
    val week = range == 0
    val total = remember(stats, week) { (if (week) stats.week else stats.month).sumOf { it.points } }
    Panel {
        CardHeader(R.drawable.emoji_bar_chart, "Activity") { NumberUnit("$total", "pts") }
        Spacer(Modifier.height(12.dp))
        Segmented(Ranges, range, onRange)
        Spacer(Modifier.height(16.dp))
        if (week) {
            val hits = remember(stats, goal) { stats.week.count { it.points >= goal.coerceAtLeast(1) } }
            WeekBars(stats.week, today, goal = goal, contentDescription = "This week $total points, goal hit on $hits days")
        } else {
            val values = remember(stats.month) { stats.month.map { it.points.toFloat() } }
            LineChart(values, Modifier.fillMaxWidth().height(120.dp), goal = goal.toFloat(), contentDescription = "Last 30 days $total points")
            Spacer(Modifier.height(8.dp))
            SpanLabels(stats.month.first().day)
        }
        Spacer(Modifier.height(12.dp))
        GoalKey(goal)
    }
}

/** Average camera-checked form per day over two weeks; days without one are gaps, never bridged. */
@Composable
internal fun HomeFormCard(trend: List<Int?>, today: LocalDate) {
    val values = remember(trend) { trend.map { it?.toFloat() } }
    val average = remember(trend) { trend.filterNotNull().average().roundToInt() }
    Panel {
        CardHeader(R.drawable.emoji_target, "Form") { NumberUnit("$average", "%") }
        Spacer(Modifier.height(16.dp))
        LineChart(
            values,
            Modifier.fillMaxWidth().height(96.dp),
            color = Nova.colors.good,
            max = 100f,
            contentDescription = "Form over two weeks, average $average percent",
        )
        Spacer(Modifier.height(8.dp))
        SpanLabels(today.minusDays(trend.size - 1L))
    }
}

/** Points per move over 30 days, each bar in its category colour. */
@Composable
internal fun HomeMixCard(mix: List<Pair<Exercise, Int>>) {
    val max = remember(mix) { mix.maxOf { it.second }.coerceAtLeast(1) }
    val c = Nova.colors
    Panel {
        CardHeader(R.drawable.emoji_chart, "Mix") {
            Text("30 days", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        mix.forEach { (ex, pts) ->
            Row(Modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                ExerciseTile(ex, 40)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row {
                        Text(ex.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("$pts pts", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(6.dp))
                    Meter(pts.toFloat() / max, c.of(ex.category), Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Card title row: emoji art, title, and a trailing number or label. */
@Composable
private fun CardHeader(@DrawableRes emoji: Int, title: String, trailing: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Emoji(emoji, 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing()
    }
}

/** A number with its small unit on the same baseline. */
@Composable
private fun RowScope.NumberUnit(number: String, unit: String) {
    Text(number, style = MaterialTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.alignByBaseline())
    Text(
        unit,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.alignByBaseline().padding(start = 4.dp),
    )
}

/** The first day and "Today" under a line chart. Rows mirror in RTL, and so do the charts. */
@Composable
private fun SpanLabels(from: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    val first = remember(from, locale) { from.format(localizedPattern(locale, "dMMM")) }
    Row(Modifier.fillMaxWidth()) {
        Text(first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Legend for the charts' dashed goal line, drawn the same way (8/6 dp dashes). */
@Composable
private fun GoalKey(goal: Int) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier.size(width = 22.dp, height = 2.dp).drawWithCache {
                val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                onDrawBehind { drawLine(color, Offset(0f, center.y), Offset(size.width, center.y), size.height, pathEffect = dash) }
            },
        )
        Spacer(Modifier.width(8.dp))
        Text("Goal $goal pts", style = MaterialTheme.typography.labelSmall, color = color)
    }
}
