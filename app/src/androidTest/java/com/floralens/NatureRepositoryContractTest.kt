package com.floralens

import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.culture.*
import com.floralens.domain.culture.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

/** Explicit HTTP contract fixtures, NOT evidence of a real DeepSeek call. No Room writes. */
class NatureRepositoryContractTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val identity = CultureIdentityResolver.resolve(scientific = "Welwitschia mirabilis")
    private val text = "把目光交给一段安静，\n让每次停留都有新的意义。"
    private class Server : AutoCloseable {
        private val socket = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}/v1/nature"
        val requests = AtomicInteger()
        @Volatile var status = 200
        @Volatile var body = "{}"
        @Volatile var delayMs = 0L
        @Volatile var input: JSONObject? = null
        @Volatile var path: String? = null
        private val thread = Thread {
            while (!socket.isClosed) try { socket.accept().use { client ->
                client.soTimeout = 30_000
                val stream = client.getInputStream()
                val header = StringBuilder()
                while (!header.endsWith("\r\n\r\n") && header.length < 8192) { val b = stream.read(); if (b < 0) break; header.append(b.toChar()) }
                path = header.lineSequence().firstOrNull()?.split(' ')?.getOrNull(1)
                val size = Regex("(?i)Content-Length: (\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                val bytes = ByteArray(size)
                var read = 0
                while (read < size) { val n = stream.read(bytes, read, size-read); if (n < 0) break; read += n }
                input = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
                requests.incrementAndGet()
                Thread.sleep(delayMs)
                val payload = body.toByteArray()
                client.getOutputStream().apply {
                    write("HTTP/1.1 $status Result\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(payload); flush()
                }
            } } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
        override fun close() { socket.close(); thread.interrupt(); thread.join(1000) }
    }
    private fun fixture(action: suspend (NatureExpressionRepository, NatureServiceConfig, Server, File) -> Unit) = runBlocking {
        val prefs = context.getSharedPreferences("nature-service-private", 0)
        val previous = prefs.getString("encrypted", null)
        val cache = File(context.filesDir, "nature-expressions-v1/${NatureTextPolicy.cacheKey(identity, "quiet")}")
        val styleCache = File(context.filesDir, "nature-expressions-v1/${NatureTextPolicy.cacheKey(identity, "journal")}")
        val backups = (listOf(cache, styleCache) + listOf("quiet", "journal").map {
            File(context.filesDir, "nature-expressions-v1/${NatureTextPolicy.cacheKey(identity.copy(certain = false), it)}")
        }).associateWith { it.takeIf(File::exists)?.readBytes() }
        val options = context.getSharedPreferences("nature-expression-options", 0)
        val oldStyle = options.getString(identity.key, null)
        options.edit().remove(identity.key).commit()
        val config = NatureServiceConfig(context)
        try { backups.keys.forEach(File::delete); Server().use { server ->
            assertTrue("Debug loopback-only HTTP policy required", android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1"))
            config.save(server.url, "contract-only-proxy-token-0000000000")
            try { action(NatureExpressionRepository(context, config), config, server, cache) }
            catch (e: NatureServiceException) { throw AssertionError("Contract failure ${e.reason}/${e.diagnosticType}; received=${server.requests.get()}") }
        } } finally {
            backups.forEach { (file, old) -> if (old == null) file.delete() else { file.parentFile!!.mkdirs(); file.writeBytes(old) } }
            options.edit().apply { if (oldStyle == null) remove(identity.key) else putString(identity.key, oldStyle) }.commit()
            prefs.edit().apply { if (previous == null) remove("encrypted") else putString("encrypted", previous) }.commit()
        }
    }
    private fun response() = JSONObject().put("text", text).put("source", "ai_original").put("scientificName", identity.scientific).toString()
    @Test fun cloudbaseMountIsPreservedForNatureGeneration() = fixture { repo, config, server, _ ->
        config.save(server.url.replace("/v1/nature", "/wildflower-ai"), "contract-only-proxy-token-0000000000")
        server.body = response()
        assertEquals(ExpressionSource.AI, repo.generate(identity, force = true).source)
        assertEquals("/wildflower-ai/v1/nature", server.path)
    }

    @Test fun cacheHitForceRegeneratePersistenceAndPrivatePayload() = fixture { repo, config, server, cache ->
        server.body = response()
        assertEquals(ExpressionSource.LOCAL, repo.current(identity).source)
        assertEquals(0, server.requests.get())
        val generated = repo.generate(identity)
        assertEquals(ExpressionSource.AI, generated.source); assertEquals(text, generated.text)
        assertEquals(setOf("scientificName", "chineseName", "style"), server.input!!.keys().asSequence().toSet())
        assertEquals(identity.scientific, server.input!!.getString("scientificName"))
        assertEquals(generated, repo.generate(identity)); assertEquals(1, server.requests.get())
        assertEquals(generated, NatureExpressionRepository(context, config).current(identity))
        repo.generate(identity, force = true); assertEquals(2, server.requests.get())
        repo.selectStyle(identity, "journal")
        assertEquals(ExpressionSource.LOCAL, repo.current(identity).source)
        val styled = repo.generate(identity)
        assertEquals("journal", server.input!!.getString("style"))
        assertEquals(styled, NatureExpressionRepository(context, config).current(identity))
        repo.selectStyle(identity, "quiet")
        val expired = JSONObject(cache.readText()).put("created", System.currentTimeMillis() - NatureExpressionRepository.TTL - 1)
        cache.writeText(expired.toString()); assertEquals(ExpressionSource.LOCAL, repo.current(identity).source)
        cache.writeText("x".repeat(5000)); assertEquals(ExpressionSource.LOCAL, repo.current(identity).source)
    }
    @Test fun invalidAndHttpResponsesKeepPreviousValidCache() = fixture { repo, _, server, _ ->
        server.body = response(); val previous = repo.generate(identity)
        for ((code, value) in listOf(401 to "key failure", 429 to "rate", 500 to "provider error", 200 to "", 200 to "{}",
            200 to response().replace("ai_original", "ancient"), 200 to response().replace(identity.scientific!!, "Ginkgo biloba"),
            200 to JSONObject().put("text", "作者：李白\n这是一首古诗作品").put("source", "ai_original").put("scientificName", identity.scientific).toString(), 200 to "x".repeat(8193))) {
            server.status = code; server.body = value
            assertTrue("Must fail closed: HTTP $code", runCatching { repo.generate(identity, force = true) }.exceptionOrNull() is NatureServiceException)
            assertEquals(previous, repo.current(identity))
        }
    }
    @Test fun duplicateClickAndCanceledPageDoNotCacheLateOutput() = fixture { repo, _, server, _ ->
        server.body = response(); server.delayMs = 900
        val pending = CoroutineScope(currentCoroutineContext()).async { repo.generate(identity, force = true) }
        withTimeout(5000) { while (server.requests.get() == 0) delay(10) }
        assertEquals("busy", (runCatching { repo.generate(identity, force = true) }.exceptionOrNull() as NatureServiceException).reason)
        pending.cancelAndJoin()
        assertNull(repo.cached(identity))
        server.delayMs = 0
        assertEquals(ExpressionSource.AI, repo.generate(identity).source)
    }
    @Test fun unconfiguredAndOfflineRetainTextWithoutBarringUnknownIdentity() = fixture { repo, config, server, _ ->
        config.clear()
        assertEquals("configuration", (runCatching { repo.generate(identity) }.exceptionOrNull() as NatureServiceException).reason)
        val unknown = CultureIdentityResolver.resolve(scientific = "Ginkgo biloba", certain = false)
        assertEquals("configuration", (runCatching { repo.generate(unknown) }.exceptionOrNull() as NatureServiceException).reason)
        assertEquals(ExpressionSource.LOCAL, repo.current(unknown).source)
        config.save(server.url, "contract-only-proxy-token-0000000000"); server.close()
        assertEquals("network", (runCatching { repo.generate(identity) }.exceptionOrNull() as NatureServiceException).reason)
        assertEquals(ExpressionSource.LOCAL, repo.current(identity).source)
    }
    @Test fun genericModeBothStylesCacheAndOfflineRegenerationNeverSendCandidateOrNickname() = fixture { repo, config, server, _ ->
        val unknown = identity.copy(certain = false)
        server.body = JSONObject().put("text", text).put("source", "ai_original")
            .put("scientificName", "").put("mode", "general").toString()
        val generated = repo.generate(unknown, "quiet", force = true)
        assertEquals(ExpressionSource.AI, generated.source)
        assertEquals(setOf("mode", "scientificName", "chineseName", "style"), server.input!!.keys().asSequence().toSet())
        assertEquals("general", server.input!!.getString("mode"))
        assertEquals("", server.input!!.getString("scientificName"))
        assertEquals("", server.input!!.getString("chineseName"))
        assertNull(repo.cached(identity))
        assertEquals(generated, NatureExpressionRepository(context, config).generate(unknown, "quiet"))
        val calls = server.requests.get()
        repo.generate(unknown, "journal", force = true)
        assertEquals("journal", server.input!!.getString("style"))
        assertEquals(calls + 1, server.requests.get())
        server.close()
        assertEquals("network", (runCatching { repo.generate(unknown, "quiet", force = true) }.exceptionOrNull() as NatureServiceException).reason)
        assertEquals(generated, repo.cached(unknown, "quiet"))
    }
    @Test fun configurationRejectsProviderKeyInsecureEndpointAndRestoresSafely() = fixture { _, config, _, _ ->
        assertTrue(runCatching { config.save("https://api.deepseek.com/chat/completions", "proxy-scoped-test-token-00000000000") }.isFailure)
        assertTrue(runCatching { config.save("https://example.org/v1/nature", "sk-not-a-real-provider-key-0000000000") }.isFailure)
        listOf("http://example.org/v1/nature", "https://user:password@example.org/", "https://example.org/?token=value", "https://example.org/#secret").forEach { assertFalse(NatureServiceConfig.validUrl(it)) }
        config.save("https://example.org/v1/nature", "proxy-scoped-test-token-00000000000")
        assertEquals("https://example.org/v1/nature", NatureServiceConfig(context).read()!!.url)
        val encrypted = context.getSharedPreferences("nature-service-private", 0).getString("encrypted", "")!!
        assertFalse(encrypted.contains("proxy-scoped")); assertFalse(encrypted.contains("example.org"))
    }
    @Test fun realReadTimeoutDoesNotCacheAnError() = fixture { repo, _, server, _ ->
        server.body = response(); server.delayMs = 26_000
        val failure = runCatching { repo.generate(identity) }.exceptionOrNull() as NatureServiceException
        assertEquals("network", failure.reason)
        assertEquals("SocketTimeoutException", failure.diagnosticType)
        assertEquals(1, server.requests.get()); assertNull(repo.cached(identity))
        assertEquals(ExpressionSource.LOCAL, repo.current(identity).source)
    }
}
