package com.campusfinance.wallet

import android.database.sqlite.SQLiteDatabaseLockedException
import androidx.compose.ui.test.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecordBookUiTest:TeaUiScenario() {
    // Repository commands own their transactions. An extra fixture-wide transaction on this
    // second Room connection can contend with the foreground connection's invalidation sync.
    private fun <T> database(action:suspend ()->T):T=runBlocking {
        repeat(30){try{return@runBlocking action()}catch(_:SQLiteDatabaseLockedException){delay(100)}};action()
    }
    private fun openRecords(){navigate("记账");ui.onNodeWithTag("leaf-TRANSFER").assertDoesNotExist();tapTag("leaf-RECORDS");ui.onNodeWithTag("form-title").assertTextEquals("记账记录");find(hasTestTag("records-count"))}
    private fun assertSummary(text:String){find(hasTestTag("records-summary"));ui.onNodeWithTag("records-summary").assertContentDescriptionEquals(text)}
    @Test fun recordsEntryFiltersBothTransferSidesAndReturnsFromEditingWithItsSearchIntact() {
        val oldTheme=prefs.getString("theme_mode",null);val oldMotion=prefs.getBoolean("motion_enabled",true)
        prefs.edit().putString("theme_mode","LIGHT").putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=database{db.walletDao().read()};val prefix="records-ui-${System.currentTimeMillis()}"
        val a=before.accounts[0];val b=before.accounts[1]
        val expense=Entry(kind="EXPENSE",amount=2000,accountId=a.id,date=today().toString(),category="餐饮食品",categoryGroupId="food",subcategoryId="food.canteen",note=prefix+"-expense").withConsumptionType("NECESSARY")
        val refund=Entry(kind="REFUND",amount=500,accountId=b.id,date=today().toString(),originalId=expense.id,note=prefix+"-refund")
        val income=Entry(kind="INCOME",amount=3000,accountId=a.id,date=today().toString(),category="红包",note=prefix+"-income")
        val transfer=Entry(kind="TRANSFER",amount=1000,accountId=a.id,toAccountId=b.id,date=today().toString(),note=prefix+"-transfer")
        try {
            database{listOf(expense,refund,income,transfer).forEach{repo.entry(it)}}
            navigate("分析");openRecords();ui.onNodeWithTag("dock-分析").assertDoesNotExist();ui.onNodeWithTag("full-screen-destination").assertIsDisplayed()
            fill("搜索分类、账户或备注",prefix);find(hasTestTag("records-count"))
            assertSummary("收入 ¥30.00，支出 ¥20.00，退款 ¥5.00，净消费 ¥15.00，还款 ¥0.00");shot("record-book-light")
            tapTag("records-filter-toggle");ui.onNodeWithText("记录类型").assertDoesNotExist()
            tap("全部账户");ui.onAllNodesWithText(b.name).onLast().performScrollTo().performClick();ui.waitForIdle()
            tapTag("record-${transfer.id}");ui.onNodeWithTag("form-title").assertTextEquals("交易详情");back()
            find(hasTestTag("records-filter-toggle"));ui.onNodeWithTag("records-filter-toggle").assertTextContains("全部分类 · 全部性质 · ${b.name}")
            tapTag("records-clear");fill("搜索分类、账户或备注",prefix);tapTag("record-${expense.id}")
            tap("修改记录");fill("金额（元）","22.00");fill("修改原因（可选）","记录页编辑检查");submit()
            ui.waitUntil(10000){database{db.walletDao().read().entries.first{it.id==expense.id}.amount==2200L}}
            back();find(hasText("搜索分类、账户或备注"));ui.onNodeWithText("搜索分类、账户或备注").assertTextContains(prefix)
            assertSummary("收入 ¥30.00，支出 ¥22.00，退款 ¥5.00，净消费 ¥17.00，还款 ¥0.00")
            assertEquals(before.cash()+1300,database{db.walletDao().read().cash()})
        } finally {
            database{listOf(refund,transfer,income,expense).forEach{e->if(db.walletDao().read().activeEntries.any{it.id==e.id})repo.voidEntry(e.id,"记录页测试清理")}}
            db.close();prefs.edit().putString("theme_mode",oldTheme).putBoolean("motion_enabled",oldMotion).commit()
        }
    }
    @Test fun darkRecordsCanShowVoidedEntriesWithoutCountingThemAndExplainEmptyResults() {
        val oldTheme=prefs.getString("theme_mode",null);val oldMotion=prefs.getBoolean("motion_enabled",true)
        prefs.edit().putString("theme_mode","DARK").putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=database{db.walletDao().read()};val prefix="records-dark-${System.currentTimeMillis()}"
        val entry=Entry(kind="EXPENSE",amount=1234,accountId=before.accounts.first().id,date=today().toString(),category="餐饮",note=prefix)
        val voided=entry.copy(id=newId(),amount=500,note=prefix+"-voided")
        try {
            database{repo.entry(entry);repo.entry(voided);repo.voidEntry(voided.id,"测试作废")}
            openRecords();fill("搜索分类、账户或备注",prefix);tapTag("records-filter-toggle");tapTag("records-show-voided")
            assertSummary("收入 ¥0.00，支出 ¥12.34，退款 ¥0.00，净消费 ¥12.34，还款 ¥0.00")
            tapTag("record-${voided.id}");find(hasText("已撤销"));back();find(hasTestTag("records-show-voided"));ui.onNodeWithTag("records-show-voided").assertIsOn()
            tapTag("records-filter-toggle");find(hasTestTag("records-summary"));shot("record-book-dark")
            fill("搜索分类、账户或备注",prefix+"-absent");find(hasTestTag("records-empty"));ui.onNodeWithText("没有符合条件的记录").assertIsDisplayed();shot("record-book-empty")
            tapTag("records-clear");find(hasTestTag("records-count"));ui.onNodeWithText("搜索分类、账户或备注").assertTextContains("")
        } finally {
            database{if(db.walletDao().read().activeEntries.any{it.id==entry.id})repo.voidEntry(entry.id,"记录页测试清理")};db.close();prefs.edit().putString("theme_mode",oldTheme).putBoolean("motion_enabled",oldMotion).commit()
        }
    }
    @Test fun primaryCategoryAndNatureReplaceRecordKindAndCombineInBothThemes() {
        val oldTheme=prefs.getString("theme_mode",null);val oldMotion=prefs.getBoolean("motion_enabled",true)
        prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=database{db.walletDao().read()};val prefix="record-filters-${System.currentTimeMillis()}"
        val account=before.accounts.first().id
        fun expense(amount:Long,nature:String,note:String)=Entry(kind="EXPENSE",amount=amount,accountId=account,date=today().toString(),category="餐饮食品",categoryGroupId="food",subcategoryId="food.canteen",note=prefix+note).withConsumptionType(nature)
        val necessary=expense(2000,"NECESSARY","-necessary");val nonessential=expense(1000,"NONESSENTIAL","-nonessential");val undecided=expense(300,"UNDECIDED","-undecided")
        val clothes=expense(700,"NECESSARY","-clothing").copy(category="服饰鞋包",categoryGroupId="clothing",subcategoryId="clothing.clothes")
        val refund=Entry(kind="REFUND",amount=500,accountId=account,date=today().toString(),originalId=necessary.id,note=prefix+"-refund")
        val income=Entry(kind="INCOME",amount=6000,accountId=account,date=today().toString(),category="红包",note=prefix+"-income")
        val transfer=Entry(kind="TRANSFER",amount=1000,accountId=account,toAccountId=before.accounts[1].id,date=today().toString(),note=prefix+"-transfer")
        fun choose(tag:String,target:String){val trigger=hasAnyAncestor(hasTestTag(tag)) and hasClickAction();find(trigger);node(trigger).performClick();ui.onAllNodesWithText(target).onLast().performScrollTo().performClick();ui.waitForIdle()}
        try {
            database{listOf(necessary,nonessential,undecided,clothes,refund,income,transfer).forEach{repo.entry(it)}}
            listOf("LIGHT","DARK").forEach{theme->
                prefs.edit().putString("theme_mode",theme).commit();ready();openRecords();fill("搜索分类、账户或备注",prefix);tapTag("records-filter-toggle")
                ui.onNodeWithText("记录类型").assertDoesNotExist()
                choose("records-category-select","餐饮食品");choose("records-nature-select","必要消费")
                assertSummary("收入 ¥0.00，支出 ¥20.00，退款 ¥5.00，净消费 ¥15.00，还款 ¥0.00")
                find(hasTestTag("records-filter-toggle"));ui.onNodeWithTag("records-filter-toggle").assertTextContains("餐饮食品 · 必要消费",substring=true)
                shot("record-primary-filters-${theme.lowercase()}")
                tapTag("record-${necessary.id}");ui.onNodeWithTag("form-title").assertTextEquals("交易详情");back()
                find(hasTestTag("records-filter-toggle"));ui.onNodeWithTag("records-filter-toggle").assertTextContains("餐饮食品 · 必要消费",substring=true)
                choose("records-nature-select","非必要消费");assertSummary("收入 ¥0.00，支出 ¥10.00，退款 ¥0.00，净消费 ¥10.00，还款 ¥0.00")
                choose("records-nature-select","未判断");assertSummary("收入 ¥0.00，支出 ¥3.00，退款 ¥0.00，净消费 ¥3.00，还款 ¥0.00")
                choose("records-nature-select","全部性质");assertSummary("收入 ¥0.00，支出 ¥33.00，退款 ¥5.00，净消费 ¥28.00，还款 ¥0.00")
                tapTag("records-clear");fill("搜索分类、账户或备注",prefix);assertSummary("收入 ¥60.00，支出 ¥40.00，退款 ¥5.00，净消费 ¥35.00，还款 ¥0.00")
                choose("records-nature-select","必要消费");assertSummary("收入 ¥0.00，支出 ¥27.00，退款 ¥5.00，净消费 ¥22.00，还款 ¥0.00")
            }
            assertEquals(before.cash()+2500,database{db.walletDao().read().cash()})
        } finally {
            database{listOf(refund,transfer,income,clothes,undecided,nonessential,necessary).forEach{e->if(db.walletDao().read().activeEntries.any{it.id==e.id})repo.voidEntry(e.id,"组合筛选测试清理")}}
            db.close();prefs.edit().putString("theme_mode",oldTheme).putBoolean("motion_enabled",oldMotion).commit()
        }
    }
}
