package com.campusfinance.wallet

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HuabeiRepositoryTest {
    private lateinit var db: WalletDatabase
    private lateinit var repo: WalletRepository
    private val purchaseDate = today().minusMonths(1).withDayOfMonth(1).toString()
    private val purchase = Entry(kind = "EXPENSE", amount = 12000, accountId = HUABEI_ACCOUNT,
        date = purchaseDate, category = "餐饮")

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WalletDatabase::class.java).build()
        repo = WalletRepository(db)
        repo.prepare(false)
        repo.account(Account("bank", "银行卡", "银行卡", 100000, purchaseDate), "测试余额")
    }

    @After
    fun close() { db.close() }

    private suspend fun read() = db.walletDao().read()

    @Test
    fun billCombinesPurchasesAndRefundsAndOnlyRepaymentMovesCash() = runBlocking {
        repo.entry(purchase, huabeiDay = 12)
        repo.entry(purchase.copy(id = newId(), amount = 3000), huabeiDay = 20)
        val first = read()
        val originalBill = first.huabeiBills().single()
        assertEquals("${currentMonth()}-12", originalBill.dueDate)
        assertEquals(15000L, first.usage(purchaseDate.take(7)))
        assertEquals(100000L, first.cash())

        val refund = Entry(kind = "REFUND", amount = 2000, accountId = HUABEI_ACCOUNT,
            date = today().toString(), originalId = purchase.id)
        repo.entry(refund)
        assertTrue(runCatching { repo.payHuabei(originalBill, "bank", today().toString()) }.isFailure)
        repo.updateHuabeiDueDate(read().huabeiBills().single(), "${currentMonth()}-15")
        val adjusted = read()
        val bill = adjusted.huabeiBills().single()
        assertEquals(PendingRepayment("${currentMonth()}-15", 13000, "花呗"), adjusted.pendingRepayments().single())
        assertEquals(100000L, adjusted.cash())

        repo.payHuabei(bill, "bank", today().toString())
        assertTrue(runCatching { repo.payHuabei(bill, "bank", today().toString()) }.isFailure)
        assertTrue(runCatching { repo.entry(purchase.copy(amount = 12500)) }.isFailure)
        val paid = read()
        val payment = paid.huabeiBills().single().payments.single()
        assertEquals(87000L, paid.cash())
        assertEquals(-2000L, paid.usage(currentMonth()))
        assertEquals(13000L, recordSummary(listOf(payment)).repayments)
        assertTrue(paid.pendingRepayments().isEmpty())
        assertEquals(paid.huabeiBills(), Backup.decode(Backup.encode(paid)).huabeiBills())

        repo.voidEntry(payment.id, "更正误登")
        assertEquals(100000L, read().cash())
        assertEquals(13000L, read().pendingRepayments().single().due)
        repo.voidEntry(refund.id, "更正退款")
        repo.voidEntry(purchase.id, "更正消费")
        assertEquals(3000L, read().pendingRepayments().single().due)
    }

    @Test
    fun dateCorrectionMovesTheBillAndFullCreditRefundClearsPending() = runBlocking {
        val older = purchase.copy(date = today().minusMonths(2).withDayOfMonth(1).toString())
        repo.entry(older)
        assertEquals(older.date, read().huabeiBills().single().purchases.single().date)
        repo.entry(purchase.copy(amount = 5000))
        assertEquals(currentMonth(), read().huabeiBills().single().month)
        assertEquals(5000L, read().pendingRepayments().single().due)
        repo.entry(Entry(kind = "REFUND", amount = 5000, accountId = HUABEI_ACCOUNT,
            date = today().toString(), originalId = purchase.id))
        assertTrue(read().pendingRepayments().isEmpty())
        assertEquals(100000L, read().cash())
    }
}
