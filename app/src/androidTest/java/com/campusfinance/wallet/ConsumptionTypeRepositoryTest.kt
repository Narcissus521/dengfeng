package com.campusfinance.wallet

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.ZonedDateTime

class ConsumptionTypeRepositoryTest {
    private lateinit var db:WalletDatabase
    private lateinit var repo:WalletRepository
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WalletDatabase::class.java).build()
        repo=WalletRepository(db);repo.prepare(false)
        listOf("a","b").forEach{repo.account(Account(it,it,"现金",100000,today().minusYears(1).toString()),"测试账户")}
    }
    @After fun close(){db.close()}
    @Test fun reopeningAndOldBackupPreserveConflictsUntilAuditedConfirmation()=runBlocking {
        val e=Entry("legacy","EXPENSE",1000,"a",today().minusMonths(1).toString(),"餐饮",bucket="FREE",necessity="NECESSARY")
        repo.entry(e);repo.entry(Entry("refund","REFUND",200,"a",today().toString(),originalId=e.id))
        val before=db.walletDao().read();repo.prepare(false);assertEquals(before,db.walletDao().read())
        val old=JSONObject(Backup.encode(before)).put("version",5);val restored=Backup.decode(old.toString())
        assertEquals(before.entries.toSet(),restored.entries.toSet());assertEquals(before.usage(currentMonth(),"FREE"),restored.usage(currentMonth(),"FREE"))
        repo.entry(e.withConsumptionType("NECESSARY"),"确认合并消费性质")
        val after=db.walletDao().read();assertEquals(before.cash(),after.cash());assertEquals(-200L,after.usage(currentMonth(),"NECESSARY"))
        assertEquals(0L,after.usage(currentMonth(),"FREE"));assertTrue(after.pendingConsumption(currentMonth()).isEmpty())
        assertTrue(after.audits.any{it.reason=="确认合并消费性质"&&it.before.contains("bucket=FREE")&&it.after.contains("bucket=NECESSARY")})
        val json=Backup.encode(after);assertEquals(8,JSONObject(json).getInt("version"));assertEquals(after.entries.toSet(),Backup.decode(json).entries.toSet())
    }
    @Test fun confirmedSplitAndRefundShareOneTypeAndPlanChangesDoNotRewritePastEntries()=runBlocking {
        val plan=RecurringRule(id="plan",name="午餐",kind="EXPENSE",amount=1000,frequency="DAILY",time="00:00",startDate=today().toString()).withConsumptionType("NONESSENTIAL")
        val allocations=listOf(RecurringAllocation(plan.id,"a",600),RecurringAllocation(plan.id,"b",400))
        repo.recurring(plan,allocations);assertEquals(0,repo.postDue(ZonedDateTime.now(financeZone).withHour(23).withMinute(59)))
        assertTrue(db.walletDao().read().entries.isEmpty())
        repo.confirmRecurring(plan.id,today().toString(),today().toString(),plan.amount,allocations.map{it.accountId to it.amount})
        val posted=db.walletDao().read();assertTrue(posted.entries.all{it.bucket=="FREE"&&it.necessity=="NONESSENTIAL"})
        repo.entry(Entry("refund","REFUND",100,"a",today().toString(),originalId=posted.entries.first().id))
        val refunded=db.walletDao().read();assertEquals(900L,refunded.usage(currentMonth(),"FREE"))
        assertEquals(900L,ExpenseIndex(refunded).report(ReportRange.month(currentMonth())).necessities.getValue("NONESSENTIAL").net)
        repo.recurring(plan.withConsumptionType("UNDECIDED"),allocations)
        val after=db.walletDao().read();assertEquals(refunded.entries.toSet(),after.entries.toSet())
        val copy=Backup.decode(Backup.encode(after));assertEquals("UNDECIDED",copy.recurringRules.single().bucket);assertEquals(after.cash(),copy.cash())
    }
    @Test fun transferFeeUsesSelectedTypeAndUnjudgedRoundTripsWithoutConsumingASubBudget()=runBlocking {
        repo.entry(Entry("transfer","TRANSFER",1000,"a",today().toString(),bucket="NECESSARY",toAccountId="b"),fee=100)
        val fee=db.walletDao().read().entries.single{it.transferId=="transfer"}
        assertEquals("NECESSARY",fee.necessity);assertEquals("NECESSARY",fee.bucket)
        repo.entry(Entry("unjudged","EXPENSE",200,"a",today().toString(),category="餐饮"))
        val s=db.walletDao().read();val copy=Backup.decode(Backup.encode(s))
        assertEquals(s.entries.toSet(),copy.entries.toSet());assertEquals(300L,copy.usage(currentMonth()))
        assertEquals(200L,copy.usage(currentMonth(),"UNDECIDED"));assertEquals(100L,copy.usage(currentMonth(),"NECESSARY"));assertEquals(0L,copy.usage(currentMonth(),"FREE"))
        assertEquals(199700L,copy.cash())
    }
}
