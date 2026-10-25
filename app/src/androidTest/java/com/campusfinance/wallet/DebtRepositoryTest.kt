package com.campusfinance.wallet

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtRepositoryTest {
    private lateinit var db:WalletDatabase
    private lateinit var repo:WalletRepository
    private val start=today().minusMonths(1).withDayOfMonth(1).toString()
    private val debt=Debt(id="d",name="电脑",principal=0,purchaseDate=start,categoryId="debt-kind-shopping",scheduleType="CUSTOM")
    @Before fun setup()=runBlocking{
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),WalletDatabase::class.java).build()
        repo=WalletRepository(db);repo.prepare(false)
        repo.account(Account("a","微信","微信",100000,start),"测试账户");repo.account(Account("b","银行卡","银行卡",50000,start),"测试账户")
    }
    @After fun close(){db.close()}
    private fun rows()=manualDebtSchedule("d",today().toString(),listOf(50001L,60002L,70003L))
    @Test fun thirtyYearMortgageBacksUpAndRestoresAllPeriodsAndPayments()=runBlocking {
        val category=DebtCategory(id="mortgage",name="房贷");repo.debtCategory(category)
        val d=debt.copy(name="30年房贷",categoryId=category.id,scheduleType="EQUAL")
        val plan=manualDebtSchedule(d.id,today().toString(),List(360){10000L})
        repo.debt(d,plan);assertEquals(150000L,db.walletDao().read().cash())
        repo.repay(plan.first(),listOf("a" to 6000L,"b" to 4000L),today().toString())
        val source=Backup.decode(Backup.encode(db.walletDao().read()))
        assertEquals(360,source.installments.size);assertEquals(360,source.installments.maxOf{it.number})
        assertEquals(140000L,source.cash());assertEquals(0L,source.usage(currentMonth()))
        val target=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),WalletDatabase::class.java).build()
        try {
            val restoredRepo=WalletRepository(target);restoredRepo.prepare(false);restoredRepo.restore(source,false)
            val restored=target.walletDao().read();assertEquals(source.installments.toSet(),restored.installments.toSet())
            assertEquals(source.entries.toSet(),restored.entries.toSet());assertEquals(source.cash(),restored.cash())
            assertEquals(359,restored.unpaid().size);assertEquals("房贷",restored.debtCategoryName(restored.debts.single()))
        }finally{target.close()}
    }
    @Test fun backupRejectsAThreeHundredSixtyFirstPeriod()=runBlocking {
        repo.debt(debt,manualDebtSchedule(debt.id,today().toString(),List(360){100L}))
        val j=JSONObject(Backup.encode(db.walletDao().read()))
        j.getJSONArray("installments").getJSONObject(359).put("number",361)
        try{Backup.decode(j.toString());fail("361st installment accepted")}catch(_:IllegalArgumentException){}
    }
    @Test fun equalAndCustomSchedulesPersistWithoutChangingCash()=runBlocking{
        val r=rows();repo.debt(debt,r)
        val s=db.walletDao().read();assertEquals(150000L,s.cash());assertEquals(180006L,s.installments.map{it.due}.exactSum());assertEquals("购物分期",s.debtCategoryName(s.debts.single()))
    }
    @Test fun shorteningUnpaidPlanRemovesOnlyUnusedRowsAndAuditsOldPlan()=runBlocking{
        val old=rows();repo.debt(debt,old)
        val next=manualDebtSchedule("d",old.first().date,listOf(45000L,65000L),old);repo.debt(debt,next)
        val s=db.walletDao().read();assertEquals(2,s.installments.size);assertEquals(next.toSet(),s.installments.toSet());assertEquals(150000L,s.cash());assertTrue(s.audits.any{it.entityId=="d"&&it.before.contains("70003")})
    }
    @Test fun combinationIsAtomicAndCanBeVoidedAsOne()=runBlocking{
        val r=rows();repo.debt(debt,r);repo.repay(r.first(),listOf("a" to 30000L,"b" to 20001L),today().toString())
        val paid=db.walletDao().read();assertEquals(70000L,paid.balance("a"));assertEquals(29999L,paid.balance("b"));assertEquals(99999L,paid.cash());assertEquals(0L,paid.usage(currentMonth()));assertEquals(2,paid.payments(r.first()).size)
        repo.voidEntry(paid.payments(r.first()).last().id,"整笔纠错")
        val undone=db.walletDao().read();assertEquals(150000L,undone.cash());assertTrue(undone.payments(r.first()).isEmpty());assertTrue(undone.entries.all{it.voided});assertEquals(3,undone.unpaid().size)
    }
    @Test fun badSumRollsBackAllBalancesAndAudits()=runBlocking{
        val r=rows();repo.debt(debt,r);val before=db.walletDao().read()
        try{repo.repay(r.first(),listOf("a" to 30000L,"b" to 20000L),today().toString());fail("invalid sum accepted")}catch(_:IllegalArgumentException){}
        assertEquals(before,db.walletDao().read())
    }
    @Test fun disabledOrRepeatedAccountCannotPartiallyDeduct()=runBlocking{
        val r=rows();repo.debt(debt,r);repo.toggleAccount("b");val before=db.walletDao().read()
        try{repo.repay(r.first(),listOf("a" to 30000L,"b" to 20001L),today().toString());fail("disabled accepted")}catch(_:IllegalArgumentException){}
        assertEquals(before,db.walletDao().read())
        try{repo.repay(r.first(),listOf("a" to 30000L,"a" to 20001L),today().toString());fail("duplicate account accepted")}catch(_:IllegalArgumentException){}
        assertEquals(before,db.walletDao().read())
    }
    @Test fun concurrentConfirmationsProduceOneCombination()=runBlocking{
        val r=rows();repo.debt(debt,r)
        val results=coroutineScope{List(2){async{runCatching{repo.repay(r.first(),listOf("a" to 30000L,"b" to 20001L),today().toString())}}}.map{it.await()}}
        assertEquals(1,results.count{it.isSuccess});assertEquals(2,db.walletDao().entries().size);assertEquals(99999L,db.walletDao().read().cash())
    }
    @Test fun previouslyPaidPeriodsAreImmutableEvenAfterReversal()=runBlocking{
        val r=rows();repo.debt(debt,r);repo.repay(r.first(),"a",today().toString())
        val paid=db.walletDao().read();repo.voidEntry(paid.entries.single().id,"撤销错误付款")
        val before=db.walletDao().read()
        try{repo.debt(debt,manualDebtSchedule("d",r.first().date,listOf(49999L,60002L,70003L),r));fail("history rewritten")}catch(_:IllegalArgumentException){}
        assertEquals(before,db.walletDao().read())
        repo.debt(debt,manualDebtSchedule("d",r.first().date,listOf(50001L,40000L),r));assertEquals(2,db.walletDao().installments().size)
    }
    @Test fun renameAndPauseCategoryPreservesExistingDebt()=runBlocking{
        val c=DebtCategory(id="c",name="室友借款");repo.debtCategory(c);repo.debt(debt.copy(categoryId=c.id),rows())
        repo.debtCategory(c.copy(name="同学借款",archived=true))
        val s=db.walletDao().read();assertEquals("同学借款",s.debtCategoryName(s.debts.single()))
        val newDebt=debt.copy(id="new",categoryId="c")
        try{repo.debt(newDebt,manualDebtSchedule("new",today().toString(),listOf(100L)));fail("inactive category accepted")}catch(_:IllegalArgumentException){}
        assertEquals(s,db.walletDao().read())
    }
    @Test fun backupKeepsCategoryPlanAndRepaidAndReversedBatches()=runBlocking{
        val r=rows();repo.debt(debt,r);repo.repay(r.first(),listOf("a" to 30000L,"b" to 20001L),today().toString())
        repo.voidEntry(db.walletDao().entries().first().id,"纠错重记");repo.repay(r.first(),"a",today().toString())
        val s=db.walletDao().read();val restored=Backup.decode(Backup.encode(s))
        assertEquals(s.cash(),restored.cash());assertEquals(s.entries.toSet(),restored.entries.toSet());assertEquals(s.installments.toSet(),restored.installments.toSet());assertEquals(s.debtCategories.toSet(),restored.debtCategories.toSet());assertEquals(2,restored.entries.mapNotNull{it.repaymentId}.distinct().size)
    }
    @Test fun versionTwoBackupKeepsSavingsAndMonthlyIncome()=runBlocking{
        repo.goal(Goal(name="旅行",target=100000,date=today().plusMonths(3).toString(),saved=12345))
        repo.incomePlan(IncomePlan("a",10000,1,"生活费",currentMonth()));repo.receiveIncome("a",currentMonth(),10000,today().toString())
        val j=JSONObject(Backup.encode(db.walletDao().read())).put("version",2);j.remove("debtCategories")
        val restored=Backup.decode(j.toString());assertEquals(12345L,restored.goals.single().saved);assertEquals(1,restored.incomePlans.size);assertNotNull(restored.incomeReceipt("a",currentMonth()));assertEquals(160000L,restored.cash())
    }
    @Test fun restoringOverCustomCategoriesIsRejected()=runBlocking{
        val target=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),WalletDatabase::class.java).build()
        try{
            val repository=WalletRepository(target);repository.prepare(false);repository.debtCategory(DebtCategory(name="自定义类别"));val before=target.walletDao().read()
            try{repository.restore(db.walletDao().read(),false);fail("custom category overwritten")}catch(_:IllegalArgumentException){}
            assertEquals(before,target.walletDao().read())
        }finally{target.close()}
    }
    @Test fun restoreReplacesOnlyDefaultSeedCategoriesAndKeepsAllNewRelations()=runBlocking{
        val r=rows();repo.debt(debt,r);repo.debtCategory(defaultDebtCategories().first().copy(name="我的分期"));repo.repay(r.first(),listOf("a" to 30000L,"b" to 20001L),today().toString())
        val source=Backup.decode(Backup.encode(db.walletDao().read()))
        val target=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),WalletDatabase::class.java).build()
        try{val repository=WalletRepository(target);repository.prepare(false);repository.restore(source,false);val s=target.walletDao().read();Rules.validate(s);assertEquals(source.cash(),s.cash());assertEquals(source.debtCategories.toSet(),s.debtCategories.toSet());assertEquals(2,s.payments(s.installments.first{it.number==1}).size)}finally{target.close()}
    }
}
