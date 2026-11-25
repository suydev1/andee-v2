package net.kuafuai.andee.brain

import com.sun.net.httpserver.HttpServer
import net.kuafuai.andee.config.BrainConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Reading a file poisons the rest of the turn, even though it began as a user turn. */
class FileReadTrustLatchTest {
    private val calls = mutableListOf<Pair<String, Boolean>>()
    private var n = 0
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/chat/completions") { ex ->
            ex.requestBody.readBytes()
            val step = synchronized(this) { ++n }
            val msg = if (step == 1) {
                JSONObject().put("role", "assistant").put("content", "").put("tool_calls", JSONArray()
                    .put(JSONObject().put("id", "c1").put("type", "function").put("function",
                        JSONObject().put("name", "file_read").put("arguments", "{\"path\":\"/x\"}")))
                    .put(JSONObject().put("id", "c2").put("type", "function").put("function",
                        JSONObject().put("name", "vault_list").put("arguments", "{}"))))
            } else {
                JSONObject().put("role", "assistant").put("content", "读完了")
            }
            val out = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", msg)))
                .put("usage", JSONObject().put("prompt_tokens", 1).put("completion_tokens", 1)).toString().toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        start()
    }
    private val trace = File.createTempFile("trace", "").also { it.delete(); it.mkdirs() }
    private val done = CountDownLatch(1)
    private val tools = JSONArray()
        .put(JSONObject().put("name", "file_read").put("method", "file.read")
            .put("description", "read").put("parameters", JSONObject().put("type", "object")))
        .put(JSONObject().put("name", "vault_list").put("method", "device.vault.list")
            .put("description", "vault").put("parameters", JSONObject().put("type", "object")))
    private val host = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(BrainHost::class.java)) { _, m, args ->
        when (m.name) {
            "text" -> args[0].toString()
            "locale" -> Locale.ENGLISH
            "maxSteps" -> 10
            "basePrompt" -> LocalPrompt.TEXT
            "toolLists" -> listOf(tools)
            "persona", "platformPrompt", "notebookIndex", "scenesIndex", "pagesIndex", "topicsIndex",
            "gearIndex", "flowsIndex", "planIndex", "friendsIndex", "agentIndex", "selfIndex", "stageIndex", "wikiIndex", "tasksIndex", "peopleIndex", "reviewRecord", "guard" -> ""
            "scrollback" -> emptyList<ScrollRow>()
            "methodOf" -> when (args[0]) {
                "file_read" -> "file.read"
                "vault_list" -> "device.vault.list"
                else -> null
            }
            "dispatch" -> {
                synchronized(calls) { calls += args[0] as String to args[4] as Boolean }
                JSONObject().put("ok", true)
            }
            "traceDir" -> trace
            "todoStillActive" -> true
            else -> null
        }
    } as BrainHost
    private val brain = LocalBrain(
        BrainConfig(BrainConfig.BRAIN_LOCAL, "key", "http://127.0.0.1:${server.address.port}", "m", false, "low"),
        host, onProgress = {}, onFinal = { done.countDown() },
    )

    @After fun tearDown() { brain.stop(); server.stop(0) }

    @Test fun `the read stays a user call and the rest of the turn is untrusted`() {
        brain.wake("read the file")
        assertTrue(done.await(20, TimeUnit.SECONDS))
        val snapshot = synchronized(calls) { calls.toList() }
        assertEquals(listOf("file.read" to false, "device.vault.list" to true), snapshot)
    }
}
