package com.campusfinance.wallet

/** Keep stored legacy pairs intact; only an explicit user selection unifies them. */
fun consumptionBucket(necessity:String):String=when(Necessity.valueOf(necessity)) {
    Necessity.NECESSARY->Bucket.NECESSARY.name
    Necessity.NONESSENTIAL->Bucket.FREE.name
    Necessity.UNDECIDED->Bucket.UNDECIDED.name
}
fun consumptionNecessity(bucket:String):String=when(Bucket.valueOf(bucket)) {
    Bucket.NECESSARY->Necessity.NECESSARY.name
    Bucket.FREE->Necessity.NONESSENTIAL.name
    Bucket.UNDECIDED->Necessity.UNDECIDED.name
}
fun consumptionPending(necessity:String,bucket:String)=consumptionBucket(necessity)!=bucket
fun Entry.withConsumptionType(value:String)=copy(necessity=value,bucket=consumptionBucket(value))
fun RecurringRule.withConsumptionType(value:String)=copy(necessity=value,bucket=consumptionBucket(value))
fun Budget.amountFor(bucket:String):Long?=when(Bucket.valueOf(bucket)) {
    Bucket.NECESSARY->necessary;Bucket.FREE->free;Bucket.UNDECIDED->null
}
fun Ledger.consumptionSource(e:Entry)=if(e.kind==Kind.REFUND.name)entries.first{it.id==e.originalId} else e
fun Ledger.pendingConsumption(month:String):List<Entry> {
    val affected=activeEntries.filter{it.date.startsWith(month)&&it.isConsumptionRecord()}.map{consumptionSource(it).id}.toSet()
    return activeEntries.filter{it.isConsumptionExpense()&&it.id in affected&&consumptionPending(it.necessity,it.bucket)}.sortedByDescending{it.date}
}
