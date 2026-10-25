package com.campusfinance.wallet

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

// The virtual Compose clock on API 26 can leave freshly measured layers unplaced; API 26 uses native-touch visual verification instead.
@SdkSuppress(minSdkVersion=28)
class WhiteTeaUiTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun assertVisible(node:SemanticsNodeInteraction) {
        node.assertIsDisplayed()
    }
    private fun ready(theme:String="LIGHT") {
        context.getSharedPreferences("wallet_settings",Context.MODE_PRIVATE).edit().putString("mode","demo").putString("theme_mode",theme).putInt(TutorialPolicy.KEY,TutorialPolicy.VERSION).commit()
        ui.activityRule.scenario.recreate()
        ui.waitUntil(15000){ui.onAllNodesWithTag("dock-首页").fetchSemanticsNodes().isNotEmpty()}
        ui.waitForIdle()
    }
    private fun shot(name:String) {
        ui.waitForIdle()
        val out=File(context.getExternalFilesDir(null),"screenshots/0.7-$name.png");out.parentFile?.mkdirs()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        out.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    private fun reveal(text:String) {
        ui.waitForIdle()
        revealMatcher(hasText(text))
    }
    private fun revealMatcher(matcher:SemanticsMatcher) {
        val target=ui.onAllNodes(matcher).onLast()
        if(runCatching{assertVisible(target)}.isSuccess)return
        for(i in 0 until 40) {
            val previous=ui.onAllNodesWithTag("pagePrevious").onLast()
            if(runCatching{previous.assertIsEnabled()}.isFailure)break
            previous.performClick();ui.waitForIdle()
        }
        repeat(35) {
            if(runCatching{assertVisible(target)}.isSuccess)return
            val next=ui.onAllNodesWithTag("pageNext").onLast()
            if(runCatching{next.assertIsEnabled()}.isSuccess){next.performClick();ui.waitForIdle()}
        }
        error("未能在分页中找到 $matcher")
    }
    @Test fun overviewPagesFitAndCreditWithAvatarIsAccessibleInBothThemes() {
        listOf("LIGHT","DARK").forEach { theme ->
            ready(theme)
            listOf("首页","账本","规划","分析").forEach { page ->
                ui.onNodeWithTag("dock-$page").performClick();ui.waitForIdle()
                shot("${theme.lowercase()}-$page")
                ui.onNodeWithTag("pageNext").assertDoesNotExist()
                if(page=="账本") {
                    ui.onNodeWithText("各账户").performClick();ui.waitForIdle()
                    ui.onNodeWithTag("pageNext").assertDoesNotExist()
                    shot("${theme.lowercase()}-accounts")
                    ui.onNodeWithText("总金额").performClick()
                }
            }
            ui.onNodeWithText("年度").performClick()
            ui.onNodeWithContentDescription("双层圆环图：外圈消费种类，内圈必要消费、非必要消费、未判断").assertIsDisplayed()
            ui.onNodeWithText("3 个月").performClick();ui.onNodeWithText("消费必要性").assertIsDisplayed();shot("${theme.lowercase()}-3months")
            ui.onNodeWithTag("dock-记账").performClick();ui.onNodeWithTag("leaf-SETTINGS").performClick()
            reveal("共同设计与免责声明");ui.onNodeWithText("共同设计与免责声明").performClick()
            ui.waitUntil(10000){ui.onAllNodesWithText("梨花怎黛玉").fetchSemanticsNodes().isNotEmpty()}
            ui.waitForIdle();shot("${theme.lowercase()}-about")
            assertVisible(ui.onNodeWithText("梨花怎黛玉"))
            assertVisible(ui.onNodeWithContentDescription("梨花怎黛玉的头像"))
            assertVisible(ui.onNodeWithText("由梨花怎黛玉与 OpenAI Codex 共同设计"));shot("${theme.lowercase()}-about")
            reveal("使用免责声明");assertVisible(ui.onNodeWithText("使用免责声明"));shot("${theme.lowercase()}-disclaimer")
            ui.onAllNodesWithTag("dock-首页").onLast().performClick()
        }
    }
    @Test fun recurringExpenseFormSplitsAccountsPostsOnceAndCanUndo() {
        ready();val name="组合固定消费测试${System.currentTimeMillis()}"
        val db=WalletDatabase.open(context,true)
        var rule:RecurringRule?=null
        try {
            val before=runBlocking{db.walletDao().read().cash()}
            ui.onNodeWithTag("dock-账本").performClick();ui.onNodeWithText("固定消费").performClick()
            ui.onNodeWithTag("form-submit").performClick()
            ui.onNodeWithText("计划名称").performTextInput(name);ui.onNodeWithText("每次金额（元）").performTextInput("36.66")
            Espresso.closeSoftKeyboard();ui.onNodeWithTag("form-submit").performClick()
            reveal("记账时间 HH:mm");ui.onNodeWithText("记账时间 HH:mm").performTextReplacement("00:00")
            Espresso.closeSoftKeyboard();ui.onNodeWithTag("form-submit").performClick()
            reveal("添加付款账户");ui.onNodeWithText("添加付款账户").performClick()
            val accounts=runBlocking{db.walletDao().accounts().filter{it.disabledOn==null}.take(2)}
            accounts.forEachIndexed{i,a->val tag="recurring-split-${a.id}";revealMatcher(hasTestTag(tag));ui.onNodeWithTag(tag).performTextReplacement(if(i==0)"20.00" else "16.66");Espresso.closeSoftKeyboard()}
            ui.onNodeWithTag("form-submit").performClick();shot("fixed-split-confirm")
            ui.onNodeWithTag("form-submit").performClick()
            ui.waitUntil(15000){runBlocking{db.walletDao().read().recurringRules.any{it.name==name}}}
            rule=runBlocking{db.walletDao().read().recurringRules.single{it.name==name}}
            ui.waitUntil(15000){runBlocking{db.walletDao().read().recurringOccurrences.any{it.ruleId==rule!!.id}}}
            assertEquals(before-3666,runBlocking{db.walletDao().read().cash()})
            reveal(name);ui.onNodeWithText(name).performClick();reveal("撤销本次");ui.onNodeWithText("撤销本次").performClick()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().cash()==before}}
            runBlocking{WalletRepository(db).postDue()};assertEquals(before,runBlocking{db.walletDao().read().cash()})
        }finally{
            runBlocking {
                val s=db.walletDao().read();val r=rule ?: s.recurringRules.firstOrNull{it.name==name}
                if(r!=null){val repo=WalletRepository(db);s.recurringOccurrences.filter{it.ruleId==r.id&&it.status=="POSTED"}.forEach{repo.undoRecurring(it.id,"自动测试清理")};repo.recurring(r.copy(enabled=false),s.recurringAllocations.filter{it.ruleId==r.id})}
            };db.close()
        }
    }
    @Test fun leafOpensRealEntryAndLedgerKeepsDetailsInChildPage() {
        ready();val name="white-tea-test-${System.currentTimeMillis()}"
        val db=WalletDatabase.open(context,true)
        try {
            val before=runBlocking{db.walletDao().read().cash()}
            ui.onNodeWithTag("dock-记账").performClick();ui.onNodeWithText("支出").performClick()
            ui.onNodeWithText("金额（元）").performTextInput("12.34")
            Espresso.closeSoftKeyboard();reveal("备注（可选）")
            ui.onNodeWithText("备注（可选）").performTextInput(name)
            Espresso.closeSoftKeyboard();shot("entry")
            ui.onNodeWithTag("form-submit").performClick()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().entries.any{it.note==name}}}
            assertEquals(before-1234,runBlocking{db.walletDao().read().cash()})
            ui.onNodeWithTag("dock-账本").performClick();ui.onNodeWithText(name).assertDoesNotExist()
            ui.onNodeWithText("本月账本").performClick()
            ui.onNodeWithText("搜索分类、账户或备注").performTextInput(name);Espresso.closeSoftKeyboard()
            reveal("其他支出");ui.onNodeWithText("其他支出").performClick()
            ui.onNodeWithTag("form-title").assertTextEquals("交易详情");reveal("撤销这笔记录");ui.onNodeWithText("撤销这笔记录").performClick()
            reveal("撤销原因");ui.onNodeWithText("撤销原因").performTextInput("自动测试纠错");Espresso.closeSoftKeyboard()
            reveal("确认撤销");ui.onNodeWithText("确认撤销").performClick()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().entries.single{it.note==name}.voided}}
            assertEquals(before,runBlocking{db.walletDao().read().cash()})
        }finally{
            runBlocking{val state=db.walletDao().read();state.activeEntries.filter{it.note==name}.forEach{WalletRepository(db).voidEntry(it.id,"自动测试清理")}}
            db.close()
        }
    }
}
