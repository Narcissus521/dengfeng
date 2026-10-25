package com.campusfinance.wallet

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExpenseAccountUiTest:TeaUiScenario() {
    private fun restore(key:String,value:String?){prefs.edit().apply{if(value==null)remove(key) else putString(key,value)}.commit()}
    private fun expense(){navigate("记账");tapTag("leaf-EXPENSE")}

    @Test fun savedExpenseAccountSurvivesReopeningAndRecreationButCancelledSelectionDoesNotReplaceIt() {
        val key=ExpenseAccountMemory.key(true);val previous=prefs.getString(key,null)
        prefs.edit().remove(key).commit();ready()
        val db=WalletDatabase.open(context,true);val note="expense-default-${System.currentTimeMillis()}"
        try {
            val before=runBlocking{db.walletDao().read()}
            val accounts=before.accounts.filter{it.disabledOn==null&&it.start<=today().toString()}
            assertTrue(accounts.size>=2)
            val first=accounts[0];val selected=accounts[1]
            expense();ui.onNodeWithText(first.name).assertIsDisplayed()
            tap(first.name);tap(selected.name);fill("金额（元）","12.34");fill("备注（可选）",note);submit()
            ui.waitUntil(10000){prefs.getString(key,null)==selected.id}
            val posted=runBlocking{db.walletDao().read()}
            assertEquals(selected.id,posted.entries.single{it.note==note}.accountId)
            assertEquals(before.cash()-1234,posted.cash())
            expense();ui.onNodeWithText(selected.name).assertIsDisplayed();back()
            ui.activityRule.scenario.recreate();ui.waitForIdle()
            expense();ui.onNodeWithText(selected.name).assertIsDisplayed();shot("expense-default-restored")
            tap(selected.name);tap(first.name);back()
            assertEquals(selected.id,prefs.getString(key,null))
            expense();ui.onNodeWithText(selected.name).assertIsDisplayed();back()
        }finally {
            runBlocking {db.walletDao().read().activeEntries.filter{it.note==note}.forEach{WalletRepository(db).voidEntry(it.id,"自动测试清理")}}
            db.close();restore(key,previous)
        }
    }

    @Test fun changingFromIncomeToExpenseUsesTheExpenseDefaultAndKeepsItsCurrentDraftChoice() {
        val key=ExpenseAccountMemory.key(true);val previous=prefs.getString(key,null)
        ready();val db=WalletDatabase.open(context,true)
        try {
            val accounts=runBlocking{db.walletDao().accounts()}.filter{it.disabledOn==null&&it.start<=today().toString()}
            val first=accounts[0];val selected=accounts[1]
            prefs.edit().putString(key,selected.id).commit()
            navigate("记账");tapTag("leaf-INCOME");tap("支出")
            ui.onNodeWithText(selected.name).assertIsDisplayed()
            tap(selected.name);tap(first.name);tap("收入");tap("支出")
            ui.onNodeWithText(first.name).assertIsDisplayed();back()
            assertEquals(selected.id,prefs.getString(key,null))
        }finally{db.close();restore(key,previous)}
    }

    @Test fun personalAndDemoDefaultsAreSeparateAndOtherEntryOperationsCannotReplaceThem() {
        val demoKey=ExpenseAccountMemory.key(true);val personalKey=ExpenseAccountMemory.key(false)
        val previousDemo=prefs.getString(demoKey,null);val previousPersonal=prefs.getString(personalKey,null)
        try {
            val memory=ExpenseAccountMemory(prefs)
            val expense=Entry(kind="EXPENSE",amount=100,accountId="demo-account",date=today().toString())
            memory.rememberSaved(true,expense,true)
            memory.rememberSaved(false,expense.copy(accountId="personal-account"),true)
            listOf("INCOME","TRANSFER","REFUND","REPAYMENT","ADJUSTMENT").forEach{kind->memory.rememberSaved(true,expense.copy(kind=kind,accountId="other"),true)}
            memory.rememberSaved(true,expense.copy(accountId="edited"),false)
            memory.rememberSaved(true,expense.copy(accountId="automatic",recurringOccurrenceId="fixed"),true)
            assertEquals("demo-account",prefs.getString(demoKey,null))
            assertEquals("personal-account",prefs.getString(personalKey,null))
        }finally{restore(demoKey,previousDemo);restore(personalKey,previousPersonal)}
    }
}
