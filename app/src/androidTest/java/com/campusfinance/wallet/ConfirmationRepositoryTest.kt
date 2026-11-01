package com.campusfinance.wallet

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalTime

class ConfirmationRepositoryTest {
    private lateinit var db:WalletDatabase
    private lateinit var repo:WalletRepository
    private val now=today().atTime(LocalTime.NOON).atZone(financeZone)
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WalletDatabase::class.java).build()
        repo=WalletRepository(db);repo.prepare(false)
        listOf("a","b").forEach{repo.account(Account(it,it,"现金",1000000,today().minusMonths(2).toString()),"")}
    }
    @After fun close(){db.close()}
    private suspend fun plan():RecurringRule {
        val r=RecurringRule("r","固定早餐","EXPENSE",50000,"DAILY",time="08:00",startDate=today().minusDays(2).toString(),category="餐饮食品",bucket="NECESSARY",categoryGroupId="food",subcategoryId="food.canteen",necessity="NECESSARY")
        repo.recurring(r,listOf(RecurringAllocation(r.id,"a",30000),RecurringAllocation(r.id,"b",20000)));return r
    }
    @Test fun noAutomaticPostingAndEditedOccurrenceLeavesRuleAndOtherDatesUntouched()=runBlocking {
        val rule=plan();val before=db.walletDao().read()
        assertEquals(0,repo.postDue(now));assertEquals(before,db.walletDao().read())
        val due=today().minusDays(2).toString()
        repo.confirmRecurring(rule.id,due,today().toString(),40000,listOf("a" to 24000L,"b" to 16000L),now)
        val after=db.walletDao().read();Rules.validate(after)
        assertEquals(before.cash()-40000,after.cash());assertEquals(rule,after.recurringRules.single())
        assertEquals(before.recurringAllocations,after.recurringAllocations);assertEquals(40000L,after.recurringOccurrences.single().amount)
        assertEquals(due,after.recurringOccurrences.single().date);assertEquals(today().toString(),after.recurringOccurrences.single().postedDate)
        assertTrue(after.entries.all{it.date==today().toString()});assertEquals(2,pendingRecurring(after,now).size)
        assertTrue(runCatching{repo.confirmRecurring(rule.id,due,due,50000,listOf("a" to 50000L),now)}.isFailure)
        assertEquals(after,db.walletDao().read())
    }
    @Test fun skippedOccurrenceIsPermanentAndUnavailableAccountNeverSilentlySkipsOrPartiallyPosts()=runBlocking {
        val r=plan();repo.toggleAccount("b");val before=db.walletDao().read();repo.postDue(now)
        assertEquals(before,db.walletDao().read())
        val due=today().toString()
        assertTrue(runCatching{repo.confirmRecurring(r.id,due,due,50000,listOf("a" to 30000L,"b" to 20000L),now)}.isFailure)
        assertEquals(before,db.walletDao().read())
        repo.skipRecurring(r.id,due,now);val skipped=db.walletDao().read()
        assertEquals(before.cash(),skipped.cash());assertTrue(skipped.entries.isEmpty());assertEquals("SKIPPED",skipped.recurringOccurrences.single().status)
        assertTrue(pendingRecurring(skipped,now).none{it.second==today()})
    }
    @Test fun repaymentClassificationAndExplicitHistorySyncNeverChangeTheCashOrContract()=runBlocking {
        val d=Debt("phone","三星手机",0,today().toString(),categoryId="debt-kind-shopping",scheduleType="EQUAL")
        val rows=manualDebtSchedule(d.id,today().toString(),listOf(50000,50000))
        repo.debt(d,rows);val registered=db.walletDao().read();assertEquals(2000000L,registered.cash());assertEquals(0L,registered.usage(currentMonth()))
        repo.repay(rows.first(),listOf("a" to 30000L,"b" to 20000L),today().toString())
        val old=db.walletDao().read();assertEquals(0L,old.usage(currentMonth()))
        val tagged=d.copy(repaymentsAreConsumption=true,categoryGroupId="digital",subcategoryId="digital.devices",necessity="NECESSARY")
        repo.debt(tagged,rows);assertEquals(old.entries,db.walletDao().read().entries)
        repo.debt(tagged,rows,applyToHistory=true);val changed=db.walletDao().read()
        assertEquals(old.cash(),changed.cash());assertEquals(old.installments,changed.installments);assertEquals(old.unpaid(),changed.unpaid())
        assertEquals(50000L,changed.usage(currentMonth()));assertEquals(1,ExpenseIndex(changed).report(ReportRange.month(currentMonth())).total.count)
        repo.expenseMetadata(changed.entries.first().id,"digital","digital.devices","NONESSENTIAL","FREE")
        assertEquals(50000L,db.walletDao().read().usage(currentMonth(),"FREE"))
        repo.voidEntry(changed.entries.first().id,"撤销测试还款");val undone=db.walletDao().read()
        assertEquals(registered.cash(),undone.cash());assertEquals(0L,undone.usage(currentMonth()));assertEquals(rows.toSet(),undone.unpaid().toSet())
    }
    @Test fun jsonEightRoundTripRetainsActualDateAndRepaymentPolicy()=runBlocking {
        val r=plan();repo.confirmRecurring(r.id,today().minusDays(1).toString(),today().toString(),50000,listOf("a" to 50000L),now)
        val debt=Debt(id="json-debt",name="数码分期",principal=0,purchaseDate=today().toString(),categoryId="debt-kind-shopping",scheduleType="EQUAL",repaymentsAreConsumption=true,categoryGroupId="digital",subcategoryId="digital.devices",necessity="NONESSENTIAL")
        val rows=manualDebtSchedule(debt.id,today().toString(),listOf(1000,1000))
        repo.debt(debt,rows);repo.repay(rows.first(),"a",today().toString())
        val s=db.walletDao().read();val restored=Backup.decode(Backup.encode(s))
        assertEquals(s.entries,restored.entries);assertEquals(s.recurringOccurrences,restored.recurringOccurrences)
        assertEquals(s.debts,restored.debts);assertTrue(restored.entries.single{it.kind=="REPAYMENT"}.repaymentConsumption)
        assertEquals("NONESSENTIAL",restored.debts.single().necessity)
        assertEquals(s.cash(),restored.cash());assertEquals(s.usage(currentMonth()),restored.usage(currentMonth()))
    }
    @Test fun optionalModificationReasonsStillPreserveAuditAndCorrectBalance()=runBlocking {
        val e=Entry(kind="EXPENSE",amount=1000,accountId="a",date=today().toString(),category="其他支出")
        repo.entry(e,"");repo.entry(e.copy(amount=2000),"");val s=db.walletDao().read()
        assertEquals(1998000L,s.cash());assertEquals(2000L,s.usage(currentMonth()));assertTrue(s.audits.count{it.entityId==e.id}>=2)
        repo.calibrate("a",999000,"");assertEquals(999000L,db.walletDao().read().balance("a"))
    }
}
