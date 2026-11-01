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

class DebtArchiveRepositoryTest {
    private lateinit var db:WalletDatabase
    private lateinit var repo:WalletRepository
    private val d=Debt(id="archive-debt",name="贷款",principal=0,purchaseDate=today().toString(),categoryId="debt-kind-loan",scheduleType="CUSTOM")
    private val rows=manualDebtSchedule(d.id,today().toString(),listOf(2000,3000))
    @Before fun setup()=runBlocking{
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WalletDatabase::class.java).build()
        repo=WalletRepository(db);repo.prepare(false)
        repo.account(Account("a","现金","现金",100000,today().minusMonths(1).toString()),"测试")
        repo.account(Account("b","银行卡","银行卡",50000,today().minusMonths(1).toString()),"测试")
        repo.debt(d,rows);repo.repay(rows.first(),listOf("a" to 1500L,"b" to 500L),today().toString())
        repo.entry(Entry("e","EXPENSE",2000,"a",today().toString(),category="餐饮食品",bucket="NECESSARY",categoryGroupId="food",subcategoryId="food.canteen",necessity="NECESSARY"))
        repo.entry(Entry("r","REFUND",500,"a",today().toString(),originalId="e"))
    }
    @After fun cleanup(){db.close()}
    @Test fun removingAndRestoringPaidPlanPreservesEveryPaymentAndAmount()=runBlocking{
        val before=db.walletDao().read();repo.archiveDebt(d.id,true);val removed=db.walletDao().read()
        assertTrue(removed.debts.single().archived);assertTrue(removed.unpaid().isEmpty())
        assertEquals(before.entries,removed.entries);assertEquals(before.installments,removed.installments)
        assertEquals(before.cash(),removed.cash());assertEquals(before.usage(currentMonth()),removed.usage(currentMonth()))
        assertEquals(before.payments(rows.first()),removed.payments(rows.first()))
        repo.archiveDebt(d.id,false);val restored=db.walletDao().read()
        assertEquals(listOf(rows.last()),restored.unpaid());assertEquals(before.entries,restored.entries);assertEquals(before.cash(),restored.cash())
        assertEquals(2,restored.audits.count{it.action in listOf("移除负债计划","恢复负债计划")})
    }
    @Test fun removedPlanRejectsNewPaymentButKeepsOldPaymentReversible()=runBlocking{
        repo.archiveDebt(d.id,true);val before=db.walletDao().read()
        try{repo.repay(rows.last(),"a",today().toString());fail("removed plan accepted a new payment")}catch(_:IllegalArgumentException){}
        assertEquals(before.entries,db.walletDao().read().entries)
        val payment=before.payments(rows.first()).first();repo.voidEntry(payment.id,"撤销错误还款")
        val after=db.walletDao().read();assertTrue(after.payments(rows.first()).isEmpty());assertTrue(after.unpaid().isEmpty())
        assertEquals(before.cash()+2000,after.cash());assertEquals(before.usage(currentMonth()),after.usage(currentMonth()))
    }
    @Test fun backupSevenPreservesRemovalAndVersionSixDefaultsToActive()=runBlocking{
        repo.archiveDebt(d.id,true);val before=db.walletDao().read();val encoded=Backup.encode(before)
        val copy=Backup.decode(encoded);assertTrue(copy.debts.single().archived);assertEquals(before.entries,copy.entries);assertEquals(before.cash(),copy.cash());assertTrue(copy.unpaid().isEmpty())
        val old=JSONObject(encoded).put("version",6);val debts=old.getJSONArray("debts");for(i in 0 until debts.length())debts.getJSONObject(i).remove("archived")
        val legacy=Backup.decode(old.toString());assertFalse(legacy.debts.single().archived);assertEquals(before.entries,legacy.entries);assertEquals(before.cash(),legacy.cash());assertEquals(listOf(rows.last()),legacy.unpaid())
    }
    @Test fun restoreIntoEmptyLedgerRetainsRemovalAndHistory()=runBlocking{
        repo.archiveDebt(d.id,true);val original=db.walletDao().read()
        val fresh=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),WalletDatabase::class.java).build()
        try{val target=WalletRepository(fresh);target.prepare(false);target.restore(Backup.decode(Backup.encode(original)),false)
            val restored=fresh.walletDao().read();assertTrue(restored.debts.single().archived);assertEquals(original.entries.toSet(),restored.entries.toSet());assertEquals(original.installments.toSet(),restored.installments.toSet());assertEquals(original.cash(),restored.cash());assertTrue(restored.unpaid().isEmpty())
        }finally{fresh.close()}
    }
}
