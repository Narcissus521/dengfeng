package com.campusfinance.wallet

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
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
    var error by remember { mutableStateOf<String?>(null) }
    val report = remember(s.profile.revision, month, details, plans) {
        s.analysisShare(YearMonth.parse(month), details, plans)
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
        Caption("生成截至所选月份的近三个月资料。分享后在接收应用里确认发送，分析与建议会在聊天中返回。")
        Row(Modifier.fillMaxWidth().toggleable(details, role = Role.Checkbox, onValueChange = { details = it }),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(details, null); Text("包含逐笔消费和退款")
        }
        Row(Modifier.fillMaxWidth().toggleable(plans, role = Role.Checkbox, onValueChange = { plans = it }),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(plans, null); Text("包含当前余额、待还和储蓄规划")
        }
        Caption("默认包含月度金额、分类及预算。账户名称、备注、内部编号和修改历史会省略。")
        error?.let { Notice(it, true) }
        Text("分享内容预览")
        report.take(12000).chunked(400).forEach { Text(it) }
        if (report.length > 12000) Caption("预览显示前 12000 字，分享文件包含所选资料全文。")
    }
}
