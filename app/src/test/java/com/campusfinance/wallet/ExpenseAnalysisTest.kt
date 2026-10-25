package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpenseAnalysisTest {
    private val now=LocalDate.of(2026,10,24)
    private val month=ReportRange.month("2026-10",now)
    private fun e(id:String="e",amount:Long=10000,day:String="2026-10-02",leaf:String="food.canteen",n:String="UNDECIDED")=Entry(id,"EXPENSE",amount,"a",day,category="测试用途",categoryGroupId=leaf.substringBefore('.'),subcategoryId=leaf,necessity=n)
    private fun ledger(vararg rows:Entry)=Ledger(accounts=listOf(Account("a","微信","微信",1000000,"2025-01-01"),Account("b","现金","现金",0,"2025-01-01")),entries=rows.toList())
    private fun r(id:String,original:String,amount:Long,day:String="2026-10-03")=Entry(id,"REFUND",amount,"b",day,originalId=original)
    private fun report(s:Ledger,range:ReportRange=month)=ExpenseIndex(s).report(range)
    private fun reject(block:()->Unit)=assertThrows(IllegalArgumentException::class.java,block)

    @Test fun fourteenCategoriesKeepTwoLevelsExceptCharityAndOther() {
        val g=ExpenseTaxonomy.groups;assertEquals(14,g.size);assertTrue(g.take(12).all{it.children.isNotEmpty()});assertTrue(g.takeLast(2).all{it.children.isEmpty()});assertEquals(61,g.sumOf{it.children.size})
        assertEquals(63,ExpenseTaxonomy.legacyLeaves.size);assertTrue(g.any{it.id=="charity"&&it.name=="公益捐赠"})
        assertFalse(g.any{it.name=="购物"});assertEquals("其他支出",g.last().name)
        g.forEach{p->assertEquals(p.id,ExpenseTaxonomy.explicit(p.id,"")!!.group.id)}
    }
    @Test fun oldCharityAndItsRefundMoveToIndependentCategoryWithoutChangingMoney() {
        val old=e(leaf="other.charity");val s=ledger(old,r("r","e",3000))
        Rules.validate(s,now);val p=report(s)
        assertEquals(7000L,p.groups.getValue("charity").net);assertEquals(0L,p.groups.getValue("other").net)
        assertEquals(993000L,s.cash());assertEquals("公益捐赠",s.expenseCategory(old).label)
    }
    @Test fun legacyMappingPreservesAmbiguityWithoutChangingCashOrBudget() {
        val old=e().copy(category="购物",categoryGroupId="",subcategoryId="",bucket="NECESSARY")
        val s=ledger(old);Rules.validate(s,now)
        assertEquals("other.unclassified",s.expenseCategory(old).leaf.id);assertEquals("UNDECIDED",s.necessityOf(old))
        assertEquals("clothing.clothes",ExpenseTaxonomy.fromLegacy("衣服").leaf.id)
        assertEquals("other.unclassified",ExpenseTaxonomy.fromLegacy("美护").leaf.id)
        assertEquals(990000L,s.cash());assertEquals(10000L,s.usage("2026-10","NECESSARY"))
    }
    @Test fun malformedParentChildAndUnknownNecessityRejected() {
        reject{Rules.validate(ledger(e().copy(categoryGroupId="transport")),now)}
        reject{Rules.validate(ledger(e().copy(necessity="AUTO")),now)}
        Rules.validate(ledger(e().copy(subcategoryId="")),now)
        reject{Rules.validate(ledger(e().copy(kind="INCOME")),now)}
    }
    @Test fun classificationAndNecessityAreIndependentOfBudgetBucketAndAmount() {
        val s=ledger(e(n="NONESSENTIAL").copy(bucket="NECESSARY"),e("b",200,"2026-10-04",n="NECESSARY").copy(bucket="FREE"),e("c",90000,"2026-10-05"))
        Rules.validate(s,now);val p=report(s)
        assertEquals(10000L,p.necessities.getValue("NONESSENTIAL").net);assertEquals(200L,p.necessities.getValue("NECESSARY").net);assertEquals(90000L,p.necessities.getValue("UNDECIDED").net)
    }
    @Test fun crossPeriodRefundAndHistoricalReclassificationMoveAllLevelsTogether() {
        val original=e(day="2026-09-20");val before=ledger(original,r("r","e",4000))
        val edited=Rules.replaceEntry(before,original.copy(categoryGroupId="clothing",subcategoryId="clothing.clothes",necessity="NONESSENTIAL"))
        assertEquals(before.cash(),edited.cash());assertEquals(before.usage("2026-10"),edited.usage("2026-10"))
        val p=report(edited);assertEquals(-4000L,p.total.net);assertEquals(-4000L,p.groups.getValue("clothing").net);assertEquals(-4000L,p.necessities.getValue("NONESSENTIAL").net)
        assertEquals(0L,p.groups.getValue("food").net)
        val previous=report(edited,ReportRange.month("2026-09",now));assertEquals(10000L,previous.groups.getValue("clothing").net)
    }
    @Test fun allAmountsConserveAcrossEveryLevelEvenNegativeRefundOnlySlices() {
        val s=ledger(e("past",8000,"2026-09-10","clothing.clothes"),r("r","past",6000),e(amount=3000),e("health",700,"2026-10-04","health.medicine","NECESSARY"))
        Rules.validate(s,now);val p=report(s)
        assertEquals(-2300L,p.total.net);assertEquals(p.total.net,p.groups.values.map{it.net}.exactSum());assertEquals(p.total.net,p.necessities.values.map{it.net}.exactSum())
        assertTrue(p.grossBasis);assertEquals(3700L,p.chartTotal);assertEquals(p.chartTotal,p.groups.values.map{it.chart(p.grossBasis)}.exactSum())
    }
    @Test fun completeRefundSwitchesToGrossChartButNetStaysZero() {
        val p=report(ledger(e(),r("r","e",10000)));assertEquals(0L,p.total.net);assertTrue(p.grossBasis);assertEquals(10000L,p.chartTotal)
    }
    @Test fun transfersLoansRepaymentAndVoidedRowsAreNotConsumption() {
        val s=ledger(e(),Entry("t","TRANSFER",20000,"a","2026-10-02",toAccountId="b"),Entry("i","INCOME",20000,"a","2026-10-02"),Entry("rep","REPAYMENT",50000,"a","2026-10-02"),e("void").copy(voided=true),e("fee",500,leaf="finance.interest"))
        val p=report(s);assertEquals(10500L,p.total.net);assertEquals(2,p.total.count);assertEquals(500L,p.groups.getValue("finance").net)
    }
    @Test fun rangeIsInclusiveAndDailyAverageUsesSelectedCalendarDays() {
        val p=report(ledger(e(day="2026-10-01",amount=100),e("b",101,"2026-10-03"),e("c",999,"2026-10-04")),ReportRange(LocalDate.of(2026,10,1),LocalDate.of(2026,10,3)))
        assertEquals(201L,p.total.net);assertEquals(67L,p.dailyAverage);assertEquals(2,p.total.count)
    }
    @Test fun drillDownUsesOriginalCurrentMetadataAndIncludesRefundsOnlyInRange() {
        val s=ledger(e(n="NECESSARY"),r("r","e",500),e("other",300,leaf="other.unclassified"))
        val filter=ReportFilter(month,"food",necessity="NECESSARY")
        assertEquals(filter,ReportFilter.decode(filter.encode()));assertEquals(setOf("e","r"),ExpenseIndex(s).records(filter).map{it.id}.toSet())
    }
    @Test fun zeroNegativeAndMissingPreviousMonthNeverProduceInvalidGrowthRate() {
        assertNull(compareSpending(100,0).percent);assertNull(compareSpending(100,100,false).percent)
        assertNull(compareSpending(-100,100).percent);assertNull(compareSpending(100,-100).percent)
        assertEquals(50.0,compareSpending(150,100).percent!!,.001);assertEquals(-100.0,compareSpending(0,100).percent!!,.001)
    }
    @Test fun trendIncludesEmptyMonthsAndRebuildsAfterHistoricalDateEdit() {
        val original=e(day="2026-09-01");val index=ExpenseIndex(ledger(original))
        val t=index.trend(YearMonth.of(2026,10),6,"food",now=now)
        assertEquals(6,t.size);assertFalse(t.first().hasData);assertEquals(10000L,t[t.lastIndex-1].amount)
        val moved=ExpenseIndex(ledger(original.copy(date="2026-08-01"))).trend(YearMonth.of(2026,10),12,"food",now=now)
        assertEquals(12,moved.size);assertEquals(0L,moved.find{it.month=="2026-09"}!!.amount);assertEquals(10000L,moved.find{it.month=="2026-08"}!!.amount)
    }
    @Test fun categoryBudgetsAreAnotherSubdivisionOfTotalAndNeverChangeCash() {
        val s=ledger(e()).copy(budgets=listOf(Budget("2026-10",20000,10000,5000)),categoryBudgets=listOf(CategoryBudget("2026-10","food",12000),CategoryBudget("2026-10","health",8000)))
        Rules.validate(s,now);val c=ExpenseIndex(s).budgetComparison(month,"food")
        assertEquals(12000L,c.planned);assertEquals(10000L,c.actual);assertEquals(2000L,c.remaining);assertEquals(990000L,s.cash())
        reject{Rules.validate(s.copy(categoryBudgets=s.categoryBudgets+CategoryBudget("2026-10","other",1)),now)}
        reject{Rules.validate(s.copy(categoryBudgets=listOf(CategoryBudget("2026-09","food",100))),now)}
    }
    @Test fun customRangeBudgetSeparatesUnbudgetedMonthsAndDoesNotProrate() {
        val s=ledger(e(),e("past",5000,"2026-09-25")).copy(budgets=listOf(Budget("2026-10",20000,0,0)),categoryBudgets=listOf(CategoryBudget("2026-10","food",0)))
        val range=ReportRange(LocalDate.of(2026,9,20),now);val total=ExpenseIndex(s).budgetComparison(range)
        assertEquals(20000L,total.planned);assertEquals(10000L,total.actual);assertEquals(5000L,total.unbudgetedActual);assertEquals(1,total.coveredMonths);assertEquals(2,total.allMonths)
        val cap=ExpenseIndex(s).budgetComparison(range,"food");assertEquals(0L,cap.planned);assertNull(cap.rate)
    }
    @Test fun splitAccountOccurrenceCountsOnceAndLargeAlertUsesCombinedUnrefundedAmount() {
        val s=ledger(e(amount=30000).copy(recurringOccurrenceId="rule@2026-10-02"),e("part",25000).copy(recurringOccurrenceId="rule@2026-10-02"))
        assertEquals(1,report(s).total.count);assertEquals(1,report(s).groups.getValue("food").count)
        val alert=ExpenseIndex(s).alerts(YearMonth.of(2026,10),AnomalyRules(),now).alerts.single()
        assertEquals("large",alert.type);assertNull(alert.entryId);assertEquals(2,ExpenseIndex(s).records(alert.filter).size)
        val refunded=s.copy(entries=s.entries+r("r","e",10000));assertTrue(ExpenseIndex(refunded).alerts(YearMonth.of(2026,10),AnomalyRules(),now).alerts.isEmpty())
    }
    @Test fun largeAlertRespectsUserThresholdAndRefundsBeforePeriodEnd() {
        val s=ledger(e(amount=60000),r("r","e",20000))
        assertTrue(ExpenseIndex(s).alerts(YearMonth.of(2026,10),AnomalyRules(50000),now).alerts.isEmpty())
        assertEquals(1,ExpenseIndex(s).alerts(YearMonth.of(2026,10),AnomalyRules(40000),now).alerts.size)
    }
    private fun history()=(7..9).flatMap{m->listOf(e("f$m",10000,"2026-0$m-02"),e("h$m",90000,"2026-0$m-03","health.medicine"))}
    @Test fun explainableGrowthAndStructureNeedThreeCompleteValidMonthsAndMinimumGap() {
        val s=ledger(*(history()+e(amount=30000)).toTypedArray())
        val alerts=ExpenseIndex(s).alerts(YearMonth.of(2026,10),AnomalyRules(100000),now)
        assertEquals(setOf("growth","structure"),alerts.alerts.map{it.type}.toSet());assertTrue(alerts.alerts.all{it.filter.groupId=="food"})
        val insufficient=s.copy(entries=s.entries.filterNot{it.date.startsWith("2026-07")})
        assertTrue(ExpenseIndex(insufficient).alerts(YearMonth.of(2026,10),AnomalyRules(100000),now).alerts.isEmpty())
        val tiny=ledger(*(history()+e(amount=15001)).toTypedArray())
        assertTrue(ExpenseIndex(tiny).alerts(YearMonth.of(2026,10),AnomalyRules(100000),now).alerts.isEmpty())
    }
    @Test fun budgetAlertWorksWithoutHistoryAndLinksMatchingCategory() {
        val s=ledger(e(amount=12000)).copy(budgets=listOf(Budget("2026-10",30000,0,0)),categoryBudgets=listOf(CategoryBudget("2026-10","food",10000)))
        val alerts=ExpenseIndex(s).alerts(YearMonth.of(2026,10),AnomalyRules(),now).alerts
        assertEquals("budget",alerts.single().type);assertEquals(listOf("e"),ExpenseIndex(s).records(alerts.single().filter).map{it.id})
    }
    @Test fun currentYearAdapterDoesNotIncludePreviousYearWhenClipped() {
        val s=ledger(e(day="2025-12-01"))
        val stats=s.spending(YearMonth.of(today().year,12),12)
        assertTrue(stats.months.all{it.startsWith(today().year.toString())});assertEquals(0L,stats.net)
    }
    @Test fun futureOnlyOverviewIsEmptyWithoutIncludingOrCrashingOnPlannedMonths() {
        val stats=ledger(e()).spending(YearMonth.from(today()).plusMonths(1),1)
        assertTrue(stats.months.isEmpty());assertEquals(0L,stats.net);assertNull(stats.overPercent)
    }
    @Test fun necessityTrendUsesExplicitJudgmentAndKeepsUndecidedSeparate() {
        val s=ledger(e(amount=10000).copy(bucket="NECESSARY"),e("n",3000,n="NECESSARY"),e("free",5000,n="NONESSENTIAL"))
        val index=ExpenseIndex(s);val point=index.trend(YearMonth.of(2026,10),6,"food","food.canteen",now,necessity="NECESSARY").last()
        assertEquals(3000L,point.amount);assertEquals(10000L,point.report.slice("food","food.canteen","UNDECIDED").net)
    }
    @Test fun historyMeanUsesOnlyCoveredCompleteMonthsAndIncludesKnownZeroMonths() {
        val s=ledger(e(day="2026-09-02"));val points=ExpenseIndex(s).trend(YearMonth.of(2026,10),6,now=now)
        val mean=trendHistoricalMean(points,s,now)!!;assertEquals(5,mean.months);assertEquals(2000L,mean.amount)
        assertNull(trendHistoricalMean(points,s.copy(accounts=s.accounts.map{it.copy(start="2026-09-15")}),now))
    }
    @Test fun moduleRoutesCarryRangeAndBothCategoryFiltersWithoutLosingThem() {
        val dest=AnalysisDestination("trend",month,"food","food.takeaway")
        assertEquals(dest,AnalysisDestination.decode(dest.encode(),"2026-10"));assertEquals(dest.encode(),analysisEditor("trend",month,"food","food.takeaway").kind)
    }

    @Test fun categoryLegendsUseDistinctStableColorsWithinEachDisplayedLevel() {
        assertEquals(ExpenseTaxonomy.groups.size,ExpenseTaxonomy.groups.map{expenseChartColor(it.id)}.distinct().size)
        ExpenseTaxonomy.groups.forEach{g->assertEquals(g.children.size,g.children.map{expenseChartColor(it.id)}.distinct().size)}
    }

}
