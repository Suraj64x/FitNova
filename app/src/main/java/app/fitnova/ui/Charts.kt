package app.fitnova.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitnova.domain.DayTotal
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle
import java.util.Locale

// All charts are single Canvas draws: flat fills, no gradients or glow, cheap to redraw on low-end GPUs.

/** Seven bars, Monday to Sunday, with today in the accent colour and point labels above non-empty days. */
@Composable
fun WeekBars(week: List<DayTotal>, today: LocalDate, modifier: Modifier = Modifier, goal: Int? = null) {
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val past = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val max = maxOf(week.maxOfOrNull { it.points } ?: 0, goal ?: 0).coerceAtLeast(1)
    val grow by animateFloatAsState(1f, tween(500), label = "bars")
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            week.forEach { d ->
                Text(
                    if (d.points > 0) "${d.points}" else "",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val gap = 12.dp.toPx()
            val barW = (size.width - gap * 6) / 7
            val radius = CornerRadius(5.dp.toPx())
            week.forEachIndexed { i, d ->
                val h = if (d.points == 0) 4.dp.toPx() else (size.height * d.points / max * grow).coerceAtLeast(8.dp.toPx())
                val color = when {
                    goal != null && d.points >= goal -> Tone.good
                    d.day == today -> Tone.accent
                    d.points == 0 -> idle
                    else -> past
                }
                drawRoundRect(color, Offset(i * (barW + gap), size.height - h), Size(barW, h), radius)
            }
            if (goal != null && goal > 0) dashedGoal(size.height - size.height * goal / max * grow, goalColor)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            week.forEach { d ->
                Text(
                    d.day.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault()).take(3),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d.day == today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Line chart with a flat 10% area fill. Null values are gaps (e.g. days without tracked workouts). */
@Composable
fun LineChart(values: List<Float?>, modifier: Modifier = Modifier, color: Color = Tone.accent, max: Float? = null, goal: Float? = null) {
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Canvas(modifier) {
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        if (values.isEmpty()) return@Canvas
        val top = maxOf(max ?: values.filterNotNull().maxOrNull() ?: 0f, goal ?: 0f).coerceAtLeast(1f)
        if (goal != null && goal > 0f) dashedGoal(size.height - goal / top * size.height * 0.9f, goalColor)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val points = values.mapIndexedNotNull { i, v -> v?.let { Offset(i * stepX, size.height - (it / top).coerceIn(0f, 1f) * size.height * 0.9f) } }
        if (points.isEmpty()) return@Canvas
        if (points.size > 1) {
            val line = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            drawPath(area, color.copy(alpha = 0.10f))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawCircle(Color.White, 5.dp.toPx(), points.last())
        drawCircle(color, 3.5.dp.toPx(), points.last())
    }
}

/** Progress ring with optional centre content. Animates when [progress] changes. */
@Composable
fun Ring(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Tone.accent,
    stroke: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val topLeft = Offset(w / 2, w / 2)
            val arc = Size(size.width - w, size.height - w)
            drawArc(track, -90f, 360f, false, topLeft, arc, style = Stroke(w))
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Calories split by macro (protein and carbs 4 kcal/g, fat 9 kcal/g), with small gaps between segments. */
@Composable
fun MacroDonut(
    protein: Int,
    carbs: Int,
    fat: Int,
    modifier: Modifier = Modifier,
    stroke: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val parts = listOf(protein * 4f to Macro.protein, carbs * 4f to Macro.carbs, fat * 9f to Macro.fat)
    val total = parts.sumOf { it.first.toDouble() }.toFloat()
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val topLeft = Offset(w / 2, w / 2)
            val arc = Size(size.width - w, size.height - w)
            if (total <= 0f) {
                drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
                return@Canvas
            }
            var start = -90f
            for ((kcal, color) in parts) {
                val sweep = 360f * kcal / total
                if (sweep > 6f) drawArc(color, start + 2f, sweep - 4f, false, topLeft, arc, style = Stroke(w))
                start += sweep
            }
        }
        content()
    }
}

@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "meter")
    Box(modifier.height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/** Concentric progress rings (outermost first), each animating independently. */
@Composable
fun Rings(
    rings: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    stroke: Dp = 11.dp,
    gap: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated = rings.map { (p, _) -> animateFloatAsState(p.coerceIn(0f, 1f), tween(800), label = "rings").value }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
        val w = stroke.toPx()
        val step = w + gap.toPx()
        rings.forEachIndexed { i, (_, color) ->
            val inset = w / 2 + i * step
            val topLeft = Offset(inset, inset)
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            if (arc.width <= 0f) return@forEachIndexed
            drawArc(color.copy(alpha = 0.13f).compositeOver(track), -90f, 360f, false, topLeft, arc, style = Stroke(w))
            if (animated[i] > 0f) drawArc(color, -90f, 360f * animated[i], false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
    content()
    }
}

private fun DrawScope.dashedGoal(y: Float, color: Color) {
    drawLine(
        color, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(),
        cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
    )
}
