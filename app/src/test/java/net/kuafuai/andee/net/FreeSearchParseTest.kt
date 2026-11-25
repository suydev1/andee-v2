package net.kuafuai.andee.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeSearchParseTest {
    @Test fun `duckduckgo html yields title url and snippet`() {
        val html = """
            <div class="result results_links">
              <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa">Example <b>A</b></a>
              <a class="result__snippet">A short <b>snippet</b> &amp; more</a>
            </div>
        """.trimIndent()
        val r = FreeSearch.parseDdg(html)
        assertEquals(1, r.size)
        assertTrue(r.single().url.contains("example.com"))
        assertEquals("Example A", r.single().title)
        assertEquals("A short snippet & more", r.single().content)
    }

    @Test fun `wikipedia search yields article links`() {
        val json = """{"query":{"search":[{"title":"Android","snippet":"A <span class=\"searchmatch\">mobile</span> OS"}]}}"""
        val r = FreeSearch.parseWikipedia(json)
        assertEquals(1, r.size)
        assertEquals("Android", r.single().title)
        assertEquals("A mobile OS", r.single().content)
        assertTrue(r.single().url.startsWith("https://en.wikipedia.org/wiki/Android"))
    }
}
