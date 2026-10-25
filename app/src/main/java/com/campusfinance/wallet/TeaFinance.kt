package com.campusfinance.wallet

import java.time.LocalDate
import java.time.YearMonth

data class SpendingPeriod(val months: List<String>, val expenses: Long, val refunds: Long, val net: Long,
    val categories: Map<String,Long>, val buckets: Map<String,Long>, val chartUsesGross: Boolean,
    val budgetMonths: List<Budget>, val overMonths: List<Budget>, val necessities:Map<String,Long> = emptyMap()) {
    val overPercent get() = if(budgetMonths.isEmpty())null else overMonths.size*100.0/budgetMonths.size
    val chartTotal get() = categories.values.exactSum()
}

fun Ledger.spending(end: YearMonth, count: Int): SpendingPeriod {
    require(count in 1..12)
    val periods=(count-1 downTo 0).map { end.minusMonths(it.toLong()).toString() }.filter { it<=currentMonth() }
    if(periods.isEmpty())return SpendingPeriod(emptyList(),0,0,0,emptyMap(),emptyMap(),false,emptyList(),emptyList())
    val report=ExpenseIndex(this).report(ReportRange(YearMonth.parse(periods.first()).atDay(1),minOf(end.atEndOfMonth(),today())))
    val rows=report.rows
    val netBuckets=rows.groupBy{bucketOf(it.entry)}.mapValues{(_,v)->v.map{it.signed}.exactSum()}
    val gross=report.grossBasis||netBuckets.values.any{it<0}
    val bucketValues=if(gross)rows.filter{it.entry.kind=="EXPENSE"}.groupBy{bucketOf(it.entry)}.mapValues{(_,v)->v.map{it.signed}.exactSum()} else netBuckets
    val withBudget=budgets.filter { it.month in periods && it.month<=currentMonth() }
    return SpendingPeriod(periods,report.total.gross,report.total.refunds,report.total.net,
        ExpenseTaxonomy.groups.associate{it.name to report.groups.getValue(it.id).chart(gross)}.filterValues{it>0},
        bucketValues.filterValues{it>0},gross,withBudget,withBudget.filter { usage(it.month)>it.total },
        report.necessities.mapValues{it.value.chart(gross)}.filterValues{it>0})
}

fun Ledger.teaSummary(kind: String, month: YearMonth = YearMonth.parse(currentMonth())): String {
    val count=when(kind){"half"->if(month.monthValue<=6)month.monthValue else month.monthValue-6;"year"->month.monthValue;else->1}
    val stats=spending(month,count)
    val top=stats.categories.maxByOrNull { it.value }?.key
    val title=when(kind){"half"->"本半年";"year"->"今年";else->"本月"}
    val result=if(kind=="month") {
        val b=budget(month.toString())
        when {
            stats.expenses==0L && stats.refunds==0L -> "还没有消费记录。像每日一盏茶，慢慢养成记账习惯。"
            b==null -> "${title}净消费${Money.plain(stats.net)}元，${top ?: "其他"}最多，记得安排预算。"
            usage(month.toString())>b.total -> "${title}超预算${Money.plain(usage(month.toString())-b.total)}元，买前先留一点思考时间。"
            else -> "${title}净消费${Money.plain(stats.net)}元，预算剩余${Money.plain(b.total-usage(month.toString()))}元。"
        }
    } else if(stats.budgetMonths.isEmpty()) "${title}净消费${Money.plain(stats.net)}元。慢慢记录，为喜欢的生活留余地。"
    else "${title}有${stats.overMonths.size}个月超预算，${top ?: "其他"}花费最多。回看每一笔，找到刚好的分寸。"
    return result.take(50)
}

data class PurchaseInput(val name: String, val price: Long, val accountId: String, val bucket: String="FREE",
    val category: String="其他支出", val down: Long=0, val amounts: List<Long> = emptyList(),
    val firstDate: String=today().plusMonths(1).toString(),val categoryGroupId:String="",val subcategoryId:String="")
data class PurchaseReview(val cashAfter: Long, val accountAfter: Long, val budgetLeft: Long?, val bucketLeft: Long?,
    val savings: Long, val pendingDebt: Long, val payNow: Long, val quotedTotal: Long, val quoteExtra: Long,
    val installmentCashAfter: Long, val installmentAccountAfter: Long, val onceAdvice: String, val installmentAdvice: String)

fun Ledger.review(p: PurchaseInput, now: LocalDate = today()): PurchaseReview {
    require(p.name.isNotBlank() && p.name.length<=40 && p.price in 1..MAX_MONEY) { "请填写商品名称及有效价格" }
    require(accounts.any { it.id==p.accountId && it.disabledOn==null }) { "请选择启用中的付款账户" }
    require(p.bucket in Bucket.entries.map { it.name } && p.down in 0..p.price) { "首付或消费归属无效" }
    require(p.amounts.size<=120 && p.amounts.all { it in 1..MAX_MONEY }) { "请填写每期应付金额" }
    val first=LocalDate.parse(p.firstDate);if(p.amounts.isNotEmpty())require(first>=now) { "首期还款日不能在过去" }
    val quoted=if(p.amounts.isEmpty())p.price else Math.addExact(p.down,p.amounts.exactSum());require(quoted in p.price..MAX_MONEY) { "请核对首付、各期应付与商品价格" }
    val payNow=if(p.amounts.isEmpty())p.price else Math.addExact(p.down,if(first==now)p.amounts.first() else 0)
    val budget=budget(YearMonth.from(now).toString())
    val used=usage(YearMonth.from(now).toString())
    val bucketUsed=usage(YearMonth.from(now).toString(),p.bucket)
    val left=budget?.let { it.total-used-p.price }
    val bucketLeft=budget?.let { (if(p.bucket=="FREE")it.free else it.necessary)-bucketUsed-p.price }
    val cashAfter=cash()-p.price;val accountAfter=balance(p.accountId)-p.price
    val savings=goals.filterNot { it.archived }.map { it.saved }.exactSum()
    val pending=unpaid().filter { LocalDate.parse(it.date)<=now.plusDays(30) }.map { it.due }.exactSum()
    fun advice(cash: Long, account: Long): String = when {
        cash<0 || account<0 -> "建议调整付款安排"
        left!=null && left<0 -> "本月总预算将超出，建议暂缓"
        bucketLeft!=null && bucketLeft<0 -> "${Bucket.valueOf(p.bucket).label}将超预算，建议缓一缓"
        cash<pending -> "近期还款高于购买后现金，建议暂缓"
        cash<savings -> "可能影响储蓄安排，建议暂缓"
        budget==null -> "先安排本月预算，再考虑购买"
        else -> "在当前预算范围内，可以考虑"
    }
    val installmentCash=cash()-payNow;val installmentAccount=balance(p.accountId)-payNow
    var installmentAdvice=advice(installmentCash,installmentAccount)
    if(p.amounts.isEmpty())installmentAdvice="填写真实分期报价后再比较"
    if(installmentAdvice=="在当前预算范围内，可以考虑") installmentAdvice=if(quoted>p.price && accountAfter>=0 && cashAfter>=savings)"一次付总额更低，分期会增加未来还款" else "可比较分期，先确认后续收入能覆盖还款"
    return PurchaseReview(cashAfter,accountAfter,left,bucketLeft,savings,
        pending,payNow,quoted,quoted-p.price,
        installmentCash,installmentAccount,advice(cashAfter,accountAfter),installmentAdvice)
}
