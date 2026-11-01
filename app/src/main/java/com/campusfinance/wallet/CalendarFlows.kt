package com.campusfinance.wallet

import java.time.LocalDate
import java.time.YearMonth

data class CalendarFlowDay(val date:LocalDate,val spending:ExpenseTotals,val income:Long,val incomeCount:Int)
/** True posted income only; refunds remain attached consumption offsets, never income. */
fun Ledger.calendarFlows(report:ExpenseReport,month:YearMonth):List<CalendarFlowDay> {
    val receipts=activeEntries.filter{it.kind==Kind.INCOME.name&&report.range.contains(it.date)}.groupBy{it.date}
    return report.calendar(month).map{day->val income=receipts[day.date.toString()].orEmpty()
        CalendarFlowDay(day.date,day.totals,income.map{it.amount}.exactSum(),income.size)}
}
