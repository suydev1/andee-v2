package net.kuafuai.andee.device

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import net.kuafuai.andee.config.VoiceConfig
import java.io.File

object StorageSearch {
    enum class Scope { OWN, MANAGED, PICKED }

    fun scope(context: Context): Scope = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() -> Scope.MANAGED
        VoiceConfig.storageTree(context).isNotBlank() -> Scope.PICKED
        else -> Scope.OWN
    }

    private fun ownRoots(context: Context): List<File> = listOfNotNull(
        context.getExternalFilesDir(null),
        context.filesDir,
        File(context.filesDir, "context"),
        File(context.getExternalFilesDir(null)?.path ?: return listOf(context.filesDir), "captures"),
        File(context.getExternalFilesDir(null)?.path ?: return listOf(context.filesDir), "inbox"),
    ).distinct()

    fun search(context: Context, query: String, ext: String?, limit: Int): FileWalk.Result {
        val cap = limit.coerceIn(1, 100)
        return when (scope(context)) {
            Scope.MANAGED -> FileWalk.walk(Environment.getExternalStorageDirectory(), query, ext, cap, 2000)
            Scope.OWN -> {
                val hits = ArrayList<FileWalk.Hit>()
                var visited = 0
                var partial = false
                for (root in ownRoots(context)) {
                    val r = FileWalk.walk(root, query, ext, cap - hits.size, 2000)
                    hits += r.hits
                    visited += r.visited
                    partial = partial || r.partial
                    if (hits.size >= cap) break
                }
                FileWalk.Result(hits, visited, partial)
            }
            Scope.PICKED -> walkTree(context, VoiceConfig.storageTree(context), query, ext, cap)
        }
    }

    private fun walkTree(context: Context, tree: String, query: String, ext: String?, limit: Int): FileWalk.Result {
        val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(tree)) }.getOrNull()
            ?: return FileWalk.Result(emptyList(), 0, false)
        val deadline = System.currentTimeMillis() + 2000
        val queue = ArrayDeque<DocumentFile>()
        root.listFiles().forEach { queue.addLast(it) }
        val hits = ArrayList<FileWalk.Hit>()
        var visited = 0
        var partial = false
        while (queue.isNotEmpty() && hits.size < limit && visited < 20_000) {
            if (System.currentTimeMillis() > deadline) { partial = true; break }
            val f = queue.removeFirst(); visited++
            val name = f.name.orEmpty()
            if (name.startsWith(".")) continue
            if (f.isDirectory) {
                if (!name.equals("Android", ignoreCase = true)) f.listFiles().forEach { queue.addLast(it) }
            } else if (name.contains(query, ignoreCase = true) &&
                (ext.isNullOrBlank() || name.substringAfterLast('.', "").equals(ext.trim('.', ' '), true))
            ) {
                hits += FileWalk.Hit(
                    name = name, path = "", uri = f.uri.toString(),
                    size = f.length(), modified = f.lastModified(),
                    mime = f.type ?: FileWalk.mime(name),
                )
            }
        }
        return FileWalk.Result(hits, visited, partial)
    }
}
