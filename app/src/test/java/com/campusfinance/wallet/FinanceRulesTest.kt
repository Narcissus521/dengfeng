package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinanceRulesTest {
    private val now=today()
    private val start=now.minusMonths(2).withDayOfMonth(1).toString()
    private val a=Account(id="a",name="微信",type="微信",opening=200000,start=start)
    private val b=Account(id="b",name="银行卡",type="银行卡",opening=50000,start=start)
    private fun ledger(vararg entries:Entry)=Ledger(accounts=listOf(a,b),entries=entries.toList())
    private fun entry(id:String="e",kind:String="EXPENSE",amount:Long=30000,date:String=now.toString(),bucket:String="FREE")=Entry(id=id,kind=kind,amount=amount,accountId="a",date=date,category="购物",bucket=bucket)
    private fun rejects(block:()->Unit){try{block();fail("Expected rejection")}catch(e:IllegalArgumentException){assertTrue(e.message?.isNotBlank()==true)}}
    @Test fun centsAreExact(){assertEquals(29L,Money.parse("0.29"));assertEquals(600000L,Money.parse("6000"));assertEquals("0.29",Money.plain(29))}
    @Test fun rejectsExcessPrecision(){rejects{Money.parse("1.001")}}
    @Test fun rejectsExponentAndNaN(){listOf("1e2","NaN","Infinity","1,000","","-1").forEach{rejects{Money.parse(it)}}}
    @Test fun supportsSignedOpening(){assertEquals(-123L,Money.parse("-1.23",true))}
    @Test fun rejectsOversizedInput(){rejects{Money.parse("1000000000.00")}}
    @Test fun openingIsNotIncome(){val s=ledger();Rules.validate(s);assertEquals(250000L,s.cash());assertEquals(0L,s.income(currentMonth()))}
    @Test fun incomeIsCountedOnceAcrossPlanningMonths(){val s=ledger(entry(kind="INCOME",amount=600000));assertEquals(850000L,s.cash());assertEquals(600000L,s.income(currentMonth()));assertEquals(0L,s.income(now.plusMonths(1).toString().take(7)))}
    @Test fun transferConservesCash(){val e=entry(kind="TRANSFER").copy(toAccountId="b");val s=ledger(e);Rules.validate(s);assertEquals(250000L,s.cash());assertEquals(170000L,s.balance("a"));assertEquals(80000L,s.balance("b"));assertEquals(0L,s.income(currentMonth()));assertEquals(0L,s.usage(currentMonth()))}
    @Test fun sameAccountTransferRejected(){rejects{Rules.validate(ledger(entry(kind="TRANSFER").copy(toAccountId="a")))}}
    @Test fun transferFeeOnlyCountsOnce(){val t=entry(id="t",kind="TRANSFER",amount=9800).copy(toAccountId="b");val fee=entry(id="f",amount=200).copy(transferId="t");val s=ledger(t,fee);Rules.validate(s);assertEquals(249800L,s.cash());assertEquals(200L,s.usage(currentMonth()))}
    @Test fun voidTransferKeepsFee(){val t=entry(id="t",kind="TRANSFER",amount=9800).copy(toAccountId="b",voided=true);val s=ledger(t,entry(id="f",amount=200).copy(transferId="t"));Rules.validate(s);assertEquals(249800L,s.cash())}
    @Test fun crossMonthRefundUsesArrivalMonth(){val oldDate=now.minusMonths(1).withDayOfMonth(1).toString();val e=entry(date=oldDate);val r=entry(id="r",kind="REFUND",amount=12000).copy(accountId="b",originalId="e");val s=ledger(e,r);Rules.validate(s);assertEquals(30000L,s.usage(oldDate.take(7)));assertEquals(-12000L,s.usage(currentMonth()));assertEquals(0L,s.income(currentMonth()));assertEquals(232000L,s.cash())}
    @Test fun multipleRefundsShareCap(){val s=ledger(entry(),entry(id="r1",kind="REFUND",amount=20000).copy(originalId="e"),entry(id="r2",kind="REFUND",amount=10001).copy(originalId="e"));rejects{Rules.validate(s)}}
    @Test fun fullMultipleRefundsAllowed(){val s=ledger(entry(),entry(id="r1",kind="REFUND",amount=20000).copy(originalId="e"),entry(id="r2",kind="REFUND",amount=10000).copy(originalId="e"));Rules.validate(s);assertEquals(250000L,s.cash());assertEquals(0L,s.usage(currentMonth()))}
    @Test fun refundMayGoToAnotherAccount(){val s=ledger(entry(),entry(id="r",kind="REFUND",amount=10000).copy(accountId="b",originalId="e"));Rules.validate(s);assertEquals(60000L,s.balance("b"))}
    @Test fun refundCannotPrecedePurchase(){rejects{Rules.validate(ledger(entry(),entry(id="r",kind="REFUND").copy(date=now.minusDays(1).toString(),originalId="e")))}}
    @Test fun cannotVoidPurchaseWithActiveRefund(){rejects{Rules.validate(ledger(entry().copy(voided=true),entry(id="r",kind="REFUND").copy(originalId="e")))}}
    @Test fun editCannotLowerPurchaseBelowRefunds(){val s=ledger(entry(),entry(id="r",kind="REFUND",amount=20000).copy(originalId="e"));rejects{Rules.replaceEntry(s,entry(amount=10000))}}
    @Test fun changingOriginalBucketRecomputesRefundMonth(){val s=ledger(entry(date=now.minusMonths(1).toString(),bucket="NECESSARY"),entry(id="r",kind="REFUND",amount=10000).copy(originalId="e"));assertEquals(-10000L,s.usage(currentMonth(),"NECESSARY"));assertEquals(0L,s.usage(currentMonth(),"FREE"))}
    @Test fun unallocatedDoesNotCoverCategoryOverrun(){val b=Budget(currentMonth(),150000,90000,50000);val s=ledger(entry(amount=55000)).copy(budgets=listOf(b));Rules.validate(s);assertEquals(-5000L,b.free-s.usage(currentMonth(),"FREE"));assertEquals(95000L,b.total-s.usage(currentMonth()));assertEquals(10000L,b.total-b.necessary-b.free)}
    @Test fun budgetsCannotOverallocate(){rejects{Rules.validate(ledger().copy(budgets=listOf(Budget(currentMonth(),10000,8000,3000))))}}
    @Test fun zeroBudgetDiffersFromNoBudget(){assertNull(ledger().budget(currentMonth()));val s=ledger().copy(budgets=listOf(Budget(currentMonth(),0,0,0)));Rules.validate(s);assertNotNull(s.budget(currentMonth()))}
    @Test fun budgetsAndIntentionsDoNotChangeCash(){val s=ledger().copy(budgets=listOf(Budget(currentMonth(),900000,500000,400000)),goals=listOf(Goal(name="电脑",target=600000,intention=80000,date=now.plusYears(1).toString())));Rules.validate(s);assertEquals(250000L,s.cash())}
    @Test fun fixedCalibrationDoesNotAbsorbHistoricalEdits(){val calibration=entry(id="c",kind="ADJUSTMENT",amount=-10000);val original=ledger(calibration);assertEquals(240000L,original.cash());val after=original.copy(entries=original.entries+entry(amount=10000,date=now.minusDays(1).toString()));Rules.validate(after);assertEquals(230000L,after.cash());assertEquals(10000L,after.usage(currentMonth()))}
    @Test fun negativeBalanceAllowed(){val s=ledger(entry(amount=300000));Rules.validate(s);assertEquals(-100000L,s.balance("a"))}
    @Test fun disabledAccountRetainsCash(){val s=ledger().copy(accounts=listOf(a.copy(disabledOn=now.toString()),b));Rules.validate(s);assertEquals(250000L,s.cash());rejects{Rules.replaceEntry(s,entry())}}
    @Test fun disabledAccountPermitsHistoricalCorrection(){val e=entry();val s=ledger(e).copy(accounts=listOf(a.copy(disabledOn=now.toString()),b));Rules.replaceEntry(s,e.copy(note="纠错"))}
    @Test fun disabledAccountPermitsRefund(){val s=ledger(entry()).copy(accounts=listOf(a.copy(disabledOn=now.toString()),b));Rules.replaceEntry(s,entry(id="r",kind="REFUND",amount=1000).copy(originalId="e"))}
    @Test fun futureTransactionRejected(){rejects{Rules.validate(ledger(entry(date=now.plusDays(1).toString())))}}
    @Test fun preBaselineTransactionRejected(){rejects{Rules.validate(ledger(entry(date=LocalDate.parse(start).minusDays(1).toString())))}}
    @Test fun voidEntriesDoNotChangeCashOrBudget(){val s=ledger(entry().copy(voided=true));assertEquals(250000L,s.cash());assertEquals(0L,s.usage(currentMonth()))}
    @Test fun repaymentAffectsCashOnceWithoutOrdinaryConsumption(){val d=Debt(id="d",name="手机",principal=30000,purchaseDate=start);val i=Installment(id="i",debtId="d",number=1,date=now.toString(),principal=30000,fee=1000);val pay=entry(kind="REPAYMENT",amount=31000).copy(installmentId="i");val s=ledger(pay).copy(debts=listOf(d),installments=listOf(i));Rules.validate(s);assertEquals(219000L,s.cash());assertEquals(0L,s.usage(currentMonth()));assertTrue(s.unpaid().isEmpty())}
    @Test fun duplicateRepaymentRejected(){val d=Debt(id="d",name="手机",principal=30000,purchaseDate=start);val i=Installment(id="i",debtId="d",number=1,date=now.toString(),principal=30000,fee=1000);rejects{Rules.validate(ledger(entry(kind="REPAYMENT",amount=31000).copy(installmentId="i"),entry(id="p2",kind="REPAYMENT",amount=31000).copy(installmentId="i")).copy(debts=listOf(d),installments=listOf(i)))}}
    @Test fun scheduleMustConserveContractPrincipal(){val d=Debt(id="d",name="手机",principal=30000,purchaseDate=start);rejects{Rules.validate(ledger().copy(debts=listOf(d),installments=listOf(Installment(debtId="d",number=1,date=now.toString(),principal=29999,fee=1000))))}}
    @Test fun demoMatchesExampleCash(){val s=demoLedger();Rules.validate(s);assertEquals(250000L,s.cash());assertEquals(60000L,s.usage(currentMonth()));assertEquals(600000L,s.income(currentMonth()))}
}
