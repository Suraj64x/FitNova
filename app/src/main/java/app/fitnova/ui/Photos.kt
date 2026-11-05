package app.fitnova.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ponytail: tiny asset image loader instead of Coil/Glide. Photos are bundled and few (~22), so
// downsampled decode + one LruCache is all that's needed. Add a library only for network images.
private val cache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().byteCount
}

/** Decodes at the smallest power-of-two size that still covers [targetPx], in RGB_565 (half the memory). */
private fun decode(context: Context, path: String, targetPx: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}.getOrNull()

/** A bundled photo (assets/[path]) that fades in once decoded off the main thread; a flat tile until then. */
@Composable
fun AssetPhoto(path: String, targetPx: Int, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    val key = "$path@$targetPx"
    val bitmap by produceState(cache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { decode(context.applicationContext, path, targetPx) }?.also { cache.put(key, it) }
        }
    }
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0f, tween(220), label = "photo")
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let {
            Image(it, contentDescription, Modifier.matchParentSize().alpha(alpha), contentScale = ContentScale.Crop)
        }
    }
}
