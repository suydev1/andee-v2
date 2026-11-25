package net.kuafuai.andee.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchParseTest {
    @Test fun `a Tavily response keeps title url snippet and date`() {
        val json = """
            {"query":"q","results":[
              {"title":"A","url":"https://a.example","content":"snippet","published_date":"2026-01-01"},
              {"title":"B","url":"https://b.example","content":"more"}
            ],"usage":{"credits":1}}
        """.trimIndent()
        val r = WebSearch.parse(json)
        assertEquals(2, r.results.size)
        assertEquals("A", r.results[0].title)
        assertEquals("https://a.example", r.results[0].url)
        assertEquals("snippet", r.results[0].content)
        assertEquals("2026-01-01", r.results[0].published)
        assertEquals(1, r.credits)
        assertEquals("tavily", r.source)
    }

    @Test fun `a result without a date stays blank`() {
        val r = WebSearch.parse("""{"results":[{"title":"B","url":"u","content":"c"}]}""")
        assertEquals("", r.results.single().published)
        assertTrue(r.results.single().content.isNotBlank())
        assertEquals("tavily", r.source)
    }
}
