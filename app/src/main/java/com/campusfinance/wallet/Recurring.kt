package com.campusfinance.wallet

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

val financeZone: ZoneId = ZoneId.of("Asia/Shanghai")

@Entity(tableName = "recurring_rules")
data class RecurringRule(@PrimaryKey val id: String = newId(), val name: String, val kind: String,
    val amount: Long, val frequency: String = "MONTHLY", val day: Int = 1, val time: String = "08:00",
    val startDate: String = today().toString(), val category: String = if(kind=="INCOME")incomeCategories.first() else expenseCategories.first(), val bucket: String = "NECESSARY",
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue="''") val categoryGroupId:String="",
    @ColumnInfo(defaultValue="''") val subcategoryId:String="",
    @ColumnInfo(defaultValue="'UNDECIDED'") val necessity:String=Necessity.UNDECIDED.name) {
    fun dateIn(month: YearMonth): LocalDate = month.atDay(minOf(day, month.lengthOfMonth()))
    fun dueOn(date: LocalDate): Boolean = date.toString() >= startDate &&
        (frequency == "DAILY" || date == dateIn(YearMonth.from(date)))
    fun nextAfter(now: ZonedDateTime): ZonedDateTime {
        val local = now.withZoneSameInstant(financeZone)
        var date = maxOf(local.toLocalDate(), LocalDate.parse(startDate))
        if (frequency == "MONTHLY") {
            var month = YearMonth.from(date)
            var candidate = dateIn(month)
            if (candidate < date || candidate.atTime(LocalTime.parse(time)).atZone(financeZone) <= local) {
                month = month.plusMonths(1); candidate = dateIn(month)
            }
            date = candidate
        } else if (date.atTime(LocalTime.parse(time)).atZone(financeZone) <= local) date = date.plusDays(1)
        return date.atTime(LocalTime.parse(time)).atZone(financeZone)
    }
}
@Entity(tableName = "recurring_allocations", primaryKeys = ["ruleId", "accountId"])
data class RecurringAllocation(val ruleId: String, val accountId: String, val amount: Long)
@Entity(tableName = "recurring_occurrences")
data class RecurringOccurrence(@PrimaryKey val id: String, val ruleId: String, val date: String,
    val amount: Long, val kind: String, val status: String = "POSTED", val reason: String = "")
@Entity(tableName = "reminder_settings")
data class ReminderSettings(@PrimaryKey val id: Int = 1, val enabled: Boolean = false,
    val startTime: String = "20:00", val endTime: String = "22:00", val categories: String = "餐饮食品,生活日用,服饰鞋包,学习教育,交通出行,休闲娱乐,其他支出") {
    fun selectedCategories() = categories.split(',').filter { it.isNotBlank() }.toSet()
    fun selectedGroupIds()=selectedCategories().mapNotNull{ExpenseTaxonomy.reminderGroupId(it)}.toSet()
}

/** An occurrence, including one explicitly undone or skipped, is never posted a second time. */
fun pendingRecurring(s: Ledger, now: ZonedDateTime, limit: Int = 400): List<Pair<RecurringRule, LocalDate>> {
    val local = now.withZoneSameInstant(financeZone)
    val done = s.recurringOccurrences.map { it.id }.toHashSet()
    val pending = mutableListOf<Pair<RecurringRule, LocalDate>>()
    s.recurringRules.filter { it.enabled }.forEach { rule ->
        var date = LocalDate.parse(rule.startDate)
        if (rule.frequency == "MONTHLY") {
            date = rule.dateIn(YearMonth.from(date))
            if (date.toString() < rule.startDate) date = rule.dateIn(YearMonth.from(date).plusMonths(1))
        }
        while (date <= local.toLocalDate() && pending.size < limit) {
            if (date.atTime(LocalTime.parse(rule.time)).atZone(financeZone) <= local && "${rule.id}@$date" !in done)
                pending += rule to date
            date = if (rule.frequency == "DAILY") date.plusDays(1) else rule.dateIn(YearMonth.from(date).plusMonths(1))
        }
    }
    return pending.sortedBy { (_, date) -> date }
}

