package net.kuafuai.andee.net

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileReadSchemasTest {
    private fun names(arr: JSONArray) = (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }

    private fun schemaFor(name: String): org.json.JSONObject {
        val all = ToolSchemas.all()
        for (i in 0 until all.length()) {
            val tool = all.getJSONObject(i)
            if (tool.getString("name") == name) return tool
        }
        throw AssertionError("no schema for $name")
    }

    @Test fun `file_read is a local-only tool outside the screen prefix`() {
        assertTrue("file_read" in names(ToolSchemas.all()))
        assertFalse("file_read" in names(ToolSchemas.forHub()))
        assertEquals("file.read", ToolSchemas.methodOf("file_read"))
        assertTrue(ToolSchemas.isLocalOnly("file.read"))
        assertFalse("file.read".startsWith("screen."))
    }

    @Test fun `the schema offers a path or an id and a bounded line window`() {
        val parameters = schemaFor("file_read").getJSONObject("parameters")
        assertEquals(0, parameters.getJSONArray("required").length())
        val props = parameters.getJSONObject("properties")
        assertTrue(props.has("path"))
        assertTrue(props.has("id"))
        assertFalse(props.has("uri"))
        assertTrue(props.has("from_line"))
        assertTrue(props.has("to_line"))
    }
}
