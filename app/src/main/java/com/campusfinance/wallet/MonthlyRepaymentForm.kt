package com.campusfinance.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun MonthlyRepaymentForm(s: Ledger, month: YearMonth, close: () -> Unit, open: (Editor) -> Unit) {
    val period = month.toString()
    val installments = s.unpaid().filter { it.date.startsWith(period) }
    val bill = s.huabeiBills().firstOrNull { it.month == period }
    val total = Math.addExact(installments.map { it.due }.exactSum(), bill?.remaining ?: 0L)
    Form("${month.monthValue} 月待还", close = close, confirm = null) {
        TeaCard(padding = 16.dp) {
            Text("待还总额", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
            GridMoney(total, Modifier.fillMaxWidth(), font = 36, color = Forest)
            if (total == 0L) Caption("这个月没有待还款项。")
        }
        if (bill != null) TeaCard(onClick = { open(Editor("huabeiBill", period)) }, padding = 16.dp, spacing = 8.dp) {
            val due = LocalDate.parse(bill.dueDate)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("花呗", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Caption("${due.monthValue} 月 ${due.dayOfMonth} 日")
            }
            HorizontalDivider(color = MaterialEdge)
            AmountLine("账单金额", bill.amount)
            HorizontalDivider(color = MaterialEdge)
            AmountLine("已还", bill.paid)
            HorizontalDivider(color = MaterialEdge)
            AmountLine("剩余待还", bill.remaining, Forest)
        }
        if (installments.isNotEmpty()) TeaCard(padding = 16.dp) {
            Text("其他分期", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            installments.forEachIndexed { index, row ->
                val name = s.debts.first { it.id == row.debtId }.name
                val due = LocalDate.parse(row.date)
                if (index > 0) HorizontalDivider(color = MaterialEdge)
                Row(Modifier.fillMaxWidth().clickable { open(Editor("debtDetail", row.debtId)) }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 14.sp, color = Ink)
                        Caption("第 ${row.number} 期")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Caption("${due.monthValue} 月 ${due.dayOfMonth} 日")
                        GridMoney(row.due, Modifier.fillMaxWidth(), font = 18, color = Forest,
                            align = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
        TeaLink("查看全部还款计划") { open(Editor("debtList")) }
    }
}
