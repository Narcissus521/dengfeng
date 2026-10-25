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
import java.time.YearMonth

class AnalysisRepositoryTest {
    private lateinit var db:WalletDatabase
    private lateinit var repo:WalletRepository
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WalletDatabase::class.java).build();repo=WalletRepository(db);repo.prepare(false)
        repo.account(Account("a","现金","现金",1000000,"2025-01-01"),"测试账户")
    }
    @After fun close(){db.close()}
    private fun expense()=Entry("e","EXPENSE",10000,"a",today().minusMonths(1).toString(),"餐饮食品",categoryGroupId="food",subcategoryId="food.canteen",necessity="UNDECIDED")
    @Test fun oldDonationAndRefundPromoteToCharityWhileBackupAndOtherBudgetRemainIntact()=runBlocking {
        repo.entry(expense().copy(category="其他支出",categoryGroupId="other",subcategoryId="other.charity"))
        repo.entry(Entry("r","REFUND",3000,"a",today().toString(),originalId="e"))
        repo.budget(Budget(currentMonth(),20000,0,0));repo.categoryBudgets(currentMonth(),listOf(CategoryBudget(currentMonth(),"other",10000),CategoryBudget(currentMonth(),"charity",5000)))
        val s=db.walletDao().read();val restored=Backup.decode(Backup.encode(s))
        assertEquals(s.entries.toSet(),restored.entries.toSet());assertEquals(s.categoryBudgets,restored.categoryBudgets);assertEquals(s.cash(),restored.cash())
        val report=ExpenseIndex(restored).report(ReportRange.month(currentMonth()))
        assertEquals(-3000L,report.groups.getValue("charity").net);assertEquals(0L,report.groups.getValue("other").net)
    }
    @Test fun metadataEditsRefundsVoidsAndBudgetsUpdateTogetherWithoutAffectingMoney()=runBlocking {
        val e=expense();repo.entry(e);repo.entry(Entry("r","REFUND",3000,"a",today().toString(),originalId="e"))
        repo.budget(Budget(currentMonth(),20000,10000,5000));repo.categoryBudgets(currentMonth(),listOf(CategoryBudget(currentMonth(),"food",8000)))
        val before=db.walletDao().read();repo.entry(e.copy(category="服饰鞋包",categoryGroupId="clothing",subcategoryId="clothing.clothes",necessity="NONESSENTIAL"),"重新归类")
        val after=db.walletDao().read();assertEquals(before.cash(),after.cash());assertEquals(before.usage(currentMonth()),after.usage(currentMonth()))
        val index=ExpenseIndex(after);val report=index.report(ReportRange.month(currentMonth()))
        assertEquals(-3000L,report.groups.getValue("clothing").net);assertEquals(-3000L,report.necessities.getValue("NONESSENTIAL").net)
        assertEquals(0L,index.budgetComparison(report.range,"food").actual);assertTrue(after.audits.any{it.reason=="重新归类"})
        val trend=index.trend(YearMonth.from(today()),6,"clothing");assertEquals(10000L,trend[trend.lastIndex-1].amount)
        repo.voidEntry("r","撤销退款");repo.voidEntry("e","撤销消费");assertEquals(0L,ExpenseIndex(db.walletDao().read()).report(report.range).total.net)
    }
    @Test fun categoryBudgetReplacementDeletionAndInvalidSaveAreAtomic()=runBlocking {
        repo.budget(Budget(currentMonth(),20000,10000,5000));repo.categoryBudgets(currentMonth(),listOf(CategoryBudget(currentMonth(),"food",10000),CategoryBudget(currentMonth(),"health",10000)))
        val before=db.walletDao().read()
        try{repo.categoryBudgets(currentMonth(),listOf(CategoryBudget(currentMonth(),"food",20001)));fail("overallocated")}catch(_:IllegalArgumentException){}
        assertEquals(before,db.walletDao().read())
        repo.categoryBudgets(currentMonth(),listOf(CategoryBudget(currentMonth(),"health",0)));val after=db.walletDao().read()
        assertEquals(listOf(CategoryBudget(currentMonth(),"health",0)),after.categoryBudgets);assertEquals(before.cash(),after.cash())
        repo.categoryBudgets(currentMonth(),emptyList());assertTrue(db.walletDao().read().categoryBudgets.isEmpty())
    }
    @Test fun versionFiveBackupRoundTripPreservesTwoLevelsNecessityAndCaps()=runBlocking {
        repo.entry(expense().copy(necessity="NECESSARY"));repo.budget(Budget(currentMonth(),20000,0,0));repo.categoryBudgets(currentMonth(),listOf(CategoryBudget(currentMonth(),"food",15000)))
        val s=db.walletDao().read();val json=Backup.encode(s);assertEquals(5,JSONObject(json).getInt("version"))
        val copy=Backup.decode(json);assertEquals(s.entries,copy.entries);assertEquals(s.categoryBudgets,copy.categoryBudgets);assertEquals(s.cash(),copy.cash())
        val fresh=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WalletDatabase::class.java).build()
        try{WalletRepository(fresh).prepare(false);WalletRepository(fresh).restore(copy,false);assertEquals(s.categoryBudgets,fresh.walletDao().read().categoryBudgets)}finally{fresh.close()}
    }
    @Test fun oldBackupVersionsDefaultNecessityAndInvalidExplicitMetadataIsRejected()=runBlocking {
        repo.entry(expense());val text=Backup.encode(db.walletDao().read())
        for(version in 1..4){val j=JSONObject(text).put("version",version);val copy=Backup.decode(j.toString());assertEquals("UNDECIDED",copy.entries.single().necessity);assertEquals("",copy.entries.single().subcategoryId);assertTrue(copy.categoryBudgets.isEmpty())}
        for(field in listOf("subcategoryId","necessity")) {
            val j=JSONObject(text);j.getJSONArray("entries").getJSONObject(0).put(field,"invalid")
            try{Backup.decode(j.toString());fail("invalid metadata accepted")}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun feeRemainsSeparateExpenseAndPrincipalRemainsOutsideConsumption()=runBlocking {
        repo.account(Account("b","微信","微信",0,"2025-01-01"),"测试账户")
        repo.entry(Entry("t","TRANSFER",10000,"a",today().toString(),toAccountId="b"),fee=200)
        val s=db.walletDao().read();val p=ExpenseIndex(s).report(ReportRange.month(currentMonth()))
        assertEquals(200L,p.total.net);assertEquals(200L,p.groups.getValue("finance").net);assertEquals(1,p.total.count);assertEquals(999800L,s.cash())
    }
    @Test fun automaticSplitCopiesMetadataAndFutureRuleEditPreservesPostedSnapshot()=runBlocking {
        repo.account(Account("b","微信","微信",0,"2025-01-01"),"测试账户")
        val rule=RecurringRule(id="r",name="固定餐饮",kind="EXPENSE",amount=1000,frequency="DAILY",time="00:00",startDate=today().toString(),category="餐饮食品",categoryGroupId="food",subcategoryId="food.takeaway",necessity="NONESSENTIAL")
        val splits=listOf(RecurringAllocation("r","a",700),RecurringAllocation("r","b",300))
        repo.recurring(rule,splits);assertEquals(1,repo.postDue())
        val posted=db.walletDao().read();assertEquals(2,posted.entries.size);assertTrue(posted.entries.all{it.subcategoryId=="food.takeaway"&&it.necessity=="NONESSENTIAL"})
        assertEquals(1,ExpenseIndex(posted).report(ReportRange.month(currentMonth())).total.count)
        repo.recurring(rule.copy(category="医疗健康",categoryGroupId="health",subcategoryId="health.medicine",necessity="NECESSARY"),splits)
        val edited=db.walletDao().read();assertEquals(posted.entries.toSet(),edited.entries.toSet());assertEquals(posted.cash(),edited.cash())
        val restored=Backup.decode(Backup.encode(edited));assertEquals(edited.recurringRules,restored.recurringRules);assertEquals(edited.entries.toSet(),restored.entries.toSet())
    }
}
