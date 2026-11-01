package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class ConsumptionTypeTest {
    private val now=today()
    private fun expense(id:String="e",amount:Long=1000,date:String=now.toString())=Entry(id,"EXPENSE",amount,"a",date,category="餐饮食品",categoryGroupId="food",subcategoryId="food.canteen")
    private fun ledger(vararg rows:Entry)=Ledger(accounts=listOf(Account("a","现金","现金",1000000,now.minusYears(1).toString())),entries=rows.toList(),budgets=listOf(Budget(currentMonth(),10000,4000,3000)))
    @Test fun newExpensesAndPlansDefaultToUnjudgedAndEachExplicitChoiceMapsToOneBudget() {
        assertEquals("UNDECIDED",expense().bucket)
        val plan=RecurringRule(name="午餐",kind="EXPENSE",amount=1000)
        assertEquals("UNDECIDED",plan.bucket)
        Necessity.entries.forEach{n->
            val e=expense().withConsumptionType(n.name);val p=plan.withConsumptionType(n.name)
            assertEquals(n.name,consumptionNecessity(e.bucket));assertFalse(consumptionPending(e.necessity,e.bucket))
            assertEquals(e.bucket,p.bucket);assertEquals(e.necessity,p.necessity)
        }
    }
    @Test fun unjudgedSpendingAndItsRefundUseTotalButNeitherBudgetPartNorUnallocatedCap() {
        val e=expense();val r=Entry("r","REFUND",200,"a",now.toString(),originalId=e.id)
        val s=ledger(e,r);Rules.validate(s)
        assertEquals(800L,s.usage(currentMonth()));assertEquals(800L,s.usage(currentMonth(),"UNDECIDED"))
        assertEquals(0L,s.usage(currentMonth(),"NECESSARY"));assertEquals(0L,s.usage(currentMonth(),"FREE"))
        assertEquals(3000L,s.budgets.single().let{it.total-it.necessary-it.free})
        assertEquals(800L,ExpenseIndex(s).report(ReportRange.month(currentMonth())).necessities.getValue("UNDECIDED").net)
        assertEquals(1000000L-800,s.cash())
    }
    @Test fun legacyConflictsStayUnchangedAndPendingRefundSourcesAreDeduplicated() {
        val e=expense(date=now.minusMonths(1).toString()).copy(bucket="FREE",necessity="NECESSARY")
        val r=Entry("r","REFUND",100,"a",now.toString(),originalId=e.id)
        val r2=r.copy(id="r2");val s=ledger(e,r,r2)
        assertTrue(consumptionPending(e.necessity,e.bucket));assertEquals(listOf(e),s.pendingConsumption(currentMonth()))
        assertEquals(-200L,s.usage(currentMonth(),"FREE"));assertEquals(0L,s.usage(currentMonth(),"NECESSARY"))
        assertEquals(e,s.entries.first());assertTrue(s.copy(entries=listOf(e,r.copy(voided=true),r2.copy(voided=true))).pendingConsumption(currentMonth()).isEmpty())
    }
    @Test fun explicitConfirmationReclassifiesOriginalAndCrossMonthRefundWithoutChangingCash() {
        val e=expense(date=now.minusMonths(1).toString()).copy(bucket="FREE",necessity="NECESSARY")
        val s=ledger(e,Entry("r","REFUND",200,"a",now.toString(),originalId=e.id))
        val updated=Rules.replaceEntry(s,e.withConsumptionType("UNDECIDED"))
        assertEquals(s.cash(),updated.cash());assertEquals(s.usage(currentMonth()),updated.usage(currentMonth()))
        assertEquals(-200L,updated.usage(currentMonth(),"UNDECIDED"));assertEquals(0L,updated.usage(currentMonth(),"FREE"))
        assertEquals(1000L,updated.usage(YearMonth.from(now.minusMonths(1)).toString(),"UNDECIDED"));assertTrue(updated.pendingConsumption(currentMonth()).isEmpty())
        assertEquals(-200L,ExpenseIndex(updated).report(ReportRange.month(currentMonth())).necessities.getValue("UNDECIDED").net)
    }
    @Test fun unjudgedPurchaseHasNoInventedSubBudgetButStillReducesTotalBudget() {
        val s=ledger();val p=PurchaseInput("茶杯",500,"a")
        val unjudged=s.review(p);assertEquals(9500L,unjudged.budgetLeft);assertNull(unjudged.bucketLeft)
        assertEquals(3500L,s.review(p.copy(bucket="NECESSARY")).bucketLeft)
        assertEquals(2500L,s.review(p.copy(bucket="FREE")).bucketLeft)
    }
    @Test fun voidedExpenseAndRefundDisappearFromBothUnifiedViews() {
        val e=expense().withConsumptionType("NONESSENTIAL");val r=Entry("r","REFUND",200,"a",now.toString(),originalId=e.id)
        val s=ledger(e.copy(voided=true),r.copy(voided=true));Rules.validate(s)
        assertEquals(0L,s.usage(currentMonth()));assertEquals(0L,s.usage(currentMonth(),"FREE"))
        assertEquals(0L,ExpenseIndex(s).report(ReportRange.month(currentMonth())).total.net)
    }
}
