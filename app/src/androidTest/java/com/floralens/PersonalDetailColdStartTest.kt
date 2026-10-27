package com.floralens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.domain.model.Species
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Invoke once with pdCold=seed, adb force-stop, then pdCold=verify.
 * Only exact owned IDs are removed; an existing user collection blocks seeding. */
class PersonalDetailColdStartTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private val prefs get() = context.getSharedPreferences("personal-detail-kit-owned-test", 0)
    private val nickname = "测试 · 进程重启档案"
    private fun waitText(text: String) = ui.waitUntil(25_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes(false).isNotEmpty() }
    private fun openArchive(id: Long) {
        waitText("发现自然，收藏四季。"); ui.onNodeWithText("花园").performClick()
        ui.waitUntil(20_000) { ui.onAllNodesWithTag("garden-grid").fetchSemanticsNodes(false).isNotEmpty() }
        ui.onNodeWithTag("garden-grid").performScrollToNode(hasTestTag("garden-plant-$id"))
        ui.onNodeWithTag("garden-plant-$id").performClick(); waitText("我的植物档案")
        ui.onNodeWithText(nickname).assertIsDisplayed()
        ui.onNodeWithText("1 张归档照片").assertExists()
    }

    @Test fun ownedObservation_persistentArchiveAndExplicitColdVerification() {
        val mode = InstrumentationRegistry.getArguments().getString("pdCold")
        require(mode == null || mode == "seed" || mode == "verify")
        val reference = context.assets.open("explore/osmanthus_kenraiz.jpg").use { it.readBytes() }
        val time = LocalDateTime.parse("2024-10-02T09:20:00").atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if(mode != "verify") {
            check(!prefs.contains("ownedId"))
            val id = runBlocking {
                check(access.plants().observeAll().first().isEmpty()) { "Empty dedicated AVD required; never clear user data" }
                access.settings().setOnboarded()
                val id = access.archive().collect("pd-cold-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"), nickname, null, null)
                prefs.edit().putLong("ownedId", id).putInt("seedPid", android.os.Process.myPid()).commit()
                val capture = access.captures().create(reference)
                access.archive().add(id, capture.id, time, "测试档案 · 授权参考摄影，非用户野外实拍。用于真实进程重启验证。")
                id
            }
            ActivityScenario.launch(MainActivity::class.java).use { openArchive(id) }
            android.util.Log.i("PersonalDetailCold", "SEED pid=${android.os.Process.myPid()} ownedId=$id")
            if(mode == "seed") return // Retain precisely this owned fixture for the separate process.
        }
        run {
            val id = prefs.getLong("ownedId", 0)
            check(id > 0)
            try {
                if(mode == "verify") assertNotEquals("Cold verification must use a different process",
                    prefs.getInt("seedPid", 0), android.os.Process.myPid())
                runBlocking {
                    val plant = requireNotNull(access.database().archiveDao().plant(id))
                    check(plant.nickname == nickname) { "Ownership mismatch: do not touch this archive" }
                    val observation = access.database().archiveDao().history(id).single()
                    assertEquals(time, observation.timestampEpochMs)
                    assertEquals("PHOTO_OBSERVATION", observation.type)
                    assertEquals(observation.id, plant.coverObservationId)
                    assertEquals(observation.photoUri, plant.photoUri)
                    assertArrayEquals(reference, File(URI(observation.photoUri)).readBytes())
                }
                ActivityScenario.launch(MainActivity::class.java).use { openArchive(id) }
                android.util.Log.i("PersonalDetailCold", "${if(mode == "verify") "COLD" else "IN_PROCESS_READ"} PASS pid=${android.os.Process.myPid()} ownedId=$id originalBytes=${reference.size}")
            } finally {
                runBlocking {
                    val owned = access.database().archiveDao().plant(id)
                    if(owned?.nickname == nickname) {
                        access.database().archiveDao().history(id).forEach { access.archive().delete(id, it.id) }
                        access.plants().delete(id)
                        prefs.edit().clear().commit()
                    }
                }
            }
        }
    }
}
