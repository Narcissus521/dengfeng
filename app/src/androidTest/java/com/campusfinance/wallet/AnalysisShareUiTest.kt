package com.campusfinance.wallet

import android.accessibilityservice.AccessibilityService
import androidx.compose.ui.test.hasText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AnalysisShareUiTest : TeaUiScenario() {
    @Test fun previewCreatesReadableShareFile() {
        ready()
        navigate("首页")
        tap("全部记录")
        tap("分享到 ChatGPT 分析")
        find(hasText("同期净消费"))
        find(hasText("分类预算"))
        val folder = File(context.cacheDir, "analysis-share")
        val before = folder.listFiles().orEmpty().map { it.name }.toSet()
        submit()
        ui.waitUntil(10000) { folder.listFiles().orEmpty().any { it.name !in before } }
        val file = folder.listFiles().orEmpty().single { it.name !in before }
        assertTrue(file.readText().contains("等风消费分析资料"))
        assertTrue(file.readText().contains("同期净消费"))
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.analysis-share", file)
        assertEquals(file.readText(), context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }
}
