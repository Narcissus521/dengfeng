package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BudgetPresentationTest {
    @Test fun presentationPreservesHistoricBudgetBucketsAndRefundDates() {
        val old=Entry(id="old",kind="EXPENSE",amount=10000,accountId="a",date="2026-09-12",bucket="NECESSARY",necessity="UNDECIDED")
        val non=Entry(id="non",kind="EXPENSE",amount=3000,accountId="a",date="2026-10-03",bucket="FREE",necessity="NONESSENTIAL")
        val refund=Entry(id="refund",kind="REFUND",amount=2000,accountId="a",date="2026-10-05",originalId="old")
        val undecided=non.copy(id="unjudged",amount=4000,bucket="UNDECIDED",necessity="UNDECIDED")
        val ledger=Ledger(entries=listOf(old,non,refund,undecided),budgets=listOf(Budget("2026-10",20000,13000,5000)))
        val index=ExpenseIndex(ledger);val range=ReportRange(LocalDate.of(2026,9,1),LocalDate.of(2026,10,28))
        val necessary=index.budgetNatureComparison(range,"NECESSARY")
        assertEquals(13000L,necessary.planned);assertEquals(-2000L,necessary.actual)
        assertEquals(10000L,necessary.unbudgetedActual);assertEquals(15000L,necessary.remaining)
        assertEquals(1,necessary.coveredMonths);assertEquals(2,necessary.allMonths)
        val free=index.budgetNatureComparison(range,"FREE")
        assertEquals(3000L,free.actual);assertEquals(2000L,free.remaining)
        assertEquals(5000L,index.report(ReportRange.month("2026-10",range.end)).total.net)
        assertEquals(listOf(old,non,refund,undecided),ledger.entries)
    }
    @Test fun absentAndZeroBudgetsNeverBecomeInventedAllowances() {
        val range=ReportRange.month("2026-10",LocalDate.of(2026,10,28))
        val missing=ExpenseIndex(Ledger()).budgetNatureComparison(range,"NECESSARY")
        assertNull(missing.planned);assertNull(missing.remaining);assertNull(missing.rate)
        val zero=ExpenseIndex(Ledger(budgets=listOf(Budget("2026-10",0,0,0)))).budgetNatureComparison(range,"FREE")
        assertEquals(0L,zero.planned);assertEquals(0L,zero.remaining);assertNull(zero.rate)
    }
}
