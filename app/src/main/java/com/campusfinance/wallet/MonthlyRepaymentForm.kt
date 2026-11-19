package com.campusfinance.wallet

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.time.YearMonth

@Composable
internal fun MonthlyRepaymentForm(s: Ledger, month: YearMonth, close: () -> Unit, open: (Editor) -> Unit) {
    val period = month.toString()
    val installments = s.unpaid().filter { it.date.startsWith(period) }
    val bill = s.huabeiBills().firstOrNull { it.month == period }
    val total = Math.addExact(installments.map { it.due }.exactSum(), bill?.remaining ?: 0L)
    Form("${month.year} 年 ${month.monthValue} 月待还", close = close, confirm = null) {
        TeaCard {
            AmountLine("待还总额", total, Forest)
            if (total == 0L) Caption("这个月没有待还款项。")
        }
        if (bill != null) TeaCard(onClick = { open(Editor("huabeiBill", period)) }) {
            Text("花呗")
            Caption("还款日 ${bill.dueDate}")
            AmountLine("账单金额", bill.amount)
            AmountLine("已还", bill.paid)
            AmountLine("剩余待还", bill.remaining, Forest)
        }
        if (installments.isNotEmpty()) TeaCard {
            Text("其他分期")
            installments.forEach { row ->
                val name = s.debts.first { it.id == row.debtId }.name
                TeaLink(name, "${row.date} · 第 ${row.number} 期 · ¥${Money.format(row.due)}") {
                    open(Editor("debtDetail", row.debtId))
                }
            }
        }
        TeaLink("查看全部还款计划") { open(Editor("debtList")) }
    }
}
