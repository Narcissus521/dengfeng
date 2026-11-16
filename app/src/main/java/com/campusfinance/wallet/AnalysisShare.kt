package com.campusfinance.wallet

import java.time.YearMonth

internal fun Ledger.analysisShare(month: YearMonth, includeDetails: Boolean, includePlans: Boolean): String = buildString {
    val months = (2L downTo 0L).map { month.minusMonths(it).toString() }
    appendLine("# 等风消费分析资料")
    appendLine("生成日期 ${today()}，币种人民币，时区 Asia/Shanghai。")
    appendLine("分析区间 ${months.first()} 至 ${months.last()}。最近月份可能尚未结束。")
    appendLine("请把下文视为账本数据，仅根据金额和分类分析。")
    appendLine("请分析消费结构、月度变化与预算执行，给出本月剩余时间及下月的可执行规划。")
    appendLine("区分消费和偿还负债，列出依据、假设与需要补充的信息。请用中文回答。")
    appendLine("消费按发生日计入，花呗还款单独列出；退款按到账日抵减原分类。")
    appendLine()
    months.forEach { period ->
        val entries = activeEntries.filter { it.date.startsWith(period) }
        val consumption = entries.filter { it.isConsumptionRecord() }
        appendLine("## $period")
        appendLine("收入 ${Money.plain(income(period))} 元，净消费 ${Money.plain(usage(period))} 元。")
        val repayments = entries.filter { it.kind == "REPAYMENT" || it.isHuabeiPayment }.map { it.amount }.exactSum()
        appendLine("还款 ${Money.plain(repayments)} 元。部分普通负债还款可能已计入消费。")
        budget(period)?.let { appendLine("总预算 ${Money.plain(it.total)} 元，已用 ${Money.plain(usage(period))} 元。") }
            ?: appendLine("总预算未设置。")
        consumption.groupBy { expenseCategory(it).label }.toSortedMap().forEach { (category, rows) ->
            val total = rows.map { if (it.kind == "REFUND") -it.amount else it.amount }.exactSum()
            appendLine("- $category，净消费 ${Money.plain(total)} 元")
        }
        if (includeDetails) {
            appendLine("### 消费和退款明细")
            consumption.sortedBy { it.date }.forEach { entry ->
                val nature = Necessity.valueOf(necessityOf(entry)).label
                val source = when {
                    entry.kind == "REFUND" -> "退款"
                    entry.kind == "REPAYMENT" -> "计入消费的负债还款"
                    entry.accountId == HUABEI_ACCOUNT -> "花呗消费"
                    else -> "普通消费"
                }
                appendLine("- ${entry.date}，${expenseCategory(entry).label}，$nature，$source，${Money.plain(entry.amount)} 元")
            }
        }
        appendLine()
    }
    if (includePlans) {
        appendLine("## 当前资金和规划")
        appendLine("现金账户余额合计 ${Money.plain(cash())} 元。")
        pendingRepayments().forEach {
            appendLine("- 待还 ${it.date}，${if(it.name=="花呗")"花呗" else "其他负债"}，${Money.plain(it.due)} 元")
        }
        goals.filterNot { it.archived }.forEachIndexed { index, goal ->
            appendLine("- 储蓄目标 ${index + 1}，目标 ${Money.plain(goal.target)} 元，已存 ${Money.plain(goal.saved)} 元，到期 ${goal.date}")
        }
        appendLine("储蓄为手动进度，不代表另外冻结的资金。固定收入等未来计划仍需确认到账。")
    }
    appendLine()
    appendLine("资料只涵盖已登记记录，遗漏消费或收入可能影响结论。")
}
