package cool.sink.mobile.data

import cool.sink.mobile.R
import cool.sink.mobile.util.UiText
import cool.sink.mobile.util.decodeUrlForDisplay
import cool.sink.mobile.util.parseSlugDefaultLength
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class SinkException(val text: UiText, val code: Int? = null) : Exception()

/** Single entry point for every Sink API call used by the app. */
class SinkRepository(
    private val settingsStore: SettingsStore,
    private val apiFactory: SinkApiFactory,
) {
    val settings: Flow<Settings> = settingsStore.settings

    suspend fun currentSettings(): Settings = settingsStore.current()

    suspend fun verify(baseUrl: String, token: String): VerifyResponse = wrap {
        apiFactory.create(baseUrl, token).verify()
    }

    suspend fun saveConnection(baseUrl: String, token: String, verify: VerifyResponse) {
        settingsStore.saveConnection(baseUrl, token, verify.userEmail, verify.authMethod)
    }

    suspend fun listLinks(
        cursor: String? = null,
        sort: String = "newest",
        status: String = "active",
        tag: String? = null,
        limit: Int = 30,
    ): LinkList = wrap {
        api().listLinks(limit = limit, cursor = cursor, sort = sort, tag = tag, status = status)
    }

    suspend fun searchLinks(query: String): List<SearchItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.any { it.code > 127 }) {
            searchLinksLocally(trimmed)
        } else {
            wrap { api().searchLinks(query = trimmed) }
        }
    }

    /**
     * Sink stores target URLs as submitted, so Chinese characters are usually percent-encoded
     * and SQL LIKE over the raw URL cannot match the decoded text a user types. Scan the active
     * links page by page and match against decoded fields instead. Capped to keep it bounded.
     */
    private suspend fun searchLinksLocally(query: String): List<SearchItem> {
        val results = LinkedHashMap<String, SearchItem>()
        var cursor: String? = null
        var pages = 0
        while (pages < 10) {
            val page = wrap {
                api().listLinks(limit = 1000, cursor = cursor, sort = "newest", status = "active")
            }
            page.links
                .filter { it.matchesQuery(query) }
                .forEach { link ->
                    results[link.slug] = SearchItem(
                        slug = link.slug,
                        url = decodeUrlForDisplay(link.url),
                        comment = link.comment,
                        tags = link.tags,
                    )
                }
            pages++
            if (page.listComplete || page.cursor == null) break
            cursor = page.cursor
        }
        return results.values.toList()
    }

    suspend fun tags(): List<TagCount> = wrap { api().tags() }

    suspend fun link(slug: String): Link = wrap { api().queryLink(slug) }

    suspend fun createLink(payload: CreateLinkPayload): Link = wrap { api().createLink(payload).link }

    suspend fun editLink(payload: EditLinkPayload): Link = wrap { api().editLink(payload).link }

    suspend fun deleteLink(slug: String) = wrap { api().deleteLink(DeleteLinkPayload(slug)) }

    suspend fun generateSlug(url: String): String = wrap { api().generateSlug(url).slug }

    /** The instance's configured random-slug length, or null when it can't be read. */
    suspend fun slugDefaultLength(): Int? = wrap {
        api().dashboardHtml().use { body -> parseSlugDefaultLength(body.string()) }
    }

    suspend fun generateOg(url: String, locale: String?): AiOgResponse = wrap { api().generateOg(url, locale) }

    /** Uploads an OpenGraph image; the instance must have R2 configured. */
    suspend fun uploadImage(slug: String, bytes: ByteArray, mimeType: String): String = wrap {
        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "img"
        }
        val file = MultipartBody.Part.createFormData(
            "file",
            "image.$extension",
            bytes.toRequestBody(mimeType.toMediaType()),
        )
        val slugPart = slug.toRequestBody("text/plain".toMediaType())
        api().uploadImage(file, slugPart).url
    }

    private suspend fun api(): SinkApi {
        val settings = settingsStore.current()
        if (!settings.isConnected) throw SinkException(UiText(R.string.error_not_connected))
        return apiFactory.create(settings.baseUrl, settings.token)
    }

    private suspend fun <T> wrap(block: suspend () -> T): T = try {
        block()
    } catch (error: SinkException) {
        throw error
    } catch (error: HttpException) {
        throw SinkException(messageFor(error.code()), error.code())
    } catch (_: SerializationException) {
        throw SinkException(UiText(R.string.error_unrecognized_data))
    } catch (_: IOException) {
        throw SinkException(UiText(R.string.error_network))
    }

    private fun messageFor(code: Int): UiText = when (code) {
        400 -> UiText(R.string.error_bad_request)
        401 -> UiText(R.string.error_unauthorized)
        403 -> UiText(R.string.error_forbidden)
        404 -> UiText(R.string.error_not_found)
        409 -> UiText(R.string.error_conflict)
        423 -> UiText(R.string.error_storage_not_ready)
        429 -> UiText(R.string.error_rate_limited)
        501 -> UiText(R.string.error_not_supported)
        in 500..599 -> UiText(R.string.error_server, listOf(code))
        else -> UiText(R.string.error_http, listOf(code))
    }
}
