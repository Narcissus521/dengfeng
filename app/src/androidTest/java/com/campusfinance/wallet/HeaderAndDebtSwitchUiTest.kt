package com.campusfinance.wallet

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Isolated emulator/demo only. */
class HeaderAndDebtSwitchUiTest:TeaUiScenario(){
    @After fun finish(){prefs.edit().putBoolean("motion_enabled",true).putString("theme_mode","SYSTEM").commit()}
    @Test fun allRootPagesUseTheStatusInsetWithoutAnAppNameBar(){
        prefs.edit().putBoolean("motion_enabled",false).commit()
        listOf("LIGHT","DARK").forEach{theme->
            prefs.edit().putString("theme_mode",theme).commit();ready()
            listOf("首页","账本","规划","分析").forEachIndexed{index,page->
                navigate(page);ui.onNodeWithTag("brand-$index").assertDoesNotExist()
                ui.onAllNodesWithText("等风").assertCountEquals(0)
                val inset=androidx.core.view.ViewCompat.getRootWindowInsets(ui.activity.window.decorView)!!.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
                assertEquals(inset.toFloat(),ui.onNodeWithTag("main-page-viewport").fetchSemanticsNode().boundsInRoot.top,2f)
                shot("0151-$index-${theme.lowercase()}")
            }
        }
    }
    @Test fun mortgageSwitchOnlyChangesFutureBudgetAndAnalysisAndNeverRewritesCashOrHistory(){
        prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=runBlocking{db.walletDao().read()}
        val account=before.accounts.first{it.disabledOn==null}
        val debt=Debt(name="房贷开关-${newId().take(6)}",principal=0,purchaseDate=today().toString(),categoryId="debt-kind-loan",scheduleType="EQUAL",repaymentsAreConsumption=true,categoryGroupId="housing",subcategoryId="housing.rent",necessity="NECESSARY")
        val rows=manualDebtSchedule(debt.id,today().toString(),List(3){50000L})
        try{
            runBlocking{repo.debt(debt,rows);repo.repay(rows[0],account.id,today().toString())}
            val paid=runBlocking{db.walletDao().read()}
            navigate("规划");tapTag("card-PLAN_DEBT");tapTag("debt-plan-${debt.id}")
            tapTag("debt-detail-count-consumption")
            ui.waitUntil(10000){runBlocking{!db.walletDao().read().debts.first{it.id==debt.id}.repaymentsAreConsumption}}
            val disabled=runBlocking{db.walletDao().read()};assertEquals(paid.entries,disabled.entries);assertEquals(paid.cash(),disabled.cash());assertEquals(paid.usage(currentMonth()),disabled.usage(currentMonth()))
            shot("0151-mortgage-excluded")
            runBlocking{repo.repay(rows[1],account.id,today().toString())}
            val second=runBlocking{db.walletDao().read()};assertEquals(disabled.cash()-50000,second.cash());assertEquals(disabled.usage(currentMonth()),second.usage(currentMonth()))
            val range=ReportRange.month(currentMonth());assertEquals(ExpenseIndex(disabled).report(range).total.net,ExpenseIndex(second).report(range).total.net)
            tapTag("debt-detail-count-consumption")
            ui.waitUntil(10000){runBlocking{db.walletDao().read().debts.first{it.id==debt.id}.repaymentsAreConsumption}}
            runBlocking{repo.repay(rows[2],account.id,today().toString())}
            val third=runBlocking{db.walletDao().read()};assertEquals(second.cash()-50000,third.cash());assertEquals(second.usage(currentMonth())+50000,third.usage(currentMonth()))
            assertEquals(ExpenseIndex(second).report(range).total.net+50000,ExpenseIndex(third).report(range).total.net)
            assertEquals(paid.entries.first{it.installmentId==rows[0].id},third.entries.first{it.installmentId==rows[0].id})
            assertFalse(third.entries.first{it.installmentId==rows[1].id}.repaymentConsumption)
            assertTrue(third.entries.first{it.installmentId==rows[2].id}.repaymentConsumption)
        }finally{
            runBlocking{rows.forEach{row->db.walletDao().read().payments(row).firstOrNull()?.let{repo.voidEntry(it.id,"测试清理")}};repo.archiveDebt(debt.id,true)};db.close()
        }
    }
}
