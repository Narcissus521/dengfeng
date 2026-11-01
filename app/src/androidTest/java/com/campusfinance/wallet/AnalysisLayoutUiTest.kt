package com.campusfinance.wallet

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AnalysisLayoutUiTest:TeaUiScenario() {
    @Test fun manyBudgetCategoriesStayInOnePanelAndRememberTheirPageAfterDrillingDown() {
        val motion=prefs.getBoolean("motion_enabled",true);prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=runBlocking{db.walletDao().read()}
        try {
            runBlocking{repo.categoryBudgets(currentMonth(),ExpenseTaxonomy.groups.map{CategoryBudget(currentMonth(),it.id,100)})}
            navigate("分析");tapTag("analysis-entry-budget");find(hasTestTag("budget-focus-list"))
            val seen=mutableSetOf<String>()
            repeat(8) {
                ui.onNodeWithTag("budget-total-summary").assertIsDisplayed();ui.onNodeWithTag("budget-nature-summary").assertIsDisplayed()
                ExpenseTaxonomy.groups.forEach{g->if(runCatching{ui.onNodeWithTag("budget-category-${g.id}").assertIsDisplayed()}.isSuccess)seen+=g.id}
                val next=ui.onNodeWithTag("budget-list-next");if(runCatching{next.assertIsEnabled()}.isSuccess)next.performClick()
            }
            assertEquals(ExpenseTaxonomy.groups.map{it.id}.toSet(),seen)
            val lastVisible=ExpenseTaxonomy.groups.last{runCatching{ui.onNodeWithTag("budget-category-${it.id}").assertIsDisplayed()}.isSuccess}
            ui.onNodeWithTag("pageNext").assertDoesNotExist();shot("budget-many-categories")
            tap("查看全部分类");ui.onNodeWithTag("form-title").assertTextEquals("全部分类预算")
            tapTag("budget-category-education");find(hasTestTag("budget-branch-diagram"));back();back()
            ui.onNodeWithTag("budget-category-${lastVisible.id}").assertIsDisplayed()
            assertEquals(before.cash(),runBlocking{db.walletDao().read().cash()})
        } finally {runBlocking{repo.categoryBudgets(currentMonth(),before.categoryBudgets.filter{it.month==currentMonth()})};db.close();prefs.edit().putBoolean("motion_enabled",motion).commit()}
    }
    @Test fun necessityDualRingKeepsAiScopeAndLegendAfterRealRecordAndTrendNavigation() {
        val motion=prefs.getBoolean("motion_enabled",true);val theme=prefs.getString("theme_mode",null)
        prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=runBlocking{db.walletDao().read()}
        val note="ai-layout-${System.currentTimeMillis()}"
        val entry=Entry(kind="EXPENSE",amount=16800,accountId=before.accounts.first().id,date=today().toString(),category="学习教育",categoryGroupId="education",subcategoryId="education.ai",bucket="NECESSARY",necessity="NECESSARY",note=note)
        try {
            runBlocking{repo.entry(entry)}
            listOf("LIGHT","DARK").forEach{mode->
                prefs.edit().putString("theme_mode",mode).commit();ready();navigate("分析");tapTag("analysis-open-category");tap("必要性详细分析")
                find(hasTestTag("necessity-distribution"));tap("分析范围");tap("全部分类")
                ui.onNodeWithText("学习教育").performScrollTo().performClick();tap("全部二级分类");ui.onNodeWithText("AI 费用").performScrollTo().performClick();tap("完成筛选")
                tap("外圈");tapTag("necessity-category-education.ai");ui.onNodeWithTag("form-title").assertTextEquals("消费记录")
                fill("搜索分类、账户或备注",note);find(hasText(note));back()
                ui.onNodeWithText("外圈").assertIsSelected();ui.onNodeWithTag("necessity-category-education.ai").assertIsDisplayed()
                tap("内圈");tapTag("necessity-type-NECESSARY");find(hasTestTag("analysis-trend"));back()
                ui.onNodeWithText("内圈").assertIsSelected();find(hasText("学习教育 · AI 费用"))
                ui.onNodeWithTag("analysis-donut").assertContentDescriptionEquals("双层圆环图：外圈消费种类，内圈必要消费、非必要消费、未判断");shot("necessity-ai-${mode.lowercase()}")
            }
            assertEquals(before.cash()-16800,runBlocking{db.walletDao().read().cash()})
        } finally {runBlocking{if(db.walletDao().read().activeEntries.any{it.id==entry.id})repo.voidEntry(entry.id,"布局测试清理")};db.close();prefs.edit().putString("theme_mode",theme).putBoolean("motion_enabled",motion).commit()}
    }
}
