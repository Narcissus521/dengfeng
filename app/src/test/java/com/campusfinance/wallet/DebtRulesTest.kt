package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test

class DebtRulesTest {
    private val start=today().minusMonths(1).withDayOfMonth(1).toString()
    private val d=Debt(id="d",name="电脑",principal=0,purchaseDate=start,categoryId="debt-kind-shopping",scheduleType="CUSTOM")
    private val rows=manualDebtSchedule("d",today().toString(),listOf(50001L,60002L,70003L))
    private fun ledger(entries:List<Entry> = emptyList())=Ledger(accounts=listOf(Account("a","微信","微信",100000,start),Account("b","银行卡","银行卡",100000,start)),debts=listOf(d),installments=rows,entries=entries,debtCategories=defaultDebtCategories())
    private fun pay(id:String,account:String,amount:Long,batch:String="batch")=Entry(id=id,kind="REPAYMENT",amount=amount,accountId=account,date=today().toString(),installmentId=rows.first().id,repaymentId=batch)
    private fun rejects(block:()->Unit)=assertThrows(IllegalArgumentException::class.java,block)
    @Test fun monthlyDatesKeepOriginalDayAcrossShortMonths(){val r=manualDebtSchedule("d","2026-01-31",List(3){100L});assertEquals(listOf("2026-01-31","2026-02-28","2026-03-31"),r.map{it.date})}
    @Test fun leapYearHasFebruaryTwentyNine(){assertEquals("2028-02-29",manualDebtSchedule("d","2028-01-31",List(2){1L})[1].date)}
    @Test fun customAmountsKeepEveryCent(){val s=ledger();Rules.validate(s);assertEquals(180006L,s.installments.map{it.due}.exactSum());assertEquals(200000L,s.cash())}
    @Test fun acceptsShortAndLongTermsThroughThirtyYears(){for(n in listOf(1,120,121,240,360))assertEquals(n,manualDebtSchedule("d",today().toString(),List(n){1L}).size)}
    @Test fun rejectsEmptyAndOversizedPlans(){rejects{manualDebtSchedule("d",today().toString(),emptyList())};rejects{manualDebtSchedule("d",today().toString(),List(361){1L})}}
    @Test fun thirtyYearVariablePlanKeepsCentsMonthEndsAndExistingIds() {
        val values=List(360){300000L+it}
        val schedule=manualDebtSchedule("d","2026-01-31",values)
        val state=ledger().copy(debts=listOf(d.copy(purchaseDate="2026-01-01")),installments=schedule)
        Rules.validate(state)
        assertEquals("2055-12-31",schedule.last().date);assertEquals(360,schedule.last().number)
        assertEquals(values,schedule.map{it.due});assertEquals(values.exactSum(),schedule.map{it.due}.exactSum())
        assertEquals(schedule,manualDebtSchedule("d","2026-01-31",values,schedule))
        assertEquals(200000L,state.cash());assertEquals(0L,state.usage(currentMonth()))
    }
    @Test fun validatingAStoredPlanAlsoRejectsMoreThanThreeHundredSixtyPeriods() {
        val schedule=List(361){i->Installment(debtId="d",number=i+1,date=today().plusMonths(i.toLong()).toString(),principal=0,fee=0,scheduledAmount=100L)}
        rejects{Rules.validate(ledger().copy(installments=schedule))}
    }
    @Test fun rejectsZeroNegativeAndOversizedAmounts(){for(n in listOf(0L,-1L,MAX_MONEY+1))rejects{manualDebtSchedule("d",today().toString(),listOf(n))}}
    @Test fun rejectsOversizedTotal(){rejects{manualDebtSchedule("d",today().toString(),listOf(MAX_MONEY,1L))}}
    @Test fun changingCountKeepsStableIdsOfRetainedPeriods(){val next=manualDebtSchedule("d",today().toString(),listOf(1L,2L),rows);assertEquals(rows.take(2).map{it.id},next.map{it.id})}
    @Test fun equalModeRejectsUnequalAmounts(){rejects{Rules.validate(ledger().copy(debts=listOf(d.copy(scheduleType="EQUAL"))))}}
    @Test fun customPlanDoesNotInventPrincipalOrInterest(){val s=ledger();Rules.validate(s);assertEquals(0L,s.debts.single().principal);assertTrue(s.installments.all{it.principal==0L&&it.fee==0L})}
    @Test fun combinedPaymentDeductsEachAccountAndCountsOnlyOnePeriod(){val s=ledger(listOf(pay("p1","a",30000),pay("p2","b",20001)));Rules.validate(s);assertEquals(70000L,s.balance("a"));assertEquals(79999L,s.balance("b"));assertEquals(149999L,s.cash());assertEquals(0L,s.usage(currentMonth()));assertEquals(2,s.unpaid().size)}
    @Test fun incompleteAndExcessCombinedPaymentsRejected(){for(n in listOf(20000L,20002L))rejects{Rules.validate(ledger(listOf(pay("p1","a",30000),pay("p2","b",n))))}}
    @Test fun duplicateAccountInSamePaymentRejected(){rejects{Rules.validate(ledger(listOf(pay("p1","a",30000),pay("p2","a",20001))))}}
    @Test fun combinationRequiresSameBatch(){rejects{Rules.validate(ledger(listOf(pay("p1","a",30000),pay("p2","b",20001,"other"))))}}
    @Test fun cannotVoidOnlyOneMember(){rejects{Rules.validate(ledger(listOf(pay("p1","a",30000).copy(voided=true),pay("p2","b",20001))))}}
    @Test fun voidingWholeCombinationRestoresCashAndDebt(){val s=ledger(listOf(pay("p1","a",30000).copy(voided=true),pay("p2","b",20001).copy(voided=true)));Rules.validate(s);assertEquals(200000L,s.cash());assertEquals(3,s.unpaid().size)}
    @Test fun duplicateCategoryNamesRejected(){rejects{Rules.validate(ledger().copy(debtCategories=defaultDebtCategories()+DebtCategory(name="购物分期")))}}
    @Test fun manualDebtRequiresExistingCategory(){rejects{Rules.validate(ledger().copy(debts=listOf(d.copy(categoryId="missing"))))}}
    @Test fun oldSingleAccountHistoryCannotContainPartialPayment(){rejects{Rules.validate(ledger(listOf(pay("p1","a",10000).copy(repaymentId=null,voided=true))))}}
    @Test fun oldLongDebtNameRemainsReadable(){val old=Debt(id="old",name="旧".repeat(80),principal=10000,purchaseDate=start);Rules.validate(Ledger(debts=listOf(old),installments=listOf(Installment(debtId="old",number=1,date=today().toString(),principal=10000,fee=1000))))}
    @Test fun legacyPrincipalAndFeeLimitsArePreservedSeparately(){val old=Debt(id="old",name="旧计划",principal=MAX_MONEY,purchaseDate=start);val row=Installment(debtId="old",number=1,date=today().toString(),principal=MAX_MONEY,fee=MAX_MONEY);Rules.validate(Ledger(debts=listOf(old),installments=listOf(row)));assertEquals(MAX_MONEY*2,row.due)}
}
