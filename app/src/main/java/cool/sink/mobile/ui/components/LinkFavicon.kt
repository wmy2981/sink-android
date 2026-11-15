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
    val faviconUrl = remember(host) { host?.let { "https://unavatar.webp.se/$it" } }
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
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
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
    connection.inputStream.use { stream -> BitmapFactory.decodeStream(stream) }
}.getOrNull()?.let { decoded ->
    if (decoded.width > 96 || decoded.height > 96) {
        Bitmap.createScaledBitmap(decoded, 96, 96, true)
    } else {
        decoded
    }
}
