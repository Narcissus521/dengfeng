package com.campusfinance.wallet

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    private lateinit var db:WalletDatabase
    private lateinit var repo:WalletRepository
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),WalletDatabase::class.java).build()
        repo=WalletRepository(db);repo.prepare(false)
        repo.account(Account(id="a",name="微信",type="微信",opening=100000,start=today().minusMonths(2).toString()),"测试账户")
        repo.account(Account(id="b",name="银行卡",type="银行卡",opening=0,start=today().minusMonths(2).toString()),"测试账户")
    }
    @After fun close(){db.close()}
    private fun expense(id:String="e")=Entry(id=id,kind="EXPENSE",amount=30000,accountId="a",date=today().toString(),category="购物",bucket="FREE")
    @Test fun transactionAndAuditCommitTogether()=runBlocking {
        repo.entry(expense());val s=db.walletDao().read();assertEquals(70000L,s.cash());assertEquals(1,s.audits.count{it.entityId=="e"})
    }
    @Test fun invalidRefundRollsBackWithoutAudit()=runBlocking {
        repo.entry(expense());val before=db.walletDao().read()
        try{repo.entry(Entry(kind="REFUND",amount=30001,accountId="b",date=today().toString(),originalId="e"));fail("invalid refund accepted")}catch(_:IllegalArgumentException){}
        val after=db.walletDao().read();assertEquals(before,after)
    }
    @Test fun transferFeeAndBothSidesPersistAtomically()=runBlocking {
        repo.entry(Entry(id="t",kind="TRANSFER",amount=9800,accountId="a",toAccountId="b",date=today().toString()),fee=200)
        val s=db.walletDao().read();assertEquals(90000L,s.balance("a"));assertEquals(9800L,s.balance("b"));assertEquals(99800L,s.cash());assertEquals(2,s.entries.size);assertTrue(s.audits.any{it.entityId==s.entries.first{it.kind=="EXPENSE"}.id})
    }
    @Test fun invalidFeeRollsBackTransfer()=runBlocking {
        val before=db.walletDao().read()
        try{repo.entry(Entry(id="t",kind="TRANSFER",amount=9800,accountId="a",toAccountId="b",date=today().toString()),fee=MAX_MONEY+1);fail("invalid fee accepted")}catch(_:IllegalArgumentException){}
        assertEquals(before,db.walletDao().read())
    }
    @Test fun historicalEditPreservesIdAndAudit()=runBlocking {
        val e=expense();repo.entry(e);repo.entry(e.copy(amount=15000,bucket="NECESSARY"),"纠正金额归属")
        val s=db.walletDao().read();assertEquals(1,s.entries.size);assertEquals(85000L,s.cash());assertEquals(2,s.audits.count{it.entityId=="e"});assertTrue(s.audits.single{it.reason=="纠正金额归属"}.before.contains("30000"))
    }
    @Test fun voidIsLogicalAndRetainsHistory()=runBlocking {
        repo.entry(expense());repo.voidEntry("e","测试纠错");val s=db.walletDao().read();assertEquals(1,s.entries.size);assertTrue(s.entries.first().voided);assertEquals(100000L,s.cash());assertEquals(2,s.audits.count{it.entityId=="e"})
    }
    @Test fun cannotMoveBaselineForwardWithHistory()=runBlocking {
        repo.entry(expense());val a=db.walletDao().read().accounts.first{it.id=="a"}
        try{repo.account(a.copy(start=today().toString()),"错误移动");fail("baseline moved")}catch(_:IllegalArgumentException){}
        assertEquals(a,db.walletDao().read().accounts.first{it.id=="a"})
    }
    @Test fun calibrationIsFixedAndNotIncome()=runBlocking {
        repo.calibrate("a",90000,"实账核对");repo.entry(expense().copy(amount=10000,date=today().minusDays(1).toString()))
        val s=db.walletDao().read();assertEquals(80000L,s.cash());assertEquals(0L,s.income(currentMonth()));assertEquals(-10000L,s.entries.first{it.kind=="ADJUSTMENT"}.amount)
    }
    @Test fun backupRoundTripPreservesCashAndRelations()=runBlocking {
        repo.entry(expense());repo.entry(Entry(kind="REFUND",amount=12000,accountId="b",date=today().toString(),originalId="e"))
        val s=db.walletDao().read();val decoded=Backup.decode(Backup.encode(s));assertEquals(s.cash(),decoded.cash());assertEquals(s.entries.toSet(),decoded.entries.toSet());assertEquals(s.audits.toSet(),decoded.audits.toSet())
    }
    @Test fun fractionalCentsInBackupRejected()=runBlocking {
        val j=JSONObject(Backup.encode(db.walletDao().read()));j.getJSONArray("accounts").getJSONObject(0).put("opening",1.5)
        try{Backup.decode(j.toString());fail("fractional cents accepted")}catch(_:IllegalArgumentException){}
    }
    @Test fun restoreCannotOverwriteExistingLedger()=runBlocking {
        val before=db.walletDao().read();try{repo.restore(demoLedger(),false);fail("existing data overwritten")}catch(_:IllegalArgumentException){}
        assertEquals(before,db.walletDao().read())
    }
    @Test fun fileDatabaseSurvivesCloseAndReopen()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="test-persistence-${newId()}.db"
        var fileDb=Room.databaseBuilder(context,WalletDatabase::class.java,name).build()
        try {
            WalletRepository(fileDb).prepare(true);val before=fileDb.walletDao().read();fileDb.close()
            fileDb=Room.databaseBuilder(context,WalletDatabase::class.java,name).build()
            val after=fileDb.walletDao().read();Rules.validate(after);assertEquals(before,after)
        }finally{fileDb.close();context.deleteDatabase(name)}
    }
    private fun plan()=IncomePlan("a",200000,31,"父母生活费",currentMonth())
    @Test fun monthlyPlanIsNotMoneyAndActualReceiptIsCountedOnce()=runBlocking {
        repo.incomePlan(plan());assertEquals(100000L,db.walletDao().read().cash())
        repo.receiveIncome("a",currentMonth(),180000,today().toString())
        val before=db.walletDao().read();assertEquals(280000L,before.cash());assertEquals(180000L,before.income(currentMonth()))
        try{repo.receiveIncome("a",currentMonth(),200000,today().toString());fail("duplicate accepted")}catch(_:IllegalArgumentException){}
        assertEquals(before,db.walletDao().read())
    }
    @Test fun concurrentConfirmationsCannotDuplicateIncome()=runBlocking {
        repo.incomePlan(plan())
        val outcomes=coroutineScope{listOf(async{runCatching{repo.receiveIncome("a",currentMonth(),200000,today().toString())}},async{runCatching{repo.receiveIncome("a",currentMonth(),200000,today().toString())}}).map{it.await()}}
        assertEquals(1,outcomes.count{it.isSuccess});assertEquals(300000L,db.walletDao().read().cash())
    }
    @Test fun voidThenCorrectReceiptKeepsOneActiveRecord()=runBlocking {
        repo.incomePlan(plan());repo.receiveIncome("a",currentMonth(),200000,today().toString())
        val e=db.walletDao().read().incomeReceipt("a",currentMonth())!!
        repo.voidEntry(e.id,"登记有误");repo.receiveIncome("a",currentMonth(),190000,today().toString())
        val s=db.walletDao().read();assertEquals(290000L,s.cash());assertEquals(2,s.entries.size);assertEquals(1,s.activeEntries.size)
    }
    @Test fun planChangesAndPauseDoNotRewriteReceivedMoney()=runBlocking {
        repo.incomePlan(plan());repo.receiveIncome("a",currentMonth(),180000,today().toString())
        val e=db.walletDao().read().incomeReceipt("a",currentMonth())!!
        repo.incomePlan(plan().copy(amount=50000,source="兼职工资",startMonth=java.time.YearMonth.now().plusMonths(1).toString(),enabled=false))
        assertEquals(e,db.walletDao().read().incomeReceipt("a",currentMonth()));assertEquals(280000L,db.walletDao().read().cash())
    }
    @Test fun futureMonthAndPausedPlanCannotCreateIncome()=runBlocking {
        repo.incomePlan(plan());val before=db.walletDao().read()
        try{repo.receiveIncome("a",java.time.YearMonth.parse(currentMonth()).plusMonths(1).toString(),200000,today().toString());fail("future receipt accepted")}catch(_:IllegalArgumentException){}
        assertEquals(before,db.walletDao().read())
        repo.incomePlan(plan().copy(enabled=false))
        try{repo.receiveIncome("a",currentMonth(),200000,today().toString());fail("paused receipt accepted")}catch(_:IllegalArgumentException){}
        assertEquals(100000L,db.walletDao().read().cash())
    }
    @Test fun savingsAndMonthlyPlanBackupRoundTrip()=runBlocking {
        repo.goal(Goal(name="旅行",target=100000,date=today().plusMonths(3).toString(),saved=12345,intention=999))
        repo.incomePlan(plan());repo.receiveIncome("a",currentMonth(),190000,today().toString())
        val s=db.walletDao().read();val decoded=Backup.decode(Backup.encode(s))
        assertEquals(s.goals,decoded.goals);assertEquals(s.incomePlans,decoded.incomePlans);assertEquals(s.entries,decoded.entries);assertEquals(s.cash(),decoded.cash())
    }
    @Test fun oldBackupDoesNotTurnPlannedReserveIntoSavedMoney()=runBlocking {
        val legacy=JSONObject(Backup.encode(demoLedger())).put("version",1)
        legacy.remove("incomePlans")
        val goals=legacy.getJSONArray("goals");goals.getJSONObject(0).put("intention",80000)
        for(i in 0 until goals.length())goals.getJSONObject(i).remove("saved")
        val entries=legacy.getJSONArray("entries")
        for(i in 0 until entries.length()){entries.getJSONObject(i).remove("incomePlanId");entries.getJSONObject(i).remove("incomePlanMonth")}
        val decoded=Backup.decode(legacy.toString());assertEquals(250000L,decoded.cash());assertEquals(80000L,decoded.goals.first().intention);assertEquals(0L,decoded.goals.first().saved);assertTrue(decoded.incomePlans.isEmpty())
    }
}
