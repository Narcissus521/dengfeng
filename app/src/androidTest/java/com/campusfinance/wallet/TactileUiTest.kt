package com.campusfinance.wallet

import android.database.sqlite.SQLiteDatabaseLockedException
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Run only in the isolated emulator/demo ledger. Never target a user's phone. */
class TactileUiTest:TeaUiScenario() {
    private fun <T> database(action:suspend ()->T):T=runBlocking {
        repeat(30){try{return@runBlocking action()}catch(_:SQLiteDatabaseLockedException){delay(100)}};action()
    }
    @Before fun start(){prefs.edit().putBoolean("custom_home",false).putBoolean("motion_enabled",false).putString("theme_mode","LIGHT").commit();ready()}
    @After fun finish(){prefs.edit().putBoolean("custom_home",false).putBoolean("motion_enabled",true).putString("theme_mode","SYSTEM").commit()}

    @Test fun homeAndPhysicalDockWorkInLightDarkAndReducedMotion() {
        val db=WalletDatabase.open(context,true)
        try{
            val before=database{db.walletDao().read()}
            listOf("LIGHT","DARK").forEach{mode->
                prefs.edit().putString("theme_mode",mode).commit();ready()
                ui.onNodeWithTag("card-HOME_BUDGET").assertIsDisplayed()
                ui.onNodeWithText("资金安排").assertIsDisplayed()
                assertNull(ui.activity.activeOpticalScene)
                shot("paper-home-${mode.lowercase()}")
                navigate("账本");shot("paper-ledger-${mode.lowercase()}")
                navigate("规划");shot("paper-planning-${mode.lowercase()}")
                navigate("分析");find(hasTestTag("analysis-entry-budget"));shot("paper-analysis-${mode.lowercase()}")
                tapTag("analysis-entry-budget");ui.onNodeWithTag("full-screen-destination").assertIsDisplayed();shot("paper-budget-analysis-${mode.lowercase()}");back()
                navigate("首页")
                val dock=ui.onNodeWithTag("ledger-dock")
                dock.performTouchInput{swipe(Offset(width*.12f,centerY),Offset(width*.91f,centerY),650)}
                ui.waitForIdle();ui.onNodeWithTag("dock-分析").assertIsSelected();ui.onNodeWithTag("leaf-menu").assertDoesNotExist()
                dock.performTouchInput{swipe(Offset(width*.9f,centerY),Offset(width*.08f,centerY),650)}
                ui.waitForIdle();ui.onNodeWithTag("dock-首页").assertIsSelected()
            }
            val after=database{db.walletDao().read()}
            assertEquals(before.accounts,after.accounts);assertEquals(before.entries,after.entries);assertEquals(before.budgets,after.budgets)
        }finally{db.close()}
    }

