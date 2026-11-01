package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test

class RepaymentConsumptionTest {
    private val date=today().toString()
    private val debt=Debt(id="phone",name="三星手机",principal=0,purchaseDate=date,categoryId="debt-kind-shopping",scheduleType="EQUAL",
        repaymentsAreConsumption=true,categoryGroupId="digital",subcategoryId="digital.devices",necessity="NECESSARY")
    private val rows=manualDebtSchedule(debt.id,date,List(12){50000L})
    private fun ledger()=Ledger(accounts=listOf(Account("a","银行卡","银行卡",1000000,date),Account("b","零钱","现金",200000,date)),
        debts=listOf(debt),installments=rows,debtCategories=defaultDebtCategories(),budgets=listOf(Budget(currentMonth(),200000,150000,50000)))
    private fun paid(s:Ledger,enabled:Boolean=true):Ledger {
        val d=debt.copy(repaymentsAreConsumption=enabled)
        return s.copy(entries=listOf("a" to 30000L,"b" to 20000L).map{(account,amount)->
            Entry(kind="REPAYMENT",amount=amount,accountId=account,date=date,installmentId=rows.first().id,repaymentId="payment").withDebtConsumption(d)
        })
    }
    @Test fun registeringSixThousandDebtDoesNotPostCashOrConsumption() {
        val s=ledger();Rules.validate(s)
        assertEquals(1200000L,s.cash());assertEquals(600000L,s.unpaid().map{it.due}.exactSum());assertEquals(0L,s.usage(currentMonth()))
    }
    @Test fun oneFiveHundredPaymentHasTwoAttributesButCountsOnlyOnceIncludingSplitAccounts() {
        val before=ledger();val s=paid(before);Rules.validate(s)
        assertEquals(before.cash()-50000,s.cash());assertEquals(550000L,s.unpaid().map{it.due}.exactSum())
        assertEquals(50000L,s.usage(currentMonth()));assertEquals(50000L,s.usage(currentMonth(),"NECESSARY"))
        val report=ExpenseIndex(s).report(ReportRange.month(currentMonth()))
        assertEquals(50000L,report.total.net);assertEquals(1,report.total.count);assertEquals(50000L,report.leaves.getValue("digital.devices").net)
        assertEquals(report.total.net,report.groups.values.map{it.net}.exactSum())
        assertEquals(2,s.bookRecords(RecordQuery(listOf(currentMonth()),groupId="digital")).size)
        assertEquals(50000L,recordSummary(s.entries).net);assertEquals(50000L,recordSummary(s.entries).consumptionRepayments)
    }
    @Test fun disabledSwitchKeepsRepaymentPurposeButDoesNotRepeatAnAlreadyRecordedPurchase() {
        val s=paid(ledger(),false);Rules.validate(s)
        assertEquals(0L,s.usage(currentMonth()));assertEquals(0L,ExpenseIndex(s).report(ReportRange.month(currentMonth())).total.net)
        assertTrue(s.entries.all{it.categoryGroupId=="digital"&&it.kind=="REPAYMENT"});assertEquals(1150000L,s.cash())
    }
    @Test fun voidedSplitPaymentRestoresCashBudgetAndDebtTogether() {
        val s=paid(ledger()).let{it.copy(entries=it.entries.map{e->e.copy(voided=true)})};Rules.validate(s)
        assertEquals(1200000L,s.cash());assertEquals(0L,s.usage(currentMonth()));assertEquals(600000L,s.unpaid().map{it.due}.exactSum())
    }
    @Test fun integerProportionsHandleRemaindersTinyAmountsAndLargeAmountsExactly() {
        assertEquals(listOf("a" to 2L,"b" to 2L,"c" to 1L),proportionalAllocation(5,listOf("a" to 1L,"b" to 1L,"c" to 1L)))
        assertEquals(listOf("a" to 1L),proportionalAllocation(1,listOf("a" to 1L,"b" to 1L)))
        val large=proportionalAllocation(MAX_MONEY,listOf("a" to MAX_MONEY-1,"b" to 1L));assertEquals(MAX_MONEY,large.map{it.second}.exactSum())
    }
}
