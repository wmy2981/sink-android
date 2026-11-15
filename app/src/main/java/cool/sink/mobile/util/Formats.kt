package cool.sink.mobile.util

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
