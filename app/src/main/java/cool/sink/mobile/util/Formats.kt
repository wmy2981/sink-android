package cool.sink.mobile.util

import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

fun formatDateTime(epochSeconds: Long?): String? = epochSeconds?.let {
    Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)
}

fun formatDate(epochSeconds: Long?): String? = epochSeconds?.let {
    Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(dateFormatter)
}

fun normalizeTag(raw: String): String = raw.trim().lowercase(Locale.ROOT)

fun isValidHttpUrl(raw: String): Boolean {
    val value = raw.trim()
    if (!value.startsWith("http://") && !value.startsWith("https://")) return false
    return try {
        val uri = URI(value)
        !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}

/** End of the selected local day in unix seconds, matching a "valid until" date. */
fun endOfDayEpochSeconds(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toEpochSecond() - 1


/**
 * Decodes percent-encoded UTF-8 sequences for display only. Unlike [java.net.URLDecoder]
 * a literal plus sign is preserved, because '+' is meaningful in URLs.
 */
fun decodeUrlForDisplay(raw: String): String {
    if (!raw.contains('%')) return raw
    val result = StringBuilder(raw.length)
    val pending = ByteArrayOutputStream()
    fun flushPending() {
        if (pending.size() > 0) {
            result.append(pending.toByteArray().toString(Charsets.UTF_8))
            pending.reset()
        }
    }
    var index = 0
    while (index < raw.length) {
        val char = raw[index]
        if (char == '%' && index + 2 < raw.length) {
            val value = raw.substring(index + 1, index + 3).toIntOrNull(16)
            if (value != null) {
                pending.write(value)
                index += 3
                continue
            }
        }
        flushPending()
        result.append(char)
        index++
    }
    flushPending()
    return result.toString()
}
