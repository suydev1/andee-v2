package net.kuafuai.andee.device

import java.io.File

/** One bounded slice of a text file, never the whole file. */
object FileReader {
    const val MAX_LINES = 400
    const val MAX_CHARS = 20_000

    data class Result(
        val text: String,
        val fromLine: Int,
        val toLine: Int,
        val lineCount: Int,
        val truncated: Boolean,
        val nextFromLine: Int?,
    )

    /**
     * Reads lines [fromLine]..[toLine], 1-based and inclusive, with a hard
     * [MAX_LINES] window and a [MAX_CHARS] budget for the returned text.
     * Lines are consumed one at a time and the reader is closed on every path.
     * A line that cannot fit is left unread rather than clipped.
     */
    fun read(file: File, fromLine: Int, toLine: Int): Result {
        require(fromLine >= 1) { "from_line must be at least 1." }
        require(toLine >= fromLine) { "to_line must be at least from_line." }
        val end = minOf(toLine, fromLine + MAX_LINES - 1)
        val text = StringBuilder()
        var count = 0
        var truncated = false
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            var lineNo = 0
            while (true) {
                val line = reader.readLine() ?: break
                lineNo++
                if (lineNo < fromLine) continue
                if (lineNo > end) { truncated = true; break }
                check(line.length <= MAX_CHARS) {
                    "Line $lineNo alone exceeds the $MAX_CHARS character cap, so nothing was read. " +
                        "Ask the user for a file with shorter lines."
                }
                val piece = if (count == 0) line else "\n$line"
                if (text.length + piece.length > MAX_CHARS) { truncated = true; break }
                text.append(piece)
                count++
            }
        }
        val last = fromLine + count - 1
        return Result(
            text = text.toString(),
            fromLine = fromLine,
            toLine = last,
            lineCount = count,
            truncated = truncated,
            nextFromLine = if (truncated) last + 1 else null,
        )
    }
}
