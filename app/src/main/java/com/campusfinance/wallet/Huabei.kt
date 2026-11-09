package com.campusfinance.wallet

import java.time.LocalDate
import java.time.YearMonth

internal const val HUABEI_ACCOUNT = "system:huabei"
internal const val DEFAULT_HUABEI_DAY = 10
internal const val HUABEI_DAY_SETTING = "huabei_repayment_day"

internal val Entry.isHuabeiPayment get() = kind == "TRANSFER" && toAccountId == HUABEI_ACCOUNT

internal data class HuabeiBill(
    val dueDate: String,
    val purchases: List<Entry>,
    val refunds: List<Entry>,
    val payments: List<Entry>
) {
    val month get() = dueDate.take(7)
    val amount get() = purchases.map { it.amount }.exactSum() - refunds.map { it.amount }.exactSum()
    val remaining get() = amount - payments.map { it.amount }.exactSum()
}

internal fun Ledger.huabeiBills(): List<HuabeiBill> {
    val active = activeEntries
    val purchases = active.filter { it.kind == "EXPENSE" && it.accountId == HUABEI_ACCOUNT }
    if (purchases.isEmpty()) return emptyList()
    val refunds = active.filter { it.kind == "REFUND" && it.accountId == HUABEI_ACCOUNT }
        .groupBy { it.originalId }
    val payments = active.filter { it.isHuabeiPayment }.groupBy { it.huabeiDueDate?.take(7) }
    return purchases.groupBy { checkNotNull(it.huabeiDueDate) }.map { (dueDate, rows) ->
        HuabeiBill(dueDate, rows, rows.flatMap { refunds[it.id].orEmpty() }, payments[dueDate.take(7)].orEmpty())
    }.sortedBy { it.dueDate }
}

internal fun Ledger.huabeiDueDate(purchase: LocalDate, day: Int): String {
    require(day in 1..31) { "每月还款日须为 1–31 日" }
    val month = YearMonth.from(purchase).plusMonths(1)
    return huabeiBills().firstOrNull { it.month == month.toString() }?.dueDate
        ?: month.atDay(minOf(day, month.lengthOfMonth())).toString()
}

internal fun Ledger.validateHuabei() {
    val bills = huabeiBills()
    require(bills.map { it.month }.distinct().size == bills.size) { "同月花呗账单须使用同一个还款日" }
    require(activeEntries.filter { it.isHuabeiPayment }.all { payment ->
        bills.any { it.dueDate == payment.huabeiDueDate }
    }) { "还款须关联有效花呗账单，请先撤销还款再修改原消费" }
    bills.forEach { bill ->
        require(bill.amount in 0..MAX_MONEY) { "花呗账单金额超出范围" }
        require(bill.payments.size <= 1) { "这期花呗已经登记还款" }
        bill.payments.singleOrNull()?.let { payment ->
            require(payment.amount == bill.amount &&
                (bill.purchases + bill.refunds).all { it.date <= payment.date }
            ) { "这期花呗已结清，请先撤销还款再调整账单。退款请按实际到账账户登记" }
        }
    }
}

internal data class PendingRepayment(val date: String, val due: Long, val name: String, val number: Int? = null)

internal fun Ledger.pendingRepayments(): List<PendingRepayment> {
    val names = debts.associate { it.id to it.name }
    val installments = unpaid().map { PendingRepayment(it.date, it.due, names.getValue(it.debtId), it.number) }
    val huabei = huabeiBills().filter { it.remaining > 0 }
        .map { PendingRepayment(it.dueDate, it.remaining, "花呗") }
    return (installments + huabei).sortedBy { it.date }
}
