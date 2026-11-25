package net.kuafuai.andee.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class DropInbox(
    private val root: File,
    private val indexFile: File,
    private val maxBytes: () -> Long = { DEFAULT_MAX_BYTES },
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Item(
        val id: String,
        val name: String,
        val size: Long,
        val sha256: String,
        val addedAt: Long,
        val readAt: Long?,
        val status: Status,
    )

    enum class Status { NEW, TOO_LARGE, UNSUPPORTED, MISSING }

    data class ReadResult(val text: String, val truncated: Boolean)

    companion object {
        const val DEFAULT_MAX_BYTES = 2L * 1024 * 1024
        const val MAX_PER_SCAN = 10
        const val SETTLE_MS = 1500L
        const val MAX_CHARS = 20_000
        private val TEXT_EXT = setOf("txt", "md", "json", "csv", "log", "html")

        fun from(context: Context): DropInbox = DropInbox(
            root = File(context.getExternalFilesDir(null), "inbox"),
            indexFile = File(context.filesDir, "drop-index.json"),
            maxBytes = { VoiceConfig.dropMaxBytes(context) },
        )

        fun safeName(raw: String?): String {
            val base = raw.orEmpty().substringAfterLast('/')
            val cleaned = base.replace('\\', '_').filter { it !in "/:*?\"<>|" }
            return cleaned.ifBlank { "picked" }
        }

        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }

        fun label(status: Status): String = when (status) {
            Status.NEW -> "New"
            Status.TOO_LARGE -> "Too large — not read"
            Status.UNSUPPORTED -> "Unsupported type"
            Status.MISSING -> "File gone"
        }
    }

    fun ensureDir() { root.mkdirs() }

    fun path(): String = root.absolutePath

    fun scan(): List<Item> {
        ensureDir()
        val known = loadIndex().associateBy { it.id }
        val cap = maxBytes().coerceIn(1024L, 1024L * 1024 * 1024)
        val cutoff = now() - SETTLE_MS
        val out = mutableListOf<Item>()
        var added = 0
        for (f in root.listFiles().orEmpty().sortedBy { it.name }) {
            if (!f.isFile || f.name.startsWith(".") || f.lastModified() > cutoff) continue
            val hash = sha256(f)
            val id = "$hash:${f.name}"
            val old = known[id]
            if (old == null && added >= MAX_PER_SCAN) continue
            val ext = f.name.substringAfterLast('.', "").lowercase()
            val status = when {
                f.length() > cap -> Status.TOO_LARGE
                ext !in TEXT_EXT -> Status.UNSUPPORTED
                else -> Status.NEW
            }
            out += Item(
                id = id,
                name = f.name,
                size = f.length(),
                sha256 = hash,
                addedAt = old?.addedAt ?: f.lastModified(),
                readAt = old?.readAt,
                status = status,
            )
            if (old == null) added++
        }
        for ((id, old) in known) if (out.none { it.id == id }) out += old.copy(status = Status.MISSING)
        saveIndex(out)
        return out
    }

    fun read(id: String): ReadResult {
        val item = loadIndex().firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("unknown drop id: $id")
        check(item.status == Status.NEW) { "this file is ${item.status}: ${item.name}" }
        val f = File(root, item.name)
        check(f.isFile && f.length() <= maxBytes()) { "the file is no longer readable: ${item.name}" }
        val buffer = CharArray(MAX_CHARS + 1)
        val count = f.bufferedReader(Charsets.UTF_8).use { it.read(buffer, 0, buffer.size) }
        val truncated = count > MAX_CHARS
        val text = if (count <= 0) "" else String(buffer, 0, minOf(count, MAX_CHARS))
        markRead(id)
        return ReadResult(text, truncated)
    }

    fun markRead(id: String) {
        val stamp = now()
        val next = loadIndex().map { if (it.id == id) it.copy(readAt = stamp) else it }
        saveIndex(next)
    }

    private fun sha256(f: File): String = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    private fun loadIndex(): List<Item> = runCatching {
        val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Item(
                id = o.getString("id"),
                name = o.getString("name"),
                size = o.getLong("size"),
                sha256 = o.optString("sha256"),
                addedAt = o.getLong("addedAt"),
                readAt = if (o.isNull("readAt")) null else o.getLong("readAt"),
                status = runCatching { Status.valueOf(o.getString("status")) }.getOrDefault(Status.NEW),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveIndex(items: List<Item>) {
        val arr = JSONArray()
        for (i in items) arr.put(
            JSONObject()
                .put("id", i.id)
                .put("name", i.name)
                .put("size", i.size)
                .put("sha256", i.sha256)
                .put("addedAt", i.addedAt)
                .put("readAt", i.readAt)
                .put("status", i.status.name),
        )
        val tmp = File(indexFile.path + ".tmp")
        tmp.writeText(arr.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(arr.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }
}
