package com.campusfinance.wallet

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
fun today(): LocalDate = LocalDate.now(ZoneId.of("Asia/Shanghai"))
fun currentMonth(): String = YearMonth.from(today()).toString()
const val MAX_MONEY = 99_999_999_999L
const val MAX_DEBT_INSTALLMENTS = 360
object Money {
    fun parse(text: String, signed: Boolean = false): Long {
        val s = text.trim()
        require((if (signed) Regex("-?\\d{1,9}(\\.\\d{1,2})?") else Regex("\\d{1,9}(\\.\\d{1,2})?")).matches(s)) { "请输入有效金额，最多两位小数" }
        return BigDecimal(s).movePointRight(2).longValueExact().also { require(it in -MAX_MONEY..MAX_MONEY) { "金额超出支持范围" } }
    }
    fun plain(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
    fun format(cents: Long): String = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.CHINA)).format(BigDecimal.valueOf(cents, 2))
}
fun Iterable<Long>.exactSum(): Long = fold(0L, Math::addExact)
enum class Kind(val label: String) { EXPENSE("支出"), INCOME("收入"), TRANSFER("转账"), REFUND("退款"), ADJUSTMENT("余额校准"), REPAYMENT("还款") }
enum class Bucket(val label: String) { NECESSARY("必要消费"), FREE("自由消费") }
val expenseCategories = ExpenseTaxonomy.groups.map{it.name}
val incomeCategories = listOf("父母生活费", "兼职工资", "奖学金", "红包", "其他收入")

@Entity(tableName = "profiles")
data class Profile(@PrimaryKey val id: Int = 1, val name: String = "我的校园账本", val revision: Long = 0, val demo: Boolean = false)
@Entity(tableName = "accounts")
data class Account(@PrimaryKey val id: String = newId(), val name: String, val type: String,
    val opening: Long, val start: String, val disabledOn: String? = null)
@Entity(tableName = "entries")
data class Entry(@PrimaryKey val id: String = newId(), val kind: String, val amount: Long,
    val accountId: String, val date: String, val category: String = "", val bucket: String = Bucket.FREE.name,
    val toAccountId: String? = null, val originalId: String? = null, val installmentId: String? = null,
    val note: String = "", val voided: Boolean = false, val transferId: String? = null,
    val incomePlanId: String? = null, val incomePlanMonth: String? = null, val repaymentId: String? = null,
    val recurringOccurrenceId: String? = null,
    @ColumnInfo(defaultValue = "''") val categoryGroupId:String="",
    @ColumnInfo(defaultValue = "''") val subcategoryId:String="",
    @ColumnInfo(defaultValue = "'UNDECIDED'") val necessity:String=Necessity.UNDECIDED.name)
@Entity(tableName = "budgets")
data class Budget(@PrimaryKey val month: String, val total: Long, val necessary: Long, val free: Long)
@Entity(tableName="category_budgets",primaryKeys=["month","categoryGroupId"])
data class CategoryBudget(val month:String,val categoryGroupId:String,val amount:Long)
@Entity(tableName = "goals")
data class Goal(@PrimaryKey val id: String = newId(), val name: String, val target: Long, val date: String,
    val intention: Long = 0, val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val saved: Long = 0)
@Entity(tableName = "income_plans")
data class IncomePlan(@PrimaryKey val accountId: String, val amount: Long, val day: Int,
    val source: String, val startMonth: String, val enabled: Boolean = true) {
    fun expectedDate(month: String): LocalDate = YearMonth.parse(month).let { it.atDay(minOf(day, it.lengthOfMonth())) }
}

