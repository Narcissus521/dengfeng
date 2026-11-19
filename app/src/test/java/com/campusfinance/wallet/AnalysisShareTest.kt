package com.campusfinance.wallet

import java.time.YearMonth
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AnalysisShareTest {
    @Test fun sameDayWindowsAndCategoryBudgetUseTheirOwnCutoffs() {
        val food = Entry(id = "food", kind = "EXPENSE", amount = 12000, accountId = "cash",
            category = "餐饮", date = "2026-02-28")
        val ledger = Ledger(
            entries = listOf(
                food,
                food.copy(id = "march28", date = "2026-03-28", amount = 9000),
                food.copy(id = "march29", date = "2026-03-29", amount = 5000),
                Entry(kind = "REFUND", amount = 1000, accountId = "cash", originalId = food.id, date = "2026-03-27")
            ),
            categoryBudgets = listOf(CategoryBudget("2026-03", "food", 10000))
        )
        val month = YearMonth.of(2026, 3)
        val now = LocalDate.of(2026, 3, 30)
        val overview = AnalysisShareOverview(ledger, month, now)
        assertEquals(28, overview.days)
        assertEquals(8000L, overview.current)
        assertEquals(12000L, overview.previous)
        assertEquals(-3000L, overview.budgets.single().remaining)
        val report = ledger.analysisShare(month, false, false, now)
        assertTrue(report.contains("2026-02 1–28 日，120.00 元"))
        assertTrue(report.contains("已用 130.00 元，预算 100.00 元，超出 30.00 元"))
    }

    @Test fun reportSeparatesRepaymentAndOmitsPrivateFields() {
        val s = Ledger(accounts = listOf(Account("secret-id", "私人账户名", "现金", 50000, "2026-01-01")),
            entries = listOf(
                Entry(kind = "EXPENSE", amount = 12000, accountId = HUABEI_ACCOUNT, date = "2026-10-10",
                    category = "餐饮", note = "私密备注", huabeiDueDate = "2026-11-10"),
                Entry(kind = "TRANSFER", amount = 3000, accountId = "secret-id", date = "2026-10-11",
                    toAccountId = HUABEI_ACCOUNT, huabeiDueDate = "2026-11-10")))
        val report = s.analysisShare(YearMonth.of(2026, 10), true, true)
        assertTrue(report.contains("净消费 120.00"))
        assertTrue(report.contains("还款 30.00"))
        assertTrue(report.contains("90.00"))
        assertFalse(report.contains("私密备注"))
        assertFalse(report.contains("私人账户名"))
        assertFalse(report.contains("secret-id"))
        val summary = s.analysisShare(YearMonth.of(2026, 10), false, false)
        assertFalse(summary.contains("2026-10-10"))
        assertFalse(summary.contains("现金账户余额"))
    }
}