fun validateRecurring(s: Ledger, now: LocalDate) {
    require(s.recurringRules.map { it.id }.distinct().size == s.recurringRules.size) { "固定计划编号重复" }
    s.recurringRules.forEach { r ->
        require(r.id.isNotBlank() && '@' !in r.id && r.name.isNotBlank() && r.name.length <= 40) { "固定计划名称须为 1–40 个字" }
        require(r.kind in listOf("EXPENSE", "INCOME") && r.frequency in listOf("DAILY", "MONTHLY")) { "固定记账类型无效" }
        require(r.amount in 1..MAX_MONEY && r.day in 1..31) { "固定金额须大于零，日期须为 1–31 日" }
        require(LocalDate.parse(r.startDate).toString() == r.startDate && r.startDate >= "2000-01-01") { "开始日期无效" }
        require(Regex("\\d{2}:\\d{2}").matches(r.time)) { "时间格式应为 HH:mm" }; LocalTime.parse(r.time)
        require(r.category.isNotBlank() && r.category.length <= 40 && r.bucket in Bucket.entries.map { it.name }) { "请选择消费种类与归属" }
        ExpenseTaxonomy.validate(r.categoryGroupId,r.subcategoryId,r.necessity)
        require(r.kind=="EXPENSE" || r.categoryGroupId.isEmpty()&&r.subcategoryId.isEmpty()&&r.necessity==Necessity.UNDECIDED.name){"固定收入不设置消费必要性"}
        val splits = s.recurringAllocations.filter { it.ruleId == r.id }
        require(splits.isNotEmpty() && splits.map { it.accountId }.distinct().size == splits.size) { "请选择不同的资金账户" }
        require(r.kind != "INCOME" || splits.size == 1) { "固定收入请分别指定到账账户" }
        require(splits.all { a -> a.amount in 1..MAX_MONEY && s.accounts.any { it.id == a.accountId } } && splits.map { it.amount }.exactSum() == r.amount) { "账户分摊合计须等于每次金额" }
    }
    require(s.recurringAllocations.all { a -> s.recurringRules.any { it.id == a.ruleId } }) { "账户分摊缺少固定计划" }
    require(s.recurringOccurrences.map { it.id }.distinct().size == s.recurringOccurrences.size) { "固定记账期次重复" }
    s.recurringOccurrences.forEach { o ->
        require(s.recurringRules.any { it.id == o.ruleId } && o.id == "${o.ruleId}@${o.date}" && LocalDate.parse(o.date) <= now) { "固定记账期次无效" }
        require(o.status in listOf("POSTED", "UNDONE", "SKIPPED") && o.amount in 1..MAX_MONEY && o.kind in listOf("EXPENSE", "INCOME")) { "固定记账状态无效" }
        val entries = s.entries.filter { it.recurringOccurrenceId == o.id }
        if (o.status == "SKIPPED") require(entries.isEmpty()) { "未记账期次不能包含扣款" }
        else {
            require(entries.isNotEmpty() && entries.all { it.kind == o.kind && it.date == o.date && it.voided == (o.status == "UNDONE") }) { "固定记账须整笔保留或撤销" }
            require(entries.map { it.accountId }.distinct().size == entries.size && entries.map { it.amount }.exactSum() == o.amount) { "固定记账的账户合计不一致" }
        }
    }
    require(s.entries.all { e -> e.recurringOccurrenceId == null || s.recurringOccurrences.any { it.id == e.recurringOccurrenceId } }) { "固定交易缺少对应期次" }
    val reminder = s.reminder
    require(reminder.id == 1 && Regex("\\d{2}:\\d{2}").matches(reminder.startTime) && Regex("\\d{2}:\\d{2}").matches(reminder.endTime)) { "提醒时间无效" }
    LocalTime.parse(reminder.startTime); LocalTime.parse(reminder.endTime)
    require(!reminder.enabled || reminder.selectedCategories().isNotEmpty()) { "请至少选择一种提醒消费" }
    // Keep previously saved reminders and backups readable after retiring this picker option.
    require(reminder.selectedCategories().all { ExpenseTaxonomy.reminderGroupId(it)!=null }) { "提醒消费种类无效" }
}
