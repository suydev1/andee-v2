package net.kuafuai.andee.net

import android.content.Context
import android.os.SystemClock
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

    /**
     * The whole fallback chain shares this wall clock. Each OkHttp call keeps
     * 15 s connect/read caps, but the chain must finish well inside the hub's
     * 30 s request timeout, so Tavily, DDG and Wikipedia together get 25 s.
     */
    private const val SEARCH_BUDGET_MS = 25_000L
    private val json = "application/json; charset=utf-8".toMediaType()

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun clientFor(timeoutMs: Long): OkHttpClient = baseClient.newBuilder()
        .callTimeout(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        .build()

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
        topic: String, timeRange: String?, timeoutMs: Long,
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
        clientFor(timeoutMs).newCall(req).execute().use { res ->
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
        val deadline = SystemClock.elapsedRealtime() + SEARCH_BUDGET_MS
        var keyError: Exception? = null
        val key = VoiceConfig.tavilyKey(context)
        if (key.isNotBlank()) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining > 0) {
                try {
                    return tavily(key, query, n, topic, timeRange, remaining)
                } catch (e: Exception) {
                    keyError = e
                }
            }
        }
        val ddgBudget = deadline - SystemClock.elapsedRealtime()
        if (ddgBudget > 0) {
            val ddg = FreeSearch.ddg(query, n, ddgBudget)
            if (ddg.isNotEmpty()) return Response(ddg, null, "duckduckgo")
        }
        val wikiBudget = deadline - SystemClock.elapsedRealtime()
        if (wikiBudget > 0) {
            val wiki = FreeSearch.wikipedia(query, n, wikiBudget)
            if (wiki.isNotEmpty()) return Response(wiki, null, "wikipedia")
        }
        if (keyError != null) throw keyError
        return Response(emptyList(), null, "none")
    }
}
