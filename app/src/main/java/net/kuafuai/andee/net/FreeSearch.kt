package net.kuafuai.andee.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object FreeSearch {
    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun clientFor(timeoutMs: Long): OkHttpClient = baseClient.newBuilder()
        .callTimeout(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        .build()

    private fun strip(html: String): String = html
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .trim()

    fun parseDdg(html: String): List<WebSearch.Result> {
        val links = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snippets = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snips = snippets.findAll(html).map { strip(it.groupValues[1]) }.toList()
        return links.findAll(html).mapIndexed { i, m ->
            var url = m.groupValues[1].replace("&amp;", "&")
            val uddg = Regex("""[?&]uddg=([^&]+)""").find(url)?.groupValues?.get(1)
            if (uddg != null) url = runCatching { URLDecoder.decode(uddg, "UTF-8") }.getOrDefault(url)
            if (url.startsWith("//")) url = "https:$url"
            WebSearch.Result(
                title = strip(m.groupValues[2]),
                url = url,
                content = snips.getOrNull(i).orEmpty(),
                published = "",
            )
        }.toList()
    }

    fun parseWikipedia(json: String): List<WebSearch.Result> {
        val arr = JSONObject(json).optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        val out = ArrayList<WebSearch.Result>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val title = o.optString("title")
            val enc = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
            out += WebSearch.Result(
                title = title,
                url = "https://en.wikipedia.org/wiki/$enc",
                content = strip(o.optString("snippet")),
                published = "",
            )
        }
        return out
    }

    private fun get(url: String, timeoutMs: Long): String = clientFor(timeoutMs).newCall(
        Request.Builder()
            .url(url).header("User-Agent", "Andee/1.0 (on-device assistant)")
            .build(),
    ).execute().use { res ->
        check(res.isSuccessful) { "Search endpoint answered ${res.code}." }
        res.body?.string().orEmpty()
    }

    fun ddg(query: String, max: Int, timeoutMs: Long): List<WebSearch.Result> = runCatching {
        val q = URLEncoder.encode(query, "UTF-8")
        parseDdg(get("https://html.duckduckgo.com/html/?q=$q", timeoutMs)).take(max)
    }.getOrDefault(emptyList())

    fun wikipedia(query: String, max: Int, timeoutMs: Long): List<WebSearch.Result> = runCatching {
        val q = URLEncoder.encode(query, "UTF-8")
        parseWikipedia(
            get(
                "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=$max&srsearch=$q",
                timeoutMs,
            ),
        )
    }.getOrDefault(emptyList())
}
