package app.fitnova.pose

import app.fitnova.domain.Exercise
import app.fitnova.domain.Mode
import app.fitnova.domain.Rule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

// Pure Kotlin on purpose: no ML Kit or Android types, so posture rules run in plain JVM unit tests.

enum class Part { SHOULDER, ELBOW, WRIST, HIP, KNEE, ANKLE }

data class Joint(val left: Boolean, val part: Part)

/** A landmark in view pixels; [p] is the detector's in-frame likelihood (0..1). */
data class Pt(val x: Float, val y: Float, val p: Float = 1f)

const val MIN_LIKELIHOOD = 0.5f

class Frame(val points: Map<Joint, Pt>) {
    operator fun get(left: Boolean, part: Part): Pt? = points[Joint(left, part)]?.takeIf { it.p >= MIN_LIKELIHOOD }

    /** Side (true = left) on which every [parts] joint is visible, preferring the more confident side. */
    fun side(vararg parts: Part): Boolean? {
        fun score(left: Boolean) = parts.minOf { points[Joint(left, it)]?.p ?: 0f }
        val l = score(true)
        val r = score(false)
        return when {
            l < MIN_LIKELIHOOD && r < MIN_LIKELIHOOD -> null
            l >= r -> true
            else -> false
        }
    }

    /** Angle at [b] on one side, or null if any of the three joints isn't visible. */
    fun angle(left: Boolean, a: Part, b: Part, c: Part): Float? {
        val pa = this[left, a] ?: return null
        val pb = this[left, b] ?: return null
        val pc = this[left, c] ?: return null
        return angle(pa, pb, pc)
    }
}

val BONES: List<Pair<Joint, Joint>> = buildList {
    for (l in listOf(true, false)) {
        add(Joint(l, Part.SHOULDER) to Joint(l, Part.ELBOW))
        add(Joint(l, Part.ELBOW) to Joint(l, Part.WRIST))
        add(Joint(l, Part.SHOULDER) to Joint(l, Part.HIP))
        add(Joint(l, Part.HIP) to Joint(l, Part.KNEE))
        add(Joint(l, Part.KNEE) to Joint(l, Part.ANKLE))
    }
    add(Joint(true, Part.SHOULDER) to Joint(false, Part.SHOULDER))
    add(Joint(true, Part.HIP) to Joint(false, Part.HIP))
}

/** Interior angle at [b], in degrees (0..180). */
fun angle(a: Pt, b: Pt, c: Pt): Float {
    val d = Math.toDegrees((atan2(c.y - b.y, c.x - b.x) - atan2(a.y - b.y, a.x - b.x)).toDouble())
    val deg = abs(d).toFloat()
    return if (deg > 180f) 360f - deg else deg
}

/** Angle of the segment [from]→[to] away from straight up: 0 = vertical, 90 = horizontal (image y grows down). */
fun leanFromVertical(from: Pt, to: Pt): Float =
    Math.toDegrees(atan2(abs(to.x - from.x).toDouble(), (from.y - to.y).toDouble())).toFloat()

/**
 * Distance of [p] from the line [a]→[c], as a fraction of the line length.
 * Positive means [p] sits on the same side as [floor] (e.g. the hands in a push-up); without a floor
 * reference, positive means further down the image. Body-relative, so it works in any phone orientation.
 */
fun offsetFromLine(a: Pt, c: Pt, p: Pt, floor: Pt? = null): Float {
    val dx = c.x - a.x
    val dy = c.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 < 1f) return 0f
    fun side(q: Pt) = (dx * (q.y - a.y) - dy * (q.x - a.x)) / len2
    val ref = floor?.let { side(it) }?.takeIf { abs(it) > 0.02f } ?: if (dx >= 0) 1f else -1f
    val s = side(p)
    return if (ref >= 0) s else -s
}

/**
 * Rep counting with hysteresis on one angle: a rep is dropping below [low] and coming back above [high].
 * A move that turns around before reaching [low] is reported as [Event.PARTIAL].
 */
class RepCounter(private val low: Float, private val high: Float) {
    enum class Event { NONE, REP, PARTIAL }

    var reps = 0
        private set
    var cleanReps = 0
        private set

    private val movingBelow = high - (high - low) * 0.25f
    private val partialBelow = high - (high - low) * 0.45f
    private var bottom = false
    private var moving = false
    private var faulty = false
    private var deepest = Float.MAX_VALUE

    fun update(angle: Float, fault: Boolean): Event {
        if (angle < movingBelow) moving = true
        if (moving && fault) faulty = true
        deepest = min(deepest, angle)
        if (angle < low) bottom = true
        if (angle <= high) return Event.NONE

        val event = when {
            bottom -> {
                reps++
                if (!faulty) cleanReps++
                Event.REP
            }
            moving && deepest < partialBelow -> Event.PARTIAL
            else -> Event.NONE
        }
        bottom = false
        moving = false
        faulty = false
        deepest = Float.MAX_VALUE
        return event
    }
}