    @Test fun quickBudgetSaveStaysOnHomeAndDoesNotMoveCash() {
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db)
        val old=database{db.walletDao().read().budget(currentMonth())}
        val original=old ?: Budget(currentMonth(),200000,130000,50000)
        try{
            database{repo.budget(original)};ui.waitForIdle()
            val before=database{db.walletDao().read()}
            tapTag("home-adjust-budget");ui.onNodeWithTag("quick-edit-dialog").assertIsDisplayed()
            ui.onNodeWithTag("full-screen-destination").assertDoesNotExist()
            fill("总预算（元）",Money.plain(original.total+10000));shot("paper-quick-budget")
            submit();ui.waitUntil(10000){database{db.walletDao().read().budget(currentMonth())?.total==original.total+10000}}
            ui.waitUntil(10000){ui.onAllNodesWithTag("quick-edit-dialog").fetchSemanticsNodes().isEmpty()}
            val after=database{db.walletDao().read()}
            assertEquals(before.cash(),after.cash());assertEquals(before.entries,after.entries)
            val remaining=original.total+10000-after.usage(currentMonth())
            ui.onAllNodesWithText("¥${Money.format(kotlin.math.abs(remaining))}").onFirst().assertIsDisplayed()
            ui.onNodeWithTag("card-HOME_BUDGET").performTouchInput{longClick(center)}
            ui.onNodeWithTag("quick-action-menu").assertIsDisplayed();shot("paper-quick-actions");tap("关闭")
        }finally{database{repo.budget(original)};db.close()}
    }

    @Test fun longPressSlideChoosesTheVisibleQuickAction() {
        val card=ui.onNodeWithTag("card-HOME_BUDGET");val origin=card.fetchSemanticsNode().boundsInRoot
        card.performTouchInput{down(center);advanceEventTime(650)}
        ui.waitUntil(10000){ui.onAllNodesWithTag("quick-action-0").fetchSemanticsNodes().isNotEmpty()}
        val target=ui.onNodeWithTag("quick-action-0").fetchSemanticsNode().boundsInRoot.center-origin.topLeft
        card.performTouchInput{moveTo(target);advanceEventTime(100);up()}
        ui.waitForIdle();ui.onNodeWithTag("quick-edit-dialog").assertIsDisplayed()
        ui.onNodeWithText("总预算（元）").assertIsDisplayed();back()
        ui.onNodeWithTag("card-HOME_BUDGET").assertIsDisplayed()
    }

    @Test fun quickHistoricalEditPreservesRefundsAndRecordFilters() {
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=database{db.walletDao().read()}
        val account=before.accounts.first{it.disabledOn==null};val prefix="tactile-${System.currentTimeMillis()}"
        val expense=Entry(kind="EXPENSE",amount=2000,accountId=account.id,date=today().toString(),category="餐饮食品",bucket="NECESSARY",necessity="NECESSARY",categoryGroupId="food",subcategoryId="food.canteen",note=prefix)
        val refund=Entry(kind="REFUND",amount=500,accountId=account.id,date=today().toString(),originalId=expense.id,note=prefix)
        try{
            database{repo.entry(expense);repo.entry(refund)}
            navigate("记账");tapTag("leaf-RECORDS");fill("搜索分类、账户或备注",prefix)
            find(hasTestTag("quick-entry-${expense.id}"));ui.onNodeWithTag("quick-entry-${expense.id}").performTouchInput{longClick(center)}
            tapTag("quick-action-0");ui.onNodeWithTag("quick-edit-dialog").assertIsDisplayed()
            fill("金额（元）","22.00");fill("修改原因（可选）","同页快捷编辑检查");submit()
            ui.waitUntil(10000){database{db.walletDao().read().entries.first{it.id==expense.id}.amount==2200L}}
            ui.waitUntil(10000){ui.onAllNodesWithTag("quick-edit-dialog").fetchSemanticsNodes().isEmpty()}
            val after=database{db.walletDao().read()}
            assertEquals(500L,after.refunds(expense.id));assertEquals(before.cash()-1700,after.cash())
            assertEquals(before.usage(currentMonth())+1700,after.usage(currentMonth()))
            ui.waitForIdle()
            find(hasText("搜索分类、账户或备注"));ui.onNodeWithText("搜索分类、账户或备注").assertTextContains(prefix)
            find(hasTestTag("records-summary"));ui.onNodeWithTag("records-summary").assertContentDescriptionEquals("收入 ¥0.00，支出 ¥22.00，退款 ¥5.00，净消费 ¥17.00，还款 ¥0.00")
            shot("paper-records-quick-edit")
        }finally{
            database{val current=db.walletDao().read();if(current.activeEntries.any{it.id==refund.id})repo.voidEntry(refund.id,"测试清理");if(db.walletDao().read().activeEntries.any{it.id==expense.id})repo.voidEntry(expense.id,"测试清理")};db.close()
        }
    }

    @Test fun quickSavingAndRecurringSwitchUseExistingRepositoryWithoutCashChanges() {
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=database{db.walletDao().read()}
        val goal=before.goals.first{!it.archived};val future=today().plusDays(3).toString()
        val rule=RecurringRule(name="快捷启停测试",kind="INCOME",amount=3000,startDate=future,category="红包")
        try{
            database{repo.recurring(rule,listOf(RecurringAllocation(rule.id,before.accounts.first().id,rule.amount)))}
            // Use the same menu entry a long press on the arrangement opens.
            val area=ui.onNodeWithText("储蓄已存");area.performTouchInput{longClick(center)}
            tapTag("quick-action-0");fill("目前已存（元）",Money.plain(goal.saved+100));submit()
            ui.waitUntil(10000){database{db.walletDao().read().goals.first{it.id==goal.id}.saved==goal.saved+100}}
            assertEquals(before.cash(),database{db.walletDao().read().cash()})
            navigate("账本");find(hasTestTag("card-LEDGER_FIXED_INCOME"));ui.onNodeWithTag("card-LEDGER_FIXED_INCOME").performTouchInput{longClick(center)}
            tapTag("quick-action-0");find(hasTestTag("quick-recurring-${rule.id}"));ui.onNodeWithTag("quick-recurring-${rule.id}").performClick()
            ui.waitUntil(10000){database{db.walletDao().read().recurringRules.first{it.id==rule.id}.enabled==false}}
            ui.onNodeWithTag("quick-recurring-${rule.id}").assertIsOff();find(hasTestTag("quick-save-confirmation"));shot("paper-quick-recurring")
            assertEquals(before.cash(),database{db.walletDao().read().cash()})
        }finally{database{repo.goal(goal);repo.recurring(rule.copy(enabled=false),listOf(RecurringAllocation(rule.id,before.accounts.first().id,rule.amount)))};db.close()}
    }

    @Test fun layoutActionRetainsGridResizeAndCanReturnToStandardHome() {
        ui.onNodeWithTag("card-HOME_BUDGET").performTouchInput{longClick(center)};tapTag("quick-action-2")
        find(hasTestTag("card-resize-panel"));ui.onNodeWithTag("overview-grid").assertIsDisplayed()
        find(hasTestTag("resize-HOME_BUDGET-right"))
        val before=ui.activity.displayController.state.value.cardGrids[OverviewCard.HOME_BUDGET]?.columns ?: 4
        val direction=if(before>2)"缩窄一格" else "加宽一格"
        val resize=ui.onNodeWithTag("resize-HOME_BUDGET-right").fetchSemanticsNode().config[SemanticsActions.CustomActions].first{it.label==direction}
        ui.runOnIdle{assertTrue(resize.action())}
        ui.waitUntil(10000){ui.activity.displayController.state.value.cardGrids[OverviewCard.HOME_BUDGET]?.columns==before+(if(before>2)-1 else 1)}
        tapTag("card-resize-done");assertTrue(ui.activity.displayController.state.value.customHome)
        val saved=ui.activity.displayController.state.value.cardGrids
        ui.runOnUiThread{ui.activity.displayController.setCustomHome(false)};ui.waitForIdle()
        ui.onNodeWithTag("ledger-home-scroll").assertIsDisplayed();assertEquals(saved,ui.activity.displayController.state.value.cardGrids)
    }
    @Test fun longPressCanRemoveAndRestorePaidDebtWithoutDeletingHistory(){
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val original=database{db.walletDao().read()}
        val d=Debt(name="可恢复贷款测试",principal=0,purchaseDate=today().toString(),categoryId="debt-kind-loan",scheduleType="CUSTOM")
        val rows=manualDebtSchedule(d.id,today().toString(),listOf(2000,3000))
        try{
            database{repo.debt(d,rows);repo.repay(rows.first(),original.accounts.first().id,today().toString())}
            val paid=database{db.walletDao().read()};val history=paid.payments(rows.first())
            navigate("规划");tapTag("card-PLAN_DEBT");find(hasTestTag("debt-plan-${d.id}"))
            ui.onNodeWithTag("debt-plan-${d.id}").performTouchInput{longClick(center)};tapTag("quick-action-0")
            ui.onNodeWithTag("quick-edit-dialog").assertIsDisplayed();ui.onAllNodesWithTag("form-title").onLast().assertTextEquals("删除负债计划")
            shot("paper-delete-plan-confirm");submit()
            ui.waitUntil(10000){database{db.walletDao().read().debts.first{it.id==d.id}.archived}}
            ui.waitUntil(10000){ui.onAllNodesWithTag("quick-edit-dialog").fetchSemanticsNodes().isEmpty()};ui.waitForIdle()
            val removed=database{db.walletDao().read()};assertEquals(paid.cash(),removed.cash());assertEquals(paid.entries,removed.entries);assertEquals(history,removed.payments(rows.first()));assertTrue(removed.unpaid().none{it.debtId==d.id})
            ui.onNodeWithTag("debt-plan-${d.id}").assertDoesNotExist()
            val link=hasText("已移除计划（",substring=true);find(link);node(link).performClick();ui.waitForIdle()
            find(hasTestTag("debt-plan-${d.id}"));shot("paper-removed-plans")
            ui.onNodeWithTag("debt-plan-${d.id}").performTouchInput{longClick(center)};tapTag("quick-action-0");submit()
            ui.waitUntil(10000){!database{db.walletDao().read().debts.first{it.id==d.id}.archived}}
            val restored=database{db.walletDao().read()};assertEquals(paid.cash(),restored.cash());assertEquals(paid.entries,restored.entries);assertEquals(listOf(rows.last()),restored.unpaid().filter{it.debtId==d.id})
        }finally{database{db.walletDao().read().payments(rows.first()).firstOrNull()?.let{repo.voidEntry(it.id,"测试清理")};repo.archiveDebt(d.id,true)};db.close()}
    }

}
