package app.fitnova.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.fitnova.domain.Exercise

/**
 * Exercise icons as stick figures, drawn from coordinates in a 100×100 box.
 * Same visual language as the live skeleton, scales to any size, and costs no image files.
 */
private class Figure(
    val head: Offset,
    val body: List<FloatArray>,
    val props: List<FloatArray> = emptyList(),
    val scenery: List<FloatArray> = emptyList(),
    val ground: Boolean = false,
)

private fun l(vararg v: Int) = FloatArray(v.size) { v[it].toFloat() }

private fun fig(hx: Int, hy: Int, vararg body: FloatArray, props: List<FloatArray> = emptyList(), scenery: List<FloatArray> = emptyList(), ground: Boolean = false) =
    Figure(Offset(hx.toFloat(), hy.toFloat()), body.toList(), props, scenery, ground)

private fun FloatArray.mirrored() = FloatArray(size) { i -> if (i % 2 == 0) 100f - this[i] else this[i] }

private fun Figure.mirrored() = Figure(Offset(100f - head.x, head.y), body.map { it.mirrored() }, props.map { it.mirrored() }, scenery.map { it.mirrored() }, ground)

private val LUNGE_FIG = fig(50, 13, l(50, 22, 50, 54), l(50, 54, 72, 58, 72, 87), l(50, 54, 36, 82, 20, 86), l(50, 25, 55, 46), ground = true)

