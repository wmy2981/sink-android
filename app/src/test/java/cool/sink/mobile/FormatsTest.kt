package cool.sink.mobile

import cool.sink.mobile.util.decodeUrlForDisplay
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
    fun `percent encoded urls are decoded for display`() {
        assertEquals(
            "https://wshare.cc.cd/「350234」是什么——禁漫",
            decodeUrlForDisplay("https://wshare.cc.cd/%E3%80%8C350234%E3%80%8D%E6%98%AF%E4%BB%80%E4%B9%88%E2%80%94%E2%80%94%E7%A6%81%E6%BC%AB"),
        )
        assertEquals("https://example.com/a+b c", decodeUrlForDisplay("https://example.com/a+b%20c"))
        assertEquals("https://example.com/100%", decodeUrlForDisplay("https://example.com/100%"))
        assertEquals("https://example.com/plain", decodeUrlForDisplay("https://example.com/plain"))
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
