package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarFlowsTest {
    private val range=ReportRange(LocalDate.of(2026,10,1),LocalDate.of(2026,10,25))
    private fun ledger(vararg entries:Entry)=Ledger(accounts=listOf(Account("a","现金","现金",1000000,"2026-01-01"),Account("b","备用","现金",0,"2026-01-01")),entries=entries.toList())
    private fun flow(s:Ledger)=s.calendarFlows(ExpenseIndex(s).report(range),YearMonth.of(2026,10))
    private fun expense()=Entry("e","EXPENSE",2345,"a","2026-10-03",category="餐饮食品",categoryGroupId="food",subcategoryId="food.canteen")
    @Test fun oneDayKeepsBothIncomeAndExpenseAndDoesNotTurnRefundTransferOrCalibrationIntoIncome() {
        val s=ledger(expense(),Entry("i","INCOME",50000,"a","2026-10-03",category="奖学金"),Entry("r","REFUND",500,"b","2026-10-03",originalId="e"),Entry("t","TRANSFER",80000,"a","2026-10-03",toAccountId="b"),Entry("a","ADJUSTMENT",70000,"a","2026-10-03"),Entry("v","INCOME",99999,"a","2026-10-03",category="红包",voided=true))
        val day=flow(s).first{it.date.dayOfMonth==3}
        assertEquals(2345L,day.spending.gross);assertEquals(500L,day.spending.refunds);assertEquals(1845L,day.spending.net)
        assertEquals(50000L,day.income);assertEquals(1,day.incomeCount)
        assertEquals(s.income("2026-10"),flow(s).map{it.income}.exactSum())
    }
    @Test fun refundOnlyDayStaysARefundAndVoidingItUpdatesTheCalendar() {
        val refund=Entry("r","REFUND",500,"a","2026-10-04",originalId="e")
        val s=ledger(expense(),refund);val day=flow(s).first{it.date.dayOfMonth==4}
        assertEquals(0L,day.income);assertEquals(0L,day.spending.gross);assertEquals(500L,day.spending.refunds)
        val edited=s.copy(entries=s.entries.map{if(it.id==refund.id)it.copy(voided=true) else it})
        assertEquals(0L,flow(edited).first{it.date.dayOfMonth==4}.spending.refunds)
    }
    @Test fun plannedIncomeDoesNotAppearBeforeItsRealReceipt() {
        val planned=ledger().copy(incomePlans=listOf(IncomePlan("a",600000,1,"生活费","2026-10")))
        assertEquals(0L,flow(planned).map{it.income}.exactSum())
        val received=planned.copy(entries=listOf(Entry("receipt","INCOME",600000,"a","2026-10-03",category="生活费")))
        assertEquals(600000L,flow(received).map{it.income}.exactSum())
    }
}
