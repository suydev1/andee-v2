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
    data class Response(val results: List<Result>, val credits: Int?)

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
        return Response(out, credits)
    }

    fun search(
        context: Context, query: String, maxResults: Int = 5,
        topic: String = "general", timeRange: String? = null,
    ): Response {
        val key = VoiceConfig.tavilyKey(context)
        check(key.isNotBlank()) { "No web search key is set. Add one in Settings → Web search." }
        val body = JSONObject()
            .put("query", query)
            .put("max_results", maxResults.coerceIn(1, 10))
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
                in 200..299 -> return parse(raw)
                else -> throw IllegalStateException("Web search failed (${res.code}).")
            }
        }
    }
}