enum class Status { SEARCHING, GOOD, FIX }

data class Feedback(val status: Status, val cue: String)

/**
 * One reading of the pose. [angle] drives the rep counter (null for holds); [inPosition] gates hold timers;
 * [fault] is a form cue. [left]/[right] are per-leg angles for alternating moves.
 */
private data class Reading(
    val angle: Float = 0f,
    val fault: String? = null,
    val left: Float? = null,
    val right: Float? = null,
)

class Coach(val exercise: Exercise) {
    private val rule = exercise.rule

    private val counter: RepCounter? = when (rule) {
        Rule.PUSH, Rule.KNEE_PUSH -> RepCounter(low = 95f, high = 150f)
        Rule.DIP -> RepCounter(low = 100f, high = 150f)
        Rule.SQUAT -> RepCounter(low = 100f, high = 160f)
        Rule.LUNGE -> RepCounter(low = 105f, high = 155f)
        Rule.CURL -> RepCounter(low = 60f, high = 145f)
        Rule.PRESS -> RepCounter(low = 95f, high = 155f)
        Rule.RAISE -> RepCounter(low = 105f, high = 150f)
        Rule.JACK -> RepCounter(low = 45f, high = 140f)
        Rule.BRIDGE -> RepCounter(low = 12f, high = 35f)
        Rule.SITUP -> RepCounter(low = 80f, high = 120f)
        Rule.LEG_RAISE -> RepCounter(low = 115f, high = 160f)
        Rule.KNEE_DRIVE, Rule.PLANK, Rule.WALL_SIT, Rule.NONE -> null
    }

    // Alternating moves (high knees, mountain climbers): one counter per leg, on knee-to-shoulder distance.
    private val leftLeg = if (rule == Rule.KNEE_DRIVE) RepCounter(low = 140f, high = 170f) else null
    private val rightLeg = if (rule == Rule.KNEE_DRIVE) RepCounter(low = 140f, high = 170f) else null

    private val searchCue = when (rule) {
        Rule.CURL, Rule.PRESS, Rule.RAISE -> "Show your upper body and arms"
        Rule.PLANK -> "Get into position, whole body in frame"
        else -> "Step back so your whole body is visible"
    }

    private val partialCue = when (rule) {
        Rule.PUSH, Rule.KNEE_PUSH -> "Go lower, chest toward the floor"
        Rule.DIP -> "Lower until your elbows reach 90°"
        Rule.SQUAT -> "Squat deeper"
        Rule.LUNGE -> "Lower your back knee more"
        Rule.CURL -> "Curl all the way up"
        Rule.PRESS -> "Lower the weights to shoulder level"
        Rule.RAISE -> "Raise your arms to shoulder height"
        Rule.JACK -> "Raise your arms higher"
        Rule.BRIDGE -> "Lift your hips higher"
        Rule.SITUP -> "Sit all the way up"
        Rule.LEG_RAISE -> "Raise your legs higher"
        Rule.KNEE_DRIVE -> "Drive your knees higher"
        else -> ""
    }

    private var smooth = Float.NaN
    private var lastFault = ""
    private var lastFaultAt = Long.MIN_VALUE / 2
    private var partialAt = Long.MIN_VALUE / 2
    private var lastFrameAt = -1L

    /** Holds only: time in position, and the part of it held with good form. */
    var holdMs = 0L
        private set
    var goodHoldMs = 0L
        private set

    var feedback = Feedback(Status.SEARCHING, searchCue)
        private set

    val reps get() = counter?.reps ?: ((leftLeg?.reps ?: 0) + (rightLeg?.reps ?: 0))

    /** Reps, or good-form seconds for a hold. */
    val amount get() = if (exercise.mode == Mode.HOLD) (goodHoldMs / 1000).toInt() else reps

    val formScore: Int
        get() {
            if (exercise.mode == Mode.HOLD) return if (holdMs == 0L) 0 else (goodHoldMs * 100 / holdMs).toInt()
            val clean = counter?.cleanReps ?: ((leftLeg?.cleanReps ?: 0) + (rightLeg?.cleanReps ?: 0))
            return if (reps == 0) 0 else clean * 100 / reps
        }

