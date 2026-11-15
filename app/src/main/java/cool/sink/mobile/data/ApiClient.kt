package cool.sink.mobile.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Builds Sink API clients for arbitrary self-hosted instances. A placeholder base URL is
 * used by Retrofit, while an interceptor rewrites every request onto the configured instance.
 */
class SinkApiFactory {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, SinkApi>()

    fun create(baseUrl: String, token: String): SinkApi =
        cache.getOrPut("$baseUrl|$token") { build(baseUrl, token) }

    private fun build(baseUrl: String, token: String): SinkApi {
        val instance = baseUrl.toHttpUrl()
        val client = baseClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val targetBuilder = instance.newBuilder()
                    .addEncodedPathSegments(request.url.encodedPath.trimStart('/'))
                request.url.encodedQuery?.let { query -> targetBuilder.encodedQuery(query) }
                chain.proceed(request.newBuilder().url(targetBuilder.build()).build())
            }
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("Accept", chain.request().header("Accept") ?: "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SinkApi::class.java)
    }

    companion object {
        private const val PLACEHOLDER_BASE_URL = "https://sink.invalid/"

        /** Accepts `sink.example.com`, `https://sink.example.com` or a sub-path deployment. */
        fun normalizeBaseUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val hasScheme = trimmed.contains("://")
            val isHttp = trimmed.startsWith("http://") || trimmed.startsWith("https://")
            if (hasScheme && !isHttp) return null
            val withScheme = if (isHttp) trimmed else "https://$trimmed"
            val url: HttpUrl = withScheme.toHttpUrlOrNull() ?: return null
            val path = if (url.encodedPath.endsWith("/")) url.encodedPath else "${url.encodedPath}/"
            return url.newBuilder()
                .encodedPath(path)
                .encodedQuery(null)
                .encodedFragment(null)
                .build()
                .toString()
        }

        fun hostOf(baseUrl: String): String =
            baseUrl.toHttpUrlOrNull()?.host ?: baseUrl.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
    }
}
