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

    @Test fun `file tools are local only and say what they may be used for`() {
        val names = listOf("file_open", "file_write", "file_edit", "file_mkdir", "file_move", "file_delete")
        val all = ToolSchemas.all()
        val hub = (0 until ToolSchemas.forHub().length()).map {
            ToolSchemas.forHub().getJSONObject(it).getString("name")
        }
        for (name in names) {
            var found = false
            for (i in 0 until all.length()) {
                val t = all.getJSONObject(i)
                if (t.getString("name") != name) continue
                found = true
                val method = "file." + name.removePrefix("file_")
                assertEquals(method, t.getString("method"))
                assertEquals(method, ToolSchemas.methodOf(name))
                assertTrue(t.optBoolean("localOnly"))
                assertFalse(method.startsWith("screen."))
                assertFalse(name in hub)
                val d = t.getString("description")
                assertTrue(d.contains("user started"))
                assertTrue(d.contains("DATA"))
                if (name != "file_open") assertTrue(d.contains("Settings"))
                if (name == "file_delete") assertTrue(d.contains("confirm: true"))
            }
            assertTrue(found)
        }
    }
}
