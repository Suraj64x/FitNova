package app.fitnova.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ponytail: tiny asset image loader instead of Coil/Glide. Photos are bundled and few (~60), so
// downsampled decode + one LruCache is all that's needed. Add a library only for network images.

// An eighth of the heap (the usual Android rule), capped: low-RAM phones get a smaller cache.
private val cache = object : LruCache<String, ImageBitmap>(minOf(Runtime.getRuntime().maxMemory() / 8, 24L shl 20).toInt()) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().byteCount
}

// Two decodes at a time, so opening Train or Food doesn't start ten JPEG decodes against the UI thread.
private val decodeDispatcher = Dispatchers.IO.limitedParallelism(2)

/** Decodes at the smallest power-of-two size that still covers [width] x [height] (Crop fills both), in RGB_565. */
private fun decode(context: Context, path: String, width: Int, height: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}.getOrNull()

/**
 * A bundled photo (assets/[path]) decoded off the main thread for a [targetPx] x [targetHeightPx] slot, fading in
 * once ready. Pass [contentDescription] only when no text next to the photo already names it.
 */
@Composable
fun AssetPhoto(
    path: String,
    targetPx: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    targetHeightPx: Int = targetPx,
) {
    val context = LocalContext.current
    val key = "$path@${targetPx}x$targetHeightPx"
    // produceState keeps its value across key changes, so reset it first or a reused slot shows the old photo.
    val bitmap by produceState(cache.get(key), key) {
        value = cache.get(key)
        if (value == null) {
            value = withContext(decodeDispatcher) { decode(context.applicationContext, path, targetPx, targetHeightPx) }?.also { cache.put(key, it) }
        }
    }
    val alpha = animateFloatAsState(if (bitmap != null) 1f else 0f, tween(220), label = "photo")
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    // Placeholder only until the photo is opaque; the fade is read in the layer, so it never recomposes.
    Box(modifier.drawBehind { if (alpha.value < 1f) drawRect(placeholder) }) {
        bitmap?.let {
            Image(
                it,
                contentDescription,
                Modifier.matchParentSize().graphicsLayer {
                    this.alpha = alpha.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * Photo card: the photo on top, then the title and subtitle on the card (no scrim over the photo).
 * Give it a fixed size. [badge] is drawn over the photo.
 */
@Composable
fun PhotoCard(
    path: String,
    title: String,
    subtitle: String?,
    targetPx: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable BoxScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, shape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AssetPhoto(path, targetPx, Modifier.matchParentSize())
            badge()
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
