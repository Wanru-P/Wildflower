package com.floralens

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.culture.NatureExpressionRepository
import com.floralens.data.culture.NatureServiceConfig
import com.floralens.domain.culture.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit real-provider opt-in, four requests total; no Room, photos, notes or locations touched. */
class CultureNatureReleaseTest {
    @Test fun actualReleaseBothIdentityModesAndStylesUseRealDeepSeekAndPersistentCache() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("cultureNatureLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        val repo = NatureExpressionRepository(context, NatureServiceConfig(context))
        val known = CultureIdentityResolver.resolve(scientific = "Ginkgo biloba")
        val general = CultureIdentityResolver.resolve("unknown:culture-release-acceptance")
        val allCases = listOf(known, general).flatMap { identity -> listOf("quiet", "journal").map { identity to it } }
        val selectedCase = InstrumentationRegistry.getArguments().getString("cultureNatureCase")
        val cases = allCases.filter { (identity, style) -> selectedCase == null || "${identity.natureMode}/$style" == selectedCase }
        require(cases.isNotEmpty()) { "Unknown live verification case" }
        val backups = cases.associate { (identity, style) ->
            File(context.filesDir, "nature-expressions-v1/${NatureTextPolicy.cacheKey(identity, style)}")
                .let { it to it.takeIf(File::exists)?.readBytes() }
        }
        val results = JSONArray()
        val report = JSONObject().put("realProvider", true).put("results", results).put("selectedCase", selectedCase ?: "all")
        try {
            for ((identity, style) in cases) {
                val started = System.currentTimeMillis()
                val expression = repo.generate(identity, style, force = true)
                assertEquals(ExpressionSource.AI, expression.source)
                assertTrue(NatureTextPolicy.valid(expression))
                assertEquals(expression, NatureExpressionRepository(context, repo.config).current(identity, style))
                // Cache hit must not generate another paid request.
                assertEquals(expression, repo.generate(identity, style, force = false))
                results.put(JSONObject().put("mode", identity.natureMode).put("style", style)
                    .put("text", expression.text).put("source", expression.source.label)
                    .put("durationMs", System.currentTimeMillis() - started).put("passed", true))
                instrumentation.sendStatus(0, android.os.Bundle().apply {
                    putString("natureCase", "${identity.natureMode}/$style"); putBoolean("passed", true)
                })
            }
            assertEquals(cases.size, results.length())
            report.put("passed", true)
        } finally {
            backups.forEach { (file, bytes) -> if (bytes == null) file.delete() else file.writeBytes(bytes) }
            val directory = File(context.getExternalFilesDir(null), "culture-unified").apply { mkdirs() }
            File(directory, "real-nature-results.json").writeText(report.toString(2))
        }
        Unit
    }
}
