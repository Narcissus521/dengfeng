package com.campusfinance.wallet

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.YearMonth

class AnalysisHierarchyUiTest:TeaUiScenario() {
    private fun capture(name:String) {
        val bitmap=ui.onRoot().captureToImage().asAndroidBitmap()
        val file=File(context.getExternalFilesDir(null),"screenshots/0.12-$name.png");file.parentFile?.mkdirs()
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun calendarOpensFullScreenAndReturnsToTheSelectedDayInBothThemes() {
        val oldTheme=prefs.getString("theme_mode",null);val oldMotion=prefs.getBoolean("motion_enabled",true)
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=runBlocking{db.walletDao().read()}
        val day=today().withDayOfMonth(minOf(3,today().dayOfMonth));val note="calendar-ui-${System.currentTimeMillis()}"
        val previous=before.calendarFlows(ExpenseIndex(before).report(ReportRange.month(currentMonth())),YearMonth.from(day)).first{it.date==day}
        val entry=Entry(kind="EXPENSE",amount=2345,accountId=before.accounts.first().id,date=day.toString(),category="餐饮食品",categoryGroupId="food",subcategoryId="food.canteen",note=note)
        val receipt=Entry(kind="INCOME",amount=50000,accountId=before.accounts.first().id,date=day.toString(),category="奖学金",note=note+"-income")
        val refund=Entry(kind="REFUND",amount=500,accountId=before.accounts.first().id,date=day.toString(),originalId=entry.id,note=note+"-refund")
        try {
            runBlocking{repo.entry(entry);repo.entry(receipt);repo.entry(refund)}
            listOf("LIGHT","DARK").forEach{theme->
                prefs.edit().putString("theme_mode",theme).putBoolean("motion_enabled",false).commit();ready();navigate("分析")
                ui.onNodeWithTag("analysis-entry-calendar").assertIsDisplayed();ui.onNodeWithTag("analysis-entry-necessity").assertDoesNotExist()
                tapTag("analysis-entry-calendar");ui.onNodeWithTag("form-title").assertTextEquals("消费日历")
                ui.onNodeWithTag("dock-分析").assertDoesNotExist();ui.onNodeWithTag("brand-3").assertDoesNotExist()
                val root=ui.onRoot().fetchSemanticsNode().boundsInRoot;val destination=ui.onNodeWithTag("full-screen-destination").fetchSemanticsNode().boundsInRoot
                assertTrue(destination.height>root.height*.85f);assertEquals(root.width,destination.width,1f)
                tapTag("calendar-day-$day");ui.onNodeWithTag("calendar-day-$day").assertContentDescriptionEquals("$day，支出 ¥${Money.format(previous.spending.gross+2345)}，收入 ¥${Money.format(previous.income+50000)}，退款 ¥${Money.format(previous.spending.refunds+500)}");capture("calendar-${theme.lowercase()}")
                tap("查看当天明细");ui.onNodeWithTag("form-title").assertTextEquals("当天收支")
                fill("搜索分类、账户或备注",note)
                // Record filtering runs off the UI thread; wait for the actual result before paging it.
                ui.waitUntil(10000){ui.onAllNodes(hasText(receipt.note)).fetchSemanticsNodes().isNotEmpty()}
                find(hasText(note));find(hasText(receipt.note))
                back()
                ui.onNodeWithTag("calendar-day-$day").assertIsSelected()
                tapTag("calendar-previous");val previous=YearMonth.from(day).minusMonths(1)
                ui.waitUntil(10000){ui.onAllNodesWithText("${previous.year} 年 ${previous.monthValue} 月").fetchSemanticsNodes().isNotEmpty()}
                tapTag("calendar-next")
                ui.waitUntil(10000){ui.onAllNodesWithText("${day.year} 年 ${day.monthValue} 月").fetchSemanticsNodes().isNotEmpty()}
                ui.onNodeWithTag("calendar-next").assertIsNotEnabled();back()
                ui.onNodeWithTag("analysis-entry-calendar").assertIsDisplayed()
            }
        } finally {
            runBlocking{listOf(refund,receipt,entry).forEach{e->if(db.walletDao().read().activeEntries.any{it.id==e.id})repo.voidEntry(e.id,"日历界面测试清理")}};db.close()
            prefs.edit().putString("theme_mode",oldTheme).putBoolean("motion_enabled",oldMotion).commit()
        }
    }
    @Test fun budgetAndAnomalyListsOpenBranchComparisonsAndTraceTheRealExpense() {
        val oldMotion=prefs.getBoolean("motion_enabled",true);val oldLarge=prefs.getLong("analysis_large_demo",50000)
        prefs.edit().putBoolean("motion_enabled",false).putLong("analysis_large_demo",50000).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=runBlocking{db.walletDao().read()}
        val entry=Entry(kind="EXPENSE",amount=70000,accountId=before.accounts.first().id,date=today().toString(),category="餐饮食品",categoryGroupId="food",subcategoryId="food.canteen",note="branch-ui-${System.currentTimeMillis()}")
        try {
            assertNotNull(before.budget(currentMonth()))
            runBlocking{repo.categoryBudgets(currentMonth(),listOf(CategoryBudget(currentMonth(),"food",10000)));repo.entry(entry)}
            navigate("分析");tapTag("analysis-entry-budget")
            ui.onNodeWithTag("budget-branch-diagram").assertDoesNotExist();tapTag("budget-category-food")
            find(hasTestTag("budget-branch-diagram"));find(hasText("预算金额"));find(hasText("实际净消费"));capture("budget-branches")
            tap("查看分类消费明细");fill("搜索分类、账户或备注",entry.note);find(hasText(entry.note));back();back();back()
            tapTag("analysis-entry-alerts");ui.onNodeWithTag("budget-branch-diagram").assertDoesNotExist()
            tapTag("alert-category-food");find(hasTestTag("budget-branch-diagram"));capture("anomaly-branches")
            val cause=hasText("截至时段末未退款金额 ¥700.00",substring=true) and hasClickAction();find(cause);node(cause).performClick()
            ui.onNodeWithTag("form-title").assertTextEquals("异常详情");tap("查看构成此项的交易");find(hasText(entry.note))
            assertEquals(before.cash()-70000,runBlocking{db.walletDao().read().cash()})
        } finally {
            runBlocking{if(db.walletDao().read().activeEntries.any{it.id==entry.id})repo.voidEntry(entry.id,"分支图测试清理");repo.categoryBudgets(currentMonth(),before.categoryBudgets.filter{it.month==currentMonth()})};db.close()
            prefs.edit().putBoolean("motion_enabled",oldMotion).putLong("analysis_large_demo",oldLarge).commit()
        }
    }
    @Test fun trendRhythmKeepsItsModeAfterDrillingIntoWeekends() {
        val oldMotion=prefs.getBoolean("motion_enabled",true)
        prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        try {
            navigate("分析");tapTag("analysis-open-trend");find(hasTestTag("spending-rhythm"));capture("trend-rhythm")
            tapTag("rhythm-WEEKEND");ui.onNodeWithTag("form-title").assertTextEquals("消费记录");find(hasText("消费节奏 · 周末"));back()
            find(hasTestTag("spending-rhythm"));tap("月内节奏");capture("trend-month-rhythm")
            tapTag("rhythm-EARLY");find(hasText("消费节奏 · 月初 1–10 日"));back()
            find(hasText("月内节奏"));ui.onNodeWithText("月内节奏").assertIsSelected()
        } finally {prefs.edit().putBoolean("motion_enabled",oldMotion).commit()}
    }
}
