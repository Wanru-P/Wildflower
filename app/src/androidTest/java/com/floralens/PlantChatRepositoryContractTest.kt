package com.floralens

import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.chat.PlantChatConsentStore
import com.floralens.data.chat.PlantChatException
import com.floralens.data.chat.PlantChatRepository
import com.floralens.data.culture.NatureServiceConfig
import com.floralens.domain.chat.PlantChatContext
import com.floralens.domain.chat.PlantChatRole
import com.floralens.domain.chat.PlantChatSource
import com.floralens.domain.chat.PlantChatTurn
import com.floralens.domain.culture.CultureIdentityResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

/** Local HTTP fixtures validate the Android contract; they are not DeepSeek evidence. */
class PlantChatRepositoryContractTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val identity = CultureIdentityResolver.resolve(scientific = "Ginkgo biloba")
    private val chatContext = PlantChatContext(PlantChatSource.SPECIES, identity, "银杏", "Ginkgo biloba", "private-photo-uri")
    private val answer = "可以先记录整体轮廓，再留意不同时间的变化。"

    private class Server : AutoCloseable {
        private val socket = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val configuredUrl = "http://127.0.0.1:${socket.localPort}/v1/nature"
        val requests = AtomicInteger()
        @Volatile var status = 200
        @Volatile var body = "{}"
        @Volatile var delayMs = 0L
        @Volatile var bodyDelayMs = 0L
        @Volatile var input: JSONObject? = null
        @Volatile var path: String? = null
        private val thread = Thread {
            while (!socket.isClosed) try { socket.accept().use { client ->
                client.soTimeout = 35_000
                val stream = client.getInputStream(); val header = StringBuilder()
                while (!header.endsWith("\r\n\r\n") && header.length < 16384) { val byte = stream.read(); if (byte < 0) break; header.append(byte.toChar()) }
                path = header.lineSequence().firstOrNull()?.split(' ')?.getOrNull(1)
                val size = Regex("(?i)Content-Length: (\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                val bytes = ByteArray(size); var read = 0
                while (read < size) { val count = stream.read(bytes, read, size - read); if (count < 0) break; read += count }
                input = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull(); requests.incrementAndGet()
                Thread.sleep(delayMs)
                val payload = body.toByteArray()
                client.getOutputStream().apply {
                    write("HTTP/1.1 $status Result\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); flush()
                    Thread.sleep(bodyDelayMs)
                    write(payload); flush()
                }
            } } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
        override fun close() { socket.close(); thread.interrupt(); thread.join(1000) }
    }

    private fun fixture(action: suspend (PlantChatRepository, NatureServiceConfig, Server) -> Unit) = runBlocking {
        val prefs = context.getSharedPreferences("nature-service-private", 0)
        val previous = prefs.getString("encrypted", null)
        val config = NatureServiceConfig(context)
        try { Server().use { server ->
            config.save(server.configuredUrl, "chat-contract-proxy-token-0000000000")
            action(PlantChatRepository(config), config, server)
        } } finally {
            prefs.edit().apply { if (previous == null) remove("encrypted") else putString("encrypted", previous) }.commit()
        }
    }

    private fun response() = JSONObject().put("answer", answer).put("source", "ai_answer").put("version", 1).toString()

    @Test fun exactPrivatePayloadHistoryAndIndependentEndpoint() = fixture { repository, _, server ->
        server.body = response()
        val history = listOf(PlantChatTurn(PlantChatRole.USER, "怎样开始观察？"), PlantChatTurn(PlantChatRole.ASSISTANT, "先看整体轮廓。"))
        assertEquals(answer, repository.reply(chatContext, history, "下一步记录什么？"))
        assertEquals("/v1/chat", server.path)
        val input = server.input!!
        assertEquals(setOf("scientificName", "chineseName", "certain", "question", "history"), input.keys().asSequence().toSet())
        val serialized = input.toString()
        listOf("private-photo-uri", "latitude", "longitude", "observation", "nickname", "account").forEach { assertFalse(serialized.contains(it)) }
        assertEquals(2, input.getJSONArray("history").length())
    }
    @Test fun cloudbaseMountedPathIsNotReplacedByTheHostRoot() = fixture { repository, config, server ->
        config.save(server.configuredUrl.replace("/v1/nature", "/wildflower-ai"), "chat-contract-proxy-token-0000000000")
        server.body = response()
        assertEquals(answer, repository.reply(chatContext, emptyList(), "怎样观察？"))
        assertEquals("/wildflower-ai/v1/chat", server.path)
    }

    @Test fun viewModelMainDispatcherReadsDelayedNetworkBodyOffTheUiThread() = fixture { repository, _, server ->
        server.body = response(); server.bodyDelayMs = 350
        withContext(Dispatchers.Main) {
            val previous = android.os.StrictMode.getThreadPolicy()
            android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder(previous).detectNetwork().penaltyDeathOnNetwork().build())
            try { assertEquals(answer, repository.reply(chatContext, emptyList(), "怎样观察？")) }
            finally { android.os.StrictMode.setThreadPolicy(previous) }
        }
    }

    @Test fun errorsAreTypedAndProviderBodiesAreNeverReturned() = fixture { repository, _, server ->
        for ((status, reason) in listOf(401 to "authorization", 403 to "authorization", 429 to "rate", 500 to "network")) {
            server.status = status; server.body = "private provider failure"
            val failure = runCatching { repository.reply(chatContext, emptyList(), "怎样观察？") }.exceptionOrNull() as PlantChatException
            assertEquals(reason, failure.reason); assertFalse(failure.toString().contains("private provider failure"))
        }
        server.status = 200
        for (invalid in listOf("{}", JSONObject().put("answer", answer).put("source", "mock").toString(),
            JSONObject().put("answer", "https://example.org").put("source", "ai_answer").toString(), "x".repeat(32769))) {
            server.body = invalid
            assertEquals("response", (runCatching { repository.reply(chatContext, emptyList(), "怎样观察？") }.exceptionOrNull() as PlantChatException).reason)
        }
    }

    @Test fun unconfiguredInvalidInputAndConsentAreIndependent() = fixture { repository, config, server ->
        config.clear()
        assertEquals("configuration", (runCatching { repository.reply(chatContext, emptyList(), "怎样观察？") }.exceptionOrNull() as PlantChatException).reason)
        config.save(server.configuredUrl, "chat-contract-proxy-token-0000000000")
        assertEquals("input", (runCatching { repository.reply(chatContext, emptyList(), " ") }.exceptionOrNull() as PlantChatException).reason)
        assertEquals(0, server.requests.get())
        val consent = PlantChatConsentStore(context); consent.revoke(); assertFalse(consent.accepted()); consent.grant(); assertTrue(consent.accepted()); consent.revoke()
    }

    @Test fun duplicateAndCancellationDoNotProduceASecondSuccess() = fixture { repository, config, server ->
        server.body = response(); server.delayMs = 1000
        val pending = CoroutineScope(currentCoroutineContext()).async { repository.reply(chatContext, emptyList(), "怎样观察？") }
        withTimeout(5000) { while (server.requests.get() == 0) delay(10) }
        assertEquals("busy", (runCatching { repository.reply(chatContext, emptyList(), "再次提交") }.exceptionOrNull() as PlantChatException).reason)
        pending.cancelAndJoin(); assertTrue(pending.isCancelled)
        // A cancelled HTTP/1.1 socket can remain unusable on some emulator images.
        // A fresh fixture proves the repository released its busy guard and accepts a new call.
        Server().use { fresh ->
            fresh.body = response()
            config.save(fresh.configuredUrl, "chat-contract-proxy-token-0000000000")
            val freshResult = runCatching { repository.reply(chatContext, emptyList(), "重新提问") }
            val failure = freshResult.exceptionOrNull() as? PlantChatException
            assertTrue("fresh request failed: ${failure?.reason}/${failure?.diagnosticType}", freshResult.isSuccess)
            assertEquals(answer, freshResult.getOrThrow())
        }
    }

    @Test fun disconnectedServerReturnsNetworkWithoutLocalMockAnswer() = fixture { repository, _, server ->
        server.close()
        val failure = runCatching { repository.reply(chatContext, emptyList(), "怎样观察？") }.exceptionOrNull() as PlantChatException
        assertEquals("network", failure.reason)
        assertNull(failure.cause)
    }
}
