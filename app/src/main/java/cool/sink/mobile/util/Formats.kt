package cool.sink.mobile.util

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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

/** Sink marks a stored password with this prefix instead of returning the secret. */
const val LINK_PASSWORD_MASK_PREFIX = "__SINK_MASKED__"

const val MAX_COMMENT_LENGTH = 2048
const val MAX_TITLE_LENGTH = 256
const val MAX_DESCRIPTION_LENGTH = 2048
const val MAX_PASSWORD_LENGTH = 128
const val MAX_TAGS = 10
const val MAX_TAG_LENGTH = 32
const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

val IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

private const val SLUG_ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz"

/** Sink's documented default for `NUXT_PUBLIC_SLUG_DEFAULT_LENGTH`. */
const val DEFAULT_SLUG_LENGTH = 6

private val slugDefaultLengthPattern = Regex("""slugDefaultLength\s*:\s*"?(\d{1,4})"?""")

/**
 * Reads the instance's configured random-slug length from the Nuxt runtime config embedded in
 * the dashboard HTML. Sink has no API for this value, so parsing the public payload is the only
 * way to follow the server's `NUXT_PUBLIC_SLUG_DEFAULT_LENGTH`.
 */
fun parseSlugDefaultLength(html: String): Int? =
    slugDefaultLengthPattern.find(html)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..2048 }

fun isMaskedLinkPassword(value: String?): Boolean = value?.startsWith(LINK_PASSWORD_MASK_PREFIX) == true

/** Matches Sink's default slug format: six characters, no look-alikes. */
fun randomSlug(length: Int = 6): String = buildString(length) {
    repeat(length) { append(SLUG_ALPHABET.random()) }
}

/** Host part of a target URL, used for the favicon service. */
fun hostOfLink(url: String): String? =
    runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }

/** Turns a stored asset path such as `/_assets/images/...` into an absolute URL. */
fun resolveAssetUrl(baseUrl: String, path: String): String {
    val trimmed = path.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        baseUrl.trimEnd('/') + "/" + trimmed.trimStart('/')
    }
}

/** The five standard UTM parameters, in the order the web editor shows them. */
data class UtmValues(
    val source: String = "",
    val medium: String = "",
    val campaign: String = "",
    val term: String = "",
    val content: String = "",
)

private val utmKeys = listOf("utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content")

private fun decodeQueryComponent(value: String): String =
    runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

private fun encodeQueryComponent(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun queryPairs(rawQuery: String): List<Pair<String, String>> =
    rawQuery.split('&')
        .filter { it.isNotEmpty() }
        .map { pair ->
            decodeQueryComponent(pair.substringBefore('=')) to
                decodeQueryComponent(pair.substringAfter('=', ""))
        }

fun parseUtmValues(url: String): UtmValues {
    val pairs = queryPairs(url.substringAfter('?', "").substringBefore('#'))
    fun value(key: String): String = pairs.firstOrNull { it.first == key }?.second.orEmpty()
    return UtmValues(
        source = value("utm_source"),
        medium = value("utm_medium"),
        campaign = value("utm_campaign"),
        term = value("utm_term"),
        content = value("utm_content"),
    )
}

/**
 * Replaces the UTM parameters of [url] while keeping every other query parameter and the
 * fragment untouched. Empty values remove that parameter.
 */
fun applyUtmValues(url: String, values: UtmValues): String {
    val fragmentIndex = url.indexOf('#')
    val fragment = if (fragmentIndex >= 0) url.substring(fragmentIndex) else ""
    val withoutFragment = if (fragmentIndex >= 0) url.substring(0, fragmentIndex) else url
    val queryIndex = withoutFragment.indexOf('?')
    val base = if (queryIndex >= 0) withoutFragment.substring(0, queryIndex) else withoutFragment
    val rawQuery = if (queryIndex >= 0) withoutFragment.substring(queryIndex + 1) else ""

    val kept = rawQuery.split('&')
        .filter { it.isNotEmpty() }
        .filterNot { pair -> decodeQueryComponent(pair.substringBefore('=')) in utmKeys }

    val added = listOf(
        "utm_source" to values.source,
        "utm_medium" to values.medium,
        "utm_campaign" to values.campaign,
        "utm_term" to values.term,
        "utm_content" to values.content,
    ).mapNotNull { (key, value) ->
        value.trim().takeIf { it.isNotEmpty() }?.let { "$key=${encodeQueryComponent(it)}" }
    }

    val query = (kept + added).joinToString("&")
    return when {
        query.isEmpty() -> base + fragment
        else -> "$base?$query$fragment"
    }
}
