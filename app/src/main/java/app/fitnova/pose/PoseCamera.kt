package app.fitnova.pose

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

private val LANDMARKS = mapOf(
    PoseLandmark.LEFT_SHOULDER to Joint(true, Part.SHOULDER),
    PoseLandmark.RIGHT_SHOULDER to Joint(false, Part.SHOULDER),
    PoseLandmark.LEFT_ELBOW to Joint(true, Part.ELBOW),
    PoseLandmark.RIGHT_ELBOW to Joint(false, Part.ELBOW),
    PoseLandmark.LEFT_WRIST to Joint(true, Part.WRIST),
    PoseLandmark.RIGHT_WRIST to Joint(false, Part.WRIST),
    PoseLandmark.LEFT_HIP to Joint(true, Part.HIP),
    PoseLandmark.RIGHT_HIP to Joint(false, Part.HIP),
    PoseLandmark.LEFT_KNEE to Joint(true, Part.KNEE),
    PoseLandmark.RIGHT_KNEE to Joint(false, Part.KNEE),
    PoseLandmark.LEFT_ANKLE to Joint(true, Part.ANKLE),
    PoseLandmark.RIGHT_ANKLE to Joint(false, Part.ANKLE),
)

private fun Pose.toFrame() = Frame(
    buildMap {
        for ((type, joint) in LANDMARKS) {
            val lm = getPoseLandmark(type) ?: continue
            put(joint, Pt(lm.position.x, lm.position.y, lm.inFrameLikelihood))
        }
    },
)

/**
 * Camera preview that streams poses in preview (view) coordinates, so the overlay can draw them as-is.
 * Tuned for low-end phones: ML Kit's base (fast) model, ~640x480 analysis frames, and stale frames dropped.
 */
@Composable
fun PoseCamera(front: Boolean, onFrame: (Frame) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onFrame)

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build()
        }
    }

    LaunchedEffect(front) {
        controller.cameraSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }

    DisposableEffect(lifecycleOwner) {
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
        )
        val executor = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                result.getValue(detector)?.let { latest(it.toFrame()) }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            detector.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = modifier,
    )
}

/** Skeleton lines and joints. Reads [frame] and [color] while drawing, so new frames only redraw, never recompose. */
@Composable
fun SkeletonOverlay(frame: () -> Frame?, color: () -> Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val f = frame() ?: return@Canvas
        val c = color()
        val stroke = 4.dp.toPx()
        val dot = 4.5.dp.toPx()
        for ((a, b) in BONES) {
            val pa = f.points[a]?.takeIf { it.p >= MIN_LIKELIHOOD } ?: continue
            val pb = f.points[b]?.takeIf { it.p >= MIN_LIKELIHOOD } ?: continue
            drawLine(c, Offset(pa.x, pa.y), Offset(pb.x, pb.y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        for (p in f.points.values) {
            if (p.p < MIN_LIKELIHOOD) continue
            drawCircle(Color.White, dot, Offset(p.x, p.y))
            drawCircle(c, dot * 0.55f, Offset(p.x, p.y))
        }
    }
}
