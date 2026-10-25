package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class CategoryCompatibilityTest {
    private fun ledger()=Ledger(accounts=listOf(Account("a","微信","微信",10000,"2020-01-01")))
    @Test fun oldOtherRecordsRefundAndReminderStillValidateAlongsideNewSpendingCategories() {
        val date=today().toString()
        val s=ledger().copy(entries=listOf(
            Entry("old","EXPENSE",1000,"a",date,category="其他"),
            Entry("refund","REFUND",200,"a",date,originalId="old"),
            Entry("clothes","EXPENSE",300,"a",date,category="衣服"),
            Entry("beauty","EXPENSE",500,"a",date,category="美护")),
            reminder=ReminderSettings(enabled=true,categories="其他,衣服,美护"))
        Rules.validate(s)
        val stats=s.spending(YearMonth.from(today()),1)
        assertEquals(8400L,s.cash());assertEquals(1600L,stats.net)
        assertEquals(1300L,stats.categories["其他支出"]);assertEquals(300L,stats.categories["服饰鞋包"])
    }
    @Test fun legacyCompatibilityDoesNotAllowUnknownReminderCategories() {
        assertThrows(IllegalArgumentException::class.java) {
            Rules.validate(ledger().copy(reminder=ReminderSettings(enabled=true,categories="不存在的类别")))
        }
    }
}
