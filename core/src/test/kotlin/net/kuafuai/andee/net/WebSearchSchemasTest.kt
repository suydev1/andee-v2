package net.kuafuai.andee.net

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchSchemasTest {
    @Test fun `web search is local only and outside screen`() {
        val all = ToolSchemas.all()
        var found = false
        for (i in 0 until all.length()) {
            val t = all.getJSONObject(i)
            if (t.getString("name") == "web_search") {
                found = true
                assertEquals("web.search", t.getString("method"))
                assertTrue(t.optBoolean("localOnly"))
                assertFalse(ToolSchemas.isLocalOnly("screen.tap") == false && t.getString("method").startsWith("screen."))
            }
        }
        assertTrue(found)
        assertEquals("web.search", ToolSchemas.methodOf("web_search"))
        assertFalse("web_search" in (0 until ToolSchemas.forHub().length()).map {
            ToolSchemas.forHub().getJSONObject(it).getString("name")
        })
    }
}