private val FIGURES: Map<Exercise, Figure> = mapOf(
    Exercise.PUSHUP to fig(17, 55, l(90, 87, 58, 73, 27, 62), l(27, 62, 27, 87), ground = true),
    Exercise.KNEE_PUSHUP to fig(17, 58, l(66, 87, 47, 74, 27, 64), l(66, 87, 84, 74), l(27, 64, 27, 87), ground = true),
    Exercise.WIDE_PUSHUP to fig(17, 55, l(90, 87, 58, 73, 27, 62), l(27, 62, 14, 87), l(27, 62, 40, 87), ground = true),
    Exercise.DIAMOND_PUSHUP to fig(17, 55, l(90, 87, 58, 73, 27, 62), l(27, 62, 34, 84), props = listOf(l(29, 86, 34, 81, 39, 86)), ground = true),
    Exercise.DIP to fig(
        46, 29, l(44, 40, 46, 64, 70, 62, 72, 87), l(44, 40, 33, 47, 34, 56),
        scenery = listOf(l(8, 56, 34, 56), l(10, 56, 10, 87), l(32, 56, 32, 87)), ground = true,
    ),
    Exercise.CURL to fig(50, 13, l(50, 22, 50, 56), l(44, 90, 50, 56, 56, 90), l(50, 25, 52, 42, 64, 30), props = listOf(l(60, 26, 68, 34))),
    Exercise.HAMMER_CURL to fig(50, 13, l(50, 22, 50, 56), l(44, 90, 50, 56, 56, 90), l(50, 25, 52, 42, 62, 29), props = listOf(l(62, 22, 62, 35))),
    Exercise.PRESS to fig(
        50, 16, l(50, 25, 50, 58), l(42, 90, 50, 58, 58, 90), l(38, 28, 62, 28), l(38, 28, 30, 17, 33, 6), l(62, 28, 70, 17, 67, 6),
        props = listOf(l(26, 6, 40, 6), l(60, 6, 74, 6)),
    ),
    Exercise.LATERAL_RAISE to fig(
        50, 15, l(50, 24, 50, 58), l(42, 90, 50, 58, 58, 90), l(38, 28, 62, 28), l(38, 28, 15, 30), l(62, 28, 85, 30),
        props = listOf(l(14, 24, 14, 36), l(86, 24, 86, 36)),
    ),
    Exercise.SQUAT to fig(49, 22, l(46, 32, 30, 60), l(30, 60, 58, 62, 54, 87, 66, 87), l(46, 34, 74, 42), ground = true),
    Exercise.SUMO_SQUAT to fig(50, 16, l(50, 26, 50, 56), l(50, 56, 28, 64, 26, 87), l(50, 56, 72, 64, 74, 87), l(42, 30, 50, 42, 58, 30), ground = true),
    Exercise.JUMP_SQUAT to fig(
        50, 10, l(50, 20, 50, 50), l(50, 50, 44, 70, 46, 84), l(50, 50, 56, 70, 54, 84), l(50, 22, 36, 6), l(50, 22, 64, 6),
        props = listOf(l(36, 94, 46, 94), l(54, 94, 64, 94)),
    ),
    Exercise.LUNGE to LUNGE_FIG,
    Exercise.REVERSE_LUNGE to LUNGE_FIG.mirrored(),
    Exercise.GLUTE_BRIDGE to fig(11, 80, l(19, 83, 50, 64, 72, 58, 76, 87), l(19, 83, 42, 87), ground = true),
    Exercise.WALL_SIT to fig(25, 22, l(24, 31, 24, 60, 54, 60, 54, 87), l(24, 34, 50, 58), scenery = listOf(l(15, 4, 15, 88)), ground = true),
    Exercise.CALF_RAISE to fig(
        50, 12, l(50, 21, 50, 54), l(50, 54, 47, 79, 55, 87), l(50, 54, 53, 79, 61, 87), l(50, 24, 44, 48), l(50, 24, 56, 48),
        props = listOf(l(80, 62, 80, 38), l(74, 45, 80, 38, 86, 45)), ground = true,
    ),
    Exercise.PLANK to fig(17, 59, l(90, 87, 58, 72, 27, 66), l(27, 66, 27, 86, 44, 86), ground = true),
    Exercise.SIDE_PLANK to fig(18, 37, l(88, 87, 56, 64, 26, 44), l(26, 44, 26, 87), l(26, 44, 26, 10), ground = true),
    Exercise.SITUP to fig(31, 38, l(40, 85, 33, 49), l(40, 85, 64, 64, 80, 87), l(33, 52, 60, 60), ground = true),
    Exercise.CRUNCH to fig(17, 64, l(50, 85, 25, 72), l(50, 85, 68, 62, 84, 87), l(25, 72, 14, 60), ground = true),
    Exercise.LEG_RAISE to fig(11, 80, l(19, 83, 50, 85), l(50, 85, 66, 34), l(19, 83, 38, 87), ground = true),
    Exercise.MOUNTAIN_CLIMBER to fig(19, 52, l(29, 57, 58, 64, 90, 87), l(29, 57, 29, 87), l(58, 64, 42, 74, 52, 87), ground = true),
    Exercise.JACK to fig(50, 14, l(50, 24, 50, 56), l(50, 27, 28, 8), l(50, 27, 72, 8), l(50, 56, 30, 90), l(50, 56, 70, 90)),
    Exercise.HIGH_KNEES to fig(50, 11, l(50, 21, 48, 53), l(48, 53, 46, 90), l(48, 53, 68, 52, 64, 74), l(50, 25, 62, 35, 66, 22), l(50, 25, 38, 38, 32, 28)),
    Exercise.BURPEE to fig(
        50, 12, l(50, 22, 50, 52), l(50, 52, 40, 64, 48, 76), l(50, 52, 60, 64, 52, 76), l(50, 24, 44, 3), l(50, 24, 56, 3),
        props = listOf(l(34, 92, 44, 92), l(56, 92, 66, 92)),
    ),
    Exercise.JUMP_ROPE to fig(
        50, 13, l(50, 22, 50, 56), l(50, 56, 46, 88), l(50, 56, 54, 88), l(50, 26, 36, 50), l(50, 26, 64, 50),
        props = listOf(l(36, 50, 26, 72, 34, 92, 50, 97, 66, 92, 74, 72, 64, 50)),
    ),
)

@Composable
fun ExerciseIcon(exercise: Exercise, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val figure = FIGURES[exercise] ?: return
    val faint = color.copy(alpha = 0.3f)
    Canvas(modifier) {
        val s = size.minDimension / 100f
        val origin = Offset((size.width - 100f * s) / 2f, (size.height - 100f * s) / 2f)
        fun at(x: Float, y: Float) = Offset(origin.x + x * s, origin.y + y * s)
        val stroke = 6.5f * s

        if (figure.ground) drawLine(faint, at(4f, 90f), at(96f, 90f), 2.5f * s, StrokeCap.Round)
        figure.scenery.forEach { polyline(it, faint, 3.5f * s, ::at) }
        figure.props.forEach { polyline(it, color, stroke * 0.7f, ::at) }
        figure.body.forEach { polyline(it, color, stroke, ::at) }
        drawCircle(color, 7f * s, at(figure.head.x, figure.head.y))
    }
}

private fun DrawScope.polyline(points: FloatArray, color: Color, width: Float, at: (Float, Float) -> Offset) {
    var i = 0
    while (i + 3 < points.size) {
        drawLine(color, at(points[i], points[i + 1]), at(points[i + 2], points[i + 3]), width, StrokeCap.Round)
        i += 2
    }
}