data class SavingsProgress(val remaining: Long, val months: Long, val monthly: Long, val overdue: Boolean)
fun Goal.progress(now: LocalDate = today()): SavingsProgress {
    val remaining = (target - saved).coerceAtLeast(0)
    val overdue = LocalDate.parse(date) < now
    val months = if (overdue) 0L else ChronoUnit.MONTHS.between(YearMonth.from(now), YearMonth.from(LocalDate.parse(date))).coerceAtLeast(1)
    val monthly = if (remaining == 0L || months == 0L) 0L else remaining / months + if (remaining % months == 0L) 0 else 1
    return SavingsProgress(remaining, months, monthly, overdue)
}
@Entity(tableName = "debts")
data class Debt(@PrimaryKey val id: String = newId(), val name: String, val principal: Long, val purchaseDate: String,
    val note: String = "", val categoryId: String? = null,
    @ColumnInfo(defaultValue = "'LEGACY'") val scheduleType: String = "LEGACY")
@Entity(tableName = "debt_categories")
data class DebtCategory(@PrimaryKey val id: String = newId(), val name: String, val archived: Boolean = false)
fun defaultDebtCategories() = listOf(
    DebtCategory("debt-kind-shopping", "购物分期"), DebtCategory("debt-kind-credit", "信用卡欠款"),
    DebtCategory("debt-kind-loan", "贷款"), DebtCategory("debt-kind-personal", "亲友借款"), DebtCategory("debt-kind-other", "其他欠款")
)
enum class DebtSchedule(val label: String) { LEGACY("旧版明细"), EQUAL("每期相同"), CUSTOM("逐期设置") }
@Entity(tableName = "installments")
data class Installment(@PrimaryKey val id: String = newId(), val debtId: String, val number: Int,
    val date: String, val principal: Long, val fee: Long, val scheduledAmount: Long? = null) {
    val due: Long get() = scheduledAmount ?: Math.addExact(principal, fee)
}

/** Use the original day for each month: Jan 31 -> Feb 28 -> Mar 31. No interest is inferred. */
fun manualDebtSchedule(debtId: String, firstDate: String, amounts: List<Long>, existing: List<Installment> = emptyList()): List<Installment> {
    require(amounts.size in 1..MAX_DEBT_INSTALLMENTS) { "还款总期数须为 1–$MAX_DEBT_INSTALLMENTS 期" }
    val first = runCatching { LocalDate.parse(firstDate) }.getOrElse { error("首期还款日期应为 YYYY-MM-DD") }
    require(first.toString() == firstDate) { "首期还款日期应为 YYYY-MM-DD" }
    require(amounts.all { it in 1..MAX_MONEY } && amounts.exactSum() <= MAX_MONEY) { "每期金额须大于零，应还合计不能超出支持范围" }
    return amounts.mapIndexed { index, amount -> Installment(
        id = existing.find { it.number == index + 1 }?.id ?: newId(), debtId = debtId, number = index + 1,
        date = first.plusMonths(index.toLong()).toString(), principal = 0, fee = 0, scheduledAmount = amount)
    }
}
@Entity(tableName = "audits")
data class Audit(@PrimaryKey val id: String = newId(), val entityId: String, val action: String,
    val before: String, val after: String, val reason: String, val time: Long = System.currentTimeMillis())
