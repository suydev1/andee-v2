package net.kuafuai.andee.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class FileReaderTest {
    private fun file(): File = File.createTempFile("reader", "")

    @Test fun `reads an inclusive window and names the last line returned`() {
        val f = file()
        f.writeText((1..10).joinToString("\n") { "line$it" })
        val got = FileReader.read(f, 3, 5)
        assertEquals("line3\nline4\nline5", got.text)
        assertEquals(3, got.fromLine)
        assertEquals(5, got.toLine)
        assertEquals(3, got.lineCount)
        assertTrue(got.truncated)
        assertEquals(6, got.nextFromLine ?: -1)
    }

    @Test fun `a short file is not truncated`() {
        val f = file()
        f.writeText("a\nb")
        val got = FileReader.read(f, 1, 400)
        assertEquals("a\nb", got.text)
        assertEquals(2, got.toLine)
        assertEquals(2, got.lineCount)
        assertFalse(got.truncated)
        assertNull(got.nextFromLine)
    }

    @Test fun `the window never exceeds four hundred lines`() {
        val f = file()
        f.writeText((1..500).joinToString("\n") { "l$it" })
        val got = FileReader.read(f, 1, 10_000)
        assertEquals(400, got.lineCount)
        assertEquals(400, got.toLine)
        assertEquals("l400", got.text.substringAfterLast('\n'))
        assertTrue(got.truncated)
        assertEquals(401, got.nextFromLine ?: -1)
    }

    @Test fun `a line that would pass the character cap is left unread`() {
        val f = file()
        val long = "x".repeat(3000)
        f.writeText((1..10).joinToString("\n") { long })
        val got = FileReader.read(f, 1, 400)
        assertEquals(6, got.lineCount)
        assertEquals(6, got.toLine)
        assertEquals(6 * 3000 + 5, got.text.length)
        assertTrue(got.truncated)
        assertEquals(7, got.nextFromLine ?: -1)
    }

    @Test fun `a line exactly at the cap is returned`() {
        val f = file()
        val exact = "y".repeat(FileReader.MAX_CHARS)
        f.writeText(exact)
        val got = FileReader.read(f, 1, 400)
        assertEquals(FileReader.MAX_CHARS, got.text.length)
        assertEquals(1, got.lineCount)
        assertFalse(got.truncated)
    }

    @Test fun `a single oversized line refuses with a readable error`() {
        val f = file()
        f.writeText("z".repeat(FileReader.MAX_CHARS + 1))
        try {
            FileReader.read(f, 1, 400)
            fail("an oversized line must refuse")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("character cap"))
            assertTrue(e.message!!.contains("Line 1"))
        }
    }

    @Test fun `an impossible window refuses`() {
        val f = file()
        f.writeText("a\nb")
        try {
            FileReader.read(f, 0, 2)
            fail("from_line below 1 must refuse")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("from_line"))
        }
        try {
            FileReader.read(f, 3, 2)
            fail("to_line before from_line must refuse")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("to_line"))
        }
    }

    @Test fun `a window past the end returns no lines`() {
        val f = file()
        f.writeText("a\nb")
        val got = FileReader.read(f, 100, 120)
        assertEquals("", got.text)
        assertEquals(0, got.lineCount)
        assertFalse(got.truncated)
        assertNull(got.nextFromLine)
    }
}
