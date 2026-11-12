package app.fitnova.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import app.fitnova.domain.DayTotal
import java.time.LocalDate
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.time.format.TextStyle as DayStyle

// Every chart is one flat draw: no gradients or glow. Animated values are read inside the draw block and all
// Paths, Strokes and PathEffects come from drawWithCache, so an animation frame only redraws and allocates nothing.

private const val ARC_START = 135f // 270° open arc with its gap centred at the bottom
private const val ARC_SWEEP = 270f

/** One-shot fill from 0 on first show and a tween on every change; read `.value` only while drawing. */
@Composable
private fun animatedFractions(targets: List<Float>, durationMs: Int): List<Animatable<Float, AnimationVector1D>> {
    val anims = remember(targets.size) { List(targets.size) { Animatable(0f) } }
    targets.forEachIndexed { i, target ->
        LaunchedEffect(anims, target) { anims[i].animateTo(target.coerceIn(0f, 1f), tween(durationMs, easing = FastOutSlowInEasing)) }
    }
    return anims
}

private fun Modifier.describe(text: String?) = if (text != null) clearAndSetSemantics { contentDescription = text } else this

/**
 * Concentric 270° open arcs, outermost first, each a fraction of its goal. The neutral track shows the goal
 * and a Nova dot rides the head of each arc. [size] may be Dp.Unspecified when [modifier] sizes the chart.
 */
@Composable
fun Arcs(
    values: List<Pair<Float, Color>>,
    size: Dp = 160.dp,
    stroke: Dp = 12.dp,
    gap: Dp = 4.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val anims = animatedFractions(values.map { it.first }, 600)
    val track = Nova.colors.track
    val dot = MaterialTheme.colorScheme.surfaceContainer
    Box(
        modifier.then(if (size.isSpecified) Modifier.size(size) else Modifier).describe(contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val w = stroke.toPx()
                val step = w + gap.toPx()
                val box = this.size // the `size` parameter shadows the draw size here
                val side = box.minDimension
                val origin = Offset((box.width - side) / 2f, (box.height - side) / 2f)
                val line = Stroke(w, cap = StrokeCap.Round)
                onDrawBehind {
                    for (i in values.indices) {
                        val inset = w / 2f + i * step
                        val diameter = side - inset * 2f
                        if (diameter <= 0f) break
                        val topLeft = Offset(origin.x + inset, origin.y + inset)
                        val arc = Size(diameter, diameter)
                        drawArc(track, ARC_START, ARC_SWEEP, false, topLeft, arc, style = line)
                        val p = anims[i].value
                        if (p <= 0f) continue
                        val color = values[i].second
                        drawArc(color, ARC_START, ARC_SWEEP * p, false, topLeft, arc, style = line)
                        // Nova dot: a surface-coloured centre inside the round cap, so the head reads as a ringed dot.
                        val angle = (ARC_START + ARC_SWEEP * p) * (PI / 180.0)
                        val r = diameter / 2f
                        val head = Offset(topLeft.x + r + r * cos(angle).toFloat(), topLeft.y + r + r * sin(angle).toFloat())
                        drawCircle(dot, w / 4f, head)
                    }
                }
            },
        )
        content()
    }
}

/** A single 270° open arc (e.g. kcal or sleep vs goal). */
@Composable
fun Arc(
    progress: Float,
    color: Color,
    size: Dp = 160.dp,
    stroke: Dp = 12.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) = Arcs(listOf(progress to color), size, stroke, 0.dp, modifier, contentDescription, content)

/** 1.4 name for [Arcs]; size it with [modifier]. */
@Composable
fun Rings(
    rings: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    stroke: Dp = 12.dp,
    gap: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit = {},
) = Arcs(rings, Dp.Unspecified, stroke, gap, modifier, content = content)

/** 1.4 name for [Arc]; size it with [modifier]. */
@Composable
fun Ring(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Nova.colors.points,
    stroke: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit = {},
) = Arc(progress, color, Dp.Unspecified, stroke, modifier, content = content)

