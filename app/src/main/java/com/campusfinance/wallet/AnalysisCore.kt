package com.campusfinance.wallet

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class ReportRange(val start:LocalDate,val end:LocalDate) {
    init{require(start<=end){"开始日期不能晚于结束日期"}}
    val days get()=ChronoUnit.DAYS.between(start,end)+1
    val months get()=generateSequence(YearMonth.from(start)){it.plusMonths(1)}.takeWhile{it<=YearMonth.from(end)}.map{it.toString()}.toList()
    fun contains(date:String)=date>=start.toString()&&date<=end.toString()
    val key get()="$start|$end"
    companion object {
        fun month(month:String,now:LocalDate=today()):ReportRange {val m=YearMonth.parse(month);return ReportRange(m.atDay(1),minOf(m.atEndOfMonth(),now).coerceAtLeast(m.atDay(1)))}
        fun parse(key:String)=key.split('|').let{ReportRange(LocalDate.parse(it[0]),LocalDate.parse(it[1]))}
    }
}
data class ExpenseRow(val entry:Entry,val groupId:String,val leafId:String,val necessity:String,val signed:Long,val consumptionId:String)
data class ExpenseTotals(val gross:Long=0,val refunds:Long=0,val count:Int=0,val refundCount:Int=0) {
    val net get()=Math.subtractExact(gross,refunds)
    fun plus(row:ExpenseRow)=if(row.signed>0)copy(gross=Math.addExact(gross,row.signed),count=count+1)
        else copy(refunds=Math.addExact(refunds,-row.signed),refundCount=refundCount+1)
    fun chart(grossBasis:Boolean)=if(grossBasis)gross else net
}
fun expenseTotals(rows:List<ExpenseRow>):ExpenseTotals {
    val amounts=rows.fold(ExpenseTotals()){a,r->a.plus(r)}
    return amounts.copy(count=rows.filter{it.entry.kind=="EXPENSE"}.map{it.consumptionId}.distinct().size)
}
data class ExpenseReport(val range:ReportRange,val rows:List<ExpenseRow>,val total:ExpenseTotals,
    val groups:Map<String,ExpenseTotals>,val leaves:Map<String,ExpenseTotals>,val necessities:Map<String,ExpenseTotals>,val grossBasis:Boolean) {
    val dailyAverage get()=BigDecimal.valueOf(total.net).divide(BigDecimal.valueOf(range.days),0,RoundingMode.HALF_UP).longValueExact()
    val chartTotal get()=total.chart(grossBasis)
    fun share(value:ExpenseTotals,denominator:ExpenseTotals=total):Double?=denominator.chart(grossBasis).takeIf{it>0}?.let{value.chart(grossBasis)*100.0/it}
    fun slice(groupId:String?=null,leafId:String?=null,necessity:String?=null)=expenseTotals(rows.filter{(groupId==null||it.groupId==groupId)&&(leafId==null||it.leafId==leafId)&&(necessity==null||it.necessity==necessity)})
    fun necessities(groupId:String?=null,leafId:String?=null):Map<String,ExpenseTotals> = Necessity.entries.associate{n->n.name to slice(groupId,leafId,n.name)}
}
data class ReportFilter(val range:ReportRange,val groupId:String?=null,val leafId:String?=null,val necessity:String?=null,val consumptionId:String?=null) {
    fun accepts(row:ExpenseRow)=range.contains(row.entry.date)&&(groupId==null||row.groupId==groupId)&&(leafId==null||row.leafId==leafId)&&(necessity==null||row.necessity==necessity)&&(consumptionId==null||row.consumptionId==consumptionId)
    fun encode()=listOf(range.start,range.end,groupId ?: "",leafId ?: "",necessity ?: "",consumptionId ?: "").joinToString("|")
    companion object{fun decode(value:String)=value.split('|').let{ReportFilter(ReportRange(LocalDate.parse(it[0]),LocalDate.parse(it[1])),it.getOrNull(2)?.ifEmpty{null},it.getOrNull(3)?.ifEmpty{null},it.getOrNull(4)?.ifEmpty{null},it.getOrNull(5)?.ifEmpty{null})}}
}
/** One signed row source for charts, tables, trends and drill-downs. Refunds inherit current original metadata. */
class ExpenseIndex(val ledger:Ledger) {
    private val originals=ledger.entries.associateBy{it.id}
    val rows=ledger.activeEntries.filter{it.kind=="EXPENSE"||it.kind=="REFUND"}.map {e->
        val source=if(e.kind=="REFUND")originals[e.originalId] ?: e else e
        val category=ExpenseTaxonomy.resolve(source.categoryGroupId,source.subcategoryId,source.category)
        ExpenseRow(e,category.group.id,if(category.group.children.isEmpty())"${category.group.id}.direct" else category.leaf.id,source.necessity,if(e.kind=="REFUND")-e.amount else e.amount,source.recurringOccurrenceId ?: source.id)
    }
    fun report(range:ReportRange):ExpenseReport {
        val selected=rows.filter{range.contains(it.entry.date)}
        val total=expenseTotals(selected)
        val byGroup=selected.groupBy{it.groupId};val byLeaf=selected.groupBy{it.leafId};val byNecessity=selected.groupBy{it.necessity}
        val groups=ExpenseTaxonomy.groups.associate{it.id to expenseTotals(byGroup[it.id].orEmpty())}
        val leaves=ExpenseTaxonomy.analysisLeaves.associate{it.id to expenseTotals(byLeaf[it.id].orEmpty())}
        val necessary=Necessity.entries.associate{it.name to expenseTotals(byNecessity[it.name].orEmpty())}
        val gross=total.net<=0&&total.gross>0 || groups.values.any{it.net<=0&&it.gross>0} || leaves.values.any{it.net<0} || necessary.values.any{it.net<0}
        return ExpenseReport(range,selected,total,groups,leaves,necessary,gross)
    }
    fun records(filter:ReportFilter)=rows.filter(filter::accepts).map{it.entry}.sortedWith(compareByDescending<Entry>{it.date}.thenByDescending{it.id})
}
data class Growth(val delta:Long,val percent:Double?,val explanation:String)
fun compareSpending(current:Long,previous:Long,previousHasData:Boolean=true):Growth {
    val delta=Math.subtractExact(current,previous)
    return when {
        !previousHasData->Growth(delta,null,"上月没有消费记录，不计算增长率")
        previous==0L->Growth(delta,null,"上月净消费为零，不计算增长率")
        previous<0||current<0->Growth(delta,null,"存在净退款，不计算增长率")
        else->Growth(delta,delta*100.0/previous,"")
    }
}
data class TrendPoint(val month:String,val report:ExpenseReport,val amount:Long,val hasData:Boolean)
fun ExpenseIndex.trend(end:YearMonth,count:Int,groupId:String?=null,leafId:String?=null,now:LocalDate=today(),necessity:String?=null):List<TrendPoint> {
    require(count in 1..12)
    return (count-1 downTo 0).map{end.minusMonths(it.toLong())}.map {m->
        val report=report(ReportRange.month(m.toString(),now));val amount=when{necessity!=null->report.slice(groupId,leafId,necessity).net;leafId!=null->report.leaves[leafId]?.net ?: 0;groupId!=null->report.groups[groupId]?.net ?: 0;else->report.total.net}
        TrendPoint(m.toString(),report,amount,report.total.count>0||report.total.refundCount>0)
    }
}
data class BudgetComparison(val planned:Long?,val actual:Long,val coveredMonths:Int,val allMonths:Int,val unbudgetedActual:Long) {
    val remaining get()=planned?.let{Math.subtractExact(it,actual)}
    val rate get()=planned?.takeIf{it>0}?.let{actual*100.0/it}
}
fun ExpenseIndex.budgetComparison(range:ReportRange,groupId:String?=null):BudgetComparison {
    val plans=if(groupId==null)ledger.budgets.filter{it.month in range.months}.associate{it.month to it.total}
        else ledger.categoryBudgets.filter{it.month in range.months&&it.categoryGroupId==groupId}.associate{it.month to it.amount}
    val selected=rows.filter{range.contains(it.entry.date)&&(groupId==null||it.groupId==groupId)}
    val actual=selected.filter{it.entry.date.take(7) in plans}.map{it.signed}.exactSum()
    return BudgetComparison(plans.values.takeIf{it.isNotEmpty()}?.exactSum(),actual,plans.size,range.months.size,
        selected.filter{it.entry.date.take(7) !in plans}.map{it.signed}.exactSum())
}
data class AnomalyRules(val largeExpense:Long=50000,val minimumDifference:Long=10000) {
    init{require(largeExpense in 1..MAX_MONEY&&minimumDifference in 1..MAX_MONEY){"阈值须大于零"}}
}
data class SpendingAlert(val type:String,val title:String,val explanation:String,val filter:ReportFilter,val entryId:String?=null)
data class AlertResult(val alerts:List<SpendingAlert>,val baselineNote:String)
fun ExpenseIndex.alerts(month:YearMonth,rules:AnomalyRules,now:LocalDate=today()):AlertResult {
    val range=ReportRange.month(month.toString(),now);val current=report(range);val result=mutableListOf<SpendingAlert>()
    val refundedByConsumption=rows.filter{it.entry.kind=="REFUND"&&it.entry.date<=range.end.toString()}.groupBy{it.consumptionId}.mapValues{(_,v)->v.map{-it.signed}.exactSum()}
    current.rows.filter{it.entry.kind=="EXPENSE"}.groupBy{it.consumptionId}.forEach {(id,parts)->
        val remaining=parts.map{it.entry.amount}.exactSum()-(refundedByConsumption[id] ?: 0)
        if(remaining>=rules.largeExpense)result+=SpendingAlert("large","单笔支出较大","截至时段末未退款金额 ¥${Money.format(remaining)}，达到你设置的 ¥${Money.format(rules.largeExpense)} 阈值。组合扣款按同次消费合并。",ReportFilter(range,consumptionId=id),parts.singleOrNull()?.entry?.id)
    }
    ExpenseTaxonomy.groups.forEach{g->ledger.categoryBudgets.find{it.month==month.toString()&&it.categoryGroupId==g.id}?.let{cap->
        val actual=current.groups.getValue(g.id).net
        if(actual>cap.amount)result+=SpendingAlert("budget","${g.name}超过分类预算","净支出 ¥${Money.format(actual)}，分类预算 ¥${Money.format(cap.amount)}，超出 ¥${Money.format(actual-cap.amount)}。",ReportFilter(range,g.id))
    }}
    val earliest=ledger.accounts.minOfOrNull{it.start}
    val historical=(3 downTo 1).map{month.minusMonths(it.toLong())}.map{report(ReportRange(it.atDay(1),it.atEndOfMonth()))}
    val enough=historical.all{it.range.end<now&&earliest!=null&&earliest<=it.range.start.toString()&&it.total.count>0}
    val valid=enough&&historical.all{it.total.net>0&&it.groups.values.none{g->g.net<0}}
    if(valid&&current.total.net>=0)ExpenseTaxonomy.groups.forEach{g->
        val value=current.groups.getValue(g.id).net
        val sum=BigDecimal.valueOf(historical.map{it.groups.getValue(g.id).net}.exactSum())
        val scaledValue=BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(3))
        val gapEnough=scaledValue.subtract(sum)>=BigDecimal.valueOf(rules.minimumDifference).multiply(BigDecimal.valueOf(3))
        val mean=sum.divide(BigDecimal.valueOf(3),0,RoundingMode.HALF_UP).longValueExact()
        if(gapEnough && scaledValue.multiply(BigDecimal.valueOf(2))>sum.multiply(BigDecimal.valueOf(3)))result+=SpendingAlert("growth","${g.name}高于近期水平","本月净支出 ¥${Money.format(value)}；此前 3 个完整月平均 ¥${Money.format(mean)}。差额至少 ¥${Money.format(rules.minimumDifference)}，且高于均值 50%。",ReportFilter(range,g.id))
        val gross=current.grossBasis||historical.any{it.grossBasis}
        val denominator=current.total.chart(gross)
        val share=if(denominator>0)current.groups.getValue(g.id).chart(gross)*100.0/denominator else 0.0
        val average=historical.map{h->h.groups.getValue(g.id).chart(gross)*100.0/h.total.chart(gross)}.average()
        if(gapEnough&&share-average>=10.0)result+=SpendingAlert("structure","${g.name}占比提高","${if(gross)"消费原额" else "净消费"}占比较此前 3 个完整月平均提高 ${String.format(java.util.Locale.ROOT,"%.1f",share-average)} 个百分点，金额差额至少 ¥${Money.format(rules.minimumDifference)}。",ReportFilter(range,g.id))
    }
    return AlertResult(result,when{!enough->"需要此前连续 3 个完整月都有消费记录，才比较分类增长和结构。";!valid->"历史月份存在净退款或非正净消费，暂不作历史异常判断。";else->"与此前 3 个完整月比较；增长超过 50% 或占比提高至少 10 个百分点，同时须达到最低金额差。当前月截至今天，不按天放大预测。"})
}

data class HistoricalMean(val amount:Long,val months:Int)
fun trendHistoricalMean(points:List<TrendPoint>,ledger:Ledger,now:LocalDate=today()):HistoricalMean? {
    val first=ledger.accounts.minOfOrNull{it.start} ?: return null
    val full=points.filter{YearMonth.parse(it.month).atEndOfMonth()<now&&first<=YearMonth.parse(it.month).atDay(1).toString()}
    if(full.isEmpty()||full.none{it.hasData})return null
    return HistoricalMean(BigDecimal.valueOf(full.map{it.amount}.exactSum()).divide(BigDecimal.valueOf(full.size.toLong()),0,RoundingMode.HALF_UP).longValueExact(),full.size)
}
