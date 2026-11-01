package com.campusfinance.wallet

/** Presentation of existing budget buckets; never infer a historic bucket from necessity. */
fun ExpenseIndex.budgetNatureComparison(range:ReportRange,bucket:String):BudgetComparison {
    require(bucket in listOf(Bucket.NECESSARY.name,Bucket.FREE.name))
    val plans=ledger.budgets.filter{it.month in range.months}.associate{it.month to if(bucket==Bucket.NECESSARY.name)it.necessary else it.free}
    val originals=ledger.entries.associateBy{it.id}
    val selected=rows.filter{row->
        val source=if(row.entry.kind=="REFUND")originals[row.entry.originalId] ?: row.entry else row.entry
        range.contains(row.entry.date)&&source.bucket==bucket
    }
    return BudgetComparison(plans.values.takeIf{it.isNotEmpty()}?.exactSum(),selected.filter{it.entry.date.take(7) in plans}.map{it.signed}.exactSum(),plans.size,range.months.size,
        selected.filter{it.entry.date.take(7) !in plans}.map{it.signed}.exactSum())
}
