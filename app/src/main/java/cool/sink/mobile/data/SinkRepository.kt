package cool.sink.mobile.data

import cool.sink.mobile.util.decodeUrlForDisplay
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class SinkException(message: String, val code: Int? = null) : Exception(message)

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

    private suspend fun api(): SinkApi {
        val settings = settingsStore.current()
        if (!settings.isConnected) throw SinkException("尚未连接 Sink 实例")
        return apiFactory.create(settings.baseUrl, settings.token)
    }

    private suspend fun <T> wrap(block: suspend () -> T): T = try {
        block()
    } catch (error: SinkException) {
        throw error
    } catch (error: HttpException) {
        throw SinkException(messageFor(error.code()), error.code())
    } catch (_: SerializationException) {
        throw SinkException("实例返回了无法识别的数据，请确认地址指向 Sink 实例")
    } catch (_: IOException) {
        throw SinkException("网络请求失败，请检查实例地址和网络连接")
    }

    private fun messageFor(code: Int): String = when (code) {
        400 -> "请求参数不合法"
        401 -> "令牌无效或已过期，请到设置中重新连接"
        403 -> "实例拒绝了本次访问"
        404 -> "目标不存在，可能已被删除"
        409 -> "链接刚刚被修改，请刷新后重试"
        423 -> "实例存储尚未就绪，请先在网页端打开「链接」页面"
        429 -> "请求过于频繁，请稍后再试"
        in 500..599 -> "实例暂时不可用（HTTP $code）"
        else -> "请求失败（HTTP $code）"
    }
}
