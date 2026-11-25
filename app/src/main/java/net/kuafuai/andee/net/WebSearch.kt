package net.kuafuai.andee.net

import android.content.Context
import net.kuafuai.andee.config.VoiceConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WebSearch {
    data class Result(val title: String, val url: String, val content: String, val published: String)
    data class Response(val results: List<Result>, val credits: Int?, val source: String)

    private const val URL = "https://api.tavily.com/search"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    fun parse(body: String): Response {
        val o = JSONObject(body)
        val arr = o.optJSONArray("results") ?: org.json.JSONArray()
        val out = ArrayList<Result>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            out += Result(
                title = r.optString("title"),
                url = r.optString("url"),
                content = r.optString("content"),
                published = r.optString("published_date"),
            )
        }
        val credits = runCatching { o.getJSONObject("usage").optInt("credits") }.getOrNull()
        return Response(out, credits, "tavily")
    }

    private fun tavily(
        key: String, query: String, max: Int,
        topic: String, timeRange: String?,
    ): Response {
        val body = JSONObject()
            .put("query", query)
            .put("max_results", max.coerceIn(1, 10))
            .put("topic", if (topic == "news") "news" else "general")
            .put("include_answer", false)
            .put("include_raw_content", false)
            .put("include_images", false)
        if (!timeRange.isNullOrBlank()) body.put("time_range", timeRange)
        val req = Request.Builder()
            .url(URL)
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(json))
            .build()
        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            when (res.code) {
                401, 403 -> throw IllegalStateException("The web search key was rejected. Check it in Settings.")
                429 -> throw IllegalStateException("Web search is rate limited. Try again in a minute.")
                in 200..299 -> return parse(raw).copy(source = "tavily")
                else -> throw IllegalStateException("Web search failed (${res.code}).")
            }
        }
    }

    fun search(
        context: Context, query: String, maxResults: Int = 5,
        topic: String = "general", timeRange: String? = null,
    ): Response {
        val n = maxResults.coerceIn(1, 10)
        var keyError: Exception? = null
        val key = VoiceConfig.tavilyKey(context)
        if (key.isNotBlank()) {
            try {
                val r = tavily(key, query, n, topic, timeRange)
                return r.copy(source = "tavily")
            } catch (e: Exception) {
                keyError = e
            }
        }
        val ddg = FreeSearch.ddg(query, n)
        if (ddg.isNotEmpty()) return Response(ddg, null, "duckduckgo")
        val wiki = FreeSearch.wikipedia(query, n)
        if (wiki.isNotEmpty()) return Response(wiki, null, "wikipedia")
        if (keyError != null) throw keyError
        return Response(emptyList(), null, "none")
    }
}
