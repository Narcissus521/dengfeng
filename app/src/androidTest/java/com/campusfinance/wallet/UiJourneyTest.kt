package com.campusfinance.wallet

import androidx.test.filters.SdkSuppress

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion=28)
class UiJourneyTest:TeaUiScenario() {
    @Test fun cashAndQuotedInstallmentsGiveAdviceWithoutPostingMoney() {
        ready();val db=WalletDatabase.open(context,true)
        val old=prefs.getString("purchase_demo",null)
        try {
            val before=runBlocking{db.walletDao().read()}
            tap("买前想想");fill("商品名称","测试耳机");fill("商品价格（元）","500");submit()
            fill("首付（元）","50");fill("还款总期数（1–120）","3");submit()
            fill("每期应付（含费用，元）","160");submit()
            ui.onNodeWithTag("form-title").assertTextEquals("购买建议")
            find(hasText("¥530.00"));shot("purchase-advice")
            tap("一次付：余额与预算");find(hasText("购买后账户总资产"))
            val result=before.review(PurchaseStore.load(context,true)!!)
            ui.onNodeWithText("¥ "+Money.format(result.cashAfter)).assertExists()
            back();tap("分期付：后续付款安排");find(hasText("现在需付"));shot("purchase-installments")
            assertEquals(before.cash(),runBlocking{db.walletDao().read().cash()})
            assertEquals(before.entries,runBlocking{db.walletDao().read().entries})
            navigate("首页");ui.onNodeWithText("测试耳机").assertIsDisplayed()
        }finally{prefs.edit().apply{if(old==null)remove("purchase_demo") else putString("purchase_demo",old)}.commit();db.close()}
    }
}


