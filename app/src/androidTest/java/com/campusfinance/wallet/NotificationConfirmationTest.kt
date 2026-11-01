package com.campusfinance.wallet

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class NotificationConfirmationTest {
    @Test fun backgroundRunNeverPostsADueFixedIncomeEvenWithConsent()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val prefs=context.getSharedPreferences("wallet_settings",Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ConsentPolicy.KEY,true).putString("mode","demo").commit()
        val db=WalletDatabase.open(context,true);val repo=WalletRepository(db);repo.prepare(true)
        val initial=db.walletDao().read();val account=initial.accounts.first{it.disabledOn==null}
        val rule=RecurringRule(name="后台禁止自动到账",kind="INCOME",amount=50000,frequency="DAILY",time="00:00",startDate=today().toString(),category="生活费")
        val allocations=listOf(RecurringAllocation(rule.id,account.id,rule.amount))
        try{
            repo.recurring(rule,allocations);val before=db.walletDao().read()
            WalletAutomation.run(context,notify=false);val after=db.walletDao().read()
            assertEquals(before.entries,after.entries);assertEquals(before.cash(),after.cash());assertEquals(before.recurringOccurrences,after.recurringOccurrences)
            assertTrue(pendingRecurring(after,java.time.ZonedDateTime.now(financeZone)).any{it.first.id==rule.id})
        }finally{repo.recurring(rule.copy(enabled=false),allocations);db.close()}
    }
    @Test fun reminderStillNotifiesAfterExpenseButNeverPostsDueIncomeAndOnlySendsOnce()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        assertTrue("Grant notifications on the isolated emulator before this test",WalletAutomation.notificationsAllowed(context))
        val prefs=context.getSharedPreferences("wallet_settings",Context.MODE_PRIVATE)
        val now=today().atTime(LocalTime.NOON).atZone(financeZone);val mode="notification-test-${newId()}"
        val manager=context.getSystemService(NotificationManager::class.java)
        val beforeKey="reminded_${mode}_${today()}"
        val account=Account("a","现金","现金",100000,today().toString())
        val s=Ledger(accounts=listOf(account),entries=listOf(Entry(kind="EXPENSE",amount=1000,accountId="a",date=today().toString(),category="餐饮食品")),reminder=ReminderSettings(enabled=true,startTime="11:00",endTime="13:00"))
        try{
            WalletAutomation.remind(context,s,now,mode)
            assertTrue(prefs.getBoolean(beforeKey,false));assertTrue(manager.activeNotifications.any{it.id==702})
            manager.cancel(702)
            WalletAutomation.remind(context,s,now.plusMinutes(1),mode)
            assertTrue(manager.activeNotifications.none{it.id==702})
            val lateMode=mode+"-late";WalletAutomation.remind(context,s,now.plusHours(3),lateMode)
            assertFalse(prefs.getBoolean("reminded_${lateMode}_${today()}",false));assertTrue(manager.activeNotifications.none{it.id==702})
            assertEquals(99000L,s.cash());assertEquals(1,s.entries.size)
        }finally{manager.cancel(702);prefs.edit().remove(beforeKey).commit()}
    }
}
