package cool.sink.mobile

import cool.sink.mobile.util.isValidHttpUrl
import cool.sink.mobile.util.normalizeTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {

    @Test
    fun `tags are trimmed and lowercased`() {
        assertEquals("news", normalizeTag("  News "))
        assertEquals("中文标签", normalizeTag("中文标签"))
    }

    @Test
    fun `http urls are validated`() {
        assertTrue(isValidHttpUrl("https://example.com/path?q=1"))
        assertTrue(isValidHttpUrl("http://192.168.1.10:8080/a"))
        assertFalse(isValidHttpUrl("example.com"))
        assertFalse(isValidHttpUrl("ftp://example.com"))
        assertFalse(isValidHttpUrl("https://"))
        assertFalse(isValidHttpUrl(""))
    }
}
