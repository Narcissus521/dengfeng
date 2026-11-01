package com.campusfinance.wallet

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Only the isolated emulator's demo ledger is opened by these scenarios. */
class ConfirmedWorkflowUiTest:TeaUiScenario() {
    @After fun finish(){prefs.edit().putBoolean(ConsentPolicy.KEY,true).putString("mode","demo").putBoolean("motion_enabled",true).putString("theme_mode","SYSTEM").commit()}
    private fun awaitHome(){ui.waitUntil(20000){ui.onAllNodesWithTag("dock-首页").fetchSemanticsNodes().isNotEmpty()};ui.waitForIdle()}

    @Test fun decliningConsentFinishesWithoutAcceptance(){
        prefs.edit().remove(ConsentPolicy.KEY).remove("mode").commit();ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("first-consent").assertIsDisplayed();tapTag("consent-decline")
        assertFalse(prefs.getBoolean(ConsentPolicy.KEY,false));assertNull(prefs.getString("mode",null))
        ui.waitUntil(10000){ui.activityRule.scenario.state==androidx.lifecycle.Lifecycle.State.DESTROYED}
    }

    @Test fun productDebtIsCategorizedAndSplitPaymentCountsOnceWithExplicitHistoryPolicy(){
        prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=runBlocking{db.walletDao().read()}
        val name="三星确认用途-${newId().take(8)}";var id:String?=null
        try{
            navigate("规划");tapTag("card-PLAN_DEBT");tap("＋ 添加负债")
            fill("负债名称",name);find(hasTestTag("debt-count-consumption"));ui.onNodeWithTag("debt-count-consumption").assertIsOn()
            tap("其他支出");tap("数码电子");tap("其他数码");tap("电子设备")
            fill("还款总期数（1–360）","12");fill("每月还款金额（元）","500")
            fill("首期还款日期 YYYY-MM-DD",today().toString());shot("015-debt-contract");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().debts.any{it.name==name}}}
            val d=runBlocking{db.walletDao().read().debts.first{it.name==name}};id=d.id
            assertEquals("digital",d.categoryGroupId);assertEquals("digital.devices",d.subcategoryId);assertTrue(d.repaymentsAreConsumption)
            assertEquals(before.cash(),runBlocking{db.walletDao().read().cash()})
            tapTag("debt-plan-${d.id}");tapTag("repay1")
            val accounts=before.accounts.filter{it.disabledOn==null}.take(2)
            find(hasTestTag("repayAccount-${accounts[1].id}"));ui.onNodeWithTag("repayAccount-${accounts[1].id}").performClick()
            fillTag("repayAmount-${accounts[0].id}","300");fillTag("repayAmount-${accounts[1].id}","200");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().usage(currentMonth())==before.usage(currentMonth())+50000}}
            val paid=runBlocking{db.walletDao().read()};assertEquals(before.cash()-50000,paid.cash())
            assertEquals(550000L,paid.unpaid().filter{it.debtId==d.id}.map{it.due}.exactSum())
            assertEquals(2,paid.activeEntries.count{it.installmentId==paid.installments.first{row->row.debtId==d.id&&row.number==1}.id})
            tap("修改负债与还款计划");find(hasTestTag("debt-apply-history"));ui.onNodeWithTag("debt-apply-history").assertIsOff()
            tapTag("debt-count-consumption");submit()
            ui.waitUntil(10000){runBlocking{!db.walletDao().read().debts.first{it.id==d.id}.repaymentsAreConsumption}}
            assertEquals(paid.entries,runBlocking{db.walletDao().read().entries})
            tap("修改负债与还款计划");tapTag("debt-apply-history");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().usage(currentMonth())==before.usage(currentMonth())}}
            assertEquals(paid.cash(),runBlocking{db.walletDao().read().cash()})
        }finally{
            id?.let{debtId->runBlocking{val s=db.walletDao().read();s.installments.filter{it.debtId==debtId}.forEach{row->s.payments(row).firstOrNull()?.let{repo.voidEntry(it.id,"测试清理")}};repo.archiveDebt(debtId,true)}}
            db.close()
        }
    }

    @Test fun upgradeConsentGatesExistingLedgerAndStaysAcceptedAfterRecreation(){
        ready();val db=WalletDatabase.open(context,true)
        try{
            val before=runBlocking{db.walletDao().read()}
            prefs.edit().remove(ConsentPolicy.KEY).commit();ui.activityRule.scenario.recreate()
            ui.onNodeWithTag("first-consent").assertIsDisplayed();ui.onNodeWithTag("dock-首页").assertDoesNotExist()
            shot("015-first-consent")
            ui.onNodeWithTag("consent-agree").performClick();awaitHome()
            if(ui.onAllNodesWithTag("pending-recurring-later").fetchSemanticsNodes().isNotEmpty())tapTag("pending-recurring-later")
            assertTrue(prefs.getBoolean(ConsentPolicy.KEY,false));assertEquals("demo",prefs.getString("mode",null))
            assertEquals(before,runBlocking{db.walletDao().read()})
            ui.activityRule.scenario.recreate();awaitHome();ui.onNodeWithTag("first-consent").assertDoesNotExist()
        }finally{db.close()}
    }

    @Test fun freshConsentOffersOnlyAnEmptyPersonalLedger(){
        prefs.edit().remove(ConsentPolicy.KEY).remove("mode").commit();ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("first-consent").assertIsDisplayed();ui.onNodeWithText("创建我的账本").assertDoesNotExist()
        tapTag("consent-agree");find(hasText("创建我的账本"));ui.onNodeWithText("进入示例账本").assertDoesNotExist()
        assertNull(prefs.getString("mode",null));shot("015-personal-welcome")
    }

    @Test fun rootHeaderAndSettingsExposeOnlyTheRetainedControls(){
        prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        ui.onAllNodesWithText("设置").assertCountEquals(0)
        navigate("记账");tapTag("leaf-SETTINGS");find(hasText("共同设计与免责声明"))
        listOf("独立演示账本","导入与导出","查看全部变更历史","新手教程").forEach{ui.onAllNodesWithText(it).assertCountEquals(0)}
        shot("015-settings");tap("共同设计与免责声明");find(hasText("梨花怎黛玉"));find(hasText("使用免责声明"));back()
        ui.onNodeWithTag("form-title").assertTextEquals("设置")
    }

    @Test fun recordEditAllowsBlankReasonHidesIdentifierAndStillShowsSaveConfirmation(){
        prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=runBlocking{db.walletDao().read()}
        val entry=Entry(kind="EXPENSE",amount=1000,accountId=before.accounts.first{it.disabledOn==null}.id,date=today().toString(),category="餐饮食品",categoryGroupId="food",subcategoryId="food.canteen",necessity="NECESSARY",bucket="NECESSARY",note="可选原因-${newId().take(8)}")
        try{
            runBlocking{repo.entry(entry)}
            navigate("记账");tapTag("leaf-RECORDS");fill("搜索分类、账户或备注",entry.note);tapTag("quick-entry-${entry.id}")
            ui.onAllNodes(hasText("记录编号",substring=true)).assertCountEquals(0)
            tap("修改记录");fill("金额（元）","12");find(hasText("修改原因（可选）"));submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().entries.first{it.id==entry.id}.amount==1200L}}
            ui.waitUntil(10000){ui.onAllNodesWithTag("save-confirmation").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("save-confirmation").assertTextContains("保存成功",substring=true)
            val after=runBlocking{db.walletDao().read()};assertEquals(before.cash()-1200,after.cash());assertTrue(after.audits.count{it.entityId==entry.id}>=2)
        }finally{runBlocking{if(db.walletDao().read().activeEntries.any{it.id==entry.id})repo.voidEntry(entry.id,"测试清理")};db.close()}
    }

    @Test fun pendingPromptCanDismissThenEditOneOccurrenceAndPermanentlySkipAnother(){
        prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db)
        val old=runBlocking{db.walletDao().read()};val accounts=old.accounts.filter{it.disabledOn==null}.take(2)
        assertEquals(2,accounts.size)
        val rule=RecurringRule(id="confirm-ui-${newId()}",name="确认流程早餐",kind="EXPENSE",amount=50000,frequency="DAILY",time="00:00",startDate=today().minusDays(1).toString(),category="餐饮食品",categoryGroupId="food",subcategoryId="food.canteen",necessity="NECESSARY",bucket="NECESSARY")
        val allocation=listOf(RecurringAllocation(rule.id,accounts[0].id,30000),RecurringAllocation(rule.id,accounts[1].id,20000))
        try{
            runBlocking{repo.recurring(rule,allocation)}
            ui.activityRule.scenario.recreate();awaitHome();ui.onNodeWithTag("pending-recurring-dialog").assertIsDisplayed()
            tapTag("pending-recurring-later");assertEquals(old.entries,runBlocking{db.walletDao().read().entries})
            ui.activityRule.scenario.recreate();awaitHome();tap("查看待确认清单")
            tapTag("pending-${rule.id}@${today().minusDays(1)}")
            fillTag("confirm-recurring-amount","400.00")
            fill("实际发生日期 YYYY-MM-DD",today().toString())
            find(hasTestTag("confirm-share-${accounts[0].id}"));ui.onNodeWithTag("confirm-share-${accounts[0].id}").assertTextContains("240.00")
            fillTag("confirm-share-${accounts[0].id}","200.00");fillTag("confirm-share-${accounts[1].id}","200.00")
            shot("015-confirm-splits");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().recurringOccurrences.any{it.ruleId==rule.id}}}
            val after=runBlocking{db.walletDao().read()};assertEquals(old.cash()-40000,after.cash())
            assertEquals(rule,after.recurringRules.first{it.id==rule.id});assertEquals(allocation.toSet(),after.recurringAllocations.filter{it.ruleId==rule.id}.toSet())
            val event=after.recurringOccurrences.single{it.ruleId==rule.id};assertEquals(today().toString(),event.postedDate)
            assertEquals(listOf(20000L,20000L),after.entries.filter{it.recurringOccurrenceId==event.id}.map{it.amount}.sorted())
            tapTag("pending-${rule.id}@${today()}");tapTag("skip-recurring")
            ui.waitUntil(10000){runBlocking{db.walletDao().read().recurringOccurrences.count{it.ruleId==rule.id}==2}}
            assertEquals(after.cash(),runBlocking{db.walletDao().read().cash()});assertTrue(runBlocking{pendingRecurring(db.walletDao().read(),java.time.ZonedDateTime.now(financeZone)).none{it.first.id==rule.id}})
        }finally{
            runBlocking{
                val state=db.walletDao().read()
                state.recurringOccurrences.filter{it.ruleId==rule.id&&it.status=="POSTED"}.forEach{event->state.activeEntries.firstOrNull{it.recurringOccurrenceId==event.id}?.let{repo.voidEntry(it.id,"测试清理")}}
                repo.recurring(rule.copy(enabled=false),allocation)
            };db.close()
        }
    }

    @Test fun raisedDoubleRingsStayReadableAndInteractiveInBothThemes(){
        listOf("LIGHT","DARK").forEach{theme->
            prefs.edit().putString("theme_mode",theme).putBoolean("motion_enabled",false).commit();ready();navigate("分析")
            find(hasTestTag("analysis-open-category"));shot("015-double-ring-${theme.lowercase()}")
            tapTag("analysis-open-category");ui.onNodeWithTag("form-title").assertTextEquals("分类分析")
            back();ui.onNodeWithTag("analysis-entry-calendar").assertIsDisplayed()
        }
    }
}
