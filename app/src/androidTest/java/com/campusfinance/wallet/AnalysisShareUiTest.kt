package com.campusfinance.wallet

import android.accessibilityservice.AccessibilityService
import androidx.compose.ui.test.hasText
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AnalysisShareUiTest : TeaUiScenario() {
    @Test fun previewCreatesReadableShareFile() {
        ready()
        val db = WalletDatabase.open(context, true)
        try {
            val month = currentMonth()
            val repo = WalletRepository(db)
            val saved = runBlocking { db.walletDao().categoryBudgets().filter { it.month == month } }
            try {
                runBlocking {
                    repo.categoryBudgets(month, listOf(CategoryBudget(month, "food", 70000),
                        CategoryBudget(month, "transport", 25000), CategoryBudget(month, "clothing", 40000)))
                }
                verifyShare()
            } finally {
                runBlocking { repo.categoryBudgets(month, saved) }
            }
        } finally { db.close() }
    }

    private fun verifyShare() {
        navigate("首页")
        tap("全部记录")
        tap("分享到 ChatGPT 分析")
        find(hasText("同期净消费"))
        find(hasText("分类预算"))
        find(hasText("已用 / 预算"))
        shot("preview-aligned-analysis")
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
