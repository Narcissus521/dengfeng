package com.campusfinance.wallet

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CategoryUiTest:TeaUiScenario() {
    @Test fun categoryToRecordReturnsToSameCategoryAndCategoryBudgetCanBeSaved() {
        ready();val db=WalletDatabase.open(context,true);val note="analysis-drill-${System.currentTimeMillis()}"
        val before=runBlocking{db.walletDao().read()};val repo=WalletRepository(db)
        val e=Entry(kind="EXPENSE",amount=2468,accountId=before.accounts.first().id,date=today().toString(),category="餐饮食品",categoryGroupId="food",subcategoryId="food.canteen",necessity="NECESSARY",note=note)
        try {
            runBlocking{repo.entry(e)}
            navigate("分析");tapTag("analysis-open-category");tapTag("analysis-group-food")
            find(hasText("本类净消费"));shot("analysis-child-structure");tapTag("analysis-leaf-food.canteen")
            ui.onNodeWithTag("form-title").assertTextEquals("消费记录");fill("搜索分类、账户或备注",note);tap("食堂")
            ui.onNodeWithTag("form-title").assertTextEquals("交易详情");back();back()
            find(hasText("本类净消费"));find(hasText("餐饮食品"));tap("调整 ${currentMonth()} 分类预算")
            fillTag("category-budget-food","100");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().categoryBudgets.any{it.month==currentMonth()&&it.categoryGroupId=="food"&&it.amount==10000L}}}
            val after=runBlocking{db.walletDao().read()};assertEquals(before.cash()-2468,after.cash())
            ui.waitUntil(10000){ui.onAllNodes(hasTestTag("form-title") and hasText("餐饮食品")).fetchSemanticsNodes().isNotEmpty()}
            find(hasText("本类净消费"));shot("analysis-budget-linked")
        }finally {
            runBlocking{if(db.walletDao().read().entries.any{it.id==e.id&&!it.voided})repo.voidEntry(e.id,"自动测试清理");repo.categoryBudgets(currentMonth(),before.categoryBudgets.filter{it.month==currentMonth()})}
            db.close()
        }
    }
    @Test fun newExpenseUsesOneCategoryAndCharityIsIndependentAndHistoryCanReclassify() {
        ready();val db=WalletDatabase.open(context,true);val note="two-level-${System.currentTimeMillis()}"
        try {
            val before=runBlocking{db.walletDao().read()}
            navigate("记账");tapTag("leaf-EXPENSE");fill("金额（元）","12.34")
            tap("其他支出");ui.onNodeWithText("公益捐赠").performScrollTo().performClick()
            ui.onNodeWithText("二级分类").assertDoesNotExist()
            find(hasText("未判断"));tap("非必要消费");fill("备注（可选）",note);submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().entries.any{it.note==note}}}
            val posted=runBlocking{db.walletDao().read()};val e=posted.entries.single{it.note==note}
            assertEquals("charity",e.categoryGroupId);assertEquals("",e.subcategoryId);assertEquals("NONESSENTIAL",e.necessity);assertEquals(before.cash()-1234,posted.cash())
            navigate("账本");tap("本月账本");fill("搜索分类、账户或备注",note);tap("公益捐赠");tap("修改分类与必要性")
            tap("公益捐赠");ui.onNodeWithText("餐饮食品").performScrollTo().performClick();tap("其他餐饮");tap("食堂");tap("未判断");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().entries.first{it.id==e.id}.categoryGroupId=="food"}}
            val edited=runBlocking{db.walletDao().read()};assertEquals(posted.cash(),edited.cash());assertEquals("UNDECIDED",edited.entries.first{it.id==e.id}.necessity)
            shot("expense-two-level-edited")
        }finally {
            runBlocking{db.walletDao().read().activeEntries.filter{it.note==note}.forEach{WalletRepository(db).voidEntry(it.id,"自动测试清理")}}
            db.close()
        }
    }
    @Test fun modulesInheritCustomRangeAndProvideSeparateNecessityTrendBudgetAndAlerts() {
        ready();navigate("分析");tap("自定")
        val from=today().minusMonths(2).withDayOfMonth(1).toString();val to=today().toString()
        fill("统计开始日期",from);fill("统计结束日期",to);tap("应用时段")
        tapTag("analysis-entry-necessity");ui.onNodeWithTag("form-title").assertTextEquals("消费必要性")
        find(hasText("全部分类"));find(hasText("必要性历史变化"));find(hasTestTag("analysis-trend"));shot("analysis-necessity");tapTag("necessity-type-UNDECIDED");ui.onNodeWithTag("form-title").assertTextEquals("未判断详情");tap("查看本时段必要性记录");ui.onNodeWithTag("form-title").assertTextEquals("消费记录");back();back();back()
        tapTag("analysis-open-trend");ui.onNodeWithTag("form-title").assertTextEquals("消费趋势")
        find(hasText("$from — $to · ${ReportRange(java.time.LocalDate.parse(from),today()).days} 天"));tap("12 个月")
        find(hasTestTag("analysis-trend"));node(hasTestTag("analysis-trend")).performTouchInput{click(center)};shot("analysis-trend");back()
        tapTag("analysis-entry-budget");ui.onNodeWithTag("form-title").assertTextEquals("预算分析");tapTag("budget-category-food")
        ui.onNodeWithTag("form-title").assertTextEquals("分类预算详情");tap("查看分类消费明细");ui.onNodeWithTag("form-title").assertTextEquals("消费记录");back();back();back()
        tapTag("analysis-entry-alerts");ui.onNodeWithTag("form-title").assertTextEquals("异常消费")
        tap("设置判断阈值");fill("单笔大额提醒阈值（元）","600");fill("异常增长最低金额差（元）","120");submit()
        find(hasText("大额 ¥600.00 · 最低差额 ¥120.00"));shot("analysis-alert-rules")
    }
    @Test fun alertDetailRechecksAfterTransactionIsVoidedAndCanTraceRecords() {
        ready();val db=WalletDatabase.open(context,true);val repo=WalletRepository(db)
        val previous=prefs.getLong("analysis_large_demo",50000);prefs.edit().putLong("analysis_large_demo",50000).commit()
        val s=runBlocking{db.walletDao().read()};val e=Entry(kind="EXPENSE",amount=70000,accountId=s.accounts.first().id,date=today().toString(),category="服饰鞋包",categoryGroupId="clothing",subcategoryId="clothing.clothes",note="alert-drill-${System.currentTimeMillis()}")
        try {
            runBlocking{repo.entry(e)};navigate("分析");tapTag("analysis-entry-alerts")
            val target=hasText("截至时段末未退款金额 ¥700.00",substring=true) and hasClickAction();find(target);node(target).performClick()
            ui.onNodeWithTag("form-title").assertTextEquals("异常详情");find(hasText("判断原因"));shot("analysis-alert-detail")
            tap("查看构成此项的交易");find(hasText(e.note,substring=true));back()
            runBlocking{repo.voidEntry(e.id,"自动测试撤销")}
            ui.waitUntil(10000){ui.onAllNodes(hasText("交易或规则已变化，此项已不再满足提醒条件。")).fetchSemanticsNodes().isNotEmpty()}
        } finally {runBlocking{if(db.walletDao().read().activeEntries.any{it.id==e.id})repo.voidEntry(e.id,"自动测试清理")};db.close();prefs.edit().putLong("analysis_large_demo",previous).commit()}
    }
}
