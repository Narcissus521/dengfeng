package com.campusfinance.wallet

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
    Form("分享到 ChatGPT 分析", close = close, confirm = "选择分享应用", submit = {
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
        TeaCard {
            Text("同期净消费")
            AmountLine("${overview.month} 1–${overview.days} 日", overview.current)
            AmountLine("${overview.previousMonth} 1–${overview.days} 日", overview.previous)
            Caption(overview.changeLabel)
        }
        TeaCard {
            Text("分类预算")
            if (overview.budgets.isEmpty()) Caption("所选月份未设置分类预算。")
            overview.budgets.forEach { budget ->
                Caption("${budget.name} · 已用 ¥${Money.format(budget.spent)} / 预算 ¥${Money.format(budget.planned)}")
                if (budget.remaining < 0) AmountLine("超出", -budget.remaining, Red)
                else AmountLine("剩余", budget.remaining)
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
