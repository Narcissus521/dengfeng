package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

class RecurringAndAnalysisTest {
    private val now=ZonedDateTime.of(2026,10,21,9,0,0,0,financeZone)
    private val rule=RecurringRule(id="breakfast",name="早餐",kind="EXPENSE",amount=800,frequency="DAILY",startDate="2026-10-20",category="餐饮")
    private fun ledger()=Ledger(accounts=listOf(Account("a","微信","微信",100000,"2026-01-01"),Account("b","支付宝","支付宝",100000,"2026-01-01")),
        recurringRules=listOf(rule),recurringAllocations=listOf(RecurringAllocation(rule.id,"a",300),RecurringAllocation(rule.id,"b",500)))
    @Test fun dailyOnlyPostsAfterTimeAndDoesNotCountFutureIncome(){val s=ledger();Rules.validate(s);assertEquals(200000L,s.cash());assertEquals(2,pendingRecurring(s,now).size);assertEquals(1,pendingRecurring(s,now.withHour(7)).size)}
    @Test fun undoneAndSkippedOccurrencesNeverReappear(){val s=ledger().copy(recurringOccurrences=listOf(RecurringOccurrence("breakfast@2026-10-20","breakfast","2026-10-20",800,"EXPENSE","SKIPPED")));assertEquals(listOf(LocalDate.of(2026,10,21)),pendingRecurring(s,now).map{it.second})}
    @Test fun monthlyClampsButDoesNotDrift(){val r=rule.copy(frequency="MONTHLY",day=31,startDate="2026-01-01");val s=ledger().copy(recurringRules=listOf(r));val dates=pendingRecurring(s,ZonedDateTime.of(2026,3,31,10,0,0,0,financeZone)).map{it.second.toString()};assertEquals(listOf("2026-01-31","2026-02-28","2026-03-31"),dates)}
    @Test fun monthlyStartAfterDaySkipsThePastOccurrence(){val r=rule.copy(frequency="MONTHLY",day=1,startDate="2026-10-20");assertTrue(pendingRecurring(ledger().copy(recurringRules=listOf(r)),now).isEmpty());assertEquals("2026-11-01",r.nextAfter(now).toLocalDate().toString())}
    @Test fun splitMismatchRejected(){val s=ledger().copy(recurringAllocations=listOf(RecurringAllocation(rule.id,"a",800),RecurringAllocation(rule.id,"b",500)));assertThrows(IllegalArgumentException::class.java){Rules.validate(s)}}
    @Test fun groupUndoCannotLeaveOneAccountCharged(){val o=RecurringOccurrence("breakfast@2026-10-20",rule.id,"2026-10-20",800,"EXPENSE");val s=ledger().copy(recurringOccurrences=listOf(o),entries=listOf(Entry(kind="EXPENSE",amount=300,accountId="a",date=o.date,category="餐饮",recurringOccurrenceId=o.id),Entry(kind="EXPENSE",amount=500,accountId="b",date=o.date,category="餐饮",recurringOccurrenceId=o.id,voided=true)));assertThrows(IllegalArgumentException::class.java){Rules.validate(s)}}
    @Test fun leapYearNextDueUsesBeijingTime(){val r=rule.copy(frequency="MONTHLY",day=31);assertEquals("2028-02-29",r.nextAfter(ZonedDateTime.of(2028,2,1,0,0,0,0,financeZone)).toLocalDate().toString())}
    @Test fun nextDailyCanBeginInFuture(){val r=rule.copy(startDate="2026-11-01");assertEquals("2026-11-01",r.nextAfter(now).toLocalDate().toString())}
    @Test fun crossMonthRefundCannotProduceNegativeRingSlice(){val original=Entry(id="e",kind="EXPENSE",amount=10000,accountId="a",date="2026-09-01",category="购物");val s=ledger().copy(entries=listOf(original,Entry(kind="REFUND",amount=5000,accountId="a",date="2026-10-01",originalId="e"),Entry(kind="EXPENSE",amount=3000,accountId="a",date="2026-10-02",category="餐饮")));val p=s.spending(YearMonth.of(2026,10),1);assertTrue(p.chartUsesGross);assertEquals(-2000L,p.net);assertEquals(3000L,p.chartTotal);assertEquals(p.categories.values.exactSum(),p.buckets.values.exactSum())}
    @Test fun budgetRateOnlyUsesMonthsWithBudget(){val s=ledger().copy(entries=listOf(Entry(kind="EXPENSE",amount=20000,accountId="a",date="2026-09-02",category="购物")),budgets=listOf(Budget("2026-08",30000,10000,10000),Budget("2026-09",10000,5000,5000)));val p=s.spending(YearMonth.of(2026,10),3);assertEquals(2,p.budgetMonths.size);assertEquals(1,p.overMonths.size);assertEquals(50.0,p.overPercent!!,0.001)}
    @Test fun quotedPlanDoesNotEvadeGoodsBudget(){val s=ledger().copy(budgets=listOf(Budget("2026-10",10000,5000,5000)));val r=s.review(PurchaseInput("耳机",10000,"a",amounts=listOf(3500,3500,3500),firstDate="2026-11-01"),now.toLocalDate());assertEquals(-5000L,r.bucketLeft);assertEquals(0L,r.payNow);assertEquals(500L,r.quoteExtra);assertTrue(r.installmentAdvice.contains("超预算"));assertEquals(200000L,s.cash())}
    @Test fun firstInstallmentDueTodayIsIncludedInCashNow(){val r=ledger().review(PurchaseInput("耳机",10000,"a",down=1000,amounts=listOf(3000,3000,3000),firstDate="2026-10-21"),now.toLocalDate());assertEquals(4000L,r.payNow);assertEquals(196000L,r.installmentCashAfter)}
    @Test fun teaSummariesStayWithinFiftyCharacters(){val s=demoLedger();listOf("month","half","year").forEach{assertTrue(s.teaSummary(it).length<=50)}}
}
