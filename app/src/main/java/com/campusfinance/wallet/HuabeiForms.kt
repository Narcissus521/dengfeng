package com.campusfinance.wallet

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun HuabeiBillLink(bill: HuabeiBill, open: (Editor) -> Unit) {
    val status = when {
        bill.remaining > 0 -> "待还 ¥${Money.format(bill.remaining)}"
        bill.payments.isNotEmpty() -> "已结清"
        else -> "已全额退款"
    }
    TeaLink("${bill.month} 花呗账单", "${bill.dueDate} · $status") {
        open(Editor("huabeiBill", bill.month))
    }
}

@Composable
internal fun HuabeiBillForm(s: Ledger, month: String, close: () -> Unit, open: (Editor) -> Unit) {
    val bill = s.huabeiBills().firstOrNull { it.month == month }
    Form("$month 花呗账单", close = close, confirm = null) {
        if (bill == null) {
            Caption("这个月没有花呗账单。")
        } else {
            AmountLine("账单金额", bill.amount)
            AmountLine("剩余待还", bill.remaining, Forest)
            Caption("还款日 ${bill.dueDate}")
            if (bill.remaining > 0) {
                OutlinedButton(onClick = { open(Editor("huabeiRepay", month)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("登记实际还款")
                }
                TextButton(onClick = { open(Editor("huabeiDueDate", month)) }) { Text("调整本期还款日") }
            } else {
                Caption(if (bill.payments.isEmpty()) "已全额退款" else "已结清")
            }
            (bill.purchases + bill.refunds + bill.payments).sortedByDescending { it.date }.forEach { entry ->
                EntryRow(entry, s) { open(Editor("detail", entry.id)) }
            }
        }
    }
}

@Composable
internal fun HuabeiRepayForm(s: Ledger, month: String, vm: WalletViewModel, busy: Boolean, close: () -> Unit) {
    val bill = s.huabeiBills().firstOrNull { it.month == month && it.remaining > 0 }
    var date by rememberSaveable { mutableStateOf(today().toString()) }
    var accountId by rememberSaveable { mutableStateOf(s.accounts.firstOrNull { it.disabledOn == null }?.id.orEmpty()) }
    val accounts = s.accounts.filter { date >= it.start && (it.disabledOn == null || date < it.disabledOn) }
    Form("登记花呗还款", busy, close, confirm = if (bill != null && accounts.isNotEmpty()) "确认还款记录" else null,
        submit = {
            require(accounts.any { it.id == accountId }) { "请选择实际扣款账户" }
            if (bill != null) vm.execute({ payHuabei(bill, accountId, date) }, close)
        }) {
        if (bill == null) {
            Caption("这期账单已经结清或已移除。")
        } else {
            AmountLine("$month 花呗待还", bill.remaining)
            Select("实际扣款账户", accounts, accounts.find { it.id == accountId }, { it.name }) { accountId = it.id }
            DateInput("实际还款日期 YYYY-MM-DD", date) { date = it }
            if (accounts.isEmpty()) Notice("请选择有效的还款日期，并先添加实际扣款的资金账户。", true)
            Caption("确认后整期结清，扣减所选账户的账本余额。消费已记在购买当天。")
        }
    }
}

@Composable
internal fun HuabeiDueDateForm(s: Ledger, month: String, vm: WalletViewModel, busy: Boolean, close: () -> Unit) {
    val bill = s.huabeiBills().firstOrNull { it.month == month && it.remaining > 0 }
    var date by rememberSaveable { mutableStateOf(bill?.dueDate.orEmpty()) }
    Form("调整本期还款日", busy, close, confirm = if (bill == null) null else "保存", submit = {
        if (bill != null) vm.execute({ updateHuabeiDueDate(bill, date) }, close)
    }) {
        DateInput("还款日 YYYY-MM-DD", date) { date = it }
        Caption("选择 $month 内的日期。这期花呗账单统一使用该还款日。")
    }
}

@Composable
internal fun HuabeiSettingsForm(close: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("wallet_settings", Context.MODE_PRIVATE) }
    var day by rememberSaveable { mutableStateOf(prefs.getInt(HUABEI_DAY_SETTING, DEFAULT_HUABEI_DAY).toString()) }
    Form("花呗设置", close = close, submit = {
        val value = day.trim().toIntOrNull()
        require(value != null && value in 1..31) { "每月还款日须为 1–31 日" }
        prefs.edit().putInt(HUABEI_DAY_SETTING, value).apply()
        close()
    }) {
        Field("默认每月还款日（1–31）", day, { day = it }, money = true)
        Caption("消费默认计入次月账单，遇到短月按月末计算。已生成的账单可在「规划 → 负债」单独调整。")
    }
}