    fun onFrame(frame: Frame, nowMs: Long): Feedback {
        val dt = if (lastFrameAt < 0) 0L else (nowMs - lastFrameAt).coerceIn(0L, 500L)
        lastFrameAt = nowMs

        val reading = read(frame)
        if (reading == null) {
            smooth = Float.NaN
            feedback = Feedback(Status.SEARCHING, searchCue)
            return feedback
        }
        if (reading.fault != null) {
            lastFault = reading.fault
            lastFaultAt = nowMs
        }

        when {
            counter != null -> {
                smooth = if (smooth.isNaN()) reading.angle else smooth * 0.5f + reading.angle * 0.5f
                if (counter.update(smooth, reading.fault != null) == RepCounter.Event.PARTIAL) partialAt = nowMs
            }
            leftLeg != null && rightLeg != null -> {
                // Fast alternating moves: no smoothing, it would swallow quick knee drives.
                val events = listOfNotNull(
                    reading.left?.let { leftLeg.update(it, false) },
                    reading.right?.let { rightLeg.update(it, false) },
                )
                if (RepCounter.Event.PARTIAL in events) partialAt = nowMs
            }
            else -> {
                holdMs += dt
                if (reading.fault == null) goodHoldMs += dt
            }
        }

        // Hold a cue briefly so it doesn't flicker between frames.
        feedback = when {
            nowMs - lastFaultAt < 800 -> Feedback(Status.FIX, lastFault)
            nowMs - partialAt < 1200 -> Feedback(Status.FIX, partialCue)
            else -> Feedback(Status.GOOD, "Good form")
        }
        return feedback
    }

    private fun read(f: Frame): Reading? = when (rule) {
        Rule.PUSH -> pushUp(f, kneeLine = false)
        Rule.KNEE_PUSH -> pushUp(f, kneeLine = true)
        Rule.DIP -> dip(f)
        Rule.SQUAT -> squat(f)
        Rule.LUNGE -> lunge(f)
        Rule.CURL -> curl(f)
        Rule.PRESS -> press(f)
        Rule.RAISE -> armsOut(f)?.let { arm -> Reading(180f - arm, if (arm > 115f) "Stop at shoulder height" else null) }
        Rule.JACK -> armsOut(f)?.let { arm -> Reading(180f - arm) }
        Rule.BRIDGE -> bridge(f)
        Rule.SITUP -> f.side(Part.SHOULDER, Part.HIP, Part.KNEE)?.let { s -> Reading(f.angle(s, Part.SHOULDER, Part.HIP, Part.KNEE)!!) }
        Rule.LEG_RAISE -> legRaise(f)
        Rule.KNEE_DRIVE -> kneeDrive(f)
        Rule.PLANK -> plank(f)
        Rule.WALL_SIT -> wallSit(f)
        Rule.NONE -> null
    }

    /** Straight body check shared by push-ups and planks; returns a cue or null when aligned. */
    private fun bodyLineFault(f: Frame, s: Boolean, floor: Pt?, kneeLine: Boolean): String? {
        val shoulder = f[s, Part.SHOULDER] ?: return null
        val hip = f[s, Part.HIP] ?: return null
        val end = (if (kneeLine) f[s, Part.KNEE] else f[s, Part.ANKLE] ?: f[s, Part.KNEE]) ?: return null
        val drop = offsetFromLine(shoulder, end, hip, floor)
        return when {
            drop > 0.07f -> "Hips sagging, tighten your core"
            drop < -0.09f -> "Hips too high, straighten your body"
            else -> null
        }
    }

    private fun pushUp(f: Frame, kneeLine: Boolean): Reading? {
        val s = f.side(Part.SHOULDER, Part.ELBOW, Part.WRIST, Part.HIP) ?: return null
        if (f[s, Part.ANKLE] == null && f[s, Part.KNEE] == null) return null
        val wrist = f[s, Part.WRIST]!!
        return Reading(f.angle(s, Part.SHOULDER, Part.ELBOW, Part.WRIST)!!, bodyLineFault(f, s, floor = wrist, kneeLine = kneeLine))
    }

    private fun dip(f: Frame): Reading? {
        val s = f.side(Part.SHOULDER, Part.ELBOW, Part.WRIST) ?: return null
        return Reading(f.angle(s, Part.SHOULDER, Part.ELBOW, Part.WRIST)!!)
    }

    private fun plank(f: Frame): Reading? {
        val s = f.side(Part.SHOULDER, Part.HIP) ?: return null
        val shoulder = f[s, Part.SHOULDER]!!
        val end = f[s, Part.ANKLE] ?: f[s, Part.KNEE] ?: return null
        // Must be roughly horizontal or diagonal, otherwise someone standing straight would "plank".
        if (leanFromVertical(end, shoulder) !in 40f..140f) return null
        val floor = f[s, Part.ELBOW] ?: f[s, Part.WRIST]
        return Reading(fault = bodyLineFault(f, s, floor, kneeLine = false))
    }

