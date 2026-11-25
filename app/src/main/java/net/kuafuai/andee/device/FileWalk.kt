package net.kuafuai.andee.device

import java.io.File
import java.net.URLConnection

object FileWalk {
    data class Hit(val name: String, val path: String, val uri: String?, val size: Long, val modified: Long, val mime: String)
    data class Result(val hits: List<Hit>, val visited: Int, val partial: Boolean)

    fun mime(name: String): String =
        URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"

    private fun matches(name: String, query: String, ext: String?): Boolean {
        if (query.isNotBlank() && !name.contains(query, ignoreCase = true)) return false
        if (!ext.isNullOrBlank() && !name.substringAfterLast('.', "").equals(ext.trim('.', ' '), ignoreCase = true)) return false
        return true
    }

    fun walk(
        root: File, query: String, ext: String?, limit: Int,
        budgetMs: Long, maxVisited: Int = 20_000,
    ): Result {
        val deadline = System.currentTimeMillis() + budgetMs
        val hits = ArrayList<Hit>()
        val queue = ArrayDeque<File>()
        queue.add(root)
        var visited = 0
        var partial = false
        while (queue.isNotEmpty() && hits.size < limit && visited < maxVisited) {
            if (System.currentTimeMillis() >= deadline) { partial = true; break }
            val dir = queue.removeFirst()
            visited++
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (f in children) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory && f.name.equals("Android", ignoreCase = true)) continue
                if (f.isDirectory) {
                    queue.addLast(f)
                } else if (matches(f.name, query, ext)) {
                    hits += Hit(
                        name = f.name, path = f.absolutePath, uri = null,
                        size = runCatching { f.length() }.getOrDefault(0L),
                        modified = runCatching { f.lastModified() }.getOrDefault(0L),
                        mime = mime(f.name),
                    )
                    if (hits.size >= limit) break
                }
            }
        }
        if (visited >= maxVisited && hits.size < limit) partial = true
        return Result(hits, visited, partial)
    }
}
