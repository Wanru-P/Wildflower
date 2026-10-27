package com.floralens

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.culture.CulturePolicy
import com.floralens.data.culture.NatureExpressionRepository
import com.floralens.data.culture.NatureServiceConfig
import com.floralens.data.culture.NatureServiceEndpoints
import com.floralens.data.db.entity.ObservationEntity
import com.floralens.data.db.entity.PlantEntity
import com.floralens.domain.model.Species
import com.floralens.domain.culture.CultureIdentity
import com.floralens.domain.culture.CultureIdentityResolver
import com.floralens.domain.culture.ExpressionSource
import com.floralens.domain.culture.NatureExpression
import com.floralens.domain.culture.NatureTextPolicy
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Real MainActivity routes and Room records; accepts an already populated garden.
 * Requires completed onboarding, leaving existing settings and permissions intact.
 * cultureNatureUiLive=true opts into one Debug CloudBase generation through UI consent;
 * otherwise no live requests or service changes. Private service preferences and
 * affected caches/options are restored. Screenshot evidence is retained in a unique
 * run folder. Only cultureVisual=true enables wm overrides; never target signedRelease.
 */
class CultureUnifiedJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val visual get() = InstrumentationRegistry.getArguments().getString("cultureVisual") == "true"
    private val natureLive get() = InstrumentationRegistry.getArguments().getString("cultureNatureUiLive") == "true"
    private var generationAttempted = false
    private val runId = UUID.randomUUID().toString()
    private val evidence = "/sdcard/Download/wildflower-culture-unified/$runId"
    private val unknownName = "尚待认识的植物"
    private val referenceNote = "界面回归授权素材，不是野外实拍。"

    private data class Geometry(val sizeOverride: String?, val densityOverride: String?)
    private data class Viewport(val label: String, val width: Int, val height: Int, val density: Int)
    private data class ArchiveSnapshot(
        val plants: List<PlantEntity>,
        val histories: Map<Long, List<ObservationEntity>>,
        val photoHashes: Map<String, String?>,
    )

    // Preserve encrypted service values without decrypting or logging backup objects.
    private class PreferenceBackup(val name: String, val keys: Set<String>?, val values: Map<String, Any?>)
    private class CacheBackup(val file: File, val bytes: ByteArray?, val modified: Long)
    private class NatureBackup(
        val preferences: List<PreferenceBackup>,
        val caches: List<CacheBackup>,
        val identityFiles: Set<File>,
    )

    private fun backupPreferences(name: String, keys: Set<String>? = null): PreferenceBackup {
        val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            .filterKeys { keys == null || it in keys }
            .mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
        return PreferenceBackup(name, keys, values)
    }

    private fun backupNature(identity: CultureIdentity): NatureBackup {
        val directory = File(context.filesDir, "nature-expressions-v1")
        val identityFiles = listOf("quiet", "journal").flatMap { style ->
            val key = NatureTextPolicy.cacheKey(identity, style)
            listOf("", ".bak", ".new").map { File(directory, key + it) }
        }.toSet()
        // Generation's cache limit can evict unrelated entries. Preserve those bytes too.
        val existing = directory.listFiles().orEmpty().filter {
            it.isFile && it.name.matches(Regex("[0-9a-f]{64}(\\.bak|\\.new)?"))
        }
        return NatureBackup(
            listOf(backupPreferences("nature-service-private"),
                backupPreferences("nature-expression-options", setOf(identity.key))),
            (existing + identityFiles).distinct().map {
                CacheBackup(it, if (it.exists()) it.readBytes() else null, it.lastModified())
            },
            identityFiles,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun restorePreferences(backup: PreferenceBackup) {
        val preferences = context.getSharedPreferences(backup.name, Context.MODE_PRIVATE)
        val keys = backup.keys ?: (preferences.all.keys + backup.values.keys)
        val editor = preferences.edit()
        keys.forEach { key ->
            when (val value = backup.values[key]) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
                else -> error("Unsupported preference type")
            }
        }
        check(editor.commit()) { "Could not restore private nature preferences" }
        // Boolean comparison avoids putting any preference values in assertion output.
        check(preferences.all.filterKeys { backup.keys == null || it in backup.keys } == backup.values) {
            "Private nature preferences were not restored"
        }
    }

    private fun restoreCache(backup: CacheBackup, identityFile: Boolean) {
        if (backup.bytes == null) {
            if (identityFile) check(!backup.file.exists() || backup.file.delete()) { "Could not remove test cache" }
        } else {
            // Restore the scoped identity exactly, and replace unrelated cache entries only if evicted.
            if (identityFile || !backup.file.exists()) {
                check(backup.file.parentFile!!.isDirectory || backup.file.parentFile!!.mkdirs())
                backup.file.writeBytes(backup.bytes)
                check(backup.file.setLastModified(backup.modified))
            }
            check(backup.file.readBytes().contentEquals(backup.bytes)) { "Existing nature cache changed" }
        }
    }

    private suspend fun configureAnonymousCloudBaseSession() = withContext(Dispatchers.IO) {
        check(natureLive && BuildConfig.DEBUG) { "Live Hilt UI journey requires explicit opt-in and Debug" }
        val base = NatureServiceEndpoints.base(BuildConfig.NATURE_PROXY_ORIGIN)
        check(NatureServiceConfig.validUrl(base) && URI(base).scheme == "https" &&
            URI(base).host.endsWith(".app.tcloudbase.com")) { "Expected the configured HTTPS CloudBase service" }
        val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
        try {
            // The scoped token exists only inside Android memory and encrypted app preferences.
            val request = Request.Builder().url(NatureServiceEndpoints.endpoint(base, "session"))
                .post(JSONObject().put("installId", UUID.randomUUID().toString()).toString()
                    .toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Anonymous session HTTP ${response.code}" }
                val bytes = response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(2049)
                    var count = 0
                    while (count < buffer.size) {
                        val read = input.read(buffer, count, buffer.size - count)
                        if (read < 0) break
                        count += read
                    }
                    buffer.copyOf(count)
                } ?: error("Missing session response")
                check(bytes.size <= 2048) { "Oversized session response" }
                val session = JSONObject(String(bytes, Charsets.UTF_8))
                val token = session.getString("token")
                check(token.length in 50..256 && !token.startsWith("sk-") && token.none(Char::isWhitespace)) {
                    "Invalid anonymous session credential"
                }
                val now = System.currentTimeMillis()
                check(session.getLong("expiresAt") in (now + 30_000L)..(now + 86_400_000L)) {
                    "Invalid anonymous session expiry"
                }
                NatureServiceConfig(context).save(base, token)
            }
        } catch (error: Exception) {
            // Do not propagate a parser/HTTP exception that could contain response credentials.
            throw AssertionError("Android anonymous session failed (${error.javaClass.simpleName})")
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun assertAiText(expression: NatureExpression) {
        scrollCultureTo(hasTestTag("nature-source"))
        ui.onNodeWithTag("nature-source").assertIsDisplayed().assertTextEquals(ExpressionSource.AI.label)
        scrollCultureTo(hasTestTag("nature-text"))
        ui.onNodeWithTag("nature-text").assertIsDisplayed().assertTextEquals(expression.text)
        ui.onNodeWithTag("nature-generate").assertIsEnabled()
        ui.onAllNodesWithTag("culture-excerpt").assertCountEquals(0)
    }

    private suspend fun generateUnknownThroughConsent(identity: CultureIdentity): NatureExpression {
        check(natureLive && BuildConfig.DEBUG && !generationAttempted)
        check(!identity.canGenerate && identity.natureMode == "general")
        val repository = NatureExpressionRepository(context, NatureServiceConfig(context))
        assertNull(repository.cached(identity, "quiet"))
        scrollCultureTo(hasText("静谧短诗"))
        ui.onNodeWithText("静谧短诗").performClick()
        ui.onNodeWithText("静谧短诗").assertIsSelected()
        scrollCultureTo(hasTestTag("nature-generate"))
        generationAttempted = true
        ui.onNodeWithTag("nature-generate").assertIsEnabled().performClick()
        waitText("同意并生成")
        ui.onNodeWithText("本次使用通用自然创作模式", substring = true).assertIsDisplayed()
        click("同意并生成") // The sole generation action; never retry or call repository.generate here.
        ui.waitUntil(45_000) {
            ui.onAllNodes(hasTestTag("nature-source") and hasText(ExpressionSource.AI.label))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() &&
                ui.onAllNodes(hasTestTag("nature-generate") and isEnabled())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        val expression = requireNotNull(repository.cached(identity, "quiet")) { "Missing real AI cache" }
        assertEquals(ExpressionSource.AI, expression.source)
        assertTrue(NatureTextPolicy.valid(expression))
        assertEquals(identity.key, expression.identityKey)
        assertEquals("quiet", repository.selectedStyle(identity))
        assertAiText(expression)
        return expression
    }

    private suspend fun assertAiRetained(identity: CultureIdentity, expected: NatureExpression) {
        val repository = NatureExpressionRepository(context, NatureServiceConfig(context))
        val cached = requireNotNull(repository.cached(identity, "quiet")) { "AI cache lost on reentry" }
        assertTrue(NatureTextPolicy.valid(cached))
        assertEquals(expected, cached)
        assertEquals("quiet", repository.selectedStyle(identity))
        assertAiText(cached)
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().readText()
    }

    private fun geometry(): Geometry {
        val size = shell("wm size")
        val density = shell("wm density")
        // Fail before changing anything if the device cannot report its original state.
        check(Regex("(?m)^Physical size: \\d+x\\d+\\s*$").containsMatchIn(size)) { size }
        check(Regex("(?m)^Physical density: \\d+\\s*$").containsMatchIn(density)) { density }
        return Geometry(
            Regex("(?m)^Override size: (\\d+x\\d+)\\s*$").find(size)?.groupValues?.get(1),
            Regex("(?m)^Override density: (\\d+)\\s*$").find(density)?.groupValues?.get(1),
        )
    }

    private fun resize(viewport: Viewport, scenario: ActivityScenario<MainActivity>) {
        check(visual) { "Device geometry requires cultureVisual=true" }
        shell("wm size ${viewport.width}x${viewport.height}")
        shell("wm density ${viewport.density}")
        ui.waitUntil(25_000) {
            val screenshotMatches = instrumentation.uiAutomation.takeScreenshot()?.let {
                try { it.width == viewport.width && it.height == viewport.height } finally { it.recycle() }
            } ?: false
            var configurationMatches = false
            scenario.onActivity {
                val configuration = it.resources.configuration
                val expectedDp = viewport.width * 160f / viewport.density
                configurationMatches = configuration.densityDpi == viewport.density &&
                    kotlin.math.abs(configuration.screenWidthDp - expectedDp) <= 2f
            }
            screenshotMatches && configurationMatches
        }
        waitTag("culture-list")
    }

    private fun waitTag(tag: String) = ui.waitUntil(25_000) {
        ui.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun waitText(text: String) = ui.waitUntil(25_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun click(text: String) {
        waitText(text)
        val node = ui.onNodeWithText(text)
        // Fixed navigation controls have no scrollable ancestor.
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.assertIsDisplayed().performClick()
    }

    private fun back() {
        Espresso.pressBack()
        ui.waitForIdle()
    }

    private fun shot(name: String) {
        ui.waitForIdle()
        shell("mkdir -p $evidence")
        val file = "$evidence/$name.png"
        val output = shell("screencap -p $file")
        check(output.isBlank()) { "Screenshot failed: $output" }
        check((shell("stat -c %s $file").trim().toLongOrNull() ?: 0L) > 0L) { "Missing screenshot: $file" }
    }

    private fun cultureReady() {
        waitTag("culture-list")
        ui.onNodeWithTag("culture-list").performScrollToIndex(0)
        ui.onNodeWithTag("culture-introduction").assertIsDisplayed()
    }

    private fun scrollCultureTo(matcher: SemanticsMatcher) {
        ui.onNodeWithTag("culture-list").performScrollToNode(matcher)
    }

    private fun openGinkgoFromExplore() {
        waitTag("explore-list")
        ui.onNodeWithTag("explore-list").performScrollToIndex(0)
        ui.onNodeWithText("发现自然，收藏四季。").assertIsDisplayed()
        // The real Explore ViewModel loads its cultural links asynchronously.
        ui.waitUntil(25_000) {
            runCatching {
                ui.onNodeWithTag("explore-list").performScrollToNode(hasText("银杏 · 阅读文化"))
                ui.onNodeWithText("银杏 · 阅读文化").assertIsDisplayed()
            }.isSuccess
        }
        click("银杏 · 阅读文化")
        cultureReady()
        ui.onNodeWithText("Ginkgo biloba").assertIsDisplayed()
    }

    private fun openUnknownFromGarden(owner: Long) {
        waitTag("garden-grid")
        // Use the owned ID, since a preexisting plant may have the same nickname.
        ui.onNodeWithTag("garden-grid").performScrollToNode(hasTestTag("garden-plant-$owner"))
        ui.onNodeWithTag("garden-plant-$owner").performClick()
        waitText("我的植物档案")
        ui.onNodeWithTag("personal-journal").assertExists()
        click("阅读植物文化 →")
        cultureReady()
        ui.onNodeWithText(unknownName).assertIsDisplayed()
    }

    private fun assertUnknownGenerationAvailable() {
        ui.onAllNodesWithTag("culture-excerpt").assertCountEquals(0)
        scrollCultureTo(hasTestTag("nature-generate"))
        ui.onNodeWithTag("nature-generate").assertIsDisplayed().assertIsEnabled()
        // Assert availability only: clicking would enter the real generation workflow.
        ui.onAllNodesWithTag("culture-excerpt").assertCountEquals(0)
    }

    private fun captureCulture(kind: String, scenario: ActivityScenario<MainActivity>, excerpt: String? = null,
        expectedAi: NatureExpression? = null) {
        val viewports: List<Viewport?> = if (visual) listOf(
            Viewport("393", 1080, 2340, 440),
            Viewport("320", 880, 1904, 440),
            Viewport("800", 1600, 2400, 320),
        ) else listOf(null)
        viewports.forEach { viewport ->
            if (viewport != null) resize(viewport, scenario)
            val label = viewport?.label ?: "native"
            cultureReady()
            scrollCultureTo(hasText("自然观察"))
            ui.onNodeWithText("自然观察").assertIsDisplayed()
            ui.onNodeWithTag("culture-list").performScrollToIndex(0)
            shot("$kind-$label-introduction")
            if (excerpt != null) {
                scrollCultureTo(hasTestTag("culture-excerpt"))
                ui.onNodeWithTag("culture-excerpt").assertIsDisplayed().assertTextEquals(excerpt)
                ui.onNodeWithText("查看作品出处 ↗").assertHasClickAction()
                shot("$kind-$label-literature")
            } else {
                assertUnknownGenerationAvailable()
            }
            scrollCultureTo(hasText("AI 原创"))
            ui.onNodeWithText("AI 原创").assertIsDisplayed()
            ui.onNodeWithTag("nature-source").assertExists()
            if (expectedAi != null) assertAiText(expectedAi)
            shot("$kind-$label-expression")
            scrollCultureTo(hasTestTag("nature-generate"))
            ui.onNodeWithTag("nature-generate").assertIsDisplayed().assertIsEnabled()
            shot("$kind-$label-generation-available")
        }
    }

    private fun photoHash(uri: String): String? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }.getOrNull() // Existing unavailable photos are valid inputs; preserve their records too.

    private suspend fun snapshot(excluding: Long? = null): ArchiveSnapshot {
        val plants = access.database().plantDao().observeAll().first().filterNot { it.id == excluding }.sortedBy { it.id }
        val histories = plants.associate { it.id to access.database().archiveDao().history(it.id).sortedBy { row -> row.id } }
        val uris = (plants.mapNotNull { it.photoUri } + histories.values.flatten().mapNotNull { it.photoUri }).distinct()
        return ArchiveSnapshot(plants, histories, uris.associateWith(::photoHash))
    }

    @Test fun exploreAndOwnedUnknownUseProductionCultureRoutesAndPreserveExistingArchive(): Unit = runBlocking {
        assumeTrue("Complete onboarding first; this journey preserves existing settings and permissions.",
            access.settings().onboarded.first())
        val before = snapshot()
        val originalGeometry = if (visual) geometry() else null
        var owner: Long? = null
        var pendingCapture: String? = null
        var scenario: ActivityScenario<MainActivity>? = null
        val unknownIdentity = CultureIdentityResolver.resolve(key = Species.manual(unknownName).key)
        var natureBackup: NatureBackup? = null
        var failure: Throwable? = null
        suspend fun cleanup(action: suspend () -> Unit) {
            try { action() } catch (error: Throwable) {
                val primary = failure
                if (primary == null) failure = error else primary.addSuppressed(error)
            }
        }
        try {
            if (natureLive) check(BuildConfig.DEBUG) { "Live Hilt UI journey requires Debug" }
            val id = access.archive().collect("culture-unified-$runId", Species.manual(unknownName), unknownName, null, null)
            owner = id
            assertTrue(before.plants.none { it.id == id })
            val licensedBytes = context.assets.open("explore/ginkgo.jpg").use { it.readBytes() }
            val capture = access.captures().create(licensedBytes)
            pendingCapture = capture.id
            access.archive().add(id, capture.id, System.currentTimeMillis(), referenceNote)
            val ownedHistory = access.database().archiveDao().history(id)
            assertEquals(referenceNote, ownedHistory.single().note)
            val ownedUri = requireNotNull(ownedHistory.single().photoUri)
            val ownedPhotoHash = requireNotNull(photoHash(ownedUri))
            val expectedHash = MessageDigest.getInstance("SHA-256").digest(licensedBytes)
                .joinToString("") { "%02x".format(it) }
            assertEquals(expectedHash, ownedPhotoHash)
            assertEquals(Species.manual(unknownName).key, access.database().archiveDao().plant(id)!!.speciesKey)
            val ginkgo = requireNotNull(access.culture().readingContent("scientific:ginkgo biloba"))
            val excerpt = requireNotNull(CulturePolicy.readingWorks(ginkgo).single().excerpt)

            val launched = ActivityScenario.launch(MainActivity::class.java)
            scenario = launched
            openGinkgoFromExplore()
            captureCulture("known-ginkgo", launched, excerpt)
            back()
            waitTag("explore-list")
            openGinkgoFromExplore()
            scrollCultureTo(hasTestTag("culture-excerpt"))
            ui.onNodeWithTag("culture-excerpt").assertTextEquals(excerpt)
            back()
            waitTag("explore-list")
            click("花园")
            if (natureLive) {
                val backup = backupNature(unknownIdentity)
                natureBackup = backup // Install the finally guard before touching any existing state.
                backup.identityFiles.forEach { file ->
                    check(!file.exists() || file.delete()) { "Could not prepare the backed-up identity cache" }
                }
                configureAnonymousCloudBaseSession()
            }
            openUnknownFromGarden(id)
            val liveExpression = if (natureLive) generateUnknownThroughConsent(unknownIdentity) else null
            captureCulture(if (liveExpression == null) "unknown-personal" else "unknown-personal-ai",
                launched, expectedAi = liveExpression)

            // Both the page's return action and system back must retain the personal route.
            scrollCultureTo(hasText("返回植物笔记"))
            click("返回植物笔记")
            waitText("我的植物档案")
            click("阅读植物文化 →")
            cultureReady()
            assertUnknownGenerationAvailable()
            if (liveExpression != null) assertAiRetained(unknownIdentity, liveExpression)
            shot("unknown-personal-reentry")
            back()
            waitText("我的植物档案")
            back()
            openUnknownFromGarden(id)
            assertUnknownGenerationAvailable()
            if (liveExpression != null) assertAiRetained(unknownIdentity, liveExpression)
            back()
            waitText("我的植物档案")
            assertEquals(ownedHistory, access.database().archiveDao().history(id))
            assertEquals(ownedPhotoHash, photoHash(ownedUri))
            assertEquals(before, snapshot(excluding = id))
        } catch (error: Throwable) {
            failure = error
            runCatching { shot("failure") }
        } finally {
            cleanup { scenario?.close() }
            natureBackup?.let { backup ->
                backup.caches.forEach { cache ->
                    cleanup { restoreCache(cache, cache.file in backup.identityFiles) }
                }
                backup.preferences.forEach { preferences -> cleanup { restorePreferences(preferences) } }
            }
            // Attempt both restores independently, including after a resize/assertion failure.
            if (originalGeometry != null) {
                cleanup { shell("wm size ${originalGeometry.sizeOverride ?: "reset"}") }
                cleanup { shell("wm density ${originalGeometry.densityOverride ?: "reset"}") }
                cleanup { assertEquals(originalGeometry, geometry()) }
            }
            owner?.let { id ->
                cleanup {
                    // A unique collect operation created this owner; no name-based deletion.
                    check(before.plants.none { it.id == id })
                    access.database().archiveDao().history(id).forEach { observation ->
                        cleanup { access.archive().delete(id, observation.id) }
                    }
                    if (access.database().archiveDao().history(id).isEmpty()) access.plants().delete(id)
                    assertNull(access.database().archiveDao().plant(id))
                }
            }
            // Normally archive.add already consumed it; also cover partial fixture setup failure.
            pendingCapture?.let { captureId -> cleanup { access.captures().cancel(captureId) } }
            cleanup { assertEquals(before, snapshot()) }
        }
        failure?.let { throw it }
    }
}
