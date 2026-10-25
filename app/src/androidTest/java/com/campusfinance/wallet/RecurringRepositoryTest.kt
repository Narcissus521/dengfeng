package com.campusfinance.wallet

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.ZonedDateTime

class RecurringRepositoryTest {
    private lateinit var db:WalletDatabase
    private lateinit var repo:WalletRepository
    private fun clock()=today().atTime(23,59).atZone(financeZone)
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),WalletDatabase::class.java).build()
        repo=WalletRepository(db);repo.prepare(false)
        listOf("a","b").forEach { repo.account(Account(id=it,name=it,type="银行卡",opening=100000,start=today().minusMonths(2).toString()),"测试") }
    }
    @After fun close(){db.close()}
    private suspend fun plan(id:String="r",kind:String="EXPENSE") {
        repo.recurring(RecurringRule(id=id,name="每天早餐",kind=kind,amount=1500,frequency="DAILY",time="00:00",category=if(kind=="EXPENSE")"餐饮" else "兼职工资"),
            if(kind=="EXPENSE")listOf(RecurringAllocation(id,"a",1000),RecurringAllocation(id,"b",500)) else listOf(RecurringAllocation(id,"b",1500)))
    }
    @Test fun concurrentPostingAndGroupedUndoNeverDuplicate()=runBlocking {
        plan();coroutineScope { val x=async{repo.postDue(clock())};val y=async{repo.postDue(clock())};assertEquals(1,x.await()+y.await()) }
        val posted=db.walletDao().read();assertEquals(198500L,posted.cash());assertEquals(2,posted.entries.size)
        repo.voidEntry(posted.entries.first().id,"撤销错误自动记账")
        val undone=db.walletDao().read();assertEquals(200000L,undone.cash());assertTrue(undone.entries.all{it.voided});assertEquals("UNDONE",undone.recurringOccurrences.single().status)
        assertEquals(0,repo.postDue(clock()));assertEquals(2,db.walletDao().read().entries.size)
    }
    @Test fun unavailableAccountSkipsEntireSplitAndDoesNotRetry()=runBlocking {
        plan();repo.toggleAccount("b");assertEquals(1,repo.postDue(clock()))
        val s=db.walletDao().read();assertEquals(200000L,s.cash());assertTrue(s.entries.isEmpty());assertEquals("SKIPPED",s.recurringOccurrences.single().status)
        repo.toggleAccount("b");assertEquals(0,repo.postDue(clock()))
    }
    @Test fun changingFutureSplitsKeepsHistoricalSnapshots()=runBlocking {
        plan();repo.postDue(clock());val old=db.walletDao().read()
        repo.recurring(old.recurringRules.single().copy(amount=2200),listOf(RecurringAllocation("r","b",2200)))
        val next=db.walletDao().read();assertEquals(old.entries,next.entries);assertEquals(old.cash(),next.cash())
        assertEquals(listOf(RecurringAllocation("r","b",2200)),next.recurringAllocations)
        assertEquals(1500L,next.recurringOccurrences.single().amount)
    }
    @Test fun refundBlocksGroupUndoUntilRefundIsVoided()=runBlocking {
        plan();repo.postDue(clock());val e=db.walletDao().read().entries.first()
        val refund=Entry(kind="REFUND",amount=100,accountId="a",date=today().toString(),originalId=e.id)
        repo.entry(refund);val before=db.walletDao().read()
        try{repo.undoRecurring(before.recurringOccurrences.single().id);fail("refunded group undone")}catch(_:IllegalArgumentException){}
        assertEquals(before,db.walletDao().read())
        repo.voidEntry(refund.id,"测试退款纠错");repo.undoRecurring(before.recurringOccurrences.single().id)
        assertEquals(200000L,db.walletDao().read().cash())
    }
    @Test fun backupFourRoundTripRetainsPlansUndoAndReminder()=runBlocking {
        plan();plan("income","INCOME");repo.postDue(clock());repo.undoRecurring("r@${today()}")
        repo.reminder(ReminderSettings(enabled=true,startTime="22:00",endTime="01:00",categories="餐饮,通讯"))
        val s=db.walletDao().read();val restored=Backup.decode(Backup.encode(s))
        assertEquals(s.cash(),restored.cash());assertEquals(s.entries.toSet(),restored.entries.toSet())
        assertEquals(s.recurringRules.toSet(),restored.recurringRules.toSet());assertEquals(s.recurringAllocations.toSet(),restored.recurringAllocations.toSet())
        assertEquals(s.recurringOccurrences.toSet(),restored.recurringOccurrences.toSet());assertEquals(s.reminder,restored.reminder)
        assertTrue(pendingRecurring(restored,clock()).isEmpty())
    }
    @Test fun legacyIncomeConversionDoesNotPostHistoricalCash()=runBlocking {
        repo.incomePlan(IncomePlan("a",50000,1,"生活费",currentMonth()))
        repo.receiveIncome("a",currentMonth(),50000,today().toString());val cash=db.walletDao().read().cash()
        repo.prepare(false);repo.prepare(false)
        val s=db.walletDao().read();assertEquals(cash,s.cash());assertEquals(1,s.recurringRules.size)
        assertEquals(today().plusDays(1).toString(),s.recurringRules.single().startDate);assertFalse(s.incomePlans.single().enabled)
        assertEquals(0,repo.postDue(clock()));assertEquals(cash,db.walletDao().read().cash())
    }
    @Test fun invalidSplitRollsBackAllTablesAndAudit()=runBlocking {
        plan();val old=db.walletDao().read()
        try { repo.recurring(old.recurringRules.single(),listOf(RecurringAllocation("r","a",1400)));fail("invalid split saved") }catch(_:IllegalArgumentException){}
        assertEquals(old,db.walletDao().read())
    }
    @Test fun retiredOtherCategoryRemainsReadableInRecordsPlansRemindersAndBackups()=runBlocking {
        val date=today().toString()
        repo.entry(Entry("legacy-other","EXPENSE",1000,"a",date,category="其他"))
        repo.entry(Entry("legacy-refund","REFUND",200,"a",date,originalId="legacy-other"))
        repo.entry(Entry("clothes","EXPENSE",300,"a",date,category="衣服"))
        repo.entry(Entry("beauty","EXPENSE",500,"a",date,category="美护"))
        repo.reminder(ReminderSettings(enabled=true,categories="其他"))
        val oldRule=RecurringRule(id="legacy-rule",name="旧版计划",kind="EXPENSE",amount=100,startDate=today().plusDays(1).toString(),category="其他")
        repo.recurring(oldRule,listOf(RecurringAllocation(oldRule.id,"a",100)))
        val before=db.walletDao().read();val restored=Backup.decode(Backup.encode(before))
        assertEquals(198400L,before.cash());assertEquals(before.cash(),restored.cash())
        assertEquals(before.entries.toSet(),restored.entries.toSet());assertEquals("其他",restored.recurringRules.single().category)
        assertEquals(setOf("其他"),restored.reminder.selectedCategories())
        repo.profile("分类升级兼容性检查");assertEquals(before.cash(),db.walletDao().read().cash())
    }
    @Test fun resumingDoesNotBackfillPausedDays()=runBlocking {
        val r=RecurringRule(id="paused",name="暂停早餐",kind="EXPENSE",amount=1000,frequency="DAILY",time="00:00",startDate=today().minusDays(3).toString(),enabled=false)
        val allocations=listOf(RecurringAllocation(r.id,"a",1000))
        repo.recurring(r,allocations);assertEquals(0,repo.postDue(clock()))
        repo.recurring(r.copy(enabled=true),allocations)
        assertEquals(today().toString(),db.walletDao().read().recurringRules.single().startDate)
        assertEquals(1,repo.postDue(clock()));assertEquals(today().toString(),db.walletDao().read().recurringOccurrences.single().date)
    }
}