data class Ledger(
    val profile: Profile = Profile(), val accounts: List<Account> = emptyList(), val entries: List<Entry> = emptyList(),
    val budgets: List<Budget> = emptyList(), val goals: List<Goal> = emptyList(),
    val debts: List<Debt> = emptyList(), val installments: List<Installment> = emptyList(), val audits: List<Audit> = emptyList(),
    val incomePlans: List<IncomePlan> = emptyList(), val debtCategories: List<DebtCategory> = emptyList(),
    val recurringRules: List<RecurringRule> = emptyList(), val recurringAllocations: List<RecurringAllocation> = emptyList(),
    val recurringOccurrences: List<RecurringOccurrence> = emptyList(), val reminder: ReminderSettings = ReminderSettings(),
    val categoryBudgets:List<CategoryBudget> = emptyList()
) {
    val activeEntries get() = entries.filterNot { it.voided }
    fun accountName(id: String?) = accounts.find { it.id == id }?.name ?: "未知账户"
    fun balance(id: String, cutoff: String = today().toString()): Long {
        val a = accounts.first { it.id == id }
        if (cutoff < a.start) return 0
        return Math.addExact(a.opening, activeEntries.filter { it.date >= a.start && it.date <= cutoff }.map { e ->
            when {
                e.kind == Kind.TRANSFER.name && e.toAccountId == id -> e.amount
                e.accountId != id -> 0L
                e.kind in listOf(Kind.EXPENSE.name, Kind.REPAYMENT.name, Kind.TRANSFER.name) -> -e.amount
                else -> e.amount
            }
        }.exactSum())
    }
    fun cash(cutoff: String = today().toString()): Long = accounts.map { balance(it.id, cutoff) }.exactSum()
    fun bucketOf(e: Entry): String = if (e.kind == Kind.REFUND.name) entries.first { it.id == e.originalId }.bucket else e.bucket
    fun categoryOf(e: Entry): String = if (e.kind == Kind.REFUND.name) entries.first { it.id == e.originalId }.category else e.category
    fun usage(month: String, bucket: String? = null): Long = activeEntries.filter {
        it.date.startsWith(month) && it.kind in listOf(Kind.EXPENSE.name, Kind.REFUND.name) && (bucket == null || bucketOf(it) == bucket)
    }.map { if (it.kind == Kind.REFUND.name) -it.amount else it.amount }.exactSum()
    fun income(month: String) = activeEntries.filter { it.date.startsWith(month) && it.kind == Kind.INCOME.name }.map { it.amount }.exactSum()
    fun refunds(id: String): Long = activeEntries.filter { it.kind == Kind.REFUND.name && it.originalId == id }.map { it.amount }.exactSum()
    fun payments(i: Installment): List<Entry> = activeEntries.filter { it.installmentId == i.id && it.kind == Kind.REPAYMENT.name }
    fun payment(i: Installment): Entry? = payments(i).firstOrNull()
    fun unpaid() = installments.filter { payment(it) == null }.sortedBy { it.date }
    fun budget(month: String) = budgets.find { it.month == month }
    fun incomeReceipt(accountId: String, month: String) = activeEntries.find { it.incomePlanId == accountId && it.incomePlanMonth == month }
    fun debtCategoryName(d: Debt) = debtCategories.find { it.id == d.categoryId }?.name ?: "未分类"
}

