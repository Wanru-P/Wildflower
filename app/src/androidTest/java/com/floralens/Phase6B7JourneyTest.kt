package com.floralens

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.floralens.domain.model.*
import com.floralens.reminders.NotificationHelper
import com.floralens.reminders.WateringWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream
import java.util.UUID

/** Uses only owned records on a dedicated test AVD. Never clears existing data. */
class Phase6B7JourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val access get() = EntryPointAccessors.fromApplication(context, Phase1TestAccess::class.java)
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { FileInputStream(it.fileDescriptor).bufferedReader().readText() }
    private fun waitText(text: String) = ui.waitUntil(25_000) { ui.onAllNodesWithText(text, substring = true).fetchSemanticsNodes(false).isNotEmpty() }
    private fun click(text: String) {
        waitText(text)
        val node = ui.onNodeWithText(text)
        try { node.performScrollTo() } catch (_: AssertionError) { }
        node.performClick()
    }
    private fun back() { Espresso.pressBack(); ui.waitForIdle() }
    private fun shot(name: String) {
        ui.waitForIdle(); Thread.sleep(700)
        shell("mkdir -p /sdcard/Download/wildflower-phase6b7")
        shell("screencap -p /sdcard/Download/wildflower-phase6b7/$name.png")
    }
    private fun normalSize() { shell("wm size 1080x2340"); shell("wm density 440"); shell("settings put system font_scale 1.0"); ui.waitForIdle() }
    private fun compactSize() { shell("wm size 880x1904"); shell("wm density 440"); shell("settings put system font_scale 1.3"); ui.waitForIdle() }

    @Test fun realRoom_settings_care_cacheTabs_photoOperations_andRestart() {
        assertFalse(BuildConfig.SEED_DEMO)
        assertFalse(BuildConfig.INTERNAL_TOOLS)
        val name = "桂花 · 参考影集"
        var owner: Long? = null
        var ownsCache = false
        val oldMode = runBlocking { access.settings().identificationMode.first() }
        val originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        val originalDensity = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1) ?: "reset"
        val originalFont = shell("settings get system font_scale").trim().takeIf { it.toFloatOrNull() != null } ?: "1.0"
        val manager = context.getSystemService(NotificationManager::class.java)
        runBlocking {
            check(access.plants().observeAll().first().isEmpty()) { "Dedicated empty AVD required; do not delete personal archives" }
            check(access.info().getSaved("Osmanthus fragrans") == null) { "Existing cache must be preserved" }
            access.settings().setOnboarded()
            access.settings().setIdentificationMode(IdentificationMode.OFFLINE)
            owner = access.archive().collect("phase6b7-${UUID.randomUUID()}", Species.identified("Osmanthus fragrans", "桂花"), name, null, null)
            listOf("osmanthus_kenraiz.jpg" to "Kenraiz · CC BY-SA 4.0", "osmanthus_starr.jpg" to "Forest & Kim Starr · CC BY 2.0").forEachIndexed { index, (photo, credit) ->
                val capture = access.captures().create(context.assets.open("explore/$photo").use { it.readBytes() })
                access.archive().add(owner!!, capture.id, System.currentTimeMillis() - index * 86_400_000L,
                    "记录花朵与枝叶的细节。\n摄影：$credit\n照片来源：授权参考影集；观察时间为归档时间，不代表原图拍摄日期。")
            }
        }
        val id = requireNotNull(owner)
        val originalPhotos = runBlocking { access.database().archiveDao().history(id) }.associate { it.id to it.photoUri }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            normalSize(); waitText("发现自然，收藏四季。")
            ui.onNodeWithContentDescription("设置").performClick(); waitText("设置与帮助")
            ui.onNodeWithText("版本 ${BuildConfig.VERSION_NAME}").assertExists()
            ui.onAllNodesWithText("从 FloraLens 演进而来", substring = true).assertCountEquals(0)
            shot("01-settings-393")
            click("在线 · Pl@ntNet")
            ui.waitUntil(10_000) { runBlocking { access.settings().identificationMode.first() } == IdentificationMode.ONLINE }
            click("离线 · 本机识别")
            ui.waitUntil(10_000) { runBlocking { access.settings().identificationMode.first() } == IdentificationMode.OFFLINE }
            listOf("字体与装饰素材", "Noto Serif SC · 开源字体许可", "开源项目与致谢", "FloraLens 上游仓库")
                .forEach { ui.onAllNodesWithText(it).assertCountEquals(0) }
            compactSize(); scenario.recreate(); waitText("设置与帮助")
            ui.onNodeWithText("版本 ${BuildConfig.VERSION_NAME}").performScrollTo(); shot("02-settings-320-font130")
            ui.onNodeWithText("识别模式").performScrollTo().assertIsDisplayed()
            ui.onAllNodesWithText("FloraLens 原项目作者").assertCountEquals(0)
            back(); normalSize(); click("花园"); waitText(name); click("养护提醒")
            waitText(name); shot("03-care-393")
            compactSize(); waitText(name)
            // Collection intentionally defaults to reminders off. Opt in only
            // this test-owned plant before exercising the real scheduled worker.
            runBlocking { access.plants().setRemindersEnabled(id, true) }
            fun runCareWorker() {
                val work = OneTimeWorkRequestBuilder<WateringWorker>().build()
                val workManager = WorkManager.getInstance(context)
                workManager.enqueue(work)
                ui.waitUntil(20_000) { workManager.getWorkInfoById(work.id).get()?.state?.isFinished == true }
                assertEquals(WorkInfo.State.SUCCEEDED, workManager.getWorkInfoById(work.id).get()?.state)
            }
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                assertFalse(NotificationHelper(context).showCareReminder("草木知时 · 养护提醒", "查看植物的日常。"))
                runCareWorker()
                assertFalse(manager.activeNotifications.any { it.id == NotificationHelper.NOTIFICATION_ID_CARE })
            }
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            runCareWorker()
            ui.waitUntil(10_000) { manager.activeNotifications.any { it.id == NotificationHelper.NOTIFICATION_ID_CARE } }
            click(name); waitText("还没有浇水记录"); click("记录浇水")
            waitText("撤销"); click("撤销")
            assertNull(runBlocking { access.plants().observeById(id).first() }!!.lastWateredAt)
            click(name); click("记录浇水")
            // MarkWatered publishes the Plant update before appending its Observation.
            // Wait for both asynchronous writes, not just the earlier Flow emission.
            ui.waitUntil(15_000) { runBlocking {
                access.plants().observeById(id).first()?.lastWateredAt != null &&
                    access.database().archiveDao().history(id).count { it.type == ObservationType.WATERED.name } == 1
            } }
            assertEquals(1, runBlocking { access.database().archiveDao().history(id).count { it.type == ObservationType.WATERED.name } })
            scenario.recreate(); waitText("7 天后留意浇水")
            val plant = runBlocking { access.plants().observeById(id).first()!! }
            runBlocking { access.plants().upsert(plant.copy(nickname = "$name · 复访")) }
            waitText("$name · 复访")
            runBlocking { access.plants().upsert(plant) }; waitText(name)
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            assertTrue(NotificationHelper(context).showCareReminder("草木知时 · 养护提醒", "查看植物的日常。"))
            ui.waitUntil(10_000) { manager.activeNotifications.any { it.id == NotificationHelper.NOTIFICATION_ID_CARE } }
            manager.activeNotifications.single { it.id == NotificationHelper.NOTIFICATION_ID_CARE }.notification.contentIntent.send()
            Thread.sleep(700) // system PendingIntent delivery precedes Compose destination consumption
            ui.waitForIdle()
            waitText("养护提醒"); back(); waitText("My Garden")
            click(name); waitText("我的植物档案"); click("查看全部照片")
            waitText("2 张照片 · 按观察时间排序")
            val selected = originalPhotos.keys.first()
            ui.onNodeWithContentDescription("查看观察 $selected").performClick()
            waitText("照片来源与许可"); ui.onNodeWithText("观察内容").assertExists()
            compactSize(); waitText("观察照片集"); shot("07-photo-detail-320-font130")
            click("设为植物封面")
            ui.waitUntil(10_000) { runBlocking { access.plants().observeById(id).first() }?.coverObservationId == selected }
            scenario.recreate(); waitText("照片来源与许可")
            click("编辑日期与备注"); waitText("编辑观察")
            ui.onNodeWithText("观察备注").performScrollTo().performTextReplacement("复访时记录枝叶。\n摄影：Kenraiz · CC BY-SA 4.0")
            click("保存观察"); waitText("观察照片集")
            ui.onNodeWithContentDescription("查看观察 $selected").performClick(); waitText("复访时记录枝叶。")
            click("删除这条观察"); waitText("删除观察？"); click("取消")
            assertEquals(3, runBlocking { access.database().archiveDao().history(id).size })
            click("关闭照片"); back(); waitText("我的植物档案")
            click("认识这个物种  →"); waitText("植物笔记")
            click("重新加载"); waitText("重新加载")
            // Real offline-cache plumbing with intentionally empty optional fields.
            // No generated botanical claim or fabricated video is inserted.
            runBlocking {
                access.info().save(PlantInsights(null, emptyList(), null, emptyList(), null, null, CareLevels(), null,
                    emptyList(), emptyList(), emptyList(), null, "Osmanthus fragrans"), emptyList(), null, "桂花")
                ownsCache = true
            }
            click("重新加载"); click("浏览科学、养护与分布资料")
            normalSize(); waitText("扩展科学资料"); waitText("桂花"); shot("04-science-393")
            ui.onAllNodesWithText("Osmanthus fragrans").onLast().assertIsDisplayed()
            ui.onNodeWithText("本地精选资料").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("打开精选科学资料来源 ↗").performScrollTo().assertHasClickAction()
            click("养护"); waitText("养护资料暂未收录。")
            click("分布"); waitText("这株植物的原生分布资料暂未收录。")
            click("视频"); waitText("当前视频服务暂不可用。仍可阅读植物笔记与文化资料。")
            compactSize(); scenario.recreate()
            waitText("当前视频服务暂不可用。仍可阅读植物笔记与文化资料。"); shot("05-science-video-320-font130")
            back(); waitText("植物笔记")
            assertEquals(1, runBlocking { access.database().plantDao().count() })
            originalPhotos.forEach { (observation, uri) ->
                assertEquals(uri, runBlocking { access.database().archiveDao().history(id).single { it.id == observation }.photoUri })
                assertTrue(java.io.File(java.net.URI(uri!!)).isFile)
            }
        } finally {
            scenario.close(); manager.cancel(NotificationHelper.NOTIFICATION_ID_CARE)
            shell("settings put system font_scale $originalFont"); shell("wm size $originalSize"); shell("wm density $originalDensity")
            runBlocking {
                owner?.let { p -> access.database().archiveDao().history(p).forEach { access.archive().delete(p, it.id) }; access.plants().delete(p) }
                if (ownsCache) access.info().delete("Osmanthus fragrans")
                access.settings().setIdentificationMode(oldMode)
            }
        }
    }
}
