package net.kuafuai.andee.device

import java.io.File

object FileManager {
    data class Done(val path: String, val backup: String?)

    fun resolve(root: File, raw: String): File {
        val base = root.canonicalPath
        val given = File(raw)
        val target = (if (given.isAbsolute) given else File(root, given.path)).canonicalPath
        check(target == base || target.startsWith(base + File.separator)) {
            "That path is outside the folder Andee may touch: $raw"
        }
        return File(target)
    }

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

    fun write(root: File, path: String, text: String, backupDir: File, cap: Long): Done {
        check(text.toByteArray(Charsets.UTF_8).size <= cap) { "The text is larger than the file size cap." }
        val target = resolve(root, path)
        val b = backup(target, backupDir)
        atomicWrite(target, text)
        return Done(target.absolutePath, b)
    }

    fun edit(
        root: File, path: String, old: String, new: String,
        replaceAll: Boolean, backupDir: File, cap: Long,
    ): Done {
        check(old.isNotEmpty()) { "The text to replace cannot be empty." }
        val target = resolve(root, path)
        check(target.isFile) { "There is no file at $path." }
        val before = target.readText(Charsets.UTF_8)
        val count = before.windowed(old.length).count { it == old }
        check(count > 0) { "The text to replace was not found in ${target.name}." }
        check(replaceAll || count == 1) { "That text appears $count times; pass replace_all if you mean all of them." }
        val after = if (replaceAll) before.replace(old, new) else before.replaceFirst(old, new)
        return write(root, path, after, backupDir, cap)
    }

    fun mkdir(root: File, path: String): Done {
        val target = resolve(root, path)
        check(target.mkdirs() || target.isDirectory) { "Could not create the folder: $path" }
        return Done(target.absolutePath, null)
    }

    fun move(root: File, from: String, to: String, overwrite: Boolean): Done {
        val a = resolve(root, from)
        val b = resolve(root, to)
        check(a.canonicalPath != b.canonicalPath) { "The source and destination are the same file: $from." }
        check(a.exists()) { "There is nothing at $from." }
        check(overwrite || !b.exists()) { "There is already something at $to; pass overwrite if you mean it." }
        if (b.exists()) check(b.delete()) { "Could not remove the old $to." }
        check(a.renameTo(b)) { "Could not move $from to $to." }
        return Done(b.absolutePath, null)
    }

    fun delete(root: File, path: String, recursive: Boolean, confirm: Boolean): Done {
        check(confirm) {
            "Deleting changes the user's files for good. Pass confirm: true only after saying what would be " +
                "deleted and asking them first."
        }
        val target = resolve(root, path)
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
}
