package com.campusfinance.wallet

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.time.YearMonth

@Composable
internal fun AnalysisShareForm(s: Ledger, close: () -> Unit) {
    val context = LocalContext.current
    var month by rememberSaveable { mutableStateOf(currentMonth()) }
    var details by rememberSaveable { mutableStateOf(false) }
    var plans by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val today = today()
    val overview = remember(s.profile.revision, month, today) {
        AnalysisShareOverview(s, YearMonth.parse(month), today)
    }
    val report = remember(s.profile.revision, month, details, plans, today) {
        s.analysisShare(YearMonth.parse(month), details, plans, today)
    }
    Form("消费分析资料", close = close, confirm = "分享分析资料", submit = {
        val folder = File(context.cacheDir, "analysis-share").apply { mkdirs() }
        val file = File(folder, "dengfeng-analysis-${newId()}.txt").apply { writeText(report, Charsets.UTF_8) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.analysis-share", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "等风消费分析资料")
            putExtra(Intent.EXTRA_TEXT, "请分析附件中的消费资料，给出消费规划和建议。")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("消费分析资料", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "分享到 ChatGPT 或其他应用"))
            error = null
        } catch (_: ActivityNotFoundException) {
            error = "没有可接收资料的应用，请先安装 ChatGPT。"
        }
    }) {
        MonthPicker(month, { month = minOf(it, currentMonth()) })
        TeaCard(Modifier.listArrival(0), padding = 16.dp, spacing = 8.dp) {
            Text("同期净消费", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            HorizontalDivider(color = MaterialEdge)
            AmountLine("${overview.month.monthValue} 月 1–${overview.days} 日", overview.current, Forest)
            HorizontalDivider(color = MaterialEdge)
            AmountLine("${overview.previousMonth.monthValue} 月 1–${overview.days} 日", overview.previous, Forest)
            HorizontalDivider(color = MaterialEdge)
            Text(overview.changeLabel, fontSize = 13.sp, color = if (overview.delta > 0) Red else Ink)
        }
        val exceeded = overview.budgets.filter { it.remaining < 0 }.sortedBy { it.remaining }
        exceeded.firstOrNull()?.let { budget ->
            val remainingCategories = if (exceeded.size > 1) "，另有 ${exceeded.size-1} 类超支" else ""
            Notice("${budget.name}超出 ¥${Money.format(-budget.remaining)}$remainingCategories", warning = true)
        }
        TeaCard(Modifier.listArrival(1), padding = 16.dp, spacing = 8.dp) {
            Text("分类预算", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            if (overview.budgets.isEmpty()) Caption("所选月份未设置分类预算。")
            else {
                BudgetTableRow("分类", "已用 / 预算", "剩余", header = true)
                overview.budgets.forEachIndexed { index, budget ->
                    if (index > 0) HorizontalDivider(color = MaterialEdge)
                    val remaining = if (budget.remaining < 0) "超出 ¥${Money.format(-budget.remaining)}"
                        else "¥${Money.format(budget.remaining)}"
                    key(budget.name) {
                        BudgetTableRow(budget.name, "${Money.format(budget.spent)} / ${Money.format(budget.planned)}",
                            remaining, modifier = Modifier.listArrival(index+2), overBudget = budget.remaining < 0)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(details, role = Role.Checkbox, onValueChange = { details = it }),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(details, null); Text("包含逐笔消费和退款")
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(plans, role = Role.Checkbox, onValueChange = { plans = it }),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(plans, null); Text("包含当前余额、待还和储蓄规划")
        }
        Caption("分享资料覆盖所选月份及之前两个月。请在接收应用中确认发送。")
        error?.let { Notice(it, true) }
        TextButton(onClick = { showReport = !showReport }) { Text(if (showReport) "收起分享资料" else "预览完整分享资料") }
        if (showReport) {
            report.take(12000).chunked(400).forEach { Text(it) }
            if (report.length > 12000) Caption("预览显示前 12000 字，分享文件包含所选资料全文。")
        }
    }
}

@Composable
private fun BudgetTableRow(category: String, usage: String, remaining: String, modifier: Modifier = Modifier, header: Boolean = false, overBudget: Boolean = false) {
    val background = if (header) Modifier.background(Pale, RoundedCornerShape(5.dp)) else Modifier
    Row(modifier.fillMaxWidth().then(background).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(category, Modifier.weight(1f), fontSize = 13.sp, color = if (header) Muted else Ink)
        Text(usage, Modifier.weight(1.4f), fontSize = 13.sp, textAlign = TextAlign.End, color = if (header) Muted else Ink)
        Text(remaining, Modifier.weight(1.15f), fontSize = 13.sp, textAlign = TextAlign.End,
            color = if (header) Muted else if (overBudget) Red else Ink)
    }
}
