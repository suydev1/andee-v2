package net.kuafuai.andee.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileWalkTest {
    private fun root(): File = File.createTempFile("walk", "").also { it.delete(); it.mkdirs() }

    @Test fun `finds by name with case folded and extension filter`() {
        val r = root()
        File(r, "Holiday Photo.JPG").writeText("x")
        File(r, "notes.txt").writeText("x")
        File(r, ".hidden.txt").writeText("x")
        File(r, "Android").mkdirs()
        File(r, "Android/data.txt").writeText("x")
        val found = FileWalk.walk(r, "holiday", null, 20, 2000)
        assertEquals(1, found.hits.size)
        assertEquals("Holiday Photo.JPG", found.hits.single().name)
        assertTrue(FileWalk.walk(r, "txt", "txt", 20, 2000).hits.any { it.name == "notes.txt" })
        assertFalse(FileWalk.walk(r, "hidden", null, 20, 2000).hits.any { it.name.startsWith(".") })
        assertFalse(found.hits.any { it.path.contains("Android") })
    }

    @Test fun `a tiny budget returns partial results instead of hanging`() {
        val r = root()
        repeat(50) { File(r, "f$it.txt").writeText("x") }
        val got = FileWalk.walk(r, "f", null, 100, 0)
        assertTrue(got.partial)
        assertTrue(got.hits.size < 50)
    }

    @Test fun `mime has an honest fallback`() {
        assertEquals("text/plain", FileWalk.mime("a.txt"))
        assertEquals("application/octet-stream", FileWalk.mime("a.unknownext"))
    }
}
