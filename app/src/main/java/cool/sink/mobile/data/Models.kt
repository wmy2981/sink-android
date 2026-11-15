package cool.sink.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A short link as returned by the Sink API. Unknown fields are ignored while parsing. */
@Serializable
data class Link(
    val id: String? = null,
    val slug: String,
    val url: String,
    val comment: String? = null,
    val expiration: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
    val apple: String? = null,
    val google: String? = null,
    val cloaking: Boolean? = null,
    val redirectWithQuery: Boolean? = null,
    val proxy: Boolean? = null,
    val password: String? = null,
    val unsafe: Boolean? = null,
    val geo: Map<String, String>? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
) {
    val isExpired: Boolean
        get() = expiration != null && expiration < System.currentTimeMillis() / 1000
}

@Serializable
data class LinkList(
    val links: List<Link> = emptyList(),
    @SerialName("list_complete") val listComplete: Boolean = true,
    val cursor: String? = null,
)

@Serializable
data class LinkResponse(
    val link: Link,
    val shortLink: String = "",
)

@Serializable
data class SearchItem(
    val slug: String,
    val url: String,
    val comment: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class TagCount(
    val name: String,
    val count: Int = 0,
)

@Serializable
data class VerifyResponse(
    val name: String = "Sink",
    val url: String = "",
    val authMethod: String = "",
    val userID: String = "",
    val userEmail: String = "",
    val accessEnabled: Boolean = false,
)

@Serializable
data class CreateLinkPayload(
    val url: String,
    val slug: String? = null,
    val comment: String? = null,
    val expiration: Long? = null,
    val tags: List<String> = emptyList(),
)

/**
 * The edit endpoint replaces the stored link, so every field the app does not edit is
 * carried over from the fetched link to avoid dropping settings made on the web dashboard.
 */
@Serializable
data class EditLinkPayload(
    val url: String,
    val slug: String,
    val comment: String? = null,
    val expiration: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val image: String? = null,
    val apple: String? = null,
    val google: String? = null,
    val cloaking: Boolean? = null,
    val redirectWithQuery: Boolean? = null,
    val proxy: Boolean? = null,
    val unsafe: Boolean? = null,
    val geo: Map<String, String>? = null,
    val tags: List<String> = emptyList(),
) {
    companion object {
        fun from(
            original: Link,
            url: String,
            comment: String?,
            expiration: Long?,
            tags: List<String>,
        ) = EditLinkPayload(
            url = url,
            slug = original.slug,
            comment = comment,
            expiration = expiration,
            title = original.title,
            description = original.description,
            image = original.image,
            apple = original.apple,
            google = original.google,
            cloaking = original.cloaking,
            redirectWithQuery = original.redirectWithQuery,
            proxy = original.proxy,
            unsafe = original.unsafe,
            geo = original.geo,
            tags = tags,
        )
    }
}

@Serializable
data class DeleteLinkPayload(val slug: String)
