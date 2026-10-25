package com.campusfinance.wallet

import androidx.test.filters.SdkSuppress

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion=28)
class DebtUiTest:TeaUiScenario() {
    private val a="ui-e2e-debt-a";private val b="ui-e2e-debt-b"
    private val equalName="ui-e2e-debt-equal";private val customName="ui-e2e-debt-custom";private val kind="ui-e2e-debt-kind"
    private val mortgageName="ui-e2e-30年房贷"
    private fun clean(db:WalletDatabase)=runBlocking {
        val s=db.walletDao().read();val ids=s.debts.filter{it.name in listOf(equalName,customName,mortgageName)}.map{it.id}
        val rows=s.installments.filter{it.debtId in ids}.map{it.id}
        val entries=s.entries.filter{it.installmentId in rows||it.accountId in listOf(a,b)}.map{it.id}
        val kinds=s.debtCategories.filter{it.name in listOf(kind,"$kind-updated")}.map{it.id}
        val sql=db.openHelper.writableDatabase;sql.beginTransaction()
        try {
            (ids+rows+entries+kinds+listOf(a,b)).forEach{sql.execSQL("DELETE FROM audits WHERE entityId = ?",arrayOf(it))}
            entries.forEach{sql.execSQL("DELETE FROM entries WHERE id = ?",arrayOf(it))}
            rows.forEach{sql.execSQL("DELETE FROM installments WHERE id = ?",arrayOf(it))}
            ids.forEach{sql.execSQL("DELETE FROM debts WHERE id = ?",arrayOf(it))}
            kinds.forEach{sql.execSQL("DELETE FROM debt_categories WHERE id = ?",arrayOf(it))}
            listOf(a,b).forEach{sql.execSQL("DELETE FROM accounts WHERE id = ?",arrayOf(it))}
            sql.setTransactionSuccessful()
        }finally{sql.endTransaction()}
    }
    @Test fun createEqualAndCustomDebtsCombineTwoAccountsAndUndoTogether() {
        val db=WalletDatabase.open(context,true)
        try {
            runBlocking{
                val r=WalletRepository(db);r.prepare(true);clean(db)
                r.account(Account(a,"测试微信","微信",100000,today().minusMonths(1).toString()),"界面测试")
                r.account(Account(b,"测试银行卡","银行卡",50000,today().minusMonths(1).toString()),"界面测试")
            }
            ready();val before=runBlocking{db.walletDao().read()}
            navigate("规划");tapTag("card-PLAN_DEBT");submit()
            fill("负债名称",equalName);fill("每月还款金额（元）","120.50");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().debts().any{it.name==equalName}}}
            runBlocking{val s=db.walletDao().read();val d=s.debts.single{it.name==equalName};assertEquals(listOf(12050L,12050L,12050L),s.installments.filter{it.debtId==d.id}.sortedBy{it.number}.map{it.due});assertEquals(before.cash(),s.cash())}
            tap(equalName);tap("修改负债与还款计划");fill("还款总期数（1–360）","2");submit()
            ui.waitUntil(10000){runBlocking{val s=db.walletDao().read();s.installments.count{it.debtId==s.debts.single{it.name==equalName}.id}==2}}
            navigate("规划");tapTag("card-PLAN_DEBT");submit();fill("负债名称",customName)
            tap("＋ 自定义欠款种类");fill("种类名称",kind);tap("添加并选用")
            tap("逐期设置")
            listOf("500.00","600.00","700.00").forEachIndexed{i,v->fillTag("debtAmount"+(i+1),v)}
            find(hasText("应还合计"));shot("custom-debt");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().debts().any{it.name==customName}}}
            tap(customName);tapTag("repay1")
            val default=before.accounts.first{it.disabledOn==null}.id
            tapTag("repayAccount-$default");tapTag("repayAccount-$a");fillTag("repayAmount-$a","300.00")
            tapTag("repayAccount-$b");fillTag("repayAmount-$b","200.00")
            find(hasText("各账户扣款合计"));shot("combined-repayment");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().read().cash()==before.cash()-50000}}
            runBlocking{val s=db.walletDao().read();assertEquals(70000L,s.balance(a));assertEquals(30000L,s.balance(b));assertEquals(before.usage(currentMonth()),s.usage(currentMonth()))}
            tap("测试微信 ¥300.00 · 查看记录");tap("撤销错误还款记录");fill("撤销原因","整笔测试纠错");tap("确认撤销")
            ui.waitUntil(10000){runBlocking{db.walletDao().read().cash()==before.cash()}}
            runBlocking{val s=db.walletDao().read();assertEquals(100000L,s.balance(a));assertEquals(50000L,s.balance(b))}
            navigate("规划");tapTag("card-PLAN_DEBT");tap("管理自定义欠款种类")
            val category=runBlocking{db.walletDao().debtCategories().single{it.name==kind}}
            tapTag("debtCategoryEdit-"+category.id);fill("种类名称","$kind-updated");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().debtCategories().single{it.id==category.id}.name=="$kind-updated"}}
            tapTag("debtCategoryToggle-"+category.id)
            ui.waitUntil(10000){runBlocking{db.walletDao().debtCategories().single{it.id==category.id}.archived}}
            navigate("首页")
        }finally{runCatching{navigate("首页")};clean(db);db.close()}
    }
    @Test fun thirtyYearDebtCanBeSavedReopenedAndLastPeriodAdjusted() {
        val db=WalletDatabase.open(context,true)
        try {
            runBlocking{WalletRepository(db).prepare(true);clean(db)};ready()
            val before=runBlocking{db.walletDao().read().cash()}
            navigate("规划");tapTag("card-PLAN_DEBT");submit()
            fill("负债名称",mortgageName);tap("30年");fill("每月还款金额（元）","2000.00")
            find(hasText("应还合计"));shot("mortgage-360-preview");submit()
            ui.waitUntil(15000){runBlocking{db.walletDao().debts().any{it.name==mortgageName}}}
            var state=runBlocking{db.walletDao().read()};val id=state.debts.single{it.name==mortgageName}.id
            var plan=state.installments.filter{it.debtId==id}.sortedBy{it.number}
            assertEquals(360,plan.size);assertEquals(72000000L,plan.map{it.due}.exactSum());assertEquals(before,state.cash())
            assertEquals(java.time.LocalDate.parse(plan.first().date).plusMonths(359).toString(),plan.last().date)
            tap(mortgageName);tapTag("debt-detail-last-group");find(hasText("第 360 期"));shot("mortgage-360-last-period")
            tap("修改负债与还款计划");tap("逐期设置");tapTag("debt-edit-last-group")
            fillTag("debtAmount360","1999.99");submit()
            ui.waitUntil(15000){runBlocking{db.walletDao().installments().single{it.debtId==id&&it.number==360}.due==199999L}}
            state=runBlocking{db.walletDao().read()};plan=state.installments.filter{it.debtId==id}.sortedBy{it.number}
            assertEquals(360,plan.size);assertEquals(71999999L,plan.map{it.due}.exactSum());assertEquals(before,state.cash())
            val decoded=Backup.decode(Backup.encode(state));assertEquals(plan.toSet(),decoded.installments.filter{it.debtId==id}.toSet())
        }finally{runCatching{navigate("首页")};clean(db);db.close()}
    }
}


