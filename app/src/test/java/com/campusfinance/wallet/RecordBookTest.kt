package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test

class RecordBookTest {
    private val date=today().toString()
    private fun ledger(vararg entries:Entry)=Ledger(accounts=listOf(Account("a","银行卡","银行卡",1000000,"2025-01-01"),Account("b","微信零钱","微信",0,"2025-01-01")),entries=entries.toList())
    private fun e(id:String,kind:String,amount:Long)=Entry(id,kind,amount,"a",date,category="餐饮",note=id)
    @Test fun summaryExcludesTransfersPrincipalAndVoidsAndKeepsRefundSeparateFromIncome() {
        val expense=e("餐费","EXPENSE",3000)
        val s=ledger(expense,e("退款","REFUND",500).copy(originalId=expense.id),e("奖学金","INCOME",50000),e("转账","TRANSFER",10000).copy(toAccountId="b"),e("还款","REPAYMENT",8000),e("校准","ADJUSTMENT",900),e("作废消费","EXPENSE",1000).copy(voided=true))
        val rows=s.bookRecords(RecordQuery(listOf(currentMonth()),showVoided=true));val totals=recordSummary(rows)
        assertEquals(50000L,totals.income);assertEquals(3000L,totals.expense);assertEquals(500L,totals.refunds);assertEquals(2500L,totals.net);assertEquals(8000L,totals.repayments)
        assertEquals(6,totals.activeCount);assertEquals(1,totals.voidedCount)
        assertEquals(6,s.bookRecords(RecordQuery(listOf(currentMonth()))).size)
    }
    @Test fun accountAndSearchFindBothTransferSidesWithoutManufacturingAnIncome() {
        val transfer=e("充值零钱","TRANSFER",10000).copy(toAccountId="b")
        val s=ledger(transfer,e("外卖","EXPENSE",1000),e("工资","INCOME",10000))
        val query=RecordQuery(listOf(currentMonth()),accountId="b")
        assertEquals(listOf(transfer),s.bookRecords(query));assertEquals(0L,recordSummary(s.bookRecords(query)).income)
        assertEquals(listOf(transfer),s.bookRecords(query.copy(search="微信零钱")))
        assertEquals(listOf(transfer),s.bookRecords(query.copy(search="银行卡")))
        assertTrue(s.bookRecords(query.copy(groupId="food")).isEmpty())
    }
    @Test fun twoLevelSearchAndCrossMonthRefundUseCurrentOriginalCategory() {
        val original=e("电脑","EXPENSE",10000).copy(date=today().minusMonths(1).toString(),category="数码电子",categoryGroupId="digital",subcategoryId="digital.accessories")
        val refund=e("退配件","REFUND",2000).copy(originalId=original.id)
        val s=ledger(original,refund)
        val rows=s.bookRecords(RecordQuery(listOf(currentMonth()),search="数码配件"))
        assertEquals(listOf(refund),rows);assertEquals(-2000L,recordSummary(rows).net)
        val changed=s.copy(entries=listOf(original.copy(category="服饰鞋包",categoryGroupId="clothing",subcategoryId="clothing.clothes"),refund))
        assertTrue(changed.bookRecords(RecordQuery(listOf(currentMonth()),search="数码配件")).isEmpty())
        assertEquals(listOf(refund),changed.bookRecords(RecordQuery(listOf(currentMonth()),search="服装")))
    }
    @Test fun monthQueriesKeepManualAndAutomaticSnapshotsAndAnEmptyMonthHasNoTotals() {
        val manual=e("手动","EXPENSE",1000);val automatic=e("固定","EXPENSE",2000).copy(recurringOccurrenceId="p@$date")
        val old=manual.copy(id="上月",date=today().minusMonths(1).toString());val s=ledger(manual,automatic,old)
        assertEquals(setOf(manual,automatic),s.bookRecords(RecordQuery(listOf(currentMonth()))).toSet())
        val empty=s.bookRecords(RecordQuery(listOf("2020-01")));assertTrue(empty.isEmpty());assertEquals(0L,recordSummary(empty).net)
        assertEquals(listOf(old),s.bookRecords(RecordQuery(listOf(old.date.take(7)))))
    }
    @Test fun primaryCategoryAndNatureIntersectAndRefundsInheritBothFromTheirOriginal() {
        val necessary=e("食堂","EXPENSE",2000).withConsumptionType("NECESSARY")
        val nonessential=e("零食","EXPENSE",1000).withConsumptionType("NONESSENTIAL")
        val undecided=e("未判断","EXPENSE",300)
        val clothes=e("衣服","EXPENSE",700).copy(category="服饰鞋包",categoryGroupId="clothing",subcategoryId="clothing.clothes").withConsumptionType("NECESSARY")
        val refund=e("退餐费","REFUND",500).copy(originalId=necessary.id)
        val s=ledger(necessary,nonessential,undecided,clothes,refund,e("生活费","INCOME",6000),e("转账","TRANSFER",10000).copy(toAccountId="b"))
        val query=RecordQuery(listOf(currentMonth()),groupId="food",necessity="NECESSARY")
        val selected=s.bookRecords(query);assertEquals(setOf(necessary,refund),selected.toSet());assertEquals(1500L,recordSummary(selected).net)
        assertEquals(listOf(nonessential),s.bookRecords(query.copy(necessity="NONESSENTIAL")))
        assertEquals(listOf(undecided),s.bookRecords(query.copy(necessity="UNDECIDED")))
        assertEquals(2800L,recordSummary(s.bookRecords(query.copy(necessity=null))).net)
        val changed=s.copy(entries=s.entries.map{if(it.id==necessary.id)it.copy(category="服饰鞋包",categoryGroupId="clothing",subcategoryId="clothing.clothes").withConsumptionType("NONESSENTIAL") else it})
        assertTrue(changed.bookRecords(query).isEmpty())
        assertEquals(setOf(necessary.id,refund.id),changed.bookRecords(query.copy(groupId="clothing",necessity="NONESSENTIAL")).map{it.id}.toSet())
    }
    @Test fun unjudgedIsAnExplicitConsumptionFilterAndDoesNotInferLegacyBudgetOrIncludeOtherKinds() {
        val legacy=e("旧消费","EXPENSE",1234).copy(bucket="NECESSARY")
        val voided=e("已撤销","EXPENSE",500).copy(voided=true)
        val s=ledger(legacy,voided,e("收入","INCOME",2000),e("转账","TRANSFER",3000).copy(toAccountId="b"),e("校准","ADJUSTMENT",100),e("还款","REPAYMENT",700))
        val query=RecordQuery(listOf(currentMonth()),necessity="UNDECIDED")
        assertEquals(listOf(legacy),s.bookRecords(query));assertTrue(s.bookRecords(query.copy(necessity="NECESSARY")).isEmpty())
        assertEquals(setOf(legacy,voided),s.bookRecords(query.copy(showVoided=true)).toSet())
        assertEquals(1234L,recordSummary(s.bookRecords(query.copy(showVoided=true))).net)
        assertEquals(6,s.bookRecords(query.copy(necessity=null,showVoided=true)).size)
    }
}