    private fun wallSit(f: Frame): Reading? {
        val s = f.side(Part.SHOULDER, Part.HIP, Part.KNEE, Part.ANKLE) ?: return null
        val knee = f.angle(s, Part.HIP, Part.KNEE, Part.ANKLE)!!
        if (knee > 150f) return null // standing up: not in position
        val lean = leanFromVertical(f[s, Part.HIP]!!, f[s, Part.SHOULDER]!!)
        val fault = when {
            knee > 115f -> "Slide lower, thighs parallel to the floor"
            knee < 70f -> "Too low, come up a little"
            lean > 25f -> "Keep your back flat against the wall"
            else -> null
        }
        return Reading(fault = fault)
    }

    private fun squat(f: Frame): Reading? {
        val s = f.side(Part.SHOULDER, Part.HIP, Part.KNEE, Part.ANKLE) ?: return null
        val lean = leanFromVertical(f[s, Part.HIP]!!, f[s, Part.SHOULDER]!!)
        return Reading(f.angle(s, Part.HIP, Part.KNEE, Part.ANKLE)!!, if (lean > 50f) "Chest up, keep your back straighter" else null)
    }

    private fun lunge(f: Frame): Reading? {
        val s = f.side(Part.SHOULDER, Part.HIP, Part.KNEE, Part.ANKLE) ?: return null
        // The front knee bends the most, whichever side it is.
        val bend = listOfNotNull(f.angle(true, Part.HIP, Part.KNEE, Part.ANKLE), f.angle(false, Part.HIP, Part.KNEE, Part.ANKLE)).min()
        val lean = leanFromVertical(f[s, Part.HIP]!!, f[s, Part.SHOULDER]!!)
        return Reading(bend, if (lean > 25f) "Keep your torso upright" else null)
    }

    private fun curl(f: Frame): Reading? {
        val s = f.side(Part.SHOULDER, Part.ELBOW, Part.WRIST, Part.HIP) ?: return null
        val swing = f.angle(s, Part.HIP, Part.SHOULDER, Part.ELBOW)!!
        return Reading(f.angle(s, Part.SHOULDER, Part.ELBOW, Part.WRIST)!!, if (swing > 40f) "Keep your elbows by your sides" else null)
    }

    private fun press(f: Frame): Reading? {
        val s = f.side(Part.SHOULDER, Part.ELBOW, Part.WRIST) ?: return null
        val hip = f[s, Part.HIP]
        val lean = hip?.let { leanFromVertical(it, f[s, Part.SHOULDER]!!) } ?: 0f
        return Reading(f.angle(s, Part.SHOULDER, Part.ELBOW, Part.WRIST)!!, if (lean > 20f) "Brace your core, don't lean back" else null)
    }

    /** Average arm raise (hip–shoulder–wrist) across both arms, front view. */
    private fun armsOut(f: Frame): Float? {
        val left = f.angle(true, Part.HIP, Part.SHOULDER, Part.WRIST) ?: return null
        val right = f.angle(false, Part.HIP, Part.SHOULDER, Part.WRIST) ?: return null
        return (left + right) / 2f
    }

    private fun bridge(f: Frame): Reading? {
        val s = f.side(Part.SHOULDER, Part.HIP, Part.KNEE) ?: return null
        // Flip so the effort (hips up, ~175°) reads low and rest (hips down) reads high.
        return Reading(180f - f.angle(s, Part.SHOULDER, Part.HIP, Part.KNEE)!!)
    }

    private fun legRaise(f: Frame): Reading? {
        val s = f.side(Part.SHOULDER, Part.HIP, Part.KNEE, Part.ANKLE) ?: return null
        val knee = f.angle(s, Part.HIP, Part.KNEE, Part.ANKLE)!!
        return Reading(angle(f[s, Part.SHOULDER]!!, f[s, Part.HIP]!!, f[s, Part.ANKLE]!!), if (knee < 145f) "Keep your legs straight" else null)
    }

    /**
     * Knee-to-shoulder distance as % of torso length: ~190 with the leg extended, ~120 with the knee driven up.
     * Distance (not a joint angle) so it works both from the front (high knees, where the thigh is
     * foreshortened) and from the side (mountain climbers).
     */
    private fun kneeDrive(f: Frame): Reading? {
        fun lift(l: Boolean): Float? {
            val shoulder = f[l, Part.SHOULDER] ?: return null
            val hip = f[l, Part.HIP] ?: return null
            val knee = f[l, Part.KNEE] ?: return null
            val torso = kotlin.math.hypot(hip.x - shoulder.x, hip.y - shoulder.y)
            if (torso < 1f) return null
            return 100f * kotlin.math.hypot(knee.x - shoulder.x, knee.y - shoulder.y) / torso
        }
        val left = lift(true)
        val right = lift(false)
        if (left == null && right == null) return null
        return Reading(left = left, right = right)
    }
}