object Rules {
    fun validate(s: Ledger, now: LocalDate = today()) {
        require(s.profile.name.isNotBlank() && s.profile.name.length <= 40) { "账本名称须为 1–40 个字" }
        require(s.profile.id == 1) { "账本身份无效" }
        fun amount(n: Long, positive: Boolean = false) { require(n in (if (positive) 1L else 0L)..MAX_MONEY) { "金额超出范围或必须大于零" } }
        fun date(v: String): LocalDate = runCatching { LocalDate.parse(v) }.getOrElse { error("日期格式应为 YYYY-MM-DD") }.also {
            require(it.toString() == v) { "日期须使用 YYYY-MM-DD" }
        }
        fun unique(ids: List<String>) { require(ids.size == ids.distinct().size && ids.none { it.isBlank() }) { "存在重复或空白 ID" } }
        unique(s.accounts.map { it.id }); unique(s.entries.map { it.id }); unique(s.goals.map { it.id })
        unique(s.debts.map { it.id }); unique(s.installments.map { it.id }); unique(s.audits.map { it.id })
        unique(s.debtCategories.map { it.id })
        s.debtCategories.forEach { require(it.name.isNotBlank() && it.name == it.name.trim() && it.name.length <= 30) { "欠款种类须为 1–30 个字，首尾不能有空格" } }
        require(s.debtCategories.map { it.name.lowercase(Locale.ROOT) }.distinct().size == s.debtCategories.size) { "这个欠款种类已存在，请换一个名称" }
        require(s.budgets.map { it.month }.distinct().size == s.budgets.size) { "月份预算重复" }
        s.accounts.forEach { a ->
            require(a.name.isNotBlank() && a.name.length <= 30) { "账户名称须为 1–30 个字" }
            require(a.opening in -MAX_MONEY..MAX_MONEY) { "开始余额超出范围" }
            require(!date(a.start).isAfter(now)) { "开始记账日不能在未来" }
            a.disabledOn?.let { require(it >= a.start && !date(it).isAfter(now)) { "停用日期无效" } }
        }
        s.budgets.forEach {
            require(YearMonth.parse(it.month).toString() == it.month) { "月份格式无效" }
            amount(it.total); amount(it.necessary); amount(it.free)
            require(Math.addExact(it.necessary, it.free) <= it.total) { "必需与可选预算之和不能超过总预算" }
        }
        s.goals.forEach { amount(it.target, true); amount(it.intention); amount(it.saved); date(it.date)
            require(it.name.isNotBlank() && it.name.length <= 40) { "目标名称须为 1–40 个字" } }
        unique(s.incomePlans.map { it.accountId })
        s.incomePlans.forEach { p ->
            require(s.accounts.any { it.id == p.accountId }) { "固定收入的到账账户不存在" }
            amount(p.amount, true)
            require(p.day in 1..31) { "每月到账日须为 1–31 日" }
            require(p.source.isNotBlank() && p.source.length <= 40) { "收入来源须为 1–40 个字" }
            require(YearMonth.parse(p.startMonth).toString() == p.startMonth) { "开始月份应为 YYYY-MM" }
        }
        s.debts.forEach { d ->
            require(d.scheduleType in DebtSchedule.entries.map { it.name }) { "还款方式无效" }
            val legacy = d.scheduleType == DebtSchedule.LEGACY.name
            require(d.name.isNotBlank() && (legacy || d.name.length <= 40)) { "负债名称须为 1–40 个字" }
            if (legacy) amount(d.principal, true) else require(d.principal == 0L) { "自定还款不自动推算借款本金" }
            require(d.categoryId == null || s.debtCategories.any { it.id == d.categoryId }) { "欠款种类不存在" }
            require(legacy || d.categoryId != null) { "请选择欠款种类" }
            require(!date(d.purchaseDate).isAfter(now)) { "欠款发生日不能在未来" }
            val rows = s.installments.filter { it.debtId == d.id }.sortedBy { it.number }
            require(rows.size in 1..MAX_DEBT_INSTALLMENTS) { "还款总期数须为 1–$MAX_DEBT_INSTALLMENTS 期" }
            require(rows.map { it.number } == (1..rows.size).toList()) { "还款期数不连续" }
            if (legacy) {
                require(rows.map { it.principal }.exactSum() == d.principal) { "各期借款金额合计必须等于原借款金额" }
                require(rows.all { it.scheduledAmount == null }) { "旧版明细不能改成未拆分的还款金额" }
            } else {
                require(rows.all { it.principal == 0L && it.fee == 0L && it.scheduledAmount != null }) { "自定还款须填写每期金额，不推算本金和利息" }
                amount(rows.map { it.due }.exactSum(), true)
                if (d.scheduleType == DebtSchedule.EQUAL.name) require(rows.map { it.due }.distinct().size == 1) { "选择每期相同时，各期金额必须一致" }
                require(rows.map { it.date } == rows.indices.map { LocalDate.parse(rows.first().date).plusMonths(it.toLong()).toString() }) { "自定还款须按首期日期逐月安排" }
            }
            rows.forEach { amount(it.principal); amount(it.fee)
                if (legacy) require(it.due > 0) { "每期应还须大于零" } else amount(it.due, true)
                require(date(it.date) >= date(d.purchaseDate)) { "应还日不能早于欠款发生日" } }
            require(rows.zipWithNext().all { (a,b) -> a.date < b.date }) { "应还日期须逐期递增" }
        }
        s.installments.forEach { require(s.debts.any { d -> d.id == it.debtId }) { "还款表缺少所属合同" } }
        s.entries.forEach { e ->
            val kind = runCatching { Kind.valueOf(e.kind) }.getOrElse { error("不支持的交易类型") }
            ExpenseTaxonomy.validate(e.categoryGroupId,e.subcategoryId,e.necessity)
            require(kind==Kind.EXPENSE || e.categoryGroupId.isEmpty()&&e.subcategoryId.isEmpty()&&e.necessity==Necessity.UNDECIDED.name){"只有消费记录保存分类与必要性；退款随原消费更新"}
            require(e.bucket in Bucket.entries.map { it.name }) { "预算类型无效" }
            require(kind == Kind.TRANSFER || e.toAccountId == null) { "只有转账可以指定转入账户" }
            require(kind == Kind.REFUND || e.originalId == null) { "只有退款可以关联原消费" }
            require(kind == Kind.REPAYMENT || e.installmentId == null) { "只有还款可以关联期次" }
            require(kind == Kind.REPAYMENT || e.repaymentId == null) { "只有还款可以指定组合记录" }
            require(e.repaymentId == null || e.repaymentId.isNotBlank()) { "还款记录编号无效" }
            require(e.transferId == null || kind == Kind.EXPENSE) { "关联转账手续费必须是独立消费" }
            if (e.incomePlanId != null || e.incomePlanMonth != null) {
                require(kind == Kind.INCOME && e.incomePlanId != null && e.incomePlanMonth != null) { "固定收入记录缺少对应月份" }
                require(e.accountId == e.incomePlanId && s.incomePlans.any { it.accountId == e.incomePlanId }) { "固定收入的到账账户不匹配" }
                require(YearMonth.parse(e.incomePlanMonth).toString() == e.incomePlanMonth && YearMonth.parse(e.incomePlanMonth) <= YearMonth.from(now)) { "不能登记未来月份的固定收入" }
            }
            if (kind in listOf(Kind.INCOME, Kind.EXPENSE)) require(e.category.isNotBlank()) { "请填写来源或消费分类" }
            if (kind == Kind.ADJUSTMENT) require(e.amount != 0L && e.amount in -MAX_MONEY..MAX_MONEY) { "调整差额须为非零金额" }
            else amount(e.amount, true)
            require(!date(e.date).isAfter(now)) { "真实交易不能使用未来日期" }
            val account = s.accounts.find { it.id == e.accountId } ?: error("账户不存在")
            require(e.date >= account.start) { "交易早于开始记账日，请先核对并向前扩展开始余额" }
            if (kind == Kind.TRANSFER) {
                val target = s.accounts.find { it.id == e.toAccountId } ?: error("请选择转入账户")
                require(target.id != account.id) { "不能向同一个账户转账" }
                require(e.date >= target.start) { "交易早于转入开始记账日" }
            }
            if (kind == Kind.REFUND) {
                val original = s.entries.find { it.id == e.originalId } ?: error("退款必须关联原消费")
                require(original.kind == Kind.EXPENSE.name) { "仅支持日常消费退款" }
                if (!e.voided) require(!original.voided) { "有有效退款时不能撤销原消费" }
                require(e.date >= original.date) { "退款不能早于原消费" }
            }
            if (kind == Kind.REPAYMENT) {
                val row = s.installments.find { it.id == e.installmentId } ?: error("还款未关联合同期次")
                require(e.amount <= row.due) { "单个账户扣款不能超过当期应还金额" }
                if (e.repaymentId == null) require(e.amount == row.due) { "旧版单账户还款金额须等于当期应还" }
                val d = s.debts.first { it.id == row.debtId }
                require(e.date >= d.purchaseDate) { "还款不能早于欠款发生日" }
            }
            e.transferId?.let { id -> require(s.entries.any { it.id == id && it.kind == Kind.TRANSFER.name }) { "手续费缺少原转账" } }
        }
        s.activeEntries.filter { it.kind == Kind.EXPENSE.name }.forEach { require(s.refunds(it.id) <= it.amount) { "累计退款不能超过原消费金额" } }
        s.activeEntries.filter { it.kind == Kind.REPAYMENT.name }.groupBy { it.installmentId }.forEach { (id, payments) ->
            val row = s.installments.first { it.id == id }
            require(payments.map { it.amount }.exactSum() == row.due) { "各账户扣款合计须等于当期应还金额，请勿重复还款" }
            require(payments.map { it.accountId }.distinct().size == payments.size) { "同一期中，每个账户只能填写一次" }
            require(payments.map { it.date }.distinct().size == 1) { "组合还款须使用同一实际还款日期" }
            if (payments.size > 1) require(payments.all { it.repaymentId != null } && payments.map { it.repaymentId }.distinct().size == 1) { "组合还款须一起登记" }
        }
        s.entries.filter { it.repaymentId != null }.groupBy { it.repaymentId }.forEach { (_, group) ->
            require(group.map { it.installmentId }.distinct().size == 1 && group.map { it.date }.distinct().size == 1 && group.map { it.accountId }.distinct().size == group.size) { "同一次组合还款的期次、日期或账户不一致" }
            require(group.map { it.voided }.distinct().size == 1) { "组合还款必须整笔撤销" }
            require(group.map { it.amount }.exactSum() == s.installments.first { it.id == group.first().installmentId }.due) { "组合还款历史金额与当期应还不一致" }
        }
        val receipts = s.activeEntries.filter { it.incomePlanId != null }
        require(receipts.map { it.incomePlanId to it.incomePlanMonth }.distinct().size == receipts.size) { "这个账户本月的固定收入已登记，请勿重复记账" }
        require(s.categoryBudgets.map{it.month to it.categoryGroupId}.distinct().size==s.categoryBudgets.size){"分类预算重复"}
        s.categoryBudgets.forEach{cap->
            require(YearMonth.parse(cap.month).toString()==cap.month&&s.budgets.any{it.month==cap.month}){"请先设置对应月份的总预算"}
            require(ExpenseTaxonomy.groups.any{it.id==cap.categoryGroupId}&&cap.amount in 0..MAX_MONEY){"分类预算无效"}
        }
        s.categoryBudgets.groupBy{it.month}.forEach{(month,caps)->require(caps.map{it.amount}.exactSum()<=s.budget(month)!!.total){"分类预算合计不能超过月度总预算，请先调整分类额度"}}
        validateRecurring(s, now)
        s.cash() // Verify aggregate overflow before committing.
    }
    fun checkEnabled(s: Ledger, e: Entry) {
        if (e.kind == Kind.REFUND.name) return
        listOfNotNull(e.accountId, e.toAccountId).forEach { id ->
            val a = s.accounts.first { it.id == id }
            require(a.disabledOn == null || e.date < a.disabledOn) { "账户已停用，请先启用；仅可补录停用日前的真实交易" }
        }
    }
    fun replaceEntry(s: Ledger, entry: Entry): Ledger {
        val old = s.entries.find { it.id == entry.id }
        require(old?.voided != true) { "已撤销记录不能编辑" }
        if (old != null) require(old.kind == entry.kind) { "编辑不能变更交易类型" }
        if (old != null) require(old.incomePlanId == entry.incomePlanId && old.incomePlanMonth == entry.incomePlanMonth) { "修改收入时请保留原固定收入月份" }
        if (old != null) require(old.repaymentId == entry.repaymentId) { "还款账户记录不能拆开修改" }
        if (old != null) require(old.recurringOccurrenceId == entry.recurringOccurrenceId) { "固定记账记录须保留对应计划" }
        if (old == null || old.date != entry.date || old.accountId != entry.accountId || old.toAccountId != entry.toAccountId) checkEnabled(s, entry)
        return s.copy(entries = s.entries.filterNot { it.id == entry.id } + entry).also { validate(it) }
    }
}
