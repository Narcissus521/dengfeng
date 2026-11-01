package com.campusfinance.wallet

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import android.database.sqlite.SQLiteDatabaseLockedException
import org.junit.Assert.*
import org.junit.Test

class ConsumptionTypeUiTest:TeaUiScenario() {
    // The test opens a second Room connection alongside foreground automation.
    // Retry only SQLite's transient busy condition; business/validation failures are never retried.
    private fun <T> database(action:suspend ()->T):T=runBlocking {
        repeat(10){try{return@runBlocking action()}catch(error:SQLiteDatabaseLockedException){delay(100)}}
        action()
    }
    @Test fun expenseHasOneClearPickerAndSavesTheSameTypeForAnalysisAndBudgetInBothThemes() {
        val oldTheme=prefs.getString("theme_mode",null);val oldMotion=prefs.getBoolean("motion_enabled",true)
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val prefix="unified-ui-${System.currentTimeMillis()}"
        try {
            listOf("LIGHT","DARK").forEachIndexed{i,theme->
                prefs.edit().putString("theme_mode",theme).putBoolean("motion_enabled",false).commit();ready()
                val before=database{db.walletDao().read()};navigate("记账");tapTag("leaf-EXPENSE")
                fill("金额（元）","12.34");find(hasTestTag("consumption-type-picker"))
                ui.onAllNodesWithTag("consumption-type-picker").assertCountEquals(1)
                ui.onNodeWithText("未判断").assertDoesNotExist();ui.onNodeWithText("必要消费").assertIsSelected()
                listOf("支出","收入","转账").forEach{ui.onNodeWithText(it).assertDoesNotExist()}
                ui.onAllNodes(hasText("预算归属",substring=true)).assertCountEquals(0)
                if(i==0){tap("非必要消费");ui.onNodeWithText("非必要消费").assertIsSelected();ui.onNodeWithText("必要消费").assertIsNotSelected()}
                tap("其他支出");ui.onAllNodesWithText("餐饮食品").onLast().performScrollTo().performClick();ui.waitForIdle()
                tap("其他餐饮");ui.onAllNodesWithText(if(i==0)"餐馆／小吃摊" else "零食／饮品").onLast().performScrollTo().performClick();ui.waitForIdle()
                find(hasText("实际发生日期 YYYY-MM-DD"));ui.onNodeWithText("实际发生日期 YYYY-MM-DD").assertTextContains(today().toString())
                val chosenDate=if(i==0)today() else today().minusDays(1)
                if(i!=0)fill("实际发生日期 YYYY-MM-DD",chosenDate.toString())
                shot("expense-form-${theme.lowercase()}")
                val note="$prefix-$theme";fill("备注（可选）",note);submit()
                ui.waitUntil(10000){database{db.walletDao().read().entries.any{it.note==note}}}
                val after=database{db.walletDao().read()};val e=after.entries.single{it.note==note}
                assertEquals(if(i==0)"NONESSENTIAL" else "NECESSARY",e.necessity);assertEquals(consumptionBucket(e.necessity),e.bucket)
                assertEquals(if(i==0)"food.restaurant" else "food.snacks",e.subcategoryId);assertEquals(chosenDate.toString(),e.date)
                assertEquals(before.cash()-1234,after.cash());assertEquals(before.usage(e.date.take(7),e.bucket)+1234,after.usage(e.date.take(7),e.bucket))
            }
        } finally {
            database{db.walletDao().read().activeEntries.filter{it.note.startsWith(prefix)}.forEach{repo.voidEntry(it.id,"统一消费性质测试清理")}}
            db.close();prefs.edit().putString("theme_mode",oldTheme).putBoolean("motion_enabled",oldMotion).commit()
        }
    }
    @Test fun legacyConflictIsVisibleAndOnlyExplicitSelectionChangesItsBudget() {
        val oldMotion=prefs.getBoolean("motion_enabled",true);prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=database{db.walletDao().read()}
        val e=Entry(kind="EXPENSE",amount=321,accountId=before.accounts.first().id,date=today().toString(),category="餐饮",bucket="FREE",necessity="NECESSARY",note="legacy-type-${System.currentTimeMillis()}")
        try {
            database{repo.entry(e)};navigate("分析");tapTag("analysis-entry-budget");tap("查看全部分类")
            val review=hasText("历史消费待确认 ·",substring=true) and hasClickAction();find(review);node(review).performClick();ui.waitForIdle()
            tapTag("consumption-review-${e.id}");ui.onNodeWithTag("form-title").assertTextEquals("修改分类与消费性质")
            find(hasTestTag("consumption-type-picker"));ui.onNodeWithText("必要消费").assertIsNotSelected();ui.onNodeWithText("非必要消费").assertIsNotSelected();ui.onNodeWithText("未判断").assertIsNotSelected()
            val untouched=database{db.walletDao().read()};assertEquals(e,untouched.entries.first{it.id==e.id})
            shot("consumption-type-legacy");tap("未判断");submit()
            ui.waitUntil(10000){database{db.walletDao().read().entries.first{it.id==e.id}.bucket=="UNDECIDED"}}
            val after=database{db.walletDao().read()};assertEquals(untouched.cash(),after.cash());assertEquals("UNDECIDED",after.entries.first{it.id==e.id}.necessity)
            assertEquals(untouched.usage(currentMonth(),"FREE")-321,after.usage(currentMonth(),"FREE"))
            assertEquals(untouched.usage(currentMonth(),"UNDECIDED")+321,after.usage(currentMonth(),"UNDECIDED"))
            ui.onNodeWithTag("consumption-review-${e.id}").assertDoesNotExist()
        } finally {
            database{if(db.walletDao().read().activeEntries.any{it.id==e.id})repo.voidEntry(e.id,"统一消费性质测试清理")};db.close();prefs.edit().putBoolean("motion_enabled",oldMotion).commit()
        }
    }
    @Test fun editingOldUnjudgedExpenseKeepsItsDateNatureAndBudgetUntilExplicitlyChanged() {
        val oldMotion=prefs.getBoolean("motion_enabled",true);prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);val before=database{db.walletDao().read()}
        val note="old-undecided-${System.currentTimeMillis()}";val date=today().minusDays(1).toString()
        val e=Entry(kind="EXPENSE",amount=567,accountId=before.accounts.first().id,date=date,category="餐饮食品",categoryGroupId="food",subcategoryId="food.snacks",necessity="UNDECIDED",bucket="NECESSARY",note=note)
        try {
            database{repo.entry(e)};navigate("记账");tapTag("leaf-RECORDS")
            // Yesterday may belong to the previous month; keep the historical-edit scenario valid on day one.
            if(date.take(7)!=currentMonth())tap("‹")
            fill("搜索分类、账户或备注",note);tapTag("record-${e.id}")
            find(hasText("零食／饮品"));tap("修改记录");find(hasText("消费性质"));ui.onNodeWithText("未判断").assertExists()
            find(hasText("实际发生日期 YYYY-MM-DD"));ui.onNodeWithText("实际发生日期 YYYY-MM-DD").assertTextContains(date)
            fill("备注（可选）",note+"-edited");fill("修改原因（可选）","只改备注，保留旧消费性质与日期");submit()
            ui.waitUntil(10000){database{db.walletDao().read().entries.first{it.id==e.id}.note.endsWith("-edited")}}
            val after=database{db.walletDao().read()};val saved=after.entries.first{it.id==e.id}
            assertEquals("UNDECIDED",saved.necessity);assertEquals("NECESSARY",saved.bucket);assertEquals(date,saved.date)
            assertEquals(before.cash()-567,after.cash());assertEquals("food.snacks",saved.subcategoryId)
        } finally {database{if(db.walletDao().read().activeEntries.any{it.id==e.id})repo.voidEntry(e.id,"历史消费编辑测试清理")};db.close();prefs.edit().putBoolean("motion_enabled",oldMotion).commit()}
    }
}
