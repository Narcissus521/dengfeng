package com.campusfinance.wallet

data class RecordQuery(val months:List<String>,val accountId:String="",val search:String="",val showVoided:Boolean=false,val category:String?=null,val groupId:String?=null,val necessity:String?=null)
data class RecordSummary(val income:Long,val expense:Long,val refunds:Long,val repayments:Long,val activeCount:Int,val voidedCount:Int,val consumptionRepayments:Long=0) {
    val net get()=Math.subtractExact(expense,refunds)
}
fun Ledger.bookRecords(query:RecordQuery):List<Entry> {
    val months=query.months.toSet();val keyword=query.search.trim()
    val consumptionOnly=query.groupId!=null||query.necessity!=null
    return entries.filter{e->
        e.date.take(7) in months&&(query.showVoided||!e.voided)&&
            (query.category==null||categoryOf(e)==query.category)&&
            (query.accountId.isEmpty()||e.accountId==query.accountId||e.kind=="TRANSFER"&&e.toAccountId==query.accountId)&&
            (!consumptionOnly||e.isConsumptionRecord())&&
            (query.groupId==null||expenseCategory(e).group.id==query.groupId)&&
            (query.necessity==null||necessityOf(e)==query.necessity)&&
            (keyword.isEmpty()||listOf(entrySearchTitle(e),e.note,accountName(e.accountId),e.toAccountId?.let{accountName(it)} ?: "",e.category).any{it.contains(keyword,true)})
    }.sortedWith(compareByDescending<Entry>{it.date}.thenByDescending{it.id})
}
private fun Ledger.entrySearchTitle(e:Entry)=when {
    e.isConsumptionRecord() -> expenseCategory(e).let{"${it.group.name} ${it.leaf.name}"}
    e.isHuabeiPayment -> "花呗还款"
    else -> Kind.valueOf(e.kind).label
}
fun recordSummary(rows:List<Entry>):RecordSummary {
    val active=rows.filterNot{it.voided}
    fun total(kind:String)=active.filter{it.kind==kind}.map{it.amount}.exactSum()
    val repayments=active.filter{it.kind=="REPAYMENT"||it.isHuabeiPayment}.map{it.amount}.exactSum()
    return RecordSummary(total("INCOME"),active.filter{it.isConsumptionExpense()}.map{it.amount}.exactSum(),total("REFUND"),repayments,active.size,rows.count{it.voided},active.filter{it.kind=="REPAYMENT"&&it.repaymentConsumption}.map{it.amount}.exactSum())
}
