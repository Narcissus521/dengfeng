package com.campusfinance.wallet

import androidx.compose.ui.test.hasText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class HuabeiUiTest : TeaUiScenario() {
    @Test
    fun manualExpenseAppearsInNextMonthsBillAndCanBeRepaid() {
        ready()
        val db = WalletDatabase.open(context, true)
        val note = "huabei-ui-${newId()}"
        val month = YearMonth.from(today()).plusMonths(1).toString()
        val key = ExpenseAccountMemory.key(true)
        val previous = prefs.getString(key, null)
        try {
            prefs.edit().remove(key).commit()
            val before = runBlocking { db.walletDao().read() }
            assertTrue(before.huabeiBills().none { it.month == month })
            val defaultAccount = ExpenseAccountMemory(prefs).selected(before, today().toString())
            navigate("记账")
            tapTag("leaf-EXPENSE")
            tap(before.accountName(defaultAccount))
            tap("花呗（次月还款）")
            fill("金额（元）", "12.34")
            find(hasText("将计入 $month 花呗待还", substring = true))
            fill("备注（可选）", note)
            submit()
            ui.waitUntil(10000) { runBlocking { db.walletDao().entries().any { it.note == note } } }
            val posted = runBlocking { db.walletDao().read() }
            val bill = posted.huabeiBills().single { it.month == month }
            assertEquals(before.cash(), posted.cash())
            assertEquals(before.usage(currentMonth()) + 1234, posted.usage(currentMonth()))
            assertEquals(HUABEI_ACCOUNT, bill.purchases.single { it.note == note }.accountId)
            navigate("记账")
            tapTag("leaf-EXPENSE")
            find(hasText("花呗（次月还款）"))

            navigate("规划")
            tapTag("card-PLAN_DEBT")
            tap("$month 花呗账单")
            tap("登记实际还款")
            submit()
            ui.waitUntil(10000) { runBlocking { db.walletDao().read().cash() == before.cash() - bill.remaining } }
            find(hasText("已结清"))
            assertEquals(posted.usage(currentMonth()), runBlocking { db.walletDao().read().usage(currentMonth()) })
        } finally {
            runCatching { navigate("首页") }
            runBlocking {
                val s = db.walletDao().read()
                val entry = s.activeEntries.firstOrNull { it.note == note }
                if (entry != null) {
                    val repo = WalletRepository(db)
                    s.huabeiBills().firstOrNull { it.month == month }?.payments?.forEach {
                        repo.voidEntry(it.id, "撤销测试还款")
                    }
                    repo.voidEntry(entry.id, "撤销测试消费")
                }
            }
            prefs.edit().putString(key, previous).commit()
            db.close()
        }
    }
}
