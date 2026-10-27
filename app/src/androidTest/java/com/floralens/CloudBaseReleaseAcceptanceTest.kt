package com.floralens

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.data.chat.PlantChatException
import com.floralens.data.chat.PlantChatRepository
import com.floralens.data.culture.NatureExpressionRepository
import com.floralens.data.culture.NatureServiceConfig
import com.floralens.data.culture.NatureServiceException
import com.floralens.domain.chat.PlantChatContext
import com.floralens.domain.chat.PlantChatRole
import com.floralens.domain.chat.PlantChatSource
import com.floralens.domain.chat.PlantChatTurn
import com.floralens.domain.culture.CultureIdentityResolver
import com.floralens.domain.culture.ExpressionSource
import com.floralens.domain.culture.NatureTextPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real-provider acceptance on the ORIGINAL signed non-debug target; never changes Room or photos. */
class CloudBaseReleaseAcceptanceTest {
    @Test fun signedReleaseSession_threeRealTurns_natureAndCache() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Explicit real-provider opt-in required", InstrumentationRegistry.getArguments().getString("cloudbaseReleaseLive") == "true")
        val context = instrumentation.targetContext
        assertEquals("Must target the actual signed Release", 0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        val configuration = NatureServiceConfig(context)
        val identity = CultureIdentityResolver.resolve(scientific = "Osmanthus fragrans")
        val chat = PlantChatRepository(configuration)
        val plant = PlantChatContext(PlantChatSource.SPECIES, identity, "桂花", "Osmanthus fragrans", null)
        val history = mutableListOf<PlantChatTurn>()
        val questions = listOf(
            "请把观察计划命名为慢慢看，并用两句话建议第一次如何记录植物。",
            "请结合上一次的方法，用两句话说明下次怎样比较。",
            "我之前给计划取了什么名字？请重述名字并给出一句观察建议。",
        )
        for ((index, question) in questions.withIndex()) {
            val start = System.currentTimeMillis()
            val answer = try { withContext(Dispatchers.Main) { chat.reply(plant, history, question) } } catch (error: PlantChatException) {
                instrumentation.sendStatus(0, Bundle().apply { putString("cloudbaseStage", "chat_${index + 1}"); putString("reason", error.reason); putString("diagnosticType", error.diagnosticType) })
                throw error
            }
            assertTrue(answer.isNotBlank())
            if (index == 2) assertTrue("Actual model must recall prior user context", answer.contains("慢慢看"))
            history += PlantChatTurn(PlantChatRole.USER, question)
            history += PlantChatTurn(PlantChatRole.ASSISTANT, answer)
            instrumentation.sendStatus(0, Bundle().apply { putString("cloudbaseStage", "chat_${index + 1}_passed"); putLong("durationMs", System.currentTimeMillis() - start) })
        }
        val nature = NatureExpressionRepository(context, configuration)
        val expression = try { nature.generate(identity, "quiet", force = true) } catch (error: NatureServiceException) {
            instrumentation.sendStatus(0, Bundle().apply { putString("cloudbaseStage", "nature"); putString("reason", error.reason); putString("diagnosticType", error.diagnosticType) })
            throw error
        }
        assertEquals(ExpressionSource.AI, expression.source)
        assertTrue(NatureTextPolicy.valid(expression))
        assertEquals(expression, nature.cached(identity, "quiet"))
        assertEquals(expression, nature.generate(identity, "quiet", force = false))
        val session = requireNotNull(configuration.read())
        assertEquals("https://wildflower-ai-test-d5cge7e1432c7-1410768603.ap-shanghai.app.tcloudbase.com/wildflower-ai", session.url)
        assertTrue(session.expiresAt > System.currentTimeMillis())
        instrumentation.sendStatus(0, Bundle().apply { putString("cloudbaseStage", "nature_and_cache_passed") })
    }
}
