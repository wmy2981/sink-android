package cool.sink.mobile

import cool.sink.mobile.data.CreateLinkPayload
import cool.sink.mobile.data.EditLinkPayload
import cool.sink.mobile.data.Link
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPayloadTest {

    private val json = Json { explicitNulls = false }

    @Test
    fun `edit payload keeps the slug and serializes every editable field`() {
        val payload = EditLinkPayload.from(
            original = Link(slug = "demo", url = "https://example.com/old"),
            url = "https://example.com/new",
            comment = "note",
            expiration = 1800000000,
            title = "Title",
            description = "Description",
            image = "/_assets/images/demo/a.png",
            apple = "https://apps.apple.com/app/id1",
            google = "https://play.google.com/store/apps/details?id=1",
            cloaking = true,
            redirectWithQuery = true,
            proxy = false,
            unsafe = true,
            password = "secret",
            geo = mapOf("JP" to "https://example.jp/"),
            tags = listOf("work"),
        )

        val body = json.encodeToJsonElement(EditLinkPayload.serializer(), payload).jsonObject
        assertEquals("demo", body["slug"]?.jsonPrimitive?.content)
        assertEquals("https://example.com/new", body["url"]?.jsonPrimitive?.content)
        assertEquals("Title", body["title"]?.jsonPrimitive?.content)
        assertEquals("Description", body["description"]?.jsonPrimitive?.content)
        assertEquals("/_assets/images/demo/a.png", body["image"]?.jsonPrimitive?.content)
        assertEquals("https://apps.apple.com/app/id1", body["apple"]?.jsonPrimitive?.content)
        assertEquals("true", body["cloaking"]?.jsonPrimitive?.content)
        assertEquals("true", body["redirectWithQuery"]?.jsonPrimitive?.content)
        assertEquals("false", body["proxy"]?.jsonPrimitive?.content)
        assertEquals("true", body["unsafe"]?.jsonPrimitive?.content)
        assertEquals("secret", body["password"]?.jsonPrimitive?.content)
        assertEquals("https://example.jp/", body["geo"]?.jsonObject?.get("JP")?.jsonPrimitive?.content)
    }

    @Test
    fun `create payload omits untouched advanced fields`() {
        val body = json.encodeToJsonElement(
            CreateLinkPayload.serializer(),
            CreateLinkPayload(url = "https://example.com"),
        ).jsonObject
        assertFalse(body.containsKey("title"))
        assertFalse(body.containsKey("password"))
        assertFalse(body.containsKey("geo"))
        assertFalse(body.containsKey("cloaking"))
        assertFalse(body.containsKey("tags"))
        assertNull(body["image"])
    }
}
