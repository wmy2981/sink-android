package cool.sink.mobile.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cool.sink.mobile.util.faviconUrlForHost
import cool.sink.mobile.util.hostOfLink
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small memory cache so scrolling the list does not refetch every favicon. */
private val faviconCache = object : LruCache<String, Bitmap>(64) {}

/**
 * Circular site icon for a link, matching the web dashboard's unavatar fallback chain.
 * The local bolt is shown while loading and when the icon cannot be fetched.
 */
@Composable
fun LinkFavicon(
    url: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val host = remember(url) { hostOfLink(url) }
    val faviconUrl = remember(host) { host?.let(::faviconUrlForHost) }
    val bitmap by produceState<Bitmap?>(
        initialValue = faviconUrl?.let { faviconCache.get(it) },
        key1 = faviconUrl,
    ) {
        if (faviconUrl == null) {
            value = null
            return@produceState
        }
        val cached = faviconCache.get(faviconUrl)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) { loadFavicon(faviconUrl) }
        if (loaded != null) faviconCache.put(faviconUrl, loaded)
        value = loaded
    }

    val current = bitmap
    if (current == null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    } else {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun loadFavicon(url: String): Bitmap? = runCatching {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 5_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Sink-for-Android")
    }
    val bytes = try {
        connection.inputStream.use { stream -> stream.readBytes() }
    } finally {
        connection.disconnect()
    }

    // Decode bounds first so a large icon is sampled down instead of fully inflated.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, FAVICON_SIZE)
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    when {
        decoded == null -> null
        decoded.width > FAVICON_SIZE || decoded.height > FAVICON_SIZE ->
            Bitmap.createScaledBitmap(decoded, FAVICON_SIZE, FAVICON_SIZE, true)

        else -> decoded
    }
}.getOrNull()

private const val FAVICON_SIZE = 96

private fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= target && height / (sample * 2) >= target) {
        sample *= 2
    }
    return sample
}
