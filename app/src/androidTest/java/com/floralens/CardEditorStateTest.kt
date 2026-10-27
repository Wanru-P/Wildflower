package com.floralens

import android.graphics.BitmapFactory
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.floralens.card.export.CardExporter
import com.floralens.card.render.*
import com.floralens.card.share.CardShare
import com.floralens.domain.model.Species
import com.floralens.ui.screens.card.*
import com.floralens.ui.theme.FloraLensTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.FileInputStream
import java.util.UUID

/** Real repositories and renderer: no fake successful export state. */
class CardEditorStateTest {
    @get:Rule val ui=createComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val access get()=EntryPointAccessors.fromApplication(context,Phase1TestAccess::class.java)
    private fun shell(cmd:String)=instrumentation.uiAutomation.executeShellCommand(cmd).use { FileInputStream(it.fileDescriptor).bufferedReader().readText() }
    private fun SemanticsNodeInteraction.isEnabled():Boolean =
        try { assertIsEnabled(); true } catch (_:AssertionError) { false }
    private fun fixture(action:(Long,Long,BotanicalCardViewModel)->Unit) {
        val store=androidx.lifecycle.ViewModelStore()
        val owner=runBlocking { access.archive().collect("card-state-${UUID.randomUUID()}",Species.identified("Ginkgo biloba","银杏"),"Debug · 诗笺状态",null,null) }
        try {
            val observation=runBlocking {
                val capture=access.captures().create(context.assets.open("explore/ginkgo.jpg").use { it.readBytes() })
                access.archive().add(owner,capture.id,System.currentTimeMillis(),"Debug fixture · 状态测试")
                access.database().archiveDao().history(owner).single().id
            }
            lateinit var vm:BotanicalCardViewModel
            ui.runOnUiThread { vm=BotanicalCardViewModel(SavedStateHandle(mapOf("plantId" to owner)),access.plants(),access.observations(),access.cards(),CardRenderer(context),CardPhotoDecoder(),CardExporter(context),CardShare(context)) }
            ui.runOnUiThread { store.put("card",vm) }
            ui.setContent { FloraLensTheme { BotanicalCardScreen({}, {}, vm) } }
            ui.waitUntil(20_000) { vm.state.value.preview!=null }
            action(owner,observation,vm)
        } finally { ui.runOnUiThread { store.clear() }; runBlocking { access.database().archiveDao().history(owner).forEach { access.archive().delete(owner,it.id) }; access.plants().delete(owner) } }
    }
    @Test fun userDraftRemainsAuthoritativeWhilePreviewRefreshIsPending() = fixture { _, _, vm ->
        val written = "把一次停留留在纸上，\n让下一次相遇仍有回响。"
        ui.runOnUiThread {
            vm.creativeText(written)
            vm.refresh()
            // No render/collector turn has run yet: editing must not use its
            // transient empty metadata in place of the saved user's text.
            assertEquals(written, vm.creativeTextForEditing())
        }
        ui.waitUntil(20_000) { vm.state.value.expressionText == written && vm.state.value.preview != null }
        ui.onNodeWithTag("card-edit-expression").performScrollTo().performClick()
        ui.onNodeWithTag("card-expression-input").assertTextContains(written)
        ui.onNodeWithText("取消").performClick()
        ui.runOnUiThread {
            vm.creativeText("")
            assertEquals("", vm.creativeTextForEditing()) // explicit blank is not automatic text
        }
    }
    @Test fun deletionRevalidatesErrorVisibleAndOtherSelectionCanRetry() = fixture { owner, observation, vm ->
        val second=runBlocking {
            val capture=access.captures().create(context.assets.open("explore/ginkgo.jpg").use { it.readBytes() })
            access.archive().add(owner,capture.id,System.currentTimeMillis(),"Debug fixture · 另一张")
            access.database().archiveDao().history(owner).first { it.id!=observation }.id
        }
        ui.runOnUiThread { vm.select(observation) }
        ui.waitUntil(20_000) { vm.state.value.selectedId==observation && vm.state.value.preview!=null }
        runBlocking { access.archive().delete(owner,observation) }
        ui.waitUntil(20_000) { vm.state.value.error!=null }
        ui.onNodeWithTag("card-save").assertIsNotEnabled(); ui.onNodeWithTag("card-share").assertIsNotEnabled()
        ui.onNodeWithText("观察已删除，请重新选择照片").performScrollTo().assertIsDisplayed()
        ui.runOnUiThread { vm.select(second) }
        ui.waitUntil(20_000) { vm.state.value.preview!=null && vm.state.value.selectedId==second }
        ui.onNodeWithTag("card-save").assertIsEnabled()
    }
    @Test fun realCreateDocumentCancelThenSavePng() = fixture { _, _, vm ->
        // Exercises the same production API 26–28 launcher on API 35 without changing SDK routing.
        // Lifecycle resume revalidates previews asynchronously. Use the actual enabled UI entry,
        // rather than racing a direct VM call against its next refresh after DocumentsUI closes.
        ui.waitUntil(20_000) { vm.state.value.preview!=null && vm.state.value.templatePreviews.size==com.floralens.domain.card.CardTemplateId.entries.size }
        ui.onNodeWithText("另存到所选位置…").performScrollTo()
        // StateFlow completion can precede its lifecycle collector's rendered button state.
        ui.waitUntil(20_000) { ui.onNodeWithText("另存到所选位置…").isEnabled() }
        ui.onNodeWithText("另存到所选位置…").assertIsEnabled().performClick()
        ui.waitUntil(20_000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()=="com.google.android.documentsui" }
        shell("input keyevent 4")
        ui.waitUntil(15_000) { vm.message.value=="已取消保存，观察照片未改变" }
        assertNull(vm.savedUri.value); assertFalse(vm.busy.value)
        ui.waitUntil(20_000) { vm.state.value.preview!=null && vm.state.value.templatePreviews.size==com.floralens.domain.card.CardTemplateId.entries.size && !vm.busy.value && instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()=="com.floralens" }
        ui.waitForIdle()
        ui.onNodeWithText("另存到所选位置…").performScrollTo()
        ui.waitUntil(20_000) { ui.onNodeWithText("另存到所选位置…").isEnabled() }
        ui.onNodeWithText("另存到所选位置…").assertIsEnabled().performClick()
        ui.waitUntil(20_000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()=="com.google.android.documentsui" }
        shell("mkdir -p /sdcard/Download/wildflower-phase4"); shell("screencap -p /sdcard/Download/wildflower-phase4/saf-file-picker.png")
        fun findSave(node:android.view.accessibility.AccessibilityNodeInfo?):android.view.accessibility.AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.isEnabled && node.isClickable && (node.viewIdResourceName?.endsWith(":id/menu_save")==true || node.text?.toString()?.uppercase() in setOf("SAVE","保存"))) return node
            for(i in 0 until node.childCount) findSave(node.getChild(i))?.let { return it }
            return null
        }
        var clicked=false
        repeat(20) { if(!clicked) { val button=findSave(instrumentation.uiAutomation.rootInActiveWindow); clicked=button?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)==true; if(!clicked) Thread.sleep(250) } }
        assertTrue("Actual DocumentsUI Save button must be clicked",clicked)
        ui.waitUntil(25_000) { vm.savedUri.value!=null }
        val uri=vm.savedUri.value!!
        try {
            assertEquals("content",uri.scheme)
            val bitmap=context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }!!
            assertEquals(1600,bitmap.width); assertEquals(2400,bitmap.height); bitmap.recycle()
            assertEquals("PNG 已保存到所选位置",vm.message.value)
        } finally { android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri) }
    }
    @Test fun failedPhotoReadHasFeedbackAndRetryUsesActualNewObservation() = fixture { owner, observation, vm ->
        val photo=runBlocking { access.database().archiveDao().observation(observation)!!.photoUri!! }
        runBlocking { access.photos().delete(photo) } // only the new photo owned by this fixture
        ui.runOnUiThread { vm.saveGallery() }
        // Failure feedback and finally's busy reset are separate StateFlow writes.
        // Await the completed failure, not the transient message-before-finally instant.
        ui.waitUntil(20_000) { vm.message.value?.contains("照片已失效")==true && !vm.busy.value }
        assertNull(vm.savedUri.value); assertFalse(vm.busy.value)
        val newObservation=runBlocking {
            val capture=access.captures().create(context.assets.open("explore/ginkgo.jpg").use { it.readBytes() })
            access.archive().add(owner,capture.id,System.currentTimeMillis(),"Debug fixture · 恢复后新照片")
            access.database().archiveDao().history(owner).first { it.id!=observation }.id
        }
        ui.waitUntil(20_000) { vm.state.value.preview!=null && vm.state.value.selectedId==newObservation }
        fun count()=context.contentResolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.Images.Media._ID),null,null,null)!!.use { it.count }
        val before=count()
        ui.runOnUiThread { vm.saveGallery(); vm.saveGallery() } // same main-loop duplicate must be ignored
        ui.waitUntil(20_000) { vm.savedUri.value!=null && !vm.busy.value }
        try { assertEquals(before+1,count()); assertEquals("PNG 已保存到 Pictures/Wildflower Lens",vm.message.value) }
        finally { context.contentResolver.delete(vm.savedUri.value!!,null,null) }
    }
}
