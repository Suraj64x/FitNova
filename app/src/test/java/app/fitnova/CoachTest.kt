package app.fitnova

import app.fitnova.domain.Exercise
import app.fitnova.pose.Coach
import app.fitnova.pose.Frame
import app.fitnova.pose.Joint
import app.fitnova.pose.Part
import app.fitnova.pose.Pt
import app.fitnova.pose.Status
import app.fitnova.pose.angle
import app.fitnova.pose.leanFromVertical
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CoachTest {
    private var t = 0L

    /** Feed each angle a few frames (~15 fps) so the smoothing settles, like a real camera stream. */
    private fun Coach.play(vararg frames: Frame, repeat: Int = 4) {
        for (f in frames) repeat(repeat) { t += 66; onFrame(f, t) }
    }

    private fun rad(deg: Double) = Math.toRadians(deg)

    private fun frame(vararg pts: Pair<Joint, Pt>) = Frame(pts.toMap())
    private fun l(part: Part) = Joint(true, part)
    private fun r(part: Part) = Joint(false, part)

    /** Side-view push-up with the left side visible. [hipY] 112 = straight body, larger = sagging, smaller = piked. */
    private fun pushUp(elbowDeg: Double, hipY: Float = 112f): Frame {
        val elbow = Pt(100f, 150f)
        val wrist = Pt((100 + 40 * sin(rad(elbowDeg))).toFloat(), (150 - 40 * cos(rad(elbowDeg))).toFloat())
        return frame(
            l(Part.SHOULDER) to Pt(100f, 110f), l(Part.ELBOW) to elbow, l(Part.WRIST) to wrist,
            l(Part.HIP) to Pt(200f, hipY), l(Part.KNEE) to Pt(250f, 113f), l(Part.ANKLE) to Pt(300f, 114f),
        )
    }

    /** Side-view squat: knee angle [kneeDeg], torso tilted [leanDeg] from vertical. */
    private fun squat(kneeDeg: Double, leanDeg: Double = 15.0): Frame {
        val hip = Pt((100 - 80 * sin(rad(kneeDeg))).toFloat(), (220 + 80 * cos(rad(kneeDeg))).toFloat())
        val shoulder = Pt((hip.x + 80 * sin(rad(leanDeg))).toFloat(), (hip.y - 80 * cos(rad(leanDeg))).toFloat())
        return frame(
            l(Part.SHOULDER) to shoulder, l(Part.HIP) to hip, l(Part.KNEE) to Pt(100f, 220f), l(Part.ANKLE) to Pt(100f, 300f),
        )
    }

    /** Front-view jumping jack: both arms raised [armDeg] from the body. */
    private fun jack(armDeg: Double): Frame {
        val s = sin(rad(armDeg)).toFloat() * 60
        val c = cos(rad(armDeg)).toFloat() * 60
        return frame(
            l(Part.SHOULDER) to Pt(100f, 100f), l(Part.HIP) to Pt(100f, 200f), l(Part.WRIST) to Pt(100 - s, 100 + c),
            r(Part.SHOULDER) to Pt(140f, 100f), r(Part.HIP) to Pt(140f, 200f), r(Part.WRIST) to Pt(140 + s, 100 + c),
        )
    }

    @Test fun angleMath() {
        assertEquals(90f, angle(Pt(0f, 1f), Pt(0f, 0f), Pt(1f, 0f)), 0.01f)
        assertEquals(180f, angle(Pt(-1f, 0f), Pt(0f, 0f), Pt(1f, 0f)), 0.01f)
        assertEquals(0f, leanFromVertical(Pt(0f, 10f), Pt(0f, 0f)), 0.01f)
        assertEquals(90f, leanFromVertical(Pt(0f, 0f), Pt(10f, 0f)), 0.01f)
    }

    @Test fun pushUpCountsCleanRep() {
        val coach = Coach(Exercise.PUSHUP)
        coach.play(pushUp(170.0), pushUp(120.0), pushUp(85.0), pushUp(120.0), pushUp(170.0))
        assertEquals(1, coach.reps)
        assertEquals(100, coach.formScore)
        assertEquals(Status.GOOD, coach.feedback.status)
    }

    @Test fun shallowPushUpIsNotCountedAndAsksToGoLower() {
        val coach = Coach(Exercise.PUSHUP)
        coach.play(pushUp(170.0), pushUp(120.0), pushUp(170.0))
        assertEquals(0, coach.reps)
        assertEquals(Status.FIX, coach.feedback.status)
        assertTrue(coach.feedback.cue.contains("lower"))
    }

    @Test fun saggingHipsAreFlaggedAndCostFormScore() {
        val coach = Coach(Exercise.PUSHUP)
        coach.play(pushUp(170.0), pushUp(120.0, hipY = 145f), pushUp(85.0, hipY = 145f))
        assertEquals(Status.FIX, coach.feedback.status)
        assertTrue(coach.feedback.cue.startsWith("Hips sagging"))
        coach.play(pushUp(120.0), pushUp(170.0))
        assertEquals(1, coach.reps)
        assertEquals(0, coach.formScore)
    }

    @Test fun pikedHipsAreFlagged() {
        val coach = Coach(Exercise.PUSHUP)
        coach.play(pushUp(120.0, hipY = 80f))
        assertTrue(coach.feedback.cue.startsWith("Hips too high"))
    }

    @Test fun missingJointsMeanSearching() {
        val coach = Coach(Exercise.PUSHUP)
        coach.play(frame(l(Part.SHOULDER) to Pt(1f, 1f)))
        assertEquals(Status.SEARCHING, coach.feedback.status)
        coach.play(frame(l(Part.SHOULDER) to Pt(100f, 110f, p = 0.2f), l(Part.ELBOW) to Pt(100f, 150f), l(Part.WRIST) to Pt(100f, 190f)))
        assertEquals(Status.SEARCHING, coach.feedback.status)
    }

    @Test fun plankTimesOnlyGoodForm() {
        val coach = Coach(Exercise.PLANK)
        val straight = pushUp(90.0)
        val sagging = pushUp(90.0, hipY = 145f)
        coach.play(straight, repeat = 46) // ~3 s
        coach.play(sagging, repeat = 15) // ~1 s
        assertTrue("good hold ${coach.goodHoldMs}", coach.goodHoldMs in 2_800..3_100)
        assertTrue("form ${coach.formScore}", coach.formScore in 70..80)
        assertEquals(Status.FIX, coach.feedback.status)
    }

    @Test fun standingUpIsNotAPlank() {
        val coach = Coach(Exercise.PLANK)
        coach.play(
            frame(l(Part.SHOULDER) to Pt(100f, 100f), l(Part.HIP) to Pt(100f, 200f), l(Part.ANKLE) to Pt(100f, 300f)),
            repeat = 30,
        )
        assertEquals(0L, coach.holdMs)
        assertEquals(Status.SEARCHING, coach.feedback.status)
    }

    @Test fun squatCountsAndCuesChestUp() {
        val coach = Coach(Exercise.SQUAT)
        coach.play(squat(175.0), squat(130.0), squat(85.0), squat(130.0), squat(175.0))
        assertEquals(1, coach.reps)
        assertEquals(100, coach.formScore)

        coach.play(squat(130.0, leanDeg = 65.0))
        assertEquals("Chest up, keep your back straighter", coach.feedback.cue)
    }

    @Test fun gluteBridgeCountsHipLifts() {
        val shoulder = l(Part.SHOULDER) to Pt(20f, 180f)
        val knee = l(Part.KNEE) to Pt(160f, 150f)
        val down = frame(shoulder, knee, l(Part.HIP) to Pt(80f, 195f))
        val up = frame(shoulder, knee, l(Part.HIP) to Pt(80f, 166f))
        val coach = Coach(Exercise.GLUTE_BRIDGE)
        coach.play(down, up, down, up, down)
        assertEquals(2, coach.reps)
    }

    @Test fun wallSitTimesOnlyInPosition() {
        val sitting = frame(
            l(Part.SHOULDER) to Pt(100f, 110f), l(Part.HIP) to Pt(100f, 200f), l(Part.KNEE) to Pt(160f, 200f), l(Part.ANKLE) to Pt(160f, 280f),
        )
        val standing = frame(
            l(Part.SHOULDER) to Pt(100f, 60f), l(Part.HIP) to Pt(100f, 150f), l(Part.KNEE) to Pt(100f, 220f), l(Part.ANKLE) to Pt(100f, 290f),
        )
        val coach = Coach(Exercise.WALL_SIT)
        coach.play(standing, repeat = 20)
        assertEquals(0L, coach.holdMs)
        coach.play(sitting, repeat = 31) // ~2 s
        assertTrue("good ${coach.goodHoldMs}", coach.goodHoldMs in 1_900..2_100)
        assertEquals(100, coach.formScore)
    }

    @Test fun highKneesCountEachLeg() {
        fun knees(leftUp: Boolean, rightUp: Boolean) = frame(
            l(Part.SHOULDER) to Pt(100f, 100f), l(Part.HIP) to Pt(100f, 200f), l(Part.KNEE) to Pt(100f, if (leftUp) 215f else 290f),
            r(Part.SHOULDER) to Pt(140f, 100f), r(Part.HIP) to Pt(140f, 200f), r(Part.KNEE) to Pt(140f, if (rightUp) 215f else 290f),
        )
        val coach = Coach(Exercise.HIGH_KNEES)
        coach.play(knees(false, false), knees(true, false), knees(false, false), knees(false, true), knees(false, false), knees(true, false), knees(false, false))
        assertEquals(3, coach.reps)
    }

    @Test fun manualOnlyExercisesHaveNoCameraReading() {
        val coach = Coach(Exercise.BURPEE)
        coach.play(pushUp(170.0))
        assertEquals(Status.SEARCHING, coach.feedback.status)
    }

    @Test fun jumpingJacksCountFullCycles() {
        val coach = Coach(Exercise.JACK)
        coach.play(jack(15.0), jack(90.0), jack(170.0), jack(90.0), jack(15.0))
        coach.play(jack(170.0), jack(15.0))
        assertEquals(2, coach.reps)
    }
}
