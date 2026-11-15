package cool.sink.mobile

import cool.sink.mobile.data.CreateLinkPayload
import cool.sink.mobile.data.SinkApiFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SinkApiFactoryTest {

    private lateinit var server: MockWebServer
    private val factory = SinkApiFactory()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `list links sends bearer token and parses pagination`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """
                {
                  "links": [{
                    "id": "abc",
                    "slug": "demo",
                    "url": "https://example.com/page",
                    "comment": "示例",
                    "tags": ["work", "docs"],
                    "createdAt": 1700000000,
                    "updatedAt": 1700000100,
                    "expiration": 1800000000,
                    "cloaking": true
                  }],
                  "list_complete": false,
                  "cursor": "next-page"
                }
                """.trimIndent(),
            ),
        )

        val api = factory.create(server.url("/").toString(), "site-token")
        val page = kotlinx.coroutines.runBlocking { api.listLinks(limit = 2) }

        val request = server.takeRequest()
        assertEquals("/api/link/list?limit=2&sort=newest&status=active", request.path)
        assertEquals("Bearer site-token", request.getHeader("Authorization"))

        assertEquals(1, page.links.size)
        val link = page.links.single()
        assertEquals("demo", link.slug)
        assertEquals(listOf("work", "docs"), link.tags)
        assertTrue(link.cloaking == true)
        assertFalse(page.listComplete)
        assertEquals("next-page", page.cursor)
    }

    @Test
    fun `instance sub path is preserved`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("""{"name":"Sink","authMethod":"site-token","userEmail":"root@example.com"}"""),
        )

        val api = factory.create(server.url("/sink/").toString(), "token")
        val verify = kotlinx.coroutines.runBlocking { api.verify() }

        assertEquals("/sink/api/verify", server.takeRequest().path)
        assertEquals("root@example.com", verify.userEmail)
    }

    @Test
    fun `create payload omits empty optional fields`() {
        server.enqueue(
            MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json")
                .setBody("""{"link":{"slug":"abc","url":"https://example.com"},"shortLink":"https://s.example/abc"}"""),
        )

        val api = factory.create(server.url("/").toString(), "token")
        kotlinx.coroutines.runBlocking {
            api.createLink(CreateLinkPayload(url = "https://example.com"))
        }

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("https://example.com", body["url"].toString().trim('"'))
        assertFalse(body.containsKey("slug"))
        assertFalse(body.containsKey("comment"))
        assertFalse(body.containsKey("expiration"))
        assertFalse(body.containsKey("tags"))
    }

    @Test
    fun `link counters request sends all ids`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """{"data":[{"id":"a","visits":3,"visitors":2,"referers":1}]}""",
            ),
        )

        val api = factory.create(server.url("/").toString(), "token")
        val counters = kotlinx.coroutines.runBlocking { api.linkCounters("a,b").data.orEmpty() }

        val request = server.takeRequest()
        assertEquals("/api/stats/counters", request.requestUrl?.encodedPath)
        assertEquals("a,b", request.requestUrl?.queryParameter("id"))
        assertEquals(3L, counters.single().visits)
        assertEquals(2L, counters.single().visitors)
        assertEquals(1L, counters.single().referers)
    }

    @Test
    fun `dashboard html request keeps an explicit accept header`() {
        val html = """<script>window.__NUXT__={};window.__NUXT__.config={public:{slugDefaultLength:"8"}}</script>"""
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html").setBody(html),
        )

        val api = factory.create(server.url("/").toString(), "token")
        val body = kotlinx.coroutines.runBlocking { api.dashboardHtml().use { it.string() } }

        assertEquals(html, body)
        val request = server.takeRequest()
        assertEquals("/dashboard", request.path)
        assertEquals("text/html", request.getHeader("Accept"))
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test
    fun `normalizeBaseUrl adds scheme and trailing slash`() {
        assertEquals("https://sink.example.com/", SinkApiFactory.normalizeBaseUrl("sink.example.com"))
        assertEquals("https://sink.example.com/", SinkApiFactory.normalizeBaseUrl(" https://sink.example.com "))
        assertEquals("https://example.com/sink/", SinkApiFactory.normalizeBaseUrl("https://example.com/sink"))
        assertEquals("http://192.168.1.10:8787/", SinkApiFactory.normalizeBaseUrl("http://192.168.1.10:8787"))
        assertNull(SinkApiFactory.normalizeBaseUrl("   "))
        assertNull(SinkApiFactory.normalizeBaseUrl("ftp://example.com"))
    }

    @Test
    fun `normalizeBaseUrl drops query and fragment`() {
        assertEquals(
            "https://sink.example.com/",
            SinkApiFactory.normalizeBaseUrl("https://sink.example.com/?foo=bar#top"),
        )
    }

    @Test
    fun `hostOf returns hostname only`() {
        assertEquals("sink.example.com", SinkApiFactory.hostOf("https://sink.example.com/"))
        assertEquals("192.168.1.10", SinkApiFactory.hostOf("http://192.168.1.10:8787/"))
    }
}
