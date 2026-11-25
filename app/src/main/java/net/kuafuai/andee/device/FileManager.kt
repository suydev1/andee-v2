package net.kuafuai.andee.device

import java.io.File

object FileManager {
    data class Done(val path: String, val backup: String?)

    /**
     * Canonicalises [raw] and accepts it only when the result is inside one of
     * [roots]. A relative path is tried against each root in turn, so the first
     * root that contains the target wins. The single-root overload below stays
     * as a thin delegate for callers (and host tests) that grant one folder.
     */
    fun resolve(roots: List<File>, raw: String): File {
        val given = File(raw)
        val candidates = if (given.isAbsolute) listOf(given) else roots.map { File(it, raw) }
        val bases = roots.map { it.canonicalPath }
        for (candidate in candidates) {
            val target = candidate.canonicalPath
            if (bases.any { base -> target == base || target.startsWith(base + File.separator) }) {
                return File(target)
            }
        }
        throw IllegalStateException("That path is outside the folders Andee may touch: $raw")
    }

    fun resolve(root: File, raw: String): File = resolve(listOf(root), raw)

    private fun backup(target: File, backupDir: File): String? {
        if (!target.isFile) return null
        backupDir.mkdirs()
        val b = File(backupDir, "${target.name}.${System.currentTimeMillis()}.bak")
        target.copyTo(b, overwrite = true)
        return b.absolutePath
    }

    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        check(tmp.renameTo(target)) { "Could not replace the file: ${target.name}" }
    }

    /**
     * Counts matches the way [String.replaceFirst] and [String.replace] do, one
     * after another — `windowed` would also count every overlapping occurrence,
     * so `"aaa"` plus `"aa"` reported 2 while only 1 replacement happens.
     */
    private fun countNonOverlapping(haystack: String, needle: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) return count
            count++
            from = at + needle.length
        }
    }

    fun write(
        roots: List<File>, path: String, text: String, backupDir: File, cap: Long,
    ): Done {
        check(text.toByteArray(Charsets.UTF_8).size <= cap) { "The text is larger than the file size cap." }
        val target = resolve(roots, path)
        val b = backup(target, backupDir)
        atomicWrite(target, text)
        return Done(target.absolutePath, b)
    }

    fun write(root: File, path: String, text: String, backupDir: File, cap: Long): Done =
        write(listOf(root), path, text, backupDir, cap)

    fun edit(
        roots: List<File>, path: String, old: String, new: String,
        replaceAll: Boolean, backupDir: File, cap: Long,
    ): Done {
        check(old.isNotEmpty()) { "The text to replace cannot be empty." }
        val target = resolve(roots, path)
        check(target.isFile) { "There is no file at $path." }
        val before = target.readText(Charsets.UTF_8)
        val count = countNonOverlapping(before, old)
        check(count > 0) { "The text to replace was not found in ${target.name}." }
        check(replaceAll || count == 1) { "That text appears $count times; pass replace_all if you mean all of them." }
        val after = if (replaceAll) before.replace(old, new) else before.replaceFirst(old, new)
        return write(roots, path, after, backupDir, cap)
    }

    fun edit(
        root: File, path: String, old: String, new: String,
        replaceAll: Boolean, backupDir: File, cap: Long,
    ): Done = edit(listOf(root), path, old, new, replaceAll, backupDir, cap)

    fun mkdir(roots: List<File>, path: String): Done {
        val target = resolve(roots, path)
        check(target.mkdirs() || target.isDirectory) { "Could not create the folder: $path" }
        return Done(target.absolutePath, null)
    }

    fun mkdir(root: File, path: String): Done = mkdir(listOf(root), path)

    fun move(roots: List<File>, from: String, to: String, overwrite: Boolean): Done {
        val a = resolve(roots, from)
        val b = resolve(roots, to)
        check(a.canonicalPath != b.canonicalPath) { "The source and destination are the same file: $from." }
        check(a.exists()) { "There is nothing at $from." }
        check(overwrite || !b.exists()) { "There is already something at $to; pass overwrite if you mean it." }
        if (b.exists()) check(b.delete()) { "Could not remove the old $to." }
        check(a.renameTo(b)) { "Could not move $from to $to." }
        return Done(b.absolutePath, null)
    }

    fun move(root: File, from: String, to: String, overwrite: Boolean): Done =
        move(listOf(root), from, to, overwrite)

    fun delete(roots: List<File>, path: String, recursive: Boolean, confirm: Boolean): Done {
        check(confirm) {
            "Deleting changes the user's files for good. Pass confirm: true only after saying what would be " +
                "deleted and asking them first."
        }
        val target = resolve(roots, path)
        check(target.exists()) { "There is nothing at $path." }
        if (target.isDirectory) {
            val children = target.listFiles().orEmpty()
            check(recursive || children.isEmpty()) { "That folder is not empty; pass recursive if you mean all of it." }
        }
        check(if (target.isDirectory) target.deleteRecursively() else target.delete()) {
            "Could not delete $path."
        }
        return Done(target.absolutePath, null)
    }

    fun delete(root: File, path: String, recursive: Boolean, confirm: Boolean): Done =
        delete(listOf(root), path, recursive, confirm)
}
