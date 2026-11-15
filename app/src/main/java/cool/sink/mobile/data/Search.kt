package cool.sink.mobile.data

import cool.sink.mobile.util.decodeUrlForDisplay

/**
 * Local matching used for queries the Sink API cannot evaluate on the server, such as
 * Chinese text inside percent-encoded target URLs. Mirrors the server's search fields.
 */
fun Link.matchesQuery(query: String): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return false
    return slug.lowercase().contains(needle) ||
        decodeUrlForDisplay(url).lowercase().contains(needle) ||
        comment.orEmpty().lowercase().contains(needle) ||
        tags.any { it.lowercase().contains(needle) }
}
