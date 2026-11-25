package net.kuafuai.andee.device

object AppChoice {
    fun preferred(mime: String): List<String> = when {
        mime == "application/pdf" -> listOf("com.google.android.apps.pdfviewer", "com.adobe.reader")
        mime.startsWith("image/") -> listOf("com.google.android.apps.photos", "com.android.gallery3d", "com.miui.gallery")
        mime.startsWith("audio/") -> listOf("com.google.android.music", "com.android.music")
        mime.startsWith("video/") -> listOf("com.google.android.apps.youtube", "com.android.gallery3d")
        mime.startsWith("text/") || mime.contains("word") || mime.contains("document") ->
            listOf("com.google.android.apps.docs", "com.microsoft.office.officelandroid")
        else -> emptyList()
    }

    fun choose(candidates: List<String>, mime: String): String? {
        for (p in preferred(mime)) if (p in candidates) return p
        return candidates.firstOrNull()
    }
}
