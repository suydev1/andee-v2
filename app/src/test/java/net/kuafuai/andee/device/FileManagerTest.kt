package net.kuafuai.andee.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class FileManagerTest {
    private fun root(): File = File.createTempFile("files", "").also { it.delete(); it.mkdirs() }

    @Test fun `write is atomic and keeps a backup of the old text`() {
        val r = root(); val backups = File(r, "../backups").apply { mkdirs() }
        val target = File(r, "a.txt"); target.writeText("old")
        val a = FileManager.write(r, "a.txt", "new", backups, 1024)
        assertEquals("new", target.readText())
        assertTrue(a.backup != null && File(a.backup).readText() == "old")
    }

    @Test fun `edit replaces one match and refuses an absent match`() {
        val r = root(); val backups = File(r, "../backups").apply { mkdirs() }
        File(r, "a.txt").writeText("one fish two fish")
        FileManager.edit(r, "a.txt", "one", "red", false, backups, 1024)
        assertEquals("red fish two fish", File(r, "a.txt").readText())
        try {
            FileManager.edit(r, "a.txt", "absent", "x", false, backups, 1024)
            fail("an absent old string must refuse")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("not found"))
        }
    }

    @Test fun `paths cannot escape the granted root`() {
        val r = root()
        try {
            FileManager.resolve(r, "../../etc/passwd")
            fail("traversal must refuse")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("outside"))
        }
    }

    @Test fun `delete needs confirmation and move refuses silent overwrite`() {
        val r = root()
        File(r, "a.txt").writeText("x")
        try {
            FileManager.delete(r, "a.txt", false, false)
            fail("delete without confirmation must refuse")
        } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("confirm")) }
        File(r, "b.txt").writeText("y")
        try {
            FileManager.move(r, "a.txt", "b.txt", false)
            fail("move must not overwrite silently")
        } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("overwrite")) }
        FileManager.move(r, "a.txt", "c.txt", false)
        assertTrue(File(r, "c.txt").exists())
    }
}
