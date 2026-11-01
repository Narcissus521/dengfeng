package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AnalysisPatternsTest {
    private fun expense(id:String,day:String,amount:Long,group:String="food",leaf:String="food.canteen")=Entry(id,"EXPENSE",amount,"a",day,category=ExpenseTaxonomy.groups.first{it.id==group}.name,categoryGroupId=group,subcategoryId=leaf)
    private fun index(vararg entries:Entry)=ExpenseIndex(Ledger(accounts=listOf(Account("a","现金","现金",1000000,"2024-01-01"),Account("b","备用现金","现金",0,"2024-01-01")),entries=entries.toList()))
    private fun range(first:String,last:String)=ReportRange(LocalDate.parse(first),LocalDate.parse(last))

    @Test fun calendarUsesRefundDayAndDoesNotCountTransfersOrFillOutsideRange() {
        val source=index(expense("e","2026-10-01",10000),Entry("r","REFUND",2000,"a","2026-10-02",originalId="e"),Entry("t","TRANSFER",50000,"a","2026-10-02",toAccountId="b"))
        val report=source.report(range("2026-10-01","2026-10-03"));val days=report.calendar(YearMonth.of(2026,10))
        assertEquals(3,days.size);assertEquals(10000L,days[0].totals.net);assertEquals(-2000L,days[1].totals.net)
        assertEquals(0L,days[2].totals.net);assertEquals(0,days[2].totals.count)
        assertEquals(report.total.net,days.map{it.totals.net}.exactSum())
        val filter=ReportFilter(range("2026-10-02","2026-10-02"))
        assertEquals(listOf("r"),source.records(filter).map{it.id})
    }
    @Test fun leapMonthAndMultiMonthSelectionOnlyIncludeCoveredDays() {
        val report=index().report(range("2024-02-26","2024-03-02"))
        assertEquals(listOf(26,27,28,29),report.calendar(YearMonth.of(2024,2)).map{it.date.dayOfMonth})
        assertEquals(listOf(1,2),report.calendar(YearMonth.of(2024,3)).map{it.date.dayOfMonth})
        assertTrue(report.calendar(YearMonth.of(2024,4)).isEmpty())
    }
    @Test fun weekendAveragesUseAllCoveredDaysIncludingZeroSpendDays() {
        val report=index(expense("e","2026-10-03",9000),Entry("r","REFUND",3000,"a","2026-10-04",originalId="e")).report(range("2026-10-01","2026-10-10"))
        val parts=report.rhythm();val weekday=parts.first{it.pattern==SpendingPattern.WEEKDAY};val weekend=parts.first{it.pattern==SpendingPattern.WEEKEND}
        assertEquals(7,weekday.days);assertEquals(3,weekend.days);assertEquals(0L,weekday.average)
        assertEquals(6000L,weekend.totals.net);assertEquals(2000L,weekend.average)
        assertEquals(report.total.net,parts.take(2).map{it.totals.net}.exactSum())
        assertNull(parts.first{it.pattern==SpendingPattern.MIDDLE}.average)
    }
    @Test fun monthSegmentsConserveNetRefundsAndNeverInventUncoveredDays() {
        val report=index(expense("old","2026-09-30",1000),Entry("r","REFUND",1000,"a","2026-10-01",originalId="old"),expense("new","2026-10-21",6000)).report(range("2026-10-01","2026-10-25"))
        val parts=report.rhythm().drop(2)
        assertEquals(listOf(10,10,5),parts.map{it.days});assertEquals(listOf(-1000L,0L,6000L),parts.map{it.totals.net})
        assertEquals(report.total.net,parts.map{it.totals.net}.exactSum())
    }
    @Test fun rhythmDrillDownRetainsCategoryFiltersAndOldLinksRemainReadable() {
        val source=index(expense("food","2026-10-03",1000),expense("health","2026-10-03",5000,"health","health.medicine"),expense("weekday","2026-10-05",3000))
        val range=range("2026-10-01","2026-10-10")
        val filter=ReportFilter(range,"food","food.canteen",pattern=SpendingPattern.WEEKEND)
        assertEquals(filter,ReportFilter.decode(filter.encode()));assertEquals(listOf("food"),source.records(filter).map{it.id})
        assertEquals(1000L,source.report(range).rhythm("food","food.canteen").first{it.pattern==SpendingPattern.WEEKEND}.totals.net)
        val old=ReportFilter.decode("2026-10-01|2026-10-10|food|food.canteen||")
        assertNull(old.pattern);assertEquals(2,source.records(old).size)
    }
    @Test fun severalAnomalyRulesResolveToOneRealCategoryWithoutAddingAmounts() {
        val source=index(expense("e","2026-10-03",100000));val range=range("2026-10-01","2026-10-25")
        val large=SpendingAlert("large","大额","",ReportFilter(range,consumptionId="e"),"e")
        val budget=SpendingAlert("budget","超支","",ReportFilter(range,"food"))
        val groups=listOf(large,budget).mapNotNull(source::alertGroup).toSet()
        assertEquals(setOf("food"),groups)
        assertEquals(100000L,groups.map{source.report(range).groups.getValue(it).net}.exactSum())
    }
}
