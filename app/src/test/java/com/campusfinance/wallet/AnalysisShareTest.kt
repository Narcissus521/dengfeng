package com.campusfinance.wallet

import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class AnalysisShareTest {
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
