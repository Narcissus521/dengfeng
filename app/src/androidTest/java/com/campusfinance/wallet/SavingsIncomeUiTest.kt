package com.campusfinance.wallet

import androidx.test.filters.SdkSuppress

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

@SdkSuppress(minSdkVersion=28)
class SavingsIncomeUiTest:TeaUiScenario() {
    @Test fun savingsEstimateAndAutomaticIncomeUseRealStoredAmounts() {
        ready();val db=WalletDatabase.open(context,true);val name="储蓄界面测试"+System.currentTimeMillis()
        val incomeName="收入界面测试"+System.currentTimeMillis()
        try {
            val cash=runBlocking{db.walletDao().read().cash()}
            navigate("规划");tap("查看全部储蓄计划");submit()
            fill("目标名称",name);fill("目标金额（元）","1200")
            fill("预计存够日期 YYYY-MM-DD",YearMonth.parse(currentMonth()).plusMonths(2).atEndOfMonth().toString())
            fill("目前已存（元）","240");find(hasText("每月建议存"));ui.onNodeWithText("¥ 480.00").assertExists();shot("savings-calculation");submit()
            ui.waitUntil(10000){runBlocking{db.walletDao().goals().any{it.name==name}}}
            runBlocking{val s=db.walletDao().read();assertEquals(cash,s.cash());assertEquals(48000L,s.goals.single{it.name==name}.progress().monthly)}
            navigate("账本");tap("固定收入");submit();fill("计划名称",incomeName);fill("每次金额（元）","123.45");submit()
            fill("记账时间 HH:mm","00:00");submit();submit();submit()
            ui.waitUntil(15000){runBlocking{db.walletDao().read().recurringRules.any{it.name==incomeName}}}
            ui.waitUntil(15000){runBlocking{db.walletDao().read().cash()==cash+12345}}
            tap(incomeName);tap("撤销本次")
            ui.waitUntil(10000){runBlocking{db.walletDao().read().cash()==cash}}
        }finally{
            runBlocking {
                val repo=WalletRepository(db);val s=db.walletDao().read()
                s.goals.filter{it.name==name}.forEach{repo.goal(it.copy(archived=true))}
                s.recurringRules.filter{it.name==incomeName}.forEach{r->
                    s.recurringOccurrences.filter{it.ruleId==r.id&&it.status=="POSTED"}.forEach{repo.undoRecurring(it.id,"测试清理")}
                    repo.recurring(r.copy(enabled=false),s.recurringAllocations.filter{it.ruleId==r.id})
                }
            };db.close()
        }
    }
}


