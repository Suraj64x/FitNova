package app.fitnova.pose

import android.app.ActivityManager
import android.content.Context
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume

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

/** One thread for every session: analysis never runs on the UI thread, and nothing to shut down mid-callback. */
private val analysis by lazy { Executors.newSingleThreadExecutor() }

private fun selector(front: Boolean) = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

/** isLowRamDevice alone means about 1 GB; phones sold as 3 GB report a little under 3 GiB. */
private fun Context.isLowEnd(): Boolean {
    val am = getSystemService(ActivityManager::class.java)
    val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
    return am.isLowRamDevice || mem.totalMem < 3L * 1024 * 1024 * 1024
}

private suspend fun ListenableFuture<*>.awaitDone(executor: Executor) =
    suspendCancellableCoroutine { cont -> addListener({ cont.resume(Unit) }, executor) }

/**
 * Camera preview that streams poses in preview (view) coordinates, so the overlay can draw them as-is.
 * Tuned for low-end phones: ML Kit's base model on CPU only, stale frames dropped, and on phones under 3 GB
 * 480x360 frames (ML Kit's minimum) at about 15 per second; 640x480 otherwise.
 */
@Composable
fun PoseCamera(front: Boolean, onFrame: (Frame) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onFrame)
    val lowEnd = remember { context.isLowEnd() }

    val controller = remember {
        LifecycleCameraController(context).apply {
            // Before the first bind: the default (back) camera would otherwise open first and then be swapped.
            cameraSelector = selector(front)
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            // Low end: never below ML Kit's 480x360 minimum, so fall back upwards.
            val strategy = if (lowEnd) ResolutionStrategy(Size(480, 360), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            else ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            imageAnalysisResolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(strategy).build()
        }
    }

    DisposableEffect(lifecycleOwner) {
        // CPU only: GPU acceleration starts a benchmark process and fights the preview for weak GPUs.
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptions.CPU)
                .build(),
        )
        val main = ContextCompat.getMainExecutor(context)
        val mlkit = MlKitAnalyzer(listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, analysis) { result ->
            val frame = result.getValue(detector)?.toFrame() ?: return@MlKitAnalyzer
            main.execute { latest(frame) }
        }
        controller.setImageAnalysisAnalyzer(analysis, if (lowEnd) Throttled(mlkit, 66) else mlkit)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            detector.close()
        }
    }

    // Binds only once CameraX knows which cameras exist, so a phone without the wanted one uses the other
    // instead of crashing; the same path handles the flip button.
    LaunchedEffect(front, lifecycleOwner) {
        controller.initializationFuture.awaitDone(ContextCompat.getMainExecutor(context))
        val pick = listOf(selector(front), selector(!front))
            .firstOrNull { runCatching { controller.hasCamera(it) }.getOrDefault(false) } ?: return@LaunchedEffect
        runCatching {
            controller.cameraSelector = pick // rebinds at once if already bound
            if (controller.cameraInfo == null) controller.bindToLifecycle(lifecycleOwner)
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

/** Drops frames that come sooner than [gapMs] after the last analysed one. Time-based, so dim light's lower fps isn't cut again. */
private class Throttled(private val inner: MlKitAnalyzer, private val gapMs: Long) : ImageAnalysis.Analyzer {
    private var last = 0L

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.uptimeMillis()
        if (now - last < gapMs) return image.close()
        last = now
        inner.analyze(image)
    }

    // The controller feeds the view-referenced transform through these, so they must reach MlKitAnalyzer.
    override fun getDefaultTargetResolution(): Size? = inner.defaultTargetResolution
    override fun getTargetCoordinateSystem(): Int = inner.targetCoordinateSystem
    override fun updateTransform(matrix: Matrix?) = inner.updateTransform(matrix)
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