/**
 * Seven bars, Monday to Sunday, with values above them. Goal-hit days are green, today is [color], past days
 * grey and empty days a short track stub; a Nova dot marks today. Bars grow from zero on first show.
 */
@Composable
fun WeekBars(
    week: List<DayTotal>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    goal: Int? = null,
    color: Color = Nova.colors.points,
    format: (Int) -> String = { it.toString() },
    contentDescription: String? = null,
) {
    val c = Nova.colors
    val colors = MaterialTheme.colorScheme
    // Past days in a soft tint of the series colour (values are labelled above), today in full colour.
    val past = color.copy(alpha = 0.45f)
    val nova = colors.primary
    val max = maxOf(week.maxOfOrNull { it.points } ?: 0, goal ?: 0).coerceAtLeast(1)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
    Column(modifier.describe(contentDescription)) {
        Row(Modifier.fillMaxWidth()) {
            week.forEach { d ->
                Text(
                    if (d.points > 0) format(d.points) else "",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(
            Modifier.fillMaxWidth().height(122.dp).drawWithCache {
                val n = week.size.coerceAtLeast(1)
                val dotRow = 12.dp.toPx()
                val chartH = size.height - dotRow
                // Each bar is centred in the same 1/n cell as its labels, so they line up exactly.
                val slot = size.width / n
                val gapX = 12.dp.toPx()
                val barW = slot - gapX
                val radius = CornerRadius(5.dp.toPx())
                val stub = 4.dp.toPx()
                val minBar = 8.dp.toPx()
                val goalY = if (goal != null && goal > 0) chartH - chartH * goal / max else -1f
                val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                val goalWidth = 1.5.dp.toPx()
                val dotR = 2.dp.toPx()
                onDrawBehind {
                    val g = grow.value
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    for (i in week.indices) {
                        val d = week[i]
                        val x = (if (rtl) n - 1 - i else i) * slot + gapX / 2f
                        val hit = goal != null && d.points >= goal && d.points > 0
                        val h = if (d.points == 0) stub else (chartH * d.points / max * g).coerceAtLeast(minBar)
                        val fill = when {
                            d.points == 0 -> c.track
                            hit -> c.good
                            d.day == today -> color
                            else -> past
                        }
                        drawRoundRect(fill, Offset(x, chartH - h), Size(barW, h), radius)
                        if (d.day == today) drawCircle(nova, dotR, Offset(x + barW / 2f, chartH + dotRow / 2f))
                    }
                    if (goalY >= 0f) {
                        drawLine(past, Offset(0f, goalY), Offset(size.width, goalY), goalWidth, StrokeCap.Round, dash)
                    }
                }
            },
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            week.forEach { d ->
                val isToday = d.day == today
                Text(
                    d.day.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault()).take(3),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (isToday) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Line chart with a flat 10 % area. Null values are gaps: the line breaks there and is never bridged,
 * and a lone value between gaps shows as a dot. The last value gets a halo in the card colour.
 */
@Composable
fun LineChart(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    color: Color = Nova.colors.points,
    max: Float? = null,
    goal: Float? = null,
    contentDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val grid = colors.outlineVariant
    val goalColor = colors.onSurfaceVariant
    val halo = colors.surfaceContainer
    Spacer(
        modifier.describe(contentDescription).drawWithCache {
            val rtl = layoutDirection == LayoutDirection.Rtl
            val top = maxOf(max ?: values.maxOfOrNull { it ?: 0f } ?: 0f, goal ?: 0f).coerceAtLeast(1f)
            val plotH = size.height * 0.9f
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            fun x(i: Int) = if (rtl) size.width - i * stepX else i * stepX
            fun y(v: Float) = size.height - (v / top).coerceIn(0f, 1f) * plotH

            val line = Path()
            val area = Path()
            val dots = Path()
            val dotR = 3.dp.toPx()
            var runStart = -1
            var lastIndex = -1
            fun endRun(end: Int) {
                if (runStart < 0) return
                if (end == runStart) {
                    dots.addOval(Rect(Offset(x(end), y(values[end]!!)), dotR))
                } else {
                    area.lineTo(x(end), size.height)
                    area.lineTo(x(runStart), size.height)
                    area.close()
                }
                runStart = -1
            }
            for (i in values.indices) {
                val v = values[i]
                if (v == null) {
                    endRun(i - 1)
                    continue
                }
                if (runStart < 0) {
                    runStart = i
                    line.moveTo(x(i), y(v))
                    area.moveTo(x(i), y(v))
                } else {
                    line.lineTo(x(i), y(v))
                    area.lineTo(x(i), y(v))
                }
                lastIndex = i
            }
            endRun(values.size - 1)

            val gridWidth = 1.dp.toPx()
            val stroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val goalY = if (goal != null && goal > 0f) y(goal) else -1f
            val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
            val goalWidth = 1.5.dp.toPx()
            val hasLast = lastIndex >= 0
            val last = if (hasLast) Offset(x(lastIndex), y(values[lastIndex]!!)) else Offset.Zero
            val fillColor = color.copy(alpha = 0.10f)
            onDrawBehind {
                for (i in 0..3) {
                    val gy = size.height * i / 3f
                    drawLine(grid, Offset(0f, gy), Offset(size.width, gy), gridWidth)
                }
                if (goalY >= 0f) drawLine(goalColor, Offset(0f, goalY), Offset(size.width, goalY), goalWidth, StrokeCap.Round, dash)
                drawPath(area, fillColor)
                drawPath(line, color, style = stroke)
                drawPath(dots, color)
                if (hasLast) {
                    drawCircle(halo, 5.dp.toPx(), last)
                    drawCircle(color, 3.5.dp.toPx(), last)
                }
            }
        },
    )
}

/** Calories split by macro (protein and carbs 4 kcal/g, fat 9 kcal/g) in the Nova macro colours, with 2 dp gaps. */
@Composable
fun MacroDonut(
    protein: Int,
    carbs: Int,
    fat: Int,
    modifier: Modifier = Modifier,
    stroke: Dp = 8.dp,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val c = Nova.colors
    val parts = floatArrayOf(protein * 4f, carbs * 4f, fat * 9f)
    val partColors = arrayOf(c.protein, c.carbs, c.fat)
    val total = parts.sum()
    val track = c.track
    Box(modifier.describe(contentDescription), contentAlignment = Alignment.Center) {
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val w = stroke.toPx()
                val side = size.minDimension - w
                val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
                val arc = Size(side, side)
                val ring = Stroke(w)
                val gapDeg = (2.dp.toPx() / (PI.toFloat() * side)) * 360f
                onDrawBehind {
                    if (total <= 0f) {
                        drawArc(track, 0f, 360f, false, topLeft, arc, style = ring)
                        return@onDrawBehind
                    }
                    var start = -90f
                    for (i in parts.indices) {
                        val sweep = 360f * parts[i] / total
                        if (sweep > gapDeg * 2f) drawArc(partColors[i], start + gapDeg / 2f, sweep - gapDeg, false, topLeft, arc, style = ring)
                        start += sweep
                    }
                }
            },
        )
        content()
    }
}

/** 8 dp progress pill on the neutral track; the fill animates in the draw phase. */
@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val anim = animatedFractions(listOf(fraction), 600)[0]
    val track = Nova.colors.track
    Spacer(
        modifier.height(8.dp).progressSemantics(fraction.coerceIn(0f, 1f)).drawWithCache {
            val radius = CornerRadius(size.height / 2f)
            onDrawBehind {
                drawRoundRect(track, cornerRadius = radius)
                val w = size.width * anim.value
                if (w > 0f) {
                    val fill = w.coerceAtLeast(size.height)
                    val left = if (layoutDirection == LayoutDirection.Rtl) size.width - fill else 0f
                    drawRoundRect(color, Offset(left, 0f), Size(fill, size.height), radius)
                }
            }
        },
    )
}
